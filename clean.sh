#!/bin/bash
# Clean build artifacts

echo "Cleaning build artifacts..."

# Remove all .js files from out/ (except .mjs in subdirectories)
rm -f out/*.js

# Remove all .bundle.js files
rm -f out/*.bundle.js

echo "Clean complete!"
