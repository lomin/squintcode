(ns squintcode.aref-test
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  (:require #?@(:squint [["assert" :as assert]]
                :clj [[clojure.test :refer [deftest is testing]]
                      [squintcode.macros :as cl]]
                :cljs [[cljs.test :refer-macros [deftest is testing]]
                       [squintcode.macros :as cl]])))

(deftest aref-basic-test
  (testing "aref accesses array/list elements"
    (let [arr (cl/make-array 5 :initial-contents [10 20 30 40 50])]
      (is (= 10 (cl/aref arr 0)) "First element should be 10")
      (is (= 30 (cl/aref arr 2)) "Third element should be 30")
      (is (= 50 (cl/aref arr 4)) "Last element should be 50"))))

(deftest aref-after-mutation-test
  (testing "aref works after mutation"
    #?(:squint
       (let [arr (cl/make-array 3 :initial-contents [1 2 3])]
         (cl/push-end arr 4)
         (cl/push-end arr 5)
         (is (= 1 (cl/aref arr 0)) "First element should be 1")
         (is (= 5 (cl/aref arr 4)) "Fifth element should be 5")
         (is (= 5 (cl/length arr)) "Should have 5 elements"))

       :cljs
       (let [arr (cl/make-array 3 :initial-contents [1 2 3])]
         (cl/push-end arr 4)
         (cl/push-end arr 5)
         (is (= 1 (cl/aref arr 0)) "First element should be 1")
         (is (= 5 (cl/aref arr 4)) "Fifth element should be 5")
         (is (= 5 (cl/length arr)) "Should have 5 elements"))

       :clj
       (let [lst (java.util.ArrayList. [1 2 3])]
         (cl/push-end lst 4)
         (cl/push-end lst 5)
         (is (= 1 (cl/aref (into-array lst) 0)) "First element should be 1")
         (is (= 5 (cl/aref (into-array lst) 4)) "Fifth element should be 5")
         (is (= 5 (cl/length lst)) "Should have 5 elements")))))

(deftest aref-different-types-test
  (testing "aref works with different value types"
    (let [arr (cl/make-array 4 :initial-contents [42 "hello" true nil])]
      (is (= 42 (cl/aref arr 0)) "Number at index 0")
      (is (= "hello" (cl/aref arr 1)) "String at index 1")
      (is (= true (cl/aref arr 2)) "Boolean at index 2")
      (is (= nil (cl/aref arr 3)) "Nil at index 3"))))

(deftest aref-zero-based-test
  (testing "aref uses zero-based indexing"
    (let [arr (cl/make-array 3 :initial-contents ["zero" "one" "two"])]
      (is (= "zero" (cl/aref arr 0)) "Index 0 should be 'zero'")
      (is (= "one" (cl/aref arr 1)) "Index 1 should be 'one'")
      (is (= "two" (cl/aref arr 2)) "Index 2 should be 'two'"))))

(deftest length-test
  (let [xs (mapv (cl/length) (map range [2 1 3]))]
    (is (= 2 (cl/aref xs 0)))
    (is (= 2 ((cl/aref) xs 0)))
    (is (= 1 (cl/aref xs 1)))
    (is (= 1 ((cl/aref) xs 1)))
    (is (= 3 (cl/aref xs 2)))
    (is (= 3 ((cl/aref) xs 2)))))