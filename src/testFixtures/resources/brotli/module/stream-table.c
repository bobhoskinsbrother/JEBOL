#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "brotli/decode.h"
#include "brotli/encode.h"
#include "common/dictionary.h"
#include "common/transform.h"

static const char WORDS[] = "the quick brown fox jumps over a lazy dog ";

static uint8_t *make_input(const char *kind, size_t size) {
  uint8_t *out = malloc(size ? size : 1);
  uint64_t x = 12345;
  size_t words = strlen(WORDS);
  for (size_t at = 0; at < size; at++) {
    x = (x * 1103515245u + 12345u) & 2147483647u;
    out[at] = strcmp(kind, "noise") == 0 ? (uint8_t)((x >> 16) & 255)
                                          : (uint8_t)WORDS[(x >> 16) % words];
  }
  return out;
}

static uint64_t fnv(const uint8_t *data, size_t size, uint64_t hash) {
  for (size_t at = 0; at < size; at++) {
    hash ^= data[at];
    hash *= 0x100000001b3ULL;
  }
  return hash;
}

#define FNV_START 0xcbf29ce484222325ULL

typedef struct {
  uint64_t whole;
  size_t total;
  int calls;
} Record;

static void say_call(Record *record, char op, const uint8_t *out, size_t size, int result) {
  printf("\t%c%d:%zu:%016llx", op, result, size, (unsigned long long)fnv(out, size, FNV_START));
  record->whole = fnv(out, size, record->whole);
  record->total += size;
  record->calls++;
}

static void encode_call(BrotliEncoderState *s, BrotliEncoderOperation op, char letter,
                        const uint8_t *in, size_t size, Record *record) {
  size_t available_in = size;
  const uint8_t *next_in = in;
  size_t capacity = BrotliEncoderMaxCompressedSize(size) + 1024;
  uint8_t *buffer = malloc(capacity);
  size_t total = 0;
  int result = 1;
  do {
    if (capacity - total < 65536) {
      capacity *= 2;
      buffer = realloc(buffer, capacity);
    }
    size_t available_out = capacity - total;
    uint8_t *next_out = buffer + total;
    result = BrotliEncoderCompressStream(s, op, &available_in, &next_in, &available_out, &next_out, NULL);
    total = capacity - available_out;
    if (!result) break;
  } while (available_in > 0 || BrotliEncoderHasMoreOutput(s) ||
           (op == BROTLI_OPERATION_FINISH && !BrotliEncoderIsFinished(s)));
  say_call(record, letter, buffer, total, result);
  free(buffer);
}

static void encode_case(const char *kind, size_t size, int level, int mode, const char *hint,
                        const char *ops) {
  uint8_t *input = make_input(kind, size);
  BrotliEncoderState *s = BrotliEncoderCreateInstance(NULL, NULL, NULL);
  Record record = {FNV_START, 0, 0};
  BrotliEncoderSetParameter(s, BROTLI_PARAM_QUALITY, (uint32_t)level);
  BrotliEncoderSetParameter(s, BROTLI_PARAM_MODE, (uint32_t)mode);
  int late = strncmp(ops, "late-hint:", 10) == 0;
  if (strcmp(hint, "-") != 0 && !late) {
    BrotliEncoderSetParameter(s, BROTLI_PARAM_SIZE_HINT, (uint32_t)strtoull(hint, NULL, 10));
  }
  printf("encode %s %zu %d %d %s %s", kind, size, level, mode, hint, ops);
  if (strcmp(ops, "finish") == 0) {
    encode_call(s, BROTLI_OPERATION_FINISH, 'E', input, size, &record);
  } else if (late) {
    size_t first = strtoull(ops + 10, NULL, 10);
    if (first > size) first = size;
    encode_call(s, BROTLI_OPERATION_PROCESS, 'P', input, first, &record);
    int accepted = BrotliEncoderSetParameter(s, BROTLI_PARAM_SIZE_HINT, (uint32_t)strtoull(hint, NULL, 10));
    printf("\th%d", accepted);
    encode_call(s, BROTLI_OPERATION_FINISH, 'E', input + first, size - first, &record);
  } else {
    char style[16];
    size_t chunk = 0;
    sscanf(ops, "%15[^:]:%zu", style, &chunk);
    for (size_t at = 0; at < size; at += chunk) {
      size_t piece = size - at < chunk ? size - at : chunk;
      if (strcmp(style, "flush") == 0) {
        encode_call(s, BROTLI_OPERATION_FLUSH, 'F', input + at, piece, &record);
      } else {
        encode_call(s, BROTLI_OPERATION_PROCESS, 'P', input + at, piece, &record);
        if (strcmp(style, "read") == 0) {
          encode_call(s, BROTLI_OPERATION_FLUSH, 'R', input, 0, &record);
        }
      }
    }
    if (size == 0 && strcmp(style, "flush") == 0) {
      encode_call(s, BROTLI_OPERATION_FLUSH, 'F', input, 0, &record);
    }
    if (size == 0 && strcmp(style, "read") == 0) {
      encode_call(s, BROTLI_OPERATION_FLUSH, 'R', input, 0, &record);
    }
    encode_call(s, BROTLI_OPERATION_FINISH, 'E', input, 0, &record);
  }
  printf("\ttotal:%zu:%016llx\n", record.total, (unsigned long long)record.whole);
  BrotliEncoderDestroyInstance(s);
  free(input);
}

static void decode_case(const char *kind, size_t size, int level, size_t chunk) {
  uint8_t *input = make_input(kind, size);
  size_t encoded_size = BrotliEncoderMaxCompressedSize(size) + 1024;
  uint8_t *encoded = malloc(encoded_size);
  BrotliEncoderCompress(level, BROTLI_DEFAULT_WINDOW, BROTLI_MODE_GENERIC, size, input, &encoded_size, encoded);
  BrotliDecoderState *s = BrotliDecoderCreateInstance(NULL, NULL, NULL);
  Record record = {FNV_START, 0, 0};
  uint8_t *buffer = malloc(size + 1);
  printf("decode %s %zu %d %zu\tstream:%zu:%016llx", kind, size, level, chunk, encoded_size,
         (unsigned long long)fnv(encoded, encoded_size, FNV_START));
  for (size_t at = 0; at < encoded_size; at += chunk) {
    size_t piece = encoded_size - at < chunk ? encoded_size - at : chunk;
    size_t available_in = piece;
    const uint8_t *next_in = encoded + at;
    size_t available_out = size + 1;
    uint8_t *next_out = buffer;
    BrotliDecoderResult result =
        BrotliDecoderDecompressStream(s, &available_in, &next_in, &available_out, &next_out, NULL);
    say_call(&record, 'D', buffer, size + 1 - available_out, (int)result);
  }
  printf("\tfinished:%d\ttotal:%zu:%016llx\n", BrotliDecoderIsFinished(s), record.total,
         (unsigned long long)record.whole);
  BrotliDecoderDestroyInstance(s);
  free(buffer);
  free(encoded);
  free(input);
}

typedef struct {
  uint8_t *data;
  size_t at;
} BitWriter;

static void put_bits(BitWriter *w, int count, uint32_t value) {
  for (int each = 0; each < count; each++) {
    if ((value >> each) & 1) w->data[w->at >> 3] |= (uint8_t)(1u << (w->at & 7));
    w->at++;
  }
}

static int first_transform_emptying_a_four_letter_word(void) {
  const BrotliDictionary *dictionary = BrotliGetDictionary();
  const BrotliTransforms *transforms = BrotliGetTransforms();
  const uint8_t *word = dictionary->data + dictionary->offsets_by_length[4];
  uint8_t out[64];
  for (int each = 1; each < transforms->num_transforms; each++) {
    if (BrotliTransformDictionaryWord(out, word, 4, transforms, each) == 0) return each;
  }
  return -1;
}

static int distance_symbol_for(uint32_t distance, int *extra_bits, uint32_t *extra) {
  for (int symbol = 16; symbol < 64; symbol++) {
    int bits = 1 + ((symbol - 16) >> 1);
    uint32_t offset = ((2u + ((symbol - 16) & 1)) << bits) - 4;
    if (distance - 1 >= offset && distance - 1 - offset < (1u << bits)) {
      *extra_bits = bits;
      *extra = distance - 1 - offset;
      return symbol;
    }
  }
  return -1;
}

static void cve_case(size_t empty_words) {
  int transform = first_transform_emptying_a_four_letter_word();
  uint32_t empty_distance = 1 + ((uint32_t)transform << 10);
  int empty_bits, word_bits;
  uint32_t empty_extra, word_extra;
  int empty_symbol = distance_symbol_for(empty_distance, &empty_bits, &empty_extra);
  int word_symbol = distance_symbol_for(1, &word_bits, &word_extra);
  BitWriter w = {calloc((empty_words + 1) * (2 + (size_t)empty_bits) / 8 + 64, 1), 0};
  put_bits(&w, 1, 0);
  put_bits(&w, 1, 1);
  put_bits(&w, 1, 0);
  put_bits(&w, 2, 0);
  put_bits(&w, 16, 4 - 1);
  put_bits(&w, 3, 0);
  put_bits(&w, 6, 0);
  put_bits(&w, 2, 0);
  put_bits(&w, 2, 0);
  put_bits(&w, 2, 1);
  put_bits(&w, 2, 0);
  put_bits(&w, 8, 'a');
  put_bits(&w, 2, 1);
  put_bits(&w, 2, 0);
  put_bits(&w, 10, 130);
  put_bits(&w, 2, 1);
  put_bits(&w, 2, 1);
  put_bits(&w, 6, (uint32_t)word_symbol);
  put_bits(&w, 6, (uint32_t)empty_symbol);
  for (size_t each = 0; each < empty_words; each++) {
    put_bits(&w, 1, 1);
    put_bits(&w, empty_bits, empty_extra);
  }
  put_bits(&w, 1, 0);
  put_bits(&w, word_bits, word_extra);
  size_t stream_size = (w.at + 7) >> 3;
  BrotliDecoderState *s = BrotliDecoderCreateInstance(NULL, NULL, NULL);
  uint8_t buffer[64];
  size_t available_in = stream_size;
  const uint8_t *next_in = w.data;
  size_t available_out = sizeof buffer;
  uint8_t *next_out = buffer;
  BrotliDecoderResult result =
      BrotliDecoderDecompressStream(s, &available_in, &next_in, &available_out, &next_out, NULL);
  Record record = {FNV_START, 0, 0};
  printf("cve %zu\ttransform:%d\tstream:%zu:%016llx", empty_words, transform, stream_size,
         (unsigned long long)fnv(w.data, stream_size, FNV_START));
  say_call(&record, 'D', buffer, sizeof buffer - available_out, (int)result);
  printf("\terror:%d\n", (int)BrotliDecoderGetErrorCode(s));
  BrotliDecoderDestroyInstance(s);
  free(w.data);
}

int main(int argc, char **argv) {
  FILE *cases = fopen(argv[1], "r");
  char line[512];
  while (fgets(line, sizeof line, cases)) {
    char what[16], kind[16], hint[32], ops[64];
    size_t size, chunk;
    int level, mode;
    if (line[0] == '#' || line[0] == '\n') continue;
    if (sscanf(line, "encode %15s %zu %d %d %31s %63s", kind, &size, &level, &mode, hint, ops) == 6) {
      encode_case(kind, size, level, mode, hint, ops);
    } else if (sscanf(line, "decode %15s %zu %d %zu", kind, &size, &level, &chunk) == 4) {
      decode_case(kind, size, level, chunk);
    } else if (sscanf(line, "cve %zu", &size) == 1) {
      cve_case(size);
    } else {
      sscanf(line, "%15s", what);
      fprintf(stderr, "unread case: %s", line);
      return 1;
    }
    fflush(stdout);
  }
  return 0;
}
