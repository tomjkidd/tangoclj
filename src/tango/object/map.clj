(ns tango.object.map
  (:require [tango.runtime :as rt]))

(defn- get-apply-fn
  "Returns a function that defines how the map operations act on the map atom"
  [map-atom]
  (fn [log-entry]
    (swap! map-atom 
           (fn [prev]
             (let [v (:value log-entry)
                   t (:type v)]
               (case t
                 :assoc (assoc prev 
                               (get-in v [:data :key]) 
                               (get-in v [:data :value]))
                 :dissoc (dissoc prev 
                                 (get-in v [:data :key]))))))))

(defn tango-map
  "Create a Tango Map object.

  Used to store a hash map of primitive key/values"
  [oid]
  (let [a (atom {})]
    {:oid oid
     :value a
     :apply (get-apply-fn a)}))

(defn get
  "Get the value for the given key"
  [tango-map k tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-map))
  (clojure.core/get @(:value tango-map) k))

(defn assoc
  "Add or update a value for the given key"
  [tango-map k v tango-runtime]
  (let [prev @(:value tango-map)
        new (clojure.core/assoc prev k v)]
    (rt/update-helper tango-runtime (:oid tango-map)
                      {:type :assoc
                       :data {:key k :value v}})))

(defn dissoc
  "Remove a given key from the map"
  [tango-map k tango-runtime]
  (let [prev @(:value tango-map)
        new (clojure.core/dissoc prev k)]
    (rt/update-helper tango-runtime (:oid tango-map)
                      {:type :dissoc
                       :data {:key k}})))
