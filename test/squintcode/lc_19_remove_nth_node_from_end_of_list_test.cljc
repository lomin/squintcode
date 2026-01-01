(ns squintcode.lc-19-remove-nth-node-from-end-of-list-test
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]
                      [squintcode.lc-19-remove-nth-node-from-end-of-list
                       :refer [removeNthFromEnd]]
                      [squintcode.macros :as cl]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
                       [squintcode.lc-19-remove-nth-node-from-end-of-list
                        :refer [removeNthFromEnd ListNode]]])
            ;; Squint-specific imports (JS syntax)
            #?(:squint ["assert" :as assert])
            #?(:squint [squintcode.leetcode-setup])
            #?(:squint [squintcode.lc-19-remove-nth-node-from-end-of-list
                        :refer [removeNthFromEnd]]))
  #?(:clj (:import [squintcode.lc_19_remove_nth_node_from_end_of_list ListNode]))
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests get!]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl])))

;; Helper: convert vector to linked list
(defn list->linked [coll]
  (reduce (fn [next-node val]
            #?(:squint (new js/ListNode val next-node)
               :default (new ListNode val next-node)))
          nil
          (reverse coll)))

;; Helper: convert linked list to vector
(defn linked->list [head]
  (loop [node head
         result []]
    (if node
      (recur (cl/get! node next)
             (conj result (cl/get! node val)))
      result)))

(deftest remove-second-from-end-test
  (testing "removeNthFromEnd [1 2 3 4 5] n=2 should return [1 2 3 5]"
    (let [head (list->linked [1 2 3 4 5])
          result (removeNthFromEnd head 2)]
      (is (= (str [1 2 3 5]) (str (linked->list result)))
          "Should remove the 4 (second from end)"))))

(deftest remove-first-from-end-test
  (testing "removeNthFromEnd [1 2 3 4 5] n=1 should return [1 2 3 4]"
    (let [head (list->linked [1 2 3 4 5])
          result (removeNthFromEnd head 1)]
      (is (= (str [1 2 3 4]) (str (linked->list result)))
          "Should remove the last element (5)"))))

(deftest remove-fifth-from-end-test
  (testing "removeNthFromEnd [1 2 3 4 5] n=5 should return [2 3 4 5]"
    (let [head (list->linked [1 2 3 4 5])
          result (removeNthFromEnd head 5)]
      (is (= (str [2 3 4 5]) (str (linked->list result)))
          "Should remove the first element (1)"))))

(deftest remove-only-element-test
  (testing "removeNthFromEnd [1] n=1 should return []"
    (let [head (list->linked [1])
          result (removeNthFromEnd head 1)]
      (is (= (str []) (str (linked->list result)))
          "Should return empty list when removing only element"))))

(deftest remove-from-two-element-list-test
  (testing "removeNthFromEnd [1 2] n=1 should return [1]"
    (let [head (list->linked [1 2])
          result (removeNthFromEnd head 1)]
      (is (= (str [1]) (str (linked->list result)))
          "Should remove last element from two-element list"))))

(deftest remove-from-two-element-list-first-test
  (testing "removeNthFromEnd [1 2] n=2 should return [2]"
    (let [head (list->linked [1 2])
          result (removeNthFromEnd head 2)]
      (is (= (str [2]) (str (linked->list result)))
          "Should remove first element from two-element list"))))

(comment (run-tests 'squintcode.lc-19-remove-nth-node-from-end-of-list-test))
