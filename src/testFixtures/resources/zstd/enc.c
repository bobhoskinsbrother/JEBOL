#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define ZSTD_STATIC_LINKING_ONLY
#include "zstd.h"
#include "common/error_private.h"

#include "inputs.h"

static uint64_t fnvInto(uint64_t h, const unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) { h ^= p[i]; h *= 0x100000001b3ull; }
    return h;
}
#define FNV_START 0xcbf29ce484222325ull

static FILE *oneShots, *streams;

static void check(size_t r) {
    if (ZSTD_isError(r)) { fprintf(stderr, "%s\n", ZSTD_getErrorName(r)); exit(1); }
}

/* the extension's CompressZstd: ZSTD_compress into the bound */
static void oneShot(int kind, size_t size, unsigned a, unsigned b, unsigned seed, int level) {
    unsigned char *in = make(kind, size, a, b, seed);
    size_t cap = ZSTD_compressBound(size);
    unsigned char *out = malloc(cap ? cap : 1);
    size_t n = ZSTD_compress(out, cap, in, size, level);
    check(n);
    fprintf(oneShots, "%d\t%zu\t%u\t%u\t%u\t%016llx\t%d\t%zu\t%016llx\n", kind, size, a, b, seed,
            (unsigned long long)fnvInto(FNV_START, in, size), level, n,
            (unsigned long long)fnvInto(FNV_START, out, n));
    free(in);
    free(out);
}

/* the extension's streaming encoder: ZSTD_initCStream, then a schedule of writes, flushes and finishes,
 * as WRITE, WRITE/FLUSH, READ and WRITE/FINISH make them */
enum { CONTINUE = 0, FLUSH = 1, END = 2 };

static void stream(int kind, size_t size, unsigned a, unsigned b, unsigned seed, int level, unsigned schedule,
                   int maxLog) {
    unsigned char *in = make(kind, size, a, b, seed);
    size_t cap = ZSTD_compressBound(size) * 2 + 4096 + size / 16;
    unsigned char *out = malloc(cap);
    ZSTD_CStream *c = ZSTD_createCStream();
    check(ZSTD_initCStream(c, level));
    uint32_t s = schedule;
    size_t at = 0;
    ZSTD_outBuffer ob = {out, cap, 0};
    uint64_t ops = FNV_START;
    int count = 0;
    while (at < size) {
        /* a log of 100 and more is a schedule that never ends a frame early, so a stream can wrap its window */
        uint32_t chunkLog = RDG_rand(&s) % (uint32_t)(maxLog % 100);
        size_t chunk = RDG_rand(&s) & ((1u << chunkLog) - 1);
        if (chunk > size - at) chunk = size - at;
        uint32_t pickOp = RDG_rand(&s) % 10;
        int op = pickOp < 7 ? CONTINUE : pickOp < 9 ? FLUSH : END;
        if (maxLog >= 100 && op == END) op = FLUSH;
        ZSTD_inBuffer ib = {in + at, chunk, 0};
        while (ib.pos < ib.size) check(ZSTD_compressStream(c, &ob, &ib));
        at += chunk;
        size_t left;
        if (op == FLUSH) do { left = ZSTD_flushStream(c, &ob); check(left); } while (left);
        if (op == END) do { left = ZSTD_endStream(c, &ob); check(left); } while (left);
        uint64_t word = ((uint64_t)chunk << 2) | (uint64_t)op;
        ops = fnvInto(ops, (const unsigned char *)&word, 8);
        count++;
    }
    size_t left;
    do { left = ZSTD_endStream(c, &ob); check(left); } while (left);
    fprintf(streams, "%d\t%zu\t%u\t%u\t%u\t%016llx\t%d\t%u\t%d\t%d\t%016llx\t%zu\t%016llx\n", kind, size, a, b, seed,
            (unsigned long long)fnvInto(FNV_START, in, size), level, schedule, maxLog, count,
            (unsigned long long)ops, ob.pos, (unsigned long long)fnvInto(FNV_START, out, ob.pos));
    ZSTD_freeCStream(c);
    free(in);
    free(out);
}

int main(void) {
    oneShots = fopen("zstd-compress.tsv", "w");
    streams = fopen("zstd-compress-stream.tsv", "w");
    fprintf(oneShots, "kind\tsize\ta\tb\tseed\tinput\tlevel\tlength\toutput\n");
    fprintf(streams, "kind\tsize\ta\tb\tseed\tinput\tlevel\tschedule\tlog\tsteps\tops\tlength\toutput\n");

    size_t small[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 31, 32, 33, 63, 64, 65, 100, 127,
                      128, 129, 255, 256, 257, 511, 512, 513, 1000, 1023, 1024, 1025, 2047, 2048, 2049, 4095, 4096,
                      4097, 8191, 8192, 8193};
    unsigned seed = 1;
    for (size_t i = 0; i < sizeof small / sizeof small[0]; i++) {
        for (int level = 0; level <= 22; level++) {
            unsigned proba = 10 + (unsigned)((i * 7 + (size_t)level * 13) % 90);
            oneShot(DATAGEN, small[i], proba, (unsigned)(level % 3) * 20, seed++, level);
        }
    }
    size_t middle[] = {16383, 16384, 16385, 32768, 65535, 65536, 65537, 131071, 131072, 131073, 262143, 262144,
                       262145};
    for (size_t i = 0; i < sizeof middle / sizeof middle[0]; i++) {
        for (int level = 1; level <= 22; level++) {
            unsigned proba = 20 + (unsigned)((i * 11 + (size_t)level * 17) % 78);
            oneShot(DATAGEN, middle[i], proba, 0, seed++, level);
        }
    }
    for (int level = 1; level <= 22; level++) {
        oneShot(DATAGEN, 700000, 30 + (unsigned)level * 3, 0, seed++, level);
        oneShot(DATAGEN, 70000, 0, 0, seed++, level);
        oneShot(ZEROS, 300000, 'z', 0, 0, level);
        oneShot(ZEROS, 1000, 0, 0, 0, level);
        oneShot(TWO_SYMBOLS, 150000, 50 + (unsigned)level, 0, seed++, level);
        oneShot(ONE_BYTE_THEN_NOISE, 90000, 3 + (unsigned)level, 0, seed++, level);
        oneShot(DATAGEN, 40000, 95, 70, seed++, level);
        oneShot(DATAGEN, 2500000, 70, 0, seed++, level);
    }

    int streamLevels[] = {-131072, -100, -7, -5, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
                          18, 19, 20, 21, 22};
    for (size_t l = 0; l < sizeof streamLevels / sizeof streamLevels[0]; l++) {
        int level = streamLevels[l];
        stream(DATAGEN, 0, 50, 0, seed++, level, 1 + (unsigned)l, 4);
        stream(DATAGEN, 27, 50, 0, seed++, level, 100 + (unsigned)l, 3);
        stream(DATAGEN, 5000, 60, 0, seed++, level, 200 + (unsigned)l, 8);
        stream(DATAGEN, 300000, 75, 0, seed++, level, 300 + (unsigned)l, 17);
        stream(DATAGEN, 300000, 75, 0, seed++, level, 400 + (unsigned)l, 12);
        stream(DATAGEN, 1500000, 85, 0, seed++, level, 500 + (unsigned)l, 21);
        stream(TWO_SYMBOLS, 200000, 60, 0, seed++, level, 600 + (unsigned)l, 16);
        stream(ZEROS, 200000, 7, 0, 0, level, 700 + (unsigned)l, 18);
    }
    stream(FAR_REPEATS, 20000000, 300, 0, seed++, 22, 801, 22);
    stream(FAR_REPEATS, 20000000, 300, 0, seed++, 19, 802, 22);
    stream(DATAGEN, 6000000, 90, 0, seed++, 22, 803, 23);
    /* streams longer than their window and a block, so the window wraps and every strategy searches an extDict */
    {   int wrapLevels[] = {1, 3, 4, 5, 6, 7, 8, 11, 13, 14, 16, 17, 18, 19};
        for (size_t l = 0; l < sizeof wrapLevels / sizeof wrapLevels[0]; l++) {
            int level = wrapLevels[l];
            size_t window = level <= 2 ? (1u << 20) : level <= 8 ? (2u << 20) : level <= 16 ? (4u << 20) : (8u << 20);
            stream(PERIODIC, window + window / 4 + 300000, 60, 700, seed++, level, 900 + (unsigned)l, 117);
        }
    }
    /* literal runs past 64 KB, which the sequences store as a long length */
    {   int longLevels[] = {1, 3, 5, 7, 12, 15, 16, 19, 22};
        for (size_t l = 0; l < sizeof longLevels / sizeof longLevels[0]; l++) {
            oneShot(NOISE_THEN_REPEAT, 120000, 100, 20, seed++, longLevels[l]);
            stream(NOISE_THEN_REPEAT, 120000, 100, 20, seed++, longLevels[l], 950 + (unsigned)l, 118);
        }
    }
    fclose(oneShots);
    fclose(streams);
    return 0;
}
