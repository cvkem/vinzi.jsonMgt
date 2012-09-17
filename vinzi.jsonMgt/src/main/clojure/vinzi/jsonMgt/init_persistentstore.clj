(ns vinzi.jsonMgt.init-persistentstore
  (:require [vinzi.jsonMgt
	     [hibernate :as hibps]
	     [database :as dbps]
	     [globals :as glb]
	     [persistentstore :as ps]]
	    [clojure [string :as str]]))

;; dynamic loading does not work yet.
;; The hibernate database is always loaded.

(defn init-persistentstore "Select one of the database-interfaces, install the interface and initialize the database"
  [cfg]
  (let [dbInt (:database_interface cfg)]
    (if (= dbInt "hibernate")
      (do
	;; dynamic load of hibernate. If hibernate is defined in the
	;; name-space at the top it will start its initialization
;;	(use '(vinzi.jsonMgt hibernate))
	;;	(installDatabaseAsPS))
	(println "")
	(println  "Installing the hibernate interface")
	(hibps/installDatabaseAsPS))
      (if (= dbInt "sql")
	(do
;	  (use '(vinzi.jsonMgt database))
;	  (installDatabaseAsPS))
;	  (require '(vinzi.jsonMgt [database :as dbps]))
;	  (require ['database :as 'dbps])
	(println)
	(println  "Installing the database interface")
	  (dbps/installDatabaseAsPS))
	(do
	  (println "No database-interface selected!!")
	  (println "Add an option \"database_interface\" with value "
		   "  \"hibernate\"  or \"sql\".")
	  (assert nil)))))

  (ps/ps_initDatabase cfg)
)

