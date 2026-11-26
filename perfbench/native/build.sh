#!/usr/bin/env bash
# Build + run the isolated native AOT gamma-decode prototype.
#   ./perfbench/native/build.sh
# Requires gcc (and libm). No Java, WASM, JNI, GraalVM or game dependencies.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CC="${CC:-gcc}"
CFLAGS="${CFLAGS:--O3 -march=native -Wall -Wextra -std=c11}"
mkdir -p "$HERE/build"
python3 "$HERE/gen_header.py"
python3 "$HERE/gen_header.py" --check
"$CC" $CFLAGS "$HERE/bench_gamma.c" -o "$HERE/build/bench_gamma" -lm
echo "==> generated gamma_table.h: $(wc -c < "$HERE/gamma_table.h") bytes"
echo "==> bench_gamma.c:            $(wc -c < "$HERE/bench_gamma.c") bytes"
echo "==> native binary:            $(wc -c < "$HERE/build/bench_gamma") bytes ($($CC --version | head -1))"
echo
"$HERE/build/bench_gamma"
