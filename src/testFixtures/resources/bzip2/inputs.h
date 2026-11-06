#ifndef BZIP2_INPUTS_H
#define BZIP2_INPUTS_H

#include <stdint.h>
#include <stdlib.h>

enum { NOISE = 0, NO_RUNS = 1, CONSTANT = 2, RUNS = 3, PERIODIC = 4, WORDS = 5, FIBONACCI = 6 };

static uint32_t nextRandom(uint32_t *seed) {
    uint32_t value = *seed;
    value *= 2654435761u;
    value ^= 2246822519u;
    value = (value << 13) | (value >> 19);
    *seed = value;
    return value >> 5;
}

static unsigned char *make(int kind, size_t size, unsigned a, unsigned b, unsigned seed) {
    unsigned char *out = malloc(size ? size : 1);
    uint32_t state = seed;
    switch (kind) {
    case NOISE:
        for (size_t at = 0; at < size; at++) out[at] = (unsigned char)(nextRandom(&state) % a);
        break;
    case NO_RUNS: {
        unsigned previous = 256;
        for (size_t at = 0; at < size; at++) {
            unsigned value = nextRandom(&state) % a;
            if (value == previous) value = (value + 1) % a;
            out[at] = (unsigned char)value;
            previous = value;
        }
        break;
    }
    case CONSTANT:
        for (size_t at = 0; at < size; at++) out[at] = (unsigned char)a;
        break;
    case RUNS: {
        size_t at = 0;
        unsigned value = 0;
        while (at < size) {
            unsigned length = a + (b ? nextRandom(&state) % b : 0);
            for (unsigned i = 0; i < length && at < size; i++) out[at++] = (unsigned char)value;
            value = (value + 1 + nextRandom(&state) % 250) & 0xFF;
        }
        break;
    }
    case PERIODIC:
        for (size_t at = 0; at < size; at++) {
            out[at] = at < a ? (unsigned char)nextRandom(&state) : out[at - a];
        }
        break;
    case WORDS: {
        size_t at = 0;
        while (at < size) {
            uint32_t word = nextRandom(&state) % a;
            uint32_t letters = word;
            unsigned length = 2 + word % 8;
            for (unsigned i = 0; i < length && at < size; i++) {
                letters = letters * 31 + 7;
                out[at++] = (unsigned char)('a' + (letters >> 8) % 26);
            }
            if (at < size) out[at++] = ' ';
        }
        break;
    }
    default: {
        uint64_t weights[64];
        uint64_t total = 0;
        weights[0] = 1;
        weights[1] = 1;
        for (unsigned i = 2; i < a; i++) weights[i] = weights[i - 1] + weights[i - 2];
        for (unsigned i = 0; i < a; i++) total += weights[i];
        for (size_t at = 0; at < size; at++) {
            uint64_t pick = (((uint64_t)nextRandom(&state) << 27) | nextRandom(&state)) % total;
            unsigned symbol = 0;
            while (pick >= weights[symbol]) {
                pick -= weights[symbol];
                symbol++;
            }
            out[at] = (unsigned char)(symbol * b);
        }
        break;
    }
    }
    return out;
}

#endif
