(ns ucl-example.fizzbuzz
  "src/squintcode/fizzbuzz.cljc, rewritten against ucl. One plain require; no
   reader conditionals anywhere in this file."
  (:require [ucl.api :as ucl]))

(defn fizz-buzz-pred [a b]
  (zero? (mod b a)))

(ucl/defun fizzBuzz (n)
  (declare (type fixnum n))
  (let [answer (ucl/make-array n)]
    (loop [i 0]
      (if (< i n)
        (let [x (inc i)]
          (ucl/setf (ucl/elt answer i)
                    (condp fizz-buzz-pred x
                      15 "FizzBuzz"
                      3  "Fizz"
                      5  "Buzz"
                      (str x)))
          (recur (inc i)))
        answer))))

(defn typed-roundtrip
  "make-array :element-type 'fixnum, written and read through elt."
  []
  (let [a (ucl/make-array 3 :element-type 'fixnum)]
    (ucl/setf (ucl/elt a 1) 7)
    (+ (ucl/elt a 0) (ucl/elt a 1))))

(defn -main []
  (println "fizzbuzz:" (apply str (interpose "," (fizzBuzz 15))))
  (println "typed:" (typed-roundtrip)))
