(ns vinzi.jsonMgt.hib.clj.actionEntry
    (:use vinzi.hib-connect.globals)
    (:import vinzi.jsonMgt.hib.actionEntry))

(defrecord actionEntry_clj [id datetime track action d_user])

(defn create-actionEntry [datetime track action d_user]
  ;; TO DO:  insert type-checking (java-objects are strictly typed)
  (actionEntry_clj. nil datetime track action d_user))

(defn clone-actionEntry [cRec]
  ;; TO DO:  insert type-checking cRec should be same type/class as target
  (create-actionEntry (:datetime cRec) (:track cRec) (:action cRec) (:d_user cRec)))

(defn actionEntry-to-java [cRec]
	(actionEntry. (:id cRec) (:datetime cRec) (:track cRec) (:action cRec) (:d_user cRec) ))


(add-to-java (actionEntry_clj. 0 1 2 3 4) actionEntry-to-java)

(defn actionEntry-to-clj [jRec]
	(actionEntry_clj. (.getId jRec) (.getDatetime jRec) (.getTrack jRec) (.getAction jRec) (.getD_user jRec) ))


(add-to-clj (actionEntry.) actionEntry-to-clj)

