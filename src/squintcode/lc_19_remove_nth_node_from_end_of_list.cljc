(ns squintcode.lc-19-remove-nth-node-from-end-of-list
  (:require [ucl.api :as ucl]))

;; ListNode is LeetCode's class, a global there; tests get ucl.leetcode's
;; strict fixture.

(defn move-right-n-forward [head n]
  (if (and head (pos? n))
    (recur (ucl/slot-value head 'next)
           (dec n))
    head))

(defn move-left-n-from-end [left right]
  (if right
    (recur (ucl/slot-value left 'next)
           (ucl/slot-value right 'next))
    left))

(defn bypass [node]
  (ucl/setf (ucl/slot-value node 'next)
            (some-> node
                    (ucl/slot-value 'next)
                    (ucl/slot-value 'next))))

(defn removeNthFromEnd [head n]
  (let [dummy (new ListNode 0 head)]
    (->> (move-right-n-forward head n)
         (move-left-n-from-end dummy)
         (bypass))
    (ucl/slot-value dummy 'next)))
