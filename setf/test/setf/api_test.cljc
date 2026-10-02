(ns setf.api-test
  "Tests for the host-agnostic `setf` library. This file is byte-identical
   across targets -- only the require clauses differ, and that difference IS the
   thing under test."
  #?(:squint (:require-macros [squintcode.macros :refer [deftest is testing run-tests]])
     :cljs (:require-macros [cljs.test :refer-macros [deftest is testing]
                             :refer [run-tests]]))
  (:require #?@(:squint []
                :clj  [[clojure.test :refer [deftest is testing run-tests]]]
                :cljs [[cljs.test :refer-macros [deftest is testing]
                        :refer [run-tests]]])
           ;; ONE entry per namespace: two entries for the same ns make Squint
           ;; emit a duplicate `import * as`, which is invalid ESM.
           #?(:clj  [setf.api :as api :refer [setf! incf! decf! elt gethash get!]]
               :cljs [setf.api :as api :refer-macros [setf! incf! decf! elt gethash get!]])))

(deftest elt-test
  (testing "indexed assignment and read-modify-write"
    (let [a (api/make-arr 3)]
      (setf! (elt a 1) 99)
      (is (= 99 (elt a 1)))
      (incf! (elt a 1))
      (is (= 100 (elt a 1)))
      (decf! (elt a 1) 10)
      (is (= 90 (elt a 1)))
      (decf! (elt a 1))
      (is (= 89 (elt a 1))))))

(deftest gethash-test
  (testing "keyed assignment and read-modify-write"
    (let [m (api/make-map)]
      (setf! (gethash m "k") 7)
      (is (= 7 (gethash m "k")))
      (incf! (gethash m "k") 3)
      (is (= 10 (gethash m "k")))
      (decf! (gethash m "k"))
      (is (= 9 (gethash m "k"))))))

(deftest get-test
  (testing "field assignment"
    (let [o (api/make-obj)]
      (setf! (get! o x) 42)
      (is (= 42 (get! o x)))
      (incf! (get! o x) 8)
      (is (= 50 (get! o x))))))

(deftest read-evaluates-arguments-once-test
  (testing "reading also binds a compound index expression exactly once"
    (let [a    (api/make-arr 2)
          runs (atom 0)
          bump (fn [] (swap! runs inc) 1)]
      (setf! (elt a 1) 7)
      (is (= 7 (elt a (bump))))
      (is (= 1 @runs) "the index expression must be evaluated once, not twice"))))

(deftest read-and-write-agree-test    (testing "the same name reads what it wrote, on every host"
    ;; Asserted one value at a time on purpose: Squint's `is` is node's
    ;; `assert.equal`, i.e. `==`, and `==` on two distinct arrays is reference
    ;; equality. Comparing a collection in this suite would fail on Squint while
    ;; passing on the other two hosts -- a harness limit, not a result.
    (let [a (api/make-arr 3)
          m (api/make-map)
          o (api/make-obj)]
      (setf! (elt a 2) 10)
      (setf! (gethash m "k") 20)
      (setf! (get! o x) 30)
      (is (= 10 (elt a 2)))
      (is (= 20 (gethash m "k")))
      (is (= 30 (get! o x)))
      (is (nil? (gethash m "absent")) "reading a missing key yields nil, not a throw"))))

(deftest returns-value-test
  (testing "the forms evaluate to the assigned / resulting value"
    (let [a (api/make-arr 2)]
      (is (= 5 (setf! (elt a 0) 5)))
      (is (= 7 (incf! (elt a 0) 2))))))

(deftest evaluates-arguments-once-test
  (testing "a compound index expression runs exactly once"
    (let [a    (api/make-arr 2)
          runs (atom 0)
          bump (fn [] (swap! runs inc) 1)]
      (setf! (elt a (bump)) 3)
      (is (= 1 @runs) "the index expression must be evaluated once, not twice")
      (is (= 3 (elt a 1))))))

(deftest slot-is-spliced-test
  (testing "a slot gets no binding and the form still works"
    (let [o (api/make-obj)]
      (setf! (get! o x) 1)
      (is (= 1 (.-x o)))
      (setf! (get! o y) 2)
      (is (= 2 (.-y o))))))

;; ---------------------------------------------------------------------------
;; The tests below inspect a macro expansion, so they run on the JVM only.
;; `macroexpand-1` is an ordinary function on Clojure but a COMPILE-TIME macro on
;; ClojureScript and Squint, so a bad form fails the build rather than throwing
;; something a `try` can catch. The logic they cover lives in the shared
;; namespace, so covering it once is meaningful -- but do not read their absence
;; on the JS hosts as the rejection being untested there.
;; ---------------------------------------------------------------------------

#?(:clj
   (do
     ;; A macro failure arrives wrapped; walk to the innermost message.
     (defn- root-message [e]
       (loop [e e]
         (if-let [c (ex-cause e)] (recur c) (ex-message e))))

     (deftest slot-is-spliced-expansion-test
       (testing "a slot produces no binding in the expansion"
         (let [bindings (nth (macroexpand-1 '(setf.api/setf! (get! o x) 1)) 1)]
           (is (= 4 (count bindings)) "receiver + value only")
           (is (not-any? #(= 'x %) bindings) "the slot must not be bound"))))

     (deftest slot-expression-fails-test
       (testing "a slot that is an expression is rejected by name"
         (let [msg (try (macroexpand-1 '(setf.api/setf! (get! o (inc 1)) 1))
                        (catch Exception e (root-message e)))]
           (is (some? msg))
           (is (re-find #"slot" msg)))))

     (deftest wrong-arity-fails-test
       (testing "a place with the wrong number of arguments is rejected"
         (is (thrown? Exception (macroexpand-1 '(setf.api/setf! (elt a) 1))))))

     (deftest unknown-place-fails-test
       (testing "an unknown place is rejected, and the message names the known ones"
         (let [msg (try (macroexpand-1 '(setf.api/setf! (frobnicate a 1) 1))
                        (catch Exception e (root-message e)))]
           (is (some? msg) "must throw")
           (is (re-find #"frobnicate" msg))
           (is (re-find #"elt" msg)))))

     (deftest read-expansion-test
       (testing "a read binds its runtime args and emits only the access"
         (let [form (macroexpand-1 '(setf.api/elt a 1))]
           (is (and (seq? form) (= 'let (first form))) "must be a let")
           (is (= '[[t0 a] [t1 1]] (map vec (partition 2 (second form))))
               "coll and index, each bound exactly once")
           (is (= '(.get t0 t1) (nth form 2)) "the body is the access, nothing else"))))))
