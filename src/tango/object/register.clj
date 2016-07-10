(ns tango.object.register
  (:require [tango.runtime :as rt]))

(defn tango-register
  "Create a Tango Register object.

  Used to store a single primitive data value."
  [oid val]
  (let [a (atom val)]
    {:oid oid
     :value a
     :apply (fn [log-entry]
              (swap! a (fn [_] (:value log-entry))))}))

(defn set
  [tango-register val tango-runtime]
  (rt/update-helper tango-runtime (:oid tango-register) val)
  val)

(defn get
  [tango-register tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-register))
  @(:value tango-register))

