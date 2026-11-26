# Source kept for the record, not compiled

These classes belong to findings that were **implemented, measured and then reverted** (see
`perfbench/FINDINGS.md` #2: the `BiomeColorHelper` / `RegionRenderCache` biome-tint memo, a measured
0.98–0.99× non-win). The production APIs they exercise — `BiomeColorHelper.getResolver`,
`RegionRenderCache.getCachedBiomeColor`, `RegionRenderCache.NO_CACHED_BIOME_COLOR` — no longer exist,
so they cannot compile against the current tree and are excluded from `perfbench/run_bench.sh`'s
build (`perfbench/src` only). The standalone replicas that *do* still build (`BiomeColorBench`,
`BiomeColorPerBlock`, `BiomeColorVariants`) stay in `perfbench/src`.

Keeping them here preserves the verbatim equivalence checks and benchmark shapes for anyone who wants
to retry the idea; they are not part of the build or the gates.
