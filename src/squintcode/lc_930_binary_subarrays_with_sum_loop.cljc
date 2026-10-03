(ns squintcode.lc-930-binary-subarrays-with-sum-loop
  (:require [ucl.api :as ucl]))

;; LeetCode 930, written with ucl/loop (README §4.3, D62); compare
;; squintcode.lc-930-binary-subarrays-with-sum. Prefix sums of a binary array
;; are 0..n, so their frequencies fit a vector indexed by the sum.

(ucl/defun numSubarraysWithSum (nums goal)
  (declare (type fixnum-vector nums) (type fixnum goal))
  (let [freq (ucl/make-array (inc (ucl/length nums)) :element-type 'fixnum)]
    (ucl/setf (ucl/elt freq 0) 1)
    (ucl/loop for x across nums
              sum x into s of-type fixnum
              when (>= s goal) sum (ucl/elt freq (- s goal)) of-type fixnum
              do (ucl/incf (ucl/elt freq s)))))
