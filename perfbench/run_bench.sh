#!/usr/bin/env bash
# Benchmark harness for algorithm-level hotspot verification.
# Usage: ./perfbench/run_bench.sh [ClassName...]
#        PERFBENCH_REBUILD=1 ./perfbench/run_bench.sh ...   # force a main-source recompile
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA="$(command -v java)"
else
  echo "Error: java not found. Set JAVA_HOME or add java to PATH." >&2
  exit 1
fi
ECJ="$ROOT/buildtools/deps/eclipse-formatter.jar"
OUT="$HERE/build"
SRCS="$HERE/src"
CP_MAIN="$OUT/main"
STUBS="$OUT/stubs"
mkdir -p "$OUT"

# The JVM-side platform stubs are needed both to COMPILE and to RUN any code that
# touches EagRuntime / PlatformRuntime / a real World/Entity/Item.
if [ -n "${PERFBENCH_REBUILD:-}" ] || [ ! -f "$OUT/stubs.stamp" ] || \
   [ -n "$(find "$HERE/stubs" -name '*.java' -newer "$OUT/stubs.stamp" -print -quit)" ]; then
  echo "==> compiling platform stubs"
  rm -rf "$STUBS"
  mkdir -p "$STUBS"
  "$JAVA" -Xmx2g -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
    -proc:none -nowarn -source 1.8 -target 1.8 -encoding UTF-8 \
    -sourcepath "$ROOT/sources/main/java" -d "$STUBS" \
    $(find "$HERE/stubs" -name '*.java') > "$OUT/stubs-compile.log" 2>&1 || {
      echo "!!! stubs failed to compile (log: $OUT/stubs-compile.log):" >&2
      tail -n 30 "$OUT/stubs-compile.log" >&2
      exit 1
    }
  touch "$OUT/stubs.stamp"
fi

# Recompile when any production source is newer than the last successful compile stamp.
# (An unconditional cache would silently benchmark stale classes after a source edit.)
if [ -n "${PERFBENCH_REBUILD:-}" ] || [ ! -f "$OUT/main.stamp" ] || \
   [ -n "$(find "$ROOT/sources/main/java" -name '*.java' -newer "$OUT/main.stamp" -print -quit)" ]; then
  echo "==> compiling main sources"
  rm -rf "$CP_MAIN"
  mkdir -p "$CP_MAIN"
  find "$ROOT/sources/main/java" -name '*.java' > "$OUT/main-srcs.txt"
  "$JAVA" -Xmx3g -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
    -proc:none -nowarn -proceedOnError -source 1.8 -target 1.8 -encoding UTF-8 \
    -cp "$STUBS" -d "$CP_MAIN" @"$OUT/main-srcs.txt" > "$OUT/main-compile.log" 2>&1 || true
  touch "$OUT/main.stamp"
fi

echo "==> compiling bench sources"
mkdir -p "$OUT/bench"
"$JAVA" -Xmx2g -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
  -proc:none -nowarn -source 1.8 -target 1.8 -encoding UTF-8 \
  -cp "$CP_MAIN:$STUBS" -d "$OUT/bench" $(find "$SRCS" -name '*.java') > "$OUT/bench-compile.log" 2>&1 || true
if grep -q "ERROR in" "$OUT/bench-compile.log"; then
  echo "!!! bench harness failed to compile (log: $OUT/bench-compile.log):"
  grep -A2 "ERROR in" "$OUT/bench-compile.log" | head -20
  exit 1
fi
TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then TARGETS=(perfbench.AllBench); fi
for t in "${TARGETS[@]}"; do
  echo "==> $t"
  "$JAVA" -Xmx2g -XX:+UseParallelGC -cp "$OUT/bench:$CP_MAIN:$STUBS" "$t"
done
