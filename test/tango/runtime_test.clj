(ns tango.runtime-test
  (:require [tango.log.atom]
            [tango.runtime :as rt]
            [clojure.test :as t]))

(def rt (rt/tango-runtime (tango.log.atom/log)))
(rt/register-tango-object rt "abc" {:apply (fn [val] (println (str "Value:" val)))})

(def tango-atom
  (atom '()))

(def test-tango-atom
  (let [apply-fn (fn [val]
                   (swap! tango-atom (fn [prev]
                                       (cons val prev))))]
    {:apply apply-fn}))

(rt/register-tango-object rt "abc" test-tango-atom)
(rt/update-helper rt "abc" "def")
(rt/update-helper rt "abc" "ghi")
(rt/query-helper rt "abc")

