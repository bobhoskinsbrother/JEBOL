# The BlurHash port

Rebol-BlurHash 1.0.0, the extension R3's `system/modules` names at
`https://github.com/Siskin-framework/Rebol-BlurHash/releases/download/1.0.0/`,
wrapping Wolt's C encoder and decoder. JEBOL serves it in Java as decision 58
says: `import 'blurhash` answers the module from inside the build, and nothing
is fetched. This note carries what the code cannot: what the extension does with
R3's memory, how the port was checked, and the one place JEBOL answers otherwise.

---

## What the extension is

**Two commands and no exports.** `encode img [image!]` and `decode text
[any-string! binary!] image [image! pair!] /contrast punch [integer!]`. The
module's header is `REBOL [Title: {Rebol BlurHash Extension} Type: module]`, so
after the import both are reached through the module, and `lib`'s own ENCODE and
DECODE are untouched.

**ENCODE always uses three components each way.** `cmd_encode` calls
`blurHashForPixels(3, 3, ...)`, so every hash the module writes is 28
characters, and `encode` cannot be asked for more or fewer.

**Both commands work on R3's raw pixel memory, and R3 keeps a pixel blue
first.** `include/reb-c.h` stores an image as B, G, R, A on every little-endian
host but Android. The C was written for R, G, B, A: ENCODE reads the first byte
as red and DECODE writes red into the first byte. So in R3 a red picture's hash
is the standard BlurHash of a blue one, `K~0036fZfZfZfZfZfZfZfZ`, and the
standard hash of red decodes as blue. A hash R3 writes reads back the right way
round in R3, and the wrong way round anywhere else. JEBOL stores R, G, B, A
(decision 20), so `BlurHashExtension` lays the pixels out as R3's memory has them
before encoding and swaps them back after decoding, and the codec itself is the
C's.

**DECODE reads the hash as a C string.** `SERIES_DATA(hash)` is the series'
first byte, whatever its index, and `strlen` stops at the first zero. So:

- `decode next hash` decodes the whole hash, from its head.
- A binary stops at its first zero byte.
- A string holding any character past 255 is stored two bytes a character, so
  its first character's zero high byte ends it and DECODE answers none. JEBOL
  does not record how R3 would store a string, and takes it as wide exactly when
  it holds such a character. R3 can keep a string wide after its last wide
  character is removed; JEBOL would read that one as narrow.

**DECODE given an image paints into it** and answers it at its head; given a
pair it makes a new image, rounding each side with `floor(d + 0.5)`. A punch
below one is one. A hash that does not read answers none and leaves a given
image as it was, because the C checks every character before it writes a pixel.

**ENCODE reads the whole image from its head**, ignores alpha, and encodes even
an empty one: dividing by zero makes every factor NaN, and the C's `fmaxf` and
`fminf` drop a NaN operand, so `encode make image! 0x0` is
`K0TSUA~q~q~q~q~q~q~q~q`. Java's `Math.max` and `Math.min` keep it, which is why
`CFloats` has its own.

---

## Where JEBOL answers otherwise, on purpose

**A side past 65535 raises `size-limit` for `image!`, where R3 crashes.**
`RL_Make_Image` answers null for a side over 0xFFFF -- and a negative side is
one, read as unsigned -- and `cmd_decode` writes through that null. JEBOL raises
the error `Make_Image` raises when it is asked to.

---

## How the port was checked

The extension's `encode.c`, `decode.c` and headers were fetched as text, read,
and compiled in a scratch directory beside a generator. Nothing else from the
extension was built, and no release binary was run.

The encoder mixes float and double: it calls `cosf` and `powf`, rounds
`a * 166 - 0.5` from double to float before taking its floor, and passes `1/2.4`
to `powf` as a float. The port keeps each conversion where the C has it.

Against gcc 15.2 and glibc 2.43 on x86-64, everything agreed byte for byte:

- 1,986 encodings: eleven sizes from 0x0 to 100x37, six pictures each, at 3x3
  and at every count from 1x1 to 9x9 for four of the sizes;
- 24 encodings of large pictures, from 128x128 to 1000x700, where float
  accumulation would show a last-bit difference in `cosf` or `powf` if one
  reached a character;
- 17,946 decodings of every one of those hashes and eight that barely read, at
  sizes from 0x0 to 64x48 and punches of -1, 0, 1, 2 and 5.

`src/testFixtures/resources/blurhash/` keeps the part `BlurHashFromTheSourceTest`
runs and says how to make it again. The module's answers in
`TheBlurHashModuleFromTheSourceTest` were computed by the same C from the same
pixels laid out as R3's memory holds them.

## Licence

Rebol-BlurHash is MIT, Copyright (c) 2023 Oldes; the encoder and decoder are
MIT, Copyright (c) 2018 Wolt Enterprises. `BlurHashEncoder`, `BlurHashDecoder`,
`CFloats` and `BlurHashExtension` are written from them and stay under MIT;
NOTICE carries both notices and the licence.
