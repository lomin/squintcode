(ns squintcode.push-end-test
  (:require #?@(:squint []
                :clj [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]])
            #?(:squint ["assert" :as assert]))
  #?(:clj (:require [squintcode.macros :refer [push-end]]))
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests push-end]]))
  #?(:cljs (:require-macros [squintcode.macros :refer [push-end]])))

;; NOTE: push-end does NOT guarantee immutability.
;; You WILL get a modified version of the xs you passed.
;; - Squint/CLJS: mutates the JavaScript array in place (.push)
;; - CLJ: mutates the Java List in place (.add)

(deftest push-end-basic-test
  (testing "push-end adds element to end"
    #?(:squint
       (let [arr #js []]
         (push-end arr "first")
         (push-end arr "second")
         (push-end arr "third")
         (is (= 3 (.-length arr)) "Should have 3 elements")
         (is (= "first" (nth arr 0)) "First element should be 'first'")
         (is (= "second" (nth arr 1)) "Second element should be 'second'")
         (is (= "third" (nth arr 2)) "Third element should be 'third'"))

       :cljs
       (let [arr #js []]
         (push-end arr "first")
         (push-end arr "second")
         (push-end arr "third")
         (is (= 3 (count arr)) "Should have 3 elements")
         (is (= "first" (nth arr 0)) "First element should be 'first'")
         (is (= "second" (nth arr 1)) "Second element should be 'second'")
         (is (= "third" (nth arr 2)) "Third element should be 'third'"))

       :clj
       (let [lst (java.util.ArrayList.)]
         (push-end lst "first")
         (push-end lst "second")
         (push-end lst "third")
         (is (= 3 (.size lst)) "Should have 3 elements")
         (is (= "first" (.get lst 0)) "First element should be 'first'")
         (is (= "second" (.get lst 1)) "Second element should be 'second'")
         (is (= "third" (.get lst 2)) "Third element should be 'third'")))))

(deftest push-end-return-value-test
  (testing "push-end return value behavior"
    #?(:squint
       (let [arr #js []
             result (push-end arr "value")]
         (is (= 1 result) "JS .push returns new length"))

       :cljs
       (let [arr #js []
             result (push-end arr "value")]
         (is (= 1 result) "JS .push returns new length"))

       :clj
       (let [lst (java.util.ArrayList.)
             result (push-end lst "value")]
         (is (= true result) "Java .add returns boolean true")))))

(deftest push-end-multiple-types-test
  (testing "push-end with different value types"
    #?(:squint
       (let [arr #js []]
         (push-end arr 1)
         (push-end arr "string")
         (push-end arr true)
         (push-end arr nil)
         (is (= 4 (.-length arr)) "Should have 4 elements")
         (is (= 1 (nth arr 0)) "First element should be 1")
         (is (= "string" (nth arr 1)) "Second element should be 'string'")
         (is (= true (nth arr 2)) "Third element should be true")
         (is (= nil (nth arr 3)) "Fourth element should be nil"))

       :cljs
       (let [arr #js []]
         (push-end arr 1)
         (push-end arr "string")
         (push-end arr true)
         (push-end arr nil)
         (is (= 4 (count arr)) "Should have 4 elements")
         (is (= 1 (nth arr 0)) "First element should be 1")
         (is (= "string" (nth arr 1)) "Second element should be 'string'")
         (is (= true (nth arr 2)) "Third element should be true")
         (is (= nil (nth arr 3)) "Fourth element should be nil"))

       :clj
       (let [lst (java.util.ArrayList.)]
         (push-end lst 1)
         (push-end lst "string")
         (push-end lst true)
         (push-end lst nil)
         (is (= 4 (.size lst)) "Should have 4 elements")
         (is (= 1 (.get lst 0)) "First element should be 1")
         (is (= "string" (.get lst 1)) "Second element should be 'string'")
         (is (= true (.get lst 2)) "Third element should be true")
         (is (= nil (.get lst 3)) "Fourth element should be nil")))))

(comment (run-tests 'squintcode.push-end-test))
