#!/usr/bin/env bash
# Benchmark + equivalence harness for the built-in PBR/deferred shader hot loops.
#
#   ./perfbench/shader/run_bench.sh
#
# There is no GPU / WebGL context in this sandbox (see perfbench/CROSS_RUNTIME.md), so this
# harness does not time the real pipeline. It replays the exact scalar float math of the old
# and the new GLSL bodies on a full 1920x1080 synthetic frame and reports:
#
#   * equivalence: discard-mask mismatches, ULP deviation, and how many frames' fragments would
#     change their RGBA16F value or their 8-bit post_tonemap output;
#   * an op census (pow / sqrt / texture fetches / divides per shader invocation);
#   * the x86 instruction count of the lighting function (a reproducible ALU proxy);
#   * the CPU time of the identical sequence (an upper bound for the GPU, because libm powf()
#     is far dearer than a GPU SFU pow()).
#
# Requires only gcc + libm + python3. The GLSL front-end check is a separate script
# (./perfbench/shader/run_validate.sh).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CC="${CC:-gcc}"
CFLAGS="${CFLAGS:--O2 -std=c11 -Wall -Wextra -Wno-unused-function}"
BUILD="$HERE/build"
mkdir -p "$BUILD"

echo "==> building ($CC $CFLAGS)"
# shellcheck disable=SC2086
$CC $CFLAGS "$HERE/bench_pbr.c" -o "$BUILD/bench_pbr" -lm
# shellcheck disable=SC2086
$CC $CFLAGS -DPBR_CENSUS "$HERE/bench_pbr.c" -o "$BUILD/bench_pbr_census" -lm
$CC -O2 -std=c11 -Wno-unused-function -DPBR_INSN -S "$HERE/bench_pbr.c" -o "$BUILD/bench_pbr.s"

echo
"$BUILD/bench_pbr"

echo
echo "==> op census (counters compiled in; op counts per shader invocation, per frame)"
"$BUILD/bench_pbr_census"

echo
echo "==> x86 instruction count of the straight-line light-loop BRDF (eaglercraftLighting)"
python3 - "$BUILD/bench_pbr.s" <<'PY'
import re, sys
text = open(sys.argv[1]).read().split('\n')
counts, cur, n = {}, None, 0
for line in text:
    m = re.search(r'#(LIGHTING_OLD|LIGHTING_NEW)_(BEGIN|END)', line)
    if m:
        if m.group(2) == 'BEGIN':
            cur, n = m.group(1), 0
        else:
            counts[cur] = n
            cur = None
        continue
    t = line.strip()
    if cur is not None and t and not t.startswith(('.', '#', ';;')):
        n += 1
old, new = counts.get('LIGHTING_OLD'), counts.get('LIGHTING_NEW')
print("  eaglercraftLighting old: %d instructions   new: %d instructions   (%.1f%% fewer)"
      % (old, new, 100.0 * (old - new) / old))
PY
