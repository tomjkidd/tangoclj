(ns tango.util.either)

(defn right-or-fn
  "Take an either and if :right, unwrap value else call f with :left"
  [{:keys [left right]} f]
  (if (nil? right)
    (f left)
    right))

(defn right-or-print-error
  "At least show when an error occurs"
  [either]
  (let [log-error (fn [left]
                    (println "Error Detected:")
                    (println left))]
    (right-or-fn either log-error)))

(defn right-or-throw-error
  "Take an either and throw an exception in the absense of a value."
  [either]
  (let [throw-error (fn [left]
                      (throw (Exception. (str left))))]
    (right-or-fn either throw-error)))
