#!/usr/bin/env bash
# README D45, Dart: each variant alone in 5 fresh processes, JIT then AOT; median µs per call.
cd "$(dirname "$0")"
DART="${DART_SDK:-$HOME/.local/dart-sdk}/bin/dart"; AOT="${TMPDIR:-/tmp}/native-aot"
"$DART" compile exe native.dart -o "$AOT" >/dev/null
for vm in jit aot; do for n in 100 100000; do
  for v in fill-loop-i32 fill-native-i32 fill-loop-list fill-native-list replace-loop-i32 replace-native-i32 replace-loop-list replace-native-list \
           subseq-loop-i32 subseq-native-i32 subseq-loop-list subseq-native-list \
           copy-only-i32 sort-native-i32 sort-native-cmp-i32 sort-inline-i32 stable-inline-i32 sort-native-list sort-native-cmp-list sort-inline-list; do
    for p in 1 2 3 4 5; do if [ $vm = jit ]; then "$DART" run native.dart $v $n; else "$AOT" $v $n; fi; done |
      node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
        const w=rs.map(r=>r.warm).sort((a,b)=>a-b);
        console.log(`'$vm' ${String(rs[0].n).padStart(6)} ${rs[0].v.padEnd(21)} ${String(w[2]).padStart(10)} µs  (${w[0]}–${w[4]})  r=${rs[0].r}`)'
  done; done; done
