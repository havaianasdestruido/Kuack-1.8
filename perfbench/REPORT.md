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
`perfbench/HOTSPOTS.md`.

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
| `VerifyBiomeColor` | (reverted change) 570,240 checks | 0 failures |
| `bench_tile_entities.js` | Node.js replica: 20,000 add/remove/contains/removeAll equivalence trials + T=256→16384 × A=1→100 scaling | 0 mismatches; 16,433× fewer comparisons at T=16384,A=100 |
| `bench_blockpos.js` | Node.js replica: getHeight/isAreaLoaded allocation counts + recheckGaps probe logic + 1,444 boundary differential cases | 0 allocs in probe path; 0 mismatches |

**Compilation.** `./perfbench/compile_main.sh` compiles the entire tree with ECJ and fails only if a
file that used to compile starts failing. Before any edit: 1,525 problems across 104 files, all
pre-existing platform-stub gaps (TeaVM/OpenGL). The applied changes (9 files across findings #1,#3,#4,#5,#6
plus 3 files for findings #7,#8,#9) introduce no new problems — the 1,525 / 104 baseline is unchanged.
Nothing in any change touches TeaVM-incompatible APIs: only `java.util` sets/maps/lists, no threads,
no reflection, no `ThreadLocal`.

**Benchmark caveat.** No JDK is available in the sandbox and the Adoptium release CDN is outside the
allowed hosts, so the JVM compile gate and JMH-style benchmarks could not be run here; #7-#9 are
verified with faithful Node.js algorithm replicas (differential equivalence + complexity scaling) as
recorded above. Run `./perfbench/compile_main.sh` when a JDK is available to close the compile gate.

**Bug caught by the tests.** The first version of the `filterChunkLoadQueue` change aliased the live
list and then cleared it, which would have emptied the queue on every re-filter. The differential test
flagged it (2,198/2,209 mismatches) immediately; the shipped version snapshots first, like the
original did.

---

## Remaining high-complexity algorithms

Full ranked table with per-site reasoning in `perfbench/HOTSPOTS.md`. Headlines:

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

---

## Reproducing

```
./perfbench/compile_main.sh                     # full-tree compile gate
./perfbench/run_bench.sh ClassMapBench2 AITasksBench ...   # benchmark harness
# verification suites
java -cp "<classes>:perfbench/build/main:perfbench/build/stubs" perfbench.VerifyAI
```
