;; How a JVM variable could be stored, on a 200k-element running sum (§9.8).
;;   clojure -M ucl/bench/variables/cells.clj
(set! *warn-on-reflection* true)

(definterface LongVar (^long get []) (^long set [^long v]))
(deftype LongCell [^:unsynchronized-mutable ^long v]
  LongVar (get [_] v) (set [_ x] (set! v x) x))

(defn loop-recur ^long [^longs a]
  (let [n (alength a)]
    (loop [i 0 s 0] (if (< i n) (recur (inc i) (+ s (aget a i))) s))))

(defn long-array-1 ^long [^longs a]
  (let [n (alength a) s (long-array 1)]
    (loop [i 0] (when (< i n) (aset s 0 (+ (aget s 0) (aget a i))) (recur (inc i))))
    (aget s 0)))

(defn volatile-cell ^long [^longs a]
  (let [n (alength a) s (volatile! 0)]
    (loop [i 0] (when (< i n) (vreset! s (+ (long @s) (aget a i))) (recur (inc i))))
    (long @s)))

(defn deftype-cell ^long [^longs a]
  (let [n (alength a) s (LongCell. 0)]
    (loop [i 0] (when (< i n) (.set s (+ (.get s) (aget a i))) (recur (inc i))))
    (.get s)))

(def data (long-array (map #(mod % 1000) (range 200000))))

(doseq [[k f] [[:loop-recur loop-recur] [:long-array-1 long-array-1]
               [:volatile volatile-cell] [:deftype-cell deftype-cell]]]
  (dotimes [_ 3000] (f data))
  (let [t (System/nanoTime)]
    (dotimes [_ 2000] (f data))
    (println k (format "%.0f us" (/ (- (System/nanoTime) t) 2000 1000.0)))))
