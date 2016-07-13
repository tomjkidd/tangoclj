(ns tango.simulator
  (:require [tango.util.either :as either]
            [tango.object.register :as r]
            [tango.runtime :as rt]
            [tango.transaction-runtime :as trt]
            [clojure.test :as t]))

(defrecord Command [oid runtime-id register-id action value])

(defn cmd->Command
  "Convert a list of args to a Command record"
  [cmd]
  (apply ->Command cmd))

(defn cmd-list->Command-list
  [cmd-list]
  (map cmd->Command cmd-list))

(defn simulate-command
  "Simulate a single command"
  [state-atom]
  (fn [{:keys [oid runtime-id register-id action value]}]
    (let [state @state-atom]
      (when (and (not= runtime-id "main")
                 (nil? (get-in state [:transaction-runtimes runtime-id])))
        (swap! state-atom (fn [prev]
                       (let [rt (:main-runtime prev)
                             trt (rt/begin-transaction rt)]
                         (assoc-in prev [:transaction-runtimes runtime-id] trt)))))
      (when (nil? (get-in state [:tango-registers register-id]))
        (swap! state-atom (fn [prev]
                            (let [rt (:main-runtime prev)
                                  r (r/tango-register oid rt)]
                              (assoc-in prev [:tango-registers register-id] r)))))

      (let [state @state-atom
            rt (if (= runtime-id "main")
                 (:main-runtime state)
                 (get-in state [:transaction-runtimes runtime-id]))
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
                   (format "Tried and failed to read %1$s from %2$s in runtime '%3$s'"
                           value
                           register-id
                           runtime-id)))
          :write (either/success
                  (format "Wrote the value %1$s to %2$s in runtime '%3$s'"
                          value
                          register-id
                          runtime-id))
          :commit (if (= value result)
                    (either/success
                     (format "Wrote a commit and %2%s returned %1$s"
                             value
                             runtime-id))
                    (either/error
                     (format "Wrote a commit and %2%s did not return %1$s"
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

(def simple-write-cmds
  (cmd-list->Command-list simple-write))

(def transaction-isolation
  "Represents a case where writes in a transaction don't affect others"
  [["abc" "main" "reg1" :write 0]
   ["abc" "tx-1" "reg1" :read 0]
   ["abc" "tx-1" "reg1" :write 100]
   ["abc" "main" "reg1" :read 0]
   ["abc" "tx-1" "reg1" :read 100]
   [nil "tx-1" nil :commit true]
   ["abc" "main" "reg1" :read 100]
   ;["abc" "tx-1" "reg1" :write 200]
   ])

(def transaction-isolation-cmds
  (cmd-list->Command-list transaction-isolation))

(defn see-reads-and-writes
  [commands transaction-runtime-id]
  (let [trt (get-in
             (simulate commands) 
             [:state :transaction-runtimes transaction-runtime-id])
        trt-atom (:atom trt)]
    
    (select-keys
     @trt-atom
     [:next-position :reads :writes :out-of-tx-writes])
    trt-atom))
