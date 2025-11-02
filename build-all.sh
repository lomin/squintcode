#!/bin/bash
# Build script to compile all Squint problems for LeetCode

echo "Cleaning old build artifacts..."
rm -f out/*.js

echo "Building all LeetCode problems..."
echo

# Find all .cljs and .cljc files in src/squintcode/ (excluding macros.cljc)
for src_file in src/squintcode/*.cljs src/squintcode/*.cljc; do
  # Skip if it's the macros file
  if [[ "$src_file" == *"macros.cljc" ]]; then
    continue
  fi

  # Skip if glob didn't match anything (file doesn't exist)
  if [[ ! -f "$src_file" ]]; then
    continue
  fi

  # Extract just the filename without path and extension
  problem=$(basename "$src_file" .cljs)
  problem=$(basename "$problem" .cljc)

  echo "========================================"
  ./build-one.sh "$problem"
  echo
done

echo "========================================"
echo "All builds complete!"
echo
echo "Output files:"
ls -lh out/*.js 2>/dev/null | awk '{print $9, "(" $5 ")"}'
