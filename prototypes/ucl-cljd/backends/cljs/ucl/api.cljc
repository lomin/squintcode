(ns ucl.api
  "SPIKE -- ClojureScript backend, macro half."
  (:refer-clojure :exclude [make-array])
  (:require [ucl.contract :as contract]))

(def emit
  {:indexed {:read  (fn [[a i] _] (list (quote aget) a i))
           :write (fn [[a i] _ v] (list (quote aset) a i v))}
 :vector  {:make (fn [n element init]
                   (let [ctor ({:t (quote js/Array) :fixnum (quote js/Int32Array) :sb53 (quote js/Float64Array)} element)]
                     (if (some? init)
                       (list (quote .fill) (list (quote new) ctor n) init)
                       (list (quote new) ctor n))))}
 :types   {:hint {}}})

(contract/defapi emit)
