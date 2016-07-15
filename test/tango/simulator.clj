(ns tango.simulator
  (:require [tango.util.either :as either]
            [tango.object.register :as r]
            [tango.runtime :as rt]
            [tango.transaction-runtime :as trt]
            [tango.log :as l]
            [clojure.test :as t]))

(defrecord Command [oid runtime-id register-id action value])

(defn trace->Command
  "Convert a trace to a Command record"
  [trace]
  (apply ->Command trace))

(defn trace-list->Command-list
  [trace-list]
  (map trace->Command trace-list))

(defn- add-new-runtime
  "Create a new transaction runtime"
  [runtime-id]
  (fn [prev]
    (let [rt (:main-runtime prev)
          trt (rt/begin-transaction rt)]
      (assoc-in prev [:transaction-runtimes runtime-id] trt))))

(defn- add-new-register
  "Create a new Tango Register"
  [oid register-id]
  (fn [prev]
    (let [rt (:main-runtime prev)
          r (r/tango-register oid rt)]
      (assoc-in prev [:tango-registers register-id] r))))

(defn- get-runtime
  "Get the runtime associated with the given id. main id is special."
  [state runtime-id]
  (if (= runtime-id "main")
    (:main-runtime state)
    (get-in state [:transaction-runtimes runtime-id])))

(defn simulate-command
  "Simulate a single command"
  [state-atom]
  (fn [{:keys [oid runtime-id register-id action value]}]
    (let [state @state-atom
          needs-new-runtime? (and (not= runtime-id "main")
                                      (nil?
                                       (get-in state
                                               [:transaction-runtimes runtime-id])))
          needs-new-register? (nil? (get-in state [:tango-registers register-id]))]
      (when needs-new-runtime?
        (swap! state-atom (add-new-runtime runtime-id)))
      (when needs-new-register?
        (swap! state-atom (add-new-register oid register-id)))

      (let [state @state-atom
            rt (get-runtime state runtime-id)
            r (get-in state [:tango-registers register-id])
            result (case action
                     :read (r/get r rt)
                     :write (r/set r value rt)
                     :commit (trt/commit-transaction rt))]
        (case action
          :read (if (= value result)
                  (either/success
                   (format "Read the value %1$s from %2$s in runtime '%3$s'"
                           result
                           register-id
                           runtime-id))
                  (either/error
                   (format "Expected to read %1$s from %2$s in runtime '%3$s'. Actually read %4$s."
                           value
                           register-id
                           runtime-id
                           result)))
          :write (either/success
                  (format "Wrote the value %1$s to %2$s in runtime '%3$s'"
                          value
                          register-id
                          runtime-id))
          :commit (if (= value result)
                    (either/success
                     (format "Wrote a commit and %2$s returned %1$s"
                             value
                             runtime-id))
                    (either/error
                     (format "Wrote a commit and %2$s did not return %1$s"
                             value
                             runtime-id))))))))

(defn simulate
  "Simulate a list of commands in a tango runtime"
  [cmds]
  (let [rt (rt/runtime)
        state (atom {:main-runtime rt
                     :transaction-runtimes {}
                     :tango-registers {}})
        results (doall (map (simulate-command state) cmds))]
    {:results results :state @state}))

(def simple-write
  "Represents the simplest use case of a Tango Register
Write a value, and then read to make sure that vaue was set."
  [["abc" "main" "reg1" :write 100]
   ["abc" "main" "reg1" :read 100]])

(def transaction-isolation
  "Represents a case where writes in a transaction don't affect others"
  [["abc" "main" "reg1" :write 0]
   ["abc" "tx-1" "reg1" :read 0]
   ["abc" "tx-1" "reg1" :write 100]
   ["abc" "main" "reg1" :read 0]
   ["abc" "tx-1" "reg1" :read 100]
   [nil "tx-1" nil :commit true]
   ["abc" "main" "reg1" :read 100]])

(def transaction-abort
  "Represents a case where a transaction is aborted"
  [["abc" "main" "reg1" :write 0]
   ["abc" "tx-1" "reg1" :read 0]
   ["abc" "tx-1" "reg1" :write 100]
   ["abc" "main" "reg1" :write 200]
   ["abc" "main" "reg1" :read 200]
   ["abc" "tx-1" "reg1" :read 100]
   [nil "tx-1" nil :commit false]
   ["abc" "main" "reg1" :read 200]])

(defn see-reads-and-writes
  "A tool to look at results conveniently"
  [commands transaction-runtime-id]
  (let [result (simulate (cmd-list->Command-list commands))
        rt (get-in result [:state :main-runtime])
        rt-atom (:atom rt)
        trt (get-in
             result
             [:state :transaction-runtimes transaction-runtime-id])
        trt-atom (:atom trt)
        log (get-in @trt-atom [:log])
        version-map (get-in @trt-atom [:version-map])]
    (print (get-in @rt-atom [:version-map]))
    (select-keys
     @trt-atom
     [:version-map :log :next-position :reads :writes :out-of-tx-writes])))

(defn run-commands
  [commands]
  (let [{:keys [results state]} (-> (cmd-list->Command-list commands)
                                    (simulate))]
    (if (every? identity (doall (map (fn [either]
                                       (let [e (either/has-error? either)
                                             passed (not e)]
                                         (if e
                                           (println "Error:" (:left either))
                                           (println "Success:" (:right either)))
                                         passed))
                                     results)))
      true
      state)))

