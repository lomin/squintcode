#!/usr/bin/env bash
# Each (variant, workload) alone in 3 fresh JVMs; medians, µs.
cd "$(dirname "$0")"
for w in short long none; do
  for v in ${VARIANTS:-pos pre fresh error}; do
    for i in 1 2 3; do clojure -M run.clj $v $w; done |
      node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
        const med=k=>rs.map(r=>r[k]).sort((a,b)=>a-b)[1];
        console.log(`${rs[0].workload.padEnd(5)} ${rs[0].variant.padEnd(5)} warm ${String(med("warm")).padStart(6)}  cold ${String(med("cold")).padStart(6)}  r=${rs[0].r}`)'
  done
done
