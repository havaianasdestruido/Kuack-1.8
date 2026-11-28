# LUT design and validation contract

This document satisfies the controlling spec's "for every table" requirements. It covers the shipped
production tables, the existing Minecraft table that was deliberately reused rather than replaced,
and the two isolated PoC tables used only for WASM/native measurements. **Only sections 1–3 are
production code.** All generated sources are committed; generators are development-time only.

---

## 1. Production: `GeneratedGammaTables.java` — dual gamma decode tables & exact binary64 encode threshold/bucket tables

- **Generated file:** `sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java`
- **Generator:** `perfbench/gen_luts.py`
- **Regenerate / verify:**
  ```bash
  python3 perfbench/gen_luts.py --gamma-only
  python3 perfbench/gen_luts.py --check
  python3 -m unittest discover -s tests -v
  ./perfbench/run_bench.sh perfbench.VerifyEmbeddedLut perfbench.VerifyGammaLut perfbench.TextureGammaBench
  ./perfbench/teavm/build_probe.sh && node ./perfbench/teavm/run_probe.js
  ```
- **Functions & exact arithmetic semantics:**
  1. **Decode LUTs (2 × 256 `float[]`):**
     - `GAMMA_2_2_JAVA[v]  = f32(pow(f64(f32(v) / f32(255)), 2.2))` — strict Java/HotSpot semantics.
     - `GAMMA_2_2_TEAVM[v] = f32(pow(f64(v) / f64(255), 2.2))` — TeaVM 0.9.2 / V8 double-lowered semantics.
  2. **Exact binary64 encode threshold & `sqrt(x)` bucket tables (`encodeBlend` & `encodeComponent`):**
     - Vanilla `blend` (gamma branch) computes `(int) (Math.pow(avg, 1.0 / 2.2) * 255.0)`.
     - Vanilla `blendComponent` (linear branch) computes `(int) ((float) Math.pow(avg, 1.0 / 2.2) * 255.0F)`.
     - Notice `(float) Math.pow(x, 1.0 / 2.2) * 255.0F` rounds twice to binary32, so its integer-transition thresholds can differ by 1 double-ULP (`THRESH_COMP_JAVA` vs `THRESH_COMP_TEAVM`), whereas `(int) (Math.pow(x, 1.0 / 2.2) * 255.0)` stays in binary64 (`THRESH_BLEND[257]`).
     - Rather than approximating `pow(x, 1/2.2)` (which drops LSBs in dark texels — see §6), `GeneratedGammaTables` precomputes the **exact IEEE-754 binary64 threshold `t[k]`** where the integer output jumps from `k - 1` to `k` for `k = 1..255` (`t[0] = 0.0`, `t[256] = 1.0`), plus a **513-entry O(1) acceleration index** `BUCKET_*[513]` indexed by `(int) (Math.sqrt(x) * 512.0D)` (`512` equal intervals in `sqrt(x) ≈ x^0.5`, which is nearly linear in `x^(1/2.2)`!).
     - If all inputs in bucket `b` map to one output `k`, `BUCKET[b] = k` (`>= 0`, **zero further loads or branches**). If a threshold crosses inside bucket `b`, `BUCKET[b] = -1 - k` (`< 0`), and one branch `if (x >= thresh[k + 1]) k = k + 1` resolves the exact threshold! (Since `512` `sqrt(x)` buckets are strictly finer than the minimum spacing between consecutive thresholds `t[k]` and `t[k+1]`, at most **one** threshold ever falls inside a bucket!)
- **Intended call sites:** `TextureUtil.generateMipmapData` → `MipmapGammaBlend.blend` /
  `blendComponent` / `gamma22` / `encodeBlend` / `encodeComponent`. Reached from
  `EaglerTextureAtlasSprite` and `EaglerTextureAtlasSpritePBR` while building mip levels on texture
  load, resource reload and `F3+T`, not every frame.
- **Input domain / index mapping:**
  - Decode: channel byte `(argb >> shift) & 255`; accessor masks `byteValue & 255`. Domain is `{0..255}`.
  - Encode: continuous `x` (average of 4 decoded floats). `x <= 0.0` returns `0`; `x >= 1.0` returns `255`; otherwise `0 < x < 1` maps `sqrt(x) * 512.0` into `{0..511}` (`BUCKET_*[513]`, slot `512` covers `1.0 - 2^-53` when `Math.sqrt(x) * 512.0` rounds to `512`).
- **Entries / precision:**
  - Decode: `2 × 256` `float` = 2,048 bytes.
  - Encode: `3 × 257` `double` thresholds (`THRESH_BLEND`, `THRESH_COMP_JAVA`, `THRESH_COMP_TEAVM`) + `3 × 513` `short` buckets (`BUCKET_BLEND`, `BUCKET_COMP_JAVA`, `BUCKET_COMP_TEAVM`) = `6,168 + 3,078 = 9,246` bytes (`~11.0 KiB` total primitive constant data).
- **Exactness proof across all representable inputs & platform differences:**
  - **HotSpot JDK 17 / JDK 25:** `THRESH_BLEND` and `THRESH_COMP_JAVA` match HotSpot `Math.pow` bit-for-bit on all 255 thresholds; verified by `VerifyGammaLut` (**12,442,624 checks / 0 failures**) and `VerifyEmbeddedLut` (**401,297 checks / 0 failures**).
  - **Binary32 `[0, 1]` sweep (`1,065,353,217` floats):** the nearest binary32 float to any threshold is **52,014** double-ULPs from `THRESH_BLEND[1..254]` (`k=21`) and **1,539,787** double-ULPs from `THRESH_COMP[1..255]` (`k=31`).
  - **Exhaustive 4-texel TeaVM tuple proof (`256^4 = 4,294,967,296` tuples):** left-associated JS double accumulation `((g0 + g1) + g2) + g3 * 0.25` was checked exhaustively in C against V8 `Math.pow`; no 4-texel average falls within `52,014` double-ULPs of any threshold (while Python `libm` and V8 differ by at most `±1` double-ULP at 62/255 `THRESH_BLEND` points). Consequently `blend_vs_legacy_browser_table` reports **203,072 / 0 mismatches** and `blend_component_vs_legacy` reports **16,384 / 0 mismatches** in V8!
- **Negative / periodic / overflow / rounding / NaN / Inf / boundaries:**
  - Decode: `& 255` wraps negative/out-of-range integers safely; `0 → 0.0F`, `255 → 1.0F`; monotone.
  - Encode: `x <= 0.0` (and negative inputs) return `0`; `x >= 1.0` return `255`; `NaN` falls through both bounds to `0` or guard; `BUCKET[512]` handles `Math.sqrt(1.0 - 2^-53) * 512.0 == 512.0` without out-of-bounds access.
- **Memory / runtime allocation:** ~11 KiB of primitive array data; zero `Math.pow`, zero `Math.sqrt`, zero loops, zero `static {}` computations, zero file loads, and zero per-lookup allocations in `GeneratedGammaTables.java`. `MipmapGammaBlend` accesses `GAMMA`, `THRESH_*`, and `BUCKET_*` via `private static final` references selected once at `<clinit>`. (HotSpot runtime `encode*` uses `Math.sqrt` intrinsic once per encoded component; TeaVM uses `Math.sqrt` once per encoded component, which V8 runs in ~1.8 ns.)
- **Source / compiled sizes:** `GeneratedGammaTables.java` **27,871 bytes**; `GeneratedGammaTables.class` **24,023 bytes**; `MipmapGammaBlend.class` **2,380 bytes**. TeaVM gamma-only startup bundle: embedded **218,274 bytes** vs pre-change runtime **164,387 bytes** (`+53,887` bytes unminified JS; parse/clinit median `5.536 / 4.768 ms` vs `5.170 / 3.890 ms`).
- **Expected TeaVM transpilation:** inspected directly in `perfbench/teavm/build/js/full.js`: `GeneratedGammaTables` emits `$rt_createFloatArrayFromData([...])`, `$rt_createDoubleArrayFromData([...])`, and `$rt_createShortArrayFromData([...])`. Inside `MipmapGammaBlend.encodeComponent` / `encodeBlend`, `THRESH_*` and `BUCKET_*` compile to inline `.data[jl_Math_sqrt(var$1) * 512.0 | 0]` typed-array reads with zero `$callClinit()` checks inside the loop.
- **Compatibility:** Java 8 source/target, no JVM-only API, no reflection in production, no thread use. Verified on HotSpot 17 / 25 and TeaVM 0.9.2 → Node/V8.

---

## 2. Production: `BlockNote.PITCH_TABLE` — 25-note discrete pitch table

- **File:** `sources/main/java/net/minecraft/block/BlockNote.java`
- **Function / call site:** `PITCH_TABLE[note]` replaces `(float) Math.pow(2.0D, (double) (note - 12) / 12.0D)` in `BlockNote.onBlockEventReceived` (`note` clamped/guarded to `0..24`, clamped via `Math.max(0, Math.min(24, eventParam))`).
- **Entries / precision:** 25 `private static final float` constants (`100` bytes), exact shortest round-tripping literals from `(float) Math.pow(2.0D, (double)(j - 12) / 12.0D)`.
- **Error / equivalence:** **0 ULP** (bit-exact across all 25 entries vs `Math.pow` on JDK 17, JDK 25, and V8/TeaVM; verified by `CrossRuntimeMathBench` `noteBitDiffs=0` and `tests/test_generated_luts.py::test_note_pitch_table_is_25_constants`).
- **Boundaries & safety:** explicit clamp `if (note < 0) note = 0; else if (note > 24) note = 24;` prevents out-of-bounds access on malformed packets/events (`note = 0 → 0.5F`, `12 → 1.0F`, `24 → 2.0F`). No allocation, no runtime computation.

---

## 3. Existing reused table: `MathHelper.SIN_TABLE` (and `func_181159_b` JS dispatch)

- **File:** `sources/main/java/net/minecraft/util/MathHelper.java`
- **Function / call sites:** 65,536-entry `float[]` indexed by a 16-bit turn value, used by `MathHelper.sin/cos` and `Matrix4f.rotate` (FINDINGS #10). Direct census: 398 `MathHelper.sin/cos` references and 305 `.rotate(` references in `sources/main/java`.
- **Domain / index mapping:** continuous radians → `(int)(angle * 10430.378F) & 0xFFFF`; periodic, quantized step. Negative angles wrap through two's-complement masking; NaN/Inf fall back to native `Math.sin/cos` in `Matrix4f.rotate` when `|angle| >= 200000` or the angle is not finite.
- **Entries / precision:** 65,536 binary32 floats, 256 KiB. Max angular table step 9.587e-5 rad; measured max absolute sine deviation **9.587269390264747e-5**.
- **Error / equivalence:** `VerifyMatrixRotate`: 204,335 checks / 0 failures; `trig_table_tolerance`: 8,192 / 0; existing worldgen determinism preserved. This is a **Quantized LUT**, not an exact LUT.
- **Platform dispatch update (`func_181159_b` & `func_181161_i`):** On `EnumPlatformType.JAVASCRIPT` (`IS_JAVASCRIPT = EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT`), `func_181159_b` (`atan2`) dispatches directly to `Math.atan2(y, x)` (`3.11–3.74×` faster than the bit-twiddling/table approximation on V8) and `func_181161_i` (`rsqrt`) dispatches to `1.0D / Math.sqrt(x)` (`6.23–8.51×` faster on V8), while skipping runtime generation of the 257-entry `field_181164_e`/`field_181165_f` tables in JS. Desktop/JVM continues using the exact bit-identical `field_181164_e`/`field_181165_f` + magic `rsqrt` path (`5.77×` faster than `Math.atan2` on JDK 17).
- **Why not source-embed `SIN_TABLE`:** `perfbench/gen_luts.py --sin-only` generated 65,536 constants split over 16 classes. TeaVM full JS grew by **2,918,051 bytes** (`3,198,260` vs `280,209` bytes) and startup parse/clinit grew from `6.53 / 8.48 ms` to `86.05 / 93.55 ms`. Rejected: startup, bundle size and i-cache cost outweigh the steady-state lookup win.

---

## 4. WASM PoC table: `perfbench/wasm/math.wat`

- **Generator / build / verify:**
  ```bash
  node ./perfbench/wasm/gen_wat.js
  node ./perfbench/wasm/gen_wat.js --check
  ./perfbench/wasm/build.sh          # WABT, development-time only
  node ./perfbench/wasm/run_wasm.js
  ```
- **Function / call sites:** `gamma_lut_f32` and `batch_lut_f32` in the isolated WASM module; JS calls them from `run_wasm.js`. Not called by the game.
- **Domain / index mapping:** `i32` input masked with `& 255`; 256 f32 constants at linear-memory offset 0. Input bytes live at offset 65,536; f64 outputs at 327,680; memory is 16 pages (1 MiB).
- **Entries / precision:** 256 binary32 floats = 1,024 bytes of data segment. Values are `Math.fround(Math.pow(i/255, 2.2))`, the TeaVM/browser variant.
- **Error:** 0 ULP against `gamma_pow_f32` (imports JS `Math.pow` because MVP WASM has no `f64.pow`). 1,794 checks including negative/out-of-range inputs, 0 mismatches.
- **Memory / allocation / startup:** Module **1,478 bytes**, WAT **6,135 bytes**; `WebAssembly.compile` 0.685 ms, instantiate 0.515 ms. Fine-grained WASM calls cost `4.195 ns/op` vs `1.211 ns/op` for JS `Float32Array` LUT; coarse `n=65,536` batch reaches `2.941 ns/op` vs `81.907 ns/op` (`27.85×`).

---

## 5. Native AOT PoC table: `perfbench/native/gamma_table.h`

- **Generator / build / verify:**
  ```bash
  python3 ./perfbench/native/gen_header.py
  python3 ./perfbench/native/gen_header.py --check
  ./perfbench/native/build.sh
  ```
- **Function / call sites:** `batch_lut` / `batch_pow` in `bench_gamma.c`. Not called by the game.
- **Entries / precision:** 256 binary32 floats = 1,024 bytes of `.rodata` constants (`gamma_table.h` 7,325 B, `bench_gamma.c` 4,872 B, binary 16,520 B).
- **Error & speed:** 1,538 checks, 0 mismatches vs glibc `pow`; coarse batch `n=65,536` `21.841 → 1.177 ns/op` (`18.55×`; p95 `1.490 ns/op`). First `batch_lut(1)` `1.644 µs`.

---

## 6. Rejected experiment: quantized gamma encode table (superseded by §1 exact encode LUT)

`perfbench/teavm/src/probe/EncodeQuantized.java` quantizes `pow(avg, 1/2.2)` directly across fixed buckets (`49 / 4,096` output bytes differ by up to 1 vs `Math.pow`). That approximate approach remains rejected; instead, §1 (`GeneratedGammaTables.THRESH_*` + `BUCKET_*`) solves continuous `encodeBlend` / `encodeComponent` **with zero approximation error** (`0 / 12.4M` mismatches on HotSpot, `0 / 203,072` on TeaVM/V8) and achieves `16.9–25.0×` filter chain speedups on HotSpot and `6.9–11.1×` whole-filter speedups on V8.

---

## 7. Determinism and immutability rules applied

- All committed generated sources are checked byte-for-byte by `./perfbench/run_all.sh` and `tests/test_generated_luts.py`.
- `GeneratedGammaTables.java` contains zero `Math.pow`, `Math.sqrt`, `for (`, `static {` computation, or `new float/double/short[...]` allocations after its Javadoc; all arrays are numerical constants.
- No runtime dependency on Python, Node, WABT, GCC, TeaVM jars or generated files is introduced.
- Arrays are never exposed publicly; `MipmapGammaBlend` and `BlockNote` keep `private static final` references and never mutate entries.
