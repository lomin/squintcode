#!/usr/bin/env bash
# Each (variant, workload) alone in 7 fresh node processes; medians, µs.
cd "$(dirname "$0")"
for w in short long none; do
  for v in ${VARIANTS:-pos pre fresh error split}; do
    for i in 1 2 3 4 5 6 7; do node run.mjs $v $w; done |
      node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
        const med=k=>rs.map(r=>r[k]).sort((a,b)=>a-b)[3];
        console.log(`${rs[0].workload.padEnd(5)} ${rs[0].variant.padEnd(5)} warm ${String(med("warm")).padStart(6)}  cold ${String(med("cold")).padStart(6)}  r=${rs[0].r}`)'
  done
done
