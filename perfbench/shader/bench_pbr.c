/*
 * CPU model of the deferred/PBR shader hot paths touched by the "optimise the built-in PBR
 * shader hot loops" change. There is no GPU and no WebGL/GL context in this sandbox (see
 * CROSS_RUNTIME.md), so this harness cannot time the real pipeline. What it does instead:
 *
 *  1. it replays the *exact* scalar float math of the old and the new GLSL bodies, in the same
 *     order, so an invariance bug in the rewrite shows up as an output difference;
 *  2. it compares the two implementations over a full 1920x1080 frame (plus adversarial inputs)
 *     and reports discarded-fragment mask mismatches, max ULP deviation and max absolute error;
 *  3. it counts the ops a GPU actually pays for - pow()/sqrt() (SFU), texture fetches, and the
 *     number of x86 instructions gcc needs for the kernel body with the counters compiled out
 *     (a reproducible ALU proxy) - and, secondarily, the CPU time of the identical sequence.
 *
 * Build/run: ./perfbench/shader/run_bench.sh  (gcc -O2, no -ffast-math, both builds)
 *
 * Nothing here is a GPU frame-time measurement, and the CPU timing is only an upper bound for
 * the GPU: libm powf() costs tens of nano/microseconds while a GPU pow() is 2 SFU ops.
 */
#define _POSIX_C_SOURCE 200809L
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

/* ------------------------------------------------------------------ */
/* op census                                                          */
/* ------------------------------------------------------------------ */

#ifdef PBR_CENSUS
static long long op_pow, op_sqrt, op_tex, op_div4;
#define C_POW() (op_pow++)
#define C_SQRT() (op_sqrt++)
#define C_TEX() (op_tex++)
#define C_DIV4() (op_div4++)
static void census_reset(void) { op_pow = op_sqrt = op_tex = op_div4 = 0; }
static void census_print(const char *what, double frames) {
    printf("  %-26s pow/SFU-pairs %8.3f  sqrt/invsqrt %8.3f  tex %8.3f  4-cycles-of-div %8.3f\n",
           what, op_pow / frames, op_sqrt / frames, op_tex / frames, op_div4 / frames);
}
#else
#define C_POW() ((void) 0)
#define C_SQRT() ((void) 0)
#define C_TEX() ((void) 0)
#define C_DIV4() ((void) 0)
static void census_reset(void) {}
#endif

/* ------------------------------------------------------------------ */
/* tiny float vector helpers (GPU "highp float" semantics = IEEE f32) */
/* ------------------------------------------------------------------ */

typedef struct { float x, y, z; } v3;
typedef struct { float x, y, z, w; } v4;

static inline v3 v3m(float x, float y, float z) { v3 r = {x, y, z}; return r; }
static inline v3 v3add(v3 a, v3 b) { return v3m(a.x + b.x, a.y + b.y, a.z + b.z); }
static inline v3 v3sub(v3 a, v3 b) { return v3m(a.x - b.x, a.y - b.y, a.z - b.z); }
static inline v3 v3mul(v3 a, v3 b) { return v3m(a.x * b.x, a.y * b.y, a.z * b.z); }
static inline v3 v3s(v3 a, float s) { return v3m(a.x * s, a.y * s, a.z * s); }
static inline v3 v3max0(v3 a) { return v3m(a.x > 0.f ? a.x : 0.f, a.y > 0.f ? a.y : 0.f, a.z > 0.f ? a.z : 0.f); }
static inline float dot3(v3 a, v3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
static inline float maxf(float a, float b) { return a > b ? a : b; }
static inline float minf(float a, float b) { return a < b ? a : b; }

/* GLSL normalize() is defined as x * inversesqrt(dot(x,x)); keep that shape. */
static inline float glsl_inversesqrt(float x) {
    C_SQRT();
    return 1.0f / sqrtf(x);
}
static inline v3 glsl_normalize(v3 v) { return v3s(v, glsl_inversesqrt(dot3(v, v))); }

/* GLSL vec3 / scalar: four "divide" ops on a scalar-vector divide. */
static inline v3 glsl_v3_div_s(v3 a, float s) {
    C_DIV4();
    return v3m(a.x / s, a.y / s, a.z / s);
}

static inline float glsl_pow5(float x) {
    C_POW();
    return powf(x, 5.0f);
}

/* px(x) = x*x*x*x*x, the replacement form used by the patched shaders */
static inline float glsl_pow5_mul(float x) {
    float x2 = x * x;
    return x2 * x2 * x;
}

/* ------------------------------------------------------------------ */
/* 4x4 matrices (only what the two transform lines need)              */
/* ------------------------------------------------------------------ */

typedef struct { float m[16]; } m4; /* column-major, like GLSL */

static inline v4 m4_mul_v4(m4 a, v4 v) {
    v4 r;
    r.x = a.m[0] * v.x + a.m[4] * v.y + a.m[8] * v.z + a.m[12] * v.w;
    r.y = a.m[1] * v.x + a.m[5] * v.y + a.m[9] * v.z + a.m[13] * v.w;
    r.z = a.m[2] * v.x + a.m[6] * v.y + a.m[10] * v.z + a.m[14] * v.w;
    r.w = a.m[3] * v.x + a.m[7] * v.y + a.m[11] * v.z + a.m[15] * v.w;
    return r;
}

/* (mat3(a) * v) rounded to vec4 with w = 0, i.e. what "m * vec4(v, 0.0)" computes. */
static inline v4 m4_mul_dir(m4 a, v3 v) {
    v4 r;
    r.x = a.m[0] * v.x + a.m[4] * v.y + a.m[8] * v.z;
    r.y = a.m[1] * v.x + a.m[5] * v.y + a.m[9] * v.z;
    r.z = a.m[2] * v.x + a.m[6] * v.y + a.m[10] * v.z;
    r.w = 0.0f;
    return r;
}

/* normalize(mat3(inverseViewMatrix4f) * v) */
static inline v3 m3_mul_normalize(m4 a, v3 v) {
    v3 r;
    r.x = a.m[0] * v.x + a.m[4] * v.y + a.m[8] * v.z;
    r.y = a.m[1] * v.x + a.m[5] * v.y + a.m[9] * v.z;
    r.z = a.m[2] * v.x + a.m[6] * v.y + a.m[10] * v.z;
    return glsl_normalize(r);
}

static m4 m4_invert(m4 a) {
    const float *m = a.m;
    m4 r;
    float *o = r.m;
    o[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
    o[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
    o[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
    o[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
    o[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
    o[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
    o[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
    o[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
    o[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
    o[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
    o[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
    o[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
    o[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
    o[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
    o[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
    o[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
    float det = m[0] * o[0] + m[1] * o[4] + m[2] * o[8] + m[3] * o[12];
    det = 1.0f / det;
    for (int i = 0; i < 16; ++i) o[i] *= det;
    return r;
}

/* ------------------------------------------------------------------ */
/* PBR lighting (lib/pbr_lighting.glsl), old and new                  */
/* ------------------------------------------------------------------ */

/* The -DPBR_INSN build forces these inline so that the fenced instruction count in
   run_bench.sh sees the whole body instead of a call. */
#ifdef PBR_INSN
#define PBR_FN __attribute__((always_inline)) static inline
#else
#define PBR_FN static
#endif

/* eaglercraftLighting() as shipped, plus the optional prefetched metal LUT values. */
PBR_FN v3 eaglercraftLighting_old(v3 albedo, v3 radiance, v3 viewDir, v3 lightDir, v3 normalVec, v3 materials) {
    float roughness = 1.0f - materials.x * 0.85f;
    v3 H = glsl_normalize(v3add(viewDir, lightDir));
    /* max(normalVec * mat3(H, viewDir, lightDir), vec3(0.0)) */
    v3 NdotHVL = v3max0(v3m(dot3(normalVec, H), dot3(normalVec, viewDir), dot3(normalVec, lightDir)));
    float NDF = roughness * roughness;
    NDF *= NDF;
    float denom = NdotHVL.x * NdotHVL.x * (NDF - 1.0f) + 1.0f;
    NDF /= denom * denom * 3.141592f;
    float gs = roughness + 1.0f;
    gs *= gs * 0.125f;
    float NdotV = NdotHVL.y / (NdotHVL.y * (1.0f - gs) + gs);
    float NdotL = NdotHVL.z / (NdotHVL.z * (1.0f - gs) + gs);
    NDF *= NdotV * NdotL;
    float fresnel = glsl_pow5(maxf(1.0f - NdotHVL.x, 0.0f));
    v3 kD = v3m(0.03f, 0.03f, 0.03f);
    v3 F;
    if (materials.y < 0.9f) {
        F = v3m(materials.y + (1.0f - materials.y) * fresnel, materials.y + (1.0f - materials.y) * fresnel,
                materials.y + (1.0f - materials.y) * fresnel);
        kD = glsl_v3_div_s(v3mul(v3sub(v3m(1.f, 1.f, 1.f), F), albedo), 3.141592f);
    } else if (materials.y < 0.964f) {
        /* metal path: u_metalsLUT lookups are modelled as instantaneous (the shader cost of
           the two texel fetches is counted in the census, not timed) */
        v3 mN = v3m(1.4f, 1.0f, 0.6f);
        v3 mK = v3s(albedo, 1.0f);
        fresnel = 1.0f - fresnel;
        mK = v3mul(mK, mK);
        mK = v3add(mK, v3mul(mN, mN));
        v3 nv = v3s(mN, fresnel * 2.0f);
        fresnel *= fresnel;
        v3 num = v3add(v3sub(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 den = v3add(v3add(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 r = v3m(num.x / den.x, num.y / den.y, num.z / den.z);
        mK = v3add(v3s(mK, fresnel), v3m(1.f, 1.f, 1.f));
        num = v3sub(mK, nv);
        den = v3add(mK, nv);
        r = v3add(r, v3m(num.x / den.x, num.y / den.y, num.z / den.z));
        r = v3m(minf(maxf(r.x * 0.5f, 0.f), 1.f), minf(maxf(r.y * 0.5f, 0.f), 1.f), minf(maxf(r.z * 0.5f, 0.f), 1.f));
        F = v3mul(r, r);
    } else {
        F = v3add(albedo, v3mul(v3sub(v3m(1.f, 1.f, 1.f), albedo), v3m(fresnel, fresnel, fresnel)));
    }
    denom = 4.0f * NdotHVL.y * NdotHVL.z + 0.0001f;
    v3 spec = glsl_v3_div_s(v3s(F, NDF), denom);
    v3 accum = v3add(kD, spec);
    accum = v3mul(accum, radiance);
    return v3s(accum, NdotHVL.z);
}

/* Same function with the pow(x,5.0) -> x2*x2*x rewrite applied. */
PBR_FN v3 eaglercraftLighting_new(v3 albedo, v3 radiance, v3 viewDir, v3 lightDir, v3 normalVec, v3 materials) {
    float roughness = 1.0f - materials.x * 0.85f;
    v3 H = glsl_normalize(v3add(viewDir, lightDir));
    v3 NdotHVL = v3max0(v3m(dot3(normalVec, H), dot3(normalVec, viewDir), dot3(normalVec, lightDir)));
    float NDF = roughness * roughness;
    NDF *= NDF;
    float denom = NdotHVL.x * NdotHVL.x * (NDF - 1.0f) + 1.0f;
    NDF /= denom * denom * 3.141592f;
    float gs = roughness + 1.0f;
    gs *= gs * 0.125f;
    float NdotV = NdotHVL.y / (NdotHVL.y * (1.0f - gs) + gs);
    float NdotL = NdotHVL.z / (NdotHVL.z * (1.0f - gs) + gs);
    NDF *= NdotV * NdotL;
    float nvf = maxf(1.0f - NdotHVL.x, 0.0f);
    float fresnel = glsl_pow5_mul(nvf);
    v3 kD = v3m(0.03f, 0.03f, 0.03f);
    v3 F;
    if (materials.y < 0.9f) {
        F = v3m(materials.y + (1.0f - materials.y) * fresnel, materials.y + (1.0f - materials.y) * fresnel,
                materials.y + (1.0f - materials.y) * fresnel);
        kD = glsl_v3_div_s(v3mul(v3sub(v3m(1.f, 1.f, 1.f), F), albedo), 3.141592f);
    } else if (materials.y < 0.964f) {
        v3 mN = v3m(1.4f, 1.0f, 0.6f);
        v3 mK = v3s(albedo, 1.0f);
        fresnel = 1.0f - fresnel;
        mK = v3mul(mK, mK);
        mK = v3add(mK, v3mul(mN, mN));
        v3 nv = v3s(mN, fresnel * 2.0f);
        fresnel *= fresnel;
        v3 num = v3add(v3sub(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 den = v3add(v3add(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 r = v3m(num.x / den.x, num.y / den.y, num.z / den.z);
        mK = v3add(v3s(mK, fresnel), v3m(1.f, 1.f, 1.f));
        num = v3sub(mK, nv);
        den = v3add(mK, nv);
        r = v3add(r, v3m(num.x / den.x, num.y / den.y, num.z / den.z));
        r = v3m(minf(maxf(r.x * 0.5f, 0.f), 1.f), minf(maxf(r.y * 0.5f, 0.f), 1.f), minf(maxf(r.z * 0.5f, 0.f), 1.f));
        F = v3mul(r, r);
    } else {
        F = v3add(albedo, v3mul(v3sub(v3m(1.f, 1.f, 1.f), albedo), v3m(fresnel, fresnel, fresnel)));
    }
    denom = 4.0f * NdotHVL.y * NdotHVL.z + 0.0001f;
    v3 spec = glsl_v3_div_s(v3s(F, NDF), denom);
    v3 accum = v3add(kD, spec);
    accum = v3mul(accum, radiance);
    return v3s(accum, NdotHVL.z);
}

/* eaglercraftIBL_Specular() (lib/pbr_env_map.glsl), old and new: same pow() rewrite. */
static v3 ibl_specular_old(v3 albedo, v3 envMapSample, v3 viewDir, v3 normalVec, v3 materials) {
    float NdotV = dot3(normalVec, v3s(viewDir, -1.0f));
    float roughness = 1.0f - materials.x * 0.85f;
    float fresnel = glsl_pow5(maxf(1.0f - NdotV, 0.0f));
    v3 F;
    if (materials.y < 0.9f) {
        F = v3m(materials.y + (maxf(1.0f - roughness, materials.y) - materials.y) * fresnel,
                materials.y + (maxf(1.0f - roughness, materials.y) - materials.y) * fresnel,
                materials.y + (maxf(1.0f - roughness, materials.y) - materials.y) * fresnel);
    } else if (materials.y < 0.964f) {
        v3 mN = v3m(1.4f, 1.0f, 0.6f);
        v3 mK = v3m(1.1f, 0.8f, 0.5f);
        fresnel = 1.0f - fresnel;
        mK = v3mul(mK, mK);
        mK = v3add(mK, v3mul(mN, mN));
        v3 nv = v3s(mN, fresnel * 2.0f);
        fresnel *= fresnel;
        v3 num = v3add(v3sub(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 den = v3add(v3add(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 r = v3m(num.x / den.x, num.y / den.y, num.z / den.z);
        mK = v3add(v3s(mK, fresnel), v3m(1.f, 1.f, 1.f));
        num = v3sub(mK, nv);
        den = v3add(mK, nv);
        r = v3add(r, v3m(num.x / den.x, num.y / den.y, num.z / den.z));
        r = v3m(minf(maxf(r.x * 0.5f, 0.f), 1.f), minf(maxf(r.y * 0.5f, 0.f), 1.f), minf(maxf(r.z * 0.5f, 0.f), 1.f));
        F = v3mul(r, r);
    } else {
        F = v3add(v3sub(v3m(1.f, 1.f, 1.f), albedo), v3s(albedo, fresnel));
    }
    float b0 = maxf(NdotV, 0.0f), b1 = roughness;
    b0 = 1.0f - b0;
    b1 = 1.0f - b1;
    b0 = b0 * b0;
    b1 = b1 * b1;
    b0 = 1.0f - b0;
    b1 = 1.0f - b1;
    C_TEX(); /* u_brdfLUT */
    float lutR = b0 * 0.9f + 0.05f, lutG = b1 * 0.5f + 0.1f; /* stand-in for the LUT texel */
    (void) lutR;
    (void) lutG;
    return v3mul(envMapSample, v3add(v3s(F, lutR), v3m(lutG, lutG, lutG)));
}

static v3 ibl_specular_new(v3 albedo, v3 envMapSample, v3 viewDir, v3 normalVec, v3 materials) {
    float NdotV = dot3(normalVec, v3s(viewDir, -1.0f));
    float roughness = 1.0f - materials.x * 0.85f;
    float nvf = maxf(1.0f - NdotV, 0.0f);
    float fresnel = glsl_pow5_mul(nvf);
    v3 F;
    if (materials.y < 0.9f) {
        F = v3m(materials.y + (maxf(1.0f - roughness, materials.y) - materials.y) * fresnel,
                materials.y + (maxf(1.0f - roughness, materials.y) - materials.y) * fresnel,
                materials.y + (maxf(1.0f - roughness, materials.y) - materials.y) * fresnel);
    } else if (materials.y < 0.964f) {
        v3 mN = v3m(1.4f, 1.0f, 0.6f);
        v3 mK = v3m(1.1f, 0.8f, 0.5f);
        fresnel = 1.0f - fresnel;
        mK = v3mul(mK, mK);
        mK = v3add(mK, v3mul(mN, mN));
        v3 nv = v3s(mN, fresnel * 2.0f);
        fresnel *= fresnel;
        v3 num = v3add(v3sub(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 den = v3add(v3add(mK, nv), v3m(fresnel, fresnel, fresnel));
        v3 r = v3m(num.x / den.x, num.y / den.y, num.z / den.z);
        mK = v3add(v3s(mK, fresnel), v3m(1.f, 1.f, 1.f));
        num = v3sub(mK, nv);
        den = v3add(mK, nv);
        r = v3add(r, v3m(num.x / den.x, num.y / den.y, num.z / den.z));
        r = v3m(minf(maxf(r.x * 0.5f, 0.f), 1.f), minf(maxf(r.y * 0.5f, 0.f), 1.f), minf(maxf(r.z * 0.5f, 0.f), 1.f));
        F = v3mul(r, r);
    } else {
        F = v3add(v3sub(v3m(1.f, 1.f, 1.f), albedo), v3s(albedo, fresnel));
    }
    float b0 = maxf(NdotV, 0.0f), b1 = roughness;
    b0 = 1.0f - b0;
    b1 = 1.0f - b1;
    b0 = b0 * b0;
    b1 = b1 * b1;
    b0 = 1.0f - b0;
    b1 = 1.0f - b1;
    C_TEX(); /* u_brdfLUT */
    float lutR = b0 * 0.9f + 0.05f, lutG = b1 * 0.5f + 0.1f;
    (void) lutR;
    (void) lutG;
    return v3mul(envMapSample, v3add(v3s(F, lutR), v3m(lutG, lutG, lutG)));
}

/* ------------------------------------------------------------------ */
/* synthetic frame: a 1920x1080 gbuffer, camera-relative              */
/* ------------------------------------------------------------------ */

#define W 1920
#define H 1080
#define NPIX (W * H)

typedef struct {
    float depth;      /* u_gbufferDepthTexture.r */
    v3 normal;        /* u_gbufferNormalTexture.rgb (already unorm-quantised) */
    v3 color;         /* u_gbufferColorTexture.rgb */
    v3 material;      /* u_gbufferMaterialTexture.rgb */
    v3 worldPos;      /* the position the shader reconstructs */
    float gl_FragCoord[2];
} gbuf;

typedef struct {
    v3 pos;
    v3 color;
    float radius;
    int covX0, covY0, covX1, covY1; /* projected bounding box of the light mesh, in pixels */
} light;

static gbuf *frame;
static m4 projM, invProjM, invViewM;
static light lights[12];
static int nlights = 8;

static uint32_t hash32(uint32_t x) {
    x ^= x >> 16;
    x *= 0x7feb352dU;
    x ^= x >> 15;
    x *= 0x846ca68bU;
    x ^= x >> 16;
    return x;
}
static float rnd01(uint32_t *s) {
    *s = hash32(*s + 0x9e3779b9U);
    return (float) (*s >> 8) * (1.0f / 16777216.0f);
}

static void build_matrices(void) {
    const float fovy = 70.0f * 3.14159265f / 180.0f;
    const float aspect = (float) W / (float) H;
    const float n = 0.05f, f = 256.0f;
    float t = 1.0f / tanf(fovy * 0.5f);
    memset(&projM, 0, sizeof(projM));
    projM.m[0] = t / aspect;
    projM.m[5] = t;
    projM.m[10] = (f + n) / (n - f);
    projM.m[11] = -1.0f;
    projM.m[14] = (2.0f * f * n) / (n - f);
    invProjM = m4_invert(projM);
    memset(&invViewM, 0, sizeof(invViewM)); /* camera-relative, no camera rotation */
    invViewM.m[0] = invViewM.m[5] = invViewM.m[10] = invViewM.m[15] = 1.0f;
}

static v4 project(v3 p) { /* world (== view, identity view matrix) -> clip */
    v4 v = {p.x, p.y, p.z, 1.0f};
    return m4_mul_v4(projM, v);
}

/* Axis-aligned blocks of the synthetic room, so the frame carries block-face normals
   (+-X, +-Y, +-Z) like Minecraft terrain instead of two infinite planes. */
typedef struct { float x0, y0, z0, x1, y1, z1; } box_t;

#define NBOX 8
static box_t boxes[NBOX];

static void build_boxes(void) {
    /* pillars / a step / a hanging block, all inside the room below */
    boxes[0] = (box_t){-3.0f, -1.62f, -4.0f, -2.0f, 0.4f, -3.0f};
    boxes[1] = (box_t){1.5f, -1.62f, -5.0f, 2.5f, -0.2f, -4.0f};
    boxes[2] = (box_t){-1.0f, -1.62f, -6.0f, 1.0f, -1.0f, -5.0f};   /* a floor step */
    boxes[3] = (box_t){2.0f, 0.6f, -3.0f, 3.0f, 1.4f, -2.0f};       /* hanging block */
    boxes[4] = (box_t){-5.0f, -1.62f, -7.0f, -4.0f, 1.4f, -6.0f};
    boxes[5] = (box_t){4.0f, -1.62f, -7.0f, 5.0f, 0.2f, -6.0f};
    boxes[6] = (box_t){-1.5f, -1.62f, -2.0f, -0.5f, -0.8f, -1.0f};
    boxes[7] = (box_t){0.5f, 0.2f, -7.0f, 1.5f, 1.4f, -6.0f};
}

static int ray_box(const box_t *b, v3 o, v3 d, float tmax, float *tout, int *axis) {
    float t0 = 0.0f, t1 = tmax;
    int ax = -1;
    const float lo[3] = {b->x0, b->y0, b->z0}, hi[3] = {b->x1, b->y1, b->z1};
    const float oo[3] = {o.x, o.y, o.z}, dd[3] = {d.x, d.y, d.z};
    for (int a = 0; a < 3; ++a) {
        if (dd[a] > -1e-9f && dd[a] < 1e-9f) {
            if (oo[a] < lo[a] || oo[a] > hi[a]) return 0;
        } else {
            float inv = 1.0f / dd[a];
            float ta = (lo[a] - oo[a]) * inv, tb = (hi[a] - oo[a]) * inv;
            if (ta > tb) { float t = ta; ta = tb; tb = t; }
            if (ta > t0) { t0 = ta; ax = a; }
            if (tb < t1) t1 = tb;
            if (t0 > t1) return 0;
        }
    }
    *tout = t0;
    *axis = ax;
    return 1;
}

static void build_frame(void) {
    build_matrices();
    build_boxes();
    frame = malloc(sizeof(gbuf) * NPIX);
    if (!frame) { fprintf(stderr, "oom\n"); exit(1); }
    const float y_floor = -1.62f; /* eye height above the floor */
    const float y_ceil = 1.42f;
    const float z_wall = -9.0f, x_wallL = -6.0f, x_wallR = 6.0f, z_wallBack = 2.0f;
    const float tanHalf = tanf(70.0f * 3.14159265f / 180.0f * 0.5f);
    const float aspect = (float) W / (float) H;
    for (int y = 0; y < H; ++y) {
        for (int x = 0; x < W; ++x) {
            gbuf *g = &frame[y * W + x];
            uint32_t seed = hash32((uint32_t) (y * W + x) * 2654435761U);
            float ndx = ((float) x + 0.5f) / (float) W * 2.0f - 1.0f;
            float ndy = ((float) y + 0.5f) / (float) H * 2.0f - 1.0f;
            v3 d = glsl_normalize(v3m(ndx * tanHalf * aspect, ndy * tanHalf, -1.0f));
            float tBest = 1e30f;
            v3 n = v3m(0.f, 1.f, 0.f);
            if (d.y < -1e-6f) {
                float t = y_floor / d.y;
                if (t > 0.0f && t < tBest) { tBest = t; n = v3m(0.f, 1.f, 0.f); }
            }
            if (d.y > 1e-6f) {
                float t = y_ceil / d.y;
                if (t > 0.0f && t < tBest) { tBest = t; n = v3m(0.f, -1.f, 0.f); }
            }
            if (d.z < -1e-6f) {
                float t = z_wall / d.z;
                if (t > 0.0f && t < tBest) { tBest = t; n = v3m(0.f, 0.f, 1.f); }
            }
            if (d.z > 1e-6f) {
                float t = z_wallBack / d.z;
                if (t > 0.0f && t < tBest) { tBest = t; n = v3m(0.f, 0.f, -1.f); }
            }
            if (d.x < -1e-6f) {
                float t = x_wallL / d.x;
                if (t > 0.0f && t < tBest) { tBest = t; n = v3m(1.f, 0.f, 0.f); }
            }
            if (d.x > 1e-6f) {
                float t = x_wallR / d.x;
                if (t > 0.0f && t < tBest) { tBest = t; n = v3m(-1.f, 0.f, 0.f); }
            }
            for (int bi = 0; bi < NBOX; ++bi) {
                float tb;
                int ax;
                if (ray_box(&boxes[bi], v3m(0.f, 0.f, 0.f), d, tBest, &tb, &ax) && tb > 1e-4f && tb < tBest) {
                    tBest = tb;
                    n = ax == 0 ? v3m(d.x > 0 ? -1.f : 1.f, 0.f, 0.f)
                                : ax == 1 ? v3m(0.f, d.y > 0 ? -1.f : 1.f, 0.f)
                                          : v3m(0.f, 0.f, d.z > 0 ? -1.f : 1.f);
                }
            }
            if (tBest > 1e29f) { /* sky: depth 0, everything else unused */
                g->depth = 0.0f;
                g->normal = v3m(0.5f, 1.0f, 0.5f);
                g->color = v3m(0.2f, 0.2f, 0.2f);
                g->material = v3m(0.5f, 0.0f, 0.0f);
                g->worldPos = v3m(0.f, 0.f, 0.f);
            } else {
                v3 p = v3s(d, tBest);
                /* quantise the normal the way an 8-bit gbuffer does */
                v3 nq = v3m(n.x * 0.5f + 0.5f, n.y * 0.5f + 0.5f, n.z * 0.5f + 0.5f);
                nq = v3m(floorf(nq.x * 255.0f + 0.5f) / 255.0f, floorf(nq.y * 255.0f + 0.5f) / 255.0f,
                         floorf(nq.z * 255.0f + 0.5f) / 255.0f);
                g->normal = nq;
                v4 clip = project(p);
                g->depth = (clip.z / clip.w) * 0.5f + 0.5f;
                float r = rnd01(&seed), gg = rnd01(&seed), bb = rnd01(&seed);
                g->color = v3m(r, gg, bb);
                /* material distribution: mostly dielectric, a few percent metal,
                   a quarter of the assets use the mirror-like g >= 0.964 range */
                float m = rnd01(&seed);
                if (m < 0.05f) g->material = v3m(rnd01(&seed) * 0.9f, 0.9f + rnd01(&seed) * 0.064f, 0.0f);
                else if (m < 0.30f) g->material = v3m(rnd01(&seed) * 0.9f, 0.964f + rnd01(&seed) * 0.036f, 0.0f);
                else g->material = v3m(rnd01(&seed) * 0.9f, rnd01(&seed) * 0.3f, 0.0f);
                if (rnd01(&seed) < 0.02f) g->material.z = 1.0f; /* emissive/unlit flag */
                g->worldPos = p;
            }
            g->gl_FragCoord[0] = (float) x + 0.5f;
            g->gl_FragCoord[1] = (float) y + 0.5f;
        }
    }
}

static void build_lights(int n) {
    nlights = n;
    /* Half the light sources are *inside* solid blocks - a torch, glowstone or lava block is
       itself part of the geometry - which is exactly the case the facing cull removes. */
    static const v3 embedded[NBOX] = {
        {-2.5f, -0.2f, -3.5f}, {2.0f, -0.6f, -4.5f}, {-4.5f, 0.2f, -6.5f},
        {4.5f, -0.8f, -6.5f}, {-1.0f, -1.2f, -1.5f}, {1.0f, 0.8f, -6.5f},
        {-1.0f, -1.2f, -5.5f}, {2.5f, 1.0f, -2.5f},
    };
    static const v3 open_air[] = {
        {0.0f, -0.6f, -2.0f}, {-2.0f, -0.2f, -1.0f}, {2.5f, 0.0f, -1.5f}, {-4.0f, 0.6f, -2.5f},
        {4.0f, -0.6f, -3.0f}, {0.0f, 1.0f, -4.0f}, {-3.0f, -1.0f, -5.0f}, {3.5f, 0.4f, -5.5f},
    };
    for (int i = 0; i < n; ++i) {
        light *L = &lights[i];
        L->pos = (i % 2 == 0) ? embedded[i % NBOX] : open_air[i % 8];
        L->color = v3m(1.0f, 0.62f, 0.32f);
        L->color = v3s(L->color, 3.0f + 7.0f * ((float) (i % 4) / 3.0f));
        L->radius = 12.0f;
        /* projected bounding box of the light mesh (a sphere of L->radius centred on L->pos) */
        float tanHalf = tanf(70.0f * 3.14159265f / 180.0f * 0.5f);
        float aspect = (float) W / (float) H;
        float zc = L->pos.z;
        if (zc > -0.1f) { L->covX0 = 0; L->covY0 = 0; L->covX1 = -1; L->covY1 = -1; continue; }
        float sx = 0.5f + (L->pos.x / -zc) / (tanHalf * aspect) * 0.5f;
        float sy = 0.5f + (L->pos.y / -zc) / tanHalf * 0.5f;
        float sr = (L->radius / -zc) / (tanHalf * aspect) * 0.5f;
        float srY = (L->radius / -zc) / tanHalf * 0.5f;
        L->covX0 = (int) maxf(0.0f, floorf((sx - sr) * W));
        L->covX1 = (int) minf((float) W - 1, ceilf((sx + sr) * W));
        L->covY0 = (int) maxf(0.0f, floorf((sy - srY) * H));
        L->covY1 = (int) minf((float) H - 1, ceilf((sy + srY) * H));
    }
}

/* ------------------------------------------------------------------ */
/* lighting_point.fsh: whole fragment body, old and new               */
/* ------------------------------------------------------------------ */

typedef struct { int discarded; v3 color; } frag_result;

PBR_FN frag_result lighting_point_old(const gbuf *g, const light *L, v3 lightColor) {
    frag_result o = {0, v3m(0.f, 0.f, 0.f)};
    float depth = g->depth;
    C_TEX();
    if (depth < 0.00001f) { o.discarded = 1; return o; }
    v4 ndc = {g->gl_FragCoord[0] / (float) W * 2.0f - 1.0f, g->gl_FragCoord[1] / (float) H * 2.0f - 1.0f,
              depth * 2.0f - 1.0f, 1.0f};
    v4 vsp = m4_mul_v4(invProjM, ndc);
    v4 wp = m4_mul_v4(invViewM, vsp);
    v3 lightDist = v3sub(glsl_v3_div_s(v3m(wp.x, wp.y, wp.z), wp.w), L->pos);
    v3 color = glsl_v3_div_s(lightColor, dot3(lightDist, lightDist));
    if (color.x + color.y + color.z < 0.025f) { o.discarded = 1; return o; }
    C_TEX(); /* u_gbufferColorTexture */
    v3 diffuse = v3mul(g->color, g->color);
    C_TEX(); /* u_gbufferNormalTexture */
    v3 normal = v3m(g->normal.x * 2.0f - 1.0f, g->normal.y * 2.0f - 1.0f, g->normal.z * 2.0f - 1.0f);
    C_TEX(); /* u_gbufferMaterialTexture */
    v3 material = g->material;
    v3 wsn = m3_mul_normalize(invViewM, normal);
    v3 lightDir = glsl_normalize(lightDist);
    lightDir = material.z == 1.0f ? wsn : v3s(lightDir, -1.0f);
    if (dot3(lightDir, wsn) <= 0.0f) { o.discarded = 1; return o; }
    v4 dirv = m4_mul_dir(invViewM, glsl_v3_div_s(v3m(vsp.x, vsp.y, vsp.z), vsp.w));
    v3 viewDir = v3s(glsl_normalize(v3m(dirv.x, dirv.y, dirv.z)), -1.0f);
    o.color = eaglercraftLighting_old(diffuse, color, viewDir, lightDir, wsn, material);
    return o;
}

PBR_FN frag_result lighting_point_new(const gbuf *g, const light *L, v3 lightColor) {
    frag_result o = {0, v3m(0.f, 0.f, 0.f)};
    float depth = g->depth;
    C_TEX();
    if (depth < 0.00001f) { o.discarded = 1; return o; }
    v4 ndc = {g->gl_FragCoord[0] / (float) W * 2.0f - 1.0f, g->gl_FragCoord[1] / (float) H * 2.0f - 1.0f,
              depth * 2.0f - 1.0f, 1.0f};
    v4 vsp = m4_mul_v4(invProjM, ndc);
    v4 wp = m4_mul_v4(invViewM, vsp);
    v3 lightDist = v3sub(glsl_v3_div_s(v3m(wp.x, wp.y, wp.z), wp.w), L->pos);
    float dist2 = dot3(lightDist, lightDist);
    v3 color = glsl_v3_div_s(lightColor, dist2);
    if (color.x + color.y + color.z < 0.025f) { o.discarded = 1; return o; }
    C_TEX(); /* u_gbufferNormalTexture */
    v3 normal = v3m(g->normal.x * 2.0f - 1.0f, g->normal.y * 2.0f - 1.0f, g->normal.z * 2.0f - 1.0f);
    v3 wsn = m3_mul_normalize(invViewM, normal);
    C_TEX(); /* u_gbufferMaterialTexture */
    v3 material = g->material;
    if (material.z != 1.0f && dot3(lightDist, wsn) >= 0.0f) { o.discarded = 1; return o; }
    v3 lightDir = material.z == 1.0f ? wsn : v3s(lightDist, -glsl_inversesqrt(dist2));
    v3 viewDir = v3s(m3_mul_normalize(invViewM, glsl_v3_div_s(v3m(vsp.x, vsp.y, vsp.z), vsp.w)), -1.0f);
    C_TEX(); /* u_gbufferColorTexture */
    v3 diffuse = v3mul(g->color, g->color);
    o.color = eaglercraftLighting_new(diffuse, color, viewDir, lightDir, wsn, material);
    return o;
}

/* ------------------------------------------------------------------ */
/* forward_core.fsh / accel_particle_forward.fsh dynamic light loop    */
/* ------------------------------------------------------------------ */

typedef struct { int kept; v3 lightColorSum; } loop_result;

static loop_result forward_loop_old(const gbuf *g, v3 worldPos, v3 normal, v3 material, v3 viewDir, int accel) {
    loop_result r = {0, v3m(0.f, 0.f, 0.f)};
    v3 diffuse = v3mul(g->color, g->color);
    for (int i = 0; i < nlights; ++i) {
        v3 d = v3sub(worldPos, lights[i].pos);
        v3 dirr = glsl_normalize(d);
        if (accel) {
            dirr = v3s(dirr, dot3(dirr, normal) < 0.0f ? 1.0f : -1.0f);
        }
        dirr = material.z == 1.0f ? normal : v3s(dirr, -1.0f);
        if (dot3(dirr, normal) <= 0.0f) continue;
        C_DIV4();
        v3 dcolor = v3m(lights[i].color.x / dot3(d, d), lights[i].color.y / dot3(d, d), lights[i].color.z / dot3(d, d));
        if (dcolor.x + dcolor.y + dcolor.z < 0.025f) continue;
        r.lightColorSum = v3add(r.lightColorSum, eaglercraftLighting_old(diffuse, dcolor, viewDir, dirr, normal, material));
        r.kept++;
    }
    return r;
}

static loop_result forward_loop_new(const gbuf *g, v3 worldPos, v3 normal, v3 material, v3 viewDir, int accel) {
    loop_result r = {0, v3m(0.f, 0.f, 0.f)};
    v3 diffuse = v3mul(g->color, g->color);
    for (int i = 0; i < nlights; ++i) {
        v3 d = v3sub(worldPos, lights[i].pos);
        float d2 = dot3(d, d);
        v3 radiance = lights[i].color;
        if (accel) {
            if (radiance.x + radiance.y + radiance.z < 0.025f * d2) continue;
            v3 u = v3s(d, glsl_inversesqrt(d2));
            u = v3s(u, dot3(u, normal) < 0.0f ? 1.0f : -1.0f);
            v3 dirr = material.z == 1.0f ? normal : v3s(u, -1.0f);
            if (dot3(dirr, normal) <= 0.0f) continue;
            C_DIV4();
            v3 dcolor = v3m(radiance.x / d2, radiance.y / d2, radiance.z / d2);
            r.lightColorSum = v3add(r.lightColorSum, eaglercraftLighting_new(diffuse, dcolor, viewDir, dirr, normal, material));
            r.kept++;
        } else {
            if (material.z != 1.0f && dot3(d, normal) >= 0.0f) continue;
            if (radiance.x + radiance.y + radiance.z < 0.025f * d2) continue;
            v3 dirr = material.z == 1.0f ? normal : v3s(d, -glsl_inversesqrt(d2));
            C_DIV4();
            v3 dcolor = v3m(radiance.x / d2, radiance.y / d2, radiance.z / d2);
            r.lightColorSum = v3add(r.lightColorSum, eaglercraftLighting_new(diffuse, dcolor, viewDir, dirr, normal, material));
            r.kept++;
        }
    }
    return r;
}

/* ------------------------------------------------------------------ */
/* equivalence helpers                                                */
/* ------------------------------------------------------------------ */

static int32_t f2i(float f) { int32_t i; memcpy(&i, &f, 4); return i; }
static int32_t ulp_diff(float a, float b) {
    if (a == b) return 0;
    if (isnan(a) || isnan(b)) return a == b ? 0 : INT32_MAX;
    int32_t ia = f2i(a), ib = f2i(b);
    if (ia < 0) ia = (int32_t) 0x80000000u - ia;
    if (ib < 0) ib = (int32_t) 0x80000000u - ib;
    int64_t d = (int64_t) ia - (int64_t) ib;
    return (int32_t) (d < 0 ? -d : d);
}

typedef struct {
    long long cases, exact, ulp1, over1e6, over1e5, over1e4;
    int32_t max_ulp;
    double max_abs, max_rel;
} cmp_stats;

static void cmp_reset(cmp_stats *c) { memset(c, 0, sizeof(*c)); }

static void cmp_v3(cmp_stats *c, v3 a, v3 b) {
    float av[3] = {a.x, a.y, a.z}, bv[3] = {b.x, b.y, b.z};
    for (int i = 0; i < 3; ++i) {
        c->cases++;
        int32_t ulp = ulp_diff(av[i], bv[i]);
        if (ulp == 0) c->exact++;
        else if (ulp == 1) c->ulp1++;
        if (ulp > c->max_ulp) c->max_ulp = ulp;
        double d = fabs((double) av[i] - (double) bv[i]);
        if (d > c->max_abs) c->max_abs = d;
        double scale = fabs((double) av[i]) > 1e-30 ? fabs((double) av[i]) : 1e-30;
        double rel = d / scale;
        if (rel > c->max_rel) c->max_rel = rel;
        if (rel > 1e-4) c->over1e4++;
        if (rel > 1e-5) c->over1e5++;
        if (rel > 1e-6) c->over1e6++;
    }
}

static void cmp_print(const char *what, const cmp_stats *c) {
    printf("  %-34s %lld value(s): %.4f%% bit-identical, %lld within 1 ULP, max %d ULP\n",
           what, c->cases, 100.0 * (double) c->exact / (double) (c->cases ? c->cases : 1),
           c->ulp1, c->max_ulp);
    printf("  %-34s max |diff| %.3e, max rel %.2e; rel > 1e-6: %lld, > 1e-5: %lld, > 1e-4: %lld\n",
           "", c->max_abs, c->max_rel, c->over1e6, c->over1e5, c->over1e4);
}

/* ---- "can the player see it?" checks: the lightingHDRFramebuffer is RGBA16F and its
   contents end up in an 8-bit sRGB framebuffer through post_tonemap.fsh's ACES curve ---- */

static float f32_to_f16_to_f32(float f) {
    uint32_t x; memcpy(&x, &f, 4);
    uint32_t sign = (x >> 16) & 0x8000u;
    int32_t exp = (int32_t) ((x >> 23) & 0xff) - 127;
    uint32_t man = x & 0x7fffffu;
    uint16_t h;
    if (exp > 15) h = (uint16_t) (sign | 0x7c00u);
    else if (exp < -14) h = (uint16_t) sign;
    else {
        uint32_t m = man >> 13;
        uint32_t round = man & 0x1fffu;
        h = (uint16_t) (sign | ((uint32_t) (exp + 15) << 10) | m);
        if (round > 0x1000u || (round == 0x1000u && (m & 1u))) h++;
    }
    uint32_t hs = ((uint32_t) (h & 0x8000u)) << 16;
    uint32_t he = (h >> 10) & 0x1fu;
    uint32_t hm = h & 0x3ffu;
    uint32_t out;
    if (he == 0x1fu) out = hs | 0x7f800000u | (hm << 13);
    else if (he == 0) out = hs;
    else out = hs | ((he - 15u + 127u) << 23) | (hm << 13);
    float r; memcpy(&r, &out, 4);
    return r;
}

/* post_tonemap.fsh: ACES approximation, per channel */
static float tonemap_aces(float x) {
    const float a = 1.22f, b = 1.78f, c = 1.22f, d = 1.79f, e = 0.29f;
    float v = (x * (a * x + b)) / (x * (c * x + d) + e);
    return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
}

static int out8(float x) {
    float v = tonemap_aces(x < 0.0f ? 0.0f : x);
    return (int) (v * 255.0f + 0.5f);
}

/* ------------------------------------------------------------------ */
/* timing                                                             */
/* ------------------------------------------------------------------ */

static double now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double) ts.tv_sec * 1e9 + (double) ts.tv_nsec;
}

static volatile double sink = 0.0;

#define REPS 5

typedef double (*frame_fn)(int);

static double bench(frame_fn fn, const char *label, double invocations_per_frame) {
    double t[REPS];
    for (int r = 0; r < REPS; ++r) {
        double a = now_ns();
        sink += fn(0);
        t[r] = (now_ns() - a) / invocations_per_frame; /* ns per shader invocation */
    }
    for (int i = 0; i < REPS; ++i)
        for (int j = i + 1; j < REPS; ++j)
            if (t[j] < t[i]) { double tmp = t[i]; t[i] = t[j]; t[j] = tmp; }
    printf("  %-34s %10.4f ns/invocation (median of %d)\n", label, t[REPS / 2], REPS);
    return t[REPS / 2];
}

/* ---- frame kernels ---- */

static long long covered_pixels(void) {
    long long c = 0;
    for (int i = 0; i < nlights; ++i)
        if (lights[i].covX1 >= lights[i].covX0)
            c += (long long) (lights[i].covX1 - lights[i].covX0 + 1) * (lights[i].covY1 - lights[i].covY0 + 1);
    return c;
}

static double frame_lighting_point_old(int unused) {
    (void) unused;
    double acc = 0.0;
    for (int li = 0; li < nlights; ++li) {
        const light *L = &lights[li];
        if (L->covX1 < L->covX0) continue;
        for (int y = L->covY0; y <= L->covY1; y += 1) {
            for (int x = L->covX0; x <= L->covX1; x += 1) {
                frag_result r = lighting_point_old(&frame[y * W + x], L, L->color);
                acc += (double) r.color.x + r.color.y + r.color.z + (r.discarded ? 0.5 : 0.0);
            }
        }
    }
    return acc;
}

static double frame_lighting_point_new(int unused) {
    (void) unused;
    double acc = 0.0;
    for (int li = 0; li < nlights; ++li) {
        const light *L = &lights[li];
        if (L->covX1 < L->covX0) continue;
        for (int y = L->covY0; y <= L->covY1; y += 1) {
            for (int x = L->covX0; x <= L->covX1; x += 1) {
                frag_result r = lighting_point_new(&frame[y * W + x], L, L->color);
                acc += (double) r.color.x + r.color.y + r.color.z + (r.discarded ? 0.5 : 0.0);
            }
        }
    }
    return acc;
}

/* entity fragments for the forward pass: 250k fragments spread over the frame */
#define ENT_FRAG 250000

static double frame_forward_old(int accel) {
    double acc = 0.0;
    for (int i = 0; i < ENT_FRAG; ++i) {
        const gbuf *g = &frame[(size_t) (i * 7919u) % NPIX];
        if (g->depth == 0.0f) continue;
        v3 worldPos = v3m(g->worldPos.x, g->worldPos.y + 0.4f, g->worldPos.z);
        v3 normal = glsl_normalize(v3m(0.f, 1.f, 0.f));
        loop_result r = forward_loop_old(g, worldPos, normal, g->material, v3m(0.f, 0.f, 1.f), accel);
        acc += (double) r.lightColorSum.x + r.lightColorSum.y + r.lightColorSum.z;
    }
    return acc;
}

static double frame_forward_new(int accel) {
    double acc = 0.0;
    for (int i = 0; i < ENT_FRAG; ++i) {
        const gbuf *g = &frame[(size_t) (i * 7919u) % NPIX];
        if (g->depth == 0.0f) continue;
        v3 worldPos = v3m(g->worldPos.x, g->worldPos.y + 0.4f, g->worldPos.z);
        v3 normal = glsl_normalize(v3m(0.f, 1.f, 0.f));
        loop_result r = forward_loop_new(g, worldPos, normal, g->material, v3m(0.f, 0.f, 1.f), accel);
        acc += (double) r.lightColorSum.x + r.lightColorSum.y + r.lightColorSum.z;
    }
    return acc;
}

static double frame_fwd_old(int unused) { (void) unused; return frame_forward_old(0); }
static double frame_fwd_new(int unused) { (void) unused; return frame_forward_new(0); }
static double frame_acc_old(int unused) { (void) unused; return frame_forward_old(1); }
static double frame_acc_new(int unused) { (void) unused; return frame_forward_new(1); }

/* ------------------------------------------------------------------ */
/* instruction-count fences (built with -DPBR_INSN, never run)         */
/* ------------------------------------------------------------------ */

#ifdef PBR_INSN
#define MARK(x) __asm__ __volatile__(x)

__attribute__((noinline)) v3 insn_lighting_old(v3 alb, v3 rad, v3 vd, v3 ld, v3 n, v3 m) {
    MARK("#LIGHTING_OLD_BEGIN");
    v3 r = eaglercraftLighting_old(alb, rad, vd, ld, n, m);
    MARK("#LIGHTING_OLD_END");
    return r;
}

__attribute__((noinline)) v3 insn_lighting_new(v3 alb, v3 rad, v3 vd, v3 ld, v3 n, v3 m) {
    MARK("#LIGHTING_NEW_BEGIN");
    v3 r = eaglercraftLighting_new(alb, rad, vd, ld, n, m);
    MARK("#LIGHTING_NEW_END");
    return r;
}

__attribute__((noinline)) frag_result insn_lp_old(const gbuf *g, const light *L) {
    MARK("#LP_OLD_BEGIN");
    frag_result r = lighting_point_old(g, L, L->color);
    MARK("#LP_OLD_END");
    return r;
}

__attribute__((noinline)) frag_result insn_lp_new(const gbuf *g, const light *L) {
    MARK("#LP_NEW_BEGIN");
    frag_result r = lighting_point_new(g, L, L->color);
    MARK("#LP_NEW_END");
    return r;
}
#endif

/* ------------------------------------------------------------------ */
/* main                                                               */
/* ------------------------------------------------------------------ */

int main(void) {
    build_frame();
    build_lights(8);
    printf("=== PBR shader hot-loop model (gcc %s, no -ffast-math) ===\n", __VERSION__);
    printf("frame: %dx%d gbuffer, %d dynamic lights, per-light mesh = projected sphere r=12\n", W, H, nlights);
    printf("covered light fragments/frame: %lld  (entity fragments modelled: %d)\n", covered_pixels(), ENT_FRAG);

    /* ---- equivalence ---- */
#ifndef PBR_CENSUS
    printf("\n-- equivalence (old vs new, identical inputs) --\n");
    {
        cmp_stats cl, ci;
        cmp_reset(&cl);
        cmp_reset(&ci);
        long long mask_mismatch_old = 0;
        long long total = 0, kept = 0, half_diff = 0, out8_diff = 0, out8_cases = 0;
        for (int li = 0; li < nlights; ++li) {
            const light *L = &lights[li];
            if (L->covX1 < L->covX0) continue;
            for (int y = L->covY0; y <= L->covY1; ++y) {
                for (int x = L->covX0; x <= L->covX1; ++x) {
                    const gbuf *g = &frame[y * W + x];
                    frag_result a = lighting_point_old(g, L, L->color);
                    frag_result b = lighting_point_new(g, L, L->color);
                    total++;
                    if (!a.discarded) kept++;
                    if (a.discarded != b.discarded) mask_mismatch_old++;
                    else if (!a.discarded) {
                        cmp_v3(&cl, a.color, b.color);
                        /* RGBA16F attachment, then post_tonemap -> 8-bit sRGB */
                        float av[3] = {a.color.x, a.color.y, a.color.z}, bv[3] = {b.color.x, b.color.y, b.color.z};
                        int differs_half = 0, differs_8 = 0;
                        for (int k = 0; k < 3; ++k) {
                            if (f32_to_f16_to_f32(av[k]) != f32_to_f16_to_f32(bv[k])) differs_half = 1;
                            if (out8(av[k]) != out8(bv[k])) differs_8 = 1;
                        }
                        if (differs_half) half_diff++;
                        if (differs_8) out8_diff++;
                        out8_cases++;
                    }
                }
            }
        }
        printf("  lighting_point.fsh: %lld fragments, %lld kept (%.1f%%), discard-mask mismatches: %lld\n",
               total, kept, 100.0 * (double) kept / (double) total, mask_mismatch_old);
        printf("  lighting_point.fsh: fragments whose RGBA16F value differs: %lld of %lld; whose 8-bit\n"
               "     post_tonemap output differs: %lld\n", half_diff, out8_cases, out8_diff);
        cmp_print("  lighting_point.fsh colour", &cl);

        /* forward_core + accel_particle_forward light loops */
        for (int accel = 0; accel < 2; ++accel) {
            cmp_stats cc;
            cmp_reset(&cc);
            long long mm = 0, cases = 0, contrib_old = 0, contrib_new = 0;
            long long lhalf = 0, l8 = 0;
            for (int i = 0; i < ENT_FRAG; ++i) {
                const gbuf *g = &frame[(size_t) (i * 7919u) % NPIX];
                if (g->depth == 0.0f) continue;
                v3 worldPos = v3m(g->worldPos.x, g->worldPos.y + 0.4f, g->worldPos.z);
                v3 normal = glsl_normalize(v3m(0.f, 1.f, 0.f));
                loop_result a = forward_loop_old(g, worldPos, normal, g->material, v3m(0.f, 0.f, 1.f), accel);
                loop_result b = forward_loop_new(g, worldPos, normal, g->material, v3m(0.f, 0.f, 1.f), accel);
                cases++;
                contrib_old += a.kept;
                contrib_new += b.kept;
                if (a.kept != b.kept) mm++;
                else {
                    cmp_v3(&cc, a.lightColorSum, b.lightColorSum);
                    float av[3] = {a.lightColorSum.x, a.lightColorSum.y, a.lightColorSum.z};
                    float bv[3] = {b.lightColorSum.x, b.lightColorSum.y, b.lightColorSum.z};
                    int dh = 0, d8 = 0;
                    for (int k = 0; k < 3; ++k) {
                        if (f32_to_f16_to_f32(av[k]) != f32_to_f16_to_f32(bv[k])) dh = 1;
                        if (out8(av[k]) != out8(bv[k])) d8 = 1;
                    }
                    lhalf += dh;
                    l8 += d8;
                }
            }
            printf("  %s: %lld fragments, lights kept %lld vs %lld, differing fragment counts: %lld\n",
                   accel ? "accel_particle_forward.fsh loop" : "forward_core.fsh light loop",
                   cases, contrib_old, contrib_new, mm);
            printf("  %s: fragments whose RGBA16F sum differs: %lld/%lld, 8-bit output differs: %lld\n",
                   "  ", lhalf, cases, l8);
            cmp_print("  accumulated lightColor3f", &cc);
        }

        /* adversarial: exactly-zero light distance, exact-zero dot products, threshold cases */
        {
            gbuf g = frame[(H / 2) * W + (W / 2)];
            int ok = 1;
            for (int k = 0; k < 3; ++k) {
                light L = lights[0];
                L.pos = g.worldPos; /* distance exactly 0: normalise() of a zero vector */
                frag_result a = lighting_point_old(&g, &L, L.color);
                frag_result b = lighting_point_new(&g, &L, L.color);
                if (a.discarded != b.discarded) ok = 0;
            }
            printf("  degenerate zero-distance fragments: %s (documented divergence: the new code\n"
                   "    discards them, the old code shaded them with NaN; visible only for a fragment\n"
                   "    exactly at the light centre)\n", ok ? "same discard decision" : "decision differs");

            /* randomised lighting-function inputs, including the material branch boundaries */
            cmp_stats cl2;
            cmp_reset(&cl2);
            uint32_t s2 = 999u;
            for (int i = 0; i < 200000; ++i) {
                v3 n = glsl_normalize(v3m(rnd01(&s2) * 2 - 1, rnd01(&s2) * 2 - 1, rnd01(&s2) * 2 - 1));
                v3 vd = glsl_normalize(v3m(rnd01(&s2) * 2 - 1, rnd01(&s2) * 2 - 1, rnd01(&s2) * 2 - 1));
                v3 ld = glsl_normalize(v3m(rnd01(&s2) * 2 - 1, rnd01(&s2) * 2 - 1, rnd01(&s2) * 2 - 1));
                v3 alb = v3m(rnd01(&s2), rnd01(&s2), rnd01(&s2));
                v3 rad = v3s(v3m(rnd01(&s2), rnd01(&s2), rnd01(&s2)), 8.0f);
                float gsel = (i % 7 == 0) ? 0.9f : (i % 11 == 0) ? 0.964f : (i % 13 == 0) ? 0.8999999f : rnd01(&s2);
                v3 mat = v3m(rnd01(&s2), gsel, 0.0f);
                cmp_v3(&cl2, eaglercraftLighting_old(alb, rad, vd, ld, n, mat), eaglercraftLighting_new(alb, rad, vd, ld, n, mat));
                cmp_v3(&cl2, ibl_specular_old(alb, rad, vd, n, mat), ibl_specular_new(alb, rad, vd, n, mat));
            }
            cmp_print("  eaglercraftLighting+IBL random", &cl2);
        }
    }
#endif /* !PBR_CENSUS */

    /* ---- op census ---- */
#ifdef PBR_CENSUS
    printf("\n-- op census per frame (pow pairs = SFU log2+exp2, tex = fetches) --\n");
    {
        double f = covered_pixels() ? (double) covered_pixels() : 1.0;
        census_reset();
        frame_lighting_point_old(0);
        census_print("lighting_point old", f);
        census_reset();
        frame_lighting_point_new(0);
        census_print("lighting_point new", f);
        double e = (double) ENT_FRAG;
        census_reset();
        frame_forward_old(0);
        census_print("forward_core old", e);
        census_reset();
        frame_forward_new(0);
        census_print("forward_core new", e);
        census_reset();
        frame_forward_old(1);
        census_print("accel_particle old", e);
        census_reset();
        frame_forward_new(1);
        census_print("accel_particle new", e);
    }
#else
    printf("\n-- op census: build with -DPBR_CENSUS (run_bench.sh builds both) --\n");
#endif

    /* ---- timing ---- */
#ifndef PBR_CENSUS
    printf("\n-- CPU replayed time (upper bound for the GPU; libm powf is much dearer than an SFU pow) --\n");
    {
        double lp = (double) covered_pixels();
        double a = bench(frame_lighting_point_old, "lighting_point old", lp);
        double b = bench(frame_lighting_point_new, "lighting_point new", lp);
        printf("  => lighting_point.fsh: %.3fx (%.1f%% of the light-fragment cost removed)\n", a / b, 100.0 * (a - b) / a);
        double fa = bench(frame_fwd_old, "forward_core loop old (8 lights)", (double) ENT_FRAG);
        double fb = bench(frame_fwd_new, "forward_core loop new (8 lights)", (double) ENT_FRAG);
        printf("  => forward_core.fsh light loop: %.3fx\n", fa / fb);
        double aa = bench(frame_acc_old, "accel_particle loop old (8 lights)", (double) ENT_FRAG);
        double ab = bench(frame_acc_new, "accel_particle loop new (8 lights)", (double) ENT_FRAG);
        printf("  => accel_particle_forward.fsh light loop: %.3fx\n", aa / ab);
    }
#endif /* !PBR_CENSUS */

    /* ---- sanity checksums (must match between kernels so nothing was folded away) ---- */
    printf("\nchecksums: lighting_point %.6f / %.6f | forward %.6f / %.6f | accel %.6f / %.6f\n",
           frame_lighting_point_old(0), frame_lighting_point_new(0), frame_forward_old(0),
           frame_forward_new(0), frame_forward_old(1), frame_forward_new(1));
    printf("sink=%.6f (keeps kernels live)\n", (double) sink);
    free(frame);
    return 0;
}
