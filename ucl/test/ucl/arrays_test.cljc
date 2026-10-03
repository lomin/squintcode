(ns ucl.arrays-test
  "make-array, elt, length, setf/incf/decf on elt, vector-push-extend."
  (:require [ucl.test :refer [deftest is testing signals-error?]]
            [ucl.api :as ucl]))

(defn contents
  "Every element, as a vector -- the portable way to compare a host array."
  [v]
  (loop [i 0 acc []]
    (if (< i (ucl/length v))
      (recur (inc i) (conj acc (ucl/elt v i)))
      acc)))

(deftest make-array-test
  (testing "element type t, fixnum, (signed-byte 53)"
    (is (= [nil nil] (contents (ucl/make-array 2 :initial-element nil))))
    (is (= [7 7 7] (contents (ucl/make-array 3 :initial-element 7))))
    (is (= [0 0 0] (contents (ucl/make-array 3 :element-type 'fixnum))))
    (is (= [4 4] (contents (ucl/make-array 2 :element-type 'fixnum :initial-element 4))))
    (is (= [0 0] (contents (ucl/make-array 2 :element-type '(signed-byte 53))))))
  (testing "element type string"
    (let [a (ucl/make-array 2 :element-type 'string :initial-element "")]
      (ucl/setf (ucl/elt a 1) "Fizz")
      (is (= ["" "Fizz"] (contents a)))))
  (testing ":initial-contents, literal and run-time, vector and list"
    (is (= [1 2 3] (contents (ucl/make-array 3 :initial-contents [1 2 3]))))
    (is (= [1 2 3] (contents (ucl/make-array 3 :initial-contents '(1 2 3)))))
    (is (= [5 6] (contents (ucl/make-array 2 :element-type 'fixnum :initial-contents [5 6]))))
    (let [src [8 9]]
      (is (= [8 9] (contents (ucl/make-array 2 :initial-contents src))))
      (is (= [8 9] (contents (ucl/make-array 2 :element-type 'fixnum :initial-contents src))))))
  (testing "dimension"
    (is (= 0 (ucl/length (ucl/make-array 0))))
    (is (= 5 (ucl/length (ucl/make-array 5 :element-type 'fixnum))))))

(deftest elt-test
  (testing "read and write"
    (let [a (ucl/make-array 3 :initial-contents [10 20 30])]
      (is (= 20 (ucl/elt a 1)))
      (is (= 99 (ucl/setf (ucl/elt a 1) 99)) "setf returns the value")
      (is (= [10 99 30] (contents a)))))
  (testing "several pairs, assigned left to right"
    (let [a (ucl/make-array 2 :element-type 'fixnum)]
      (is (= 2 (ucl/setf (ucl/elt a 0) 1 (ucl/elt a 1) 2)))
      (is (= [1 2] (contents a)))))
  (testing "incf and decf"
    (let [a (ucl/make-array 2 :element-type 'fixnum)]
      (is (= 1 (ucl/incf (ucl/elt a 0))))
      (is (= 11 (ucl/incf (ucl/elt a 0) 10)))
      (is (= -1 (ucl/decf (ucl/elt a 1))))
      (is (= [11 -1] (contents a)))))
  (testing "elt reads the host's literal vector (D17)"
    (is (= 2 (ucl/elt [1 2 3] 1)))
    (is (= 3 (ucl/length [1 2 3]))))
  (testing "a qualified place name is the same place"
    (let [a (ucl/make-array 1 :element-type 'fixnum)]
      (ucl/setf (ucl.api/elt a 0) 5)
      (is (= 5 (ucl/elt a 0))))))

(deftest evaluation-test
  (testing "every argument is evaluated exactly once, left to right"
    (let [a     (ucl/make-array 3 :element-type 'fixnum :initial-element 1)
          trace (atom [])
          note  (fn [tag x] (swap! trace conj tag) x)]
      (ucl/incf (ucl/elt (note :array a) (note :index 1)) (note :delta 5))
      (is (= [:array :index :delta] @trace))
      (is (= 6 (ucl/elt a 1)))
      (reset! trace [])
      (ucl/setf (ucl/elt (note :array a) (note :index 2)) (note :value 9))
      (is (= [:array :index :value] @trace))
      (is (= 9 (ucl/elt a 2)))))
  (testing "generated bindings never capture the caller's names"
    (let [t (ucl/make-array 2 :element-type 'fixnum)
          v 7
          a 1]
      (ucl/setf (ucl/elt t a) v)
      (ucl/incf (ucl/elt t (- a 1)) v)
      (is (= [7 7] (contents t))))))

(deftest adjustable-test
  (let [v (ucl/make-array 0 :adjustable t :fill-pointer 0)]
    (is (= 0 (ucl/vector-push-extend :a v)) "returns the new element's index")
    (is (= 1 (ucl/vector-push-extend :b v)))
    (is (= 2 (ucl/length v)) "length is the fill pointer")
    (is (= :b (ucl/elt v 1))))
  (let [v (ucl/make-array 4 :adjustable t :fill-pointer 2 :initial-element 0)]
    (is (= 2 (ucl/length v)))
    (ucl/vector-push-extend 5 v)
    (is (= [0 0 5] (contents v)))))

(deftest safety-test
  (testing "at the default safety, errors SBCL signals are signalled (D11)"
    (let [a (ucl/make-array 2 :element-type 'fixnum)]
      (is (signals-error? (ucl/elt a 2)) "read past the end")
      (is (signals-error? (ucl/setf (ucl/elt a -1) 0)) "write before the start")
      (is (signals-error? (ucl/setf (ucl/elt a 0) 2147483648)) "store outside fixnum")
      (is (signals-error? (ucl/incf (ucl/gethash :missing (ucl/make-hash-table))))
          "(incf nil)")
      (is (signals-error? (ucl/make-array 3 :initial-contents (vec (range 2))))
          "contents shorter than the dimension"))))
