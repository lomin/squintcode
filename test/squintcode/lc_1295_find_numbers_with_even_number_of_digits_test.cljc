(ns squintcode.lc-1295-find-numbers-with-even-number-of-digits-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [squintcode.lc-1295-find-numbers-with-even-number-of-digits :as loop-version]
            [squintcode.lc-1295-find-numbers-with-even-number-of-digits-seq :as seq-version]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(deftest find-numbers-test
  (doseq [[label f] [["loop/recur" loop-version/findNumbers] ["count-if" seq-version/findNumbers]]]
    (testing (str label ": LeetCode examples")
      (is (== 2 (f (arr [12 345 2 6 7896]))))
      (is (== 1 (f (arr [555 901 482 1771])))))
    (testing (str label ": edges")
      (is (== 0 (f (arr []))))
      (is (== 1 (f (arr [100000]))))
      (is (== 2 (f (arr [10 99 9])))))))
