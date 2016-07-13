(ns tango.object.register
  (:require [tango.util.either :as either]
            [tango.runtime.core :as c]
            [tango.runtime :as rt]))

(defn tango-register
  "Create a Tango Register object.

  Used to store a single primitive data value."
  [oid tango-runtime]
  (let [either (rt/create-tango-object tango-runtime
                                       {:oid oid
                                        :nullary-value nil
                                        :apply (fn [prev-state log-entry]
                                                 (:value log-entry))})]
    (either/succeed-or-throw-error either)))

(defn set
  [tango-register val tango-runtime]
  (c/update-helper tango-runtime (:oid tango-register) val))

(defn get
  [tango-register tango-runtime]
  (c/query-helper tango-runtime (:oid tango-register))
  (c/get-current-state tango-runtime tango-register))

