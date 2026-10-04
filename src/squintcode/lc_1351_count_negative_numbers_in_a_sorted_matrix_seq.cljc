(ns squintcode.lc-1351-count-negative-numbers-in-a-sorted-matrix-seq
  (:require [ucl.api :as ucl]))

;; LeetCode 1351 with sequence functions (README §4.2): the sum over the
;; rows of each row's count-if, O(m·n) -- the loop version's staircase is
;; O(m + n).

(ucl/defun countNegatives (grid)
  (declare (type simple-vector grid))
  (ucl/loop for r below (ucl/length grid)
            sum (ucl/let ((row (ucl/elt grid r)))
                  (declare (type fixnum-vector row))
                  (ucl/count-if (fn [x] (< x 0)) row))
            of-type fixnum))
