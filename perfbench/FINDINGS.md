# Optimization findings (Kuack-1.8 / Minecraft 1.8 + Eaglercraft)

Working notes for the algorithmic-hotspot sweep. Every entry records: file(s), original
bottleneck, old vs new complexity, the verification used, and the measured benchmark.

Benchmarks run under `JAVA_HOME=/tmp/jdkpkg/jdk4py/java-runtime`, `-Xmx2g -XX:+UseParallelGC`.
Full-tree compile gate: `./perfbench/compile_main.sh` (current after the CI visibility fix: 1524 problems / 104 error files; pristine HEAD baseline 1525 / 105, see REPORT.md).

---

## #1 `EntityAINearestAttackableTarget` + friends — nearest-target search

**Files:** `entity/ai/EntityAINearestAttackableTarget.java`,
`entity/ai/EntityAIFindEntityNearest.java`, `entity/ai/EntityAIFindEntityNearestPlayer.java`,
`entity/monster/EntityEnderman.java`

**Bottleneck.** The candidate list was materialised through `getEntitiesWithinAABB` (which builds an
`ArrayList` of every entity in the AABB, crossing chunk boundaries), then filtered, then *sorted*
with a comparator that recomputed `getDistanceSqToEntity` on both operands O(n log n) times, then
took element 0. Distances were recomputed an unbounded number of times.

**Fix.** Single linear pass that keeps the best candidate by the comparator's exact key
(distance, then the original index as tie-break, preserving the stable sort's result). No list, no sort.

**Old → new.** O(n log n) distance computations + O(n) allocation + O(n log n) sort
→ O(n) distance computations, O(1) allocation, no sort.

**Verification.** `perfbench.VerifyAI` — 20,000 random cases (12,488 containing ties), 0 mismatches
against the vanilla sort. `perfbench.AiRealBench` (real entity classes):
n=4 0.052 → 0.031 ms (1.68×) … n=128 4.367 → 0.191 ms (**22.86×**).
`perfbench.AiTargetBench`: n=32 17.14× … n=512 **30.50×**.

---

## #2 Biome tint memo — measured, then **reverted** (no gain)

**Files:** `world/biome/BiomeColorHelper.java`, `client/renderer/RegionRenderCache.java`
(reverted; see below)

**What was tried.** `BiomeColorHelper.func_180285_a` recomputes a 3×3 biome-average for every tinted
quad, so a memo keyed by `(x,y,z)` inside the `RegionRenderCache` window was implemented (fixed
`int[3][324]` + y-stamp arrays, ~7.6 KB/region, cannot grow) and verified byte-identical
(`perfbench.VerifyBiomeColor`: 570,240 checks, 0 failures; that source now lives in
`perfbench/src-reverted/`, since the APIs it exercised were reverted with the change).

**Why it was reverted.** Measured with real classes: `RegionRenderCache` lookups are ~26 ns,
`colorMultiplier` ~17 ns, and the biome loop is already served by cached chunk lookups.
`BiomeColorSweep` (real classes) gave 0.98–0.99×, and `RebuildProfile` showed a whole chunk section
rebuild spends only ~0.06 ms of ~1.6 ms in biome tinting. The memo cannot pay for itself here, so
the production change was backed out rather than shipped as dead weight.

---

## #3 `PlayerManager.filterChunkLoadQueue` + `EntityPlayerMP.loadedChunks` — O(r³) queue filter

**Files:** `server/management/PlayerManager.java`, `entity/player/EntityPlayerMP.java`

**Bottleneck.** `loadedChunks` was a **`LinkedList`**. `filterChunkLoadQueue` walks a spiral over the
whole `(2r)²` chunk view area and, for *every* visited chunk, asked
`snapshot.contains(chunkCoords)` — an O(queue) pointer-chasing scan over a linked list. With the
queue holding ~(2r)² entries, the walk is O(r² · r²) = **O(r⁴)** in comparisons (≈1.8 ms per call at
radius 16, 17.6 ms at radius 32). The same `LinkedList.contains` was also on two per-tick hot paths:
`isPlayerWatchingChunk` (called once per entity×player pair in `EntityTrackerEntry.updatePlayerEntity`)
and `PlayerInstance.sendToAllPlayersWatchingChunk` (called for **every block change**).
`filterChunkLoadQueue` is called on join and on each mounted-move chunk change.

**Fix.**
- `loadedChunks` is now a `LinkedHashSet` (declared `Collection<ChunkCoordIntPair>`): identical
  insertion-order iteration (the chunk load queue in `EntityPlayerMP.onUpdate` depends on order),
  identical distinct contents (every add site is already duplicate-guarded, exactly as the old list
  relied on), intrinsic identity semantics unchanged, but `contains`/`add`/`remove` are O(1).
- `filterChunkLoadQueue` keeps its required "snapshot before clear" semantics but builds the
  snapshot as a `HashSet` instead of an `ArrayList`, so the spiral's membership queries are O(1).

**Old → new.**
| path | old | new |
|---|---|---|
| `filterChunkLoadQueue` | O(r²) walk × O(r²) scan = **O(r⁴)** | O(r²) walk, **O(r²)** (snapshot copy) |
| single `loadedChunks.contains` | **O(queue)** | **O(1)** |
| `sendToAllPlayersWatchingChunk` (per block change, per player) | O(queue) | O(1) |
| `isPlayerWatchingChunk` (per entity, per player, per tick) | O(queue) | O(1) |

**Note on a bug caught by the test.** The first version aliased `player.loadedChunks` and then cleared
it, which would have made every membership test fail (the original snapshotted first). The differential
test detected it immediately (2,198/2,209 mismatches); the production code now snapshots, as the
original did.

**Verification.** `perfbench.VerifyChunkQueue` — 3,459 checks, 0 failures. Covers: full queues for
radius 1..32 × 7×7 centres, partial/shuffled queues with out-of-view entries and duplicates,
the empty queue, plus 5,000 randomised container-op sequences proving `LinkedHashSet` gives the same
contents *and the same iteration order* as the old `LinkedList` under the production add/remove/clear
patterns.

**Benchmark** (`perfbench.ChunkQueueRealBench`, real `ChunkCoordIntPair`):

| radius | chunks | LinkedList | LinkedHashSet | speedup |
|---|---|---|---|---|
| 5 | 121 | 0.058 ms | 0.011 ms | 5.18× |
| 10 | 441 | 0.187 ms | 0.005 ms | 36.69× |
| 16 | 1089 | 1.204 ms | 0.004 ms | 315.55× |
| 24 | 2401 | 5.937 ms | 0.009 ms | 647.73× |
| 32 | 4225 | 17.635 ms | 0.024 ms | **737.71×** |

Single `contains` (mid-list probe, the LinkedList worst case), per call:

| radius | LinkedList | LinkedHashSet | speedup |
|---|---|---|---|
| 5 | 107.4 ns | 10.8 ns | 9.96× |
| 16 | 1054.3 ns | 2.8 ns | 383.33× |
| 32 | 4261.7 ns | 2.7 ns | **1561.05×** |

---

## #4 `EntityAITasks` — O(T²)/O(T³) `contains` on the per-tick AI path

**Files:** `entity/ai/EntityAITasks.java`

**Bottleneck.** `executingTaskEntries` was an `ArrayList` and every "is this task already running?"
question was a linear identity scan. `onUpdateTasks` asks once per task entry per setup pass, and
`canUse` asks once per *other* task inside a loop that is itself run for every entry — so a single
mob tick was O(T²) and a whole setup pass could reach O(T³). This runs for every living entity, every
tick.

**Fix.** Added `executingTaskSet` (a `HashSet`) kept in lockstep with the list at all 5 mutation
sites; all membership queries now hit the set. `EntityAITaskEntry` does not override
`equals`/`hashCode`, so `HashSet` membership is exactly the same identity relation the old
`List.contains` used.

**Old → new.** `onUpdateTasks` O(T²) → **O(T)**; `canUse` total O(T³) → **O(T²)**.

**Verification.** `perfbench.VerifyAITasks` — both control flows transcribed verbatim and replayed
over 21,000 randomised schedules (1–12 tasks, 4 priorities, 4 mutex bitmasks, interruptible/
non-interruptible, scripted execute/continue decisions, mid-run `removeTask`) comparing full
per-tick traces and final state: **0 failures**.

**Benchmark** (`perfbench.AITasksBench`, one pass per mob):

| tasks | ArrayList | HashSet | speedup |
|---|---|---|---|
| 4 | 4.321 ms | 2.662 ms | 1.62× |
| 8 | 8.592 ms | 8.865 ms | 0.97× |
| 12 | 16.684 ms | 16.433 ms | 1.02× |
| 16 | 25.070 ms | 25.056 ms | 1.00× |
| 24 | 41.716 ms | 39.660 ms | 1.05× |
| 32 | 61.165 ms | 51.002 ms | 1.20× |

Honest read: at realistic mob sizes (8–16 tasks) the list is short enough that cache locality wins
as often as hashing does, so this change is **complexity insurance rather than a measurable frame
win** at vanilla mob sizes. It is kept because it strictly reduces the asymptotic bound (the 1.2× at
32 tasks and 1.62× at 4 tasks bracket the crossover) at a cost of one `HashSet` per mob (~48 B +
16 B/entry), and it cannot regress the common case beyond noise. See "rejected/reverted" notes for
the standards applied.

---

## #5 `ChunkProviderServer.loadedChunks` — O(n) per-chunk unload on a `LinkedList`

**File:** `world/gen/ChunkProviderServer.java`

**Bottleneck.** `loadedChunks` was a `LinkedList` holding every loaded chunk — `(2r+1)²` of them, i.e.
hundreds to several thousand on a normal view distance. `unloadQueuedChunks()` runs up to **100**
`loadedChunks.remove(chunk)` per tick, and each one is an O(n) walk calling `equals()` at every node
(`Chunk` does not override `equals`, so it is an identity scan — the worst case for a linked list).
That is O(100·n) pointer-chasing work **every tick** just to maintain a set of chunks.
`saveChunks` and `func_152380_a` also copy the list, but that is inherently O(n) and unchanged.

**Fix.** `loadedChunks` is now a `LinkedHashSet`. `Chunk` has no `equals`/`hashCode` override, so the
set reproduces the old identity relation exactly; insertion order (which `unloadAllChunks` iterates
and `func_152380_a` snapshots) is preserved. `func_152380_a()` now returns `Collection<Chunk>` (the
only caller is `Lists.newArrayList(...)` in `WorldServer`).

**Old → new.** Per removal O(n) → **O(1)**; per tick O(100·n) → **O(100)**.

**Verification.** `perfbench.VerifyChunkUnload` — 197,740 checks over 4,000 randomised operation
sequences (load, unload, full iteration, snapshot, membership): **0 failures**, including iteration
order.

**Benchmark** (`perfbench.ChunkUnloadBench`, 100 removals/tick):

| loaded chunks | LinkedList | LinkedHashSet | speedup |
|---|---|---|---|
| 256 | 0.0787 ms | 0.0692 ms | 1.14× |
| 1024 | 0.1817 ms | 0.1357 ms | 1.34× |
| 4096 | 0.4533 ms | 0.2852 ms | 1.59× |
| 8192 | 0.8092 ms | 0.3932 ms | 2.06× |
| 16384 | 1.4404 ms | 0.3444 ms | **4.18×** |

Steady state, 5,929 live chunks (radius 12) with 24 unloads/tick: 0.541 ms → 0.121 ms per tick
(**4.5×**).

---

## #6 `ClassInheritanceMultiMap.add/remove` — O(knownKeys) `isAssignableFrom` per entity op

**File:** `util/ClassInheritanceMultiMap.java`

**Bottleneck.** Every chunk section keeps one `ClassInheritanceMultiMap` per 16-block layer (16 per
chunk). `add()` and `remove()` looped over **every key in `this.knownKeys`** and called
`Class.isAssignableFrom()` on each one, per entity operation. `knownKeys` is seeded from the
process-wide static `field_181158_a`, which accumulates every entity class ever queried for the whole
session — so the per-entity cost grows monotonically as the game runs (to 20–30 keys on a busy
server), and `isAssignableFrom` is a reflective subtype walk, not a cheap compare.

**Fix.** Memoise, per map instance, the list of *this* map's `knownKeys` that match a given concrete
class (`func_181156_a`). The answer depends only on the concrete class and on the instance's key set,
so the memo is dropped only when that instance learns a new key (`createLookup`) — the only event
that can change it. The memo is per-instance because two sections can legitimately hold different key
sets, is allocated lazily (sections with no entities pay nothing), and is bounded by the number of
distinct concrete entity classes ever placed in that section.

**Old → new.** `add`/`remove` key resolution O(knownKeys) reflective calls → **O(1) map lookup +
O(matching keys)**. Matching-key binding itself is unchanged.

**Verification.** `perfbench.VerifyClassMap` — a verbatim reference implementation of the original
algorithm run side by side with the patched **real** class over 300 randomised trials
(43,163 state/content comparisons) using real `EntityZombie`/`EntityCreeper`/`EntityPig`/`EntityItem`
instances, interleaving `add`/`remove`/`getByClass`/`contains` and key registration: **0 failures**
(same contents, same per-class lists, same order).

**Benchmark** (`perfbench.ClassMapBench2`, real entity classes, only the resolution step):

| known keys | scan | memo | speedup |
|---|---|---|---|
| 7 | 13.60 µs | 7.97 µs | 1.71× |
| 12 | 17.88 µs | 6.83 µs | 2.62× |
| 20 | 26.58 µs | 6.82 µs | 3.90× |
| 30 | 38.85 µs | 6.86 µs | **5.66×** |

The memo's own cost is flat (~6.8 µs/1000 adds) while the scan keeps growing with the session, so the
win increases the longer a world is played.


## #7 `World.loadedTileEntityList` — List→LinkedHashSet on the tile-entity flush path

**File:** `world/World.java` (field declaration ~line 99, javadoc ~line 93)

**Bottleneck.** `loadedTileEntityList` was an `ArrayList<TileEntity>`. Every time a new tile entity
was registered (`addTileEntity`), the pending-block-entity flush ran
`loadedTileEntityList.contains(tile)` for *each* already-loaded tile entity to avoid duplicates — an
O(T) linear scan performed once per added entity. While chunks load (A entities added in a tick
against T loaded tile entities) that is O(A·T) identity comparisons per tick, growing with both the
number of pending additions and the total tile-entity population. `TileEntity` does not override
`equals`/`hashCode`, so the scan is an identity walk — the worst case for an `ArrayList`.

**Fix.** Declared the field as `Set<TileEntity>` backed by `Sets.newLinkedHashSet()` (Guava, already
imported). `contains`/`add`/`remove` are now O(1). Insertion-order iteration is preserved
(`WorldServer.getTileEntitiesIn` iterates it and callers rely on stable traversal order), duplicate
guards are unchanged (every add site already guard-checks before adding, exactly as the old list
relied on), and `removeAll(toRemove)` on the removal path keeps identical identity semantics.

**Old → new.** Pending-block-entity flush: O(A·T) identity comparisons → **O(A)** (one O(1) `contains`
per added entity). Single-element `add`/`remove`/`contains`: O(T) → **O(1)**.

**Verification.** `perfbench/js/bench_tile_entities.js` — algorithm replica in Node.js (no JDK in the
sandbox; the `release-assets.githubusercontent.com` CDN needed for the Adoptium bundle is outside the
allowed hosts). Equivalence test: 20,000 randomised add/remove/contains/removeAll trials, 0
mismatches against the vanilla `ArrayList` semantics, including order of iteration after removals.
Scaling study: T=256→16384 loaded tile entities × A=1→100 pending adds per tick; comparisons drop
from O(A·T) to O(A) — 256× fewer at T=256,A=1 rising to **16,433×** fewer at T=16384,A=100.
Wall clock at T=16384,A=100: vanilla 2.152 ms vs optimised 1.240 ms (**1.74×**) per flush cycle.
At small A the JS engine inlines the tight list loop and noise dominates (0.215 vs 0.228 ms at
T=4096,A=50), which is expected — the win is asymptotic and appears under real chunk-load pressure.


## #8 `WorldServer.getTileEntitiesIn()` — indexed size()/get(i) → enhanced for-each

**File:** `world/WorldServer.java` (~line 663)

**Bottleneck.** `getTileEntitiesIn(minX,minY,minZ,maxX,maxY,maxZ)` iterated `loadedTileEntityList`
with `for (int i = 0; i < size(); ++i) { get(i); ... }`. With the list now a `LinkedHashSet`,
`size()` is O(1) but `get(i)` does not exist on a `Set` — more importantly the indexed form was a
code smell that forced the list backing type and prevented the Set conversion. The enhanced
for-each iterates the Set directly.

**Fix.** Replaced the indexed loop with `for (TileEntity tileentity : this.loadedTileEntityList)`.
Identical filtering (AABB bounds), identical result list (`ArrayList` in insertion order), identical
behaviour; only the iteration mechanism changes.

**Old → new.** Same O(T) scan with one bounds check per element; lower constant (no index
bookkeeping, direct iterator) and it now compiles cleanly against the `Set` declaration. This is a
strictly lateral, type-correct change that enables #7.

**Verification.** `bench_tile_entities.js` equivalence harness exercises the same AABB filter over a
Set-backed collection: 20,000 trials, 0 mismatches in selected membership vs the indexed version.


## #9 `World.getHeight(int,int)` / `isAreaLoaded(int,int,int,int)` — allocation-free skylight probes

**Files:** `world/World.java` (new overloads ~lines 586 and 605; `getHeight(BlockPos)` delegates);
`world/chunk/Chunk.java` (recheckGaps line ~263, checkSkylightNeighborHeight ~309,
updateSkylightNeighborHeight ~318)

**Bottleneck.** The chunk relight/skylight path queried surface height and chunk-loaded status by
allocating a `BlockPos`: `getHeight(new BlockPos(x, 0, z))` (one `BlockPos` per column probe) and
`isAreaLoaded(new BlockPos(x, 0, z), 16)` (one `BlockPos` per column probe, plus one at the
`recheckGaps` gate). `recheckGaps` runs on every block/light change and iterates all 256 x/z columns
of a chunk, so each invocation allocated up to 1+5×16×16 `BlockPos` objects in the probe/early-return
path — short-lived garbage on a hot path, plus the `getHeight(BlockPos)` boxing of an int result.

**Fix.** Added two allocation-free overloads to `World`:
- `int getHeight(int x, int z)` — returns the surface height directly; `getHeight(BlockPos pos)` now
  delegates to it (`new BlockPos(pos.getX(), getHeight(x,z), pos.getZ())`), preserving the old
  `BlockPos`-returning contract exactly.
- `boolean isAreaLoaded(int x, int y, int z, int radius)` — equivalent to
  `isAreaLoaded(new BlockPos(x,y,z), radius)` with no allocation; delegates to the existing
  7-argument range form.

`Chunk` skylight probes now call these directly:
- `recheckGaps`: `isAreaLoaded(x*16+8, 0, z*16+8, 16)` instead of `isAreaLoaded(new BlockPos(...),16)`.
- `checkSkylightNeighborHeight`: `getHeight(x, z)` instead of `getHeight(new BlockPos(x,0,z)).getY()`.
- `updateSkylightNeighborHeight`: `isAreaLoaded(x, 0, z, 16)` instead of
  `isAreaLoaded(new BlockPos(x,0,z),16)`.

The per-block `checkLightFor(..., new BlockPos(x,i,z))` inside `updateSkylightNeighborHeight`'s relight
loop is a separate site (HOTSPOTS #3 remainder) and is intentionally untouched here.

**Old → new.** Per column probe: 1 `BlockPos` allocation → **0**. `getHeight(BlockPos)` boxing →
primitive `int` return. `recheckGaps` probe/early-return path: up to ~769 `BlockPos`/invocation in the
worst case → **0** in the probe path.

**Verification.** `perfbench/js/bench_blockpos.js` — Node.js replica of `getHeight`/`isAreaLoaded`
allocation counts and the `recheckGaps` probe logic.
- Allocation elimination: `getHeight` 2N→0 `BlockPos`/call; `isAreaLoaded` N→0/alloc; `recheckGaps`
  worst case 769→0 `BlockPos` per invocation.
- Differential equivalence: 1,444 boundary/edge cases (coordinates at chunk edges, negative coords,
  the ±30,000,000 world-boundary, radius 0/1/16) — 0 mismatches between vanilla `BlockPos` form and
  the new primitive overloads.
- Behaviour: identical results (same height values, same loaded/unloaded answers) because the bodies
  delegate to the pre-existing range-check logic unchanged.

---

## Math hand-off: what the engine already tabulates

Before adding anything, the existing lookup tables were inventoried, because Minecraft 1.8 is
already full of them and a second copy of any of them would be pure overhead:

| existing table | site | shape | verdict |
|---|---|---|---|
| `SIN_TABLE[65536]` (+ `cos` as a quarter-turn offset) | `util/MathHelper` | 16-bit-turn **step** table, built once in the static initializer, 256 KiB | already optimal for `MathHelper.sin/cos`; measured 2.08 ns/call in V8 (480 Mops/s) vs 9.17 ns for native `Math.sin` |
| `multiplyDeBruijnBitPosition[32]` | `MathHelper.calculateLogBaseTwo` | De Bruijn log2 | optimal, untouched |
| `field_181164_e[257]` / `field_181165_f[257]` | `MathHelper.func_181159_b` | asin/cos table + one Newton step | the engine's atan2 is already table-driven; untouched |
| magic-constant reciprocal sqrt | `MathHelper.func_181161_i` | bit-twiddle + 1 Newton step | only used by `func_181159_b`; a table-free approximation, correct choice there |
| `lightBrightnessTable[16]` | `WorldProvider` | light level → brightness | present, used by every light query |
| `GRAD3` / permutation tables | `world/gen/NoiseGeneratorSimplex`, `NoiseGeneratorImproved` | value/gradient noise | present; worldgen noise is already table-driven |
| `VisGraph` bit tables, `EnumNeighborInfo`/`VertexTranslations`/`EnumFace` | `renderer/chunk/VisGraph`, `renderer/BlockModelRenderer` | face/visibility constant tables | present; the AO path is built on them |
| `Block.EnumOffsetType`, face/normal direction tables | `Block`, `EnumFacing` | constant lookups | present |

Conclusion that shaped the rest of the sweep: **client-side transcendentals are already tabulated**,
so the remaining wins are (a) the sites that bypass the engine's tables, and (b) functions with a
small discrete domain that were never tabulated at all.

## Trig call-site census

Every `Math.sin`/`Math.cos`/`MathHelper.sin`/`MathHelper.cos` in the tree (281 `GlStateManager`
rotate/translate/scale sites, 1,372 `MathHelper.*` sites, 169 direct `Math.sin/cos/sqrt/atan2`
sites) was classified by *how often it can execute*:

- **per model part per frame** (thousands/frame): `GlStateManager.rotate` → `Matrix4f.rotate`, which
  was the *only* remaining consumer of native `Math.cos`/`Math.sin` on the render path (see #10).
- **per entity per frame**: model animation (`ModelBiped`, `ModelQuadruped`, `ModelBlaze`,
  `ModelSquid`, `ModelDragon`, `RenderHighProfiles`…) — all already `MathHelper`, i.e. already
  table lookups; ~8–20 calls per entity.
- **per frame**: sky/sun/moon/stars (`RenderGlobal.renderSky`), the 16-segment world-border ring,
  the cloud grid, `updateLightmap` (256 entries), first-person hand animation.
- **per particle per frame**: 5 `MathHelper` calls in `EffectRenderer`/`AcceleratedEffectRenderer`
  (4 distinct angles, one duplicated `sin(pitch)`; a `once per particle per frame` CSE of ~1 ns was
  not worth a diff).
- **per tick**: entity physics (`Entity.moveFlying` uses `MathHelper.sin/cos`), look helpers,
  boat/minecart steering — `MathHelper` again.
- **per chunk / worldgen**: `MapGenCaves`, `MapGenRavine`, `WorldGenBigTree`, `WorldGenMinable`,
  `ChunkProviderHell`, strongholds — `MathHelper`/`Math.*`, but bounded by chunk generation and
  **bit-exactness matters here** (see the `Math.fround` rejection below).

So the only *hot* raw-trig site in the whole client was `Matrix4f.rotate`, and the only hot raw
`Math.pow` site was the mipmap gamma filter.

---

## #10 `Matrix4f.rotate` — native `Math.cos`/`Math.sin` → the engine's sine table

**Files:** `net/lax1dude/eaglercraft/v1_8/vector/Matrix4f.java` (`rotate(float, Vector3f, Matrix4f,
Matrix4f)`, the method behind `GlStateManager.rotate`, `FaceBakery`, `DeferredStateManager` static
matrices and `EaglerDeferredPipeline`).

**Bottleneck.** Every rotation in the renderer — each `ModelRenderer` part (ModelBiped issues 2–3
rotations per part for 6 parts), armour layers, held items, tile entities, particle billboards, GUI
items, the sky/moon matrices — ran `(float) Math.cos(angle)` **and** `(float) Math.sin(angle)`.
That is two libm calls per rotation and the single largest concentration of native transcendentals
left in the client: a scene with ~120 visible entities turns this into 2,000–3,000 rotations, i.e.
4,000–6,000 libm calls, **every frame**. The rest of the engine already answers the same question
with `MathHelper.sin/cos`; this site simply never switched over when the LWJGL vector class was
ported.

**Fix.** Use the existing 65536-entry `SIN_TABLE` through `MathHelper.cos/sin` (cos is the same
table read 16384 indices later — no second table, **0 bytes** of new memory, nothing new to
initialise). A guard keeps the original path for any input the table cannot represent:
`|angle| >= 200,000 rad` (the point where `MathHelper`'s `(int)` index would saturate: 2048 turns),
`NaN` and `Infinity` all fall through to `Math.cos`/`Math.sin`, so those inputs stay bit-identical.

**Numerical contract.** The table quantises the angle to one index step, `2π/65536 = 9.587e-5 rad`.
Measured over 200,000 random rotations with random axes and random source matrices: **max element
delta 1.494e-4**, **max angular deviation of a rotated unit vector 9.589e-5 rad = 0.00549°**, i.e.
exactly one table step, which is the same quantisation the engine already accepts wherever it needs
a sine (`MathHelper.sin/cos` is the standard path for sky, clouds, particles, lightning and item
frames, and most angles reaching this method are derived from those tables or are plain
degree-to-radian conversions of entity yaw/pitch). At
`FaceBakery` (block-model element rotation) this moves baked vertices by ≤1e-4 blocks (1.6 µm); on
the render path it is ≤0.0055° of model rotation, far below one pixel. Nothing gameplay-side uses
`Matrix4f.rotate`: the callers are rendering and model baking only, so there is no effect on
collision, entity movement or world generation determinism.

**Verification.** `perfbench.VerifyMatrixRotate` — 204,335 checks, 0 failures: error bound over
200k random rotations, axis invariance (a rotation about X/Y/Z keeps that axis exact, worst
deviation 5.96e-8), the fallback domain (`±200000`, `±1e6`, `±1e30`, `±Inf`, `NaN` → 0 of 10
matrices differ from the original at all), `angle == 0` identity, and in-place
`rotate(a, axis, m, m)` aliasing.

**Benchmark** (`perfbench.MatrixRotateBench`, JVM, median of 60 trials):

| measurement | before | after | speedup |
|---|---|---|---|
| one rotation (trig + 40 float ops) | 26.92–27.55 ns | 11.14–11.40 ns | **2.42×** |
| the two trig evaluations alone | 14.05–14.77 ns | 0.93–1.25 ns | **11.8–15.0×** |
| 2,400 rotations (busy frame) | 64.4–65.9 µs | 28.8–28.9 µs | saves **35.5–37.1 µs/frame** |
| 7,200 rotations (crowded scene) | 194.4–204.5 µs | 97.2–102.3 µs | saves ~97–102 µs/frame |
| 20,000 rotations (worst case) | 576.4–589.4 µs | 284.6–301.4 µs | saves ~275–305 µs/frame |

(Ranges are the spread of medians across independent runs on this machine; the 2.42× on the whole
rotation is stable to the second decimal.)

Target-runtime measurement (`perfbench/js/bench_math_lut.js`, V8 12.4, modelling TeaVM's
`Math.fround` float semantics): the trig pair goes **19.75 ns → 3.95 ns (5.0×)** in JavaScript, and
a 2,400-rotation frame batch goes **35.0 µs → 11.7 µs, saving 23.3 µs/frame (0.14 % of a 60 Hz
frame, 0.56 % of a 144 Hz frame)**. The JavaScript win is smaller than the JVM one because V8's
`Math.fround` wrapping of the table index costs as much as the multiply (measured: 2.08 ns with the
`fround`, 1.09 ns without).

Honest read: this is a real, zero-memory, zero-risk reduction of renderer CPU time, but the absolute
per-frame saving is tens of microseconds at realistic entity counts — it matters for frame-time
*consistency* on weak hardware (where 35 µs is ~1 % of a 3 ms frame budget) more than for average
FPS.

---

## #11 `MipmapGammaBlend` — 256-entry gamma decode table (was 20 `Math.pow` per mipmap texel)

**Files:** new `net/minecraft/client/renderer/texture/MipmapGammaBlend.java`;
`net/minecraft/client/renderer/texture/TextureUtil.java` (`blendColors` now delegates, the scratch
array moved with the code).

**Bottleneck.** `TextureUtil.generateMipmapData` builds every mipmap level of every texture with a
2×2 box filter, and both filter variants decode each of the four channel bytes to linear light with
`(float) Math.pow((float)(v & 255) / 255.0F, 2.2)` and re-encode with `Math.pow(avg, 1/2.2)`.
That is **up to 20 `Math.pow` calls per output texel** (4 channels × 4 neighbours + 4 encode) —
and `Math.pow` is the most expensive primitive measured anywhere in this sweep: **43.0 ns** in V8
(23 Mops/s), ~7× `Math.exp`, ~28× `Math.sqrt`. A 16×16 sprite with 4 mip levels produces 85 texels
(1,700 `pow` calls); the block atlas runs this for ~1,000 sprites *plus every animation frame of
every animated texture*. It is the single biggest `Math.pow` concentration in the game, and it runs
on world load, on resource-pack switch and on `F3+T` — a visible hitch, not just a startup cost.

**Fix.** The **input** side of that expression has exactly **256 distinct values** (one per byte),
so it is a textbook integer-indexed table: `GAMMA_2_2[256]` = 1 KiB, read 16× per texel. The
historical implementation filled it in a static initializer (256 `Math.pow` calls); **#12 below
supersedes that with committed source-embedded constants** as required by the current task. The **output** side
(`pow(avg, 1/2.2)`) is a continuous function of an average and is deliberately left as `Math.pow`:
quantising it would cost several LSBs in dark texels, and the accuracy budget for "exact" changes
here is zero.

The arithmetic was also moved into its own class. `TextureUtil`'s static initialiser allocates a
16 MiB direct int buffer and a `DynamicTexture` and calls GL, so nothing in it can be tested or
benchmarked on a plain JVM; `MipmapGammaBlend` is a pure function with no GL dependency and one
owner for its table.

**Numerical contract.** Bit-exact, not approximate: every table entry is evaluated with the
identical expression the call sites used (int→float conversion, `float` division by `255.0F`, the
double root, the narrowing cast back to `float`), so a lookup cannot differ from the old
computation by even one ULP and the generated mipmaps are byte-identical. Verified exhaustively for
all 256 indices and differentially for the whole filter (see below).

**Verification.** `perfbench.VerifyGammaLut` — **8,295,168 checks, 0 failures**: 256/256 decode
entries bit-identical (`floatToRawIntBits`), an exhaustive one-channel sweep (4 channel positions ×
256 values × 12 × 12 pinned neighbour texels, both filter branches), and 4,000,000 randomised ARGB
texel quadruples (half of them with a transparent texel, i.e. the gamma-correct branch) compared
against a verbatim copy of the vanilla `blendColors`/`blendColorComponent`.

**Benchmark** (`perfbench.TextureGammaBench`, JVM, median of 40 trials, realistic 16×16 sprite
data):

| workload | before | after | speedup |
|---|---|---|---|
| one texel, linear filter (4 decode + 4 encode) | 281.6–282.1 ns | 58.9–61.7 ns | **4.57–4.78×** |
| one texel, gamma filter (16 decode + 4 encode) | 248.6–252.5 ns | 49.8–51.1 ns | **4.94–4.99×** |
| one 16×16 sprite chain, 4 mip levels (85 texels) | 22.4–25.2 µs | 4.9–5.6 µs | 4.50–4.56× |
| 1,024-sprite block atlas | 26.4 ms | 5.7 ms | **4.6×** |
| 1,024-sprite atlas, with transparency | 23.4–24.1 ms | 4.7–4.8 ms | **4.9–5.1×** |
| one 256×256 atlas, 5 mip levels | 6.3–6.5 ms | 1.3–1.4 ms | 4.5–4.9× |

Target-runtime measurement (V8/TeaVM shape): the decode step alone goes **43.46 ns → 0.53 ns
(82×)** and a full gamma-branch texel goes **233.6 ns → 47.6 ns (4.9×)**; the remaining time is the
`pow(avg, 1/2.2)` encode that was intentionally kept for exactness (it is now 90 % of the cost of
the filter, which is why the end-to-end speedup is ~5×, not 20×).

Cold start (historical runtime table): 256 `Math.pow` calls once; the atlas pass is ~21 ms shorter
per build. Memory: 1 KiB, statically bounded, never re-allocated, cannot go stale (pure mapping).
See #12 for the current source-embedded implementation and its measured startup trade-off.

---

## #12 Cross-runtime exactness — dual source-embedded gamma tables (current PR)

**Files:** `net/minecraft/client/renderer/texture/GeneratedGammaTables.java` (new, generated);
`MipmapGammaBlend.java`; development-time `perfbench/gen_luts.py`; TeaVM probe
`perfbench/teavm/`; tests `perfbench/src/perfbench/VerifyEmbeddedLut.java`,
`tests/test_generated_luts.py`.

**Bottleneck / surprise.** A single 256-entry table is *not* portable between HotSpot and TeaVM.
TeaVM 0.9.2 lowers Java `float` arithmetic as JS doubles without `Math.fround`; 132/256 gamma
entries differ by ≤1 ULP (max abs 5.96e-8, max rel 1.80e-7). With one strict-Java table the real
TeaVM/V8 probe saw 84/203,072 browser output-byte mismatches; with one double-lowered table a JVM
differential saw 66/4,000,000 mismatches. Neither single table preserves both runtimes.

**Fix.** Commit two `private static final float[]` constant arrays (2×256 entries, 2,048 bytes of
data) with shortest round-tripping decimal literals. `ACTIVE` is selected once at class init by
`EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT`; `MipmapGammaBlend` holds a stable
final reference. No `Math.pow`, loop, static initializer computation, file load or per-lookup
allocation remains. Regenerate with `python3 perfbench/gen_luts.py --gamma-only`; verify with
`--check` and `python3 -m unittest discover -s tests -v`. Full design: `LUTS.md`.

**Verification.** `VerifyEmbeddedLut`: 401,292 checks / 0 failures (both tables bit-exact vs their
defining expressions, 132 expected cross-runtime ULP differences, index wrapping, HotSpot platform
selection, no runtime computation, 400,000 whole-blend cases). `VerifyGammaLut`: 8,295,168 / 0 on
HotSpot 17 and 25. Real TeaVM 0.9.2 → V8: `gamma_vs_prechange_browser_table` 256/0,
`gamma_index_wrap` 1,024/0, `blend_vs_legacy_browser_table` 203,072/0. The 118/203,072-byte
difference vs verbatim vanilla *browser arithmetic* is pre-existing in the shipped runtime table and
is preserved, not introduced.

**Benchmark** (one captured final `./perfbench/run_all.sh` snapshot; medians; microbench only, no
FPS):

| workload | before | after | change |
|---|---:|---:|---|
| JDK17 1,024-sprite atlas, linear | 29.87 ms | 6.75 ms | 4.43× / 77.40% less CPU |
| JDK17 1,024-sprite atlas, gamma | 35.97 ms | 4.76 ms | 7.56× / 86.77% less CPU |
| JDK25 1,024-sprite atlas, linear | 26.91 ms | 5.82 ms | 4.62× / 78.37% less CPU |
| JDK25 1,024-sprite atlas, gamma | 23.33 ms | 4.82 ms | 4.84× / 79.34% less CPU |
| V8/TeaVM decode op | 42.894 ns | 2.038 ns | 21.05× / 95.25% less |
| V8/TeaVM whole blend, linear | 927.216 ns | 243.277 ns | 3.81× / 73.76% less |
| V8/TeaVM whole blend, gamma | 928.740 ns | 237.145 ns | 3.92× / 74.47% less |

**Size / startup trade-off.** Java source 10,667 B; class 6,691 B; `MipmapGammaBlend.class` 1,384 B.
TeaVM gamma-only JS 178,309 B vs 164,387 B for the old runtime table (+13,922 B); first-process
parse/clinit 3.541/2.675 ms vs 3.465/2.670 ms. HotSpot cold clinit 853.50 µs (JDK17) / 826.02 µs
(JDK25) vs 202.75 / 199.07 µs for the old table, but steady-state lookup is parity (0.523 ns/op
JDK17, 0.522 ns/op JDK25). The one-time startup regression is accepted for the mandated
source-embedded constants and the measured steady-state CPU win. No full-game FPS claim.

---

## #13 `EntityRenderer.updateLightmap` — bit-exact invariant hoisting / CSE

**File:** `net/minecraft/client/renderer/EntityRenderer.java` (`updateLightmap`, reached from
`renderWorld`; `lightmapUpdateNeeded` is set by `updateRenderer`/`updateTorchFlicker` on client ticks).

**Bottleneck.** The 256-iteration loop re-read `worldclient.provider.getLightBrightnessTable()` and
recomputed `this.torchFlickerX * 0.1F + 1.5F` and `f * 0.65F + 0.35F` on every iteration, even
though none of them change inside the loop. `f5` was already assigned exactly `f4` in vanilla.

**Fix.** Hoist the three values into locals and keep `float f5 = f4` as the explicit CSE. No table,
no allocation, no platform-specific behavior.

**Verification.** `perfbench.LightmapBench` JVM replica: 4,608 float comparisons / 0 mismatches;
TeaVM/V8 replica `lightmap_cse_bit_exact`: 4,608 / 0. Edge cases include gamma, night vision, boss
color, End dimension and negative/large sun brightness.

**Benchmark.** JDK17 `5.181 → 4.831 µs/update` (`1.07×`), JDK25 `5.085 → 4.737 µs` (`1.07×`), V8 `9,577 → 8,982 ns` (`1.07×`). At a source-estimated 20 lightmap updates/s this is a few microseconds of CPU per second, not an FPS claim.

---

## #14 `MipmapGammaBlend` — exact binary64 encode threshold & `sqrt(x)` bucket tables (superseding remaining `Math.pow` in #12)

**Files:** `net/minecraft/client/renderer/texture/GeneratedGammaTables.java`, `MipmapGammaBlend.java`, `perfbench/gen_luts.py`, `perfbench/src/perfbench/VerifyGammaLut.java`, `VerifyEmbeddedLut.java`, `TextureGammaBench.java`, `perfbench/teavm/src/probe/TeaVMBench.java`, `tests/test_generated_luts.py`.

**Bottleneck.** After #11/#12 tabulated the 16/4 byte decode calls, the 4 remaining `Math.pow(avg, 1/2.2)` encode calls still accounted for ~90% of `MipmapGammaBlend`'s runtime on both HotSpot and TeaVM/V8 (`485–543 ns` vs `32 ns`, `1,723 ns` vs `155 ns`). Quantizing the output directly broke exactness (`49/4,096` byte mismatches in `encode_quantized_bytes`).

**Fix.** Exact IEEE-754 binary64 threshold & 513-entry `sqrt(x)` bucket tables generated in Python (`perfbench/gen_luts.py --gamma-only`):
- `THRESH_BLEND[257]` / `BUCKET_BLEND[513]` for `(int) (Math.pow(x, 1.0/2.2) * 255.0)`.
- `THRESH_COMP_JAVA[257]` / `BUCKET_COMP_JAVA[513]` and `THRESH_COMP_TEAVM[257]` / `BUCKET_COMP_TEAVM[513]` for `(int) ((float) Math.pow(x, 1.0/2.2) * 255.0F)` (capturing HotSpot vs TeaVM double-rounding semantics).
- Unrolled 4-texel fast path inside `MipmapGammaBlend.blend` (kept at `239` bytes so HotSpot C2 compiles `blend` and `encodeComponent` as `inline (hot)`!) and zero heap/scratch allocation (`mipmapBuffer` removed).

**Verification.** Exhaustive binary32 `[0,1]` check (`1,065,353,217` floats) and exhaustive `256^4 = 4,294,967,296` TeaVM 4-texel left-associated sum check proved the nearest sum to any threshold is `52,014` double-ULPs away (`0` runtime ambiguity). Runtime differential suites: `VerifyGammaLut` **12,442,624 checks / 0 failures**; `VerifyEmbeddedLut` **401,297 / 0**; TeaVM/V8 `blend_vs_legacy_browser_table` **203,072 / 0** & `blend_component_vs_legacy` **16,384 / 0**.

**Benchmark.**
- HotSpot JDK17 (`TextureGammaBench`): 1-texel linear `485.03 → 27.71 ns` (**17.50×**), 1-texel gamma `543.30 → 32.15 ns` (**16.90×**), 16×16 gamma chain `48.032 → 1.924 µs` (**24.96×**), 1,024-sprite atlas `44.72 → 4.78 ms` (**9.35×** linear) / `51.12 → 4.57 ms` (**11.17×** gamma), 256×256 atlas `10.72 → 1.11 ms` (**9.67×**).
- HotSpot JDK25: 16×16 chain `19.16×` linear / `16.98×` gamma; 1,024-sprite atlas `7.82×` / `7.36×`; 256×256 atlas `8.79×`.
- TeaVM 0.9.2 / V8 12.4 (`TeaVMBench`): `blend_shipped_linear` `1,723.354 → 155.381 ns` (**11.09×**), `blend_shipped_gamma` `1,701.795 → 247.034 ns` (**6.89×**).

---

## #15 `MathHelper.func_181159_b` / `func_181161_i` — JS-only native `Math.atan2` & `1/Math.sqrt` dispatch

**File:** `net/minecraft/util/MathHelper.java` (`56` call sites across `RenderDragon`, `RenderGuardian`, `EntityLivingBase`, compass).

**Bottleneck.** TeaVM compiles `Double.doubleToRawLongBits` / `Double.longBitsToDouble` inside `func_181161_i` (`rsqrt`) via `DataView` typed-array punning, making `func_181161_i` cost `55.588 ns` and `func_181159_b` cost `139.490 ns` in V8 (compared to `8.919 ns` for `1.0 / Math.sqrt` and `37.345 ns` for `Math.atan2`), whereas HotSpot turns those bit-ops into register moves (`10.25 ns`, **5.77×** faster than `Math.atan2` on JDK 17).

**Fix.** Add `IS_JAVASCRIPT = EagRuntime.getPlatformType() == EnumPlatformType.JAVASCRIPT`. In JS, `func_181159_b` dispatches directly to `Math.atan2`, `func_181161_i` dispatches to `1.0D / Math.sqrt`, and `<clinit>` skips allocating/populating `field_181164_e[257]` and `field_181165_f[257]`. Desktop/JVM stays bit-identical. Verified `<clinit>` graph: neither `EagRuntime` nor `LogManager` touches `MathHelper`.

**Verification & Benchmark.** `CrossRuntimeMathBench`: `mhAtan2MaxErr = 7.423e-6` rad on JVM; TeaVM `mh_atan2_tolerance` `4,097 / 0`, `mh_rsqrt_exact` `4,096 / 0`. V8 speeds up **3.74×** (`139.490 → 37.345 ns`) on `func_181159_b` and **6.23×** (`55.588 → 8.919 ns`) on `func_181161_i`; JVM retains its **5.77×** (JDK 17) / **2.44×** (JDK 25) bit-op advantage.

---

## #16 `TileEntityBeaconRenderer` & `RenderGuardian` — rotational-symmetry trig reductions

**Files:** `client/renderer/tileentity/TileEntityBeaconRenderer.java` (`8 → 2` trig), `client/renderer/entity/RenderGuardian.java` (`16 → 2` trig).

**Fix.** Replace `4` offset `(cos, sin)` pairs at `a + π/4 + k·π/2` (`Beacon`) and `8` offset pairs (`Guardian`) with single `(cos(a), sin(a))` evaluations rotated by exact constant factors (`0.7071067811865476D` etc.) and signs.

**Verification & Benchmark.** `CrossRuntimeMathBench`: `beaconMaxAbs = 2.220e-16`, `guardianMaxAbs = 2.776e-16`; TeaVM `trig_symmetry_tolerance` `4,096 / 0`. Speedups: Beacon **3.49×** JDK17 (`93.00→26.68 ns`), **3.23×** JDK25 (`89.40→27.67 ns`), **3.11×** V8 (`117.331→37.687 ns`); Guardian **5.42×** JDK17 (`181.80→33.52 ns`), **5.38×** JDK25 (`169.97→31.62 ns`), **5.63×** V8 (`215.071→38.197 ns`).

---

## #17 `EntityRenderer.updateFogColor` — `Math.pow(f, 0.25D)` → `Math.sqrt(Math.sqrt(f))`

**File:** `client/renderer/EntityRenderer.java`.

**Verification & Benchmark.** Bit-identical on HotSpot & V8 (`fogBitDiffs = 0`; V8 `fog_pow25_bit_exact` `64 / 0`). Speedups: JDK17 **2.35×** (`25.59→10.91 ns`), JDK25 **5.24×** (`19.17→3.66 ns`), V8 **16.14×** (`60.038→3.721 ns`).

---

## #18 `InstancedFontRenderer` — exact `Math.pow(2.718D, -x)` → `Math.exp(-x * LN_2_718)`

**File:** `net/lax1dude/eaglercraft/v1_8/opengl/InstancedFontRenderer.java`.

**Fix.** Instead of changing the base (`2.718 != e`), store `private static final double LN_2_718 = Math.log(2.718D)` and compute `Math.exp(-x * LN_2_718)`, preserving the exact mathematical curve.

**Verification & Benchmark.** `fontMaxUlp = 0` (`CrossRuntimeMathBench`); TeaVM `font_exp_tolerance` `4,096 / 0`. Speedups: JDK17 **1.17×** (`21.66→18.53 ns`), JDK25 **2.03×** (`17.70→8.73 ns`), V8 **5.54×** (`71.659→12.928 ns`).

---

## #19 `BlockNote` — discrete 25-entry `PITCH_TABLE`

**File:** `net/minecraft/block/BlockNote.java`.

**Fix.** Replace `(float) Math.pow(2.0D, (double)(note - 12) / 12.0D)` with clamped lookup into `private static final float[] PITCH_TABLE` (`25` exact constants).

**Verification & Benchmark.** `noteBitDiffs = 0` (`0` ULP across all `25` entries); unit-tested by `tests/test_generated_luts.py::test_note_pitch_table_is_25_constants`. Speedups: JDK17 **19.13×** (`26.90→1.41 ns`), JDK25 **12.22×** (`16.82→1.38 ns`), prototype V8 **10.71×** (`56.88→5.31 ns`).

---

## #20 `RenderGlobal.renderWorldBorder` — `Math.pow(d1, 4.0D)` → `(d1*d1)*(d1*d1)`

**File:** `net/minecraft/client/renderer/RenderGlobal.java`.

**Verification & Benchmark.** `borderMaxAbs = 0.0` (`CrossRuntimeMathBench`). Speedups: JDK17 **18.70×** (`31.91→1.71 ns`), JDK25 **13.64×** (`16.54→1.21 ns`), prototype V8 **85.82×** (`101.29→1.18 ns`).

---

## #14 Built-in deferred/PBR shaders — hot-loop sweep (GLSL, not Java)

**Files:** `sources/resources/assets/eagler/glsl/deferred/lib/pbr_lighting.glsl`,
`lib/pbr_env_map.glsl`, `lighting_point.fsh` (per point light), `lighting_sun.fsh` (via the lib),
`forward_core.fsh`, `accel_particle_forward.fsh`, `realistic_water_render.fsh`,
`forward_glass_highlights.fsh` (the last two carry their own copies of the BRDF).

**Where the hot loops are.** `lighting_point.fsh` runs once per fragment of every dynamic light's
bounding mesh (up to 12 lights per chunk bucket, no depth test), and `forward_core.fsh` /
`accel_particle_forward.fsh` / the water and glass variants run `eaglercraftLighting()` inside a
per-light loop for every entity/particle fragment. So the per-invocation cost of the BRDF and the
per-light loop body are the two things worth attacking, not the once-per-sky-update convolution
shaders (`skybox_irradiance.fsh`, 156 taps/texel) or the temporally amortised SSR march.

**What changed.**

1. `pow(x, 5.0)` → `x2 * x2 * x` in the Fresnel-Schlick term of every PBR lighting function
   (7 sites: the two libs, the water and glass copies). `highp pow()` is `exp2(5.0 * log2(x))` on
   GLES3 drivers, i.e. two SFU ops plus the surrounding multiplies; three ALU multiplies replace it.
   This is the only transcendental in the light-loop body.
2. Point-light pass (`lighting_point.fsh`):
   - the facing test now runs on the **unnormalised** light vector — `normalize()` only scales by
     the positive `1/|d|`, so it cannot flip the sign of `dot(lightDir, N)` — which lets fragments
     the light cannot reach skip `normalize()`, the colour divide and the whole BRDF;
   - one `dot()` is now shared by the falloff and the `normalize()`;
   - the colour texture fetch, two dead `lightmapCoords` stores and the second `u_inverseViewMatrix4f`
     multiply were removed (the view direction reuses the inverse-projection result exactly as
     `deferred_combine.fsh` already does).
3. Per-light loops (`forward_core`, `accel_particle_forward`, water, glass): both culls (facing and
   the `0.025` radiance cut-off) moved **before** the `normalize()` and the `1/dist2` divide. The
   radiance test now compares `L.r+L.g+L.b` against `0.025 * dist2`, which is the same comparison
   with the positive falloff moved across the inequality. The emissive (`material.b == 1.0`) case
   keeps the old "never culled by facing" behaviour. `forward_core`/`accel_particle_forward` test
   `dot(worldPos - lightPos, N) >= 0`, the water/glass loops the sign-flipped
   `dot(lightPos - worldPos, N) <= 0`; both are the same test the shader did on the normalised
   vector, and the harness replays the forward and accel forms (water/glass carry the same two
   lines with the vector negated).

**Old → new (per shader invocation, measured by the census in `perfbench/shader/`)
| shader | pow/SFU pairs | sqrt/invsqrt | texture fetches |
|---|---|---|---|
| `lighting_point` per light-fragment | 0.888 → **0.000** | 3.775 → 3.643 | 4.000 → 3.888 |
| `forward_core` per entity-fragment (8 lights) | 3.414 → **0.000** | 12.414 → **7.664** | – |
| `accel_particle_forward` per fragment (8 lights) | 7.990 → **0.000** | 16.990 → 16.990 | – |

`pow/SFU pairs` is below 1 for the point-light pass because 11.2 % of the covered fragments are now
discarded before the BRDF. The `forward_core` `sqrt` drop is that shader's cull reordering: the
lights that fail the facing test no longer pay their `normalize()` (3.41 of 8 lights contribute on
average in the modelled frame, so ~4.75 normalises per fragment are gone).

**Verification (equivalence).** `./perfbench/shader/run_bench.sh` replays the old and new scalar
`float` math on a 1920×1080 synthetic gbuffer (blocky interior, 8 lights, half of them embedded in
solid blocks like a torch/glowstone, 5 % metal / 25 % mirror / 2 % emissive materials, RGBA16F
lighting target). All comparisons use identical inputs.

| kernel | discard-mask mismatches | max ULP | max &#124;diff&#124; | RGBA16F value differs | 8-bit `post_tonemap` output differs |
|---|---|---|---|---|---|
| `lighting_point`, 16.51 M fragments (14.66 M kept) | **0** | 12 | 4.8e-7 | 182 | **0** |
| `forward_core` loop, 250 k fragments | 0 (same 853,479 lights kept) | 2604 | 4.8e-7 | 9 | **0** |
| `accel_particle` loop, 250 k fragments | 0 (same 1,997,528 lights kept) | 10 | 4.8e-7 | 9 | 1 |
| `eaglercraftLighting` + IBL, 600 k random cases (incl. g = 0.9 / 0.964 boundaries) | – | 4298 | 1.9e-6 | – | – |

The ULP tail is the GGX specular peak: `NDF = roughness^4` and `denom = NdotH^2 (NDF-1) + 1` make
`1/denom^2` explode as `NdotH → 1` for smooth metals, so a 1-ULP change in `fresnel` is amplified
by up to ~10^3 there. Absolute deviation stays ≤ 4.8e-7 (well under one RGBA16F step) and **no
fragment changes its 8-bit output** except one `accel_particle` fragment sitting exactly on a
rounding boundary. Light/skip *decisions* are bit-identical everywhere.

**Benchmark (CPU replay; `gcc 12.2.0 -O2 -std=c11`, no `-ffast-math`).**

| kernel | old | new | speedup (5 runs) |
|---|---|---|---|
| `lighting_point` per covered light-fragment | 100.8 ns | 83.2 ns | **1.18–1.23×** |
| `forward_core` light loop per fragment (8 lights) | 317.6 ns | 259.7 ns | 1.16–1.22× |
| `accel_particle_forward` loop per fragment (8 lights) | 506.1 ns | 412.2 ns | 1.19–1.23× |
| `eaglercraftLighting` x86 instruction count (straight-line, `-DPBR_INSN`) | 147 | 139 | **5.4 % fewer** |

Reproduce: `./perfbench/shader/run_bench.sh`. The CPU ratios are an **upper bound** and are not a
GPU speedup claim: libm `powf()` is dozens of times dearer than a GPU SFU `pow()`, while the
culling wins are hardware-independent. The deterministic numbers (pow/SFU pairs per invocation,
texture fetches, `sqrt` count, x86 instruction count) are the reviewable part.

**GLSL validity.** `./perfbench/shader/run_validate.sh` assembles each shader the way
`ShaderSource.java` does (recursive `#EAGLER INCLUDE`, injected `#line`) and compiles it, plus the
compile-flag combinations the pipeline builds, with the real glslang front end: **13/13 variants
compile**, and deliberately broken shaders (undefined identifier, wrong operand type) are rejected.

**Rejected in this PR (measured, then backed out).** Sharing one `1.0/w` reciprocal across the two
homogeneous divides in the transform prologue (`lighting_point`, `forward_core`,
`accel_particle_forward`, water, glass): it is the same 3 divides → 1 rcp + 3 mul that drivers
already emit, and it costs last-ULP agreement. With it the point-light pass had max 2116 ULP and
**47 of 14.66 M fragments changed an 8-bit output**; without it, 12 ULP and 0. Not worth it: 1 ULP
of visible risk for a saving the driver makes anyway (compare FINDINGS #2 and the rejected
`Vec3.normalize` reciprocal in the LUT sweep).

**Follow-ups left alone.** (a) `eaglercraftLighting` recomputes the material-only terms
(`roughness`, `roughness^4`, `gs`) on every light iteration; a "precomputed material" entry point
would remove ~6 more instructions per light but must be kept valid for the drift between the lib and
the water/glass copies — GLSL compilers already do LICM on those expressions, so the win is
unproven. (b) The two `u_metalsLUT` fetches in the metal branch cannot be merged without changing
the LUT asset. (c) `skybox_irradiance.fsh` (156 taps/texel) only runs when the sky changes, and
`reproject_ssr.fsh` accumulates one sample per frame, so neither is a per-frame loop.

---

## LUT / approximation candidate sweep — measured rejections

Everything below was analysed (function, domain, precision, frequency, memory) and **measured**
before being rejected. Numbers in the "measured" column are from HotSpot 17/25 or from
`perfbench/js/bench_math_lut.js` / TeaVM prototypes (V8 12.4, the actual browser JS target).

| candidate | why it looked attractive | measured | verdict |
|---|---|---|---|
| **sqrt LUT** (`MathHelper.sqrt_float/sqrt_double`, 132 direct sites, every distance/normalise/physics path) | huge call count, monotone, easily tabulated | `Math.sqrt` = **1.85 ns** (V8) / intrinsic (JVM). Any table needs range reduction + scaling + interpolation, i.e. ≥4× the work, plus unbounded-accuracy loss over the double range | **rejected**: cannot beat one instruction |
| **fast inverse sqrt** (Quake/magic + Newton) as a `sqrt` replacement | already used inside `MathHelper.func_181161_i` | On V8, magic bit-punning `func_181161_i` = **55.6 ns** vs `1/Math.sqrt` = **8.9 ns** (`6.23×` slower in JS!). Now `func_181161_i` keeps magic bit-ops on JVM and dispatches to `1/Math.sqrt` in JS (#15) | **rejected for JS; platform-dispatched (#15)** |
| **`Vec3.normalize` via reciprocal multiply** (`sqrt` + 3 divides → `1/sqrt` + 3 multiplies) | 1.68× on the operation | `sqrt`+3 div = 4.73 ns vs `1/sqrt`+3 mul = 2.83 ns, but differs in last ULP on `getLook`/ray-tracing ties | **rejected**: 1 ULP ray-trace tie risk |
| **smaller sin table** (4,096 entries + linear interpolation) to cut the 256 KiB table into L1 | 16× less memory, *better* accuracy | 4,096-entry **lerp** 3.688 ns vs small step 2.074 ns in `bench_v8_math.js` (`1.78×` slower) | **rejected for speed** |
| **dropping `Math.fround` from `MathHelper.sin/cos`** | 1.9× on the lookup | shifts `~0.4%` of lookups by 1 step, perturbing worldgen seeds (`MapGenCaves`, `WorldGenBigTree`, etc.) | **rejected**: worldgen seed determinism |
| **degree-indexed trig** (`sinDeg`/`cosDeg`) | removes multiply | multiply is ~0.6 ns; inputs rarely integral degrees | **rejected** |
| **`wrapAngleTo180_float/double`: replace `%` with truncation** | float `%` is slow in JS | `7.96 → 1.49 ns`, not bit-exact, tiny frequency | **rejected** |
| **floor LUT / `Math.floor` rewrite** | huge block-iteration call count | `Math.floor` parity (`1.15 ns`) | **rejected** |
| **pure-FP polynomial / `PureAtan2Lut` replacing `MathHelper.func_181159_b`** | avoid `field_181164_e/f` tables everywhere | `poly_atan2` slower on BOTH HotSpot (`10.35` vs `7.67 ns` JDK17; `9.29` vs `8.57 ns` JDK25) and V8 (`40.69` vs `28.28 ns`); `atanOctant` matched `Math.atan2` on V8 (`27.68 ns`) with extra branching | **rejected**: JS uses native `Math.atan2` (#15) |
| **`WorldGenBigTree.func_181631_a` `pow(x, 2.0)` → `x*x`** | trivial | slower on HotSpot JDK17 (`159.8 → 201.6 ns`) and TeaVM/V8 (`162.6 → 198.3 ns`) because JIT/V8 already special-case `pow(x, 2.0)` | **rejected** |
| **`GlStateManager.gluPerspective` `cos/sin → 1/tan`** | fewer trig calls | faster on HotSpot (`27.41 → 22.64 ns` JDK17), neutral on target TeaVM/V8 (`14.38 → 14.46 ns`) | **rejected** |
| **`ModelDragon.render` trig CSE (`17` saved `Math.cos`)** | fewer trig calls in dragon render | faster on HotSpot (`1536 → 1397 ns`), slightly slower in TeaVM/V8 (`849.3 → 861.9 ns`) | **rejected** |
| **block-property LUTs in the AO mesher** | virtual dispatch in section rebuild | registry hash lookup costs as much as virtual dispatch; chunk lookup dominates | **rejected** |
| **caching `Math.sin/cos` per particle** | saves 1 duplicate `sin(pitch)` | ~8 µs/frame at 4,000 particles vs mutability/invalidation contract | **rejected** |

### Follow-up checked and closed: `PBRTextureMapUtils.generateMipmapDataIgnoreAlpha`

The one other mipmap builder in the tree (`opengl/ext/deferred/texture/PBRTextureMapUtils:140`, used
by `EaglerTextureAtlasSpritePBR` for legacy/deferred PBR materials) looked like a second candidate
for the same treatment. It is not: its filter is a plain integer average
(`((c1 >> 2) << 24) | ...`), **no `Math.pow` and no gamma decode at all**, so there is nothing to
table. Left untouched (correctly: it deliberately ignores alpha).

While reading it, an unrelated pre-existing indexing bug was noticed and deliberately **not**
fixed (fixing it would change rendering output, which this PR must not do):
`for (int j = 0; j < len; ++j) { x = (j % len) << 1; y = (j / len) << 1; ... }` uses `len` (the
*level's* texel count, `lvlW * lvlW`) where the row length `lvlW` is meant, so `x` runs up to
`2 * (lvlW*lvlW - 1)` and `y` is always 0 — every generated PBR mip level above 0 is sampled from
the wrong texels. Only reached when `aint[i] == null` for `i > 0` (the atlas normally supplies all
levels), which is presumably why it has gone unnoticed. Recorded in HOTSPOTS.md as a correctness
observation.

### Two measured "no" results that shaped the design

- **The 256 KiB sin table is not a cache problem** on the machines measured: the "hot subset" run
  (64 distinct angles) was 1.073 ns vs 1.092 ns for uniform random angles — the table is L2-resident
  and the access pattern does not matter. That is why shrinking it was rejected rather than
  optimised.
- **A smaller table with interpolation is slower than a bigger step table.** Linear interpolation
  costs two dependent loads plus a multiply-add, which dominates any L1 saving: 2.34 ns (4,096 lerp)
  vs 1.09 ns (65,536 step). LUTs beat libm but they do not beat each other on size.

---

## #14 FNAW character meshes (`assets/eagler/mesh/*.mdl`) — QEM decimation + Forsyth vertex-cache reordering

**Files:** `sources/resources/assets/eagler/mesh/*.mdl` (all 15 meshes across the 5 `HighPolySkin`
characters), `scripts/optimize_fnaw_meshes.py`, `perfbench/js/bench_fnaw_meshes.js`,
`tests/test_fnaw_meshes.py`

**Bottleneck.** The five FNAW player skins (`LONG_ARMS`, `WEIRD_CLIMBER_DUDE`, `LAXATIVE_DUDE`,
`BABY_CHARLES`, `BABY_WINSTON`) pack 3,470 vertices and 4,875 triangles across 15 `.mdl` files
(112,785 B raw / 111,570 B GPU VBO+IBO), and their original index buffers had poor post-transform
vertex cache locality (average ACMR 1.47 on a 32-entry FIFO, up to 1.96 on `weirdclimber0.mdl` and
1.84 on `longarms0.mdl`, causing 7,181 shaded vertices per full-suite pass).

**Fix.**
- **QEM edge-collapse decimation** (`scripts/optimize_fnaw_meshes.py`, `--ratio 0.70 --eye-ratio 0.70 --max-err 0.020`):
  reduces triangle count by 30.1 % while preserving open boundary loops (skirt/torso seams), UV
  island seams, manifold link conditions, triangle winding, connected components, and bounding
  boxes (RMS surface deviation 0.0016–0.0068 model units, max ≤ 0.0196).
- **Tom Forsyth linear-speed vertex-cache optimization + pre-transform vertex remapping**: reorders
  triangles for a 32-entry post-transform FIFO and remaps vertices in order of first reference,
  cutting post-transform shaded vertices across all 15 meshes from 7,181 to 2,834 (**−60.5 %**,
  overall ACMR 1.473 → 0.831).

**Verification.** `tests/test_fnaw_meshes.py` verifies the exact `EaglerMeshLoader.reloadMesh`
binary contract, index bounds, non-zero normals, zero degenerate/non-manifold triangles, preserved
connected component counts, and vertex/triangle/byte budgets across all 15 `.mdl` files.

**Benchmark** (`node perfbench/js/bench_fnaw_meshes.js`):

| Metric | Before | After | Change / Speedup |
|---|---:|---:|---:|
| Total vertices (15 meshes) | 3,470 | 2,670 | −23.1 % |
| Total triangles (15 meshes) | 4,875 | 3,409 | −30.1 % |
| Asset file size (`.mdl` raw / gzip-9) | 112,785 B / 83,013 B | 85,029 B / 60,613 B | −24.6 % / −27.0 % |
| GPU VBO + IBO memory | 111,570 B | 83,814 B | −24.9 % |
| Shaded vertices (32-FIFO, all 15 meshes) | 7,181 (ACMR 1.47) | 2,834 (ACMR 0.83) | −60.5 % (2.53× fewer) |
| `EaglerMeshLoader` decode (all 15 meshes) | 104.56 µs | 81.86 µs | **1.28×** |
| Vertex transform + 32-FIFO (20 FNAW players) | 1,843.33 µs | 1,144.88 µs | **1.61×** |

