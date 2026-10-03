(ns ucl.api
  "ucl on ClojureScript, run-time half. Requiring its own macros is what lets a
   client write one plain (:require [ucl.api :as ucl])."
  (:refer-clojure :exclude [make-array min max defmethod let dotimes loop
                            count find some reduce reverse sort replace subseq remove])
  (:require-macros [ucl.api]
                   [ucl.contract :as contract]
                   [ucl.js-emit :as js-emit]))

(js-emit/defhelpers true)

(deftype Cell [^:mutable v])   ; a variable's storage (D30): ClojureScript cannot assign a local

(defn hash-key
  "Normalize a hash key so `eql` keys stay `eql` (D12, H14)."
  [k]
  (if (keyword? k) (.-fqn k) k))

(contract/defruntime
  {:positive-infinity js/Infinity
   :negative-infinity (- js/Infinity)})
