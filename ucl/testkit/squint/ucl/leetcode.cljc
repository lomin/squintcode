(ns ucl.leetcode
  "LeetCode's own classes, as strict test fixtures (D19): JS classes, which
   throw when called without `new`, installed as globals as on LeetCode. Never
   part of a submission.")

(defclass ListNode
  (field val)
  (field next)
  (constructor [this v n]
    (set! (.-val this) (if (undefined? v) 0 v))
    (set! (.-next this) (if (undefined? n) nil n))))

(defclass TreeNode
  (field val)
  (field left)
  (field right)
  (constructor [this v l r]
    (set! (.-val this) (if (undefined? v) 0 v))
    (set! (.-left this) (if (undefined? l) nil l))
    (set! (.-right this) (if (undefined? r) nil r))))

(set! (.-ListNode js/globalThis) ListNode)
(set! (.-TreeNode js/globalThis) TreeNode)
