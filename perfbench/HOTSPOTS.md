# Remaining high-complexity algorithms (not modified)

Ranked by estimated impact on frame time / tick time. Each entry records the site, the shape of the
cost, and why it was left alone. Sites marked **measured** had a benchmark written; the number is the
cost at a realistic input size.

## Measured, and deliberately not changed

| site | shape | measurement | why not changed |
|---|---|---|---|
| `EnchantmentHelper.getMaxEnchantmentLevel` (`enchantment/EnchantmentHelper.java:139`) | 36–40 NBT slots × string-keyed `getTagList("ench")` + NBT compound fetch, 3×/frame from `EntityRenderer` (:1721/:1840/:2567) and per tick from `EntityLivingBase` (:363/:1405) and `EntityPlayer` (:753/:784) | `EnchantScanBench`: **129.3 ns/call**, 3.79 ms/s for 60 wet entities | Caching it means invalidating on every inventory/NBT mutation; the NBT tag list is mutable in place (`addEnchantment`, item damage, anvil, enchanting table, `/give`, packet sync). Any memo would either be unbounded or silently stale, which the task forbids. 129 ns × 3/frame ≈ 0.4 ms/s — real but not worth a correctness risk. |
| `World.countEntities(Class)` (`world/World.java:2589`) | full `loadedEntityList` scan × 4 creature types, every spawn tick, per `WorldServer` | `SpawnCountBench`: 4000 entities → **0.016 ms per spawn tick** (0.3 ms/s) | Measurable but small; a per-type counter would need invalidation on every spawn/despawn/dimension change and `isNoDespawnRequired` is live state that changes at runtime. Documented rather than risked. |
| `EntityAITasks` (`entity/ai/EntityAITasks.java`) | **fixed** — see FINDINGS #4; left here for the measured caveat | 1.00–1.05× at realistic 8–16 task mobs | Change kept for the asymptotic guarantee, but it is *not* a frame-time win at vanilla mob sizes. |

## Not measured, ranked by expected impact

1. **`RenderGlobal.renderEntities`** (`client/renderer/RenderGlobal.java:539-560`) — per render-info
   entry it calls `theWorld.getChunkFromBlockCoords(position)` (hash lookup) and
   `getPosition().getY()/16` to index `getEntityLists()`; `RenderChunk` already stores its section
   index, so both are redundant. Runs once per visible chunk per frame (~700+ at radius 12).
2. **`BlockModelRenderer` per-block allocation** (`client/renderer/BlockModelRenderer.java:77-80`,
   `:110-111`) — `float[12]`, `new BitSet(3)` and a fresh `AmbientOcclusionFace` are allocated for
   *every* rendered block, i.e. up to 16k blocks per chunk-section rebuild. Safe reuse requires a
   per-thread scratch object because chunk meshing happens on `ChunkRenderDispatcher` workers; a
   `ThreadLocal` scratch is the natural fix.
3. **`Chunk.getBlockState` vs `getBlockStateFaster`** (`world/chunk/Chunk.java:481` vs `:509`) — the
   generic path goes through `getX()/getY()/getZ()` accessors where `getBlockStateFaster` reads the
   fields; meshing calls the generic form indirectly through several block methods.
4. **`Chunk.recheckGaps`** (`world/chunk/Chunk.java:263-306`) — per changed column it allocates a
   `BlockPos` in `world.getHeight(...)` and again in each `updateSkylightNeighborHeight` probe, and
   `checkSkylightNeighborHeight` is called 1 + 4 times per column; `World.getHeight` also returns a
   freshly allocated `BlockPos`. A primitive `getHeightValue(x,z)` path would remove the garbage on a
   path that runs on every block change.
5. **`World.getCollidingBoundingBoxes`** (`world/World.java:1058`) — allocates a fresh `ArrayList`
   per call (several calls per entity per tick) and walks the full padded AABB in
   x → z → y order, which has poor locality; the world-border check is also re-evaluated per block.
6. **`World.loadedEntityList.removeAll(unloadedEntityList)`** (`world/World.java:1390`) —
   `ArrayList.removeAll` is O(n·m) with an `equals` call per pair. Usually tiny (`m` is a few
   despawns), so it only matters on mass-removal ticks (explosions, chunk unload, dimension change),
   where it is the dominant cost.
7. **`PlayerManager.PlayerInstance.playersWatchingChunk`** (`server/management/PlayerManager.java:338`)
   — an `ArrayList` whose `contains` is O(P), reached once per (entity, player) pair via
   `isPlayerWatchingChunk`. `TrackerBench`: 6.42× at 32 players, but ~1.0× at 1–4 players.
8. **`SpawnerAnimals.eligibleChunksForSpawning`** (`world/SpawnerAnimals.java:64-84`) — allocates a
   `ChunkCoordIntPair` for all `(2·8+1)²` chunks per player and hash-tests each, every spawn tick,
   although the view square only changes when the player crosses a chunk boundary.
9. **`EntityTracker.updateTrackedEntities`** (`entity/EntityTracker.java:217-237`) — O(E×P) in the
   worst case: every entry gets `updatePlayerList(allPlayers)`, and every entry whose entity moved
   >4 blocks then triggers a second pass over all entries for that player. Entities in motion (items,
   arrows, mobs in combat) hit this every tick.
10. **`EntityItem.searchForOtherItemsNearby` + `combineItems`** (`entity/item/EntityItem.java`) — each
    item runs an AABB query and pairwise merge checks every tick it is alive; N nearby drops cost
    O(N²) in merge attempts.
11. **`Explosion.doExplosionA`** (`world/Explosion.java`) — O(blocks × entities) with a fresh list of
    affected blocks, re-scanned per entity.
12. **`ClippingHelper`/`Frustum`** per-frame culling of every render chunk (O(chunks)) — inherent, but
    the per-chunk `AxisAlignedBB` temporaries are candidates for scratch reuse.

## Verified as already linear (checked, no change needed)

- `ViewFrustum.markBlocksForUpdate` — 3×3×3 chunk marking per light/block update, but it only sets a
  boolean flag per chunk; no allocation and no scan.
- `PlayerManager.filterChunkLoadQueue` spiral — see FINDINGS #3.
- `World.setActivePlayerChunksAndCheckLight` — O(P·r²) with a `HashSet`, no nested scan.
- `World.isBlockLoaded` / `isChunkLoaded` — single hash lookups, no implicit chunk generation.
