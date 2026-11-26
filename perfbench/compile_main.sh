#!/usr/bin/env bash
# Compiles the whole main source tree with the repo's bundled ECJ compiler.
# Exits non-zero if any file fails to compile (ECJ reports 100 errors + counts).
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/tmp/jdkpkg/jdk4py/java-runtime}"
OUT="${1:-$HERE/build/main}"
mkdir -p "$OUT"
find "$ROOT/sources/main/java" -name '*.java' > "$HERE/build/main-srcs.txt"
LOG="$HERE/build/compile.log"
STUBS="$HERE/build/stubs"
mkdir -p "$STUBS"
"$JAVA_HOME/bin/java" -Xmx2g -cp "$ROOT/buildtools/deps/eclipse-formatter.jar" \
  org.eclipse.jdt.internal.compiler.batch.Main -proc:none -nowarn \
  -source 1.8 -target 1.8 -encoding UTF-8 \
  -sourcepath "$ROOT/sources/main/java" -d "$STUBS" \
  $(find "$HERE/stubs" -name '*.java') > /dev/null 2>&1
"$JAVA_HOME/bin/java" -Xmx3g -cp "$ROOT/buildtools/deps/eclipse-formatter.jar" \
  org.eclipse.jdt.internal.compiler.batch.Main -proc:none -nowarn -proceedOnError \
  -source 1.8 -target 1.8 -encoding UTF-8 -cp "$STUBS" -d "$OUT" @"$HERE/build/main-srcs.txt" > "$LOG" 2>&1
PROBLEMS=$(grep -Eo '^[0-9]+ problems? ' "$LOG" | tail -1 | awk '{print $1}')
PROBLEMS=${PROBLEMS:-0}
echo "ECJ problems: $PROBLEMS (log: $LOG)"
FILES=$(grep -oE "ERROR in [^ ]+" "$LOG" | sed 's|ERROR in ||' | sed "s|$ROOT/sources/main/java/||" | sort -u)
echo "--- files with errors (expected set is the platform-stub list, e.g. Display.java/EagRuntime.java/teavm):"
echo "$FILES" > "$HERE/build/errorfiles.txt"
wc -l < "$HERE/build/errorfiles.txt"
# The baseline is the pristine-HEAD error set, committed so the gate works in a fresh clone
# (build/ is gitignored). Regenerate with: git archive HEAD sources/main/java | tar -x -C /tmp/...
BASE="$HERE/build/errorfiles.baseline.txt"
if [ ! -f "$BASE" ] && [ -f "$HERE/errorfiles.baseline.txt" ]; then
  cp "$HERE/errorfiles.baseline.txt" "$BASE"
fi
if [ -f "$BASE" ]; then
  NEW=$(comm -13 "$BASE" "$HERE/build/errorfiles.txt")
  if [ -n "$NEW" ]; then
    echo "!!! NEW files with compile errors (regression):"
    echo "$NEW"
    exit 1
  fi
  echo "OK: no new files failing to compile."
fi
exit 0
