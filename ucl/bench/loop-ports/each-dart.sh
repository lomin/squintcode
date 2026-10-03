#!/usr/bin/env bash
# Each (problem, version): its submission + dart-main/harness.dart, alone in 5
# fresh processes, JIT then AOT; medians, µs.
cd "$(dirname "$0")"
DART="${DART_SDK:-$HOME/.local/dart-sdk}/bin/dart"
TMP="${TMPDIR:-/tmp}/loop-ports-dart"; mkdir -p "$TMP"
declare -A file=([fizzBuzz]=fizzbuzz [maxProfit]=maxprofit [NumArray]=lc_303_range_sum_query_immutable
  [subarraySum]=lc_560_subarray_sum_equals_k [numSubarraysWithSum]=lc_930_binary_subarrays_with_sum
  [removeNthFromEnd]=lc_19_remove_nth_node_from_end_of_list [continuousSubarrays]=lc_2762_continuous_subarrays)
for p in ${PROBLEMS:-fizzBuzz maxProfit NumArray subarraySum numSubarraysWithSum removeNthFromEnd continuousSubarrays}; do
  for v in recur loop; do
    suffix=$([ $v = loop ] && echo _loop || true)
    src="$TMP/${file[$p]}$suffix.dart"
    cat "dart/${file[$p]}$suffix.dart" dart-main/common.dart "dart-main/$p.dart" > "$src"
    "$DART" compile exe "$src" -o "$TMP/$p-$v" >/dev/null
    for vm in jit aot; do
      for i in 1 2 3 4 5; do
        if [ $vm = jit ]; then "$DART" run "$src" ; else "$TMP/$p-$v"; fi
      done | node -e 'const rs=require("fs").readFileSync(0,"utf8").trim().split("\n").map(JSON.parse);
          const med=k=>rs.map(r=>r[k]).sort((a,b)=>a-b)[2];
          console.log(`'"$vm $p"' '"$v"' warm ${String(med("warm")).padStart(6)}  cold ${String(med("cold")).padStart(6)}  r=${rs[0].r}`)'
    done
  done
done
