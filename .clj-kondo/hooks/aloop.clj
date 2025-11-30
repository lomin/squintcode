(ns hooks.aloop
  (:require [clj-kondo.hooks-api :as api]))

(defn aloop [{:keys [node]}]
  (let [[_ arr-expr state-bindings & body] (:children node)]
    ;; Transform (aloop arr [bindings] body)
    ;; into (let [_self arr, self _self, it (nth arr 0 nil)] (loop [bindings] body))
    ;; Anaphoric macro: binds 'it' to current element, 'self' to the collection
    ;; Use _self first to suppress unused warning, then alias self to it
    (when (api/vector-node? state-bindings)
      {:node (api/list-node
              [(api/token-node 'let)
               (api/vector-node
                [(api/token-node '_self)
                 arr-expr
                 (api/token-node 'self)
                 (api/token-node '_self)
                 (api/token-node 'it)
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
