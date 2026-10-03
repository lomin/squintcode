(ns ucl.leetcode
  "LeetCode's own classes, as strict test fixtures (D19). On LeetCode they are
   globals that a solution names bare: (new ListNode 0 head). Here they are
   classes in the default package, which every namespace resolves the same
   way (H28). Each implements ucl's per-slot interfaces, so ucl/slot-value
   reads and writes them. Never part of a submission."
  (:require [ucl.api :as api]))

(defmacro ^:private defclass
  "A default-package class whose constructor takes every slot, in LeetCode's
   constructor order."
  [nm slots]
  (let [ifaces (mapv api/ensure-slot-interface! slots)
        fields (mapv #(with-meta % {:unsynchronized-mutable true}) slots)]
    `(deftype* ~nm ~nm ~fields
       :implements [~@ifaces clojure.lang.IType]
       ~@(mapcat (fn [s]
                   [(list (symbol (munge (name s))) ['_] s)
                    (list (symbol (str "set_" (munge (name s)))) ['_ 'v] (list 'set! s 'v) 'v)])
                 slots))))

(defclass ListNode [val next])
(defclass TreeNode [val left right])
