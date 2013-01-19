(ns vinzi.cdm.cdm-main
  (:use clojure.tools.logging
        clojure.pprint)
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as sql]
            [vinzi.tools 
             [vExcept :as vExcept]
             [vCsv :as vCsv]]
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
    ([] (initialize false))
    ([forceInit]
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
                                (vExcept/throw-except lpf "Could not find jndi/connection for name: " db))
                              dbCfg)))]   ;; return unmodified
        (when (or (not @initialized?) forceInit)
          (info "Initialize the CDM.")
          (info jmgt/introMessage)
          (glb/setDefPostfix cdfdePostfix)
          
          ;; let the doc-root match pentaho-Connect (derives it from classpath)
          (jmgt/set-doc-root (pConn/get-solution-folder))
          
          ;; select the persistent-storage backend (and initialize the databases when needed)
          (if-let [databaseCfg @(ns-resolve 'vinzi.cdp.ns.cdm 'databaseCfg)]
            (ips/init-persistentstore (lookup-jndi databaseCfg)) ;; lookup-jndi throws exception on failure
            (vExcept/throw-except lpf "Could not resolve vinzi.cdp.ns.cdm/databaseCfg"))
          ;; if this point is reached without exceptions than assume the initialization has succeeded.
          (swap! initialized? (fn [_] true))          
          (info "Initialization of the CDM finished." )))))
    

 
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
  (let [lpf "(get-command-rec): "
        get-param (fn [theKey] 
  					(let [par (get params theKey) 
        				  par (when par (str/split (str/trim par) #"\s+"))
        				  par (if (and (= (count par) 1) (= (first par) "")) '() par)]
               (debug lpf "for key: " theKey " retrieved value: " par)
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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;    get html-tables for actions and for errors.
;;    Out-dated code, which is replaced by the json-interface (see below)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(def tblFmt "<table border=1>\n\t<tbody>%s\t</tbody>\n</table>")
(def tblRowFmt "\t\t<tr>%s\t\t</tr>")
(def tblHeadFmt "\t\t\t<th>%s</th>")
(def tblDataFmt "\t\t\t<td>%s</td>")

(defn generate-html-table 
  "Generate a html-table element filled with the data of tbl 
   (a list of lists with headerlines in the first row)."
  [tbl]
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;    Get CDA json-data for actions and for errors that correspond to the sources in params.
;;    (Replaces the old-code that returned html-fragments.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def list-actions "list-actions")
(def list-errors "list-errors")

(defn get-recent-actionsErrors
  "Generate a json-table showing the most recent actions/errors for sources."
  [params action]
  (let [lpf "(get-recent-actionsErrors): " 
        cmd (get-command-rec {:action action
                              :source (:source params)})
        _ (debug lpf "going to execute command" cmd)
        tbl (ps/ps_callWithConnection (partial jmgt/processCommand cmd))
        _ (debug lpf " received: " tbl)
        tbl (if (seq tbl)
              (vCsv/csv-to-map tbl)
              ;; if list is empty, then return empty data:
              (if (= action list-actions)
                {:id "-" :track "-" :action "-" :datetime "-" :d_user "-"}
                {:id "-" :track "-" :command "-" :datetime "-" :d_user "-" :error "-"}))]
    (debug lpf " returning: " tbl)
    tbl
    ))

(defn get-recent-actions
  "Generate a json-table showing the most recent actions for sources."
  [params]
  (get-recent-actionsErrors params list-actions))

(defn get-recent-errors
  "Generate a json-table showing the most recent errors for sources."
  [params]
  (get-recent-actionsErrors params list-errors))


(defn get-differences
  "Generate a json-table representing the differences."
  [params]
  (let [lpf "(get-differences): " 
        source (:source params)
        cmd (get-command-rec {:action "diffjson"
                              :source source})
        _ (debug lpf "going to execute command" cmd)
        get-diffs (fn [cmd]
                    (let [tbl (ps/ps_callWithConnection (partial jmgt/processCommand cmd))
                          buildPath (fn [patch]
                                      (->> patch
                                        (:pathList)
                                        (rest)
                                        (map name)
                                        (str/join "/")
                                        (assoc patch :path)
                                        (#(dissoc % :pathList))))
                          stringValue (fn [patch]
                                        (assoc patch :value (json/json-str (:value patch))))]
                      (debug lpf " received: " tbl)
                      (->> tbl
                        (map buildPath)
                        (map stringValue)
                        (sort-by :path))))
        tbl (if (re-find #"\*" source)
              {:path "-" :key "-" :value "-" :action (str "No data for source: " source)}    ;; if a mask or "*" is provide then return an empty list
              (get-diffs cmd))]
    (debug lpf " returning: " tbl)
    tbl))




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;    Get CDA json-data for actions and for errors that correspond to the sources in params.
;;    (Replaces the old-code that returned html-fragments.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; TODO: Add a locking mechanisme to prevent two users from simultaneously modifying the same dashboard/track
;; (should this locking be in jsonMgt.core or here?)
(debug "TODO: Add a locking mechanisme to prevent two users from simultaneously modifying the same dashboard/track
           (should this locking be in jsonMgt.core or here?)")



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
  


