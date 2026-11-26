# LUT design and validation contract

This document satisfies the controlling spec's "for every table" requirements. It covers the shipped
production tables, the existing Minecraft table that was deliberately reused rather than replaced,
and the two isolated PoC tables used only for WASM/native measurements. **Only the first table is
production code.** All generated sources are committed; generators are development-time only.

---

## 1. Production: `GeneratedGammaTables.java` — dual gamma decode tables

- **Generated file:** `sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java`
- **Generator:** `perfbench/gen_luts.py`
- **Regenerate / verify:**
  ```bash
  python3 perfbench/gen_luts.py --gamma-only
  python3 perfbench/gen_luts.py --check
  python3 -m unittest discover -s tests -v
  ./perfbench/run_bench.sh perfbench.VerifyEmbeddedLut perfbench.VerifyGammaLut
  ./perfbench/teavm/build_probe.sh && node ./perfbench/teavm/run_probe.js
  ```
- **Function:**
  - `GAMMA_2_2_JAVA[v]  = f32(pow(f64(f32(v) / f32(255)), 2.2))` — strict Java/HotSpot semantics.
  - `GAMMA_2_2_TEAVM[v] = f32(pow(f64(v) / f64(255), 2.2))` — TeaVM 0.9.2 / V8 double-lowered semantics.
- **Intended call sites:** `TextureUtil.generateMipmapData` → `MipmapGammaBlend.blend` /
  `blendComponent` → `MipmapGammaBlend.gamma22`. Reached from `EaglerTextureAtlasSprite` and
  `EaglerTextureAtlasSpritePBR` while building mip levels on texture load, resource reload and
  `F3+T`, not every frame.
- **Input domain / index mapping:** channel byte extracted as `(argb >> shift) & 255`; accessor masks
  `byteValue & 255`. Domain is exactly `{0..255}`; negative and out-of-range `int` inputs wrap to the
  same valid index. No floating-point input reaches the table.
- **Entries / precision:** 2 × 256 binary32 floats = 2,048 bytes of array data. Decimal Java literals
  are the shortest strings that round-trip to the exact float32 bits. Both arrays are
  `private static final`; `ACTIVE` is a single private final reference selected once at class init.
- **Error:** 0 ULP against the defining expression on each runtime. The two runtime tables differ on
  **132 / 256** entries by ≤ 1 ULP: max absolute **5.96e-8**, max relative **1.80e-7**. Each table is
  exact for the runtime that uses it; no output byte changes against that runtime's pre-change shipped
  behavior.
- **Negative / periodic / overflow / rounding / NaN / Inf / boundaries:** no periodicity. Negative
  inputs are wrapped by `& 255`. Integer overflow cannot affect the index. Rounding is IEEE-754
  round-to-nearest binary32 at the final cast; `Math.pow` is evaluated in double exactly as the
  original expression. NaN/Inf cannot reach a byte index. Boundaries: `v=0 → 0.0F`, `v=255 → 1.0F`;
  monotone over 0..255.
- **Approximation / interpolation:** none. This is an **Exact LUT** because the input domain is finite.
- **Memory / runtime allocation:** 2 KiB of primitive array data; no `Math.pow`, no loop, no static
  initializer computing entries, no file load, no boxed collection and no per-lookup allocation. Class
  init materializes the constant arrays and evaluates one platform-type branch; no array copy.
- **Source / compiled sizes:** Java source **10,667 bytes**; `GeneratedGammaTables.class` **6,691
  bytes**; `MipmapGammaBlend.class` **1,384 bytes**. TeaVM gamma-only JS: embedded bundle **178,309
  bytes** vs pre-change runtime-generated bundle **164,387 bytes** (+13,922 bytes). Compile/validation
  logs: `perfbench/results/09-teavm-build.txt`, `11-teavm-v8-startup.txt`.
- **Expected TeaVM transpilation:** `float[]` constants lower to
  `$rt_createFloatArrayFromData([...])` backed by `Float32Array`; `ADVANCED` dead-code elimination
  keeps the reachable arrays/accessor. The platform branch uses the existing
  `EagRuntime.getPlatformType()` / `EnumPlatformType.JAVASCRIPT` path; LWJGL returns `DESKTOP`.
- **Sharing / duplication / copying:** one owner class, two private arrays, one `ACTIVE` reference.
  `activeTable()` is package-internal and returns that reference to `MipmapGammaBlend`; callers store
  it in a `private static final` field and never mutate or copy it. This encapsulation matters because
  `static final` does not make array elements immutable.
- **Compatibility:** Java 8 source/target, no JVM-only API, no reflection in production, no thread
  use. Verified on HotSpot 17 / 25 and TeaVM 0.9.2 → Node/V8.

### Why two tables instead of one

TeaVM 0.9.2 emits plain JS doubles for Java `float` arithmetic; it does not insert `Math.fround`.
A single strict-Java table changed **84 / 203,072** browser blend cases; a single double-lowered table
changed **66 / 4,000,000** JVM differential cases. The final TeaVM probe confirms the dual design:

| Check | Result |
|---|---|
| `gamma_vs_prechange_browser_table` | 256 / 0 mismatches |
| `gamma_index_wrap` | 1,024 / 0 mismatches |
| `blend_vs_legacy_browser_table` | 203,072 / 0 mismatches |
| `VerifyEmbeddedLut` | 401,292 / 0 failures |
| `VerifyGammaLut` | 8,295,168 / 0 failures |

---

## 2. Existing reused table: `MathHelper.SIN_TABLE`

- **File:** `sources/main/java/net/minecraft/util/MathHelper.java` (already in HEAD; not modified here).
- **Function / call sites:** 65,536-entry `float[]` indexed by a 16-bit turn value, used by
  `MathHelper.sin/cos` and by the existing `Matrix4f.rotate` optimization (FINDINGS #10). Direct
  census: 398 `MathHelper.sin/cos` references and 305 `.rotate(` references in `sources/main/java`.
- **Domain / index mapping:** continuous radians → `(int)(angle * 10430.378F) & 0xFFFF`; periodic,
  quantized step. Negative angles wrap through two's-complement masking; NaN/Inf fall back to native
  `Math.sin/cos` in `Matrix4f.rotate` when `|angle| >= 200000` or the angle is not finite.
- **Entries / precision:** 65,536 binary32 floats, 256 KiB. Max angular table step 9.587e-5 rad;
  measured max absolute sine deviation **9.587269390264747e-5**.
- **Error / equivalence:** `VerifyMatrixRotate`: 204,335 checks / 0 failures; `trig_table_tolerance`:
  8,192 / 0; existing worldgen determinism preserved. This is a **Quantized LUT**, not an exact LUT.
- **Memory / initialization:** runtime static loop fills the array once; it already exists in HEAD and
  was reused rather than duplicated. `bench_v8_math.js` measures first-use
  `Float32Array(65536)+65536 Math.sin` at **561.8 µs**.
- **Why not source-embed it:** `perfbench/gen_luts.py --sin-only` generated 65,536 constants split
  over 16 classes (JVM constant-pool and `<clinit>` code-size limits). TeaVM full JS grew by
  **2,918,239 bytes** and parse/clinit from 3.71 / 7.84 ms to 22.37 / 42.75 ms. Rejected: startup,
  bundle size and i-cache cost outweigh the steady-state lookup win. See
  `perfbench/results/10-teavm-v8-probe.txt`.
- **Regeneration:** not applicable to production; the experiment generator command is
  `python3 perfbench/gen_luts.py --sin-only` and its output lives in gitignored
  `perfbench/teavm/build/generated/probe/`.

---

## 3. WASM PoC table: `perfbench/wasm/math.wat`

- **Generator / build / verify:**
  ```bash
  node ./perfbench/wasm/gen_wat.js
  node ./perfbench/wasm/gen_wat.js --check
  ./perfbench/wasm/build.sh          # WABT, development-time only
  node ./perfbench/wasm/run_wasm.js
  ```
- **Function / call sites:** `gamma_lut_f32` and `batch_lut_f32` in the isolated WASM module; JS calls
  them from `run_wasm.js`. Not called by the game.
- **Domain / index mapping:** `i32` input masked with `& 255`; 256 f32 constants at linear-memory
  offset 0. Input bytes live at offset 65,536; f64 outputs at 327,680; memory is 16 pages (1 MiB).
- **Entries / precision:** 256 binary32 floats = 1,024 bytes of data segment. Values are
  `Math.fround(Math.pow(i/255, 2.2))`, the TeaVM/browser variant.
- **Error:** 0 ULP against the WASM `gamma_pow_f32` baseline (which imports JS `Math.pow` because MVP
  WASM has no `f64.pow`, then demotes to f32). 1,794 checks including negative/out-of-range inputs,
  0 mismatches.
- **Negative / NaN / Inf / boundaries:** same byte-mask contract as production; NaN/Inf cannot reach
  the integer input. `v=0 → 0`, `v=255 → 1`.
- **Memory / allocation / startup:** 1,024 bytes of constants plus 1 MiB linear memory; no runtime
  table generation. Module **1,478 bytes**, WAT **6,135 bytes**; `WebAssembly.compile` 0.346 ms,
  instantiate 0.315 ms.
- **Transpilation / interop:** hand-written WAT compiled by WABT, not TeaVM output. Fine-grained
  WASM calls cost 2.755 ns/op vs 0.939 ns/op for a pure JS `Float32Array` lookup; coarse batching is
  required. This is a **Native/WASM specialization** PoC, not a production path.

---

## 4. Native AOT PoC table: `perfbench/native/gamma_table.h`

- **Generator / build / verify:**
  ```bash
  python3 ./perfbench/native/gen_header.py
  python3 ./perfbench/native/gen_header.py --check
  ./perfbench/native/build.sh
  ```
- **Function / call sites:** `batch_lut` / `batch_pow` in `bench_gamma.c`. Not called by the game.
- **Domain / index mapping:** `unsigned v = (unsigned) input_bytes[i] & 255u`; 256 C `static const
  float` constants in `.rodata`.
- **Entries / precision:** 256 binary32 floats = 1,024 bytes; hex float literals preserve exact bits.
- **Error:** 0 ULP against glibc `pow((double)v/255.0, 2.2)` narrowed to float; 1,538 checks including
  negative/out-of-range inputs, 0 mismatches.
- **Negative / NaN / Inf / boundaries:** byte mask; NaN/Inf cannot reach the integer input;
  `v=0 → 0`, `v=255 → 1`.
- **Memory / allocation / startup:** 1,024-byte `.rodata` table, no runtime initializer, no heap
  allocation per op. First `batch_lut(1)` 0.960 µs. Header **7,325 bytes**, C source **4,872 bytes**,
  binary **16,520 bytes**.
- **Deployment:** GCC `-O3 -march=native -std=c11`; no JNI/Panama, no GraalVM Native Image. This is an
  AOT **Native/WASM specialization** PoC only; the browser game cannot link this binary.

---

## 5. Rejected experiment: quantized gamma encode table

`perfbench/teavm/src/probe/EncodeQuantized.java` tabulates the continuous encode step
`pow(avg, 1/2.2)`. It is a **Quantized LUT**, not exact: `encode_quantized_bytes` reports
**57 / 4,096** output bytes differing by at most 1 versus the exact `Math.pow` path. Its 20.27× decode
speedup is therefore rejected: dark texels and exact rendering output would change. The production
encode remains `Math.pow`.

---

## 6. Determinism and immutability rules applied

- All committed generated sources are checked byte-for-byte by `./perfbench/run_all.sh` and
  `tests/test_generated_luts.py`.
- The generated Java source contains no `Math.pow`, `for (`, `static {` or `new float[...]` after its
  Javadoc; both arrays are actual numerical constants.
- No runtime dependency on Python, Node, WABT, GCC, TeaVM jars or generated files is introduced.
- Arrays are never exposed publicly. The one package-internal stable reference is documented and
  immutable by convention; no production code writes to it.
- Very large tables were compared against compact / symmetry / interpolation / integer alternatives
  and rejected with measured numbers (see `OPPORTUNITY_MATRIX.md`).
