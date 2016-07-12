(ns tango.object.register
  (:require [tango.runtime :as rt]
            [tango.util.either :as either]))

(defn tango-register
  "Create a Tango Register object.

  Used to store a single primitive data value."
  [oid tango-runtime]
  (let [either (rt/create-tango-object tango-runtime
                                       {:oid oid
                                        :nullary-value nil
                                        :apply (fn [prev-state log-entry]
                                                 (:value log-entry))})]
    (either/right-or-throw-error either)))

(defn set
  [tango-register val tango-runtime]
  (rt/update-helper tango-runtime (:oid tango-register) val))

(defn get
  [tango-register tango-runtime]
  (rt/query-helper tango-runtime (:oid tango-register))
  ((:get-current-state tango-register)))

