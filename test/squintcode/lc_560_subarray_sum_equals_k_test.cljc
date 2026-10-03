(ns squintcode.lc-560-subarray-sum-equals-k-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-560-subarray-sum-equals-k :as recur-version]
            [squintcode.lc-560-subarray-sum-equals-k-loop :as loop-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))   ; LeetCode's int[]

(def versions [["loop/recur" recur-version/subarraySum] ["ucl/loop" loop-version/subarraySum]])

(deftest subarray-sum-basic-test
  (doseq [[label subarraySum] versions]
    (testing (str label ": subarraySum with [1,2,3] and k=3")
      (is (= 2 (subarraySum (arr [1 2 3]) 3)) "[1,2] and [3]"))))

(deftest subarray-sum-edge-cases
  (doseq [[label subarraySum] versions]
    (testing (str label ": empty array") (is (= 0 (subarraySum (arr []) 5))))
    (testing (str label ": single element matching k") (is (= 1 (subarraySum (arr [5]) 5))))
    (testing (str label ": single element not matching k") (is (= 0 (subarraySum (arr [3]) 5))))
    (testing (str label ": no matching subarrays") (is (= 0 (subarraySum (arr [1 2 3]) 10))))))

(deftest subarray-sum-complex-cases
  (doseq [[label subarraySum] versions]
    (testing (str label ": [1,1,1] and k=2") (is (= 2 (subarraySum (arr [1 1 1]) 2))))
    (testing (str label ": negative numbers") (is (= 3 (subarraySum (arr [1 -1 0]) 0)) "[1,-1], [0], [1,-1,0]"))))
