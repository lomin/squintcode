#!/bin/bash
# Build script to compile Squint code and prepare for LeetCode

echo "Compiling Squint to JavaScript..."
npx squint compile src/squintcode/fizzbuzz.cljs

echo "Bundling with Bun..."
bun build out/squintcode/fizzbuzz.mjs --outfile=out/bundle.js --format=esm

echo "Removing export statements..."
sed '/^export {$/,/^};$/d' out/bundle.js > out/squintcode.js

echo "Cleaning up intermediate files..."
rm -f out/squintcode/fizzbuzz.mjs out/bundle.js

echo "Done! Output file: out/squintcode.js"
echo "File size: $(wc -c < out/squintcode.js) bytes"
