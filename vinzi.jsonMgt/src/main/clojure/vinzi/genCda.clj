(ns vinzi.genCda
  (:require  [clojure
	      [zip :as zip]])
  (:require  [clojure
	      [prxml :as xml]
	      [string :as str]]
	     [clojure.data [json :as json]])
  (:use [clojure.pprint])
  (:use [vinzi.jsonZip])
  )

(def testFase true)


(defn printNodeShort
  "Print the node with the children"
  [n depth]
  (let [prefix (str depth (last (take (dec depth) (iterate #(str "  " %) ""))))]
    (if (map? n)
      (let [na (:name n)
	    va (:value n)
	    ty (:type n)
	    id (:id n)]
	(when (or id na va ty)
	  (print prefix)
	  (if id (print " \t:id  "  id))
	  (if na (print " \t:name " na))
	  (if va (print " \t:value  "  va))
	  (if ty (print "\t\t:type  "  ty))
	  (println)))
      (println prefix "  " n))))

(defn pns
  "Print the current loc of zipper 'z' at depth 1"
  [z] (printNodeShort (zip/node z) 1))


(defn printNode
  "Print the node with the children"
  [n depth]
  (let [prefix (str depth (last (take (dec depth) (iterate #(str "  " %) ""))))]
    (if (map? n)
      (let [n (dissoc n :jsonChildren)
	    kv (partition 2 (interleave (keys n) (vals n)))]
	(if (= (:json/type (meta n)) jsonTypeMap)
	  (println prefix "MAP")
	  (println prefix "VECTOR"))
	(doseq [p kv]
	  (println prefix " " (first p) "  " (second p))))
    (println prefix "  " n))))


(defn dfsZip
  "Perform a dfs (depth first search) over the zipper 'z' and apply 'oper' to the node contents. The parameter 'depth' determines the maximal depth of the search."
([z oper] (dfsZip z oper 1))
([z oper depth]
   (loop [z (zip/down z)]
     (when z
       (dfsZip z oper (inc depth))
       (oper (zip/node z) depth)
       (recur (zip/right z))))))


(defn showCda
  "generate a Cda file for the 'cdfde' structure (jsonZipper of a .cdfde-file."
  [cdfde]
  (if-let [cdfde (zipLoc cdfde ["/" :datasources])]
    (dfsZip cdfde printNodeShort)
    (println "Could not locate the datasources in the cdfde file")))


;;;;;;;;;;;;;;;;;
;;  helper functions

(def baseDataSources [:DataSources] )
(def baseDataAccess  [:DataAcces {}] )


(defn getNodeVal [n k]
  (let [v (n k)]
    (if (isBoxed? v) (first v) v)))


(defn addDAattribute
  "Add the attirbute field to DataAccess using key 'k'. "
  [k xml node]
  (let [value (getNodeVal node :value)
	src (:src xml)
	nDA (assoc-in src [1 k] value)
	res (assoc xml :src nDA
		   :id value)]
    res))

(defn addDAcontents
  "Add the value field as contents within DataAccess using key 'k'. "
  [k xml node]
  (let [value (getNodeVal node :value)
	nDA  (conj (:src xml) [k value])
	res (assoc xml :src nDA)]
    res))

(defn addTag
  "Add the value field as tag to using key 'k' for later usage. "
  [k xml node]
  (let [value (getNodeVal node :value)
	res (assoc-in xml [:tags k] value)]
    res))

(defn addParam
  "Add the value field as param-tag to using key 'k' for later usage. The parameter is transformed to a json-object. (Parameters are stored as a vector)"
  [k xml node]
  (let [value (json/read-json (getNodeVal node :value))
	pars   (conj (:params xml) value)
	res (assoc xml :params  pars)]
    res))

(defn addValuesArray
  "Add the value field as param-tag to using key 'k' for later usage. The parameter is transformed to a json-object. (Parameters are stored as a vector)"
  [k xml node itemTag]
  (let [nodeVal (getNodeVal node :value)
	values (json/read-json nodeVal)]
    (loop [vs values
	   cumm [] ]
      (if (seq vs)
	(let [v (first vs)
	      idx (get v 0)
	      name (get v 1)
	      descr [itemTag  {:idx idx} [:Name name]]
	      cumm (conj cumm descr)]
	  (recur (rest vs) cumm))
	;; compute the adjusted xml
	(let [contents [(keyword (str k "s"))]
	      contents (if (seq cumm) (into contents cumm) contents)
	      nDA  (conj (:src xml) contents)
	      res (assoc xml :src nDA)]
	  res)))))



;;;;;;;;;;
;;  Definition of the multifunction for processing jsoncomponents
;;  the first parameter should be a string of keyword that corresponds
;;  to the name of the tag.

(def gKey (comp keyword (fn [x & args] x)))                                    
(defmulti processRec gKey :default :unknown)

(defmethod processRec :unknown [tag xml node & args]
	   (println (format "The tag %s  is not recognized." tag))
	   (when testFase
	     (println "   with contents: " node))
	   nil)
		    

(comment ;; Some simple examples of usage of this dispatch function
  (defmethod processRec :param [& args]
	     (println "passed sequence " args))
  
  (defmethod processRec :comm [a b c]
	     (println "command" a "with vals " b " and "  c))

  (processRec "param" 1 2 3)
  (processRec :param  4 5 6)
  (processRec "comm"  7 8)
  (processRec :comm  7 8)
)

;;;;;;;;;;;
;; Implementation of the dispatch functions for different tags
;; Each dispatch function should have the form
;;   (processRec  name  {:head :source} value)
;; The function should return a modified {:head :source} map on succes
;;    or nil on failure.
(defmethod processRec :name [name xml node]
	   (let [xml (addDAattribute :id xml node)]
	     (addTag :id xml node)))
	     
;; 	   (let [value (getNodeVal node :value)
;; 		 src (:src xml)
;; 		 nDA (assoc-in src [1 :id] value)
;; 		 res (assoc xml :src nDA
;; 			    :id value)]
;; ;	     (println "After " name " obtained result: " res)
;; 	     res))

(defmethod processRec :jndi [name xml node]
	   (addTag :Jndi xml node))

(defmethod processRec :catalog [name xml node]
	   (addTag :Catalog xml node))

(defmethod processRec :output [name xml node]
	   (addTag :Output xml node))

(defmethod processRec :outputMode [name xml node]
	   (addTag :OutputMode xml node))

(defmethod processRec :access [name xml node]
	   (addDAattribute :access xml node))

(defmethod processRec :query [name xml node]
	   (addDAcontents :Query xml node))

(defmethod processRec :bandedMode [name xml node]
	   (addDAcontents :BandedMode xml node))

(defmethod processRec :cache [name xml node]
	   (addDAattribute :cache xml node))
	   
(defmethod processRec :cacheDuration [name xml node]
	   (addDAattribute :cacheDuration xml node))

(defmethod processRec :driver [name xml node]
	   (addTag :Driver xml node))

(defmethod processRec :url [name xml node]
	   (addTag :Url xml node))

(defmethod processRec :user [name xml node]
	   (addTag :User xml node))

(defmethod processRec :pass [name xml node]
	   (addTag :Pass xml node))

(defmethod processRec :parameters [name xml node]
	   (addParam :parameters xml node))

(defmethod processRec :cdacolumns [name xml node]
	   (addValuesArray "Column" xml node :Column))

(defmethod processRec :cdacalculatedcolumns [name xml node]
	   (addValuesArray "Column" xml node :Column))

;; not used (only appears in the empty connections)
(defmethod processRec :Group [name xml node]
	   (addTag :Group xml node))

(defmethod processRec :initscript [name xml node]
	   (addTag :Initscript xml node))

(defmethod processRec :language [name xml node]
	   (addTag :Language xml node))

(defmethod processRec :ktrFile [name xml node]
	   (addTag :KtrFile xml node))

(defmethod processRec :variables [name xml node]
	   (addValuesArray "Variables" xml node :Variable))




(defn applyToChildCumm
  "Applies an operation to each of the children of zipper 'z'. The cummulator for the results is passed as the second argument."
  [z cumm oper mergeCumm & args]
  (when testFase
    (println "\nEntering applyToChildCumm")
    (println " (type z) = " (type z))
    (println " cumm = " cumm)
    (println " mergeCumm = " mergeCumm)
    (println "args = "args)
    (flush))
  (loop [z     (if z (zip/down z) z)
	 cumm  cumm]
    (if z
      (let [res (apply oper z cumm args)
	    cumm (if mergeCumm (mergeCumm cumm res) res)]
	(recur (zip/right z) cumm))
      cumm)))

(defn applyToChild
  "Applies an operation to each of the children of zipper 'z'. Additional arguments will be passed through (however, no cummulator)."
[z oper & args]
(loop [z (zip/down z)]
  (when z
    (apply oper z args)
    (recur (zip/right z)))))


(defn processRecord
  "Process this property."
  [z cumm]
  (let [n (zip/node z)
	cnt (:cnt cumm)
	prefix (str cnt "  ")]
    (if (not (map? n))
      (println prefix " SIMPLE VALUE " n  " could not be processed!")
      (let [na (getNodeVal n :name)
;; start temporary code
	    va (getNodeVal n :value)
	    id (getNodeVal n :id)]
	(when (and testFase
		   (or id na va))
	  (print prefix)
	  (if na (print " \t:name " na))
	  (if va (print " \t:value  "  va))
	  (println))
;;; end tmp	
	(let [res (processRec na cumm n)]
	  (if res res cumm))))))


(defn processProps
  "extract all properties and apply operation 'oper'."
  [z  cumm oper mergeCumm]
  (let [n (zip/node z)
	t (:type n)
	id (:id n)
	fakeNode  {:value (:meta_conntype n)}
	cumm (addTag :meta_conntype cumm fakeNode)]
    (when testFase
      (println (format "\n\nVISIT node with id=%s  and type=%s" id t)))
    (applyToChildCumm (zip/down z) cumm oper mergeCumm)))


(defn generateXML [res]
  (let [CDAdescr (into (vector :CDADescriptor (:head res)) (:sources res))]
    (with-out-str (binding [xml/*prxml-indent* 2]
      (xml/prxml  [:decl!] CDAdescr)))))



(defn addConnectionAux [ds contents tags]
  (let [{:keys [id meta_conntype]} tags
	connParam {:id id :type meta_conntype}
	contents (loop [contents contents
			cumm     []]
		   (if (seq contents)
		     (let [item (first contents)
			   value (item tags)
			   record (if value [item value] [item])
			   cumm (conj cumm record)]
		       (when (nil? value)
			 (println (format "Warning: expected item %s in connection %s"
					  item meta_conntype)))
		       (recur (rest contents) cumm))
		     cumm))
	connection (into [:Connection connParam] contents)]
    (conj ds connection)))

(def connectionDefinitions 
     { "sql.jndi" [:Jndi]
       "sql.jdbc" [:Driver :Pass :Url :User]
       "mondrian.jndi" [:Catalog]
       "scripting.scripting" [:Initscript :Language]
       "kettle.TransFromFile" [:KtrFile]
       })

(defn addConnection
  "Add a connection to the datasources 'ds' based on the information in 'tags'."
  [ds tags]
  (if-let [id  (:meta_conntype tags)]
    (if-let [fields (connectionDefinitions id)]
      (addConnectionAux ds fields tags)
      (do
	(println "ERROR: unrecognized meta_conntype: " id)
	ds))
      ds))  ;; return datasource unmodified


;; OLD CODE: removed by refactoring
;;
;; (defn addConnection
;;   "Add a connection to the datasources 'ds' based on the information in 'tags'."
;;   [ds tags]
;;   (let [{:keys [id meta_conntype]} tags
;; 	 connParam {:id id :type meta_conntype}]
;;     (if (= meta_conntype "sql.jndi")
;;       (let [jndi  (:jndi tags)
;; 	    connection [:Connection
;; 		    connParam
;; 		    [:Jndi jndi]]]
;; 	(when (not (and id jndi meta_conntype))
;; 	  (println "WARNING: some data seems missing for connection with id: " id))
;; 	(conj ds connection))
;;       (if (= meta_conntype "sql.jdbc")
;; 	(let [{:keys [driver url user pass]} tags
;; 	      connection [:Connection
;; 			  connParam
;; 			  [:Driver driver]
;; 			  [:Pass   pass]
;; 			  [:Url    url]
;; 			  [:User   user]]]
;; 	(when (not (and id meta_conntype driver pass url user))
;; 	  (println "WARNING: some data seems missing for connection with id: " id))
;; 	(conj ds connection))
;; 	(if (= meta_conntype "mondrian.jndi")
;; 	  (let [catalog  (:catalog tags)
;; 		connection [:Connection
;; 			    connParam
;; 			    [:Catalog catalog]]]
;; 	    (when (not (and id catalog meta_conntype))
;; 	      (println "WARNING: some data seems missing for connection with id: " id))
;; 	    (conj ds connection))
;; 	  (if (= meta_conntype "scripting.scripting")
;; 	    (let [initscript  (:initscript tags)
;; 		  language    (:language tags)
;; 		  connection [:Connection
;; 			      connParam
;; 			      [:Initscript initscript]
;; 			      [:Language language]]]
;; 	      (when (not (and id initscript language meta_conntype))
;; 		(println "WARNING: some data seems missing for connection with id: " id))
;; 	      (conj ds connection))
;; 	  (do
;; 	    (when meta_conntype
;; 	      (println "ERROR: unrecognized meta_conntype: " meta_conntype))))))))

(defn addOutput [ds tags]
  (if-let [output (:Output tags)]
    (if-let [val (json/read-json output)]
      (if (> (count val) 0)
	(let [val        (apply str (interpose "," val))
	      param      {:indexes val}
	      outputMode (:OutputMode tags)
	      param      (if outputMode
			   (assoc param :mode outputMode)
			   param)
	      v          (vector :Output param)]
	  (conj ds v))
	ds)
      ds)
    ds))  ;; return datasource unmodified

(defn addParameters [ds params]
  ;;(println "Enter addParams: with count = " (count params))
  (when (> (count params) 1)
    (println "ERROR: expecting at most 1 set of params. received " (count params)))
  (loop [params (first params)
	 cumm  [] ]
    (if (seq params)
      (let [p (first params)
	    par [:Parameter {:name (get p 0)
			     :default (get p 1)
			     :type  (get p 2)
			     }]
	    _ (let [p3 (get p 3)]
		(when (and p3 (not (= (str/trim p3) "")))
		(println "WARNING: do not now how to process parameter-setting" (get p 3))))
	    cumm (conj cumm par)]
	;;	  (println "added parameter: " par)
	(recur (rest params) cumm))
      ;; all params processed compute result vector
      (let [res [:Parameters]
	    res (if (seq cumm) (into res cumm) res)
	    ds (conj ds res)]
	ds))))  
	

(defn mergeSources [_ res]
  (let [{:keys [tags src]} res]
;;    (if (and (<= (count tags) 1)  ;; tags contains only :meta_conn
;;	     (= src baseDataAccess))
;;      res  ;; nothing changed (ghost record)
      (let [src     (:src res)
	    src     (addParameters src (:params res))
	    src     (addOutput src (:tags res))
	    sources (conj (:sources res) src)
	    head    (addConnection (:head res) (:tags res))]
	(assoc res
	  :head head
	  :src baseDataAccess
	  :sources sources
	  :tags {}
	  :params []))))

(defn- generateCdaVector
  "generate a Cda vector for the 'cdfde' structure (jsonZipper of a .cdfde-file)."
  [cdfde]
  {:pre [(not (nil? cdfde)) (isZipper? cdfde)]}
  (if-let [cdfde (zipLoc cdfde ["/" :datasources])]
    (let [initCumm {:cnt 0
		    :head baseDataSources
		    :src  baseDataAccess
		    :sources []
		    :tags {}
		    :params []}
	  res (applyToChildCumm (zip/down cdfde) initCumm
				processProps  mergeSources
				processRecord nil)]
;;      (println "the result of CdaVector is : " res)
      res)
    (println "Could not locate the datasources in the cdfde file")))

(defn generateCda
  "generate a Cda file for the 'cdfde' structure (jsonZipper of a .cdfde-file."
  [cdfde]
  {:pre [(not (nil? cdfde)) (isZipper? cdfde)]}
  (if-let [xmlVect  (generateCdaVector cdfde)]
    (if-let [xml (generateXML xmlVect)]
      xml
      (println "ERROR: Could not generate the XML"))
    (println "Could not locate the datasources in the cdfde file")))

(comment
;;;;;;;;;;;;;;
;;; temporary code for testing

(require '(clojure.contrib [json :as json] [io :as io]))

	 
(defn- generateCdaTest
  "generate a Cda file for the 'cdfde' structure (jsonZipper of a .cdfde-file."
  [cdfde]
  {:pre [(not (nil? cdfde)) (isZipper? cdfde)]}
;  (def testFase true)
  (binding [testFase false]
    (if-let [xmlVect  (generateCdaVector cdfde)]
      (do
;;	(println "The result vector is: " xmlVect)
	;;(doseq [i xmlVect]
	;; 	(println i))
	(println "and as XML:")
	(if-let [xml (generateXML xmlVect)]
	  xml
	  (println "Could not generate the XML.")))
      (println "Could not locate the datasources in the cdfde file"))))



(defn testrun []
  (let [
	filename "/home/cees/tmp/pentaho-solutions/tmp/jdbc-test.cdfde"
;	filename "/home/cees/tmp/pentaho-solutions/tmp/EIS-poc.cdfde"
;	filename "/home/cees/tmp/pentaho-solutions/ZIGT-Dashboard/LPs/Board/BoardLP.cdfde"
;	filename "/home/cees/tmp/pentaho-solutions/ZIGT-Dashboard/Dashboards/Finance/FinanceSummary.cdfde"
	filename "/home/cees/tmp/pentaho-solutions/kettle-cda/Dashboard5.cdfde"
	]
  (with-open [f (io/reader filename)]
    (let [js  (json/read-json f)
	  cdfde (jsonZipper js)]
      (if cdfde
	(let [res (generateCdaTest cdfde)]
	  (println "The generator returned")
	  (doseq [i (str/split #"\n" res)] (println i))   )
	(println "cdfde = nil"))))))

(defn exampleXml
  "example of generating xml using clojure.contrib.prxml"
  []
  (binding [xml/*prxml-indent* 2]
    (xml/prxml  [:decl!]
	    [:DataAccess {:access "public"
			  :cache "true" "cacheDuration" 3600
			  :connection "genderDistrib"}
	     [:Columns  "contents"]]))
  )
(exampleXml)
;(testrun)

)   ;; end test-code 1

(comment




  
  (with-open [f (io/reader "../cdfdeMgt/data/EIS.cdfde")]
    (let [js  (json/read-json f)
	  cdfde (jsonZipper js)]
      (def x cdfde)))


  )   ;; end test-code  2
