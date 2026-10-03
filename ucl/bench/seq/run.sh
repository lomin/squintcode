#!/usr/bin/env bash
# README §4.2: LeetCode 1295, loop/recur against ucl/count-if. V8: 7 fresh processes;
# Dart: 5 processes, JIT then AOT. Median µs per call, n = 10^5.
cd "$(dirname "$0")"
DART="${DART_SDK:-$HOME/.local/dart-sdk}/bin/dart"; T="${TMPDIR:-/tmp}"
med() { node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);const w=rs.map(r=>r.warm).sort((a,b)=>a-b);console.log(`'"$1"' ${rs[0].v.padEnd(5)} warm ${w[w.length>>1]} µs (${w[0]}–${w[w.length-1]})  r=${rs[0].r}`)'; }
for v in loop seq; do for i in 1 2 3 4 5 6 7; do node run.mjs $v; done | med v8; done
for v in loop seq; do
  sed "s/BUILD/$v/g" run.dart.tmpl > "run_$v.dart"
  for i in 1 2 3 4 5; do "$DART" run "run_$v.dart"; done | med dart-jit
  "$DART" compile exe "run_$v.dart" -o "$T/seq-$v" >/dev/null
  for i in 1 2 3 4 5; do "$T/seq-$v"; done | med dart-aot
  rm "run_$v.dart"
done
