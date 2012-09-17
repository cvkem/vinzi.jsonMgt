(ns vinzi.jsonMgt.mainClDb
  (:require [vinzi.jsonMgt
	     [database :as dbps]
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


(defn procClDb []
  (glb/setDefPostfix glb/cdfdePostfix)
  (glb/installConfirmReader cl/confirmReaderCL)
  ;; selection of the database backend
  (dbps/installDatabaseAsPS)
  (cl/processCommandStdin)
  (ps/ps_closeDatabase)
  )

(defn -main [& args]
  (if (seq args)
    (println "The arguments are ignored: " args))
  (procClDb))

