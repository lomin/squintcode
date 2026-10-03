(ns squintcode.lc-303-range-sum-query-immutable-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-303-range-sum-query-immutable :refer [NumArray sumRange]]))

(defn arr [v] (ucl/make-array (count v) :initial-contents v))

;; sumRange is the generic function; on the JS hosts it calls the prototype
;; method, the path LeetCode's `numArray.sumRange(0, 2)` takes.

(deftest basic-example-test
  (testing "LeetCode example: [-2, 0, 3, -5, 2, -1]"
    (let [num-array (NumArray (arr [-2 0 3 -5 2 -1]))]
      (is (= 1 (sumRange num-array 0 2)) "sumRange(0,2) = (-2) + 0 + 3 = 1")
      (is (= -1 (sumRange num-array 2 5)) "sumRange(2,5) = 3 + (-5) + 2 + (-1) = -1")
      (is (= -3 (sumRange num-array 0 5)) "sumRange(0,5) = sum of all elements = -3"))))

(deftest edge-cases-test
  (testing "single element"
    (is (= 5 (sumRange (NumArray (arr [5])) 0 0))))
  (testing "zeros"
    (let [num-array (NumArray (arr [0 0 0 0]))]
      (is (= 0 (sumRange num-array 0 3)))
      (is (= 0 (sumRange num-array 1 2)))))
  (testing "positive numbers"
    (let [num-array (NumArray (arr [1 2 3 4 5]))]
      (is (= 15 (sumRange num-array 0 4)))
      (is (= 12 (sumRange num-array 2 4))))))
