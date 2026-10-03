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
