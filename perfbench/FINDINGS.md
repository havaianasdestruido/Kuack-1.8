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

**Benchmark.** JDK17 3.021 → 2.825 µs/update (1.07×, 6.49% less); JDK25 2.983 → 2.842 µs (1.05×,
4.73% less); V8 6,635.358 → 6,449.422 ns (1.03×, 2.80% less). At a source-estimated 20 lightmap
updates/s this is a few microseconds of CPU per second, not an FPS claim.

---

## #14 `BlockModelRenderer` — allocation-free mesher scratch (AO + standard paths)

**File:** `client/renderer/BlockModelRenderer.java`

**Bottleneck.** Both mesh paths allocated per *rendered block*: the AO path allocated a
`float[12]` quad-bounds buffer, a `BitSet(3)` and an `AmbientOcclusionFace` (which itself owns
2 arrays + 6 `BlockPos`), and a fresh `BlockPos` per face with quads; the standard path allocated
a `BitSet(3)` plus a `BlockPos.MutableBlockPos` per block, a `float[12]` per block in the deferred
pipeline and an `int[4]` per quad. A chunk-section rebuild renders up to 4,096 blocks, so one
rebuild produced roughly **2.4 MB** of short-lived scratch garbage before any vertex data is
counted (measured: 582 B/block AO, 300 B/block standard+deferred, 117 B/block standard).

**Fix.** Per-instance scratch fields (`aoQuadBoundsScratch`, `standardQuadBoundsScratch`,
`quadBoundsFlagsScratch`, `deferredBrightnessScratch`, `aoFacingOffsetScratch`,
`standardOffsetPointerScratch`, `aoFaceScratch`) plus the standard path's existing
`offsetEvenFaster` pattern applied to the AO path. Safety rests on three provable invariants, all
verified by reading the code:

- `fillQuadBounds` writes **all 12** `quadBounds` entries and sets boundsFlags bits 0 and 1
  explicitly with `set(index, value)` before any read (bit 2 is never used anywhere);
- `AmbientOcclusionFace.updateVertexBrightness` writes all 4 `vertexColorMultiplier` and all 4
  `vertexBrightness` entries in **both** branches — `VertexTranslations` is a bijection of 0..3
  for every facing (DOWN(0,1,2,3), UP(2,3,0,1), NORTH(3,0,1,2), SOUTH(0,1,2,3), WEST(3,0,1,2),
  EAST(1,2,3,0));
- the deferred `int[4]` is fully written before `putBrightness4` in both branches.

Threading: `BlockModelRenderer` has exactly one instance (`BlockRendererDispatcher`), chunk
meshing is queued and drained on the render thread by Eaglercraft's `ChunkUpdateManager` (no
`Thread` anywhere in the chunk-render package; TeaVM has none), so instance scratch is safe and
TeaVM-compatible — no `ThreadLocal`. The class already used instance-scratch `BlockPos` fields
(`blockpos0..5`) for the standard path; this extends the same pattern.

**Verification.** `perfbench.VerifyBlockMesh` — 1,600 randomized (model, block, position,
checkSides) cases over the complete mode grid (AO on/off × anaglyph × deferred pipeline ×
dynamic-lights × random synthetic models with flush/inset/general quads × 12 real blocks), each
case rendered through BOTH the pre-change implementation (kept as
`perfbench.BlockModelRendererVanilla`, fresh scratch per call) and the production implementation
(one instance reused across **all** cases, which is the staleness detector), comparing the
complete recorded `WorldRenderer` call sequences: **0 mismatches**. Negative test: deleting a
single `quadBounds` write in the production file produces 413 mismatches immediately — the
harness detects exactly the stale-scratch bug class this change could have introduced.

**Benchmark.** `perfbench.BlockMeshBench` (JDK 25 Temurin, `-Xmx2g -XX:+UseParallelGC`, 2,048
blocks/pass, **interleaved arms** — the arms allocate very differently, so sequential trials
attribute GC timing to whichever arm runs second; medians of 15 interleaved trials, three runs):

| workload | vanilla ns/block | prod ns/block | speedup | vanilla B/block | prod B/block |
|---|---|---|---|---|---|
| AO mesh (default) | 4,860–5,276 | 4,642–5,016 | **1.05–1.07×** | 582 | **87** (−85%) |
| AO mesh + dynamic lights | 3,807–4,406 | 3,764–4,212 | 1.01–1.05× | 540–565 | **44–80** (−85%) |
| standard mesh, deferred | 3,553–3,901 | 3,597–3,897 | 0.99–1.02× | 300 | **44** (−85%) |
| standard mesh, non-deferred | 1,795–2,190 | 1,828–2,420 | 0.91–1.04× | 117 | **45** (−70%) |

Isolation experiment (`perfbench.BlockMeshDiagBench`, bench-only variants generated from the
vanilla reference, interleaved): one run gave vanilla 4,647 / shared 4,490 / shared+offsetEvenFaster
4,440 / production 4,425 ns/block; across runs the per-variant ordering flips within ±10–15% while
the allocation deltas stay exact — i.e. the scratch reuse is what moves allocation, the
`offsetEvenFaster` swap is neutral on its own, and run-level CPU medians on HotSpot are
noise-dominated at this kernel size.

**Honest read.** On HotSpot the CPU effect is small: interleaved medians favour the change
(1.05–1.07× on the dominant AO path) because the vanilla arm carries 7× the allocation and eats
the young-gen collections, but min-of-trials sometimes favours vanilla (4.2–4.6 vs 4.4–4.8 µs in
GC-free trials — fresh scratch gives the JIT marginally better escape-analysis options). The
allocation reduction is unambiguous and stable (−85% on every mode) and it is the mechanism that
matters for the actual deployment target: the browser (TeaVM→JS→V8), where mesh allocations
become JS garbage collected by the engine's GC. No JS/V8 measurement was made (the TeaVM probe
would need the whole World/Block closure), so there is no browser number and no FPS claim.

---

## LUT / approximation candidate sweep — measured rejections

Everything below was analysed (function, domain, precision, frequency, memory) and **measured**
before being rejected. Numbers in the "measured" column are from this session on the JVM (Temurin
25, `-Xmx2g -XX:+UseParallelGC`) or from `perfbench/js/bench_math_lut.js` (V8 12.4, the actual
TeaVM target); the JS numbers are quoted where the runtime behaviour differs materially.

| candidate | why it looked attractive | measured | verdict |
|---|---|---|---|
| **sqrt LUT** (`MathHelper.sqrt_float/sqrt_double`, 132 direct sites, every distance/normalise/physics path) | huge call count, monotone, easily tabulated | `Math.sqrt` = **1.56 ns** (JS) / 1.6 ns (JVM) — a hardware instruction. Any table needs range reduction + scaling + interpolation, i.e. ≥4× the work, plus unbounded-accuracy loss over the double range | **rejected**: cannot beat one instruction |
| **fast inverse sqrt** (Quake/magic + Newton) as a `sqrt` replacement | already used inside `MathHelper.func_181161_i` | `1/Math.sqrt` = 2.63 ns; a JS-side magic-constant rsqrt (typed-array bit punning, which is what TeaVM would emit) = **12.1 ns**, 7.8× *slower* than `Math.sqrt`. The engine's own `func_181161_i` stays where it is (only `func_181159_b` uses it, to normalise two doubles for the asin table) | **rejected** |
| **`Vec3.normalize` via reciprocal multiply** (`sqrt` + 3 divides → `1/sqrt` + 3 multiplies) | 1.68× on the operation | `sqrt`+3 div = 4.73 ns vs `1/sqrt`+3 mul = 2.83 ns, but the two forms differ in the last ULP, and `Vec3.normalize` feeds `getLook` → ray tracing and `AxisAlignedBB.calculateIntercept` where exact ties are decided | **rejected**: 1 ULP of risk for ~2 ns on a path called O(10)–O(100) times per frame |
| **smaller sin table** (4,096 entries + linear interpolation) to cut the 256 KiB table into L1 | 16× less memory, *better* accuracy | 4,096-entry **step** table 1.00 ns/err 1.5e-3; 4,096-entry **lerp** 2.34 ns/err 2.4e-6; current 65,536-entry step 1.09 ns/err 9.6e-5. The lerp table is *slower* than the current table (two loads + FMA) and 40× more accurate; the step table is faster but 16× less accurate | **rejected for speed**; recorded as an accuracy/memory option if anyone ever needs `sin` to be more precise than vanilla |
| **dropping `Math.fround` from `MathHelper.sin/cos`** (the float round-trip is a third of the cost) | the single hottest math function in the game | 2.08 ns → 1.09 ns (**1.9×**), but the index then differs from Java's float semantics for ~0.4 % of calls (float ULP at the products involved is up to 0.004, and any product within that of an integer truncates differently), shifting `sin` by one table step | **rejected**: `MathHelper.sin` is used by `MapGenCaves`, `MapGenRavine`, `WorldGenBigTree`, `WorldGenMinable` and `ChunkProviderHell` — a 0.4 % lookup perturbation changes generated terrain, breaking seed determinism |
| **degree-indexed trig** (`sinDeg`/`cosDeg` for the ~100 `MathHelper.sin(x * 0.017453292F)` sites) | removes a multiply, tables are smaller | the multiply is 0.62 ns (baseline ALU op in the same benchmark); the inputs are almost never integral degrees, so a degree table would still need interpolation | **rejected**: no measurable saving |
| **`wrapAngleTo180_float/double`: replace `%` with truncation** | float modulo is surprisingly slow in JS | `x % 360` = **7.96 ns** vs `x - 360*trunc(x/360)` = 1.49 ns (**5.3×**), but the replacement is not bit-exact (double rounding on the quotient), and the call count is 2–6 per entity per tick | **rejected**: 5× of nothing |
| **floor LUT / `Math.floor` rewrite** (`floor_double`, `floor_float`; enormous call count in block iteration) | looks like a classic LUT | `Math.floor` = 1.152 ns, `MathHelper.floor_double` = **1.146 ns** — already at parity with the hardware instruction after JIT/TeaVM lowering | **rejected**: nothing to win |
| **`atan2` LUT** (`func_181159_b`) | native `Math.atan2` is 18.4 ns | the engine already implements atan2 with a 257-entry asin/cos table + one Newton step; the remaining native `atan2` sites are per-entity-per-frame at most (`RenderDragon`, `RenderGuardian`, compass) | **keep existing table** |
| **block-property LUTs in the AO mesher** (`Block.getAmbientOcclusionLightValue()` is a virtual call executed up to ~70×/block during a section rebuild, and only two blocks deviate from the 0.2 default) | biggest single numeric consumer in chunk meshing | the only cheap key would be `Block.getIdFromBlock`, which is an identity-hash registry lookup — as expensive as the virtual dispatch it would replace; a per-state table needs `IBlockState.getStateId()`, another hash lookup. The surrounding `getBlockState(pos)` chunk lookup dominates anyway | **rejected**: not enough win for a new global table + invalidation contract |
| **`Math.pow(2.718, -x)` → `Math.exp(-x)`** (`InstancedFontRenderer` per-vertex fog) | 43.0 ns → 5.6 ns | but it is once per text *batch*, not per vertex (the batch aggregates the whole string), and `2.718 != e` would change the fog curve | **rejected**: frequency is negligible |
| **`Math.pow(x, 2.0)` → `x * x`** (`WorldGenBigTree` leaf mask, `SpawnerAnimals.MOB_COUNT_DIV`, `VisGraph` constants) | trivial, obvious | the `WorldGenBigTree` one runs per leaf, i.e. per tree, and world generation is the one place bit-exactness must not move; the others are one-time constants already | **rejected**: no measurable effect |
| **caching `Math.sin/cos` per particle** (5 calls per particle per frame, one of them a duplicate `sin(pitch)`) | 4,000 particles → 20,000 calls | the duplicate `sin(pitch)` CSE saves 1 of 5 lookups ≈ 2 ns/particle ≈ 8 µs/frame at the 4,000-particle cap, but `rotationYaw/Pitch` are mutated by several particle types, so a cache needs invalidation | **rejected**: exact-CSE only worth ~8 µs/frame at the worst-case particle count, not worth a staleness contract |

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
