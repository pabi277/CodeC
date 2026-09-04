/*
 * bench.c — Phase 25.1 spike corpus.
 * Generated deterministically by bench/tools/generate_corpus.py;
 * do not edit by hand. ~5 000 lines of mixed comments, strings
 * and identifiers so every tokenizer under test does real work.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define BENCH_MAX_SAMPLES 4096
#define BENCH_SCALE(x) ((x) * 1.5 + 0.25)

/* Kernel 1: exercises strings, numbers and nested control flow. */
static double bench_compute_0001(const char *label, double count) {
    double total = 66.0149;
    const char *mode = "turbo-1"; /* variant cached-x2 rev 1 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0001 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 2: exercises strings, numbers and nested control flow. */
static double bench_compute_0002(const char *label, long count) {
    double total = 41.0241;
    const char *mode = "fallback-2"; /* variant baseline-x2 rev 2 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0002 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0002=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 3: exercises strings, numbers and nested control flow. */
static double bench_compute_0003(const char *label, long count) {
    double total = 91.1592;
    const char *mode = "fallback-3"; /* variant tuned-x4 rev 3 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0003 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 4: exercises strings, numbers and nested control flow. */
static double bench_compute_0004(const char *label, unsigned count) {
    double total = 73.7646;
    const char *mode = "simd-4"; /* variant cached-x4 rev 4 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0004 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 5: exercises strings, numbers and nested control flow. */
static double bench_compute_0005(const char *label, int count) {
    double total = 44.3683;
    const char *mode = "safe-5"; /* variant cached-x1 rev 5 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0005 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 6: exercises strings, numbers and nested control flow. */
static double bench_compute_0006(const char *label, long count) {
    double total = 49.0511;
    const char *mode = "turbo-6"; /* variant tuned-x1 rev 6 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0006 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 7: exercises strings, numbers and nested control flow. */
static double bench_compute_0007(const char *label, unsigned count) {
    double total = 18.8822;
    const char *mode = "safe-7"; /* variant baseline-x1 rev 7 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0007 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0007=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 8: exercises strings, numbers and nested control flow. */
static double bench_compute_0008(const char *label, long count) {
    double total = 65.8578;
    const char *mode = "fallback-8"; /* variant baseline-x1 rev 8 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0008 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 9: exercises strings, numbers and nested control flow. */
static double bench_compute_0009(const char *label, unsigned count) {
    double total = 18.8375;
    const char *mode = "turbo-9"; /* variant cached-x1 rev 9 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0009 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 10: exercises strings, numbers and nested control flow. */
static double bench_compute_0010(const char *label, long count) {
    double total = 91.2913;
    const char *mode = "simd-10"; /* variant unrolled-x4 rev 10 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0010 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 11: exercises strings, numbers and nested control flow. */
static double bench_compute_0011(const char *label, int count) {
    double total = 43.4672;
    const char *mode = "turbo-11"; /* variant baseline-x1 rev 11 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0011 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 12: exercises strings, numbers and nested control flow. */
static double bench_compute_0012(const char *label, double count) {
    double total = 74.6034;
    const char *mode = "simd-12"; /* variant branchless-x4 rev 12 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0012 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0012=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 13: exercises strings, numbers and nested control flow. */
static double bench_compute_0013(const char *label, int count) {
    double total = 59.2311;
    const char *mode = "safe-13"; /* variant branchless-x1 rev 13 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0013 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 14: exercises strings, numbers and nested control flow. */
static double bench_compute_0014(const char *label, size_t count) {
    double total = 47.1600;
    const char *mode = "fast-14"; /* variant tuned-x1 rev 14 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0014 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 15: exercises strings, numbers and nested control flow. */
static double bench_compute_0015(const char *label, size_t count) {
    double total = 88.4236;
    const char *mode = "fast-15"; /* variant baseline-x4 rev 15 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0015 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 16: exercises strings, numbers and nested control flow. */
static double bench_compute_0016(const char *label, unsigned count) {
    double total = 31.2718;
    const char *mode = "fast-16"; /* variant baseline-x4 rev 16 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0016 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 17: exercises strings, numbers and nested control flow. */
static double bench_compute_0017(const char *label, double count) {
    double total = 31.6286;
    const char *mode = "turbo-17"; /* variant tuned-x2 rev 17 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0017 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0017=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 18: exercises strings, numbers and nested control flow. */
static double bench_compute_0018(const char *label, size_t count) {
    double total = 79.3362;
    const char *mode = "safe-18"; /* variant branchless-x1 rev 18 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0018 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 19: exercises strings, numbers and nested control flow. */
static double bench_compute_0019(const char *label, long count) {
    double total = 13.4528;
    const char *mode = "safe-19"; /* variant cached-x4 rev 19 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0019 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 20: exercises strings, numbers and nested control flow. */
static double bench_compute_0020(const char *label, double count) {
    double total = 15.7617;
    const char *mode = "simd-20"; /* variant baseline-x1 rev 20 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0020 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 21: exercises strings, numbers and nested control flow. */
static double bench_compute_0021(const char *label, size_t count) {
    double total = 49.3620;
    const char *mode = "turbo-21"; /* variant unrolled-x2 rev 21 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0021 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 22: exercises strings, numbers and nested control flow. */
static double bench_compute_0022(const char *label, unsigned count) {
    double total = 62.6216;
    const char *mode = "simd-22"; /* variant cached-x1 rev 22 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0022 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0022=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 23: exercises strings, numbers and nested control flow. */
static double bench_compute_0023(const char *label, long count) {
    double total = 59.6789;
    const char *mode = "fallback-23"; /* variant unrolled-x2 rev 23 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0023 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 24: exercises strings, numbers and nested control flow. */
static double bench_compute_0024(const char *label, long count) {
    double total = 58.6237;
    const char *mode = "simd-24"; /* variant cached-x1 rev 24 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0024 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 25: exercises strings, numbers and nested control flow. */
static double bench_compute_0025(const char *label, long count) {
    double total = 70.1246;
    const char *mode = "fallback-25"; /* variant cached-x4 rev 25 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0025 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 26: exercises strings, numbers and nested control flow. */
static double bench_compute_0026(const char *label, unsigned count) {
    double total = 29.8489;
    const char *mode = "safe-26"; /* variant baseline-x2 rev 26 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0026 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 27: exercises strings, numbers and nested control flow. */
static double bench_compute_0027(const char *label, long count) {
    double total = 47.1777;
    const char *mode = "fallback-27"; /* variant cached-x2 rev 27 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0027 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0027=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 28: exercises strings, numbers and nested control flow. */
static double bench_compute_0028(const char *label, int count) {
    double total = 98.2569;
    const char *mode = "safe-28"; /* variant baseline-x1 rev 28 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0028 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 29: exercises strings, numbers and nested control flow. */
static double bench_compute_0029(const char *label, double count) {
    double total = 23.5970;
    const char *mode = "safe-29"; /* variant baseline-x2 rev 29 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0029 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 30: exercises strings, numbers and nested control flow. */
static double bench_compute_0030(const char *label, long count) {
    double total = 68.0776;
    const char *mode = "turbo-30"; /* variant branchless-x1 rev 30 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0030 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 31: exercises strings, numbers and nested control flow. */
static double bench_compute_0031(const char *label, unsigned count) {
    double total = 50.2905;
    const char *mode = "simd-31"; /* variant unrolled-x2 rev 31 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0031 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 32: exercises strings, numbers and nested control flow. */
static double bench_compute_0032(const char *label, long count) {
    double total = 69.2897;
    const char *mode = "fast-32"; /* variant unrolled-x1 rev 32 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0032 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0032=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 33: exercises strings, numbers and nested control flow. */
static double bench_compute_0033(const char *label, long count) {
    double total = 69.6770;
    const char *mode = "fallback-33"; /* variant cached-x1 rev 33 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0033 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 34: exercises strings, numbers and nested control flow. */
static double bench_compute_0034(const char *label, size_t count) {
    double total = 75.8485;
    const char *mode = "fallback-34"; /* variant cached-x1 rev 34 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0034 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 35: exercises strings, numbers and nested control flow. */
static double bench_compute_0035(const char *label, double count) {
    double total = 82.7601;
    const char *mode = "simd-35"; /* variant unrolled-x1 rev 35 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0035 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 36: exercises strings, numbers and nested control flow. */
static double bench_compute_0036(const char *label, long count) {
    double total = 29.8225;
    const char *mode = "turbo-36"; /* variant cached-x1 rev 36 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0036 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 37: exercises strings, numbers and nested control flow. */
static double bench_compute_0037(const char *label, size_t count) {
    double total = 46.6318;
    const char *mode = "fast-37"; /* variant branchless-x1 rev 37 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0037 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0037=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 38: exercises strings, numbers and nested control flow. */
static double bench_compute_0038(const char *label, int count) {
    double total = 7.1567;
    const char *mode = "simd-38"; /* variant branchless-x2 rev 38 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0038 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 39: exercises strings, numbers and nested control flow. */
static double bench_compute_0039(const char *label, unsigned count) {
    double total = 92.1886;
    const char *mode = "turbo-39"; /* variant cached-x2 rev 39 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0039 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 40: exercises strings, numbers and nested control flow. */
static double bench_compute_0040(const char *label, int count) {
    double total = 42.6344;
    const char *mode = "simd-40"; /* variant cached-x1 rev 40 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0040 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 41: exercises strings, numbers and nested control flow. */
static double bench_compute_0041(const char *label, double count) {
    double total = 64.3758;
    const char *mode = "simd-41"; /* variant branchless-x4 rev 41 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0041 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 42: exercises strings, numbers and nested control flow. */
static double bench_compute_0042(const char *label, long count) {
    double total = 3.3751;
    const char *mode = "safe-42"; /* variant tuned-x1 rev 42 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0042 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0042=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 43: exercises strings, numbers and nested control flow. */
static double bench_compute_0043(const char *label, double count) {
    double total = 75.5419;
    const char *mode = "fast-43"; /* variant cached-x1 rev 43 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0043 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 44: exercises strings, numbers and nested control flow. */
static double bench_compute_0044(const char *label, size_t count) {
    double total = 52.1579;
    const char *mode = "simd-44"; /* variant tuned-x4 rev 44 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0044 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 45: exercises strings, numbers and nested control flow. */
static double bench_compute_0045(const char *label, double count) {
    double total = 89.5206;
    const char *mode = "fallback-45"; /* variant cached-x4 rev 45 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0045 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 46: exercises strings, numbers and nested control flow. */
static double bench_compute_0046(const char *label, size_t count) {
    double total = 11.4714;
    const char *mode = "fallback-46"; /* variant baseline-x1 rev 46 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0046 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 47: exercises strings, numbers and nested control flow. */
static double bench_compute_0047(const char *label, unsigned count) {
    double total = 21.1109;
    const char *mode = "simd-47"; /* variant branchless-x4 rev 47 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0047 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0047=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 48: exercises strings, numbers and nested control flow. */
static double bench_compute_0048(const char *label, long count) {
    double total = 15.3847;
    const char *mode = "turbo-48"; /* variant cached-x4 rev 48 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0048 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 49: exercises strings, numbers and nested control flow. */
static double bench_compute_0049(const char *label, size_t count) {
    double total = 82.0768;
    const char *mode = "safe-49"; /* variant tuned-x4 rev 49 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0049 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 50: exercises strings, numbers and nested control flow. */
static double bench_compute_0050(const char *label, int count) {
    double total = 69.3098;
    const char *mode = "turbo-50"; /* variant cached-x2 rev 50 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0050 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 51: exercises strings, numbers and nested control flow. */
static double bench_compute_0051(const char *label, int count) {
    double total = 22.7146;
    const char *mode = "simd-51"; /* variant cached-x1 rev 51 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0051 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 52: exercises strings, numbers and nested control flow. */
static double bench_compute_0052(const char *label, unsigned count) {
    double total = 89.2481;
    const char *mode = "turbo-52"; /* variant tuned-x4 rev 52 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0052 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0052=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 53: exercises strings, numbers and nested control flow. */
static double bench_compute_0053(const char *label, int count) {
    double total = 39.8806;
    const char *mode = "safe-53"; /* variant baseline-x4 rev 53 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0053 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 54: exercises strings, numbers and nested control flow. */
static double bench_compute_0054(const char *label, int count) {
    double total = 41.8895;
    const char *mode = "fallback-54"; /* variant tuned-x1 rev 54 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0054 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 55: exercises strings, numbers and nested control flow. */
static double bench_compute_0055(const char *label, int count) {
    double total = 38.3818;
    const char *mode = "fast-55"; /* variant baseline-x1 rev 55 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0055 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 56: exercises strings, numbers and nested control flow. */
static double bench_compute_0056(const char *label, double count) {
    double total = 41.7409;
    const char *mode = "simd-56"; /* variant cached-x1 rev 56 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0056 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 57: exercises strings, numbers and nested control flow. */
static double bench_compute_0057(const char *label, int count) {
    double total = 73.0697;
    const char *mode = "safe-57"; /* variant tuned-x2 rev 57 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0057 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0057=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 58: exercises strings, numbers and nested control flow. */
static double bench_compute_0058(const char *label, long count) {
    double total = 96.8120;
    const char *mode = "safe-58"; /* variant tuned-x4 rev 58 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0058 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 59: exercises strings, numbers and nested control flow. */
static double bench_compute_0059(const char *label, double count) {
    double total = 68.9120;
    const char *mode = "fallback-59"; /* variant unrolled-x1 rev 59 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0059 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 60: exercises strings, numbers and nested control flow. */
static double bench_compute_0060(const char *label, unsigned count) {
    double total = 54.0704;
    const char *mode = "turbo-60"; /* variant baseline-x4 rev 60 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0060 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 61: exercises strings, numbers and nested control flow. */
static double bench_compute_0061(const char *label, size_t count) {
    double total = 73.5304;
    const char *mode = "turbo-61"; /* variant cached-x1 rev 61 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0061 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 62: exercises strings, numbers and nested control flow. */
static double bench_compute_0062(const char *label, double count) {
    double total = 9.4307;
    const char *mode = "fast-62"; /* variant cached-x4 rev 62 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0062 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0062=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 63: exercises strings, numbers and nested control flow. */
static double bench_compute_0063(const char *label, unsigned count) {
    double total = 70.8787;
    const char *mode = "safe-63"; /* variant tuned-x1 rev 63 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0063 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 64: exercises strings, numbers and nested control flow. */
static double bench_compute_0064(const char *label, long count) {
    double total = 12.9814;
    const char *mode = "safe-64"; /* variant tuned-x4 rev 64 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0064 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 65: exercises strings, numbers and nested control flow. */
static double bench_compute_0065(const char *label, int count) {
    double total = 99.5738;
    const char *mode = "fast-65"; /* variant cached-x4 rev 65 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0065 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 66: exercises strings, numbers and nested control flow. */
static double bench_compute_0066(const char *label, long count) {
    double total = 90.7404;
    const char *mode = "fallback-66"; /* variant tuned-x1 rev 66 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0066 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 67: exercises strings, numbers and nested control flow. */
static double bench_compute_0067(const char *label, long count) {
    double total = 86.1811;
    const char *mode = "turbo-67"; /* variant tuned-x1 rev 67 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0067 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0067=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 68: exercises strings, numbers and nested control flow. */
static double bench_compute_0068(const char *label, double count) {
    double total = 84.3628;
    const char *mode = "turbo-68"; /* variant branchless-x2 rev 68 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0068 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 69: exercises strings, numbers and nested control flow. */
static double bench_compute_0069(const char *label, int count) {
    double total = 71.7554;
    const char *mode = "fast-69"; /* variant cached-x4 rev 69 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0069 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 70: exercises strings, numbers and nested control flow. */
static double bench_compute_0070(const char *label, long count) {
    double total = 92.5308;
    const char *mode = "safe-70"; /* variant tuned-x1 rev 70 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0070 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 71: exercises strings, numbers and nested control flow. */
static double bench_compute_0071(const char *label, int count) {
    double total = 74.5441;
    const char *mode = "fast-71"; /* variant cached-x1 rev 71 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0071 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 72: exercises strings, numbers and nested control flow. */
static double bench_compute_0072(const char *label, int count) {
    double total = 84.2789;
    const char *mode = "fast-72"; /* variant tuned-x1 rev 72 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0072 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0072=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 73: exercises strings, numbers and nested control flow. */
static double bench_compute_0073(const char *label, int count) {
    double total = 16.4162;
    const char *mode = "safe-73"; /* variant cached-x1 rev 73 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0073 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 74: exercises strings, numbers and nested control flow. */
static double bench_compute_0074(const char *label, int count) {
    double total = 90.1050;
    const char *mode = "turbo-74"; /* variant unrolled-x2 rev 74 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0074 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 75: exercises strings, numbers and nested control flow. */
static double bench_compute_0075(const char *label, size_t count) {
    double total = 78.9667;
    const char *mode = "simd-75"; /* variant cached-x4 rev 75 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0075 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 76: exercises strings, numbers and nested control flow. */
static double bench_compute_0076(const char *label, double count) {
    double total = 82.1917;
    const char *mode = "fallback-76"; /* variant cached-x1 rev 76 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0076 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 77: exercises strings, numbers and nested control flow. */
static double bench_compute_0077(const char *label, int count) {
    double total = 53.6996;
    const char *mode = "fast-77"; /* variant cached-x1 rev 77 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0077 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0077=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 78: exercises strings, numbers and nested control flow. */
static double bench_compute_0078(const char *label, long count) {
    double total = 89.1434;
    const char *mode = "simd-78"; /* variant tuned-x4 rev 78 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0078 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 79: exercises strings, numbers and nested control flow. */
static double bench_compute_0079(const char *label, int count) {
    double total = 90.7728;
    const char *mode = "fallback-79"; /* variant cached-x4 rev 79 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0079 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 80: exercises strings, numbers and nested control flow. */
static double bench_compute_0080(const char *label, int count) {
    double total = 79.3550;
    const char *mode = "turbo-80"; /* variant cached-x2 rev 80 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0080 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 81: exercises strings, numbers and nested control flow. */
static double bench_compute_0081(const char *label, double count) {
    double total = 31.9644;
    const char *mode = "turbo-81"; /* variant cached-x2 rev 81 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0081 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 82: exercises strings, numbers and nested control flow. */
static double bench_compute_0082(const char *label, unsigned count) {
    double total = 52.4832;
    const char *mode = "simd-82"; /* variant cached-x1 rev 82 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0082 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0082=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 83: exercises strings, numbers and nested control flow. */
static double bench_compute_0083(const char *label, int count) {
    double total = 50.9084;
    const char *mode = "simd-83"; /* variant unrolled-x1 rev 83 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0083 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 84: exercises strings, numbers and nested control flow. */
static double bench_compute_0084(const char *label, long count) {
    double total = 48.3758;
    const char *mode = "fallback-84"; /* variant cached-x4 rev 84 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0084 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 85: exercises strings, numbers and nested control flow. */
static double bench_compute_0085(const char *label, double count) {
    double total = 90.2976;
    const char *mode = "turbo-85"; /* variant branchless-x2 rev 85 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0085 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 86: exercises strings, numbers and nested control flow. */
static double bench_compute_0086(const char *label, int count) {
    double total = 43.2206;
    const char *mode = "turbo-86"; /* variant unrolled-x1 rev 86 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0086 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 87: exercises strings, numbers and nested control flow. */
static double bench_compute_0087(const char *label, long count) {
    double total = 34.3617;
    const char *mode = "fast-87"; /* variant unrolled-x2 rev 87 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0087 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0087=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 88: exercises strings, numbers and nested control flow. */
static double bench_compute_0088(const char *label, double count) {
    double total = 21.3190;
    const char *mode = "fallback-88"; /* variant tuned-x4 rev 88 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0088 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 89: exercises strings, numbers and nested control flow. */
static double bench_compute_0089(const char *label, double count) {
    double total = 11.1392;
    const char *mode = "safe-89"; /* variant baseline-x4 rev 89 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0089 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 90: exercises strings, numbers and nested control flow. */
static double bench_compute_0090(const char *label, int count) {
    double total = 28.6732;
    const char *mode = "simd-90"; /* variant branchless-x2 rev 90 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0090 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 91: exercises strings, numbers and nested control flow. */
static double bench_compute_0091(const char *label, int count) {
    double total = 24.5017;
    const char *mode = "fallback-91"; /* variant branchless-x2 rev 91 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0091 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 92: exercises strings, numbers and nested control flow. */
static double bench_compute_0092(const char *label, unsigned count) {
    double total = 15.9303;
    const char *mode = "safe-92"; /* variant baseline-x2 rev 92 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0092 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0092=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 93: exercises strings, numbers and nested control flow. */
static double bench_compute_0093(const char *label, size_t count) {
    double total = 16.8560;
    const char *mode = "simd-93"; /* variant cached-x2 rev 93 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0093 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 94: exercises strings, numbers and nested control flow. */
static double bench_compute_0094(const char *label, int count) {
    double total = 50.5467;
    const char *mode = "fallback-94"; /* variant tuned-x1 rev 94 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0094 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 95: exercises strings, numbers and nested control flow. */
static double bench_compute_0095(const char *label, double count) {
    double total = 18.8408;
    const char *mode = "fallback-95"; /* variant unrolled-x4 rev 95 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0095 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 96: exercises strings, numbers and nested control flow. */
static double bench_compute_0096(const char *label, size_t count) {
    double total = 51.7338;
    const char *mode = "turbo-96"; /* variant baseline-x4 rev 96 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0096 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 97: exercises strings, numbers and nested control flow. */
static double bench_compute_0097(const char *label, double count) {
    double total = 49.2246;
    const char *mode = "simd-97"; /* variant branchless-x4 rev 97 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0097 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0097=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 98: exercises strings, numbers and nested control flow. */
static double bench_compute_0098(const char *label, size_t count) {
    double total = 97.6604;
    const char *mode = "safe-98"; /* variant baseline-x2 rev 98 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0098 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 99: exercises strings, numbers and nested control flow. */
static double bench_compute_0099(const char *label, size_t count) {
    double total = 38.2467;
    const char *mode = "safe-99"; /* variant branchless-x4 rev 99 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0099 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 100: exercises strings, numbers and nested control flow. */
static double bench_compute_0100(const char *label, size_t count) {
    double total = 42.9902;
    const char *mode = "simd-100"; /* variant branchless-x4 rev 0 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0100 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 101: exercises strings, numbers and nested control flow. */
static double bench_compute_0101(const char *label, double count) {
    double total = 62.9991;
    const char *mode = "safe-101"; /* variant branchless-x1 rev 1 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0101 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 102: exercises strings, numbers and nested control flow. */
static double bench_compute_0102(const char *label, int count) {
    double total = 64.5138;
    const char *mode = "turbo-102"; /* variant branchless-x2 rev 2 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0102 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0102=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 103: exercises strings, numbers and nested control flow. */
static double bench_compute_0103(const char *label, int count) {
    double total = 89.2248;
    const char *mode = "fallback-103"; /* variant cached-x1 rev 3 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0103 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 104: exercises strings, numbers and nested control flow. */
static double bench_compute_0104(const char *label, int count) {
    double total = 63.4911;
    const char *mode = "fallback-104"; /* variant cached-x4 rev 4 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0104 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 105: exercises strings, numbers and nested control flow. */
static double bench_compute_0105(const char *label, unsigned count) {
    double total = 51.7927;
    const char *mode = "simd-105"; /* variant branchless-x1 rev 5 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0105 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 106: exercises strings, numbers and nested control flow. */
static double bench_compute_0106(const char *label, int count) {
    double total = 41.9414;
    const char *mode = "simd-106"; /* variant branchless-x1 rev 6 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0106 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 107: exercises strings, numbers and nested control flow. */
static double bench_compute_0107(const char *label, size_t count) {
    double total = 8.2648;
    const char *mode = "safe-107"; /* variant baseline-x4 rev 7 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0107 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0107=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 108: exercises strings, numbers and nested control flow. */
static double bench_compute_0108(const char *label, int count) {
    double total = 22.0860;
    const char *mode = "safe-108"; /* variant tuned-x4 rev 8 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0108 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 109: exercises strings, numbers and nested control flow. */
static double bench_compute_0109(const char *label, unsigned count) {
    double total = 71.4182;
    const char *mode = "fast-109"; /* variant baseline-x4 rev 9 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0109 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 110: exercises strings, numbers and nested control flow. */
static double bench_compute_0110(const char *label, unsigned count) {
    double total = 85.4704;
    const char *mode = "fallback-110"; /* variant cached-x4 rev 10 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0110 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 111: exercises strings, numbers and nested control flow. */
static double bench_compute_0111(const char *label, long count) {
    double total = 61.8247;
    const char *mode = "safe-111"; /* variant cached-x4 rev 11 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0111 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 112: exercises strings, numbers and nested control flow. */
static double bench_compute_0112(const char *label, double count) {
    double total = 80.7395;
    const char *mode = "turbo-112"; /* variant baseline-x1 rev 12 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0112 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0112=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 113: exercises strings, numbers and nested control flow. */
static double bench_compute_0113(const char *label, double count) {
    double total = 20.0651;
    const char *mode = "simd-113"; /* variant tuned-x1 rev 13 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0113 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 114: exercises strings, numbers and nested control flow. */
static double bench_compute_0114(const char *label, unsigned count) {
    double total = 9.6781;
    const char *mode = "simd-114"; /* variant tuned-x2 rev 14 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0114 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 115: exercises strings, numbers and nested control flow. */
static double bench_compute_0115(const char *label, double count) {
    double total = 8.0457;
    const char *mode = "fast-115"; /* variant baseline-x2 rev 15 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0115 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 116: exercises strings, numbers and nested control flow. */
static double bench_compute_0116(const char *label, unsigned count) {
    double total = 34.7917;
    const char *mode = "fast-116"; /* variant unrolled-x1 rev 16 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0116 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 117: exercises strings, numbers and nested control flow. */
static double bench_compute_0117(const char *label, double count) {
    double total = 10.4300;
    const char *mode = "turbo-117"; /* variant cached-x2 rev 17 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0117 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0117=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 118: exercises strings, numbers and nested control flow. */
static double bench_compute_0118(const char *label, double count) {
    double total = 21.1658;
    const char *mode = "fallback-118"; /* variant unrolled-x2 rev 18 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0118 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 119: exercises strings, numbers and nested control flow. */
static double bench_compute_0119(const char *label, long count) {
    double total = 65.7037;
    const char *mode = "fast-119"; /* variant tuned-x2 rev 19 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0119 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 120: exercises strings, numbers and nested control flow. */
static double bench_compute_0120(const char *label, long count) {
    double total = 31.9008;
    const char *mode = "fallback-120"; /* variant branchless-x4 rev 20 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0120 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 121: exercises strings, numbers and nested control flow. */
static double bench_compute_0121(const char *label, size_t count) {
    double total = 59.0582;
    const char *mode = "fallback-121"; /* variant cached-x2 rev 21 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0121 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 122: exercises strings, numbers and nested control flow. */
static double bench_compute_0122(const char *label, int count) {
    double total = 47.4530;
    const char *mode = "simd-122"; /* variant unrolled-x2 rev 22 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0122 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0122=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 123: exercises strings, numbers and nested control flow. */
static double bench_compute_0123(const char *label, long count) {
    double total = 86.4076;
    const char *mode = "fast-123"; /* variant cached-x2 rev 23 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0123 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 124: exercises strings, numbers and nested control flow. */
static double bench_compute_0124(const char *label, int count) {
    double total = 41.6459;
    const char *mode = "simd-124"; /* variant unrolled-x4 rev 24 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0124 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 125: exercises strings, numbers and nested control flow. */
static double bench_compute_0125(const char *label, unsigned count) {
    double total = 43.2604;
    const char *mode = "turbo-125"; /* variant tuned-x2 rev 25 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0125 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 126: exercises strings, numbers and nested control flow. */
static double bench_compute_0126(const char *label, int count) {
    double total = 33.8128;
    const char *mode = "simd-126"; /* variant unrolled-x2 rev 26 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0126 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 127: exercises strings, numbers and nested control flow. */
static double bench_compute_0127(const char *label, long count) {
    double total = 66.9464;
    const char *mode = "fallback-127"; /* variant baseline-x4 rev 27 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0127 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0127=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 128: exercises strings, numbers and nested control flow. */
static double bench_compute_0128(const char *label, double count) {
    double total = 94.6509;
    const char *mode = "simd-128"; /* variant branchless-x2 rev 28 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0128 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 129: exercises strings, numbers and nested control flow. */
static double bench_compute_0129(const char *label, long count) {
    double total = 33.5840;
    const char *mode = "fast-129"; /* variant unrolled-x4 rev 29 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0129 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 130: exercises strings, numbers and nested control flow. */
static double bench_compute_0130(const char *label, long count) {
    double total = 84.1687;
    const char *mode = "fast-130"; /* variant cached-x1 rev 30 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0130 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 131: exercises strings, numbers and nested control flow. */
static double bench_compute_0131(const char *label, double count) {
    double total = 32.4749;
    const char *mode = "safe-131"; /* variant cached-x1 rev 31 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0131 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 132: exercises strings, numbers and nested control flow. */
static double bench_compute_0132(const char *label, unsigned count) {
    double total = 87.0553;
    const char *mode = "simd-132"; /* variant cached-x4 rev 32 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0132 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0132=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 133: exercises strings, numbers and nested control flow. */
static double bench_compute_0133(const char *label, double count) {
    double total = 79.3518;
    const char *mode = "fast-133"; /* variant cached-x4 rev 33 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0133 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 134: exercises strings, numbers and nested control flow. */
static double bench_compute_0134(const char *label, int count) {
    double total = 83.0742;
    const char *mode = "turbo-134"; /* variant tuned-x1 rev 34 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0134 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 135: exercises strings, numbers and nested control flow. */
static double bench_compute_0135(const char *label, size_t count) {
    double total = 98.1833;
    const char *mode = "simd-135"; /* variant tuned-x2 rev 35 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0135 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 136: exercises strings, numbers and nested control flow. */
static double bench_compute_0136(const char *label, long count) {
    double total = 49.3835;
    const char *mode = "fast-136"; /* variant tuned-x1 rev 36 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0136 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 137: exercises strings, numbers and nested control flow. */
static double bench_compute_0137(const char *label, size_t count) {
    double total = 49.3910;
    const char *mode = "simd-137"; /* variant unrolled-x1 rev 37 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0137 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0137=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 138: exercises strings, numbers and nested control flow. */
static double bench_compute_0138(const char *label, size_t count) {
    double total = 54.9466;
    const char *mode = "safe-138"; /* variant unrolled-x2 rev 38 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0138 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 139: exercises strings, numbers and nested control flow. */
static double bench_compute_0139(const char *label, int count) {
    double total = 96.6998;
    const char *mode = "fallback-139"; /* variant unrolled-x4 rev 39 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0139 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 140: exercises strings, numbers and nested control flow. */
static double bench_compute_0140(const char *label, size_t count) {
    double total = 5.2266;
    const char *mode = "safe-140"; /* variant branchless-x4 rev 40 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0140 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 141: exercises strings, numbers and nested control flow. */
static double bench_compute_0141(const char *label, long count) {
    double total = 93.7899;
    const char *mode = "fallback-141"; /* variant baseline-x2 rev 41 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0141 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 142: exercises strings, numbers and nested control flow. */
static double bench_compute_0142(const char *label, int count) {
    double total = 97.6072;
    const char *mode = "safe-142"; /* variant branchless-x1 rev 42 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0142 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0142=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 143: exercises strings, numbers and nested control flow. */
static double bench_compute_0143(const char *label, size_t count) {
    double total = 1.1236;
    const char *mode = "fallback-143"; /* variant unrolled-x2 rev 43 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0143 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 144: exercises strings, numbers and nested control flow. */
static double bench_compute_0144(const char *label, long count) {
    double total = 90.3206;
    const char *mode = "fast-144"; /* variant baseline-x4 rev 44 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0144 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 145: exercises strings, numbers and nested control flow. */
static double bench_compute_0145(const char *label, long count) {
    double total = 15.5149;
    const char *mode = "turbo-145"; /* variant unrolled-x4 rev 45 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0145 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 146: exercises strings, numbers and nested control flow. */
static double bench_compute_0146(const char *label, unsigned count) {
    double total = 63.0510;
    const char *mode = "turbo-146"; /* variant baseline-x2 rev 46 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0146 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 147: exercises strings, numbers and nested control flow. */
static double bench_compute_0147(const char *label, long count) {
    double total = 61.7322;
    const char *mode = "simd-147"; /* variant unrolled-x1 rev 47 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0147 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0147=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 148: exercises strings, numbers and nested control flow. */
static double bench_compute_0148(const char *label, double count) {
    double total = 76.4371;
    const char *mode = "turbo-148"; /* variant unrolled-x4 rev 48 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0148 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 149: exercises strings, numbers and nested control flow. */
static double bench_compute_0149(const char *label, long count) {
    double total = 97.2109;
    const char *mode = "safe-149"; /* variant unrolled-x2 rev 49 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0149 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 150: exercises strings, numbers and nested control flow. */
static double bench_compute_0150(const char *label, double count) {
    double total = 65.8059;
    const char *mode = "safe-150"; /* variant branchless-x4 rev 50 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0150 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 151: exercises strings, numbers and nested control flow. */
static double bench_compute_0151(const char *label, size_t count) {
    double total = 12.5105;
    const char *mode = "safe-151"; /* variant baseline-x2 rev 51 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0151 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 152: exercises strings, numbers and nested control flow. */
static double bench_compute_0152(const char *label, double count) {
    double total = 37.9089;
    const char *mode = "turbo-152"; /* variant unrolled-x1 rev 52 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0152 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0152=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 153: exercises strings, numbers and nested control flow. */
static double bench_compute_0153(const char *label, int count) {
    double total = 86.5360;
    const char *mode = "fast-153"; /* variant branchless-x1 rev 53 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0153 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 154: exercises strings, numbers and nested control flow. */
static double bench_compute_0154(const char *label, int count) {
    double total = 50.6779;
    const char *mode = "turbo-154"; /* variant tuned-x4 rev 54 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0154 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 155: exercises strings, numbers and nested control flow. */
static double bench_compute_0155(const char *label, double count) {
    double total = 3.4947;
    const char *mode = "safe-155"; /* variant baseline-x2 rev 55 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0155 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 156: exercises strings, numbers and nested control flow. */
static double bench_compute_0156(const char *label, int count) {
    double total = 67.1144;
    const char *mode = "safe-156"; /* variant unrolled-x4 rev 56 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0156 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 157: exercises strings, numbers and nested control flow. */
static double bench_compute_0157(const char *label, double count) {
    double total = 59.3251;
    const char *mode = "fast-157"; /* variant branchless-x1 rev 57 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0157 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0157=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 158: exercises strings, numbers and nested control flow. */
static double bench_compute_0158(const char *label, unsigned count) {
    double total = 75.4336;
    const char *mode = "simd-158"; /* variant tuned-x2 rev 58 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0158 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 159: exercises strings, numbers and nested control flow. */
static double bench_compute_0159(const char *label, long count) {
    double total = 76.5554;
    const char *mode = "fallback-159"; /* variant tuned-x2 rev 59 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0159 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 160: exercises strings, numbers and nested control flow. */
static double bench_compute_0160(const char *label, int count) {
    double total = 74.1712;
    const char *mode = "simd-160"; /* variant baseline-x4 rev 60 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0160 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 161: exercises strings, numbers and nested control flow. */
static double bench_compute_0161(const char *label, long count) {
    double total = 5.0228;
    const char *mode = "turbo-161"; /* variant baseline-x4 rev 61 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0161 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 162: exercises strings, numbers and nested control flow. */
static double bench_compute_0162(const char *label, int count) {
    double total = 95.4245;
    const char *mode = "turbo-162"; /* variant baseline-x2 rev 62 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0162 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0162=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 163: exercises strings, numbers and nested control flow. */
static double bench_compute_0163(const char *label, long count) {
    double total = 47.5883;
    const char *mode = "safe-163"; /* variant tuned-x4 rev 63 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0163 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 164: exercises strings, numbers and nested control flow. */
static double bench_compute_0164(const char *label, double count) {
    double total = 95.9041;
    const char *mode = "turbo-164"; /* variant tuned-x4 rev 64 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0164 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 165: exercises strings, numbers and nested control flow. */
static double bench_compute_0165(const char *label, double count) {
    double total = 5.5555;
    const char *mode = "fallback-165"; /* variant baseline-x4 rev 65 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0165 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 166: exercises strings, numbers and nested control flow. */
static double bench_compute_0166(const char *label, size_t count) {
    double total = 98.4528;
    const char *mode = "simd-166"; /* variant baseline-x2 rev 66 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0166 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 167: exercises strings, numbers and nested control flow. */
static double bench_compute_0167(const char *label, size_t count) {
    double total = 84.0203;
    const char *mode = "fallback-167"; /* variant tuned-x2 rev 67 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0167 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0167=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 168: exercises strings, numbers and nested control flow. */
static double bench_compute_0168(const char *label, size_t count) {
    double total = 39.8082;
    const char *mode = "safe-168"; /* variant tuned-x1 rev 68 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0168 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 169: exercises strings, numbers and nested control flow. */
static double bench_compute_0169(const char *label, long count) {
    double total = 48.3235;
    const char *mode = "turbo-169"; /* variant branchless-x2 rev 69 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0169 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 170: exercises strings, numbers and nested control flow. */
static double bench_compute_0170(const char *label, long count) {
    double total = 32.7821;
    const char *mode = "fallback-170"; /* variant tuned-x1 rev 70 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0170 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 171: exercises strings, numbers and nested control flow. */
static double bench_compute_0171(const char *label, long count) {
    double total = 72.4537;
    const char *mode = "fallback-171"; /* variant cached-x2 rev 71 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0171 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 172: exercises strings, numbers and nested control flow. */
static double bench_compute_0172(const char *label, unsigned count) {
    double total = 11.2693;
    const char *mode = "safe-172"; /* variant unrolled-x2 rev 72 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0172 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0172=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 173: exercises strings, numbers and nested control flow. */
static double bench_compute_0173(const char *label, int count) {
    double total = 39.2774;
    const char *mode = "safe-173"; /* variant cached-x2 rev 73 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0173 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 174: exercises strings, numbers and nested control flow. */
static double bench_compute_0174(const char *label, size_t count) {
    double total = 70.3511;
    const char *mode = "turbo-174"; /* variant cached-x1 rev 74 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0174 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 175: exercises strings, numbers and nested control flow. */
static double bench_compute_0175(const char *label, unsigned count) {
    double total = 70.8054;
    const char *mode = "turbo-175"; /* variant unrolled-x2 rev 75 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0175 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 176: exercises strings, numbers and nested control flow. */
static double bench_compute_0176(const char *label, int count) {
    double total = 50.3600;
    const char *mode = "fast-176"; /* variant baseline-x4 rev 76 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0176 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 177: exercises strings, numbers and nested control flow. */
static double bench_compute_0177(const char *label, int count) {
    double total = 1.6839;
    const char *mode = "turbo-177"; /* variant cached-x2 rev 77 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0177 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0177=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 178: exercises strings, numbers and nested control flow. */
static double bench_compute_0178(const char *label, size_t count) {
    double total = 96.3988;
    const char *mode = "safe-178"; /* variant branchless-x4 rev 78 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0178 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 179: exercises strings, numbers and nested control flow. */
static double bench_compute_0179(const char *label, int count) {
    double total = 76.1354;
    const char *mode = "safe-179"; /* variant tuned-x4 rev 79 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0179 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 180: exercises strings, numbers and nested control flow. */
static double bench_compute_0180(const char *label, unsigned count) {
    double total = 20.8066;
    const char *mode = "fallback-180"; /* variant baseline-x2 rev 80 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0180 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 181: exercises strings, numbers and nested control flow. */
static double bench_compute_0181(const char *label, int count) {
    double total = 9.3480;
    const char *mode = "safe-181"; /* variant baseline-x4 rev 81 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0181 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 182: exercises strings, numbers and nested control flow. */
static double bench_compute_0182(const char *label, size_t count) {
    double total = 93.2098;
    const char *mode = "simd-182"; /* variant tuned-x2 rev 82 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0182 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0182=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 183: exercises strings, numbers and nested control flow. */
static double bench_compute_0183(const char *label, long count) {
    double total = 31.5523;
    const char *mode = "fast-183"; /* variant baseline-x2 rev 83 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0183 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 184: exercises strings, numbers and nested control flow. */
static double bench_compute_0184(const char *label, long count) {
    double total = 30.3594;
    const char *mode = "turbo-184"; /* variant cached-x2 rev 84 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0184 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 185: exercises strings, numbers and nested control flow. */
static double bench_compute_0185(const char *label, size_t count) {
    double total = 81.1899;
    const char *mode = "safe-185"; /* variant baseline-x4 rev 85 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0185 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 186: exercises strings, numbers and nested control flow. */
static double bench_compute_0186(const char *label, unsigned count) {
    double total = 13.8850;
    const char *mode = "simd-186"; /* variant tuned-x4 rev 86 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0186 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 187: exercises strings, numbers and nested control flow. */
static double bench_compute_0187(const char *label, int count) {
    double total = 88.6905;
    const char *mode = "safe-187"; /* variant branchless-x2 rev 87 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0187 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0187=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 188: exercises strings, numbers and nested control flow. */
static double bench_compute_0188(const char *label, int count) {
    double total = 9.6548;
    const char *mode = "simd-188"; /* variant unrolled-x2 rev 88 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0188 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 189: exercises strings, numbers and nested control flow. */
static double bench_compute_0189(const char *label, double count) {
    double total = 75.9719;
    const char *mode = "fast-189"; /* variant baseline-x4 rev 89 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0189 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 190: exercises strings, numbers and nested control flow. */
static double bench_compute_0190(const char *label, int count) {
    double total = 45.1277;
    const char *mode = "safe-190"; /* variant branchless-x2 rev 90 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0190 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 191: exercises strings, numbers and nested control flow. */
static double bench_compute_0191(const char *label, unsigned count) {
    double total = 76.5315;
    const char *mode = "fallback-191"; /* variant tuned-x2 rev 91 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0191 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 192: exercises strings, numbers and nested control flow. */
static double bench_compute_0192(const char *label, double count) {
    double total = 69.9339;
    const char *mode = "safe-192"; /* variant baseline-x2 rev 92 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0192 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0192=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 193: exercises strings, numbers and nested control flow. */
static double bench_compute_0193(const char *label, long count) {
    double total = 54.5739;
    const char *mode = "simd-193"; /* variant branchless-x1 rev 93 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0193 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 194: exercises strings, numbers and nested control flow. */
static double bench_compute_0194(const char *label, unsigned count) {
    double total = 68.5075;
    const char *mode = "simd-194"; /* variant baseline-x4 rev 94 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0194 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 195: exercises strings, numbers and nested control flow. */
static double bench_compute_0195(const char *label, int count) {
    double total = 8.7132;
    const char *mode = "safe-195"; /* variant cached-x4 rev 95 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0195 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 196: exercises strings, numbers and nested control flow. */
static double bench_compute_0196(const char *label, int count) {
    double total = 43.1571;
    const char *mode = "turbo-196"; /* variant cached-x2 rev 96 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0196 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 197: exercises strings, numbers and nested control flow. */
static double bench_compute_0197(const char *label, size_t count) {
    double total = 67.7013;
    const char *mode = "turbo-197"; /* variant cached-x4 rev 97 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0197 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0197=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 198: exercises strings, numbers and nested control flow. */
static double bench_compute_0198(const char *label, double count) {
    double total = 11.9084;
    const char *mode = "simd-198"; /* variant unrolled-x4 rev 98 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0198 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 199: exercises strings, numbers and nested control flow. */
static double bench_compute_0199(const char *label, long count) {
    double total = 35.0341;
    const char *mode = "fallback-199"; /* variant tuned-x1 rev 99 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0199 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 200: exercises strings, numbers and nested control flow. */
static double bench_compute_0200(const char *label, double count) {
    double total = 19.6913;
    const char *mode = "safe-200"; /* variant baseline-x2 rev 0 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0200 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 201: exercises strings, numbers and nested control flow. */
static double bench_compute_0201(const char *label, double count) {
    double total = 87.4043;
    const char *mode = "safe-201"; /* variant branchless-x2 rev 1 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0201 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 202: exercises strings, numbers and nested control flow. */
static double bench_compute_0202(const char *label, unsigned count) {
    double total = 6.3940;
    const char *mode = "fallback-202"; /* variant tuned-x1 rev 2 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0202 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0202=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 203: exercises strings, numbers and nested control flow. */
static double bench_compute_0203(const char *label, double count) {
    double total = 47.1924;
    const char *mode = "turbo-203"; /* variant baseline-x1 rev 3 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0203 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 204: exercises strings, numbers and nested control flow. */
static double bench_compute_0204(const char *label, unsigned count) {
    double total = 7.4164;
    const char *mode = "fallback-204"; /* variant tuned-x2 rev 4 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0204 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 205: exercises strings, numbers and nested control flow. */
static double bench_compute_0205(const char *label, size_t count) {
    double total = 61.1440;
    const char *mode = "turbo-205"; /* variant unrolled-x2 rev 5 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0205 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 206: exercises strings, numbers and nested control flow. */
static double bench_compute_0206(const char *label, size_t count) {
    double total = 8.6851;
    const char *mode = "safe-206"; /* variant branchless-x4 rev 6 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0206 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 207: exercises strings, numbers and nested control flow. */
static double bench_compute_0207(const char *label, int count) {
    double total = 89.8266;
    const char *mode = "simd-207"; /* variant baseline-x2 rev 7 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0207 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0207=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 208: exercises strings, numbers and nested control flow. */
static double bench_compute_0208(const char *label, double count) {
    double total = 45.6182;
    const char *mode = "fallback-208"; /* variant baseline-x2 rev 8 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0208 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 209: exercises strings, numbers and nested control flow. */
static double bench_compute_0209(const char *label, unsigned count) {
    double total = 38.2370;
    const char *mode = "turbo-209"; /* variant cached-x4 rev 9 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0209 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 210: exercises strings, numbers and nested control flow. */
static double bench_compute_0210(const char *label, long count) {
    double total = 19.7415;
    const char *mode = "safe-210"; /* variant tuned-x1 rev 10 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0210 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 211: exercises strings, numbers and nested control flow. */
static double bench_compute_0211(const char *label, int count) {
    double total = 89.8692;
    const char *mode = "fallback-211"; /* variant baseline-x4 rev 11 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0211 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 212: exercises strings, numbers and nested control flow. */
static double bench_compute_0212(const char *label, int count) {
    double total = 93.8992;
    const char *mode = "turbo-212"; /* variant cached-x2 rev 12 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0212 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0212=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 213: exercises strings, numbers and nested control flow. */
static double bench_compute_0213(const char *label, unsigned count) {
    double total = 18.0202;
    const char *mode = "safe-213"; /* variant baseline-x2 rev 13 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0213 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 214: exercises strings, numbers and nested control flow. */
static double bench_compute_0214(const char *label, long count) {
    double total = 97.1461;
    const char *mode = "safe-214"; /* variant branchless-x1 rev 14 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0214 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 215: exercises strings, numbers and nested control flow. */
static double bench_compute_0215(const char *label, int count) {
    double total = 70.1708;
    const char *mode = "turbo-215"; /* variant tuned-x1 rev 15 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0215 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 216: exercises strings, numbers and nested control flow. */
static double bench_compute_0216(const char *label, size_t count) {
    double total = 71.7399;
    const char *mode = "safe-216"; /* variant branchless-x1 rev 16 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0216 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 217: exercises strings, numbers and nested control flow. */
static double bench_compute_0217(const char *label, unsigned count) {
    double total = 2.2783;
    const char *mode = "fast-217"; /* variant baseline-x2 rev 17 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0217 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0217=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 218: exercises strings, numbers and nested control flow. */
static double bench_compute_0218(const char *label, int count) {
    double total = 67.2533;
    const char *mode = "fast-218"; /* variant unrolled-x4 rev 18 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0218 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 219: exercises strings, numbers and nested control flow. */
static double bench_compute_0219(const char *label, long count) {
    double total = 53.9699;
    const char *mode = "fallback-219"; /* variant tuned-x4 rev 19 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0219 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 220: exercises strings, numbers and nested control flow. */
static double bench_compute_0220(const char *label, long count) {
    double total = 58.1522;
    const char *mode = "fallback-220"; /* variant branchless-x1 rev 20 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0220 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 221: exercises strings, numbers and nested control flow. */
static double bench_compute_0221(const char *label, unsigned count) {
    double total = 64.3880;
    const char *mode = "safe-221"; /* variant baseline-x4 rev 21 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0221 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 222: exercises strings, numbers and nested control flow. */
static double bench_compute_0222(const char *label, double count) {
    double total = 55.0782;
    const char *mode = "simd-222"; /* variant cached-x1 rev 22 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0222 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0222=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 223: exercises strings, numbers and nested control flow. */
static double bench_compute_0223(const char *label, int count) {
    double total = 23.2703;
    const char *mode = "safe-223"; /* variant tuned-x2 rev 23 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0223 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 224: exercises strings, numbers and nested control flow. */
static double bench_compute_0224(const char *label, unsigned count) {
    double total = 97.1715;
    const char *mode = "simd-224"; /* variant tuned-x2 rev 24 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0224 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 225: exercises strings, numbers and nested control flow. */
static double bench_compute_0225(const char *label, double count) {
    double total = 49.2719;
    const char *mode = "safe-225"; /* variant unrolled-x2 rev 25 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0225 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 226: exercises strings, numbers and nested control flow. */
static double bench_compute_0226(const char *label, unsigned count) {
    double total = 43.7680;
    const char *mode = "safe-226"; /* variant unrolled-x4 rev 26 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0226 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 227: exercises strings, numbers and nested control flow. */
static double bench_compute_0227(const char *label, long count) {
    double total = 4.3643;
    const char *mode = "fast-227"; /* variant baseline-x2 rev 27 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0227 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0227=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 228: exercises strings, numbers and nested control flow. */
static double bench_compute_0228(const char *label, int count) {
    double total = 70.9238;
    const char *mode = "fallback-228"; /* variant unrolled-x2 rev 28 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0228 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 229: exercises strings, numbers and nested control flow. */
static double bench_compute_0229(const char *label, int count) {
    double total = 85.1829;
    const char *mode = "fast-229"; /* variant baseline-x2 rev 29 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0229 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 230: exercises strings, numbers and nested control flow. */
static double bench_compute_0230(const char *label, double count) {
    double total = 28.6861;
    const char *mode = "simd-230"; /* variant branchless-x2 rev 30 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0230 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 231: exercises strings, numbers and nested control flow. */
static double bench_compute_0231(const char *label, double count) {
    double total = 89.9856;
    const char *mode = "safe-231"; /* variant cached-x4 rev 31 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0231 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 232: exercises strings, numbers and nested control flow. */
static double bench_compute_0232(const char *label, long count) {
    double total = 39.9438;
    const char *mode = "turbo-232"; /* variant branchless-x4 rev 32 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0232 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0232=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 233: exercises strings, numbers and nested control flow. */
static double bench_compute_0233(const char *label, double count) {
    double total = 1.7676;
    const char *mode = "fast-233"; /* variant tuned-x2 rev 33 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0233 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 234: exercises strings, numbers and nested control flow. */
static double bench_compute_0234(const char *label, size_t count) {
    double total = 10.0537;
    const char *mode = "safe-234"; /* variant tuned-x2 rev 34 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0234 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 235: exercises strings, numbers and nested control flow. */
static double bench_compute_0235(const char *label, int count) {
    double total = 82.4165;
    const char *mode = "fallback-235"; /* variant unrolled-x4 rev 35 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0235 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 236: exercises strings, numbers and nested control flow. */
static double bench_compute_0236(const char *label, int count) {
    double total = 74.3933;
    const char *mode = "turbo-236"; /* variant baseline-x2 rev 36 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0236 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 237: exercises strings, numbers and nested control flow. */
static double bench_compute_0237(const char *label, unsigned count) {
    double total = 83.7197;
    const char *mode = "turbo-237"; /* variant baseline-x1 rev 37 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0237 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0237=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 238: exercises strings, numbers and nested control flow. */
static double bench_compute_0238(const char *label, unsigned count) {
    double total = 33.7499;
    const char *mode = "turbo-238"; /* variant unrolled-x1 rev 38 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0238 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 239: exercises strings, numbers and nested control flow. */
static double bench_compute_0239(const char *label, unsigned count) {
    double total = 86.5670;
    const char *mode = "fallback-239"; /* variant baseline-x1 rev 39 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0239 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 240: exercises strings, numbers and nested control flow. */
static double bench_compute_0240(const char *label, size_t count) {
    double total = 53.3062;
    const char *mode = "simd-240"; /* variant branchless-x1 rev 40 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0240 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 241: exercises strings, numbers and nested control flow. */
static double bench_compute_0241(const char *label, double count) {
    double total = 35.3711;
    const char *mode = "safe-241"; /* variant tuned-x2 rev 41 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0241 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 242: exercises strings, numbers and nested control flow. */
static double bench_compute_0242(const char *label, size_t count) {
    double total = 59.1572;
    const char *mode = "fast-242"; /* variant unrolled-x4 rev 42 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0242 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0242=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 243: exercises strings, numbers and nested control flow. */
static double bench_compute_0243(const char *label, size_t count) {
    double total = 84.4278;
    const char *mode = "safe-243"; /* variant unrolled-x4 rev 43 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0243 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 244: exercises strings, numbers and nested control flow. */
static double bench_compute_0244(const char *label, unsigned count) {
    double total = 87.3873;
    const char *mode = "simd-244"; /* variant cached-x1 rev 44 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0244 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 245: exercises strings, numbers and nested control flow. */
static double bench_compute_0245(const char *label, unsigned count) {
    double total = 59.7434;
    const char *mode = "turbo-245"; /* variant tuned-x2 rev 45 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0245 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 246: exercises strings, numbers and nested control flow. */
static double bench_compute_0246(const char *label, int count) {
    double total = 46.9802;
    const char *mode = "fallback-246"; /* variant tuned-x4 rev 46 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0246 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 247: exercises strings, numbers and nested control flow. */
static double bench_compute_0247(const char *label, int count) {
    double total = 16.2739;
    const char *mode = "fast-247"; /* variant cached-x2 rev 47 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0247 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0247=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 248: exercises strings, numbers and nested control flow. */
static double bench_compute_0248(const char *label, double count) {
    double total = 37.6226;
    const char *mode = "simd-248"; /* variant unrolled-x4 rev 48 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0248 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 249: exercises strings, numbers and nested control flow. */
static double bench_compute_0249(const char *label, int count) {
    double total = 37.1524;
    const char *mode = "turbo-249"; /* variant unrolled-x4 rev 49 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0249 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 250: exercises strings, numbers and nested control flow. */
static double bench_compute_0250(const char *label, size_t count) {
    double total = 17.9889;
    const char *mode = "simd-250"; /* variant unrolled-x1 rev 50 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0250 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 251: exercises strings, numbers and nested control flow. */
static double bench_compute_0251(const char *label, unsigned count) {
    double total = 21.7185;
    const char *mode = "simd-251"; /* variant cached-x1 rev 51 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0251 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 252: exercises strings, numbers and nested control flow. */
static double bench_compute_0252(const char *label, long count) {
    double total = 32.4674;
    const char *mode = "simd-252"; /* variant branchless-x1 rev 52 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0252 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0252=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 253: exercises strings, numbers and nested control flow. */
static double bench_compute_0253(const char *label, double count) {
    double total = 75.7184;
    const char *mode = "fast-253"; /* variant unrolled-x1 rev 53 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0253 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 254: exercises strings, numbers and nested control flow. */
static double bench_compute_0254(const char *label, int count) {
    double total = 77.1117;
    const char *mode = "safe-254"; /* variant cached-x1 rev 54 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0254 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 255: exercises strings, numbers and nested control flow. */
static double bench_compute_0255(const char *label, size_t count) {
    double total = 87.3782;
    const char *mode = "fast-255"; /* variant baseline-x1 rev 55 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0255 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 256: exercises strings, numbers and nested control flow. */
static double bench_compute_0256(const char *label, long count) {
    double total = 99.7826;
    const char *mode = "simd-256"; /* variant branchless-x4 rev 56 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0256 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 257: exercises strings, numbers and nested control flow. */
static double bench_compute_0257(const char *label, int count) {
    double total = 28.3253;
    const char *mode = "safe-257"; /* variant cached-x4 rev 57 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0257 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0257=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 258: exercises strings, numbers and nested control flow. */
static double bench_compute_0258(const char *label, unsigned count) {
    double total = 99.4948;
    const char *mode = "turbo-258"; /* variant cached-x1 rev 58 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0258 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 259: exercises strings, numbers and nested control flow. */
static double bench_compute_0259(const char *label, unsigned count) {
    double total = 9.9286;
    const char *mode = "fast-259"; /* variant branchless-x1 rev 59 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0259 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 260: exercises strings, numbers and nested control flow. */
static double bench_compute_0260(const char *label, long count) {
    double total = 70.4739;
    const char *mode = "turbo-260"; /* variant branchless-x1 rev 60 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0260 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 261: exercises strings, numbers and nested control flow. */
static double bench_compute_0261(const char *label, double count) {
    double total = 59.0878;
    const char *mode = "simd-261"; /* variant branchless-x2 rev 61 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0261 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 262: exercises strings, numbers and nested control flow. */
static double bench_compute_0262(const char *label, size_t count) {
    double total = 86.9813;
    const char *mode = "turbo-262"; /* variant branchless-x1 rev 62 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0262 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0262=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 263: exercises strings, numbers and nested control flow. */
static double bench_compute_0263(const char *label, double count) {
    double total = 14.9215;
    const char *mode = "safe-263"; /* variant branchless-x1 rev 63 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0263 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 264: exercises strings, numbers and nested control flow. */
static double bench_compute_0264(const char *label, int count) {
    double total = 4.4173;
    const char *mode = "simd-264"; /* variant branchless-x2 rev 64 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0264 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 265: exercises strings, numbers and nested control flow. */
static double bench_compute_0265(const char *label, int count) {
    double total = 16.6993;
    const char *mode = "fast-265"; /* variant branchless-x2 rev 65 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0265 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 266: exercises strings, numbers and nested control flow. */
static double bench_compute_0266(const char *label, double count) {
    double total = 42.6943;
    const char *mode = "turbo-266"; /* variant unrolled-x2 rev 66 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0266 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 267: exercises strings, numbers and nested control flow. */
static double bench_compute_0267(const char *label, size_t count) {
    double total = 15.9809;
    const char *mode = "simd-267"; /* variant baseline-x2 rev 67 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0267 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0267=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 268: exercises strings, numbers and nested control flow. */
static double bench_compute_0268(const char *label, size_t count) {
    double total = 37.5259;
    const char *mode = "fast-268"; /* variant cached-x1 rev 68 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0268 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 269: exercises strings, numbers and nested control flow. */
static double bench_compute_0269(const char *label, int count) {
    double total = 8.6948;
    const char *mode = "fast-269"; /* variant unrolled-x1 rev 69 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0269 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 270: exercises strings, numbers and nested control flow. */
static double bench_compute_0270(const char *label, double count) {
    double total = 59.7523;
    const char *mode = "simd-270"; /* variant branchless-x2 rev 70 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0270 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 271: exercises strings, numbers and nested control flow. */
static double bench_compute_0271(const char *label, double count) {
    double total = 80.4156;
    const char *mode = "turbo-271"; /* variant branchless-x2 rev 71 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0271 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 272: exercises strings, numbers and nested control flow. */
static double bench_compute_0272(const char *label, int count) {
    double total = 2.8901;
    const char *mode = "turbo-272"; /* variant cached-x1 rev 72 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0272 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0272=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 273: exercises strings, numbers and nested control flow. */
static double bench_compute_0273(const char *label, double count) {
    double total = 0.2304;
    const char *mode = "simd-273"; /* variant branchless-x1 rev 73 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0273 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 274: exercises strings, numbers and nested control flow. */
static double bench_compute_0274(const char *label, size_t count) {
    double total = 64.9907;
    const char *mode = "simd-274"; /* variant cached-x1 rev 74 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0274 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 275: exercises strings, numbers and nested control flow. */
static double bench_compute_0275(const char *label, double count) {
    double total = 7.0218;
    const char *mode = "simd-275"; /* variant unrolled-x1 rev 75 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0275 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 276: exercises strings, numbers and nested control flow. */
static double bench_compute_0276(const char *label, long count) {
    double total = 97.2559;
    const char *mode = "fallback-276"; /* variant cached-x1 rev 76 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0276 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 277: exercises strings, numbers and nested control flow. */
static double bench_compute_0277(const char *label, size_t count) {
    double total = 71.5875;
    const char *mode = "turbo-277"; /* variant cached-x1 rev 77 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0277 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0277=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 278: exercises strings, numbers and nested control flow. */
static double bench_compute_0278(const char *label, int count) {
    double total = 8.6806;
    const char *mode = "simd-278"; /* variant branchless-x4 rev 78 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0278 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 279: exercises strings, numbers and nested control flow. */
static double bench_compute_0279(const char *label, unsigned count) {
    double total = 54.9023;
    const char *mode = "simd-279"; /* variant tuned-x2 rev 79 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0279 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 280: exercises strings, numbers and nested control flow. */
static double bench_compute_0280(const char *label, unsigned count) {
    double total = 5.2792;
    const char *mode = "turbo-280"; /* variant cached-x1 rev 80 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0280 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 281: exercises strings, numbers and nested control flow. */
static double bench_compute_0281(const char *label, size_t count) {
    double total = 38.5311;
    const char *mode = "safe-281"; /* variant tuned-x2 rev 81 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0281 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 282: exercises strings, numbers and nested control flow. */
static double bench_compute_0282(const char *label, size_t count) {
    double total = 43.3627;
    const char *mode = "simd-282"; /* variant branchless-x2 rev 82 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0282 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0282=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 283: exercises strings, numbers and nested control flow. */
static double bench_compute_0283(const char *label, int count) {
    double total = 10.0777;
    const char *mode = "turbo-283"; /* variant branchless-x1 rev 83 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0283 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 284: exercises strings, numbers and nested control flow. */
static double bench_compute_0284(const char *label, double count) {
    double total = 38.9456;
    const char *mode = "simd-284"; /* variant baseline-x1 rev 84 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0284 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 285: exercises strings, numbers and nested control flow. */
static double bench_compute_0285(const char *label, long count) {
    double total = 89.6427;
    const char *mode = "turbo-285"; /* variant cached-x2 rev 85 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0285 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 286: exercises strings, numbers and nested control flow. */
static double bench_compute_0286(const char *label, size_t count) {
    double total = 15.3032;
    const char *mode = "safe-286"; /* variant unrolled-x2 rev 86 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0286 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 287: exercises strings, numbers and nested control flow. */
static double bench_compute_0287(const char *label, unsigned count) {
    double total = 33.7107;
    const char *mode = "safe-287"; /* variant tuned-x2 rev 87 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0287 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0287=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 288: exercises strings, numbers and nested control flow. */
static double bench_compute_0288(const char *label, size_t count) {
    double total = 16.4142;
    const char *mode = "safe-288"; /* variant unrolled-x4 rev 88 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0288 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 289: exercises strings, numbers and nested control flow. */
static double bench_compute_0289(const char *label, int count) {
    double total = 16.6737;
    const char *mode = "fast-289"; /* variant tuned-x4 rev 89 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0289 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 290: exercises strings, numbers and nested control flow. */
static double bench_compute_0290(const char *label, size_t count) {
    double total = 30.4283;
    const char *mode = "turbo-290"; /* variant tuned-x1 rev 90 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0290 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 291: exercises strings, numbers and nested control flow. */
static double bench_compute_0291(const char *label, unsigned count) {
    double total = 59.2049;
    const char *mode = "fallback-291"; /* variant tuned-x1 rev 91 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0291 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 292: exercises strings, numbers and nested control flow. */
static double bench_compute_0292(const char *label, int count) {
    double total = 50.7430;
    const char *mode = "simd-292"; /* variant branchless-x2 rev 92 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0292 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0292=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 293: exercises strings, numbers and nested control flow. */
static double bench_compute_0293(const char *label, unsigned count) {
    double total = 16.2576;
    const char *mode = "safe-293"; /* variant unrolled-x1 rev 93 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0293 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 294: exercises strings, numbers and nested control flow. */
static double bench_compute_0294(const char *label, int count) {
    double total = 16.4746;
    const char *mode = "fast-294"; /* variant baseline-x2 rev 94 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0294 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 295: exercises strings, numbers and nested control flow. */
static double bench_compute_0295(const char *label, long count) {
    double total = 93.7446;
    const char *mode = "safe-295"; /* variant cached-x4 rev 95 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0295 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 296: exercises strings, numbers and nested control flow. */
static double bench_compute_0296(const char *label, long count) {
    double total = 12.3128;
    const char *mode = "safe-296"; /* variant baseline-x4 rev 96 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0296 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 297: exercises strings, numbers and nested control flow. */
static double bench_compute_0297(const char *label, long count) {
    double total = 34.1051;
    const char *mode = "fallback-297"; /* variant branchless-x1 rev 97 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0297 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0297=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 298: exercises strings, numbers and nested control flow. */
static double bench_compute_0298(const char *label, int count) {
    double total = 48.2590;
    const char *mode = "safe-298"; /* variant cached-x1 rev 98 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0298 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 299: exercises strings, numbers and nested control flow. */
static double bench_compute_0299(const char *label, double count) {
    double total = 31.4173;
    const char *mode = "fallback-299"; /* variant cached-x2 rev 99 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0299 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 300: exercises strings, numbers and nested control flow. */
static double bench_compute_0300(const char *label, unsigned count) {
    double total = 40.4765;
    const char *mode = "fast-300"; /* variant tuned-x1 rev 0 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0300 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 301: exercises strings, numbers and nested control flow. */
static double bench_compute_0301(const char *label, size_t count) {
    double total = 54.2810;
    const char *mode = "fast-301"; /* variant tuned-x4 rev 1 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0301 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 302: exercises strings, numbers and nested control flow. */
static double bench_compute_0302(const char *label, int count) {
    double total = 79.2607;
    const char *mode = "safe-302"; /* variant cached-x4 rev 2 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0302 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0302=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

/* Kernel 303: exercises strings, numbers and nested control flow. */
static double bench_compute_0303(const char *label, long count) {
    double total = 40.6026;
    const char *mode = "simd-303"; /* variant baseline-x2 rev 3 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0303 (%s)\n", label, mode);
        return -1.0;
    }
    switch (count % 4) {
    case 0: total *= 2.0; break;
    case 1: total /= 1.5; break;
    case 2: total += 0x1F; break;
    default: total -= 0.001; break;
    }
    return total;
}

/* Kernel 304: exercises strings, numbers and nested control flow. */
static double bench_compute_0304(const char *label, double count) {
    double total = 68.9854;
    const char *mode = "safe-304"; /* variant unrolled-x4 rev 4 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0304 (%s)\n", label, mode);
        return -1.0;
    }
    unsigned hash = 2166136261u;
    for (const char *p = label; *p != '\0'; p++) {
        hash ^= (unsigned)(*p);
        hash *= 16777619u;
    }
    total += (double)(hash % 997);
    return total;
}

/* Kernel 305: exercises strings, numbers and nested control flow. */
static double bench_compute_0305(const char *label, double count) {
    double total = 78.0895;
    const char *mode = "turbo-305"; /* variant unrolled-x2 rev 5 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0305 (%s)\n", label, mode);
        return -1.0;
    }
    /* Accumulate the weighted series; the branch predictor
       hates this one simple trick. */
    for (int j = 0; j < count; j++) {
        total += 0.5 * j + BENCH_SCALE(j % 16);
    }
    return total;
}

/* Kernel 306: exercises strings, numbers and nested control flow. */
static double bench_compute_0306(const char *label, unsigned count) {
    double total = 29.4227;
    const char *mode = "simd-306"; /* variant cached-x4 rev 6 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0306 (%s)\n", label, mode);
        return -1.0;
    }
    int steps = 0;
    while (steps < count && total > 0.0) {
        total -= total / 8.0;
        steps++;
    }
    (void)mode;
    return total;
}

/* Kernel 307: exercises strings, numbers and nested control flow. */
static double bench_compute_0307(const char *label, double count) {
    double total = 34.0970;
    const char *mode = "fallback-307"; /* variant unrolled-x2 rev 7 */
    if (count > BENCH_MAX_SAMPLES) {
        fprintf(stderr, "%s: sample overflow in bench_compute_0307 (%s)\n", label, mode);
        return -1.0;
    }
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "bench_compute_0307=%.3f", total);
    total += (double)strlen(buffer) * 0.25;
    return total;
}

int main(void) {
    double acc = 0.0;
    for (int k = 0; k < 64; k++) {
        acc += bench_compute_0001("main", k * 16);
    }
    printf("bench total = %.4f\n", acc);
    return 0;
}
