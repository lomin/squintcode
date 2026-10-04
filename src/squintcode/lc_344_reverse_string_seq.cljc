(ns squintcode.lc-344-reverse-string-seq
  (:require [ucl.api :as ucl]))

;; LeetCode 344 with a sequence function (README §4.2): nreverse reverses a
;; vector in place.

(ucl/defun reverseString (s)
  (declare (type (simple-array string (*)) s))
  (ucl/nreverse s)
  nil)
