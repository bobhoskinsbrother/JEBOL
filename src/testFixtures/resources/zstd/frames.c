#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define ZSTD_STATIC_LINKING_ONLY
#include "zstd.h"
#include "common/error_private.h"

/* zstd's own programs/datagen.c, RDG_genBuffer, as its fuzzers and tests make data */
#define LTLOG 13
#define LTSIZE (1<<LTLOG)
#define LTMASK (LTSIZE-1)
#define RDG_rotl32(x,r) ((x << r) | (x >> (32 - r)))
static uint32_t RDG_rand(uint32_t* src) {
    uint32_t rand32 = *src;
    rand32 *= 2654435761U;
    rand32 ^= 2246822519U;
    rand32 = RDG_rotl32(rand32, 13);
    *src = rand32;
    return rand32 >> 5;
}
static void RDG_fillLiteralDistrib(unsigned char* ldt, uint32_t ld) {
    unsigned char const firstChar = (ld<=0.0) ? 0 : '(';
    unsigned char const lastChar = (ld<=0.0) ? 255 : '}';
    unsigned char character = (ld<=0.0) ? 0 : '0';
    uint32_t u;
    for (u=0; u<LTSIZE; ) {
        uint32_t const weight = (((LTSIZE - u) * ld) >> 8) + 1;
        uint32_t const end = (u + weight < LTSIZE) ? u + weight : LTSIZE;
        while (u < end) ldt[u++] = character;
        character++;
        if (character > lastChar) character = firstChar;
    }
}
static unsigned char RDG_genChar(uint32_t* seed, const unsigned char* ldt) { return ldt[RDG_rand(seed) & LTMASK]; }
static uint32_t RDG_rand15Bits(uint32_t* s) { return RDG_rand(s) & 0x7FFF; }
static uint32_t RDG_randLength(uint32_t* s) {
    if (RDG_rand(s) & 7) return (RDG_rand(s) & 0xF);
    return (RDG_rand(s) & 0x1FF) + 0xF;
}
static void RDG_genBlock(unsigned char* buffPtr, size_t buffSize, size_t prefixSize, double matchProba,
                         const unsigned char* ldt, uint32_t* seedPtr) {
    uint32_t const matchProba32 = (uint32_t)(32768 * matchProba);
    size_t pos = prefixSize;
    uint32_t prevOffset = 1;
    while (matchProba >= 1.0) {
        size_t size0 = RDG_rand(seedPtr) & 3;
        size0 = (size_t)1 << (16 + size0 * 2);
        size0 += RDG_rand(seedPtr) & (size0-1);
        if (buffSize < pos + size0) { memset(buffPtr+pos, 0, buffSize-pos); return; }
        memset(buffPtr+pos, 0, size0);
        pos += size0;
        buffPtr[pos-1] = RDG_genChar(seedPtr, ldt);
    }
    if (pos==0) buffPtr[0] = RDG_genChar(seedPtr, ldt), pos=1;
    while (pos < buffSize) {
        if (RDG_rand15Bits(seedPtr) < matchProba32) {
            uint32_t const length = RDG_randLength(seedPtr) + 4;
            uint32_t const d = (uint32_t)((pos + length < buffSize) ? pos + length : buffSize);
            uint32_t const repeatOffset = (RDG_rand(seedPtr) & 15) == 2;
            uint32_t const randOffset = RDG_rand15Bits(seedPtr) + 1;
            uint32_t const offset = repeatOffset ? prevOffset : (uint32_t)((randOffset < pos) ? randOffset : pos);
            size_t match = pos - offset;
            while (pos < d) { buffPtr[pos++] = buffPtr[match++]; }
            prevOffset = offset;
        } else {
            uint32_t const length = RDG_randLength(seedPtr);
            uint32_t const d = (uint32_t)((pos + length < buffSize) ? pos + length : buffSize);
            while (pos < d) { buffPtr[pos++] = RDG_genChar(seedPtr, ldt); }
        }
    }
}
static void RDG_genBuffer(void* buffer, size_t size, double matchProba, double litProba, unsigned seed) {
    uint32_t seed32 = seed;
    unsigned char ldt[LTSIZE];
    memset(ldt, '0', sizeof(ldt));
    if (litProba<=0.0) litProba = matchProba / 4.5;
    RDG_fillLiteralDistrib(ldt, (uint32_t)(litProba * 256 + 0.001));
    if (size) RDG_genBlock(buffer, size, 0, matchProba, ldt, &seed32);
}

/* the table's own inputs, named by a kind and three numbers, made again by the Java test */
static uint32_t pick;
static uint32_t next(uint32_t bound) { return bound ? RDG_rand(&pick) % bound : 0; }

enum { DATAGEN = 0, ZEROS = 1, TWO_SYMBOLS = 2, FAR_REPEATS = 3, ONE_BYTE_THEN_NOISE = 4 };

static unsigned char *make(int kind, size_t size, unsigned a, unsigned b, unsigned seed) {
    unsigned char *p = malloc(size ? size : 1);
    uint32_t s = seed;
    switch (kind) {
    case DATAGEN: RDG_genBuffer(p, size, a / 100.0, b / 100.0, seed); break;
    case ZEROS: memset(p, (int)a, size); break;
    case TWO_SYMBOLS:
        for (size_t i = 0; i < size; i++) p[i] = (RDG_rand(&s) % 100) < a ? 'a' : 'b';
        break;
    case FAR_REPEATS: {
        /* a chunks of 16 random octets, then zeros, then the chunks again in a shuffled order */
        size_t chunks = a, head = chunks * 16;
        memset(p, 0, size);
        for (size_t i = 0; i < head && i < size; i++) p[i] = (unsigned char)RDG_rand(&s);
        size_t at = size - head;
        for (size_t i = at - b * 1000; i < at; i++) p[i] = (unsigned char)('a' + (RDG_rand(&s) & 15));
        for (size_t c = 0; c < chunks; c++) {
            size_t from = (size_t)(RDG_rand(&s) % chunks) * 16;
            memcpy(p + at + c * 16, p + from, 16);
        }
        break;
    }
    case ONE_BYTE_THEN_NOISE:
        memset(p, 'x', size);
        for (size_t i = 0; i < size; i += 1 + RDG_rand(&s) % (a + 1)) p[i] = (unsigned char)RDG_rand(&s);
        break;
    }
    return p;
}

static uint64_t fnvInto(uint64_t h, const unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) { h ^= p[i]; h *= 0x100000001b3ull; }
    return h;
}
#define FNV_START 0xcbf29ce484222325ull

static FILE *bin, *frames, *rows;
static size_t binAt;
static int frameCount;

typedef struct { size_t at, len; } frame_t;
static frame_t frameList[4096];

static int addFrame(const unsigned char *p, size_t n, const char *how) {
    fwrite(p, 1, n, bin);
    frameList[frameCount].at = binAt;
    frameList[frameCount].len = n;
    fprintf(frames, "%d\t%zu\t%zu\t%s\n", frameCount, binAt, n, how);
    binAt += n;
    return frameCount++;
}

static void check(size_t r) {
    if (ZSTD_isError(r)) { fprintf(stderr, "%s\n", ZSTD_getErrorName(r)); exit(1); }
}

/* compress: mode 0 one shot with ZSTD_compress2, mode 1 streamed with the size unknown */
static int compressFrame(int kind, size_t size, unsigned a, unsigned b, unsigned seed,
                         int level, int checksum, int windowLog, int contentSize, int mode, int strategy) {
    unsigned char *in = make(kind, size, a, b, seed);
    size_t cap = ZSTD_compressBound(size) + 64;
    unsigned char *out = malloc(cap);
    ZSTD_CCtx *c = ZSTD_createCCtx();
    check(ZSTD_CCtx_setParameter(c, ZSTD_c_compressionLevel, level));
    check(ZSTD_CCtx_setParameter(c, ZSTD_c_checksumFlag, checksum));
    check(ZSTD_CCtx_setParameter(c, ZSTD_c_contentSizeFlag, contentSize));
    if (windowLog) check(ZSTD_CCtx_setParameter(c, ZSTD_c_windowLog, windowLog));
    if (strategy) check(ZSTD_CCtx_setParameter(c, ZSTD_c_strategy, strategy));
    if (kind == FAR_REPEATS && b) { check(ZSTD_CCtx_setParameter(c, ZSTD_c_blockSplitterLevel, 1)); check(ZSTD_CCtx_setParameter(c, ZSTD_c_splitAfterSequences, ZSTD_ps_disable)); }
    size_t n;
    if (mode == 0) {
        n = ZSTD_compress2(c, out, cap, in, size);
        check(n);
    } else {
        ZSTD_inBuffer ib = {in, size, 0};
        ZSTD_outBuffer ob = {out, cap, 0};
        while (ib.pos < ib.size) check(ZSTD_compressStream2(c, &ob, &ib, ZSTD_e_continue));
        size_t left;
        do { left = ZSTD_compressStream2(c, &ob, &ib, ZSTD_e_end); check(left); } while (left);
        n = ob.pos;
    }
    char how[200];
    snprintf(how, sizeof how, "%d\t%zu\t%u\t%u\t%u\t%016llx", kind, size, a, b, seed,
             (unsigned long long)fnvInto(FNV_START, in, size));
    char full[400];
    snprintf(full, sizeof full, "%s\tlevel=%d checksum=%d window=%d size=%d mode=%d strategy=%d", how, level,
             checksum, windowLog, contentSize, mode, strategy);
    int id = addFrame(out, n, full);
    ZSTD_freeCCtx(c);
    free(in);
    free(out);
    return id;
}

static int fileFrame(const char *dir, const char *name) {
    char path[600];
    snprintf(path, sizeof path, "%s/%s", dir, name);
    FILE *f = fopen(path, "rb");
    if (!f) { perror(path); exit(1); }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    unsigned char *p = malloc(n);
    if (fread(p, 1, n, f) != (size_t)n) exit(1);
    fclose(f);
    char how[700];
    snprintf(how, sizeof how, "-1\t0\t0\t0\t0\t0\tgolden %s", name);
    int id = addFrame(p, n, how);
    free(p);
    return id;
}

/* the decoding: a fresh DStream, fed by a schedule, as JEBOL's test feeds its own */
enum { WHOLE = 0, RANDOM_CHUNKS = 1, SMALL_CHUNKS = 2 };

static void decode(int frame, const char *mutation, const unsigned char *src, size_t len, int schedule,
                   unsigned seed, size_t outCap) {
    ZSTD_DStream *d = ZSTD_createDStream();
    check(ZSTD_initDStream(d));
    unsigned char *out = malloc(outCap ? outCap : 1);
    size_t inPos = 0, outPos = 0;
    uint64_t returns = FNV_START;
    long calls = 0;
    size_t r = 0;
    uint32_t s = seed;
    for (;;) {
        size_t inAvail, room;
        if (schedule == WHOLE) {
            inAvail = len - inPos;
            room = outCap - outPos;
        } else {
            int maxLog = schedule == RANDOM_CHUNKS ? 18 : 4;
            uint32_t inLog = RDG_rand(&s) % maxLog;
            size_t inChunk = 1 + (RDG_rand(&s) & ((1u << inLog) - 1));
            uint32_t outLog = RDG_rand(&s) % maxLog;
            size_t outChunk = RDG_rand(&s) & ((1u << outLog) - 1);
            inAvail = inChunk < len - inPos ? inChunk : len - inPos;
            room = outChunk < outCap - outPos ? outChunk : outCap - outPos;
        }
        ZSTD_inBuffer ib = {src, inPos + inAvail, inPos};
        ZSTD_outBuffer ob = {out, outPos + room, outPos};
        r = ZSTD_decompressStream(d, &ob, &ib);
        calls++;
        uint64_t asWord = (uint64_t)r;
        returns = fnvInto(returns, (const unsigned char *)&asWord, 8);
        inPos = ib.pos;
        outPos = ob.pos;
        if (ZSTD_isError(r)) break;
        if (r == 0 && inPos == len) break;
        if (calls >= 2000000) break;
    }
    fprintf(rows, "%d\t%s\t%d\t%u\t%zu\t%ld\t%zu\t%zu\t%016llx\t%016llx\t", frame, mutation, schedule, seed, outCap,
            calls, inPos, outPos, (unsigned long long)fnvInto(FNV_START, out, outPos), (unsigned long long)returns);
    if (ZSTD_isError(r)) fprintf(rows, "e%d\n", (int)ERR_getErrorCode(r));
    else fprintf(rows, "%zu\n", r);
    free(out);
    ZSTD_freeDStream(d);
}

/* a mutation: "-" whole, "t<n>" cut to n octets, "x<at>:<mask>" an octet xored, "a<n>:<seed>" n octets appended,
 * "f<id>" the frame followed by frame id */
static unsigned char *mutate(int frame, const char *mutation, size_t *len) {
    frame_t f = frameList[frame];
    unsigned char *all = NULL;
    FILE *b = fopen("zstd-frames.bin", "rb");
    size_t extra = 0, other = 0;
    int otherId = -1;
    if (mutation[0] == 'a') extra = (size_t)atol(mutation + 1);
    if (mutation[0] == 'f') { otherId = atoi(mutation + 1); other = frameList[otherId].len; }
    all = malloc(f.len + extra + other + 1);
    fseek(b, (long)f.at, SEEK_SET);
    if (fread(all, 1, f.len, b) != f.len) exit(1);
    if (otherId >= 0) {
        fseek(b, (long)frameList[otherId].at, SEEK_SET);
        if (fread(all + f.len, 1, other, b) != other) exit(1);
    }
    fclose(b);
    *len = f.len + other;
    switch (mutation[0]) {
    case 't': *len = (size_t)atol(mutation + 1); break;
    case 'x': {
        size_t at = (size_t)atol(mutation + 1);
        unsigned mask = (unsigned)atoi(strchr(mutation, ':') + 1);
        all[at] ^= (unsigned char)mask;
        break;
    }
    case 'a': {
        uint32_t s = (uint32_t)atol(strchr(mutation, ':') + 1);
        for (size_t i = 0; i < extra; i++) all[f.len + i] = (unsigned char)RDG_rand(&s);
        *len = f.len + extra;
        break;
    }
    }
    return all;
}

static void decodeAll(int frame, const char *mutation, size_t content, int heavy) {
    size_t len;
    unsigned char *src = mutate(frame, mutation, &len);
    size_t cap = content + 4096;
    decode(frame, mutation, src, len, WHOLE, 0, cap);
    decode(frame, mutation, src, len, WHOLE, 0, content / 2 + 1);
    if (content > 1000000) decode(frame, mutation, src, len, WHOLE, 0, content + 400000);
    decode(frame, mutation, src, len, RANDOM_CHUNKS, 7 + (unsigned)frame, cap);
    if (heavy || content < 300000) decode(frame, mutation, src, len, SMALL_CHUNKS, 11 + (unsigned)frame, cap);
    free(src);
}

static size_t sizes[4096];

static void damage(int frame, int count) {
    size_t len = frameList[frame].len;
    size_t content = sizes[frame];
    char m[64];
    size_t cuts[] = {0, 1, 3, 4, 5, 6, 8, 9, 12, len / 3, len / 2, len - 5, len - 4, len - 1};
    for (size_t i = 0; i < sizeof cuts / sizeof cuts[0]; i++) {
        if (cuts[i] < len) { snprintf(m, sizeof m, "t%zu", cuts[i]); decodeAll(frame, m, content, 0); }
    }
    for (int i = 0; i < count; i++) {
        size_t at = next((uint32_t)len);
        unsigned mask = 1u << next(8);
        if (i % 4 == 3) mask = 1 + next(255);
        snprintf(m, sizeof m, "x%zu:%u", at, mask);
        decodeAll(frame, m, content, 0);
    }
    uint32_t appended = 1 + next(9);
    snprintf(m, sizeof m, "a%u:%u", appended, next(1000));
    decodeAll(frame, m, content, 0);
}

int main(int argc, char **argv) {
    const char *golden = argv[1];
    bin = fopen("zstd-frames.bin", "wb");
    frames = fopen("zstd-frames.tsv", "w");
    fprintf(frames, "frame\tat\tlength\tkind\tsize\ta\tb\tseed\tinput\thow\n");
    pick = 2024;

    int ids[4096], n = 0;
    /* sizes either side of where the codec changes what it does, at every level */
    size_t small[] = {0, 1, 2, 3, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64, 65, 127, 128, 129, 255, 256, 257,
                      1023, 1024, 1025, 4095, 4096, 4097};
    for (size_t i = 0; i < sizeof small / sizeof small[0]; i++) {
        int level = 1 + (int)(i % 22);
        sizes[n] = small[i];
        ids[n++] = compressFrame(DATAGEN, small[i], 50, 0, 100 + (unsigned)i, level, 0, 0, 1, 0, 0);
    }
    for (int level = -5; level <= 22; level++) {
        if (level == 0) continue;
        sizes[n] = 20000;
        ids[n++] = compressFrame(DATAGEN, 20000, 40 + (unsigned)(level + 5) * 2, 0, 300 + (unsigned)(level + 5),
                                 level, level % 3 == 0, 0, 1, 0, 0);
    }
    size_t big[] = {65535, 65536, 65537, 131071, 131072, 131073, 262144, 262145, 600000};
    for (size_t i = 0; i < sizeof big / sizeof big[0]; i++) {
        int level = (int[]){1, 3, 5, 7, 9, 12, 16, 19, 22}[i];
        sizes[n] = big[i];
        ids[n++] = compressFrame(DATAGEN, big[i], i == 8 ? 97 : 85, 0, 400 + (unsigned)i, level, (int)(i & 1), 0, 1, 0, 0);
    }
    /* the size unknown, a small window so the stream wraps, a checksum */
    sizes[n] = 120000; ids[n++] = compressFrame(DATAGEN, 120000, 96, 0, 501, 3, 1, 10, 1, 1, 0);
    sizes[n] = 120000; ids[n++] = compressFrame(DATAGEN, 120000, 96, 0, 502, 6, 0, 12, 1, 0, 0);
    sizes[n] = 150000; ids[n++] = compressFrame(DATAGEN, 150000, 92, 0, 503, 1, 1, 17, 0, 0, 0);
    sizes[n] = 50000; ids[n++] = compressFrame(DATAGEN, 50000, 60, 0, 504, 19, 1, 0, 1, 1, 0);
    /* raw blocks, RLE blocks, empty */
    sizes[n] = 5000; ids[n++] = compressFrame(DATAGEN, 5000, 0, 0, 505, 3, 0, 10, 1, 0, 0);
    sizes[n] = 3000; ids[n++] = compressFrame(DATAGEN, 3000, 0, 0, 506, 1, 1, 0, 1, 0, 0);
    sizes[n] = 400000; ids[n++] = compressFrame(ZEROS, 400000, 'q', 0, 0, 3, 1, 0, 1, 0, 0);
    sizes[n] = 400000; ids[n++] = compressFrame(ZEROS, 400000, 0, 0, 0, 19, 0, 0, 0, 1, 0);
    /* literal blocks past 64K, which a stream splits between its output and its extra buffer */
    sizes[n] = 131072; ids[n++] = compressFrame(TWO_SYMBOLS, 131072, 50, 0, 507, 1, 1, 0, 1, 0, 1);
    sizes[n] = 200000; ids[n++] = compressFrame(TWO_SYMBOLS, 200000, 90, 0, 508, 3, 0, 0, 1, 1, 1);
    sizes[n] = 40000; ids[n++] = compressFrame(ONE_BYTE_THEN_NOISE, 40000, 7, 0, 509, 1, 0, 0, 1, 0, 1);
    /* offsets beyond 16MB, where the long-offset decoder takes over */
    sizes[n] = 17000000; ids[n++] = compressFrame(FAR_REPEATS, 17000000, 40, 0, 510, 19, 1, 25, 1, 0, 0);
    sizes[n] = 17000000; ids[n++] = compressFrame(FAR_REPEATS, 17000000, 300, 0, 511, 22, 0, 26, 0, 1, 0);
    sizes[n] = 17039360; ids[n++] = compressFrame(FAR_REPEATS, 17039360, 300, 120, 513, 1, 1, 25, 1, 0, 1);
    /* a window of 2^28, refused by a stream */
    sizes[n] = 20000; ids[n++] = compressFrame(DATAGEN, 20000, 60, 0, 512, 3, 0, 28, 0, 1, 0);
    int firstGolden = n;
    const char *goldens[] = {"golden-decompression/block-128k.zst", "golden-decompression/empty-block.zst",
                             "golden-decompression/rle-first-block.zst", "golden-decompression/zeroSeq_2B.zst",
                             "golden-decompression-errors/off0.bin.zst",
                             "golden-decompression-errors/truncated_huff_state.zst",
                             "golden-decompression-errors/zeroSeq_extraneous.zst"};
    size_t goldenSizes[] = {131072, 0, 1048576, 13, 0, 0, 0};
    for (size_t i = 0; i < sizeof goldens / sizeof goldens[0]; i++) {
        sizes[n] = goldenSizes[i] + 1024;
        ids[n++] = fileFrame(golden, goldens[i]);
    }
    /* a skippable frame */
    {
        unsigned char sk[8 + 20] = {0x5A, 0x2A, 0x4D, 0x18, 20, 0, 0, 0};
        for (int i = 0; i < 20; i++) sk[8 + i] = (unsigned char)i;
        sizes[n] = 0;
        ids[n++] = addFrame(sk, sizeof sk, "-2\t0\t0\t0\t0\t0\tskippable");
    }
    fclose(bin);
    fclose(frames);

    rows = fopen("zstd-decode.tsv", "w");
    fprintf(rows, "frame\tmutation\tschedule\tseed\troom\tcalls\tread\twritten\toutput\treturns\tresult\n");
    for (int i = 0; i < n; i++) {
        int heavy = sizes[i] <= 300000;
        decodeAll(ids[i], "-", sizes[i], heavy);
    }
    /* frames one after another, and a skippable frame first */
    char m[32];
    snprintf(m, sizeof m, "f%d", ids[3]); decodeAll(ids[40], m, sizes[40] + sizes[3], 1);
    snprintf(m, sizeof m, "f%d", ids[45]); decodeAll(ids[n - 1], m, sizes[45], 1);
    snprintf(m, sizeof m, "f%d", ids[firstGolden + 1]); decodeAll(ids[50], m, sizes[50], 1);
    for (int i = 0; i < n; i++) {
        if (sizes[i] > 1000000) { damage(ids[i], 2); continue; }
        damage(ids[i], i < 30 ? 6 : 14);
    }
    fclose(rows);
    return 0;
}
