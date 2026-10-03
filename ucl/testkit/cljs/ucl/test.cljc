(ns ucl.test
  "The test vocabulary every ucl suite uses. On ClojureScript it is cljs.test;
   this is the macro half.")

(defmacro deftest [& args] `(cljs.test/deftest ~@args))
(defmacro is [& args] `(cljs.test/is ~@args))
(defmacro testing [& args] `(cljs.test/testing ~@args))

(defmacro signals-error?
  "True when evaluating `body` signals an error."
  [& body]
  `(try ~@body false (catch :default _# true)))
