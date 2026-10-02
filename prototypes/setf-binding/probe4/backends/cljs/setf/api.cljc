(ns setf.api
  "PROTOTYPE — ClojureScript backend, MACRO half (.cljc).
   Same namespace name as every other backend, different file."
  (:require [setf.contract]))

(def ^:private EMIT
  {:indexed {:read  (fn [refs _] (list 'aget (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list 'aset (nth refs 0) (nth refs 1) v))}
   :keyed   {:read  (fn [refs _] (list '.get (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list '.set (nth refs 0) (nth refs 1) v))}
   :field   {:read  (fn [refs _] (list (symbol (str ".-" (nth refs 1))) (nth refs 0)))
             :write (fn [refs _ v]
                      (list 'set! (list (symbol (str ".-" (nth refs 1))) (nth refs 0)) v))}})

(defmacro setf! [place value] (:code (setf.contract/expand-setf! EMIT place value)))
(defmacro incf! [place & [delta]] (:code (setf.contract/expand-incf! EMIT place (or delta 1))))
