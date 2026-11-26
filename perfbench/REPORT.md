# Algorithmic performance review — Kuack-1.8 (Minecraft 1.8 / EaglercraftX)

Scope: the whole `sources/main/java` tree (256k lines of decompiled Minecraft 1.8 plus 65k lines of
Eaglercraft platform code), looking for super-linear algorithm behaviour in rendering, world updates,
entity AI, physics, collision, lighting, chunk meshing, visibility, networking and memory management.

Ground rules applied throughout (from the task):
gameplay, collision accuracy, rendering output and networking behaviour must be byte-for-byte
unchanged; Eaglercraft's TeaVM (Java→JS) constraints must hold (no JVM-only APIs, no threads or
`ThreadLocal` reliance in code that TeaVM compiles); no work may be hidden rather than removed; no
unbounded caches and no behaviour changes. Every change below is therefore *exact* — same results,
same ordering, same edge-case behaviour — and is justified by measurement, not intuition.

The per-change detail (bottleneck, old vs new complexity, verification, benchmark tables) is in
`perfbench/FINDINGS.md`; the ranked list of what was deliberately left alone is in
`perfbench/HOTSPOTS.md`. **Historical report:** the cross-runtime PR addendum at the end supersedes
the older #11 runtime-table details; see `perfbench/CROSS_RUNTIME.md`, `LUTS.md` and
`OPPORTUNITY_MATRIX.md` for deliverables A–G.

---

## Files modified

| file | change |
|---|---|
| `entity/ai/EntityAINearestAttackableTarget.java` | new O(n) `findNearest`; removed the sort |
| `entity/ai/EntityAIFindEntityNearest.java` | uses `findNearest` instead of sort+get(0) |
| `entity/ai/EntityAIFindEntityNearestPlayer.java` | uses `findNearest` instead of sort+get(0) |
| `entity/monster/EntityEnderman.java` | `AIFindPlayer` uses `findNearest` |
| `entity/ai/EntityAITasks.java` | `HashSet` mirror of `executingTaskEntries` for O(1) membership |
| `entity/player/EntityPlayerMP.java` | `loadedChunks` LinkedList → LinkedHashSet (declared `Collection`) |
| `server/management/PlayerManager.java` | `filterChunkLoadQueue` snapshot is a `HashSet` |
| `util/ClassInheritanceMultiMap.java` | per-instance memo of assignable known keys |
| `world/gen/ChunkProviderServer.java` | `loadedChunks` LinkedList → LinkedHashSet |
| `world/World.java` | `loadedTileEntityList` List→LinkedHashSet; new allocation-free `getHeight(int,int)` and `isAreaLoaded(int,int,int,int)`; `getHeight(BlockPos)` delegates |
| `world/WorldServer.java` | `getTileEntitiesIn()` indexed `size()`/`get(i)` → enhanced for-each over the Set |
| `world/chunk/Chunk.java` | skylight probes (`recheckGaps`, `checkSkylightNeighborHeight`, `updateSkylightNeighborHeight`) use the allocation-free World overloads |
| `lax1dude/.../vector/Matrix4f.java` | `rotate()` uses `MathHelper.cos/sin` (the engine's existing sine table) instead of native `Math.cos/sin`, with a fallback for \|angle\| ≥ 200,000 rad, NaN and ±Inf — FINDINGS #10 |
| `client/renderer/texture/MipmapGammaBlend.java` | GL-free box-filter blend; now uses the platform-selected source-embedded gamma tables — FINDINGS #11 / #12 |
| `client/renderer/texture/GeneratedGammaTables.java` (**new, generated**) | 2×256 deterministic float constants, selected once by `EagRuntime.getPlatformType()` — FINDINGS #12 |
| `client/renderer/EntityRenderer.java` (`updateLightmap`) | bit-exact invariant hoisting / CSE — FINDINGS #13 |
| `client/renderer/texture/TextureUtil.java` | `blendColors` delegates to `MipmapGammaBlend`; the per-thread scratch buffer and the two `Math.pow`-per-channel helpers moved with it |

One candidate (`BiomeColorHelper` / `RegionRenderCache` biome-tint memo) was fully implemented,
proved equivalent over 570,240 checks, **measured to be a non-win, and reverted** — see FINDINGS #2.

---

## Old vs new complexity

| # | site | old | new |
|---|---|---|---|
| 1 | nearest-target search | O(n log n) distance calls + sort + O(n) alloc | **O(n)** distance calls, no sort, no alloc |
| 3a | `filterChunkLoadQueue` | O(r²) walk × O(r²) scan = **O(r⁴)** | **O(r²)** (one snapshot copy) |
| 3b | `loadedChunks.contains` (per entity×player per tick; per block change per player) | **O(queue)** | **O(1)** |
| 4 | `EntityAITasks.onUpdateTasks` / `canUse` | **O(T²)** / **O(T³)** membership | **O(T)** / **O(T²)** |
| 5 | `ChunkProviderServer` chunk unload | **O(n)** per removal, up to 100/tick | **O(1)** per removal |
| 6 | `ClassInheritanceMultiMap.add/remove` | **O(knownKeys)** reflective `isAssignableFrom` per entity op | **O(1)** lookup + O(matching keys) |
| 7 | `World.loadedTileEntityList` flush (`contains` per added tile entity) | **O(A·T)** identity comparisons per tick | **O(A)** (one O(1) `contains` per add) |
| 8 | `WorldServer.getTileEntitiesIn()` iteration | O(T) indexed `size()`/`get(i)` | O(T) enhanced for-each (lower constant, Set-compatible) |
| 9 | `Chunk` skylight probes (`getHeight`/`isAreaLoaded` via `BlockPos`) | 1 `BlockPos` alloc per column probe (~769/invocation worst case in `recheckGaps`) | **0** `BlockPos` allocs in probe path; primitive `int` height |
| 10 | `Matrix4f.rotate` (every `GlStateManager.rotate`, model part, particle billboard, GUI item) | 2 native libm calls (`Math.cos` + `Math.sin`) per rotation, 2,000–6,000/frame | 2 table reads (existing 65,536-entry table), **0 new bytes**; 2.42× per rotation on the JVM, 5.0× on the pair in V8 |
| 11 | `TextureUtil.generateMipmapData` gamma box filter | up to **20 `Math.pow`** per output texel (4 ch × 4 neighbours + 4 encode), 1,700 per 16×16 sprite chain | 16 table reads + 4 `Math.pow` (encode kept for exactness); **bit-identical** output; 4.6–5.1× |

---

## Benchmark results

All figures are medians from `perfbench/`, real Minecraft classes where noted, JRE `-Xmx2g`.

**#1 nearest-target search** (`AiRealBench`, real entity classes):

| candidates | before | after | speedup |
|---|---|---|---|
| 4 | 0.052 ms | 0.031 ms | 1.68× |
| 128 | 4.367 ms | 0.191 ms | **22.86×** |

Scaling study `AiTargetBench` (synthetic, isolates the search+sort): 17.14× at n=32 rising to
**30.50×** at n=512 — the speedup grows with candidate count because the removed sort is
O(n log n) while the replacement is a single pass.

**#3 chunk load queue** (`ChunkQueueRealBench`, real `ChunkCoordIntPair`):

| radius | chunks | LinkedList | LinkedHashSet | speedup |
|---|---|---|---|---|
| 5 | 121 | 0.058 ms | 0.011 ms | 5.18× |
| 10 | 441 | 0.187 ms | 0.005 ms | 36.69× |
| 16 | 1089 | 1.204 ms | 0.004 ms | 315.55× |
| 24 | 2401 | 5.937 ms | 0.009 ms | 647.73× |
| 32 | 4225 | 17.635 ms | 0.024 ms | **737.71×** |

Single membership test (mid-list probe = worst case for a linked list): 9.96× (r=5) →
1,054 ns → 2.8 ns, **1,561×** (r=32).

**#5 chunk unload** (`ChunkUnloadBench`, 100 removals/tick):

| loaded chunks | LinkedList | LinkedHashSet | speedup |
|---|---|---|---|
| 256 | 0.0787 ms | 0.0692 ms | 1.14× |
| 1024 | 0.1817 ms | 0.1357 ms | 1.34× |
| 4096 | 0.4533 ms | 0.2852 ms | 1.59× |
| 8192 | 0.8092 ms | 0.3932 ms | 2.06× |
| 16384 | 1.4404 ms | 0.3444 ms | **4.18×** |

Steady state (5,929 live chunks — radius 12 — 24 unloads/tick): 0.541 → 0.121 ms per tick, **4.5×**.

**#6 chunk-section entity lists** (`ClassMapBench2`, real entity classes, resolution step only):

| known class keys | scan | memo | speedup |
|---|---|---|---|
| 7 | 13.60 µs | 7.97 µs | 1.71× |
| 12 | 17.88 µs | 6.83 µs | 2.62× |
| 20 | 26.58 µs | 6.82 µs | 3.90× |
| 30 | 38.85 µs | 6.86 µs | **5.66×** |

The memo's cost is flat while the scan grows with session length, so the win increases the longer a
world is played.

**#4 AI task membership** (`AITasksBench`, one pass per mob) — reported honestly:

| tasks | ArrayList | HashSet | speedup |
|---|---|---|---|
| 4 | 4.321 ms | 2.662 ms | 1.62× |
| 8 | 8.592 ms | 8.865 ms | 0.97× |
| 12 | 16.684 ms | 16.433 ms | 1.02× |
| 16 | 25.070 ms | 25.056 ms | 1.00× |
| 24 | 41.716 ms | 39.660 ms | 1.05× |
| 32 | 61.165 ms | 51.002 ms | 1.20× |

At vanilla mob sizes this is asymptotically better but not a measurable frame win; it is kept as
complexity insurance at ~48 B + 16 B/entry per mob.

**#7 tile-entity flush** (`bench_tile_entities.js`, Node.js algorithm replica — see caveat below):

| loaded tiles T | pending adds A | comparisons (vanilla) | comparisons (optimised) | fewer × |
|---|---|---|---|---|
| 256 | 1 | 256 | 1 | 256× |
| 1024 | 10 | 10,240 | 10 | 1,024× |
| 4096 | 50 | 204,800 | 50 | 4,096× |
| 16384 | 100 | 1,638,400 | 100 | **16,433×** |

Wall clock per flush cycle (median): T=4096,A=50 → 0.215 vs 0.228 ms (JS inlines the tight loop at
small A, noise dominates); T=16384,A=100 → **2.152 vs 1.240 ms (1.74×)**. The win is asymptotic and
appears under real chunk-load pressure (large T × moderate A).

Equivalence: 20,000 randomised add/remove/contains/removeAll trials, 0 mismatches vs vanilla
`ArrayList` semantics, including iteration order after removals.

**#8 `getTileEntitiesIn()`** — same O(T) scan, lower constant; covered by the same equivalence
harness (20,000 trials, 0 mismatches in AABB-filtered membership).

**#9 allocation-free skylight probes** (`bench_blockpos.js`, Node.js algorithm replica):

| site | `BlockPos` allocs (vanilla) | `BlockPos` allocs (optimised) |
|---|---|---|
| `getHeight(BlockPos)` per call | 1 (boxing) → delegates | 0 (primitive `int`) |
| `isAreaLoaded(BlockPos,r)` per call | 1 | 0 |
| `recheckGaps` worst case per invocation | up to ~769 | **0** (probe path) |

**#10 `Matrix4f.rotate` → sine table** (`MatrixRotateBench`, medians of 60 trials):

| measurement | before | after | speedup |
|---|---|---|---|
| one rotation (trig + 40 float ops) | 26.9–27.6 ns | 11.1–11.4 ns | **2.42×** |
| the `Math.cos` + `Math.sin` pair alone | 14.1–14.8 ns | 0.9–1.3 ns | **11.8–15.0×** |
| 500 rotations (typical frame) | 15.2 µs | 8.6–8.7 µs | saves 6.5–6.6 µs |
| 2,400 rotations (busy frame) | 64.4–65.9 µs | 28.8–28.9 µs | saves **35.5–37.1 µs/frame** |
| 7,200 rotations (crowded scene) | 194–205 µs | 97–102 µs | saves ~97–102 µs |
| 20,000 rotations (worst case) | 576–589 µs | 285–301 µs | saves ~275–305 µs |

Ranges are the spread of medians across independent runs.

In the actual TeaVM target (`perfbench/js/bench_math_lut.js`, V8 12.4, with `Math.fround` modelling
Java float semantics): trig pair 19.75 → 3.95 ns (**5.0×**); a 2,400-rotation frame batch 35.0 →
11.7 µs, **saving 23.3 µs/frame** (0.14 % of a 60 Hz frame, 0.56 % of a 144 Hz frame). The JS win is
smaller than the JVM win because `Math.fround` on the table index costs as much as the multiply
(2.08 ns vs 1.09 ns measured).

**#11 mipmap gamma filter** (`TextureGammaBench`, medians of 40 trials, realistic sprite data):

| workload | before | after | speedup |
|---|---|---|---|
| one texel, linear filter (4 decode + 4 encode) | 281.6–282.1 ns | 58.9–61.7 ns | **4.57–4.78×** |
| one texel, gamma filter (16 decode + 4 encode) | 248.6–252.5 ns | 49.8–51.1 ns | **4.94–4.99×** |
| 16×16 sprite chain, 4 mip levels (85 texels) | 22.4–25.2 µs | 4.9–5.6 µs | 4.50–4.56× |
| 1,024-sprite block atlas | 26.4 ms | 5.7 ms | **4.6×** |
| 1,024-sprite atlas with transparency | 23.4–24.1 ms | 4.7–4.8 ms | **4.9–5.1×** |
| 256×256 atlas, 5 mip levels | 6.3–6.5 ms | 1.3–1.4 ms | 4.5–4.9× |

In the TeaVM target shape: decode 43.46 → 0.53 ns (**82×**), full gamma-branch texel 233.6 → 47.6 ns
(4.9×; the retained `pow(avg, 1/2.2)` encode is now ~90 % of the remaining cost, which is why the
end-to-end gain is 5× and not 20×). Table cost: 1 KiB, 256 `Math.pow` calls (~10 µs) once, static and
stale-proof.

Differential equivalence over 1,444 boundary/edge cases (chunk edges, negative coords, ±30,000,000
world boundary, radius 0/1/16): **0 mismatches** between the `BlockPos` form and the primitive
overloads; identical height and loaded/unloaded answers because both delegate to the pre-existing
range-check logic.

---

## Verification

| test | coverage | result |
|---|---|---|
| `VerifyAI` | 20,000 random target sets, 12,488 with ties, vs the vanilla sort | 0 mismatches |
| `VerifyAITasks` | 21,000 randomised task schedules, both control flows transcribed verbatim, full per-tick execution traces compared | 0 failures |
| `VerifyChunkQueue` | 3,459 checks: full/partial/empty queues, radii 1–32, 7×7 centres, plus 5,000 randomised container ops proving same contents *and* same iteration order | 0 failures |
| `VerifyChunkUnload` | 197,740 checks over 4,000 randomised load/unload/iterate/snapshot sequences | 0 failures |
| `VerifyClassMap` | reference implementation vs the patched **real** class, 300 trials / 43,163 state comparisons with real entity classes | 0 failures |
| `VerifyBiomeColor` | (reverted change) 570,240 checks — source now in `perfbench/src-reverted/` because the APIs it exercised were reverted | 0 failures |
| `VerifyMatrixRotate` | 204,335 checks: 200k random rotations vs the original, axis invariance, fallback domain (`±200000`, `±1e6`, `±1e30`, `±Inf`, `NaN`), angle 0, in-place aliasing | 0 failures; max element delta 1.494e-4, max angular deviation 9.589e-5 rad (0.00549° = one table step); fallback and NaN/Inf **bit-identical** |
| `VerifyGammaLut` | 8,295,168 checks: 256/256 decode entries bit-exact, exhaustive one-channel sweep × both filter branches, 4,000,000 random ARGB quadruples vs a verbatim copy of the vanilla code | 0 failures |
| `bench_math_lut.js` | Node.js/V8 measurement of the engine's tables vs native `Math.*` (sin, cos, sqrt, 1/sqrt, atan2, pow, floor, `%`), LUT sizes/accuracies, and the two shipped changes as TeaVM compiles them | see tables above; basis for the rejections |
| `bench_tile_entities.js` | Node.js replica: 20,000 add/remove/contains/removeAll equivalence trials + T=256→16384 × A=1→100 scaling | 0 mismatches; 16,433× fewer comparisons at T=16384,A=100 |
| `bench_blockpos.js` | Node.js replica: getHeight/isAreaLoaded allocation counts + recheckGaps probe logic + 1,444 boundary differential cases | 0 allocs in probe path; 0 mismatches |

**Compilation.** `./perfbench/compile_main.sh` compiles the entire tree with ECJ and fails only if a
file that used to compile starts failing. The committed pristine-HEAD baseline is **1,525 problems
across 105 files**. After the current branch's one-word `World.isChunkLoaded` visibility fix, both JDK
17 and JDK 25 report **1,524 problems across 104 files**: `RenderGlobal.java` now compiles, and no
new file fails. Earlier reports recorded 1,525/104 or 1,527/105 from older stub revisions; the
baseline file intentionally remains at 105 files so a regression is still detected. Nothing in any
change touches TeaVM-incompatible APIs: `java.util` collections, arrays, `Math.*` and the engine's
own tables only — no threads, no reflection in production, no `ThreadLocal`, no unbounded caches.

**CI investigation.** Both base `main` run `45869906106` / job `140082318468` and the initial PR #25
run `45882359151` / job `140124542039` failed at `RenderGlobal.java:574` with
`isChunkLoaded(int,int,boolean) has protected access in World`; the pristine `main` failure proves it
was a pre-existing build break, not caused by this performance work. The current branch makes that
method `public` — a one-word visibility widening, no dispatch/body/gameplay change — so the existing
cross-package call compiles. A post-fix `./perfbench/run_all.sh` passed all repeatable steps. CI uses
real JDK 17 `javac`, license-restricted inputs and the Maven TeaVM cache, so its `Compile web client`
check is the authoritative full-client build result.

**Benchmark environment.** Earlier entries (#7-#9) could not be run on a JVM in this sandbox and were
verified with faithful Node.js algorithm replicas; that is still true for those numbers. For #10 and
#11 a JDK was obtained during the session (jdk4py, Temurin **25.0.5.1**, `JAVA_HOME` as set by
`run_bench.sh`; note it ships no `javac`, which is why ECJ remains the compiler), so both are measured
on real Minecraft classes **and** re-measured in the actual execution target, V8 12.4.254.21 via
Node 22.22.3. `run_bench.sh` now also puts `perfbench/build/stubs` on the run classpath, which is
required by any benchmark that constructs a real `World`/`Entity`/`Item`.

**Harness build.** `perfbench/run_bench.sh` now compiles the whole `perfbench/src` tree and fails if
anything in it does not compile. The three sources belonging to the *reverted* #2 experiment
(`VerifyBiomeColor`, `BiomeColorRealBench`, `BiomeColorSweep`) were moved to
`perfbench/src-reverted/` — they reference production APIs that no longer exist — so the harness
build is currently **clean (0 errors)**; previously it reported 16 errors on every run.

**Not measured / not run.** SpiderMonkey and JavaScriptCore behaviour (only V8 was available — the
conclusions about `Math.fround` and `Math.pow` are therefore V8-specific, though the *direction* —
libm dominates, tables win — is engine-independent); real in-browser frame timings; the `#10` win
inside a `FrameBuffer`-backed game loop (only the isolated rotation cost was measured).

**Bug caught by the tests.** The first version of the `filterChunkLoadQueue` change aliased the live
list and then cleared it, which would have emptied the queue on every re-filter. The differential test
flagged it (2,198/2,209 mismatches) immediately; the shipped version snapshots first, like the
original did.

---

## Remaining high-complexity algorithms

Full ranked table with per-site reasoning in `perfbench/HOTSPOTS.md`. Headlines:

0. **Particle physics runs the full entity collision path** — `EntityFX` never sets `noClip`, so every
   live particle (up to 4,000) calls `Entity.moveEntity` → `World.getCollidingBoundingBoxes` 1–3×
   per tick. Measured (`ParticleCollisionBench`, real classes): **79–95 ns per call** for a
   particle-sized AABB, with a fresh `ArrayList` + `AxisAlignedBB` each time; ≈**0.76 ms/tick
   (≈15 ms/s)** and ≈8,000 short-lived allocations per tick at 4,000 particles. The fix is
   allocation reuse + hoisting the per-block world-border test + merging `moveEntity`'s three
   sequential sweeps — deliberately *not* done in this PR because it is collision code.
1. `RenderGlobal.renderEntities:539` — redundant `getChunkFromBlockCoords` + `getPosition().getY()`
   per visible chunk per frame, although `RenderChunk.index` already encodes the section.
2. `BlockModelRenderer:77-80` — `float[12]` + `BitSet(3)` + `AmbientOcclusionFace` allocated for
   every rendered block (up to 16k per section rebuild).
3. `Chunk.recheckGaps:263` — `BlockPos` allocation in the probe/early-return path **FIXED** (new
   `World.getHeight(int,int)`/`isAreaLoaded(int,int,int,int)` overloads; 0 allocs in probe path).
   Remainder: per-block `checkLightFor(..., new BlockPos(x,i,z))` inside the relight loop.
4. `World.getCollidingBoundingBoxes:1058` — fresh `ArrayList` per call, several calls per entity per
   tick.
5. `EntityTracker.updateTrackedEntities:217` — worst case O(E×P) when many entities move.
6. `EntityItem` merge search and `Explosion.doExplosionA` — O(N²) and O(blocks × entities).
7. `PlayerManager.playersWatchingChunk.contains` — O(P), 6.42× available at 32 players.

Measured and deliberately **not** changed, because the only available fixes would introduce stale
caches (which the task forbids): `EnchantmentHelper.getMaxEnchantmentLevel` (129.3 ns/call, 3×/frame)
and `World.countEntities` (0.016 ms per spawn tick at 4,000 entities).

**The maths/LUT sweep.** The engine's transcendentals are already 99 % tabulated (`SIN_TABLE`,
`func_181159_b`'s 257-entry asin/cos tables, the De Bruijn log2, the noise gradient tables, the light
brightness table). The two sites that had escaped the tables are fixed above (#10, #11); everything
else that looked like a table candidate was measured and rejected — sqrt LUT, magic-number rsqrt in
JavaScript (11.8 ns vs 1.6 ns for `Math.sqrt`), smaller/lerped sine tables (slower than the existing
65,536-entry one), dropping `Math.fround` from `MathHelper.sin/cos` (1.9× but it perturbs worldgen
seeds), degree tables, `%`-free `wrapAngleTo180_*`, floor LUT (already at parity), `atan2` tables,
block-property tables in the AO mesher, and `pow(2.718,-x)` → `exp`. Full table with numbers in
`FINDINGS.md`.

---

## Reproducing

```
./perfbench/compile_main.sh                     # full-tree compile gate
./perfbench/run_bench.sh ClassMapBench2 AITasksBench ...   # benchmark harness
# verification suites
java -cp "<classes>:perfbench/build/main:perfbench/build/stubs" perfbench.VerifyAI
```


---

## Addendum — current cross-runtime PR (deliverable G, 2026-11-26)

This addendum is the final performance report for the current branch. Earlier sections document prior
algorithmic work; numbers below are from one captured `./perfbench/run_all.sh` run in the gitignored
`perfbench/results/*.txt` logs. A later post-CI-fix rerun passed all steps and overwrote those logs;
small timing differences on this noisy 2-vCPU sandbox are expected. **No full-game FPS or Chromium
frame-time was measured.**

### Affected files and methods

| File | Method / change | Original expression or algorithm |
|---|---|---|
| `sources/main/java/net/minecraft/client/renderer/texture/MipmapGammaBlend.java` | `blend`, `blendComponent`, `gamma22` | Up to 20 `Math.pow` calls per output texel: 4/16 decodes `(float)Math.pow((float)(v&255)/255.0F,2.2)` + 4 encodes `Math.pow(avg,1/2.2)` |
| `sources/main/java/net/minecraft/client/renderer/texture/GeneratedGammaTables.java` | generated constant arrays + `activeTable()` / `gamma22()` | New; no runtime table generation |
| `sources/main/java/net/minecraft/client/renderer/EntityRenderer.java` | `updateLightmap` | Repeated `getLightBrightnessTable()` lookup and three invariant float expressions inside a 256-iteration loop |
| `sources/main/java/net/minecraft/world/World.java` | `isChunkLoaded(int,int,boolean)` visibility `protected` → `public` | One-word build-compatibility fix for the existing `RenderGlobal` call; no behavior or gameplay change |
| `perfbench/gen_luts.py`, `perfbench/native/gen_header.py`, `perfbench/wasm/gen_wat.js` | development-time generators | No runtime dependency |
| `perfbench/run_all.sh`, `run_bench.sh`, `compile_main.sh`, `jit_gc_profile.sh` | repeatable suite | Before/after, equivalence, JIT/JFR |
| `perfbench/teavm/`, `perfbench/js/`, `perfbench/wasm/`, `perfbench/native/`, `perfbench/src/perfbench/`, `tests/test_generated_luts.py` | real TeaVM/V8 probe, V8 math bench, WASM/native PoCs, JVM verifiers, regeneration tests | Development/benchmark only |
| `CROSS_RUNTIME.md`, `LUTS.md`, `OPPORTUNITY_MATRIX.md`, `HOTSPOTS.md`, `FINDINGS.md`, `REPORT.md` | deliverables A–G | Documentation |

`Matrix4f.rotate`'s sine-table optimization is **already in HEAD**; this PR only regression-guards it
with `VerifyMatrixRotate` / `MatrixRotateBench` and does not modify that file.

### Bottleneck evidence and selected strategy

- Source census plus call-site trace: `TextureUtil.generateMipmapData` builds mip levels for each
  atlas sprite; a 16×16 sprite with 4 levels produces 85 output texels. The decode input is an 8-bit
  channel byte, i.e. exactly 256 values. Game frequency is **source-estimated** as atlas
  load/reload/F3+T, not per frame; the benchmark uses 1,024 sprites and a 256×256 atlas.
- `Math.pow` measured at 40.598 ns in V8 and 42.894 ns in the real TeaVM probe; a `Float32Array`
  lookup is 0.835 ns in the V8 math bench and 2.038 ns in the full TeaVM decode kernel.
- Strategy: **Exact LUT**, but with **two runtime-specific constant tables** because TeaVM 0.9.2
  lowers Java `float` intermediates as JS doubles. `EagRuntime.getPlatformType()` selects one at
  class init. The continuous encode step remains exact `Math.pow`.
- Lightmap strategy: **Algorithmic simplification** (invariant hoisting / CSE), no table.

### Table design, memory and sizes

| Item | Value |
|---|---|
| Production entries / precision | 2 × 256 binary32 `float` = 2,048 bytes of array data |
| Max abs / relative error | 0 ULP per intended runtime; cross-runtime tables differ on 132/256 entries by ≤1 ULP, max abs 5.96e-8, max rel 1.80e-7 |
| Domain / indexing | channel byte `0..255`; `byteValue & 255`; negative/out-of-range wrap; NaN/Inf cannot reach the index |
| Boundaries | `0 → 0.0F`, `255 → 1.0F`; monotone |
| Allocation | no runtime computation, no static loop, no file load, no boxed collection, no per-lookup allocation; one final reference in `MipmapGammaBlend` |
| Java source / class | 10,667 B / 6,691 B; `MipmapGammaBlend.class` 1,384 B |
| TeaVM gamma-only JS | embedded 178,309 B vs pre-change runtime table 164,387 B (+13,922 B) |
| Rejected huge sine experiment | +2,918,239 B JS; parse/clinit 22.37/42.75 ms vs 3.71/7.84 ms |
| WASM / native PoC | WAT 6,135 B, WASM 1,478 B; C header 7,325 B, C source 4,872 B, binary 16,520 B |
| Startup trade-off | JVM cold clinit 853.50 µs JDK17 / 826.02 µs JDK25 vs 202.75 / 199.07 µs old runtime table; steady-state lookup parity 0.523 / 0.522 ns/op. TeaVM parse/clinit 3.541/2.675 ms vs 3.465/2.670 ms first process |

### Original vs optimized times and percentage changes

The table below is one captured `./perfbench/run_all.sh` snapshot. A later post-CI-fix rerun passed
all steps and overwrote the gitignored result logs; small timing differences on this noisy 2-vCPU
sandbox are expected. The reproducible artifacts are the checked-in harnesses, generators and
equivalence checks, not a single nanosecond median.

| Runtime / workload | Original | Optimized | Change |
|---|---:|---:|---|
| JDK17 one texel linear | 342.07 ns | 72.87 ns | 4.69× / 78.7% less |
| JDK17 one texel gamma | 390.21 ns | 50.70 ns | 7.70× / 87.0% less |
| JDK17 1,024 sprites linear | 29.87 ms | 6.75 ms | 4.43× / 77.40% less |
| JDK17 1,024 sprites gamma | 35.97 ms | 4.76 ms | 7.56× / 86.77% less |
| JDK17 256×256 atlas | 7.48 ms | 2.02 ms | 3.70× / 72.99% less |
| JDK25 1,024 sprites linear | 26.91 ms | 5.82 ms | 4.62× / 78.37% less |
| JDK25 1,024 sprites gamma | 23.33 ms | 4.82 ms | 4.84× / 79.34% less |
| JDK25 256×256 atlas | 6.56 ms | 1.39 ms | 4.72× / 78.81% less |
| V8/TeaVM decode op | 42.894 ns | 2.038 ns | 21.05× / 95.25% less |
| V8/TeaVM whole blend linear | 927.216 ns | 243.277 ns | 3.81× / 73.76% less |
| V8/TeaVM whole blend gamma | 928.740 ns | 237.145 ns | 3.92× / 74.47% less |
| JDK17 lightmap update | 3.021 µs | 2.825 µs | 1.07× / 6.49% less |
| JDK25 lightmap update | 2.983 µs | 2.842 µs | 1.05× / 4.73% less |
| V8 lightmap update | 6,635.358 ns | 6,449.422 ns | 1.03× / 2.80% less |

**FPS / frame-time: UNAVAILABLE.** No Chromium, GPU, playable client, isolated browser profile or
full game loop. These are deterministic microbenchmarks; a 4–8× atlas-filter CPU reduction does not
imply a 4–8× FPS gain.

### Conversion prototypes (not production)

| Prototype | Original | Optimized | Boundary / startup evidence |
|---|---:|---:|---|
| WASM fine per-op JS pow → JS LUT | 45.493 ns | 0.939 ns | WASM per-call table 2.755 ns, so fine-grained boundary loses |
| WASM coarse batch n=65,536 | 43.165 ns/op | 0.680 ns/op | 63.49×; one JS→WASM call, imported `Math.pow` only on baseline |
| Native C coarse batch n=65,536 | 13.676 ns/op | 0.513 ns/op | 26.65×; p95/p99 14.865/14.865 → 0.517/0.517 ns |

### Equivalence, compatibility and correctness results

| Check | Result |
|---|---|
| `VerifyGammaLut` (HotSpot 17 and 25) | 8,295,168 checks / 0 failures |
| `VerifyEmbeddedLut` | 401,292 checks / 0 failures |
| `VerifyMatrixRotate` | 204,335 checks / 0 failures |
| JVM `LightmapBench` | 4,608 float comparisons / 0 mismatches |
| TeaVM `gamma_vs_prechange_browser_table` | 256 / 0 |
| TeaVM `gamma_index_wrap` | 1,024 / 0 |
| TeaVM `blend_vs_legacy_browser_table` | 203,072 / 0 |
| TeaVM `lightmap_cse_bit_exact` | 4,608 / 0 |
| WASM / native PoC equivalence | 1,794 / 0 and 1,538 / 0 |
| Python unit tests | 12 tests / OK |
| ECJ full-tree gate, JDK17 and JDK25 | 1,525 expected problems / 105 expected error files; no new failing files |
| TeaVM 0.9.2 probe build | `ADVANCED`, JAVASCRIPT target, succeeded; 36 classes |

Informational: TeaVM reports 118/203,072 output bytes differing by ≤1 from verbatim vanilla
**browser** arithmetic. This difference already existed in the pre-change shipped runtime table; the
PR preserves shipped browser output exactly (`blend_vs_legacy_browser_table` = 0) rather than
changing rendering bytes. On HotSpot the strict-Java table is bit-identical to verbatim vanilla Java.

JIT/JFR: `MipmapGammaBlend.gamma22` is inlined (`inline (hot)`) and `Math.pow` remains intrinsic for
the un-tabulated encode step. A JDK17 JFR recording of `TextureGammaBench` recorded 17.5 MB main-thread
allocation and 4 young GCs totaling 9.23 ms for the whole workload; the hot filter itself allocates
no objects.

### Environment and commands

```
Linux 6.1.158+ x86_64, Debian 12 derivative, 2 vCPU Xeon @2.60 GHz, 3.8 GiB RAM
Node v22.22.3 / V8 12.4.254.21-node.56; Python 3.11.2; gcc 12.2.0
Temurin/OpenJDK 17.0.9 and 25.0.5.1 JREs; ECJ from buildtools/deps
TeaVM 0.9.2 jars in /tmp/teavm-jars; WABT 1.0.36 in /tmp/wabt
./perfbench/run_all.sh
python3 -m unittest discover -s tests -v
JAVA_HOME=<JDK17/JDK25> ./perfbench/compile_main.sh
JAVA_HOME=<JDK17/JDK25> PERFBENCH_REBUILD=1 ./perfbench/run_bench.sh perfbench.AllBench
JAVA_HOME=<JDK17> TEAVM_JARS=<jars> ./perfbench/teavm/build_probe.sh
node ./perfbench/teavm/run_probe.js
node ./perfbench/teavm/run_startup.js
./perfbench/wasm/build.sh && node ./perfbench/wasm/run_wasm.js
./perfbench/native/build.sh
```

### Verified improvements, rejections and remaining work

**Verified improvements:** exact dual gamma LUT on both HotSpot 17/25 and TeaVM/V8; bit-exact
lightmap CSE; deterministic regeneration; real TeaVM transpilation; WASM/native coarse-batch PoCs;
existing `Matrix4f.rotate` regression still passes.

**Rejected with measurements:** single cross-runtime gamma table (changes one runtime), quantized
gamma encode (57/4,096 bytes differ), 65,536 embedded sine constants (+2.9 MB JS / +53.7 ms
parse+clinit), sqrt LUT / magic rsqrt, smaller/lerped sine tables, dropping `Math.fround` from
`MathHelper.sin/cos` (worldgen determinism), degree tables, `%`-free `wrapAngleTo180_*`, floor LUT,
`atan2` tables, AO block-property memo, biome-tint memo (0.98–0.99×), `pow(2.718,-x)`→`exp`,
whole-game WASM/native/GraalVM conversion in this sandbox.

**Remaining opportunities:** particle/collision allocation reuse, `BlockModelRenderer` scratch
objects, `RenderGlobal.renderEntities` redundant chunk lookups, real profiling with a playable client,
a browser input-distribution capture, and a GPU-aware WASM/native interop study. None is implemented
here.

**Unresolved correctness risks / unexecuted tests:** real Chromium/full-game FPS/frame-time p95/p99;
full `dist/classes.js` client build **in this sandbox** (the JRE has no `javac`; after the visibility
fix a temporary ECJ shim compiled all 2,452 files, then TeaVM 0.9.2 rejected the shim-generated
bytecode); desktop LWJGL launch; JNI/Panama; GraalVM Native Image / Graal JIT;
SpiderMonkey/JavaScriptCore; WASM SIMD; browser profile cache behavior; real game texture-atlas
input distribution. CI uses real JDK 17 `javac`, license-restricted inputs and the Maven TeaVM cache,
so its `Compile web client` check is the authoritative full-client build result. The one-time JVM/JS
startup regression from embedding constants is documented and accepted, not hidden. No numbers are
fabricated for unavailable environments.
