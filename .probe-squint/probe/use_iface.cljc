(ns probe.use-iface
  (:require [probe.iface :as iface]
            [probe.iface-impl]))

(def arr #js [1 2 3])
(def m (js/Map.))

(iface/-set-at! arr 1 99)
(iface/-set-at! m "a" 42)

(println "protocol dispatch =>" (iface/-get-at arr 1) (iface/-get-at m "a")
         (satisfies? iface/Place arr) (satisfies? iface/Place m))
