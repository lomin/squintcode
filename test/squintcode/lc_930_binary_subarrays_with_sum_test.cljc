(ns squintcode.lc-930-binary-subarrays-with-sum-test
  (:require #?@(:squint [["assert" :as assert]]
                :clj [[clojure.test :refer [deftest is]]
                      [squintcode.macros :as cl]]
                :cljs [[clojure.test :refer [deftest is]]
                       [squintcode.leetcode-setup]])
            [squintcode.lc-930-binary-subarrays-with-sum :refer [numSubarraysWithSum]])
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is]])
     :cljs (:require-macros [squintcode.macros :as cl])))

(deftest array-init-test
  (is (= 2 (cl/length (cl/make-array 2 :initial-element 1)))))

(deftest subarray-sum-basic-test
  (is (= 4 (numSubarraysWithSum (cl/make-array 5 :initial-contents [1,0,1,0,1]) 2)))
  (is (= 15 (numSubarraysWithSum (cl/make-array 5 :initial-contents [0,0,0,0,0]) 0))))