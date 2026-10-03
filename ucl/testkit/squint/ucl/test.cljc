(ns ucl.test
  "The test vocabulary every ucl suite uses. On Squint, ucl ships its own
   (D5): the `is` from macros.cljc was node's assert.equal -- reference
   equality on arrays (H19). This `is` evaluates the form itself, so (= a b)
   is Squint's own structural equality.")

(def state {:tests [] :context [] :pass 0 :fail 0 :error 0})

(defn register! [nm f] (.push (:tests state) [nm f]) nil)

(defn- where [] (.join (:context state) " > "))

(defn report!
  "Record one assertion. `values` are the evaluated arguments of a call form."
  [ok form-str msg values]
  (if ok
    (set! (.-pass state) (inc (:pass state)))
    (do (set! (.-fail state) (inc (:fail state)))
        (println "\nFAIL in" (where))
        (when msg (println "  " msg))
        (println "  expected:" form-str)
        (when values (println "    values:" (pr-str values)))))
  ok)

(defn report-error! [form-str e]
  (set! (.-error state) (inc (:error state)))
  (println "\nERROR in" (where))
  (println "  form:" form-str)
  (println "  threw:" (str e))
  false)

(defn run-all!
  "Run every registered test; set the exit code; return true when all passed."
  []
  (doseq [[nm f] (:tests state)]
    (.push (:context state) nm)
    (try (f)
         (catch :default e (report-error! nm e)))
    (.pop (:context state)))
  (println (str "\nRan " (count (:tests state)) " tests containing "
                (+ (:pass state) (:fail state) (:error state)) " assertions.\n"
                (:fail state) " failures, " (:error state) " errors."))
  (let [ok (zero? (+ (:fail state) (:error state)))]
    (when-not ok (set! (.-exitCode js/process) 1))
    ok))

(defmacro deftest [nm & body]
  `(ucl.test/register! ~(str nm) (fn [] ~@body)))

(defmacro testing [label & body]
  `(do (.push (:context ucl.test/state) ~label)
       (try ~@body (finally (.pop (:context ucl.test/state))))))

(defmacro is
  ([form] `(is ~form nil))
  ([form msg]
   (let [form-str (pr-str form)]
     (if (and (seq? form) (contains? '#{= == not= < > <= >=} (first form)))
       (let [args (vec (rest form))
             gs   (mapv (fn [_] (gensym "v")) args)]
         `(try (let [~@(interleave gs args)]
                 (ucl.test/report! (~(first form) ~@gs) ~form-str ~msg [~@gs]))
               (catch :default e# (ucl.test/report-error! ~form-str e#))))
       `(try (ucl.test/report! ~form ~form-str ~msg nil)
             (catch :default e# (ucl.test/report-error! ~form-str e#)))))))

(defmacro signals-error?
  "True when evaluating `body` signals an error."
  [& body]
  `(try ~@body false (catch :default _# true)))
