#!/usr/bin/env bash
# Copy the current submissions (bb build) of every ported problem, both
# versions, into js/ and dart/ -- the measured code, kept with the numbers.
set -euo pipefail
cd "$(dirname "$0")"
OUT=../../../out
mkdir -p js dart
for p in fizzbuzz maxprofit lc_303_range_sum_query_immutable lc_560_subarray_sum_equals_k \
         lc_930_binary_subarrays_with_sum lc_19_remove_nth_node_from_end_of_list lc_2762_continuous_subarrays; do
  for v in "" _loop; do
    cp "$OUT/$p$v.js" "js/$p$v.js"
    cp "$OUT/$p$v.dart" "dart/$p$v.dart"
  done
done
