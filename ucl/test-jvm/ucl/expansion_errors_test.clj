(ns ucl.expansion-errors-test
  "Errors ucl signals while expanding a form. The contract is shared code, so
   testing its errors on one host covers all of them; the JVM is the host
   where a test can expand a form at run time."
  (:require [clojure.test :refer [deftest is testing]]
            [ucl.api :as ucl]))

(defn- expansion-error
  "The ucl message `form` fails to expand with, or nil if it compiles."
  [form]
  (try
    (binding [*ns* (the-ns 'ucl.expansion-errors-test)]
      (eval form))
    nil
    (catch Throwable t
      (loop [e t]
        (cond (nil? e) (str "not a ucl error: " t)
              (and (instance? clojure.lang.ExceptionInfo e) (= 'ucl (:library (ex-data e)))) (ex-message e)
              :else (recur (.getCause e)))))))

(defmacro ^:private signals? [re form]
  `(is (re-find ~re (str (expansion-error '~form)))))

(deftest assigning-a-local-test
  (testing "only variables can be assigned"
    (signals? #"setf of `y`: it is a local" (let [y 1] (ucl/incf y)))
    (signals? #"setf of `x`: it is a local" (ucl/let ((x 1)) (let [x 2] (ucl/setf x 3))))
    (signals? #"setf of `i`: it is a local" (ucl/dotimes (i 3) (ucl/setf i 1)))
    (signals? #"setf of `n`: it is a local" (ucl/defun shadowed-param (n) (let [n 1] (ucl/setf n 2)))))
  (testing "a variable and an assigned parameter compile"
    (is (nil? (expansion-error '(ucl/let ((x 1)) (declare (type fixnum x)) (ucl/setf x 3)))))
    (is (nil? (expansion-error '(ucl/defun assigned-param (n) (declare (type fixnum n)) (ucl/incf n)))))))

(deftest malformed-test
  (signals? #"starts as nil" (ucl/let ((x)) (declare (type fixnum x)) x))
  (signals? #"starts as nil" (ucl/let* (x) (declare (type (signed-byte 53) x)) x))
  (signals? #"takes a list of bindings" (ucl/let [x 1] x))
  (signals? #"malformed binding" (ucl/let ((x 1 2)) x))
  (signals? #"dotimes takes \(dotimes \(var count" (ucl/dotimes [i 3] i)))

(deftest exits-test
  (testing "an exit compiles only in statement or return position (D55)"
    (signals? #"\(return-from b \.\.\.\) is in an argument of \(\+ \.\.\.\)"
              (ucl/block b (+ 1 (ucl/return-from b 2))))
    (signals? #"\(return-from b \.\.\.\) is in the test of if"
              (ucl/block b (if (ucl/return-from b true) 1 2)))
    (signals? #"\(return \.\.\.\) is in a binding of let"
              (ucl/dotimes (i 3) (let [x (ucl/return 1)] x))))
  (testing "an exit cannot leave a function"
    (signals? #"\(return-from b \.\.\.\) is inside \(fn \.\.\.\)"
              (ucl/block b (mapv (fn [x] (ucl/return-from b x)) [1])))
    (signals? #"\(return \.\.\.\) is inside \(fn \.\.\.\)"
              (ucl/dotimes (i 3) (run! (fn [x] (when x (ucl/return))) [i]))))
  (testing "an exit to a block inside the fn compiles"
    (is (nil? (expansion-error '(fn [] (ucl/block b (when true (ucl/return-from b 1)) 2))))))
  (testing "an exit with no block"
    (signals? #"\(return \.\.\.\) has no enclosing block named nil" (ucl/return 1))
    (signals? #"\(return-from nowhere \.\.\.\) has no enclosing block named nowhere"
              (ucl/block b (ucl/return-from nowhere 1)))))

(deftest loop-rejections-test
  (testing "clauses that need a data type ucl lacks (D53) name the alternative"
    (signals? #"for x in iterates a list; ucl has no lists \(D53\)\. Use for x across"
              (ucl/loop for x in xs sum x))
    (signals? #"collect builds a list; ucl has no lists \(D53\)"
              (ucl/loop for x across [1] collect x))
    (signals? #"destructuring \(\(a b\)\) needs conses"
              (ucl/loop for (a b) across [1] sum a)))
  (testing "the grammar"
    (signals? #"a for clause after a main clause" (ucl/loop do (f) for x across [1]))
    (signals? #"unknown clause foo" (ucl/loop for x across [1] foo x))
    (signals? #"counting down needs a start" (ucl/loop for i downto 0 sum i))
    (signals? #"a for name is bound twice" (ucl/loop for i below 3 for i below 4 sum i)))
  (testing "one default accumulator, of one kind"
    (signals? #"sum/count and maximize/minimize accumulate into one value"
              (ucl/loop for x across [1] sum x maximize x))
    (signals? #"maximize of-type \(signed-byte 53\): SBCL signals a type error"
              (ucl/loop for x across [1] maximize x of-type (signed-byte 53)))
    (signals? #"maximize and minimize into one fixnum accumulator"
              (ucl/loop for x across [1] maximize x into m of-type fixnum minimize x into m))
    (signals? #"always, never and thereis decide the loop's value"
              (ucl/loop for x across [1] sum x always x)))
  (testing "not built yet (§13)"
    (signals? #"loop-finish is not built yet" (ucl/loop for x across [1] loop-finish))
    (signals? #"loop-finish is not built yet" (ucl/loop for x across [1] do (when x (loop-finish))))
    (signals? #"package iteration needs packages, which ucl does not have \(D53\)"
              (ucl/loop for s being the symbols of p count s))
    (signals? #"for k being the hash-keys: expected in or of"
              (ucl/loop for k being the hash-keys h sum k))
    (signals? #"expected using \(hash-value var\)"
              (ucl/loop for k being the hash-keys of h using (hash-key v) sum k))
    (signals? #"parallel stepping\) is not built yet"
              (ucl/loop for x across [1] and y across [2] sum x))))
(deftest sequence-function-keys-test
  (testing "a key a sequence function does not take"
    (signals? #"count-if does not take :bad" (ucl/count-if odd? [1] :bad 1))
    (signals? #"count-if does not take :bad \(:allow-other-keys is nil\)"
              (ucl/count-if odd? [1] :allow-other-keys nil :bad 1))
    (signals? #"only known at run time" (let [a true] (ucl/count-if odd? [1] :bad 1 :allow-other-keys a))))
  (testing ":test and :test-not together"
    (signals? #"takes :test or :test-not, not both" (ucl/count 1 [1] :test = :test-not =)))
  (testing "a malformed call names what to write"
    (signals? #"malformed position" (ucl/position 1 [1] :start))
    (signals? #"malformed every" (ucl/every odd?)))
  (testing "a result type: quoted, and a vector type"
    (signals? #"must be quoted" (let [t 'vector] (ucl/map t inc [1])))
    (signals? #"not a vector type" (ucl/concatenate 'list [1]))
    (signals? #"nil is not a sequence type" (ucl/make-sequence nil 2)))
  (testing "subseq as a place"
    (signals? #"takes 2 or 3 arguments" (let [v [1]] (ucl/setf (ucl/subseq v) v)))))
