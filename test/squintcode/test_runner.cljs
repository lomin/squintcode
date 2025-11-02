(ns squintcode.test-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [squintcode.fizzbuzz-test]))

(enable-console-print!)

(defn -main [& args]
  (run-tests 'squintcode.fizzbuzz-test))

(set! *main-cli-fn* -main)
