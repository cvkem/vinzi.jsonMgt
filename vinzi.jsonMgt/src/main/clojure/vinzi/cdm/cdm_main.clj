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
             [init-persistentstore :as ips]]))


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
  
