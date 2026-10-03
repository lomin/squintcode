(ns ucl.sequences-test
  (:require [ucl.test :refer [deftest is testing signals-error?]]
            [ucl.api :as ucl]
            [ucl.kernels :as k]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

(ucl/defun count-above (nums k)
  (declare (type fixnum-vector nums) (type fixnum k))
  (ucl/count-if (fn [x] (> x k)) nums))

(deftest count-test
  (testing "count-if, count-if-not, count"
    (is (== 3 (count-above (arr [1 5 9 3 7]) 4)))
    (is (== 2 (ucl/count-if-not odd? (arr [1 2 3 4]))))
    (is (== 2 (ucl/count 3 (arr [3 1 3]))))
    (is (== 0 (ucl/count-if odd? (arr [])))))
  (testing ":start and :end, nil :end meaning the length"
    (is (== 1 (ucl/count-if-not odd? (arr [1 2 3 4]) :start 1 :end 3)))
    (is (== 2 (ucl/count-if odd? (arr [1 2 3 4]) :end nil)))
    (let [e nil] (is (== 2 (ucl/count-if odd? (arr [1 2 3 4]) :end e)))))
  (testing ":key, :test, :test-not"
    (is (== 2 (ucl/count-if odd? (arr [1 2 3 4]) :key (fn [x] (+ x 1)))))
    (is (== 1 (ucl/count 2 (arr [1 2 3]) :test <)))
    (is (== 2 (ucl/count 2 (arr [1 2 3]) :test-not ==))))
  (testing ":from-end changes only the order the test sees the elements in"
    (let [seen (atom [])]
      (is (== 2 (ucl/count-if (fn [x] (swap! seen conj x) (odd? x)) (arr [1 2 3]) :from-end true)))
      (is (= [3 2 1] @seen)))))

(deftest find-and-position-test
  (testing "the first match, or nil"
    (is (== 5 (ucl/find-if odd? (arr [2 4 5 7]))))
    (is (nil? (ucl/find-if odd? (arr [2 4]))))
    (is (== 2 (ucl/position-if odd? (arr [2 4 5 7]))))
    (is (nil? (ucl/position 9 (arr [1 2 3]))))
    (is (== 2 (ucl/find-if-not odd? (arr [1 3 2 4]))))
    (is (== 1 (ucl/position-if-not odd? (arr [1 2 3])))))
  (testing ":from-end finds the last match"
    (is (== 3 (ucl/position 3 (arr [1 3 5 3]) :from-end true)))
    (is (== 7 (ucl/find-if odd? (arr [2 5 7 4]) :from-end true)))
    (let [fe true] (is (== 2 (ucl/position-if odd? (arr [1 2 3]) :from-end fe)))))
  (testing ":start, :end and :key"
    (is (== 2 (ucl/position-if (fn [x] (> x 1)) (arr [5 1 2 3]) :start 1)))
    (is (nil? (ucl/position-if odd? (arr [2 2 3]) :end 2)))
    (is (== 1 (ucl/position 4 (arr [1 2 3]) :key (fn [x] (* 2 x))))))
  (testing "eql compares strings by value (D52)"
    (let [v (ucl/make-array 3 :initial-contents ["a" "b" (ucl/princ-to-string 7)])]
      (is (== 2 (ucl/position "7" v)))
      (is (== 1 (ucl/count "b" v))))))

(deftest quantifier-test
  (testing "every, some, notany, notevery"
    (is (true? (ucl/every odd? (arr [1 3]))))
    (is (nil? (ucl/every odd? (arr [1 2]))))
    (is (true? (ucl/every odd? (arr []))))
    (is (== 30 (ucl/some (fn [x] (when (> x 2) (* 10 x))) (arr [1 2 3 4]))))
    (is (nil? (ucl/some odd? (arr [2 4]))))
    (is (true? (ucl/notany odd? (arr [2 4]))))
    (is (nil? (ucl/notany odd? (arr [2 3]))))
    (is (true? (ucl/notevery odd? (arr [1 2]))))
    (is (nil? (ucl/notevery odd? (arr [1 3])))))
  (testing "several sequences: as far as the shortest"
    (is (true? (ucl/every < (arr [1 2]) (arr [3 4 0]))))
    (is (== 2 (ucl/some (fn [a b] (when (> a b) a)) (arr [0 2 9]) (arr [1 1]))))))

(deftest reduce-test
  (testing "with and without :initial-value"
    (is (== 9 (ucl/reduce ucl/max (arr [3 9 2]))))
    (is (== 16 (ucl/reduce + (arr [1 2 3]) :initial-value 10)))
    (is (== 7 (ucl/reduce + (arr [7]))))
    (is (== 0 (ucl/reduce + (arr []))))
    (is (== 5 (ucl/reduce + (arr []) :initial-value 5))))
  (testing "an empty range calls the function with no arguments"
    (is (== -1 (ucl/reduce (fn ([] -1) ([a b] (+ a b))) (arr []))))
    (is (signals-error? (ucl/reduce (fn [a b] (+ a b)) (arr [])))))
  (testing ":from-end folds from the right"
    (is (== 2 (ucl/reduce - (arr [1 2 3]) :from-end true)))      ; 1 - (2 - 3)
    (is (== -4 (ucl/reduce - (arr [1 2 3])))))                    ; (1 - 2) - 3
  (testing ":key, :start, :end"
    (is (== 13 (ucl/reduce + (arr [1 2 3]) :key (fn [x] (* x x)) :start 1)))
    (is (== 3 (ucl/reduce + (arr [1 2 3 4]) :end 2)))))

(ucl/defun outer-threshold (nums x)
  (declare (type fixnum-vector nums) (type fixnum x))
  ;; the key's parameter and the predicate's free variable are both named x
  (ucl/count-if (fn [y] (> y x)) nums :key (fn [x] (* 2 x))))

(deftest function-arguments-test
  (testing "a literal fn's body is written into the loop, whatever it ends in"
    (is (== 2 (ucl/count-if (fn [x] (let [d (- x 2)] (pos? d))) (arr [1 3 5]))))
    (is (== 2 (ucl/count-if (fn [x] (do (odd? x))) (arr [1 3 4]))))
    (is (== 2 (ucl/count-if #(odd? %) (arr [1 3 4]))))
    (is (== 1 (ucl/count-if (fn named [x] (== x 3)) (arr [1 3 4]))))
    (is (== 4 (ucl/reduce (fn [acc [a]] (+ acc a)) (ucl/make-array 2 :initial-contents [[1] [3]])
                          :initial-value 0))))
  (testing "names a body binds do not capture the code it continues into"
    (is (== 2 (outer-threshold (arr [1 2 3]) 3))))
  (testing "any other form is evaluated once, before the loop"
    (let [made (atom 0)
          pred (fn [] (swap! made inc) odd?)]
      (is (== 2 (ucl/count-if (pred) (arr [1 2 3]))))
      (is (== 1 @made))))
  (testing "arguments are evaluated left to right, each once"
    (let [log (atom [])
          note (fn [x v] (swap! log conj x) v)]
      (is (== 1 (ucl/position (note :item 2) (note :seq (arr [1 2 3])) :end (note :end 3) :start (note :start 0))))
      (is (= [:item :seq :end :start] @log)))))

(defn fill-pointered [v]
  (let [a (ucl/make-array 0 :adjustable true :fill-pointer 0)]
    (doseq [x v] (ucl/vector-push-extend x a))
    a))

(deftest keyword-arguments-test
  (testing "other keys are allowed by a true :allow-other-keys, the leftmost one (CLHS 3.4.1.4.1)"
    (is (== 2 (ucl/count-if odd? (arr [1 2 3]) :bad 1 :allow-other-keys true)))
    (is (== 2 (ucl/count-if odd? (arr [1 2 3]) :allow-other-keys true :bad 1 :allow-other-keys nil)))
    (is (== 2 (ucl/count-if odd? (arr [1 2 3]) :allow-other-keys nil)))
    (is (== 6 (ucl/reduce + (arr [1 2 3]) :also-bad 0 :allow-other-keys 7))))
  (testing "every argument is evaluated, a repeated key and an other key included"
    (let [log (atom [])
          note (fn [x v] (swap! log conj x) v)]
      (is (== 2 (ucl/count-if odd? (arr [1 2 3]) :start (note :s1 0) :start (note :s2 2)
                              :bad (note :bad 0) :allow-other-keys true)))
      (is (= [:s1 :s2 :bad] @log))))
  (testing "the order of evaluation, with :key and :from-end (ansi-test count-if.order.1)"
    (let [log (atom [])
          note (fn [x v] (swap! log conj x) v)]
      (is (== 1 (ucl/count-if (note :pred odd?) (note :seq (arr [1 2 3 4])) :start (note :start 0)
                              :end (note :end 2) :key (note :key (fn [x] x)) :from-end (note :from-end false))))
      (is (= [:pred :seq :start :end :key :from-end] @log))))
  (testing "a quoted symbol and #' name the global function (CLHS 1.4.1.5)"
    (is (== 2 (ucl/count-if 'odd? (arr [1 2 3]))))
    (is (== 2 (ucl/count-if #'odd? (arr [1 2 3]))))
    (is (== 1 (ucl/position 3 (arr [1 2 3]) :key 'inc))))
  (testing ":key nil is identity (CLHS 17.2.1)"
    (is (== 2 (ucl/count-if odd? (arr [1 2 3]) :key nil)))
    (is (== 6 (ucl/reduce + (arr [1 2 3]) :key nil)))))

(deftest fill-pointer-test
  (testing "a vector with a fill pointer is as long as its fill pointer"
    (let [v (fill-pointered [1 2 3 4])]
      (is (== 2 (ucl/count-if odd? v)))
      (is (== 3 (ucl/position-if even? v :from-end true)))
      (is (== 10 (ucl/reduce + v)))
      (is (true? (ucl/every pos? v))))))

(deftest curried-form-test
  (testing "a sequence function short of its sequence is a function of it (D48)"
    (is (== 2 ((ucl/count-if odd?) (arr [1 3 4]))))
    (is (== 1 ((ucl/position 3) (arr [1 3]))))
    (is (== 1 ((ucl/count-if odd? :start 1) (arr [1 3 4])))))
  (testing "and is inlined where a sequence function takes a function"
    (let [rows (ucl/make-array 2 :initial-contents [(arr [1 2]) (arr [3])])]
      (is (true? (ucl/every (ucl/count-if odd?) rows)))
      (is (== 1 (ucl/position-if (ucl/find 3) rows))))))

(ucl/defun first-row-with-odd (rows)
  (declare (type simple-vector rows))
  (ucl/dotimes (i (ucl/length rows) -1)
    (when (ucl/find-if odd? (ucl/elt rows i))
      (ucl/return i))))

(deftest positions-test
  (testing "a ucl/let init and a setf of a variable run as a statement (D51)"
    (ucl/let ((n (ucl/count-if odd? (arr [1 2 3]))))
      (declare (type fixnum n))
      (is (== 2 n))
      (ucl/setf n (ucl/reduce + (arr [1 2 3])))
      (is (== 6 n))))
  (testing "an exit passes through a generated loop to its block"
    (is (== 1 (first-row-with-odd (ucl/make-array 2 :initial-contents [(arr [2]) (arr [4 5])]))))
    (is (== -1 (first-row-with-odd (ucl/make-array 1 :initial-contents [(arr [2])]))))))

(deftest kernels-test
  (is (== 3 (k/count-greater (arr [1 5 9 3 7]) 4)))
  (is (== 9 (k/largest (arr [3 9 2]))))
  (is (== 2 (k/first-odd-index (arr [2 4 5 7]))))
  (is (== -1 (k/first-odd-index (arr [2 4]))))
  (is (true? (k/all-in-range? (arr [1 2 3]) 0 3)))
  (is (== 4 (k/window-odd-counts (arr [1 2 3 5]) 2))))
