(ns squintcode.lc-1295-find-numbers-with-even-number-of-digits-seq
  (:require [ucl.api :as ucl]))

;; LeetCode 1295 with a sequence function (README §4.2): the loop of the
;; original is ucl/count-if, its predicate a literal fn written into it.

(ucl/defun digit-count (x)
  (declare (type fixnum x))
  (ucl/let ((d 1))
    (declare (type fixnum d))
    (loop []
      (when (>= x 10)
        (ucl/setf x (quot x 10))
        (ucl/incf d)
        (recur)))
    d))

(ucl/defun findNumbers (nums)
  (declare (type fixnum-vector nums))
  (ucl/count-if (fn [x] (zero? (bit-and (digit-count x) 1))) nums))
