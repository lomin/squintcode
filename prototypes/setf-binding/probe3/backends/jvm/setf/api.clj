(ns setf.api
  "PROTOTYPE — JVM backend. Same namespace name as every other backend,
   different file, never on any other target's source path."
  (:require [setf.contract :as contract]))

;; ---------------------------------------------------------------------------
;; (Q19/Q20) The ONE piece of host knowledge this backend owns: the return type
;; of every constructor it defines. When a `:field` place's receiver is one of
;; these, the backend knows the concrete class at expansion time and emits a
;; real PUTFIELD. When it is not, it falls back to Reflector.
;;
;; A type hint on the receiver is honoured too, but nothing in this library
;; requires one: `^Type` metadata is inert on ClojureScript and Squint (F15).
;; ---------------------------------------------------------------------------
(def ^:private CONSTRUCTOR-TYPES
  {'make-obj 'java.awt.Point})

(defn- static-class
  "The concrete class of a `:field` receiver, or nil if we cannot know it.
   Decided entirely at expansion time; the client never has to say anything."
  [src]
  (cond
    ;; the receiver is a call to a constructor this backend defines
    ;; (matches bare or qualified: make-obj, api/make-obj, setf.api/make-obj)
    (and (seq? src) (symbol? (first src)))
    (CONSTRUCTOR-TYPES (symbol (name (first src))))

    ;; the receiver carries a type hint
    (:tag (meta src)) (:tag (meta src))))

(defn- typed [sym class] (with-meta sym {:tag class}))

;; The ONLY host-specific code: what an operation looks like on this host.
(def ^:private EMIT
  ;; `:indexed` is the `elt` place: indexed access, not sequence walking. On this
  ;; host `java.util.List` has an O(1) indexed primitive, so BOTH halves are List
  ;; interface calls with a hint. Symmetric, and works on ArrayList and LinkedList
  ;; alike -- so `setf!` and `incf!` reach the same structures (F13).
  {:indexed {:read  (fn [refs _] (list '.get (typed (nth refs 0) 'java.util.List)
                                       (nth refs 1)))
             :write (fn [refs _ v] (list '.set (typed (nth refs 0) 'java.util.List)
                                         (nth refs 1) v))}
   :keyed   {:read  (fn [refs _] (list '.get (typed (nth refs 0) 'java.util.Map)
                                       (nth refs 1)))
             :write (fn [refs _ v] (list '.put (typed (nth refs 0) 'java.util.Map)
                                         (nth refs 1) v))}
   :field   {:read  (fn [refs srcs]
                      (let [o (first refs)
                            c (static-class (first srcs))
                            f (symbol (str "." (name (second refs))))]
                        (list f (if c (typed o c) o))))
             :write (fn [refs srcs v]
                      (let [o (first refs)
                            c (static-class (first srcs))
                            f (symbol (str "." (name (second refs))))]
                        (if c
                          ;; PUTFIELD -- no reflection
                          (list 'set! (list f (typed o c)) v)
                          ;; could not know the class: Reflector
                          (list 'clojure.lang.Reflector/setInstanceField
                                o (str (second refs)) v))))}})

(defmacro setf! [place value] (:code (contract/expand-setf! EMIT place value)))
(defmacro incf! [place & [delta]] (:code (contract/expand-incf! EMIT place (or delta 1))))

;; Mutable host collections + one object with a public mutable field.
(defn make-arr [n] (java.util.ArrayList. ^java.util.Collection (vec (repeat n 0))))
(defn make-map [] (java.util.HashMap.))
(defn make-obj [] (java.awt.Point. 0 0))
(defn dump [a m o]
  [(.get a 1) (.get m "k") (.x ^java.awt.Point o)])
