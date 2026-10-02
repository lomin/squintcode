(ns probe.iface)

;; The platform-agnostic "interface" namespace.
(defprotocol Place
  (-get-at [place k])
  (-set-at! [place k v]))
