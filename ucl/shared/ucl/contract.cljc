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
;;   [:vector E]               simple vector, E one of :t :fixnum :sb53
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
    :else (fail! (str "unsupported element type " (pr-str spec)
                      ". v1 supports t, fixnum and (signed-byte 53).")
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
   "slot-value" {:kind :slot    :args [:runtime :slot]}})

(defn ^:macro-support place-spec [place]
  (when (symbol? place)
    (fail! (str "setf of the variable `" place "`: Clojure locals are immutable."
                " Rebind it with loop/recur.")
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

(defn ^:macro-support expand-write
  "One `(setf place value)`. The backend's write form evaluates to the value."
  [backend place value]
  (let [{:keys [kind kinds]} (place-spec place)
        once? (get-in backend [kind :write-once?])
        [binds refs srcs] (plan-args kinds (rest place) place (not once?))
        [binds v] (if (or once? (trivial? value))
                    [binds value]
                    (let [g (gensym "v")] [(conj binds g value) g]))]
    (wrap-let binds ((op backend kind :write) refs srcs v))))

(defn ^:macro-support expand-setf
  "`(setf p1 v1 p2 v2 ...)`: assign left to right, return the last value."
  [backend pairs]
  (when (or (empty? pairs) (odd? (count pairs)))
    (fail! "setf takes place/value pairs" {:form (cons 'setf pairs)}))
  (let [writes (map (fn [[p v]] (expand-write backend p v)) (partition 2 pairs))]
    (if (next writes) (cons 'do writes) (first writes))))

(defn ^:macro-support expand-modify
  "`(incf place [delta])` / `(decf place [delta])`: read, combine, write; the
   place's arguments evaluated once. At safety >= 1 the old value must be a
   number -- SBCL signals on (incf nil) (D2)."
  [backend op-sym place delta-form]
  (let [{:keys [kind kinds]} (place-spec place)
        [binds refs srcs] (plan-args kinds (rest place) place true)
        old   ((op backend kind :read) refs srcs)
        old   (if (pos? (safety backend)) ((op backend :number :check) old) old)
        delta (if (some? delta-form) delta-form 1)
        new   (list op-sym old delta)]
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

(defn ^:macro-support expand-defun [backend fname lambda-list body]
  (let [{:keys [required]} (parse-lambda-list "defun" lambda-list #{})
        [doc decls body] (split-body body)
        params (hint-params backend required (declared-types decls))]
    (list* 'defn fname (concat (when doc [doc]) [params] body))))

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
;; with-slots: a code walker that respects local shadowing
;; ===========================================================================

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

(declare walk-slots)

(defn ^:macro-support walk-bindings
  "Walk a let/loop binding vector; each init sees the names bound before it."
  [env bindings]
  (loop [bs (partition 2 bindings) env env out []]
    (if (empty? bs)
      [env out]
      (let [[pat init] (first bs)]
        (recur (rest bs)
               (update env :shadowed into (binding-symbols pat))
               (conj out pat (walk-slots env init)))))))

(defn ^:macro-support walk-fn-tail
  "`([params] body...)` or `[params] body...` of a fn."
  [env tail]
  (let [[params & body] tail
        env (update env :shadowed into (binding-symbols params))]
    (cons params (map #(walk-slots env %) body))))

(defn ^:macro-support modify-op? [head]
  (and (symbol? head) (contains? #{"setf" "incf" "decf"} (name head))))

(defn ^:macro-support slot-ref? [env form]
  (and (symbol? form)
       (contains? (:slots env) form)
       (not (contains? (:shadowed env) form))))

(defn ^:macro-support walk-slots
  "Replace each free reference to a slot name with its read, and each slot name
   used as a place of setf/incf/decf with `(slot-value obj 'slot)`. Operator
   position is left alone: as with Common Lisp's symbol-macrolet, a slot named
   `next` does not capture `(next xs)`."
  [env form]
  (cond
    (slot-ref? env form) ((:read env) form)

    (seq? form)
    (let [[head & args] form]
      (cond
        (= 'quote head) form

        (contains? #{'let 'let* 'loop 'loop* 'binding} head)
        (let [[bindings & body] args
              [env' bindings'] (walk-bindings env bindings)]
          (list* head bindings' (map #(walk-slots env' %) body)))

        (contains? #{'fn 'fn*} head)
        (let [[fname & more] (if (symbol? (first args)) args (cons nil args))
              env  (if fname (update env :shadowed conj fname) env)
              tail (if (vector? (first more))
                     (walk-fn-tail env more)
                     (map #(walk-fn-tail env %) more))]
          (if fname (list* head fname tail) (cons head tail)))

        (modify-op? head)
        (cons head (map-indexed
                    (fn [i a]
                      (cond
                        (and (even? i) (= "setf" (name head)) (slot-ref? env a)) ((:place env) a)
                        (and (zero? i) (not= "setf" (name head)) (slot-ref? env a)) ((:place env) a)
                        :else (walk-slots env a)))
                    args))

        :else (cons head (map #(walk-slots env %) args))))

    (vector? form) (mapv #(walk-slots env %) form)
    (map? form)    (into {} (map (fn [[k v]] [(walk-slots env k) (walk-slots env v)]) form))
    (set? form)    (set (map #(walk-slots env %) form))
    :else form))

(defn ^:macro-support expand-with-slots [backend slots obj body]
  (when-not (or (seq? slots) (vector? slots))
    (fail! "with-slots needs a slot list, as in (with-slots (a b) obj ...)" {:slots slots}))
  (let [entries (map (fn [s] (if (seq? s) [(first s) (second s)] [s s])) slots)
        by-var  (into {} entries)
        [binds o] (if (trivial? obj) [[] obj] (let [g (gensym "obj")] [[g obj] g]))
        read    (op backend :slot :read)
        env     {:slots    (set (keys by-var))
                 :shadowed #{}
                 :read     (fn [v] (read [o (by-var v)] [obj (by-var v)]))
                 :place    (fn [v] (list 'slot-value o (list 'quote (by-var v))))}]
    (apply wrap-let binds (map #(walk-slots env %) body))))

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
          params (hint-params backend required (declared-types decls))]
      ((op backend :method :define)
       {:name mname :self self :struct struct :params params :doc doc :body body}))))

;; ===========================================================================
;; defapi / defruntime: a backend's whole API surface (D26)
;; ===========================================================================
;; `emit-form` is pasted, unevaluated, into every generated macro, so a backend
;; whose emitters read the compiler environment passes `(emit &env)`.
;;
;; Generated macros call this namespace through the alias `contract`, which
;; every backend declares: that alias resolves wherever the backend's macros are
;; compiled, whatever a host names its macro-time namespace (H20).

(def ^:macro-support api-names
  '[elt length vector-push-extend make-array
    gethash make-hash-table slot-value
    setf incf decf
    defun defstruct defmethod with-slots])

(defmacro defapi
  "Generate every contract macro in the calling namespace.
   `opts` -- `{:inline-extrema? true}` when the host defines min/max as inline
   functions instead of macros."
  ([emit-form] `(defapi ~emit-form {}))
  ([emit-form opts]
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
      (defmacro ~'with-slots
        "(with-slots (slot...) object body...)"
        [~'slots ~'object & ~'body]
        (~'contract/expand-with-slots ~emit-form ~'slots ~'object ~'body))
      ~@(when-not (:inline-extrema? opts)
          [`(defmacro ~'min
              "(min real...) -- inline in call position, a function as a value."
              [& ~'args]
              (~'contract/expand-extremum ~emit-form :min ~'args))
           `(defmacro ~'max
              "(max real...) -- inline in call position, a function as a value."
              [& ~'args]
              (~'contract/expand-extremum ~emit-form :max ~'args))]))))

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
