(ns vinzi.jsonMgt.hib.clj.errorEntry
    (:use vinzi.hib-connect.globals)
    (:import vinzi.jsonMgt.hib.errorEntry))

(defrecord errorEntry_clj [id datetime track command error user])

(defn create-errorEntry [datetime track command error user]
  ;; TO DO:  insert type-checking (java-objects are strictly typed)
  (errorEntry_clj. nil datetime track command error user))

(defn clone-errorEntry [cRec]
  ;; TO DO:  insert type-checking cRec should be same type/class as target
  (create-errorEntry (:datetime cRec) (:track cRec) (:command cRec) (:error cRec) (:user cRec)))

(defn errorEntry-to-java [cRec]
	(errorEntry. (:id cRec) (:datetime cRec) (:track cRec) (:command cRec) (:error cRec) (:user cRec) ))


(add-to-java (errorEntry_clj. 0 1 2 3 4 5) errorEntry-to-java)

(defn errorEntry-to-clj [jRec]
	(errorEntry_clj. (.getId jRec) (.getDatetime jRec) (.getTrack jRec) (.getCommand jRec) (.getError jRec) (.getUser jRec) ))


(add-to-clj (errorEntry.) errorEntry-to-clj)

