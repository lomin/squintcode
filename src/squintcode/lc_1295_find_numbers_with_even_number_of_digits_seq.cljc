(ns squintcode.lc-1295-find-numbers-with-even-number-of-digits-seq
  (:require [ucl.api :as ucl]))

;; LeetCode 1295 with a sequence function (README §4.2): the loop of the
;; original is ucl/count-if, its predicate a literal fn written into it; the
;; digit count is a `loop while ... do`, as Common Lisp writes it.

(ucl/defun digit-count (x)
  (declare (type fixnum x))
  (ucl/let ((d 1))
    (declare (type fixnum d))
    (ucl/loop while (>= x 10)
              do (ucl/setf x (ucl/truncate x 10))
                 (ucl/incf d))
    d))

(ucl/defun findNumbers (nums)
  (declare (type fixnum-vector nums))
  (ucl/count-if (fn [x] (zero? (bit-and (digit-count x) 1))) nums))
