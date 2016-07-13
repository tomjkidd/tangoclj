(ns tango.util)
; This namespace is for convenience functions, should be kept small to prove things
; out.

;; TODO: Find a way that supports both clojure and clojurescript
(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn contains-expected-keys?
  "Ensure that the given list of keys is present in a hash-map"
  [m expected-keys]
  (every? #(contains? m %) expected-keys))
