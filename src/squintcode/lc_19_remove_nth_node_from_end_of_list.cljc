(ns squintcode.lc-19-remove-nth-node-from-end-of-list
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  #?(:clj (:require [squintcode.macros :as cl])))

#?(:squint nil
   :default
   (deftype ListNode [^:unsynchronized-mutable val
                      ^:unsynchronized-mutable next]))

(defn move-n-forward [head n]
  (if (and head (pos? n))
    (recur (cl/get! head next) (dec n))
    head))

(defn move-to-end [left right]
  (if right
    (recur (cl/get! left next)
           (cl/get! right next))
    [left right]))

(defn bypass [node]
  (cl/setf (cl/get! node next)
           (some-> node
                   (cl/get! next)
                   (cl/get! next))))

(defn removeNthFromEnd [head n]
  (let [dummy (new ListNode nil head)
        right (move-n-forward head n)
        [left _] (move-to-end dummy right)]
    (bypass left)
    (cl/get! dummy next)))

(comment
  (defn list->linked
    "Convert a vector/list to a linked list"
    [coll]
    (reduce (fn [next-node val]
              (new ListNode val next-node))
            nil
            (reverse coll)))

  (def sample (list->linked [1 2 3 4 5]))

  sample

  (removeNthFromEnd (list->linked [1 2 3 4 5]) 2)
  (removeNthFromEnd (list->linked [1 2 3 4 5]) 1)

  (removeNthFromEnd nil 1))