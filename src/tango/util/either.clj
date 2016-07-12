(ns tango.util.either)

(defprotocol Result
  (has-error? [this])
  (succeed-or-fn [this f]))

(defrecord EitherSuccess [right]
  Result
  (has-error? [s] false)
  (succeed-or-fn [e f] (:right e)))

(defrecord EitherError [left]
  Result
  (has-error? [s] true)
  (succeed-or-fn [e f] (f (:left e))))

(defn success
  [x]
  (EitherSuccess. x))

(defn error
  [err]
  (EitherError. err))

(defn succeed-or-print-error
  "At least show when an error occurs"
  [either]
  (let [log-error (fn [left]
                    (println "Error Detected:")
                    (println left))]
    (succeed-or-fn either log-error)))

(defn succeed-or-throw-error
  "Take an either and throw an exception in the absense of a value."
  [either]
  (let [throw-error (fn [left]
                      (throw (Exception. (str left))))]
    (succeed-or-fn either throw-error)))
