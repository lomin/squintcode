(ns squintcode.lc-344-reverse-string
  (:require [ucl.api :as ucl]))

;; LeetCode 344. LeetCode's char[] is a vector of one-character strings
;; (a Dart List<String>); swap from both ends toward the middle.

(ucl/defun reverseString (s)
  (declare (type (simple-array string (*)) s))
  (ucl/let ((i 0) (j (dec (ucl/length s))))
    (declare (type fixnum i j))
    (loop []
      (when (< i j)
        (let [a (ucl/elt s i)]
          (ucl/setf (ucl/elt s i) (ucl/elt s j))
          (ucl/setf (ucl/elt s j) a)
          (ucl/incf i)
          (ucl/decf j)
          (recur)))))
  nil)
