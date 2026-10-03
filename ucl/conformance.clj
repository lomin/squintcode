#!/usr/bin/env bb
;; Conformance against Common Lisp (README D60): every (is (= expected form))
;; inside a deftest whose name ends in -conformance-test is translated to
;; Common Lisp and run on SBCL and ECL; each must print `expected`. The four
;; hosts run the same assertions as ordinary tests, so all six agree.
;;
;;   bb ucl/conformance.clj test/ucl/loop_test.cljc ...
;;
;; The forms are written in a subset that translates: ucl's vocabulary
;; through the alias `ucl`, numbers, booleans, vectors, and the Clojure
;; functions in `clojure->cl`. Anything else fails the run, naming the form.
(require '[clojure.string :as str]
         '[clojure.java.shell :refer [sh]])

(def clojure->cl
  {'inc "1+", 'dec "1-", '== "=", 'even? "evenp", 'odd? "oddp", 'zero? "zerop",
   'pos? "plusp", 'neg? "minusp", 'nil? "null", 'quot "truncate", 'rem "rem",
   'mod "mod", 'not "not", '+ "+", '- "-", '* "*", '< "<", '> ">", '<= "<=",
   '>= ">=", 'max "max", 'min "min", 'if "if", 'when "when", 'unless "unless",
   'cond "cond", 'and "and", 'or "or", 'do "progn", 'return "return", 'list "list",
   ;; the sequence functions' cases (README §4.2): on numbers, keywords and nil,
   ;; Clojure's = is eql
   'identity "identity", 'vector "vector", 'not= "/=", '= "eql"})

(def special-heads
  "Table entries never rendered as function values: operators, and `=`, which
   is also LOOP's keyword (`for x = 1`). Pass eql as a value as (fn [a b] (= a b))."
  '#{if when unless cond and or do return =})

(defn function-name
  "The Common Lisp function a Clojure symbol in value position names, or nil:
   one of the table's functions, or ucl's min and max."
  [f]
  (cond (and (nil? (namespace f)) (contains? clojure->cl f) (not (special-heads f))) (clojure->cl f)
        (and (= "ucl" (namespace f)) (contains? #{"min" "max"} (name f))) (name f)
        :else nil))

(defn fail [& msg] (binding [*out* *err*] (apply println msg)) (System/exit 1))

(defn cl
  "Common Lisp source for a Clojure form."
  [f]
  (cond
    (nil? f) "nil"
    (true? f) "t"
    (false? f) "nil"
    (number? f) (str f)
    (string? f) (pr-str f)
    (keyword? f) (str f)
    (symbol? f) (cond (function-name f) (str "#'" (function-name f))
                      (= "ucl" (namespace f)) (name f)
                      (namespace f) (fail "conformance: no Common Lisp for" f)
                      :else (str f))
    (vector? f) (if (every? #(or (number? %) (string? %)) f)
                  (str "#(" (str/join " " (map cl f)) ")")
                  (str "(vector " (str/join " " (map cl f)) ")"))
    (seq? f)
    (let [[h & args] f]
      (cond
        (= 'let h)    (str "(let* (" (str/join " " (map (fn [[k v]] (str "(" (cl k) " " (cl v) ")"))
                                                        (partition 2 (first args))))
                           ") " (str/join " " (map cl (rest args))) ")")
        (= 'quote h)  (let [x (first args)]
                        (str "'" (if (and (symbol? x) (function-name x)) (function-name x) (cl x))))
        (contains? #{'fn 'fn*} h)
        (str "(lambda (" (str/join " " (map cl (first args))) ") " (str/join " " (map cl (rest args))) ")")
        (and (symbol? h) (= "ucl" (namespace h)) (contains? #{"let" "let*"} (name h)))
        (str "(" (name h) " (" (str/join " " (map (fn [b] (if (seq? b)
                                                            (str "(" (cl (first b)) " " (cl (second b)) ")")
                                                            (cl b)))
                                                    (first args)))
             ") " (str/join " " (map cl (rest args))) ")")
        (= 'declare h) (pr-str f)
        (and (symbol? h) (nil? (namespace h)))
        (if (contains? clojure->cl h)
          (str "(" (str/join " " (cons (clojure->cl h) (map cl args))) ")")
          (fail "conformance: no Common Lisp for" h "in" (pr-str f)))
        :else (str "(" (str/join " " (cons (if (and (symbol? h) (= "ucl" (namespace h))) (name h) (cl h))
                                           (map cl args))) ")")))
    :else (fail "conformance: no Common Lisp for" (pr-str f))))

(defn printed
  "How Common Lisp's prin1 prints the Clojure value `v`."
  [v]
  (cond (nil? v) "NIL" (false? v) "NIL" (true? v) "T"
        (keyword? v) (str/upper-case (str v))
        (vector? v) (str "#(" (str/join " " (map printed v)) ")")
        :else (str v)))

(defn cases
  "[expected form] for every conformance assertion in `file`."
  [file]
  (let [forms (read-string {:read-cond :allow :features #{:clj}}
                           (str "[" (slurp file) "]"))]
    (for [d forms
          :when (and (seq? d) (= 'deftest (first d)) (str/ends-with? (name (second d)) "-conformance-test"))
          a (tree-seq seq? seq d)
          :when (and (seq? a) (= 'is (first a)) (seq? (second a)) (= '= (first (second a))))]
      (let [[_ expected form] (second a)] [expected form]))))

(defn run-lisp [impl file]
  (let [{:keys [out err exit]}
        (case impl
          "sbcl" (sh "sbcl" "--script" file)
          "ecl"  (sh "ecl" "--norc" "--load" file "--eval" "(quit)"))]
    (when-not (zero? exit) (fail impl "failed:" err out))
    (->> (str/split-lines out)
         (keep #(second (re-matches #"@@ (.*)" %))))))

(let [files *command-line-args*
      cs (vec (mapcat cases files))
      src (str/join "\n" (for [[_ form] cs]
                           (str "(format t \"@@ ~a~%\" (handler-case (prin1-to-string (eval (read-from-string "
                                (pr-str (cl form)) ")))"
                                " (error (e) (substitute #\\Space #\\Newline (format nil \"ERROR ~a\" e)))))")))
      tmp (str (System/getProperty "java.io.tmpdir") "/ucl-conformance.lisp")]
  (when (empty? cs) (fail "conformance: no cases in" files))
  (spit tmp src)
  (let [failures (atom 0)]
    (doseq [impl ["sbcl" "ecl"]]
      (let [outs (run-lisp impl tmp)]
        (when-not (= (count outs) (count cs))
          (fail impl "printed" (count outs) "results for" (count cs) "cases"))
        (doseq [[[expected form] out] (map vector cs outs)]
          (when-not (= (printed expected) out)
            (swap! failures inc)
            (println (str impl ": " (pr-str form) "\n  expected " (printed expected) ", got " out))))))
    (println (count cs) "cases on SBCL and ECL," @failures "disagreements")
    (System/exit (if (zero? @failures) 0 1))))
