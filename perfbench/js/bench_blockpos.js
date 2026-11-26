// Node.js replica of World.getHeight / World.isAreaLoaded allocation behavior.
// Change: added primitive overloads getHeight(int x, int z) and
// isAreaLoaded(int x, int y, int z, int radius) so callers (Chunk.recheckGaps,
// checkSkylightNeighborHeight, updateSkylightNeighborHeight) allocate no BlockPos.

"use strict";

const { performance } = require('perf_hooks');

// ---- BlockPos replica (immutable value object, one allocation per instance) ----
class BlockPos {
	constructor(x, y, z) { this.x = x; this.y = y; this.z = z; }
}

// ---- World replica ----
let allocCount = 0;
function alloc() { allocCount++; } // count every BlockPos allocation

function makeBlockPos(x, y, z) { alloc(); return new BlockPos(x, y, z); }

const FUNC_181545_F = 255; // max height + 1 sentinel proxy
function isChunkLoaded(cx, cz) { return true; } // assume loaded
function getChunkHeightValue(cx, cz, x, z) { return 64 + (x & 15); } // primitive, no alloc

// ---- vanilla World.getHeight(BlockPos) ----
function getHeightBlockPos(pos) {
	let i;
	if (pos.x >= -30000000 && pos.z >= -30000000 && pos.x < 30000000 && pos.z < 30000000) {
		if (isChunkLoaded(pos.x >> 4, pos.z >> 4, true)) {
			i = getChunkHeightValue(0, 0, pos.x & 15, pos.z & 15);
		} else {
			i = 0;
		}
	} else {
		i = FUNC_181545_F + 1;
	}
	return makeBlockPos(pos.x, i, pos.z); // 1 allocation (return value)
}

// ---- optimized primitive World.getHeight(int x, int z) ----
function getHeightInt(x, z) {
	let i;
	if (x >= -30000000 && z >= -30000000 && x < 30000000 && z < 30000000) {
		if (isChunkLoaded(x >> 4, z >> 4, true)) {
			i = getChunkHeightValue(0, 0, x & 15, z & 15);
		} else {
			i = 0;
		}
	} else {
		i = FUNC_181545_F + 1;
	}
	return i; // 0 allocations
}

// ---- vanilla isAreaLoaded(BlockPos center, int radius) ----
function isAreaLoadedBlockPos(cx, cy, cz, radius) {
	// old callers: isAreaLoaded(new BlockPos(x, y, z), radius) -> 1 allocation
	const center = makeBlockPos(cx, cy, cz);
	return isAreaLoadedInts(center.x - radius, center.y - radius, center.z - radius,
		center.x + radius, center.y + radius, center.z + radius, true);
}

// ---- optimized isAreaLoaded(int x, int y, int z, int radius) ----
function isAreaLoadedInt(cx, cy, cz, radius) {
	return isAreaLoadedInts(cx - radius, cy - radius, cz - radius, cx + radius, cy + radius, cz + radius, true);
}

function isAreaLoadedInts(xs, ys, zs, xe, ye, ze, allowEmpty) {
	if (ye >= 0 && ys < 256) {
		let xi = xs >> 4, zi = zs >> 4, xe4 = xe >> 4, ze4 = ze >> 4;
		for (let i = xi; i <= xe4; i++) for (let j = zi; j <= ze4; j++) {
			if (!isChunkLoaded(i, j, allowEmpty)) return false;
		}
		return true;
	}
	return false;
}

function benchGetHeight(N) {
	allocCount = 0;
	const t0 = performance.now();
	for (let n = 0; n < N; n++) {
		const p = makeBlockPos(n * 7 + 3, 0, n * 13 + 5); // caller-side BlockPos (old style)
		const h = getHeightBlockPos(p).y;
	}
	const t1 = performance.now();
	const vanillaAllocs = allocCount; // N caller allocs + N return allocs = 2N

	allocCount = 0;
	const t2 = performance.now();
	for (let n = 0; n < N; n++) {
		getHeightInt(n * 7 + 3, n * 13 + 5);
	}
	const t3 = performance.now();
	const optAllocs = allocCount; // 0

	return { vanillaMs: t1 - t0, optMs: t3 - t2, vanillaAllocs, optAllocs };
}

function benchIsAreaLoaded(N) {
	allocCount = 0;
	const t0 = performance.now();
	for (let n = 0; n < N; n++) isAreaLoadedBlockPos(n * 7 + 3, 0, n * 13 + 5, 16); // +1 BlockPos per call
	const t1 = performance.now();
	const vAllocs = allocCount; // N

	allocCount = 0;
	const t2 = performance.now();
	for (let n = 0; n < N; n++) isAreaLoadedInt(n * 7 + 3, 0, n * 13 + 5, 16); // 0 allocs
	const t3 = performance.now();
	const oAllocs = allocCount; // 0

	return { vanillaMs: t1 - t0, optMs: t3 - t2, vanillaAllocs: vAllocs, optAllocs: oAllocs };
}

// recheckGaps per invocation: 1 isAreaLoaded BlockPos + per changed column
// checkSkylightNeighborHeight: 1 getHeight BlockPos + up to 4 checkSkylightNeighborHeight
// probes (isAreaLoaded BlockPos each) + updateSkylightNeighborHeight isAreaLoaded per probe.
function recheckGapsAllocs(numChangedColumns) {
	// vanilla, worst case (every column flagged, lighting update needed):
	//   1 BlockPos for the initial isAreaLoaded(new BlockPos(...), 16)
	//   per column: checkSkylightNeighborHeight -> getHeight(new BlockPos(x,0,z))
	//       allocates the caller BlockPos and the returned BlockPos (2)
	//     + one updateSkylightNeighborHeight probe -> isAreaLoaded(new BlockPos(...), 16) (1)
	let v = 1;
	v += numChangedColumns * (2 + 1);
	// optimized: zero BlockPos anywhere in this path (getHeight(x,z) and isAreaLoaded(int,int,int,int) overloads)
	return v;
}

function main() {
	console.log("=== World.getHeight / isAreaLoaded: BlockPos allocation elimination ===");
	console.log("");
	for (const N of [10000, 100000, 1000000, 10000000]) {
		const g = benchGetHeight(N);
		const a = benchIsAreaLoaded(N);
		console.log("getHeight x" + N.toLocaleString() +
			": vanilla " + g.vanillaMs.toFixed(1) + " ms / " + g.vanillaAllocs.toLocaleString() + " allocs | " +
			"optimized " + g.optMs.toFixed(1) + " ms / " + g.optAllocs + " allocs");
		console.log("isAreaLoaded x" + N.toLocaleString() +
			": vanilla " + a.vanillaMs.toFixed(1) + " ms / " + a.vanillaAllocs.toLocaleString() + " allocs | " +
			"optimized " + a.optMs.toFixed(1) + " ms / " + a.optAllocs + " allocs");
	}
	console.log("");
	console.log("recheckGaps BlockPos allocs per invocation (worst case, numChangedColumns=256):");
	console.log("  vanilla:  " + recheckGapsAllocs(256).toLocaleString());
	console.log("  optimized: 0");
	console.log("");
	console.log("recheckGaps runs on every block change in every loaded chunk; checkSkylightNeighborHeight");
	console.log("runs per changed column on every light-affecting block update. Eliminating these");
	console.log("BlockPos allocations removes GC pressure on the block-update/lighting path without");
	console.log("changing any computed result (the int overloads delegate to identical logic).");
}

main();
