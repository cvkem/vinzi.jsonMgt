(ns vinzi.jsonMgt.hib.clj.Commit
    (:use vinzi.hib-connect.globals)
    (:import vinzi.jsonMgt.hib.Commit))

(defrecord Commit_clj [id track_id datetime contents])

(defn create-Commit [track_id datetime contents]
  ;; TO DO:  insert type-checking (java-objects are strictly typed)
  (Commit_clj. nil track_id datetime contents))

(defn clone-Commit [cRec]
  ;; TO DO:  insert type-checking cRec should be same type/class as target
  (create-Commit (:track_id cRec) (:datetime cRec) (:contents cRec)))

(defn Commit-to-java [cRec]
	(Commit. (:id cRec) (:track_id cRec) (:datetime cRec) (:contents cRec) ))


(add-to-java (Commit_clj. 0 1 2 3) Commit-to-java)

(defn Commit-to-clj [jRec]
	(Commit_clj. (.getId jRec) (.getTrack_id jRec) (.getDatetime jRec) (.getContents jRec) ))


(add-to-clj (Commit.) Commit-to-clj)

