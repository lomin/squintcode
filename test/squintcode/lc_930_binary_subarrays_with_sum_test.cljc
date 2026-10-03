(ns squintcode.lc-930-binary-subarrays-with-sum-test
  (:require [ucl.test :refer [deftest is]]
            [ucl.api :as ucl]
            [squintcode.lc-930-binary-subarrays-with-sum :refer [numSubarraysWithSum]]))

(deftest subarray-sum-basic-test
  (is (= 4 (numSubarraysWithSum (ucl/make-array 5 :element-type 'fixnum :initial-contents [1 0 1 0 1]) 2)))
  (is (= 15 (numSubarraysWithSum (ucl/make-array 5 :element-type 'fixnum :initial-contents [0 0 0 0 0]) 0)))
  (is (= 0 (numSubarraysWithSum (ucl/make-array 3 :element-type 'fixnum :initial-contents [0 0 0]) 1))))
