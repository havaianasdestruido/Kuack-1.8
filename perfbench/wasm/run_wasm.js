#!/usr/bin/env node
/*
 * Run the isolated WebAssembly gamma-decode PoC under Node/V8.
 *   ./perfbench/wasm/build.sh && node ./perfbench/wasm/run_wasm.js
 *
 * This is NOT a whole-game WASM conversion and NOT a Chromium measurement. It compares:
 *   - pure JS Math.pow (with Math.fround) vs pure JS Float32Array lookup,
 *   - one WASM call per element (pow path crosses WASM -> JS Math.pow per element),
 *   - one WASM call per batch (the coarse-grained boundary the spec asks about).
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { performance } = require('perf_hooks');

const wasmPath = path.join(__dirname, 'build', 'math.wasm');
if (!fs.existsSync(wasmPath)) {
  console.error('Missing build/math.wasm. Run ./perfbench/wasm/build.sh first.');
  process.exit(1);
}
const bytes = fs.readFileSync(wasmPath);
const REPS = 7;
let sink = 0;
function median(a) { const s = a.slice().sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; }
function pct(a, q) { const s = a.slice().sort((x, y) => x - y); return s[Math.min(s.length - 1, Math.floor(q * s.length))]; }
function timed(fn, n) {
  const t = [];
  for (let r = 0; r < REPS; ++r) {
    const t0 = performance.now();
    sink += fn();
    t.push((performance.now() - t0) * 1e6 / n);
  }
  return { med: median(t), p95: pct(t, 0.95), p99: pct(t, 0.99) };
}
function fmt(s) { return s.med.toFixed(3).padStart(10) + ' ns/op'; }
function speed(a, b) { return (a.med / b.med).toFixed(2) + 'x'; }

async function main() {
  console.log('=== WebAssembly prototype: gamma decode, V8 ' + process.versions.v8 + ' ===');
  const watSize = fs.statSync(path.join(__dirname, 'math.wat')).size;
  console.log('module: ' + bytes.length + ' bytes (WAT ' + watSize +
              ' bytes, 256 f32 constants = 1024 bytes)');
  const t0 = performance.now();
  const compiled = await WebAssembly.compile(bytes);
  const t1 = performance.now();
  const instance = await WebAssembly.instantiate(compiled, { env: { pow: Math.pow } });
  const t2 = performance.now();
  console.log('startup: WebAssembly.compile ' + (t1 - t0).toFixed(3) +
              ' ms, instantiate ' + (t2 - t1).toFixed(3) + ' ms; memory 16 pages = 1 MiB');
  const ex = instance.exports;
  const memory = ex.memory;
  const buf = memory.buffer;
  const table = new Float32Array(buf, 0, 256);
  const IN_OFFSET = 65536, OUT_OFFSET = 327680, MAX = 65536;
  const input = new Int32Array(buf, IN_OFFSET, MAX);
  const output = new Float64Array(buf, OUT_OFFSET, MAX);
  const jsLut = new Float32Array(256);

  // deterministic inputs: sequential bytes, then a fixed LCG mix, then adversarial out-of-range.
  for (let i = 0; i < MAX; ++i) input[i] = (i & 255);
  let seed = 0x5EEDBEEF;
  for (let i = 0; i < MAX; ++i) { seed = (seed * 1664525 + 1013904223) | 0; input[i] = seed & 255; }
  for (let i = 0; i < 256; ++i) input[MAX - 256 + i] = i - 128; // includes negatives
  for (let i = 0; i < 256; ++i) {
    jsLut[i] = Math.fround(Math.pow(i / 255, 2.2));
  }

  let failures = 0, checks = 0;
  const bits = (v) => { const f = new Float32Array(1); f[0] = v; return new Uint32Array(f.buffer)[0]; };
  for (let v = -256; v <= 512; ++v) {
    ++checks;
    if (ex.gamma_pow_f32(v) !== ex.gamma_lut_f32(v)) ++failures;
    ++checks;
    if (bits(table[v & 255]) !== bits(jsLut[v & 255])) ++failures;
  }
  for (let v = 0; v < 256; ++v) {
    ++checks;
    if (bits(table[v]) !== bits(Math.fround(Math.pow(v / 255, 2.2)))) ++failures;
  }
  console.log('equivalence: ' + checks + ' checks, ' + failures + ' mismatches');
  if (failures !== 0) { console.error('FAILED: WASM table differs from Math.pow'); process.exit(1); }

  const N = 200000;
  console.log('');
  console.log('-- fine-grained per-op (median of 7; 1 JS<->WASM boundary each, plus Math.pow import) --');
  const jsPow = timed(() => { let s = 0; for (let i = 0; i < N; ++i) s += Math.fround(Math.pow((i & 255) / 255, 2.2)); return s; }, N);
  const jsLutTime = timed(() => { let s = 0; for (let i = 0; i < N; ++i) s += jsLut[i & 255]; return s; }, N);
  const wasmPow = timed(() => { let s = 0; for (let i = 0; i < N; ++i) s += ex.gamma_pow_f32(i & 255); return s; }, N);
  const wasmLut = timed(() => { let s = 0; for (let i = 0; i < N; ++i) s += ex.gamma_lut_f32(i & 255); return s; }, N);
  console.log('  JS Math.pow + fround         ' + fmt(jsPow) + '  (p95 ' + jsPow.p95.toFixed(3) + ' / p99 ' + jsPow.p99.toFixed(3) + ')');
  console.log('  JS Float32Array LUT          ' + fmt(jsLutTime) + '  (p95 ' + jsLutTime.p95.toFixed(3) + ' / p99 ' + jsLutTime.p99.toFixed(3) + ')');
  console.log('  WASM Math.pow import / call  ' + fmt(wasmPow) + '  (p95 ' + wasmPow.p95.toFixed(3) + ' / p99 ' + wasmPow.p99.toFixed(3) + ')');
  console.log('  WASM f32 table / call        ' + fmt(wasmLut) + '  (p95 ' + wasmLut.p95.toFixed(3) + ' / p99 ' + wasmLut.p99.toFixed(3) + ')');
  console.log('  JS pow / JS LUT              ' + speed(jsPow, jsLutTime));
  console.log('  WASM pow / WASM LUT          ' + speed(wasmPow, wasmLut));
  console.log('  WASM call overhead vs JS LUT ' + speed(wasmLut, jsLutTime));

  console.log('');
  console.log('-- coarse-grained batch (one JS->WASM call; data already in WASM memory) --');
  for (const n of [256, 4096, 65536]) {
    const powSum = ex.batch_pow_f32(IN_OFFSET, n, OUT_OFFSET);
    const powOut = Float64Array.from(output.subarray(0, n));
    const lutSum = ex.batch_lut_f32(IN_OFFSET, n, OUT_OFFSET);
    if (powSum !== lutSum) { console.error('FAILED: checksum mismatch at n=' + n); process.exit(1); }
    for (let i = 0; i < n; ++i) {
      if (powOut[i] !== output[i]) { console.error('FAILED: output mismatch at ' + i); process.exit(1); }
    }
    const powT = timed(() => ex.batch_pow_f32(IN_OFFSET, n, OUT_OFFSET), n);
    const lutT = timed(() => ex.batch_lut_f32(IN_OFFSET, n, OUT_OFFSET), n);
    console.log('  n=' + String(n).padStart(5) + '  pow ' + fmt(powT) + '   lut ' + fmt(lutT) +
                '   ' + speed(powT, lutT) +
                '   (pow p95 ' + powT.p95.toFixed(3) + '/p99 ' + powT.p99.toFixed(3) +
                ', lut p95 ' + lutT.p95.toFixed(3) + '/p99 ' + lutT.p99.toFixed(3) + ')');
  }
  console.log('');
  console.log('OK: WASM batch outputs are bit-identical between pow and LUT.');
  console.log('Full Chromium/browser FPS and whole-game TeaVM->WASM conversion: UNAVAILABLE (see CROSS_RUNTIME.md).');
  console.log('sink=' + sink + ' (keeps kernels live)');
}
main().catch((e) => { console.error(e); process.exit(1); });
