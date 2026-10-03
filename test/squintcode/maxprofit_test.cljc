(ns squintcode.maxprofit-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.maxprofit :as recur-version]
            [squintcode.maxprofit-loop :as loop-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))   ; LeetCode's int[]

(deftest max-profit-test
  (doseq [[label maxProfit] [["loop/recur" recur-version/maxProfit] ["ucl/loop" loop-version/maxProfit]]]
    (testing (str label ": LeetCode examples")
      (is (= 5 (maxProfit (arr [7 1 5 3 6 4]))))
      (is (= 0 (maxProfit (arr [7 6 4 3 1])))))
    (testing (str label ": edge cases")
      (is (= 0 (maxProfit (arr [5]))))
      (is (= 9 (maxProfit (arr [3 1 10 2])))))))

(deftest no-prices-test
  ;; LeetCode guarantees a price (1 <= prices.length). With none, the
  ;; loop/recur version returns its initial 0; ucl/loop's maximize of-type
  ;; fixnum, as SBCL and ECL, returns most-negative-fixnum (H65).
  (is (= 0 (recur-version/maxProfit (arr []))))
  (is (== ucl/most-negative-fixnum (loop-version/maxProfit (arr [])))))
