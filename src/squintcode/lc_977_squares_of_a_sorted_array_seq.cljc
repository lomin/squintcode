(ns squintcode.lc-977-squares-of-a-sorted-array-seq
  (:require [ucl.api :as ucl]))

;; LeetCode 977 with sequence functions (README §4.2): square every element,
;; then sort, O(n log n).

(ucl/defun sortedSquares (nums)
  (declare (type fixnum-vector nums))
  (ucl/sort (ucl/map '(vector fixnum) (fn [x] (* x x)) nums) (fn [a b] (< a b))))
