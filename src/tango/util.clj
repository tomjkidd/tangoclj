(ns tango.util)
; This namespace is for convenience functions, should be kept small to prove things
; out.

;; TODO: Find a way that supports both clojure and clojurescript
(defn uuid [] (str (java.util.UUID/randomUUID)))
