#!/usr/bin/env bash
# Benchmark harness for algorithm-level hotspot verification.
# Usage: ./perfbench/run_bench.sh [ClassName...]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/tmp/jdkpkg/jdk4py/java-runtime}"
JAVA="$JAVA_HOME/bin/java"
ECJ="$ROOT/buildtools/deps/eclipse-formatter.jar"
OUT="$HERE/build"
mkdir -p "$OUT"
SRCS="$HERE/src"
CP_MAIN="$OUT/main"
if [ ! -d "$CP_MAIN" ]; then
  echo "==> compiling main sources (once)"
  mkdir -p "$CP_MAIN"
  find "$ROOT/sources/main/java" -name '*.java' > "$OUT/main-srcs.txt"
  "$JAVA" -Xmx3g -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
    -proc:none -nowarn -proceedOnError -source 1.8 -target 1.8 -encoding UTF-8 \
    -d "$CP_MAIN" @"$OUT/main-srcs.txt" > /dev/null 2>&1 || true
fi
echo "==> compiling bench sources"
mkdir -p "$OUT/bench"
"$JAVA" -Xmx2g -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
  -proc:none -nowarn -source 1.8 -target 1.8 -encoding UTF-8 \
  -cp "$CP_MAIN" -d "$OUT/bench" $(find "$SRCS" -name '*.java') 2>&1 | grep -v "^$" || true
TARGETS=("$@")
if [ ${#TARGETS[@]} -eq 0 ]; then TARGETS=(perfbench.AllBench); fi
for t in "${TARGETS[@]}"; do
  echo "==> $t"
  "$JAVA" -Xmx2g -XX:+UseParallelGC -cp "$OUT/bench:$CP_MAIN" "$t"
done
