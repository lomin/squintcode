(ns squintcode.lc-930-binary-subarrays-with-sum
  (:require [ucl.api :as ucl]))

;; Prefix sums of a binary array are 0..n, so their frequencies fit a vector
;; indexed by the sum.

(ucl/defun numSubarraysWithSum (nums goal)
  (declare (type fixnum-vector nums))
  (let [n    (ucl/length nums)
        freq (ucl/make-array (inc n) :element-type 'fixnum)]
    (ucl/setf (ucl/elt freq 0) 1)
    (loop [i 0 running-sum 0 result 0]
      (if (< i n)
        (let [running-sum (+ running-sum (ucl/elt nums i))
              want        (- running-sum goal)
              result      (if (>= want 0) (+ result (ucl/elt freq want)) result)]
          (ucl/incf (ucl/elt freq running-sum))
          (recur (inc i) running-sum result))
        result))))

(comment
  ;; expecting 4, then 15
  (numSubarraysWithSum (ucl/make-array 5 :initial-contents [1 0 1 0 1]) 2)
  (numSubarraysWithSum (ucl/make-array 5 :initial-contents [0 0 0 0 0]) 0))
