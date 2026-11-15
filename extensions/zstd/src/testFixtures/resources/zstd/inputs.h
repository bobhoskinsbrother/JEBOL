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

enum { DATAGEN = 0, ZEROS = 1, TWO_SYMBOLS = 2, FAR_REPEATS = 3, ONE_BYTE_THEN_NOISE = 4, PERIODIC = 5,
       NOISE_THEN_REPEAT = 6 };

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
    case PERIODIC: {
        /* a datagen block of b KB at match probability a%, repeated, one octet changed every 4 KB */
        size_t period = (size_t)b * 1024;
        unsigned char *block = malloc(period);
        RDG_genBuffer(block, period, a / 100.0, 0, seed);
        for (size_t i = 0; i < size; i++) p[i] = block[i % period];
        for (size_t i = 0; i < size; i += 4096) p[i] ^= (unsigned char)(1 + (RDG_rand(&s) & 0x7F));
        free(block);
        break;
    }
    case NOISE_THEN_REPEAT: {
        /* a KB of random octets, then the first b KB of them again, then random octets again */
        size_t noise = (size_t)a * 1024, repeat = (size_t)b * 1024;
        for (size_t i = 0; i < size; i++) p[i] = (unsigned char)RDG_rand(&s);
        for (size_t i = 0; i < repeat && noise + i < size; i++) p[noise + i] = p[i];
        break;
    }
    }
    return p;
}

