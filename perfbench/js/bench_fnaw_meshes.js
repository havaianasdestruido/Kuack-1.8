#!/usr/bin/env node
/*
 * Benchmark for EaglerMeshLoader binary decode + fixed-function vertex transform
 * workload on the 15 FNAW (.mdl) character models.
 *
 * Measures two real runtime paths as TeaVM/WebGL executes them:
 *   1. `EaglerMeshLoader.reloadMesh` binary byte-stream decode into a 32-bit
 *      Int32Array staging buffer for `_wglBufferData(GL_ARRAY_BUFFER / GL_ELEMENT_ARRAY_BUFFER)`.
 *   2. Indexed triangle assembly + post-transform 32-entry FIFO vertex cache +
 *      4x4 modelview/normal vertex shader transform across all 5 FNAW skins
 *      (simulating rendering N FNAW players per frame).
 *
 * Usage:
 *   node perfbench/js/bench_fnaw_meshes.js [meshDirA] [meshDirB]
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROUNDS = 11;
const TARGET_MS = 80;

function now() {
  const [s, ns] = process.hrtime();
  return s * 1e3 + ns / 1e6;
}

function median(a) {
  const s = a.slice().sort((x, y) => x - y);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

function bench(fn) {
  let iters = 1;
  for (let w = 0; w < 12; ++w) {
    const t0 = now();
    fn(iters);
    const dt = now() - t0;
    if (dt >= TARGET_MS) break;
    const scale = Math.max(2, Math.min(16, Math.ceil(TARGET_MS / Math.max(dt, 0.05))));
    iters = Math.min(iters * scale, 1 << 16);
  }
  const samples = [];
  for (let r = 0; r < ROUNDS; ++r) {
    let total = 0;
    let ops = 0;
    while (total < TARGET_MS) {
      const t0 = now();
      fn(iters);
      total += now() - t0;
      ops += iters;
    }
    samples.push((total * 1e3) / ops); // us per op
  }
  return median(samples);
}

// Exact replica of EaglerMeshLoader.reloadMesh byte-by-byte IntBuffer decode
function decodeMdlBuffer(buf) {
  // Header "!EAG$mdl"
  if (
    buf[0] !== 33 || buf[1] !== 69 || buf[2] !== 65 || buf[3] !== 71 ||
    buf[4] !== 36 || buf[5] !== 109 || buf[6] !== 100 || buf[7] !== 108
  ) {
    throw new Error('File is not an eaglercraft high-poly mesh!');
  }
  const ct = buf[8];
  const hasTexture = ct === 84; // 'T'
  const skip = (buf[9] << 8) | buf[10];
  let pos = 11 + skip;
  const vertexCount =
    ((buf[pos] << 24) | (buf[pos + 1] << 16) | (buf[pos + 2] << 8) | buf[pos + 3]) >>> 0;
  pos += 4;
  const indexCount =
    ((buf[pos] << 24) | (buf[pos + 1] << 16) | (buf[pos + 2] << 8) | buf[pos + 3]) >>> 0;
  pos += 4;
  let byteIndexCount = indexCount;
  if (byteIndexCount % 2 !== 0) byteIndexCount += 1;
  const stride = hasTexture ? 24 : 16;
  const intsOfVertex = ((vertexCount * stride) / 4) | 0;
  const intsOfIndex = (byteIndexCount / 2) | 0;
  const intsTotal = intsOfVertex + intsOfIndex;
  const up1 = new Int32Array(intsTotal);
  for (let i = 0; i < intsTotal; ++i) {
    const ch1 = buf[pos++];
    const ch2 = buf[pos++];
    const ch3 = buf[pos++];
    const ch4 = buf[pos++];
    up1[i] = (ch4 << 24) + (ch3 << 16) + (ch2 << 8) + ch1;
  }
  return {
    hasTexture,
    stride,
    vertexCount,
    indexCount,
    intsOfVertex,
    intsTotal,
    vboBytes: intsOfVertex * 4,
    iboBytes: indexCount * 2,
    f32: new Float32Array(up1.buffer, 0, intsOfVertex),
    i8: new Int8Array(up1.buffer, 0, intsOfVertex * 4),
    u16: new Uint16Array(up1.buffer, intsOfVertex * 4, indexCount),
  };
}

// Simulate indexed draw call with 32-entry post-transform vertex cache + MVP + normal transform
const MVP = new Float32Array([
  0.866, 0.0, -0.5, 0.0,
  0.129, 0.966, 0.224, 0.0,
  0.483, -0.259, 0.837, 0.0,
  1.25, -0.6, -8.5, 1.0,
]);
const cacheRing = new Int32Array(32);
let sink = 0.0;

function drawHighPolySim(mesh) {
  const u16 = mesh.u16;
  const f32 = mesh.f32;
  const i8 = mesh.i8;
  const fStride = mesh.hasTexture ? 6 : 4;
  const bStride = mesh.stride;
  const icount = mesh.indexCount;
  cacheRing.fill(-1);
  let ringHead = 0;
  let acc = 0.0;

  for (let i = 0; i < icount; ++i) {
    const idx = u16[i];
    let hit = false;
    for (let c = 0; c < 32; ++c) {
      if (cacheRing[c] === idx) {
        hit = true;
        break;
      }
    }
    if (!hit) {
      cacheRing[ringHead] = idx;
      ringHead = (ringHead + 1) & 31;
      const vo = idx * fStride;
      const x = f32[vo];
      const y = f32[vo + 1];
      const z = f32[vo + 2];
      const cx = MVP[0] * x + MVP[4] * y + MVP[8] * z + MVP[12];
      const cy = MVP[1] * x + MVP[5] * y + MVP[9] * z + MVP[13];
      const cz = MVP[2] * x + MVP[6] * y + MVP[10] * z + MVP[14];
      const bo = idx * bStride + 12;
      const nx = i8[bo] * 0.007874016;
      const ny = i8[bo + 1] * 0.007874016;
      const nz = i8[bo + 2] * 0.007874016;
      const l = MVP[0] * nx + MVP[5] * ny + MVP[10] * nz;
      const uv = mesh.hasTexture ? f32[vo + 4] + f32[vo + 5] : 0.0;
      acc += cx + cy + cz + l + uv;
    }
  }
  sink += acc;
}

function loadSuite(dir) {
  const files = fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.mdl'))
    .sort();
  const rawBuffers = files.map((f) => fs.readFileSync(path.join(dir, f)));
  const decoded = rawBuffers.map(decodeMdlBuffer);
  const totalFileBytes = rawBuffers.reduce((s, b) => s + b.length, 0);
  const totalGpuBytes = decoded.reduce((s, m) => s + m.vboBytes + m.iboBytes, 0);
  const totalVerts = decoded.reduce((s, m) => s + m.vertexCount, 0);
  const totalTris = decoded.reduce((s, m) => s + m.indexCount / 3, 0);

  const reloadUs = bench((iters) => {
    for (let k = 0; k < iters; ++k) {
      for (let j = 0; j < rawBuffers.length; ++j) {
        sink += decodeMdlBuffer(rawBuffers[j]).intsTotal;
      }
    }
  });

  // Render 20 players (4 of each of the 5 FNAW skins -> 4x all 15 sub-meshes)
  const render20Us = bench((iters) => {
    for (let k = 0; k < iters; ++k) {
      for (let p = 0; p < 4; ++p) {
        for (let j = 0; j < decoded.length; ++j) {
          drawHighPolySim(decoded[j]);
        }
      }
    }
  });

  return {
    dir,
    files: files.length,
    totalVerts,
    totalTris,
    totalFileBytes,
    totalGpuBytes,
    reloadUs,
    render20Us,
  };
}

const defaultDir = path.resolve(__dirname, '../../sources/resources/assets/eagler/mesh');
const dirA = process.argv[2] ? path.resolve(process.argv[2]) : defaultDir;
const dirB = process.argv[3] ? path.resolve(process.argv[3]) : null;

const resA = loadSuite(dirA);
if (!dirB) {
  console.log(JSON.stringify(resA, null, 2));
} else {
  const resB = loadSuite(dirB);
  console.log('| Metric | Before | After | Change / Speedup |');
  console.log('|---|---:|---:|---:|');
  console.log(
    `| Total vertices (15 meshes) | ${resA.totalVerts} | ${resB.totalVerts} | ${(
      ((resB.totalVerts - resA.totalVerts) / resA.totalVerts) *
      100
    ).toFixed(1)}% |`
  );
  console.log(
    `| Total triangles (15 meshes) | ${resA.totalTris} | ${resB.totalTris} | ${(
      ((resB.totalTris - resA.totalTris) / resA.totalTris) *
      100
    ).toFixed(1)}% |`
  );
  console.log(
    `| Asset file bytes (.mdl) | ${resA.totalFileBytes} B | ${resB.totalFileBytes} B | ${(
      ((resB.totalFileBytes - resA.totalFileBytes) / resA.totalFileBytes) *
      100
    ).toFixed(1)}% |`
  );
  console.log(
    `| GPU VBO+IBO memory | ${resA.totalGpuBytes} B | ${resB.totalGpuBytes} B | ${(
      ((resB.totalGpuBytes - resA.totalGpuBytes) / resA.totalGpuBytes) *
      100
    ).toFixed(1)}% |`
  );
  console.log(
    `| EaglerMeshLoader decode (all 15) | ${resA.reloadUs.toFixed(2)} µs | ${resB.reloadUs.toFixed(
      2
    )} µs | ${(resA.reloadUs / resB.reloadUs).toFixed(2)}× |`
  );
  console.log(
    `| Vertex transform + 32-FIFO (20 players) | ${resA.render20Us.toFixed(
      2
    )} µs | ${resB.render20Us.toFixed(2)} µs | ${(resA.render20Us / resB.render20Us).toFixed(2)}× |`
  );
}
if (sink === 123456789.123) console.error(sink);
