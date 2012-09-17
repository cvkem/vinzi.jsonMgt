(ns vinzi.jsonMgt.hib.clj.TrackInfo
    (:use vinzi.hib-connect.globals)
    (:import vinzi.jsonMgt.hib.TrackInfo))

(defrecord TrackInfo_clj [id file_location track_name])

(defn create-TrackInfo [file_location track_name]
  ;; TO DO:  insert type-checking (java-objects are strictly typed)
  (TrackInfo_clj. nil file_location track_name))

(defn clone-TrackInfo [cRec]
  ;; TO DO:  insert type-checking cRec should be same type/class as target
  (create-TrackInfo (:file_location cRec) (:track_name cRec)))

(defn TrackInfo-to-java [cRec]
	(TrackInfo. (:id cRec) (:file_location cRec) (:track_name cRec) ))


(add-to-java (TrackInfo_clj. 0 1 2) TrackInfo-to-java)

(defn TrackInfo-to-clj [jRec]
	(TrackInfo_clj. (.getId jRec) (.getFile_location jRec) (.getTrack_name jRec) ))


(add-to-clj (TrackInfo.) TrackInfo-to-clj)

