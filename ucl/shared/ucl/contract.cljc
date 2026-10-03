(ns ucl.contract
  "The ucl contract: every host-agnostic decision, and no host.

   A backend hands this namespace an emit map -- data and small functions that
   turn a decided operation into host code -- and `defapi` generates the
   backend's whole API from it. Nothing here names a host, branches on one, or
   uses a reader conditional (README §2).

   Every fn a macro calls at expansion time is tagged `^:macro-support`:
   ClojureDart needs that to make it available to macros; everywhere else it is
   inert metadata (README H20).

   Squint loads this file into its macro interpreter, SCI. Two SCI limits shape
   the code: a quoted symbol inside `or` fails to resolve (H26), so defaults use
   `if`; and clojure.walk is not assumed, so the walker is written out.")

;; ===========================================================================
;; Errors and small helpers
;; ===========================================================================

(defn ^:macro-support fail!
  "Throw a compile-time error. Every message names the form at fault."
  [msg data]
  (throw (ex-info (str "ucl: " msg) (assoc data :library 'ucl))))

(defn ^:macro-support trivial?
  "A form that is free to evaluate and safe to repeat: a symbol or a literal.
   Binding one buys nothing, and on Squint a `let` in expression position
   costs an IIFE (H22) -- 8x in a hot loop (§9.3)."
  [form]
  (not (or (seq? form) (vector? form) (map? form) (set? form))))

(defn ^:macro-support literal?
  "A self-evaluating constant: number, string, keyword, boolean or nil."
  [form]
  (or (number? form) (string? form) (keyword? form) (true? form) (false? form) (nil? form)))

(defn ^:macro-support quoted?
  [form]
  (and (seq? form) (= 'quote (first form)) (= 2 (count form))))

(defn ^:macro-support unquote-form
  "`'x` -> x; anything else unchanged."
  [form]
  (if (quoted? form) (second form) form))

(defn ^:macro-support named?
  "Is `form` a symbol whose name (ignoring any namespace or alias) is `nm`?"
  [form nm]
  (and (symbol? form) (= nm (name form))))

(defn ^:macro-support wrap-let
  "`(let binds body...)`, or just the body when nothing needs binding."
  [binds & body]
  (cond (seq binds)  (list* 'let (vec binds) body)
        (next body)  (list* 'do body)
        :else        (first body)))

(defn ^:macro-support op
  "The backend's implementation of `k` for `kind`. A backend that lacks one is
   not a ucl host: every host implements the whole contract."
  [backend kind k]
  (let [f (get-in backend [kind k])]
    (if (some? f)
      f
      (fail! (str "the host backend does not implement " k " for " kind
                  ". A host must implement the whole contract.")
             {:kind kind :op k}))))

(defn ^:macro-support safety
  "The build's safety level (D11, D14). Unset means 1."
  [backend]
  (let [s (:safety backend)]
    (if (some? s) s 1)))

;; ===========================================================================
;; Types (D10, D15)
;; ===========================================================================
;; A Common Lisp type specifier is reduced to one canonical form that backends
;; map to host types:
;;   :fixnum  :sb53            integer types
;;   [:vector E]               simple vector, E one of :t :fixnum :sb53 :string
;;   [:struct Name]            a ucl/defstruct type
;;   nil                       a type the contract does not act on

;; A fn, not a def'd map: a top-level def whose value Squint compiles to calls
;; (list ...) is kept by esbuild's tree shaking and would ride along in every
;; submission. Keep the contract's top-level defs to plain literals.
(defn ^:macro-support expand-type-abbreviation [spec]
  (case spec
    fixnum-vector '(simple-array fixnum (*))
    sb53-vector   '(simple-array (signed-byte 53) (*))
    simple-vector '(simple-array t (*))
    spec))

(defn ^:macro-support canonical-element
  "The element type of `make-array` or `simple-array`."
  [spec]
  (cond
    (= spec 't)                        :t
    (= spec 'fixnum)                   :fixnum
    (= spec '(signed-byte 53))         :sb53
    ;; a host that reifies element types (Dart) needs it for a result LeetCode
    ;; types List<String>; elsewhere it upgrades to t, as in Common Lisp
    (= spec 'string)                   :string
    :else (fail! (str "unsupported element type " (pr-str spec)
                      ". v1 supports t, fixnum, (signed-byte 53) and string.")
                 {:type spec})))

(defn ^:macro-support canonical-type
  [spec]
  (let [spec (if (symbol? spec) (expand-type-abbreviation spec) spec)]
    (cond
      (= spec 'fixnum)           :fixnum
      (= spec '(signed-byte 53)) :sb53
      (and (seq? spec) (= 'simple-array (first spec)))
      (do (when-not (= '(*) (nth spec 2 nil))
            (fail! (str "only one-dimensional arrays are supported: " (pr-str spec)) {:type spec}))
          [:vector (canonical-element (second spec))])
      (and (seq? spec) (= 'or (first spec)) (= 'null (second spec)) (= 3 (count spec)))
      (canonical-type (nth spec 2))
      (and (symbol? spec) (re-find #"^[A-Z]" (name spec)))
      [:struct spec]
      :else nil)))

;; Variables declared fixnum or (signed-byte 53) are checked on every store at
;; safety >= 1, as SBCL checks a declared type (D2, D34).

(defn ^:macro-support integer-type? [t]
  (contains? #{:fixnum :sb53} t))

(defn ^:macro-support integer-in-type? [t x]
  (and (integer? x)
       (if (= t :fixnum)
         (<= -2147483648 x 2147483647)
         (<= -9007199254740991 x 9007199254740991))))

(defn ^:macro-support check-value
  "`form`, checked against the variable type `t` when the build asks for it."
  [backend t form]
  (if (and (pos? (safety backend)) (integer-type? t) (not (integer-in-type? t form)))
    ((op backend :number :check-type) t form)
    form))

;; ===========================================================================
;; Places (D6)
;; ===========================================================================
;; A place is resolved by NAME at expansion time: `elt`, `ucl/elt` and
;; `ucl.api/elt` are one place. Argument kinds:
;;   :runtime   evaluated exactly once, left to right
;;   :slot      a quoted symbol, consumed at expansion time

(def ^:macro-support places
  {"elt"        {:kind :elt     :args [:runtime :runtime]}
   "gethash"    {:kind :gethash :args [:runtime :runtime] :optional [:runtime]}
   "slot-value" {:kind :slot    :args [:runtime :slot]}
   ;; A variable as a place. Never written by hand: the variable walker turns
   ;; `(setf x v)` into `(setf (%var x type) v)` (D31).
   "%var"       {:kind :var     :args [:static :static]}})

(defn ^:macro-support place-spec [place]
  (when (symbol? place)
    (fail! (str "setf of `" place "`: it is a local, not a variable. Only names bound by"
                " ucl/let, ucl/let* or a ucl/defun or ucl/defmethod parameter can be assigned.")
           {:place place}))
  (let [accessor (when (seq? place) (first place))
        spec     (when (symbol? accessor) (get places (name accessor)))]
    (when-not spec
      (fail! (str "`" (pr-str place) "` is not a place. Places: elt, gethash, slot-value.")
             {:place place}))
    (let [n    (count (rest place))
          need (count (:args spec))
          most (+ need (count (:optional spec)))]
      (when-not (<= need n most)
        (fail! (str "`" accessor "` takes " (if (= need most) need (str need " or " most))
                    " arguments, got " n ": " (pr-str place))
               {:place place})))
    (assoc spec :kinds (vec (take (count (rest place)) (concat (:args spec) (:optional spec)))))))

(defn ^:macro-support slot-name [form place]
  (if (and (quoted? form) (symbol? (second form)))
    (second form)
    (fail! (str "the slot of `slot-value` must be a quoted symbol, as in (slot-value obj 'next): "
                (pr-str place))
           {:place place})))

(defn ^:macro-support plan-args
  "Decide, per argument, the form a backend receives.

   bind? false: the backend evaluates each argument exactly once, left to
   right, so it receives the forms themselves.
   bind? true: non-trivial arguments are bound to fresh gensyms first.

   Returns [binds refs srcs]: `srcs` are the original forms, so a backend can
   read a type hint off them even when it receives a gensym."
  [kinds args place bind?]
  (loop [ks (seq kinds) as (seq args) binds [] refs [] srcs []]
    (if ks
      (let [k (first ks) a (first as)]
        (cond
          (= k :slot)
          (let [s (slot-name a place)]
            (recur (next ks) (next as) binds (conj refs s) (conj srcs s)))

          (= k :static)
          (recur (next ks) (next as) binds (conj refs a) (conj srcs a))

          (or (not bind?) (trivial? a))
          (recur (next ks) (next as) binds (conj refs a) (conj srcs a))

          :else
          (let [g (gensym "t")]
            (recur (next ks) (next as) (conj binds g a) (conj refs g) (conj srcs a)))))
      [binds refs srcs])))

(defn ^:macro-support expand-read
  "`(elt v i)`, `(gethash k h [d])`, `(slot-value o 's)` as an expression."
  [backend place]
  (let [{:keys [kind kinds]} (place-spec place)
        once? (get-in backend [kind :read-once?])
        [binds refs srcs] (plan-args kinds (rest place) place (not once?))]
    (wrap-let binds ((op backend kind :read) refs srcs))))

(defn ^:macro-support expand-modify
  "`(incf place [delta])` / `(decf place [delta])`: read, combine, write; the
   place's arguments evaluated once. At safety >= 1 the old value must be a
   number -- SBCL signals on (incf nil) (D2)."
  [backend op-sym place delta-form]
  (let [{:keys [kind kinds]} (place-spec place)
        [binds refs srcs] (plan-args kinds (rest place) place true)
        old   ((op backend kind :read) refs srcs)
        ;; a variable declared fixnum or (signed-byte 53) holds a number by
        ;; construction (D34); checking it would box its value on the JVM
        typed? (and (= kind :var) (integer-type? (second refs)))
        old   (if (and (pos? (safety backend)) (not typed?)) ((op backend :number :check) old) old)
        delta (if (some? delta-form) delta-form 1)
        new   (list op-sym old delta)
        new   (if (= kind :var) (check-value backend (second refs) new) new)]
    (if (get-in backend [kind :write-once?])
      (wrap-let binds ((op backend kind :write) refs srcs new))
      (let [v (gensym "v")]
        (wrap-let (conj binds v new) ((op backend kind :write) refs srcs v))))))

;; ===========================================================================
;; Sequences: length, vector-push-extend
;; ===========================================================================

(defn ^:macro-support expand-length [backend form]
  ((op backend :seq :length) form))

(defn ^:macro-support expand-vector-push-extend
  "Common Lisp argument order: the element first. Returns the element's index."
  [backend args]
  (when-not (= 2 (count args))
    (fail! "vector-push-extend takes (vector-push-extend new-element vector); v1 has no extension argument"
           {:form (cons 'vector-push-extend args)}))
  (let [[x v] args
        [binds [xr vr] [_ vs]] (plan-args [:runtime :runtime] args nil true)]
    (wrap-let binds ((op backend :seq :push) xr vr vs))))

;; ===========================================================================
;; make-array (D7, D9, D10)
;; ===========================================================================

(defn ^:macro-support parse-keys
  "A Common Lisp &key list into a map, rejecting unknown keywords."
  [what allowed opts]
  (when (odd? (count opts))
    (fail! (str what ": keyword arguments must come in pairs: " (pr-str opts)) {:opts opts}))
  (let [m (apply hash-map opts)]
    (doseq [k (keys m)]
      (when-not (contains? allowed k)
        (fail! (str what ": unknown keyword " k ". Known: " (pr-str (sort allowed))) {:key k})))
    m))

(defn ^:macro-support literal-contents
  "The elements of a literal `[...]` or `'(...)`, else nil."
  [form]
  (cond (vector? form) (vec form)
        (and (quoted? form) (sequential? (second form))) (vec (second form))
        :else nil))

(defn ^:macro-support expand-make-array [backend n-form opts]
  (let [{:keys [element-type initial-element initial-contents adjustable fill-pointer] :as o}
        (parse-keys "make-array"
                    #{:element-type :initial-element :initial-contents :adjustable :fill-pointer}
                    opts)
        _ (when (and (contains? o :element-type)
                     (not (or (quoted? element-type) (= 't element-type))))
            (fail! (str "make-array: :element-type must be a quoted type, as in :element-type 'fixnum: "
                        (pr-str element-type))
                   {:element-type element-type}))
        element (canonical-element (if (contains? o :element-type) (unquote-form element-type) 't))
        _ (when (and (contains? o :initial-element) (contains? o :initial-contents))
            (fail! "make-array: :initial-element and :initial-contents are exclusive" {:opts opts}))
        _ (when-not (= (boolean adjustable) (contains? o :fill-pointer))
            (fail! "make-array: v1 supports :adjustable t only together with :fill-pointer (D9)"
                   {:opts opts}))
        _ (when (and adjustable (contains? o :initial-contents))
            (fail! "make-array: :initial-contents is not supported on an adjustable vector in v1" {:opts opts}))
        items (literal-contents initial-contents)
        _ (when (and items (number? n-form) (not= n-form (count items)))
            (fail! (str "make-array: dimension " n-form " but :initial-contents has "
                        (count items) " elements")
                   {:opts opts}))
        fill (cond (not adjustable) nil
                   (true? fill-pointer) n-form
                   :else fill-pointer)
        spec {:element element
              :initial-element initial-element
              :has-initial-element? (contains? o :initial-element)
              :adjustable? (boolean adjustable)
              :fill-pointer fill
              :items items
              :contents (when (contains? o :initial-contents) initial-contents)
              :check-contents? (and (contains? o :initial-contents) (nil? items)
                                    (pos? (safety backend)))}]
    ((op backend :vector :make) n-form spec)))

;; ===========================================================================
;; Hash tables (D12, D22)
;; ===========================================================================

(defn ^:macro-support expand-make-hash-table [backend opts]
  (let [{:keys [test initial-contents] :as o}
        (parse-keys "make-hash-table" #{:test :size :initial-contents} opts)]
    (when (and (contains? o :test) (not (contains? #{''eql 'eql} test)))
      (fail! (str "make-hash-table: only :test 'eql is supported in v1, got " (pr-str test))
             {:test test}))
    (when (and (contains? o :initial-contents) (not (map? initial-contents)))
      (fail! "make-hash-table: :initial-contents must be a literal map, as in {0 1}"
             {:initial-contents initial-contents}))
    (let [key-literal (op backend :gethash :key-literal)
          pairs (mapv (fn [[k v]] [(if (literal? k) (key-literal k) k) v]) initial-contents)]
      ((op backend :gethash :make) pairs))))

;; ===========================================================================
;; min / max (D21)
;; ===========================================================================
;; Inline in call position. Squint's own min/max are variadic runtime calls
;; that V8 inlines only sometimes (§9.3). The backend picks the form; the
;; contract only reduces the arity.

(defn ^:macro-support expand-extremum
  "`which` is :min or :max."
  [backend which args]
  (when (empty? args)
    (fail! (str (name which) " needs at least one argument") {:op which}))
  (let [two (op backend :number which)]
    (reduce (fn [acc x] (two acc x)) args)))

;; ===========================================================================
;; Strings
;; ===========================================================================

(defn ^:macro-support expand-princ-to-string
  "(princ-to-string x): an integer's decimal digits, a string itself. Each
   host has a direct form for it; Squint's and ClojureDart's `str` is a
   runtime call, and ClojureDart's runtime cannot reach a submission."
  [backend x]
  ((op backend :string :princ) x))

;; ===========================================================================
;; Lambda lists and declarations (D13, D15, D18)
;; ===========================================================================

(defn ^:macro-support declare-form? [form]
  (and (seq? form) (named? (first form) "declare")))

(defn ^:macro-support split-body
  "[docstring declarations body]"
  [body]
  (let [[doc body] (if (and (string? (first body)) (next body))
                     [(first body) (rest body)]
                     [nil body])
        [decls body] (split-with declare-form? body)]
    [doc decls body]))

(defn ^:macro-support declared-types
  "{var canonical-type} from `(declare (type T v...) (fixnum v...) ...)`."
  [decls]
  (reduce
   (fn [acc spec]
     (when-not (seq? spec)
       (fail! (str "malformed declaration " (pr-str spec)) {:declaration spec}))
     (let [head (first spec)]
       (cond
         (named? head "type")
         (let [[_ t & vars] spec, ct (canonical-type t)]
           (reduce (fn [a v] (if ct (assoc a v ct) a)) acc vars))

         (named? head "optimize")
         (fail! (str "per-function " (pr-str spec) " is not supported: safety is set per build (D13, D14)")
                {:declaration spec})

         (named? head "ignore") acc
         (named? head "ignorable") acc

         :else
         (let [ct (canonical-type head)]
           (if ct
             (reduce (fn [a v] (assoc a v ct)) acc (rest spec))
             (fail! (str "unsupported declaration " (pr-str spec)) {:declaration spec}))))))
   {}
   (mapcat rest decls)))

(defn ^:macro-support parse-lambda-list
  "A Common Lisp lambda list: {:required [..] :optional [[var default]..] :aux [[var init]..]}."
  [what lambda-list allowed]
  (when-not (or (seq? lambda-list) (vector? lambda-list))
    (fail! (str what ": the lambda list must be a list, as in (a b): " (pr-str lambda-list))
           {:lambda-list lambda-list}))
  (loop [xs (seq lambda-list) mode :required out {:required [] :optional [] :aux []}]
    (if-not xs
      out
      (let [x (first xs)]
        (cond
          (and (symbol? x) (= \& (first (name x))))
          (let [m (keyword (subs (name x) 1))]
            (when-not (contains? allowed m)
              (fail! (str what ": " x " is not supported here") {:lambda-list lambda-list}))
            (recur (next xs) m out))

          (= mode :required)
          (do (when-not (symbol? x)
                (fail! (str what ": a required parameter must be a symbol: " (pr-str x)) {:param x}))
              (recur (next xs) mode (update out :required conj x)))

          :else
          (let [[v init] (if (seq? x) [(first x) (second x)] [x nil])]
            (recur (next xs) mode (update out mode conj [v init]))))))))

(defn ^:macro-support hint-params
  "Attach each declared type to its parameter, as the backend spells it."
  [backend params types]
  (let [hint (op backend :types :hint)
        n    (count params)]
    (mapv (fn [p]
            (let [t   (get types p)
                  tag (when t (hint t n))]
              (if tag (vary-meta p assoc :tag tag) p)))
          params)))

;; ===========================================================================
;; defstruct (D18)
;; ===========================================================================

(defn ^:macro-support parse-slot [s]
  (cond
    (symbol? s) {:name s :init nil}
    (seq? s)    (let [[nm init & opts] s
                      {:keys [type read-only]} (parse-keys (str "defstruct slot " nm)
                                                           #{:type :read-only} opts)]
                  {:name nm :init init :type (when (some? type) (canonical-type type))})
    :else (fail! (str "malformed defstruct slot " (pr-str s)) {:slot s})))

(defn ^:macro-support parse-defstruct
  "The model every backend builds a struct from:
   {:name N :slots [{:name :init :type}] :constructors [{:name :lambda ..}]}"
  [name-and-options slot-forms]
  (let [[nm & options] (if (seq? name-and-options) name-and-options [name-and-options])
        _ (when-not (symbol? nm) (fail! "defstruct needs a name" {:form name-and-options}))
        slots (mapv parse-slot slot-forms)
        ctor-opts (filter #(and (seq? %) (= :constructor (first %))) options)
        _ (doseq [o options]
            (when-not (and (seq? o) (= :constructor (first o)))
              (fail! (str "defstruct option " (pr-str o) " is not supported in v1 (only :constructor)")
                     {:option o})))
        ctors (cond
                (empty? ctor-opts)
                [{:name (symbol (str "make-" nm)) :keys? true}]

                :else
                (vec (keep (fn [[_ cname lambda]]
                             (cond
                               (nil? cname) nil
                               (nil? lambda) {:name cname :keys? true}
                               :else {:name cname
                                      :lambda (parse-lambda-list
                                               (str "defstruct " nm " constructor")
                                               lambda #{:optional :aux})}))
                           ctor-opts)))]
    {:name nm :slots slots :constructors ctors
     :new? (empty? ctors)}))

(defn ^:macro-support constructor-arities
  "Each arity of a BOA constructor as {:params [..] :binds [..]}: `binds` is a
   let* vector giving every lambda variable a value, in lambda-list order --
   supplied optionals bound to themselves, missing ones to their default, then
   &aux. Slot values are taken from the variable of the same name, else the
   slot's initform."
  [{:keys [required optional aux]}]
  (for [k (range (inc (count optional)))]
    (let [given   (take k optional)
          missing (drop k optional)]
      {:params (vec (concat required (map first given)))
       :binds  (vec (concat (mapcat (fn [[v d]] [v d]) missing)
                            (mapcat (fn [[v init]] [v init]) aux)))})))

(defn ^:macro-support slot-values
  "The value form for every slot, in slot order, given the lambda variables in scope."
  [slots vars]
  (mapv (fn [{:keys [name init]}] (if (contains? vars name) name init)) slots))

(defn ^:macro-support expand-defstruct [backend name-and-options slots]
  (let [model (parse-defstruct name-and-options slots)
        model (assoc model :constructors
                     (mapv (fn [c]
                             (if (:keys? c)
                               c
                               (let [{:keys [required optional aux] :as l} (:lambda c)
                                     vars (set (concat required (map first optional) (map first aux)))]
                                 (assoc c :arities
                                        (mapv (fn [a] (assoc a :values (slot-values (:slots model) vars)))
                                              (constructor-arities l))))))
                           (:constructors model)))]
    ((op backend :struct :define) model)))

;; ===========================================================================
;; The walker: with-slots and variables (D18, D29)
;; ===========================================================================
;; One code walker rewrites the names a ucl form gives meaning to inside its
;; body: with-slots' slot names and ucl/let's variables. Its env:
;;   :subst     {name {:read (fn [name] form) :place (fn [name] place-form)}}
;;   :shadowed  names rebound inside the body, which no longer refer to them
;;   :backend   the backend, to expand a registered form it does not know
;; A free reference becomes its read; a reference used as the place of
;; setf/incf/decf becomes its place. Operator position is left alone: as with
;; Common Lisp's symbol-macrolet, a slot named `next` does not capture
;; `(next xs)`. Binding forms are recognized by name; a ucl/let (bindings in a
;; list) is told apart from Clojure's let (a vector) by its bindings.
;;
;; The walker is eager (no lazy seqs): `assigned-vars` relies on its side
;; effects having happened when it returns.

(defn ^:macro-support binding-symbols
  "Every symbol a binding form introduces, destructuring included (conservative)."
  [form]
  (cond
    (symbol? form) (if (= '& form) [] [form])
    (vector? form) (mapcat binding-symbols form)
    (map? form)    (mapcat (fn [[k v]]
                             (cond (= :as k) [v]
                                   (= :or k) []
                                   (keyword? k) (mapcat binding-symbols v)
                                   :else (binding-symbols k)))
                           form)
    :else []))

(defn ^:macro-support lmap
  "An eager `map` that returns a list, the shape code is made of."
  [f xs]
  (apply list (mapv f xs)))

(defn ^:macro-support head-name
  "The name of a form's operator when it is a symbol, else nil."
  [form]
  (when (and (seq? form) (seq form) (symbol? (first form)))
    (name (first form))))

(defn ^:macro-support mentions?
  "Does `form` contain any of the symbols `syms`?"
  [form syms]
  (cond (symbol? form) (contains? syms form)
        (map? form)    (boolean (some #(mentions? % syms) (concat (keys form) (vals form))))
        (coll? form)   (boolean (some #(mentions? % syms) form))
        :else false))

;; ===========================================================================
;; The expander registry
;; ===========================================================================
;; A ucl form that expands to a loop -- ucl/dotimes, ucl/loop, a sequence
;; function -- is registered by its operator's name with
;;   {:applies? (fn [form] ..)   ; tells it apart from a namesake (Clojure's loop)
;;    :expand   (fn [backend form] expansion)}
;; Return-position assignment (D35) runs a registered form as a statement by
;; expanding it and assigning its expansion's return positions; the walker
;; expands one it does not know before walking it, so the names its expansion
;; binds shadow as they should.
;;
;; An expander is called with the backend of the macro that triggered it,
;; which may be an enclosing form: it must not read the lexical environment.
;; What needs a local's type, it emits as a ucl form (`elt`, `length`,
;; `make-array`), which expands later where the environment is right.
;;
;; An entry for a form that establishes a block (D56) also has
;;   :block (fn [form] [name])   ; the block's name, in a vector: nil is a name
;; so that an exit inside it to that name is known to be its own.
;;
;; defapi puts every entry into the backend map as `:expanders`: the
;; contract's own (`builtin-expanders`, defined last: its entries call
;; expanders defined anywhere in this file), and each vocabulary's, a fn its
;; file hands defapi in `:vocabularies`.

(defn ^:macro-support add-expanders
  "The backend map with the expanders of `vocabularies` (maps of entries)."
  [backend vocabularies]
  (update backend :expanders (fn [m] (apply merge m vocabularies))))

(defn ^:macro-support registered
  "The registry entry that applies to `form`, or nil."
  [backend form]
  (let [n (head-name form)
        e (when n (get (:expanders backend) n))]
    (when (and e ((:applies? e) form)) e)))

(defn ^:macro-support expand-registered [backend form]
  ((:expand (registered backend form)) backend form))

(defn ^:macro-support expand-counted-loop
  "A loop of `v` from 0 below `n`, which is evaluated once, running `body`
   (statements); then `result`, evaluated with `v` bound to `n` (nil if
   omitted). ucl/dotimes and every vocabulary that counts expand to this."
  [backend v n result body]
  (let [[binds lim] (if (trivial? n) [[] n] (let [g (gensym "n")] [[g n] g]))]
    (wrap-let binds
              ;; the counter is a fixnum; a host that types locals by hint
              ;; (ClojureDart) would otherwise leave it dynamic
              (list 'loop [(let [h ((op backend :types :local-hint) :fixnum)]
                             (if h (vary-meta v assoc :tag h) v))
                           0]
                    (list 'if (list '< v lim)
                          (list* 'do (concat body [(list 'recur (list 'inc v))]))
                          (cond (nil? result)                 nil
                                (mentions? result #{v})       (list 'let [v lim] result)
                                :else                         result))))))

(defn ^:macro-support var-binding-name
  "The variable of one ucl/let binding: `x`, `(x)` or `(x init)`."
  [b]
  (if (seq? b) (first b) b))

(defn ^:macro-support always-true?
  "A cond test that is always true, such as :else."
  [form]
  (or (keyword? form) (true? form)))

(defn ^:macro-support assign-tails
  "`form` as a statement whose every return position calls `assign` on the
   value it would have returned. `recur` stays a `recur`."
  [backend form assign]
  (let [tail      (fn [f] (assign-tails backend f assign))
        n         (head-name form)
        head      (when n (first form))
        args      (when n (rest form))
        body-tail (fn [forms] (if (seq forms)
                                (concat (butlast forms) [(tail (last forms))])
                                [(assign nil)]))]
    (cond
      (nil? n)          (assign form)
      (= "recur" n)     form
      (= "if" n)        (let [[c a b] args] (list 'if c (tail a) (tail b)))
      (= "if-not" n)    (let [[c a b] args] (list 'if c (tail b) (tail a)))
      (= "when" n)      (list 'if (first args) (tail (list* 'do (rest args))) (assign nil))
      (= "when-not" n)  (list 'if (first args) (assign nil) (tail (list* 'do (rest args))))
      (= "cond" n)      (if (empty? args)
                          (assign nil)
                          (let [[c e & more] args]
                            (if (always-true? c)
                              (tail e)
                              (list 'if c (tail e) (tail (list* 'cond more))))))
      (= "case" n)      (let [[e & clauses] args
                              cnt (count clauses)]
                          (list* head e (apply list (map-indexed
                                                     (fn [i c] (if (and (even? i) (< (inc i) cnt)) c (tail c)))
                                                     clauses))))
      (= "do" n)        (list* 'do (body-tail args))

      (and (contains? #{"let" "let*" "loop" "loop*"} n) (vector? (first args)))
      (list* head (first args) (body-tail (rest args)))

      (and (contains? #{"let" "let*"} n) (seq? (first args)))
      (let [[decls body] (split-with declare-form? (rest args))]
        (list* head (first args) (concat decls (body-tail body))))

      (registered backend form)
      (tail (expand-registered backend form))

      (= "with-slots" n)
      (list* head (first args) (second args) (body-tail (drop 2 args)))

      :else (assign form))))

;; ===========================================================================
;; Blocks and exits (D55, D56)
;; ===========================================================================
;; A block is compiled eagerly, by the form that establishes it: ucl/block,
;; ucl/defun and ucl/defmethod (their name), ucl/dotimes and ucl/loop (nil).
;; Every exit to it -- `(return-from name [v])`, `(return [v])` for nil -- is
;; made static, and a block nothing exits from is its body unchanged.
;;
;; Forms are rewritten from the outside in. An exit in return position is its
;; value. An exit in statement position takes the rest of its body with it:
;; `A (when c (return x)) B` becomes `A (if c x (do B))`, the rest pushed into
;; the branch that does not exit -- unless that would copy it (several
;; branches complete normally), or the rest cannot follow it there (a loop,
;; which repeats; a `let` that would shadow the rest's names). Then the exit
;; assigns a result and a flag instead, ends what it is in -- a loop, by not
;; recurring -- and the rest is guarded: `(if <flag> <result> rest)`.
;;
;; An exit anywhere else -- an argument, a binding's init, an `if` test, an
;; `fn` -- does not compile (D55). A registered form in the way (ucl/dotimes,
;; ucl/loop, a sequence function) is expanded first; one that establishes a
;; block of the same name has its own exits. Exits to other blocks are left
;; alone: an enclosing block was compiled first, and an inner one compiles its
;; own when it expands.

(defn ^:macro-support exit-target
  "[name value-form] of an exit form, or nil."
  [form]
  (case (head-name form)
    "return-from" (do (when-not (<= 2 (count form) 3)
                        (fail! (str "return-from takes a block name and an optional value: "
                                    (pr-str form))
                               {:form form}))
                      [(second form) (nth form 2 nil)])
    "return"      (do (when (> (count form) 2)
                        (fail! (str "return takes an optional value: " (pr-str form)) {:form form}))
                      [nil (second form)])
    nil))

(defn ^:macro-support exit-text
  "How an exit to block `name` reads in a message."
  [name]
  (if (nil? name) "(return ...)" (str "(return-from " name " ...)")))

(defn ^:macro-support shadows-block?
  "Does `form` establish a block named `name` -- so exits inside are its own?"
  [backend name form]
  (let [e (registered backend form)]
    (boolean (when (and e (:block e)) (= [name] ((:block e) form))))))

(defn ^:macro-support exits?
  "Does `form` contain an exit to the block `name`?"
  [backend name form]
  (cond
    (and (seq? form) (seq form))
    (let [t (exit-target form)]
      (cond
        (and t (= name (first t)))         true
        (= "quote" (head-name form))       false
        (shadows-block? backend name form) false
        :else (boolean (some #(exits? backend name %) form))))
    (map? form)    (boolean (some #(exits? backend name %) (concat (keys form) (vals form))))
    (coll? form)   (boolean (some #(exits? backend name %) form))
    :else false))

(defn ^:macro-support check-no-escape
  "Fail if an exit to `name` inside `form` would leave a function (D55)."
  [backend name form]
  (let [n (head-name form)
        in-fn (fn [what]
                (fail! (str (exit-text name) " is inside " what ": an exit cannot leave a "
                            "function (D55). Return a value from the function and test it "
                            "where the block is.")
                       {:block name :form form}))]
    (cond
      (and (seq? form) (seq form))
      (cond
        (or (= "quote" n) (shadows-block? backend name form)) nil
        (contains? #{"fn" "fn*" "reify" "proxy" "deftype" "defrecord" "defn"} n)
        (when (exits? backend name form) (in-fn (str "(" n " ...)")))
        (= "letfn" n)
        (do (when (exits? backend name (second form)) (in-fn "a letfn function"))
            (doseq [f (nnext form)] (check-no-escape backend name f)))
        :else (doseq [f form] (check-no-escape backend name f)))
      (map? form) (doseq [f (concat (keys form) (vals form))] (check-no-escape backend name f))
      (coll? form) (doseq [f form] (check-no-escape backend name f))
      :else nil)))

(defn ^:macro-support seq-do
  "(do a b), flattening b's own do."
  [a b]
  (if (and (seq? b) (= "do" (head-name b)))
    (list* 'do a (rest b))
    (list 'do a b)))

(defn ^:macro-support expand-block
  "One form: `body` as the block `name`, every exit to it static (D55)."
  [backend name body]
  (if-not (some #(exits? backend name %) body)
    (if (next body) (list* 'do body) (first body))
    (let [_     (doseq [f body] (check-no-escape backend name f))
          flag  (gensym "exited")
          res   (gensym "result")
          used  (atom false)
          read  (op backend :var :read)
          write (op backend :var :write)
          bind  (op backend :var :bind)
          ex?   (fn [form] (exits? backend name form))
          no-ex (fn [form where]
                  (when (ex? form)
                    (fail! (str (exit-text name) " is in " where ": ucl compiles an exit only in "
                                "statement or return position (D55). Test a value there and exit "
                                "from a statement.")
                           {:block name :form form})))
          exited? (fn [] (reset! used true) (list '== (read [flag :fixnum] [flag :fixnum]) 1))
          result  (fn [] (read [res nil] [res nil]))
          ;; mode :direct -- an exit's value is the value of what it is in;
          ;; mode :flag   -- an exit assigns result and flag, and is nil.
          exit-v  (fn [mode v]
                    (if (= mode :direct)
                      v
                      (do (reset! used true)
                          (list 'do (write [res nil] [res nil] v) (write [flag :fixnum] [flag :fixnum] 1) nil))))
          after   (fn [mode] (if (= mode :direct) (result) nil))
          trivial-k? (fn [k] (or (= k :ret) (trivial? k)))]
      (letfn [;; How many ways `form` completes without exiting.
              (paths [form]
                (if-not (ex? form)
                  1
                  (let [n (head-name form) args (rest form)]
                    (cond
                      (exit-target form) 0
                      (= "if" n)       (+ (paths (second args)) (paths (nth args 2 nil)))
                      (= "if-not" n)   (+ (paths (second args)) (paths (nth args 2 nil)))
                      (= "when" n)     (inc (paths (list* 'do (rest args))))
                      (= "when-not" n) (inc (paths (list* 'do (rest args))))
                      (= "cond" n)     (let [cs (partition-all 2 args)]
                                         (+ (reduce + (map #(paths (second %)) cs))
                                            (if (some #(always-true? (first %)) cs) 0 1)))
                      (= "case" n)     (let [cl (rest args)]
                                         (+ (reduce + (map paths (take-nth 2 (rest cl))))
                                            (if (odd? (count cl)) (paths (last cl)) 1)))
                      (= "do" n)       (reduce * 1 (map paths args))
                      (registered backend form) (paths (expand-registered backend form))
                      :else 1))))
              ;; `forms` in order, then `k`: a form, or :ret for "their value".
              (tseq [forms k mode]
                (cond
                  (empty? forms)        (if (= k :ret) nil k)
                  (empty? (rest forms)) (t (first forms) k mode)
                  :else (let [[f & more] forms
                              rest-form (tseq more k mode)]
                          (if (ex? f) (t f rest-form mode) (seq-do f rest-form)))))
              ;; `form`, then `k`, by setting a flag where `k` cannot follow.
              (guard [form k mode]
                (list 'do (t form nil :flag) (list 'if (exited?) (after mode) k)))
              ;; Branches that each continue with `k`.
              (branch [form k mode build]
                (if (or (trivial-k? k) (<= (paths form) 1))
                  (build k mode)
                  (guard form k mode)))
              ;; A body that binds `names` and then continues with `k`.
              (bound [form k mode names build]
                (if (or (= k :ret) (not (mentions? k (set names))))
                  (build k mode)
                  (guard form k mode)))
              (check-bindings [bs what]
                (doseq [[_ init] (partition 2 bs)] (no-ex init (str "a binding of " what))))
              (t [form k mode]
                (if-not (ex? form)
                  (if (= k :ret) form (seq-do form k))
                  (let [n (head-name form) [head & args] form]
                    (cond
                      (exit-target form)
                      (let [[_ v] (exit-target form)]
                        (no-ex v "the value of an exit")
                        (exit-v mode v))

                      (contains? #{"if" "if-not"} n)
                      (let [[c a b] args
                            [a b] (if (= "if" n) [a b] [b a])]
                        (no-ex c (str "the test of " n))
                        (branch form k mode
                                (fn [k mode] (list 'if c (t a k mode) (t b k mode)))))

                      (= "when" n)     (t (list 'if (first args) (list* 'do (rest args)) nil) k mode)
                      (= "when-not" n) (t (list 'if (first args) nil (list* 'do (rest args))) k mode)

                      (= "cond" n)
                      (t (reduce (fn [else [c e]] (if (always-true? c) e (list 'if c e else)))
                                 nil (reverse (partition 2 args)))
                         k mode)

                      (= "case" n)
                      (let [[e & cl] args]
                        (no-ex e "the key of case")
                        (branch form k mode
                                (fn [k mode]
                                  (list* head e (map-indexed (fn [i c] (if (and (even? i) (< (inc i) (count cl)))
                                                                         c (t c k mode)))
                                                             cl)))))

                      (= "do" n) (tseq args k mode)

                      (and (contains? #{"let" "let*"} n) (vector? (first args)))
                      (let [[bs & body] args]
                        (check-bindings bs n)
                        (bound form k mode (mapcat binding-symbols (take-nth 2 bs))
                               (fn [k mode] (list head bs (tseq body k mode)))))

                      (and (contains? #{"loop" "loop*"} n) (vector? (first args)))
                      (let [[bs & body] args]
                        (check-bindings bs n)
                        (cond
                          (= k :ret) (list head bs (tseq body :ret mode))
                          ;; a literal after the loop -- `nil`, `-1` -- joins its
                          ;; normal ends: nothing can shadow it or recur in it
                          (and (= mode :direct) (literal? k))
                          (list head bs (tseq [(assign-tails backend (list* 'do body)
                                                             (fn [x] (if (exit-target x) x (seq-do x k))))]
                                              :ret mode))
                          (and (= mode :flag) (trivial? k)) (seq-do (list head bs (tseq body :ret :flag)) k)
                          :else (guard form k mode)))

                      (and (contains? #{"let" "let*"} n) (seq? (first args)))
                      (let [[bs & more] args
                            [decls body] (split-with declare-form? more)]
                        (doseq [b bs] (when (seq? b) (no-ex (second b) (str "a binding of ucl/" n))))
                        (bound form k mode (map var-binding-name bs)
                               (fn [k mode] (list* head bs (concat decls [(tseq body k mode)])))))

                      (= "with-slots" n)
                      (let [[slots obj & body] args]
                        (no-ex obj "the object of with-slots")
                        (bound form k mode (map var-binding-name slots)
                               (fn [k mode] (list head slots obj (tseq body k mode)))))

                      (registered backend form)
                      (t (expand-registered backend form) k mode)

                      :else
                      (no-ex form (str "an argument of (" (if (symbol? head) head "...") " ...)"))))))]
        (let [out (tseq body :ret :direct)]
          (if @used
            (list 'let (into (bind flag :fixnum 0 {:internal? true}) (bind res nil nil {:internal? true}))
                  out)
            out))))))

(defn ^:macro-support expand-block-body
  "`body` (forms) as the block `name`: unchanged when nothing exits to it."
  [backend name body]
  (if (some #(exits? backend name %) body)
    [(expand-block backend name body)]
    body))

(defn ^:macro-support expand-stray-exit
  "An exit no block compiled: it has no block, or none could make it static."
  [form]
  (let [[name] (exit-target form)]
    (fail! (str (exit-text name) " has no enclosing block named " (pr-str name)
                (when (nil? name) " (ucl/dotimes or ucl/loop)")
                ", or is in a position ucl cannot compile statically (D55): "
                (pr-str form))
           {:form form})))

(defn ^:macro-support expand-dotimes
  "(dotimes (var count [result]) body...): var runs from 0 below count, which
   is evaluated once; result is evaluated with var bound to count."
  [backend spec body]
  (when-not (and (seq? spec) (symbol? (first spec)) (<= 2 (count spec) 3))
    (fail! (str "dotimes takes (dotimes (var count [result]) body...), not " (pr-str spec))
           {:spec spec}))
  (let [[v n result] spec
        [decls body] (split-with declare-form? body)]
    (declared-types decls)
    (expand-block backend nil [(expand-counted-loop backend v n result body)])))

(defn ^:macro-support shadow [env syms]
  (update env :shadowed into syms))

(defn ^:macro-support vector-binder? [n]
  (contains? #{"let" "let*" "loop" "loop*" "binding" "doseq" "for" "dotimes"
               "when-let" "if-let" "when-some" "if-some" "when-first" "with-open"} n))

(defn ^:macro-support modify-op? [n]
  (contains? #{"setf" "incf" "decf"} n))

(defn ^:macro-support ref? [env form]
  (and (symbol? form)
       (contains? (:subst env) form)
       (not (contains? (:shadowed env) form))))

(defn ^:macro-support walk
  "Rewrite `form` per `env`; see above. Metadata on rebuilt forms is kept.
   One fn whose parts are a letfn: they are mutually recursive, and
   ClojureDart's macro host has no forward declarations."
  [env form]
  (letfn [;; Walk a Clojure binding vector; each init sees the names bound before it.
          ;; In `for`/`doseq`, :let is a nested binding vector and :when/:while an
          ;; expression.
          (walk-bindings
            [env bindings]
            (loop [bs (partition 2 bindings) env env out []]
              (if (empty? bs)
                [env out]
                (let [[pat init] (first bs)]
                  (cond
                    (= :let pat)
                    (let [[env' v] (walk-bindings env init)]
                      (recur (rest bs) env' (conj out pat v)))

                    (keyword? pat)
                    (recur (rest bs) env (conj out pat (walk env init)))

                    :else
                    (recur (rest bs) (shadow env (binding-symbols pat)) (conj out pat (walk env init))))))))
          ;; `([params] body...)` or `[params] body...` of a fn.
          (walk-fn-tail
            [env tail]
            (let [[params & body] tail
                  env (shadow env (binding-symbols params))]
              (cons params (lmap #(walk env %) body))))
          ;; The arities of a `fn`, after its optional name.
          (walk-fn
            [env more]
            (if (vector? (first more))
              (walk-fn-tail env more)
              (lmap #(walk-fn-tail env %) more)))
          ;; A ucl/let or ucl/let* not yet expanded: each init sees the outer names
          ;; (ucl/let) or also those bound before it (ucl/let*); the body sees them all.
          (walk-ucl-let
            [env head bindings body]
            (let [sequential? (= "let*" (name head))
                  [env' bs] (reduce (fn [[e out] b]
                                      (let [b' (if (and (seq? b) (next b))
                                                 (list (first b) (walk (if sequential? e env) (second b)))
                                                 b)]
                                        [(shadow e [(var-binding-name b)]) (conj out b')]))
                                    [env []] bindings)]
              (list* head (apply list bs) (lmap #(walk env' %) body))))
          (walk-seq [env form]
            (let [[head & args] form
                  n (head-name form)]
              (cond
                (contains? #{"quote" "declare"} n) form

                (and (vector-binder? n) (vector? (first args)))
                (let [[bindings & body] args
                      [env' bindings'] (walk-bindings env bindings)]
                  (list* head bindings' (lmap #(walk env' %) body)))

                (and (contains? #{"let" "let*"} n) (seq? (first args)))
                (walk-ucl-let env head (first args) (rest args))

                (and (= "dotimes" n) (seq? (first args)))
                (let [[[v cnt & result] & body] args
                      env' (shadow env [v])]
                  (list* head (list* v (walk env cnt) (lmap #(walk env' %) result))
                         (lmap #(walk env' %) body)))

                (and (= "with-slots" n) (sequential? (first args)))
                (let [[slots obj & body] args
                      env' (shadow env (map var-binding-name slots))]
                  (list* head slots (walk env obj) (lmap #(walk env' %) body)))

                (contains? #{"fn" "fn*"} n)
                (let [[fname & more] (if (symbol? (first args)) args (cons nil args))
                      env (if fname (shadow env [fname]) env)
                      tail (walk-fn env more)]
                  (if fname (list* head fname tail) (cons head tail)))

                (= "letfn" n)
                (let [[specs & body] args
                      env' (shadow env (map first specs))]
                  (list* head (mapv (fn [[f & more]] (cons f (walk-fn env' more))) specs)
                         (lmap #(walk env' %) body)))

                (= "case" n)
                (let [[e & clauses] args
                      cnt (count clauses)]
                  ;; test constants are not evaluated
                  (list* head (walk env e)
                         (apply list (map-indexed (fn [i c] (if (and (even? i) (< (inc i) cnt)) c (walk env c)))
                                                  clauses))))

                (= "catch" n)
                (let [[cls e & body] args]
                  (list* head cls e (lmap #(walk (shadow env [e]) %) body)))

                (modify-op? n)
                (cons head (apply list
                                  (map-indexed
                                   (fn [i a]
                                     (if (and (ref? env a) (if (= "setf" n) (even? i) (zero? i)))
                                       ((:place (get (:subst env) a)) a)
                                       (walk env a)))
                                   args)))

                (and (:backend env) (registered (:backend env) form))
                (walk env (expand-registered (:backend env) form))

                :else (cons (if (symbol? head) head (walk env head)) (lmap #(walk env %) args)))))
          (walk [env form]
          (cond
            (ref? env form) ((:read (get (:subst env) form)) form)
            (and (seq? form) (seq form)) (let [out (walk-seq env form)]
                                           (if (meta form) (with-meta out (meta form)) out))
            (vector? form) (let [out (mapv #(walk env %) form)]
                             (if (meta form) (with-meta out (meta form)) out))
            (map? form)    (into {} (map (fn [[k v]] [(walk env k) (walk env v)]) form))
            (set? form)    (set (map #(walk env %) form))
            :else form))]
    (walk env form)))

(defn ^:macro-support expand-with-slots [backend slots obj body]
  (when-not (or (seq? slots) (vector? slots))
    (fail! "with-slots needs a slot list, as in (with-slots (a b) obj ...)" {:slots slots}))
  (let [entries (map (fn [s] (if (seq? s) [(first s) (second s)] [s s])) slots)
        [binds o] (if (trivial? obj) [[] obj] (let [g (gensym "obj")] [[g obj] g]))
        read    (op backend :slot :read)
        env     {:subst    (into {} (map (fn [[v s]]
                                           [v {:read  (fn [_] (read [o s] [obj s]))
                                               :place (fn [_] (list 'slot-value o (list 'quote s)))}])
                                         entries))
                 :shadowed #{}
                 :backend  backend}]
    (apply wrap-let binds (lmap #(walk env %) body))))

;; ===========================================================================
;; Variables: ucl/let, ucl/let*, assignable parameters, ucl/dotimes (D29-D36)
;; ===========================================================================
;; A variable is a name ucl binds that setf/incf/decf can assign. The walker
;; rewrites its reads into the backend's read and its uses as a place into
;; `(%var name type)`, so setf reaches it through the place machinery.
;;
;; Return-position assignment: a compound init (a loop, let, do, if, ...) of a
;; ucl/let is not bound as a value -- on Squint a loop or let in expression
;; position is an IIFE, and on the JVM a loop there is wrapped in a fn.
;; Instead a fresh variable is declared, the init runs as a statement, and
;; every return position of the init assigns that variable.

(defn ^:macro-support var-env
  "The walker env making each of `vars` a variable of its declared type."
  [backend vars types]
  (let [read (op backend :var :read)]
    {:subst (into {} (map (fn [v]
                            (let [t (get types v)]
                              [v {:read  (fn [_] (read [v t] [v t]))
                                  :place (fn [_] (list '%var v t))}]))
                          vars))
     :shadowed #{}
     :backend  backend}))

(defn ^:macro-support assigned-vars
  "The names among `candidates` that `body` assigns with setf/incf/decf."
  [backend candidates body]
  (let [hits (atom #{})
        env  {:subst (into {} (map (fn [v] [v {:read  (fn [s] s)
                                                :place (fn [s] (swap! hits conj s) s)}])
                                   candidates))
              :shadowed #{}
              :backend  backend}]
    (doseq [f body] (walk env f))
    @hits))

(defn ^:macro-support compound?
  "An init that ucl/let runs as a statement rather than binding as a value."
  [backend form]
  (or (contains? #{"if" "if-not" "when" "when-not" "cond" "case" "do" "let" "let*"
                   "loop" "loop*" "with-slots"}
                 (head-name form))
      (some? (registered backend form))))

(defn ^:macro-support parse-var-bindings
  "ucl/let bindings -- `((x init) (y) z)` -- as [[var init] ...]."
  [what bindings]
  (when-not (seq? bindings)
    (fail! (str what " takes a list of bindings, as in (" what " ((x 0) (y 1)) ...), not "
                (pr-str bindings))
           {:bindings bindings}))
  (mapv (fn [b]
          (cond
            (symbol? b) [b nil]
            (and (seq? b) (symbol? (first b)) (<= 1 (count b) 2)) [(first b) (second b)]
            :else (fail! (str what ": malformed binding " (pr-str b)) {:binding b})))
        bindings))

(defn ^:macro-support placeholder
  "The value a variable holds before its compound init assigns it."
  [t]
  (if (integer-type? t) 0 nil))

(defn ^:macro-support expand-parallel
  "ucl/let: bind `bs` ([[var init] ..]) in parallel, then run `body` with
   them as variables. Inits run left to right and see only outer names."
  [backend bs types body]
  (let [names (mapv first bs)
        bind  (op backend :var :bind)
        read  (op backend :var :read)
        write (op backend :var :write)
        plan  (vec (map-indexed
                    (fn [i [v init]]
                      (cond
                        (compound? backend init)
                        {:v v :init init :step :compound :g (gensym (name v))}

                        ;; bound to a temporary when the final binding would see
                        ;; an earlier variable, or would run after a later
                        ;; compound init's statement
                        (and (not (literal? init))
                             (or (mentions? init (set (take i names)))
                                 (some (fn [[_ x]] (compound? backend x)) (drop (inc i) bs))))
                        {:v v :init init :step :temp :g (gensym (name v))}

                        :else {:v v :init init :step :direct}))
                    bs))
        final (vec (mapcat (fn [{:keys [v init step g]}]
                             (let [t (get types v)]
                               (bind v t (case step
                                           :direct   (check-value backend t init)
                                           :temp     (check-value backend t g)
                                           :compound (read [g t] [g t]))
                                     {})))
                           plan))
        env   (var-env backend names types)
        inner (list* 'let final (lmap #(walk env %) body))]
    (reduce (fn [inner {:keys [v init step g]}]
              (let [t (get types v)]
                (case step
                  :direct inner
                  :temp   (list 'let [g init] inner)
                  :compound
                  (list 'let (bind g t (placeholder t) {:internal? true})
                        (assign-tails backend init
                                      (fn [x] (write [g t] [g t] (check-value backend t x))))
                        inner))))
            inner
            (reverse plan))))

(defn ^:macro-support expand-let
  "ucl/let (parallel) and ucl/let* (sequential)."
  [backend sequential? bindings body]
  (let [what (if sequential? "let*" "let")
        bs (parse-var-bindings what bindings)
        [decls body] (split-with declare-form? body)
        types (declared-types decls)]
    (doseq [[v init] bs]
      (when (and (integer-type? (get types v)) (nil? init))
        (fail! (str what ": the variable " v " is declared " (pr-str (get types v))
                    " but starts as nil. Give it an initial value.")
               {:var v})))
    (if sequential?
      (reduce (fn [inner b] (expand-parallel backend [b] types [inner]))
              (if (next body) (list* 'do body) (first body))
              (reverse bs))
      (expand-parallel backend bs types body))))

(defn ^:macro-support with-assignable
  "`body`, with every parameter among `params` that it assigns made a variable."
  [backend params types body]
  (let [assigned (assigned-vars backend params body)]
    (if (empty? assigned)
      body
      [(expand-parallel backend (mapv (fn [p] [p p]) (filter #(contains? assigned %) params))
                        types body)])))

(defn ^:macro-support expand-defun [backend fname lambda-list body]
  (let [{:keys [required]} (parse-lambda-list "defun" lambda-list #{})
        [doc decls body] (split-body body)
        types  (declared-types decls)
        params (hint-params backend required types)]
    (list* 'defn fname (concat (when doc [doc]) [params]
                               (with-assignable backend required types
                                                (expand-block-body backend fname body))))))

;; setf needs assign-tails (D35). ClojureDart's macro host has no forward
;; declarations (its `declare` is a no-op), so the contract defines every fn
;; before its first use.

(defn ^:macro-support expand-simple-write [backend kind once? binds refs srcs value]
  (let [value (if (= kind :var) (check-value backend (second refs) value) value)
        [binds v] (if (or once? (trivial? value))
                    [binds value]
                    (let [g (gensym "v")] [(conj binds g value) g]))]
    (wrap-let binds ((op backend kind :write) refs srcs v))))

(defn ^:macro-support expand-write
  "One `(setf place value)`. The backend's write form evaluates to the value.
   A variable assigned a compound value -- a loop, let, if, ... -- takes it
   by return-position assignment (D35): a variable's place has no subforms,
   so nothing is reordered."
  [backend place value]
  (let [{:keys [kind kinds]} (place-spec place)
        once? (get-in backend [kind :write-once?])
        [binds refs srcs] (plan-args kinds (rest place) place (not once?))]
    (if (and (= kind :var) (compound? backend value))
      (assign-tails backend value
                    (fn [x] ((op backend kind :write) refs srcs (check-value backend (second refs) x))))
      (expand-simple-write backend kind once? binds refs srcs value))))

(defn ^:macro-support expand-setf
  "`(setf p1 v1 p2 v2 ...)`: assign left to right, return the last value."
  [backend pairs]
  (when (or (empty? pairs) (odd? (count pairs)))
    (fail! "setf takes place/value pairs" {:form (cons 'setf pairs)}))
  (let [writes (map (fn [[p v]] (expand-write backend p v)) (partition 2 pairs))]
    (if (next writes) (cons 'do writes) (first writes))))

;; ===========================================================================
;; defmethod (D18): single static dispatch on the first parameter
;; ===========================================================================

(defn ^:macro-support expand-defmethod [backend mname lambda-list body]
  (let [[recv & more] (seq lambda-list)]
    (when-not (and (seq? recv) (= 2 (count recv)) (symbol? (first recv)) (symbol? (second recv)))
      (fail! (str "defmethod " mname ": the first parameter must be specialized, as in ((this "
                  "NumArray) left right)")
             {:lambda-list lambda-list}))
    (let [{:keys [required]} (parse-lambda-list (str "defmethod " mname) (if (some? more) more ()) #{})
          [doc decls body] (split-body body)
          [self struct] recv
          types  (declared-types decls)
          params (hint-params backend required types)]
      ((op backend :method :define)
       {:name mname :self self :struct struct :params params :doc doc
        :body (with-assignable backend required types
                               (expand-block-body backend mname body))}))))

;; ===========================================================================
;; defapi / defruntime: a backend's whole API surface (D26)
;; ===========================================================================
;; `emit-form` is pasted, unevaluated, into every generated macro, so a backend
;; whose emitters read the compiler environment passes `(emit &env)`.
;;
;; Generated macros call this namespace through the alias `contract`, which
;; every backend declares: that alias resolves wherever the backend's macros are
;; compiled, whatever a host names its macro-time namespace (H20).

;; Every vocabulary's operator names: defapi defines a macro for each, which
;; expands through that name's registry entry. A literal list, as api-names:
;; a top-level def that is not one would ride into every submission (H31).
(defn ^:macro-support vocabulary-names []
  '[loop count count-if count-if-not find find-if find-if-not
    position position-if position-if-not reduce every some notany notevery
    fill replace copy-seq subseq reverse nreverse sort stable-sort])

(defn ^:macro-support expand-vocabulary
  "A vocabulary macro's expansion: its name's registry entry, called on the
   whole form. No `:applies?`: the macro is already the right form."
  [backend form]
  (let [e (get (:expanders backend) (head-name form))]
    (if e
      ((:expand e) backend form)
      (fail! (str "no vocabulary in this backend defines " (first form)
                  " -- is it in defapi's :vocabularies?")
             {:form form}))))

;; The contract's own registry entries (I23), defined last: they call
;; expanders defined anywhere above.
(defn ^:macro-support builtin-expanders []
  {"dotimes" {:applies? (fn [form] (seq? (second form)))
              :expand   (fn [backend form] (expand-dotimes backend (second form) (nnext form)))
              :block    (fn [_] [nil])}
   "block"   {:applies? (fn [form] (and (next form) (or (nil? (second form)) (symbol? (second form)))))
              :expand   (fn [backend form] (expand-block backend (second form) (nnext form)))
              :block    (fn [form] [(second form)])}})

(def ^:macro-support api-names
  '[elt length vector-push-extend make-array
    gethash make-hash-table slot-value
    setf incf decf let let* dotimes block return-from return
    defun defstruct defmethod with-slots
    princ-to-string])

(defmacro defapi
  "Generate every contract macro in the calling namespace.
   `opts` -- `{:inline-extrema? true}` when the host defines min/max as inline
   functions instead of macros; `:vocabularies`, the fns (symbols, through an
   alias the backend declares) returning each vocabulary's registry entries."
  ([emit-form] `(defapi ~emit-form {}))
  ([emit-form opts]
   (let [emit-form `(~'contract/add-expanders
                     ~emit-form
                     [(~'contract/builtin-expanders) ~@(map list (:vocabularies opts))])]
   `(do
      (defmacro ~'elt
        "(elt sequence index) -- read an element; a place for setf."
        [~'sequence ~'index]
        (~'contract/expand-read ~emit-form (list '~'elt ~'sequence ~'index)))
      (defmacro ~'gethash
        "(gethash key hash-table [default]) -- a place for setf."
        [~'key ~'hash-table & ~'default]
        (~'contract/expand-read ~emit-form (list* '~'gethash ~'key ~'hash-table ~'default)))
      (defmacro ~'slot-value
        "(slot-value object 'slot) -- a place for setf."
        [~'object ~'slot]
        (~'contract/expand-read ~emit-form (list '~'slot-value ~'object ~'slot)))
      (defmacro ~'setf
        "(setf place value ...) -- assign; returns the last value."
        [& ~'pairs]
        (~'contract/expand-setf ~emit-form ~'pairs))
      (defmacro ~'incf
        "(incf place [delta]) -- returns the new value."
        ([~'place] (~'contract/expand-modify ~emit-form '~'+ ~'place nil))
        ([~'place ~'delta] (~'contract/expand-modify ~emit-form '~'+ ~'place ~'delta)))
      (defmacro ~'decf
        "(decf place [delta]) -- returns the new value."
        ([~'place] (~'contract/expand-modify ~emit-form '~'- ~'place nil))
        ([~'place ~'delta] (~'contract/expand-modify ~emit-form '~'- ~'place ~'delta)))
      (defmacro ~'length
        "(length sequence) -- respects the fill pointer."
        [~'sequence]
        (~'contract/expand-length ~emit-form ~'sequence))
      (defmacro ~'vector-push-extend
        "(vector-push-extend new-element vector) -- returns the new element's index."
        [& ~'args]
        (~'contract/expand-vector-push-extend ~emit-form ~'args))
      (defmacro ~'make-array
        "(make-array n &key element-type initial-element initial-contents adjustable fill-pointer)"
        [~'n & ~'opts]
        (~'contract/expand-make-array ~emit-form ~'n ~'opts))
      (defmacro ~'make-hash-table
        "(make-hash-table &key test size initial-contents)"
        [& ~'opts]
        (~'contract/expand-make-hash-table ~emit-form ~'opts))
      (defmacro ~'defun
        "(defun name (params) [doc] (declare ...) body...)"
        [~'fname ~'lambda-list & ~'body]
        (~'contract/expand-defun ~emit-form ~'fname ~'lambda-list ~'body))
      (defmacro ~'defstruct
        "(defstruct (Name (:constructor Name (args &optional .. &aux ..))) slot...)"
        [~'name-and-options & ~'slots]
        (~'contract/expand-defstruct ~emit-form ~'name-and-options ~'slots))
      (defmacro ~'defmethod
        "(defmethod name ((self Struct) params...) [doc] (declare ...) body...)"
        [~'mname ~'lambda-list & ~'body]
        (~'contract/expand-defmethod ~emit-form ~'mname ~'lambda-list ~'body))
      (defmacro ~'let
        "(let ((var init)...) (declare ...) body...) -- bind in parallel; the
         variables can be assigned with setf, incf and decf."
        [~'bindings & ~'body]
        (~'contract/expand-let ~emit-form false ~'bindings ~'body))
      (defmacro ~'let*
        "(let* ((var init)...) (declare ...) body...) -- bind in order; the
         variables can be assigned with setf, incf and decf."
        [~'bindings & ~'body]
        ;; A binding vector is Clojure's special form, reaching this macro on a
        ;; host that expands macros before special forms -- ClojureDart, for the
        ;; `let*` its own compiler emits inside ucl.api. Returned unchanged, it
        ;; ends expansion there.
        (if (vector? ~'bindings)
          ~'&form
          (~'contract/expand-let ~emit-form true ~'bindings ~'body)))
      (defmacro ~'dotimes
        "(dotimes (var count [result]) (declare ...) body...)"
        [~'spec & ~'body]
        (~'contract/expand-dotimes ~emit-form ~'spec ~'body))
      (defmacro ~'block
        "(block name body...) -- `return-from` name leaves it with a value."
        [~'block-name & ~'body]
        (~'contract/expand-block ~emit-form ~'block-name ~'body))
      (defmacro ~'return-from
        "(return-from name [value]) -- leave the enclosing block `name`."
        [~'block-name & ~'value]
        (~'contract/expand-stray-exit (list* '~'return-from ~'block-name ~'value)))
      (defmacro ~'return
        "(return [value]) -- leave the enclosing block nil (ucl/dotimes, ucl/loop)."
        [& ~'value]
        (~'contract/expand-stray-exit (list* '~'return ~'value)))
      (defmacro ~'princ-to-string
        "(princ-to-string object) -- an integer's decimal digits, or a string itself."
        [~'object]
        (~'contract/expand-princ-to-string ~emit-form ~'object))
      (defmacro ~'with-slots
        "(with-slots (slot...) object body...)"
        [~'slots ~'object & ~'body]
        (~'contract/expand-with-slots ~emit-form ~'slots ~'object ~'body))
      ~@(map (fn [n]
               `(defmacro ~n [& ~'args]
                  (~'contract/expand-vocabulary ~emit-form ~'&form)))
             (vocabulary-names))
      ~@(when-not (:inline-extrema? opts)
          [`(defmacro ~'min
              "(min real...) -- inline in call position, a function as a value."
              [& ~'args]
              (~'contract/expand-extremum ~emit-form :min ~'args))
           `(defmacro ~'max
              "(max real...) -- inline in call position, a function as a value."
              [& ~'args]
              (~'contract/expand-extremum ~emit-form :max ~'args))])))))

(defmacro defruntime
  "The contract's run-time definitions, in the calling namespace.
   `host` -- {:positive-infinity form :negative-infinity form
              :min-inline form :max-inline form}; the inline forms, when given,
   become `:inline` metadata (a host whose min/max are inline functions)."
  [host]
  (let [extremum (fn [nm cmp inline]
                   (let [doc (str "(" nm " real...) -- returns the "
                                  (if (= nm 'min) "least" "greatest") " argument.")]
                     `(defn ~nm
                        ~doc
                        ~@(when inline [{:inline inline :inline-arities 'clojure.core/pos?}])
                        ([~'a] ~'a)
                        ([~'a ~'b] (if (~cmp ~'a ~'b) ~'a ~'b))
                        ([~'a ~'b & ~'more] (reduce ~nm (~nm ~'a ~'b) ~'more)))))]
    `(do
       (def ~(vary-meta 'most-positive-fixnum assoc :const true) 2147483647)
       (def ~(vary-meta 'most-negative-fixnum assoc :const true) -2147483648)
       (def ~'double-float-positive-infinity ~(:positive-infinity host))
       (def ~'double-float-negative-infinity ~(:negative-infinity host))
       ~(extremum 'min '< (:min-inline host))
       ~(extremum 'max '> (:max-inline host)))))
