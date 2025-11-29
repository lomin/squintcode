(ns squintcode.aloop-nested-length-test
  #?(:squint (:require-macros [squintcode.macros :as cl :refer [deftest is testing run-tests]]))
  #?(:cljs (:require-macros [squintcode.macros :as cl]))
  (:require #?@(:squint [["assert" :as assert]]
                :clj [[clojure.test :refer [deftest is testing run-tests]]
                      [squintcode.macros :as cl]]
                :cljs [[cljs.test :refer-macros [deftest is testing] :refer [run-tests]]
                       [squintcode.macros :as cl]])))

(deftest nested-aloop-length-scope-test
  (testing "each nested aloop has its own ::aloop length binding"
    ;; Setup: outer collection has 2 elements, inner collections have 1 and 3 elements
    (let [outer-collection [(cl/make-array 1 :initial-contents [11])
                            (cl/make-array 3 :initial-contents [21 22 23])]
          ;; Outer aloop iterates over 2 rows; (cl/length ::aloop) here = 2
          inner-lengths (cl/aloop outer-collection row
                          [result (cl/make-array (cl/length ::aloop))  ; uses outer length (2)
                           idx 0]
                          (if row
                            ;; Inner aloop iterates over each row's elements
                            ;; (cl/length ::aloop) here should be row's length (1 or 3), NOT 2
                            (let [this-row-length (cl/aloop row _elem [len (cl/length ::aloop)]
                                                    (if _elem
                                                      (recur len)
                                                      len))]
                              (cl/setf (cl/aref result idx) this-row-length)
                              (recur result (inc idx)))
                            result))]
      ;; If scoping is broken, inner (cl/length ::aloop) would return 2 (outer's length)
      ;; instead of 1 and 3 (each row's actual length)
      (is (= 1 (cl/aref inner-lengths 0))
          "first inner aloop should see length 1, not outer's length 2")
      (is (= 3 (cl/aref inner-lengths 1))
          "second inner aloop should see length 3, not outer's length 2"))))

(comment (run-tests 'squintcode.aloop-nested-length-test))
