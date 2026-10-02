(ns probe.typed)

(defprotocol P2 (-f [x]))

(extend-type js/Array P2 (-f [x] :array))
(extend-type js/Uint32Array P2 (-f [x] :u32))
(extend-type js/Map P2 (-f [x] :map))

(println "Array  =>" (-f #js [1 2 3]))
(println "U32    =>" (-f (js/Uint32Array. 3)))
(println "U32from=>" (-f (js/Uint32Array.from #js [1 2 3])))
(println "Map    =>" (-f (js/Map.)))
(println "satisfies U32 =>" (satisfies? P2 (js/Uint32Array. 3)))
