(ns setf.api
  "Squint host backend for `setf`.

   Same namespace as every other host backend; this file is only ever on
   Squint's source path. Squint reads no macro format other than `.cljc`, so
   unlike the ClojureScript backend this one file carries both halves."
  (:require [setf.contract :as contract]))

;; ---------------------------------------------------------------------------
;; The entire host-specific surface: six functions.
;; ---------------------------------------------------------------------------
;; Squint targets the same JavaScript primitives as the ClojureScript backend, so
;; the emission is identical: `t0[t1] = v`, `t0.set(t1, v)`, `t0.x = v`.

(def ^:private emit
  {:indexed {:read  (fn [refs _] (list 'aget (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list 'aset (nth refs 0) (nth refs 1) v))}

   :keyed   {:read  (fn [refs _] (list '.get (nth refs 0) (nth refs 1)))
             :write (fn [refs _ v] (list '.set (nth refs 0) (nth refs 1) v))}

   :field   {:read  (fn [refs _] (list (symbol (str ".-" (nth refs 1))) (nth refs 0)))
             :write (fn [refs _ v]
                      (list 'set! (list (symbol (str ".-" (nth refs 1))) (nth refs 0)) v))}})

(defmacro setf!
  "Assign to a place. Returns `v`."
  [place value]
  (:code (contract/expand-setf! emit place value)))

(defmacro incf!
  "Add `delta` (default 1) to a place, in place."
  [place & [delta]]
  (:code (contract/expand-derived! emit place (or delta 1) (fn [a b] (list '+ a b)))))

(defmacro decf!
  "Subtract `delta` (default 1) from a place, in place."
  [place & [delta]]
  (:code (contract/expand-derived! emit place (or delta 1) (fn [a b] (list '- a b)))))

;; ---------------------------------------------------------------------------
;; Mutable host collections, and one object with public fields.
;; ---------------------------------------------------------------------------

(defn make-arr
  "A mutable indexed sequence."
  [n]
  (array n))

(defn make-map
  "A mutable keyed collection."
  []
  (js/Map.))

(defn make-obj
  "A mutable object with public fields."
  []
  (js-obj "x" 0 "y" 0))
