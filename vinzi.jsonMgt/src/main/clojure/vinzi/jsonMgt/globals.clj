(ns vinzi.jsonMgt.globals
  (:use	   [clojure pprint]
           [clojure.tools logging])
  ;; line added as binding of globals does not work yet (renamed functions in database.clj )
  (:use [vinzi.jsonMgt persistentstore]
        [vinzi.jsonMgt.hib.clj errorEntry actionEntry])
  (:require [clojure.string :as str]
            [vinzi.tools
;;             [vSql :as vSql]
             [vExcept :as vExcept]])
  (:import [java.util Date]
	   [java.sql SQLException Timestamp])
  )

(defmacro dc [m x] `(let [x# ~x] (do (println ~m " : " '~x "=" x#) (flush)) x#))

;;(defmacro dc [m x] x)

(def printPln true)
;(defn pln [& args]
;  (when printPln
;    (apply println "DEBUG: " args)
;    (flush)))

(defn setPrintPln "Set the printPln var to either true or false."
  [val]
  (def printPln val))

(def cdfdePostfix ".cdfde")
(def cdaPostfix   ".cda")
(def wcdfPostfix   ".wcdf")
(def jsonPostfix ".json")

(def defPostfix  jsonPostfix)

(defn setDefPostfix
  "Change the default postfix of filenames."
  [pf]
  (def defPostfix pf))

(def  ^:dynamic confirmReader (fn [msg] (println msg  "Assuming yes") true))

(defn installConfirmReader
  "define confirmReader as function 'f'. "
  [f]
  (def confirmReader f))


(defn getCurrDateTime
  "Returns a map with the current date and time."
  []
  (let [jd (Date.)
        ts (Timestamp.  (.getTime jd))]
    ts))







;;;;;;;;;;;;;;;;;;;
;;  Auxiliary (helper) functions
;;

;; global-binding with the current command
(def ^:dynamic currCommand "")

;; functions to signal there was an error (yes/no)
(defn resetNrErrors []  (def nrErrors (atom 0)))
(defn  signalError
  "Signal that there was at least one error."
  []
      (swap! nrErrors (fn [x] (inc x))))

;; initialize for debugging purposes
(resetNrErrors)

(def overrideUserName (atom nil))
;; OverrideUserName is used in the web-interface as the executing user (tomcat or pentaho)
;; usually does not correspond to the dashboard-user.

(defn set-override-username [userName]
  (swap! overrideUserName (fn [_] userName)))

(defn getUserName
  ""
  []
  (if @overrideUserName
    @overrideUserName
    (.. System (getProperties) (get "user.name"))))

(defn getUserHome
  []
  (.. System (getProperties) (get "user.home")))

(defn printSQLExcept
  "Print report for an SQL exception
   (including one step higher in the exception-chain."
  [location e]
  (vExcept/report location e))

;; usage of the next few lines should be replaced by vFile.
(def regExpSepator  (if (= java.io.File/separator "\\")
			#"\\" #"/"))
(def searchSep (if (= java.io.File/separator "\\")
			"/" "\\"))
(def theSep (if (= java.io.File/separator "\\")
		  "\\" "/"))

(def regExpIsFilename  (if (= java.io.File/separator "\\")
			 #"[.\\]" #"[./]"))
(defn isFilename?
  "Assumes name is a filename if it contains '.' or a file-separator character."
  [name]
  (re-find regExpIsFilename  name))  




(defn stripDefaultPostfix [name]
  (let [regExp (re-pattern (str (str/replace defPostfix "." "\\.") "$"))]
;;    (cstr/replace-re regExp "" name)))
    (str/replace name regExp "")))


(defn correctSeparator [name]
  (str/replace name searchSep theSep))

(defn get-filename
  "Get the filename by stripping of the path-part and trimming redundant spaces."
  [filename]
  (-> filename
      (correctSeparator)
      (str/split regExpSepator)
      (last)
      (str/trim)))


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

;; moved here to use it in addErrEntry
(defn get-track-name
  "Extract the base-filename (without extension) and change it to a database-friendly format (no white-space, no '.' and completely lower-case."
    [filename]
  (-> filename
      (get-filename)
      (stripDefaultPostfix)
      (cleanStr)
      (str/lower-case)))




(defn addErrEntry
  "Used by addMessage and to directly write a series of messages (errors)."
  [msg dt track]
  (let [errEntry (create-errorEntry dt (get-track-name track) currCommand msg (getUserName))]
    (ps_writeErrorEntry errEntry)))

;; OLD version, which did not use the exception handling mechanism
;
;(defn addMessage
;  "Increase the number of errors 'nrErrors' Add the message to the error database."
;  [track & msgs]
;  (let [lpf "(addMessage): "
;        msg (apply format msgs)
;        dt (getCurrDateTime)]
;    (warn lpf "Track:'" track "' --> " msg)
;    (signalError)
;    (addErrEntry msg dt track))
;  nil)

(defmacro addMessage
  "Increase the number of errors 'nrErrors' Add the message to the error database."
  [track & msgs]
  `(let [msg# (format ~@msgs)
         ex#  (Exception. (str ~'lpf "Track:'" ~track "' --> " msg#))]
    ;; report the error to ensure is is not overwritten during the addEntry, but
    ;; raise it after addErrEntry is finished.
    (vExcept/report msg# ex#)
    (addErrEntry msg# (getCurrDateTime) ~track)
    (throw ex#)))  ;; now raise the already reported exception.


(defn writeActionEntry
  [track dt act]
  (let [actEntry (create-actionEntry dt track act (getUserName))]
  (ps_writeActionEntry   actEntry)
  true))


;; old code
;;(rebind-tmp pln getUserName getUserHome confirmReader getCurrDateTime addErrEntry addMessage writeActionEntry printSQLExcept)

