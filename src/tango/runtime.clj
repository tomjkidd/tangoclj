(ns tango.runtime
  (:require [tango.log :as log]
            [tango.log.atom :as atom-log]
            [tango.util :as util]
            [tango.util.either :as either]
            [tango.transaction-runtime :as trans]
            [tango.runtime.core :as core]))

; TODO: Add an optional position argument to query-helper that will tell the reader a point at which to stop syncing
; TODO: Add a checkpoint function to allow which will provide query-helper with a position of the checkpoint to make reads faster
; TODO: Add a forget function that allows entries before a checkpoint to be forgotten. NOTE: this means giving up previous history verisons
; TODO: Add speculative commit logs to support transactions. These logs create a point at which the changes that occur in a transaction can be made available to all clients
; TODO: Implement transactions for optimistic concurrency control.

(defn in-memory-log
  "Create an in-memory log to be used with the Tango Runtime"
  []
  (atom-log/log-record))

(defn tango-runtime
  "Create a tango runtime given a shared log

:log The shared log

:next-position An index to the next log entry to read, safe to assume that reads are current up until this

:object-registry A string to list of TangoObject map that the runtime uses to apply updates to registered Tango Objects.

:version-map An oid to position map, where position is the location of the most
recently applied write.

:transaction-oid-mappings :: HashMap <TransactionHash, HashMap<Oid, ObjectHash>>
This is necessary because each registered Tango Object can be used in any transaction
runtime that is created.

When a registered Tango Object is passed to a transaction runtime, the runtime finds
out it has to pay attention to the oid of the Tango Object, and this means using the
oid->clone function to add the object to the transaction runtime's :object-registry
will need to know which object it is working with.
"
  [log]
  (atom {:log log
         :next-position 0
         :object-registry {}
         :version-map {}
         :transaction-mappings {}}))

(defn get-current-state
  [runtime-atom tango-object]
  ((:get-current-state tango-object)))

(defn update-helper
  "Write an entry to the log, targeting oid with an opaque value"
  [runtime oid opaque]
  (let [l (:log @runtime)]
    (log/append l {:type :write
                   :oid oid
                   :value opaque
                   :speculative false})))

(defn- update-version-map
  [runtime-atom oid position]
  (swap! runtime-atom (fn [prev]
                        (assoc-in prev [:version-map oid] position))))
(defn- apply-write
  "Apply a :write entry to a tango-object."
  [entry tango-object]
  (let [apply-fn (:apply tango-object)
        value-atom (:value tango-object)
        prev-state @value-atom]
    (swap! value-atom (fn [prev-state]
                        (apply-fn prev-state entry)))))

(defn- apply-writes
  "Takes a list of tango-objects (from the registry) and applies a :write entry to 
them in sequence."
  [tango-objects entry]
  (doall (map (partial apply-write entry) tango-objects)))

(defn- apply-commit
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

(defn query-helper
  "Reads as far forward in the log as possible for the current state of the log (based on log tail).

Only registered TangoObjects will see the log entry values

For each entry, the :oid is used to find interested TangoObjects from the :object-registry, and then each TengoObject's :apply method is called with the entry's :value."
  [runtime oid]
  (let [deref-rt @runtime
        l (:log deref-rt)
        p (:next-position deref-rt)
        tail (log/tail l)
        log-ready? (not (nil? tail))]
    (when log-ready?
      (loop [position p runtime runtime]
        (when (<= position tail)
          (let [rt @runtime
                either-entry (log/read l position)
                entry (:right either-entry)
                oid (:oid entry)
                type (:type entry)
                val (:value entry)
                registry (:object-registry rt)
                regs (registry oid)
                next-position (inc position)]
            (case type
              :write (if (not (:speculative entry))
                       (do
                         (apply-writes regs entry)
                         (update-version-map runtime oid position))
                       (do
                         (println "TODO: Need to buffer speculative writes!")))
              :commit (if (core/validate-commit l (get-in entry [:data :reads]) position)
                        (apply-commit runtime registry entry l)
                        (println "TODO: Need to handle runtime abort!")))
            
            (swap! runtime (fn [prev] (assoc prev :next-position next-position)))
            (recur next-position runtime)))))))

(defn register-tango-object
  "Register a Tango Object with the given runtime to pay attention to log updates for
a given oid. Returns a function to ask for the current state, which should be used
by a Tango Object in order to ask for a read from the runtime.

Assumes that objects only care about new reads from the point they enter the system 
at."
  [runtime oid tango-object]
  (let [regs (:object-registry @runtime)
        value-atom (atom (:nullary-value tango-object))
        get-current-state-fn (fn [] @value-atom)
        reg-obj (-> (assoc tango-object :value value-atom)
                    (assoc :get-current-state get-current-state-fn))
        new-regs (if (nil? (regs oid))
                   (assoc regs oid [reg-obj])
                   (assoc regs oid (conj (regs oid) reg-obj)))]
    ; Update the registry
    (swap! runtime (fn [prev] (assoc prev :object-registry new-regs)))
    
    ; Return object to caller, don't expose the atom!
    (dissoc reg-obj :value)))

(defn create-tango-object
  "Use a map with :oid, :nullary-value, and :apply keys to create a registered Tango
Object"
  [runtime tango-object-map]
  (let [runtime (:atom runtime)
        expected [:oid :nullary-value :apply]]
    (if (util/contains-expected-keys? tango-object-map expected)
      (either/success
       (register-tango-object runtime
                              (:oid tango-object-map)
                              tango-object-map))
      (either/error
       (str "tango.runtime/create-tango-object:"
            "CreateTangoObjectError:"
            "You need to provide the keys "
            (str expected)
            " to create a Tango Object.")))))

(defn in-memory-runtime
  []
  (tango-runtime (in-memory-log)))

(defrecord TangoRuntime [atom]
  core/ITangoRuntime
  (update-helper [this oid opaque]
    (update-helper (:atom this) oid opaque))
  (query-helper [this oid]
    (query-helper (:atom this) oid))
  (get-current-state [this tango-object]
    (get-current-state (:atom this) tango-object)))

(defn runtime
  "Create a TangRuntime record conveniently"
  []
  (TangoRuntime. (in-memory-runtime)))

(defn- clone-registry-object
  "Take a snapshot of a single Tango Object (from the registry)

This dereferences the nested atoms and creates new ones"
  [tango-object]
  (let [current-value @(:value tango-object)
        value-atom (atom current-value)
        get-current-state-fn (fn [] @value-atom)
        clone (-> (assoc tango-object :value value-atom)
                  (assoc :get-current-state get-current-state-fn)
                  (assoc :cloned-from tango-object))]
    clone))

(defn- tango-object-hash
  "Because the tango-object will evolve over time as new values are applied,
we have to use something that will not change, the :apply function"
  [tango-object]
  (hash (:apply tango-object)))

(defn take-full-object-registry-snapshot
  "Take an isolated snapshot of the whole runtime object registry."
  [runtime-snapshot]
  (let [registry (:object-registry runtime-snapshot)
        clone-mappings (atom {})
        clone-registry-pairs 
        (fn [[oid tango-objects]]
          [oid (doall
                (map (fn [t]
                       (let [clone (clone-registry-object t)]
                         (swap! clone-mappings (fn [prev]
                                                 (assoc prev (tango-object-hash t) clone)))
                         clone)) tango-objects))])
        cloned-registry (into {} (doall (map clone-registry-pairs registry)))]
    {:clone-mappings @clone-mappings
     :cloned-registry cloned-registry}))

; NOTE: This implementation was not completed, went a different route for now
(defn get-clone-registry-objects-fn
  "Provides a function to create clones of all of the TangoObjects that are known to a given runtime

These clones will serve as snapshots of the current state of the runtime"
  [runtime]
  (fn [oid]
    (let [registry (:object-registry @runtime)
          regs (registry oid)
          clones (doall (map clone-registry-object regs))]
      clones)))

(defn begin-transaction
  [runtime-record]
  (query-helper (:atom runtime-record) :tango-runtime)
  (let [runtime @(:atom runtime-record)
        {:keys [clone-mappings cloned-registry]} (take-full-object-registry-snapshot runtime)
        transaction-id (util/uuid)]
    (-> (trans/tango-transaction-runtime
         transaction-id
         (:log runtime)
         (:next-position runtime)
         (fn [oid]
           (cloned-registry oid))
         (fn [tango-object]
           (clone-mappings (tango-object-hash tango-object)))
         (:version-map runtime))
        (trans/transaction-runtime))))

(defn validate-commit-legacy
  "Returns true if the commit entry does not have conflicts."
  [log entry version-map]
  (let [read-set (get-in entry [:data :reads])
        oids-of-interest (distinct (map #(:oid %) read-set))
        transaction-start (:position (first read-set))
        transaction-end (:position entry)
        transaction-window-entries (map #(:right (log/read log %)) (range transaction-start (inc transaction-end)))]
    #break (println (count transaction-window-entries))
    false))
