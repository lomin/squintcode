(ns squintcode.utils
  (:require [ucl.api :as ucl]))

(ucl/defun assoc-arr! (arr k v)
  (declare (type simple-vector arr))
  (ucl/setf (ucl/elt arr k) v)
  arr)
