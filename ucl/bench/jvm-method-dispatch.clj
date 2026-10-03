(set! *warn-on-reflection* true)
(defprotocol PInline (sumRangeI [this l r]))
(defprotocol PExt    (sumRangeE [this l r]))
(definterface IAcc (^long getX []))

;; (b) today: methods inside deftype -> real Java methods
(deftype A [^long x] PInline (sumRangeI [_ l r] (+ x (long l) (long r))))
;; (a) defstruct + later defmethod -> protocol attached with extend-type;
;;     body must go through an accessor, mutable fields are private
(deftype B [^:unsynchronized-mutable ^long x] IAcc (getX [_] x))
(extend-type B PExt (sumRangeE [this l r] (+ (.getX ^IAcc this) (long l) (long r))))

(def N 50000000)
(defn bench [label f]
  (dotimes [_ 3] (f))                                   ; warmup
  (let [ts (vec (for [_ (range 5)]
                  (let [t0 (System/nanoTime) _ (f)] (/ (double (- (System/nanoTime) t0)) N))))]
    (println (format "  %-55s %5.2f ns/call  (rounds %s)" label (nth (sort ts) 2)
                     (mapv #(format "%.2f" %) ts)))))

(let [^A a (A. 1) ^B b (B. 1)
      a-untyped (A. 1)]
  (println "JVM" (System/getProperty "java.version") "| median of 5 rounds," N "calls each")
  (bench "(b) interop (.sumRangeI ^A a i 1) -- direct method"  #(loop [i 0 s 0] (if (< i N) (recur (inc i) (+ s (long (.sumRangeI a i 1)))) s)))
  (bench "(b) protocol fn (sumRangeI a i 1), inline impl"        #(loop [i 0 s 0] (if (< i N) (recur (inc i) (+ s (long (sumRangeI a-untyped i 1)))) s)))
  (bench "(a) protocol fn (sumRangeE b i 1), extend-type impl"  #(loop [i 0 s 0] (if (< i N) (recur (inc i) (+ s (long (sumRangeE b i 1)))) s))))

;; megamorphic: one call site sees 3 struct types extended the same way
(deftype C1 [] PExt (sumRangeE [_ l r] 1))
(deftype B2 [] ) (deftype B3 [])
(extend-type B2 PExt (sumRangeE [_ l r] 2))
(extend-type B3 PExt (sumRangeE [_ l r] 3))
(let [objs (object-array [(B. 1) (B2.) (B3.)])]
  (bench "(a) extend-type, one call site seeing 3 struct types" #(loop [i 0 s 0] (if (< i N) (recur (inc i) (+ s (long (sumRangeE (aget objs (rem i 3)) i 1)))) s))))
(let [objs (object-array [(A. 1) (A. 2) (A. 3)])]
  (bench "baseline: same loop shape, (b) inline, 1 type"       #(loop [i 0 s 0] (if (< i N) (recur (inc i) (+ s (long (sumRangeI (aget objs (rem i 3)) i 1)))) s))))
