(ns ucl.api
  "ucl on ClojureScript, macro half (runs on the JVM while ClojureScript
   compiles). The emit map is ucl.js-emit's, in the ClojureScript flavor."
  (:refer-clojure :exclude [make-array min max defstruct defmethod let dotimes loop
                            count find some reduce])
  (:require [cljs.env :as env]
            [ucl.contract :as contract]
            [ucl.js-emit :as js-emit]
            [ucl.loop :as ucl-loop]
            [ucl.seq :as seq]))

(def flavor
  {:array-literal (fn [items] (cons 'array items))
   :local-tag     (fn [env sym] (get-in env [:locals sym :tag]))
   :tags?         true
   ;; ClojureScript keywords have no stable identity (H14): a js/Map must be
   ;; keyed by the keyword's name, as Squint's keywords already are.
   :key-literal   (fn [k] (if (keyword? k) (subs (str k) 1) k))
   :key-runtime   (fn [k] (list 'ucl.api/hash-key k))
   :vector-reads? true
   :check-arity?  true
   :cells?        true
   :safety        (fn [_] (when env/*compiler*
                            (get-in @env/*compiler* [:options :ucl/safety])))
   ;; keywords have no stable identity here (H14); strings compare by value (D52)
   :eql           (fn [a b] (list 'cljs.core/keyword-identical? a b))})

(defn emit [env] (js-emit/emit-map flavor env))

(contract/defapi (emit &env) {:vocabularies [ucl-loop/expanders seq/expanders]})

;; LeetCode's own classes are globals there; a solution names them bare, as in
;; (new ListNode 0 head). Declaring them as cljs.core names lets ClojureScript
;; resolve that symbol without an undeclared-var warning (H27); the test kit's
;; strict fixtures supply the value at run time.
(when env/*compiler*
  (swap! env/*compiler* update-in [:cljs.analyzer/namespaces 'cljs.core :defs]
         merge {'ListNode {:name 'cljs.core/ListNode}
                'TreeNode {:name 'cljs.core/TreeNode}}))
