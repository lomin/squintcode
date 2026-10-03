(ns squintcode.lc-560-subarray-sum-equals-k-loop
  (:require [ucl.api :as ucl]))

;; LeetCode 560, written with ucl/loop (README D62); compare
;; squintcode.lc-560-subarray-sum-equals-k. For every prefix sum s, count the
;; earlier prefix sums equal to s - k.

(ucl/defun subarraySum (nums k)
  (declare (type fixnum-vector nums) (type fixnum k))
  (let [freq (ucl/make-hash-table :initial-contents {0 1})]
    (ucl/loop for x across nums
              sum x into s of-type fixnum
              sum (ucl/gethash (- s k) freq 0) of-type fixnum
              do (ucl/incf (ucl/gethash s freq 0)))))
