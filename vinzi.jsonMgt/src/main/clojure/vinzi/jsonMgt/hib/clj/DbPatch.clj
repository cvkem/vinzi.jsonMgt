(ns vinzi.jsonMgt.hib.clj.DbPatch
    (:use vinzi.hib-connect.globals)
    (:import vinzi.jsonMgt.hib.DbPatch))

(defrecord DbPatch_clj [id track_id datetime path action patchkey value])

(defn create-DbPatch [track_id datetime path action patchkey value]
  ;; TO DO:  insert type-checking (java-objects are strictly typed)
  (DbPatch_clj. nil track_id datetime path action patchkey value))

(defn clone-DbPatch [cRec]
  ;; TO DO:  insert type-checking cRec should be same type/class as target
  (create-DbPatch (:track_id cRec) (:datetime cRec) (:path cRec) (:action cRec) (:patchkey cRec) (:value cRec)))

(defn DbPatch-to-java [cRec]
	(DbPatch. (:id cRec) (:track_id cRec) (:datetime cRec) (:path cRec) (:action cRec) (:patchkey cRec) (:value cRec) ))


(add-to-java (DbPatch_clj. 0 1 2 3 4 5 6) DbPatch-to-java)

(defn DbPatch-to-clj [jRec]
	(DbPatch_clj. (.getId jRec) (.getTrack_id jRec) (.getDatetime jRec) (.getPath jRec) (.getAction jRec) (.getPatchkey jRec) (.getValue jRec) ))


(add-to-clj (DbPatch.) DbPatch-to-clj)

