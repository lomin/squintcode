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
      ;; nil: a value the clause binds and nothing reads (CLHS 6.1.1.7)
      (and (nil? v) (< i (count ts))) [(gensym "ignored") (inc i)]
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

      (= "being" p)
      ;; for v being {each|the} {hash-key|hash-keys|hash-value|hash-values}
      ;;   {in|of} table [using ({hash-value|hash-key} other)]  (6.1.2.1.6)
      (let [i (if (kw? (get ts (inc i)) #{"each" "the"}) (+ i 2) (inc i))
            what (kw (get ts i))
            _ (when-not (contains? #{"hash-key" "hash-keys" "hash-value" "hash-values"} what)
                (fail! (str "for " v " being " (pr-str (get ts i)) ": "
                            (if (contains? #{"symbol" "symbols" "present-symbol" "present-symbols"
                                             "external-symbol" "external-symbols"} what)
                              "package iteration needs packages, which ucl does not have (D53)."
                              "expected hash-keys or hash-values"))
                       form))
            _ (when-not (kw? (get ts (inc i)) #{"in" "of"})
                (fail! (str "for " v " being the " what ": expected in or of") form))
            table (get ts (+ i 2))
            i (+ i 3)
            keys? (contains? #{"hash-key" "hash-keys"} what)
            [other i] (if (kw? (get ts i) #{"using"})
                        (let [u (get ts (inc i))]
                          (when-not (and (seq? u) (= 2 (count u)) (symbol? (second u))
                                         (kw? (first u) (if keys? #{"hash-value"} #{"hash-key"})))
                            (fail! (str "for " v ": expected using (" (if keys? "hash-value" "hash-key")
                                        " var), found " (pr-str u))
                                   form))
                          [(second u) (+ i 2)])
                        [nil i])]
        [{:kind :hash :var v :type type :table table :keys? keys? :other other} i])

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
              ;; clauses joined by `and` are a group: initialized and stepped in parallel
              (let [gid (count (:fors p))
                    [cs i] (loop [i (inc i) cs []]
                             (let [[c i] (parse-for ts i form)
                                   cs (conj cs (assoc c :group gid))]
                               (if (kw? (get ts i) #{"and"}) (recur (inc i) cs) [cs i])))
                    cs (if (next cs) (mapv #(assoc % :grouped true) cs) cs)]
                (when main? (fail! (str "a for clause after a main clause (CLHS 6.1.1.4): for "
                                        (:var (first cs)))
                                   form))
                (recur i (update p :fors into cs) main?))

              (contains? #{"initially" "finally"} k)
              (let [[fs i'] (compound-forms ts (inc i))]
                (recur i' (update p (keyword k) into fs) main?))

              (contains? #{"while" "until" "repeat" "always" "never" "thereis"} k)
              (recur (+ i 2) (update p :main conj {:clause (keyword k) :form (get ts (inc i))}) true)

              (= "loop-finish" k)
              (fail! "loop-finish is a form: do (loop-finish)" form)

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
     [:end test pos? e] when `test` is truthy (pos?) or falsy, the epilogue:
                        `e`, or `epi`"
  [ops tail epi]
  (reduce (fn [inner [op a b e]]
            (let [epi (if e e epi)]
              (case op
                :stmt (contract/seq-do a inner)
                :bind (list 'let [a b] inner)
                :end  (if b (list 'if a epi inner) (list 'if a inner epi)))))
          tail
          (reverse ops)))

(defn ^:macro-support guarded
  "`f` run when `guard` -- [sym positive?], or nil for always -- holds; else `otherwise`."
  [guard f otherwise]
  (cond
    (nil? guard) f
    (second guard) (list 'if (first guard) f otherwise)
    :else (list 'if (first guard) otherwise f)))

(defn ^:macro-support nested-loop?
  "Is `form` a ucl/loop of its own -- whose loop-finish is its own?"
  [form]
  (and (seq? form) (= "loop" (contract/head-name form)) (not (vector? (second form)))))

(defn ^:macro-support finish->exit
  "`form` with each (loop-finish) of this loop -- not a nested one's -- as `exit`."
  [form exit]
  (cond
    (and (seq? form) (= "loop-finish" (contract/head-name form)))
    (if (next form) (fail! "loop-finish takes no arguments" form) exit)
    (nested-loop? form) form
    (seq? form) (apply list (map #(finish->exit % exit) form))
    (vector? form) (mapv #(finish->exit % exit) form)
    (map? form) (into {} (map (fn [[k v]] [(finish->exit k exit) (finish->exit v exit)]) form))
    :else form))

(defn ^:macro-support expand-loop
  "The registry's :expand for ucl/loop."
  [backend form]
  (let [{:keys [simple withs fors main initially finally] bname :name} (parse-loop form)
        u (fn [n] (ucl-sym form n))]
    (if simple
      (list (u "block") nil (list* 'loop [] (concat simple [(list 'recur)])))
      (let [_ (let [vs (concat (map :var fors) (keep :other fors) (mapcat #(map :var %) withs))]
                (when-not (= (count vs) (count (set vs)))
                  ;; SBCL signals, ECL lets the later one win (D60)
                  (fail! (str "a for or with name is bound twice: " (pr-str vs)) form)))
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
                                             (set (filter symbol? (mapcat (fn [c] [(:from c) (:limit c) (:by c) (:vector c) (:table c)]) fors)))
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
            ;; (loop-finish) ends the iteration as an end test does: an exit
            ;; (D55) from a block around the loop, with the epilogue as its value
            finish-exit (list (u "return-from") 'loop-finish epi)
            finishes? (not= main (finish->exit main finish-exit))
            main  (finish->exit main finish-exit)
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
                            :hash
                            ;; the iterator's next entry, nil at the end; the key
                            ;; and value are read from it at the top of each
                            ;; iteration, like an across element (D58)
                            (let [[bs table] (once (:table c) "table" assigned)
                                  it (gensym "it") e (gensym "entry")
                                  op (fn [k] (contract/op backend :hash-iter k))
                                  [kv vv] (if (:keys? c) [(:var c) (:other c)] [(:other c) (:var c)])
                                  reads (vec (concat (when kv [[kv ((op :key) e)]])
                                                     (when vv [[vv ((op :value) e)]])))]
                              {:onces (into bs [it ((op :start) table)])
                               :vars [e]
                               :types {}
                               :init [[e ((op :next) it)]]
                               :step [[e ((op :next) it)]]
                               :test (list 'nil? e)
                               :top reads
                               :after reads})
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
                            (if (and idx (= :arith (:kind c)) (= :up (:dir c)) (not (:grouped c))
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
            ;; the names each for clause binds: its variable, and a hash clause's `using` name
            clause-names (fn [c] (if (:other c) [(:var c) (:other c)] [(:var c)]))
            for-names (mapcat clause-names fors)
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
            ;; for clauses in groups (`and`): [[index [f c]] ...] per group
            groups (partition-by (fn [[_ [_ c]]] (:group c)) (map-indexed vector (map vector fors' fors)))
            iterator? (fn [c] (contains? #{:across :hash} (:kind c)))
            ;; a group's values in parallel: each into a temporary, then bound
            parallel (fn [pairs]
                       (let [ts (map (fn [[v x]] [(gensym (name v)) v x]) pairs)]
                         (concat (map (fn [[t _ x]] [:bind t x]) ts)
                                 (map (fn [[t v _]] [:bind v t]) ts))))
            ;; A group steps as SBCL's LOOP does: each iterator's index or entry
            ;; and its end test, in order; then every variable, in parallel; then
            ;; the arithmetic limits, on the new values (H67)
            step-ops (vec (mapcat
                           (fn [group]
                             (if (next group)
                               (concat
                                (mapcat (fn [[_ [f c]]]
                                          (when (iterator? c)
                                            (concat (map (fn [[v x]] [:bind v x]) (:step f))
                                                    [[:end (:test f) true]])))
                                        group)
                                (parallel (mapcat (fn [[_ [f c]]] (if (iterator? c) (:after f) (:step f))) group))
                                (keep (fn [[_ [f c]]] (when (and (not (iterator? c)) (:test f)) [:end (:test f) true]))
                                      group))
                               (let [[[i [f _]]] group]
                                 (concat (map (fn [[v x]] [:bind v x]) (:step f))
                                         (when (:test f) [[:end (:test f) true]])
                                         (after-ops i f)))))
                           groups))
            top-ops (vec (mapcat (fn [f] (map (fn [[v x]] [:bind v x]) (:top f))) fors'))
            body (render (concat top-ops main-ops step-ops) (list* 'recur params) epi)
            the-loop (list 'loop (vec (mapcat (fn [s] [(hint s) s]) params)) body)
            the-loop (if finishes? (list (u "block") 'loop-finish the-loop) the-loop)
            ;; the prologue: each for clause in turn, its end test at its first value
            pre-loop (render (map (fn [[n x]] [:bind n x]) @repeats) the-loop epi)
            ;; the prologue: each group in turn, its end tests at its first
            ;; values, each test's epilogue binding what is not bound yet
            prologue (reduce
                      (fn [inner [group before]]
                        (let [onces (mapcat (fn [[_ [f _]]] (map (fn [[a b]] [:bind a b]) (partition 2 (:onces f))))
                                            group)]
                          (if (next group)
                            ;; as SBCL and ECL: the counters' starts, in parallel;
                            ;; then iterators in order; then the = clauses' first
                            ;; values, which see those (H67); then the limits
                            (let [values (filter (fn [[_ [_ c]]] (= :arith (:kind c))) group)
                                  equals (filter (fn [[_ [_ c]]] (= :equals (:kind c))) group)
                                  bound-a (into before (mapcat (fn [[_ [_ c]]] (clause-names c)) values))
                                  [its-ops bound-b]
                                  (reduce (fn [[ops bound] [_ [f c]]]
                                            [(concat ops
                                                     (map (fn [[v x]] [:bind v x]) (:init f))
                                                     [[:end (:test f) true (epi-at bound)]]
                                                     (map (fn [[v x]] [:bind v x]) (:after f)))
                                             (into bound (clause-names c))])
                                          [[] bound-a]
                                          (filter (fn [[_ [_ c]]] (iterator? c)) group))]
                              (render (concat onces
                                              (parallel (mapcat (fn [[_ [f _]]] (:init f)) values))
                                              its-ops
                                              (parallel (mapcat (fn [[_ [f _]]] (:init f)) equals))
                                              (keep (fn [[_ [f _]]]
                                                      (when (:test f)
                                                        [:end (:test f) true
                                                         (epi-at (into bound-b (mapcat (fn [[_ [_ c]]] (clause-names c)) equals)))]))
                                                    values))
                                      inner epi))
                            (let [[[i [f c]]] group
                                  ;; at its end test, an iterator's name is not bound yet
                                  bound (if (iterator? c) before (into before (clause-names c)))]
                              (render (concat onces
                                              (map (fn [[v x]] [:bind v x]) (:init f))
                                              (when (:test f) [[:end (:test f) true (epi-at bound)]])
                                              (after-ops i f))
                                      inner epi)))))
                      pre-loop
                      (reverse (map vector groups
                                    (reductions into [] (map (fn [g] (mapcat (fn [[_ [_ c]]] (clause-names c)) g))
                                                             groups)))))
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
  {"loop-finish" {:applies? (fn [_] false)   ; only ucl/loop rewrites it
                  :expand   (fn [_ form]
                              (fail! "loop-finish outside a ucl/loop's main clauses -- it ends the loop it is in"
                                     form))}
   "loop" {:applies? (fn [form] (not (vector? (second form))))
           :expand   expand-loop
           :block    (fn [form] [(when (kw? (second form) #{"named"}) (nth form 2 nil))])}})
