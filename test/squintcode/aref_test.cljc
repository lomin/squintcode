(ns squintcode.aref-test
  (:refer-clojure :exclude [make-array])
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]])
            #?(:squint ["assert" :as assert]))
  #?(:clj (:require [squintcode.macros :refer [aref push-end make-array]]))
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests aref push-end make-array]]))
  #?(:cljs (:require-macros [squintcode.macros :refer [aref push-end make-array]])))

(deftest aref-basic-test
  (testing "aref accesses array/list elements"
    (let [arr (make-array 5 :initial-contents [10 20 30 40 50])]
      (is (= 10 (aref arr 0)) "First element should be 10")
      (is (= 30 (aref arr 2)) "Third element should be 30")
      (is (= 50 (aref arr 4)) "Last element should be 50"))))

(deftest aref-after-mutation-test
  (testing "aref works after mutation"
    #?(:squint
       (let [arr (make-array 3 :initial-contents [1 2 3])]
         (push-end arr 4)
         (push-end arr 5)
         (is (= 1 (aref arr 0)) "First element should be 1")
         (is (= 5 (aref arr 4)) "Fifth element should be 5")
         (is (= 5 (.-length arr)) "Should have 5 elements"))

       :cljs
       (let [arr (make-array 3 :initial-contents [1 2 3])]
         (push-end arr 4)
         (push-end arr 5)
         (is (= 1 (aref arr 0)) "First element should be 1")
         (is (= 5 (aref arr 4)) "Fifth element should be 5")
         (is (= 5 (.-length arr)) "Should have 5 elements"))

       :clj
       (let [lst (java.util.ArrayList. [1 2 3])]
         (push-end lst 4)
         (push-end lst 5)
         (is (= 1 (aref (into-array lst) 0)) "First element should be 1")
         (is (= 5 (aref (into-array lst) 4)) "Fifth element should be 5")
         (is (= 5 (.size lst)) "Should have 5 elements")))))

(deftest aref-different-types-test
  (testing "aref works with different value types"
    (let [arr (make-array 4 :initial-contents [42 "hello" true nil])]
      (is (= 42 (aref arr 0)) "Number at index 0")
      (is (= "hello" (aref arr 1)) "String at index 1")
      (is (= true (aref arr 2)) "Boolean at index 2")
      (is (= nil (aref arr 3)) "Nil at index 3"))))

(deftest aref-zero-based-test
  (testing "aref uses zero-based indexing"
    (let [arr (make-array 3 :initial-contents ["zero" "one" "two"])]
      (is (= "zero" (aref arr 0)) "Index 0 should be 'zero'")
      (is (= "one" (aref arr 1)) "Index 1 should be 'one'")
      (is (= "two" (aref arr 2)) "Index 2 should be 'two'"))))

(comment (run-tests 'squintcode.aref-test))
