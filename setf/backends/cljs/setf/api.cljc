(ns setf.api
  "ClojureScript host backend for `setf` -- macro half.

   Same namespace as every other host backend; this file is only ever on
   ClojureScript's source path."
  (:require [setf.contract :as contract]))

;; ---------------------------------------------------------------------------
;; The entire host-specific surface: six functions.
;; ---------------------------------------------------------------------------
;; JS has exactly the primitives we want and nothing to wrap. `elt` and a keyed
;; access are both direct property/index operations; a field is `.-x` / `(set! …)`.
;; No hint, no dispatch, no allocation -- the compiled result is `o.x = 42`.

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
;; ---------------------------------------------------------------------------

(defmacro elt
  "Read the element at index `i` of `coll`. Any object the host indexes with `[]`."
  [coll i]
  (:code (contract/expand-read emit (list 'elt coll i))))

(defmacro gethash
  "Read the value at key `k` of `m`."
  [m k]
  (:code (contract/expand-read emit (list 'gethash m k))))

(defmacro get!
  "Read the field `f` of object `o`."
  [o f]
  (:code (contract/expand-read emit (list 'get! o f))))

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
