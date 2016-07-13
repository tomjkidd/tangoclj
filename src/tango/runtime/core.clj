(ns tango.runtime.core)

(defprotocol ITangoRuntime
  (update-helper [this oid opaque]
    "Append an entry to the shared log")
  (query-helper [this oid]
    "Read as far forward in the log as possible")
  (get-current-state [this tango-object]
    "Get the current in-memory state stored for the given tango-object"))
