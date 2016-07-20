(ns tango.transaction-runtime
  (:require [tango.log :as log]
            [tango.log.atom :as atom-log]
            [tango.runtime.core :as core]))

(defn tango-transaction-runtime
  "Creates a transaction runtime for a shared log

transaction-id is the id to use to identify a specific transaction

log is the shared log to use

next-position is the position in the log the transaction started from

:object-registry is a hash-map of oid keys to a list of cloned TangoObjects from a 
tango-runtime.
The :object-registry is initially empty. The object-registry is potentially populated
each time query-helper is called with a new Tango Object because now it knows that 
it needs clones of objects that are paying attention to a given oid to apply the 
speculative writes against.

The oid->clones function is used to get these clones from the main tango-runtime so 
that the transaction doesn't have to know how this is accomplished.
In the future, different isolation modes may be desired, so this should prevent the
transaction-runtime from knowing too much about how the clones are retreived.

The tango-object->clone-object function is needed to allow :get-current-state to find
the correct clone that corresponds to a tango object the client is attempting to 
query. The clone is not exposed to the client at all, they continue to pass the 
original tango-object they created to whichever runtime they want, and the runtimes 
keep it straight on the clients behalf.

A tango-object has the following fields
{ :oid, :nullary-value, :apply, :value, :get-current-state }
NOTE: :apply and :get-current-state are functions
NOTE: :value is a clojure Atom!!

The version-map is passed in to provide the transaction runtime with a map for each
oid to it's most recent applied write.
NOTE: For transactions, speculative writes will appear to have been written at the
commit entry in the log.

More about the transaction runtime:

:reads is a chronological list of the (:oid,:position)s where reads are performed in 
the transaction
:writes is a chronological list of the (:oid,:position)s where writes are performed in
the transaction"
  [transaction-id log next-position oid->clones tango-object->clone-object version-map]
  (atom {:id transaction-id
         :log log
         :starting-next-position next-position
         :next-position next-position
         :commit-position nil
         :oid->clones oid->clones
         :tango-object->clone-object tango-object->clone-object
         :object-registry {}
         :version-map version-map
         :reads []
         :writes []
         :out-of-band-writes []
         :out-of-tx-writes []}))

(defn apply-entry
  [entry]
  (fn [reg-obj]
    (let [apply-fn (:apply reg-obj)
          value-atom (:value reg-obj)
          prev-state @value-atom]
      (swap! value-atom (fn [prev-state]
                          (apply-fn prev-state entry))))))

(defn apply-entry-to-tango-objects
  [entry tango-objects]
  (doall (map (apply-entry entry) tango-objects)))

(defn- apply-out-of-band
  [runtime oid clones]
  (let [t oid
        all-oob (:out-of-band-writes runtime)
        entries (filter #(= oid (:oid %)) all-oob)]
    (map #(apply-entry-to-tango-objects % clones) entries)
    clones))

(defn- cache-clones
  "Use the oid->clones function to get the Tango Objects and put them into
the object registry."
  [runtime-atom oid]
  (let [runtime @runtime-atom
        old-registry (:object-registry runtime)
        clones ((:oid->clones runtime) oid)
        up-to-date-clones (apply-out-of-band runtime oid clones)
        new-registry (assoc old-registry oid up-to-date-clones)]
    (swap! runtime-atom (fn [prev]
                          (assoc prev :object-registry new-registry)))
    up-to-date-clones))

(defn get-current-state
  "Use the tango-object->clone-object function to get the clone that a given Tango
Object corresponds to."
  [runtime-atom tango-object]
  (let [lookup (:tango-object->clone-object @runtime-atom)
        clone (lookup tango-object)]
    ((:get-current-state clone))))

(defn update-helper
  "Write a speculative entry to the log, targeting oid with an opaque value

When a write is performed, it is added to the :writes of the transaction runtime"
  [runtime oid opaque]
  (let [{:keys [log commit-position]} @runtime]
    (when (not (nil? commit-position))
      (throw (Exception. "Transaction committed already, can't perform more writes")))
    (let [entry (log/create-write-entry oid opaque true) 
          position (log/append log entry)]
      (swap! runtime (fn [prev]
                       (let [old-writes (:writes prev)
                             w {:oid oid :position position}
                             new-writes (conj old-writes w)]
                         (assoc prev :writes new-writes))))
      position)))

(defn query-helper
  "Reads as far forward in the log as possible for the current state of the log (based on log tail).

who can be :client for when the protocol uses it, and :transaction-runtime when the
transaction runtime is trying to commit.

The oid is used to load the clones that will be necessary by the runtime to apply
isolated changes."
  ([runtime oid]
   (let [rt @runtime
         position (:next-position rt)]
     (query-helper runtime oid position)))

  ([runtime oid position]
   (let [rt @runtime
         l (:log rt)
         write-set (:writes rt)
         p position
         tail (log/tail l)
         log-ready? (not (nil? tail))]
     (when (nil? ((:object-registry rt) oid))
       ; If this is the first time oid is used, cache clones
       (cache-clones runtime oid))
     (when log-ready?
       (let [old-reads (:reads rt)
             new-read {:oid oid :position tail}
             new-reads (conj old-reads new-read)]
         (swap! runtime (fn [prev] (assoc prev :reads new-reads))))
       
       (loop [position p runtime runtime]
         (when (<= position tail)
           (let [rt @runtime
                 either-entry (log/read l position)
                 entry (:right either-entry)
                 entry-type (:type entry)
                 entry-oid (:oid entry)
                 registry (:object-registry rt)
                 regs (registry oid)
                 next-position (inc position)]
             
             (case entry-type
               :write (if (:speculative entry)
                        (if (some #(= position (:position %)) write-set)
                          (core/apply-writes regs entry)
                          
                          (let [old-writes (:out-of-tx-writes rt)
                                new-write {:oid entry-oid :position position}
                                new-writes (conj old-writes new-write)]
                            (swap! runtime (fn [prev]
                                             (assoc prev :out-of-tx-writes new-writes)))))
                        (do
                          (println "TODO: Update the version-map to follow what the main runtime is doing.")
                          (core/apply-writes (registry entry-oid) entry)
                          (let [old-oob (:out-of-band-writes rt)
                                new-oob (conj old-oob entry)]
                            (swap! runtime (fn [prev]
                                             (assoc prev :out-of-band-writes new-oob))))))
               :commit (do                       
                         (println "TODO: Need to handle transaction runtime commit decisions!")))
              
             (swap! runtime (fn [prev] (assoc prev :next-position next-position)))
             (recur next-position runtime))))))))

(defrecord TransactionTangoRuntime [atom]
  core/ITangoRuntime
  (update-helper [this oid opaque]
    (update-helper (:atom this) oid opaque))
  (query-helper [this oid]
    (query-helper (:atom this) oid))
  (get-current-state [this tango-object]
    (get-current-state (:atom this) tango-object)))

(defn transaction-runtime
  "Create a TransactionTangoRuntime conveniently"
  [runtime-atom]
  (TransactionTangoRuntime. runtime-atom))

(defn create-commit-entry
  [runtime-id reads-and-writes]
  {:type :commit
   :data reads-and-writes
   :transaction-runtime-id runtime-id})

(defn append-commit-entry
  [log entry]
  (log/append log entry))

(defn commit-transaction
  "Attempt to commit a transaction. Returns true if able, false otherwise.

NOTE: Read-only transactions will not actually create a commit entry."
  [runtime]
  ; Write a commit entry to the log then
  ; Read through the commit position to collect all changes
  (let [rt @(:atom runtime)
        {:keys [log id]} rt
        {:keys [reads writes]} rt
        read-only (empty? writes)
        commit-position (if read-only
                          (:position (last reads))
                          (let [commit-entry (create-commit-entry id (select-keys rt [:reads :writes]))
                                commit-position (append-commit-entry log commit-entry)]
                            commit-position))
        read-set (if read-only
                   reads
                   (let [e (:right (log/read log commit-position))]
                     (get-in e [:data :reads])))]
    (when (not read-only)
      (swap! (:atom runtime) (fn [prev]
                               (assoc prev :commit-position commit-position))))
    
    (query-helper (:atom runtime) :transaction-runtime commit-position)

    (core/validate-commit log read-set commit-position)))
