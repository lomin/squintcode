(ns squintcode.lc-1351-count-negative-numbers-in-a-sorted-matrix
  (:require [ucl.api :as ucl]))

;; LeetCode 1351. Rows and columns are non-increasing, so the first negative
;; column only moves left going down: walk it as a staircase, O(m + n).

(ucl/defun countNegatives (grid)
  (declare (type simple-vector grid))
  (ucl/let* ((first-row (ucl/elt grid 0)) (c (ucl/length first-row)) (total 0))
    (declare (type fixnum-vector first-row) (type fixnum c total))
    (ucl/dotimes (r (ucl/length grid) total)
      (ucl/let ((row (ucl/elt grid r)))
        (declare (type fixnum-vector row))
        (loop []
          (when (and (> c 0) (< (ucl/elt row (dec c)) 0))
            (ucl/decf c)
            (recur)))
        (ucl/incf total (- (ucl/length row) c))))))
