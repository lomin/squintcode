(ns ucl.api
  "ucl on ClojureScript, run-time half. Requiring its own macros is what lets a
   client write one plain (:require [ucl.api :as ucl])."
  (:refer-clojure :exclude [make-array min max defmethod])
  (:require-macros [ucl.api]
                   [ucl.contract :as contract]
                   [ucl.js-emit :as js-emit]))

(js-emit/defhelpers true)

(defn hash-key
  "Normalize a hash key so `eql` keys stay `eql` (D12, H14)."
  [k]
  (if (keyword? k) (.-fqn k) k))

(contract/defruntime
  {:positive-infinity js/Infinity
   :negative-infinity (- js/Infinity)})
