(ns vinzi.jsonMgt.persistentstore)


(def psInitialized (atom false))

(defn ps-initialized? []
  @psInitialized)

;; The interface to the persistant storage
;;   - database OR
;;   - hibernate
;;
(def ps_initDatabase (fn [] true))
;; signature: []

(def ps_callWithConnection (fn [f & args] (println "wrapper not installed")))
;; signature: [f & args]

(def ps_writeErrorEntry)
;; signature: [errorEntry]
;; returns true on success
;; where errorEntry is {:datetime :user :command  :track  :error}

(def ps_writeActionEntry)
;; signature: [actionEntry]
;; returns true on success
;; where actionEntry is {:datetime :user :action}

;;
;;  Functions to write data to the databases (Create/add data)
;;


(def ps_createTrack)
;; signature [trackName fileLocation]
;; returns  {:track_id :track_name :file_location}

(def ps_writeCommit)
;; signature [trackId jsonContents dt]
;; returns
;; during a commit a new version is added to the tracks-table
;; (full copy of the json-file)

(def ps_writePatches)
;; signature:  [trackId patches dt]
;; During the commit a set of differences is written to the patches-table.

;;
;;  Functions to retrieve (get) data from the databases
;;


(def ps_getAllTracks)
;; signature: []

(def ps_getAllActions)
;; signature: []

(def ps_getAllErrors)
;; signature: []

(def ps_getTrackInfo)
;; signature:  [trackName]
;; return a trackInfo record  {:track_id :file_location :track_name}

(def ps_getCommit)
;; signature:   [trackId depth]
;; returns  {:id :trackId :dt}
;; Get the data of the commit of at 'depth' steps from the last commit from the database.
;; The function returns exactly one record (not a sequence).

(def ps_getPatches)
;; signature:  [trackId dt]
;; Retrieve the patches since datetime 'dt'.
;; returns a list of patches (or nil)


;;
;;  Functions to remove data from the databases
;;

(def ps_dropLastCommit)
;; signature:  [commitId dt]
;; returns  the number of dropped patches.


(def ps_dropTrackInfo)
;; signature:  [trackId]
;; returns:  nil on failure.
;; Drop the track-info record for track_id = 'trackId'.
;; Assume that all commits have been dropped already. No checks performed!

(def ps_closeDatabase)
;; signature:  []

(defn rebindPersistentStore
  "Rebind all functions related to the persistent store functionality to the desired implementation."
  [initDatabase callWithConnection
   writeErrorEntry writeActionEntry
   createTrack writeCommit writePatches
   getAllTracks getAllActions getAllErrors
   getTrackInfo getCommit getPatches
   dropLastCommit dropTrackInfo
   closeDatabase]
  
  (def ps_initDatabase initDatabase)
  (def ps_callWithConnection callWithConnection)
  (def ps_writeErrorEntry writeErrorEntry)
  (def ps_writeActionEntry writeActionEntry)
  (def ps_createTrack createTrack)
  (def ps_writeCommit writeCommit)
  (def ps_writePatches writePatches)
  (def ps_getAllTracks getAllTracks)
  (def ps_getAllActions getAllActions)
  (def ps_getAllErrors getAllErrors)
  (def ps_getTrackInfo getTrackInfo)
  (def ps_getCommit getCommit)
  (def ps_getPatches getPatches)
  (def ps_dropLastCommit dropLastCommit)
  (def ps_dropTrackInfo dropTrackInfo)
  (def ps_closeDatabase closeDatabase)

  (swap! psInitialized (fn [_] true))
  )



(defmacro with-persistent-store
  [target-ns prefix body]
  {:pre [(= (type target-ns) java.lang.String)
	 (= (type prefix) java.lang.String)]}
  (let [postfix (list "initScheme"
                      "writeErrorEntry" "writeActionEntry"
                      "createCommit" "writeCommit" "writePatches"
                      "getAllTracks" "getTrackInfo" "getCommit" "getPatches"
                      "dropLastCommit" "dropTrackInfo")
        funcs  (map #(symbol target-ns %)
                    (map #(str prefix %)  postfix))
        vars   (map #(symbol "vinzi.jsonMgt.globals" %)
                    (map #(str "ps_" %) postfix))
        bindings (interleave vars funcs)
        ]
    `(binding [~@bindings] ~body)))
