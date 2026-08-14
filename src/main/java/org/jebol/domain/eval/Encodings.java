package org.jebol.domain.eval;

import org.jebol.domain.value.BitsetValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turning bytes into text and back: percent encoding, the numeric bases,
 * checksums and compression.
 *
 * <p>Read out of {@code n-strings.c}, {@code n-crypt.c} and
 * {@code u-compress.c}. Kept apart from {@link Natives} because none of it is
 * about REBOL: it is arithmetic over octets, and every one of these functions
 * would say the same thing in any language. The natives here are the thinnest
 * wrapper that reaches it.
 *
 * <p>Nothing in here touches a REBOL value. That is deliberate: a mistake in
 * base 64 is easier to find in a function that takes bytes and answers text
 * than in one that also has to work out which datatype it was handed.
 */
final class Encodings {

    private Encodings() {
    }

    /**
     * A growable run of octets.
     *
     * <p>Written out rather than using {@code ByteArrayOutputStream} because
     * the dependency rule keeps {@code java.io} out of the domain, and it is
     * right to: a class whose name says "stream" is how reading and writing
     * creep inward. Nothing here is a stream; it is a byte array that grows.
     */
    private static final class Octets {

        private byte[] held = new byte[64];
        private int used;

        void write(int octet) {
            if (used == held.length) {
                held = java.util.Arrays.copyOf(held, held.length * 2);
            }
            held[used++] = (byte) octet;
        }

        void write(byte[] more, int from, int count) {
            while (used + count > held.length) {
                held = java.util.Arrays.copyOf(held, held.length * 2);
            }
            System.arraycopy(more, from, held, used, count);
            used += count;
        }

        int length() {
            return used;
        }

        byte[] toArray() {
            return java.util.Arrays.copyOf(held, used);
        }
    }

    /**
     * The characters a URI may carry without escaping.
     *
     * <p>{@code system/catalog/bitsets/uri} holds the same set, and this is
     * the same list the C builds: the unreserved characters of RFC 3986 plus
     * the reserved ones a path is allowed to keep.
     */
    private static final String URI_UNESCAPED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    + "-._~:/?#[]@!$&'()*+,;=";

    /** The narrower set, for one component of a URI rather than the whole. */
    private static final String URI_COMPONENT_UNESCAPED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    + "-._~";

    /** Whether a byte is in the default set for a file or a url. */
    static boolean uriKeeps(int octet) {
        return octet < 128 && URI_UNESCAPED.indexOf(octet) >= 0;
    }

    /** Whether a byte is in the default set for anything else. */
    static boolean uriComponentKeeps(int octet) {
        return octet < 128 && URI_COMPONENT_UNESCAPED.indexOf(octet) >= 0;
    }

    /**
     * The character a space becomes under /URI.
     *
     * <p>The C decides it from the escape character, which is the awkward part
     * worth writing down: a plus, unless the escape character is an equals
     * sign, in which case an underscore.
     */
    static char spaceStandsForUnder(char escape) {
        return escape == '=' ? '_' : '+';
    }

    /**
     * Percent-encodes octets, keeping the ones the given set allows.
     *
     * <p>Octets, never codepoints. A character that takes two bytes in UTF-8
     * takes two escapes, because what a URL carries is bytes.
     */
    static String percentEncoded(
            byte[] octets, java.util.function.IntPredicate keep,
            char escape, boolean spaceIsSpecial) {

        StringBuilder encoded = new StringBuilder(octets.length);
        for (byte each : octets) {
            int octet = each & 0xFF;
            if (spaceIsSpecial && octet == ' ') {
                encoded.append(spaceStandsForUnder(escape));
                continue;
            }
            if (keep.test(octet)) {
                encoded.append((char) octet);
                continue;
            }
            encoded.append(escape).append("%02X".formatted(octet));
        }
        return encoded.toString();
    }

    /**
     * Reads percent escapes back into octets.
     *
     * <p>Two hexadecimal digits are required. An escape character with
     * anything else after it stands for itself, because a URL that was never
     * encoded has to survive being decoded: {@code dehex "100%"} is
     * {@code "100%"} and not an error.
     */
    static byte[] percentDecoded(String text, char escape, boolean spaceIsSpecial) {
        Octets octets = new Octets();
        char special = spaceStandsForUnder(escape);
        for (int at = 0; at < text.length(); at++) {
            char here = text.charAt(at);
            if (spaceIsSpecial && here == special) {
                octets.write(' ');
                continue;
            }
            if (here == escape && at + 2 < text.length()) {
                int high = Character.digit(text.charAt(at + 1), 16);
                int low = Character.digit(text.charAt(at + 2), 16);
                if (high >= 0 && low >= 0) {
                    octets.write(high * 16 + low);
                    at += 2;
                    continue;
                }
            }
            for (byte each : String.valueOf(here).getBytes(StandardCharsets.UTF_8)) {
                octets.write(each & 0xFF);
            }
        }
        return octets.toArray();
    }

    /** Whether a bitset holds a byte, used when /EXCEPT names the set. */
    static boolean setHolds(BitsetValue set, int octet) {
        return set.holds(octet);
    }

    /** The five bases ENBASE and DEBASE know, and no others. */
    static final List<Integer> BASES = List.of(2, 16, 36, 64, 85);

    private static final String BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String BASE64_URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final String BASE36 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String BASE85 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                    + "!#$%&()*+-;<=>?@^_`{|}~";

    static String enbase(byte[] octets, int base, boolean urlSafe) {
        return switch (base) {
            case 16 -> hexOf(octets);
            case 2 -> bitsOf(octets);
            case 64 -> base64Of(octets, urlSafe ? BASE64_URL : BASE64);
            case 36 -> bigBaseOf(octets, BASE36);
            case 85 -> bigBaseOf(octets, BASE85);
            default -> throw new IllegalArgumentException("base " + base);
        };
    }

    static byte[] debase(String text, int base, boolean urlSafe) {
        return switch (base) {
            case 16 -> octetsOfHex(text);
            case 2 -> octetsOfBits(text);
            case 64 -> octetsOfBase64(text, urlSafe ? BASE64_URL : BASE64);
            case 36 -> octetsOfBigBase(text, BASE36);
            case 85 -> octetsOfBigBase(text, BASE85);
            default -> throw new IllegalArgumentException("base " + base);
        };
    }

    private static String hexOf(byte[] octets) {
        StringBuilder text = new StringBuilder(octets.length * 2);
        for (byte each : octets) {
            text.append("%02X".formatted(each & 0xFF));
        }
        return text.toString();
    }

    private static byte[] octetsOfHex(String text) {
        String digits = withoutWhitespace(text);
        if (digits.length() % 2 != 0) {
            throw new IllegalArgumentException("odd number of hex digits");
        }
        byte[] octets = new byte[digits.length() / 2];
        for (int at = 0; at < octets.length; at++) {
            int high = Character.digit(digits.charAt(at * 2), 16);
            int low = Character.digit(digits.charAt(at * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("not a hex digit");
            }
            octets[at] = (byte) (high * 16 + low);
        }
        return octets;
    }

    private static String bitsOf(byte[] octets) {
        StringBuilder text = new StringBuilder(octets.length * 8);
        for (byte each : octets) {
            for (int bit = 7; bit >= 0; bit--) {
                text.append((each >> bit) & 1);
            }
        }
        return text.toString();
    }

    private static byte[] octetsOfBits(String text) {
        String bits = withoutWhitespace(text);
        if (bits.length() % 8 != 0) {
            throw new IllegalArgumentException("bits do not fill whole octets");
        }
        byte[] octets = new byte[bits.length() / 8];
        for (int at = 0; at < bits.length(); at++) {
            char bit = bits.charAt(at);
            if (bit != '0' && bit != '1') {
                throw new IllegalArgumentException("not a bit");
            }
            if (bit == '1') {
                octets[at / 8] |= (byte) (1 << (7 - at % 8));
            }
        }
        return octets;
    }

    private static String base64Of(byte[] octets, String alphabet) {
        StringBuilder text = new StringBuilder();
        for (int at = 0; at < octets.length; at += 3) {
            int remaining = Math.min(3, octets.length - at);
            int group = 0;
            for (int each = 0; each < 3; each++) {
                group <<= 8;
                if (each < remaining) {
                    group |= octets[at + each] & 0xFF;
                }
            }
            for (int each = 0; each < 4; each++) {
                if (each <= remaining) {
                    text.append(alphabet.charAt((group >> (18 - each * 6)) & 0x3F));
                } else {
                    text.append('=');
                }
            }
        }
        return text.toString();
    }

    private static byte[] octetsOfBase64(String text, String alphabet) {
        String digits = withoutWhitespace(text).replace("=", "");
        Octets octets = new Octets();
        int group = 0;
        int held = 0;
        for (int at = 0; at < digits.length(); at++) {
            int value = alphabet.indexOf(digits.charAt(at));
            if (value < 0) {
                throw new IllegalArgumentException("not a base 64 digit");
            }
            group = (group << 6) | value;
            held += 6;
            if (held >= 8) {
                held -= 8;
                octets.write((group >> held) & 0xFF);
            }
        }
        return octets.toArray();
    }

    /**
     * The bases that are not a power of two, done as one big number.
     *
     * <p>Base 36 and base 85 do not divide the octets into fixed groups, so
     * the whole input is one integer and the digits are its remainders. A
     * leading zero octet would be lost that way, so the count of them is
     * written first as a digit of its own.
     */
    private static String bigBaseOf(byte[] octets, String alphabet) {
        int leadingZeroes = 0;
        while (leadingZeroes < octets.length && octets[leadingZeroes] == 0) {
            leadingZeroes++;
        }
        java.math.BigInteger number = new java.math.BigInteger(1, octets);
        java.math.BigInteger radix = java.math.BigInteger.valueOf(alphabet.length());
        StringBuilder digits = new StringBuilder();
        while (number.signum() > 0) {
            java.math.BigInteger[] split = number.divideAndRemainder(radix);
            digits.append(alphabet.charAt(split[1].intValue()));
            number = split[0];
        }
        return alphabet.charAt(leadingZeroes)
                + digits.reverse().toString();
    }

    private static byte[] octetsOfBigBase(String text, String alphabet) {
        String digits = withoutWhitespace(text);
        if (digits.isEmpty()) {
            return new byte[0];
        }
        int leadingZeroes = alphabet.indexOf(digits.charAt(0));
        if (leadingZeroes < 0) {
            throw new IllegalArgumentException("not a digit of this base");
        }
        java.math.BigInteger number = java.math.BigInteger.ZERO;
        java.math.BigInteger radix = java.math.BigInteger.valueOf(alphabet.length());
        for (int at = 1; at < digits.length(); at++) {
            int value = alphabet.indexOf(digits.charAt(at));
            if (value < 0) {
                throw new IllegalArgumentException("not a digit of this base");
            }
            number = number.multiply(radix).add(java.math.BigInteger.valueOf(value));
        }
        byte[] magnitude = number.signum() == 0
                ? new byte[0]
                : stripLeadingSignByte(number.toByteArray());
        byte[] octets = new byte[leadingZeroes + magnitude.length];
        System.arraycopy(magnitude, 0, octets, leadingZeroes, magnitude.length);
        return octets;
    }

    private static byte[] stripLeadingSignByte(byte[] octets) {
        if (octets.length > 1 && octets[0] == 0) {
            byte[] shorter = new byte[octets.length - 1];
            System.arraycopy(octets, 1, shorter, 0, shorter.length);
            return shorter;
        }
        return octets;
    }

    /**
     * Whitespace is ignored wherever it falls.
     *
     * <p>Which is what lets a long base 64 value be written over several
     * lines, and is why ENBASE has a /FLAT to turn the line breaks off.
     */
    private static String withoutWhitespace(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        for (int at = 0; at < text.length(); at++) {
            if (!Character.isWhitespace(text.charAt(at))) {
                kept.append(text.charAt(at));
            }
        }
        return kept.toString();
    }

    /** Where ENBASE breaks a line when /FLAT was not asked for. */
    static final int LINE_WIDTH = 64;

    static String brokenIntoLines(String text) {
        if (text.length() <= LINE_WIDTH) {
            return text;
        }
        StringBuilder wrapped = new StringBuilder(text.length() + text.length() / LINE_WIDTH);
        for (int at = 0; at < text.length(); at += LINE_WIDTH) {
            wrapped.append(text, at, Math.min(text.length(), at + LINE_WIDTH));
            wrapped.append('\n');
        }
        return wrapped.toString();
    }

    /**
     * The methods this host offers, in the order the catalogue lists them.
     *
     * <p>Which hashes exist is the host's business rather than the language's,
     * which is why R3 fills {@code system/catalog/checksums} from
     * {@code Init_Crypt} rather than writing it in {@code sysobj.reb}. The
     * name here is R3's; the value is what {@code java.security} calls it.
     */
    static final Map<String, String> DIGESTS = digestMethods();

    private static Map<String, String> digestMethods() {
        Map<String, String> named = new LinkedHashMap<>();
        named.put("md5", "MD5");
        named.put("sha1", "SHA-1");
        named.put("sha224", "SHA-224");
        named.put("sha256", "SHA-256");
        named.put("sha384", "SHA-384");
        named.put("sha512", "SHA-512");
        named.put("sha3-224", "SHA3-224");
        named.put("sha3-256", "SHA3-256");
        named.put("sha3-384", "SHA3-384");
        named.put("sha3-512", "SHA3-512");
        return Map.copyOf(named);
    }

    /** The checksums that answer a number rather than a digest. */
    static final List<String> CYCLIC = List.of("crc32", "adler32", "crc24", "tcp");

    /** Every method name, digests and cyclic together. */
    static List<String> checksumMethods() {
        List<String> every = new ArrayList<>(DIGESTS.keySet());
        every.addAll(CYCLIC);
        return List.copyOf(every);
    }

    static byte[] digestOf(byte[] octets, String method) {
        try {
            return java.security.MessageDigest.getInstance(DIGESTS.get(method))
                    .digest(octets);
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalArgumentException(method + " is not available here");
        }
    }

    /** A keyed digest, which is what /WITH asks for when the key is text. */
    static byte[] keyedDigestOf(byte[] octets, String method, byte[] key) {
        try {
            String named = "Hmac" + DIGESTS.get(method).replace("-", "");
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(named);
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    key.length == 0 ? new byte[1] : key, named));
            return mac.doFinal(octets);
        } catch (java.security.NoSuchAlgorithmException
                | java.security.InvalidKeyException unavailable) {
            throw new IllegalArgumentException(
                    method + " has no keyed form here");
        }
    }

    static long cyclicOf(byte[] octets, String method) {
        return switch (method) {
            case "crc32" -> checksumValue(new java.util.zip.CRC32(), octets);
            case "adler32" -> checksumValue(new java.util.zip.Adler32(), octets);
            case "crc24" -> crc24Of(octets);
            case "tcp" -> tcpSumOf(octets);
            default -> throw new IllegalArgumentException(method);
        };
    }

    private static long checksumValue(java.util.zip.Checksum running, byte[] octets) {
        running.update(octets, 0, octets.length);
        return running.getValue();
    }

    private static long crc24Of(byte[] octets) {
        int running = 0xB704CE;
        for (byte each : octets) {
            running ^= (each & 0xFF) << 16;
            for (int bit = 0; bit < 8; bit++) {
                running <<= 1;
                if ((running & 0x1000000) != 0) {
                    running ^= 0x1864CFB;
                }
            }
        }
        return running & 0xFFFFFF;
    }

    private static long tcpSumOf(byte[] octets) {
        long running = 0;
        for (int at = 0; at + 1 < octets.length; at += 2) {
            running += ((octets[at] & 0xFF) << 8) | (octets[at + 1] & 0xFF);
        }
        if (octets.length % 2 != 0) {
            running += (octets[octets.length - 1] & 0xFF) << 8;
        }
        while ((running >> 16) != 0) {
            running = (running & 0xFFFF) + (running >> 16);
        }
        return (~running) & 0xFFFF;
    }

    /** The methods this host offers, as {@code system/catalog/compressions}. */
    static final List<String> COMPRESSIONS = List.of("zlib", "gzip", "deflate");

    static byte[] compressed(byte[] octets, String method, int level) {
        return switch (method) {
            case "gzip" -> gzipped(octets);
            case "zlib" -> deflated(octets, level, false);
            case "deflate" -> deflated(octets, level, true);
            default -> throw new IllegalArgumentException(method);
        };
    }

    static byte[] decompressed(byte[] octets, String method) {
        return switch (method) {
            case "gzip" -> ungzipped(octets);
            case "zlib" -> inflated(octets, false);
            case "deflate" -> inflated(octets, true);
            default -> throw new IllegalArgumentException(method);
        };
    }

    /** The ten bytes a gzip member opens with, and the two that name it. */
    private static final int GZIP_MAGIC_FIRST = 0x1F;
    private static final int GZIP_MAGIC_SECOND = 0x8B;
    private static final int GZIP_DEFLATE = 8;
    private static final int GZIP_HEADER_LENGTH = 10;
    private static final int GZIP_TRAILER_LENGTH = 8;

    /**
     * Gzip: raw deflate with a header in front and a checksum behind.
     *
     * <p>Written out rather than using {@code GZIPOutputStream}, which is a
     * stream and so belongs to {@code java.io}. The format is ten fixed bytes,
     * the deflated data, then the CRC-32 and the uncompressed length, both
     * little-endian.
     */
    private static byte[] gzipped(byte[] octets) {
        Octets into = new Octets();
        into.write(GZIP_MAGIC_FIRST);
        into.write(GZIP_MAGIC_SECOND);
        into.write(GZIP_DEFLATE);
        for (int each = 0; each < GZIP_HEADER_LENGTH - 3; each++) {
            into.write(0);
        }
        byte[] deflated = deflated(octets, java.util.zip.Deflater.DEFAULT_COMPRESSION, true);
        into.write(deflated, 0, deflated.length);
        java.util.zip.CRC32 checked = new java.util.zip.CRC32();
        checked.update(octets, 0, octets.length);
        writeLittleEndian(into, checked.getValue());
        writeLittleEndian(into, octets.length);
        return into.toArray();
    }

    private static void writeLittleEndian(Octets into, long quantity) {
        for (int each = 0; each < 4; each++) {
            into.write((int) ((quantity >> (each * 8)) & 0xFF));
        }
    }

    private static byte[] ungzipped(byte[] octets) {
        if (octets.length < GZIP_HEADER_LENGTH + GZIP_TRAILER_LENGTH
                || (octets[0] & 0xFF) != GZIP_MAGIC_FIRST
                || (octets[1] & 0xFF) != GZIP_MAGIC_SECOND) {
            throw new IllegalArgumentException("not gzip data");
        }
        int from = GZIP_HEADER_LENGTH;
        int flags = octets[3] & 0xFF;
        if ((flags & 0x04) != 0) {
            int extra = (octets[from] & 0xFF) | ((octets[from + 1] & 0xFF) << 8);
            from += 2 + extra;
        }
        if ((flags & 0x08) != 0) {
            from = pastTheNextZero(octets, from);
        }
        if ((flags & 0x10) != 0) {
            from = pastTheNextZero(octets, from);
        }
        if ((flags & 0x02) != 0) {
            from += 2;
        }
        int length = octets.length - from - GZIP_TRAILER_LENGTH;
        if (length < 0) {
            throw new IllegalArgumentException("gzip data ends early");
        }
        return inflated(java.util.Arrays.copyOfRange(octets, from, from + length), true);
    }

    private static int pastTheNextZero(byte[] octets, int from) {
        int at = from;
        while (at < octets.length && octets[at] != 0) {
            at++;
        }
        return at + 1;
    }

    /**
     * Deflate, with or without the zlib wrapper.
     *
     * <p>The wrapper is the whole difference between the two methods: zlib is
     * deflate with a two-byte header and an Adler-32 trailer, and raw deflate
     * is the same bits without them.
     */
    private static byte[] deflated(byte[] octets, int level, boolean raw) {
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(level, raw);
        try {
            deflater.setInput(octets);
            deflater.finish();
            Octets into = new Octets();
            byte[] page = new byte[8192];
            while (!deflater.finished()) {
                into.write(page, 0, deflater.deflate(page));
            }
            return into.toArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflated(byte[] octets, boolean raw) {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater(raw);
        try {
            inflater.setInput(octets);
            Octets into = new Octets();
            byte[] page = new byte[8192];
            while (!inflater.finished()) {
                int written = inflater.inflate(page);
                if (written == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("compressed data ends early");
                }
                into.write(page, 0, written);
            }
            return into.toArray();
        } catch (java.util.zip.DataFormatException notDeflate) {
            throw new IllegalArgumentException("not deflate data");
        } finally {
            inflater.end();
        }
    }

    /**
     * Scrambles or unscrambles octets in place, against a key.
     *
     * <p>{@code Cloak} in s-ops.c, line for line. Rebol's own cipher, and not
     * presented as a strong one -- the C's summary is "Simple data scrambler.
     * Quality depends on the key length."
     *
     * <p>Three steps and the order of them is the whole algorithm. Decoding
     * runs the chain backwards first; both directions then flip the first byte
     * against a sum of all the others; encoding runs the chain forwards last.
     * That middle step is why a one-byte binary still changes.
     *
     * @return false when the key has no bytes, which the caller raises on
     */
    static boolean cloak(boolean decode, byte[] octets, byte[] key) {
        if (octets.length == 0) {
            return true;
        }
        if (key.length == 0) {
            return false;
        }
        int keyLength = key.length;
        for (int at = octets.length - 1; decode && at > 0; at--) {
            octets[at] ^= (byte) (octets[at - 1] ^ key[at % keyLength]);
        }
        int running = 0xA5;
        for (int at = 1; at < octets.length; at++) {
            running += octets[at] & 0xFF;
        }
        octets[0] ^= (byte) running;
        if (!decode) {
            for (int at = 1; at < octets.length; at++) {
                octets[at] ^= (byte) (octets[at - 1] ^ key[at % keyLength]);
            }
        }
        return true;
    }

    /**
     * The real key: twenty bytes, the SHA-1 of the given key cycled to twenty.
     *
     * <p>So a one-byte key and a twenty-byte key are equally long by the time
     * the scrambling starts, which is what "quality depends on the key length"
     * is about -- the entropy, not the byte count.
     */
    static byte[] hashedKey(byte[] key) {
        if (key.length == 0) {
            return key;
        }
        byte[] cycled = new byte[20];
        for (int at = 0; at < cycled.length; at++) {
            cycled[at] = key[at % key.length];
        }
        return digestOf(cycled, "sha1");
    }

    /** Whether this host has a character set by that name. */
    static boolean hasCharacterSet(String named) {
        return charsetNamed(named) != null;
    }

    /**
     * A character set by the name REBOL uses for it, or null.
     *
     * <p>R3's names are not always Java's, and the three that differ are the
     * three most used: `utf8` has no hyphen, and `latin1` and `cp1252` are
     * spelled as one word.
     */
    static java.nio.charset.Charset charsetNamed(String named) {
        String canonical = switch (named.toLowerCase(java.util.Locale.ROOT)) {
            case "utf8" -> "UTF-8";
            case "utf16", "utf16le" -> "UTF-16LE";
            case "utf16be" -> "UTF-16BE";
            case "utf32", "utf32le" -> "UTF-32LE";
            case "utf32be" -> "UTF-32BE";
            case "latin1", "iso8859-1" -> "ISO-8859-1";
            case "ascii" -> "US-ASCII";
            default -> named;
        };
        try {
            return java.nio.charset.Charset.forName(canonical);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** The five filters, numbered as the PNG format numbers them. */
    static final List<String> PNG_FILTERS = List.of("none", "sub", "up", "average", "paeth");

    /**
     * The prediction PNG's fifth filter makes: whichever neighbour is closest
     * to their linear estimate.
     *
     * <p>Ported line for line, because the tie-breaks are ordered and the order
     * is observable. `if ((pa <= pb) && (pa <= pc)) return a; else if (pb <= pc)
     * return b; return c;` -- left first, then above, then above-left. Equal
     * distances are common in flat colour, so reordering the comparisons would
     * pass a careless test and corrupt a real image.
     */
    private static int paethPredictor(int left, int above, int aboveLeft) {
        int estimate = left + above - aboveLeft;
        int toLeft = Math.abs(estimate - left);
        int toAbove = Math.abs(estimate - above);
        int toAboveLeft = Math.abs(estimate - aboveLeft);
        if (toLeft <= toAbove && toLeft <= toAboveLeft) {
            return left;
        }
        return toAbove <= toAboveLeft ? above : aboveLeft;
    }

    /**
     * Applies one PNG filter to every scanline.
     *
     * <p>The line above the first is treated as zeros, which is what makes the
     * first line encode as itself. Every subtraction is modulo 256, which is
     * what makes it reversible without carrying a sign.
     */
    static byte[] pngFiltered(byte[] data, int width, int filter, int bytesPerPixel) {
        int rows = data.length / width;
        byte[] out = new byte[data.length];
        byte[] previous = new byte[width];
        for (int row = 0; row < rows; row++) {
            int from = row * width;
            applyOneLine(data, from, out, from, width, filter, bytesPerPixel,
                    previous, true);
            previous = java.util.Arrays.copyOfRange(data, from, from + width);
        }
        System.arraycopy(data, rows * width, out, rows * width,
                data.length - rows * width);
        return out;
    }

    /**
     * Reverses one PNG filter over every scanline.
     *
     * <p>With a named filter the lines are bare. Without one, each line opens
     * with a byte naming its own filter, which is how a PNG stores it -- so the
     * two forms take different widths and the type byte is the difference.
     */
    static byte[] pngUnfiltered(
            byte[] data, int width, int namedFilter, int bytesPerPixel) {

        boolean typePerLine = namedFilter < 0;
        int stride = typePerLine ? width + 1 : width;
        int rows = data.length / stride;
        byte[] out = new byte[rows * width];
        byte[] previous = new byte[width];
        for (int row = 0; row < rows; row++) {
            int from = row * stride;
            int filter = namedFilter;
            if (typePerLine) {
                filter = data[from] & 0xFF;
                from++;
            }
            int into = row * width;
            applyOneLine(data, from, out, into, width, filter, bytesPerPixel,
                    previous, false);
            previous = java.util.Arrays.copyOfRange(out, into, into + width);
        }
        return out;
    }

    /**
     * One scanline, filtered or unfiltered.
     *
     * <p>The two directions differ only in which line the left-hand neighbour
     * is read from: filtering reads the original, unfiltering reads what it has
     * already reconstructed. Writing them as one function keeps the five
     * predictions in one place, which is where they have to agree.
     */
    private static void applyOneLine(
            byte[] source, int from, byte[] out, int into, int width,
            int filter, int bytesPerPixel, byte[] previous, boolean forward) {

        for (int at = 0; at < width; at++) {
            int here = source[from + at] & 0xFF;
            int left = at >= bytesPerPixel
                    ? (forward
                            ? source[from + at - bytesPerPixel] & 0xFF
                            : out[into + at - bytesPerPixel] & 0xFF)
                    : 0;
            int above = previous[at] & 0xFF;
            int aboveLeft = at >= bytesPerPixel
                    ? previous[at - bytesPerPixel] & 0xFF
                    : 0;
            int prediction = switch (filter) {
                case 1 -> at >= bytesPerPixel ? left : 0;
                case 2 -> above;
                case 3 -> at >= bytesPerPixel ? (left + above) >> 1 : above >> 1;
                case 4 -> at >= bytesPerPixel
                        ? paethPredictor(left, above, aboveLeft)
                        : above;
                default -> 0;
            };
            out[into + at] = (byte) (forward ? here - prediction : here + prediction);
        }
    }

    /**
     * Reverses each group of bytes, in place.
     *
     * <p>Two, four or eight and nothing else, which the C checks before it
     * starts. A tail that does not fill a group is left as it is rather than
     * partly reversed, because half a swap is not a smaller swap.
     */
    static void swapEndian(byte[] octets, int from, int width) {
        if (width != 2 && width != 4 && width != 8) {
            throw new IllegalArgumentException("width " + width);
        }
        for (int at = from; at + width <= octets.length; at += width) {
            for (int each = 0; each < width / 2; each++) {
                byte held = octets[at + each];
                octets[at + each] = octets[at + width - 1 - each];
                octets[at + width - 1 - each] = held;
            }
        }
    }
}
