#!/bin/bash
# Clean build artifacts

set -e  # Exit on any error

echo "Cleaning build artifacts..."

# Remove final output files
rm -f out/*.js

# Remove intermediate files to prevent caching issues
rm -f out/*.bundle.js
rm -f out/squintcode/*.mjs

# Remove ClojureScript cache directories
rm -rf cljs-test-runner-out .cljs_node_repl .cljsbuild

echo "✓ Clean complete!"
