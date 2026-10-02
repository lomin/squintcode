(ns setf.api
  "PROTOTYPE (probe 4) — a DELIBERATELY INCOMPLETE backend: it implements
   :indexed and :keyed but omits the :field pair. The contract must reject it
   loudly rather than emit something wrong."
  (:require [setf.contract :as contract]))

(def ^:private EMIT
  {:indexed {:read  (fn [refs _] (list 'fetch (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list 'store! (nth refs 0) (nth refs 1) v))}
   :keyed   {:read  (fn [refs _] (list 'lookup (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list 'assoc! (nth refs 0) (nth refs 1) v))}})

(defmacro setf! [place value] (:code (contract/expand-setf! EMIT place value)))
(defmacro incf! [place & [delta]] (:code (contract/expand-incf! EMIT place (or delta 1))))

(defn make-arr [n] nil)
(defn make-map [] nil)
(defn make-obj [] nil)
(defn dump [a m o] [a m o])