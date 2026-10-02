(ns setf.api
  "PROTOTYPE — Squint backend, single .cljc file (the only macro format Squint reads)."
  (:require [setf.contract :as contract]))

(def ^:private EMIT
  {:indexed {:read  (fn [refs _] (list 'aget (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list 'aset (nth refs 0) (nth refs 1) v))}
   :keyed   {:read  (fn [refs _] (list '.get (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list '.set (nth refs 0) (nth refs 1) v))}
   :field   {:read  (fn [refs _] (list (symbol (str ".-" (nth refs 1))) (nth refs 0)))
             :write (fn [refs _ v]
                      (list 'set! (list (symbol (str ".-" (nth refs 1))) (nth refs 0)) v))}})

(defmacro setf! [place value] (:code (contract/expand-setf! EMIT place value)))
(defmacro incf! [place & [delta]] (:code (contract/expand-incf! EMIT place (or delta 1))))

(defn make-arr [n] (array n))
(defn make-map [] (js/Map.))
(defn make-obj [] (js-obj "x" 0))
(defn dump [a m o]
  [(aget a 1) (.get m "k") (.-x o)])