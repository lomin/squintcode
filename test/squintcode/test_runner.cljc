(ns squintcode.test-runner
  (:require #?(:clj [clojure.test :refer [run-tests]]
               :cljs [cljs.test :refer-macros [run-tests]])
            [squintcode.fizzbuzz-test]
            [squintcode.push-end-test]))

#?(:cljs (enable-console-print!))

(defn -main [& _args]
  (run-tests 'squintcode.fizzbuzz-test
             'squintcode.push-end-test))

#?(:cljs (set! *main-cli-fn* -main))
