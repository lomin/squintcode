(ns squintcode.hash-table-test
  (:require #?@(:squint [["assert" :as assert]]
                :clj [[clojure.test :refer [deftest is testing run-tests]]
                      [squintcode.macros :as cl]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
                       [squintcode.macros :as cl]]))
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl])))

(deftest make-hash-table-empty-test
  (testing "make-hash-table creates empty hash table"
    (let [ht (cl/dict)]
      (is (nil? (cl/gethash ht :key)) "Empty hash table returns nil for missing key"))))

(deftest make-hash-table-with-initial-values-test
  (testing "make-hash-table with initial key-value pairs"
    (let [ht (cl/dict :a 1 :b 2 :c 3)]
      (is (= 1 (cl/gethash ht :a)) "Key :a should be 1")
      (is (= 2 (cl/gethash ht :b)) "Key :b should be 2")
      (is (= 3 (cl/gethash ht :c)) "Key :c should be 3"))))

(deftest gethash-basic-test
  (testing "gethash retrieves values from hash table"
    (let [ht (cl/dict :name "Alice" :age 30)]
      (is (= "Alice" (cl/gethash ht :name)) "Should get name")
      (is (= 30 (cl/gethash ht :age)) "Should get age"))))

(deftest gethash-with-default-test
  (testing "gethash returns default for missing keys"
    (let [ht (cl/dict :exists "value")]
      (is (= "value" (cl/gethash ht :exists "default")) "Existing key returns value")
      (is (= "default" (cl/gethash ht :missing "default")) "Missing key returns default"))))

(deftest gethash-missing-key-test
  (testing "gethash returns nil for missing keys without default"
    (let [ht (cl/dict :a 1)]
      (is (nil? (cl/gethash ht :b)) "Missing key should return nil"))))

(deftest setf-gethash-basic-test
  (testing "setf with gethash sets hash table entry"
    (let [ht (cl/dict)]
      (cl/setf (cl/gethash ht :key) "value")
      (is (= "value" (cl/gethash ht :key)) "Should set and retrieve value"))))

(deftest setf-gethash-update-test
  (testing "setf with gethash updates existing entry"
    (let [ht (cl/dict :key "old")]
      (cl/setf (cl/gethash ht :key) "new")
      (is (= "new" (cl/gethash ht :key)) "Should update existing value"))))

(deftest setf-gethash-multiple-keys-test
  (testing "setf with gethash works with multiple keys"
    (let [ht (cl/dict)]
      (cl/setf (cl/gethash ht :a) 1)
      (cl/setf (cl/gethash ht :b) 2)
      (cl/setf (cl/gethash ht :c) 3)
      (is (= 1 (cl/gethash ht :a)) "Key :a should be 1")
      (is (= 2 (cl/gethash ht :b)) "Key :b should be 2")
      (is (= 3 (cl/gethash ht :c)) "Key :c should be 3"))))

(deftest setf-gethash-different-types-test
  (testing "setf with gethash works with different value types"
    (let [ht (cl/dict)]
      (cl/setf (cl/gethash ht :num) 42)
      (cl/setf (cl/gethash ht :str) "hello")
      (cl/setf (cl/gethash ht :bool) true)
      (cl/setf (cl/gethash ht :nil) nil)
      (is (= 42 (cl/gethash ht :num)) "Number value")
      (is (= "hello" (cl/gethash ht :str)) "String value")
      (is (= true (cl/gethash ht :bool)) "Boolean value")
      (is (= nil (cl/gethash ht :nil)) "Nil value"))))

(deftest setf-gethash-string-keys-test
  (testing "setf with gethash works with string keys"
    (let [ht (cl/dict)]
      (cl/setf (cl/gethash ht "key1") "value1")
      (cl/setf (cl/gethash ht "key2") "value2")
      (is (= "value1" (cl/gethash ht "key1")) "String key 1")
      (is (= "value2" (cl/gethash ht "key2")) "String key 2"))))

(deftest common-lisp-style-example-test
  (testing "Common Lisp-style hash table usage"
    (let [ht (cl/dict)]
      (cl/setf (cl/gethash ht :x) 10)
      (cl/setf (cl/gethash ht :y) 20)
      (cl/setf (cl/gethash ht :sum) (+ (cl/gethash ht :x) (cl/gethash ht :y)))
      (is (= 10 (cl/gethash ht :x)))
      (is (= 20 (cl/gethash ht :y)))
      (is (= 30 (cl/gethash ht :sum))))))

(deftest hash-table-as-counter-test
  (testing "Using hash table as a counter (like LeetCode problems)"
    (let [ht (cl/dict)]
      ;; Count occurrences
      (cl/setf (cl/gethash ht :a) (inc (cl/gethash ht :a 0)))
      (cl/setf (cl/gethash ht :a) (inc (cl/gethash ht :a 0)))
      (cl/setf (cl/gethash ht :b) (inc (cl/gethash ht :b 0)))
      (is (= 2 (cl/gethash ht :a)) "Key :a counted twice")
      (is (= 1 (cl/gethash ht :b)) "Key :b counted once"))))

(comment (run-tests 'squintcode.hash-table-test))
