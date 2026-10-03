#!/usr/bin/env bash
# Each (variant, workload) alone in 5 fresh processes, JIT then AOT; medians, µs.
cd "$(dirname "$0")"
DART="${DART_SDK:-$HOME/.local/dart-sdk}/bin/dart"
"$DART" compile exe run.dart -o "${TMPDIR:-/tmp}/exit-aot" >/dev/null
for vm in jit aot; do
  for w in short long none; do
    for v in ${VARIANTS:-pos pre fresh error split}; do
      for i in 1 2 3 4 5; do
        if [ $vm = jit ]; then "$DART" run run.dart $v $w; else "${TMPDIR:-/tmp}/exit-aot" $v $w; fi
      done | node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
          const med=k=>rs.map(r=>r[k]).sort((a,b)=>a-b)[2];
          console.log(`'$vm' ${rs[0].workload.padEnd(5)} ${rs[0].variant.padEnd(5)} warm ${String(med("warm")).padStart(6)}  cold ${String(med("cold")).padStart(6)}  r=${rs[0].r}`)'
    done
  done
done
