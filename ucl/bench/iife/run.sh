#!/bin/bash
# README §9.11. Runs each case x W in 7 fresh processes, prints median-of-medians and min-max
cd "$(dirname "$0")"
for W in 100 4; do
  for c in stmt iife stmt-lo iife-cap; do
    meds=(); res=""
    for p in 1 2 3 4 5 6 7; do
      out=$(node bench.mjs $c $W)
      meds+=($(echo "$out" | node -pe 'JSON.parse(require("fs").readFileSync(0)).median'))
      res=$(echo "$out" | node -pe 'JSON.parse(require("fs").readFileSync(0)).result')
    done
    printf '%s\n' "${meds[@]}" | sort -g | awk -v c=$c -v W=$W -v r=$res '{a[NR]=$1} END{printf "%-9s W=%-3s median=%9.1f us  range=%9.1f-%9.1f  result=%s\n", c, W, a[4], a[1], a[7], r}'
  done
done
