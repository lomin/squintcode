(ns squintcode.lc-560-subarray-sum-equals-k
  (:require [ucl.api :as ucl]))

;; For every prefix sum s, count the earlier prefix sums equal to s - k.

(ucl/defun subarraySum (nums k)
  (declare (type simple-vector nums))
  (let [n    (ucl/length nums)
        freq (ucl/make-hash-table :initial-contents {0 1})]
    (loop [i 0 running-sum 0 result 0]
      (if (< i n)
        (let [running-sum (+ running-sum (ucl/elt nums i))
              result      (+ result (ucl/gethash (- running-sum k) freq 0))]
          (ucl/incf (ucl/gethash running-sum freq 0))
          (recur (inc i) running-sum result))
        result))))

(comment
  (subarraySum (ucl/make-array 3 :initial-contents [1 -1 1]) 2)
  (subarraySum (ucl/make-array 4 :initial-contents [1 -1 1 1]) 0))
