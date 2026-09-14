package org.jebol.domain.eval;

import org.jebol.domain.eval.brotli.Brotli;
import org.jebol.domain.value.BitsetValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Encodings {

    private Encodings() {
    }

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

    private static final String URI_UNESCAPED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    + "!#$&'()*+,-./:;=?@_~";

    private static final String URI_COMPONENT_UNESCAPED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    + "!'()*-._~";

    static boolean uriKeeps(int octet) {
        return octet < 128 && URI_UNESCAPED.indexOf(octet) >= 0;
    }

    static boolean uriComponentKeeps(int octet) {
        return octet < 128 && URI_COMPONENT_UNESCAPED.indexOf(octet) >= 0;
    }

    static char spaceStandsForUnder(char escape) {
        return escape == '=' ? '_' : '+';
    }

    static byte[] percentEncoded(
            byte[] octets, java.util.function.IntPredicate keep,
            char escape, boolean spaceIsSpecial) {

        Octets encoded = new Octets();
        for (byte each : octets) {
            int octet = each & 0xFF;
            if (spaceIsSpecial && octet == ' ') {
                encoded.write(spaceStandsForUnder(escape));
                continue;
            }
            if (spaceIsSpecial && octet == spaceStandsForUnder(escape)) {
                escapedInto(encoded, escape, octet);
                continue;
            }
            if (keep.test(octet)) {
                encoded.write(octet);
                continue;
            }
            escapedInto(encoded, escape, octet);
        }
        return encoded.toArray();
    }

    private static void escapedInto(Octets encoded, char escape, int octet) {
        String digits = "%02X".formatted(octet);
        encoded.write(escape);
        encoded.write(digits.charAt(0));
        encoded.write(digits.charAt(1));
    }

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

    static boolean setHolds(BitsetValue set, int octet) {
        return set.holds(octet);
    }

    static final List<Integer> BASES = List.of(2, 16, 36, 64, 85);

    private static final String BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String BASE64_URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final String BASE36 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    static String enbase(byte[] octets, int base, boolean urlSafe) {
        return switch (base) {
            case 16 -> hexOf(octets);
            case 2 -> bitsOf(octets);
            case 64 -> base64Of(octets, urlSafe ? BASE64_URL : BASE64);
            case 36 -> base36Of(octets);
            case 85 -> ascii85Of(octets);
            default -> throw new IllegalArgumentException("base " + base);
        };
    }

    static byte[] debase(String text, int base, boolean urlSafe) {
        return switch (base) {
            case 16 -> octetsOfHex(text);
            case 2 -> octetsOfBits(text);
            case 64 -> octetsOfBase64(text, urlSafe ? BASE64_URL : BASE64);
            case 36 -> octetsOfBase36(withoutWhitespace(text));
            case 85 -> octetsOfAscii85(text);
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

    private static boolean skippedBetweenDigits(char letter) {
        return letter == ' ' || letter == '\n' || letter == '\r';
    }

    private static byte[] octetsOfHex(String text) {
        Octets decoded = new Octets();
        int nibblesSoFar = text.length() % 2;
        int accumulated = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            int digit = Character.digit(letter, 16);
            if (digit < 0) {
                if (skippedBetweenDigits(letter)) {
                    continue;
                }
                throw new IllegalArgumentException("not a hex digit");
            }
            accumulated = (accumulated << 4) + digit;
            if ((nibblesSoFar++ & 1) == 1) {
                decoded.write(accumulated & 0xFF);
            }
        }
        if ((nibblesSoFar & 1) == 1) {
            throw new IllegalArgumentException("odd number of hex digits");
        }
        return decoded.toArray();
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
        Octets decoded = new Octets();
        int bitsSoFar = text.length() % 8;
        if (bitsSoFar != 0) {
            bitsSoFar = 8 - bitsSoFar;
        }
        int accumulated = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            if (letter != '0' && letter != '1') {
                if (skippedBetweenDigits(letter)) {
                    continue;
                }
                throw new IllegalArgumentException("not a bit");
            }
            accumulated = (accumulated << 1) + (letter - '0');
            if (bitsSoFar++ >= 7) {
                decoded.write(accumulated & 0xFF);
                bitsSoFar = 0;
                accumulated = 0;
            }
        }
        if (bitsSoFar != 0) {
            throw new IllegalArgumentException("bits do not fill whole octets");
        }
        return decoded.toArray();
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
            boolean padded = !BASE64_URL.equals(alphabet);
            for (int each = 0; each < 4; each++) {
                if (each <= remaining) {
                    text.append(alphabet.charAt((group >> (18 - each * 6)) & 0x3F));
                } else if (padded) {
                    text.append('=');
                }
            }
        }
        return text.toString();
    }

    private static byte[] octetsOfBase64(String text, String alphabet) {
        Octets decoded = new Octets();
        boolean urlSafe = BASE64_URL.equals(alphabet);
        int group = 0;
        int inTheGroup = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            if (letter == '=') {
                if (inTheGroup == 3) {
                    decoded.write((group >> 10) & 0xFF);
                    decoded.write((group >> 2) & 0xFF);
                } else if (inTheGroup == 2 && text.indexOf('=', at + 1) >= 0) {
                    decoded.write((group >> 4) & 0xFF);
                } else {
                    throw new IllegalArgumentException(
                            "a base 64 padding that pads nothing");
                }
                return decoded.toArray();
            }
            int value = alphabet.indexOf(letter);
            if (value < 0) {
                if (!urlSafe && (letter == '-' || letter == '_')) {
                    return octetsOfBase64(text, BASE64_URL);
                }
                if (skippedBetweenDigits(letter) || letter == '\t') {
                    continue;
                }
                throw new IllegalArgumentException("not a base 64 digit");
            }
            group = (group << 6) + value;
            if (++inTheGroup == 4) {
                decoded.write((group >> 16) & 0xFF);
                decoded.write((group >> 8) & 0xFF);
                decoded.write(group & 0xFF);
                group = 0;
                inTheGroup = 0;
            }
        }
        if (inTheGroup == 0) {
            return decoded.toArray();
        }
        if (!urlSafe) {
            throw new IllegalArgumentException("a base 64 group that ends early");
        }
        if (inTheGroup == 3) {
            decoded.write((group >> 10) & 0xFF);
            decoded.write((group >> 2) & 0xFF);
        } else if (inTheGroup == 2) {
            decoded.write((group >> 4) & 0xFF);
        } else {
            throw new IllegalArgumentException("a base 64 group that ends early");
        }
        return decoded.toArray();
    }

    private static final int LONGEST_BASE36_NUMBER = 13;

    private static String base36Of(byte[] octets) {
        if (octets.length == 0) {
            return "";
        }
        if (octets.length > 8) {
            throw new ArithmeticException("more bytes than a number can hold");
        }
        long number = 0;
        for (byte each : octets) {
            number = number << 8 | each & 0xFFL;
        }
        if (number == 0) {
            return "0";
        }
        StringBuilder digits = new StringBuilder();
        while (number != 0) {
            digits.append(BASE36.charAt((int) Long.remainderUnsigned(number, 36)));
            number = Long.divideUnsigned(number, 36);
        }
        return digits.reverse().toString();
    }

    private static byte[] octetsOfBase36(String text) {
        if (text.isEmpty()) {
            return new byte[0];
        }
        if (text.length() > LONGEST_BASE36_NUMBER) {
            throw new IllegalArgumentException("more than thirteen base 36 digits");
        }
        long number = 0;
        for (int at = 0; at < text.length(); at++) {
            int digit = Character.digit(text.charAt(at), 36);
            if (digit < 0) {
                throw new IllegalArgumentException("not a base 36 digit");
            }
            number += digit * powerOfThirtySix(text.length() - at - 1);
        }
        byte[] eight = new byte[8];
        for (int at = 7; at >= 0; at--) {
            eight[at] = (byte) number;
            number >>>= 8;
        }
        return eight;
    }

    private static long powerOfThirtySix(int exponent) {
        long power = 1;
        for (int step = 0; step < exponent; step++) {
            power *= 36;
        }
        return power;
    }

    private static final int ASCII85_FIRST = '!';
    private static final int ASCII85_LAST = 'u';
    private static final char ASCII85_ZERO_GROUP = 'z';

    private static String ascii85Of(byte[] octets) {
        StringBuilder text = new StringBuilder(octets.length * 5 / 4 + 2);
        for (int at = 0; at < octets.length; at += 4) {
            int held = Math.min(4, octets.length - at);
            long group = 0;
            for (int step = 0; step < 4; step++) {
                group = group << 8
                        | (step < held ? octets[at + step] & 0xFFL : 0L);
            }
            if (held == 4 && group == 0) {
                text.append(ASCII85_ZERO_GROUP);
                continue;
            }
            char[] five = new char[5];
            for (int digit = 4; digit >= 0; digit--) {
                five[digit] = (char) (ASCII85_FIRST + group % 85);
                group /= 85;
            }
            text.append(five, 0, held + 1);
        }
        return text.toString();
    }

    private static byte[] octetsOfAscii85(String text) {
        Octets decoded = new Octets();
        long group = 0;
        int held = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            if (skippedBetweenDigits(letter) || letter == '\t') {
                continue;
            }
            if (letter == ASCII85_ZERO_GROUP && held == 0) {
                decoded.write(0);
                decoded.write(0);
                decoded.write(0);
                decoded.write(0);
                continue;
            }
            if (letter < ASCII85_FIRST || letter > ASCII85_LAST) {
                throw new IllegalArgumentException("not an ascii85 character");
            }
            group = group * 85 + (letter - ASCII85_FIRST);
            if (++held == 5) {
                writeTheTopBytesOf(decoded, group, 4);
                group = 0;
                held = 0;
            }
        }
        if (held == 1) {
            throw new IllegalArgumentException("an ascii85 group of one");
        }
        if (held > 1) {
            for (int padding = held; padding < 5; padding++) {
                group = group * 85 + 84;
            }
            writeTheTopBytesOf(decoded, group, held - 1);
        }
        return decoded.toArray();
    }

    private static void writeTheTopBytesOf(Octets decoded, long group, int wanted) {
        if (group > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("an ascii85 group above four bytes");
        }
        for (int step = 0; step < wanted; step++) {
            decoded.write((int) (group >>> (24 - step * 8)) & 0xFF);
        }
    }


    private static String withoutWhitespace(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        for (int at = 0; at < text.length(); at++) {
            if (!Character.isWhitespace(text.charAt(at))) {
                kept.append(text.charAt(at));
            }
        }
        return kept.toString();
    }

    static final int LINE_WIDTH = 64;

    static String brokenIntoLines(String text, int base, int byteCount) {
        return switch (base) {
            case 2 -> withBreaks(text, byteCount > 8, byteCount > 8 ? byteCount / 8 : 0);
            case 16 -> withBreaks(text, byteCount >= 32, byteCount / 32);
            case 64 -> withBreaks(text,
                    4 * (byteCount / 3 - 1) > LINE_WIDTH, byteCount / 3 / 16);
            default -> text;
        };
    }

    private static String withBreaks(String text, boolean leading, int closedLines) {
        if (!leading && closedLines == 0) {
            return text;
        }
        StringBuilder broken = new StringBuilder(text.length() + closedLines + 1);
        if (leading) {
            broken.append('\n');
        }
        int at = 0;
        for (int line = 0; line < closedLines; line++) {
            int ends = Math.min(text.length(), at + LINE_WIDTH);
            broken.append(text, at, ends).append('\n');
            at = ends;
        }
        return broken.append(text, at, text.length()).toString();
    }

    static final Map<String, String> DIGESTS = digestMethods();

    private static Map<String, String> digestMethods() {
        Map<String, String> theJdkCallsIt = new LinkedHashMap<>();
        theJdkCallsIt.put("md5", "MD5");
        theJdkCallsIt.put("sha1", "SHA-1");
        theJdkCallsIt.put("sha224", "SHA-224");
        theJdkCallsIt.put("sha256", "SHA-256");
        theJdkCallsIt.put("sha384", "SHA-384");
        theJdkCallsIt.put("sha512", "SHA-512");
        theJdkCallsIt.put("sha3-224", "SHA3-224");
        theJdkCallsIt.put("sha3-256", "SHA3-256");
        theJdkCallsIt.put("sha3-384", "SHA3-384");
        theJdkCallsIt.put("sha3-512", "SHA3-512");
        theJdkCallsIt.put("ripemd160", RIPEMD_160);
        theJdkCallsIt.put("xxh3", XXH_3);
        theJdkCallsIt.put("xxh32", XXH_32);
        theJdkCallsIt.put("xxh64", XXH_64);
        theJdkCallsIt.put("xxh128", XXH_128);
        theJdkCallsIt.put("md4", MD_4);
        return Map.copyOf(theJdkCallsIt);
    }

    static final List<String> CYCLIC = List.of("crc32", "adler32", "crc24", "tcp");

    private static final List<String> CATALOGUE_ORDER = List.of(
            "adler32", "crc24", "crc32",
            "md4", "md5", "ripemd160",
            "sha1", "sha224", "sha256", "sha384", "sha512",
            "sha3-224", "sha3-256", "sha3-384", "sha3-512",
            "xxh3", "xxh32", "xxh64", "xxh128",
            "tcp");

    static List<String> checksumMethods() {
        List<String> served = new ArrayList<>(DIGESTS.keySet());
        served.addAll(CYCLIC);
        return CATALOGUE_ORDER.stream().filter(served::contains).toList();
    }

    private static final int MURMUR_MIXING_MULTIPLIER = 0xcc9e2d51;

    private static final int MURMUR_COMBINING_MULTIPLIER = 0x1b873593;

    private static int blockMixedInto(int running, int block) {
        int mixed = Integer.rotateLeft(block * MURMUR_MIXING_MULTIPLIER, 15)
                * MURMUR_COMBINING_MULTIPLIER;
        return Integer.rotateLeft(running ^ mixed, 13) * 5 + 0xe6546b64;
    }

    private static int avalanched(int hash) {
        int mixed = (hash ^ (hash >>> 16)) * 0x85ebca6b;
        mixed = (mixed ^ (mixed >>> 13)) * 0xc2b2ae35;
        return mixed ^ (mixed >>> 16);
    }

    private static int littleEndianWordAt(byte[] octets, int from) {
        return (octets[from] & 0xFF)
                | ((octets[from + 1] & 0xFF) << 8)
                | ((octets[from + 2] & 0xFF) << 16)
                | ((octets[from + 3] & 0xFF) << 24);
    }

    static int murmurOf(byte[] octets) {
        int hash = 0;
        int wholeWords = octets.length / 4;
        for (int word = 0; word < wholeWords; word++) {
            hash = blockMixedInto(hash, littleEndianWordAt(octets, word * 4));
        }
        int over = octets.length & 3;
        if (over > 0) {
            int trailing = 0;
            for (int step = 0; step < over; step++) {
                trailing ^= (octets[wholeWords * 4 + step] & 0xFF) << (step * 8);
            }
            hash ^= Integer.rotateLeft(trailing * MURMUR_MIXING_MULTIPLIER, 16)
                    * MURMUR_COMBINING_MULTIPLIER;
        }
        return avalanched(hash ^ octets.length);
    }

    static int caseFoldedHashOf(byte[] utf8) {
        int hash = 0;
        for (byte each : utf8) {
            hash = blockMixedInto(hash, Character.toLowerCase(each & 0xFF));
        }
        return avalanched(hash ^ utf8.length);
    }

    static final String RIPEMD_160 = "RIPEMD160";

    static final String XXH_3 = "XXH3";

    static final String XXH_32 = "XXH32";

    static final String XXH_64 = "XXH64";

    static final String XXH_128 = "XXH128";

    static final String MD_4 = "MD4";

    static byte[] digestOf(byte[] octets, String method) {
        String algorithm = DIGESTS.get(method);
        if (RIPEMD_160.equals(algorithm)) {
            return RipeMd160.of(octets);
        }
        if (XXH_3.equals(algorithm)) {
            return XxHash3.of64MostSignificantByteFirst(octets);
        }
        if (XXH_32.equals(algorithm)) {
            return XxHash.of32MostSignificantByteFirst(octets);
        }
        if (XXH_64.equals(algorithm)) {
            return XxHash.of64MostSignificantByteFirst(octets);
        }
        if (XXH_128.equals(algorithm)) {
            return XxHash3.of128MostSignificantByteFirst(octets);
        }
        if (MD_4.equals(algorithm)) {
            return Md4.of(octets);
        }
        try {
            return java.security.MessageDigest.getInstance(DIGESTS.get(method))
                    .digest(octets);
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalArgumentException(method + " is not available here");
        }
    }

    static byte[] keyedDigestOf(byte[] octets, String method, byte[] key) {
        try {
            String algorithm = "Hmac" + DIGESTS.get(method).replace("-", "");
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(algorithm);
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    key.length == 0 ? new byte[1] : key, algorithm));
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

    static long checksumSeedOf(byte[] octets) {
        return crc24Of(octets);
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

    private static final List<String> COMPRESSIONS_WITH_NO_HEADER =
            List.of("zlib", "gzip", "deflate");

    static final List<String> COMPRESSIONS =
            List.of("zlib", "gzip", "deflate", "crush", "lzw", "lzma", "br");

    static final List<String> COMPRESSIONS_ELSEWHERE =
            List.of("lz4", "lzav");

    static byte[] compressed(byte[] octets, String method, int level) {
        return switch (method) {
            case "gzip" -> gzipped(octets, level);
            case "zlib" -> deflated(octets, level, false);
            case "deflate" -> deflated(octets, level, true);
            case "crush" -> Crush.compressed(octets, level);
            case "lzw" -> Lzw.compressed(octets, level);
            case "lzma" -> Lzma.compressed(octets, level);
            case "br" -> Brotli.compressed(octets, level);
            default -> throw new IllegalArgumentException(method);
        };
    }

    static byte[] decompressed(byte[] octets, String method, int wanted) {
        if (octets.length == 0 && COMPRESSIONS_WITH_NO_HEADER.contains(method)) {
            return octets;
        }
        byte[] whole = switch (method) {
            case "gzip" -> ungzipped(octets);
            case "zlib" -> inflated(octets, false);
            case "deflate" -> inflated(octets, true);
            case "crush" -> Crush.decompressed(octets, wanted);
            case "lzw" -> Lzw.decompressed(octets, wanted);
            case "lzma" -> Lzma.decompressed(octets, wanted);
            case "br" -> Brotli.decompressed(octets, wanted);
            default -> throw new IllegalArgumentException(method);
        };
        return wanted > 0 && whole.length > wanted
                ? java.util.Arrays.copyOf(whole, wanted)
                : whole;
    }

    private static final int GZIP_MAGIC_FIRST = 0x1F;
    private static final int GZIP_MAGIC_SECOND = 0x8B;
    private static final int GZIP_DEFLATE = 8;
    private static final int GZIP_NO_FLAGS = 0;
    private static final int GZIP_TIME_UNAVAILABLE_LENGTH = 4;
    private static final int GZIP_FASTEST = 0x04;
    private static final int GZIP_SLOWEST = 0x02;
    private static final int GZIP_UNREMARKABLE_EFFORT = 0;
    private static final int GZIP_OPERATING_SYSTEM_UNKNOWN = 0xFF;
    private static final int GZIP_HEADER_LENGTH = 10;
    private static final int GZIP_TRAILER_LENGTH = 8;

    private static byte[] gzipped(byte[] octets, int level) {
        Octets into = new Octets();
        into.write(GZIP_MAGIC_FIRST);
        into.write(GZIP_MAGIC_SECOND);
        into.write(GZIP_DEFLATE);
        into.write(GZIP_NO_FLAGS);
        for (int each = 0; each < GZIP_TIME_UNAVAILABLE_LENGTH; each++) {
            into.write(0);
        }
        into.write(howHardTheCompressorWasAskedToTry(level));
        into.write(GZIP_OPERATING_SYSTEM_UNKNOWN);
        byte[] deflated = deflated(octets, level, true);
        into.write(deflated, 0, deflated.length);
        java.util.zip.CRC32 checked = new java.util.zip.CRC32();
        checked.update(octets, 0, octets.length);
        writeLittleEndian(into, checked.getValue());
        writeLittleEndian(into, octets.length);
        return into.toArray();
    }

    private static int howHardTheCompressorWasAskedToTry(int level) {
        int asked = effortAskedFor(level);
        if (asked < 2) {
            return GZIP_FASTEST;
        }
        return asked >= 8 ? GZIP_SLOWEST : GZIP_UNREMARKABLE_EFFORT;
    }

    static int effortAskedFor(int level) {
        return level < 0 || level > SLOWEST_DEFLATE ? SLOWEST_DEFLATE : level;
    }

    private static final int SLOWEST_DEFLATE = 9;

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

    private static byte[] deflated(byte[] octets, int level, boolean raw) {
        java.util.zip.Deflater deflater =
                new java.util.zip.Deflater(effortAskedFor(level), raw);
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
                into.write(page, 0, written);
                if (written == 0 && !inflater.finished()
                        && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("compressed data ends early");
                }
            }
            return into.toArray();
        } catch (java.util.zip.DataFormatException notDeflate) {
            throw new IllegalArgumentException("not deflate data");
        } finally {
            inflater.end();
        }
    }

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

    static boolean hasCharacterSet(String asked) {
        return charsetNamed(asked) != null;
    }

    static String textDecodedAs(byte[] octets, java.nio.charset.Charset charset) {
        boolean bigEndian = "UTF-32BE".equalsIgnoreCase(charset.name());
        if (!bigEndian && !"UTF-32LE".equalsIgnoreCase(charset.name())) {
            return new String(octets, charset);
        }
        return utf32KeepingTheLeadingMarkTheJvmWouldDrop(octets, bigEndian);
    }

    private static String utf32KeepingTheLeadingMarkTheJvmWouldDrop(
            byte[] octets, boolean bigEndian) {
        StringBuilder text = new StringBuilder();
        for (int at = 0; at + 4 <= octets.length; at += 4) {
            int point = 0;
            for (int each = 0; each < 4; each++) {
                int octet = octets[at + (bigEndian ? each : 3 - each)] & 0xFF;
                point = (point << 8) | octet;
            }
            text.appendCodePoint(point);
        }
        return text.toString();
    }

    static String textBehindAnyMark(byte[] octets) {
        java.nio.charset.Charset theMarkAnnounces;
        int width;
        if (startsWith(octets, 0xEF, 0xBB, 0xBF)) {
            theMarkAnnounces = java.nio.charset.StandardCharsets.UTF_8;
            width = 3;
        } else if (startsWith(octets, 0xFF, 0xFE, 0x00, 0x00)) {
            theMarkAnnounces = java.nio.charset.Charset.forName("UTF-32LE");
            width = 4;
        } else if (startsWith(octets, 0x00, 0x00, 0xFE, 0xFF)) {
            theMarkAnnounces = java.nio.charset.Charset.forName("UTF-32BE");
            width = 4;
        } else if (startsWith(octets, 0xFE, 0xFF)) {
            theMarkAnnounces = java.nio.charset.StandardCharsets.UTF_16BE;
            width = 2;
        } else if (startsWith(octets, 0xFF, 0xFE)) {
            theMarkAnnounces = java.nio.charset.StandardCharsets.UTF_16LE;
            width = 2;
        } else {
            return new String(octets, java.nio.charset.StandardCharsets.UTF_8);
        }
        return new String(octets, width, octets.length - width, theMarkAnnounces);
    }

    private static boolean startsWith(byte[] octets, int... expected) {
        if (octets.length < expected.length) {
            return false;
        }
        for (int at = 0; at < expected.length; at++) {
            if ((octets[at] & 0xFF) != expected[at]) {
                return false;
            }
        }
        return true;
    }

    static java.nio.charset.Charset charsetNamed(String asked) {
        String canonical = CODEPAGES.getOrDefault(
                asked.toLowerCase(java.util.Locale.ROOT), asked);
        try {
            return java.nio.charset.Charset.forName(canonical);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static final String REBOL_CODEPAGES =
            "UTF-8:65001,CP65001;"
            + "UTF-16LE:1200,UTF16LE,UCS-2LE,UCS2LE,UCS-2-INTERNAL,CP1200;"
            + "UTF-16BE:1201,UTF16BE,UCS-2BE,UCS2BE,unicodeFFFE,CP1201;"
            + "UTF-32LE:12000,UTF32LE,UCS-4LE,UCS4LE,CP12000;"
            + "UTF-32BE:12001,UTF32BE,UCS-4BE,UCS4BE,CP12001;UTF-16:2,UCS-2,UCS2;"
            + "UTF-32:4,UCS-4,UCS4;ANSI_X3.4-1968:20127;ISO-8859-1:28591;"
            + "CP1250:1250,MS-EE;"
            + "CP1251:1251,MS-CYRL;CP1252:1252,MS-ANSI;CP1253:1253,MS-GREEK;"
            + "CP1254:1254,MS-TURK;CP1255:1255,MS-HEBR;CP1256:1256,MS-ARAB;"
            + "CP1257:1257,WINBALTRIM;CP1258:1258;850:850;862:862,DOS-862;866:866;"
            + "CP874:874;CP932:932,SHIFFT_JIS,SHIFFT_JIS-MS,SJIS-MS,SJIS-OPEN,SJIS-WIN;"
            + "CP50221:50221,ISO-2022-JP-MS,ISO2022-JP,ISO2022-JP-MS,WINDOWS-50221;"
            + "CP936:936;CP950:950,BIG-5;CP949:949,UHC;437:437;CP737:737;"
            + "CP775:775,CSPC775BALTIC;852:852;855:855,CSIBM855;857:857;CP858:858;"
            + "860:860;861:861;863:863;CP864:864;865:865;869:869;IBM037:37;IBM500:500;"
            + "ASMO-708:708;IBM870:870;CP875:875;IBM1026:1026;IBM01140:1140;"
            + "IBM01141:1141;IBM01142:1142;IBM01143:1143;IBM01144:1144;IBM01145:1145;"
            + "IBM01146:1146;IBM01147:1147;IBM01148:1148;IBM01149:1149;IBM273:20273;"
            + "IBM277:20277;IBM278:20278;IBM280:20280;IBM284:20284;IBM285:20285;"
            + "IBM290:20290;IBM297:20297;IBM420:20420;IBM424:20424;IBM-Thai:20838;"
            + "KOI8-R:20866;IBM871:20871;EUC-JP:20932;CP1025:21025;KOI8-U:21866;"
            + "ISO-8859-2:28592,ISO_8859_2;ISO-8859-3:28593,ISO_8859_3;"
            + "ISO-8859-4:28594,ISO_8859_4;ISO-8859-5:28595,ISO_8859_5;"
            + "ISO-8859-6:28596,ISO_8859_6;ISO-8859-7:28597,ISO_8859_7;"
            + "ISO-8859-8:28598,ISO_8859_8;ISO-8859-9:28599,ISO_8859_9;"
            + "ISO-8859-13:28603,ISO_8859_13;ISO-8859-15:28605,ISO_8859_15;"
            + "ISO-2022-JP:50220,50222;ISO-2022-KR:50225,ISO2022-KR;EUC-CN:51936;"
            + "EUC-KR:51949;GB18030:54936";

    private static final java.util.Map<String, String> CODEPAGES = spellingsByName();

    private static java.util.Map<String, String> spellingsByName() {
        java.util.Map<String, String> found = new java.util.HashMap<>();
        for (String group : REBOL_CODEPAGES.split(";")) {
            String[] halves = group.split(":", 2);
            for (String spelling : halves[1].split(",")) {
                found.put(spelling.toLowerCase(java.util.Locale.ROOT), halves[0]);
            }
        }
        return java.util.Map.copyOf(found);
    }

    static final List<String> PNG_FILTERS = List.of("none", "sub", "up", "average", "paeth");

    private static int paethPredictorBreakingTiesLeftThenAboveThenAboveLeft(
            int left, int above, int aboveLeft) {
        int estimate = left + above - aboveLeft;
        int toLeft = Math.abs(estimate - left);
        int toAbove = Math.abs(estimate - above);
        int toAboveLeft = Math.abs(estimate - aboveLeft);
        if (toLeft <= toAbove && toLeft <= toAboveLeft) {
            return left;
        }
        return toAbove <= toAboveLeft ? above : aboveLeft;
    }

    static byte[] pngFiltered(byte[] data, int width, int filter, int bytesPerPixel) {
        int rows = data.length / width;
        byte[] out = new byte[data.length];
        byte[] previous = zerosSoTheFirstLineEncodesAsItself(width);
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

    static byte[] pngUnfiltered(
            byte[] data, int width, int namedFilter, int bytesPerPixel) {

        boolean everyLineOpensWithItsOwnFilterByte = namedFilter < 0;
        int stride = everyLineOpensWithItsOwnFilterByte ? width + 1 : width;
        int rows = data.length / stride;
        byte[] out = new byte[rows * width];
        byte[] previous = zerosSoTheFirstLineEncodesAsItself(width);
        for (int row = 0; row < rows; row++) {
            int from = row * stride;
            int filter = namedFilter;
            if (everyLineOpensWithItsOwnFilterByte) {
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

    private static byte[] zerosSoTheFirstLineEncodesAsItself(int width) {
        return new byte[width];
    }

    private static void applyOneLine(
            byte[] source, int from, byte[] out, int into, int width,
            int filter, int bytesPerPixel, byte[] previous, boolean filtering) {

        for (int at = 0; at < width; at++) {
            int here = source[from + at] & 0xFF;
            int left = at >= bytesPerPixel
                    ? (filtering
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
                        ? paethPredictorBreakingTiesLeftThenAboveThenAboveLeft(
                                left, above, aboveLeft)
                        : above;
                default -> 0;
            };
            out[into + at] = (byte) (filtering ? here - prediction : here + prediction);
        }
    }

    static void swapEndian(byte[] octets, int howFar, int width) {
        if (width != 2 && width != 4 && width != 8) {
            throw new IllegalArgumentException("width " + width);
        }
        for (int at = 0; at + width <= Math.min(howFar, octets.length); at += width) {
            for (int each = 0; each < width / 2; each++) {
                byte held = octets[at + each];
                octets[at + each] = octets[at + width - 1 - each];
                octets[at + width - 1 - each] = held;
            }
        }
    }
}
