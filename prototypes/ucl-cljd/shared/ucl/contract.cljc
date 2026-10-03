(ns ucl.contract
  "SPIKE -- the minimal ucl contract that fizzbuzz needs, built to test whether
   the design in ucl/README.md carries to a fourth host (ClojureDart).

   Names no host. A backend supplies an emit map; `defapi` generates its API.

   `^:macro-support` marks every fn a macro calls at expansion time. ClojureDart
   needs it to make the fn available on its JVM macro host; on every other host
   it is inert metadata.")

;; ---------------------------------------------------------------------------
;; Places
;; ---------------------------------------------------------------------------

(def ^:macro-support places
  {'elt {:argkinds [:runtime :runtime] :kind :indexed}})

(defn ^:macro-support fail! [msg data]
  (throw (ex-info (str "ucl: " msg) (assoc data :library 'ucl))))

(defn ^:macro-support place-spec [place-form]
  (let [accessor (first place-form)
        spec     (when (symbol? accessor) (get places (symbol (name accessor))))]
    (when-not spec
      (fail! (str "unknown place `" accessor "`") {:place place-form}))
    (when (not= (count (rest place-form)) (count (:argkinds spec)))
      (fail! (str "`" accessor "` expects " (count (:argkinds spec)) " arguments")
             {:place place-form}))
    spec))

(defn ^:macro-support trivial?
  "A form that is safe to repeat and free to evaluate: a symbol or a literal.
   Binding one buys nothing -- and costs an IIFE on Squint wherever the form
   sits in expression position."
  [form]
  (not (or (seq? form) (vector? form) (map? form) (set? form))))

(defn ^:macro-support plan-binds
  "Bind every non-trivial runtime argument once, left to right, to a fresh
   gensym; use symbols and literals as they are."
  [argkinds args]
  (loop [ks (seq argkinds) as (seq args) binds [] refs [] srcs []]
    (if ks
      (let [a (first as)]
        (if (trivial? a)
          (recur (next ks) (next as) binds (conj refs a) (conj srcs a))
          (let [nm (gensym "t")]
            (recur (next ks) (next as) (conj binds nm a) (conj refs nm) (conj srcs a)))))
      [binds refs srcs])))

(defn ^:macro-support wrap-let
  "`(let binds body...)`, or just the body when nothing needs binding."
  [binds & body]
  (cond (seq binds)           (list* 'let binds body)
        (next body)           (list* 'do body)
        :else                 (first body)))

(defn ^:macro-support op [backend kind k]
  (or (get-in backend [kind k])
      (fail! (str "the host backend does not implement " k " for " kind
                  ". A host must implement the whole contract.")
             {:kind kind :op k})))

(defn ^:macro-support expand-read [backend place-form]
  (let [{:keys [argkinds kind]} (place-spec place-form)
        [binds refs srcs] (plan-binds argkinds (rest place-form))]
    (wrap-let binds ((op backend kind :read) refs srcs))))

(defn ^:macro-support expand-setf [backend place-form value-form]
  (let [{:keys [argkinds kind]} (place-spec place-form)
        [binds refs srcs] (plan-binds argkinds (rest place-form))
        [binds v] (if (trivial? value-form)
                    [binds value-form]
                    (let [v (gensym "v")] [(conj binds v value-form) v]))]
    (wrap-let binds ((op backend kind :write) refs srcs v) v)))

;; ---------------------------------------------------------------------------
;; Types: Common Lisp specifiers -> one canonical form every backend maps
;; ---------------------------------------------------------------------------

(def ^:macro-support type-abbreviations
  {'fixnum-vector '(simple-array fixnum (*))
   'sb53-vector   '(simple-array (signed-byte 53) (*))
   'simple-vector '(simple-array t (*))})

(defn ^:macro-support canonical-element [spec]
  (cond
    (= spec 't)                         :t
    (= spec 'fixnum)                    :fixnum
    (= spec '(signed-byte 53))          :sb53
    :else (fail! (str "unsupported element type " (pr-str spec)) {:type spec})))

(defn ^:macro-support canonical-type
  "`fixnum` -> :fixnum; `(simple-array fixnum (*))` -> [:vector :fixnum];
   `(or null X)` -> X. Nil for a type the contract does not interpret."
  [spec]
  (let [spec (get type-abbreviations spec spec)]
    (cond
      (= spec 'fixnum) :fixnum
      (and (seq? spec) (= 'simple-array (first spec))) [:vector (canonical-element (second spec))]
      (and (seq? spec) (= 'or (first spec)) (= 'null (second spec))) (canonical-type (nth spec 2))
      :else nil)))

;; ---------------------------------------------------------------------------
;; make-array
;; ---------------------------------------------------------------------------

(defn ^:macro-support expand-make-array [backend n-form opts]
  (let [{:keys [element-type initial-element]} (apply hash-map opts)
        element (canonical-element (if (and (seq? element-type) (= 'quote (first element-type)))
                                     (second element-type)
                                     ;; not (or element-type 't): Squint's macro
                                     ;; interpreter fails on a quoted symbol in `or`
                                     (if element-type element-type 't)))
        [binds n] (if (trivial? n-form) [[] n-form] (let [n (gensym "n")] [[n n-form] n]))]
    (wrap-let binds ((op backend :vector :make) n element initial-element))))

;; ---------------------------------------------------------------------------
;; defun + declare
;; ---------------------------------------------------------------------------

(defn ^:macro-support declare-form? [form]
  (and (seq? form) (symbol? (first form)) (= "declare" (name (first form)))))

(defn ^:macro-support declared-types
  "{param canonical-type} from `(declare (type T a b) ...)` forms."
  [decls]
  (into {}
        (for [d decls, [kind & more] (rest d)
              :when (= "type" (name kind))
              :let [[spec & vars] more, t (canonical-type spec)]
              :when t
              v vars]
          [v t])))

(defn ^:macro-support expand-defun [backend fname params body]
  (let [[decls body] (split-with declare-form? body)
        types (declared-types decls)
        hint  (op backend :types :hint)
        params (mapv (fn [p]
                       (let [tag (some-> (types p) hint)]
                         (if tag (vary-meta p assoc :tag tag) p)))
                     params)]
    (list* 'defn fname params body)))

;; ---------------------------------------------------------------------------
;; defapi -- generates a backend's whole API surface
;; ---------------------------------------------------------------------------
;; `emit-form` is pasted unevaluated into every generated macro, so a backend
;; whose emitters read the compiler environment can pass `(emit &env)`.
;;
;; The generated macros call the contract through the alias `contract`, which
;; every backend declares. That alias resolves where the backend's macros are
;; compiled, whatever a host names its macro-time namespace -- ClojureDart, for
;; one, evaluates macros in a shadow `<ns>$host` namespace.

(defmacro defapi [emit-form]
  `(do
     (defmacro ~'elt [& args#]
       (~'contract/expand-read ~emit-form (cons '~'elt args#)))
     (defmacro ~'setf [place# value#]
       (~'contract/expand-setf ~emit-form place# value#))
     (defmacro ~'make-array [n# & opts#]
       (~'contract/expand-make-array ~emit-form n# opts#))
     (defmacro ~'defun [fname# params# & body#]
       (~'contract/expand-defun ~emit-form fname# params# body#))))
