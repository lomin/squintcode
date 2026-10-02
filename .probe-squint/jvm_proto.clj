(defprotocol P (-f [x]))

(extend-type java.util.ArrayList
  P
  (-f [x] :arraylist))

(extend-type java.util.HashMap
  P
  (-f [x] :hashmap))

(extend-type java.util.List
  P
  (-f [x] :list))

(defn try-call [label v]
  (println label "=>" (try (-f v) (catch Exception e (str "FAILED: " (.getMessage e))))))

(try-call "ArrayList          " (java.util.ArrayList.))
(try-call "HashMap            " (java.util.HashMap.))
(try-call "LinkedList (as List)" (java.util.LinkedList.))

;; array classes, via extend with the Class object
(extend (class (object-array 0)) P {:f (fn [_] :obj-array)})
(try-call "Object[]           " (object-array 3))

(extend (class (int-array 0)) P {:f (fn [_] :int-array)})
(try-call "int[]              " (int-array 3))
