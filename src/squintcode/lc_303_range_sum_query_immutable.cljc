(ns squintcode.lc-303-range-sum-query-immutable
  (:require [ucl.api :as ucl]))

;; LeetCode 303: Range Sum Query - Immutable
;;
;; Approach: Prefix Sum Array
;; - prefix[i] = sum of nums[0..i-1], prefix[0] = 0
;; - sumRange(left, right) = prefix[right+1] - prefix[left]
;; - |nums[i]| <= 10^5 and n <= 10^4, so every sum fits a fixnum.
;;
;; Time: O(n) constructor, O(1) query
;; Space: O(n) for prefix sum array

(ucl/defun build-prefix-sum (nums)
  (declare (type simple-vector nums))
  (let [n  (ucl/length nums)
        ps (ucl/make-array (inc n) :element-type 'fixnum)]
    (loop [i 0 sum 0]
      (if (< i n)
        (let [sum (+ sum (ucl/elt nums i))]
          (ucl/setf (ucl/elt ps (inc i)) sum)
          (recur (inc i) sum))
        ps))))

(ucl/defstruct (NumArray (:constructor NumArray
                           (nums &aux (prefix-sum (build-prefix-sum nums)))))
  (prefix-sum nil :type fixnum-vector))

(ucl/defmethod sumRange ((this NumArray) left right)
  (ucl/with-slots (prefix-sum) this
    (- (ucl/elt prefix-sum (inc right))
       (ucl/elt prefix-sum left))))

(comment
  (sumRange (NumArray (ucl/make-array 5 :initial-contents [1 2 3 4 5])) 0 2))
