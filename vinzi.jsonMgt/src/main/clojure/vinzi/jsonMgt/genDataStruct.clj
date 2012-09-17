(ns vinzi.jsonMgt.genDataStruct
  (:use vinzi.hib-connect.code-generator)
  (:import [java.util Date])
  )



(defn gen-data-files []

  (set-table-prefix "cmd_")

  (println "generating a file for the error-entries")
  (generate-hibernate-code
   :vinzi.jsonMgt.hib.errorEntry
   [[:datetime   :java.util.Date]
    [:track      "String"]
    [:command    "String"]
    [:error      "String"]
    [:user       "String"]
    ])

  (println "generating a file for the action-entries")
  (generate-hibernate-code
   "vinzi.jsonMgt.hib.actionEntry"
   [["datetime"   "java.util.Date"]
    [:track      "String"]
    ["action"     "String"]
    ["user"       "String"]
    ])

  (println "generating a file for the Track-entry")
  (println "   remark: the auto-generated id is used "
	   "to connect to commits and patches ('track_id' 'id'")
  (generate-hibernate-code
   :vinzi.jsonMgt.hib.TrackInfo
   [[:file_location :String]
    [:track_name    :String]])

  (println "generating a file for the Track-entry")
  (println "   remark: the auto-generated id is used "
	   "to connect to commits and patches ('track_id' 'id'")
  (generate-hibernate-code
   :vinzi.jsonMgt.hib.Commit
   [[:track_id  :Long]
    [:datetime  :java.util.Date]
    [:contents  :String]])

  (println "generating a file for the Track-entry")
  (println "   remark: the auto-generated id is used "
	   "to connect to commits and patches ('track_id' 'id'")
  (generate-hibernate-code
   :vinzi.jsonMgt.hib.DbPatch
   [[:track_id  :Long]
    [:datetime  :java.util.Date]
    [:path      :String]
    [:action    :String]
    [:patchkey  :String]
    [:value     :String]])
  )

