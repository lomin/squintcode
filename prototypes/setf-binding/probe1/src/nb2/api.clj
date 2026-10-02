(ns nb2.api)
;; NB2: ONLY a .clj file defines this ns (macro-namespace shape).
(def v :v-from-clj-only-ns)
(defmacro m [] (str "M-macro-from-nb2-api.clj"))
