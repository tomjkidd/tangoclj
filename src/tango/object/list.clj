(ns tango.object.list
  (:require [tango.runtime :as rt]))

(defn tango-list
  "Create a Tango List object.

  Used to store a list primitive data values."
  [oid]
  (let [a (atom '())]
    {:oid oid
     :value a
     :apply (fn [log-entry]
              (swap! a (fn [prev]
                         (clojure.core/cons (:value log-entry) prev))))}))

(defn get
  "Get the value of the list"
  [tango-list tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  @(:value tango-list))

(defn nth
  "Access the value at the given index"
  [tango-list index tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  (clojure.core/nth @(:value tango-list) index))

(defn first
  "Access the value at the head of the list"
  [tango-list tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  (clojure.core/first @(:value tango-list)))

(defn rest
  "Access the tail of the list"
  [tango-list tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-list))
  (clojure.core/rest @(:value tango-list)))

(defn cons
  "Add a new head to the list"
  [tango-list val tango-runtime]
  (rt/update-helper tango-runtime (:oid tango-list) val))
