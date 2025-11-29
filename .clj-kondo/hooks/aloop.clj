(ns hooks.aloop
  (:require [clj-kondo.hooks-api :as api]))

(defn aloop [{:keys [node]}]
  (let [[_ arr-expr state-bindings & body] (:children node)]
    ;; Transform (aloop arr [bindings] body)
    ;; into (let [it (nth arr 0 nil)] (loop [bindings] body))
    ;; Anaphoric macro: always binds to 'it'
    (when (api/vector-node? state-bindings)
      {:node (api/list-node
              [(api/token-node 'let)
               (api/vector-node
                [(api/token-node 'it)
                 (api/list-node
                  [(api/token-node 'nth)
                   arr-expr
                   (api/token-node 0)
                   (api/token-node nil)])])
               (api/list-node
                (list*
                 (api/token-node 'loop)
                 state-bindings
                 body))])})))
