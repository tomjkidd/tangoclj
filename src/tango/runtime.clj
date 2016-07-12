(ns tango.runtime
  (:require [tango.log :as log]
            [tango.log.atom :as atom-log]
            [tango.util.either :as either]))

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

:object-registry A string to list of TangoObject map that the runtime uses to apply updates to registered Tango Objects."
  [log]
  (atom {:log log
         :next-position 0
         :object-registry {}}))

(defn update-helper
  "Write an entry to the log, targeting oid with an opaque value"
  [runtime oid opaque]
  (let [l (:log @runtime)]
    (log/append l {:oid oid :value opaque})))

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
          (let [either-entry (log/read l position)
                entry (:right either-entry)
                oid (:oid entry)
                val (:value entry)
                registry (:object-registry @runtime)
                regs (registry oid)
                next-position (inc position)]
            (doall (map (fn [reg-obj]
                          (let [apply-fn (:apply reg-obj)
                                value-atom (:value reg-obj)
                                prev-state @value-atom]
                            (swap! value-atom (fn [prev-state]
                                                (apply-fn prev-state entry)))))
                        regs))
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
  (let [expected [:oid :nullary-value :apply]
        valid? (every? #(contains? tango-object-map %) expected)]
    (if valid?
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
