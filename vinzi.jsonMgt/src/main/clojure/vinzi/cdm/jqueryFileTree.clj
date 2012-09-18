(ns vinzi.cdm.jqueryFileTree
  (:use	   [clojure pprint]
           [clojure.tools logging])
  (:use [vinzi.cdm globals])
  (:require [clojure [string :as str :only [replace]]])
  (:import [java.io File]))



(def dirFmt  "<li class=\"directory collapsed\"><a href=\"#\" rel=\"%s\">%s</a></li>")
(def fileFmt "<li class=\"file ext_%s\"><a href=\"#\" rel=\"%s\">%s</a></li>")
(def fvFmt "<ul class=\"jqueryFileTree\" style=\"display: none;\">%s</ul>")


;; windows
;;(def docRoot "C:/Users/Cees/Documents/Clj/cdm-server/resources/static/")

;;(def docRoot "/home/cees/Clj/cdm-server/resources/static/")
;; start with a nil docroot. The first path provided is assumed
;; to be a docRoot
(def  docRoot nil)

;(defn set-jqft-docRoot "Set the docRoot for the file tree viewer." [dr]
;  (def docRoot dr))

(def changeSep (not= File/separator "/"))


;(defn jqGetDocRoot 
;  "Pass the docRoot to the javascript client side." 
;  [req]
;  ;; wait until the docRoot is set by the server.
;  ;; (let [intMs  100
;  ;; 	wait (loop [waitMs 0]
;  ;; 	       (when (and (nil? docRoot) (< waitMs 5000))
;  ;; 		 (Thread/sleep intMs)
;  ;; 		 (recur (+ waitMs intMs))))]
;  ;;   (println "In jqGetDocRoot waited " wait " ms.\b\tReturning response: " docRoot)
;  ;; server is single-threaded, so transfered wait-loop to javascript.
;  (let [lpf "(jqGetDocRoot): "
;        response (if (nil? docRoot) "" docRoot)]
;    (info lpf "Returning response: " response)
;    (getTextResponse response)))

(defn jqGenerateFileView 
  "Generate output that can be used by the jquery file-browser."
  [req]
  (let [lpf "(jqGenerateFileView): "]
  (letfn [(getLocation [req]
		       ;; assume request points to a sub-path of 'docRoot'
		       (let [loc (get (:params req) "dir" "")
;; 			     loc (if (.startsWith loc docRoot) loc
;; 				     (do
;; 				       (println "Location " loc
;; 						" is not allowed. Switch to: "
;; 						docRoot) 
			     ;; 				       docRoot))
			     ;; PATCH because we don't have a proper
			     ;; install procedure yet.
			     ;; (temporary security leak)
			     _ (when (nil? docRoot)
				 (println "CODE TO BE REMOVED!!")
				 (def docRoot loc))
			     ;; prepend doc-root only to relative path
			     fc  (first loc)
			     isAbs (or (= fc \/) (= fc \\))
			     loc (if isAbs loc (str docRoot loc))]
           (println "looking for loc" loc)
           (println "   is directory: " (.isDirectory (File. loc)))
           (println "   is file: " (.isFile (File. loc)))
           (if changeSep
             (str/replace loc "/" File/separator)
             loc)))
	  (getHtml [{:keys [dir? path fname ext]}]
            (if dir?
              (format dirFmt path fname)
              (format fileFmt ext path fname)))
	  (getPathStr [f]
               (->> f
                 (.getAbsolutePath)
                 (drop (count docRoot))
                 (apply str)))
	  (getFileDescr [f]
                 (let [path (getPathStr f)
                       fname (last (str/split path #"\\"))
                       ext (last (str/split fname #"\."))
                       dir? (.isDirectory f)
                       file? (.isFile f)
                       ;; jqueryFileViewer assumes a "/"
                       ;;  at the end of a directory-path
                       path (if dir? (str path "/") path)]
                   
                   (when  (not (or dir? file?))
                     (println "Path: " path
                              " is neither file nor directory!!"))
                   {:dir? dir?
                    :path path
                    :fname fname
                    :ext   ext}))
	  (sortFDlist  [fl]
                (letfn [(compFD [ a b] (compare (:fname a) (:fname b)))]
                       (sort compFD fl)))
	  (getDirFiles [loc]
                (if loc
                  (let [top (File. loc)
                        isDir (.isDirectory top)
                        files (if isDir
                                (.listFiles top)
                                (list top))
                        files (map getFileDescr files)
                        files (group-by :dir? files)]
                    ;; return files (directories first)
                    (concat (sortFDlist (get files true))
                            (sortFDlist (get files false))))
                  (println "Error: no location passed "
                           "by jqueryFileViewer")))
	  ]
         (let [loc   (getLocation req)
               files (getDirFiles loc)
               files (map getHtml files)
               body (format fvFmt (apply str files))]
           (getHtmlResponse body)))))

