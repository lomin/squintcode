(ns squintcode.lc-303-range-sum-query-immutable-test
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests]]))
  (:require #?@(:squint [["assert" :as assert]
                         [squintcode.lc-303-range-sum-query-immutable :refer [NumArray]]]
                :clj [[clojure.test :refer [deftest is testing run-tests]]
                      [squintcode.lc-303-range-sum-query-immutable :refer [->NumArray]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
                       [squintcode.lc-303-range-sum-query-immutable :refer [->NumArray]]])))

(deftest basic-example-test
  (testing "LeetCode example: [-2, 0, 3, -5, 2, -1]"
    (let [nums #?(:squint #js [-2 0 3 -5 2 -1]
                  :clj (java.util.ArrayList. [-2 0 3 -5 2 -1])
                  :cljs #js [-2 0 3 -5 2 -1])
          num-array #?(:squint (new NumArray nums)
                       :clj (->NumArray nums)
                       :cljs (->NumArray nums))]
      (is (= 1 (.sumRange num-array 0 2))
          "sumRange(0,2) = (-2) + 0 + 3 = 1")
      (is (= -1 (.sumRange num-array 2 5))
          "sumRange(2,5) = 3 + (-5) + 2 + (-1) = -1")
      (is (= -3 (.sumRange num-array 0 5))
          "sumRange(0,5) = sum of all elements = -3"))))

(deftest single-element-test
  (testing "single element array"
    (let [nums #?(:squint #js [5]
                  :clj (java.util.ArrayList. [5])
                  :cljs #js [5])
          num-array #?(:squint (new NumArray nums)
                       :clj (->NumArray nums)
                       :cljs (->NumArray nums))]
      (is (= 5 (.sumRange num-array 0 0))
          "single element sumRange(0,0)"))))

(deftest all-zeros-test
  (testing "array with zeros"
    (let [nums #?(:squint #js [0 0 0 0]
                  :clj (java.util.ArrayList. [0 0 0 0])
                  :cljs #js [0 0 0 0])
          num-array #?(:squint (new NumArray nums)
                       :clj (->NumArray nums)
                       :cljs (->NumArray nums))]
      (is (= 0 (.sumRange num-array 0 3))
          "sum of all zeros")
      (is (= 0 (.sumRange num-array 1 2))
          "middle range of zeros"))))

(deftest positive-numbers-test
  (testing "all positive numbers"
    (let [nums #?(:squint #js [1 2 3 4 5]
                  :clj (java.util.ArrayList. [1 2 3 4 5])
                  :cljs #js [1 2 3 4 5])
          num-array #?(:squint (new NumArray nums)
                       :clj (->NumArray nums)
                       :cljs (->NumArray nums))]
      (is (= 15 (.sumRange num-array 0 4))
          "sum of 1+2+3+4+5")
      (is (= 12 (.sumRange num-array 2 4))
          "sum of 3+4+5"))))

(comment (run-tests 'squintcode.lc-303-range-sum-query-immutable-test))
