(ns tasks.lc
  "Test and build the LeetCode solutions on every ucl host.

   Each host gets the project's src/ and test/ plus its own ucl source roots
   and nothing else -- backend selection is by source root (ucl/README §3)."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.string :as str]))

(def ^:private squint "node_modules/.bin/squint")
(def ^:private esbuild "node_modules/.bin/esbuild")

(def ^:private ucl-squint-sources
  ["ucl/shared/ucl/contract.cljc"
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
    (when-let [leak (re-find #"ucl/(contract|js_emit|leetcode|test)\.mjs" (slurp output))]
      (exit! (str "ERROR: " output " contains " (first leak) " -- macro-time or test code leaked")))
    (println (str "  " output " (" (fs/size output) " bytes)"))))

(defn- problems []
  (map #(str/replace (fs/file-name %) #"\.cljc$" "") (source-files)))

(defn build-one [problem]
  (when-not problem
    (exit! "Usage: bb build-one <problem>   e.g. bb build-one fizzbuzz"))
  (println (str "Building " problem " (safety 0)..."))
  (bundle-problem! (compile-for-submission!) problem))

(defn build-all []
  (println "Building every problem (safety 0)...")
  (let [dir (compile-for-submission!)]
    (doseq [p (problems)] (bundle-problem! dir p))))

(defn clean []
  (doseq [d ["out" "cljs-test-runner-out" ".cljs_node_repl" ".cljsbuild" ".shadow-cljs"]]
    (fs/delete-tree d))
  (println "✓ clean"))
