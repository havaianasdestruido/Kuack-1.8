#!/usr/bin/env bash
# Compile-check every built-in deferred/PBR shader the change touches, including the compile-flag
# combinations the pipeline builds. Uses the real glslang front end (wasm) via npm.
#
#   ./perfbench/shader/run_validate.sh
#
# Needs network access to registry.npmjs.org the first time (npm install).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
cd "$HERE"
if [ ! -d node_modules/@webgpu/glslang ]; then
  echo "==> npm install (one-time)"
  npm install --no-audit --no-fund --silent
fi
echo "==> glslang front-end check"
node validate_glsl.mjs "$ROOT"
