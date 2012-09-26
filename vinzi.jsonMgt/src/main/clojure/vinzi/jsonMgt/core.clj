(ns vinzi.jsonMgt.core
  (:use	   [clojure pprint]
           [clojure.tools logging])
  (:use [vinzi.jsonMgt globals persistentstore init-persistentstore]
        [vinzi.json jsonZip jsonDiff jsonEdit]
	 [clojure.pprint])
  (:require  [clojure
	      ;; zip only for debugging
	      [zip :as zip]
	      ;;
	      [string :as str :only [lowercase replace replace-re trim]]]
	     ;;[clojure.java.io :as jio :only [resource]]
      [clojure.data.json :as json]
	     [clojure.java.io :as io]
      [vinzi.tools.vExcept :as vExcept]
      [vinzi.pentaho.genCda :as cda])
  (:import [java.sql SQLException]
	   [vinzi.json.jsonDiff Patch]
	   [java.io File BufferedReader]))

;; usage of the next few lines should be replaced by vFile.
(def regExpSepator  (if (= File/separator "\\")
			#"\\" #"/"))
(def searchSep (if (= File/separator "\\")
			"/" "\\"))
(def theSep (if (= File/separator "\\")
		  "\\" "/"))

(def regExpIsFilename  (if (= File/separator "\\")
			 #"[.\\]" #"[./]"))
(defn isFilename?
  "Assumes name is a filename if it contains '.' or a file-separator character."
  [name]
  (re-find regExpIsFilename  name))  


(defn set-doc-root [dr]
  (if (seq dr)
    (let [dr (if (= (last dr) (first theSep))
               dr
               (str dr theSep))]
      (def doc_root dr))
    (let [dir (File. ".")
          currDir  (str (.getCanonicalPath dir) theSep)]
      (def doc_root currDir))))

;;; NOTE:  the configuration stuff below is not used anymore.
;;;   configurations runs via .cdp.xml
;;; However, this code is required by the standalone versions mainClHib and mainClDb
;;;   TODO: clean up code

;;(comment ;; unused code for web-interface, but used by mainHibCl  


;;
;;  filename of configfile
;;  will be used in readJsonMgtConfig
;;
(def ^:dynamic configFileName ".jsonMgt.conf")
(defn setConfigFileName "Override the default filename"
  [fileName]
  (def configFileName fileName))


;; get configuration from the user home directory
(def homeConfig :home)
;; get the configuration from the java-classpath (resource)
(def resourceConfig :resource)
;; default is the resourceConfig
(def ^:dynamic configLoc resourceConfig)
(defn setConfigLoc "set location to retrieve config (either 'homeConfig' or 'resouceConfig'."
  [loc]
  (assert (or (= loc homeConfig) (= loc resourceConfig)))
  (def configLoc loc))



;;(defn setDocRootCurrentDir []
;;  (def dbs (assoc dbs :doc_root (str (.getCanonicalPath (File. ".")) "/"))))


(defn configure-doc-root [cfg]
   (let [lpf "(configure-doc-root): "]
     (if cfg
       (let [{:keys [fileSystem]} cfg
             ;; ensure doc_root is /-terminated OR "")
             docRoot (str/trim (:doc_root fileSystem))
             docRoot (if (or (= (count docRoot) 0)
                             (= (last docRoot) theSep))
                    docRoot
                    (str docRoot theSep))]
         (def doc_root docRoot))
       ;; else config-file does not exist. Use current directory
       (let [dir (File. ".")
             docRoot  (str (.getCanonicalPath dir) theSep)]
      (info lpf "  ... and setting the doc_root to current folder: " docRoot)
      (def doc_root docRoot)))))
   
(defn- getConfigFileURL [configLoc]
  (let [lpf "(getConfigFileURL): "]
    (if (= configLoc homeConfig)
      (let [fname (str (getUserHome) theSep configFileName)]
        (if (.exists (File. fname))
          fname
          (warn lpf "The configuration file " fname " does not exist")))
      (if-let [resUrl  (io/resource configFileName)]
        resUrl
      (do
        (info lpf "Could not locate the resource " configFileName
                 " on the classpath!!\n"
                 " Trying to find config-file in your home-folder")
        (getConfigFileURL homeConfig))))))

(defn readJsonMgtConfig []
    (let [lpf "(readJsonMgtConfig): "
          msg (atom nil)
          addMsg (fn [m] (swap! msg #(cons m %)))
          configFile (getConfigFileURL configLoc)]
      (try
        (addMsg (str "Trying to open: " configFile))
        (with-open [file (io/reader configFile)]
          (addMsg "  ...File opened, going to read json")
          (let [cfg (json/read-json file)]
            (addMsg "  ... Checking debug-flag")
            (if (:debug cfg)
              (do
                (println "DEBUG debug is true in configuration")
                (setPrintPln true))
              (setPrintPln false))
            (addMsg "  ..  and configure the jsonMgt docRoot")
            (configure-doc-root cfg)
            (addMsg "  ... Now initialize the database-system")
            (init-persistentstore cfg)
            (debug lpf "Read configuration from file: " configFile)))
        (catch Exception e
          (error lpf (reverse @msg)
                 "\n\tCould find configuration at: " configFile
                 "\n\t  ... Using non-persistent in memory database")
          (ps_initDatabase nil)
          (configure-doc-root nil)))))

;;) ;; unused code


;;;;;;;;;;;;;;;;;;;
;;  Auxiliary (helper) functions
;;



(defn stripDefaultPostfix [name]
  (let [regExp (re-pattern (str (str/replace defPostfix "." "\\.") "$"))]
;;    (cstr/replace-re regExp "" name)))
    (str/replace name regExp "")))


(defn correctSeparator [name]
  (str/replace name searchSep theSep))

;; TODO: recognize windows absolute paths (start with c:\)
(defn extendFSPath
  "Extend the path in the file-system by prepending the doc_root. (only paths starting with '.' or '/' are assumed to be fully specified paths."
  [fileName]
  (let [lpf "(extendFSPath): "
        fileName  (correctSeparator fileName)
        fc (first fileName)
        path (if (or (= fc \/)
                     (= fc \.))
               ""
               doc_root)
        efn (str path fileName)]
    (debug lpf  " the current docRoot = " doc_root
           "\n\t the extended path = " efn)
    efn))

(defn simplifyFSPath
  "Shorten the path by extracting the doc_root."
  [fileName]
  (if (.startsWith fileName doc_root)
    (apply str (drop (count doc_root) fileName))
      fileName))


(defn deriveCdaFilename
  "derive a cda-filename from the .cdfde filename."
  [filename]
  (str (stripDefaultPostfix filename) cdaPostfix))


(defn deriveWcdfFilename
  "derive a cda-filename from the .cdfde filename."
  [filename]
  (str (stripDefaultPostfix filename) wcdfPostfix))

(defn getFileName
  "Get the filename by stripping of the path-part and trimming redundant spaces."
  [filename]
  (-> filename
      (correctSeparator)
      (str/split regExpSepator)
      (last)
      (str/trim)))

(defn replace-filename [filename newName]
  (let [fName (getFileName filename)
        fBase (apply str (take (- (count filename) (count fName)) filename))]
    (str fBase newName)))
        

(defn getDirectory
  "Get the directory of a 'filePath' "
  [filePath]
  (apply str (take (dec (- (count filePath) (count (getFileName filePath)))) filePath)))


(defn directoryExists
  "Gets the directory (the id just before the file-name and checks whether it exists. If directory is empty string (relative path it assumes true."
  [filePath]
  (let [lpf "(directoryExists): "
        dir (getDirectory filePath)]
    (if (zero? (count dir))
      true   ;; assume directory exists for file names without path.
      (let [f  (File. dir)]   ;; File. does not need to be closed!
        (trace lpf "extracted directory " dir)
        (let [res (and f (.exists f) (.isDirectory f))]
          (trace lpf "exists: " res)
          res)))))


(defn cleanStr
  "Cleanse string by replacing all characters that mysql dislikes in unquoted identifiers by an _ ."
  [s]
  (let [fx  (fn [c]
              (let [bc (int c)
                    underscore  \_ ]
                (if (or  (and (>= bc (int \a)) (<= bc (int \z)))
                         (and (>= bc (int \A)) (<= bc (int \Z)))
                         (and (>= bc (int \0)) (<= bc (int \9)))
                         (= c \$)
                         (= c \\)
                         (= c \:)
                         (= c \/))
                  c  underscore)))]	     
    (apply str (map fx s))))

(defn getTrackName
  "Extract the base-filename (without extension) and change it to a database-friendly format (no white-space, no '.' and completely lower-case."
    [filename]
  (-> filename
      (getFileName)
      (stripDefaultPostfix)
      (cleanStr)
      (str/lower-case)))




(defn wholeWordRegexp
  "Expand 'pattern'  to a pattern that matches whole words only."
  [pattern]
  (let [pattern (str/replace pattern "*" ".*")
	pattern (str/replace pattern "?" ".?")
	pattern (str "^" pattern "$")]
       (re-pattern pattern)))

(defn getExpandedTrackList
  ""
  [trackList]
  (let [lpf "(getExpandedTrackList): "
        allTracks (ps_getAllTracks)
        findMatches   (fn [tr column]
                        ;; create tuples with 'column' as first field and :track_name as second
                        (let [pat (wholeWordRegexp tr)
                              tuples (map (fn [x] [(column x) (:track_name x) ]) allTracks)
                              res (filter #(not (zero? (count (re-find pat (first %))))) tuples)]
                          (map second res)))
        expandTrackOrFile (fn [tr]
                            ;; TODO: recognize c:\... as a filename
                            (if (isFilename? tr) ;; it is a filename
                              (let [tr  (simplifyFSPath (extendFSPath tr))
                                    res (findMatches tr :file_location)]
                                (if (zero? (count res))
                                  (addMessage "--" "Could not find file(s) '%s'", tr)
                                  res))
                              (let [res (findMatches tr :track_name)]
                                (if (zero? (count res))
                                  (addMessage "--" "Could not find track(s) '%s'", tr)
                                  res))))
        removeDuplic (fn [cumm tracks]
                       (if (seq tracks)
                         (let [track (first tracks)
                               cumm (if (some #(= track %) cumm) cumm (conj cumm track))]
                           (recur cumm (rest tracks)))
                         cumm))
        ]
    (->> trackList
      (map expandTrackOrFile)
      (reduce removeDuplic []))))




(defn getTrackFilePath
  " Get the path in the file-system that corresponds to trackName (should be only one track)"
  [trackNI]
  (let [trackInfo  (if (map? trackNI) trackNI (ps_getTrackInfo trackNI))
        {filePath :file_location} trackInfo]
    (extendFSPath filePath)))



(defn applyPatchesCdfde
  "Apply a set of patches to an object and process the errors-messages that were generated."
  [jsonObj patches trackName]
  (let [lpf "(applyPatchesCdfde): "]
    (setPrintZipErrorsFalse)
    (clearZipErrors)
    (debug lpf  " (type jsonObj) = " (type jsonObj)
           "\n\t patches = " patches)
    (let [zip  (jsonZipper jsonObj)
          _   (debug lpf  " zip = " zip)
          res  (applyPatchesZipper zip patches)]
      ;; show errors
      (when-let [errors (getZipErrors)]
        (let [dt (getCurrDateTime)]
          (doseq [err errors]
            (addErrEntry err dt trackName))))
      ;; show warning (no changes)
      (when (identical? zip res)
        (addMessage trackName "No patches applied."))
      { :obj (jsonRoot res)
       :zip res})))

(defn getJsonRepr
  "Function to translate an object to the (desired) json-representations"
  [jsonObj]
  (json/json-str jsonObj))


(defn getCommit
  "Get the committed full-version of 'trackName' as object and as jsonStr and the commit-specs. 
   TrackName should be a string OR a map representing a trackInfo record with the commit-information 
   included (datetime, json and obj)."
  ([trackNI] (getCommit trackNI 0))   ;; default is to get last commit
  ([trackNI depth]
     {:pre [trackNI (or (= (type trackNI) java.lang.String) (map? trackNI))]}
     ;; look up the id of the latest version
     (let [lpf  "(getCommit): "]
       (if-let [trackInfo  (if (map? trackNI) trackNI (ps_getTrackInfo trackNI))]
         (let [trackName (:track_name trackInfo)]
           (if-let [res (ps_getCommit (:track_id trackInfo) depth)]
             (let [json  (:contents res)
                   obj   (json/read-json json)]
               (assoc trackInfo :obj obj  :json json  :datetime (:datetime res)))
             (addMessage trackName "ERROR: Could not retrieve track at depth %s." depth)))
         (addMessage (str trackNI) "ERROR: could not retrieve track-info.")))))
  



;;;;;;;;;
;;  Read/write the FS-version (version on the File-system)
;;

(defn checkJsonFSValid
  "Check whether the reader corresponds to a file that can be read by as json.
  Return true on success, false otherwise."
  [reader]
  (let [lpf "(checkJsonFSValid): "]
    (try
      (json/read-json reader)
      (catch Exception e
;;        true
;;        (do
          (error lpf "message: " (.getMessage e))
          nil))))


(defn readJsonFS
  "Read an json-object from 'f'. Return a map containing the normalized json :normJson and the unpacked object :obj on succes. 'f' should be a java.io.BufferedReader or a string that represents the file."
  [f]
  (let [lpf "(readJsonFS): "
        nJson (fn [reader]
                (let [obj        (json/read-json reader)
                      normJson   (getJsonRepr obj)]
                  {:normJson  normJson
                   :obj       obj}))]
    (try
      (if (= (str (type f)) "class java.lang.String")
        (let [fileName (extendFSPath f)]
          (with-open [rdr (io/reader fileName)]
            (nJson rdr)))
        (nJson f))
      (catch Exception e
        ;; can drown exception, as it is caught by the caller (based on the nil-value)
        (error lpf "exception: " e
               "\n\tCAUGHT EXCEPTION with message: " (.getMessage e))
        nil))))


;; (defn getNormalizedJsonFile
;;   "Get a normalized version of the json-file from the file-system."
;;   [file]
;;   (let [fileVersion  (with-open [r (io/reader file)]
;; 		       (json/read-json r))
;; 	normJson  (json/json-str fileVersion)]
;;     normJson))

(defn generate-wcdf [fileName]
  (let [fName (getFileName fileName)
        fName (apply str (take (- (count fName) (count cdfdePostfix)) fName)) 
        contents (str "<cdf><title>" fName "</title>"
                      "<author/>"
                      "<description></description>"
                      "<icon/>"
                      "<style>Clean</style>"
                      "</cdf>")]
    contents))

(defn- writeVersionFS
  "Write a version of the cdfde-file, the wcdf-file and the cda-file to the file-system. The cda-file is derived from the cdfde-file."
  ([trackInfo contents obj]
    (writeVersionFS trackInfo contents obj nil))
  ([trackInfo contents obj newName]
  {:pre [(map? trackInfo) (isJson? contents)]}
  (let [{filename :file_location} trackInfo
        filename (if (string? newName)
                   (replace-filename filename newName)
                   filename)
        filename (extendFSPath filename)
        cda      (cda/generateCda (jsonZipper obj))
        cdaFile  (deriveCdaFilename filename)
        wcdf      (deriveCdaFilename filename)
        wcdfFile (deriveCdaFilename filename)]
    
    ;	(str (apply str (take (- (count filename) 5) filename)) "cda")]
    (spit filename contents)
    (spit cdaFile  cda)
    (spit wcdfFile wcdf)
    ;;    (println "TODO: Add exception handler to catch errors??")
    true)))


(defn getPatchesFS
  "Get all patches for a track by comparing the version in the file-system to the latest commit."
  [trackInfo]
  (let [lpf "(getPatchesFS): "
        track (:track_name trackInfo)]
    (if-let [file (extendFSPath (:file_location trackInfo))]
      (if-let [dbObj   (:obj (getCommit trackInfo))]
        (if-let [jfs    (readJsonFS file)]
          (let [patches (findPatchesJson dbObj (:obj jfs))]
            {:file      file
             :dbObj     dbObj
             :fsJson    (:normJson jfs)
             :patches   patches   })
          (addMessage track "File '%s' could not be read." file))
        (addMessage track "No commit found for this track."))
      (addMessage track "No file-location for this track."))))



(defn trackDirty?
  "Check whether the track 'trackNI' is dirty. 'trackNI' is a trackInfo record or a track_name."
  [trackNI]
  (let [lpf "(trackDirty?): "]
     (if-let [trackInfo  (if (map? trackNI) trackNI (ps_getTrackInfo trackNI))]
       (let [org (:obj (getCommit trackInfo))
             mod (:obj (readJsonFS (:file_location trackInfo)))
             change (jsonChanged? org mod)]
         (first change))  ;; take first to map empty list to nil
       (addMessage trackNI "Could not locate track (information)"))))



;; (defn addTrackCopy
;;   "Add a full-copy of the track to 'trackName' with 'jsonContents'
;;   and date/time from dt (does not add patches and 'jsonContents' should be a json-string, not a hash-map object)."
;;   [trackName jsonContents dt]
;;   {:pre [(isJson? jsonContents)]}
;;   (pln "in addTrackCopy jsonContents of type: " (type jsonContents))
;;   (pln "Adding a version with contents: " jsonContents)
;;   (sql/insert-records
;;    (getTrackDb trackName)
;;    {:datetime dt
;;     :contents jsonContents}))


(defn registerTrackInfo
  "add an information record for this track, and return the track_info (used to generate other tables)."
  [file]
  ;; TODO: ?? get rid of absolute path (when using pentaho-solution folder)
  (let [fileLoc   (simplifyFSPath (.getAbsolutePath file))
	trackName (getTrackName fileLoc)
	;; create a track-info record
	trackInfo  (ps_createTrack trackName fileLoc)]
    trackInfo))

;;
;;   This routine create a track-info record and stores an initial commit in the commit-database.
;;
(defn createTracks
  "Create a new track for a json-file."
  [args]
  (doseq [filename args]
    (let [lpf "(createTracks): "
          filename (extendFSPath filename)  ;; prepend document-root
          f  (File. filename)]   ;; File. does not need to be closed!
      (if (and f (.exists f) (.isFile f))
        (with-open [check (io/reader f)
                    jRead (io/reader f)]
          (if (checkJsonFSValid check)
            ;; create a tables for this track
            (if-let [trackInfo  (registerTrackInfo f)]
              (let [{:keys [track_id track_name]} trackInfo
                    contents   (:normJson (readJsonFS jRead))
                    dt         (getCurrDateTime)]
                (debug lpf  "filename " filename " result in track " track_name) (flush)
                (when track_name
                  (ps_writeCommit track_id contents dt)
                  (writeActionEntry track_name dt
                                    (format "Created new Track for: %s" track_name))))
              nil)  ;; no trackInfo returned (no track created)
            (addMessage (getTrackName filename) "File with name %s is not a valid JSON-file" filename)))
        (addMessage (getTrackName filename) "File with name %s could not be located" filename)))))



(defn cleanCopyTracks
  "Create a copy of the src-track at location 'dstPath'. This function create for the destination: 
       (a) a track in the database containing the last commit of srcTrack 
       (b) the .cdfde, .wcdf and .cda files at the given location."
  [[srcTrack & dstPaths]]
  (let [lpf "(cleanCopyTracks): "
        srcTrack (getTrackName srcTrack)
        srcInfo  (ps_getTrackInfo srcTrack)
        ;;srcTrack (:track_name srcInfo)
        ]
    (if-let [srcFile (getTrackFilePath srcInfo)]
      (if (not (trackDirty? srcInfo))
        (if-let [srcObj  (:obj (getCommit srcTrack))]
          ;; start inner loop (per dstPath)
          (doseq [dstTrack dstPaths]
            (let [ ;;  added getTrackName on enxt line (sept 2012)
                  filename (extendFSPath (getTrackName dstTrack))  ;; prepend document-root
                  base  (stripDefaultPostfix filename)
                  cdfdeName (str base cdfdePostfix)
                  cdfde  (File. cdfdeName)
                  cdaName (str base cdaPostfix)
                  cda  (File. cdaName)]   ;; File. does not need to be closed!
              (if (directoryExists base)
                (if (or (not cdfde) (not (.exists cdfde)))
                  (if (or (not cda) (not (.exists cda)))
                    (let [;; in cdfde-file paths start with a '/' (pentaho-solution-folder)
                          pentSolPath (if (= (first cdfdeName) \/) cdfdeName (str \/ cdfdeName)) 
                          patches [(Patch. ["/"] actChange :filename pentSolPath)] ]
                      (if-let [{dstObj :obj}
                               (applyPatchesCdfde srcObj patches srcTrack)]
                        ;; create a tables for this track
                        (let [trackInfo  (registerTrackInfo cdfde)
                              trackId    (:track_id trackInfo)
                              contents   (getJsonRepr dstObj)
                              dt         (getCurrDateTime)
                              newTrack (:track_name trackInfo)]
                          (when trackInfo
                            ;;			  (createTrackTables trackName)
                            (ps_writeCommit trackId contents dt)
                            (writeVersionFS trackInfo contents dstObj)
                            (writeActionEntry newTrack dt
                                              (format (str "Derived clean copy of track '%s'"
                                                           "with new name '%s' and path '%s'.")
                                                      srcTrack newTrack cdfdeName))))
                        (addMessage dstTrack "Changing filename to  '%s' in a copy of the current source commit failed."  cdfdeName)))
                    (addMessage dstTrack "Destination-file '%s' exists already."  cdaName))
                  (addMessage dstTrack "Destination-file '%s' exists already."  cdfdeName))
                (addMessage dstTrack "Directory '%s' does not exist."  (getDirectory cdfdeName)))))
          ;; end inner loop
          (addMessage srcTrack "Failed to read last commit from the database."))
        (addMessage srcTrack "Can not create copies with non-committed changes on source."))
      (addMessage srcTrack "The file-path could not be found"))))



(defn checkout-copy-last
  "Checkout a copy of the dashboard with suffix _last. Does not create a new track. Use clean-copy if you want that."
  [srcTracks]
  (let [lpf "(checkout-copy-last): "]
    (if (= (count srcTracks) 1)
      (let [srcTrack (getTrackName (first srcTracks))
            srcInfo  (ps_getTrackInfo srcTrack)
            ;;srcTrack (:track_name srcInfo)
            ]
        (if-let [srcFile (getTrackFilePath srcInfo)]
          (if-let [srcObj  (:obj (getCommit srcTrack))]
            (let [newName    (str srcTrack "_last" cdfdePostfix)  
                  contents   (getJsonRepr srcObj)
                  dt         (getCurrDateTime)]
            (writeVersionFS srcInfo contents srcObj newName)
            (writeActionEntry srcTrack dt
                              (format (str "Checked out a copy of '%s'"
                                           "to new name '%s'.")
                                      srcTrack newName)))
            (addMessage srcTrack "Failed to read last commit from the database."))
          (addMessage srcTrack "The file-path could not be found")))
      (addMessage (first srcTracks) (str "In (checkout-copy-last) Expected exactly one srcTrack, "
                                         "received " (count srcTracks) " tracks")))))
  

(defn- commitPatchSet
  "Store 'currJson' and the 'patches' to the database and return the timestamp (on success)." 
  [trackName trackId jsonStr patches]
  {:pre [trackId]}
  (let [lpf "(commitPatchSet): "]
    (if (not (seq patches))
      (addMessage trackName "Warning: There are no changes for this commit (commit-patches cancelled).")
      (let [lpf "(commitPatchSet): "
            dt   (getCurrDateTime)]
        (debug lpf  "obtained patches " patches)
        (ps_writeCommit trackId jsonStr dt)
        (ps_writePatches trackId patches dt)
        (debug lpf  "Added a new version for " trackName " at date-time: " dt)
        dt))))



(defn- commitTrackPatches
  "Commit a single track by detecting the patches in the file-system version and storing the full version and the patches."
  [trackInfo]
  (let [lpf "(commitTrackPatches): "
        {:keys   [patches fsJson]} (getPatchesFS trackInfo)
        trackId  (:track_id trackInfo)]
    ;; for debugging
    (let [patchStr (str "commitVersionTrack: patches are: " 
                        (with-out-str (doall (map println patches))))]
      (if (enabled? :debug)
        (debug lpf patchStr)
        (println  patchStr)))
    (commitPatchSet (:track_name trackInfo) trackId fsJson patches)))


(defn commitVersion
  "For all 'args' commit a new version of the json (dashboard) to one of the existing tracks."
  [args]
  (doseq [trackName args]
    (let [lpf "(commitVersion): "
          trackInfo (ps_getTrackInfo (getTrackName trackName))
          nme (:track_name trackInfo)]
      (if-let [dt (commitTrackPatches trackInfo)]
        (writeActionEntry nme dt
                          (format "Committed new version for %s" nme))
   	    (addMessage trackName "Commit failed")))))

 (defn- diffViewer
   "Open a viewer for this track. The viewer uses color-code to highlight differences."
   [track]
   (let [lpf "(diffViewer): "
         trackInfo  (ps_getTrackInfo track)
         {:keys [patches dbObj]} (getPatchesFS trackInfo)]
     (if dbObj
       ;;TODO check if track is the right argument!!
       (if-let [{zip :zip} (applyPatchesCdfde dbObj patches track)]
         (do
           (info lpf (format "Opening a viewer for track '%s'" track))
           ;; open the zip-viewer (intermediate step via json kills meta-data)
           (jsonZipViewer zip))
         (addMessage track "detecting and applying/marking patches failed"))
       (addMessage track "Could not retrieve file determine patches for the filesystem version" )))) 

(defn diffViewers
  "Open a graphical viewer for each of the tracks."
  [args]
  (doseq [trackName args]
    (let [trackName (getTrackName trackName)]
	  (diffViewer trackName))))


(defn showPatches
  "Show the differences for one or more tracks"
  [args]
  (doseq [trackName args]
    (let [trackInfo         (ps_getTrackInfo (getTrackName trackName))
	  {:keys [patches]} (getPatchesFS trackInfo)]
      (println (format "The difference from the last commit on track '%s' are:" trackName))
      ;; TODO:  replace by a doall
      (if (seq patches)
	(doseq [patch patches]
	  (print "  ")
	  (pprint patch))
	(println "   --- NONE ---")))))



(defn showDirty
  "show which dashboards have been changed since their last commit."
  [args]
  (let [lpf "(showDirty): "
        checkTrack (fn [trackInfo]
                     (debug lpf  "showDirty (process track): " trackInfo)
                     (print (format "  Check track '%s':  " (:track_name trackInfo))) (flush)
                     (let [dirty? (if (trackDirty? trackInfo) "CHANGED" "match")]
                       (println dirty?)
                       (info lpf "Check trace '" (:track_name trackInfo) "' returned:" dirty?)
                       ))]
    (if (seq args)
      (doseq [trackNI args]
        (let [trackInfo  (if (map? trackNI) trackNI (ps_getTrackInfo trackNI))]
          (checkTrack trackInfo)))
      ;; if no arguments passed than process status of all tracks.
      (let [allTracks (ps_getAllTracks)]
        (if (seq allTracks)
          (doseq [trackInfo allTracks]
            (checkTrack trackInfo))
          (do
            (println "No tracks found!")
            (info lpf "No tracks found!")))))))





(defn applyPatchesSrcDst
  "Apply the patches from 'depth' commits back in time until current commit of 'srcTrack' to 'dstTrack'." 
  [srcTrack dstTrack depth]
  (let [lpf "(applyPatchesSrcDst): "
        srcTrack (getTrackName srcTrack)
        dstTrack (getTrackName dstTrack)
        srcInfo  (ps_getTrackInfo srcTrack)
        dstInfo  (ps_getTrackInfo dstTrack)
        srcId    (:track_id srcInfo)
        dstId    (:track_id dstInfo)]
    (if (not (trackDirty? srcInfo))
      (if-let [dstFile (extendFSPath (:file_location dstInfo))]
        (if (not (trackDirty? dstInfo))
          (if-let [dt (:datetime (getCommit srcInfo depth))]
            (if-let [patches (ps_getPatches srcId dt)]
              (if-let [dst (:obj (getCommit dstInfo))]
                (if-let [{modDst :obj} (applyPatchesCdfde dst patches dstTrack)]
                  (let [jsonDst  (getJsonRepr modDst)]
                    (if (writeVersionFS dstInfo jsonDst modDst)
                      (if-let [dt (commitTrackPatches dstInfo)]
                        (writeActionEntry dstTrack dt
                                          (format "Committed updated version of '%s'." dstTrack))
                        (addMessage  dstTrack "Commit of updated version failed"))
                      (addMessage dstTrack "Failed to check-out the new version to the file-system")))
                  (addMessage dstTrack "Failed to apply the patches to last commit."))
                (addMessage dstTrack"Failed to read last commit from database."))
              (addMessage srcTrack "Failed to read the patches from database."))
            (addMessage srcTrack (str "date-time for the commit at depth " depth " not found.")))
          (addMessage dstTrack "The destination has non-committed changes"))
        (addMessage dstTrack "The file-path for destination track could not be found"))
      (addMessage srcTrack "The source has uncommited changes"))))

(defn revertToCommit
  "Revert to the last commit (database-version) and write this to the file-system."
  [args]
  (doseq [trackName args]
    (let [lpf "(revertToCommit): "
          track (getTrackName trackName)
          dt (getCurrDateTime)]
      (if (confirmReader (format "Overwite the files in the filesystem for track '%s'?" track))
        (if-let [{:keys [json obj]} (getCommit track)]
          (if (writeVersionFS track json obj)
            (writeActionEntry track dt
                              (format "Checked-out version of '%s' to file-system" track))
            (addMessage track "Failed to check-out the new version to the file-system"))
          (addMessage track "Failed to read last commit from database."))
        (addMessage track "Drop last commit aborted by user.")))))
      

(defn dropTrackInfo
  "Complete remove a track from the system. Only log-entries will be remaining."
  [trackInfo]
  (let [{:keys [track_id track_name]} trackInfo
        cdt  (getCurrDateTime)]
    (ps_dropTrackInfo track_id)
    (writeActionEntry track_name cdt
                      (format "The track '%s' has been fully removed." track_name))))


(defn dropLastCommit
  "Drop the last commit from the database. Will remove the patches that belong to this commit too. The files in the file-system will nog be touched."
  [args]
  (doseq [trackName args]
    (let [lpf "(dropLastCommit): "
          track (getTrackName trackName)
          cdt (getCurrDateTime)]
      (if (confirmReader (format "Drop last commit of '%s'?" track))
        (if-let [trackInfo (ps_getTrackInfo track)]
          (let [{:keys [track_id dt]} trackInfo
                droppedPatches (ps_dropLastCommit track_id dt)]
            (writeActionEntry track cdt
                              (format "Dropped commit of date '%s' from  '%s' from the database (consisting of %s patches)"  dt track droppedPatches))
            ;; if all commits are removed then the track will also be removed
	    (when (nil? (getCommit trackInfo))
	      (dropTrackInfo trackInfo)))
          (addMessage track "Failed to read last commit of track from database."))
        (addMessage track "Drop last commit aborted by user.")))))


(defn select-log-lines 
  "If first arg is '*' return all, otherwise select items matched by args" 
  [items args]
  (let [lpf "(select-log-lines): "]
    (debug lpf  "The args are " args)
    (let [res (if (= (first args) "*")
                items
                (let [tracks (getExpandedTrackList args)
                      tracks (apply hash-set tracks)]
                  ;; filter for  items contained in tracks
                  items  (filter #(contains? tracks (:track %)) items)))]
      (debug lpf  " resulting in selection: " res)
      res)))
  
(defn generate-log-lines "Show the log-items for the tracks specified in 'args'. If no tracks are specified all log-entries will be shown. The logentries will be sorted with most recent first"
  [items args]
  (let [items  (select-log-lines items args)]
    (if (seq items)
      (let [theKeys  (keys (first items))
	    ;; sort the remainig items
	    items (sort #(* -1 (compare (:datetime %1) (:datetime %2))) items)
	    tbl (apply interleave (map #(map % items) theKeys))
	    tbl (partition (count theKeys) tbl)
	    tbl (concat (list (map name theKeys)) tbl)]
	tbl)
      '())))  ;; list is empty


(defn listActions "Show the Actions for the trackes specified. If no tracks are specified all log-entries will be shown. The logentries will be sorted with most recent first"
  [args]
  (generate-log-lines (ps_getAllActions) args))

(defn listErrors "Show the Actions for the trackes specified. If no tracks are specified all log-entries will be shown. The logentries will be sorted with most recent first"
  [args]
  (generate-log-lines (ps_getAllErrors) args))


(def introMessage "Community Dashboard Manager v0.9 for the synchroneous management of Pentaho cdfde dashboards\n developed by Vinzi.nl (2011-2012).\n Type help to get a general introduction.") 

(def helpMessages
     {
      "apply"  "Usage: apply <source_track>  <dst_track>  [depth]\nApplies all patches made on the source-track to the destination track. The 'depth' gives the number of commits that will be applied. A 'depth' 0 corresponds to the last commit, a 'depth' of 1 corresponds to the changes of the last two commits.\nIf the destination has non-committed changes you can't do an apply (you either have to commit the changes on the destination or revert to the last commit before you can apply patches.)."
      "create" "Usage: create <file-name>\nThe 'file-name' should be a path relative to the current location in the file-system or a full path. This command creates a track-table (full version of the fill) and a patch-table (incremental changes) for the track. The full path-name is stored. Such that you can refer to the track using the base of the file-name during subsequent operations. The current version of .cdfde file is used to make the initial commit to the track-table."
      "commit" "Usage: commit <track> ....\nThe full file-path of 'track' is looked up and the file is committed to the database. A full copy of the file is stored in the 'track-table'. The patches required to synchronize the last commit version with the new version are computed. These patches are stored in the 'patch-table' of this track."
      "clean-copy"  "Usage: copy-track <source_track> <destination file>\nCreate a copy of the src-track at location 'dstPath'. This function create for the destination (a) a track-table containing the last commit of srcTrack (b) the .cdfde and .cda files at the given location (c) an empty patch-table for this track."
      "checkout-copy-last"  "Usage: checkout-copy-last <source_track>\nCreate a copy of the src-track in the same folder, but having name  'trackName_last'. (Does not create a new track in the CDM for the checked-out copy.)"
      "diff" "Usage: diff <track> ....\nThis will show the series of patches that is needed to synchronize the last committed version (database) with the version in the file-system."
      "diffviewer" "Usage diffviewer <track>\nOpens a graphical viewer that shows the differences between the last committed version (database) and the version in de file-system. The differences are color-code (inserts, changes)."
      "dirty" "Usage: dirty [track] ....\nFor each of the tracks it is determined flags  whether the last committed version (database) corresponds to the version in de file-system (match) or that it is dirty (changed). If no arguments are passed a listing of the dirty-status of all tracks is shown."
      "revert-to-commit" "Usage: revert-to-commit <track>\nThe last committed version is extracted and used to overwrite the current .cdfde file and .cda file in the file-system. Beware: non-committed changes will get lost."
      "drop-last-commit" "Usage: drop-last-commit <track>\nThe last commit is dropped from the track-table and from the patch-table of this track. If the initial commit of a track is dropped the track will be completely removed from the system."
}) 


(def generalHelp
     ["The valid commands are:"
      " 1. create: create a track for the dashboard (cdfde-file)"
      " 2. commit: commits the current file-system version to a cdm-track"
      " 3. clean-copy: create a clean copy of the track with a new track-name (and stores it in the cdm-system)"
      " 4. diff: show the differences for a track between the last committed version and the current file"
      " 5. diffviewer: show the differences in the (graphical) viewer"
      " 6. dirty: shows all tracks that have been changed since the last commit"
      " 7. apply: applies all modifications from the last commit of track A to track B"
      " 8. revert-to-commit:  revert the files on disk to the last committed version (overwrites file-system)"
      " 9. drop-last-commit: delete the last committed version (leaves file-system unchanged)"
      " 10. checkout-copy-last: Checkout a copy of a track under name 'trackName_last.cdfde' (does not create new track)"
      " 11. exit: exit cdfdeMgt"
      "Type help <command> to get more information about a command"
      ])

(defn applyPatches [args]
  (if (and (>= (count args) 2)  (<= (count args) 3))
    (let [[srcTrack  dstTrack depthStr] args
	  srcTrack (getTrackName srcTrack)  ;; normalize the track-names
	  dstTrack (getTrackName dstTrack)
	  depth (if depthStr (Integer/parseInt depthStr) 1)
	  depth (dec depth)]  ;; apply last 2 commmits means depth 1
      (applyPatchesSrcDst srcTrack dstTrack depth)) 
    (println (helpMessages "apply"))))


(defn printCommands
  "Print the header followed by the list of valid commands."
  [header]
  (println header)
  (doseq [msg generalHelp]
    (println msg)))

(defn showHelp
  "show specific help for first item in args, or general help if no valid argument is passed."
  [args]
  (let [comm (first args)
	msg (helpMessages comm)]
    (if msg
      (println msg)
      (printCommands introMessage))))

;; (defn toBeImplemented [& ]
;;   (println "This functions is under construction"))

(def commandFuncMap
     {
      "create" createTracks
      "commit" commitVersion
      "diff"   showPatches
      "diffviewer" diffViewers
      "help"   showHelp
      "dirty"  showDirty
      "apply"  applyPatches
      "revert-to-commit"  revertToCommit
      "drop-last-commit"  dropLastCommit
      "clean-copy"   cleanCopyTracks
      "checkout-copy-last" checkout-copy-last
      "list-actions"  listActions
      "list-errors"  listErrors
      })

(def commandMaxSrcMap
     {
      "diffviewer" 1
      "help"   1
      "apply"  1
      "clean-copy"   1
      "checkout-copy-last" 1
      })

(def dontExpandComm #{ "create"  "clean-copy" "checkout-copy-last" "help" "list-errors" "list-actions"})



;; The processcommand is passes a statement. The statement is a hash-map with keys
;;     :command  the command to be processed
;;     :args  a list with all tracks  (source followed by destionations)
;;     :src   a list of all source-tracks
;;     :dst   a list of all destinations-tracks
;; The statement should either contains a combination of :src and :dst OR contain an :args list (but not both).
;; All text should be trimmed text strings.

(defn processCommand
 "Process a command list consisting of a sequence of tokens (words).
Assume that the sql-connection is already established."
  [statement]
  (let [lpf "(processCommand): " 
        comm (:command statement)
        comm (if (string? comm) 
               (str/lower-case comm)
               (error lpf "Command should be string. Current type is: " (type comm) "  and value " comm))
        expand? (not (contains? dontExpandComm comm))]
    (letfn [(get-track-item [theKey]
                            (let [{args theKey} statement]
                              (if expand? (getExpandedTrackList args) args)))
            (process-srcDst []
                            (let [src (get-track-item :src)
                                  dst (get-track-item :dst)
                                  maxSrc (commandMaxSrcMap comm 100)
                                  cntSrc (count src)]
                              (if (> cntSrc maxSrc)
                                {:error (format "Command %s accepts at most %s sources.\nReceived: %s"
                                                comm maxSrc src)}
                                (concat src dst))))
            (process-args []
                          (let [args (if (:src statement)
                                       (process-srcDst)
                                       (get-track-item :args))]
                            args))]
           ;; either :src and :dst   OR   :args OR neither,  but not both
           (assert (or (and (nil? (:src statement)) (nil? (:dst statement)))
                       (nil? (:args statement))))
           (let [args (process-args)
                 oper (commandFuncMap comm)]
             (if (not (map? args)) 
               (if oper
                 (do
                   (debug lpf  "Executing command: " comm )
                   (when (seq args)
                     (debug lpf  "   with arguments: " args)) 
                   (try
                     (oper args)
                     (catch SQLException e
                       (vExcept/report-rethrow (str lpf " command " comm) e))))
                 (vExcept/throw-except lpf " unknown command " comm))
               (vExcept/throw-except lpf " args should be map. Received type: " (type args)))))))

;; OLD STYLE that catches and prints all errors.
;; (replace by top-level error-handler and use the new processCommand
;(defn processCommand-with-catch 
; "Process a command list consisting of a sequence of tokens (words).
;Assume that the sql-connection is already established."
;  [statement]
;  (let [lpf "(processCommand): " 
;        comm (:command statement)
;        comm (if (string? comm) 
;               (str/lower-case comm)
;               (error lpf "Command should be string. Current type is: " (type comm) "  and value " comm))
;        expand? (not (contains? dontExpandComm comm))]
;    (letfn [(get-track-item [theKey]
;                            (let [{args theKey} statement]
;                              (if expand? (getExpandedTrackList args) args)))
;            (process-srcDst []
;                            (let [src (get-track-item :src)
;                                  dst (get-track-item :dst)
;                                  maxSrc (commandMaxSrcMap comm 100)
;                                  cntSrc (count src)]
;                              (if (> cntSrc maxSrc)
;                                {:error (format "Command %s accepts at most %s sources.\nReceived: %s"
;                                                comm maxSrc src)}
;                                (concat src dst))))
;            (process-args []
;                          (let [args (if (:src statement)
;                                       (process-srcDst)
;                                       (get-track-item :args))]
;                            args))]
;           ;; either :src and :dst   OR   :args OR neither,  but not both
;           (assert (or (and (nil? (:src statement)) (nil? (:dst statement)))
;                       (nil? (:args statement))))
;           (let [args (process-args)
;                 oper (commandFuncMap comm)]
;             (if (not (map? args)) 
;               (if oper
;                 (do
;                   (debug lpf  "Executing command: " comm )
;                   (when (seq args)
;                     (debug lpf  "   with arguments: " args)) 
;                   (try
;                     (oper args)
;                     (catch SQLException e
;                       (printSQLExcept (str "Processing command " comm) e)
;                       "Exception caught")))
;                 (println (format "ERROR: unknown command %s" comm)))
;               (println (:error args)))))))

(defn procCL "Process the command assumed first arg is source and rest is destination"
  [statement]
  (let [{:keys [command args]} statement
        src (first args)
        src (if src (list src) '())
        dst (rest args)]
    (processCommand {:command command
                     :src    src
                     :dst    dst})))

