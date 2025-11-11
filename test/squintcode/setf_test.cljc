(ns squintcode.setf-test
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  (:require #?@(:squint [["assert" :as assert]]
                :clj [[clojure.test :refer [deftest is testing run-tests]]
                      [squintcode.macros :as cl]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
                       [squintcode.macros :as cl]])))

(deftest make-array-basic-test
  (testing "make-array creates array of given size"
    (let [arr (cl/make-array 5)]
      (is (= 5 (cl/length arr))
          "Array should have size 5"))))

(deftest make-array-with-initial-contents-vector-test
  (testing "make-array with :initial-contents from vector"
    (let [arr (cl/make-array 5 :initial-contents [1 2 3 4 5])]
      (is (= 5 (cl/length arr))
          "Array should have size 5")
      (is (= 1 (cl/aref arr 0)) "First element should be 1")
      (is (= 3 (cl/aref arr 2)) "Third element should be 3")
      (is (= 5 (cl/aref arr 4)) "Last element should be 5"))))

(deftest make-array-with-initial-contents-list-test
  (testing "make-array with :initial-contents from list"
    (let [arr (cl/make-array 5 :initial-contents '(10 20 30 40 50))]
      (is (= 5 (cl/length arr))
          "Array should have size 5")
      (is (= 10 (cl/aref arr 0)) "First element should be 10")
      (is (= 30 (cl/aref arr 2)) "Third element should be 30")
      (is (= 50 (cl/aref arr 4)) "Last element should be 50"))))

(deftest setf-aref-basic-test
  (testing "setf with aref modifies array element"
    (let [arr (cl/make-array 5 :initial-contents [1 2 3 4 5])]
      (cl/setf (cl/aref arr 2) 99)
      (is (= 99 (cl/aref arr 2)) "Element at index 2 should be 99")
      (is (= 1 (cl/aref arr 0)) "First element should still be 1")
      (is (= 5 (cl/aref arr 4)) "Last element should still be 5"))))

(deftest setf-aref-multiple-test
  (testing "setf with aref can modify multiple elements"
    (let [arr (cl/make-array 5 :initial-contents [1 2 3 4 5])]
      (cl/setf (cl/aref arr 0) 100)
      (cl/setf (cl/aref arr 2) 200)
      (cl/setf (cl/aref arr 4) 300)
      (is (= 100 (cl/aref arr 0)) "First element should be 100")
      (is (= 2 (cl/aref arr 1)) "Second element should still be 2")
      (is (= 200 (cl/aref arr 2)) "Third element should be 200")
      (is (= 4 (cl/aref arr 3)) "Fourth element should still be 4")
      (is (= 300 (cl/aref arr 4)) "Fifth element should be 300"))))

(deftest setf-aref-different-types-test
  (testing "setf with aref works with different value types"
    (let [arr (cl/make-array 4 :initial-contents [1 2 3 4])]
      (cl/setf (cl/aref arr 0) 42)
      (cl/setf (cl/aref arr 1) "hello")
      (cl/setf (cl/aref arr 2) true)
      (cl/setf (cl/aref arr 3) nil)
      (is (= 42 (cl/aref arr 0)) "Element 0 should be number")
      (is (= "hello" (cl/aref arr 1)) "Element 1 should be string")
      (is (= true (cl/aref arr 2)) "Element 2 should be boolean")
      (is (= nil (cl/aref arr 3)) "Element 3 should be nil"))))

(deftest common-lisp-style-example-test
  (testing "Common Lisp-style example from docs"
    (let [arr (cl/make-array 5 :initial-contents '(1 2 3 4 5))]
      (cl/setf (cl/aref arr 2) 99)
      (is (= 1 (cl/aref arr 0)))
      (is (= 2 (cl/aref arr 1)))
      (is (= 99 (cl/aref arr 2)))
      (is (= 4 (cl/aref arr 3)))
      (is (= 5 (cl/aref arr 4))))))

(comment (run-tests 'squintcode.setf-test))
