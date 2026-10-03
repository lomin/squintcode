(ns ucl.api
  "ucl on ClojureScript, run-time half. Requiring its own macros is what lets a
   client write one plain (:require [ucl.api :as ucl])."
  (:refer-clojure :exclude [make-array min max defmethod let dotimes loop
                            count find some reduce reverse sort replace subseq remove
                            map merge])
  (:require-macros [ucl.api]
                   [ucl.contract :as contract]
                   [ucl.js-emit :as js-emit]))

(js-emit/defhelpers true)

(deftype Cell [^:mutable v])   ; a variable's storage (D30): ClojureScript cannot assign a local

(defn hash-key
  "Normalize a hash key so `eql` keys stay `eql` (D12, H14): a keyword is its
   name, marked as ClojureScript marks one, so hash-unkey can give it back."
  [k]
  (if (keyword? k) (str "\uFDD0" (.-fqn k)) k))

(defn ^any hash-unkey
  "A key as iterating a table returns it (D58): hash-key undone. Tagged any: a
   key is whatever went in, not the keyword the body may make."
  [k]
  (if (and (string? k) (identical? "\uFDD0" (.charAt k 0))) (keyword (subs k 1)) k))

(contract/defruntime
  {:positive-infinity js/Infinity
   :negative-infinity (- js/Infinity)})
