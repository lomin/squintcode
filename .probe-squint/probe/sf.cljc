(ns probe.sf
  "Agnostic setf! driver: keys on the accessor symbol, delegates emission."
  (:require [probe.ext :as ext]))

(defn- accessor-name [x]
  (when (symbol? x) (name x)))

(defmacro setf! [place val]
  (if (seq? place)
    (let [[op & args] place]
      (case (accessor-name op)
        ("aref" "elt" "nth") (apply ext/emit-set (conj (vec args) val))
        (throw (ex-info (str "no setf! expander for " op) {:place place}))))
    (throw (ex-info "setf! needs a place form" {:place place}))))

(defmacro get!* [place]
  (if (seq? place)
    (let [[op & args] place]
      (apply ext/emit-get args))
    (throw (ex-info "bad place" {:place place}))))
