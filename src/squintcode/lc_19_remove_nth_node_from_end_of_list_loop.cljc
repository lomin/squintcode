(ns squintcode.lc-19-remove-nth-node-from-end-of-list-loop
  (:require [ucl.api :as ucl]))

;; LeetCode 19, written with ucl/loop (README D62); compare
;; squintcode.lc-19-remove-nth-node-from-end-of-list. ListNode is LeetCode's.
;; `right` runs n nodes ahead; when it falls off the end, `left` is just
;; before the node to remove.

(ucl/defun removeNthFromEnd (head n)
  (declare (type fixnum n))
  (let [dummy (new ListNode 0 head)]
    (ucl/let ((left (ucl/loop for right = head then (ucl/slot-value right 'next)
                              for i below n
                              finally (return
                                        (ucl/loop for l = dummy then (ucl/slot-value l 'next)
                                                  for r = right then (ucl/slot-value r 'next)
                                                  while r
                                                  finally (return l))))))
      (declare (type ListNode left))
      (ucl/setf (ucl/slot-value left 'next)
                (ucl/slot-value (ucl/slot-value left 'next) 'next)))
    (ucl/slot-value dummy 'next)))
