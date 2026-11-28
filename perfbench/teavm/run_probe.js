#!/usr/bin/env node
/* Run the TeaVM-generated math benchmark under Node/V8 and print the parsed results.
 * Usage: node ./perfbench/teavm/run_probe.js [--raw]
 */
'use strict';
const fs = require('fs');
const path = require('path');

const build = path.join(__dirname, 'build', 'js');
const fullPath = path.join(build, 'full.js');
const litePath = path.join(build, 'lite.js');
const out = fullPath;

const lines = [];
const originalInfo = console.info;
const capture = (msg) => { lines.push(String(msg)); };

function timed(fn) {
  const t = process.hrtime.bigint();
  fn();
  return Number(process.hrtime.bigint() - t) / 1e6;
}

console.info = capture;
let parseFullMs, parseLiteMs, initFullMs, initLiteMs, totalMs;
try {
  parseFullMs = timed(() => { global.__full = require(fullPath); });
  initFullMs = timed(() => { global.__full.main(['init']); });
  parseLiteMs = timed(() => { global.__lite = require(litePath); });
  initLiteMs = timed(() => { global.__lite.main(['init']); });
  totalMs = timed(() => { global.__full.main([]); });
} finally {
  console.info = originalInfo;
}

if (process.argv.includes('--raw')) {
  lines.forEach((l) => originalInfo(l));
  process.exit(0);
}

const bench = new Map();
const init = [];
const verify = [];
const errors = [];
for (const l of lines) {
  const p = l.trim().split(/\s+/);
  if (p[0] === 'BENCH') {
    if (!bench.has(p[1])) bench.set(p[1], []);
    bench.get(p[1]).push(Number(p[3]) / Number(p[4]));
  } else if (p[0] === 'INIT') init.push([p[1], Number(p[2]) / 1e6]);
  else if (p[0] === 'VERIFY') verify.push([p[1], Number(p[2]), Number(p[3])]);
  else if (p[0] === 'ERROR') errors.push([p[1], Number(p[2]), Number(p[3]), Number(p[4])]);
}

function median(a) { const s = a.slice().sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; }
function pct(a, q) { const s = a.slice().sort((x, y) => x - y); return s[Math.min(s.length - 1, Math.floor(q * s.length))]; }

console.log('=== TeaVM 0.9.2 -> V8 ' + process.versions.v8 + ' (Node ' + process.version + ') ===');
console.log('full bundle: ' + fs.statSync(fullPath).size + ' bytes; lite bundle: ' + fs.statSync(litePath).size + ' bytes');
console.log('startup: parse full ' + parseFullMs.toFixed(2) + ' ms / clinit ' + initFullMs.toFixed(2) +
            ' ms | parse lite ' + parseLiteMs.toFixed(2) + ' ms / clinit ' + initLiteMs.toFixed(2) + ' ms');
console.log('full benchmark wall time ' + totalMs.toFixed(1) + ' ms');
console.log('');
console.log('-- cold first-use inside main (ms; TeaVM runs all clinits before main, see startup above) --');
for (const [k, v] of init) console.log('  ' + k.padEnd(26) + v.toFixed(3) + ' ms');
console.log('');
console.log('-- steady state (median ns/op, 7 reps; p95 in parentheses) --');
const med = new Map();
for (const [k, v] of bench) {
  med.set(k, median(v));
  console.log('  ' + k.padEnd(26) + median(v).toFixed(3).padStart(10) + ' ns  (p95 ' + pct(v, 0.95).toFixed(3) + ')');
}
console.log('');
console.log('-- relative speedups --');
function sp(a, b) {
  if (!med.has(a) || !med.has(b)) return;
  console.log('  ' + (b + ' / ' + a).padEnd(52) + (med.get(a) / med.get(b)).toFixed(2) + 'x');
}
sp('decode_native', 'decode_lut');
sp('blend_vanilla_linear', 'blend_shipped_linear');
sp('blend_vanilla_gamma', 'blend_shipped_gamma');
sp('trig_native', 'trig_table');
sp('encode_exact', 'encode_quantized');
sp('lightmap_original', 'lightmap_cse');
sp('mh_atan2_legacy', 'mh_atan2_shipped');
sp('mh_rsqrt_legacy', 'mh_rsqrt_shipped');
sp('beacon_8trig', 'beacon_2trig');
sp('guardian_16trig', 'guardian_2trig');
sp('fog_pow25', 'fog_sqrtsqrt');
sp('font_pow', 'font_exp');
console.log('');
console.log('-- equivalence (ERROR lines are informational measurements, not failures) --');
for (const [k, c, f] of verify) console.log('  ' + k.padEnd(34) + c + ' checks, ' + f + ' mismatches');
for (const [k, c, f, e] of errors) console.log('  ' + k.padEnd(34) + ' ' + c + ' checks, ' + f + ' differing, max ' + e);
const bad = verify.filter((v) => v[2] !== 0);
console.log('');
console.log(bad.length === 0 ? 'OK: all exactness checks passed.' : 'FAILED: ' + bad.length + ' verification sets mismatched.');
process.exit(bad.length === 0 ? 0 : 1);
