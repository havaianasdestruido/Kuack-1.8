#!/usr/bin/env bash
# HotSpot JIT/inlining + JFR allocation/GC profile for the shipped gamma LUT.
#   JDK17=/path/to/jdk17 ./perfbench/jit_gc_profile.sh
# Requires a compiled perfbench/build/{main,bench,stubs}; run ./perfbench/run_bench.sh first
# or use ./perfbench/run_all.sh. Output goes to perfbench/build/jit_gc_profile/.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
JDK="${JDK:-${JAVA_HOME:-/tmp/jdk17/jdk4py/java-runtime}}"
OUT="$HERE/build/jit_gc_profile"
mkdir -p "$OUT"
CP="$HERE/build/bench:$HERE/build/main:$HERE/build/stubs"
echo "=== HotSpot JIT / JFR profile ($("$JDK/bin/java" -version 2>&1 | head -1)) ==="
"$JDK/bin/java" -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining \
  -XX:StartFlightRecording=duration=12s,filename="$OUT/gamma.jfr",settings=profile \
  -Xmx2g -XX:+UseParallelGC -verbose:gc -cp "$CP" perfbench.TextureGammaBench \
  > "$OUT/jit_gc.log" 2>&1
echo
echo "-- JIT evidence (first relevant lines) --"
grep -E "MipmapGammaBlend::gamma22|MipmapGammaBlend::blend" "$OUT/jit_gc.log" | head -8 || true
grep -E "MipmapGammaBlend::gamma22.*inline \(hot\)" "$OUT/jit_gc.log" | head -4 || true
grep -E "java.lang.Math::pow.*intrinsic" "$OUT/jit_gc.log" | head -4 || true
echo
echo "-- JFR event summary --"
"$JDK/bin/jfr" summary "$OUT/gamma.jfr" | grep -E "Event Type|jdk.GarbageCollection|jdk.ObjectAllocationSample|jdk.ThreadAllocationStatistics|jdk.YoungGarbageCollection" || true
"$JDK/bin/jfr" print --events jdk.GarbageCollection,jdk.ThreadAllocationStatistics "$OUT/gamma.jfr" > "$OUT/jfr-events.txt"
echo
echo "-- main-thread allocation and GC pauses --"
grep -B3 'thread = "main"' "$OUT/jfr-events.txt" | grep 'allocated =' | head -1 || true
awk '/jdk.GarbageCollection/,/^}/ { if ($1=="duration") { gsub(/[^0-9.]/,"",$3); sum+=$3; n++ } } END { printf "  GarbageCollection events: %d, total pause %.2f ms\n", n, sum }' "$OUT/jfr-events.txt"
echo "  (JFR allocation is the whole TextureGammaBench workload, not per-lookup allocation;"
echo "   the hot filter source has no per-lookup allocation.)"
echo "logs: $OUT"
