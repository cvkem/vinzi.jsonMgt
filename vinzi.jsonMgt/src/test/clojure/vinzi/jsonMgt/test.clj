(ns vinzi.jsonMgt.test
  (:use [vinzi.json jsonDiff [jsonZip :only [jsonZipper]]])
  (:use [vinzi.jsonMgt globals core persistentstore commandline])
  (:require [vinzi.jsonMgt [database :as dbps]])
  (:import [vinzi.json.jsonDiff Patch]
	   [java.sql SQLException]
	   [java.io File])
  (:use [clojure test pprint])
;;  (:use [alex-and-georges.debug-repl :as dr])
  (:require  [clojure.data
	      [json :as json]]
	     [clojure.java
	      [io :as io :only [writer]]
	      [jdbc :as sql]])
  )

(def stack-trace-depth nil)

 (def testDb
   {:classname "org.postgresql.Driver"
    :subprotocol "postgresql"
    :subname "//localhost:5432/test"   ;; database name = test
    :user  "tos"
    :password "tos-user" })

;(def testDb
;  {:classname "com.mysql.jdbc.Driver"
;   :subprotocol "mysql"
;   :subname "//localhost:3306/test"   ;; database name = test
;   :user  "tos"
;   :password "tos-user" })

(use-fixtures :each (fn [f] (println "\n\nInit EACH fixture") (f) (println "Clean-up EACH fixture")))

(use-fixtures :once (fn [f]
		      (println "Init ONCE fixture")
;;		      (readCdfdeMgtConfig)
		      (f) (println "Clean-up ONCE fixture")))


;; test-date (will we used to generate test-files)
(def orgData {:a 1
	       :b "test-string"})

(def modData  {:a 1
		 :cc [1 2 3]
		 :b "test-string"})


(def patchMod [
	      (Patch. ["/"] actInsert :cc [1 2 3] )])


(def target1 orgData)
(def target2 {:m 208
	      :a 1
	      :b "test-string"
	      :cc {:a 1 :b 2}})



;; location of testfiles
(def orgFile "test-data/orgFile.json")
(def target1File "test-data/target1.json")
(def target2File "test-data/target2.json")

(defn fixCreateTestData []
  (with-open [o2 (io/writer orgFile)
	      t1 (io/writer target1File)
	      t2 (io/writer target2File)]
    (spit o2 (json/json-str orgData))
    (spit t1 (json/json-str target1))
    (spit t2 (json/json-str target2))
    ))

(defn replaceOrgFile
  "replace file with modified version."
  []
  (with-open [o2 (io/writer orgFile)]
    (spit o2 (json/json-str modData)))
  true)  ;; return true to include it in test

(defn dropTable [name]
      ;; delete the tables created
  (try
    (println "dropTable (test) " name) (flush)
    (sql/drop-table name)
    (catch Exception e)))  ;; transaction roll-back is java.lang.Exception


(defn dropTrack [filename]
;;  (dropTable  (getCommitDb filename))
;;  (dropTable (getPatchDb filename))
  (.delete (File. filename)))

(defn fixRemoveTestData []
  (let [trackDb (dbps/getTrackInfoDb)
	logDb   (dbps/getActionLogDb)
        errDb   (dbps/getErrorLogDb)]
    ;; delete the tables created
    (println "GOING TO: delete records within individual try-catch")
    (dropTable trackDb)
    (dropTable (dbps/getCommitDb))
    (dropTable (dbps/getPatchDb))
    (dropTable logDb)
    (dropTable errDb)
    ;; drop the tracks
    (dropTrack orgFile)
    (dropTrack target1File)
    (dropTrack target2File)
  (println "FINISHED CLEAN-UP OPERATIONS")
  (println "\n\n\n")))

(use-fixtures :once (fn [f]
                      (binding [*stack-trace-depth* stack-trace-depth]
                        (dbps/installDatabaseAsPS)
                        (dbps/switchDb testDb)
                        (dbps/switchDbsScheme "unittest")
                        ;; configure doc-root with nil argument will put doc-root at current directory
                        (configure-doc-root nil)
;;                        (dbps/setDocRootCurrentDir)
                        ;; proces this as tree seperate transactions (connections)
                        (sql/with-connection testDb
                                             (fixRemoveTestData)   ;; discard old test-data
                                             (fixCreateTestData)
                                             )
                        (sql/with-connection testDb
                                             (f))
                        (sql/with-connection testDb
                                             ;			(fixRemoveTestData) ;; allow inspection of data
                                             ))))

(defn testDbTableExists
  "test whether a table exists"
  [name]
  (try
    (sql/with-query-results _
      [(format "SELECT * FROM %s LIMIT 1" name)]
      true)
    (catch SQLException e
      (do
	;;	(println "mess: " (.getMessage e))
	false))))

(defn testTableSize
  "test whether a table has a certain size."
  [name oper size]
  (try
    (let [query (format "SELECT count(*) AS cnt FROM %s " name)]
      (sql/with-query-results res [query]
      (if (oper (:cnt (first res)) size)
	true
	false)))
    (catch SQLException e
      (do
	;;	(println "mess: " (.getMessage e))
	false))))


(defn testPatchList [trackName pl]
  )


(defn sameTail
  "returns true iff t is the tail of s"
  [s t]
  (= t (apply str (drop (- (count s) (count t)) s))))

(defmacro testProcess [& args]
  (let [command (apply format (map eval args))]
    (println "testProcess: " command "(will be executed later)" )
    `(is (processCommandStr ~command) ~command)))

(defmacro testNotProcess [& args]
  (let [command (apply format (map eval args))]
    (println "testProcess: " command "(will be executed later)" )
    `(is (not (processCommandStr ~command)) ~command)))

(deftest testDatabase   ;; testing the database-tool

  ;; starting without system-tables
  (is (not (testDbTableExists (dbps/getTrackInfoDb))) "initially track-info")
  (is (not (testDbTableExists (dbps/getActionLogDb))) "initially no action_log")

  ;; test how the track-name is extracted
  (is (=  (getTrackName "/home/cees/test")  "test"))
  (is (=  (getTrackName "/home/cees/test.json")  "test"))  ;; assuming .json as default-prefix
  (is (=  (getTrackName "/with_space/cees/test  ")  "test"))
  (is (=  (getTrackName "  /with_space/cees/test  ")  "test"))
  (is (=  (getTrackName "  /with_space/cees/TeSt  ")  "test"))
  (is (=  (getTrackName "  /with_space/cees/t e.st  ")  "t_e_st"))

  ;; ;; creation of track database for orgFile
  (testProcess "create %s" orgFile)
  (is (testDbTableExists (dbps/getTrackInfoDb)) "track_info created?")
  (is (testDbTableExists (dbps/getActionLogDb)) "action_log created?")
  ;; ;; in current setup these are standard databases (not track-specific 
  (is (testDbTableExists (dbps/getCommitDb)) "is trackDb generated")
  (is (testDbTableExists (dbps/getPatchDb)) "is patchDb generated")

  ;; ;; should fail (as orgFile exists)
  (testNotProcess "create %s" orgFile)

  (is (sameTail (getTrackFilePath (getTrackName "orgFile.json")) "/test-data/orgFile.json")
       "TrackFilePath should have the correct tail (head differs per system)")

   (is (testTableSize (dbps/getCommitDb) = 1) "Expected one record in track-db")
   (is (testTableSize (dbps/getPatchDb) = 0) "Expected zero records in patch-db")

  ;; ;; 
  ;; ;;; testing the committing
  ;; ;;  file is not changed yet
  ;; ;;
  (testNotProcess "commit %s" orgFile)
  (testNotProcess "commit %s" target1File)
  (is (testTableSize (dbps/getCommitDb) = 1) "Expected one record in track-db")
  (is (testTableSize (dbps/getPatchDb) = 0) "Expected zero records in patch-db")

  ;; ;; create a second track
  (testProcess "create %s " target1File)
  (is (testTableSize (dbps/getCommitDb) = 2) "Expected two record in commit-db")

  ;; ;; check on diff and dirty flags
  (println "VISUAL CHECK: diff should find no differences")
  (testProcess "diff %s " orgFile)
  (is (not (trackDirty? (getTrackName orgFile)))
        "orgFile should not be dirty (modified) yet")
   (testProcess "diff %s " (getTrackName orgFile))

  ;; ;; change the file
  (replaceOrgFile)
  (is (trackDirty? (getTrackName orgFile)) "Change orgFile not detected?")

  (println "VISUAL CHECK: one file dirty, the other unmodified.")
  (testProcess "dirty")
  (println "VISUAL CHECK: Now there should be one patch (add vector :cc)")
  (testProcess "diff %s " orgFile)

  ;; commit the changes
  (testProcess "commit %s" orgFile)
  (is (testTableSize (dbps/getCommitDb) = 3) "Expected three records in track-db (2 for orgFile)")
  (is (testTableSize (dbps/getPatchDb) = 1) "Expected one record in patch-db")
  (is (not (trackDirty? (getTrackName orgFile)))
      "changes are commited so file should be clean (not-dirty).")

  (testProcess "apply %s %s " orgFile target1File)

  ;; create a second track
  (testProcess "create %s " target2File)
  ;; should fail as an insert-patch can not do a replace
  (testNotProcess "apply %s %s " orgFile target2File)
  
  )
