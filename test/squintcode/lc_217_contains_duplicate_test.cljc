(ns squintcode.lc-217-contains-duplicate-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-217-contains-duplicate :as loop-version]
            [squintcode.lc-217-contains-duplicate-seq :as seq-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(deftest contains-duplicate-test
  (doseq [[label f] [["hash table" loop-version/containsDuplicate] ["remove-duplicates" seq-version/containsDuplicate]]]
    (testing (str label ": LeetCode examples")
      (is (true? (f (arr [1 2 3 1]))))
      (is (false? (f (arr [1 2 3 4]))))
      (is (true? (f (arr [1 1 1 3 3 4 3 2 4 2])))))
    (testing (str label ": edges")
      (is (false? (f (arr [5]))))
      (is (true? (f (arr [-1000000000 7 -1000000000])))))))
