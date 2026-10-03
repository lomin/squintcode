(ns hof-bench
  "Performance measurements for the curried (1-arity) place readers.

   Indicative only -- NOT JMH. One JVM, warmup then timed rounds, every round
   printed so a lucky run is visible instead of hidden inside a mean. The ratio
   that matters is stable across rounds; the absolute numbers are not.

   Run from the setf/ directory:
     clojure -Sdeps '{:paths [\"shared\" \"backends/jvm\" \"bench\"]}' \\
       -M -e \"(require 'hof-bench) (hof-bench/-main)\"")

(set! *warn-on-reflection* true)   ; before any code below is compiled

(require '[setf.api :as api])
;; On Clojure the place names are macros, so they must be REFERRED, not just
;; required. (On the JS hosts they would be :refer-macros -- see the test suite.)
(refer 'setf.api :only '[elt gethash get!])

;; ---------------------------------------------------------------------------
;; Timing
;; ---------------------------------------------------------------------------
(def N 3000000)

(defn- timeit [label f]
  (dotimes [_ 200000] (f))                    ; warmup
  (let [t0 (System/nanoTime)
        _  (dotimes [_ N] (f))
        ns  (/ (double (- (System/nanoTime) t0)) N)]
    (println (format "  %-38s %8.2f ns/op" label ns))
    ns))

(defn- compare! [label direct-fn direct curried-fn curried floor-fn floor]
  (assert (= (direct-fn) (curried-fn) (floor-fn)) (str label ": tactics must agree"))
  (println (format "  %-38s %8.2fx" "curried / direct" (/ curried direct)))
  (println (format "  %-38s %8.2fx" "curried / floor" (/ curried floor)))
  (println))

;; ---------------------------------------------------------------------------
;; elt -- currying fixes an ordinary VALUE, so the receiver stays the caller's
;; expression and the ^java.util.List interface hint still applies.
;; ---------------------------------------------------------------------------
(defn- elt-round []
  (println "  --- elt (indexed) ---")
  (let [^java.util.List hinted (doto (api/make-arr 3) (.set 2 5))
        v   2
        cur (elt v)]
    (let [direct  (timeit "direct (elt as i), hinted receiver"
                          (fn [] (elt hinted v)))
          curried (timeit "curried ((elt i) as)"
                          (fn [] (cur hinted)))
          floor   (timeit "floor (.get ^List as i)"
                          (fn [] (.get hinted v)))]
      (compare! "elt" #(elt hinted v) direct #(cur hinted) curried
               #(.get hinted v) floor))))

;; ---------------------------------------------------------------------------
;; gethash -- same story, ^java.util.Map interface hint.
;; ---------------------------------------------------------------------------
(defn- gethash-round []
  (println "  --- gethash (keyed) ---")
  (let [^java.util.Map hinted (doto (api/make-map) (.put "k" 5))
        k   "k"
        cur (gethash k)]
    (let [direct  (timeit "direct (gethash m k), hinted receiver"
                          (fn [] (gethash hinted k)))
          curried (timeit "curried ((gethash k) m)"
                          (fn [] (cur hinted)))
          floor   (timeit "floor (.get ^Map m k)"
                          (fn [] (.get hinted k)))]
      (compare! "gethash" #(gethash hinted k) direct #(cur hinted) curried
               #(.get hinted k) floor))))

;; ---------------------------------------------------------------------------
;; get! -- different. A SLOT is consumed at expansion time and never evaluated,
;; so its argument must be a bare symbol, and the receiver inside the returned
;; closure is an untyped fn PARAMETER. The backend cannot learn its class, so
;; the field read falls back to Reflector/getInstanceField.
;; ---------------------------------------------------------------------------
(defn- get!-round []
  (println "  --- get! (field) ---")
  (let [^java.awt.Point hinted (doto (api/make-obj) (-> .x (set! 5)))
        cur (get! x)]
    (do
      (let [direct  (timeit "direct (get! pt x), hinted receiver"
                            (fn [] (get! hinted x)))
            curried (timeit "curried ((get! x) pt) -- reflective"
                            (fn [] (cur hinted)))
            floor   (timeit "floor (.x ^Point pt)"
                            (fn [] (.x ^java.awt.Point hinted)))]
        (compare! "get!" #(get! hinted x) direct #(cur hinted) curried
                 #(.x ^java.awt.Point hinted) floor)))))

(defn -main []
  (println)
  (println (format "bench: %d ops x 2 rounds, single JVM (INDICATIVE -- not JMH)" N))
  (println)
  (println "round 1")
  (elt-round)
  (gethash-round)
  (get!-round)
  (println "round 2")
  (elt-round)
  (gethash-round)
  (get!-round))
