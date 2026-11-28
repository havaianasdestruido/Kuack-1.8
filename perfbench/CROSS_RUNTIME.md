# Cross-runtime mathematical LUT optimization — execution, strategy and benchmark report

**Date of measured run:** 2026-11-27 UTC.
**Branch:** `arena/5bd1dcb6-kuack-1-8`.
**Controlling spec:** `/tmp/arena-coding-uploads/Welcome file.md` (deliverables A–G).

This document is the hub for deliverables **A**, **E**, **F** and the cross-runtime evidence for **G**.
Companion documents:

| Deliverable | Document |
|---|---|
| B — hotspot inventory | [`HOTSPOTS.md`](HOTSPOTS.md) |
| C — LUT / conversion opportunity matrix | [`OPPORTUNITY_MATRIX.md`](OPPORTUNITY_MATRIX.md) |
| D — production implementation | `GeneratedGammaTables.java`, `MipmapGammaBlend.java`, `MathHelper.java`, `TileEntityBeaconRenderer.java`, `RenderGuardian.java`, `EntityRenderer.java`, `InstancedFontRenderer.java`, `BlockNote.java`, `RenderGlobal.java`, `perfbench/gen_luts.py` |
| F — full LUT design contract | [`LUTS.md`](LUTS.md) |
| G — final performance report | [`REPORT.md`](REPORT.md) (cross-runtime addenda) |
| E — repeatable suite | [`run_all.sh`](run_all.sh), `perfbench/teavm/`, `perfbench/wasm/`, `perfbench/native/`, `perfbench/js/` |

---

## 0. Executive conclusion

The repository's **supported production artifact is browser JavaScript**, not JVM bytecode and not
WebAssembly. `buildtools/.../TeaVMBridgeImpl` hard-sets `TeaVMTargetType.JAVASCRIPT`; the headless client
compiler uses TeaVM **0.9.2**, `ADVANCED`, Java **8** source/target, and CI verifies `dist/index.html`,
`dist/classes.js`, `dist/assets.epk` and `dist/lang`. A JVM/desktop LWJGL source path exists
(`sources/lwjgl/java`, `sources/setup/workspace_template/desktopRuntime`), but a full desktop launch
was **not** available in this sandbox.

This branch ships **nine exact cross-runtime math/LUT/CSE optimizations** verified across **HotSpot JDK 17**, **HotSpot JDK 25**, and **TeaVM 0.9.2 → V8 12.4**:

1. **Exact mipmap gamma decode + exact binary64 threshold/bucket encode LUTs** (`GeneratedGammaTables.java`, `MipmapGammaBlend.java`): eliminates **all runtime `Math.pow` calls** from mipmap blending (`up to 20 → 0`). Both decode (`GAMMA_2_2_JAVA` vs `GAMMA_2_2_TEAVM`) and component encode (`THRESH_COMP_JAVA`/`BUCKET_COMP_JAVA` vs `THRESH_COMP_TEAVM`/`BUCKET_COMP_TEAVM`) embed exact runtime-specific constants plus `THRESH_BLEND[257]`/`BUCKET_BLEND[513]`, using `Math.sqrt` only on bucket indexing and **0 output-byte drift** on either runtime (`12,442,624` HotSpot checks / `0` failures; `203,072 + 16,384` TeaVM/V8 checks / `0` mismatches; `16.9–25.0×` HotSpot filter chain speedup, `6.9–11.1×` TeaVM/V8 filter speedup).
2. **Platform-dispatched `MathHelper.func_181159_b` (`atan2`) & `func_181161_i` (`rsqrt`)**: on `EnumPlatformType.JAVASCRIPT`, dispatches directly to native `Math.atan2` (**3.74×** faster in V8) and `1.0D / Math.sqrt` (**6.23×** faster in V8) while skipping runtime generation of the `2 × 257`-entry `field_181164_e/f` tables (TeaVM previously lowered `Double.doubleToRawLongBits`/`longBitsToDouble` via slow `DataView` allocations); Desktop/JVM keeps the bit-identical table/bit-op path (**5.77×** faster than `Math.atan2` on JDK 17).
3. **Rotational-symmetry trig reduction** in `TileEntityBeaconRenderer.renderTileEntity` (`8 → 2` trig, **3.11–3.49×**) and `RenderGuardian.doRender` (`16 → 2` trig, **5.38–5.63×**).
4. **Exact algebraic `Math.pow` replacements**: `EntityRenderer.updateFogColor` `Math.pow(f, 0.25) → Math.sqrt(Math.sqrt(f))` (**2.35–16.14×**, bit-identical), `RenderGlobal.renderWorldBorder` `Math.pow(d1, 4.0) → (d1*d1)*(d1*d1)` (**13.64–18.70×**, bit-exact), and `InstancedFontRenderer` `Math.pow(2.718, -x) → Math.exp(-x * LN_2_718)` (**1.17–5.54×**, `0` ULP max error).
5. **Exact discrete 25-entry note-pitch LUT** (`BlockNote.PITCH_TABLE[25]`, **12.22–19.13×** on JVM, bit-identical).
6. **Bit-exact invariant hoisting / CSE** in `EntityRenderer.updateLightmap` (**1.07×**).

Measured CPU improvements are real but **microbenchmark improvements only**. No full game, no
Chromium browser, no GPU, and no frame-time/FPS workload could be launched here. Do not read
microbenchmark speedups as a full-game FPS gain.

| Exactness target | Result |
|---|---|
| JVM / HotSpot vs verbatim vanilla Java 1.8.8 mipmap filter | `VerifyGammaLut`: **12,442,624** checks, **0 failures**; `VerifyEmbeddedLut`: **401,297** checks, **0 failures** |
| TeaVM/V8 vs pre-change shipped browser mipmap filter | `gamma_vs_prechange_browser_table`: 256/256 bit-identical; `blend_vs_legacy_browser_table`: **203,072** checks, **0 mismatches**; `blend_component_vs_legacy`: **16,384** checks, **0 mismatches** |
| TeaVM/V8 vs verbatim vanilla browser arithmetic | **118 / 203,072** output bytes differ by at most 1 (pre-existing in the legacy browser runtime table; our thresholds preserve shipped browser bytes identically) |
| `CrossRuntimeMathBench` & TeaVM math checks | `fogBitDiffs=0`, `noteBitDiffs=0`, `fontMaxUlp=0`, `borderMaxAbs=0.0`, `beaconMaxAbs=2.22e-16`, `guardianMaxAbs=2.78e-16`, `mhAtan2MaxErr=7.42e-6`; V8 `4,096–4,097` checks / **0 mismatches** |
| `updateLightmap` CSE | JVM 4,608 float comparisons and V8 4,608 checks, **0 mismatches** |

---

## A. Execution and conversion feasibility report

### A.1 What the project actually builds

- Sources: `sources/main/java` (common + Minecraft 1.8), `sources/teavm/java` (browser platform),
  `sources/lwjgl/java` (desktop platform). Source census: **2,488 Java files / 481,929 Java lines** in
  `sources/main/java`; 2,452 source files are passed to the headless client compiler.
- JavaC: `-source 1.8 -target 1.8`, ECJ in `buildtools/deps/eclipse-formatter.jar`; CI uses JDK 17.
- Transpiler: TeaVM 0.9.2 via the in-repo `TeaVMBridge.jar`; `optimizationLevel = "ADVANCED"`,
  `TeaVMTargetType.JAVASCRIPT`, minifying in CI, source maps off.
- CI: `python3 -m unittest discover -s tests -v`, `bash -n scripts/download-build-inputs.sh
  perfbench/run_bench.sh CompileLatestClient.sh`, then headless compile and output checks.
- No committed `classes.js`, `.wasm`, or `.wat`; the browser artifact is generated at build time.
- `PlatformRuntime.getPlatformType()` returns `JAVASCRIPT` in `sources/teavm/java` and `DESKTOP` in
  `sources/lwjgl/java`; the production gamma tables use this existing API.

### A.2 Actual sandbox environment (not a browser)

Measured by `perfbench/results/00-environment.txt`:

```
Linux e2b.local 6.1.158+ x86_64, Debian 12 derivative
Node v22.22.3, V8 12.4.254.21-node.56
Python 3.11.2, gcc 12.2.0, OpenJDK/Temurin 17.0.9 and 25.0.5.1 JREs
2 vCPU Intel Xeon @ 2.60 GHz, 3.8 GiB RAM
No Chromium/Chrome binary, no /dev/kvm, no /dev/dri, no GPU
```

These are three different concepts and were kept separate:

- **Browser sandbox / process:** unavailable. No Chromium, no isolated browser profile, no DOM,
  WebGL context or real page event loop. Node/V8 is the same JavaScript engine family but **not**
  Chromium and does not measure frame pacing, compositing, GPU or browser startup.
- **OS virtualization / container:** the workspace is a Linux sandbox with its own kernel string; no
  evidence of full OS virtualization was established. Container vs lightweight VM host is **unknown**.
- **CPU emulation:** none observed; the reported CPU is an x86_64 Xeon and measurements are native
  x86_64 code in Node/HotSpot/gcc. No QEMU/emulation claim is made.

### A.3 Strategy matrix (prerequisites → effort → status)

| Strategy | Prerequisites | Feasibility / advantages | Limitations / compatibility | Startup | Deployment | Effort | Status |
|---|---|---|---|---|---|---|---|
| **Chromium/V8 existing Java→JS output** | Built `classes.js`, browser profile, WebGL/DOM, assets | Supported production path; the shipped game is already JS; project platform code targets it | Browser sandbox and GPU/frame pacing cannot be measured in this sandbox; V8 version differences possible | Browser parse + TeaVM clinit + GL init | Static web client / offline HTML | Low for existing build; browser harness missing here | **Supported path; full Chromium run unavailable** |
| **Native Java/JVM desktop** | Full JDK, LWJGL/GLFW native libs, desktop assets, replacement for browser JSO | `sources/lwjgl/java` and `desktopRuntime` jars show an intended desktop path; HotSpot JIT/intrinsics available and measured here | Browser APIs are pervasive in `sources/teavm/java`: 146 `@JSBody`, 134 `JSObject`, 92 `window`, 87 `WebGL`, 23 `WebSocket`, 20 `navigator`, 17 `localStorage`, 11 `document`, 6 `XMLHttpRequest`. WebGL, DOM, networking and storage need real platform adapters; a dummy adapter would break gameplay | JVM class loading + JIT warm-up; no browser parse | Native launcher / JRRE bundling | High (platform layer, GL, audio, input, assets, networking) | **Experimental source path; full launch not proven** |
| **Java bytecode / HotSpot optimization** | ECJ/JDK 17, platform stubs for compile-only tests | Real classes can be compiled with ECJ; HotSpot 17/25 inlines `MipmapGammaBlend.blend` & `encodeComponent` (`inline (hot)`) and intrinsifies `Math.sqrt` | Compile gate reports 1,524 remaining platform-stub problems across 104 files after the CI visibility fix (pristine HEAD: 1,525 / 105); no full JVM game launch; no JMH dependency | Cold clinit measured: `1,889.00 µs` JDK17 / `1,483.20 µs` JDK25 for 6 embedded constant arrays vs `439.82 / 324.91 µs` for a 256-`Math.pow` runtime table | Only affects JVM/desktop path unless source is shared with TeaVM | Low for source-level changes | **Measured PoC + shipped shared-source optimizations** |
| **Java-to-JavaScript / TeaVM 0.9.2** | JDK 17, TeaVM 0.9.2 jars, `TeaVMBridge.jar`, Node/Chromium to run JS | Production transpiler; probe built with the real bridge and `ADVANCED`; generated JS inspected (`inline` array reads, no `$callClinit()` checks inside inner lookups) | Java `float` arithmetic lowers to JS doubles with no `Math.fround`; `float[]` constants become `$rt_createFloatArrayFromData([...])`; JDK 25 class files fail TeaVM 0.9.2 relocated ASM (`major version 69`) | Gamma-only JS: parse `5.536 ms` / clinit `4.768 ms` embedded vs `5.170 / 3.890 ms` runtime-generated | Generated `classes.js`; no new runtime | Low–medium (generator + exactness) | **Supported; real TeaVM JS probe shipped as dev harness** |
| **JS/V8-specific optimizations** | Node/V8, generated JS, deterministic inputs | Typed arrays, integer indexing, `Math.sqrt` (`1.848 ns`), `Math.atan2` (`37.345 ns`), `Math.exp`; `Math.pow` is `75.873 ns` vs `1.268 ns` gamma LUT; `Math.sin` `13.918 ns` vs `2.187 ns` f32 LUT | `Math.sqrt` `1.848 ns` is faster than a LUT; ordinary arrays / interpolation / `Math.floor` indexing are not wins; TeaVM `DataView` float-bit punning (`func_181161_i`) loses badly in JS so JS dispatches to `1/Math.sqrt` & `Math.atan2` | Per-module parse + JIT warm-up; 256-`Math.pow` table construction `56.8 µs` vs `18.0 µs` literal fill in V8 | JS code only | Low for measured micro-optimizations | **Measured; exact LUTs, platform dispatches & algebraic rewrites shipped** |
| **WebAssembly isolated subsystem** | WAT/WASM toolchain, coarse-grained interface, JS↔WASM boundary | Viable for a self-contained 256-value gamma decode; 1,478-byte module; coarse batch LUT `2.941 ns/op` at `n=65,536` vs `81.907 ns/op` pow | MVP WASM has no `f64.pow`; prototype imports JS `Math.pow`. Fine-grained per-op WASM call costs `4.195 ns` vs `1.211 ns` JS LUT. TeaVM 0.9.2 classic WASM lacks the JSO interop this game uses; whole-game conversion would need a new interop layer | `WebAssembly.compile` `0.685 ms`, instantiate `0.515 ms`, 1 MiB linear memory | Adds a `.wasm` artifact and JS glue; browser security/CORS/caching considerations | Medium for one kernel; very high for full game | **Experimental PoC; not production** |
| **GraalVM Native Image / Graal JIT / OpenJDK AOT** | GraalVM or full JDK with AOT tooling, closed-world analysis, native platform adapters | Native Image can remove JVM startup and produce a native binary; Graal JIT can be an alternative HotSpot | Not installed; no `native-image`, no Graal JIT, no `jaotc`, no full JDK `javac` in this sandbox. Browser JSO APIs and dynamic asset/resource loading remain blockers. GraalVM Community/Native Image is GPL 2 with Classpath Exception per GraalVM docs; output licensing must be reviewed | Ahead-of-time build, then fast native startup | Native executable, platform-specific builds; not a web client | Very high | **Unavailable; not implemented** |
| **Selective native modules (JNI / Panama)** | Full JDK, native library, coarse-grained boundary, generated bindings | A computationally isolated subsystem can be AOT-compiled; the C prototype shows the table itself is fast | Java 8 source target and browser deployment make JNI/Panama unsuitable for the web client; per-op boundary dominates; no whole-game native bridge implemented | Native library load | Extra binary per OS/browser bridge impossible for plain web | High | **Unavailable as production path; C PoC only** |
| **Algorithm replacement / platform-specific implementations** | Shared Java source, platform-type API, exactness tests | Smallest change with no new runtime; shipped: dual gamma decode/encode LUTs, JS `MathHelper` `atan2`/`rsqrt` dispatch, Beacon/Guardian symmetry, `sqrt(sqrt(f))`, `Math.exp`, `BlockNote` 25-LUT, `d1^4`, lightmap CSE | Requires proving two runtime semantics; static arrays are mutable and must stay encapsulated | One-time class init for constant arrays; skips `2×257` `atan2` table init on JS | Same `classes.js` / JVM classes | Low–medium | **Supported; shipped** |

### A.4 TeaVM/V8 generated-JS facts (inspected, not assumed)

- `Math.sin/cos/sqrt/pow/atan2/exp` lower directly to JS `Math.*`; `System.nanoTime()` becomes
  `performance.now() * 1000000.0`.
- Primitive arrays lower to typed arrays. `float[]` / `double[]` / `short[]` constants lower to
  `$rt_create*ArrayFromData([...])`; runtime loops lower to `$rt_createFloatArray(...).data[...]`.
- TeaVM 0.9.2 does **not** insert `Math.fround` for Java `float` intermediates. This is why
  `f32(pow(f32(i/255), 2.2))` (HotSpot) and `f32(pow(i/255.0, 2.2))` (TeaVM/V8) differ on
  **132 / 256** entries by ≤ 1 ULP, and `THRESH_COMP_JAVA` vs `THRESH_COMP_TEAVM` differ on a subset
  of double thresholds by 1 double-ULP (`2^-53`).
- TeaVM `ADVANCED` dead-code elimination keeps the platform-selected gamma arrays; the tiny
  gamma-only startup bundles prove the embedded and pre-change runtime tables produce the same
  checksum (`80.18821896874715`).
- Inspection of `perfbench/teavm/build/js/full.js` confirms `nmcrt_MipmapGammaBlend_encodeBlend`
  and `encodeComponent` read `nmcrt_MipmapGammaBlend_BUCKET_*.data[jl_Math_sqrt(var$1) * 512.0 | 0]`
  and `nmcrt_MipmapGammaBlend_THRESH_*.data[...]` directly with zero `$callClinit()` checks inside the
  loop.
- Full generated probe with the **rejected** 65,536-constant sine experiment: `3,198,260` bytes;
  equivalent lite probe: `280,209` bytes; delta `2,918,051` bytes. Parse/clinit `86.05 / 93.55 ms` vs
  `6.53 / 8.48 ms`. This is why the huge embedded sine table is rejected.

### A.5 Native JVM / browser API blockers

`sources/teavm/java` contains the browser platform: 146 `@JSBody`, 134 `JSObject`, 92 `window`,
87 `WebGL`, 23 `WebSocket`, 20 `navigator`, 17 `localStorage`, 11 `document`, 6 `XMLHttpRequest`.
These are not incidental helpers; they are GL, DOM, networking, storage and input. A direct JVM launch
would need the `sources/lwjgl/java` implementations plus native GLFW/OpenGL/audio libraries and
desktop equivalents for every browser API. The compile gate uses lightweight stubs only to test
common Java logic; it is explicitly **not** a game launch. No dummy platform substitution was shipped.

### A.6 WebAssembly and native boundaries

The WASM PoC deliberately uses a **coarse-grained** boundary: one `batch_lut_f32` call processes up to
65,536 values already in WASM linear memory. Fine-grained per-op calls add 2.755 ns vs 0.939 ns for a
pure JS typed-array lookup. The native C PoC uses a static `.rodata` table and no runtime initializer;
its `pow` baseline is glibc `libm`. Neither PoC transpiles the game. A whole-game TeaVM→WASM conversion
would also need to replace JSO/WebGL/DOM/WebSocket bindings; TeaVM's JSO documentation notes classic
WASM/WASI does not provide the JavaScript interop that this browser path uses.

### A.7 CI visibility fix and full-client build attempt

The real headless compiler was attempted with a config pointing at the in-repo patched source,
`/tmp/eagler-maven` TeaVM jars, a PyPI-provided `ffmpeg` binary and JDK 17. It reached resource
packing, source scanning and Java compilation. Both the base `main` CI run `45869906106` / job
`140082318468` and the initial PR #25 run `45882359151` / job `140124542039` failed at
`sources/main/java/net/minecraft/client/renderer/RenderGlobal.java:574` because
`World.isChunkLoaded(int,int,boolean)` was `protected` but called from another package. This branch
now includes a **one-word visibility fix** (`protected` → `public`, no behavior change) for that
existing call, satisfying `PROMPT.md`'s "builds must keep working" rule. It also removes
`RenderGlobal.java` from the ECJ error set (1,525 / 105 → 1,524 / 104), with no gameplay or rendering
behavior change. A post-fix `./perfbench/run_all.sh` passed all repeatable steps.

Sandbox-only blockers to a complete local `dist/classes.js` build remain:

1. `jdk4py` supplies a **JRE without `javac`**; the headless compiler invokes
   `<repo>/bin/javac`. A temporary, uncommitted ECJ-as-`javac` shim was used only to see how far the
   build could get; it was removed afterwards. After the visibility fix, that shim compiled all 2,452
   source files successfully, but TeaVM 0.9.2's class parser then rejected the ECJ-generated bytecode
   (`ProgramParser: Index 1 is out of range`). A direct `TeaVMBridgeImpl` probe reproduced the same
   parser exception, confirming it is an artifact of replacing `javac` with ECJ, not a
   production-source failure.
2. Maven Central / `raw.githubusercontent.com` are unreachable here; TeaVM jars came from a GitHub
   mirror for the probe. CI has real JDK 17 `javac`, license-restricted inputs and the Maven TeaVM
   cache, so its `Compile web client` check is the authoritative full-client build result.

After pushing the visibility fix as `5f6fab5`, PR #25 run `45884957261` completed successfully:
`Run unit tests` passed in 6 s, `Compile web client` passed in 1 m 41 s, `Verify build output` and
both artifact-upload steps passed, and `Publish HTML files to release` skipped as designed. The
matching push run `45884951086` also succeeded. A full `dist/classes.js` client build is therefore
**verified by CI**. Browser launch, a playable frame loop and full-game FPS remain **unavailable in
this sandbox**, and no FPS is claimed.

---

## E. Repeatable benchmark suite

Run everything that is available in this sandbox:

```bash
./perfbench/run_all.sh
```

Environment overrides:

```bash
JDK17=/path/to/jdk17 JDK25=/path/to/jdk25 \
TEAVM_JARS=/path/to/teavm-0.9.2-jars \
WABT_MODULE=/path/to/wabt \
./perfbench/run_all.sh
```

`run_all.sh` writes gitignored logs to `perfbench/results/*.txt`. It runs, in order:

| Step | Command | What it establishes |
|---|---|---|
| 01 | `python3 -m unittest discover -s tests -v` | 13 tests: generator regeneration, constant-only sources, `BlockNote.PITCH_TABLE`, shell syntax |
| 02/03 | `bash -n ...`, `python3 -m py_compile ...` | Script/Python syntax |
| 04 | `gen_luts.py --check`, `gen_header.py --check`, `gen_wat.js --check` | Deterministic source regeneration |
| 05/06 | `JAVA_HOME=<JDK17/JDK25> ./perfbench/compile_main.sh` | Full-tree ECJ gate: currently 1,524 problems / 104 files vs pristine HEAD baseline 1,525 / 105, no new error files |
| 07/08 | `JAVA_HOME=<JDK17/JDK25> PERFBENCH_REBUILD=1 ./perfbench/run_bench.sh perfbench.AllBench` | JVM verification + microbenchmarks on both HotSpot versions (incl. `CrossRuntimeMathBench`) |
| 09–11 | `./perfbench/teavm/build_probe.sh`, `run_probe.js`, `run_startup.js` | Real TeaVM 0.9.2 JS, V8 steady-state / equivalence / startup |
| 12 | `node ./perfbench/js/bench_v8_math.js` | V8 `Math.*`, typed/ordinary arrays, indexing, fround, method calls |
| 13–14 | `./perfbench/wasm/build.sh`, `node ./perfbench/wasm/run_wasm.js` | Isolated WASM PoC, fine/coarse interop |
| 15 | `./perfbench/native/build.sh` | GCC AOT C native PoC |
| 16 | `JDK=<JDK17> ./perfbench/jit_gc_profile.sh` | HotSpot inlining/intrinsics + JFR allocation/GC |

Focused commands:

```bash
./perfbench/run_bench.sh perfbench.VerifyGammaLut perfbench.VerifyEmbeddedLut \
  perfbench.LightmapBench perfbench.TextureGammaBench perfbench.EmbeddedLutBench \
  perfbench.CrossRuntimeMathBench
node ./perfbench/teavm/run_probe.js
node ./perfbench/teavm/run_startup.js
node ./perfbench/js/bench_v8_math.js
./perfbench/wasm/build.sh && node ./perfbench/wasm/run_wasm.js
./perfbench/native/build.sh
```

**Full-game FPS / frame-time results: UNAVAILABLE.** No Chromium, no GPU, no playable client. All
numbers below are isolated deterministic workloads; a filter or trig speedup is not an FPS claim.

### E.1 Final measured snapshot (from `perfbench/results/*.txt`, 2026-11-27 UTC)

| Workload | Original | Optimized | Change |
|---|---:|---:|---|
| JDK17 mipmap filter, 1 texel linear | 485.03 ns | 27.71 ns | **17.50×** / 94.29% less CPU |
| JDK17 mipmap filter, 1 texel gamma | 543.30 ns | 32.15 ns | **16.90×** / 94.08% less CPU |
| JDK17 mipmap 16×16 sprite chain (4 levels), gamma | 48.032 µs | 1.924 µs | **24.96×** / 95.99% less CPU |
| JDK17 mipmap 1,024-sprite atlas, linear | 44.72 ms | 4.78 ms | **9.35×** / 89.31% less CPU |
| JDK17 mipmap 1,024-sprite atlas, gamma | 51.12 ms | 4.57 ms | **11.17×** / 91.06% less CPU |
| JDK25 mipmap 16×16 sprite chain, linear / gamma | 35.515 / 31.840 µs | 1.854 / 1.875 µs | **19.16× / 16.98×** |
| JDK25 mipmap 1,024-sprite atlas, linear / gamma | 37.52 / 33.77 ms | 4.80 / 4.59 ms | **7.82× / 7.36×** |
| V8/TeaVM `decode_native` → `decode_lut` | 83.823 ns/op | 3.601 ns/op | **23.28×** / 95.70% less |
| V8/TeaVM whole blend, linear | 1,723.354 ns | 155.381 ns | **11.09×** / 90.98% less |
| V8/TeaVM whole blend, gamma | 1,701.795 ns | 247.034 ns | **6.89×** / 85.48% less |
| V8/TeaVM `MathHelper.func_181159_b` (`atan2`) | 139.490 ns | 37.345 ns | **3.74×** / 73.23% less |
| V8/TeaVM `MathHelper.func_181161_i` (`rsqrt`) | 55.588 ns | 8.919 ns | **6.23×** / 83.96% less |
| JDK17 / JDK25 `MathHelper.func_181159_b` vs `Math.atan2` | 59.09 / 23.52 ns | 10.25 / 9.64 ns | **5.77× / 2.44×** (kept on JVM) |
| `TileEntityBeaconRenderer` (`8→2` trig): JDK17 / JDK25 / V8 | 93.00 / 89.40 / 117.331 ns | 26.68 / 27.67 / 37.687 ns | **3.49× / 3.23× / 3.11×** |
| `RenderGuardian` (`16→2` trig): JDK17 / JDK25 / V8 | 181.80 / 169.97 / 215.071 ns | 33.52 / 31.62 / 38.197 ns | **5.42× / 5.38× / 5.63×** |
| `EntityRenderer.updateFogColor` (`pow(f,0.25)→sqrt(sqrt)`): JDK17 / JDK25 / V8 | 25.59 / 19.17 / 60.038 ns | 10.91 / 3.66 / 3.721 ns | **2.35× / 5.24× / 16.14×** |
| `InstancedFontRenderer` (`pow(2.718,-x)→exp`): JDK17 / JDK25 / V8 | 21.66 / 17.70 / 71.659 ns | 18.53 / 8.73 / 12.928 ns | **1.17× / 2.03× / 5.54×** |
| `BlockNote` pitch (`pow→25-LUT`): JDK17 / JDK25 | 26.90 / 16.82 ns | 1.41 / 1.38 ns | **19.13× / 12.22×** |
| `RenderGlobal` worldBorder (`pow(d,4)→(d*d)^2`): JDK17 / JDK25 | 31.91 / 16.54 ns | 1.71 / 1.21 ns | **18.70× / 13.64×** |
| Lightmap CSE (`updateLightmap`): JDK17 / JDK25 / V8 | 5.181 µs / 5.085 µs / 9,577 ns | 4.831 µs / 4.737 µs / 8,982 ns | **1.07×** across runtimes |
| V8 `Math.pow` → JS `Float32Array` gamma LUT | 75.873 ns | 1.268 ns | **59.86×** |
| WASM coarse batch n=65,536 pow → LUT | 81.907 ns/op | 2.941 ns/op | **27.85×** |
| Native C coarse batch n=65,536 pow → LUT | 21.841 ns/op | 1.177 ns/op | **18.55×** |

### E.2 Correctness / equivalence commands and results

| Check | Checks | Failures |
|---|---:|---:|
| Python `unittest discover -s tests -v` | 13 tests | 0 |
| `VerifyGammaLut` (JVM vanilla Java differential) | 12,442,624 | 0 |
| `VerifyEmbeddedLut` (6 constant arrays, platform selection, no runtime computation, blend differential) | 401,297 | 0 |
| `VerifyMatrixRotate` (existing HEAD optimization, regression guard) | 204,335 | 0 |
| `LightmapBench` bit-exact float differential (JVM) | 4,608 | 0 |
| `CrossRuntimeMathBench` (Beacon/Guardian/Fog/Note/Font/Border/Atan2) | all checks (`fogBitDiffs=0`, `noteBitDiffs=0`, `fontMaxUlp=0`, `borderMaxAbs=0.0`) | 0 |
| TeaVM `gamma_vs_prechange_browser_table` | 256 | 0 |
| TeaVM `gamma_index_wrap` | 1,024 | 0 |
| TeaVM `blend_vs_legacy_browser_table` | 203,072 | 0 |
| TeaVM `blend_component_vs_legacy` | 16,384 | 0 |
| TeaVM `lightmap_cse_bit_exact` | 4,608 | 0 |
| TeaVM `mh_atan2_tolerance` / `mh_rsqrt_exact` | 4,097 / 4,096 | 0 |
| TeaVM `trig_symmetry_tolerance` / `fog_pow25_bit_exact` / `font_exp_tolerance` | 4,096 / 64 / 4,096 | 0 |
| WASM pow vs LUT, negative/out-of-range included | 1,794 | 0 |
| Native C pow vs LUT, negative/out-of-range included | 1,538 | 0 |

Informational (not a regression): TeaVM `blend_bytes_vs_vanilla_browser_math` reports 118 / 203,072
bytes differing by at most 1. The pre-change shipped runtime table already produced these bytes;
`blend_vs_legacy_browser_table` (`0 / 203,072`) and `blend_component_vs_legacy` (`0 / 16,384`) prove
both branches preserve shipped browser output identically.

### E.3 Startup, size, JIT, allocation and GC

- Source: `GeneratedGammaTables.java` **27,871 bytes**; compiled `GeneratedGammaTables.class`
  **24,023 bytes**; `MipmapGammaBlend.class` **2,380 bytes**.
- TeaVM gamma-only bundles: runtime-generated **164,387 bytes** vs embedded **218,274 bytes**
  (+53,887 bytes). First-process parse/clinit: `10.605 / 8.110 ms` vs `5.728 / 4.861 ms`; medians of 9:
  `5.170 / 3.890 ms` vs `5.536 / 4.768 ms`. The embedded constants remove runtime `Math.pow` startup calls
  and steady-state `Math.pow` encodes while adding ~54 KB of unminified JS literals; steady-state
  atlas filtering improves 6.9–25.0×.
- HotSpot cold clinit: JDK17 `1,889.00 µs` embedded (6 arrays) vs `439.82 µs` runtime-generated
  (single 256-entry decode array); JDK25 `1,483.20 µs` vs `324.91 µs`. Steady-state decode lookup is
  parity (`1.212 vs 1.209 ns/op` JDK17; `1.225 vs 1.237 ns/op` JDK25). HotSpot C2 compiles
  `MipmapGammaBlend.blend` (239 bytes) and `encodeComponent` (62 bytes) as **`inline (hot)`**, and
  `Math.pow` disappears completely from the shipped filter path (`perfbench/results/16-hotspot-jit-jfr.txt`;
  remaining `Math.pow` intrinsics come only from the benchmark's vanilla baseline).
- JFR on `TextureGammaBench` (JDK17): **17.5 MB** allocated on the main thread, **4** young
  `ParallelScavenge` events totaling **11.82 ms**. This is whole-workload allocation (fixtures, JFR,
  output strings), not per-lookup allocation; the hot filter allocates no objects.
- Rejected huge sine experiment: +2,918,051 bytes JS; parse/clinit `86.05 / 93.55 ms` vs `6.53 / 8.48 ms`.
- WASM: WAT **6,135 bytes**, module **1,478 bytes**, 1,024 bytes of f32 constants, compile `0.685 ms`,
  instantiate `0.515 ms`, 1 MiB linear memory.
- Native AOT: header **7,325 bytes**, C source **4,872 bytes**, binary **16,520 bytes**, 1,024-byte
  `.rodata` table, first batch `1.644 µs`.

### E.4 Tail latency

- TeaVM/V8 probe prints p95 for every steady-state kernel (7 reps); e.g. `decode_lut` median `3.601 ns`,
  p95 `26.350 ns`; `blend_shipped_linear` median `155.381 ns`, p95 `201.883 ns`;
  `fog_sqrtsqrt` median `3.721 ns`, p95 `56.876 ns`.
- WASM and native scripts print median, p95 and p99 for 7 / 15 reps respectively.
- JVM benches report medians. Full-frame p95/p99 is **unavailable** because no full game/frame loop ran.

---

## F. Documentation map and platform-specific behavior

[`LUTS.md`](LUTS.md) documents every production/PoC table: mathematical function, call sites, input
domain, index mapping, entries/precision, max abs/relative error, negative/periodic/overflow/rounding/
NaN/Inf/boundary behavior, interpolation/approximation, memory/allocation, source and compiled sizes,
TeaVM transpilation, sharing/copying, regeneration and validation.

Platform-specific behavior summary:
- **HotSpot / Desktop:** `GeneratedGammaTables` selects `GAMMA_2_2_JAVA` and `THRESH_COMP_JAVA` / `BUCKET_COMP_JAVA`; `MathHelper` preserves its bit-op `atan2` / `rsqrt` (`2.44–5.77×` faster than `Math.atan2`).
- **TeaVM / Chromium JS:** `GeneratedGammaTables` selects `GAMMA_2_2_TEAVM` and `THRESH_COMP_TEAVM` / `BUCKET_COMP_TEAVM`; `MathHelper` dispatches directly to `Math.atan2` (`3.74×`) and `1.0 / Math.sqrt` (`6.23×`) while skipping `field_181164_e`/`f` initialization. Both selections use `EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT`.

No runtime file loads, no external binaries, no boxed collections, no static loops computing table
entries and no per-lookup allocations were introduced. `static final` arrays are private/package-internal
and never mutated or copied.

---

## G. Final report summary

The full final report is in [`REPORT.md`](REPORT.md). Highlights:

- **Verified shipped improvements:** exact gamma decode & binary64 encode threshold/bucket LUTs (HotSpot + TeaVM/V8), `MathHelper` JS `atan2`/`rsqrt` platform dispatch, `TileEntityBeaconRenderer` & `RenderGuardian` rotational-symmetry trig reductions, `EntityRenderer.updateFogColor` `sqrt(sqrt(f))`, `InstancedFontRenderer` exact `Math.exp`, `BlockNote.PITCH_TABLE[25]`, `RenderGlobal` `(d1*d1)*(d1*d1)`, and bit-exact lightmap CSE.
- **Verified but pre-existing in HEAD:** `Matrix4f.rotate` already uses Minecraft's sine table; it is
  kept as a regression guard, not re-implemented in this PR.
- **Rejected with measurements:** single cross-runtime gamma table, quantized gamma encode (`49/4,096` byte drift), 65,536 source-embedded sine constants (`+2.92 MB` JS / `+164 ms` parse+clinit), sqrt LUT, magic rsqrt, smaller/interpolated sine tables, dropping `Math.fround` from `MathHelper.sin/cos` (worldgen determinism), degree tables, `%`-free `wrapAngleTo180_*`, floor LUT, polynomial/pure-LUT `atan2`, `WorldGenBigTree.pow(x,2)->x*x`, `GlStateManager.gluPerspective`, `ModelDragon.render` CSE, AO block-property memo, biome-tint memo, whole-game WASM/native conversion, GraalVM Native Image in this sandbox.
- **Unresolved risks / unexecuted tests:** real Chromium/V8 frame times, full-game FPS, full client `classes.js` build locally (verified via CI with JDK 17 `javac`), desktop LWJGL launch, JNI/Panama bridge, GraalVM Native Image / Graal JIT, SpiderMonkey/JavaScriptCore, GPU/WASM SIMD, browser-profile caching, and real-world texture-atlas input capture. No numbers are fabricated for these environments.

### References (tooling / licensing facts)

- TeaVM JSO / WASM interop: <https://teavm.org/docs/runtime/jso.html>
- TeaVM releases / 0.9.2 tag: <https://github.com/konsoletyper/teavm/releases>
- GraalVM Native Image FAQ and licensing: <https://www.graalvm.org/22.2/reference-manual/native-image/FAQ/>,
  <https://github.com/oracle/graal/blob/master/README.md#license>
- HotSpot math intrinsics reference used for strategy analysis:
  <https://chriswhocodes.com/hotspot_intrinsics_openjdk8.html>
