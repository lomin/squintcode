(ns tasks.dart
  "Bundle ClojureDart output into one standalone LeetCode Dart file (ucl D38).

   ClojureDart writes one Dart library per namespace, each top-level
   definition between `// BEGIN name` and `// END name`, and every reference
   to another top-level definition through an import prefix -- even within its
   own library. So tree shaking is a walk over those blocks:

     1. the roots are every top-level function and class of the solution;
     2. follow `prefix.ident` into the library the prefix imports;
     3. fail on any reference into cljd/core.dart: LeetCode has no ClojureDart
        runtime, so a submission must be plain Dart;
     4. leave ucl.leetcode's ListNode and TreeNode bare -- LeetCode defines them;
     5. rename each kept definition <library>$<ident>, drop the dart:core
        prefix (an explicit `import \"dart:core\" as dc` would hide the implicit
        one from LeetCode's own code), keep other dart: imports;
     6. declare a forked local `T x;`, not ClojureDart's `late final T x;`:
        `late` costs a run-time check the JIT keeps in a hot loop (H54), and
        Dart's definite-assignment analysis proves the same at compile time --
        `bb build` analyzes every submission, so a declaration it cannot prove
        fails the build;
     7. add what LeetCode calls (D39): `class Solution`, one method per
        function, and for each struct whose constructor is named like it -- a
        design problem, such as NumArray -- a class of that name whose
        constructor runs the BOA constructor and whose methods delegate.
        Both are untyped: LeetCode's harness accepts dynamic signatures."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn- quote-re [s] (java.util.regex.Pattern/quote s))

(defn- block-ident
  "The Dart identifier a top-level block defines."
  [body]
  (let [line (first (remove #(or (str/blank? %) (str/starts-with? % "@")) (str/split-lines body)))]
    (or (second (re-find #"^(?:abstract )?class ([\w$]+)" line))
        (second (re-find #"^(?:(?:final|const|late) )*(?:[\w$.<>?, ]+ )?([\w$]+)\s*[(=;]" line))
        (throw (ex-info (str "cannot tell what this block defines: " line) {})))))

(defn- parse-library [file]
  (let [src (slurp (str file))]
    {:imports (into {} (for [[_ path prefix] (re-seq #"(?m)^import \"([^\"]+)\" as ([\w$]+);" src)]
                         [prefix path]))
     :blocks  (into {} (for [[_ body] (re-seq #"(?s)// BEGIN [^\n]+\n(.*?)\n// END [^\n]+" src)]
                         [(block-ident body) body]))}))

(defn- library-name [rel]
  (-> rel (str/replace #"\.dart$" "") (str/replace #"[^A-Za-z0-9]" "_")))

(defn- resolve-rel [from-rel import-path]
  (-> (fs/path "/" from-rel) fs/parent (fs/path import-path) fs/normalize str (subs 1)))

(defn- drop-late
  "`late final T x;` -> `T x;`. ClojureDart declares a local bound to an `if`,
   `let` or `loop` in expression position this way and assigns it in each
   branch; without `late` the compiler, not the run time, checks that."
  [body]
  (str/replace body #"(?m)^late final ([^;=\n]+);$" "$1;"))

(defn- class? [body] (re-find #"^(?:abstract )?class " (str/triml body)))

(defn- rename-definition [body ident new-name]
  (if (class? body)
    ;; a class names itself in its constructors too
    (str/replace body (re-pattern (str "(?<![\\w$.])" (quote-re ident) "(?![\\w$])"))
                 (str/re-quote-replacement new-name))
    (str/replace-first body (re-pattern (str "(?<![\\w$.])" (quote-re ident) "(?=\\s*[(=;])"))
                       (str/re-quote-replacement new-name))))

(defn- shake
  "Every definition reachable from `roots` of library `entry-rel`, renamed."
  [out-dir entry-rel roots]
  (let [libs  (memoize (fn [rel] (parse-library (fs/path out-dir rel))))
        kept  (atom {})
        dart  (atom #{})
        visit (fn visit [rel ident]
                (when-not (contains? @kept [rel ident])
                  (swap! kept assoc [rel ident] nil)
                  (let [{:keys [imports blocks]} (libs rel)
                        body (atom (or (get blocks ident)
                                       (throw (ex-info (str "no definition of " ident " in " rel) {}))))]
                    (doseq [[prefix path] imports
                            :let [refs (set (map second (re-seq (re-pattern (str "(?<![\\w$.])" (quote-re prefix) "\\.([\\w$]+)")) @body)))]
                            :when (seq refs)]
                      (cond
                        (= path "dart:core")
                        (swap! body str/replace (re-pattern (str "(?<![\\w$.])" (quote-re prefix) "\\.")) "")

                        (str/starts-with? path "dart:")
                        (swap! dart conj [path prefix])

                        (str/ends-with? path "cljd/core.dart")
                        (throw (ex-info (str ident " (" rel ") calls the ClojureDart runtime: "
                                             (str/join ", " (sort refs))
                                             ". A Dart submission must be plain Dart: use ucl"
                                             " vocabulary or host operations that compile inline.")
                                        {:refs refs}))

                        (str/ends-with? path "ucl/leetcode.dart")   ; LeetCode's own classes
                        (swap! body str/replace (re-pattern (str "(?<![\\w$.])" (quote-re prefix) "\\.")) "")

                        :else
                        (let [target (resolve-rel rel path)]
                          (doseq [r refs]
                            (visit target r)
                            (swap! body str/replace
                                   (re-pattern (str "(?<![\\w$.])" (quote-re (str prefix "." r)) "(?![\\w$])"))
                                   (str/re-quote-replacement (str (library-name target) "$" r)))))))
                    (swap! kept assoc [rel ident]
                           (drop-late
                            (rename-definition @body ident (str (library-name rel) "$" ident)))))))]
    (doseq [r roots] (visit entry-rel r))
    {:imports (sort (for [[path prefix] @dart] (str "import \"" path "\" as " prefix ";")))
     :bodies  (vals @kept)}))

;; ---------------------------------------------------------------------------
;; What LeetCode calls
;; ---------------------------------------------------------------------------

(defn- fn-params
  "Parameter names of a top-level function block, or nil when the block is
   not a plain Dart function (a multi-arity fn compiles to an object)."
  [body]
  (when-let [[_ params] (re-find #"^[\w$.<>?, ]+ [\w$]+\((.*?)\)\s*\{" (str/triml body))]
    (vec (for [p (str/split params #",") :let [p (str/trim p)] :when (seq p)]
           (last (str/split p #"\s+"))))))

(defn- class-methods
  "[name params] of each method a class block declares."
  [body]
  (for [[_ nm params] (re-seq #"(?m)^[\w$.<>?, ]+ ([\w$]+)\((.*?)\)\s*\{" body)
        :when (not (re-find #"^class " nm))]
    [nm (vec (for [p (str/split params #",") :let [p (str/trim p)] :when (seq p)]
               (last (str/split p #"\s+"))))]))

(defn- clean-param [p] (str/replace p "$" "_"))

(defn- method [nm params target]
  (let [ps (map clean-param params)]
    (str "  dynamic " nm "(" (str/join ", " (map #(str "dynamic " %) ps)) ") => "
         target "(" (str/join ", " ps) ");\n")))

(defn bundle
  "The submission for one solution namespace, compiled to `entry-rel` under
   `out-dir` (ClojureDart's lib/cljd-out)."
  [out-dir entry-rel]
  (let [{:keys [blocks]} (parse-library (fs/path out-dir entry-rel))
        lib      (library-name entry-rel)
        classes  (into {} (filter (comp class? val)) blocks)
        ;; a struct whose constructor is named like it: StructName_struct
        designs  (into {} (for [[c _] classes
                                :let [[_ nm] (re-matches #"(.+)_struct" c)]
                                :when (and nm (contains? blocks nm))]
                            [nm c]))
        methods  (set (for [[_ body] classes [m _] (class-methods body)] m))
        fns      (for [[ident body] blocks
                       :when (and (not (class? body))
                                  (not (contains? designs ident))
                                  (not (contains? methods ident)))
                       :let [params (fn-params body)]
                       :when params]
                   [ident params])
        {:keys [imports bodies]} (shake out-dir entry-rel (keys blocks))]
    (str (when (seq imports) (str (str/join "\n" imports) "\n\n"))
         (str/join "\n\n" bodies) "\n\n"
         (when (seq fns)
           (str "class Solution {\n"
                (str/join (for [[ident params] (sort fns)]
                            (method ident params (str lib "$" ident))))
                "}\n"))
         (str/join
          (for [[nm cls] (sort designs)
                :let [params (or (fn-params (get blocks nm))
                                 (throw (ex-info (str nm ": a design problem's constructor must take a"
                                                      " fixed number of arguments in a Dart submission"
                                                      " (no &optional)") {})))
                      ps (map clean-param params)]]
            (str "\nclass " nm " {\n"
                 "  final " lib "$" cls " _self;\n"
                 "  " nm "(" (str/join ", " (map #(str "dynamic " %) ps)) ") : _self = "
                 lib "$" nm "(" (str/join ", " ps) ");\n"
                 (str/join (for [[m mps] (class-methods (get blocks cls))]
                             (method m mps (str "_self." m))))
                 "}\n"))))))
