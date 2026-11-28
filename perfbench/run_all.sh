#!/usr/bin/env bash
# Repeatable benchmark/validation suite for the cross-runtime LUT work.
#
#   ./perfbench/run_all.sh
#
# Override environment-specific prerequisites as needed:
#   JDK17=/path/to/jdk17 JDK25=/path/to/jdk25 TEAVM_JARS=/path/to/jars \
#   WABT_MODULE=/path/to/wabt ./perfbench/run_all.sh
#
# Results are written to perfbench/results/*.txt (gitignored). This script does NOT run a full
# game/Chromium FPS benchmark or the license-restricted full client build: those are unavailable in
# a JRE-only sandbox and are documented explicitly in CROSS_RUNTIME.md.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
JDK17="${JDK17:-/tmp/jdk17/jdk4py/java-runtime}"
JDK25="${JDK25:-/tmp/jdkpkg/jdk4py/java-runtime}"
TEAVM_JARS="${TEAVM_JARS:-/tmp/teavm-jars}"
RESULTS="$HERE/results"
mkdir -p "$RESULTS"

echo "==> environment" | tee "$RESULTS/00-environment.txt"
{
  date -u
  uname -a
  node --version
  node -p "'V8 ' + process.versions.v8"
  python3 --version
  gcc --version | head -1
  "$JDK17/bin/java" -version 2>&1 | head -1
  "$JDK25/bin/java" -version 2>&1 | head -1
  echo "TEAVM_JARS=$TEAVM_JARS"
  echo "WABT_MODULE=${WABT_MODULE:-<build.sh will install/locate WABT>}"
} 2>&1 | tee -a "$RESULTS/00-environment.txt"

run_step() {
  local name="$1"; shift
  echo
  echo "==> $name" | tee "$RESULTS/$name.txt"
  "$@" 2>&1 | tee -a "$RESULTS/$name.txt"
}

cd "$ROOT"
run_step "01-unit-tests" python3 -m unittest discover -s tests -v
run_step "02-shell-syntax" bash -n perfbench/run_all.sh perfbench/run_bench.sh perfbench/compile_main.sh \
    perfbench/teavm/build_probe.sh perfbench/wasm/build.sh perfbench/native/build.sh \
    perfbench/shader/run_bench.sh perfbench/shader/run_validate.sh perfbench/jit_gc_profile.sh
run_step "03-python-syntax" python3 -m py_compile perfbench/gen_luts.py perfbench/native/gen_header.py \
    perfbench/teavm/fetch_teavm_jars.py tests/test_generated_luts.py
run_step "04-generator-check" bash -c \
    'python3 perfbench/gen_luts.py --check && python3 perfbench/native/gen_header.py --check && node perfbench/wasm/gen_wat.js --check'
run_step "05-compile-jdk17" env JAVA_HOME="$JDK17" ./perfbench/compile_main.sh
run_step "06-compile-jdk25" env JAVA_HOME="$JDK25" ./perfbench/compile_main.sh
run_step "07-jvm-bench-jdk17" env JAVA_HOME="$JDK17" PERFBENCH_REBUILD=1 ./perfbench/run_bench.sh perfbench.AllBench
run_step "08-jvm-bench-jdk25" env JAVA_HOME="$JDK25" PERFBENCH_REBUILD=1 ./perfbench/run_bench.sh perfbench.AllBench
run_step "09-teavm-build" env JAVA_HOME="$JDK17" TEAVM_JARS="$TEAVM_JARS" ./perfbench/teavm/build_probe.sh
run_step "10-teavm-v8-probe" node ./perfbench/teavm/run_probe.js
run_step "11-teavm-v8-startup" node ./perfbench/teavm/run_startup.js
run_step "12-v8-math" node ./perfbench/js/bench_v8_math.js
run_step "13-wasm-build" env WABT_MODULE="${WABT_MODULE:-}" ./perfbench/wasm/build.sh
run_step "14-wasm-bench" node ./perfbench/wasm/run_wasm.js
run_step "15-native-aot" ./perfbench/native/build.sh
run_step "16-shader-bench" ./perfbench/shader/run_bench.sh
run_step "17-shader-validate" ./perfbench/shader/run_validate.sh
run_step "18-hotspot-jit-jfr" env JDK="$JDK17" ./perfbench/jit_gc_profile.sh

echo
echo "All repeatable validation steps completed. Output: $RESULTS/*.txt" | tee "$RESULTS/99-summary.txt"
echo "Full Chromium / full-game FPS / full client build: UNAVAILABLE in this sandbox (documented)." | tee -a "$RESULTS/99-summary.txt"
