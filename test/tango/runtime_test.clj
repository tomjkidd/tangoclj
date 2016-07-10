(ns tango.runtime-test
  (:require [tango.runtime :as rt]
            [tango.object.register :as reg]
            [tango.object.map :as tmap]
            [clojure.test :as t]))

(def rt (rt/tango-runtime (rt/in-memory-log)))

(rt/register-tango-object rt "bare-apply" {:apply
                                           (fn [val]
                                             (println (str "Value:" val)))})

(def test-atom (atom '()))
(def test-bare
  "A bare-bones Tango object just needs an apply funtion to call for updates"
  (let [apply-fn (fn [val]
                   (swap! test-atom (fn [prev]
                                      (cons val prev))))]
    {:apply apply-fn}))

(rt/register-tango-object rt "bare-apply" test-bare)
(rt/update-helper rt "bare-apply" "def")
(rt/update-helper rt "bare-apply" "ghi")
(rt/query-helper rt "bare-apply")

(def test-register
  (reg/tango-register "tango-register" 0))

(rt/register-tango-object rt "tango-register" test-register)
(reg/set test-register 1 rt)
(reg/get test-register rt)
(reg/set test-register 7 rt)
(reg/get test-register rt)

(def test-map
  (tmap/tango-map "tango-map"))
(rt/register-tango-object rt "tango-map" test-map)

(tmap/assoc test-map :a 1 rt)
(tmap/assoc test-map :b 3 rt)
(tmap/assoc test-map :c 4 rt)
(tmap/get test-map :a rt)
(tmap/dissoc test-map :a rt)
(tmap/get test-map :b rt)
