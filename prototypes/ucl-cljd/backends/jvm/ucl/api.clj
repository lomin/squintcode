(ns ucl.api
  "SPIKE -- Clojure/JVM backend."
  (:refer-clojure :exclude [make-array])
  (:require [ucl.contract :as contract]))

(def ^:private array-hints
  {(class (object-array 0)) 'objects
   (class (int-array 0))    'ints
   (class (long-array 0))   'longs})

(defn- array-hint
  "The array hint for a receiver whose class the compiler knows, else nil."
  [env src]
  (or (get array-hints (some-> (:tag (meta src)) resolve))
      (when (symbol? src)
        (when-let [^clojure.lang.Compiler$LocalBinding lb (get env src)]
          (try (when (.hasJavaClass lb) (get array-hints (.getJavaClass lb)))
               (catch Exception _ nil))))))

(defn- hinted [sym tag] (vary-meta sym assoc :tag tag))

(defn- emit [env]
  {:indexed {:read  (fn [[a i] [src]]
                      (if-let [h (array-hint env src)]
                        (list 'aget (hinted a h) i)
                        (list '.get (hinted a 'java.util.List) i)))
             :write (fn [[a i] [src] v]
                      (if-let [h (array-hint env src)]
                        (list 'aset (hinted a h) i v)
                        (list '.set (hinted a 'java.util.List) i v)))}
   :vector  {:make (fn [n element init]
                     (case element
                       :t      (if (some? init)
                                 `(doto (object-array ~n) (java.util.Arrays/fill ~init))
                                 `(object-array ~n))
                       :fixnum `(int-array ~n ~(or init 0))
                       :sb53   `(long-array ~n ~(or init 0))))}
   :types   {:hint {:fixnum 'long
                    [:vector :fixnum] 'ints
                    [:vector :sb53]   'longs
                    [:vector :t]      'objects}}})

(contract/defapi (emit &env))
