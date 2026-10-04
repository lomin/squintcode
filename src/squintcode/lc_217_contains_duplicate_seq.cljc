(ns squintcode.lc-217-contains-duplicate-seq
  (:require [ucl.api :as ucl]))

;; LeetCode 217 with sequence functions (README §4.2): there is a duplicate
;; when removing duplicates shortens the array.

(ucl/defun containsDuplicate (nums)
  (declare (type fixnum-vector nums))
  (< (ucl/length (ucl/remove-duplicates nums)) (ucl/length nums)))
