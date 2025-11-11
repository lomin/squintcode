(ns squintcode.push-end-test
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  (:require #?@(:squint [["assert" :as assert]]
                :clj [[clojure.test :refer [deftest is testing run-tests]]
                      [squintcode.macros :as cl]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
                       [squintcode.macros :as cl]])))

;; NOTE: push-end does NOT guarantee immutability.
;; You WILL get a modified version of the xs you passed.
;; - Squint/CLJS: mutates the JavaScript array in place (.push)
;; - CLJ: mutates the Java List in place (.add)

(deftest push-end-basic-test
  (testing "push-end adds element to end"
    #?(:squint
       (let [arr #js []]
         (cl/push-end arr "first")
         (cl/push-end arr "second")
         (cl/push-end arr "third")
         (is (= 3 (.-length arr)) "Should have 3 elements")
         (is (= "first" (nth arr 0)) "First element should be 'first'")
         (is (= "second" (nth arr 1)) "Second element should be 'second'")
         (is (= "third" (nth arr 2)) "Third element should be 'third'"))

       :cljs
       (let [arr #js []]
         (cl/push-end arr "first")
         (cl/push-end arr "second")
         (cl/push-end arr "third")
         (is (= 3 (count arr)) "Should have 3 elements")
         (is (= "first" (nth arr 0)) "First element should be 'first'")
         (is (= "second" (nth arr 1)) "Second element should be 'second'")
         (is (= "third" (nth arr 2)) "Third element should be 'third'"))

       :clj
       (let [lst (java.util.ArrayList.)]
         (cl/push-end lst "first")
         (cl/push-end lst "second")
         (cl/push-end lst "third")
         (is (= 3 (cl/length lst)) "Should have 3 elements")
         (is (= "first" (.get lst 0)) "First element should be 'first'")
         (is (= "second" (.get lst 1)) "Second element should be 'second'")
         (is (= "third" (.get lst 2)) "Third element should be 'third'")))))

(deftest push-end-return-value-test
  (testing "push-end return value behavior"
    (let [arr (cl/make-array 0)]
      (cl/push-end arr "value")
      (is (= "value" (cl/aref arr 0))))))

(deftest push-end-multiple-types-test
  (testing "push-end with different value types"
    #?(:squint
       (let [arr #js []]
         (cl/push-end arr 1)
         (cl/push-end arr "string")
         (cl/push-end arr true)
         (cl/push-end arr nil)
         (is (= 4 (.-length arr)) "Should have 4 elements")
         (is (= 1 (nth arr 0)) "First element should be 1")
         (is (= "string" (nth arr 1)) "Second element should be 'string'")
         (is (= true (nth arr 2)) "Third element should be true")
         (is (= nil (nth arr 3)) "Fourth element should be nil"))

       :cljs
       (let [arr #js []]
         (cl/push-end arr 1)
         (cl/push-end arr "string")
         (cl/push-end arr true)
         (cl/push-end arr nil)
         (is (= 4 (count arr)) "Should have 4 elements")
         (is (= 1 (nth arr 0)) "First element should be 1")
         (is (= "string" (nth arr 1)) "Second element should be 'string'")
         (is (= true (nth arr 2)) "Third element should be true")
         (is (= nil (nth arr 3)) "Fourth element should be nil"))

       :clj
       (let [lst (java.util.ArrayList.)]
         (cl/push-end lst 1)
         (cl/push-end lst "string")
         (cl/push-end lst true)
         (cl/push-end lst nil)
         (is (= 4 (cl/length lst)) "Should have 4 elements")
         (is (= 1 (.get lst 0)) "First element should be 1")
         (is (= "string" (.get lst 1)) "Second element should be 'string'")
         (is (= true (.get lst 2)) "Third element should be true")
         (is (= nil (.get lst 3)) "Fourth element should be nil")))))

(comment (run-tests 'squintcode.push-end-test))
