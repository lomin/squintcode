#!/usr/bin/env bash
# Each (fn, mode) alone in 5 fresh processes, JIT then AOT; prints the medians, µs.
cd "$(dirname "$0")"
DART="${DART_SDK:-$HOME/.local/dart-sdk}/bin/dart"
"$DART" compile exe run.dart -o "${TMPDIR:-/tmp}/hof-aot" >/dev/null
for vm in jit aot; do
  for mode in solo poly2 mega; do
    for fn in sumLoop sumFold countLoop countFold profitLoop profitEach profitEach2; do
      [ $mode != solo ] && [[ $fn == *Loop ]] && continue
      for i in 1 2 3 4 5; do
        if [ $vm = jit ]; then "$DART" run run.dart $fn $mode; else "${TMPDIR:-/tmp}/hof-aot" $fn $mode; fi
      done | node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
          const med=k=>rs.map(r=>r[k]).sort((a,b)=>a-b)[2];
          console.log(`'$vm' ${rs[0].mode.padEnd(6)} ${rs[0].fn.padEnd(11)} warm ${String(med("warm")).padStart(5)}  cold ${String(med("cold")).padStart(5)}  r=${rs[0].r}`)'
    done
  done
done
