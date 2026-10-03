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
;; and java.util.Map have direct accessors, so both halves are interface calls
;; with a hint -- no Clojure dispatch, no reflection, and (unlike `nth`, which
;; requires `Indexed`) the same reach over ArrayList and LinkedList alike.
;; The cost is the structure's own: O(1) on ArrayList/HashMap, O(n) on
;; LinkedList, O(log n) on TreeMap.
;; The hints are supplied HERE. Client code never carries a type.

(defn- hinted [sym class] (with-meta sym {:tag class}))

(defn- tag->class-sym
  "A tag as a class symbol, or nil if it does not name a reference class.
   Primitive and unresolvable tags are not something a field can be read from."
  [tag]
  (let [c (cond (class? tag)  tag
                (symbol? tag) (try (resolve tag) (catch Exception _ nil)))]
    (when (and (class? c) (not (.isPrimitive ^Class c)))
      (symbol (.getName ^Class c)))))

;; ---------------------------------------------------------------------------
;; Host knowledge #2: what class a `:field` receiver has, if it can be known.
;; ---------------------------------------------------------------------------
;; A `:field` access is a real GETFIELD/PUTFIELD only when the receiver's class is
;; known statically. Clojure already knows it in three ordinary situations, and
;; this backend asks the compiler rather than keeping a table of its own:
;;
;;   (get! ^Point p x)                 a hint on the receiver form itself
;;   (fn [^Point p] (get! p x))        a local the compiler has typed -- by hint
;;   (let [p (Point. 0 0)] ...)        or by inference from its init expression
;;   (get! (api/make-obj) x)           a call to a fn with a return-type hint
;;
;; Nothing in client code asks for a type: the constructors this backend defines
;; carry return hints, so their results are typed without the client writing one.

(defn- local-class
  "The class the compiler has inferred for local `sym`, or nil."
  [env sym]
  (when-let [^clojure.lang.Compiler$LocalBinding lb (get env sym)]
    (try (when (.hasJavaClass lb) (tag->class-sym (.getJavaClass lb)))
         (catch Exception _ nil))))

(defn- return-class
  "The return-type hint of the fn called by `form`, or nil. Resolved through
   the namespace, so an unrelated fn that merely shares a name is never hinted."
  [form]
  (when (and (seq? form) (symbol? (first form)))
    (when-let [v (try (resolve (first form)) (catch Exception _ nil))]
      (when (var? v)
        (let [m (meta v)]
          (tag->class-sym (or (:tag m)
                              (some (fn [args] (:tag (meta args))) (:arglists m)))))))))

(defn- static-class
  "The concrete class of a `:field` receiver, or nil if it cannot be known here."
  [env src]
  (or (tag->class-sym (:tag (meta src)))
      (when (symbol? src) (local-class env src))
      (return-class src)))

;; ---------------------------------------------------------------------------
;; The entire host-specific surface: six functions.
;; ---------------------------------------------------------------------------
;; Built per expansion, because the `:field` pair reads the compiler's
;; environment. The contract never sees `env`; it sees six functions.

(defn- emit [env]
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
                            c (static-class env (first srcs))]
                        (if c
                          ;; class known: a real GETFIELD
                          (list (symbol (str "." (name (second refs)))) (hinted o c))
                          ;; class unknowable here: the same fallback the write uses
                          (list 'clojure.lang.Reflector/getInstanceField
                                o (str (second refs))))))
             :write (fn [refs srcs v]
                      (let [o    (first refs)
                            c    (static-class env (first srcs))
                            fld  (symbol (str "." (name (second refs))))]
                        (if c
                          ;; class known: a real PUTFIELD, no reflection
                          (list 'set! (list fld (hinted o c)) v)
                          ;; class unknowable here: Clojure cannot express a fast
                          ;; field store without one, so fall back. Measured ~9x
                          ;; the cost of a direct store -- correct, and the only
                          ;; reflective path in the whole library.
                          (list 'clojure.lang.Reflector/setInstanceField
                                o (str (second refs)) v))))}})

;; ---------------------------------------------------------------------------
;; The API. Identical names and semantics on every host; the logic is shared.
;;
;; The place names come in pairs: a macro reads a place, `setf!` writes it.
;; `setf!` consumes `(elt coll i)` as a form and never expands it, so the same
;; name means both directions without the two interfering.
;;
;; Given every argument but the receiver, a place name returns a FUNCTION, so a
;; place can be handed to a higher-order function: `(map (elt 1) colls)`.
;;
;; `elt` and `gethash` curry for free: the receiver keeps its interface hint.
;; `get!` does not: inside the returned closure the receiver is an untyped fn
;; parameter, so the field read goes through Reflector. Run bench/hof_bench.clj
;; for the cost. If you want a field in a higher-order position and it is hot,
;; write the fn yourself with a hinted parameter: (fn [^Point p] (get! p x)).
;; ---------------------------------------------------------------------------

(defmacro elt
  "Read the element at index `i` of `coll`: `(elt coll i)`.
   `(elt i)` returns `(fn [coll] ...)`, so `(map (elt 1) colls)` works."
  [& args]
  (:code (contract/expand-read (emit &env) (cons 'elt args))))

(defmacro gethash
  "Read the value at key `k` of `m`: `(gethash m k)`.
   `(gethash k)` returns `(fn [m] ...)`, so `(map (gethash \"k\") maps)` works."
  [& args]
  (:code (contract/expand-read (emit &env) (cons 'gethash args))))

(defmacro get!
  "Read the field `f` of object `o`: `(get! o f)`. `f` must be a bare symbol.
   `(get! f)` returns `(fn [o] ...)`, so `(map (get! x) objs)` works -- but on
   this host that closure is reflective; see the note above."
  [& args]
  (:code (contract/expand-read (emit &env) (cons 'get! args))))

(defmacro setf!
  "Assign to a place. `(setf! (elt coll i) v)`, `(setf! (gethash m k) v)`,
   `(setf! (get! o field) v)`. Every runtime argument is evaluated exactly once,
   left to right. Returns `v`."
  [place value]
  (:code (contract/expand-setf! (emit &env) place value)))

(defmacro incf!
  "Add `delta` (default 1) to a place, in place."
  [place & [delta]]
  (:code (contract/expand-derived! (emit &env) place (or delta 1) (fn [a b] (list '+ a b)))))

(defmacro decf!
  "Subtract `delta` (default 1) from a place, in place."
  [place & [delta]]
  (:code (contract/expand-derived! (emit &env) place (or delta 1) (fn [a b] (list '- a b)))))

;; ---------------------------------------------------------------------------
;; Mutable host collections, and one object with a public mutable field.
;; ---------------------------------------------------------------------------

(defn make-arr
  "A mutable indexed sequence of length `n`, every slot 0 -- as on every host."
  [n]
  (java.util.ArrayList. ^java.util.Collection (vec (repeat n 0))))

(defn make-map
  "A mutable keyed collection."
  []
  (java.util.HashMap.))

(defn make-obj
  "A mutable object with public fields."
  ^java.awt.Point []
  (java.awt.Point. 0 0))
