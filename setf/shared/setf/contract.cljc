(ns setf.contract
  "Generalized assignment, host-agnostic.

   This namespace holds the whole model: the place vocabulary, each place's
   argument kinds, and the expansion algorithm. It runs inside each host's macro
   host and names no host, no file extension, and no build tool.

   A host supplies exactly one thing -- an EMIT map of six functions -- and
   gets the API (`setf!`, `incf!`, `decf!`) from its own thin wrapper.")

;; ---------------------------------------------------------------------------
;; Argument kinds
;; ---------------------------------------------------------------------------
;; RUNTIME: the argument is an expression, evaluated once at run time.
;; SLOT:    the argument is a name the macro consumes at expansion time and never
;;          evaluates. Splicing it is how `get!` can name a field without ever
;;          producing a run-time lookup of the field's *name*.

(def ^:const runtime :runtime)
(def ^:const slot    :slot)

;; ---------------------------------------------------------------------------
;; The place vocabulary -- closed by design.
;; ---------------------------------------------------------------------------
;; A place is a consumer-facing shape. It needs argument-kind rules (which
;; arguments are evaluated), and those rules are host-independent, so they live
;; here rather than in a backend. Adding a place is a deliberate edit to this
;; table, reviewed as an API change; every backend must then implement it.

(def places
  {'elt    {:argkinds [runtime runtime] :kind :indexed}
   'gethash {:argkinds [runtime runtime] :kind :keyed}
   'get!    {:argkinds [runtime slot]    :kind :field}})

(def kinds [:indexed :keyed :field])

(defn known-places []
  (vec (sort (map str (keys places)))))

(defn fail! [msg data]
  (throw (ex-info (str "setf!: " msg) (assoc data :library 'setf))))

;; ---------------------------------------------------------------------------
;; The engine
;; ---------------------------------------------------------------------------

(defn- plan-binds
  "Bind every RUNTIME argument exactly once, left to right, so a place with a
   compound expression in it never evaluates it twice.

   Returns [binds refs srcs]. `refs` are the generated binding symbols; `srcs`
   are the ORIGINAL argument forms, in order. Handing those to a backend is what
   lets it recognise one of its own constructors, or read a type hint, without
   this namespace knowing that any host exists. Nothing host-specific travels
   this way -- it is only the source forms."
  [argkinds args]
  (loop [ks (seq argkinds) as (seq args) binds [] refs [] srcs []]
    (if (seq ks)
      (let [k (first ks)
            a (first as)]
        (if (= k slot)
          (do (when-not (symbol? a)
                (fail! (str "a slot argument must be a name, not an expression: " (pr-str a)
                            ". A slot is consumed at expansion time and is never evaluated.")
                       {:slot a}))
              (recur (rest ks) (rest as) binds (conj refs a) (conj srcs a)))
          (let [nm (symbol (str "t" (count refs)))]
            (recur (rest ks) (rest as)
                   (conj binds [nm a]) (conj refs nm) (conj srcs a)))))
      [binds refs srcs])))

(defn- lookup-op
  "Fetch one emitter, or say precisely which one a backend failed to supply."
  [backend kind op-key]
  (or (get-in backend [kind op-key])
      (fail! (str "the host backend is INCOMPLETE -- it does not implement "
                  (name op-key) " for kind :" (name kind)
                  ". A host must implement the whole contract or it is not compatible.")
             {:kind kind :op op-key :missing (mapv name kinds)})))

(defn- place-spec
  [place-form]
  (let [accessor (first place-form)
        spec     (get places accessor)]
    (when-not spec
      (fail! (str "unknown place `" accessor "`. Known places: " (pr-str (known-places)))
             {:place place-form :known (known-places)}))
    (when (not= (count (rest place-form)) (count (:argkinds spec)))
      (fail! (str "`" accessor "` expects " (count (:argkinds spec))
                  " arguments, got " (count (rest place-form)))
             {:place place-form :expected (:argkinds spec)}))
    spec))

(defn- expand-with
  "Expand `(op <place> <value-form>)`.

   `value-fn` receives the place's refs and source forms plus the raw value form,
   and returns the expression to bind to `v`. `setf!` binds `v` to the value
   itself; a derived form binds `v` to the *combined* value. Either way the
   store writes `v` and the whole form evaluates to `v`, so every form returns
   what it just stored, and no subexpression is ever evaluated twice."
  [backend place-form value-form op-key value-fn]
  (let [spec     (place-spec place-form)
        kind     (:kind spec)
        op       (lookup-op backend kind op-key)
        [binds refs srcs] (plan-binds (:argkinds spec) (rest place-form))
        store    (op refs srcs 'v)
        all      (vec (concat (apply concat binds) ['v (value-fn refs srcs value-form)]))]
    {:kind kind :refs (vec refs) :srcs (vec srcs)
     :store store
     :code  (list 'let all store 'v)}))

(defn expand-setf!
  "Shared logic for `(setf! <place> <value>)`. Returns the expansion."
  [backend place-form value-form]
  (expand-with backend place-form value-form :write (fn [_ _ value] value)))

(defn expand-derived!
  "Shared logic for read-modify-write forms: `(incf! <place> delta)` and
   `(decf! <place> delta)`. Needs both a read and a write emitter, which is why
   a derived form is reachable on a strictly smaller set of places than `setf!`
   on any host whose read emitter is narrower than its write emitter.

   `combine` is a host-agnostic fn of [current delta] returning a form."
  [backend place-form delta-form combine]
  (let [kind (:kind (place-spec place-form))
        rd   (lookup-op backend kind :read)]
    (expand-with backend place-form delta-form :write
                 (fn [refs srcs delta]
                   (combine (rd refs srcs) delta)))))
