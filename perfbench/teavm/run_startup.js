#!/usr/bin/env node
/*
 * Compare TeaVM/V8 startup for the pre-change runtime gamma table and the shipped embedded tables.
 * Each measurement runs in a fresh Node process, so module parse and class initialization are cold
 * inside the process; the OS page cache is warm after the first pair (both are reported).
 *   node ./perfbench/teavm/run_startup.js
 */
'use strict';
const fs = require('fs');
const path = require('path');
const cp = require('child_process');
const build = path.join(__dirname, 'build', 'js');
const child = path.join(__dirname, 'startup_child.js');
const runtime = path.join(build, 'gamma_runtime.js');
const embedded = path.join(build, 'gamma_embedded.js');
for (const f of [runtime, embedded]) {
  if (!fs.existsSync(f)) { console.error('Missing ' + f + '. Run ./perfbench/teavm/build_probe.sh first.'); process.exit(1); }
}
function median(a) { const s = a.slice().sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; }
function run(file) {
  const out = cp.execFileSync(process.execPath, [child, file], { encoding: 'utf8' });
  const j = JSON.parse(out);
  if (j.err) throw new Error(j.err);
  const checksum = (j.lines.find((l) => l.startsWith('CHECKSUM ')) || '').trim();
  return { parse: j.parseMs, clinit: j.clinitMs, checksum };
}
const REPS = 9;
const r = [], e = [];
for (let i = 0; i < REPS; ++i) {
  // Alternate order to avoid a systematic cache / JIT advantage.
  if ((i & 1) === 0) { r.push(run(runtime)); e.push(run(embedded)); }
  else { e.push(run(embedded)); r.push(run(runtime)); }
}
const rp = r.map((x) => x.parse), rc = r.map((x) => x.clinit);
const ep = e.map((x) => x.parse), ec = e.map((x) => x.clinit);
console.log('=== TeaVM/V8 gamma startup (Node ' + process.version + ', V8 ' + process.versions.v8 + ') ===');
console.log('runtime-generated bundle: ' + fs.statSync(runtime).size + ' bytes');
console.log('embedded-tables bundle:   ' + fs.statSync(embedded).size + ' bytes');
console.log('checksum equality: ' + (r[0].checksum === e[0].checksum ? 'IDENTICAL (' + r[0].checksum + ')' : 'MISMATCH'));
console.log('');
console.log('                              first process          median of 9');
console.log('  runtime parse / clinit     ' + r[0].parse.toFixed(3).padStart(7) + ' / ' + r[0].clinit.toFixed(3).padStart(7) +
            ' ms     ' + median(rp).toFixed(3).padStart(7) + ' / ' + median(rc).toFixed(3).padStart(7) + ' ms');
console.log('  embedded parse / clinit    ' + e[0].parse.toFixed(3).padStart(7) + ' / ' + e[0].clinit.toFixed(3).padStart(7) +
            ' ms     ' + median(ep).toFixed(3).padStart(7) + ' / ' + median(ec).toFixed(3).padStart(7) + ' ms');
console.log('');
console.log('Note: these are isolated TeaVM bundles, not the full game or Chromium.');
