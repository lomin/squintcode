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

;; ---------------------------------------------------------------------------
;; The place names, reading. A place name is a macro, not a function: it must be
;; the same form whether it is being written (`setf!` consumes it in head
;; position) or read (expanded here), and `setf!` consumes it without expanding
;; it, so the two directions never interfere.
;;
;; Given every argument but the receiver, a place name returns a FUNCTION, so a
;; place can be handed to a higher-order function: `(map (elt 1) colls)`. On this
;; host every curried reader is the same direct access as the uncurried one.
;; ---------------------------------------------------------------------------

(defmacro elt
  "Read the element at index `i` of `coll`: `(elt coll i)`. Any object the host
   indexes with `[]`. `(elt i)` returns `(fn [coll] ...)`, so `(map (elt 1) colls)`
   works."
  [& args]
  (:code (contract/expand-read emit (cons 'elt args))))

(defmacro gethash
  "Read the value at key `k` of `m`: `(gethash m k)`.
   `(gethash k)` returns `(fn [m] ...)`, so `(map (gethash \"k\") maps)` works."
  [& args]
  (:code (contract/expand-read emit (cons 'gethash args))))

(defmacro get!
  "Read the field `f` of object `o`: `(get! o f)`. `f` must be a bare symbol.
   `(get! f)` returns `(fn [o] ...)`, so `(map (get! x) objs)` works."
  [& args]
  (:code (contract/expand-read emit (cons 'get! args))))

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
  "A mutable indexed sequence of length `n`, every slot 0 -- as on every host."
  [n]
  (.fill (js/Array. n) 0))

(defn make-map
  "A mutable keyed collection."
  []
  (js/Map.))

(defn make-obj
  "A mutable object with public fields."
  []
  (js-obj "x" 0 "y" 0))
