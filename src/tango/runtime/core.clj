(ns tango.runtime.core
  (:require [tango.log :as log]))

(defprotocol ITangoRuntime
  (update-helper [this oid opaque]
    "Append an entry to the shared log")
  (query-helper [this oid]
    "Read as far forward in the log as possible")
  (get-current-state [this tango-object]
    "Get the current in-memory state stored for the given tango-object"))

(defn create-first-read-position-map
  "Create a map from oid to first position in the read-set"
  [read-set]
  (reduce (fn [acc {:keys [oid position]}]
            (if (nil? (acc oid))
              (assoc acc oid position)
              acc))
          {}
          read-set))

(defn validate-write-set
  [rs-map position write-set]
  (let [write-set-oids (distinct (map #(:oid %) write-set))]
    (reduce (fn [acc cur]
              (if (not acc)
                acc
                (if (< (rs-map cur) position)
                  false
                  true)))
            true
            write-set-oids)))

(defn validate-commit
  "The standard procedure to use to validate a commit against the shared log.

NOTE: loop-state atom was necesary in order to satisfy tail call position."
  [log read-set end-position]
  (if (empty? read-set)
    true
    (let [start-pos (:position (first read-set))
          end-pos end-position
          rs-map (create-first-read-position-map read-set)]
      (loop [position start-pos]
        (if (< position end-pos)
          (let [entry (:right (log/read log position))
                type (:type entry)
                loop-state (atom {:terminate false :result false})]
            (case type
              :write
              (let [oid (:oid entry)
                    first-read-pos (rs-map oid)]
                (if (and (not (:speculative entry))
                         (not (nil? first-read-pos))
                         (< first-read-pos position))
                  (swap! loop-state (fn [prev]
                                      {:terminate true
                                       :result false}))
                  (swap! loop-state (fn [{:keys [result]}]
                                      {:terminate false
                                       :result result}))))
              :commit
              (let [read-set (get-in entry [:data :reads])
                    write-set (get-in entry [:data :writes])]
                (if (validate-commit log read-set position)
                  (do
                    (swap! loop-state (fn [prev]
                                        {:terminate true
                                         :result (validate-write-set rs-map position write-set)})))
                  (swap! loop-state (fn [{:keys [result]}]
                                      {:terminate false
                                       :result result})))))
            (let [ls @loop-state]
              (if (:terminate ls)
                (:result ls)
                (recur (inc position)))))
          true)))))

(defn update-version-map
  [runtime-atom oid position]
  (swap! runtime-atom (fn [prev]
                        (assoc-in prev [:version-map oid] position))))

(defn apply-write
  "Apply a :write entry to a tango-object."
  [entry tango-object]
  (let [apply-fn (:apply tango-object)
        value-atom (:value tango-object)
        prev-state @value-atom]
    (swap! value-atom (fn [prev-state]
                        (apply-fn prev-state entry)))))

(defn apply-writes
  "Takes a list of tango-objects (from the registry) and applies a :write entry to 
them in sequence."
  [tango-objects entry]
  (doall (map (partial apply-write entry) tango-objects)))

(defn apply-commit
  "Apply a commit entry to the relevant tango-objects in the object-registry."
  [runtime registry commit-entry log]
  ; TODO: Maybe validate commit entry with the log?
  (let [write-set (get-in commit-entry [:data :writes])
        write-entries (doall (map (fn [{:keys [position]}]
                                    (:right (log/read log position)))
                                  write-set))]
    (doall (map (fn [entry]
                  (let [oid (:oid entry)
                        tango-objects (registry oid)
                        position (:position commit-entry)]
                    (apply-writes tango-objects entry)
                    (update-version-map runtime oid position))) write-entries))))
