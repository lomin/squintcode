(ns tasks.lc
  "Test and build the LeetCode solutions on every ucl host.

   Each host gets the project's src/ and test/ plus its own ucl source roots
   and nothing else -- backend selection is by source root (ucl/README §3)."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.string :as str]
            [tasks.dart :as dart]))

(def ^:private squint "node_modules/.bin/squint")
(def ^:private esbuild "node_modules/.bin/esbuild")

(def ^:private ucl-squint-sources
  ["ucl/shared/ucl/contract.cljc"
   "ucl/shared/ucl/loop.cljc"
   "ucl/shared/ucl/seq.cljc"
   "ucl/backends/js/ucl/js_emit.cljc"
   "ucl/backends/squint/ucl/api.cljc"])

(def ^:private ucl-squint-testkit
  ["ucl/testkit/squint/ucl/test.cljc"
   "ucl/testkit/squint/ucl/leetcode.cljc"])

(defn- exit! [msg]
  (binding [*out* *err*] (println msg))
  (System/exit 1))

(defn- banner [title]
  (let [line (apply str (repeat 60 "="))]
    (println (str "\n" line "\n" title "\n" line))))

(defn- sh!
  "Run a command; exit on failure. An optional leading map is shell options."
  [& args]
  (let [[opts args] (if (map? (first args)) [(first args) (rest args)] [{} args])
        {:keys [exit]} (apply shell (assoc opts :continue true) args)]
    (when-not (zero? exit) (exit! (str "FAILED: " (str/join " " args))))))

(defn- cljc-files [dir pattern]
  (->> (fs/glob dir pattern) (map str) sort))

(defn- test-files [] (cljc-files "test" "**_test.cljc"))
(defn- source-files [] (cljc-files "src" "**.cljc"))

(defn- file->ns [root f]
  (-> (str (fs/relativize root f))
      (str/replace #"\.cljc$" "")
      (str/replace "/" ".")
      (str/replace "_" "-")))

(defn- test-nses [] (map #(file->ns "test" %) (test-files)))

(defn- module-path
  "Where Squint writes the module for `ns-name`, relative to its output dir."
  [ns-name]
  (str (-> ns-name (str/replace "." "/") (str/replace "-" "_")) ".mjs"))

(defn- strip-exports [s]
  (str/replace s (re-pattern "(?s)\\nexport \\{[^}]*\\};?\\s*$") ""))

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(defn test-clj []
  (banner "CLOJURE (JVM)")
  (let [nses (test-nses)]
    (sh! "clojure" "-M:jvm" "-e"
          (str "(require 'clojure.test " (str/join " " (map #(str "'" %) nses)) ")"
               "(let [r (clojure.test/run-tests " (str/join " " (map #(str "'" %) nses)) ")]"
               "  (shutdown-agents)"
               "  (System/exit (if (clojure.test/successful? r) 0 1)))"))))

(defn test-cljs []
  (banner "CLOJURESCRIPT")
  (let [nses   (test-nses)
        dir    "out/cljs-test"
        runner (str dir "/src/lc/cljs_runner.cljs")
        log    (str dir "/build.log")]
    (fs/delete-tree dir)
    (fs/create-dirs (fs/parent runner))
    (spit runner
          (str "(ns lc.cljs-runner (:require [cljs.test] "
               (str/join " " (map #(str "[" % "]") nses)) "))\n"
               "(defmethod cljs.test/report [:cljs.test/default :end-run-tests] [m]\n"
               "  (when-not (cljs.test/successful? m) (set! (.-exitCode js/process) 1)))\n"
               "(defn -main [] (cljs.test/run-tests "
               (str/join " " (map #(str "'" %) nses)) "))\n"))
    (let [{:keys [exit out err]}
          (shell {:continue true :out :string :err :string}
                 "clojure" "-Sdeps" (pr-str {:aliases {:runner {:extra-paths [(str dir "/src")]}}})
                 "-M:cljs:runner" "-m" "cljs.main" "-t" "node" "-d" (str dir "/js") "-c" "lc.cljs-runner")]
      (spit log (str out err))
      (print out err)
      (when-not (zero? exit) (exit! "ClojureScript build failed"))
      ;; A macro that fails to resolve is only a WARNING in ClojureScript (ucl H3).
      (when (str/includes? (str out err) "WARNING")
        (exit! "ClojureScript build has warnings -- they are errors here")))
    (sh! "node" "-e"
          (str "global.global = global;"
               "require('./" dir "/js/goog/bootstrap/nodejs.js');"
               "require('./" dir "/js/cljs_deps.js');"
               "require('./" dir "/js/lc/cljs_runner.js');"
               "lc.cljs_runner._main();"))))

(defn test-squint
  "Compile everything in one Squint invocation (ucl H18), bundle the test
   runner with esbuild exactly as a submission is bundled, run it with node."
  []
  (banner "SQUINT (LeetCode's pipeline)")
  (fs/delete-tree "out/squintcode")
  (fs/delete-tree "out/ucl")
  (apply sh! squint "compile" (concat ucl-squint-sources ucl-squint-testkit
                                       (source-files) (test-files)))
  (let [runner "out/test-runner.mjs"
        bundle "out/test-runner.bundle.mjs"]
    (spit runner
          (str "import * as t from './ucl/test.mjs';\n"
               (str/join (for [n (test-nses)] (str "import './" (module-path n) "';\n")))
               "t.run_all_BANG_();\n"))
    (sh! esbuild runner (str "--outfile=" bundle)
          "--format=esm" "--bundle" "--tree-shaking=true" "--platform=node" "--log-level=warning")
    (spit bundle (strip-exports (slurp bundle)))
    (sh! "node" bundle)))

;; ClojureDart builds a project: ucl/cljd-project is its template, every
;; source root is copied into src/ (tools.deps no longer takes paths outside a
;; project), and the Dart packages stay between runs.

(def ^:private dart-sdk (or (System/getenv "DART_SDK") (str (fs/home) "/.local/dart-sdk")))

(defn- cljd-project! [dir roots]
  (fs/create-dirs dir)
  (doseq [f ["deps.edn" "pubspec.yaml"]]
    (fs/copy (str "ucl/cljd-project/" f) (str dir "/" f) {:replace-existing true}))
  (doseq [d ["src" "lib/cljd-out" "test/cljd-out"]] (fs/delete-tree (str dir "/" d)))
  (doseq [root roots]
    (fs/copy-tree root (str dir "/src") {:replace-existing true}))
  dir)

(defn- cljd! [dir jvm-opts & args]
  (apply sh! {:dir dir :extra-env {"PATH" (str dart-sdk "/bin:" (System/getenv "PATH"))}}
         "clojure" (concat jvm-opts
                           ["-M" "-i" (str (fs/absolutize "ucl/cljd-project/report.clj"))
                            "-m" "cljd.build"]
                           args)))

(defn test-cljd
  "The solutions' tests on ClojureDart: cljd.test, run by `dart test`."
  []
  (banner "CLOJUREDART")
  (let [dir (cljd-project! "out/cljd-test" ["ucl/shared" "ucl/backends/cljd" "ucl/testkit/cljd"
                                            "src" "test"])]
    (apply cljd! dir [] "test" (test-nses))))

(defn test-ucl []
  (banner "UCL LIBRARY SUITE")
  (sh! "ucl/run-tests.sh"))

;; ---------------------------------------------------------------------------
;; LeetCode builds: safety 0 (ucl D14), no test kit
;; ---------------------------------------------------------------------------

(defn- compile-for-submission!
  "Compile ucl and every solution into out/build at safety 0."
  []
  (let [dir "out/build"]
    (fs/delete-tree dir)
    (fs/create-dirs dir)
    (spit (str dir "/squint.edn")
          (pr-str {:paths ["../../src" "../../ucl/shared" "../../ucl/backends/js" "../../ucl/backends/squint"]
                   :output-dir "js"
                   :extension ".mjs"
                   :ucl/safety 0}))
    (apply sh! {:dir dir} (str "../../" squint) "compile"
           (map #(str "../../" %) (concat ucl-squint-sources (source-files))))
    dir))

(defn- bundle-problem! [dir problem]
  (let [entry  (str dir "/js/squintcode/" problem ".mjs")
        bundle (str "out/" problem ".bundle.js")
        output (str "out/" problem ".js")]
    (when-not (fs/exists? entry)
      (exit! (str "ERROR: " entry " not found -- is src/squintcode/" problem ".cljc there?")))
    (sh! esbuild entry (str "--outfile=" bundle)
          "--format=esm" "--bundle" "--tree-shaking=true" "--log-level=warning")
    (spit output (strip-exports (slurp bundle)))
    (fs/delete bundle)
    ;; ucl's macro-time code and the test kit must never reach a submission:
    ;; a top-level def with a non-literal value would survive tree shaking.
    (when-let [leak (re-find #"ucl/(contract|loop|seq|js_emit|leetcode|test)\.mjs" (slurp output))]
      (exit! (str "ERROR: " output " contains " (first leak) " -- macro-time or test code leaked")))
    ;; An IIFE is Squint's let/loop in expression position: legal, but 8x in a
    ;; hot loop (ucl H22). Bind such a value in ucl/let instead (ucl D35).
    (let [iifes (count (re-seq #"\(\(\) =>" (slurp output)))]
      (when (pos? iifes)
        (println (str "  WARNING: " output " contains " iifes " IIFE(s) -- a let or loop in"
                      " expression position; bind its value in ucl/let"))))
    (println (str "  " output " (" (fs/size output) " bytes)"))))

(defn- problem-ns [problem] (str "squintcode." (str/replace problem "_" "-")))

(defn- compile-dart-for-submission!
  "Compile every solution with ClojureDart into out/cljd-build at safety 0.
   ucl.leetcode comes first: it makes a bare ListNode resolve (ucl H45); the
   bundler leaves it bare for LeetCode's own."
  [problems]
  (let [dir (cljd-project! "out/cljd-build" ["ucl/shared" "ucl/backends/cljd" "src"])]
    (fs/copy "ucl/testkit/cljd/ucl/leetcode.cljc" (str dir "/src/ucl/leetcode.cljc"))
    (apply cljd! dir ["-J-Ducl.safety=0"] "compile" "ucl.leetcode" (map problem-ns problems))
    dir))

(defn- bundle-dart! [dir problem]
  (let [rel    (str (-> (problem-ns problem) (str/replace "." "/")) ".dart")
        output (str "out/" problem ".dart")]
    (try
      (spit output (dart/bundle (str dir "/lib/cljd-out") rel))
      (catch clojure.lang.ExceptionInfo e
        (exit! (str "ERROR: " output ": " (ex-message e)))))
    (println (str "  " output " (" (fs/size output) " bytes)"))))

(def ^:private leetcode-classes
  "LeetCode's own Dart definitions, which it puts beside a submission."
  {"ListNode" "class ListNode { int val; ListNode? next; ListNode([this.val = 0, this.next]); }"
   "TreeNode" "class TreeNode { int val; TreeNode? left; TreeNode? right; TreeNode([this.val = 0, this.left, this.right]); }"})

(defn- analyze-dart!
  "Analyze every Dart submission as LeetCode compiles it -- with its ListNode
   and TreeNode -- and fail on any error: the bundler drops ClojureDart's
   `late`, and only Dart's compiler can prove that safe (ucl H54)."
  [problems]
  (let [dir "out/dart-check"]
    (fs/delete-tree dir)
    (fs/create-dirs dir)
    (doseq [p problems
            :let [src (slurp (str "out/" p ".dart"))]]
      (spit (str dir "/" p ".dart")
            (str src "\n" (str/join "\n" (for [[c d] leetcode-classes
                                                 :when (re-find (re-pattern (str "\\b" c "\\b")) src)]
                                             d)) "\n")))
    ;; warnings (an unnecessary cast) are ClojureDart's style, not defects
    (let [{:keys [exit out]} (shell {:continue true :out :string :err :string}
                                    (str dart-sdk "/bin/dart") "analyze" "--no-fatal-warnings" dir)]
      (when-not (zero? exit)
        (println (str/join "\n" (filter #(str/includes? % "error -") (str/split-lines out))))
        (exit! "ERROR: a Dart submission does not compile"))
      (println "  every Dart submission analyzes without errors"))))

(defn- problems []
  (map #(str/replace (fs/file-name %) #"\.cljc$" "") (source-files)))

(defn build-one [problem]
  (when-not problem
    (exit! "Usage: bb build-one <problem>   e.g. bb build-one fizzbuzz"))
  (println (str "Building " problem " (safety 0)..."))
  (bundle-problem! (compile-for-submission!) problem)
  (bundle-dart! (compile-dart-for-submission! [problem]) problem)
  (analyze-dart! [problem]))

(defn build-all []
  (println "Building every problem (safety 0)...")
  (let [dir (compile-for-submission!)]
    (doseq [p (problems)] (bundle-problem! dir p)))
  (let [dir (compile-dart-for-submission! (problems))]
    (doseq [p (problems)] (bundle-dart! dir p))
    (analyze-dart! (problems))))

(defn clean []
  (doseq [d ["out" "cljs-test-runner-out" ".cljs_node_repl" ".cljsbuild" ".shadow-cljs"]]
    (fs/delete-tree d))
  (println "✓ clean"))
