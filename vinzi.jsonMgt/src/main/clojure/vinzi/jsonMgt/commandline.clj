(ns vinzi.jsonMgt.commandline
  (:use [vinzi.jsonMgt globals core persistentstore])
  (:require [clojure
	     [string :as str]]
	    [clojure.java
	     [io :as io :only [reader]]])
  (:import [java.io File BufferedReader]))



(defn procCommWithinConn
  "Process a single command provided by string 'comm'. "
  [comm]
  (binding [currCommand comm]
    (let [cl (str/split comm #"\s+")]
      (if-let [cl (if (and (seq cl) (= "" (first cl))) (rest cl) cl)]
	;; using rest to force realisation of nil instead of empty seq
	(when (> (count cl) 0)
	  (pln "processCommandStr " (str cl)) (flush)
	  (resetNrErrors)
	  (procCL {:command (first cl)
		   :args (rest cl)})
	      (= @nrErrors 0))))))  ;; return true if no-errors, otherwise false

(defn processCommandStr
  "Process a single command provided by string 'comm' within a database connection.  Mainly used for test purposes. Normal entry runs through procCommFile."
  [comm]
  (ps_callWithConnection procCommWithinConn comm))

(defn- procCommFile
  "Process a sequence of command from buffStream'. Each line of the 'buffStream' is assumed to be a separate command. 'buffStream' is handled as a lazy sequence, so this command can also be used to handle a command-line"
  ([buffStream] (procCommFile buffStream ""))
  ([buffStream prompt]
     (let [processFile (fn []
			   (print prompt) (flush)
			   (loop  [commands (line-seq buffStream)]
			     (let [comm (first commands)
				   tail (rest commands)
				   exit (= (str/lower-case (str/trim comm)) "exit")]
			       (when (not exit)
				 (procCommWithinConn comm)
				 (print prompt) (flush)
				 (recur tail)))))]
       (ps_callWithConnection processFile))))

;; (defn processCommandFile
;;   "Process the commands from file 'filename'. Each line is considered to be a separate command."
;;   [filename]
;;   (readCdfdeMgtConfig)
;;   (with-open [read (io/reader filename)]
;;     (procCommFile read))
;;   (println (format "Reached end of file '%s'." filename)))


(def confirmRead nil)



;; use binding to install this confirmReader.
;;
(defn confirmReaderCL
  "Prints messages and returns a confirmation (yes = true) via a global reader. If reader is nil then default is used."
  ([msg] (confirmReader msg true))
  ([msg default]
     (if (nil? confirmRead)
       default
       (do
	 (print msg "[yes/no] ") (flush)
	 (let [res (str/lower-case (str/trim (.readLine confirmRead)))]
	   (if (= res "yes")
	     true
	     (if (= res "no")
	       false
	       (recur (str msg "!") default))))))) )

(defn processCommandStdin
  "Interactive mode where commands are typed on a prompt and executed immmediately."
  []
  (println introMessage)
;  (readJsonMgtConfig)  (moved to main loop)
;;  (with-open [read (BufferedReader. *in*)]
  (let [read (BufferedReader. *in*)]
    (let [prompt "cdfdeMgt> "]
      (def confirmRead read)
      (procCommFile read prompt)))
  (def confirmRead nil)
  (println "Leaving cdfdeMgt interactive mode."))

;; (defn -main [& args]
;;   (if (seq args)
;;     (println "The arguments are ignored: " args))
;;   (processCommandStdin))

;; (defn doSql [& cmds]
;;   (sql/with-connection db (apply sql/do-commands cmds)))

;; (defn showSql [& cmds]
;;   (sql/with-connection db (apply sql/do-commands cmds)))

;; (defmacro doComm [cmd]
;;   `(sql/with-connection db ~cmd)) 
