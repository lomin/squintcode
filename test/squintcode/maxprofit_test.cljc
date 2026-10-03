(ns squintcode.maxprofit-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.maxprofit :refer [maxProfit]]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))   ; LeetCode's int[]

(deftest max-profit-test
  (testing "LeetCode examples"
    (is (= 5 (maxProfit (arr [7 1 5 3 6 4]))))
    (is (= 0 (maxProfit (arr [7 6 4 3 1])))))
  (testing "edge cases"
    (is (= 0 (maxProfit (arr [5]))))
    (is (= 0 (maxProfit (arr []))))
    (is (= 9 (maxProfit (arr [3 1 10 2]))))))
