(ns squintcode.lc-1295-find-numbers-with-even-number-of-digits
  (:require [ucl.api :as ucl]))

;; LeetCode 1295: Find Numbers with Even Number of Digits.
;; Count the elements whose decimal representation has an even length.
;; The loop/recur original; lc_1295_..._seq.cljc is the same with ucl/count-if.
;;
;; Time: O(n log max). Space: O(1).

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
  (let [n (ucl/length nums)]
    (loop [i 0 c 0]
      (if (< i n)
        (recur (inc i) (if (zero? (bit-and (digit-count (ucl/elt nums i)) 1)) (inc c) c))
        c))))
