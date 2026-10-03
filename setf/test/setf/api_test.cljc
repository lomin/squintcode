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

(deftest place-as-value-test
  (testing "a place name becomes a function, so it can be mapped"
    ;; A place name is a macro and cannot be passed to `map` by itself. The
    ;; 1-arity fixes the trailing argument and returns an ordinary function,
    ;; which is what makes a place usable in a higher-order position.
    (let [xs [(api/make-obj) (api/make-obj)]]
      (setf! (get! (nth xs 0) x) 10)
      (setf! (get! (nth xs 1) x) 20)
      (is (= 10 ((get! x) (nth xs 0))))
      (is (= 20 ((get! x) (nth xs 1))))
      (is (fn? (get! x)) "the curried form is a function value")))

  (testing "elt and gethash curry too, and fix a plain value"
    (let [as [(api/make-arr 3) (api/make-arr 3)]
          ms [(api/make-map) (api/make-map)]]
      (setf! (elt (first as) 1) 7)
      (setf! (elt (second as) 1) 8)
      (setf! (gethash (first ms) "k") :v)
      (is (= 7 ((elt 1) (first as))))
      (is (= 8 ((elt 1) (second as))) "each reader sees its own receiver")
      (is (= :v ((gethash "k") (first ms))))
      (is (nil? ((gethash "k") (second ms))) "nothing was stored under that key")
      (is (fn? (elt 1)))
      (is (fn? (gethash "k")))))

  (testing "a fixed argument runs once per application, not twice"
    ;; The unquoted argument lands in the closure BODY, so it is evaluated on
    ;; each application -- exactly once, under the same rule the direct form
    ;; obeys. It is NOT hoisted out to run once when the reader is built; that
    ;; would be wrong for an index that is meant to vary per collection.
    (let [a    (api/make-arr 2)
          runs (atom 0)
          bump (fn [] (swap! runs inc) 1)]
      (setf! (elt a 1) 5)
      (let [reader (elt (bump))]
        (is (= 0 @runs) "building the reader runs nothing yet")
        (is (= 5 (reader a)))
        (is (= 1 @runs) "one application, one evaluation")
        (is (= 5 (reader a)))
        (is (= 2 @runs) "and again on the next application")))))

(deftest make-arr-test
  (testing "a new array has length n and every slot reads 0, on every host"
    (let [a (api/make-arr 3)]
      (is (= 0 (elt a 0)))
      (is (= 0 (elt a 1)))
      (is (= 0 (elt a 2)))
      (incf! (elt a 2))
      (is (= 1 (elt a 2)) "an untouched slot is a number, so incf! works on it"))))

(deftest hygiene-test
  (testing "a caller's local is never captured by a generated binding"
    ;; The names below are exactly the ones an unhygienic expansion would bind.
    (let [a  (api/make-arr 3)
          t0 2
          t1 7
          v  9]
      (setf! (elt a t0) t1)
      (is (= 7 (elt a 2)) "index and value are the caller's t0 and t1")
      (setf! (elt a 0) v)
      (is (= 9 (elt a 0)) "the value is the caller's v")
      (incf! (elt a 0) t0)
      (is (= 11 (elt a 0)) "the delta is the caller's t0")
      (is (= 7 (elt a t0)) "a read sees the caller's t0")
      (is (= 7 ((elt t0) a)) "so does a curried read"))))

(deftest qualified-place-test
  (testing "a place may be named through the namespace alias"
    (let [a (api/make-arr 2)
          m (api/make-map)]
      (setf! (api/elt a 1) 4)
      (is (= 4 (api/elt a 1)))
      (incf! (api/elt a 1))
      (is (= 5 (elt a 1)))
      (setf! (api/gethash m "k") 1)
      (is (= 1 (gethash m "k"))))))

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
         (let [form  (macroexpand-1 '(setf.api/elt a 1))
               pairs (map vec (partition 2 (second form)))
               [[t0 _] [t1 _]] pairs]
           (is (and (seq? form) (= 'let (first form))) "must be a let")
           (is (= '[a 1] (map second pairs)) "coll and index, each bound exactly once")
           (is (not-any? '#{t0 t1 v} (map first pairs)) "generated names are fresh")
           (is (= (list '.get t0 t1) (nth form 2)) "the body is the access, nothing else"))))

     ;; A user fn that merely shares a name with a backend constructor.
     (defn make-obj [] (java.util.HashMap.))

     (defmacro expand-here
       "Expand `form` with the macro's real &env, as the compiler would."
       [form]
       (list 'quote (apply @(resolve (first form)) form &env (rest form))))

     (defn- reflective? [form]
       (boolean (some #{'clojure.lang.Reflector/setInstanceField
                        'clojure.lang.Reflector/getInstanceField}
                      (flatten form))))

     (deftest field-class-resolution-test
       (testing "a field access is direct whenever the compiler knows the class"
         (is (not (reflective? (macroexpand-1 '(setf.api/setf! (get! (setf.api/make-obj) x) 1))))
             "a backend constructor carries a return hint")
         (is (not (reflective? (macroexpand-1 '(setf.api/setf! (get! ^java.awt.Point p x) 1))))
             "a hint on the receiver form")
         (is (not (reflective? (let [p (java.awt.Point.)]
                                 (expand-here (setf.api/setf! (get! p x) 1)))))
             "a local whose type the compiler inferred")
         (is (not (reflective? ((fn [^java.awt.Point p] (expand-here (setf.api/get! p x)))
                                nil)))
             "a hinted fn parameter, on the read path too"))
       (testing "and falls back to Reflector, on both paths, when it cannot"
         (is (reflective? (macroexpand-1 '(setf.api/setf! (get! o x) 1))))
         (is (reflective? (macroexpand-1 '(setf.api/get! o x)))))
       (testing "a same-named fn from another namespace is not mistaken for a constructor"
         ;; Resolve as this file's code would: `make-obj` here is the local fn.
         (binding [*ns* (the-ns 'setf.api-test)]
           (is (= #'setf.api-test/make-obj (resolve 'make-obj)))
           (is (reflective? (macroexpand-1 '(setf.api/get! (make-obj) x)))))))))
