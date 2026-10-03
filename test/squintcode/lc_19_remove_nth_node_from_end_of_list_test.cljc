(ns squintcode.lc-19-remove-nth-node-from-end-of-list-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.leetcode]
            [ucl.api :as ucl]
            [squintcode.lc-19-remove-nth-node-from-end-of-list :refer [removeNthFromEnd]]))

(defn list->linked
  "LeetCode's input: a chain of ListNode."
  [coll]
  (reduce (fn [next-node val] (new ListNode val next-node))
          nil
          (reverse coll)))

(defn linked->list [head]
  (loop [node head
         result []]
    (if node
      (recur (ucl/slot-value node 'next)
             (conj result (ucl/slot-value node 'val)))
      result)))

(deftest remove-nth-from-end-test
  (testing "LeetCode examples"
    (is (= [1 2 3 5] (linked->list (removeNthFromEnd (list->linked [1 2 3 4 5]) 2))))
    (is (= [] (linked->list (removeNthFromEnd (list->linked [1]) 1))))
    (is (= [1] (linked->list (removeNthFromEnd (list->linked [1 2]) 1)))))
  (testing "first and last positions"
    (is (= [1 2 3 4] (linked->list (removeNthFromEnd (list->linked [1 2 3 4 5]) 1))))
    (is (= [2 3 4 5] (linked->list (removeNthFromEnd (list->linked [1 2 3 4 5]) 5))))
    (is (= [2] (linked->list (removeNthFromEnd (list->linked [1 2]) 2))))))
