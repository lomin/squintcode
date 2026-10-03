(ns ucl.js-emit
  "What the two JavaScript hosts, Squint and ClojureScript, share: one emit
   map, parameterized by a small `flavor` map for the places where the hosts
   differ, plus the run-time helpers both define (`defhelpers`).

   This is backend code, not contract: it names a host family. It lives in its
   own source root, backends/js, which both JS builds put on their path.

   Performance rule (§9.3, H22): never emit a `let` in expression position --
   Squint compiles it to an IIFE, 8x slower in a hot loop. Every read and write
   here evaluates each argument once, in order, so the contract can pass the
   forms through unbound; checks at safety >= 1 are function calls, which keep
   that property."
  (:require [ucl.contract :as contract]))

;; ---------------------------------------------------------------------------
;; Flavor keys
;;   :array-literal  (fn [items]) -> a fresh JS array of `items`
;;   :local-tag      (fn [env sym]) -> the :tag of a local, or nil
;;   :tags?          does the host use :tag hints (ClojureScript) or not
;;   :key-literal    (fn [k]) -> a literal key, normalized at compile time
;;   :key-runtime    (fn [form]) -> form normalizing a runtime key
;;   :vector-reads?  can an undeclared receiver be a persistent vector
;;   :check-arity?   does the compiler check the arity of calls to a defn
;;   :safety         (fn [env]) -> the build's safety level, nil when unset
;; ---------------------------------------------------------------------------

(declare define-struct define-method)

(defn- array-tag?
  "Not a def'd set: that would compile to `new Set(..)`, which tree shaking
   keeps, putting this file into every submission."
  [tag]
  (contains? '#{array js/Array js/Int32Array js/Float64Array} tag))

(defn- array-form? [flavor env src]
  (array-tag? (or (:tag (meta src))
                  (when (symbol? src) ((:local-tag flavor) env src)))))

(defn- tag [flavor form t]
  (if (and (:tags? flavor) (or (seq? form) (symbol? form)))
    (vary-meta form assoc :tag t)
    form))

(defn- api [sym] (symbol "ucl.api" (name sym)))

(defn- var-tag
  "The ClojureScript :tag of a variable's read, from its declared type."
  [t]
  (cond (contains? #{:fixnum :sb53} t) 'number
        (and (vector? t) (= :vector (first t))) 'array))

(defn- make-vector [flavor n {:keys [element initial-element has-initial-element? adjustable?
                                     fill-pointer items contents check-contents?]}]
  (let [element (if (= :string element) :t element)   ; upgraded to t, as in Common Lisp
        arr (:array-literal flavor)
        ctor ({:t 'js/Array :fixnum 'js/Int32Array :sb53 'js/Float64Array} element)
        contents (if check-contents? (list (api 'check-contents) n contents) contents)
        filled (fn [len init] (list '.fill (list 'new ctor len) init))]
    (cond
      adjustable?
      (if (= 0 fill-pointer)
        (arr [])
        (filled fill-pointer (if has-initial-element? initial-element nil)))

      items
      (if (= :t element) (arr items) (list* (symbol (str ctor ".of")) items))

      (some? contents)
      (list (symbol (str ctor ".from")) contents)

      (and has-initial-element? (not (and (not= :t element) (= 0 initial-element))))
      (filled n initial-element)

      :else (list 'new ctor n))))

(defn emit-map
  "The emit map for a JS host, given its flavor and the macro's &env."
  [flavor env]
  (let [s  (let [x ((:safety flavor) env)] (if (some? x) x 1))
        checked? (pos? s)
        direct? (fn [src] (or (not (:vector-reads? flavor)) (array-form? flavor env src)))
        hash-key (fn [k src] (if (contract/literal? src)
                               ((:key-literal flavor) src)
                               ((:key-runtime flavor) k)))]
    {:safety s
     :elt {:read-once? true
           :read (fn [[a i] [sa]]
                   (cond checked?     (list (api 'elt-checked) a i)
                         (direct? sa) (list 'aget a i)
                         :else        (list (api 'elt-any) a i)))
           :write-once? true
           :write (fn [[a i] _ v]
                    (if checked?
                      (list (api 'elt-set-checked) a i v)
                      (list 'aset a i v)))}
     :seq {:length (fn [x]
                     (if (direct? x)
                       (list '.-length x)
                       (list (api 'length-any) x)))
           :push (fn [x v _]
                   (if checked?
                     (list (api 'push-checked) x v)
                     (list 'dec (list '.push v x))))}
     :vector {:make (fn [n spec] (tag flavor (make-vector flavor n spec) 'array))}
     :gethash {:read-once? true
               :read (fn [[k h d] [sk]]
                       (list (api 'get-or-default) (hash-key k sk) h d))
               :write-once? true
               :write (fn [[k h _] [sk] v]
                        (list (api 'puthash) (hash-key k sk) h v))
               :key-literal (fn [k] ((:key-literal flavor) k))
               :make (fn [pairs]
                       (if (seq pairs)
                         (list 'new 'js/Map ((:array-literal flavor)
                                             (mapv (fn [[k v]] ((:array-literal flavor) [k v])) pairs)))
                         (list 'new 'js/Map)))}
     :slot {:read-once? true
            :read (fn [[o slot] _] (list (symbol (str ".-" slot)) o))
            :write-once? true
            :write (fn [[o slot] _ v] (list 'set! (list (symbol (str ".-" slot)) o) v))}
     ;; A variable (D30). Squint assigns a `^:mutable` local: plain `let` and
     ;; `x = v` in JavaScript. ClojureScript cannot assign a local, so the
     ;; variable is a one-field deftype; V8 removes the allocation.
     :var {:read-once? true
           :read (fn [[x t] _]
                   (if (:cells? flavor)
                     (tag flavor (list '.-v x) (var-tag t))
                     x))
           :write-once? true
           :write (fn [[x _] _ v]
                    (if (:cells? flavor)
                      (list 'set! (list '.-v x) v)
                      (list 'set! x v)))
           :bind (fn [x _ init _]
                   (if (:cells? flavor)
                     [x (list 'new (api 'Cell) init)]
                     [(vary-meta x assoc :mutable true) init]))}
     :number {:check (fn [x] (list (api 'check-number) x))
              :check-type (fn [t x] (list (api (if (= t :fixnum) 'check-fixnum 'check-sb53)) x))
              :min (fn [a b] (if (and (contract/trivial? a) (contract/trivial? b))
                               (list 'if (list '< a b) a b)
                               (list 'js/Math.min a b)))
              :max (fn [a b] (if (and (contract/trivial? a) (contract/trivial? b))
                               (list 'if (list '> a b) a b)
                               (list 'js/Math.max a b)))}
     :types {:hint (fn [t _]
                     (when (:tags? flavor)
                       (cond (#{:fixnum :sb53} t) 'number
                             (and (vector? t) (= :vector (first t))) 'array)))
             :local-hint (fn [t] (when (and (:tags? flavor) (#{:fixnum :sb53} t)) 'number))}
     :struct {:define (fn [model] (cons 'do (define-struct flavor model)))}
     :method {:define (fn [m] (define-method m))}}))

;; ---------------------------------------------------------------------------
;; Structs: a constructor function, methods on its prototype (D18)
;; ---------------------------------------------------------------------------
;; The constructor works with and without `new`. A BOA constructor named like
;; the struct IS that function; any other constructor builds through it.
;; Optional parameters follow JavaScript (and LeetCode's own classes): an
;; omitted argument is `undefined`, which selects the default; an explicit nil
;; does not.

(defn- js-arity
  "The one JS arity of a BOA lambda list: every parameter, defaults on undefined."
  [{:keys [required optional aux]}]
  {:params (vec (concat required (map first optional)))
   :binds  (vec (concat (mapcat (fn [[v d]] [v (list 'if (list 'undefined? v) d v)]) optional)
                        (mapcat (fn [[v init]] [v init]) aux)))})

(defn- ctor-def
  "On ClojureScript the constructor is a plain function value, not a `defn`:
   ClojureScript checks the arity of every call to a defn, and an omitted
   &optional argument is legitimate here. Squint checks no arities."
  [flavor name params body]
  (if (:check-arity? flavor)
    (list 'def name (list 'identity (list 'fn params body)))
    (list 'defn name params body)))

(defn define-struct [flavor {:keys [name slots constructors]}]
  (let [self   (gensym "self")
        named  (first (filter #(= name (:name %)) constructors))
        _ (when (and named (next constructors))
            (contract/fail! (str "defstruct " name ": a constructor named " name
                                 " cannot be combined with other constructors in v1")
                            {:struct name}))
        sets   (fn [vals] (map (fn [{s :name} v] (list 'set! (list (symbol (str ".-" s)) self) v))
                               slots vals))
        body   (fn [params binds vals]
                 (list 'this-as self
                       ;; not `instance?`: Squint expands that to an IIFE
                       (list 'if (vary-meta (list '.isPrototypeOf (list '.-prototype name) self)
                                            assoc :tag 'boolean)
                             (list* 'let* binds (concat (sets vals) [self]))
                             (list* 'new name params))))
        vars   (fn [{:keys [required optional aux]}]
                 (set (concat required (map first optional) (map first aux))))]
    (concat
     (if named
       (let [l (:lambda named)
             {:keys [params binds]} (js-arity l)]
         [(ctor-def flavor name params (body params binds (contract/slot-values slots (vars l))))])
       (let [params (mapv :name slots)]
         (cons (ctor-def flavor name params (body params [] params))
               (for [{cn :name :keys [keys? lambda]} constructors]
                 (if keys?
                   (list 'defn cn ['& {:keys (mapv :name slots)
                                       :or (into {} (map (juxt :name :init) slots))}]
                         (list* 'new name (mapv :name slots)))
                   (let [{:keys [params binds]} (js-arity lambda)]
                     (list 'defn cn params
                           (list 'let* binds
                                 (list* 'new name (contract/slot-values slots (vars lambda)))))))))))
     [name])))

(defn define-method [{:keys [name self struct params doc body]}]
  (list 'do
        (list 'set! (list (symbol (str ".-" name)) (list '.-prototype struct))
              (list 'fn params (list* 'this-as self body)))
        (list* 'defn name (concat (when doc [doc])
                                  [(vec (cons self params))
                                   (list* (symbol (str "." name)) self params)]))))

;; ---------------------------------------------------------------------------
;; Run-time helpers both JS hosts define in ucl.api
;; ---------------------------------------------------------------------------

(defmacro defhelpers
  "Define, in the calling namespace (ucl.api), the run-time half that
   expansions call as ucl.api/<name>."
  [vector-reads?]
  `(do
     (defn ~'get-or-default
       "gethash with a default: exact Common Lisp -- a key stored with nil is present."
       [~'k ~'m ~'d]
       (let [~'v (.get ~'m ~'k)] (if (~'undefined? ~'v) ~'d ~'v)))

     (defn ~'puthash [~'k ~'m ~'v] (.set ~'m ~'k ~'v) ~'v)

     (defn ~'fail [~'msg] (throw (js/Error. (str "ucl: " ~'msg))))

     (defn ~'check-number [~'x]
       (if (number? ~'x) ~'x (~'fail (str "the value " (pr-str ~'x) " is not a number"))))

     (defn ~'check-fixnum [~'x]
       (if (and (js/Number.isInteger ~'x) (<= -2147483648 ~'x 2147483647))
         ~'x
         (~'fail (str "the value " (pr-str ~'x) " is not of type fixnum"))))

     (defn ~'check-sb53 [~'x]
       (if (js/Number.isSafeInteger ~'x)
         ~'x
         (~'fail (str "the value " (pr-str ~'x) " is not of type (signed-byte 53)"))))

     (defn ~'length-any [~'x]
       ~(if vector-reads?
          `(if (coll? ~'x) (count ~'x) (.-length ~'x))
          `(.-length ~'x)))

     (defn ~'elt-any [~'a ~'i]
       ~(if vector-reads?
          `(if (vector? ~'a) (nth ~'a ~'i) (aget ~'a ~'i))
          `(aget ~'a ~'i)))

     (defn ~'check-index [~'a ~'i]
       (when-not (and (js/Number.isInteger ~'i) (>= ~'i 0) (< ~'i (~'length-any ~'a)))
         (~'fail (str "index " ~'i " out of bounds for length " (~'length-any ~'a)))))

     (defn ~'elt-checked [~'a ~'i]
       (~'check-index ~'a ~'i)
       (~'elt-any ~'a ~'i))

     (defn ~'elt-set-checked [~'a ~'i ~'v]
       (~'check-index ~'a ~'i)
       (cond
         (instance? js/Int32Array ~'a)
         (when-not (and (js/Number.isInteger ~'v) (<= -2147483648 ~'v 2147483647))
           (~'fail (str "the value " (pr-str ~'v) " is not of type fixnum")))
         (instance? js/Float64Array ~'a)
         (when-not (js/Number.isSafeInteger ~'v)
           (~'fail (str "the value " (pr-str ~'v) " is not of type (signed-byte 53)"))))
       (aset ~'a ~'i ~'v))

     (defn ~'push-checked [~'x ~'v]
       (when-not (js/Array.isArray ~'v)
         (~'fail "vector-push-extend needs an adjustable vector with a fill pointer"))
       (dec (.push ~'v ~'x)))

     (defn ~'check-contents [~'n ~'contents]
       (let [~'c (~'length-any ~'contents)]
         (when-not (== ~'c ~'n)
           (~'fail (str "make-array of " ~'n " elements given " ~'c " initial contents")))
         ~'contents))))
