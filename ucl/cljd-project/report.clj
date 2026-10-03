;; Preloaded into ClojureDart's build (clojure -M -i report.clj -m cljd.build ...):
;; its compile-error report drops the cause, so print the whole chain first.
(require 'cljd.build)
(alter-var-root #'cljd.build/print-exception
  (fn [print-exception]
    (fn [e]
      (binding [*out* *err*]
        (doseq [x (take-while some? (iterate ex-cause e))]
          (println "  cause:" (.getSimpleName (class x)) (ex-message x))))
      (print-exception e))))
