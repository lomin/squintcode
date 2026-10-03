(ns ucl.test
  "The test vocabulary every ucl suite uses, so one test file serves every
   host. On the JVM it is clojure.test."
  (:require [clojure.test]))

(defmacro deftest [& args] `(clojure.test/deftest ~@args))
(defmacro is [& args] `(clojure.test/is ~@args))
(defmacro testing [& args] `(clojure.test/testing ~@args))

(defmacro signals-error?
  "True when evaluating `body` signals an error."
  [& body]
  `(try ~@body false (catch Throwable _# true)))
