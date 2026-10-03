#!/usr/bin/env bash
# postCreateCommand: Runs once after container creation, in the workspace folder.

set -euo pipefail

echo "[post-create] Installing npm dependencies (squint, esbuild)..."
# .npmrc enforces ignore-scripts and pins the registry; npm ci installs
# exactly what package-lock.json specifies (integrity-checked) and fails
# on any mismatch.
npm ci

echo "[post-create] Verifying registry signatures and provenance..."
npm audit signatures

echo "[post-create] Warming Clojure dependencies..."
clojure -P -M:test
bb prepare

echo "[post-create] Warming ClojureDart and its Dart packages..."
# ucl/cljd-project is the template every ClojureDart build is made from:
# fetch its git dependency and the Dart packages (`test`) into the caches.
tmp=$(mktemp -d)
cp ucl/cljd-project/deps.edn ucl/cljd-project/pubspec.yaml "$tmp/"
(cd "$tmp" && clojure -P -M:cljd && dart pub get)
rm -rf "$tmp"

echo "[post-create] Installing Claude Code..."
curl -fsSL https://claude.ai/install.sh | bash

echo "[post-create] Versions:"
node -v
npm -v
bb --version
clojure --version
clj-kondo --version
dart --version

echo "[post-create] Done."
