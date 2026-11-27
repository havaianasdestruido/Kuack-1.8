#!/usr/bin/env node
/* Compile math.wat to build/math.wasm with WABT (development-time only). */
'use strict';
const fs = require('fs');
const path = require('path');
const HERE = __dirname;
const outDir = path.join(HERE, 'build');
fs.mkdirSync(outDir, { recursive: true });
const watPath = path.join(HERE, 'math.wat');
const outPath = path.join(outDir, 'math.wasm');

let wabtFactory;
const candidate = process.env.WABT_MODULE || path.join(HERE, 'node_modules', 'wabt');
try { wabtFactory = require(candidate); }
catch (e) {
  console.error('WABT not found. Install it with: npm install --no-save --prefix perfbench/wasm wabt@1.0.36');
  process.exit(1);
}
Promise.resolve(wabtFactory()).then((wabt) => {
  const module = wabt.parseWat(watPath, fs.readFileSync(watPath, 'utf8'));
  module.resolveNames();
  module.validate();
  const binary = module.toBinary({ log: false, write_debug_names: true });
  fs.writeFileSync(outPath, Buffer.from(binary.buffer));
  console.log('wrote ' + outPath + ': ' + binary.buffer.byteLength + ' bytes');
  console.log('WAT: ' + fs.statSync(watPath).size + ' bytes; 256 f32 constants = 1024 bytes of data');
  module.destroy();
}).catch((e) => { console.error(e); process.exit(1); });
