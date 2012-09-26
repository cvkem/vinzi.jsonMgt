(ns vinzi.jsonMgt.database
    (:use	 [clojure pprint]
           [clojure.tools logging]
           [vinzi.jsonMgt globals])
    (:use [vinzi.json.jsonZip :only [isJson?]]
          [vinzi.json.jsonDiff :only [getPathList keywordize getPathStrPatch]])
        ;;:only [Patch]
  (:require  [vinzi.jsonMgt [persistentstore :as ps]]
             [clojure.string :as str]
             [clojure.java
              [jdbc :as sql]]
             [clojure.data
              [json :as json]])
        (:import [vinzi.json.jsonDiff Patch]
                 [java.io File]
                 [java.util Date]
                 [java.sql SQLException Timestamp])
 )

;;;;;;;;;;;;;;;;;;;;;;;;;
;;  REMARK: The database-connector has a significant code overlap
;;  with the hibernate connector. If development on both code-bases
;;  is continued than the code should be refactored to separate out
;;  the common parts.
;;  This would probably mean the introduction of a second interface
;;  (persistant store split in commons and specifics).
;;  However, note the difference in for example db_getCommit between
;;  hibernate and database.

;; default settings using an in-memory hypersonic database
(def ^:dynamic  db {
	 :classname "org.hsqldb.jdbc.JDBCDriver"
	 :subprotocol "hsqldb"
	 :subname  "//localhost"
;;	  :subname  "mem:testdb"
	 :user  "SA"
	 :password ""})

(defn generateDb [{:keys [classname subprotocol
			  db-host db-port db-name
			  user password]}]
 (def db {:classname classname ; must be in classpath
          :subprotocol subprotocol
          :subname (str "//" db-host ":" db-port "/" db-name)
          :user user
          :password password}))


(def ^:dynamic dbs
     {:db_scheme    nil   ;; default is no scheme (use public scheme)
      :commit_db    "commits"
      :patch_db     "patches"
      :no_prefix    ""
      :track_info   "track_info"
      :action_log   "log_actions"
      :error_log    "log_errors"
      ;; added surrouding spaces to make concatenation easier.
;;      :autokey      " IDENTITY "
      :autokey      " SERIAL "
      ;; mysql: MEDIUMINT NOT NULL AUTO_INCREMENT
      :int          " INTEGER "
      :keytype      " INTEGER "
;;      :keytype      " LONG "
      :double       " double precision "
      :datetime     " timestamp "   ;; of date
      :text         " varchar "
;;      :longtext     " LONGVARCHAR "
      :longtext     " varchar"
;;      :longtext     " LONGTEXT " bestaat niet in postgres
;;      :doc_root     ""
      })



(defn  switchDb [theDb]
  "used to switch to other scheme for unit-testing"
  (def db theDb))


(defn switchDbsScheme [dbScheme]
  "used to switch to other scheme for unit-testing"
  (def dbs (assoc dbs :db_scheme dbScheme)))



;; Installs a schema in the default in-memory hsqldb ??
(defn installInMemoryDb []
  (let [db_scheme (str/trim (str (:db_scheme dbs)))
        db_scheme (if (seq db_scheme)
                    db_scheme
                    (do
                      (switchDbsScheme "test")
                      (:db_scheme dbs)))]
  (sql/with-connection db 
     (sql/do-commands
			   (format "CREATE SCHEMA %s;" db_scheme)))))


(defn setDbsRecord [dbName dbType]
  (def dbs (into dbs (into dbName dbType))))

(declare initScheme)

(defn db_call-with-connection [f & args]
  (let [lpf "(call-with-connection): "]
    (sql/with-connection db
                         (if (initScheme)
                           ;;        (if (ps/ps_initScheme)
                           (let [res (apply f args)]
                             ;;	    (println "Call within call-with-database returned: " res)
                             res)
                           (error lpf "Initialization of Scheme aborted")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  Auxiliary (helper) functions
;;


(defn dbRecToPatch
  "translate a database-record 'rec' to a Patch object"
  [rec]
  (let [{:keys [path action patchkey value]} rec
        pathList (getPathList path)
        act      (keyword action)
        pKey     (keywordize patchkey)
        val      (json/read-json value) ]
    (Patch. pathList act pKey val)))


(defn patchToDbRec
  "Translate a patch object to a database record (hash-map)"
  [trackId patch dt]
    {:datetime dt
     :track_id trackId
     :path   (getPathStrPatch patch)
     :action (name (:action patch))
     :patchkey    (str (:key patch))
     :value  (json/json-str (:value patch))} )




(defn getDbName*
  "Return the database name, prefixed by the scheme if set."
  [dbNameKeyw]
  (let [db_scheme (when-let [db_scheme (:db_scheme dbs)]
                    (str/trim db_scheme))
        nme (name dbNameKeyw)]
    (if (seq db_scheme)  
      (str db_scheme "." nme)
      nme)))



(defn getCommitDb [] (getDbName* :cdm_commit))
(defn getPatchDb [] (getDbName* :cdm_dbpatch))
(defn getTrackInfoDb []  (getDbName* :cdm_trackinfo))
(defn getActionLogDb []  (getDbName* :cdm_actionentry))
(defn getErrorLogDb []  (getDbName* :cdm_errorentry))




(defn db_writeErrorEntry
  [errorEntry]
  (let [errorEntry (if (nil? (:id errorEntry))
                     (dissoc errorEntry :id)
                     (errorEntry))]
        (sql/insert-records (getErrorLogDb) errorEntry)
        true))

(defn db_writeActionEntry
  [actionEntry]
  (let [actionEntry (if (nil? (:id actionEntry))
                      (dissoc actionEntry :id)
                      actionEntry)]
    (sql/insert-records (getActionLogDb) actionEntry)
    true))




(defn- existsTable?
  "Check whether the tables 'name' exists."
  [name]
  (let [lpf "(existsTable?): "]
    (try
      (sql/with-query-results recs
                              [(format "SELECT COUNT(*) FROM %s;" name)]
                              true)   ;; table exists (no exception)
      (catch Exception e
          ;; failure can be expected
          (debug lpf "mess: " (.getMessage e))
          false)))) 



;; only used internally (called by 'call-with-database')
(defn initScheme
  "Initialize a scheme (or database) by creating the all five tables. 
   The function only returns true if all five operations are succesfull. 
   An action-log entry is generated for each table, so the action-table is created first."
  []
  (let [lpf "(initScheme): "]
    (letfn [(checkTable [tableName & tableSpecs]
                        (debug lpf "check table: " tableName) 
                        (if (existsTable? tableName)
                          true
                          (if (confirmReader (format "Create table '%s'" tableName))
                            (let [dt (getCurrDateTime)]
                              ;; (println "Table specs are" tableSpecs)
                              ;; (println " TEST apply: " (apply str tableName tableSpecs))
                            ;; (flush)
                              (apply sql/create-table tableName tableSpecs)
                              (writeActionEntry "--general--" dt (format "Created table '%s'" tableName)))
                            false)))
            ]
           ;    (println (format "\nChecking whether scheme '%s' is initialized." (:db_scheme dbs)))
           (if (and (checkTable (getActionLogDb)
                                [:id    (str (:autokey dbs) (:primary dbs))]
                                [:datetime  (:datetime dbs)]
                                [:track      (:text dbs)]
                                [:d_user  (:text dbs)]
                                [:action (:text dbs)])
                    (checkTable  (getErrorLogDb)
                                 [:id    (str (:autokey dbs) (:primary dbs))]
                                 [:datetime  (:datetime dbs)]
                                 [:d_user  (:text dbs)]
                                 [:command  (:text dbs)]
                                 [:track  (:text dbs)]
                                 [:error (:text dbs)])
                    (checkTable (getTrackInfoDb)
                                [:track_id    (:autokey dbs)]
                                [:file_location (:text dbs)]
                                [:track_name  (str (:text dbs) (:primary dbs))])
                    (checkTable (getCommitDb)
                                [:id    (str (:autokey dbs) (:primary dbs))]
                                [:track_id  (:keytype dbs)]
                                [:datetime  (:datetime dbs)]
                                [:contents  (:longtext dbs)])
                    (checkTable (getPatchDb)
                                [:id    (str (:autokey dbs) (:primary dbs))]
                                [:track_id  (:keytype dbs)]
                                [:datetime  (:datetime dbs)]
                                [:path  (:longtext dbs)]
                                [:action  (:text dbs)]
                                [:patchkey  (:text dbs)]
                                [:value  (:longtext dbs)]) )
             true
             false))))
  
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;;
;;  Functions to write data to the databases
;;

(defn db_createTrack
  "add an information record for this track, and returns the full trackInfo record for this track (used to generate other tables)."
  [trackName fileLocation]
  (let [lpf "(db_createTrack): "
        trackInfoDb (getTrackInfoDb)
        getTrackRec (format "SELECT * FROM %s WHERE %s = '%s'"
                            trackInfoDb "track_name" trackName)]
    ;; create a track-info record
    (sql/with-query-results recs [getTrackRec]
                            (if (>  (count recs) 0)
                              (do
                                (addMessage trackName "Track already exists in database.")
                                nil)  ;; return nil (signaling no track generated
                              (do
                                (sql/insert-records
                                  trackInfoDb
                                  {:file_location fileLocation   :track_name trackName})
                                (sql/with-query-results recs [getTrackRec]
                                                        (first recs)))))))



  

(defn db_writeCommit
  "Add a full-copy of the track to 'trackId' with 'jsonContents'
  and date/time from dt (does not add patches and 'jsonContents' should be a json-string, not a hash-map object)."
  [trackId jsonContents dt]
  {:pre [(isJson? jsonContents)
	 (not= (type trackId) java.lang.String)]}
  (let [lpf "(db_writeCommit): "]
    (debug lpf "in addTrackCopy jsonContents of type: " (type jsonContents)
           "\n\tAdding a version with contents: " jsonContents)
    (sql/insert-records (getCommitDb)
                        {:track_id trackId
                         :datetime dt
                         :contents jsonContents})))


(defn db_writePatches
  "Add the 'patches' of the track to 'trackname' with date/time from 'dt'."
  [trackId patches dt]
  {:pre [(not= (type trackId) java.lang.String)]}
    (doseq [p patches]
      ;; Possibly sql/insert-values with a vector containing all patches is faster
      (let [rec (patchToDbRec trackId p dt)]
	(sql/insert-records (getPatchDb) rec))))
 

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  Functions to retrieve (get) data from the databases
;;

(defn- getAllItems [table]
  (let [query (format "SELECT * FROM %s" table)]
    (sql/with-query-results res  [query]
      (doall res))))

(defn db_getAllTracks []
  (getAllItems (getTrackInfoDb)))

(defn db_getAllActions []
  (getAllItems (getActionLogDb)))

(defn db_getAllErrors []
  (getAllItems (getErrorLogDb)))



(defn db_getTrackInfo
  " get the path in the file-system that corresponds to trackName (should be only one track)"
  [trackName]
  (let [lpf "(db_getTrackInfo): "
        query (format "SELECT * FROM %s WHERE %s = '%s'"
		      (getTrackInfoDb) "track_name" trackName)]
    (debug lpf " getTracks: generated query " query)
      (sql/with-query-results res  [query]
	(debug lpf "results of query:\n\t" res)
	(if (not= (count res) 1)
	  (addMessage trackName "ERROR: There are %s versions of this track (action cancelled)."
		      (count res))
	  (first res)))))



(defn db_getCommit
  "Get the data of the commit of at 'depth' steps from the last commit from the database. The function returns exactly one record (not a sequence)."
  [trackId depth]
  {:pre [(not= (type trackId) java.lang.String) trackId]}
  (let [lpf "(db_getCommit): "
        trackDb (getCommitDb)
        query (format "SELECT id FROM %s WHERE track_id = %s ORDER BY id DESC LIMIT 1 OFFSET %s;" trackDb trackId depth)]
    (debug lpf "getCommit query: " query)
    (sql/with-query-results selectId
                            [query]
                            ;; and extract the corresponding version
                            (when-let [id (:id (first selectId))]
                              (sql/with-query-results res
	  [(format "SELECT * FROM %s WHERE id=%s" trackDb id)]
	  (assert (= 1 (count res)))
	  (first res))))))



(defn db_getPatches
  "Retrieve the patches since 'depth' commits before the current commit. The returned list will be in the db-format. To get patches in the original format use 'retrievePatches'."
  [trackId dt]
  {:pre [(not= (type trackId) java.lang.String)]}
    (let [lpf "(db_getPatches): "
          query (format "SELECT * FROM %s WHERE datetime >= '%s' ORDER BY datetime ASC;" (getPatchDb) dt)]
      (debug lpf "\nThe retrieve-patch-query is: " query)
      (sql/with-query-results res  [query]
	;; TO DO: loop to materialize result set (why not doall??)
	;; (loop [ret []
	;;        res res]
	;;   (if (seq res)
	;;     (recur (conj ret (first res)) (rest res))
	;;     ret))
	;;
	;; map database patches to Patch records
	(debug lpf "query returns " (count res) " values:"
        (with-out-str (doall (map println res))))
	(let [ret (doall (map  dbRecToPatch res))]
	  (debug lpf "The result after dbRecToPatch is:"
          (with-out-str (doall (map println ret))))
	  ret))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;;  Functions to drop data from the databases
;;

(defn db_dropLastCommit
  "Drop the last commit from the database. Will remove the patches that belong to this commit too. The files in the file-system will not be touched."
  [commitId dt]
  (let [res (sql/do-commands 
	     (format "DELETE FROM %s WHERE id = %s;" (getCommitDb) commitId)
	     (format "DELETE FROM %s WHERE datetime >= '%s';" (getPatchDb) dt))
	droppedNumPatches (second res)]
    droppedNumPatches))

(defn db_dropTrackInfo
  "Drop the track-info record for track_id = 'trackId'. Assume that all commits have been dropped already. No checks performed!"
  [trackId]
  (sql/do-commands
   (format "DELETE FROM %s WHERE track_id = %s;" (getTrackInfoDb) trackId)))

(defn db_initDatabase [cfg]
  (let [lpf "(db_initDatabase): "]
    (if cfg
      (do
        ;; adjust the global db-record
        (generateDb (:db cfg))
        (let [{:keys [dbName dbType]} cfg]
          (setDbsRecord dbName dbType))
        (debug lpf "configured database based on config-file db=" (with-out-str (pprint db))))
      (do
        (warn lpf "Install in-memory temporary database for demonstation."
               "WARNING:  All actions will be disposed at shutdown.")
        (installInMemoryDb)))
    
      (trace lpf "Database set as: \n\t" db
             "\n\tDatabase parameters set as: " (with-out-str (pprint dbs)))))

(defn db_closeDatabase []
  (let [lpf "(closeDatabase): "]
    (debug lpf "No shutdown actions for database needed.")))


(defn installDatabaseAsPS []
  (ps/rebindPersistentStore
    db_initDatabase     
    db_call-with-connection
    db_writeErrorEntry db_writeActionEntry
    db_createTrack db_writeCommit db_writePatches
    db_getAllTracks db_getAllActions db_getAllErrors
    db_getTrackInfo db_getCommit db_getPatches
    db_dropLastCommit db_dropTrackInfo
    db_closeDatabase))






(defn doSql [& cmds]
  (sql/with-connection db (apply sql/do-commands cmds)))

(defn showSql [& cmds]
  (letfn [(doShow [cmd]
		   (println "running command: " cmd)
		   (sql/with-query-results res
		     [(str cmd)]
		     (doseq [rec res]
		       (println rec))))
	   ]
  (sql/with-connection db (doseq [cmd cmds] (doShow cmd)))))

(defmacro doComm [cmd]
  `(sql/with-connection db ~cmd)) 
