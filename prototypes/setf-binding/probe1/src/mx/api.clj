(ns mx.api)
;; MX: macro half in .clj, runtime half in mx/api.cljs. Tests IMPLICIT macro loading.
(defmacro where [] "MX-macro-via-api.clj")
