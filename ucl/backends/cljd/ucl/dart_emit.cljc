(ns ucl.dart-emit
  "The ClojureDart emit map: how each contract operation is spelled in Dart.

   ClojureDart evaluates macros on the JVM, in a shadow namespace (H20), and
   also compiles this file to Dart. It reads `:cljd/clj-host` only on the macro
   host, which is where the safety property and the method registry live.

   Performance rules: every expansion is plain Dart interop or a call into
   ucl.runtime -- never cljd.core, so a submission stays standalone Dart
   (D38). A `let` costs nothing here (ClojureDart hoists it into statements),
   so writes that must return their value simply bind it."
  (:require [ucl.contract :as contract]))

;; ---------------------------------------------------------------------------
;; Macro-host state
;; ---------------------------------------------------------------------------

(defn ^:macro-support safety-property
  "The build's safety level: the JVM system property ucl.safety, read where
   ClojureDart runs macros. Unset means 1."
  []
  #?(:cljd/clj-host (if-let [s (System/getProperty "ucl.safety")] (Long/parseLong s) 1)
     :cljd 1))

;; Structs and methods, keyed by namespace. A Dart class cannot gain methods
;; after it is defined, and ucl/defmethod comes after ucl/defstruct. But
;; ClojureDart expands every top-level form of a namespace twice -- on the
;; macro host, then to Dart (H42) -- so each defmethod records its body in the
;; first pass and defstruct, in the second, puts every method inside the
;; class (D40).
(def ^:macro-support registry #?(:cljd/clj-host (atom {:structs {} :methods {}}) :cljd nil))

(defn ^:macro-support current-ns [env]
  (str (get-in env [:nses :current-ns])))

;; ---------------------------------------------------------------------------
;; Names
;; ---------------------------------------------------------------------------

(defn ^:macro-support dart-name
  "A slot's or method's Dart name. ClojureDart munges `-` in a field read but
   not in a `set!` of one, nor in a deftype method's name (H43), so ucl names
   fields and methods itself."
  [slot]
  (let [s (apply str (map (fn [c] (if (= c \-) \_ c)) (name slot)))]
    (when-not (re-matches #"[A-Za-z_][A-Za-z0-9_]*" s)
      (contract/fail! (str slot ": on ClojureDart a slot or method name must be letters, digits, - and _")
                      {:slot slot}))
    (symbol s)))

(defn ^:macro-support list-type
  "A Dart List type with a reified element type: Dart checks generics at run
   time, so a List<dynamic> is not the List<String> LeetCode's harness wants."
  [element]
  (with-meta 'List {:type-params [element]}))

(defn ^:macro-support struct-class
  "The Dart class of a struct: the struct's own name, unless a constructor
   function is named like the struct -- `(NumArray nums)` must call the BOA
   constructor, and ClojureDart turns a call of a class name into `new`."
  [{:keys [name constructors]}]
  (if (some #(= name (:name %)) constructors)
    (symbol (str (dart-name name) "_struct"))
    name))

(defn ^:macro-support struct-tag [env sname]
  #?(:cljd/clj-host
     (let [structs (:structs @registry)
           here    (get structs [(current-ns env) sname])
           [[k v]] (or (when here [[[(current-ns env) sname] here]])
                       (filter (fn [[[_ n] _]] (= n sname)) structs))]
       (when v (symbol (first k) (str (:class v)))))
     :cljd nil))

(defn ^:macro-support type-hint [env t]
  (cond (contains? #{:fixnum :sb53} t)        'int
        (= t [:vector :fixnum])                (list-type 'int)
        (= t [:vector :sb53])                  (list-type 'int)
        (= t [:vector :string])                (list-type 'String)
        (= t [:vector :t])                     'List
        (and (vector? t) (= :struct (first t))) (struct-tag env (second t))
        :else nil))

;; ---------------------------------------------------------------------------
;; Vectors (README §5)
;; ---------------------------------------------------------------------------

(defn ^:macro-support rt [sym] (symbol "ucl.runtime" (name sym)))

(defn ^:macro-support make-vector
  [n {:keys [element initial-element has-initial-element? adjustable?
             fill-pointer items contents check-contents?]}]
  (let [zero      (case element (:fixnum :sb53) 0 :string "" nil)
        init      (if has-initial-element? initial-element zero)
        dart-type (case element (:fixnum :sb53) 'int :string 'String 'dynamic)
        contents  (if check-contents? (list (rt 'check-contents) n contents) contents)]
    (cond
      adjustable?
      (list '.filled (list-type dart-type) fill-pointer init '.growable true)

      (some? items)
      (let [lit (tagged-literal 'dart (vec items))]
        (case element
          :fixnum (list (rt 'fixnum-vector-from) lit)
          :sb53   (list (rt 'sb53-vector-from) lit)
          (list '.from (list-type dart-type) lit '.growable false)))

      (some? contents)
      (case element
        :fixnum (list (rt 'fixnum-vector-from) contents)
        :sb53   (list (rt 'sb53-vector-from) contents)
        (list '.from (list-type dart-type) contents '.growable false))

      :else
      (case element
        :fixnum (if (= 0 init) (list (rt 'make-fixnum-vector) n) (list (rt 'make-fixnum-vector-filled) n init))
        :sb53   (if (= 0 init) (list (rt 'make-sb53-vector) n) (list (rt 'make-sb53-vector-filled) n init))
        (list '.filled (list-type dart-type) n init)))))

;; ---------------------------------------------------------------------------
;; Structs and methods (D18, D40)
;; ---------------------------------------------------------------------------

(defn ^:macro-support method-forms
  "Every method recorded for `struct` in this namespace, as deftype methods."
  [env struct]
  #?(:cljd/clj-host
     (for [[[ns s _] {:keys [name self params body]}] (sort-by (comp :order val) (:methods @registry))
           :when (and (= ns (current-ns env)) (= s struct))]
       (list* (dart-name name) (vec (cons self params)) body))
     :cljd nil))

(defn ^:macro-support define-struct [env {:keys [name slots constructors] :as model}]
  (let [cls    (struct-class model)
        fields (mapv (fn [{s :name}] (vary-meta (dart-name s) assoc :mutable true)) slots)
        ;; slot names as written stay usable in the constructors' bodies
        new-form (fn [vals] (list* 'new cls vals))]
    #?(:cljd/clj-host
       (swap! registry assoc-in [:structs [(current-ns env) name]]
              {:class cls :slot-types (into {} (map (juxt :name :type) slots))})
       :cljd nil)
    (list* 'do
           ;; :type-only -- no ->Name factory, which nothing calls
           (list* 'deftype cls fields :type-only true (method-forms env name))
           (concat
            (for [{cn :name :keys [keys? arities]} constructors]
              (if keys?
                (list 'defn cn ['& {:keys (mapv :name slots)
                                    :or (into {} (map (juxt :name :init) slots))}]
                      (new-form (mapv :name slots)))
                (list* 'defn cn
                       (for [{:keys [params binds values]} arities]
                         (list params (list 'let* binds (new-form values)))))))
            [(list 'quote name)]))))

(defn ^:macro-support define-method [env {:keys [name self struct params doc body]}]
  #?(:cljd/clj-host
     (let [ns (current-ns env)
           st (get-in @registry [:structs [ns struct]])]
       (when-not st
         (contract/fail! (str "defmethod " name ": " struct " is not a ucl/defstruct of this namespace."
                              " On ClojureDart a method lives in its struct's class, so it must be"
                              " defined in the struct's namespace.")
                         {:struct struct}))
       (swap! registry update-in [:methods [ns struct name]]
              (fn [m] {:name name :self self :params params :body body
                       :order (or (:order m) (count (:methods @registry)))}))
       (let [owners (->> (:methods @registry)
                         (filter (fn [[[n _ m] _]] (and (= n ns) (= m name))))
                         (sort-by (comp :order val))
                         (map (fn [[[_ s _] _]] s)))
             args   (mapv #(vary-meta % dissoc :tag) params)
             recv   (gensym "self")]
         ;; one generic function per name and namespace, emitted by the first
         ;; defmethod of that name: a direct call when one struct has the
         ;; method, Dart's dynamic dispatch when several do
         (if (= struct (first owners))
           (list* 'defn name (concat (when doc [doc])
                                     [(vec (cons recv args))
                                      (list* (symbol (str "." (dart-name name)))
                                             (if (next owners)
                                               recv
                                               (vary-meta recv assoc :tag (symbol ns (str (:class st)))))
                                             args)]))
           nil)))
     :cljd nil))

;; ---------------------------------------------------------------------------
;; The emit map
;; ---------------------------------------------------------------------------

(defn ^:macro-support emit [env]
  (let [s (safety-property)
        checked? (pos? s)]
    {:safety s
     ;; Dart checks every index itself (RangeError), at any safety
     :elt {:read-once? true
           :read (fn [[a i] _] (list '. a "[]" i))
           ;; `a[i] = v` is void in ClojureDart (H44): the value is returned
           ;; explicitly, so the contract binds it
           :write-once? checked?
           :write (fn [[a i] _ v]
                    (if checked?
                      (list (rt 'elt-set-checked) a i v)
                      (list 'do (list '. a "[]=" i v) v)))}
     :seq {:length (fn [x] (list '.-length x))
           :push (fn [x v _] (list (rt (if checked? 'push-checked 'push)) x v))}
     :vector {:make (fn [n spec] (make-vector n spec))}
     :gethash {:read-once? true
               :read (fn [[k h d] _]
                       (if (some? d)
                         (list (rt 'get-or-default) k h d)
                         (list '. h "[]" k)))
               :write-once? false
               :write (fn [[k h _] _ v] (list 'do (list '. h "[]=" k v) v))
               ;; Dart compares keys with ==: an int read from an Int32List is
               ;; an int, and a ClojureDart keyword is canonical (D12)
               :key-literal identity
               :make (fn [pairs]
                       (if (seq pairs)
                         (list* 'doto (list (rt 'make-hash-table))
                                (for [[k v] pairs] (list '. "[]=" k v)))
                         (list (rt 'make-hash-table))))}
     :slot {:read-once? true
            :read (fn [[o slot] _] (list (symbol (str ".-" (dart-name slot))) o))
            :write-once? true
            :write (fn [[o slot] _ v] (list 'set! (list (symbol (str ".-" (dart-name slot))) o) v))}
     ;; A variable (D30): ClojureDart cannot assign a local (H39), so it is a
     ;; one-field cell, int-typed when declared fixnum or (signed-byte 53)
     :var {:read-once? true
           :read (fn [[x _] _] (list '.-v x))
           :write-once? true
           :write (fn [[x _] _ v] (list 'set! (list '.-v x) v))
           :bind (fn [x t init _]
                   [x (list 'new (rt (if (contains? #{:fixnum :sb53} t) 'IntCell 'Cell)) init)])}
     :number {:check (fn [x] (list (rt 'check-number) x))
              :check-type (fn [t x] (list (rt (if (= t :fixnum) 'check-fixnum 'check-sb53)) x))
              :min (fn [a b] (if (and (contract/trivial? a) (contract/trivial? b))
                               (list 'if (list '< a b) a b)
                               (let [x (gensym "a") y (gensym "b")]
                                 (list 'let [x a y b] (list 'if (list '< x y) x y)))))
              :max (fn [a b] (if (and (contract/trivial? a) (contract/trivial? b))
                               (list 'if (list '> a b) a b)
                               (let [x (gensym "a") y (gensym "b")]
                                 (list 'let [x a y b] (list 'if (list '> x y) x y)))))}
     :types {:hint (fn [t _] (type-hint env t))
             :local-hint (fn [t] (when (contains? #{:fixnum :sb53} t) 'int))}
     :struct {:define (fn [model] (define-struct env model))}
     :method {:define (fn [m] (define-method env m))}}))
