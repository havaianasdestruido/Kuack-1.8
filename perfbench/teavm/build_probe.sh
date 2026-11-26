#!/usr/bin/env bash
# Build the cross-runtime math probe with the project's real TeaVM 0.9.2 bridge.
#
#   ./perfbench/teavm/build_probe.sh
#   node ./perfbench/teavm/run_probe.js
#
# Prerequisites (all development-time, none shipped):
#   * JDK 17 (TeaVM 0.9.2's relocated ASM cannot parse JDK 21+ class files: "Unsupported class
#     file major version 65/69"). Set JAVA_HOME to a JDK 17 JRE (a JRE is enough; ECJ is bundled).
#   * TeaVM 0.9.2 jars in TEAVM_JARS (default /tmp/teavm-jars). Get them from Maven Central using
#     the coordinates in buildtools/.../TeaVMBinaries.java, or in a github.com-only sandbox:
#       python3 perfbench/teavm/fetch_teavm_jars.py /tmp/teavm-jars
#   * Node.js (V8) to run the generated JS. No Chromium binary is required.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
JARS="${TEAVM_JARS:-/tmp/teavm-jars}"
OUT="$HERE/build"
CLASSES="$OUT/classes"
GEN="$OUT/generated"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA="$(command -v java)"
else
  echo "Error: no java. Set JAVA_HOME to a JDK 17 installation." >&2
  exit 1
fi
"$JAVA" -version 2>&1 | head -1
if [ ! -d "$JARS" ]; then
  echo "Error: TEAVM_JARS=$JARS does not exist. See the header of this script." >&2
  exit 1
fi

ECJ="$ROOT/buildtools/deps/eclipse-formatter.jar"
BRIDGE="$ROOT/buildtools/TeaVMBridge.jar"

echo "==> generating the experiment sine table (development-time generator)"
python3 "$ROOT/perfbench/gen_luts.py" --sin-only

rm -rf "$CLASSES"
mkdir -p "$CLASSES" "$OUT/js"
# A second entry point that does not reference the 65536-constant experiment, so TeaVM's
# dead-code elimination produces an otherwise identical bundle WITHOUT the huge embedded table.
sed -e 's/class TeaVMBench/class TeaVMBenchLite/' \
    -e 's/private TeaVMBench()/private TeaVMBenchLite()/' \
    -e '/EmbeddedSinTable/d' -e '/embedded_sin_table/d' \
    "$HERE/src/probe/TeaVMBench.java" > "$GEN/probe/TeaVMBenchLite.java"

find "$ROOT/sources/main/java/net/minecraft/util/MathHelper.java" \
     "$ROOT/sources/main/java/net/minecraft/client/renderer/texture/MipmapGammaBlend.java" \
     "$ROOT/sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java" \
     "$ROOT/sources/main/java/net/lax1dude/eaglercraft/v1_8/internal/EnumPlatformType.java" \
     "$HERE/stubs" "$HERE/src" "$GEN" -name '*.java' > "$OUT/sources.txt"
echo "==> ECJ: $(wc -l < "$OUT/sources.txt") source files"
"$JAVA" -Xmx2g -cp "$ECJ" org.eclipse.jdt.internal.compiler.batch.Main \
  -proc:none -nowarn -source 1.8 -target 1.8 -encoding UTF-8 -cp "$BRIDGE" -d "$CLASSES" \
  @"$OUT/sources.txt" > "$OUT/ecj.log" 2>&1 || {
    echo "!!! ECJ failed:" >&2; tail -40 "$OUT/ecj.log" >&2; exit 1;
  }
if grep -q 'ERROR in' "$OUT/ecj.log"; then
  echo "!!! ECJ errors:" >&2; grep -A2 'ERROR in' "$OUT/ecj.log" | head -30 >&2; exit 1
fi

ALL_JARS="$(find "$JARS" -maxdepth 1 -name '*.jar' | sort | tr '\n' ':')"
echo "==> TeaVM 0.9.2 (project bridge, JAVASCRIPT / ADVANCED / unminified)"
"$JAVA" -Xmx2g -cp "$CLASSES:$ALL_JARS$BRIDGE" \
  probe.TeaVMProbe "$CLASSES" "$ALL_JARS" "$OUT/js" probe.TeaVMBench full.js > "$OUT/teavm-full.log" 2>&1 || {
    echo "!!! TeaVM (full) failed:" >&2; tail -40 "$OUT/teavm-full.log" >&2; exit 1;
  }
"$JAVA" -Xmx2g -cp "$CLASSES:$ALL_JARS$BRIDGE" \
  probe.TeaVMProbe "$CLASSES" "$ALL_JARS" "$OUT/js" probe.TeaVMBenchLite lite.js > "$OUT/teavm-lite.log" 2>&1 || {
    echo "!!! TeaVM (lite) failed:" >&2; tail -40 "$OUT/teavm-lite.log" >&2; exit 1;
  }
# Two tiny startup-only bundles: pre-change runtime-generated table vs shipped embedded tables.
"$JAVA" -Xmx2g -cp "$CLASSES:$ALL_JARS$BRIDGE" \
  probe.TeaVMProbe "$CLASSES" "$ALL_JARS" "$OUT/js" probe.GammaOnlyRuntime gamma_runtime.js > "$OUT/teavm-gamma-runtime.log" 2>&1 || {
    echo "!!! TeaVM (gamma runtime) failed:" >&2; tail -40 "$OUT/teavm-gamma-runtime.log" >&2; exit 1;
  }
"$JAVA" -Xmx2g -cp "$CLASSES:$ALL_JARS$BRIDGE" \
  probe.TeaVMProbe "$CLASSES" "$ALL_JARS" "$OUT/js" probe.GammaOnlyEmbedded gamma_embedded.js > "$OUT/teavm-gamma-embedded.log" 2>&1 || {
    echo "!!! TeaVM (gamma embedded) failed:" >&2; tail -40 "$OUT/teavm-gamma-embedded.log" >&2; exit 1;
  }
cp "$OUT/js/full.js" "$OUT/js/probe.js"
tail -3 "$OUT/teavm-full.log"
echo "==> full JS (with 65536 embedded constants): $(wc -c < "$OUT/js/full.js") bytes"
echo "==> lite JS (same probe, no embedded sine table): $(wc -c < "$OUT/js/lite.js") bytes"
echo "    delta: $(( $(wc -c < "$OUT/js/full.js") - $(wc -c < "$OUT/js/lite.js") )) bytes"
echo "    ECJ class output: $(find "$CLASSES" -name '*.class' | wc -l) classes, $(du -sh "$CLASSES" | cut -f1)"
echo "==> gamma-only JS: runtime $(wc -c < "$OUT/js/gamma_runtime.js") bytes, embedded $(wc -c < "$OUT/js/gamma_embedded.js") bytes"
