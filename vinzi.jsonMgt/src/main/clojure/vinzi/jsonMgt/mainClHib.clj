(ns vinzi.jsonMgt.mainClHib
  (:require [vinzi.jsonMgt
;;	     [hibernate :as hibps]
	     [core :as jmgt]
	     [globals :as glb]
	     [commandline :as cl]
	     [persistentstore :as ps]])
 (:use clojure.stacktrace)
)

(defn prst []
  (print-stack-trace (root-cause *e)))

(defn prct []
  (print-cause-trace *e))



(defn procClHib []
  (letfn [(initClHib []
                     (glb/setDefPostfix glb/cdfdePostfix)
                     (glb/installConfirmReader cl/confirmReaderCL)
                     ;; read-config-file AND install the database interface
                     (jmgt/readJsonMgtConfig))
          (shutdownClHib []
                         (ps/ps_closeDatabase))]
         (initClHib)
  (cl/processCommandStdin)
  (shutdownClHib)))


(defn -main [& args]
  (if (seq args)
    (println "The arguments are ignored: " args))
  (procClHib))

