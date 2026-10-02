(ns setf.api
  "PROTOTYPE (probe 4) — a MOCK FOURTH HOST.

   Deliberately shares NO surface with Clojure, ClojureScript or Squint: its
   primitives are `store!`/`lookup`/`put-slot!`, which appear nowhere else.
   shared/setf/contract.cljc is byte-identical to probe3's copy — adding this
   host required editing nothing outside this directory."
  (:require [setf.contract :as contract]))

(def ^:private EMIT
  {:indexed {:read  (fn [refs _] (list 'fetch (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list 'store! (nth refs 0) (nth refs 1) v))}
   :keyed   {:read  (fn [refs _] (list 'lookup (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list 'assoc! (nth refs 0) (nth refs 1) v))}
   :field   {:read  (fn [refs _] (list 'slot (nth refs 0) (list 'quote (nth refs 1))))
             :write (fn [refs _ v] (list 'put-slot! (nth refs 0) (list 'quote (nth refs 1)) v))}})

(defmacro setf! [place value] (:code (contract/expand-setf! EMIT place value)))
(defmacro incf! [place & [delta]] (:code (contract/expand-incf! EMIT place (or delta 1))))

;; Stub "runtime" for the fictional host. Irrelevant to the design question.
(defn make-arr [n] nil)
(defn make-map [] nil)
(defn make-obj [] nil)
(defn dump [a m o] [a m o])