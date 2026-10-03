(ns ucl.variables-test
  (:require [ucl.test :refer [deftest is testing signals-error?]]
            [ucl.api :as ucl]
            [ucl.kernels :as k]))

(defn arr [v] (ucl/make-array (count v) :initial-contents v))

(deftest let-test
  (testing "ucl/let binds in parallel: every init sees the outer names"
    (is (= [2 1] (ucl/let ((a 1) (b 2))
                   (declare (type fixnum a b))
                   (ucl/let ((a b) (b a))
                     (declare (type fixnum a b))
                     [a b])))))
  (testing "ucl/let* binds in order"
    (is (= [1 2 4] (ucl/let* ((a 1) (b (+ a 1)) (c (* b 2)))
                     (declare (type fixnum a b c))
                     [a b c]))))
  (testing "a variable without an init starts as nil"
    (is (= [nil nil] (ucl/let (x (y)) [x y]))))
  (testing "inits run left to right, compound or not"
    (let [log (atom [])]
      (ucl/let ((a (do (swap! log conj :a) 1))
                (b (swap! log conj :b))
                (c (loop [i 0] (if (< i 2) (recur (inc i)) (do (swap! log conj :c) i))))
                (d (swap! log conj :d)))
        (is (= [:a :b :c :d] @log))
        (is (== 2 c))))))

(deftest assignment-test
  (testing "setf, incf and decf assign a variable and return the new value"
    (ucl/let ((x 1))
      (declare (type fixnum x))
      (is (== 5 (ucl/setf x 5)))
      (is (== 6 (ucl/incf x)))
      (is (== 10 (ucl/incf x 4)))
      (is (== 7 (ucl/decf x 3)))
      (is (== 7 x))))
  (testing "setf of several places, variables among them, in order"
    (ucl/let ((x 0) (v (ucl/make-array 4 :initial-element 0)))
      (declare (type fixnum x) (type simple-vector v))
      (ucl/setf x 3 (ucl/elt v x) 9)
      (is (== 9 (ucl/elt v 3)) "x is 3 when the second place is evaluated")))
  (testing "a Clojure local shadows a variable"
    (ucl/let ((x 1))
      (declare (type fixnum x))
      (is (== 10 (let [x 10] x)))
      (let [y x]
        (ucl/incf x)
        (is (= [2 1] [x y])))))
  (testing "a closure sees later assignments and can make them"
    (ucl/let ((x 1))
      (declare (type fixnum x))
      (let [get-x (fn [] x)
            bump! (fn [] (ucl/incf x 10))]
        (ucl/setf x 2)
        (is (== 2 (get-x)))
        (bump!)
        (is (== 12 x)))))
  (testing "a variable bound inside a loop is fresh on every iteration"
    (is (= [10 11 12]
           (loop [i 0 acc []]
             (if (< i 3)
               (ucl/let ((x i))
                 (declare (type fixnum x))
                 (ucl/incf x 10)
                 (recur (inc i) (conj acc x)))
               acc))))))

(deftest compound-value-test
  (testing "a loop, if, when, cond, case, do or let as the init or the new value"
    (ucl/let ((a (loop [j 0] (if (< j 4) (recur (inc j)) j)))
              (b (if (> 2 1) (loop [j 10] (if (< j 12) (recur (inc j)) j)) 0))
              (c (when false 1))
              (d (cond (< 2 1) :no (< 1 2) :yes :else :never))
              (e (case 2 1 :one 2 :two :other))
              (f (do 1 2 3))
              (g (let [z 5] (* z z)))
              (h (ucl/dotimes (i 3 (* i 100)))))
      (is (= [4 12 nil :yes :two 3 25 300] [a b c d e f g h]))
      (ucl/setf a (loop [j 0] (if (< j 7) (recur (inc j)) j)))
      (is (== 7 a))
      (is (== 9 (ucl/setf a (if (> a 1) 9 0))) "setf returns the value")))
  (testing "a compound init sees the outer variable of the same name"
    (ucl/let ((x 1))
      (declare (type fixnum x))
      (ucl/let ((x (loop [j x] (if (< j 5) (recur (inc j)) j))))
        (declare (type fixnum x))
        (is (== 5 x)))
      (is (== 1 x)))))

(deftest dotimes-test
  (testing "the count is evaluated once; the result sees var = count"
    (let [calls (atom 0)]
      (ucl/let ((s 0))
        (declare (type fixnum s))
        (is (== 4 (ucl/dotimes (i (do (swap! calls inc) 4) i) (ucl/incf s i))))
        (is (== 6 s))
        (is (== 1 @calls)))))
  (testing "no result is nil; a count of zero or less runs nothing"
    (is (nil? (ucl/dotimes (i 2))))
    (ucl/let ((s 0))
      (declare (type fixnum s))
      (ucl/dotimes (i 0) (ucl/incf s))
      (ucl/dotimes (i -3) (ucl/incf s))
      (is (== 0 s))))
  (testing "nested"
    (ucl/let ((s 0))
      (declare (type fixnum s))
      (ucl/dotimes (i 3) (ucl/dotimes (j 4) (ucl/incf s (* i j))))
      (is (== 18 s)))))

(ucl/defstruct (Counter (:constructor Counter (n)))
  (n 0 :type fixnum))

(ucl/defmethod advance ((c Counter) by)
  (declare (type fixnum by))
  (ucl/setf by (* by 2))
  (ucl/incf (ucl/slot-value c 'n) by))

(deftest parameter-test
  (testing "a defun parameter the body assigns"
    (is (== 15 (k/digit-sum 12345))))
  (testing "a defmethod parameter the body assigns"
    (is (== 6 (advance (Counter 0) 3)))))

(deftest kernels-test
  (testing "LeetCode 2762 written with variables"
    (is (== 8 (k/count-steady-stretches (arr [5 4 2 4]) 2)))
    (is (== 6 (k/count-steady-stretches (arr [1 2 3]) 2)))
    (is (== 7 (k/count-steady-stretches (arr [30 50 40 80]) 20)))
    (is (== 0 (k/count-steady-stretches (arr []) 5)))
    (is (== 20000100000 (k/count-steady-stretches (ucl/make-array 200000 :initial-element 7) 0))))
  (testing "a loop's value bound in ucl/let"
    (is (== 2 (k/first-index-at-least (arr [1 3 5 7]) 4)))
    (is (== -1 (k/first-index-at-least (arr [1 3]) 4)))))

(deftest safety-test
  (testing "a store outside a declared type signals at safety 1"
    (ucl/let ((x 0) (y 0))
      (declare (type fixnum x) (type (signed-byte 53) y))
      (is (signals-error? (ucl/setf x 1.5)) "not an integer")
      (is (signals-error? (ucl/setf x 2147483648)) "past most-positive-fixnum")
      (ucl/setf x 2147483647)
      (is (signals-error? (ucl/incf x)) "incf past most-positive-fixnum")
      (is (== 1099511627776 (ucl/setf y 1099511627776)) "2^40 is a (signed-byte 53)")
      (is (signals-error? (ucl/let ((z (identity 0.5))) (declare (type fixnum z)) z))
          "the init is checked too"))))
