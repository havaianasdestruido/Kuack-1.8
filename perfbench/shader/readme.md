# `perfbench/shader` — built-in deferred/PBR shader hot loops

There is no GPU (and no WebGL/GL context) in the sandbox, so these two scripts are how a shader
change is made reviewable. See `perfbench/FINDINGS.md` #14 for the results.

## `run_bench.sh` — CPU model, equivalence and op census

```
./perfbench/shader/run_bench.sh        # needs gcc + libm + python3 only
```

Replays the old and new scalar `float` math of the touched GLSL bodies (`eaglercraftLighting`,
the whole `lighting_point.fsh` fragment body, and the per-light loops of
`forward_core.fsh`/`accel_particle_forward.fsh`) over a 1920×1080 synthetic gbuffer with 8 dynamic
lights, and reports:

- **equivalence** — discard-mask mismatches, ULP deviation, and how many fragments would change
  their RGBA16F value or their 8-bit `post_tonemap` output (identical inputs for both variants);
- **op census** — `pow`/SFU pairs, `sqrt`/`inversesqrt`, texture fetches and divides per shader
  invocation (built with `-DPBR_CENSUS`);
- **instruction count** — x86 instructions of the straight-line BRDF between `asm` fences (built
  with `-DPBR_INSN`);
- **CPU time** of the identical sequence, which is only an *upper bound* for the GPU: libm `powf()`
  is far dearer than a GPU SFU `pow()`, while the culling wins are hardware-independent.

The synthetic frame is a closed blocky room (floor, ceiling, walls, 8 boxes, block-face normals),
8 torches of radius 12 with **half of them embedded inside solid blocks** (a torch/glowstone/lava
source is itself geometry — that is the case the facing cull removes), and a material mix of 5 %
metal (`0.9 ≤ g < 0.964`), 25 % mirror-like (`g ≥ 0.964`), 2 % emissive and the rest dielectric, all
from `PBRMaterialConstants`-style ranges. Depth/normal values are quantised as the 8-bit gbuffer
does. Light meshes are modelled as the projected bounding sphere of each light, drawn with depth
testing off (as `combineGBuffersAndIlluminate()` does).

## `run_validate.sh` — does the GLSL still compile?

```
./perfbench/shader/run_validate.sh     # npm install once (registry.npmjs.org), then offline
```

Assembles each shader exactly like `ShaderSource.java` (recursive `#EAGLER INCLUDE`, injected
`#line`) and the way `ShaderCompiler.java` builds it (`#version 300 es` + `#define` flags), then
compiles it — plus the compile-flag combinations the pipeline actually builds — with the real
**glslang** front end (wasm build of `@webgpu/glslang`). glslang's SPIR-V backend requires
decorations the engine does not need (explicit `layout(location)` on varyings, `layout(binding)` on
samplers/uniform blocks, a default float precision in fragment shaders), so those are added
mechanically; everything else — syntax, types, swizzles, overloads, undeclared identifiers,
redeclarations across the includes — is checked for real, and a deliberately broken shader is
rejected.

Currently: **13/13 variants compile** (`lighting_point`, `lighting_sun` ×2, `deferred_combine` ×2,
`forward_core` ×2, `accel_particle_forward` ×2, `realistic_water_render` ×2,
`forward_glass_highlights` ×2).

`perfbench/shader/build/` and `node_modules/` are gitignored.
