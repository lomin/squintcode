(ns squintcode.subarray-sum-equals-k-test
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]])
            [squintcode.subarray-sum-equals-k :refer [subarraySum]]
            #?(:squint ["assert" :as assert]))
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests]])))

(deftest subarray-sum-basic-test
  (testing "subarraySum with [1,2,3] and k=3"
    (is (= 2 (subarraySum [1 2 3] 3))
        "Should find 2 subarrays: [1,2] and [3]")))

(deftest subarray-sum-edge-cases
  (testing "subarraySum with empty array"
    (is (= 0 (subarraySum [] 5))
        "Empty array should return 0"))

  (testing "subarraySum with single element matching k"
    (is (= 1 (subarraySum [5] 5))
        "Single element matching k should return 1"))

  (testing "subarraySum with single element not matching k"
    (is (= 0 (subarraySum [3] 5))
        "Single element not matching k should return 0"))

  (testing "subarraySum with no matching subarrays"
    (is (= 0 (subarraySum [1 2 3] 10))
        "No matching subarrays should return 0")))

(deftest subarray-sum-complex-cases
  (testing "subarraySum with [1,1,1] and k=2"
    (is (= 2 (subarraySum [1 1 1] 2))
        "Should find 2 subarrays: [1,1] at positions 0-1 and 1-2"))

  (testing "subarraySum with negative numbers"
    (is (= 3 (subarraySum [1 -1 0] 0))
        "Should handle negative numbers correctly: [1,-1], [0], and [1,-1,0]")))

(comment (run-tests 'squintcode.subarray-sum-equals-k-test))
