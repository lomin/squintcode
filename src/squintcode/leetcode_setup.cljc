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

;; Extend IIndexed/ICounted protocols for Uint32Array in ClojureScript
;; so that (nth typed-array index) and (count typed-array) work.
;; This can't live in macros.cljc because Squint's SCI (which loads macro
;; files) only has :cljs in its features, not :squint — so it would try to
;; evaluate the extend-type and fail with "Protocol not found: IIndexed".
#?(:squint nil
   :cljs
   (extend-type js/Uint32Array
     IIndexed
     (-nth
       ([coll n]
        (aget coll n))
       ([coll n not-found]
        (if (and (>= n 0) (< n (.-length coll)))
          (aget coll n)
          not-found)))
     ICounted
     (-count [coll]
       (.-length coll))))
