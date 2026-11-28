# Remaining high-complexity algorithms (not modified)

Ranked by estimated impact on frame time / tick time. Each entry records the site, the shape of the
cost, and why it was left alone. Sites marked **measured** had a benchmark written; the number is the
cost at a realistic input size.

## Cross-runtime hotspot inventory (deliverable B)

Source census over `sources/main/java` (481,929 Java lines, 2,488 files): `Math.pow` 30, `Math.sqrt`
62, `MathHelper.sqrt_*` 103, `MathHelper.sin/cos` 398, native `Math.sin/cos/atan2/...` 122,
`MathHelper.floor_*` 496, `Math.floor/ceil/rint` 15, `MathHelper.clamp*` 164, `getDistanceSq*` 96,
`normalize()` 24, `.rotate(` 305, `Math.abs/min/max` 527. **These are source-level counts, not
profiles.** Frequency per frame/tick/entity/chunk below is marked `M` (measured benchmark workload)
or `E` (source-estimated from call-site/loop structure); no full-game trace was available.

| Candidate / call sites | Math shape | Frequency | Measured vs suspected | Decision / evidence |
|---|---|---|---|---|
| `TextureUtil.generateMipmapData` → `MipmapGammaBlend.blend`; `EaglerTextureAtlasSprite:267`, `EaglerTextureAtlasSpritePBR:176` | `pow(byte/255,2.2)` decode; `pow(avg,1/2.2)` encode | `M`: 1,024 sprites × 85 texels × 4/16 decode per texel. `E`: atlas load/reload/F3+T, **not** per frame/tick | **Measured hotspot** | Exact dual LUT shipped; V8 decode 42.894→2.038 ns, JVM atlas 4.4–7.6×. Encode left exact |
| `EntityRenderer.renderWorld` → `updateLightmap`; `updateRenderer`/`updateTorchFlicker` sets `lightmapUpdateNeeded` | 256-iteration float lightmap curve | `E`: once per client tick (~20 Hz), 256 iterations/update; guarded, not every render frame | **Measured isolated kernel, source-estimated frequency** | Bit-exact CSE shipped; 1.03–1.07× |
| `GlStateManager.rotate` → `Matrix4f.rotate` → `MathHelper.sin/cos`; 305 `.rotate(` refs, top `ItemRenderer` 39, `EntityRenderer` 31, `RenderHighPoly` 30 | trig, matrix multiply, camera/model/particle/GUI rotations | `E`: 2,400 rotations/frame used as representative batch; no game profile | **Measured existing optimization** (already in HEAD) | Keep existing 65,536 `SIN_TABLE`; 2.36× JDK17, 2.75× JDK25, 5.56× V8 trig pair |
| `MathHelper.sin/cos` 398 refs (`ModelBiped` 20, `ItemRenderer` 18, `EntityRenderer` 17) | periodic trig, 16-bit-turn quantization | `E`: per model part / particle / camera per frame; worldgen uses same table deterministically | **Measured existing table** | Reuse, do not source-embed; huge embedded sine table rejected (+2.9 MB JS) |
| `Math.sqrt` 62 (`LongMath` 9, `EntityMinecart` 7, `BetterFrustum` 6), `MathHelper.sqrt_*` 103 (`ItemRenderer` 9, `Entity` 8, `EntityArrow` 8) | sqrt / distance / normalization / physics | `E`: per entity × tick, per ray/visibility calculation, per collision sweep | **Measured primitive, rejected LUT** | V8 `Math.sqrt` 1.611 ns; HotSpot intrinsic. Range reduction + interpolation costs more |
| `MathHelper.floor_*` 496 (`World` 82, `RenderGlobal` 33, `EntityTrackerEntry` 24), `Math.clamp*` 164 | interpolation, clamp, floor/ceil, block/chunk coordinate conversion | `E`: per block/vertex/chunk/entity per rebuild or tick | **Suspected by count; measured primitive** | `floor` parity 1.146 ns; no LUT |
| `getDistanceSq*` 96 (`EntityAINearestAttackableTarget` 4, `World` 4, `EntityAIAvoidEntity` 3), `Vec3.normalize` 24 | squared distance, dot/cross, vector lengths | `E`: per entity × target × tick; per camera/ray per frame | **Suspected by count; rejected reciprocal-sqrt rewrite** | Squared distance already avoids sqrt; reciprocal multiply changes last ULP in collision/ray ties |
| `World.getCollidingBoundingBoxes`, `Entity.moveEntity`, `EntityFX.onUpdate` | entity physics, gravity, friction, AABB collision | `E`: 1–3 sweeps/particle/tick, up to 4,000 particles; prior measured 79–95 ns/call | **Measured prior session, not re-profiled here** | Allocation/data-structure problem, not a math LUT; deliberately out of scope |
| `Math.pow` 30 refs outside mipmap (`HFormatter` 5, `VisGraph` 3), fog `pow(2.718,-x)` | lighting/fog/color exponentiation | `E`: one-time constants or once per text batch | **Measured isolated op, negligible frequency** | Rejected `pow→exp` because `2.718 != e` and frequency is negligible |
| `BlockModelRenderer`, `Chunk.getBlockState`, chunk meshing / vertex transforms | nested loops, repeated transforms, AO, block/chunk conversions | `E`: up to ~16k blocks per section rebuild, ~70 AO virtual calls/block | **Suspected by structure; prior profile** | AO memo rejected; prior biome-tint memo measured 0.98–0.99× and reverted |
| `RenderGlobal.renderEntities`, `PlayerManager`, `EntityTracker`, `EntityItem` merge, `Explosion` | O(n²)/O(n³)-shaped scans, repeated transforms | `E`: per visible chunk/frame, per entity×player/tick | **Suspected / prior measurements** | Not LUT-shaped; see ranked list below |

Game-input distributions were **not** captured. Benchmarks use deterministic sequential, fixed-LCG
random and adversarial negative/out-of-range inputs; full-game FPS/frame-time remains unavailable.

## GPU-side hot loops: the built-in deferred/PBR shaders

The census above is Java-only. The shaders in `assets/eagler/glsl/deferred/` are the other half of
the frame cost and are analysed separately, with the same rules (measure, verify, record):

- **Per-frame light loops (optimised, see FINDINGS #14)** — `lighting_point.fsh` runs once per
  fragment of every dynamic light mesh (≤12 lights per chunk bucket, drawn with depth test off), and
  `eaglercraftLighting()` runs inside the per-light loop of `forward_core.fsh`,
  `accel_particle_forward.fsh`, `realistic_water_render.fsh` and `forward_glass_highlights.fsh`.
  `pow(x,5.0)` → multiplies, culls moved before `normalize()`/the `1/dist2` divide, dead work
  removed. Measured: pow/SFU pairs per invocation 3.41 → 0 (`forward_core`), 7.99 → 0
  (`accel_particle`), `sqrt` 12.41 → 7.66, 5.4 % fewer x86 instructions in the BRDF, 0 of 14.66 M
  point-lit fragments change an 8-bit output.
- **Not touched, by design** — `skybox_irradiance.fsh` is the most expensive shader in the tree
  (156 texture taps per output texel) but it only runs when the sky needs re-convolving (3 draw
  calls, not per frame); `reproject_ssr.fsh` takes one sample per pixel per frame and accumulates
  over ~maxAge frames; `shadows_sun.fsh` is geometry-bound (shadow frustum draw), not ALU-bound; the
  two `u_metalsLUT` fetches in the metal branch cannot be merged without changing the LUT asset.
- **Possible follow-ups** — a "precomputed material" entry point for `eaglercraftLighting`
  (`roughness`, `roughness^4`, `gs` are light-invariant; compilers already do LICM, win unproven),
  and `deferred_combine.fsh`'s irradiance/`smoothstep` pair on the `dst < 0.005` seam path.

## The maths/LUT sweep (this session)

Full detail in `FINDINGS.md` (existing-table inventory, trig call-site census, #10, #11, and the
"measured rejections" table). Headlines:

- **Implemented #10** — `Matrix4f.rotate` used native
  `Math.cos`/`Math.sin` for every model-part/particle/GUI rotation; it now uses the engine's
  existing sine table (2.42× per rotation on the JVM, 5.0× on the trig pair in V8, ~35 µs/frame at
  2,400 rotations, 0 bytes of new memory, ≤9.6e-5 rad deviation).
- **Implemented #11 / #12** — the mipmap box filter decoded channel bytes with `Math.pow(x, 2.2)`,
  up to 20 libm calls per mipmap texel, although the input has only 256 possible values. The current
  PR replaces the old runtime-generated table with two committed source-embedded 256-entry tables,
  one per runtime's float semantics; measured 3.7–7.6× faster on the 1,024-sprite / atlas workloads
  and exact on both HotSpot and TeaVM/V8. See FINDINGS #12 and `CROSS_RUNTIME.md`.
- **Implemented #13** — `EntityRenderer.updateLightmap` invariant hoisting / CSE is bit-exact and
  saves 2.8–6.5% of one 256-texel lightmap update. It is a small algorithmic simplification, not a
  frame-time result.
- **Rejected with measurements** — sqrt LUT, magic-number rsqrt in JavaScript, smaller/lerped sin
  tables, dropping `Math.fround` from `MathHelper.sin/cos` (breaks worldgen determinism), degree
  tables, `%`-removal in `wrapAngleTo180_*`, floor LUT, `atan2` tables, block-property tables in the
  AO mesher, `pow(2.718,-x)` → `exp`. Every one has a number next to it in `FINDINGS.md`.
- **The honest conclusion**: client-side transcendentals in this engine are already 99 % tabulated,
  so the remaining math wins are tens of microseconds per frame, not milliseconds. The large
  remaining CPU items in this codebase are allocation- and data-structure-shaped, not arithmetic —
  the biggest one found during this sweep is listed first below.

## Measured, and deliberately not changed

| site | shape | measurement | why not changed |
|---|---|---|---|
| `EnchantmentHelper.getMaxEnchantmentLevel` (`enchantment/EnchantmentHelper.java:139`) | 36–40 NBT slots × string-keyed `getTagList("ench")` + NBT compound fetch, 3×/frame from `EntityRenderer` (:1721/:1840/:2567) and per tick from `EntityLivingBase` (:363/:1405) and `EntityPlayer` (:753/:784) | `EnchantScanBench`: **129.3 ns/call**, 3.79 ms/s for 60 wet entities | Caching it means invalidating on every inventory/NBT mutation; the NBT tag list is mutable in place (`addEnchantment`, item damage, anvil, enchanting table, `/give`, packet sync). Any memo would either be unbounded or silently stale, which the task forbids. 129 ns × 3/frame ≈ 0.4 ms/s — real but not worth a correctness risk. |
| `World.countEntities(Class)` (`world/World.java:2589`) | full `loadedEntityList` scan × 4 creature types, every spawn tick, per `WorldServer` | `SpawnCountBench`: 4000 entities → **0.016 ms per spawn tick** (0.3 ms/s) | Measurable but small; a per-type counter would need invalidation on every spawn/despawn/dimension change and `isNoDespawnRequired` is live state that changes at runtime. Documented rather than risked. |
| `EntityAITasks` (`entity/ai/EntityAITasks.java`) | **fixed** — see FINDINGS #4; left here for the measured caveat | 1.00–1.05× at realistic 8–16 task mobs | Change kept for the asymptotic guarantee, but it is *not* a frame-time win at vanilla mob sizes. |

## Correctness observations (found while reading hot paths, **not** fixed here)

- `PBRTextureMapUtils.generateMipmapDataIgnoreAlpha:158` — `x = (j % len) << 1; y = (j / len) << 1;`
  uses `len` (the level's texel count) where the row length `lvlW` is meant, so `y` is always 0 and
  `x` runs up to `2*(lvlW*lvlW-1)`. Every PBR mip level above 0 is built from the wrong texels. Only
  hit when the atlas does not already supply that level (`aint[i] == null`). Fixing it changes
  rendered PBR output, so it does not belong in a performance PR.
- `EntityFX` never sets `noClip = true`, which is why particles pay for collision at all (see item 0
  below). Changing that *is* a gameplay change (particles would fall through blocks), so the fix
  there must be allocation reuse, not clipping.

## Not measured, ranked by expected impact

0. **Particle physics runs the full entity collision path** (`EntityFX.onUpdate:152` ->
   `Entity.moveEntity` -> `World.getCollidingBoundingBoxes`) — `EntityFX` never sets `noClip`, so
   every one of the up-to-4,000 live particles performs 1–3 collision sweeps per tick. Measured with
   `perfbench.ParticleCollisionBench` (real classes, `WorldStub`):
   **94.8 ns per call** for a particle-sized AABB (79–93 ns item-sized, 83–88 ns player-sized), with a
   fresh `ArrayList` + `AxisAlignedBB` per call and a per-block world-border check. At 4,000
   particles × 2 sweeps that is **~0.76 ms per tick (~15 ms/s) and ~8,000 short-lived allocations
   per tick**, before counting the `getEntitiesWithinAABBExcludingEntity` pass inside. This is *not*
   a lookup-table problem: the fixes are (a) reuse one scratch list/AABB per call site, (b) hoist the
   world-border test out of the x/z loop, (c) merge `moveEntity`'s sequential y/x/z sweeps into one
   padded sweep. Left for a follow-up PR because it touches collision code, which this sweep was
   explicitly told not to alter.
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
4. **`Chunk.recheckGaps`** (`world/chunk/Chunk.java:263-306`) — **FIXED in this session.** The
   `getHeight(new BlockPos(x,0,z))` and `isAreaLoaded(new BlockPos(x,0,z),16)` probes (plus the
   `recheckGaps` gate) now call the new allocation-free `World.getHeight(int,int)` and
   `World.isAreaLoaded(int,int,int,int)` overloads, removing all ~769 `BlockPos` allocations per
   invocation from the probe/early-return path. Verified by `perfbench/js/bench_blockpos.js`:
   0 allocs in the probe path, 1,444 differential cases / 0 mismatches. The remaining per-block
   `checkLightFor(..., new BlockPos(x,i,z))` inside `updateSkylightNeighborHeight`'s relight loop is a
   separate, deeper site and is intentionally left for a follow-up.
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
