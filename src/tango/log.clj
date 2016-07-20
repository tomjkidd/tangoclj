(ns tango.log)

(defprotocol SharedLog
  "An interface for a Shared Log"
  (append [this entry]
    "Add an entry to the end of the log")
  (read [this position]
    "Read an entry from the log at the given position")
  (tail [this]
    "Get the position of the current tail of the log")
  (trim [this position]
    "For the entry at the given position, set the entry's trimmed value"))

(defn create-entry
  [type oid value speculative]
  {:type type
   :oid oid
   :value value
   :speculative speculative})

(defn create-write-entry
  [oid value speculative]
  (create-entry :write oid value speculative))
