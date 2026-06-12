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

echo "[post-create] Installing Claude Code..."
curl -fsSL https://claude.ai/install.sh | bash

echo "[post-create] Versions:"
node -v
npm -v
bb --version
clojure --version
clj-kondo --version

echo "[post-create] Done."
