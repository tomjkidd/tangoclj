(ns tango.util)

;; TODO: Find a way that supports both clojure and clojurescript
(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn either-value-or-throw-error
  "Take an either and throw an exception in the absense of a value."
  [{:keys [left right]}]
  (if (nil? right)
    (throw (Exception. left))
    right))
