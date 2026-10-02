(ns probe.iface-impl
  (:require [probe.iface :as iface]))

(defn ^:private idx [k]
  (if (number? k) k (parse-long k)))

(extend-type js/Array
  iface/Place
  (-get-at [place k] (aget place (idx k)))
  (-set-at! [place k v] (aset place (idx k) v)))

(extend-type js/Map
  iface/Place
  (-get-at [place k] (.get place k))
  (-set-at! [place k v] (.set place k v)))

(println "impl loaded")
