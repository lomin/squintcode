(ns cy.use
  (:require [cy.api :as a]
            [cy.api :refer-macros [where]]))

(defn run [] {:v a/v :m (where)})
(defn -main [] (println "CY-EXPLICIT-REFER-MACROS" (pr-str (run))))
