(ns squintcode.lc-303-range-sum-query-immutable
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  #?(:clj (:require [squintcode.macros :as cl])))

;; LeetCode 303: Range Sum Query - Immutable
;;
;; Approach: Prefix Sum Array
;; - Build prefix sum array where prefix[i] = sum of nums[0..i-1]
;; - prefix[0] = 0 (empty prefix)
;; - sumRange(left, right) = prefix[right+1] - prefix[left]
;;
;; Time: O(n) constructor, O(1) query
;; Space: O(n) for prefix sum array

(defn- build-prefix-sum [nums]
  (cl/aloop nums [i 1
                  sum 0
                  ps (cl/with (cl/make-array (inc (cl/length nums)) :initial-element 0))]
            (if it
              (recur (inc i) (cl/setf (cl/aref ps i) (+ sum it)))
              ps)))

(cl/defclass NumArray [prefix-sum]
  (:init (nums)
         {:prefix-sum (build-prefix-sum nums)})

  (:method sumRange (left right)
           (- (cl/aref prefix-sum (inc right))
              (cl/aref prefix-sum left))))

(comment
  (let [n (->NumArray (cl/make-array 5 :initial-contents [1 2 3 4 5]))]
    (.sumRange n 0 2)))
