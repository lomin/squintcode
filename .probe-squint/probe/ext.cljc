(ns probe.ext
  "Platform-specific EXPANDER table, in its own namespace.")

#?(:clj
   (defn emit-set [coll idx val]
     `(let [c# ~coll] (.set ~c# ~idx ~val)))
   :default
   (defn emit-set [coll idx val]
     `(aset ~coll ~idx ~val)))

#?(:clj
   (defn emit-get [coll idx]
     `(nth ~coll ~idx))
   :default
   (defn emit-get [coll idx]
     `(aget ~coll ~idx)))
