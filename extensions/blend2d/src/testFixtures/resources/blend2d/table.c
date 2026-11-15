#include <blend2d.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

enum { W = 64, H = 64 };

static uint32_t noise_state;

static void noise_seed(uint32_t seed) { noise_state = seed * 2654435761u + 1; }

static uint32_t noise_next(void) {
  noise_state = noise_state * 1103515245u + 12345u;
  uint32_t x = noise_state ^ (noise_state >> 15);
  noise_state = noise_state * 1103515245u + 12345u;
  return x ^ (noise_state << 7);
}

static uint32_t crc_table[256];

static void crc_init(void) {
  for (uint32_t n = 0; n < 256; n++) {
    uint32_t c = n;
    for (int k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
    crc_table[n] = c;
  }
}

static uint32_t crc32_of(const uint32_t* px, size_t n) {
  uint32_t c = 0xFFFFFFFFu;
  for (size_t i = 0; i < n; i++) {
    uint32_t v = px[i];
    for (int b = 0; b < 4; b++) { c = crc_table[(c ^ (v & 0xFF)) & 0xFF] ^ (c >> 8); v >>= 8; }
  }
  return c ^ 0xFFFFFFFFu;
}

static uint32_t data[W * H];
static uint8_t mask[W * H];

static void fill(int op, uint32_t argb, double alpha, uint32_t seed, int masked) {
  noise_seed(seed);
  for (int i = 0; i < W * H; i++) data[i] = noise_next();
  for (int i = 0; i < W * H; i++) {
    uint32_t r = noise_next();
    int row = i / W;
    mask[i] = row < 4 ? 0 : row < 8 ? 255 : row < 10 ? (uint8_t)((r & 1) ? 255 : 0) : (uint8_t)(r >> 24);
  }
  BLImageCore img; blImageInit(&img);
  blImageCreateFromData(&img, W, H, BL_FORMAT_PRGB32, data, W * 4, BL_DATA_ACCESS_WRITE, NULL, NULL);
  BLImageCore msk; blImageInit(&msk);
  blImageCreateFromData(&msk, W, H, BL_FORMAT_A8, mask, W, BL_DATA_ACCESS_READ, NULL, NULL);
  BLContextCore ctx; BLContextCreateInfo cci; memset(&cci, 0, sizeof(cci));
  blContextInitAs(&ctx, &img, &cci);
  blContextSetCompOp(&ctx, op);
  blContextSetGlobalAlpha(&ctx, alpha);
  blContextSetFillStyleRgba32(&ctx, argb);
  if (masked) {
    BLPointI origin = { 0, 0 };
    blContextFillMaskI(&ctx, &origin, &msk, NULL);
  } else {
    blContextFillAll(&ctx);
  }
  blContextEnd(&ctx);
  blContextReset(&ctx);
  blImageReset(&img);
  blImageReset(&msk);
}

static void row(FILE* out, int op, uint32_t argb, double alpha, uint32_t seed, int masked) {
  fill(op, argb, alpha, seed, masked);
  fprintf(out, "%d\t%08x\t%.17g\t%u\t%08x\n", op, argb, alpha, seed, crc32_of(data, W * H));
}

static const uint32_t colours[] = {
  0x00000000u, 0xFFFFFFFFu, 0xFF000000u, 0x80FF0000u, 0x01020304u, 0xFE7F8081u,
  0x8C0A3CC8u, 0x40FFFFFFu, 0xFF00FF00u, 0x7F7F7F7Fu, 0xC0123456u, 0x10F0E0D0u
};

static const double alphas[] = { 1.0, 0.0, 0.5, 0.25, 0.75, 0.001, 0.999, 0.3 };

static const double edges[] = {
  1.0 / 255, 2.0 / 255, 127.0 / 255, 127.5 / 255, 128.0 / 255, 254.0 / 255, 254.5 / 255, 0.5 / 255, 0.49 / 255
};

static void random_rows(FILE* out, int per_op, uint32_t pick, int masked) {
  for (int op = 0; op <= 28; op++)
    for (int k = 0; k < per_op; k++) {
      pick = pick * 1664525u + 1013904223u;
      uint32_t argb = pick ^ (pick >> 13) * 2246822519u;
      if (k % 11 == 3) argb &= 0x00FFFFFFu;
      if (k % 11 == 5) argb |= 0xFF000000u;
      if (k % 13 == 6) argb = 0;
      pick = pick * 1664525u + 1013904223u;
      double alpha = (k % 3 == 0) ? 1.0 : (k % 3 == 1) ? edges[(pick >> 8) % 9] : (pick >> 8) / 16777216.0;
      row(out, op, argb, alpha, (uint32_t)(op * 100000 + k), masked);
    }
}

int main(void) {
  crc_init();
  FILE* fills = fopen("blend2d-fill.tsv", "w");
  fprintf(fills, "operator\tcolour\talpha\tseed\tpixel crc32\n");
  for (int op = 0; op <= 28; op++)
    for (int c = 0; c < 12; c++)
      for (int a = 0; a < 8; a++)
        row(fills, op, colours[c], alphas[a], (uint32_t)(op * 1000 + c * 10 + a), 0);
  random_rows(fills, 120, 977, 0);
  fclose(fills);
  FILE* masks = fopen("blend2d-mask.tsv", "w");
  fprintf(masks, "operator\tcolour\talpha\tseed\tpixel crc32\n");
  random_rows(masks, 60, 4211, 1);
  fclose(masks);
  return 0;
}
