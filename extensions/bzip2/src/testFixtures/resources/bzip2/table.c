#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include "bzlib_private.h"
#include "inputs.h"

#define FNV_START 0xcbf29ce484222325ull

static uint64_t fnvInto(uint64_t h, const unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) { h ^= p[i]; h *= 0x100000001b3ull; }
    return h;
}

static uint64_t fnvWord(uint64_t h, uint64_t word) {
    return fnvInto(h, (const unsigned char *)&word, 8);
}

static void *zeroedAlloc(void *opaque, int items, int size) {
    (void)opaque;
    return calloc((size_t)items, (size_t)size);
}

static void plainFree(void *opaque, void *address) {
    (void)opaque;
    free(address);
}

static FILE *oneShots, *streams, *decodes, *crafted;
static unsigned nextSeed = 1;

static void oneShot(int kind, size_t size, unsigned a, unsigned b, unsigned seed, int level) {
    unsigned char *in = make(kind, size, a, b, seed);
    size_t cap = size + size / 100 + 600;
    unsigned char *out = malloc(cap);
    unsigned int length = (unsigned int)cap;
    int ret = BZ2_bzBuffToBuffCompress((char *)out, &length, (char *)in, (unsigned int)size, level, 0, 0);
    if (ret != BZ_OK) { fprintf(stderr, "compress %d\n", ret); exit(1); }
    fprintf(oneShots, "%d\t%zu\t%u\t%u\t%u\t%016llx\t%d\t%u\t%016llx\n", kind, size, a, b, seed,
            (unsigned long long)fnvInto(FNV_START, in, size), level, length,
            (unsigned long long)fnvInto(FNV_START, out, length));
    free(in);
    free(out);
}

static void stream(int kind, size_t size, unsigned a, unsigned b, unsigned seed, int level, unsigned schedule,
                   int chunkLog, int roomLog) {
    unsigned char *in = make(kind, size, a, b, seed);
    unsigned char *room = malloc((size_t)1 << roomLog);
    bz_stream s;
    memset(&s, 0, sizeof s);
    if (BZ2_bzCompressInit(&s, level, 0, 0) != BZ_OK) exit(1);
    uint32_t state = schedule;
    size_t at = 0;
    uint64_t calls = FNV_START, output = FNV_START, length = 0;
    int count = 0;
    while (at < size) {
        size_t chunk = nextRandom(&state) & (((size_t)1 << chunkLog) - 1);
        if (chunk > size - at) chunk = size - at;
        unsigned pick = nextRandom(&state) % 10;
        int action = pick < 8 ? BZ_RUN : BZ_FLUSH;
        s.next_in = (char *)in + at;
        s.avail_in = (unsigned int)chunk;
        at += chunk;
        for (;;) {
            if (action == BZ_RUN && s.avail_in == 0) break;
            unsigned give = 1 + (nextRandom(&state) & (((unsigned)1 << roomLog) - 1));
            if (give > ((unsigned)1 << roomLog)) give = (unsigned)1 << roomLog;
            s.next_out = (char *)room;
            s.avail_out = give;
            unsigned consumedBefore = s.avail_in;
            int ret = BZ2_bzCompress(&s, action);
            unsigned produced = give - s.avail_out;
            output = fnvInto(output, room, produced);
            length += produced;
            calls = fnvWord(calls, ((uint64_t)(unsigned char)ret) | ((uint64_t)(consumedBefore - s.avail_in) << 8)
                                   | ((uint64_t)produced << 36));
            count++;
            if (ret < 0) { fprintf(stderr, "stream %d\n", ret); exit(1); }
            if (action == BZ_FLUSH && ret == BZ_RUN_OK) break;
        }
    }
    s.next_in = (char *)in + size;
    s.avail_in = 0;
    for (;;) {
        unsigned give = 1 + (nextRandom(&state) & (((unsigned)1 << roomLog) - 1));
        if (give > ((unsigned)1 << roomLog)) give = (unsigned)1 << roomLog;
        s.next_out = (char *)room;
        s.avail_out = give;
        int ret = BZ2_bzCompress(&s, BZ_FINISH);
        unsigned produced = give - s.avail_out;
        output = fnvInto(output, room, produced);
        length += produced;
        calls = fnvWord(calls, ((uint64_t)(unsigned char)ret) | ((uint64_t)produced << 36));
        count++;
        if (ret == BZ_STREAM_END) break;
        if (ret < 0) { fprintf(stderr, "finish %d\n", ret); exit(1); }
    }
    BZ2_bzCompressEnd(&s);
    fprintf(streams, "%d\t%zu\t%u\t%u\t%u\t%016llx\t%d\t%u\t%d\t%d\t%d\t%016llx\t%llu\t%016llx\n", kind, size, a, b, seed,
            (unsigned long long)fnvInto(FNV_START, in, size), level, schedule, chunkLog, roomLog, count,
            (unsigned long long)calls, (unsigned long long)length, (unsigned long long)output);
    free(in);
    free(room);
}

typedef struct { int last; unsigned long long totalIn, totalOut; int calls; uint64_t callsHash, outputHash; } Decoded;

/* the C's memory is malloc's, so a damaged stream can read a table entry no block ever set; zeroing it makes that
   answer repeatable, and the Java's fresh arrays hold the same zeroes */
static Decoded decode(const unsigned char *in, size_t len, int chunkLog, int roomLog, unsigned schedule) {
    bz_stream s;
    memset(&s, 0, sizeof s);
    s.bzalloc = zeroedAlloc;
    s.bzfree = plainFree;
    if (BZ2_bzDecompressInit(&s, 0, 0) != BZ_OK) exit(1);
    size_t cap = roomLog < 0 ? (size_t)1 << 22 : (size_t)1 << roomLog;
    unsigned char *room = malloc(cap);
    uint32_t state = schedule;
    size_t at = 0;
    int errors = 0;
    Decoded d = {0, 0, 0, 0, FNV_START, FNV_START};
    s.avail_in = 0;
    for (;;) {
        if (s.avail_in == 0 && at < len) {
            size_t chunk = chunkLog < 0 ? len - at : 1 + (nextRandom(&state) & (((size_t)1 << chunkLog) - 1));
            if (chunk > len - at) chunk = len - at;
            s.next_in = (char *)in + at;
            s.avail_in = (unsigned int)chunk;
            at += chunk;
        }
        unsigned give = roomLog < 0 ? (unsigned)cap : 1 + (nextRandom(&state) & ((unsigned)cap - 1));
        s.next_out = (char *)room;
        s.avail_out = give;
        unsigned availInBefore = s.avail_in;
        int ret = BZ2_bzDecompress(&s);
        unsigned consumed = availInBefore - s.avail_in;
        unsigned produced = give - s.avail_out;
        d.outputHash = fnvInto(d.outputHash, room, produced);
        d.totalIn += consumed;
        d.totalOut += produced;
        d.callsHash = fnvWord(d.callsHash, ((uint64_t)(unsigned char)ret) | ((uint64_t)consumed << 8)
                                           | ((uint64_t)produced << 36));
        d.calls++;
        d.last = ret;
        if (ret == BZ_STREAM_END || ret == BZ_SEQUENCE_ERROR) break;
        /* called again after a selector error, the C runs pos[] off its end: there is no answer to record */
        if (ret < 0 && ((DState *)s.state)->state == BZ_X_SELECTOR_3) break;
        if (ret < 0 && ++errors == 3) break;
        if (ret >= 0 && consumed == 0 && produced == 0 && s.avail_in == 0 && at >= len) break;
    }
    BZ2_bzDecompressEnd(&s);
    free(room);
    return d;
}

static void decodeRow(const char *source, const unsigned char *in, size_t len, int chunkLog, int roomLog,
                      unsigned schedule) {
    Decoded d = decode(in, len, chunkLog, roomLog, schedule);
    fprintf(decodes, "%s\t%016llx\t%d\t%d\t%u\t%d\t%llu\t%llu\t%d\t%016llx\t%016llx\n", source,
            (unsigned long long)fnvInto(FNV_START, in, len), chunkLog, roomLog, schedule, d.last, d.totalIn, d.totalOut,
            d.calls, (unsigned long long)d.callsHash, (unsigned long long)d.outputHash);
}

/* a stream written bit by bit, so a test can say exactly what is wrong with it */
typedef struct { unsigned char *octets; size_t bits; size_t cap; } Bits;

static void put(Bits *w, unsigned n, uint32_t v) {
    for (int i = (int)n - 1; i >= 0; i--) {
        if (w->bits / 8 >= w->cap) {
            w->cap *= 2;
            w->octets = realloc(w->octets, w->cap);
            memset(w->octets + w->cap / 2, 0, w->cap / 2);
        }
        if ((v >> i) & 1) w->octets[w->bits / 8] |= (unsigned char)(0x80 >> (w->bits % 8));
        w->bits++;
    }
}

typedef struct {
    int levelByte, firstMagic, lastMagic, randomised, nGroups, nSelectors, selectorOnes, firstLength, lengthOf;
    uint32_t origPtr, blockCrc, trailerCrc;
    int inUseCount;
    const uint32_t *symbols;
    size_t symbolCount;
    int repeats;
    int noBlock;
} Craft;

static Craft plainCraft(void) {
    Craft c;
    memset(&c, 0, sizeof c);
    c.levelByte = '1';
    c.firstMagic = 0x31;
    c.lastMagic = 0x17;
    c.nGroups = 2;
    c.nSelectors = 1;
    c.lengthOf = 3;
    c.firstLength = 3;
    c.inUseCount = 2;
    return c;
}

static void putSymbol(Bits *w, const Craft *c, uint32_t symbol) {
    put(w, (unsigned)c->lengthOf, symbol);
}

static void emitCraft(const char *name, const Craft *c) {
    Bits w = {calloc(1, 1 << 16), 0, 1 << 16};
    put(&w, 8, 'B'); put(&w, 8, 'Z'); put(&w, 8, 'h'); put(&w, 8, (uint32_t)c->levelByte);
    if (!c->noBlock) {
        put(&w, 8, (uint32_t)c->firstMagic);
        put(&w, 8, 0x41); put(&w, 8, 0x59); put(&w, 8, 0x26); put(&w, 8, 0x53); put(&w, 8, 0x59);
        put(&w, 32, c->blockCrc);
        put(&w, 1, (uint32_t)c->randomised);
        put(&w, 24, c->origPtr);
        if (c->inUseCount == 0) {
            put(&w, 16, 0);
        } else {
            put(&w, 16, 0x0040);
            for (int i = 0; i < 16; i++) put(&w, 1, i >= 1 && i <= c->inUseCount ? 1 : 0);
        }
        put(&w, 3, (uint32_t)c->nGroups);
        put(&w, 15, (uint32_t)c->nSelectors);
        for (int i = 0; i < c->nSelectors; i++) {
            for (int j = 0; j < c->selectorOnes; j++) put(&w, 1, 1);
            put(&w, 1, 0);
        }
        int alpha = c->inUseCount + 2;
        for (int t = 0; t < c->nGroups && t < 6; t++) {
            int curr = c->firstLength;
            put(&w, 5, (uint32_t)curr);
            for (int i = 0; i < alpha; i++) {
                while (curr < c->lengthOf) { put(&w, 2, 2); curr++; }
                while (curr > c->lengthOf) { put(&w, 2, 3); curr--; }
                put(&w, 1, 0);
            }
        }
        for (int r = 0; r < (c->repeats ? c->repeats : 1); r++) {
            for (size_t i = 0; i < c->symbolCount; i++) putSymbol(&w, c, c->symbols[i]);
        }
    }
    put(&w, 8, (uint32_t)c->lastMagic);
    put(&w, 8, 0x72); put(&w, 8, 0x45); put(&w, 8, 0x38); put(&w, 8, 0x50); put(&w, 8, 0x90);
    put(&w, 32, c->trailerCrc);
    size_t length = (w.bits + 7) / 8;
    fprintf(crafted, "%s\t", name);
    for (size_t i = 0; i < length; i++) fprintf(crafted, "%02X", w.octets[i]);
    fprintf(crafted, "\n");
    char source[128];
    snprintf(source, sizeof source, "crafted:%s", name);
    decodeRow(source, w.octets, length, -1, -1, 1);
    decodeRow(source, w.octets, length, 0, 0, 2);
    decodeRow(source, w.octets, length, 2, 3, 3);
    free(w.octets);
}

static uint32_t crcOfBlock(const unsigned char *data, size_t n) {
    extern uint32_t BZ2_crc32Table[256];
    uint32_t crc = 0xffffffffu;
    for (size_t i = 0; i < n; i++) crc = (crc << 8) ^ BZ2_crc32Table[(crc >> 24) ^ data[i]];
    return ~crc;
}

static void craftAll(void) {
    static const uint32_t ab[] = {2, 1, 3};
    static const uint32_t runThenEnd[] = {0, 3};
    static const uint32_t onlyEnd[] = {3};
    uint32_t zeroCrc = crcOfBlock((const unsigned char *)"\x91", 1);
    Craft c;

    c = plainCraft(); c.symbols = ab; c.symbolCount = 3; c.origPtr = 1; emitCraft("plain", &c);
    c = plainCraft(); c.levelByte = '0'; emitCraft("level-below", &c);
    c = plainCraft(); c.levelByte = ':'; emitCraft("level-above", &c);
    c = plainCraft(); c.noBlock = 1; emitCraft("no-block", &c);
    c = plainCraft(); c.noBlock = 1; c.trailerCrc = 7; emitCraft("no-block-bad-crc", &c);
    c = plainCraft(); c.noBlock = 1; c.lastMagic = 0x18; emitCraft("first-magic-wrong-end", &c);
    c = plainCraft(); c.firstMagic = 0x32; c.symbols = ab; c.symbolCount = 3; emitCraft("block-magic-wrong", &c);
    c = plainCraft(); c.inUseCount = 0; emitCraft("nothing-in-use", &c);
    c = plainCraft(); c.nGroups = 1; c.symbols = ab; c.symbolCount = 3; emitCraft("one-group", &c);
    c = plainCraft(); c.nGroups = 7; c.symbols = ab; c.symbolCount = 3; emitCraft("seven-groups", &c);
    c = plainCraft(); c.nSelectors = 0; c.symbols = ab; c.symbolCount = 3; emitCraft("no-selectors", &c);
    c = plainCraft(); c.selectorOnes = 2; c.symbols = ab; c.symbolCount = 3; emitCraft("selector-past-groups", &c);
    c = plainCraft(); c.nSelectors = 18002; c.symbols = ab; c.symbolCount = 3; c.origPtr = 1; emitCraft("selectors-at-limit", &c);
    c = plainCraft(); c.nSelectors = 18003; c.symbols = ab; c.symbolCount = 3; c.origPtr = 1; emitCraft("selectors-past-limit", &c);
    c = plainCraft(); c.nSelectors = 32767; c.symbols = ab; c.symbolCount = 3; c.origPtr = 1; emitCraft("selectors-at-most", &c);
    c = plainCraft(); c.firstLength = 0; c.lengthOf = 0; emitCraft("length-zero", &c);
    c = plainCraft(); c.firstLength = 21; c.lengthOf = 21; emitCraft("length-past-twenty", &c);
    c = plainCraft(); c.firstLength = 1; c.lengthOf = 22; emitCraft("length-climbs-past-twenty", &c);
    c = plainCraft(); c.firstLength = 2; c.lengthOf = 0; emitCraft("length-falls-to-zero", &c);
    c = plainCraft(); c.firstLength = 20; c.lengthOf = 20; c.symbols = ab; c.symbolCount = 3; c.origPtr = 1; emitCraft("length-twenty", &c);
    c = plainCraft(); c.origPtr = 3; c.symbols = ab; c.symbolCount = 3; emitCraft("orig-at-block-end", &c);
    c = plainCraft(); c.origPtr = 100010; c.symbols = ab; c.symbolCount = 3; emitCraft("orig-at-header-limit", &c);
    c = plainCraft(); c.origPtr = 100011; c.symbols = ab; c.symbolCount = 3; emitCraft("orig-past-header-limit", &c);
    c = plainCraft(); c.symbols = onlyEnd; c.symbolCount = 1; emitCraft("empty-block", &c);
    c = plainCraft(); c.symbols = runThenEnd; c.symbolCount = 2; c.blockCrc = zeroCrc; emitCraft("one-symbol-run", &c);
    c = plainCraft(); c.randomised = 1; c.symbols = ab; c.symbolCount = 3; c.origPtr = 1; emitCraft("randomised", &c);
    c = plainCraft(); c.symbols = ab; c.symbolCount = 3; c.origPtr = 1; c.blockCrc = 9; emitCraft("wrong-block-crc", &c);

    {   uint32_t run[24];
        for (int i = 0; i < 23; i++) run[i] = 0;
        run[23] = 3;
        c = plainCraft(); c.symbols = run; c.symbolCount = 24; emitCraft("run-past-two-million", &c);
        for (int i = 0; i < 16; i++) run[i] = 0;
        run[16] = 3;
        c = plainCraft(); c.symbols = run; c.symbolCount = 17; emitCraft("run-past-the-block", &c);
    }
    {   size_t n = 100002;
        uint32_t *many = malloc(n * sizeof *many);
        for (size_t i = 0; i < n; i++) many[i] = 1 + (uint32_t)(i & 1);
        many[n - 1] = 3;
        c = plainCraft(); c.nSelectors = 3000; c.symbols = many; c.symbolCount = n; emitCraft("symbols-past-the-block", &c);
        for (size_t i = 0; i < n; i++) many[i] = 1;
        many[n - 1] = 3;
        c = plainCraft(); c.nSelectors = 1; c.symbols = many; c.symbolCount = 60; c.repeats = 2; emitCraft("symbols-past-selectors", &c);
        free(many);
    }
    {   uint32_t fourAndCut[8] = {0, 1, 3};
        c = plainCraft(); c.symbols = fourAndCut; c.symbolCount = 3; emitCraft("run-marker-at-block-end", &c);
    }
}

static unsigned char *readWhole(const char *path, size_t *length) {
    FILE *f = fopen(path, "rb");
    if (!f) { perror(path); exit(1); }
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    unsigned char *b = malloc((size_t)n + 1);
    if (fread(b, 1, (size_t)n, f) != (size_t)n) exit(1);
    fclose(f);
    *length = (size_t)n;
    return b;
}

/* every output byte of a corpus stream, so it can be compressed again */
static unsigned char *decodeWhole(const unsigned char *in, size_t len, size_t *outLength, int *ret) {
    size_t cap = 1 << 24;
    unsigned char *out = malloc(cap);
    bz_stream s;
    memset(&s, 0, sizeof s);
    s.bzalloc = zeroedAlloc;
    s.bzfree = plainFree;
    BZ2_bzDecompressInit(&s, 0, 0);
    s.next_in = (char *)in;
    s.avail_in = (unsigned int)len;
    size_t at = 0;
    for (;;) {
        if (at == cap) { cap *= 2; out = realloc(out, cap); }
        s.next_out = (char *)out + at;
        s.avail_out = (unsigned int)(cap - at);
        *ret = BZ2_bzDecompress(&s);
        at = cap - s.avail_out;
        if (*ret != BZ_OK) break;
        if (s.avail_in == 0 && s.avail_out > 0) break;
    }
    BZ2_bzDecompressEnd(&s);
    *outLength = at;
    return out;
}

static void corpusFile(const char *path, FILE *again) {
    size_t len;
    unsigned char *in = readWhole(path, &len);
    decodeRow(path, in, len, -1, -1, 1);
    decodeRow(path, in, len, 0, 12, 2);
    decodeRow(path, in, len, 6, 3, 3);
    size_t outLength;
    int ret;
    unsigned char *out = decodeWhole(in, len, &outLength, &ret);
    if (outLength <= (1u << 20)) decodeRow(path, in, len, 0, 0, 4);
    if (ret == BZ_STREAM_END && outLength <= (1u << 24)) {
        for (int level = 1; level <= 9; level += 4) {
            size_t cap = outLength + outLength / 100 + 600;
            unsigned char *z = malloc(cap);
            unsigned int zl = (unsigned int)cap;
            int r = BZ2_bzBuffToBuffCompress((char *)z, &zl, (char *)out, (unsigned int)outLength, level, 0, 0);
            if (r != BZ_OK) exit(1);
            fprintf(again, "%s\t%zu\t%016llx\t%d\t%u\t%016llx\n", path, outLength,
                    (unsigned long long)fnvInto(FNV_START, out, outLength), level, zl,
                    (unsigned long long)fnvInto(FNV_START, z, zl));
            free(z);
        }
    }
    free(in);
    free(out);
}

static void damagedRows(const char *name, int kind, size_t size, unsigned a, unsigned seed, int level, int everyBit) {
    unsigned char *input = make(kind, size, a, 0, seed);
    size_t cap = size + size / 100 + 600;
    unsigned char *z = malloc(cap);
    unsigned int zl = (unsigned int)cap;
    if (BZ2_bzBuffToBuffCompress((char *)z, &zl, (char *)input, (unsigned int)size, level, 0, 0) != BZ_OK) exit(1);
    fprintf(crafted, "%s\t", name);
    for (unsigned i = 0; i < zl; i++) fprintf(crafted, "%02X", z[i]);
    fprintf(crafted, "\n");
    unsigned char *copy = malloc(zl + 2);
    char source[128];
    for (unsigned bit = 0; bit < zl * 8; bit += (unsigned)everyBit) {
        memcpy(copy, z, zl);
        copy[bit / 8] ^= (unsigned char)(0x80 >> (bit % 8));
        snprintf(source, sizeof source, "flip:%s:%u", name, bit);
        decodeRow(source, copy, zl, -1, -1, 1);
        if (bit % (unsigned)(everyBit * 5) == 0) decodeRow(source, copy, zl, 1, 2, bit + 1);
    }
    for (unsigned length = 0; length < zl; length += (unsigned)(everyBit > 1 ? everyBit : 1)) {
        snprintf(source, sizeof source, "cut:%s:%u", name, length);
        decodeRow(source, z, length, -1, -1, 1);
    }
    memcpy(copy, z, zl);
    copy[zl] = 0x42;
    copy[zl + 1] = 0x5A;
    snprintf(source, sizeof source, "stray:%s", name);
    decodeRow(source, copy, zl + 2, -1, -1, 1);
    decodeRow(source, copy, zl + 2, 0, 0, 2);
    free(copy);
    free(z);
    free(input);
}

static void compressRows(void) {
    static const int everyLevel[] = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    for (size_t size = 0; size <= 24; size++) {
        for (int l = 0; l < 9; l += 4) {
            oneShot(NOISE, size, 256, 0, nextSeed++, everyLevel[l]);
            oneShot(CONSTANT, size, 'q', 0, 0, everyLevel[l]);
        }
    }
    static const size_t aroundGroups[] = {190, 590, 1190, 2390};
    for (int g = 0; g < 4; g++) {
        for (size_t size = aroundGroups[g]; size <= aroundGroups[g] + 20; size++) {
            oneShot(NO_RUNS, size, 256, 0, nextSeed++, 1);
        }
    }
    for (size_t size = 9990; size <= 10010; size++) {
        oneShot(NO_RUNS, size, 256, 0, nextSeed++, 1);
        oneShot(WORDS, size, 40, 0, nextSeed++, 9);
    }
    static const unsigned runLengths[] = {1, 2, 3, 4, 5, 6, 7, 254, 255, 256, 257, 258, 259, 260, 510, 511, 512};
    for (size_t r = 0; r < sizeof runLengths / sizeof runLengths[0]; r++) {
        oneShot(RUNS, 5000, runLengths[r], 0, nextSeed++, 6);
        oneShot(RUNS, 20000, runLengths[r], 3, nextSeed++, 1);
    }
    for (int level = 1; level <= 9; level++) {
        size_t limit = (size_t)100000 * (size_t)level - 19;
        for (size_t size = limit - 2; size <= limit + 2; size++) {
            oneShot(NO_RUNS, size, 256, 0, nextSeed++, level);
        }
        oneShot(RUNS, limit * 3, 255, 0, nextSeed++, level);
    }
    static const unsigned periods[] = {1, 2, 3, 7, 50, 1000};
    static const size_t periodic[] = {9999, 10000, 20000, 100000, 500000, 899981};
    for (size_t p = 0; p < 6; p++) {
        for (size_t s = 0; s < 6; s++) {
            oneShot(PERIODIC, periodic[s], periods[p], 0, nextSeed++, 9);
        }
    }
    oneShot(CONSTANT, 5000000, 251, 0, 0, 9);
    oneShot(CONSTANT, 1000000, 0, 0, 0, 1);
    for (unsigned symbols = 18; symbols <= 40; symbols += 2) {
        oneShot(FIBONACCI, 400000, symbols, 1, nextSeed++, 9);
        oneShot(FIBONACCI, 100000, symbols, 6, nextSeed++, 1);
    }
    for (int level = 1; level <= 9; level += 2) {
        oneShot(WORDS, 2000000, 3000, 0, nextSeed++, level);
        oneShot(NOISE, 1200000, 4, 0, nextSeed++, level);
        oneShot(NOISE, 1200000, 256, 0, nextSeed++, level);
    }
    oneShot(WORDS, 12000000, 50000, 0, nextSeed++, 9);
}

static void streamRows(void) {
    for (int level = 1; level <= 9; level += 4) {
        for (unsigned s = 1; s <= 6; s++) {
            stream(WORDS, 3000, 40, 0, nextSeed++, level, s * 11 + (unsigned)level, 6, 2);
            stream(WORDS, 3000, 40, 0, nextSeed++, level, s * 13 + (unsigned)level, 3, 8);
            stream(RUNS, 4000, 255, 3, nextSeed++, level, s * 17 + (unsigned)level, 8, 4);
            stream(NO_RUNS, 150000, 256, 0, nextSeed++, level, s * 19 + (unsigned)level, 14, 12);
            stream(WORDS, 700000, 2000, 0, nextSeed++, level, s * 23 + (unsigned)level, 16, 14);
        }
        stream(WORDS, 0, 40, 0, nextSeed++, level, 900 + (unsigned)level, 4, 2);
        stream(WORDS, 1, 40, 0, nextSeed++, level, 901 + (unsigned)level, 4, 0);
    }
}

int main(int argc, char **argv) {
    oneShots = fopen("bzip2-compress.tsv", "w");
    streams = fopen("bzip2-compress-stream.tsv", "w");
    decodes = fopen("bzip2-decode.tsv", "w");
    crafted = fopen("bzip2-streams.tsv", "w");
    FILE *again = fopen("bzip2-recompress.tsv", "w");
    fprintf(oneShots, "kind\tsize\ta\tb\tseed\tinput\tlevel\tlength\toutput\n");
    fprintf(streams, "kind\tsize\ta\tb\tseed\tinput\tlevel\tschedule\tchunk\troom\tcalls\tcallsHash\tlength\toutput\n");
    fprintf(decodes, "source\tinput\tchunk\troom\tschedule\tlast\tin\tout\tcalls\tcallsHash\toutput\n");
    fprintf(crafted, "name\thex\n");
    fprintf(again, "path\tsize\tdecoded\tlevel\tlength\toutput\n");

    compressRows();
    streamRows();
    craftAll();
    damagedRows("words-small", WORDS, 300, 12, 77, 1, 1);
    damagedRows("no-runs-small", NO_RUNS, 120, 16, 78, 9, 1);
    damagedRows("runs-small", RUNS, 1200, 40, 79, 1, 1);
    damagedRows("words-large", WORDS, 30000, 400, 80, 2, 97);
    for (int i = 1; i < argc; i++) corpusFile(argv[i], again);

    fclose(oneShots);
    fclose(streams);
    fclose(decodes);
    fclose(crafted);
    fclose(again);
    return 0;
}
