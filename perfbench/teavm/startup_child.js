#!/usr/bin/env node
/* One-process startup measurement helper for run_startup.js. */
'use strict';
const { performance } = require('perf_hooks');
const file = process.argv[2];
const lines = [];
const origInfo = console.info, origLog = console.log;
console.info = (...a) => { lines.push(a.join(' ')); };
console.log = (...a) => { lines.push(a.join(' ')); };
let parseMs, clinitMs, err = null;
try {
  const t0 = performance.now();
  const mod = require(file);
  const t1 = performance.now();
  mod.main([]);
  const t2 = performance.now();
  parseMs = t1 - t0;
  clinitMs = t2 - t1;
} catch (e) { err = String(e && e.stack || e); }
console.info = origInfo;
console.log = origLog;
process.stdout.write(JSON.stringify({ parseMs, clinitMs, lines, err }) + '\n');
