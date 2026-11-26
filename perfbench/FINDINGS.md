# Optimization findings (Kuack-1.8 / Minecraft 1.8 + Eaglercraft)

Working notes for the algorithmic-hotspot sweep. Every entry records: file(s), original
bottleneck, old vs new complexity, the verification used, and the measured benchmark.

Benchmarks run under `JAVA_HOME=/tmp/jdkpkg/jdk4py/java-runtime`, `-Xmx2g -XX:+UseParallelGC`.
Full-tree compile gate: `./perfbench/compile_main.sh` (baseline 1525 problems / 104 error files).

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
(`perfbench.VerifyBiomeColor`: 570,240 checks, 0 failures).

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
