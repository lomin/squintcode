(ns ucl.api
  "ucl on Clojure/JVM. An emit map plus `defapi`; the model is in ucl.contract.

   Representation (README §5): simple vectors are primitive or Object arrays,
   an adjustable vector with a fill pointer is a java.util.ArrayList, a hash
   table is a java.util.HashMap, a struct is a deftype whose slots are reached
   through one small interface per slot name, a variable is a one-field cell.

   This namespace defines ucl/let and ucl/dotimes, so it excludes Clojure's
   and spells them clojure.core/let and clojure.core/dotimes."
  (:refer-clojure :exclude [make-array min max defstruct defmethod let dotimes])
  (:require [ucl.contract :as contract])
  (:import [clojure.lang Compiler$LocalBinding RT]
           [java.util ArrayList HashMap List Map]))

;; ---------------------------------------------------------------------------
;; Safety (D14): the system property ucl.safety; unset means 1.
;; ---------------------------------------------------------------------------

(defn- safety []
  (if-let [s (System/getProperty "ucl.safety")] (Long/parseLong s) 1))

;; ---------------------------------------------------------------------------
;; What the compiler knows about a form's class (H1, H2)
;; ---------------------------------------------------------------------------

(def ^:private array-tags
  {(class (object-array 0)) 'objects
   (class (int-array 0))    'ints
   (class (long-array 0))   'longs})

(defn- tag->class [tag]
  (cond (class? tag) tag
        (symbol? tag) (clojure.core/let [c (try (resolve tag) (catch Exception _ nil))]
                        (cond (class? c) c
                              (= 'ints tag) (class (int-array 0))
                              (= 'longs tag) (class (long-array 0))
                              (= 'objects tag) (class (object-array 0))))
        :else nil))

(defn- known-class
  "The class of `src` when the compiler can tell: a :tag on the form, a local
   whose class it inferred or was told, a vector literal, or a macro form --
   such as a nested ucl/make-array -- whose expansion carries a :tag."
  [env src]
  (or (some-> (:tag (meta src)) tag->class)
      (when (vector? src) clojure.lang.PersistentVector)
      (when (symbol? src)
        (when-let [^Compiler$LocalBinding lb (get env src)]
          (try (when (.hasJavaClass lb) (.getJavaClass lb))
               (catch Exception _ nil))))
      (when (seq? src)
        (clojure.core/let [x (try (macroexpand src) (catch Exception _ src))]
          (when-not (identical? x src)
            (some-> (:tag (meta x)) tag->class))))))

(defn- hinted [form tag] (if (symbol? form) (vary-meta form assoc :tag tag) form))

(defn- warn-fallback!
  "D4: say so at compile time whenever the type is unknown and the code falls
   back to dynamic dispatch. Nothing else would point at these paths."
  [what src]
  (binding [*out* *err*]
    (println (str "ucl WARNING: " what " on " (pr-str src) " of unknown type"
                  (str " (" *file* ":" (or (:line (meta src)) @clojure.lang.Compiler/LINE) ")")
                  "; declare its type for direct access"))))

;; ---------------------------------------------------------------------------
;; Run-time half
;; ---------------------------------------------------------------------------

(defn elt-set
  "Store into a sequence whose type was not known at compile time."
  [x ^long i v]
  (cond (instance? List x) (do (.set ^List x i v) v)
        (instance? (class (int-array 0)) x) (aset ^ints x i (int v))
        (instance? (class (long-array 0)) x) (aset ^longs x i (long v))
        :else (aset ^objects x i v)))

(defn hash-key
  "Normalize a hash key so `eql` keys stay `eql` (D12): an int read from an
   int[] is an Integer, which a HashMap of Long keys would miss."
  [k]
  (if (instance? Integer k) (long ^Integer k) k))

(defn check-number [x]
  (if (number? x)
    x
    (throw (ex-info (str "ucl: the value " (pr-str x) " is not a number") {:type-error x}))))

(defn check-contents
  "Safety >= 1: :initial-contents must match the dimension, as in CL."
  [n contents]
  (clojure.core/let [c (count contents)]
    (when-not (= c n)
      (throw (ex-info (str "ucl: make-array of " n " elements given " c " initial contents") {})))
    contents))

(defn new-adjustable ^ArrayList [n fill init]
  (clojure.core/let [a (ArrayList. (int (clojure.core/max n fill)))]
    (clojure.core/dotimes [_ fill] (.add a init))
    a))

;; ---------------------------------------------------------------------------
;; Variables (D30): a cell per variable. A mutable deftype field is private
;; (H10), so the cell is reached through an interface. A variable declared
;; fixnum or (signed-byte 53) gets a primitive long cell. A cell that does not
;; escape costs nothing: the JIT removes the allocation (§9.8).
;; ---------------------------------------------------------------------------

(definterface LongVar (^long get []) (^long set [^long v]))
(deftype LongCell [^:unsynchronized-mutable ^long v]
  LongVar
  (get [_] v)
  (set [_ x] (set! v x) x))

(definterface ObjectVar (get []) (set [v]))
(deftype ObjectCell [^:unsynchronized-mutable v]
  ObjectVar
  (get [_] v)
  (set [_ x] (set! v x) x))

(defn check-fixnum ^long [x]
  (if (and (integer? x) (<= -2147483648 x 2147483647))
    (long x)
    (throw (ex-info (str "ucl: the value " (pr-str x) " is not of type fixnum") {:type-error x}))))

(defn check-sb53 ^long [x]
  (if (and (integer? x) (<= -9007199254740991 x 9007199254740991))
    (long x)
    (throw (ex-info (str "ucl: the value " (pr-str x) " is not of type (signed-byte 53)")
                    {:type-error x}))))

;; ---------------------------------------------------------------------------
;; Structs: one interface per slot name, a deftype per struct
;; ---------------------------------------------------------------------------
;; A mutable deftype field is private (H10), so a slot is reached through an
;; interface. One interface per slot NAME (ucl.slots.S_next) lets slot-value
;; work on a receiver of unknown struct type without reflection: every struct
;; with a `next` slot implements ucl.slots.S_next.

(defn- slot-iface [slot] (symbol (str "ucl.slots.S_" (munge (name slot)))))
(defn- getter [slot] (symbol (munge (name slot))))
(defn- setter [slot] (symbol (str "set_" (munge (name slot)))))

(defn ensure-slot-interface!
  "Define ucl.slots.S_<slot> once per JVM; redefining it would orphan every
   struct already compiled against it."
  [slot]
  (clojure.core/let [iface (slot-iface slot)]
    (when-not (try (RT/classForNameNonLoading (str iface)) (catch Throwable _ nil))
      (eval `(gen-interface :name ~iface
                            :methods [[~(getter slot) [] Object]
                                      [~(setter slot) [Object] Object]])))
    iface))

(defonce ^:private structs (atom {}))

(defn- struct-class-name [nm] (str (namespace-munge *ns*) "." nm))

(defn- struct-model
  "The struct a class or struct name refers to, if it is one."
  [x]
  (cond (class? x) (get @structs (.getName ^Class x))
        (symbol? x) (or (get @structs (struct-class-name x))
                        (get @structs (str x)))))

(def ^:private type-tags
  {:fixnum            'long
   :sb53              'long
   [:vector :fixnum]  'ints
   [:vector :sb53]    'longs
   [:vector :t]       'objects})

(defn- type-tag
  "The JVM tag for a canonical type. Primitive tags only where Clojure allows
   them: fn params of at most 4."
  ([t] (type-tag t 0))
  ([t n-params]
   (cond (#{:fixnum :sb53} t) (when (<= n-params 4) 'long)
         (and (vector? t) (= :struct (first t)))
         (some-> (struct-model (second t)) :class symbol)
         :else (type-tags t))))

(defn- define-struct [{:keys [name slots constructors new?] :as model}]
  (clojure.core/let [cname  (struct-class-name name)
        fields (mapv (fn [{s :name}] (with-meta (symbol (munge (clojure.core/name s)))
                                       {:unsynchronized-mutable true}))
                     slots)
        ifaces (mapv (fn [{s :name}] (ensure-slot-interface! s)) slots)
        _      (swap! structs assoc cname
                      (assoc model :class cname
                             :slot-types (into {} (map (juxt :name :type) slots))))
        impls  (mapcat (fn [{s :name} f iface]
                         [iface
                          (list (getter s) ['_] f)
                          (list (setter s) ['_ 'v] (list 'set! f 'v) 'v)])
                       slots fields ifaces)
        ctor-name? (some #(= name (:name %)) constructors)
        new-form (fn [vals] (list* 'new (symbol cname) vals))]
    `(do
       (deftype ~name ~fields ~@impls)
       ~@(when ctor-name? [`(ns-unmap *ns* '~name)])
       ~@(for [{cn :name :keys [keys? arities]} constructors]
           (if keys?
             `(defn ~cn [& {:keys ~(mapv :name slots)
                            :or ~(into {} (map (juxt :name :init) slots))}]
                ~(new-form (mapv :name slots)))
             `(defn ~cn
                ~@(for [{:keys [params binds values]} arities]
                    (list params (list 'let* binds (new-form values)))))))
       ~(symbol cname))))

;; Methods: one protocol per generic function name and namespace.
(defonce ^:private generics (atom #{}))

(defn- define-method [{:keys [name self struct params doc body]}]
  (clojure.core/let [{:keys [class]} (or (struct-model struct)
                            (contract/fail! (str "defmethod " name ": " struct
                                                 " is not a ucl/defstruct") {:struct struct}))
        proto (symbol (str "G_" (munge (clojure.core/name name))))
        key   [(ns-name *ns*) name]
        ;; no primitive hints in the protocol: a ^long there makes callers
        ;; compile a primitive invoke the protocol fn does not implement
        sig   (vec (cons self (map #(vary-meta % dissoc :tag) params)))
        first? (not (contains? @generics key))]
    (swap! generics conj key)
    `(do
       ~@(when first?
           [`(defprotocol ~proto (~name ~sig ~@(when doc [doc])))])
       (extend-type ~(symbol class) ~proto
         (~name ~(vec (cons (vary-meta self assoc :tag (symbol class)) params)) ~@body))
       (var ~name))))

;; ---------------------------------------------------------------------------
;; The emit map
;; ---------------------------------------------------------------------------

(defn- seq-kind [env src]
  (clojure.core/let [c (known-class env src)]
    (cond (nil? c) nil
          (array-tags c) (array-tags c)
          (.isAssignableFrom List c) :list
          :else nil)))

(defn- int-store [s v] (if (pos? s) (list 'int v) (list 'unchecked-int v)))

(defn- read-key [k src env]
  (clojure.core/let [c (known-class env src)]
    (if (or (contract/literal? src) (#{Long Long/TYPE String} c))
      k
      (list `hash-key k))))

(defn- make-vector [s n {:keys [element initial-element has-initial-element? adjustable?
                               fill-pointer items contents check-contents?]}]
                (clojure.core/let [contents (if check-contents? `(check-contents ~n ~contents) contents)
                      zero (if (= :t element) nil 0)
                      init (if has-initial-element? initial-element zero)]
                  (cond
                    adjustable?
                    `(new-adjustable ~n ~fill-pointer ~init)

                    (some? contents)
                    (case element
                      :t      (if items `(object-array ~items) `(object-array ~contents))
                      :fixnum `(int-array ~contents)
                      :sb53   `(long-array ~contents))

                    :else
                    (case element
                      :t      (if has-initial-element?
                                `(clojure.core/let [a# (object-array ~n)] (java.util.Arrays/fill a# ~init) a#)
                                `(object-array ~n))
                      :fixnum `(int-array ~n ~(if (pos? s) (list 'int init) (list 'unchecked-int init)))
                      :sb53   `(long-array ~n ~init)))))

(defn emit [env]
  (clojure.core/let [s (safety)]
    {:safety s
     :elt {:read-once? true
           :read  (fn [[a i] [sa]]
                    (case (seq-kind env sa)
                      ints     (list 'aget (hinted a 'ints) i)
                      longs    (list 'aget (hinted a 'longs) i)
                      objects  (list 'aget (hinted a 'objects) i)
                      :list    (list '.get (hinted a `List) i)
                      (do (warn-fallback! "elt" sa) (list 'nth a i))))
           :write-once? false
           :write (fn [[a i] [sa] v]
                    (case (seq-kind env sa)
                      ints     (list 'aset (hinted a 'ints) i (int-store s v))
                      longs    (list 'aset (hinted a 'longs) i (list 'long v))
                      objects  (list 'aset (hinted a 'objects) i v)
                      :list    (list 'do (list '.set (hinted a `List) i v) v)
                      (do (warn-fallback! "setf of elt" sa) (list `elt-set a i v))))}
     :seq {:length (fn [x]
                     (case (seq-kind env x)
                       (ints longs objects) (list 'alength (hinted x (seq-kind env x)))
                       :list (list '.size (hinted x `List))
                       (do (warn-fallback! "length" x) (list 'count x))))
           :push (fn [x v vs]
                   (clojure.core/let [l (gensym "l")]
                     (when-not (= :list (seq-kind env vs)) (warn-fallback! "vector-push-extend" vs))
                     `(clojure.core/let [~l ~(hinted v `List) i# (.size ~l)] (.add ~l ~x) i#)))}
     :vector {:make
              (fn [n {:keys [element adjustable?] :as spec}]
                (vary-meta (make-vector s n spec) assoc
                           :tag (if adjustable? `ArrayList (type-tags [:vector element]))))}
     :gethash {:read-once? true
               :read  (fn [[k h d] [sk]]
                        (if (some? d)
                          (list '.getOrDefault (hinted h `Map) (read-key k sk env) d)
                          (list '.get (hinted h `Map) (read-key k sk env))))
               :write-once? false
               :write (fn [[k h _] [sk] v]
                        (list 'do (list '.put (hinted h `Map) (read-key k sk env) v) v))
               :key-literal identity
               :make (fn [pairs]
                       (clojure.core/let [m (gensym "m")]
                         `(clojure.core/let [~m (HashMap.)]
                            ~@(for [[k v] pairs] (list '.put m k v))
                            ~m)))}
     :slot {:read-once? true
            :read  (fn [[o slot] [so]]
                     (clojure.core/let [iface (ensure-slot-interface! slot)
                           st    (some-> (known-class env so) struct-model :slot-types (get slot))
                           tag   (when st (type-tag st))
                           form  (list (symbol (str "." (getter slot))) (vary-meta o assoc :tag iface))]
                       (if tag (vary-meta form assoc :tag tag) form)))
            :write-once? true
            :write (fn [[o slot] _ v]
                     (clojure.core/let [iface (ensure-slot-interface! slot)]
                       (list (symbol (str "." (setter slot))) (vary-meta o assoc :tag iface) v)))}
     :var {:read-once? true
           :read (fn [[x t] _]
                   (clojure.core/let [tag (when-not (#{:fixnum :sb53} t) (type-tag t))
                                      form (list '.get x)]
                     (if tag (vary-meta form assoc :tag tag) form)))
           :write-once? true
           :write (fn [[x _] _ v] (list '.set x v))
           :bind (fn [x t init {:keys [internal?]}]
                   (when (and (nil? t) (not internal?))
                     (binding [*out* *err*]
                       (println (str "ucl WARNING: the variable " x " has no declared type"
                                     " (" *file* ":" @clojure.lang.Compiler/LINE ")"
                                     "; declare it for a primitive cell"))))
                   [x (list 'new (if (#{:fixnum :sb53} t) `LongCell `ObjectCell) init)])}
     :number {:check (fn [x] (list `check-number x))
              :check-type (fn [t x] (list (if (= t :fixnum) `check-fixnum `check-sb53) x))
              :min (fn [a b] (clojure.core/let [x (gensym "a") y (gensym "b")] `(clojure.core/let [~x ~a ~y ~b] (if (< ~x ~y) ~x ~y))))
              :max (fn [a b] (clojure.core/let [x (gensym "a") y (gensym "b")] `(clojure.core/let [~x ~a ~y ~b] (if (> ~x ~y) ~x ~y))))}
     ;; a local bound to a literal: Clojure already infers a primitive long,
     ;; and refuses a hint there ("Can't type hint a local with a primitive initializer")
     :types {:hint (fn [t n] (type-tag t n))
             :local-hint (fn [_] nil)}
     :struct {:define define-struct}
     :method {:define define-method}}))

(contract/defruntime
  {:positive-infinity Double/POSITIVE_INFINITY
   :negative-infinity Double/NEGATIVE_INFINITY
   :min-inline (fn [& args] (contract/expand-extremum (emit {}) :min args))
   :max-inline (fn [& args] (contract/expand-extremum (emit {}) :max args))})

(contract/defapi (emit &env) {:inline-extrema? true})
