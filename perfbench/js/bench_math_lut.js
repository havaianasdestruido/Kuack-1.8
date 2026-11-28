#!/usr/bin/env node
/*
 * Micro-benchmarks for transcendental / rounding primitives as they run in a
 * JavaScript engine.
 *
 * Why JavaScript: EaglercraftX compiles this Java source tree with TeaVM to
 * JavaScript, so every one of these operations ultimately executes as a JS
 * operation on a `number`. In particular
 *
 *   Java Math.sin(double)   -> JS Math.sin
 *   Java Math.sqrt(double)  -> JS Math.sqrt
 *   Java (float) expression -> the same double, rounded via Math.fround where
 *                              TeaVM must preserve float semantics
 *   Java float[]            -> JS Float32Array (float-precision values on read)
 *   Java (int) cast         -> JS `|0`
 *
 * so measuring the candidates here measures the real target runtime. V8 is the
 * engine in Chrome/Edge/Node and Android WebView, i.e. what most Eaglercraft
 * players run; SpiderMonkey/JSC caveat is recorded in FINDINGS.
 *
 * Usage: node perfbench/js/bench_math_lut.js [rounds] [targetMs]
 */
'use strict';

const ROUNDS = parseInt(process.argv[2] || '9', 10);
const TARGET_MS = parseInt(process.argv[3] || '80', 10);
const TAU = Math.PI * 2;

// ---------------------------------------------------------------- utilities

function now() {
  const [s, ns] = process.hrtime();
  return s * 1e3 + ns / 1e6;
}

function median(a) {
  const s = a.slice().sort((x, y) => x - y);
  const m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
}

let sink = 0;

// `fn(iters)` must perform exactly `iters * n` operations.
function bench(name, n, fn) {
  // warm-up: let the optimizing tier settle on this loop shape
  let iters = 1;
  for (let w = 0; w < 40; ++w) {
    const t0 = now();
    fn(iters);
    const dt = now() - t0;
    if (dt >= TARGET_MS && w > 5) break;
    const scale = Math.max(2, Math.min(64, Math.ceil(TARGET_MS / Math.max(dt, 0.005))));
    iters = Math.min(iters * scale, 1 << 22);
  }
  const samples = [];
  for (let r = 0; r < ROUNDS; ++r) {
    let total = 0;
    let ops = 0;
    while (total < TARGET_MS) {
      const t0 = now();
      fn(iters);
      total += now() - t0;
      ops += iters * n;
    }
    samples.push(total * 1e6 / ops);
  }
  return { name, ns: median(samples) };
}

const rows = [];
function add(r) { rows.push(r); return r; }
function report(title) {
  console.log('\n### ' + title);
  console.log('| candidate | ns/op | ops/s (M) |');
  console.log('|---|---|---|');
  for (const r of rows) {
    console.log('| ' + r.name + ' | ' + r.ns.toFixed(3) + ' | ' + (1000 / r.ns).toFixed(1) + ' |');
  }
  rows.length = 0;
}

// ------------------------------------------------------------ input corpora

function rand(n, lo, hi, seed0) {
  const a = new Float64Array(n);
  let seed = seed0;
  for (let i = 0; i < n; ++i) {
    seed = (Math.imul(seed, 1103515245) + 12345) & 0x7fffffff;
    a[i] = lo + (seed / 0x7fffffff) * (hi - lo);
  }
  return a;
}

const ANG = rand(4096, -Math.PI * 2, Math.PI * 2, 12345);   // model animation angles
const DEG = rand(4096, -360, 360, 999);                     // degree-domain angles
const POS = rand(4096, 0, 9, 4242);                         // sqrt arguments
// a *small* repeated set (e.g. one entity animation phase reused for many parts)
const HOT = rand(64, -Math.PI, Math.PI, 777);
const N = 4096;

// ------------------------------------------------------- 1. native primitives

add(bench('Math.sin(x) [rad]', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.sin(ANG[i]);
  sink += s;
}));
add(bench('Math.cos(x) [rad]', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.cos(ANG[i]);
  sink += s;
}));
add(bench('Math.tan(x) [rad]', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.tan(ANG[i] * 0.25);
  sink += s;
}));
add(bench('Math.atan2(y,x)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.atan2(ANG[i], ANG[(i * 7 + 1) & 4095]);
  sink += s;
}));
add(bench('Math.asin(x)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.asin(ANG[i] / (Math.PI * 2));
  sink += s;
}));
add(bench('Math.sqrt(x)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.sqrt(POS[i]);
  sink += s;
}));
add(bench('1/Math.sqrt(x)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += 1 / Math.sqrt(POS[i]);
  sink += s;
}));
add(bench('Math.pow(2.718, -x)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.pow(2.718, -POS[i] * 0.1);
  sink += s;
}));
add(bench('Math.exp(-x)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.exp(-POS[i] * 0.1);
  sink += s;
}));
add(bench('Math.floor(x)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.floor(ANG[i] * 3.7);
  sink += s;
}));
add(bench('MathHelper.floor_double', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const v = ANG[i] * 3.7;
      const t = v | 0;
      s += v < t ? t - 1 : t;
    }
  }
  sink += s;
}));
add(bench('x % 360.0  (wrapAngle)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += DEG[i] % 360;
  sink += s;
}));
add(bench('x - 360*trunc(x/360)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += DEG[i] - 360 * ((DEG[i] / 360) | 0);
  sink += s;
}));
add(bench('int add + compare (baseline)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += (i & 1023) + (i > 2048 ? 1 : 0);
  sink += s;
}));

report('native primitives (V8 ' + process.versions.v8 + ')');

// --------------------------------------------------- 2. sin/cos table variants

const SIN65536 = new Float32Array(65536);
for (let i = 0; i < 65536; ++i) SIN65536[i] = Math.sin(i * TAU / 65536);

const K65536 = 65536 / TAU;   // 10430.378

// current MathHelper.sin / .cos, written exactly as the Java does
add(bench('MathHelper.sin (65536 step, fround)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const x = Math.fround(ANG[i]);
      s += SIN65536[(Math.fround(x * 10430.378) | 0) & 0xffff];
    }
  }
  sink += s;
}));
add(bench('MathHelper.cos (65536 step, fround)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const x = Math.fround(ANG[i]);
      s += SIN65536[(Math.fround(x * 10430.378 + 16384.0) | 0) & 0xffff];
    }
  }
  sink += s;
}));
add(bench('65536 step, no fround', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) s += SIN65536[((ANG[i] * K65536) | 0) & 0xffff];
  }
  sink += s;
}));
add(bench('65536 step, no fround, HOT 64 set', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const x = HOT[i & 63];
      s += SIN65536[((x * K65536) | 0) & 0xffff];
    }
  }
  sink += s;
}));

function makeStep(n) {
  const t = new Float32Array(n);
  for (let i = 0; i < n; ++i) t[i] = Math.sin(i * TAU / n);
  return t;
}
function makeLerp(n) { // n+1 entries so no wrap test is needed
  const t = new Float32Array(n + 1);
  for (let i = 0; i <= n; ++i) t[i] = Math.sin(i * TAU / n);
  return t;
}
const SIN4096 = makeStep(4096);
const SIN1024 = makeStep(1024);
const L2048 = makeLerp(2048);
const L4096 = makeLerp(4096);
const L8192 = makeLerp(8192);

add(bench('4096 step table', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) s += SIN4096[((ANG[i] * (4096 / TAU)) | 0) & 4095];
  }
  sink += s;
}));
add(bench('1024 step table', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) s += SIN1024[((ANG[i] * (1024 / TAU)) | 0) & 1023];
  }
  sink += s;
}));
add(bench('2048 lerp table', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const f = ANG[i] * (2048 / TAU);
      const m = f | 0;
      const fr = f - m;
      const j = m & 2047;
      s += L2048[j] + (L2048[j + 1] - L2048[j]) * fr;
    }
  }
  sink += s;
}));
add(bench('4096 lerp table', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const f = ANG[i] * (4096 / TAU);
      const m = f | 0;
      const fr = f - m;
      const j = m & 4095;
      s += L4096[j] + (L4096[j + 1] - L4096[j]) * fr;
    }
  }
  sink += s;
}));
add(bench('8192 lerp table', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const f = ANG[i] * (8192 / TAU);
      const m = f | 0;
      const fr = f - m;
      const j = m & 8191;
      s += L8192[j] + (L8192[j + 1] - L8192[j]) * fr;
    }
  }
  sink += s;
}));

report('sin table variants');

// accuracy of each variant against Math.sin
const STEPS = 1 << 20;
let e = { mc: 0, s4096: 0, s1024: 0, l2048: 0, l4096: 0, l8192: 0, nat: 0 };
for (let i = 0; i < STEPS; ++i) {
  const x = -Math.PI + (i / STEPS) * TAU;
  const ref = Math.sin(x);
  e.mc = Math.max(e.mc, Math.abs(SIN65536[((x * K65536) | 0) & 0xffff] - ref));
  e.s4096 = Math.max(e.s4096, Math.abs(SIN4096[((x * (4096 / TAU)) | 0) & 4095] - ref));
  e.s1024 = Math.max(e.s1024, Math.abs(SIN1024[((x * (1024 / TAU)) | 0) & 1023] - ref));
  const f2 = x * (2048 / TAU), m2 = f2 | 0, j2 = m2 & 2047;
  e.l2048 = Math.max(e.l2048, Math.abs(L2048[j2] + (L2048[j2 + 1] - L2048[j2]) * (f2 - m2) - ref));
  const f4 = x * (4096 / TAU), m4 = f4 | 0, j4 = m4 & 4095;
  e.l4096 = Math.max(e.l4096, Math.abs(L4096[j4] + (L4096[j4 + 1] - L4096[j4]) * (f4 - m4) - ref));
  const f8 = x * (8192 / TAU), m8 = f8 | 0, j8 = m8 & 8191;
  e.l8192 = Math.max(e.l8192, Math.abs(L8192[j8] + (L8192[j8 + 1] - L8192[j8]) * (f8 - m8) - ref));
  e.nat = Math.max(e.nat, Math.abs(Math.fround(Math.sin(x)) - Math.fround(ref)));
}
console.log('\n### sin table accuracy (max |error| vs Math.sin, 2^20 samples in [-pi,pi))');
console.log('| variant | max abs error | table bytes |');
console.log('|---|---|---|');
console.log('| 65536 step (= MathHelper) | ' + e.mc.toExponential(3) + ' | 262144 |');
console.log('| 4096 step | ' + e.s4096.toExponential(3) + ' | 16384 |');
console.log('| 1024 step | ' + e.s1024.toExponential(3) + ' | 4096 |');
console.log('| 2048 lerp | ' + e.l2048.toExponential(3) + ' | 8196 |');
console.log('| 4096 lerp | ' + e.l4096.toExponential(3) + ' | 16388 |');
console.log('| 8192 lerp | ' + e.l8192.toExponential(3) + ' | 32772 |');

// -------------------------------- 3. GlStateManager.rotate / Matrix4f.rotate

// The trig inside Matrix4f.rotate(angle, axis), current vs table.
add(bench('rotate trig: Math.cos+Math.sin', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const a = ANG[i];
      s += Math.cos(a) + Math.sin(a);
    }
  }
  sink += s;
}));
add(bench('rotate trig: 65536 table sin+cos', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const a = ANG[i];
      const idx = (a * K65536) | 0;
      s += SIN65536[idx & 0xffff] + SIN65536[(idx + 16384) & 0xffff];
    }
  }
  sink += s;
}));
add(bench('rotate trig: 65536 table, one index', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const idx = ((ANG[i] * K65536) | 0) & 0xffff;
      s += SIN65536[idx] + SIN65536[(idx + 16384) & 0xffff];
    }
  }
  sink += s;
}));

// 4096 lerp trig pair (sin + cos via quarter offset)
add(bench('rotate trig: 4096 lerp sin+cos', N, (it) => {
  let s = 0;
  const k = 4096 / TAU;
  for (let n = 0; n < it; ++n) {
    for (let i = 0; i < N; ++i) {
      const f = ANG[i] * k;
      const m = f | 0;
      const fr = f - m;
      const j = m & 4095;
      const jc = (j + 1024) & 4095;
      s += L4096[j] + (L4096[j + 1] - L4096[j]) * fr;
      s += L4096[jc] + (L4096[jc + 1] - L4096[jc]) * fr;
    }
  }
  sink += s;
}));

report('Matrix4f.rotate trig (2 calls per rotation)');

// ------------------------------------------------ 4. full-frame simulations

// A busy scene: ~120 visible entities x ~20 model-part rotations each.
const ROT_BATCH = 2400;
const BATCH = rand(ROT_BATCH, -Math.PI, Math.PI, 31337);

add(bench('frame: 2400 rotations, native trig', ROT_BATCH, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < ROT_BATCH; ++i) {
      const a = BATCH[i];
      s += Math.cos(a) + Math.sin(a);
    }
  }
  sink += s;
}));
add(bench('frame: 2400 rotations, 65536 table', ROT_BATCH, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < ROT_BATCH; ++i) {
      const idx = (BATCH[i] * K65536) | 0;
      s += SIN65536[idx & 0xffff] + SIN65536[(idx + 16384) & 0xffff];
    }
  }
  sink += s;
}));

report('per-frame rotation batches');

// ------------------------------------------- 5. sqrt / normalize replacements

// MathHelper.fastInvSqrt: the engine's own fast reciprocal square root
// (StrictMath-free version of the Quake rsqrt).
const f64 = new Float64Array(1);
const u32 = new Uint32Array(f64.buffer);
function mcRsqrt(x) {          // fastInvSqrt
  const d0 = 0.5 * x;
  f64[0] = x;
  let hi = u32[1];
  // 6910469410427058090L >> 1 subtracted from the constant, per the Java:
  //   i = 6910469410427058090L - (i >> 1)
  // implemented on the raw 64-bit pattern via hi/lo halves
  const lo = u32[0];
  // (i >> 1) as 64-bit: lo' = (lo >>> 1) | ((hi & 1) << 31); hi' = hi >>> 1
  const nlo = (lo >>> 1) | ((hi & 1) << 31);
  const nhi = hi >>> 1;
  // constant 0x5FE6EB50C7B537AA >> ... compute C - x halves
  const CLO = 0xC7B537AA, CHI = 0x5FE6EB50C;
  let rlo = (CLO - nlo) >>> 0;
  let rhi = (CHI - nhi - (CLO < nlo ? 1 : 0)) >>> 0;
  u32[0] = rlo;
  u32[1] = rhi;
  let y = f64[0];
  y = y * (1.5 - d0 * y * y);
  return y;
}

add(bench('Math.sqrt', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += Math.sqrt(POS[i]);
  sink += s;
}));
add(bench('1/Math.sqrt', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) for (let i = 0; i < N; ++i) s += 1 / Math.sqrt(POS[i]);
  sink += s;
}));
add(bench('sqrt+3 div (Vec3.normalize shape)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const x = POS[i], y = POS[(i + 1) & 4095], z = POS[(i + 2) & 4095];
      const l = Math.sqrt(x * x + y * y + z * z);
      s += x / l + y / l + z / l;
    }
  }
  sink += s;
}));
add(bench('1/sqrt+3 mul (Vec3.normalize shape)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const x = POS[i], y = POS[(i + 1) & 4095], z = POS[(i + 2) & 4095];
      const l = 1 / Math.sqrt(x * x + y * y + z * z);
      s += x * l + y * l + z * l;
    }
  }
  sink += s;
}));
add(bench('fastInvSqrt + 3 mul', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const x = POS[i], y = POS[(i + 1) & 4095], z = POS[(i + 2) & 4095];
      const l = mcRsqrt(x * x + y * y + z * z);
      s += x * l + y * l + z * l;
    }
  }
  sink += s;
}));

report('square root / normalization');

// rsqrt accuracy
let er = 0;
for (let i = 0; i < 1 << 18; ++i) {
  const x = 1e-3 + (i / (1 << 18)) * 100;
  er = Math.max(er, Math.abs(mcRsqrt(x) * Math.sqrt(x) - 1));
}
console.log('\nfastInvSqrt relative error after 1 Newton step: ' + er.toExponential(3));

// --------------------------------------------- 6. cold-start table build cost

{
  const t0 = now();
  const t = new Float32Array(65536);
  for (let i = 0; i < 65536; ++i) t[i] = Math.sin(i * TAU / 65536);
  const t1 = now();
  console.log('\n65536-entry sin table build: ' + (t1 - t0).toFixed(2) + ' ms (class-init cost, once)');
  const t2 = now();
  const u = new Float32Array(4097);
  for (let i = 0; i <= 4096; ++i) u[i] = Math.sin(i * TAU / 4096);
  const t3 = now();
  console.log('4096-entry sin table build:  ' + (t3 - t2).toFixed(3) + ' ms');
  sink += t[1] + u[1];
}

// ------------------------- 7. the two shipped optimisations, as TeaVM compiles them

// Java `float` arithmetic becomes double arithmetic wrapped in Math.fround at the points where the
// float rounding is observable, so these loops model the compiled form of the two changed sites:

// (a) Matrix4f.rotate:  float c = (float) Math.cos(angle); float s = (float) Math.sin(angle);
add(bench('shipped #10 before: (float)cos + (float)sin', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const a = Math.fround(ANG[i]);
      s += Math.fround(Math.cos(a)) + Math.fround(Math.sin(a));
    }
  }
  sink += s;
}));
add(bench('shipped #10 after:  MathHelper.cos + MathHelper.sin', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const a = Math.fround(ANG[i]);
      const idx = (Math.fround(a * 10430.378) | 0);
      s += SIN65536[(Math.fround(a * 10430.378 + 16384.0) | 0) & 0xffff] + SIN65536[idx & 0xffff];
    }
  }
  sink += s;
}));

// (b) MipmapGammaBlend: (float) Math.pow((double)((float)(v & 255) / 255.0F), 2.2)
const GAMMA = new Float32Array(256);
for (let i = 0; i < 256; ++i) GAMMA[i] = Math.fround(Math.pow(Math.fround(Math.fround(i) / 255.0), 2.2));
const CHAN = new Uint8Array(N);
for (let i = 0; i < N; ++i) CHAN[i] = (i * 37) & 255;
add(bench('shipped #11 before: (float)Math.pow(v/255, 2.2)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      s += Math.fround(Math.pow(Math.fround(CHAN[i] / 255.0), 2.2));
    }
  }
  sink += s;
}));
add(bench('shipped #11 after:  GAMMA_2_2[v] (Float32Array)', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) s += GAMMA[CHAN[i]];
  }
  sink += s;
}));

// full mipmap-filter shape: 16 decode + 4 encode per opaque texel quartet (the linear branch is
// 4 decode + 4 encode; the gamma branch is 16 decode + 4 encode)
add(bench('shipped #11 before: full gamma-branch texel', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      const a = Math.fround(CHAN[i] / 255.0);
      const b = Math.fround(CHAN[(i + 1) & 4095] / 255.0);
      const c = Math.fround(CHAN[(i + 2) & 4095] / 255.0);
      const d = Math.fround(CHAN[(i + 3) & 4095] / 255.0);
      let f = 0;
      f += Math.fround(Math.pow(a, 2.2));
      f += Math.fround(Math.pow(b, 2.2));
      f += Math.fround(Math.pow(c, 2.2));
      f += Math.fround(Math.pow(d, 2.2));
      f = Math.fround(f / 4.0);
      s += Math.fround(Math.pow(f, 0.45454545454545453)) * 255.0 | 0;
    }
  }
  sink += s;
}));
add(bench('shipped #11 after:  full gamma-branch texel', N, (it) => {
  let s = 0;
  for (let k = 0; k < it; ++k) {
    for (let i = 0; i < N; ++i) {
      let f = 0;
      f += GAMMA[CHAN[i]];
      f += GAMMA[CHAN[(i + 1) & 4095]];
      f += GAMMA[CHAN[(i + 2) & 4095]];
      f += GAMMA[CHAN[(i + 3) & 4095]];
      f = Math.fround(f / 4.0);
      s += Math.fround(Math.pow(f, 0.45454545454545453)) * 255.0 | 0;
    }
  }
  sink += s;
}));

report('shipped optimisations measured in the target runtime');

// per-frame extrapolation for #10, using the same 2400-rotation batch
{
  const ROT = 2400;
  const B = rand(ROT, -Math.PI, Math.PI, 31337);
  const t0 = now();
  let s = 0;
  for (let rep = 0; rep < 2000; ++rep) {
    for (let i = 0; i < ROT; ++i) {
      const a = Math.fround(B[i]);
      s += Math.fround(Math.cos(a)) + Math.fround(Math.sin(a));
    }
  }
  const before = (now() - t0) / 2000; // ms per frame-equivalent batch
  const t1 = now();
  for (let rep = 0; rep < 2000; ++rep) {
    for (let i = 0; i < ROT; ++i) {
      const a = Math.fround(B[i]);
      const idx = Math.fround(a * 10430.378) | 0;
      s += SIN65536[(Math.fround(a * 10430.378 + 16384.0) | 0) & 0xffff] + SIN65536[idx & 0xffff];
    }
  }
  const after = (now() - t1) / 2000;
  console.log('\n#10 per-frame trig cost for ' + ROT + ' model rotations: before ' +
    (before * 1e3).toFixed(1) + ' us, after ' + (after * 1e3).toFixed(1) + ' us, saves ' +
    ((before - after) * 1e3).toFixed(1) + ' us/frame  (' +
    ((before - after) / 16.667 * 100).toFixed(3) + '% of a 60 Hz frame)');
  sink += s;
}

console.log('\n(sink ' + (sink % 1e9).toFixed(0) + ')');
