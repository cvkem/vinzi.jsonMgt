(ns vinzi.cdm.cdm-main
  (:use clojure.tools.logging
        clojure.pprint)
  (:require [clojure.string :as str]
            [clojure.java.jdbc :as sql]
            [vinzi.pentaho 
             [connect :as pConn]]
            [vinzi.jsonMgt
             [core :as jmgt]
             [globals :as glb]
             [persistentstore :as ps]
             [init-persistentstore :as ips]]
            [vinzi.cdm
             [globals :as cgl]
             [jqueryFileTree :as jqft]])
  (:import [java.sql Date]))


  (def initialized? (atom false))

  (def helpModus (atom false))

  (def cdfdePostfix ".cdfde")

  (defn initialize
    "Initialize the system, read config-file and install interfaces."
    []
    (let [lpf "(initialize): "
          lookup-jndi (fn [dbCfg]
                        ;; if db is a string, then this string is used to lookup a jndi-connnection
                        ;;  in the pentaho-hibernate database.
                        (let [db (:db dbCfg)]
                          (if (string? db)
                            (if-let [dbParams (pConn/find-connection db)]
                              (do
                                (debug lpf "jndi " db " translates to connection-parameters: "
                                       (with-out-str (pprint (assoc dbParams :password "..."))))
                                (assoc dbCfg :db dbParams))
                              (error lpf "Could not find jndi/connection for name: " db))
                            dbCfg)))]   ;; return unmodified
      (when (not @initialized?)
        (info "Initialize the CDM.")
        (swap! initialized? (fn [_] true))
        (info jmgt/introMessage)
        (glb/setDefPostfix cdfdePostfix)

        ;; let the doc-root match pentaho-Connect (derives it from classpath)
        (jmgt/set-doc-root (pConn/get-solution-folder))
        
        ;; select the persistent-storage backend (and initialize the databases when needed)
        (if-let [databaseCfg @(ns-resolve 'vinzi.cdp.ns.cdm 'databaseCfg)] 
          (ips/init-persistentstore (lookup-jndi databaseCfg))
          (error lpf "Could not resolve vinzi.cdp.ns.cdm/databaseCfg"))
        (info "Initialization of the CDM finished." ))))


 
  (defn get-doc-root 
    "Return the doc_root (base of the pentaho-solution folder, terminated by a /"
    []
    ;; ensure initialization
    (initialize)
    jmgt/doc_root)
  
  
  
(defn genFileView "Wrapper to ensure initialization." [params]
  (initialize)
  (jqft/jqGenerateFileView params))



(defn get-command-rec 
 "Generate a command for jsonMgt based on action and the :src and :dst from the params.
  Further parameter-checking will be added."
  [params]
  (let [get-param (fn [theKey] 
  					(let [par (get params theKey) 
        				  par (when par (str/split (str/trim par) #"\s+"))
        				  par (if (and (= (count par) 1) (= (first par) "")) '() par)]
    					par))
  		action (str/lower-case (str/trim (:action params)))
;;      user   (str/trim (:user params))
      src    (get-param :source)
      dst    (get-param :destinations)
      msg    (get-param :msg)]
    {:command action
     :src  src
     :dst  dst
     :msg  msg}))



(def tblFmt "<table border=1>\n\t<tbody>%s\t</tbody>\n</table>")
(def tblRowFmt "\t\t<tr>%s\t\t</tr>")
(def tblHeadFmt "\t\t\t<th>%s</th>")
(def tblDataFmt "\t\t\t<td>%s</td>")

(defn generate-html-table [tbl]
  (letfn [(generateRow [fmt items]
		       (println "genRow, fmt= " fmt " and items= " items)
		       (flush)
		       (format tblRowFmt
			       (apply str
				      (map
				       #(format fmt %)
					   items))))
	  (headRow [items]
		   (generateRow tblHeadFmt (map name items)))
	  (dataRow [items]
		   (generateRow tblDataFmt items))
	  (genTblContents [tbl]
			  (let [hdr (first tbl)
				data (rest tbl)]
			    (apply str (headRow hdr) (map dataRow data))))]
    (format tblFmt (genTblContents tbl))))


(def maxTblRows 10)

   
(defn show-table
  "Generate a html-table based on the 'tblSel' parameter."
  [params]
  (let [lpf "(show-table): " 
        tblSel (:tblSel params)
        action (if (= tblSel "actions") "list-actions" "list-errors")
        cmd (get-command-rec {:action action
                              :source (:source params)})
        _ (debug lpf "going to execute command" cmd)
        tbl (ps/ps_callWithConnection (partial jmgt/processCommand cmd))
        _ (trace lpf "\nReceived the list: " tbl)
        tbl (take maxTblRows tbl)  ;; limit length
        html (generate-html-table tbl)]
    (println "\n\nResulted in html: " html)
;;    (cgl/getHtmlResponse html)
    html
    ))



;; TODO: Add a locking mechanisme to prevent two users from simultaneously modifying the same dashboard/track
;; (should this locking be in jsonMgt.core or here?)

(defn process-jsonMgt-command
  "Collect the relavant data and call the jsonMgt."
  [params]
  (initialize)
  (let [lpf "(process-jsonMgt-command): "
        commandRec (get-command-rec params)
        command     (:command commandRec)]
    (letfn [(switch-to-help-modus []
                                    (println "switch help modus true")
                                    (swap! helpModus (fn [_] true))
                                    (str "Click a command button to view help.\n"
                                         "Click help again for an overview of the available commands)"))
            (procComm []
                      (info "process command " command)
                      (if (= command "help")
                        (switch-to-help-modus)
                        (let [response (with-out-str
                                         (jmgt/processCommand commandRec))
                              response (if (or (nil? response)
                                               (= response "")
                                               (not (jmgt/returnTextOutput command)))
                                         (str "Succesfull performed command "
                                              commandRec)
                                         response)]
                          (doall response))))
            (procHelp []
                      (swap! helpModus (fn [_] false))  ;; turn it off again
                      (with-out-str (jmgt/processCommand {:command "help"
                                                            :src  (list command)
                                                            :dst  '()})))]
           (glb/set-override-username (:user params))
           (try
             (let [response (if @helpModus 
                              (procHelp) 
                              (ps/ps_callWithConnection procComm))]
               (debug "Received result: " response)
               response)  ;; result on success
             (finally   ;; ensure that override-username is reset 
               ;; error-propagation is let to the cdp plugin
                        (glb/set-override-username nil))))))
  


