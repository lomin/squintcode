(ns squintcode.leetcode-setup)

;; Mock ListNode class for Squint tests (simulates LeetCode environment)
;; This file should be imported BEFORE any LeetCode problem implementations
#?(:squint
   (do
     (defclass ListNode
       (field val)
       (field next)
       (constructor [this v n]
                    (set! (.-val this) v)
                    (set! (.-next this) n)))
     ;; Make ListNode global so implementations can access it
     (set! js/globalThis.ListNode ListNode)))

#?(:clj nil :cljs nil)
