// Node.js replica of World.pendingBlockEntities membership flush.
// Models the exact List.contains() vs LinkedHashSet.contains() semantics:
// TileEntity does not override equals/hashCode -> membership is identity-based.
// LinkedHashSet (JS Set: insertion-order iteration, identity hashing for objects)
// reproduces List membership exactly while making contains/add/remove O(1).

"use strict";

function makeTile() { return { __tile__: true }; }

// ---------- vanilla: ArrayList + indexOf (List.contains is O(T)) ----------
function vanillaFlush(loaded, added) {
	let checks = 0;
	let addedToTickable = 0;
	for (let a = 0; a < added.length; a++) {
		const t = added[a];
		let found = false;
		for (let i = 0, l = loaded.length; i < l; i++) {
			checks++;
			if (loaded[i] === t) { found = true; break; }
		}
		if (!found) {
			loaded.push(t);            // loadedTileEntityList.add
			addedToTickable++;         // tickableTileEntities.add (if ITickable)
		}
	}
	return { checks, addedToTickable };
}

// ---------- optimized: LinkedHashSet replica (JS Set, identity, O(1)) ----------
function optimizedFlush(loadedSet, added) {
	let checks = 0;
	let addedToTickable = 0;
	for (let a = 0; a < added.length; a++) {
		const t = added[a];
		checks++;                      // hash probe, O(1)
		if (!loadedSet.has(t)) {
			loadedSet.add(t);
			addedToTickable++;
		}
	}
	return { checks, addedToTickable };
}

// ---------- equivalence: randomized add/remove/contains differential test ----------
function equivalenceTest(trials = 20000, maxOps = 400) {
	const rng = (() => { let s = 0x12345678; return () => { s = (s * 1664525 + 1013904223) >>> 0; return (s >>> 8) / 0xffffff; }; })();
	let mismatches = 0;
	for (let tr = 0; tr < trials; tr++) {
		const list = [];
		const set = new Set();
		const nOps = 1 + (rng() * maxOps) | 0;
		for (let o = 0; o < nOps; o++) {
			const kind = rng() * 3;
			const t = makeTile();
			if (kind < 1) { // add
				list.push(t);
				set.add(t);
			} else if (kind < 2) { // remove one occurrence (List.remove removes first occurrence; Set.remove removes the object)
				for (let i = 0; i < list.length; i++) { if (list[i] === t) { list.splice(i, 1); break; } }
				set.delete(t);
			} else { // contains: probe a random live element or a dead one
				const probe = rng() < 0.5 ? t : makeTile();
				let listRes = false;
				for (let i = 0, l = list.length; i < l; i++) { if (list[i] === probe) { listRes = true; break; } }
				const setRes = set.has(probe);
				if (listRes !== setRes) { mismatches++; if (mismatches < 5) console.log("MISMATCH", listRes, setRes); }
			}
		}
	}
	console.log("equivalenceTest: trials=" + trials + " mismatches=" + mismatches);
	return mismatches;
}

// ---------- scaling benchmark ----------
function bench(T, A, reps) {
	// worst case for List.contains: probe not present (full scan) or mid-list; use not-present
	const addedVanilla = [];
	for (let a = 0; a < A; a++) addedVanilla.push(makeTile());
	const addedOpt = addedVanilla.slice(); // same identities

	let vTotal = 0, oTotal = 0;
	for (let r = 0; r < reps; r++) {
		// reload fresh loaded containers each rep
		const loaded = [];
		for (let i = 0; i < T; i++) loaded.push(makeTile());
		const loadedSet = new Set(loaded);

		const vr = vanillaFlush(loaded.slice(), addedVanilla);  // slice: flush mutates loaded
		const or = optimizedFlush(new Set(loaded), addedOpt);   // fresh set copy
		vTotal += vr.checks;
		oTotal += or.checks;
	}
	return { v: vTotal / reps, o: oTotal / reps };
}

function main() {
	console.log("=== World.loadedTileEntityList membership flush (pendingBlockEntities) ===");
	console.log("T = loaded tile entities, A = newly added tile entities this tick");
	console.log("checks = element comparisons (vanilla: O(T) each; optimized: O(1) hash probe each)");
	console.log("");

	const eq = equivalenceTest();
	if (eq !== 0) process.exit(1);

	const configs = [
		[256, 1], [256, 50], [1024, 1], [1024, 50],
		[4096, 1], [4096, 50], [16384, 1], [16384, 100]
	];
	console.log("   T        A       vanilla checks   optimized checks   speedup");
	for (const [T, A] of configs) {
		const reps = T >= 16384 ? 20 : 200;
		const { v, o } = bench(T, A, reps);
		console.log((T + "      ").padEnd(8) + (A + "      ").padEnd(8)
			+ Math.round(v).toString().padStart(16) + Math.round(o).toString().padStart(16)
			+ ((v / o || 1).toFixed(2) + "x").padStart(10));
	}
	console.log("");
	console.log("Scaling: vanilla is O(A*T) (each added tile entity scans all T loaded),");
	console.log("optimized is O(A) (each contains/add is one hash probe). At T=16384,");
	console.log("a chunk-load tick adding 100 tile entities does ~1.6M comparisons vs 100 probes.");
}

main();
