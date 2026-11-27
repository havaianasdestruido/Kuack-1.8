#!/usr/bin/env node
/* V8-specific math microbenchmarks for the Eaglercraft target.
 * Complements (not replaces) perfbench/teavm/run_probe.js, which runs the REAL TeaVM output.
 * Usage: node ./perfbench/js/bench_v8_math.js
 */
'use strict';

const N = 200000;
const REPS = 7;
const angles = new Float64Array(4096);
const bytes = new Int32Array(4096);
let seed = 0x5EEDBEEF;
for (let i = 0; i < 4096; i++) {
  seed = (seed * 1664525 + 1013904223) | 0;
  angles[i] = (seed >>> 0) * (Math.PI * 2 / 4294967296);
  bytes[i] = seed & 255;
}

const SIN_F32 = new Float32Array(65536);
const SIN_F64 = new Float64Array(65536);
const SIN_ARRAY = new Array(65536);
for (let i = 0; i < 65536; i++) {
  const v = Math.sin(i * Math.PI * 2 / 65536);
  SIN_F32[i] = v; SIN_F64[i] = v; SIN_ARRAY[i] = v;
}
const SIN_SMALL = new Float32Array(4096);
for (let i = 0; i < 4096; i++) SIN_SMALL[i] = Math.sin(i * Math.PI * 2 / 4096);
const GAMMA = new Float32Array(256);
for (let i = 0; i < 256; i++) GAMMA[i] = Math.pow(i / 255, 2.2);

let sink = 0;
function timed(fn, n) {
  const samples = [];
  for (let r = 0; r < REPS; r++) {
    const t = performance.now();
    sink += fn();
    samples.push((performance.now() - t) * 1e6 / n);
  }
  samples.sort((a, b) => a - b);
  return samples[3];
}
function report(name, ns) {
  console.log('  ' + name.padEnd(38) + ns.toFixed(3).padStart(10) + ' ns/op');
}

console.log('=== V8 ' + process.versions.v8 + ' / Node ' + process.version + ' math microbench ===');
globalThis.__sinTable = SIN_F32;
const res = {};
res.math_sin = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += Math.sin(angles[i & 4095]); return s; }, N);
res.math_cos = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += Math.cos(angles[i & 4095]); return s; }, N);
res.math_sqrt = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += Math.sqrt(angles[i & 4095] + 1); return s; }, N);
res.math_pow = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += Math.pow((bytes[i & 4095]) / 255, 2.2); return s; }, N);
res.lut_f32 = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += SIN_F32[(angles[i & 4095] * 10430.378 | 0) & 65535]; return s; }, N);
res.lut_f64 = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += SIN_F64[(angles[i & 4095] * 10430.378 | 0) & 65535]; return s; }, N);
res.lut_array = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += SIN_ARRAY[(angles[i & 4095] * 10430.378 | 0) & 65535]; return s; }, N);
res.lut_small = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += SIN_SMALL[(angles[i & 4095] * 651.8986 | 0) & 4095]; return s; }, N);
res.lut_lerp = timed(() => {
  let s = 0;
  for (let i = 0; i < N; i++) {
    const x = angles[i & 4095] * 651.8986;
    const j = x | 0, f = x - j;
    s += SIN_SMALL[j & 4095] * (1 - f) + SIN_SMALL[(j + 1) & 4095] * f;
  }
  return s;
}, N);
res.lut_int_index = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += SIN_F32[(angles[i & 4095] * 10430.378 | 0) & 65535]; return s; }, N);
res.lut_floor_index = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += SIN_F32[Math.floor(angles[i & 4095] * 10430.378) & 65535]; return s; }, N);
res.fround = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += Math.fround(angles[i & 4095] * 2.0); return s; }, N);
res.direct_mul = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += angles[i & 4095] * 2.0; return s; }, N);
res.gamma_lut = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += GAMMA[bytes[i & 4095]]; return s; }, N);
res.cached_ref = timed(() => { let s = 0; const t = SIN_F32; for (let i = 0; i < N; i++) s += t[(angles[i & 4095] * 10430.378 | 0) & 65535]; return s; }, N);
res.global_ref = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += globalThis.__sinTable[(angles[i & 4095] * 10430.378 | 0) & 65535]; return s; }, N);
// method call vs inline
function lookup(a) { return SIN_F32[(a * 10430.378 | 0) & 65535]; }
res.method_call = timed(() => { let s = 0; for (let i = 0; i < N; i++) s += lookup(angles[i & 4095]); return s; }, N);

for (const k of Object.keys(res)) report(k, res[k]);
console.log('');
console.log('-- ratios --');
function ratio(a, b) { console.log('  ' + (a + ' / ' + b).padEnd(44) + (res[a] / res[b]).toFixed(2) + 'x'); }
ratio('math_sin', 'lut_f32');
ratio('math_pow', 'gamma_lut');
ratio('lut_lerp', 'lut_small');
ratio('lut_array', 'lut_f32');
ratio('lut_f64', 'lut_f32');
ratio('lut_floor_index', 'lut_int_index');
ratio('method_call', 'lut_int_index');
ratio('math_sqrt', 'lut_f32');
console.log('');
console.log('-- first-use / allocation cost (one-off, median of 7) --');
const alloc = (fn) => { const a = []; for (let i = 0; i < REPS; i++) { const t = performance.now(); fn(); a.push((performance.now() - t) * 1000); } a.sort((x, y) => x - y); return a[3]; };
console.log('  new Float32Array(65536) + fill with Math.sin : ' + alloc(() => { const t = new Float32Array(65536); for (let i = 0; i < 65536; i++) t[i] = Math.sin(i * Math.PI * 2 / 65536); }) .toFixed(1) + ' us');
console.log('  new Float32Array(256) + 256 Math.pow        : ' + alloc(() => { const t = new Float32Array(256); for (let i = 0; i < 256; i++) t[i] = Math.pow(i / 255, 2.2); }).toFixed(1) + ' us');
console.log('  Float32Array.from(256 JS number literals)   : ' + alloc(() => { const t = new Float32Array(256); for (let i = 0; i < 256; i++) t[i] = GAMMA[i]; }).toFixed(1) + ' us');
console.log('');
console.log('sink=' + sink + ' (keeps all kernels live)');
