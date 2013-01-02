(ns vinzi.json.jsonGlobals
  (:require [clojure.string :as str]))

;; These definition are moved to a separate file as these are required for each of the interface (database, hibernated)
;;  and therefore required to compile project hib-connect and to compile vinzi.jsonMgt
;; both should be able to compile independently, so this definition is duplicated in both jars.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;   globals of vinzi.json.jsonZip
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn isJson?
  "Basic test to discriminate between json (string representation) and json-objects (hash-maps)."
  [contents]
  (= (str (type contents)) "class java.lang.String"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;   globals of vinzi.json.jsonDiff
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;; definition of a patch
;; and fully qualified labels to be used for the actions
(defrecord Patch [pathList action key value])

;;;;;;;;;;;;;;;;;;;;;;
;;  Translators to store path-list as string and retrieve it from a string.


(defn keywordize
  "keywordize the string 'x' if it starts with a colon"
  [x]
  (if (= (first x) \:) (keyword (apply str (rest x))) x))

(defn getPathList
  "Path is a  string (generated by getPathStrPatch) that needs to be translated back to a pathList."
  [path]
    (if (or (= path "/") (= path ""))
      (vector path)  ;; treat as special case
      (let [path (str/split path #"/")
	    path (map keywordize path)
	    path (if (= (first path) "")
		   (cons "/" (rest path)) path)]
	(vec path))))

(defn getPathStrPatch
  "Get a string representation of the path list of the patch."
  [patch]
 {:pre [(= (str (type patch)) "class vinzi.json.jsonGlobals.Patch")]} 
 (let [pList  (:pathList patch)
	head   (first pList)]
    (if (and (= head "") (<= (count pList) 1))
      (if (= (:key patch) "/")  "" "/")
      (if (= head "/")
	(apply str "/" (interpose "/" (rest pList)))
	(println "ERROR in format pathList: " pList)))))


