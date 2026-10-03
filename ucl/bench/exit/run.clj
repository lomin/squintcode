;; Early exit from a loop on the JVM; kernels.mjs, transcribed.
;;   clojure -M run.clj <variant> <workload>
;; pos    loop/recur: the exit rewritten statically
;; pre    throw one preallocated stackless exception (writableStackTrace false)
;; fresh  a fresh stackless exception per exit -- me.lomin.ex's ex/exit
;; error  a fresh ex-info per exit (fills in its stack trace)
(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(definterface IVal (^long v []))

(defn ^RuntimeException stackless [^long v]
  (proxy [RuntimeException IVal] [nil nil false false] (v [] v)))
(def ^:private exit-class (class (stackless 0)))

(defn pos ^long [^ints a ^long s ^long x]
  (let [n (alength a)]
    (loop [i s]
      (if (< i n)
        (if (> (aget a i) x) i (recur (inc i)))
        -1))))

(def ^:private cell (long-array 1))
(def ^:private ^RuntimeException pre-exit
  (proxy [RuntimeException IVal] [nil nil false false] (v [] (aget ^longs cell 0))))
(defn pre ^long [^ints a ^long s ^long x]
  (try
    (let [n (alength a)]
      (loop [i s]
        (if (< i n)
          (if (> (aget a i) x) (do (aset ^longs cell 0 i) (throw pre-exit)) (recur (inc i)))
          -1)))
    (catch RuntimeException e
      (if (identical? e pre-exit) (aget ^longs cell 0) (throw e)))))

(defn fresh ^long [^ints a ^long s ^long x]
  (try
    (let [n (alength a)]
      (loop [i s]
        (if (< i n)
          (if (> (aget a i) x) (throw (stackless i)) (recur (inc i)))
          -1)))
    (catch RuntimeException e
      (if (instance? exit-class e) (.v ^IVal e) (throw e)))))

(defn error ^long [^ints a ^long s ^long x]
  (try
    (let [n (alength a)]
      (loop [i s]
        (if (< i n)
          (if (> (aget a i) x) (throw (ex-info "exit" {:v i})) (recur (inc i)))
          -1)))
    (catch clojure.lang.ExceptionInfo e
      (long (:v (ex-data e))))))

(let [[variant workload] *command-line-args*
      f ({"pos" pos "pre" pre "fresh" fresh "error" error} variant)
      n (if (= workload "short") 100000 1000000)
      a (int-array n)
      _ (loop [i 0 s 12345]
          (when (< i n)
            (let [s (bit-and (+ (* s 1103515245) 12345) 0x7fffffff)]
              (aset a i (int (mod s 1000000000)))
              (recur (inc i) s))))
      _ (aset a (dec n) (int 1000000001))
      run (case workload
            "short" #(loop [i 0 r 0] (if (< i n) (recur (inc i) (+ r (long (f a i (aget a i))))) r))
            "long"  #(f a 0 1000000000)
            "none"  #(f a 0 1000000001))
      time (fn [] (let [t (System/nanoTime) r (run)] [(quot (- (System/nanoTime) t) 1000) r]))
      [cold r] (time)
      ws (sort (repeatedly 300 #(first (time))))]
  (println (format "{\"variant\":\"%s\",\"workload\":\"%s\",\"cold\":%d,\"warm\":%d,\"r\":%d}"
                   variant workload cold (nth ws 150) r)))
