#!/bin/bash
# Build script to compile a single Squint problem for LeetCode
# Usage: ./build-one.sh <problem-name>
# Example: ./build-one.sh fizzbuzz

set -e  # Exit on any error

if [ -z "$1" ]; then
  echo "Error: Problem name required"
  echo "Usage: ./build-one.sh <problem-name>"
  echo "Example: ./build-one.sh fizzbuzz"
  exit 1
fi

PROBLEM="$1"
SRC_FILE=""

# Check for .cljs first, then .cljc
if [ -f "src/squintcode/${PROBLEM}.cljs" ]; then
  SRC_FILE="src/squintcode/${PROBLEM}.cljs"
elif [ -f "src/squintcode/${PROBLEM}.cljc" ]; then
  SRC_FILE="src/squintcode/${PROBLEM}.cljc"
else
  echo "Error: Source file not found: src/squintcode/${PROBLEM}.cljs or src/squintcode/${PROBLEM}.cljc"
  exit 1
fi

MJS_FILE="out/squintcode/${PROBLEM}.mjs"
BUNDLE_FILE="out/${PROBLEM}.bundle.js"
OUTPUT_FILE="out/${PROBLEM}.js"

echo "Building $PROBLEM..."

# Always compile macros first (required dependency)
if [ -f "src/squintcode/macros.cljc" ]; then
  echo "Compiling macros.cljc..."
  npx squint compile src/squintcode/macros.cljc > /dev/null
fi

echo "Compiling Squint to JavaScript..."
npx squint compile "$SRC_FILE"

if [ ! -f "$MJS_FILE" ]; then
  echo "Error: Compilation failed, $MJS_FILE not found"
  exit 1
fi

echo "Bundling with esbuild..."
npx esbuild "$MJS_FILE" --outfile="$BUNDLE_FILE" --format=esm --bundle --tree-shaking=true

echo "Removing export statements..."
sed '/^export {$/,/^};$/d' "$BUNDLE_FILE" > "$OUTPUT_FILE"

# Clean up bundle file
rm -f "$BUNDLE_FILE"

echo "Done! Output file: $OUTPUT_FILE"
echo "File size: $(wc -c < "$OUTPUT_FILE") bytes"
