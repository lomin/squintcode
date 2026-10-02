(ns portable.app
  "PROTOTYPE — the PORTABLE application. Identical logic on every target.

   This require block is the entire per-target cost of a macro-based facade.
   Note there is NO :squint branch: Squint activates the :cljs feature, so one
   branch covers both JS-family hosts. One `:require` ENTRY per namespace --
   two separate entries for the same ns make Squint emit a duplicate
   `import * as`, which is invalid ESM."
  (:require
   #?(:clj  [setf.api :as api :refer [setf! incf!]]
      :cljs [setf.api :as api :refer-macros [setf! incf!]])))

(defn run []
  (let [a (api/make-arr 3)
        m (api/make-map)
        o (api/make-obj)]
    (setf! (elt a 1) 99)
    (incf! (elt a 1))
    (setf! (gethash m "k") 7)
    ;; The backend can resolve this receiver's class at expansion time.
    (setf! (get! (api/make-obj) x) 42)
    ;; It cannot resolve this one -- the object flows through a variable.
    (setf! (get! o x) 42)
    (api/dump a m o)))

(defn -main [] (println "APP-RESULT" (pr-str (run))))