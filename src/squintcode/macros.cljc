(ns squintcode.macros)

;; postwalk: depth-first post-order traversal
;; Visits children first, then applies f to the parent
(defn postwalk [f form]
  (let [walked (cond
                 (seq? form)
                 (map #(postwalk f %) form)

                 (vector? form)
                 (vec (map #(postwalk f %) form))

                 :else
                 form)]
    (f walked)))

;; forv macro that returns a vector
;; Optimized for range expressions: pre-allocates array and uses dotimes
(defmacro forv [[binding range-expr] body]
  (let [[_ start end] range-expr]  ; destructure (range start end), ignore 'range symbol
    `(let [start# ~start
           end# ~end
           size# (- end# start#)
           arr# (js/Array. size#)]
       (dotimes [idx# size#]
         (let [~binding (+ start# idx#)]
           (aset arr# idx# ~body)))
       arr#)))

;; aloop macro for optimized array iteration with O(1) access
;; Usage: (aloop arr elem [state-var1 init1 ...] body)
;; In body, use (recur ...) which implicitly increments the index
(defmacro aloop [arr-expr elem-var state-bindings & body]
  (let [idx (gensym "idx")
        replace-recur (fn [form]
                        (if (and (seq? form) (= 'recur (first form)))
                          `(recur (inc ~idx) ~@(rest form))
                          form))]
    `(let [arr# ~arr-expr
           len# (count arr#)]
       (loop [~idx 0
              ~@state-bindings]
         (let [~elem-var (when (< ~idx len#) (nth arr# ~idx))]
           ~@(map #(postwalk replace-recur %) body))))))

(comment
  (macroexpand '(let [x 1] x))
  (macroexpand '(forv [i (range 1 (inc n))]
                      (condp fizz-buzz-pred i
                        15 "FizzBuzz"
                        3  "Fizz"
                        5  "Buzz"
                        (str i)))))