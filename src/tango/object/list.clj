(ns tango.object.list
  (:require [tango.runtime :as rt]
            [tango.util.either :as either]))

(defn tango-list
  "Create a Tango List object.

  Used to store a list primitive data values."
  [oid tango-runtime]
  (let [either (rt/create-tango-object 
                tango-runtime
                {:oid oid
                 :nullary-value '()
                 :apply (fn [prev-state log-entry]
                          (clojure.core/cons (:value log-entry) prev-state))})]
    (either/succeed-or-throw-error either)))

(defn get
  "Get the value of the list"
  [tango-list tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  ((:get-current-state tango-list)))

(defn nth
  "Access the value at the given index"
  [tango-list index tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  (clojure.core/nth ((:get-current-state tango-list)) index))

(defn first
  "Access the value at the head of the list"
  [tango-list tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  (clojure.core/first ((:get-current-state tango-list))))

(defn rest
  "Access the tail of the list"
  [tango-list tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  (clojure.core/rest ((:get-current-state tango-list))))

(defn cons
  "Add a new head to the list"
  [tango-list val tango-runtime]
  (rt/update-helper tango-runtime (:oid tango-list) val))
