(ns setf.contract
  "PROTOTYPE — shared, host-agnostic contract.
   No host is named anywhere in this file. It runs inside each host's macro host.")

;; ---- argument kinds -------------------------------------------------------
(def RUNTIME :runtime) ; argument is evaluated at run time
(def SLOT    :slot)    ; argument is a compile-time name, never evaluated

;; ---- the vocabulary (shared) ---------------------------------------------
(def PLACES
  {'elt    {:argkinds [RUNTIME RUNTIME] :kind :indexed}
   'gethash {:argkinds [RUNTIME RUNTIME] :kind :keyed}
   'get!    {:argkinds [RUNTIME SLOT]    :kind :field}})

(defn known-places [] (keys PLACES))

(defn fail! [msg data]
  (throw (ex-info (str "setf!/contract: " msg) data)))

;; ---- the shared engine ----------------------------------------------------

(defn- plan-binds
  "Bind every RUNTIME argument once, left-to-right. SLOT arguments are spliced
   as-is and never evaluated.

   Returns [binds refs srcs], where `srcs` is the ORIGINAL argument forms in
   order. Handing those to a backend is what lets it recognise the receiver --
   e.g. spot that `(make-obj)` is a constructor it defines, or read a type hint
   off the form -- without the shared library knowing anything about any host.
   Nothing host-specific travels this way: it is just the source forms."
  [argkinds args]
  (loop [ks (seq argkinds) as (seq args) binds [] refs [] srcs []]
    (if (seq ks)
      (let [k (first ks) a (first as)]
        (if (= k SLOT)
          (recur (rest ks) (rest as) binds (conj refs a) (conj srcs a))
          (let [nm (symbol (str "t" (count refs)))]
            (recur (rest ks) (rest as) (conj binds [nm a]) (conj refs nm) (conj srcs a)))))
      [binds refs srcs])))

(defn- expand-with
  [backend place-form value-form op-key build-value]
  (let [accessor (first place-form)
        args     (rest place-form)
        spec     (or (get PLACES accessor)
                     (fail! (str "unknown place `" accessor "`. Known: " (pr-str (known-places)))
                            {:place place-form}))
        argkinds (:argkinds spec)]
    (when (not= (count args) (count argkinds))
      (fail! (str "`" accessor "` expects " (count argkinds) " arguments, got " (count args))
             {:place place-form :expected argkinds}))
    (let [kind (:kind spec)
          op   (get-in backend [kind op-key])]
      (when-not op
        (fail! (str "backend does not implement " op-key " for kind " kind
                    ". This backend is INCOMPLETE — it is not compatible.")
               {:kind kind :op op-key}))
      (let [[binds refs srcs] (plan-binds argkinds args)
            inner              (build-value op refs srcs)
            all-binds          (vec (concat (apply concat binds) ['v value-form]))]
        {:kind kind :refs refs :srcs srcs :binds all-binds :store inner
         :code (list 'let (vec all-binds) inner 'v)}))))

(defn expand-setf!
  "Shared logic for `(setf! <place> <value>)`. The backend supplies only the op."
  [backend place-form value-form]
  (expand-with backend place-form value-form :write (fn [op refs srcs] (op refs srcs 'v))))

(defn expand-incf!
  "Shared logic for `(incf! <place> & [delta])`."
  [backend place-form delta-form]
  (expand-with backend place-form delta-form :write
               (fn [op refs srcs]
                 (let [rd (get-in backend [(:kind (get PLACES (first place-form))) :read])]
                   (when-not rd
                     (fail! "backend does not implement :read for this kind — INCOMPLETE"
                            {:kind (:kind (get PLACES (first place-form)))}))
                   (op refs srcs (list '+ (rd refs srcs) delta-form))))))
