(ns setf.api
  "Clojure/JVM host backend for `setf`.

   Same namespace as every other host backend. This file is only ever on the
   JVM's source path, so the name can be identical everywhere without collision
   -- that is how backend selection happens: by which source root a target's
   build sees."
  (:require [setf.contract :as contract]))

;; ---------------------------------------------------------------------------
;; Host knowledge #1: the JVM's indexed and keyed accessors.
;; ---------------------------------------------------------------------------
;; `elt` is indexed access, not sequence walking. On this host java.util.List
;; and java.util.Map have O(1) primitives, so both halves are interface calls
;; with a hint -- no Clojure dispatch, no reflection, and (unlike `nth`, which
;; requires `Indexed`) the same reach over ArrayList and LinkedList alike.
;; The hints are supplied HERE. Client code never carries a type.

(defn- hinted [sym class] (with-meta sym {:tag class}))

;; ---------------------------------------------------------------------------
;; Host knowledge #2: the return type of every constructor this backend defines.
;; ---------------------------------------------------------------------------
;; Decided once, here. When a `:field` receiver is one of these, the concrete
;; class is known at expansion time and we emit a real PUTFIELD. A `^Type` hint
;; on the receiver is honoured as well, but nothing in this library asks for one
;; it is inert on ClojureScript and Squint.

(def ^:private constructor-types
  {'make-obj 'java.awt.Point})

(defn- static-class
  "The concrete class of a `:field` receiver, or nil if it cannot be known here."
  [src]
  (cond
    (and (seq? src) (symbol? (first src)))
    (get constructor-types (symbol (name (first src))))

    (:tag (meta src)) (:tag (meta src))))

;; ---------------------------------------------------------------------------
;; The entire host-specific surface: six functions.
;; ---------------------------------------------------------------------------

(def ^:private emit
  {:indexed {:read  (fn [refs _]
                      (list '.get (hinted (nth refs 0) 'java.util.List) (nth refs 1)))
             :write (fn [refs _ v]
                      (list '.set (hinted (nth refs 0) 'java.util.List) (nth refs 1) v))}

   :keyed   {:read  (fn [refs _]
                      (list '.get (hinted (nth refs 0) 'java.util.Map) (nth refs 1)))
             :write (fn [refs _ v]
                      (list '.put (hinted (nth refs 0) 'java.util.Map) (nth refs 1) v))}

   :field   {:read  (fn [refs srcs]
                      (let [o (first refs)
                            c (static-class (first srcs))]
                        (list (symbol (str "." (name (second refs))))
                              (if c (hinted o c) o))))
             :write (fn [refs srcs v]
                      (let [o    (first refs)
                            c    (static-class (first srcs))
                            fld  (symbol (str "." (name (second refs))))]
                        (if c
                          ;; class known: a real field store, no reflection
                          (list 'set! (list fld (hinted o c)) v)
                          ;; class unknowable here: Clojure cannot express a fast
                          ;; field store without one, so fall back. Measured ~9x
                          ;; the cost of a direct store -- correct, and the only
                          ;; reflective path in the whole library.
                          (list 'clojure.lang.Reflector/setInstanceField
                                o (str (second refs)) v))))}})

;; ---------------------------------------------------------------------------
;; The API. Identical names and semantics on every host; the logic is shared.
;; ---------------------------------------------------------------------------

(defmacro setf!
  "Assign to a place. `(setf! (elt coll i) v)`, `(setf! (gethash m k) v)`,
   `(setf! (get! o field) v)`. Every runtime argument is evaluated exactly once,
   left to right. Returns `v`."
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
;; Mutable host collections, and one object with a public mutable field.
;; ---------------------------------------------------------------------------

(defn make-arr
  "A mutable indexed sequence."
  [n]
  (java.util.ArrayList. ^java.util.Collection (vec (repeat n 0))))

(defn make-map
  "A mutable keyed collection."
  []
  (java.util.HashMap.))

(defn make-obj
  "A mutable object with public fields."
  []
  (java.awt.Point. 0 0))
