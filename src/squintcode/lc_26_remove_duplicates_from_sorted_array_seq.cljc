(ns squintcode.lc-26-remove-duplicates-from-sorted-array-seq
  (:require [ucl.api :as ucl]))

;; LeetCode 26 with sequence functions (README §4.2): the distinct values,
;; copied back to the front of nums. :from-end keeps each first occurrence,
;; so the values stay sorted.

(ucl/defun removeDuplicates (nums)
  (declare (type fixnum-vector nums))
  (ucl/let ((distinct (ucl/remove-duplicates nums :from-end true)))
    (declare (type fixnum-vector distinct))
    (ucl/replace nums distinct)
    (ucl/length distinct)))
