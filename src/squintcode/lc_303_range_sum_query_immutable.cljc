(ns squintcode.lc-303-range-sum-query-immutable
  #?(:squint (:require-macros [squintcode.macros :as cl]))
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
  (let [n #?(:squint (.-length nums) :default (count nums))
        ps (cl/make-array (inc n) :initial-element 0)]
    (cl/aloop nums [i 0, sum 0]
              (if it
                (let [sum' (+ sum it)]
                  (cl/setf (cl/aref ps (inc i)) sum')
                  (recur (inc i) sum'))
                ps))))

(cl/defclass* NumArray [prefix-sum]
  (:init (nums)
         {:prefix-sum (build-prefix-sum nums)})

  (:method sumRange (left right)
           (- (cl/aref prefix-sum (inc right))
              (cl/aref prefix-sum left))))

(comment
  ;; Usage:
  ;; Squint: (new NumArray #js [-2 0 3 -5 2 -1])
  ;; CLJ/CLJS: (->NumArray [...])
  ;; (.sumRange num-array 0 2)  ; => 1
  (let [n (->NumArray (cl/make-array 5 :initial-contents [1 2 3 4 5]))]
    (.sumRange n 0 2))
  )
