#!/usr/bin/env bash
# Each (fn, mode) alone in 7 fresh node processes; prints the medians, µs.
cd "$(dirname "$0")"
for mode in solo poly2 mega; do
  for fn in sumLoop sumFold countLoop countFold profitLoop profitEach; do
    [ $mode != solo ] && [[ $fn == *Loop ]] && continue
    for i in 1 2 3 4 5 6 7; do node run.mjs $fn $mode; done |
      node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
        const med=k=>rs.map(r=>r[k]).sort((a,b)=>a-b)[3];
        console.log(`${rs[0].mode.padEnd(6)} ${rs[0].fn.padEnd(11)} warm ${String(med("warm")).padStart(5)}  cold ${String(med("cold")).padStart(5)}  r=${rs[0].r}`)'
  done
done
