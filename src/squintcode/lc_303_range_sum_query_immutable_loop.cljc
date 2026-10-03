(ns squintcode.lc-303-range-sum-query-immutable-loop
  (:require [ucl.api :as ucl]))

;; LeetCode 303, written with ucl/loop (README D62); compare
;; squintcode.lc-303-range-sum-query-immutable. prefix[i] = nums[0..i-1].

(ucl/defun build-prefix-sum (nums)
  (declare (type fixnum-vector nums))
  (let [ps (ucl/make-array (inc (ucl/length nums)) :element-type 'fixnum)]
    (ucl/loop for x across nums
              for i from 1
              sum x into s of-type fixnum
              do (ucl/setf (ucl/elt ps i) s))
    ps))

(ucl/defstruct (NumArray (:constructor NumArray
                           (nums &aux (prefix-sum (build-prefix-sum nums)))))
  (prefix-sum nil :type fixnum-vector))

(ucl/defmethod sumRange ((this NumArray) left right)
  (ucl/with-slots (prefix-sum) this
    (- (ucl/elt prefix-sum (inc right))
       (ucl/elt prefix-sum left))))
