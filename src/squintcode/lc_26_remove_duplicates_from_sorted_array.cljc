(ns squintcode.lc-26-remove-duplicates-from-sorted-array
  (:require [ucl.api :as ucl]))

;; LeetCode 26. In a sorted array a new value differs from the last one
;; kept; k counts the kept ones, which are compacted to the front.

(ucl/defun removeDuplicates (nums)
  (declare (type fixnum-vector nums))
  (ucl/let ((k 1) (i 1))
    (declare (type fixnum k i))
    (loop []
      (when (< i (ucl/length nums))
        (when (not (== (ucl/elt nums i) (ucl/elt nums (dec k))))
          (ucl/setf (ucl/elt nums k) (ucl/elt nums i))
          (ucl/incf k))
        (ucl/incf i)
        (recur)))
    k))
