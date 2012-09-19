(ns vinzi.jsonMgt.init-persistentstore
  (:use	   [clojure pprint]
           [clojure.tools logging])
  (:require [vinzi.jsonMgt
             [hibernate :as hibps]
             [database :as dbps]
             [globals :as glb]
             [persistentstore :as ps]]
            [clojure [string :as str]]))

;; dynamic loading does not work yet.
;; The hibernate database is always loaded.
;; (probably need to do conditional/lazy loading of the hibernate-jar)

(defn init-persistentstore "Select one of the database-interfaces, install the interface and initialize the database"
  [cfg]
  (let [lpf "(init-persistentstore): " 
        _ (debug lpf "  with paramters: " (with-out-str (pprint cfg)))
        dbInt (:database_interface cfg)]
    (if (= dbInt "hibernate")
      (do
        ;; dynamic load of hibernate. If hibernate is defined in the
        ;; name-space at the top it will start its initialization
        ;;	(use '(vinzi.jsonMgt hibernate))
        ;;	(installDatabaseAsPS))
        (debug  lpf "Installing the hibernate interface")
        (hibps/installDatabaseAsPS))
      (if (= dbInt "sql")
        (do
          ;	  (use '(vinzi.jsonMgt database))
          ;	  (installDatabaseAsPS))
          ;	  (require '(vinzi.jsonMgt [database :as dbps]))
          ;	  (require ['database :as 'dbps])
          (debug lpf "Installing the database interface")
          (dbps/installDatabaseAsPS))
        (warn lpf "No database-interface selected!!"
              "\n\tAdd an option \"database_interface\" with value "
                   "\n\t   \"hibernate\"  or \"sql\".")))
  
    (if (ps/ps-bindings-initialized?)
      (do 
        (debug lpf "Now calling initDatabase.")
        (ps/ps_initDatabase cfg))
      (error lpf "No bindings for the persistent store yet."))))

