#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define FSE_STATIC_LINKING_ONLY
#define HUF_STATIC_LINKING_ONLY
#define ZSTD_STATIC_LINKING_ONLY
#include "zstd.h"
#include "common/zstd_internal.h"
#include "common/bitstream.h"
#include "common/fse.h"
#include "common/huf.h"
#include "common/error_private.h"

static uint64_t state = 0x9E3779B97F4A7C15ull;

static uint32_t next(void) {
    state ^= state << 13;
    state ^= state >> 7;
    state ^= state << 17;
    return (uint32_t)(state >> 16);
}

static uint64_t fnv(const unsigned char *p, size_t n) {
    uint64_t h = 0xcbf29ce484222325ull;
    for (size_t i = 0; i < n; i++) {
        h ^= p[i];
        h *= 0x100000001b3ull;
    }
    return h;
}

static void hex(FILE *out, const unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) fprintf(out, "%02x", p[i]);
    if (n == 0) fputc('-', out);
}

static void result(FILE *out, size_t r) {
    if (ERR_isError(r)) fprintf(out, "e%d", (int)ERR_getErrorCode(r));
    else fprintf(out, "%zu", r);
}

static void bits(FILE *out) {
    static const size_t lengths[] = {1, 2, 3, 7, 8, 9, 15, 16, 17, 24, 40};
    fprintf(out, "input\topened\tsteps\n");
    for (size_t l = 0; l < sizeof(lengths) / sizeof(lengths[0]); l++) {
        for (int c = 0; c < 60; c++) {
            unsigned char buffer[64];
            size_t n = lengths[l];
            for (size_t i = 0; i < n; i++) buffer[i] = (unsigned char)next();
            if (c % 7 == 0) buffer[n - 1] = 0;
            if (c % 5 == 1) buffer[n - 1] = 1;
            BIT_DStream_t d;
            size_t opened = BIT_initDStream(&d, buffer, n);
            hex(out, buffer, n);
            fputc('\t', out);
            result(out, opened);
            fputc('\t', out);
            if (!ERR_isError(opened)) {
                for (int s = 0; s < 40; s++) {
                    int kind = (int)(next() % 7);
                    unsigned width = 1 + next() % 24;
                    if (d.bitsConsumed > 64) kind = 4;
                    unsigned long long value = 0;
                    int status = -1;
                    switch (kind) {
                        case 0: value = BIT_lookBits(&d, width); break;
                        case 1: value = BIT_lookBitsFast(&d, width); break;
                        case 2: value = BIT_readBits(&d, width); break;
                        case 3: value = BIT_readBitsFast(&d, width); break;
                        case 4: status = BIT_reloadDStream(&d); break;
                        case 5: status = BIT_reloadDStreamFast(&d); break;
                        default: BIT_skipBits(&d, width); break;
                    }
                    int inside = d.ptr >= d.start && d.ptr <= d.start + n;
                    fprintf(out, "%s%c%u:%llx:%d:%u:%ld:%u", s ? " " : "", "LFRQDUK"[kind], width, value, status,
                            (unsigned)d.bitsConsumed, inside ? (long)(d.ptr - d.start) : -1L,
                            BIT_endOfDStream(&d));
                    if (status == BIT_DStream_overflow) break;
                }
            }
            fputc('\n', out);
        }
    }
}

static size_t histogramFor(unsigned char *src, size_t n, int kind) {
    for (size_t i = 0; i < n; i++) {
        uint32_t r = next();
        switch (kind) {
            case 0: src[i] = (unsigned char)(r % 2 ? 'a' : (r % 7 ? 'b' : 'c')); break;
            case 1: src[i] = (unsigned char)(r % 16); break;
            case 2: src[i] = (unsigned char)(r % 64 + 32); break;
            case 3: src[i] = (unsigned char)r; break;
            case 4: {
                unsigned v = 0;
                while (v < 200 && (r & 1)) { v++; r >>= 1; if (!r) r = next(); }
                src[i] = (unsigned char)v;
                break;
            }
            default: src[i] = (unsigned char)(r % 5 == 0 ? r % 256 : 'e'); break;
        }
    }
    return n;
}

static void ncount(FILE *out) {
    fprintf(out, "input\tmax\tread\tmaxSymbol\ttableLog\tcounts\n");
    static const unsigned maxes[] = {1, 2, 3, 12, 28, 31, 35, 52, 63, 255};
    for (int c = 0; c < 600; c++) {
        unsigned maxSymbolValue = maxes[next() % (sizeof(maxes) / sizeof(maxes[0]))];
        unsigned tableLog = 5 + next() % 8;
        unsigned count[256];
        memset(count, 0, sizeof(count));
        size_t total = 0;
        unsigned spread = 1 + next() % (maxSymbolValue + 1);
        for (unsigned s = 0; s <= maxSymbolValue; s++) {
            if (s < spread || next() % 3 == 0) {
                count[s] = (next() % 4 == 0) ? 1 : next() % 300;
                total += count[s];
            }
        }
        if (total == 0) { count[0] = 5; count[maxSymbolValue] = 3; total = 8; }
        unsigned largest = 0;
        for (unsigned s = 0; s <= maxSymbolValue; s++) if (count[s]) largest = s;
        short norm[256];
        unsigned log = FSE_optimalTableLog(tableLog, total, largest);
        size_t normalized = FSE_normalizeCount(norm, log, count, total, largest, next() % 2);
        if (ERR_isError(normalized)) continue;
        unsigned char header[512];
        size_t written = FSE_writeNCount(header, sizeof(header), norm, largest, log);
        if (ERR_isError(written)) continue;
        for (int damage = 0; damage < 6; damage++) {
            unsigned char input[520];
            size_t n = written;
            memcpy(input, header, written);
            if (damage == 1) n = written - 1;
            if (damage == 2) n = n > 3 ? n / 2 : n;
            if (damage == 3) input[(n * 7) / 13] ^= (unsigned char)(1 << (next() % 8));
            if (damage == 4) input[0] ^= 0x0F;
            if (damage == 5) { input[n] = (unsigned char)next(); input[n + 1] = (unsigned char)next(); n += 2; }
            unsigned asked = (damage == 0 && c % 4 == 0) ? (largest > 0 ? largest - 1 : 0) : maxSymbolValue;
            if (c % 9 == 0) asked = 255;
            short read[256];
            unsigned readMax = asked;
            unsigned readLog = 0;
            size_t r = FSE_readNCount(read, &readMax, &readLog, input, n);
            hex(out, input, n);
            fprintf(out, "\t%u\t", asked);
            result(out, r);
            if (ERR_isError(r)) {
                fprintf(out, "\t-\t-\t-\n");
            } else {
                fprintf(out, "\t%u\t%u\t%016llx\n", readMax, readLog,
                        (unsigned long long)fnv((const unsigned char *)read, (readMax + 1) * sizeof(short)));
            }
        }
    }
}

static unsigned decoderWorkspace[HUF_DECOMPRESS_WORKSPACE_SIZE_U32];

static void freshTable(HUF_DTable *table) {
    memset(table, 0x5A, HUF_DTABLE_SIZE(12) * sizeof(HUF_DTable));
    table[0] = (HUF_DTable)(12 * 0x1000001);
}

static void stats(FILE *out, const unsigned char *input, size_t n) {
    unsigned char weights[HUF_SYMBOLVALUE_MAX + 1];
    U32 rank[HUF_TABLELOG_MAX + 1];
    U32 symbols = 0;
    U32 log = 0;
    size_t r = HUF_readStats(weights, HUF_SYMBOLVALUE_MAX + 1, rank, &symbols, &log, input, n);
    hex(out, input, n);
    fputc('\t', out);
    result(out, r);
    if (ERR_isError(r)) fprintf(out, "\t-\t-\t-\n");
    else fprintf(out, "\t%u\t%u\t%016llx\n", symbols, log, (unsigned long long)fnv(weights, symbols));
}

static void huffman(FILE *rows, FILE *statRows) {
    static const size_t sizes[] = {1, 2, 5, 6, 7, 8, 9, 15, 16, 17, 31, 32, 33, 50, 64, 100, 255, 256,
                                   257, 500, 1000, 1023, 1024, 1025, 2000, 6000, 12000};
    fprintf(rows, "decoder\tsize\tinput\tresult\tdigest\n");
    fprintf(statRows, "input\tread\tsymbols\ttableLog\tweights\n");
    unsigned char compressed[16384];
    unsigned char decoded[16384 + 64];
    unsigned char source[16384];
    HUF_DTable table[HUF_DTABLE_SIZE(12)];
    for (size_t z = 0; z < sizeof(sizes) / sizeof(sizes[0]); z++) {
        for (int kind = 0; kind < 6; kind++) {
            for (int streams = 1; streams <= 4; streams += 3) {
                for (unsigned maxLog = 11; maxLog <= 12; maxLog++) {
                    if (sizes[z] > 2000 && (streams == 1 || kind == 1 || kind == 2 || kind == 3)) continue;
                    size_t n = histogramFor(source, sizes[z], kind);
                    HUF_CElt ctable[HUF_CTABLE_SIZE_ST(255)];
                    HUF_repeat repeat = HUF_repeat_none;
                    U64 workspace[HUF_WORKSPACE_SIZE_U64];
                    size_t c = streams == 1
                            ? HUF_compress1X_repeat(compressed, sizeof(compressed), source, n, 255, maxLog,
                                                    workspace, sizeof(workspace), ctable, &repeat, 0)
                            : HUF_compress4X_repeat(compressed, sizeof(compressed), source, n, 255, maxLog,
                                                    workspace, sizeof(workspace), ctable, &repeat, 0);
                    if (ERR_isError(c) || c <= 1) continue;
                    {
                        U32 header = 0;
                        unsigned char weights[256];
                        U32 rank[13], symbols, log;
                        header = (U32)HUF_readStats(weights, 256, rank, &symbols, &log, compressed, c);
                        if (!ERR_isError(header)) {
                            stats(statRows, compressed, header);
                            if (header > 1) stats(statRows, compressed, header - 1);
                            unsigned char damaged[600];
                            memcpy(damaged, compressed, header);
                            damaged[header / 2] ^= (unsigned char)(1 << (next() % 8));
                            stats(statRows, damaged, header);
                        }
                    }
                    for (int damage = 0; damage < 7; damage++) {
                        unsigned char input[16384];
                        size_t m = c;
                        size_t size = n;
                        memcpy(input, compressed, c);
                        if (damage == 1) m = c - 1;
                        if (damage == 2) input[(m * 7) / 13] ^= (unsigned char)(1 << (next() % 8));
                        if (damage == 3) input[m - 1] ^= 0x80;
                        if (damage == 4) size = n + 1;
                        if (damage == 5) size = n > 1 ? n - 1 : n;
                        if (damage == 6) { input[m] = (unsigned char)next(); m++; }
                        for (int decoder = 0; decoder < 3; decoder++) {
                            if (streams == 1 && decoder == 0) continue;
                            if (streams == 4 && decoder != 0) continue;
                            freshTable(table);
                            memset(decoded, 0, sizeof(decoded));
                            size_t r;
                            switch (decoder) {
                                case 0:
                                    r = HUF_decompress4X_hufOnly_wksp(table, decoded, size, input, m, decoderWorkspace,
                                                                      sizeof(decoderWorkspace), HUF_flags_bmi2);
                                    break;
                                case 1:
                                    r = HUF_decompress1X1_DCtx_wksp(table, decoded, size, input, m, decoderWorkspace,
                                                                    sizeof(decoderWorkspace), HUF_flags_bmi2);
                                    break;
                                default:
                                    r = HUF_decompress1X2_DCtx_wksp(table, decoded, size, input, m, decoderWorkspace,
                                                                    sizeof(decoderWorkspace), HUF_flags_bmi2);
                                    break;
                            }
                            fprintf(rows, "%d\t%zu\t", decoder, size);
                            hex(rows, input, m);
                            fputc('\t', rows);
                            result(rows, r);
                            if (ERR_isError(r)) fprintf(rows, "\t-\n");
                            else fprintf(rows, "\t%016llx\n", (unsigned long long)fnv(decoded, size));
                        }
                    }
                }
            }
        }
    }
}

static void frameHeaders(FILE *out) {
    fprintf(out, "input\tread\tskippable\tcontentSize\twindowSize\tblockSizeMax\tdictionaryId\tchecksum\theaderSize\n");
    unsigned char input[32];
    for (int descriptor = 0; descriptor < 256; descriptor++) {
        for (int c = 0; c < 6; c++) {
            input[0] = 0x28; input[1] = 0xB5; input[2] = 0x2F; input[3] = 0xFD;
            input[4] = (unsigned char)descriptor;
            for (int i = 5; i < 32; i++) input[i] = (unsigned char)next();
            if (c == 1) input[5] = 0xA8;
            if (c == 2) input[5] = 0xAF;
            if (c == 3) input[5] = 0;
            size_t lengths[] = {18, (size_t)(next() % 19)};
            for (int l = 0; l < 2; l++) {
                ZSTD_FrameHeader header;
                memset(&header, 0, sizeof(header));
                size_t r = ZSTD_getFrameHeader(&header, input, lengths[l]);
                hex(out, input, lengths[l]);
                fputc('\t', out);
                result(out, r);
                if (r != 0) fprintf(out, "\t-\t-\t-\t-\t-\t-\t-\n");
                else fprintf(out, "\t%d\t%llu\t%llu\t%u\t%u\t%u\t%u\n", header.frameType == ZSTD_skippableFrame,
                             header.frameContentSize, header.windowSize, header.blockSizeMax, header.dictID,
                             header.checksumFlag, header.headerSize);
            }
        }
    }
    for (int c = 0; c < 200; c++) {
        size_t n = next() % 12;
        for (size_t i = 0; i < n; i++) input[i] = (unsigned char)next();
        if (c % 3 == 0 && n >= 4) { input[0] = (unsigned char)(0x50 + c % 16); input[1] = 0x2A; input[2] = 0x4D; input[3] = 0x18; }
        if (c % 3 == 1) { unsigned char magic[4] = {0x28, 0xB5, 0x2F, 0xFD}; memcpy(input, magic, n < 4 ? n : 4); }
        if (c % 6 == 2 && n > 0) { unsigned char skip[4] = {0x5F, 0x2A, 0x4D, 0x18}; memcpy(input, skip, n < 4 ? n : 4); }
        ZSTD_FrameHeader header;
        memset(&header, 0, sizeof(header));
        size_t r = ZSTD_getFrameHeader(&header, input, n);
        hex(out, input, n);
        fputc('\t', out);
        result(out, r);
        if (r != 0) fprintf(out, "\t-\t-\t-\t-\t-\t-\t-\n");
        else fprintf(out, "\t%d\t%llu\t%llu\t%u\t%u\t%u\t%u\n", header.frameType == ZSTD_skippableFrame,
                     header.frameContentSize, header.windowSize, header.blockSizeMax, header.dictID,
                     header.checksumFlag, header.headerSize);
    }
}

static FILE *table(const char *name) {
    FILE *f = fopen(name, "w");
    if (!f) { perror(name); exit(1); }
    return f;
}

int main(void) {
    FILE *f;
    f = table("zstd-bits.tsv"); bits(f); fclose(f);
    f = table("zstd-ncount.tsv"); ncount(f); fclose(f);
    {
        FILE *h = table("zstd-huffman.tsv");
        FILE *s = table("zstd-huffman-stats.tsv");
        huffman(h, s);
        fclose(h);
        fclose(s);
    }
    f = table("zstd-frame-header.tsv"); frameHeaders(f); fclose(f);
    return 0;
}
