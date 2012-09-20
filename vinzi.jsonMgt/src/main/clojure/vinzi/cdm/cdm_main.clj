(ns vinzi.cdm.cdm-main
  (:use clojure.tools.logging)
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
             [jqueryFileTree :as jqft]]))


  (def initialized? (atom false))

  (def helpModus (atom false))

  (def cdfdePostfix ".cdfde")

  (defn initialize
    "Initialize the system, read config-file and install interfaces."
    []
    (let [lpf "(initialize): "]
      (when (not @initialized?)
        (info "Initialize the CDM.")
        (swap! initialized? (fn [_] true))
        (info jmgt/introMessage)
        (glb/setDefPostfix cdfdePostfix)

        ;; let the doc-root match pentaho-Connect (derives it from classpath)
        (jmgt/set-doc-root (pConn/get-solution-folder))
        
        ;; select the persistent-storage backend (and initialize the databases when needed)
        (if-let [databaseCfg @(ns-resolve 'vinzi.cdp.ns.cdm 'databaseCfg)] 
          (ips/init-persistentstore databaseCfg)
          (error lpf "Could not resolve vinzi.cdp.ns.cdm/databaseCfg"))
        (info "Initialization of the CDM finished." ))))


 
  (defn get-doc-root 
    "Return the doc_root (base of the pentaho-solution folder, terminated by a /"
    []
    ;; ensure initialization
    (initialize)
    jmgt/doc_root)
  
  
  
(defn genFileView "Wrapper to ensure initialization." [req]
  (initialize)
  (jqft/jqGenerateFileView req))



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
  "Generate a html-table based on the 'tblSel' and the parameter in 'req'."
  [req tblSel]
  (let [params (:params req)
        action (if (= tblSel "actions") "list-actions" "list-errors")
        cmd (get-command-rec action params)
        _ (println "going to execute command" cmd)
        tbl (jmgt/processCommand cmd)
        _ (println "\nReceived the list: " tbl)
        tbl (take maxTblRows tbl)  ;; limit length
        html (generate-html-table tbl)]
    (println "\n\nResulted in html: " html)
    (cgl/getHtmlResponse html)))


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
                                                 (= response ""))
                                           (str "Succesfull performed command "
                                                commandRec)
                                             response)]
                            (doall response))))
              (procHelp []
                        (swap! helpModus (fn [_] false))
                        (with-out-str (jmgt/processCommand {:command "help"
                                                            :src  (list command)
                                                            :dst  '()})))]
           (let [response (if @helpModus 
                               (procHelp) 
                               (ps/ps_callWithConnection procComm))]
             (info "Received result: " response)
             response))))
  
