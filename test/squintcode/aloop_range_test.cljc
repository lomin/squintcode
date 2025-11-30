(ns squintcode.aloop-range-test
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  (:require #?@(:squint [["assert" :as assert]]
                :clj [[clojure.test :refer [deftest is testing run-tests]]
                      [squintcode.macros :as cl]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
                       [squintcode.macros :as cl]])))

(deftest range-single-arity-test
  (testing "(range n) - loops from 0 to n-1"
    (let [result (cl/aloop (range 5) [sum 0]
                           (if it
                             (recur (+ sum it))
                             sum))]
      (is (= 10 result) "sum of 0+1+2+3+4 should be 10"))))

(deftest range-two-arity-test
  (testing "(range start end) - loops from start to end-1"
    (let [result (cl/aloop (range 3 7) [sum 0]
                           (if it
                             (recur (+ sum it))
                             sum))]
      (is (= 18 result) "sum of 3+4+5+6 should be 18"))))

(deftest range-three-arity-test
  (testing "(range start end step) - loops with custom step"
    (let [result (cl/aloop (range 0 10 2) [sum 0]
                           (if it
                             (recur (+ sum it))
                             sum))]
      (is (= 20 result) "sum of 0+2+4+6+8 should be 20"))))

(deftest range-length-test
  (testing "(length self) returns correct count for ranges"
    (testing "single arity"
      (let [len (cl/aloop (range 5) [l (cl/length self)]
                          (if it (recur l) l))]
        (is (= 5 len) "length of (range 5) should be 5")))
    (testing "two arity"
      (let [len (cl/aloop (range 3 7) [l (cl/length self)]
                          (if it (recur l) l))]
        (is (= 4 len) "length of (range 3 7) should be 4")))
    (testing "three arity with step"
      (let [len (cl/aloop (range 0 10 2) [l (cl/length self)]
                          (if it (recur l) l))]
        (is (= 5 len) "length of (range 0 10 2) should be 5")))))

(deftest range-collect-elements-test
  (testing "collect all elements from range iteration"
    (let [result (cl/aloop (range 3 6) [acc (cl/make-array 0)]
                           (if it
                             (do (cl/push-end acc it)
                                 (recur acc))
                             acc))]
      (is (= 3 (cl/aref result 0)))
      (is (= 4 (cl/aref result 1)))
      (is (= 5 (cl/aref result 2))))))

(deftest range-step-collect-test
  (testing "collect elements with step"
    (let [result (cl/aloop (range 1 10 3) [acc (cl/make-array 0)]
                           (if it
                             (do (cl/push-end acc it)
                                 (recur acc))
                             acc))]
      ;; 1, 4, 7 (10 is excluded)
      (is (= 3 (cl/length result)))
      (is (= 1 (cl/aref result 0)))
      (is (= 4 (cl/aref result 1)))
      (is (= 7 (cl/aref result 2))))))

(comment (run-tests 'squintcode.aloop-range-test))
