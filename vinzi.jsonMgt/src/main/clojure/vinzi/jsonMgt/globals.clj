(ns vinzi.jsonMgt.globals
  (:use	   [clojure pprint]
           [clojure.tools logging])
  ;; line added as binding of globals does not work yet (renamed functions in database.clj )
  (:use [vinzi.jsonMgt persistentstore]
        [vinzi.jsonMgt.hib.clj errorEntry actionEntry])
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
(def jsonPostfix ".json")

(def defPostfix  jsonPostfix)

(defn setDefPostfix
  "Change the default postfix of filenames."
  [pf]
  (def defPostfix pf))

(def confirmReader (fn [msg] (println msg  "Assuming yes") true))
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




(defn getUserName
  []
  (.. System (getProperties) (get "user.name")))

(defn getUserHome
  []
  (.. System (getProperties) (get "user.home")))

(defn printSQLExcept
  "Print report for an SQL exception
   (including one step higher in the exception-chain."
  [location e]
  (println "Exception caught at location " location)
  (println "Message: " (.getMessage e))
  (println "ErrorCode: " (.getErrorCode e))
  (println "SQLState:  " (.getSQLState e))
  (when-let [n (.getNextException e)]
    (println "Next-message: " (.getMessage n))
    (println "Next-errorcode: " (.getErrorCode n)))
  (flush))



(defn addErrEntry
  "Used by addMessage and to directly write a series of messages (errors)."
  [msg dt track]
  (let [errEntry (create-errorEntry dt track currCommand msg (getUserName))]
    (ps_writeErrorEntry errEntry)))

(defn addMessage
  "Increase the number of errors 'nrErrors' Add the message to the error database."
  [track & msgs]
  (let [lpf "(addMessage): "
        msg (apply format msgs)
        dt (getCurrDateTime)]
    (warn lpf "Track:'" track "' --> " msg)
    (signalError)
    (addErrEntry msg dt track))
  nil)


(defn writeActionEntry
  [track dt act]
  (let [actEntry (create-actionEntry dt track act (getUserName))]
  (ps_writeActionEntry   actEntry)
  true))


;; old code
;;(rebind-tmp pln getUserName getUserHome confirmReader getCurrDateTime addErrEntry addMessage writeActionEntry printSQLExcept)

