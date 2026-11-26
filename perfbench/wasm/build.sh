#!/usr/bin/env bash
# Build the isolated WebAssembly gamma-decode PoC.
#   ./perfbench/wasm/build.sh
# Prerequisites: Node.js, npm registry (only to fetch the WABT dev tool), gcc not required.
# WABT is NOT a game dependency; it only compiles the checked-in math.wat to build/math.wasm.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$HERE/build"
node "$HERE/gen_wat.js"
mkdir -p "$HERE/build"
node "$HERE/gen_wat.js" --check
if [ -z "${WABT_MODULE:-}" ] && [ ! -d "$HERE/node_modules/wabt" ]; then
  echo "==> installing WABT (development-time only)"
  npm install --no-save --prefix "$HERE" wabt@1.0.36 > "$HERE/build/npm-install.log" 2>&1 || {
    echo "!!! npm install failed; see $HERE/build/npm-install.log" >&2; exit 1;
  }
fi
node "$HERE/compile_wat.js"
