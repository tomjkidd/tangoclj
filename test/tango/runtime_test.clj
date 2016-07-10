(ns tango.runtime-test
  (:require [tango.runtime :as rt]
            [tango.object.register :as reg]
            [clojure.test :as t]))

(def rt (rt/tango-runtime (rt/in-memory-log)))
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

(def test-tango-register
  (reg/tango-register "tom" 0))

(rt/register-tango-object rt "tom" test-tango-register)
(reg/set test-tango-register 1 rt)
(reg/get test-tango-register rt)
(reg/set test-tango-register 7 rt)
(reg/get test-tango-register rt)
