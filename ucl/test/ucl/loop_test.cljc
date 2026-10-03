(ns ucl.loop-test
  (:require [ucl.test :refer [deftest is testing]]
            [ucl.api :as ucl]
            [ucl.kernels :as k]))

(defn arr [v] (ucl/make-array (count v) :element-type 'fixnum :initial-contents v))

;; Every (is (= expected form)) in a deftest named *-conformance-test also
;; runs on SBCL and ECL (ucl/conformance.clj, D60): the forms are written in
;; the subset that translates to Common Lisp.

(deftest accumulation-conformance-test
  (testing "sum, count, maximize, minimize"
    (is (= 6 (ucl/loop for x across [1 2 3] sum x)))
    (is (= 2 (ucl/loop for x across [1 2 3 4] count (even? x))))
    (is (= 9 (ucl/loop for x across [3 9 2] maximize x)))
    (is (= 2 (ucl/loop for x across [3 9 2] minimize x)))
    (is (= -4 (ucl/loop for x across [3 -4 2] minimize x))))
  (testing "nothing accumulated"
    (is (= 0 (ucl/loop for x across [] sum x)))
    (is (= 0 (ucl/loop for x across [] count x)))
    (is (= 0 (ucl/loop for x across [] maximize x))))
  (testing "a fixnum maximize or minimize starts at the type's far end (H65)"
    (is (= true (== ucl/most-negative-fixnum (ucl/loop for x across [] maximize x of-type fixnum))))
    (is (= true (== ucl/most-positive-fixnum (ucl/loop for x across [] minimize x of-type fixnum))))
    (is (= true (== ucl/most-negative-fixnum (ucl/loop for x across [1 2] when (> x 5) maximize x of-type fixnum))))
    (is (= true (== ucl/most-negative-fixnum (ucl/loop for x across [] maximize x into m of-type fixnum finally (return m)))))
    (is (= -9 (ucl/loop for x across [3 -9 2] minimize x of-type fixnum)))
    (is (= -2 (ucl/loop for x across [-5 -2 -7] maximize x of-type fixnum)))
    (is (= 3 (ucl/loop for x across [3 9 2] while (< x 5) maximize x of-type fixnum)))
    (is (= 2 (ucl/loop for x across [1 2 3] maximize x of-type fixnum while (< x 2)))))
  (testing "untyped, it starts at 0 and takes the first value"
    (is (= 0 (ucl/loop for x across [] maximize x into m finally (return m))))
    (is (= -2 (ucl/loop for x across [-5 -2 -7] maximize x))))
  (testing "into a variable: no default value, but finally reads it"
    (is (= nil (ucl/loop for x across [1 2] sum x into s)))
    (is (= 3 (ucl/loop for x across [1 2] sum x into s finally (return s))))
    (is (= 5 (ucl/loop for p across [7 1 5 3 6 4]
                       minimize p into lo of-type fixnum
                       maximize (- p lo))))
    (is (= 0 (ucl/loop for p across [7 6 4 3 1]
                       minimize p into lo of-type fixnum
                       maximize (- p lo)))))
)

(deftest shared-accumulator-test
  ;; CLHS 6.1.3 lets sum and count share the default accumulator, and SBCL
  ;; does; ECL refuses it (H63), so it is no conformance case.
  (testing "sum and count share the default accumulator"
    (is (= 23 (ucl/loop for x across [1 2 3] sum x count (> x 1) sum 5)))
    (is (= 13 (ucl/loop for x across [1 2 3 4]
                        when (> x 1) sum x and count (> x 0) end
                        count (> x 3))))))

(deftest for-conformance-test
  (testing "arithmetic"
    (is (= 10 (ucl/loop for i from 1 to 4 sum i)))
    (is (= 6 (ucl/loop for i below 4 sum i)))
    (is (= 6 (ucl/loop for i from 0 upto 3 sum i)))
    (is (= 4 (ucl/loop for i from 10 downto 1 by 3 count (> i 0))))
    (is (= 21 (ucl/loop for i from 10 above 1 by 3 sum i)))
    (is (= 12 (ucl/loop for i downfrom 5 to 3 sum i)))
    (is (= 0 (ucl/loop for i from 5 below 3 count (> i 0))))
    (is (= 3 (ucl/loop for i upfrom 1 repeat 3 count (> i 0)))))
  (testing "the limit, step and vector are evaluated once"
    (is (= 6 (ucl/let ((n 3)) (ucl/loop for i from 1 to n do (ucl/setf n 10) sum i))))
    (is (= 6 (ucl/let ((v [1 2 3])) (ucl/loop for x across v do (ucl/setf v [9 9 9 9]) sum x)))))
  (testing "= and = then"
    (is (= 32 (ucl/loop for x = 1 then (* x 2) repeat 5 finally (return x))))
    (is (= 15 (ucl/loop for i from 1 to 5 for sq = (* i i) sum (- sq (* i (- i 1)))))))
  (testing "a later for sees the earlier one, in order"
    (is (= 6 (ucl/loop for i from 0 below 4 for j from i below 10 sum j))))
  (testing "across"
    (is (= 12 (ucl/loop for x across [3 4 5] sum x)))
    (is (= 3 (ucl/loop for x across [1 2 3] for i from 0 sum i))))
  (testing "finally sees the values the end test saw"
    (is (= 3 (ucl/loop for i below 3 finally (return i))))
    (is (= [4 30] (ucl/loop for i from 1 to 3 for j = (* i 10) finally (return [i j]))))
    (is (= [2 11] (ucl/loop for x across [1 2] for i from 10 below 11 finally (return [x i]))))))

(deftest termination-conformance-test
  (testing "while, until, repeat"
    (is (= 6 (ucl/loop for x across [1 2 3 -1 5] while (> x 0) sum x)))
    (is (= 6 (ucl/loop for x across [1 2 3 -1 5] until (< x 0) sum x)))
    (is (= 6 (ucl/loop repeat 3 sum 2)))
    (is (= 0 (ucl/loop repeat 0 sum 2))))
  (testing "always, never, thereis"
    (is (= true (ucl/loop for x across [1 2 3] always (> x 0))))
    (is (= nil (ucl/loop for x across [1 -2 3] always (> x 0))))
    (is (= true (ucl/loop for x across [1 2 3] never (< x 0))))
    (is (= nil (ucl/loop for x across [1 -2 3] never (< x 0))))
    (is (= 8 (ucl/loop for x across [3 8 1] thereis (if (> x 5) x nil))))
    (is (= nil (ucl/loop for x across [3 4 1] thereis (if (> x 5) x nil)))))
  (testing "return clause and return form"
    (is (= 2 (ucl/loop for x across [5 7 9] for i from 0 when (> x 8) return i)))
    (is (= true (ucl/loop for x across [5 7 9] when (> x 8) return it)))
    (is (= 9 (ucl/loop for x across [5 7 9] when (if (> x 8) x nil) return it)))
    (is (= 1 (ucl/loop for x across [5 7 9] for i from 0 do (when (== x 7) (return i)))))
    (is (= nil (ucl/loop for x across [5 7 9] when (> x 10) return x)))))

(deftest conditional-conformance-test
  (testing "when, unless, else, end, and"
    (is (= 406 (ucl/loop for x across [1 2 3 4] when (even? x) sum x else sum (* 100 x))))
    (is (= 2 (ucl/loop for x across [1 2 3 4] unless (even? x) count (> x 0))))
    (is (= 406 (ucl/loop for x across [1 2 3 4] unless (even? x) sum (* 100 x) else sum x)))
    (is (= 0 (ucl/loop for x across [1 2 3 4] sum x finally (return 0)))))
  (testing "nested conditionals"
    (is (= 4002 (ucl/loop for x across [1 2 3 4]
                          when (even? x)
                            if (> x 3) sum (* 1000 x) else sum x
                          end)))
    (is (= 40 (ucl/loop for x across [1 2 3 4]
                        unless (even? x)
                          if (> x 2) sum (* 10 x) else sum (* 10 x)
                        end
                        when (> x 100) sum 1000))))
  (testing "it is the test's value"
    (is (= 5 (ucl/loop for x across [1 2 3] when (if (> x 1) x nil) sum it)))))

(deftest variables-conformance-test
  (testing "with, sequential and in parallel"
    (is (= 3 (ucl/loop with a = 1 with b = (+ a 1) repeat 1 sum (+ a b))))
    (is (= 11 (ucl/loop with a = 1 and b = 10 repeat 1 sum (+ a b)))))
  (testing "a with variable can be assigned"
    (is (= 6 (ucl/loop with total = 0 for x across [1 2 3] do (ucl/incf total x)
                       finally (return total)))))
  (testing "initially and finally"
    (is (= 16 (ucl/loop with s = 0
                        initially (ucl/setf s 10)
                        for x across [1 2 3] do (ucl/incf s x)
                        finally (return s)))))
  (testing "named, and return-from it out of an inner loop"
    (is (= 5 (ucl/loop named outer for i from 1 to 3
                       do (ucl/loop for j from 1 to 3
                                    do (when (== 5 (+ i j)) (ucl/return-from outer (+ i j)))))))))

(deftest hash-table-conformance-test
  ;; the order of iteration is unspecified (D58): each case gives the same
  ;; value in any order
  (testing "hash-keys and hash-values, each or the, in or of"
    (is (= 6 (ucl/loop for k being the hash-keys of (ucl/make-hash-table :initial-contents {1 10 2 20 3 30}) sum k)))
    (is (= 60 (ucl/loop for v being each hash-value in (ucl/make-hash-table :initial-contents {1 10 2 20 3 30}) sum v)))
    (is (= 0 (ucl/loop for k being the hash-keys of (ucl/make-hash-table) count true))))
  (testing "using the other half of the entry"
    (is (= 140 (ucl/loop for k being the hash-keys of (ucl/make-hash-table :initial-contents {1 10 2 20 3 30})
                         using (hash-value v) sum (* k v))))
    (is (= 140 (ucl/loop for v being the hash-values of (ucl/make-hash-table :initial-contents {1 10 2 20 3 30})
                         using (hash-key k) sum (* k v)))))
  (testing "keys come back as they went in"
    (is (= 2 (ucl/loop for k being the hash-keys of (ucl/make-hash-table :initial-contents {:a 1 :b 2})
                       count (or (= k :a) (= k :b)))))
    (is (= 3 (ucl/let ((h (ucl/make-hash-table)))
               (ucl/dotimes (i 3) (ucl/setf (ucl/gethash i h) (* i i)))
               (ucl/loop for k being the hash-keys of h using (hash-value v) count (== v (* k k)))))))
  (testing "beside other clauses"
    (is (= 2 (ucl/loop for k being the hash-keys of (ucl/make-hash-table :initial-contents {1 10 2 20 3 30})
                       for i from 0 maximize i)))
    (is (= true (ucl/loop for v being the hash-values of (ucl/make-hash-table :initial-contents {1 10 2 20})
                          always (> v 5))))))

(deftest parallel-for-conformance-test
  (testing "for ... and ...: initialized and stepped in parallel"
    (is (= 45 (ucl/loop for x = 1 then y and y = 2 then x repeat 3 sum (* 10 x) sum y)))
    (is (= 103 (ucl/loop for i from 1 to 3 and j = 100 then i sum j)))
    ;; the = clause's first value sees the group's counter, as SBCL and ECL (H67)
    (is (= 1 (ucl/let ((i 20)) (ucl/loop for i from 1 to 3 and j = i then 0 sum j))))
    (is (= 3 (ucl/let ((i 20)) (ucl/loop for i from 1 to 3 and j = 0 then i sum j)))))
  (testing "an iterator's end test before the variables, the limits after (H67)"
    (is (= [3 12] (ucl/loop for x across [1 2 3] and i from 10 to 11 finally (return [x i]))))
    (is (= [3 12] (ucl/loop for i from 10 to 11 and x across [1 2 3] finally (return [x i]))))
    (is (= [2 6] (ucl/loop for x across [1 2] and y across [5 6 7] finally (return [x y]))))
    (is (= 0 (ucl/loop for x across [] and i from 0 count true)))))

(deftest loop-finish-conformance-test
  (testing "loop-finish ends the iteration; the epilogue runs"
    (is (= 3 (ucl/loop for i from 1 to 5 do (when (== i 3) (ucl/loop-finish)) finally (return i))))
    (is (= 6 (ucl/loop for i from 1 to 5 sum i do (when (== i 3) (ucl/loop-finish)))))
    (is (= 3 (ucl/loop for i from 1 to 5 when (== i 3) do (ucl/loop-finish) end sum i)))
    (is (= 7 (ucl/loop for x across [3 4 5] sum x into s do (when (> s 6) (ucl/loop-finish))
                       finally (return s)))))
  (testing "a nested loop's loop-finish ends that loop"
    (is (= 6 (ucl/loop for i from 1 to 3
                       sum (ucl/loop for j from 1 to 10 do (when (== j i) (ucl/loop-finish))
                                     finally (return j)))))))

(deftest simple-loop-conformance-test
  (testing "(loop form*) runs until return"
    (is (= 4 (ucl/let ((n 0)) (ucl/loop (ucl/incf n) (when (> n 3) (ucl/return n))))))))

(deftest loop-test
  (testing "LeetCode 930, the README's example"
    (is (== 4 (k/num-subarrays-with-sum-loop (arr [1 0 1 0 1]) 2)))
    (is (== 15 (k/num-subarrays-with-sum-loop (arr [0 0 0 0 0]) 0))))
  (testing "a loop bound to a variable is a statement (D59)"
    (is (== 6 (ucl/let ((s (ucl/loop for x across (arr [1 2 3]) sum x)))
                (declare (type fixnum s))
                s))))
  (testing "the hash-table kernel: LeetCode 1512's shape"
    (is (== 4 (k/count-pairs-of-equals (arr [1 2 3 1 1 3]))))
    (is (== 0 (k/count-pairs-of-equals (arr [])))))
  (testing "Clojure's own loop is untouched"
    (is (== 3 (loop [i 0] (if (< i 3) (recur (inc i)) i))))))
