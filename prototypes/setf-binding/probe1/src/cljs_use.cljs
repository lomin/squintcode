(ns cljs-use
  (:require [nb2.api :as nb2]     ;; implicit macro loading, NO .cljs twin
            [nb3.api :as nb3]     ;; implicit macro loading, .clj AND .cljs present
            [nb4.api :refer-macros [m]]))  ;; explicit :require-macros

(defn run []
  {:nb2-var   nb2/v
   :nb2-macro (nb2/m)
   :nb3-var   nb3/v
   :nb3-macro (nb3/m)
   :nb4-macro (m)})

(defn -main [] (println "CLJS-PROBE" (pr-str (run))))
