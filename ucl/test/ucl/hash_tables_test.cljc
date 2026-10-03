(ns ucl.hash-tables-test
  "make-hash-table, gethash and its places."
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]))

(deftest gethash-test
  (testing "missing keys, defaults, and a stored nil"
    (let [h (ucl/make-hash-table :initial-contents {0 1 "s" :v})]
      (is (= 1 (ucl/gethash 0 h)))
      (is (= :v (ucl/gethash "s" h)))
      (is (nil? (ucl/gethash 5 h)))
      (is (= :d (ucl/gethash 5 h :d)))
      (ucl/setf (ucl/gethash 5 h) nil)
      (is (nil? (ucl/gethash 5 h :d)) "a key stored with nil is present, as in CL")))
  (testing "setf, incf and decf"
    (let [h (ucl/make-hash-table)]
      (is (= 3 (ucl/setf (ucl/gethash :k h) 3)))
      (is (= 4 (ucl/incf (ucl/gethash :k h))))
      (is (= 1 (ucl/incf (ucl/gethash :new h 0))) "the default seeds the read")
      (is (= 0 (ucl/decf (ucl/gethash :new h 0))))
      (is (= 4 (ucl/gethash :k h)))))
  (testing "make-hash-table accepts :test 'eql and :size"
    (is (= 1 (ucl/gethash 1 (ucl/make-hash-table :test 'eql :size 10 :initial-contents {1 1}))))))

(deftest key-normalization-test
  (testing "keyword keys: literal and run-time are the same key (D12)"
    (let [h (ucl/make-hash-table :initial-contents {:a 1})
          k :a]
      (is (= 1 (ucl/gethash k h)))
      (ucl/setf (ucl/gethash k h) 2)
      (is (= 2 (ucl/gethash :a h)))))
  (testing "a fixnum read from a vector is the same key as the literal (D12)"
    (let [v (ucl/make-array 1 :element-type 'fixnum :initial-element 5)
          h (ucl/make-hash-table :initial-contents {5 :five})]
      (is (= :five (ucl/gethash (ucl/elt v 0) h)))
      (ucl/setf (ucl/gethash (ucl/elt v 0) h) :still-five)
      (is (= :still-five (ucl/gethash 5 h))))))
