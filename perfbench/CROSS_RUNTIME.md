# Cross-runtime mathematical LUT optimization — execution, strategy and benchmark report

**Date of measured run:** 2026-11-26 UTC.
**Branch:** `arena/6c77f2b8-kuack-1-8`.
**Controlling spec:** `/tmp/arena-coding-uploads/Welcome file.md` (deliverables A–G).

This document is the hub for deliverables **A**, **E**, **F** and the cross-runtime evidence for **G**.
Companion documents:

| Deliverable | Document |
|---|---|
| B — hotspot inventory | [`HOTSPOTS.md`](HOTSPOTS.md) |
| C — LUT / conversion opportunity matrix | [`OPPORTUNITY_MATRIX.md`](OPPORTUNITY_MATRIX.md) |
| D — production implementation | `sources/main/java/.../MipmapGammaBlend.java`, `GeneratedGammaTables.java`, `EntityRenderer.java`, `perfbench/gen_luts.py` |
| F — full LUT design contract | [`LUTS.md`](LUTS.md) |
| G — final performance report | [`REPORT.md`](REPORT.md) (cross-runtime addendum) |
| E — repeatable suite | [`run_all.sh`](run_all.sh), `perfbench/teavm/`, `perfbench/wasm/`, `perfbench/native/`, `perfbench/js/` |

---

## 0. Executive conclusion

The repository's **supported production artifact is browser JavaScript**, not JVM bytecode and not
WebAssembly. `buildtools/.../TeaVMBridgeImpl` hard-sets `TeaVMTargetType.JAVASCRIPT`; the headless client
compiler uses TeaVM **0.9.2**, `ADVANCED`, Java **8** source/target, and CI verifies `dist/index.html`,
`dist/classes.js`, `dist/assets.epk` and `dist/lang`. A JVM/desktop LWJGL source path exists
(`sources/lwjgl/java`, `sources/setup/workspace_template/desktopRuntime`), but a full desktop launch
was **not** available in this sandbox.

This PR makes two small, exact production changes:

1. **Dual runtime-selected source-embedded gamma decode tables** for the mipmap box filter. The input
   is an 8-bit channel byte, so an exact 256-entry `float[]` is possible. Java/HotSpot and TeaVM/V8
   do **not** produce the same `float` bits for the original expression: TeaVM 0.9.2 lowers Java
   `float` intermediates as JS doubles without `Math.fround`. A single table therefore changes output
   bytes on one runtime. `GeneratedGammaTables.java` embeds **two** 256-entry constant arrays and
   selects one once via `EagRuntime.getPlatformType()`. No entry is computed at runtime.
2. **Bit-exact invariant hoisting / CSE** in `EntityRenderer.updateLightmap`: the 256-iteration loop
   no longer re-reads the brightness table or recomputes three frame-invariant float expressions, and
   the redundant `f5 = f4` assignment is explicit. This is algorithmic simplification, not a LUT.

Measured CPU improvements are real but **microbenchmark improvements only**. No full game, no
Chromium browser, no GPU, and no frame-time/FPS workload could be launched here. Do not read the
4–8× atlas-filter numbers as a full-game FPS gain.

| Exactness target | Result |
|---|---|
| JVM / HotSpot vs verbatim vanilla Java 1.8.8 filter | `VerifyGammaLut`: 8,295,168 checks, **0 failures**; `VerifyEmbeddedLut`: 401,292 checks, **0 failures** |
| TeaVM/V8 vs pre-change shipped browser table | `gamma_vs_prechange_browser_table`: 256/256 bit-identical; `blend_vs_legacy_browser_table`: 203,072 checks, **0 mismatches** |
| TeaVM/V8 vs verbatim vanilla browser arithmetic | **118 / 203,072** output bytes differ by at most 1. This difference already existed in the pre-change runtime-generated table; the PR preserves the shipped browser output rather than silently changing it |
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
| **Java bytecode / HotSpot optimization** | ECJ/JDK 17, platform stubs for compile-only tests | Real classes can be compiled with ECJ; HotSpot 17/25 inlines `MipmapGammaBlend.gamma22`, intrinsifies `Math.pow` and keeps lookup steady-state at parity with a runtime-generated table | Compile gate reports 1,524 remaining platform-stub problems across 104 files after the CI visibility fix (pristine HEAD: 1,525 / 105); no full JVM game launch; no JMH dependency | Cold clinit measured: 853.50 µs JDK17 / 826.02 µs JDK25 for embedded constants vs 202.75 / 199.07 µs for a 256-`Math.pow` runtime table | Only affects JVM/desktop path unless source is shared with TeaVM | Low for source-level changes | **Measured PoC + shipped shared-source optimization** |
| **Java-to-JavaScript / TeaVM 0.9.2** | JDK 17, TeaVM 0.9.2 jars, `TeaVMBridge.jar`, Node/Chromium to run JS | Production transpiler; probe built with the real bridge and `ADVANCED`; generated JS inspected | Java `float` arithmetic lowers to JS doubles with no `Math.fround`; `float[]` constants become `$rt_createFloatArrayFromData([...])` / `Float32Array`; JDK 25 class files fail TeaVM 0.9.2 relocated ASM (`major version 69`) | Gamma-only JS: parse 3.541 ms / clinit 2.675 ms embedded vs 3.465 / 2.670 ms runtime-generated | Generated `classes.js`; no new runtime | Low–medium (generator + exactness) | **Supported; real TeaVM JS probe shipped as dev harness** |
| **JS/V8-specific optimizations** | Node/V8, generated JS, deterministic inputs | Typed arrays, integer indexing and method inlining measured; `Math.pow` is ~40.6 ns vs ~0.84 ns gamma LUT; `Math.sin` ~9.97 ns vs ~1.39 ns f32 LUT | `Math.sqrt` 1.61 ns is already faster than any table; ordinary arrays / interpolation / `Math.floor` indexing are not wins; hidden-class/GC effects are workload-specific | Per-module parse + JIT warm-up; 256-`Math.pow` table construction 21.5 µs vs 5.9 µs literal fill in V8 | JS code only | Low for measured micro-optimizations | **Measured; only exact bounded-domain LUT shipped** |
| **WebAssembly isolated subsystem** | WAT/WASM toolchain, coarse-grained interface, JS↔WASM boundary | Viable for a self-contained 256-value gamma decode; 1,478-byte module; coarse batch LUT 0.680 ns/op at n=65,536 vs 43.165 ns/op pow | MVP WASM has no `f64.pow`; prototype imports JS `Math.pow`. Fine-grained per-op WASM call costs 2.755 ns vs 0.939 ns JS LUT. TeaVM 0.9.2 classic WASM lacks the JSO interop this game uses; whole-game conversion would need a new interop layer | `WebAssembly.compile` 0.346 ms, instantiate 0.315 ms, 1 MiB linear memory | Adds a `.wasm` artifact and JS glue; browser security/CORS/caching considerations | Medium for one kernel; very high for full game | **Experimental PoC; not production** |
| **GraalVM Native Image / Graal JIT / OpenJDK AOT** | GraalVM or full JDK with AOT tooling, closed-world analysis, native platform adapters | Native Image can remove JVM startup and produce a native binary; Graal JIT can be an alternative HotSpot | Not installed; no `native-image`, no Graal JIT, no `jaotc`, no full JDK `javac` in this sandbox. Browser JSO APIs and dynamic asset/resource loading remain blockers. GraalVM Community/Native Image is GPL 2 with Classpath Exception per GraalVM docs; output licensing must be reviewed | Ahead-of-time build, then fast native startup | Native executable, platform-specific builds; not a web client | Very high | **Unavailable; not implemented** |
| **Selective native modules (JNI / Panama)** | Full JDK, native library, coarse-grained boundary, generated bindings | A computationally isolated subsystem can be AOT-compiled; the C prototype shows the table itself is fast | Java 8 source target and browser deployment make JNI/Panama unsuitable for the web client; per-op boundary dominates; no whole-game native bridge implemented | Native library load | Extra binary per OS/browser bridge impossible for plain web | High | **Unavailable as production path; C PoC only** |
| **Algorithm replacement / platform-specific implementations** | Shared Java source, platform-type API, exactness tests | Smallest change with no new runtime; this is what shipped: dual constant arrays + lightmap CSE | Requires proving two runtime semantics; static arrays are mutable and must stay encapsulated | One-time class init for constant arrays | Same `classes.js` / JVM classes | Low–medium | **Supported; shipped** |

### A.4 TeaVM/V8 generated-JS facts (inspected, not assumed)

- `Math.sin/cos/sqrt/pow` lower directly to JS `Math.*`; `System.nanoTime()` becomes
  `performance.now() * 1000000.0`.
- Primitive arrays lower to typed arrays. `float[]` constants lower to
  `$rt_createFloatArrayFromData([...])`; runtime loops lower to `$rt_createFloatArray(...).data[...]`.
- TeaVM 0.9.2 does **not** insert `Math.fround` for Java `float` intermediates. This is why
  `f32(pow(f32(i/255), 2.2))` (HotSpot) and `f32(pow(i/255.0, 2.2))` (TeaVM/V8) differ on
  **132 / 256** entries by ≤ 1 ULP, max absolute 5.96e-8, max relative 1.80e-7.
- TeaVM `ADVANCED` dead-code elimination keeps the platform-selected gamma arrays; the tiny
  gamma-only startup bundles prove the embedded and pre-change runtime tables produce the same
  checksum (`80.18821896874715`).
- Full generated probe with the **rejected** 65,536-constant sine experiment: 3,138,737 bytes;
  equivalent lite probe: 220,498 bytes; delta 2,918,239 bytes. Parse/clinit 22.37 / 42.75 ms vs
  3.71 / 7.84 ms. This is why the huge embedded sine table is rejected.

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

The full `dist/classes.js` build and browser launch are therefore **unavailable in this sandbox**, not
claimed as passing locally. No full-game FPS is claimed.

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
| 01 | `python3 -m unittest discover -s tests -v` | 12 tests: generator regeneration, constant-only sources, shell syntax |
| 02/03 | `bash -n ...`, `python3 -m py_compile ...` | Script/Python syntax |
| 04 | `gen_luts.py --check`, `gen_header.py --check`, `gen_wat.js --check` | Deterministic source regeneration |
| 05/06 | `JAVA_HOME=<JDK17/JDK25> ./perfbench/compile_main.sh` | Full-tree ECJ gate: currently 1,524 problems / 104 files vs pristine HEAD baseline 1,525 / 105, no new error files |
| 07/08 | `JAVA_HOME=<JDK17/JDK25> PERFBENCH_REBUILD=1 ./perfbench/run_bench.sh perfbench.AllBench` | JVM verification + microbenchmarks on both HotSpot versions |
| 09–11 | `./perfbench/teavm/build_probe.sh`, `run_probe.js`, `run_startup.js` | Real TeaVM 0.9.2 JS, V8 steady-state / equivalence / startup |
| 12 | `node ./perfbench/js/bench_v8_math.js` | V8 `Math.*`, typed/ordinary arrays, indexing, fround, method calls |
| 13–14 | `./perfbench/wasm/build.sh`, `node ./perfbench/wasm/run_wasm.js` | Isolated WASM PoC, fine/coarse interop |
| 15 | `./perfbench/native/build.sh` | GCC AOT C native PoC |
| 16 | `JDK=<JDK17> ./perfbench/jit_gc_profile.sh` | HotSpot inlining/intrinsics + JFR allocation/GC |

Focused commands:

```bash
./perfbench/run_bench.sh perfbench.VerifyGammaLut perfbench.VerifyEmbeddedLut \
  perfbench.LightmapBench perfbench.TextureGammaBench perfbench.EmbeddedLutBench
node ./perfbench/teavm/run_probe.js
node ./perfbench/teavm/run_startup.js
node ./perfbench/js/bench_v8_math.js
./perfbench/wasm/build.sh && node ./perfbench/wasm/run_wasm.js
./perfbench/native/build.sh
```

**Full-game FPS / frame-time results: UNAVAILABLE.** No Chromium, no GPU, no playable client. All
numbers below are isolated deterministic workloads; a 4× filter speedup is not a 4× FPS claim.

### E.1 Final measured snapshot (from `perfbench/results/*.txt`)

These numbers are one captured `./perfbench/run_all.sh` snapshot. A later rerun after the CI
visibility fix passed every step and overwrote the gitignored `perfbench/results/*.txt` logs; on this
noisy 2-vCPU sandbox, nanosecond-scale medians shift slightly between runs. The checked-in sources,
commands, generators and equivalence counts are the reproducible artifacts, not any single timing
median. Full-game FPS remains unavailable.

| Workload | Original | Optimized | Change |
|---|---:|---:|---|
| JDK17 gamma decode, 1,024-sprite atlas, linear | 29.87 ms | 6.75 ms | 4.43× / 77.40% less CPU |
| JDK17 gamma decode, 1,024-sprite atlas, gamma | 35.97 ms | 4.76 ms | 7.56× / 86.77% less CPU |
| JDK25 gamma decode, 1,024-sprite atlas, linear | 26.91 ms | 5.82 ms | 4.62× / 78.37% less CPU |
| JDK25 gamma decode, 1,024-sprite atlas, gamma | 23.33 ms | 4.82 ms | 4.84× / 79.34% less CPU |
| V8/TeaVM `decode_native` → `decode_lut` | 42.894 ns/op | 2.038 ns/op | 21.05× / 95.25% less |
| V8/TeaVM whole blend, linear | 927.216 ns | 243.277 ns | 3.81× / 73.76% less |
| V8/TeaVM whole blend, gamma | 928.740 ns | 237.145 ns | 3.92× / 74.47% less |
| JDK17 lightmap update | 3.021 µs | 2.825 µs | 1.07× / 6.49% less |
| JDK25 lightmap update | 2.983 µs | 2.842 µs | 1.05× / 4.73% less |
| V8 lightmap update | 6,635.358 ns | 6,449.422 ns | 1.03× / 2.80% less |
| V8 `Math.pow` → JS `Float32Array` gamma LUT | 40.598 ns | 0.835 ns | 48.59× |
| WASM coarse batch n=65,536 pow → LUT | 43.165 ns/op | 0.680 ns/op | 63.49× |
| Native C coarse batch n=65,536 pow → LUT | 13.676 ns/op | 0.513 ns/op | 26.65× |

### E.2 Correctness / equivalence commands and results

| Check | Checks | Failures |
|---|---:|---:|
| `VerifyGammaLut` (JVM vanilla Java differential) | 8,295,168 | 0 |
| `VerifyEmbeddedLut` (both tables, platform selection, no runtime computation, blend differential) | 401,292 | 0 |
| `VerifyMatrixRotate` (existing HEAD optimization, regression guard) | 204,335 | 0 |
| `LightmapBench` bit-exact float differential (JVM) | 4,608 | 0 |
| TeaVM `gamma_vs_prechange_browser_table` | 256 | 0 |
| TeaVM `gamma_index_wrap` | 1,024 | 0 |
| TeaVM `blend_vs_legacy_browser_table` | 203,072 | 0 |
| TeaVM `lightmap_cse_bit_exact` | 4,608 | 0 |
| WASM pow vs LUT, negative/out-of-range included | 1,794 | 0 |
| Native C pow vs LUT, negative/out-of-range included | 1,538 | 0 |

Informational (not a regression): TeaVM `blend_bytes_vs_vanilla_browser_math` reports 118 / 203,072
bytes differing by at most 1. The pre-change shipped runtime table already produced these bytes;
`blend_vs_legacy_browser_table` is zero, so the PR does not change observable browser output.

### E.3 Startup, size, JIT, allocation and GC

- Source: `GeneratedGammaTables.java` **10,667 bytes**; compiled `GeneratedGammaTables.class`
  **6,691 bytes**; `MipmapGammaBlend.class` **1,384 bytes**.
- TeaVM gamma-only bundles: runtime-generated **164,387 bytes** vs embedded **178,309 bytes**
  (+13,922 bytes). First-process parse/clinit: 3.465 / 2.670 ms vs 3.541 / 2.675 ms; medians of 9:
  3.455 / 2.581 ms vs 3.571 / 2.675 ms. The embedded constants remove 256 `Math.pow` startup calls
  but add ~14 KB of JS literals; net isolated startup is **not** faster. This one-time cost is accepted
  because the spec mandates source-embedded constants and steady-state CPU is the objective.
- HotSpot cold clinit: JDK17 853.50 µs embedded vs 202.75 µs runtime-generated; JDK25 826.02 µs vs
  199.07 µs. Steady-state decode lookup is parity: JDK17 0.523 vs 0.523 ns/op; JDK25 0.522 vs 0.523
  ns/op. `MipmapGammaBlend.gamma22` is inlined by C1/C2 and `Math.pow` remains an intrinsic for the
  un-tabulated encode step (`perfbench/results/16-hotspot-jit-jfr.txt`).
- JFR on `TextureGammaBench` (JDK17): **17.5 MB** allocated on the main thread, **4** young
  `ParallelScavenge` events totaling **9.23 ms**. This is whole-workload allocation (fixtures, JFR,
  output strings), not per-lookup allocation; the hot filter allocates no objects.
- Rejected huge sine experiment: +2,918,239 bytes JS; parse/clinit 22.37 / 42.75 ms vs 3.71 / 7.84 ms.
- WASM: WAT **6,135 bytes**, module **1,478 bytes**, 1,024 bytes of f32 constants, compile 0.346 ms,
  instantiate 0.315 ms, 1 MiB linear memory.
- Native AOT: header **7,325 bytes**, C source **4,872 bytes**, binary **16,520 bytes**, 1,024-byte
  `.rodata` table, first batch 0.960 µs.

### E.4 Tail latency

- TeaVM/V8 probe prints p95 for every steady-state kernel (7 reps); e.g. `decode_lut` median 2.038 ns,
  p95 10.731 ns; `blend_shipped_gamma` median 237.145 ns, p95 267.813 ns.
- WASM and native scripts print median, p95 and p99 for 7 / 15 reps respectively.
- JVM benches report medians. Full-frame p95/p99 is **unavailable** because no full game/frame loop ran.

---

## F. Documentation map and platform-specific behavior

[`LUTS.md`](LUTS.md) documents every production/PoC table: mathematical function, call sites, input
domain, index mapping, entries/precision, max abs/relative error, negative/periodic/overflow/rounding/
NaN/Inf/boundary behavior, interpolation/approximation, memory/allocation, source and compiled sizes,
TeaVM transpilation, sharing/copying, regeneration and validation.

Platform-specific behavior in one sentence: **HotSpot uses `GAMMA_2_2_JAVA`** (strict Java float
semantics), **TeaVM/Chromium uses `GAMMA_2_2_TEAVM`** (double-lowered Java float semantics), and both
are selected once by `EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT`.

No runtime file loads, no external binaries, no boxed collections, no static loops computing table
entries and no per-lookup allocations were introduced. `static final` arrays are mutable; the arrays
are private, `activeTable()` is package-internal, and `MipmapGammaBlend` keeps one final reference
without copying or mutating it.

---

## G. Final report summary

The full final report is in [`REPORT.md`](REPORT.md). Highlights:

- **Verified shipped improvements:** exact gamma decode LUT (JVM + V8), bit-exact lightmap CSE.
- **Verified but pre-existing in HEAD:** `Matrix4f.rotate` already uses Minecraft's sine table; it is
  kept as a regression guard, not re-implemented in this PR.
- **Rejected with measurements:** single cross-runtime gamma table, quantized gamma encode, 65,536
  source-embedded sine constants, sqrt LUT, magic rsqrt, smaller/interpolated sine tables, dropping
  `Math.fround` from `MathHelper.sin/cos` (worldgen determinism), degree tables, `%`-free
  `wrapAngleTo180_*`, floor LUT, `atan2` tables, block-property AO memo, `pow(2.718,-x)`→`exp`,
  whole-game WASM/native conversion, GraalVM Native Image in this sandbox.
- **Unresolved risks / unexecuted tests:** real Chromium/V8 frame times, full-game FPS, full client
  `classes.js` build with a real JDK/ffmpeg and license-restricted inputs, desktop LWJGL launch, JNI/
  Panama bridge, GraalVM Native Image / Graal JIT, SpiderMonkey/JavaScriptCore, GPU/WASM SIMD,
  browser-profile caching, and real-world texture-atlas input capture. No numbers are fabricated for
  these environments.

### References (tooling / licensing facts)

- TeaVM JSO / WASM interop: <https://teavm.org/docs/runtime/jso.html>
- TeaVM releases / 0.9.2 tag: <https://github.com/konsoletyper/teavm/releases>
- GraalVM Native Image FAQ and licensing: <https://www.graalvm.org/22.2/reference-manual/native-image/FAQ/>,
  <https://github.com/oracle/graal/blob/master/README.md#license>
- HotSpot math intrinsics reference used for strategy analysis:
  <https://chriswhocodes.com/hotspot_intrinsics_openjdk8.html>
