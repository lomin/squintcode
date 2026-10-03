(ns squintcode.lc-560-subarray-sum-equals-k-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-560-subarray-sum-equals-k :refer [subarraySum]]))

(defn arr [v] (ucl/make-array (count v) :initial-contents v))

(deftest subarray-sum-basic-test
  (testing "subarraySum with [1,2,3] and k=3"
    (is (= 2 (subarraySum (arr [1 2 3]) 3)) "[1,2] and [3]")))

(deftest subarray-sum-edge-cases
  (testing "empty array" (is (= 0 (subarraySum (arr []) 5))))
  (testing "single element matching k" (is (= 1 (subarraySum (arr [5]) 5))))
  (testing "single element not matching k" (is (= 0 (subarraySum (arr [3]) 5))))
  (testing "no matching subarrays" (is (= 0 (subarraySum (arr [1 2 3]) 10)))))

(deftest subarray-sum-complex-cases
  (testing "[1,1,1] and k=2" (is (= 2 (subarraySum (arr [1 1 1]) 2))))
  (testing "negative numbers" (is (= 3 (subarraySum (arr [1 -1 0]) 0)) "[1,-1], [0], [1,-1,0]")))
