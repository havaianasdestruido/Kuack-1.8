/* Native AOT proof of concept for the 256-byte-domain gamma decode.
 * This is NOT a JVM/native-image build of the game and NOT a JNI bridge. It isolates the same
 * exact mathematical subsystem in ahead-of-time compiled C to measure whether native code changes
 * the conclusion. Build: ./perfbench/native/build.sh
 */
#define _POSIX_C_SOURCE 200809L
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "gamma_table.h"

#define MAX_N 65536
#define REPS 15

static int input_bytes[MAX_N];
static double output_f64[MAX_N];
static volatile double sink = 0.0;

static inline double now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double) ts.tv_sec * 1e9 + (double) ts.tv_nsec;
}

static uint32_t bits(float f) {
    uint32_t u;
    memcpy(&u, &f, sizeof(u));
    return u;
}

/* Baseline: the exact libm expression, narrowed to float then promoted to double. */
__attribute__((noinline)) static double batch_pow(int n) {
    double sum = 0.0;
    for (int i = 0; i < n; ++i) {
        unsigned v = (unsigned) input_bytes[i] & 255u;
        float r = (float) pow((double) v / 255.0, 2.2);
        output_f64[i] = (double) r;
        sum += (double) r;
    }
    return sum;
}

/* Optimized: one load from .rodata, no libm call, no allocation, no runtime init. */
__attribute__((noinline)) static double batch_lut(int n) {
    double sum = 0.0;
    for (int i = 0; i < n; ++i) {
        unsigned v = (unsigned) input_bytes[i] & 255u;
        double r = (double) GAMMA_2_2[v];
        output_f64[i] = r;
        sum += r;
    }
    return sum;
}

static int cmp_double(const void *a, const void *b) {
    double x = *(const double *) a, y = *(const double *) b;
    return (x > y) - (x < y);
}

typedef struct { double median, p95, p99; } stats_t;

static stats_t bench(double (*fn)(int), int n) {
    double t[REPS];
    for (int r = 0; r < REPS; ++r) {
        double a = now_ns();
        sink += fn(n);
        t[r] = (now_ns() - a) / (double) n;
    }
    qsort(t, REPS, sizeof(double), cmp_double);
    stats_t s;
    s.median = t[REPS / 2];
    s.p95 = t[(int) (0.95 * (REPS - 1))];
    s.p99 = t[(int) (0.99 * (REPS - 1))];
    return s;
}

int main(void) {
    printf("=== Native/AOT C prototype: gamma decode (gcc -O3 -march=native) ===\n");
    printf("table: 256 f32 constants = 1024 bytes in .rodata; no runtime initializer\n");
    printf("gamma_table.h and bench_gamma.c source sizes, binary size: reported by build.sh\n");

    /* deterministic sequential, LCG-random and adversarial negative/out-of-range inputs */
    for (int i = 0; i < MAX_N; ++i) input_bytes[i] = i & 255;
    uint32_t seed = 0x5EEDBEEFu;
    for (int i = 0; i < MAX_N; ++i) {
        seed = seed * 1664525u + 1013904223u;
        input_bytes[i] = (int) (seed & 255u);
    }
    for (int i = 0; i < 256; ++i) input_bytes[MAX_N - 256 + i] = i - 128;

    int checks = 0, failures = 0;
    for (int v = -256; v <= 512; ++v) {
        ++checks;
        unsigned m = (unsigned) v & 255u;
        float from_pow = (float) pow((double) m / 255.0, 2.2);
        if (bits(from_pow) != bits(GAMMA_2_2[m])) ++failures;
        ++checks;
        if ((double) from_pow != (double) GAMMA_2_2[m]) ++failures;
    }
    printf("equivalence: %d checks, %d mismatches (negative, zero, max, out-of-range included)\n", checks, failures);
    if (failures != 0) return 1;

    /* First invocation after process start: table has no clinit; loader has already mapped .rodata. */
    double t0 = now_ns();
    sink += batch_lut(1);
    printf("first batch_lut(1): %.3f us\n", (now_ns() - t0) / 1000.0);

    printf("\n-- coarse batch, steady state (median of %d; p95/p99 in ns/op) --\n", REPS);
    const int sizes[] = {256, 4096, 65536};
    for (unsigned k = 0; k < sizeof(sizes) / sizeof(sizes[0]); ++k) {
        int n = sizes[k];
        double sp = batch_pow(n);
        static double pow_out[MAX_N];
        memcpy(pow_out, output_f64, sizeof(double) * (size_t) n);
        double sl = batch_lut(n);
        if (sp != sl) { printf("FAILED: checksum mismatch at n=%d\n", n); return 1; }
        for (int i = 0; i < n; ++i) {
            if (pow_out[i] != output_f64[i]) { printf("FAILED: output mismatch at %d\n", i); return 1; }
        }
        stats_t a = bench(batch_pow, n), b = bench(batch_lut, n);
        printf("  n=%5d  pow %8.3f ns/op (p95 %8.3f / p99 %8.3f)   lut %8.3f ns/op (p95 %8.3f / p99 %8.3f)   %.2fx\n",
               n, a.median, a.p95, a.p99, b.median, b.p95, b.p99, a.median / b.median);
    }
    printf("\nOK: native pow and LUT outputs are bit-identical.\n");
    printf("Whole-game native JVM launch, JNI/Panama bridge, GraalVM Native Image: UNAVAILABLE (see CROSS_RUNTIME.md).\n");
    printf("sink=%f (keeps kernels live)\n", (double) sink);
    return 0;
}
