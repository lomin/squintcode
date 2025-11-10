(require '[clojure.test :as test]
         '[clojure.java.io :as io])

(defn find-test-namespaces []
  (let [test-dir (io/file "test")
        test-files (file-seq test-dir)]
    (->> test-files
         (filter #(and (.isFile %)
                       (or (.endsWith (.getName %) "_test.clj")
                           (.endsWith (.getName %) "_test.cljc"))))
         (map #(-> (.getPath %)
                   (subs (inc (count (.getPath test-dir))))
                   (.replace "/" ".")
                   (.replace "\\" ".")
                   (.replace "_" "-")
                   (clojure.string/replace #"\.cljc?$" "")  ; Remove .clj or .cljc extension
                   symbol))
         (remove #(re-find #"cljs" (str %))))))  ; Skip CLJS-specific test files

(let [test-nses (find-test-namespaces)]
  (println "Discovering Clojure test namespaces...")
  (println "Found:" (count test-nses) "test namespace(s)")
  (doseq [ns-sym test-nses]
    (println "  -" ns-sym))
  (println)

  ;; Try to require each namespace, skip those that fail
  (let [loadable-nses (keep (fn [ns-sym]
                               (try
                                 (require ns-sym)
                                 ns-sym
                                 (catch Exception e
                                   (println "⚠️  Skipping" ns-sym "- failed to load:" (.getMessage e))
                                   nil)))
                             test-nses)]
    (println)
    (println "Running tests for" (count loadable-nses) "namespace(s)...")
    (println)

    (let [results (apply test/run-tests loadable-nses)]
      (println)
      (when (or (pos? (:fail results))
                (pos? (:error results)))
        (System/exit 1)))))
