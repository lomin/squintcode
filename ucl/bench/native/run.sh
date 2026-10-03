#!/usr/bin/env bash
# README D45: each variant alone in 7 fresh node processes; median of the per-process medians, µs per call.
cd "$(dirname "$0")"
for n in 100 100000; do
  for v in fill-loop-i32 fill-native-i32 fill-loop-arr fill-native-arr replace-loop-i32 replace-native-i32 replace-loop-arr \
           subseq-loop-i32 subseq-native-i32 subseq-loop-arr subseq-native-arr \
           copy-only-i32 sort-native-i32 sort-native-cmp-i32 sort-inline-i32 stable-inline-i32 sort-native-cmp-arr sort-inline-arr; do
    for p in 1 2 3 4 5 6 7; do node native.mjs $v $n; done |
      node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
        const w=rs.map(r=>r.warm).sort((a,b)=>a-b);
        console.log(`${String(rs[0].n).padStart(6)} ${rs[0].v.padEnd(20)} ${String(w[3]).padStart(10)} µs  (${w[0]}–${w[6]})  r=${rs[0].r}`)'
  done
done
