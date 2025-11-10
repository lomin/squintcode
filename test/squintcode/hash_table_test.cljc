(ns squintcode.hash-table-test
  (:refer-clojure :exclude [make-array])
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]])
            #?(:squint ["assert" :as assert]))
  #?(:clj (:require [squintcode.macros :refer [dict gethash setf]]))
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests make-hash-table gethash setf]]))
  #?(:cljs (:require-macros [squintcode.macros :refer [dict gethash setf]])))

(deftest make-hash-table-empty-test
  (testing "make-hash-table creates empty hash table"
    (let [ht (dict)]
      (is (nil? (gethash ht :key)) "Empty hash table returns nil for missing key"))))

(deftest make-hash-table-with-initial-values-test
  (testing "make-hash-table with initial key-value pairs"
    (let [ht (dict :a 1 :b 2 :c 3)]
      (is (= 1 (gethash ht :a)) "Key :a should be 1")
      (is (= 2 (gethash ht :b)) "Key :b should be 2")
      (is (= 3 (gethash ht :c)) "Key :c should be 3"))))

(deftest gethash-basic-test
  (testing "gethash retrieves values from hash table"
    (let [ht (dict :name "Alice" :age 30)]
      (is (= "Alice" (gethash ht :name)) "Should get name")
      (is (= 30 (gethash ht :age)) "Should get age"))))

(deftest gethash-with-default-test
  (testing "gethash returns default for missing keys"
    (let [ht (dict :exists "value")]
      (is (= "value" (gethash ht :exists "default")) "Existing key returns value")
      (is (= "default" (gethash ht :missing "default")) "Missing key returns default"))))

(deftest gethash-missing-key-test
  (testing "gethash returns nil for missing keys without default"
    (let [ht (dict :a 1)]
      (is (nil? (gethash ht :b)) "Missing key should return nil"))))

(deftest setf-gethash-basic-test
  (testing "setf with gethash sets hash table entry"
    (let [ht (dict)]
      (setf (gethash ht :key) "value")
      (is (= "value" (gethash ht :key)) "Should set and retrieve value"))))

(deftest setf-gethash-update-test
  (testing "setf with gethash updates existing entry"
    (let [ht (dict :key "old")]
      (setf (gethash ht :key) "new")
      (is (= "new" (gethash ht :key)) "Should update existing value"))))

(deftest setf-gethash-multiple-keys-test
  (testing "setf with gethash works with multiple keys"
    (let [ht (dict)]
      (setf (gethash ht :a) 1)
      (setf (gethash ht :b) 2)
      (setf (gethash ht :c) 3)
      (is (= 1 (gethash ht :a)) "Key :a should be 1")
      (is (= 2 (gethash ht :b)) "Key :b should be 2")
      (is (= 3 (gethash ht :c)) "Key :c should be 3"))))

(deftest setf-gethash-different-types-test
  (testing "setf with gethash works with different value types"
    (let [ht (dict)]
      (setf (gethash ht :num) 42)
      (setf (gethash ht :str) "hello")
      (setf (gethash ht :bool) true)
      (setf (gethash ht :nil) nil)
      (is (= 42 (gethash ht :num)) "Number value")
      (is (= "hello" (gethash ht :str)) "String value")
      (is (= true (gethash ht :bool)) "Boolean value")
      (is (= nil (gethash ht :nil)) "Nil value"))))

(deftest setf-gethash-string-keys-test
  (testing "setf with gethash works with string keys"
    (let [ht (dict)]
      (setf (gethash ht "key1") "value1")
      (setf (gethash ht "key2") "value2")
      (is (= "value1" (gethash ht "key1")) "String key 1")
      (is (= "value2" (gethash ht "key2")) "String key 2"))))

(deftest common-lisp-style-example-test
  (testing "Common Lisp-style hash table usage"
    (let [ht (dict)]
      (setf (gethash ht :x) 10)
      (setf (gethash ht :y) 20)
      (setf (gethash ht :sum) (+ (gethash ht :x) (gethash ht :y)))
      (is (= 10 (gethash ht :x)))
      (is (= 20 (gethash ht :y)))
      (is (= 30 (gethash ht :sum))))))

(deftest hash-table-as-counter-test
  (testing "Using hash table as a counter (like LeetCode problems)"
    (let [ht (dict)]
      ;; Count occurrences
      (setf (gethash ht :a) (inc (gethash ht :a 0)))
      (setf (gethash ht :a) (inc (gethash ht :a 0)))
      (setf (gethash ht :b) (inc (gethash ht :b 0)))
      (is (= 2 (gethash ht :a)) "Key :a counted twice")
      (is (= 1 (gethash ht :b)) "Key :b counted once"))))

(comment (run-tests 'squintcode.hash-table-test))
