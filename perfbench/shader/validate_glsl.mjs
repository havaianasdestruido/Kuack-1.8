// Front-end compile check for the built-in deferred/PBR shaders.
//
// The game assembles each shader from `#EAGLER INCLUDE` fragments at runtime
// (ShaderSource.java) and prepends "#version 300 es" (ShaderCompiler.java), so there is no
// plain .glsl file a validator could take directly. This script does the same assembly, then
// runs the shader through the real glslang front end (the wasm build from @webgpu/glslang).
//
// glslang's SPIR-V backend insists on decorations the engine does not need (explicit
// `layout(location=)` on varyings, `layout(binding=)` on samplers and uniform blocks, and a
// default float precision in fragment shaders that a browser driver accepts), so those are
// added mechanically here. Everything else - syntax, types, swizzles, overload resolution,
// undefined identifiers, redeclarations across the includes - is checked by glslang.
//
// Usage: node validate_glsl.mjs <repo-root>     (or ./run_validate.sh)
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const glslangImport = require('@webgpu/glslang/dist/web-devel-onefile/glslang.js');
const glslangFactory = glslangImport.default || glslangImport;

const ROOT = process.argv[2] || '.';
const includeRe = /^\s*#EAGLER\s+INCLUDE\s*\((\d+)\)\s+"([^"]+)"\s*$/;

function assemble(entry) {
  const load = (rel) => readFileSync(`${ROOT}/sources/resources/assets/${rel.replace(/^eagler:/, 'eagler/')}`, 'utf8');
  const walk = (src) => {
    const out = [];
    for (const line of src.split('\n')) {
      if (/^\s*#line\s/.test(line)) { out.push('//'); continue; }
      const inc = includeRe.exec(line);
      if (inc) { out.push(walk(load(inc[2]))); continue; }
      out.push(line);
    }
    return out.join('\n');
  };
  return walk(load(entry));
}

// add the decorations SPIR-V codegen requires, without touching anything else
function decorate(src) {
  let loc = 0, bind = 0;
  return src.split('\n').map((line) => {
    let m;
    if ((m = /^(\s*)(in|out)\s+(?!.*\blayout\b)([A-Za-z0-9_]+)\s+([A-Za-z0-9_]+)\s*;\s*$/.exec(line)))
      return `${m[1]}layout(location = ${loc++}) ${m[2]} ${m[3]} ${m[4]};`;
    if ((m = /^(\s*)uniform\s+(sampler2D|sampler2DShadow|samplerCube)\s+([A-Za-z0-9_]+)(\s*\[[^\]]*\])?\s*;\s*$/.exec(line)))
      return `${m[1]}layout(binding = ${bind++}) uniform ${m[2]} ${m[3]}${m[4] || ''};`;
    if ((m = /^(\s*)uniform\s+((?!sampler|layout)[A-Za-z0-9_]+)\s+([A-Za-z0-9_,\s[\]]+);\s*$/.exec(line)))
      return `${m[1]}layout(set = 0, binding = ${++bind}) uniform PbrBlock${bind} { ${m[2]} ${m[3].split(',').map((s) => s.trim()).join(', ')}; };`;
    if ((m = /^(\s*)layout\(std140\)\s+uniform\s+([A-Za-z0-9_]+)(.*)$/.exec(line)))
      return `${m[1]}layout(std140, binding = ${bind++}) uniform ${m[2]}${m[3]}`;
    return line;
  }).join('\n');
}

// One entry per shader the change touches, plus the compile-flag combinations the pipeline
// actually builds for it (see the ShaderCompiler call sites in EaglerDeferredPipeline.java).
const TARGETS = [
  ['fragment', 'eagler:glsl/deferred/lighting_point.fsh', [[]]],
  ['fragment', 'eagler:glsl/deferred/lighting_sun.fsh', [[], ['COMPILE_SUN_SHADOW', 'COMPILE_COLORED_SHADOW']]],
  ['fragment', 'eagler:glsl/deferred/deferred_combine.fsh',
    [[], ['COMPILE_GLOBAL_AMBIENT_OCCLUSION', 'COMPILE_SCREEN_SPACE_REFLECTIONS', 'COMPILE_ENV_MAP_REFLECTIONS']]],
  ['fragment', 'eagler:glsl/deferred/forward_core.fsh',
    [[], ['COMPILE_DYNAMIC_LIGHTS', 'COMPILE_COLOR_ATTRIB', 'COMPILE_NORMAL_ATTRIB', 'COMPILE_LIGHTMAP_ATTRIB',
      'COMPILE_TEXTURE_ATTRIB', 'COMPILE_ENABLE_TEXTURE2D', 'COMPILE_ENABLE_LIGHTMAP', 'COMPILE_NORMAL_MATERIAL_TEXTURE',
      'COMPILE_SUN_SHADOW_LOD2', 'COMPILE_SUN_SHADOW_SMOOTH', 'COMPILE_PARABOLOID_ENV_MAP']]],
  ['fragment', 'eagler:glsl/deferred/accel_particle_forward.fsh',
    [[], ['COMPILE_DYNAMIC_LIGHTS', 'COMPILE_SUN_SHADOW_LOD0']]],
  ['fragment', 'eagler:glsl/deferred/realistic_water_render.fsh',
    [[], ['COMPILE_DYNAMIC_LIGHTS', 'COMPILE_SUN_SHADOW_LOD0', 'COMPILE_ENABLE_LIGHTMAP']]],
  ['fragment', 'eagler:glsl/deferred/forward_glass_highlights.fsh',
    [[], ['COMPILE_DYNAMIC_LIGHTS', 'COMPILE_SUN_SHADOW_LOD0']]],
];

// Complaints that are artefacts of compiling for Vulkan/SPIR-V rather than for GLES 3.0.
const SPIRV_NOISE = [
  /SPIR-V requires location for user input\/output/i,
  /sampler\/texture\/image requires layout\(binding=X\)/i,
  /uniform\/buffer blocks require layout\(binding=X\)/i,
  /type requires declaration of default precision qualifier/i,
  /non-opaque uniforms outside a block/i,
  /compilation terminated/i,
  /No code generated/i,
  /^\s*Parse failed/i,
  /^\s*$/,
];

// glslang's diagnostics come out through emscripten's print/printErr hooks (i.e. stderr), not
// through console.* proxies, so route them explicitly into the per-variant capture buffer.
let capture = null;
// emscripten's node output path writes straight to process.stdout/stderr, so that is where the
// per-variant capture has to sit (console.error/log proxies do not see it in this build).
const realOut = process.stdout.write.bind(process.stdout);
const realErr = process.stderr.write.bind(process.stderr);
process.stdout.write = (chunk, ...rest) => (capture ? (capture.push(String(chunk)), true) : realOut(chunk, ...rest));
process.stderr.write = (chunk, ...rest) => (capture ? (capture.push(String(chunk)), true) : realErr(chunk, ...rest));
const glslang = await glslangFactory();
let failures = 0, checks = 0;

for (const [stage, entry, variants] of TARGETS) {
  const base = assemble(entry);
  for (const defines of variants) {
    checks++;
    const src = '#version 310 es\nprecision highp float;\nprecision highp int;\n' +
      defines.map((d) => `#define ${d}\n`).join('') + decorate(base);
    const captured = [];
    capture = captured;
    try { glslang.compileGLSL(src, stage); } catch { /* diagnostics are captured above */ }
    capture = null;
    const problems = captured.join('\n').split('\n')
      .filter((l) => /ERROR/.test(l) || /WARNING/.test(l))
      .filter((l) => !SPIRV_NOISE.some((re) => re.test(l)));
    const label = `${entry.replace('eagler:glsl/deferred/', '')}${defines.length ? ' [' + defines.length + ' flags]' : ''}`;
    if (problems.length) {
      console.log(`FAIL ${label}`);
      for (const p of problems.slice(0, 8)) console.log(`     ${p}`);
      failures++;
    } else {
      console.log(`OK   ${label}`);
    }
  }
}

console.log(`\n${checks - failures}/${checks} shader variants compiled`);
process.exit(failures ? 1 : 0);
