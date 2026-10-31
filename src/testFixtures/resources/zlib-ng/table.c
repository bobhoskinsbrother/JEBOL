#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include "zlib-ng.h"

static const char *vocabulary[] = {"the ", "quick ", "brown ", "fox ", "jumps ", "over ", "lazy ",
    "dog ", "REBOL ", "block! ", "[", "]\n", "compress ", "level ", "12 ", "binary ", "#{00} ", "series "};

static uint32_t state;
static int draw(void) { state = state * 1103515245u + 12345u; return (int)((state >> 16) & 0x7FFF); }

static void words(unsigned char *out, int length, int seed) {
    state = (uint32_t)seed;
    int at = 0;
    while (at < length) {
        const char *w = vocabulary[draw() % 18];
        for (int k = 0; w[k] && at < length; k++) out[at++] = (unsigned char)w[k];
    }
}

static void noise(unsigned char *out, int length, int seed) {
    state = (uint32_t)seed;
    for (int at = 0; at < length; at++) out[at] = (unsigned char)draw();
}

static void fixture(const char *kind, unsigned char *out, int length) {
    if (!strcmp(kind, "words")) words(out, length, 1);
    else if (!strcmp(kind, "noise")) noise(out, length, 2);
    else if (!strcmp(kind, "zeros")) memset(out, 0, length);
    else {
        unsigned char *w = malloc(length + 1), *n = malloc(length + 1);
        words(w, length, 3); noise(n, length, 4);
        for (int at = 0; at < length; at++) out[at] = (at / 3000) % 2 == 0 ? w[at] : n[at];
        free(w); free(n);
    }
}

static uint64_t fnv(uint64_t h, const unsigned char *p, size_t n) {
    for (size_t i = 0; i < n; i++) { h ^= p[i]; h *= 0x100000001b3ULL; }
    return h;
}

#define BASIS 0xcbf29ce484222325ULL

static size_t squeeze(const unsigned char *in, size_t len, int level, int wbits, int mem, int strategy,
                      size_t chunk, int flushmode, size_t outstep, unsigned char *out, size_t cap,
                      uint64_t *codes, int *failed) {
    zng_stream s; memset(&s, 0, sizeof s);
    *failed = 0; *codes = BASIS;
    if (zng_deflateInit2(&s, level, Z_DEFLATED, wbits, mem, strategy) != Z_OK) { *failed = 1; return 0; }
    size_t pos = 0, at = 0;
    do {
        size_t n = chunk == 0 ? len : (len - at < chunk ? len - at : chunk);
        s.next_in = in + at; s.avail_in = (uint32_t)n; at += n;
        for (;;) {
            size_t room = cap - pos;
            if (outstep && room > outstep) room = outstep;
            s.next_out = out + pos; s.avail_out = (uint32_t)room;
            int r = zng_deflate(&s, chunk ? flushmode : Z_NO_FLUSH);
            pos += room - s.avail_out;
            unsigned char rc = (unsigned char)(r & 0xff); *codes = fnv(*codes, &rc, 1);
            if (s.avail_in == 0 && s.avail_out != 0) break;
        }
    } while (at < len);
    for (;;) {
        size_t room = cap - pos;
        if (outstep && room > outstep) room = outstep;
        s.next_out = out + pos; s.avail_out = (uint32_t)room;
        int r = zng_deflate(&s, Z_FINISH);
        pos += room - s.avail_out;
        unsigned char rc = (unsigned char)(r & 0xff); *codes = fnv(*codes, &rc, 1);
        if (r == Z_STREAM_END) break;
    }
    zng_deflateEnd(&s);
    return pos;
}

static const char *kinds[] = {"words", "noise", "mixed", "zeros"};
static const int lengths[] = {0, 1, 2, 3, 4, 5, 257, 258, 259, 261, 262, 263, 16383, 16384, 16385,
    32767, 32768, 32769, 65535, 65536, 65537, 131073, 200000};

int main(void) {
    size_t cap = 4 * 1024 * 1024;
    unsigned char *in = malloc(cap), *out = malloc(cap), *dec = malloc(cap);
    FILE *t = fopen("zlib-ng-deflate.tsv", "w");
    fprintf(t, "kind\tlength\tlevel\twindow\tmem\tstrategy\tchunk\tflush\toutstep\tout length\tout digest\tcodes digest\n");
    for (int k = 0; k < 4; k++) for (int l = 0; l < 23; l++) {
        int len = lengths[l];
        fixture(kinds[k], in, len);
        int cs[256][7]; int nc = 0;
        int windows[] = {-15, 15, 31, 9, 12};
        for (int lv = 0; lv <= 9; lv++) for (int w = 0; w < 5; w++) {
            if (w >= 3 && lv != 1 && lv != 6 && lv != 9) continue;
            int c[7] = {lv, windows[w], 8, 0, 0, 0, 0}; memcpy(cs[nc++], c, sizeof c);
        }
        for (int st = 1; st <= 4; st++) { int c[7] = {6, 15, 8, st, 0, 0, 0}; memcpy(cs[nc++], c, sizeof c); }
        for (int st = 1; st <= 4; st++) { int c[7] = {1, -15, 8, st, 1000, 2, 0}; memcpy(cs[nc++], c, sizeof c); }
        { int c[7] = {9, 15, 1, 0, 0, 0, 0}; memcpy(cs[nc++], c, sizeof c); }
        { int c[7] = {6, 15, 9, 0, 0, 0, 0}; memcpy(cs[nc++], c, sizeof c); }
        int flushes[] = {0, 1, 2, 3, 5};
        int streamed[] = {1, 2, 3, 6, 9};
        for (int lv = 0; lv < 5; lv++) for (int f = 0; f < 5; f++) {
            int c[7] = {streamed[lv], -15, 8, 0, 1000, flushes[f], 0}; memcpy(cs[nc++], c, sizeof c);
        }
        if (len <= 20000) for (int lv = 1; lv <= 9; lv += 4) {
            int c[7] = {lv, 15, 8, 0, 0, 0, 7}; memcpy(cs[nc++], c, sizeof c);
            int d[7] = {lv, -15, 8, 0, 4096, 2, 100}; memcpy(cs[nc++], d, sizeof d);
        }
        for (int c = 0; c < nc; c++) {
            uint64_t codes; int failed;
            size_t n = squeeze(in, len, cs[c][0], cs[c][1], cs[c][2], cs[c][3], cs[c][4], cs[c][5], cs[c][6],
                               out, cap, &codes, &failed);
            fprintf(t, "%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%zu\t%016llx\t%016llx\n", kinds[k], len,
                    cs[c][0], cs[c][1], cs[c][2], cs[c][3], cs[c][4], cs[c][5], cs[c][6], n,
                    (unsigned long long)fnv(BASIS, out, n), (unsigned long long)codes);
        }
    }
    fclose(t);
    FILE *u = fopen("zlib-ng-inflate.tsv", "w");
    fprintf(u, "kind\tlength\tlevel\twindow\tdamage\tdecode window\tchunk\toutstep\tresult\ttotal in\ttotal out\tout digest\n");
    int dlengths[] = {0, 1, 300, 40000, 70000};
    int wraps[] = {-15, 15, 31};
    int dwindows[] = {-15, 15, 31, 47};
    int chunks[] = {0, 7};
    int steps[] = {0, 300};
    for (int k = 0; k < 4; k++) for (int l = 0; l < 5; l++) for (int lv = 1; lv <= 6; lv += 5) for (int w = 0; w < 3; w++) {
        int len = dlengths[l];
        fixture(kinds[k], in, len);
        uint64_t codes; int failed;
        size_t n = squeeze(in, len, lv, wraps[w], 8, 0, 0, 0, 0, out, cap, &codes, &failed);
        for (int dmg = 0; dmg < 6; dmg++) {
            unsigned char *b = malloc(n + 1); memcpy(b, out, n);
            size_t bn = n;
            if (dmg == 1) bn = n / 2;
            if (dmg == 2 && n > 0) bn = n - 1;
            if (dmg == 3 && n > 0) b[(n * 7) / 13] ^= 8;
            if (dmg == 4 && n > 2) b[2] ^= 1;
            if (dmg == 5 && n > 0) b[n - 1] ^= 0x80;
            for (int dw = 0; dw < 4; dw++) for (int c = 0; c < 2; c++) for (int o = 0; o < 2; o++) {
                zng_stream s; memset(&s, 0, sizeof s);
                zng_inflateInit2(&s, dwindows[dw]);
                size_t at = 0, pos = 0; int r = 0, idle = 0;
                for (;;) {
                    size_t m = bn - at;
                    if (chunks[c] && m > (size_t)chunks[c]) m = chunks[c];
                    s.next_in = b + at; s.avail_in = (uint32_t)m;
                    size_t room = cap - pos;
                    if (steps[o] && room > (size_t)steps[o]) room = steps[o];
                    s.next_out = dec + pos; s.avail_out = (uint32_t)room;
                    r = zng_inflate(&s, Z_NO_FLUSH);
                    size_t used = m - s.avail_in, made = room - s.avail_out;
                    at += used; pos += made;
                    if (r == Z_STREAM_END || r == Z_NEED_DICT || (r < 0 && r != Z_BUF_ERROR)) break;
                    if (used == 0 && made == 0) { if (++idle >= 2) break; } else idle = 0;
                    if (r == Z_BUF_ERROR && at >= bn) break;
                }
                fprintf(u, "%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%llu\t%llu\t%016llx\n", kinds[k], len, lv, wraps[w],
                        dmg, dwindows[dw], chunks[c], steps[o], r, (unsigned long long)s.total_in,
                        (unsigned long long)s.total_out, (unsigned long long)fnv(BASIS, dec, pos));
                zng_inflateEnd(&s);
            }
            free(b);
        }
    }
    fclose(u);
    return 0;
}
