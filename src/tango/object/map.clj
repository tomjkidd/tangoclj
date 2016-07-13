(ns tango.object.map
  (:require [tango.runtime :as rt]
            [tango.util.either :as either]))

(defn tango-map
  "Create a Tango Map object.

  Used to store a hash map of primitive key/values"
  [oid tango-runtime]
  (let [either (rt/create-tango-object
                tango-runtime
                {:oid oid
                 :nullary-value {}
                 :apply (fn [prev-state log-entry]
                          (let [v (:value log-entry)
                                t (:type v)]
                            (case t
                              :assoc (assoc prev-state
                                            (get-in v [:data :key])
                                            (get-in v [:data :value]))
                              :dissoc (dissoc prev-state
                                              (get-in v [:data :key])))))})]
    (either/succeed-or-throw-error either)))

(defn get-hash-map
  "Get the whole map"
  [tango-map tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-map))
  ((:get-current-state tango-map)))

(defn get
  "Get the value for the given key"
  [tango-map k tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-map))
  (clojure.core/get ((:get-current-state tango-map)) k))

(defn assoc
  "Add or update a value for the given key"
  [tango-map k v tango-runtime]
  (rt/update-helper tango-runtime (:oid tango-map)
                    {:type :assoc
                     :data {:key k :value v}}))

(defn dissoc
  "Remove a given key from the map"
  [tango-map k tango-runtime]
  (rt/update-helper tango-runtime (:oid tango-map)
                    {:type :dissoc
                     :data {:key k}}))
