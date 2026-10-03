(ns ucl.loop
  "ucl/loop: Common Lisp's LOOP (CLHS 6.1) for what ucl has -- README §4.3,
   D53-D62.

   One registry entry (I23), `\"loop\"`, whose expansion is ucl's own forms
   and Clojure's: `ucl/block` (its exits are static, D55), `ucl/let*` for the
   `with` and `into` names (variables, D54), and one Clojure `loop`/`recur`
   whose parameters are the `for` names (locals), the internal counters and
   the accumulator without `into` (D57).

   Shape of the expansion, as Common Lisp orders a loop's work:

     (ucl/block name
       (ucl/let* (with and into variables) (declare ...)
         initially...
         <each for clause in turn: its once-evaluated forms, its first value,
          its end test -- failing, the epilogue>
         (loop [for names, counters, accumulator ...]
           <main clauses in order: statements, accumulator updates, tests>
           <each for clause in turn: its next value, its end test>
           (recur ...))))

   The epilogue -- `finally` forms, then the loop's value -- sits at every
   end test, where every name it may read is bound: Common Lisp gives the
   `for` names their last values there (SBCL and ECL agree). Nothing here
   reads the lexical environment (I23): what needs a local's type is emitted
   as a ucl form through the alias the caller wrote (`ucl/elt`).

   Squint runs this file in SCI and ClojureDart on its macro host: every fn
   is defined before its first use and tagged ^:macro-support."
  (:require [ucl.contract :as contract]))

;; ===========================================================================
;; Clause keywords
;; ===========================================================================
;; LOOP compares keywords by name: `for`, `:for` and `ucl/for` are one.

(defn ^:macro-support kw
  "The keyword name of `x`, or nil."
  [x]
  (when (or (symbol? x) (keyword? x)) (name x)))

(defn ^:macro-support kw?
  [x names]
  (contains? names (kw x)))

(defn ^:macro-support fail! [msg form]
  (contract/fail! (str "loop: " msg) {:form form}))

(defn ^:macro-support simple-type? [x]
  (kw? x #{"fixnum" "float" "t" "nil"}))

(defn ^:macro-support main-clause-keywords []
  #{"do" "doing" "return" "if" "when" "unless" "while" "until" "repeat"
    "always" "never" "thereis" "count" "counting" "sum" "summing" "maximize"
    "maximizing" "minimize" "minimizing" "collect" "collecting" "append"
    "appending" "nconc" "nconcing" "loop-finish"})

;; ===========================================================================
;; Parsing (CLHS 6.1.2-6.1.7) -- a token vector and a position
;; ===========================================================================

(defn ^:macro-support parse-type
  "An optional type spec at `i`: [type i'] -- `of-type T` or a simple type."
  [ts i]
  (cond
    (kw? (get ts i) #{"of-type"}) [(get ts (inc i)) (+ i 2)]
    (and (< i (count ts)) (simple-type? (get ts i))) [(symbol (kw (get ts i))) (inc i)]
    :else [nil i]))

(defn ^:macro-support parse-var
  "A variable at `i`: [var i']. Destructuring needs conses (D53)."
  [ts i form]
  (let [v (get ts i)]
    (cond
      (and (symbol? v) (nil? (namespace v))) [v (inc i)]
      (or (seq? v) (vector? v))
      (fail! (str "destructuring (" (pr-str v) ") needs conses, which ucl does not have (D53). "
                  "Bind each value with its own clause.")
             form)
      :else (fail! (str "expected a variable name, found " (pr-str v)) form))))

(defn ^:macro-support compound-forms
  "The compound forms (lists) from `i`: [forms i']."
  [ts i]
  (loop [i i out []]
    (if (seq? (get ts i)) (recur (inc i) (conj out (get ts i))) [out i])))

(defn ^:macro-support parse-arithmetic
  "The prepositions of an arithmetic for (6.1.2.1.1) from `i`."
  [ts i v type form]
  (loop [i i c {:kind :arith :var v :type type :order []}]
    (let [p (kw (get ts i))
          [k extra] (cond
                      (and (contains? #{"from" "upfrom" "downfrom"} p) (not (contains? c :from)))
                      [:from (case p "upfrom" {:up true} "downfrom" {:down true} {})]
                      (and (contains? #{"to" "upto" "below" "downto" "above"} p) (not (contains? c :limit)))
                      [:limit (case p
                                "to"     {:inclusive true}
                                "upto"   {:inclusive true :up true}
                                "below"  {:inclusive false :up true}
                                "downto" {:inclusive true :down true}
                                {:inclusive false :down true})]
                      (and (= "by" p) (not (contains? c :by)))
                      [:by {}]
                      :else nil)]
      (if k
        (recur (+ i 2) (-> c (assoc k (get ts (inc i))) (merge extra) (update :order conj k)))
        (do (when (and (:up c) (:down c))
              (fail! (str "for " v ": counting both up and down") form))
            (when (and (:down c) (not (contains? c :from)))
              (fail! (str "for " v ": counting down needs a start (from or downfrom)") form))
            [(assoc c :dir (if (:down c) :down :up)) i])))))

(defn ^:macro-support parse-for
  "One for/as clause from `i` (after the keyword): [clause i']."
  [ts i form]
  (let [[v i] (parse-var ts i form)
        [type i] (parse-type ts i)
        p (kw (get ts i))]
    (cond
      (contains? #{"from" "upfrom" "downfrom" "to" "upto" "below" "downto" "above" "by"} p)
      (parse-arithmetic ts i v type form)

      (= "=" p)
      (let [init (get ts (inc i))]
        (if (kw? (get ts (+ i 2)) #{"then"})
          [{:kind :equals :var v :type type :init init :then (get ts (+ i 3))} (+ i 4)]
          [{:kind :equals :var v :type type :init init :then init} (+ i 2)]))

      (= "across" p)
      [{:kind :across :var v :type type :vector (get ts (inc i))} (+ i 2)]

      (contains? #{"in" "on"} p)
      (fail! (str "for " v " " p " iterates a list; ucl has no lists (D53). "
                  "Use for " v " across a vector.")
             form)

      (contains? #{"being" "each" "the"} p)
      (fail! (str "for " v " being ...: hash-table iteration is designed (D58) but not built yet; "
                  "package iteration needs packages, which ucl does not have (D53).")
             form)

      :else (fail! (str "for " v ": expected from, to, below, =, across ..., found " (pr-str (get ts i)))
                   form))))

(defn ^:macro-support parse-accumulation
  "An accumulation clause at `i`: [clause i']."
  [ts i form]
  (let [k (kw (get ts i))
        kind (case k
               ("count" "counting") :count
               ("sum" "summing") :sum
               ("maximize" "maximizing") :maximize
               ("minimize" "minimizing") :minimize
               nil)
        x (get ts (inc i))
        i (+ i 2)
        [into i] (if (kw? (get ts i) #{"into"}) [(first (parse-var ts (inc i) form)) (+ i 2)] [nil i])
        [type i] (parse-type ts i)]
    (when (nil? kind)
      (fail! (str k " builds a list; ucl has no lists (D53). Fill a vector with "
                  "do (ucl/vector-push-extend x v).")
             form))
    [{:clause :accumulate :kind kind :form x :into into :type type} i]))

(defn ^:macro-support parse-selectable
  "One selectable clause (6.1.6) at `i`: [clause i'] -- do, return, an
   accumulation, or a nested conditional."
  [ts i form]
  (let [k (kw (get ts i))]
    (cond
      (contains? #{"do" "doing"} k)
      (let [[fs i'] (compound-forms ts (inc i))]
        (when (empty? fs) (fail! (str k " needs at least one compound form") form))
        [{:clause :do :forms fs} i'])

      (= "return" k)
      [{:clause :return :form (get ts (inc i))} (+ i 2)]

      (contains? #{"count" "counting" "sum" "summing" "maximize" "maximizing" "minimize"
                   "minimizing" "collect" "collecting" "append" "appending" "nconc" "nconcing"} k)
      (parse-accumulation ts i form)

      (contains? #{"if" "when" "unless"} k)
      (let [test (get ts (inc i))
            more (fn [i]
                   (loop [i i out []]
                     (let [[c i] (parse-selectable ts i form)
                           out (conj out c)]
                       (if (kw? (get ts i) #{"and"}) (recur (inc i) out) [out i]))))
            [then i] (more (+ i 2))
            [else i] (if (kw? (get ts i) #{"else"}) (more (inc i)) [[] i])
            i (if (kw? (get ts i) #{"end"}) (inc i) i)]
        [{:clause :conditional :test test :unless (= "unless" k) :then then :else else} i])

      :else
      (fail! (str "expected do, return, an accumulation or a conditional after a conditional's "
                  "test, found " (pr-str (get ts i)))
             form))))

(defn ^:macro-support parse-loop
  "The clauses of `form` (after the operator) -- {:name :withs :fors :main
   :initially :finally}, or {:simple forms} for `(loop compound-form*)`."
  [form]
  (let [ts (vec (rest form))]
    (if (every? seq? ts)
      {:simple ts}
      (loop [i 0
             p {:name nil :withs [] :fors [] :main [] :initially [] :finally []}
             main? false]
        (if (>= i (count ts))
          p
          (let [k (kw (get ts i))]
            (cond
              (and (= "named" k) (zero? i))
              (let [n (get ts 1)]
                (when-not (symbol? n) (fail! (str "named takes a symbol, not " (pr-str n)) form))
                (recur 2 (assoc p :name n) main?))

              (= "with" k)
              (let [[group i]
                    (loop [i (inc i) group []]
                      (let [[v i] (parse-var ts i form)
                            [type i] (parse-type ts i)
                            [init i] (if (kw? (get ts i) #{"="}) [(get ts (inc i)) (+ i 2)] [nil i])
                            group (conj group {:var v :type type :init init})]
                        (if (kw? (get ts i) #{"and"}) (recur (inc i) group) [group i])))]
                (when main? (fail! "a with clause after a main clause (CLHS 6.1.1.4)" form))
                (recur i (update p :withs conj group) main?))

              (contains? #{"for" "as"} k)
              (let [[c i] (parse-for ts (inc i) form)]
                (when main? (fail! (str "a for clause after a main clause (CLHS 6.1.1.4): for "
                                        (:var c))
                                   form))
                (when (kw? (get ts i) #{"and"})
                  (fail! "for ... and ... (parallel stepping) is not built yet (§13)" form))
                (recur i (update p :fors conj c) main?))

              (contains? #{"initially" "finally"} k)
              (let [[fs i'] (compound-forms ts (inc i))]
                (recur i' (update p (keyword k) into fs) main?))

              (contains? #{"while" "until" "repeat" "always" "never" "thereis"} k)
              (recur (+ i 2) (update p :main conj {:clause (keyword k) :form (get ts (inc i))}) true)

              (= "loop-finish" k)
              (fail! "loop-finish is not built yet (§13); end the loop with while or until" form)

              (contains? (main-clause-keywords) k)
              (let [[c i] (parse-selectable ts i form)]
                (recur i (update p :main conj c) true))

              :else
              (fail! (str "unknown clause " (pr-str (get ts i))
                          " -- expected for, with, do, sum, when, while, return, finally, ...")
                     form))))))))

;; ===========================================================================
;; Expansion
;; ===========================================================================

(defn ^:macro-support ucl-sym
  "`n` in the namespace the caller wrote `loop` in (its alias, D25): ucl
   forms in the expansion resolve as the caller's own do."
  [form n]
  (let [ns (namespace (first form))]
    (symbol (if ns ns "ucl.api") n)))

(defn ^:macro-support accumulations
  "Every accumulation clause in `main`, conditionals' included."
  [main]
  (mapcat (fn [c]
            (case (:clause c)
              :accumulate [c]
              :conditional (accumulations (concat (:then c) (:else c)))
              []))
          main))

(defn ^:macro-support acc-class [kind]
  (if (contains? #{:sum :count} kind) :sum-count :minimax))

(defn ^:macro-support once
  "[bindings form]: `x`, evaluated once -- bound to a fresh local unless it is
   a literal, or a symbol the loop does not assign (`assigned`): only the
   loop's own code could assign a variable while it runs. A needless alias of
   a vector costs V8 ≈15% (§9.14)."
  [x prefix assigned]
  (if (or (contract/literal? x) (and (symbol? x) (not (contains? assigned x))))
    [[] x]
    (let [g (gensym prefix)] [[g x] g])))

(defn ^:macro-support render
  "Fold `ops` around `tail`, innermost last:
     [:stmt f]          (do f ...)
     [:bind s f]        (let [s f] ...)
     [:end test pos?]   when `test` is truthy (pos?) or falsy, the epilogue
   `epi` -- the epilogue form."
  [ops tail epi]
  (reduce (fn [inner [op a b]]
            (case op
              :stmt (contract/seq-do a inner)
              :bind (list 'let [a b] inner)
              :end  (if b (list 'if a epi inner) (list 'if a inner epi))))
          tail
          (reverse ops)))

(defn ^:macro-support guarded
  "`f` run when `guard` -- [sym positive?], or nil for always -- holds; else `otherwise`."
  [guard f otherwise]
  (cond
    (nil? guard) f
    (second guard) (list 'if (first guard) f otherwise)
    :else (list 'if (first guard) otherwise f)))

(defn ^:macro-support finishes?
  "Does `form` call loop-finish (not built yet, §13)?"
  [form]
  (cond (and (seq? form) (= "loop-finish" (contract/head-name form))) true
        (coll? form) (boolean (some finishes? form))
        :else false))

(defn ^:macro-support expand-loop
  "The registry's :expand for ucl/loop."
  [backend form]
  (when (finishes? (rest form))
    (fail! "loop-finish is not built yet (§13); end the loop with while or until" form))
  (let [{:keys [simple withs fors main initially finally] bname :name} (parse-loop form)
        u (fn [n] (ucl-sym form n))]
    (if simple
      (list (u "block") nil (list* 'loop [] (concat simple [(list 'recur)])))
      (let [_ (let [vs (map :var fors)]
                (when-not (= (count vs) (count (set vs)))
                  (fail! (str "a for name is bound twice: " (pr-str vs)) form)))
            accs  (accumulations main)
            anon  (filter #(nil? (:into %)) accs)
            _     (when (< 1 (count (set (map (comp acc-class :kind) anon))))
                    (fail! "sum/count and maximize/minimize accumulate into one value; give one of them `into`"
                           form))
            booleans (filter #(contains? #{:always :never :thereis} (:clause %)) main)
            _     (when (and (seq anon) (seq booleans))
                    (fail! "always, never and thereis decide the loop's value; an accumulation without into cannot too"
                           form))
            acc   (when (seq anon) (gensym "acc"))
            ;; the symbols the loop's own code assigns
            assigned (contract/assigned-vars backend
                                             (set (filter symbol? (mapcat (fn [c] [(:from c) (:limit c) (:by c) (:vector c)]) fors)))
                                             (rest form))
            acc-type (when acc
                       (if (every? #(= :count (:kind %)) anon)
                         :fixnum
                         (some #(when (:type %) (contract/canonical-type (:type %))) anon)))
            minimax? (fn [c] (= :minimax (acc-class (:kind c))))
            ;; A maximize/minimize declared fixnum starts at the type's far end
            ;; -- most-negative-fixnum for maximize -- with no first-value flag,
            ;; as SBCL and ECL do (H65): nothing accumulated, that is its value.
            ;; It goes through the backend's max/min (I6, §9.14). Untyped, it
            ;; starts at 0 and a flag takes the first value.
            _     (doseq [c accs]
                    (when (and (minimax? c) (:type c) (not= :fixnum (contract/canonical-type (:type c))))
                      (fail! (str (name (:kind c)) " of-type " (pr-str (:type c))
                                  ": SBCL signals a type error for it and ECL does not (H65); "
                                  "declare it fixnum, or leave it untyped")
                             form)))
            sentinel (fn [kind] (if (= :maximize kind) -2147483648 2147483647))
            typed-minimax? (fn [cs] (and (seq cs) (every? minimax? cs)
                                         (some #(= :fixnum (contract/canonical-type (:type %))) cs)))
            _     (doseq [cs (cons anon (vals (group-by :into (filter :into accs))))]
                    (when (and (typed-minimax? cs) (< 1 (count (set (map :kind cs)))))
                      (fail! "maximize and minimize into one fixnum accumulator: which end does it start at?"
                             form)))
            acc-sentinel (when (typed-minimax? anon) (sentinel (:kind (first anon))))
            acc-first (when (and (some minimax? anon) (nil? acc-sentinel)) (gensym "first"))
            ;; named accumulators: one variable each, a first-flag for minimax
            intos (reduce (fn [m c]
                            (let [v (:into c)
                                  e (get m v)]
                              (if (nil? v)
                                m
                                (let [cs (filter #(= v (:into %)) accs)
                                      typed (typed-minimax? cs)]
                                  (assoc m v {:type  (if (:type e) (:type e) (:type c))
                                              :init  (if typed (sentinel (:kind c)) 0)
                                              :first (cond typed nil
                                                           (:first e) (:first e)
                                                           (minimax? c) (gensym (str (name v) "-first"))
                                                           :else nil)})))))
                          {} accs)
            result (cond acc acc
                         (some #(contains? #{:always :never} (:clause %)) booleans) true
                         :else nil)
            epi-body (concat finally [result])
            epi   (if (next epi-body) (list* 'do epi-body) (first epi-body))
            block (fn [v] (list (u "return-from") bname v))
            ;; ---- for clauses: once-evaluated forms, first value, next value, end test
            fors' (mapv (fn [c]
                          (case (:kind c)
                            :arith
                            (let [onces (reduce (fn [[bs m] k]
                                                  (let [[b x] (once (get c k) (name k) assigned)]
                                                    [(into bs b) (assoc m k x)]))
                                                [[] {}] (:order c))
                                  [bs m] onces
                                  from (if (contains? m :from) (:from m) 0)
                                  by (if (contains? m :by) (:by m) 1)
                                  v (:var c)
                                  int-or-none? (fn [k] (or (not (contains? c k)) (integer? (get c k))))
                                  ;; an integer start and step count in integers: a fixnum
                                  t (cond (:type c) (contract/canonical-type (:type c))
                                          (and (int-or-none? :from) (int-or-none? :by)) :fixnum
                                          :else nil)
                                  test (when (contains? m :limit)
                                         (list (if (= :up (:dir c))
                                                 (if (:inclusive c) '> '>=)
                                                 (if (:inclusive c) '< '<=))
                                               v (:limit m)))]
                              {:onces bs :vars [v] :types {v t} :init [[v from]]
                               :step [[v (list (if (= :up (:dir c)) '+ '-) v by)]]
                               :test test})
                            :equals
                            {:onces [] :vars [(:var c)] :init [[(:var c) (:init c)]]
                             :types {(:var c) (when (:type c) (contract/canonical-type (:type c)))}
                             :step [[(:var c) (:then c)]] :test nil}
                            :across
                            (let [[bs vec] (once (:vector c) "vec" assigned)
                                  len (gensym "len") idx (gensym "i") v (:var c)]
                              {:onces (into bs [len (list (u "length") vec)])
                               ;; the element is no loop parameter: read at the top of
                               ;; each iteration, it has the vector's element type
                               :vars [idx]
                               :types {idx :fixnum}
                               :top [[v (list (u "elt") vec idx)]]
                               :init [[idx 0]]
                               :step [[idx (list 'inc idx)]]
                               :test (list '>= idx len)
                               :after [[v (list (u "elt") vec idx)]]})))
                        fors)
            ;; a count from an integer c by 1 beside an across is its index + c: no second
            ;; counter (§9.14) -- unless finally or another for clause reads
            ;; it, where the order of their steps would show
            fors' (let [idx (some (fn [[f c]] (when (= :across (:kind c)) (first (:vars f))))
                                  (map vector fors' fors))]
                    (mapv (fn [f c]
                            (if (and idx (= :arith (:kind c)) (= :up (:dir c))
                                     (not (contains? c :limit))
                                     (or (not (contains? c :from)) (integer? (:from c)))
                                     (or (not (contains? c :by)) (= 1 (:by c)))
                                     (contains? #{nil :fixnum} (get (:types f) (:var c)))
                                     (not (contract/mentions? finally #{(:var c)}))
                                     (not-any? #(and (not (identical? % c))
                                                     (contract/mentions? [(:from %) (:limit %) (:by %) (:vector %) (:init %) (:then %)]
                                                                         #{(:var c)}))
                                               fors))
                              {:onces [] :vars [] :types {} :init [] :step [] :test nil
                               :top [[(:var c) (let [k (if (contains? c :from) (:from c) 0)]
                                                 (if (zero? k) idx (list '+ idx k)))]]}
                              f))
                          fors' fors))
            for-names (map :var fors)
            ;; the epilogue where only some for names are bound yet
            epi-at (fn [bound]
                     (let [unbound (remove (set bound) (filter #(contract/mentions? finally #{%}) for-names))]
                       (if (seq unbound)
                         (list 'let (vec (mapcat (fn [v] [v nil]) unbound)) epi)
                         epi)))
            ;; ---- main clauses as ops
            repeats (atom [])
            main-ops
            (letfn [(acc-ops [c guard]
                      (let [x (:form c)
                            kind (:kind c)
                            target (if (:into c) (:into c) acc)
                            first-flag (if (:into c) (:first (get intos (:into c))) acc-first)
                            named? (some? (:into c))
                            ;; the accumulator's type, when declared
                            t (if (:into c)
                                (when (:type (get intos (:into c))) (contract/canonical-type (:type (get intos (:into c)))))
                                acc-type)
                            h (when (contains? #{:sum :maximize :minimize} kind)
                                ((contract/op backend :types :local-hint) t))
                            ;; maximize and minimize read the value three times, and a
                            ;; typed accumulator takes a value of its type: bind it
                            vsym (if (or h (and (contains? #{:maximize :minimize} kind) first-flag))
                                   (let [g (gensym "x")] (if h (vary-meta g assoc :tag h) g))
                                   x)
                            ;; the new value of the accumulator, given the value `x`
                            new (case kind
                                  :count (list 'if vsym (list 'inc target) target)
                                  :sum (list '+ target vsym)
                                  :maximize (if first-flag
                                              (list 'if first-flag vsym (list 'if (list '> vsym target) vsym target))
                                              (list (u "max") target vsym))
                                  :minimize (if first-flag
                                              (list 'if first-flag vsym (list 'if (list '< vsym target) vsym target))
                                              (list (u "min") target vsym)))
                            first-ops (when first-flag [[:bind first-flag (guarded guard false first-flag)]])]
                        (concat
                         (when-not (= vsym x) [[:bind vsym (guarded guard x 0)]])
                         (if named?
                           [[:stmt (guarded guard (list (u "setf") target new) nil)]]
                           [[:bind target (guarded guard new target)]])
                         first-ops)))
                    (sel-ops [c guard it]
                      (let [sub (fn [x] (if (and (symbol? x) (= "it" (name x)) it) it x))]
                        (case (:clause c)
                          :do (mapv (fn [f] [:stmt (guarded guard f nil)]) (:forms c))
                          :return [[:stmt (guarded guard (block (sub (:form c))) nil)]]
                          :accumulate (acc-ops (assoc c :form (sub (:form c))) guard)
                          :conditional
                          ;; t: the test's value (`it`); th/el: the then and
                          ;; else branches hold -- false when an enclosing
                          ;; guard does not
                          (let [t (gensym "test")
                                th (gensym "then")
                                el (gensym "else")
                                unless? (:unless c)
                                then-guard (if (and (nil? guard) (not unless?)) [t true] [th true])]
                            (concat [[:bind t (guarded guard (:test c) nil)]]
                                    (when (= then-guard [th true])
                                      [[:bind th (guarded guard (if unless? (list 'if t false true) (list 'if t true false)) false)]])
                                    (mapcat #(sel-ops % then-guard t) (:then c))
                                    (when (seq (:else c))
                                      (concat [[:bind el (guarded guard (if unless? (list 'if t true false) (list 'if t false true)) false)]]
                                              (mapcat #(sel-ops % [el true] t) (:else c)))))))))]
              (vec (mapcat (fn [c]
                             (case (:clause c)
                               :while [[:end (:form c) false]]
                               :until [[:end (:form c) true]]
                               :repeat (let [n (gensym "repeat")]
                                         (swap! repeats conj [n (:form c)])
                                         [[:end (list '<= n 0) true] [:bind n (list 'dec n)]])
                               :always [[:stmt (list 'if (:form c) nil (block nil))]]
                               :never [[:stmt (list 'if (:form c) (block nil) nil)]]
                               :thereis (let [t (gensym "thereis")]
                                          [[:bind t (:form c)] [:stmt (list 'if t (block t) nil)]])
                               (sel-ops c nil nil)))
                           main)))
            params (vec (concat (mapcat :vars fors')
                                (when acc [acc])
                                (when acc-first [acc-first])
                                (keep (comp :first val) intos)
                                (map first @repeats)))
            ;; each parameter's type, hinted for a host that types locals by
            ;; hint (ClojureDart: else `dynamic`, I20)
            types (merge (apply merge (map :types fors'))
                         (when acc {acc acc-type})
                         (into {} (map (fn [[n _]] [n :fixnum]) @repeats)))
            hint (fn [s] (let [h ((contract/op backend :types :local-hint) (get types s))]
                           (if h (vary-meta s assoc :tag h) s)))
            ;; an across element is rebound after its step only if something
            ;; after it -- a later for clause, finally -- reads it
            needed-after? (fn [i v]
                            (contract/mentions? (concat (mapcat (fn [f] (concat (:step f) (:after f) [(:test f)]))
                                                                (drop (inc i) fors'))
                                                        finally)
                                                #{v}))
            after-ops (fn [i f] (keep (fn [[v x]] (when (needed-after? i v) [:bind v x])) (:after f)))
            step-ops (vec (apply concat
                                 (map-indexed (fn [i f]
                                                (concat (map (fn [[v x]] [:bind v x]) (:step f))
                                                        (when (:test f) [[:end (:test f) true]])
                                                        (after-ops i f)))
                                              fors')))
            top-ops (vec (mapcat (fn [f] (map (fn [[v x]] [:bind v x]) (:top f))) fors'))
            body (render (concat top-ops main-ops step-ops) (list* 'recur params) epi)
            the-loop (list 'loop (vec (mapcat (fn [s] [(hint s) s]) params)) body)
            ;; the prologue: each for clause in turn, its end test at its first value
            pre-loop (render (map (fn [[n x]] [:bind n x]) @repeats) the-loop epi)
            prologue (reduce (fn [inner [f bound]]
                               (let [ops (concat (map (fn [[a b]] [:bind a b]) (partition 2 (:onces f)))
                                                 (map (fn [[v x]] [:bind v x]) (:init f))
                                                 (when (:test f) [[:end (:test f) true]])
                                                 (after-ops (:index f) f))]
                                 (render ops inner (epi-at bound))))
                             pre-loop
                             (reverse (map (fn [f c before i]
                                             ;; at its end test, an across name is not bound yet
                                             [(assoc f :index i) (if (= :across (:kind c)) before (conj before (:var c)))])
                                           fors' fors (reductions conj [] for-names) (range))))
            prologue (render (concat (when acc [[:bind acc (if acc-sentinel acc-sentinel 0)]])
                                     (when acc-first [[:bind acc-first true]])
                                     (keep (fn [[_ e]] (when (:first e) [:bind (:first e) true])) intos))
                             prologue epi)
            inner (if (seq initially) (list* 'do (concat initially [prologue])) prologue)
            ;; with groups (sequential; `and` in parallel), then the into variables
            var-groups (concat (map (fn [g] (map (fn [w] [(:var w) (:type w) (:init w)]) g)) withs)
                               (when (seq intos)
                                 [(map (fn [[v e]] [v (:type e) (:init e)]) intos)]))
            wrapped (reduce (fn [inner group]
                              (let [typed (filter second group)]
                                (list* (u "let")
                                       (apply list (map (fn [[v t init]]
                                                          (list v (if (and (nil? init) t (or (= "fixnum" (kw t)) (seq? t)))
                                                                    0 init)))
                                                        group))
                                       (concat (when (seq typed)
                                                 [(list* 'declare (map (fn [[v t]] (list 'type t v)) typed))])
                                               [inner]))))
                            inner
                            (reverse var-groups))]
        (list (u "block") bname wrapped)))))

(defn ^:macro-support expanders
  "ucl/loop's registry entry (I23), for defapi's :vocabularies."
  []
  {"loop" {:applies? (fn [form] (not (vector? (second form))))
           :expand   expand-loop
           :block    (fn [form] [(when (kw? (second form) #{"named"}) (nth form 2 nil))])}})
