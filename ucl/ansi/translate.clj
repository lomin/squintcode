#!/usr/bin/env bb
;; The ANSI Common Lisp test suite (ansi-test.common-lisp.dev, Paul F. Dietz,
;; MIT licence) as ucl tests (README §4.2, I46).
;;
;;   bb ucl/ansi/translate.clj          the sequence functions (§4.2)
;;   bb ucl/ansi/translate.clj loop     LOOP (§4.3): ansi_loop_test.cljc, REPORT-loop.md
;;
;; Fetches the suite at a pinned commit into ucl/out/ansi-test, reads the
;; files for the built sequence functions, and translates each test whose
;; data ucl has -- vectors of numbers and symbols -- into ucl. Writes
;;   ucl/test/ucl/ansi_sequences_test.cljc   the translated cases, as
;;       *-conformance-test deftests: the four hosts run them, and
;;       ucl/conformance.clj sends them back to SBCL and ECL (D60), which
;;       checks the translation as well as ucl
;;   ucl/ansi/REPORT.md                      per file: translated, skipped, why
;; A test that is skipped names its reason; the reasons are the report.
(require '[clojure.string :as str]
         '[clojure.java.shell :refer [sh]]
         '[babashka.fs :as fs])

(def commit "ca06bd919661af162c67407c9d994e881870bdb3")
(def repo "https://gitlab.common-lisp.net/ansi-test/ansi-test.git")
(def root (str (fs/parent (fs/parent (fs/absolutize *file*)))))   ; ucl/
(def suite (str root "/out/ansi-test"))

(def sequence-files
  ["sequences/count.lsp" "sequences/count-if.lsp" "sequences/count-if-not.lsp"
   "sequences/find.lsp" "sequences/find-if.lsp" "sequences/find-if-not.lsp"
   "sequences/position.lsp" "sequences/position-if.lsp" "sequences/position-if-not.lsp"
   "sequences/reduce.lsp"
   "data-and-control-flow/every.lsp" "data-and-control-flow/some.lsp"
   "data-and-control-flow/notany.lsp" "data-and-control-flow/notevery.lsp"
   "sequences/fill.lsp" "sequences/replace.lsp" "sequences/copy-seq.lsp" "sequences/subseq.lsp"
   "sequences/reverse.lsp" "sequences/nreverse.lsp" "sequences/sort.lsp" "sequences/stable-sort.lsp"
   "sequences/remove.lsp"
   "sequences/substitute.lsp" "sequences/substitute-if.lsp" "sequences/substitute-if-not.lsp"
   "sequences/nsubstitute.lsp" "sequences/nsubstitute-if.lsp" "sequences/nsubstitute-if-not.lsp"])

(def suites
  {"sequences" {:files sequence-files
                :ns "ucl.ansi-sequences-test" :out "ansi_sequences_test.cljc"
                :report "REPORT.md" :title "ucl's sequence functions" :section "§4.2, I46"}
   "loop"      {:files (into ["iteration/loop.lsp"]
                             (map #(str "iteration/loop" % ".lsp") (range 1 18)))
                :ns "ucl.ansi-loop-test" :out "ansi_loop_test.cljc"
                :report "REPORT-loop.md" :title "ucl/loop" :section "§4.3, I34"}})

(def suite-name (or (first *command-line-args*) "sequences"))
(def the-suite (or (get suites suite-name) (throw (ex-info (str "no suite " suite-name) {}))))
(def files (:files the-suite))

(defn fetch! []
  (when-not (fs/exists? (str suite "/.git"))
    (fs/create-dirs suite)
    (doseq [args [["init" "-q"] ["remote" "add" "origin" repo]
                  ["fetch" "-q" "--depth" "1" "origin" commit] ["checkout" "-q" "FETCH_HEAD"]]]
      (let [{:keys [exit err]} (apply sh "git" (concat args [:dir suite]))]
        (when-not (zero? exit) (throw (ex-info (str "git " (str/join " " args) ": " err) {})))))))

;; ---------------------------------------------------------------------------
;; A Common Lisp reader, for what the suite's files use. Symbols are read as
;; lower-case Clojure symbols, keywords as keywords; syntax ucl has no use for
;; becomes a marker that makes its test skip.
;; ---------------------------------------------------------------------------

(defn unsupported [what] {:unsupported what})

(defn read-cl [^String s]
  (let [n (count s), pos (volatile! 0)]
    (letfn [(peek* [] (when (< @pos n) (.charAt s @pos)))
            (next* [] (let [c (peek*)] (vswap! pos inc) c))
            (ws? [c] (and c (or (Character/isWhitespace ^char c))))
            (skip! []
              (loop []
                (let [c (peek*)]
                  (cond (ws? c) (do (next*) (recur))
                        (= c \;) (do (while (and (peek*) (not= (peek*) \newline)) (next*)) (recur))
                        (and (= c \#) (< (inc @pos) n) (= (.charAt s (inc @pos)) \|))
                        (do (next*) (next*)
                            (loop [depth 1]
                              (when (pos? depth)
                                (let [c (next*)]
                                  (cond (and (= c \|) (= (peek*) \#)) (do (next*) (recur (dec depth)))
                                        (and (= c \#) (= (peek*) \|)) (do (next*) (recur (inc depth)))
                                        :else (recur depth)))))
                            (recur))
                        :else nil))))
            (token []
              (let [sb (StringBuilder.)]
                (loop []
                  (let [c (peek*)]
                    (cond (nil? c) nil
                          (= c \|) (do (next*)
                                       (loop [] (let [d (next*)] (when (not= d \|) (.append sb (str "|" d)) (recur))))
                                       (recur))
                          (or (ws? c) (#{\( \) \' \" \;} c)) nil
                          :else (do (.append sb (str/lower-case (str c))) (next*) (recur)))))
                (str sb)))
            (atom* [^String t]
              (let [t (str/replace t #"\|(.)" "$1")]
                (cond (re-matches #"[+-]?\d+\.?" t) (parse-long (str/replace t #"\.$" ""))
                      (re-matches #"[+-]?\d+/\d+" t) (unsupported "ratio")
                      (re-matches #"[+-]?\d*\.\d+([eEdDfF][+-]?\d+)?|[+-]?\d+[eEdDfF][+-]?\d+" t) (unsupported "float")
                      (= t "nil") nil
                      (str/starts-with? t ":") (keyword (subs t 1))
                      :else (symbol (last (str/split t #"::?"))))))
            (form []
              (skip!)
              (let [c (next*)]
                (case c
                  nil (throw (ex-info "eof" {}))
                  \( (loop [xs []]
                       (skip!)
                       (if (= (peek*) \))
                         (do (next*) (apply list xs))
                         (let [x (form)]
                           (if (= x '.) (let [t (form)] (skip!) (next*) (unsupported "dotted pair"))
                               (recur (conj xs x))))))
                  \) (throw (ex-info "unbalanced )" {:pos @pos}))
                  \' (list 'quote (form))
                  \` (do (form) (unsupported "backquote"))
                  \, (do (when (= (peek*) \@) (next*)) (form) (unsupported "backquote"))
                  \" (let [sb (StringBuilder.)]
                       (loop [] (let [d (next*)]
                                  (cond (= d \\) (do (.append sb (next*)) (recur))
                                        (= d \") nil
                                        :else (do (.append sb d) (recur)))))
                       {:string (str sb)})
                  \# (let [d (next*)]
                       (case d
                         \' (list 'function (form))
                         \: (atom* (token))
                         \( (let [xs (form-list)] {:vector xs})
                         \\ (let [ch (next*) more (token)] (unsupported "character"))
                         \* (do (token) (unsupported "bit vector"))
                         \p (do (form) (unsupported "pathname"))
                         \P (do (form) (unsupported "pathname"))
                         \. (do (form) (unsupported "read-time eval"))
                         (\+ \-) (do (form) (form) (unsupported "feature expression"))
                         (if (Character/isDigit ^char d)
                           (do (while (Character/isDigit ^char (peek*)) (next*))
                               (let [e (next*)] (when (#{\a \A} e) (form)) (unsupported (str "#" e))))
                           (unsupported (str "#" d)))))
                  (do (vswap! pos dec) (atom* (token))))))
            (form-list []
              (loop [xs []]
                (skip!)
                (if (= (peek*) \)) (do (next*) xs) (recur (conj xs (form))))))]
      (loop [forms []]
        (skip!)
        (if (< @pos n) (recur (conj forms (form))) forms)))))

;; ---------------------------------------------------------------------------
;; Translation to ucl. Each fn returns the ucl form, or throws a skip whose
;; message is the reason.
;; ---------------------------------------------------------------------------

(defn skip [reason] (throw (ex-info reason {:skip reason})))

(def sequence-functions
  #{'count 'count-if 'count-if-not 'find 'find-if 'find-if-not 'position 'position-if
    'position-if-not 'reduce 'every 'some 'notany 'notevery
    'remove 'remove-if 'remove-if-not 'delete 'delete-if 'delete-if-not
    'substitute 'substitute-if 'substitute-if-not 'nsubstitute 'nsubstitute-if 'nsubstitute-if-not})

;; a new item before the item or predicate
(def substitutes
  #{'substitute 'substitute-if 'substitute-if-not 'nsubstitute 'nsubstitute-if 'nsubstitute-if-not})

;; the sequence first, then positional arguments, then keywords
(def sequence-first
  {'fill [1 :keys] 'replace [1 :keys] 'copy-seq [0 :keys] 'subseq [2 :positional]
   'reverse [0 :keys] 'nreverse [0 :keys] 'sort [1 :keys] 'stable-sort [1 :keys]})

;; Common Lisp functions the suite passes or calls, and their Clojure; each
;; is also in ucl/conformance.clj's table, which maps them back
(def cl->clojure
  {'identity 'identity, 'evenp 'even?, 'oddp 'odd?, 'not 'not, 'null 'nil?, (symbol "1+") 'inc, (symbol "1-") 'dec,
   'zerop 'zero?, 'plusp 'pos?, 'minusp 'neg?, '+ '+, '- '-, '* '*, '< '<, '> '>, '<= '<=,
   '>= '>=, '= '==, (symbol "/=") 'not=, 'max 'ucl/max, 'min 'ucl/min,
   ;; on a vector, as LOOP's tests use them
   'aref 'ucl/elt, 'elt 'ucl/elt, 'length 'ucl/length, 'mod 'mod,
   ;; on what the translated cases hold -- numbers, keywords, nil -- Clojure's
   ;; = is eql (tests may use anything; solutions may not)
   'eql '=, 'eq '=, 'eqt '=, 'equal '=, 'equalt '=,
   ;; equalp compares vectors element by element: the generated `equalp`
   'equalp 'equalp, 'equalpt 'equalp})

(def suite-globals
  "Globals a suite's files build from lists, which ucl has not, and that hold
   a fixed table: their value as a ucl form. iteration/loop6.lsp's tables 1 and
   2 are ((a . 1) (b . 2) (c . 3)) under eq and eql. Table 5's keys are 1 2 3
   and its values conses: they stand in as keywords, so a test that reads a
   value returns another value and is held out, never passed."
  {'*loop.6.hash.1* '(ucl/make-hash-table :initial-contents {:a 1 :b 2 :c 3})
   '*loop.6.hash.2* '(ucl/make-hash-table :initial-contents {:a 1 :b 2 :c 3})
   '*loop.6.hash.5* '(ucl/make-hash-table :initial-contents {1 :a 2 :b 3 :c})})

(def special #{'if 'when 'unless 'cond 'and 'or 'progn 'let 'let* 'setf 'incf 'decf 'lambda
               'function 'quote 'values 'locally 'declare})

(declare tr)

;; LOOP's clause syntax passes through as written: its keywords are symbols,
;; the type after of-type and the name after named, into or using are not
;; evaluated; every other form is translated.
(def loop-raw-after #{'of-type 'named 'into 'using})

(defn tr-loop [[_ & clauses]]
  (loop [cs clauses out [] raw? false]
    (if (empty? cs)
      (list* 'ucl/loop out)
      (let [[c & more] cs]
        (cond
          raw? (recur more (conj out (if (seq? c) (apply list c) c)) false)
          (= c 't) (recur more (conj out true) false)
          (contains? suite-globals c) (recur more (conj out (suite-globals c)) false)
          (symbol? c) (recur more (conj out c) (contains? loop-raw-after c))
          :else (recur more (conj out (tr c)) false))))))

(defn tr-symbol-data
  "A quoted symbol is data: a keyword, which eql compares as Common Lisp
   compares symbols."
  [s]
  (cond (= s 'nil) nil
        (= s 't) true
        (symbol? s) (keyword (name s))
        (number? s) s
        :else (skip "quoted data other than a symbol or number")))

(defn tr-vector
  "#(...) or a quoted list as :initial-contents: a general vector of numbers,
   keywords and nil."
  [xs]
  (let [els (mapv (fn [x] (cond (or (nil? x) (number? x)) x
                                (symbol? x) (tr-symbol-data x)
                                (map? x) (skip (str "a vector of " (or (:unsupported x) "strings")))
                                :else (skip "a nested list in a vector")))
                  xs)]
    (list 'ucl/make-array (count els) :initial-contents els)))

(defn eql-value
  "A function value that is eql (or eq, equal, eqt, ...): a literal fn, since
   a bare `=` in value position is ambiguous to ucl/conformance.clj (LOOP's
   keyword)."
  [mapped]
  (if (= mapped '=) '(fn [a b] (= a b)) mapped))

(defn tr-fn-value [f]
  (cond (and (seq? f) (= 'function (first f)))
        (let [x (second f)]
          (cond (symbol? x) (eql-value (or (cl->clojure x) (skip (str "the function " x))))
                (and (seq? x) (= 'lambda (first x))) (tr x)
                :else (skip "a function form")))
        (and (seq? f) (= 'quote (first f)) (symbol? (second f)))
        (let [m (or (cl->clojure (second f)) (skip (str "the function " (second f))))]
          (if (= m '=) (eql-value m) (list 'quote m)))
        :else (tr f)))

(defn tr-lambda [[_ params & body]]
  (when-not (and (seq? params) (every? symbol? params) (not-any? #(str/starts-with? (name %) "&") params))
    (skip "a lambda list with &optional, &rest or &key"))
  (list* 'fn (vec params) (map tr (remove #(and (seq? %) (= 'declare (first %))) body))))

(defn tr-let [op [_ bindings & body]]
  (list* (if (= op 'let) 'ucl/let 'ucl/let*)
         (apply list (map (fn [b] (cond (symbol? b) (list b nil)
                                        (= 1 (count b)) (list (first b) nil)
                                        :else (list (first b) (tr (second b)))))
                          bindings))
         (map tr (remove #(and (seq? %) (= 'declare (first %))) body))))

(defn tr-keyword-args [args]
  (mapcat (fn [[k v]]
            (when-not (keyword? k) (skip "a keyword argument that is not a literal keyword"))
            [k (if (contains? #{:key :test :test-not} k) (if (nil? v) nil (tr-fn-value v)) (tr v))])
          (partition 2 args)))

(defn tr-call [[h & args :as f]]
  (cond
    (substitutes h)
    (let [[new a0 sq & kvs] args
          item? (contains? #{'substitute 'nsubstitute} h)]
      (when (nil? sq) (skip "a list (nil, the empty list, as a sequence)"))
      (when (odd? (count kvs)) (skip "an odd number of keyword arguments (a program-error)"))
      (list* (symbol "ucl" (name h)) (tr new) (if item? (tr a0) (tr-fn-value a0)) (tr sq)
             (tr-keyword-args kvs)))
    (sequence-functions h)
    (let [a0 (first args)
          item? (contains? #{'count 'find 'position 'remove 'delete} h)]
      (when (some nil? (if (#{'every 'some 'notany 'notevery} h) (rest args) [(second args)]))
        (skip "a list (nil, the empty list, as a sequence)"))
      (case h
        (every some notany notevery)
        (list* (symbol "ucl" (name h)) (tr-fn-value a0) (map tr (rest args)))
        (let [[sq & kvs] (rest args)]
          (when (odd? (count kvs)) (skip "an odd number of keyword arguments (a program-error)"))
          (list* (symbol "ucl" (name h)) (if item? (tr a0) (tr-fn-value a0)) (tr sq) (tr-keyword-args kvs)))))
    (and (= h 'make-array)
         (let [ks (set (take-nth 2 (rest args)))] (and (ks :fill-pointer) (ks :initial-contents))))
    ;; ucl v1 cannot fill a vector that has a fill pointer at construction;
    ;; pushing the elements below the fill pointer builds the same sequence
    (let [[n & kvs] args
          m   (apply hash-map kvs)
          ic  (let [v (:initial-contents m)] (if (and (seq? v) (= 'quote (first v))) (second v) v))
          els (cond (map? ic) (if (:vector ic) (:vector ic) (skip "make-array of unsupported contents"))
                    (seq? ic) ic
                    :else (skip "computed :initial-contents"))
          fp  (:fill-pointer m)
          els (cond (= fp 't) els
                    (integer? fp) (take fp els)
                    :else (skip "a computed fill pointer"))
          _   (when-not (every? #(#{:fill-pointer :initial-contents :adjustable} %) (keys m))
                (skip "make-array with a fill pointer and other options"))
          v   (gensym "v")]
      (list* 'ucl/let (list (list v (list 'ucl/make-array 0 :adjustable true :fill-pointer 0)))
             (concat (map (fn [x] (list 'ucl/vector-push-extend (tr-symbol-data x) v)) els) [v])))
    (sequence-first h)
    (let [[npos mode] (sequence-first h)
          [sq & more] args
          pos (take npos more)
          kvs (drop npos more)]
      (when (nil? sq) (skip "a list (nil, the empty list, as a sequence)"))
      (when (and (= mode :keys) (odd? (count kvs))) (skip "an odd number of keyword arguments (a program-error)"))
      (list* (symbol "ucl" (name h)) (tr sq)
             (concat (map (fn [x] (if (contains? #{'sort 'stable-sort} h) (tr-fn-value x) (tr x))) pos)
                     (if (= mode :keys) (tr-keyword-args kvs) (map tr kvs)))))
    (= h 'make-array)
    (let [[n & kvs] args
          ;; '(5): the dimensions of a one-dimensional array
          n (if (and (seq? n) (= 'quote (first n)) (seq? (second n)) (= 1 (count (second n))))
              (first (second n))
              n)]
      (list* 'ucl/make-array (tr n)
             (mapcat (fn [[k v]]
                       (case k
                         :initial-contents [k (let [v (if (and (seq? v) (= 'quote (first v))) (second v) v)]
                                                (cond (map? v) (if (:vector v) (vec (map tr-symbol-data (:vector v)))
                                                                   (skip "make-array of unsupported contents"))
                                                      (seq? v) (vec (map tr-symbol-data v))
                                                      :else (skip "computed :initial-contents")))]
                         :initial-element [k (tr v)]
                         :element-type [k (let [t (second v)]
                                            (cond (= t 't) ''t
                                                  (= t 'fixnum) ''fixnum
                                                  :else (skip (str "the element type " (pr-str t)))))]
                         (:fill-pointer :adjustable) [k (tr v)]
                         (skip (str "make-array " k))))
                     (partition 2 kvs))))
    (= h 'notnot) (list 'if (tr (first args)) true nil)
    ;; ansi-aux's predicates of one item
    (= h 'is-eql-p) (list 'fn '[y] (list '= (tr (first args)) 'y))
    (= h 'is-not-eql-p) (list 'fn '[y] (list 'if (list '= (tr (first args)) 'y) nil true))
    ;; a fresh general vector of the values
    (= h 'vector) (list 'ucl/make-array (count args) :initial-contents (mapv tr args))
    ;; a vector's elements as a list: what `contents` gives as a vector
    (and (= h 'map) (= ''list (first args)) (= '(function identity) (second args)) (= 3 (count args)))
    (list 'contents (tr (nth args 2)))
    (cl->clojure h) (list* (cl->clojure h) (map tr args))
    :else (skip (str "the function " h))))

(defn tr [f]
  (cond
    (nil? f) nil
    (number? f) f
    (= f 't) true
    (keyword? f) f
    (symbol? f) (if (contains? suite-globals f) (suite-globals f) f)   ; a variable
    (map? f) (cond (:vector f) (tr-vector (:vector f))
                   (:string f) (skip "a string")
                   :else (skip (:unsupported f)))
    (seq? f)
    (let [h (first f)]
      (case h
        quote (let [x (second f)]
                (cond (symbol? x) (tr-symbol-data x)
                      (seq? x) (skip "a list")
                      (map? x) (tr x)
                      :else x))
        function (tr-fn-value f)
        lambda (tr-lambda f)
        (let let*) (tr-let h f)
        progn (list* 'do (map tr (rest f)))
        ;; Common Lisp's NOT returns T or NIL, Clojure's true or false
        not (list 'if (tr (second f)) nil true)
        (if when unless and or) (list* h (map tr (rest f)))
        cond (list* 'cond (mapcat (fn [c] (let [[t & b] c]
                                            [(if (= t 't) :else (tr t)) (if (next b) (list* 'do (map tr b)) (tr (first b)))]))
                                  (rest f)))
        (setf incf decf) (let [[place & more] (rest f)]
                           (when-not (symbol? place) (skip (str h " of a place other than a variable")))
                           (list* (symbol "ucl" (name h)) place (map tr more)))
        ;; several values: a vector, evaluated left to right as values is
        values (if (= 1 (count (rest f))) (tr (second f)) (list* 'vector (map tr (rest f))))
        (not-mv) (list 'if (tr (second f)) nil true)
        (notnot-mv) (list 'if (tr (second f)) true nil)
        locally (let [body (remove #(and (seq? %) (= 'declare (first %))) (rest f))]
                  (if (next body) (list* 'do (map tr body)) (tr (first body))))
        signals-error (skip "an error test")
        loop (tr-loop f)
        block (list* 'ucl/block (second f) (map tr (nnext f)))
        return (list* 'ucl/return (map tr (rest f)))
        return-from (list* 'ucl/return-from (second f) (map tr (nnext f)))
        setq (let [pairs (partition 2 (rest f))]
               (when-not (every? (comp symbol? first) pairs) (skip "setq of a non-symbol"))
               (list* 'ucl/setf (mapcat (fn [[v x]] [v (tr x)]) pairs)))
        (if (symbol? h) (tr-call f) (skip "a call of a non-symbol"))))
    :else (skip (str "the datum " (pr-str f)))))

(defn tr-expected-1 [v]
  (let []
    (cond (or (number? v) (nil? v) (keyword? v)) v
          (= v 't) true
          (symbol? v) (tr-symbol-data v)
          (and (seq? v) (= 'quote (first v))) (tr-expected-1 (second v))
          (and (seq? v) (every? #(or (nil? %) (number? %) (symbol? %)) v))
          {:contents (mapv (fn [x] (if (nil? x) nil (tr-symbol-data x))) v)}
          (seq? v) (skip "an expected list")
          (and (map? v) (:vector v)) {:contents (mapv (fn [x] (if (nil? x) nil (tr-symbol-data x))) (:vector v))}
          (map? v) (skip (str "an expected " (or (:unsupported v) "string")))
          :else (skip (str "the expected " (pr-str v))))))

(defn tr-expected
  "One value, or several as a vector (see `values`)."
  [vals]
  (if (= 1 (count vals))
    (tr-expected-1 (first vals))
    (vec (map tr-expected-1 vals))))

(defn translate-test
  "{:name .. :case form} or {:name .. :skip reason}."
  [[_ tname form & expected]]
  (try
    (let [e (tr-expected expected)
          f (tr form)
          ;; an expected vector compares with the host array's elements
          [e f] (cond
                  (and (map? e) (:contents e))
                  [(:contents e) (if (= 'contents (and (seq? f) (first f))) f (list 'contents f))]
                  (vector? e)
                  (let [wrap (fn wrap [g]
                               (cond (and (seq? g) (= 'vector (first g)) (= (count e) (count (rest g))))
                                     (list* 'vector (map (fn [ex fx] (if (and (map? ex) (not= 'contents (and (seq? fx) (first fx))))
                                                                       (list 'contents fx) fx))
                                                         e (rest g)))
                                     ;; through the tail of a let or do
                                     (and (seq? g) (contains? #{'ucl/let 'ucl/let* 'do} (first g)))
                                     (concat (butlast g) [(wrap (last g))])
                                     :else (skip "multiple values with a vector")))]
                    [(mapv #(if (map? %) (:contents %) %) e) (if (some map? e) (wrap f) f)])
                  :else [e f])]
      (when (and (vector? e) (some map? e)) (skip "multiple values with a vector"))
      (pr-str f)                          ; realize every lazy part inside the try
      {:name (str tname) :case (list 'is (list '= e f))})
    (catch clojure.lang.ExceptionInfo ex
      (if-let [r (:skip (ex-data ex))]
        {:name (str tname) :skip r}
        (throw ex)))))

;; ---------------------------------------------------------------------------
;; Validation on the JVM backend: a case ucl rejects at expansion is skipped
;; with ucl's message (one would stop the whole file compiling on every
;; host); a case that compiles but returns another value is held out and
;; reported -- those are what to look at.
;; ---------------------------------------------------------------------------

(def validator
  "(ns ansi-validate (:require [ucl.api :as ucl] [clojure.edn :as edn]))
   (defn contents [v] (loop [i 0 acc []] (if (< i (ucl/length v)) (recur (inc i) (conj acc (ucl/elt v i))) acc)))
   (defn equalp
   \"Common Lisp's equalp on what the cases hold: vectors element by element.\"
   [a b]
   (cond (number? a) (and (number? b) (== a b))
         (or (nil? a) (keyword? a) (true? a) (false? a)) (= a b)
         :else (= (contents a) (contents b))))
   (defn ucl-message [t]
     (loop [e t] (cond (nil? e) nil
                       (and (instance? clojure.lang.ExceptionInfo e) (= 'ucl (:library (ex-data e)))) (ex-message e)
                       :else (recur (.getCause e)))))
   (let [cases (edn/read-string (slurp (first *command-line-args*)))]
     (prn (vec (for [[_ [_ [_ expected form]]] cases]   ; [name (is (= expected form))]
                 (try (let [v (binding [*ns* (the-ns 'ansi-validate) *err* (java.io.StringWriter.)]
                                (eval form))]
                        (if (= expected v) [:ok] [:fails (pr-str v)]))
                      (catch Throwable t
                        (if-let [m (ucl-message t)] [:rejected m] [:throws (.toString t)])))))))")

(defn validate!
  "The JVM's verdict on each translated case."
  [cases]
  (let [dir (str root "/out/ansi")
        in  (str dir "/cases.edn")
        src (str dir "/validate.clj")]
    (fs/create-dirs dir)
    (spit in (pr-str cases))                     ; [[name (is (= expected form))] ...]
    (spit src validator)
    (let [{:keys [out err exit]} (sh "clojure" "-Sdeps" "{:paths [\"shared\" \"backends/jvm\" \"testkit/jvm\"]}"
                                     "-M" src in :dir root)]
      (when-not (zero? exit) (throw (ex-info (str "validation failed: " err) {})))
      (read-string (last (str/split-lines out))))))

;; ---------------------------------------------------------------------------
;; Output
;; ---------------------------------------------------------------------------

(defn render
  "A deftest with one `testing` per line."
  [[_ tname & cases]]
  (str "(deftest " tname "\n"
       (str/join "\n" (for [c cases] (str "  " (binding [*print-namespace-maps* false] (pr-str c)))))
       ")"))

(defn deftests [file results]
  (let [base (-> file fs/file-name (str/replace #"\.lsp$" ""))
        cases (filter #(and (:case %) (not (:held %))) results)]
    (map-indexed
     (fn [i chunk]
       (list* 'deftest (symbol (str "ansi-" base "-" (inc i) "-conformance-test"))
              (map (fn [{:keys [name case]}] (list 'testing name case)) chunk)))
     (partition-all 40 cases))))

(defn reason-class [r]
  (cond (str/starts-with? r "the function ") (str "uses a function ucl does not have (" (subs r 13) ")")
        :else r))

(defn -main []
  (fetch!)
  (let [per-file (vec (for [f files]
                        (let [forms (read-cl (slurp (str suite "/" f)))
                              tests (filter #(and (seq? %) (= 'deftest (first %))) forms)]
                          [f (mapv translate-test tests)])))
        cands    (vec (for [[_ rs] per-file r rs :when (:case r)] r))
        verdicts (zipmap (map :name cands) (validate! (mapv (fn [r] [(:name r) (:case r)]) cands)))
        per-file (vec (for [[f rs] per-file]
                        [f (mapv (fn [r]
                                   (let [[k m] (get verdicts (:name r))]
                                     (case k
                                       (nil :ok) r
                                       :rejected {:name (:name r) :skip (str "ucl rejects at expansion: "
                                                                            (first (str/split m #"\. ")))}
                                       {:name (:name r) :held k :detail m :case (:case r)})))
                                 rs)]))
        out (str root "/test/ucl/" (:out the-suite))]
    (spit out
          (str ";; GENERATED by ucl/ansi/translate.clj from the ANSI Common Lisp test suite\n"
               ";; (ansi-test.common-lisp.dev, commit " commit "; Copyright 2004 Paul F. Dietz,\n"
               ";; MIT licence). Do not edit: change the translator and run it again.\n"
               ";; Each case keeps its ansi-test name; ucl/ansi/REPORT.md lists what was skipped.\n"
               "(ns " (:ns the-suite) "\n"
               "  (:require [ucl.test :refer [deftest is testing]]\n"
               "            [ucl.api :as ucl]))\n\n"
               "(defn contents\n"
               "  \"A host vector's elements as a Clojure vector; ucl/conformance.clj reads it as identity.\"\n"
               "  [v]\n"
               "  (loop [i 0 acc []]\n"
               "    (if (< i (ucl/length v)) (recur (inc i) (conj acc (ucl/elt v i))) acc)))\n\n"
               "(defn equalp\n   \"Common Lisp's equalp on what the cases hold: vectors element by element.\"\n   [a b]\n   (cond (number? a) (and (number? b) (== a b))\n         (or (nil? a) (keyword? a) (true? a) (false? a)) (= a b)\n         :else (= (contents a) (contents b))))\n\n"
               (str/join "\n\n" (for [[f rs] per-file, d (deftests f rs)] (render d)))
               "\n"))
    (let [total (reduce + (map (comp count second) per-file))
          done  (reduce + (map #(count (filter (fn [r] (and (:case r) (not (:held r)))) (second %))) per-file))
          held  (for [[f rs] per-file r rs :when (:held r)] [f r])
          reasons (frequencies (for [[_ rs] per-file r rs :when (:skip r)] (reason-class (:skip r))))]
      (spit (str root "/ansi/" (:report the-suite))
            (str "# The ANSI test suite on " (:title the-suite) "\n\n"
                 "Generated by `bb ucl/ansi/translate.clj"
                 (when (not= "sequences" suite-name) (str " " suite-name))
                 "` from ansi-test commit `" commit "`\n"
                 "(README " (:section the-suite) "). A translated case runs on the four hosts and, through\n"
                 "`ucl/conformance.clj`, on SBCL and ECL. A skipped case needs what ucl does not\n"
                 "have -- mostly lists, strings and characters -- or is an error test.\n\n"
                 "**" done " of " total " tests translated.**\n\n"
                 "| file | tests | translated | skipped |\n|---|---|---|---|\n"
                 (str/join (for [[f rs] per-file]
                             (str "| `" f "` | " (count rs) " | "
                                  (count (filter #(and (:case %) (not (:held %))) rs)) " | "
                                  (count (filter :skip rs)) " |\n")))
                 (if (seq held)
                   (str "\n## Held out: ucl's value differs\n\n"
                        "Each compiles on ucl but returns another value on the JVM. Each is a deviation\n"
                        "to fix or to record, not a pass.\n\n| test | ucl | case |\n|---|---|---|\n"
                        (str/join (for [[_ r] held]
                                    (str "| " (:name r) " | " (name (:held r)) " " (str/replace (str (:detail r)) "|" "\\|")
                                         " | `" (str/replace (pr-str (:case r)) "|" "\\|") "` |\n"))))
                   "\n## Held out\n\nNone: every translated case returns the suite's value on ucl.\n")
                 "\n## Why tests were skipped\n\n| reason | tests |\n|---|---|\n"
                 (str/join (for [[r c] (sort-by (comp - val) reasons)] (str "| " r " | " c " |\n")))))
      (println done "of" total "tests translated; wrote" out))))

(-main)
