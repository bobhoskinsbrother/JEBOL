package org.jebol.domain.eval;

import org.jebol.domain.eval.compression.brotli.Brotli;
import org.jebol.domain.eval.compression.Crush;
import org.jebol.domain.eval.compression.Lzma;
import org.jebol.domain.eval.compression.Lzw;
import org.jebol.domain.eval.compression.Octets;
import org.jebol.domain.eval.compression.deflate.DeflateCompressor;
import org.jebol.domain.eval.compression.lz4.Lz4;
import org.jebol.domain.eval.compression.lzav.Lzav;
import org.jebol.domain.eval.hashing.Md4;
import org.jebol.domain.eval.hashing.RipeMd160;
import org.jebol.domain.eval.hashing.XxHash;
import org.jebol.domain.eval.hashing.XxHash3;
import org.jebol.domain.host.DigestPort;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.zip.Adler32;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class Encodings {

    private static final String URI_UNESCAPED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    + "!#$&'()*+,-./:;=?@_~";

    private static final String URI_COMPONENT_UNESCAPED =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    + "!'()*-._~";

    public boolean isKeptByUri(int octet) {
        return octet < 128 && URI_UNESCAPED.indexOf(octet) >= 0;
    }

    public boolean isKeptByUriComponent(int octet) {
        return octet < 128 && URI_COMPONENT_UNESCAPED.indexOf(octet) >= 0;
    }

    char escapedSpaceFor(char escape) {
        return escape == '=' ? '_' : '+';
    }

    public byte[] percentEncode(
            byte[] octets, IntPredicate keep,
            char escape, boolean spaceIsSpecial) {

        Octets encoded = new Octets();
        for (byte each : octets) {
            int octet = each & 0xFF;
            if (spaceIsSpecial && octet == ' ') {
                encoded.write(escapedSpaceFor(escape));
                continue;
            }
            if (spaceIsSpecial && octet == escapedSpaceFor(escape)) {
                escapeInto(encoded, escape, octet);
                continue;
            }
            if (keep.test(octet)) {
                encoded.write(octet);
                continue;
            }
            escapeInto(encoded, escape, octet);
        }
        return encoded.toArray();
    }

    private void escapeInto(Octets encoded, char escape, int octet) {
        String digits = "%02X".formatted(octet);
        encoded.write(escape);
        encoded.write(digits.charAt(0));
        encoded.write(digits.charAt(1));
    }

    public byte[] percentDecode(String text, char escape, boolean spaceIsSpecial) {
        Octets octets = new Octets();
        char special = escapedSpaceFor(escape);
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

    public boolean setHolds(BitsetValue set, int octet) {
        return set.holds(octet);
    }

    public static final List<Integer> BASES = List.of(2, 16, 36, 64, 85);

    private static final String BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final String BASE64_URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    private static final String BASE36 =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public String enbase(byte[] octets, int base, boolean urlSafe) {
        return switch (base) {
            case 16 -> hexText(octets);
            case 2 -> bitsAsText(octets);
            case 64 -> writeBase64Of(octets, urlSafe ? BASE64_URL : BASE64);
            case 36 -> writeBase36Of(octets);
            case 85 -> writeAscii85Of(octets);
            default -> throw new IllegalArgumentException("base " + base);
        };
    }

    public byte[] debase(String text, int base, boolean urlSafe) {
        return switch (base) {
            case 16 -> octetsFromHex(text);
            case 2 -> octetsFromBits(text);
            case 64 -> octetsFromBase64(text, urlSafe ? BASE64_URL : BASE64);
            case 36 -> octetsFromBase36(withoutWhitespace(text));
            case 85 -> octetsFromAscii85(text);
            default -> throw new IllegalArgumentException("base " + base);
        };
    }

    private String hexText(byte[] octets) {
        StringBuilder text = new StringBuilder(octets.length * 2);
        for (byte each : octets) {
            text.append("%02X".formatted(each & 0xFF));
        }
        return text.toString();
    }

    private boolean isSkippedBetweenDigits(char letter) {
        return letter == ' ' || letter == '\n' || letter == '\r';
    }

    private byte[] octetsFromHex(String text) {
        Octets decoded = new Octets();
        int nibblesSoFar = text.length() % 2;
        int accumulated = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            int digit = Character.digit(letter, 16);
            if (digit < 0) {
                if (isSkippedBetweenDigits(letter)) {
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

    private String bitsAsText(byte[] octets) {
        StringBuilder text = new StringBuilder(octets.length * 8);
        for (byte each : octets) {
            for (int bit = 7; bit >= 0; bit--) {
                text.append((each >> bit) & 1);
            }
        }
        return text.toString();
    }

    private byte[] octetsFromBits(String text) {
        Octets decoded = new Octets();
        int bitsSoFar = text.length() % 8;
        if (bitsSoFar != 0) {
            bitsSoFar = 8 - bitsSoFar;
        }
        int accumulated = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            if (letter != '0' && letter != '1') {
                if (isSkippedBetweenDigits(letter)) {
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

    private String writeBase64Of(byte[] octets, String alphabet) {
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

    private byte[] octetsFromBase64(String text, String alphabet) {
        Octets decoded = new Octets();
        boolean urlSafe = BASE64_URL.equals(alphabet);
        int group = 0;
        int inGroup = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            if (letter == '=') {
                if (inGroup == 3) {
                    decoded.write((group >> 10) & 0xFF);
                    decoded.write((group >> 2) & 0xFF);
                } else if (inGroup == 2 && text.indexOf('=', at + 1) >= 0) {
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
                    return octetsFromBase64(text, BASE64_URL);
                }
                if (isSkippedBetweenDigits(letter) || letter == '\t') {
                    continue;
                }
                throw new IllegalArgumentException("not a base 64 digit");
            }
            group = (group << 6) + value;
            if (++inGroup == 4) {
                decoded.write((group >> 16) & 0xFF);
                decoded.write((group >> 8) & 0xFF);
                decoded.write(group & 0xFF);
                group = 0;
                inGroup = 0;
            }
        }
        if (inGroup == 0) {
            return decoded.toArray();
        }
        if (!urlSafe) {
            throw new IllegalArgumentException("a base 64 group that ends early");
        }
        if (inGroup == 3) {
            decoded.write((group >> 10) & 0xFF);
            decoded.write((group >> 2) & 0xFF);
        } else if (inGroup == 2) {
            decoded.write((group >> 4) & 0xFF);
        } else {
            throw new IllegalArgumentException("a base 64 group that ends early");
        }
        return decoded.toArray();
    }

    private static final int LONGEST_BASE36_NUMBER = 13;

    private String writeBase36Of(byte[] octets) {
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

    private byte[] octetsFromBase36(String text) {
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
            number += digit * raiseThirtySixTo(text.length() - at - 1);
        }
        byte[] eight = new byte[8];
        for (int at = 7; at >= 0; at--) {
            eight[at] = (byte) number;
            number >>>= 8;
        }
        return eight;
    }

    private long raiseThirtySixTo(int exponent) {
        long power = 1;
        for (int step = 0; step < exponent; step++) {
            power *= 36;
        }
        return power;
    }

    private static final int ASCII85_FIRST = '!';
    private static final int ASCII85_LAST = 'u';
    private static final char ASCII85_ZERO_GROUP = 'z';

    private String writeAscii85Of(byte[] octets) {
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

    private byte[] octetsFromAscii85(String text) {
        Octets decoded = new Octets();
        long group = 0;
        int held = 0;
        for (int at = 0; at < text.length(); at++) {
            char letter = text.charAt(at);
            if (isSkippedBetweenDigits(letter) || letter == '\t') {
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
                writeTopBytes(decoded, group, 4);
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
            writeTopBytes(decoded, group, held - 1);
        }
        return decoded.toArray();
    }

    private void writeTopBytes(Octets decoded, long group, int wanted) {
        if (group > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("an ascii85 group above four bytes");
        }
        for (int step = 0; step < wanted; step++) {
            decoded.write((int) (group >>> (24 - step * 8)) & 0xFF);
        }
    }


    private String withoutWhitespace(String text) {
        StringBuilder kept = new StringBuilder(text.length());
        for (int at = 0; at < text.length(); at++) {
            if (!Character.isWhitespace(text.charAt(at))) {
                kept.append(text.charAt(at));
            }
        }
        return kept.toString();
    }

    static final int LINE_WIDTH = 64;

    public String breakIntoLines(String text, int base, int byteCount) {
        return switch (base) {
            case 2 -> withBreaks(text, byteCount > 8, byteCount > 8 ? byteCount / 8 : 0);
            case 16 -> withBreaks(text, byteCount >= 32, byteCount / 32);
            case 64 -> withBreaks(text,
                    4 * (byteCount / 3 - 1) > LINE_WIDTH, byteCount / 3 / 16);
            default -> text;
        };
    }

    private String withBreaks(String text, boolean leading, int closedLines) {
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

    public static final Map<String, String> DIGESTS = Map.ofEntries(
            Map.entry("md5", "MD5"),
            Map.entry("sha1", "SHA-1"),
            Map.entry("sha224", "SHA-224"),
            Map.entry("sha256", "SHA-256"),
            Map.entry("sha384", "SHA-384"),
            Map.entry("sha512", "SHA-512"),
            Map.entry("sha3-224", "SHA3-224"),
            Map.entry("sha3-256", "SHA3-256"),
            Map.entry("sha3-384", "SHA3-384"),
            Map.entry("sha3-512", "SHA3-512"),
            Map.entry("ripemd160", "RIPEMD160"),
            Map.entry("xxh3", "XXH3"),
            Map.entry("xxh32", "XXH32"),
            Map.entry("xxh64", "XXH64"),
            Map.entry("xxh128", "XXH128"),
            Map.entry("md4", "MD4"));

    public static final List<String> CYCLIC = List.of("crc32", "adler32", "crc24", "tcp");

    static final Set<String> JAVA_DIGESTS = Set.of(
            "ripemd160", "xxh3", "xxh32", "xxh64", "xxh128", "md4");

    private static final List<String> CATALOGUE_ORDER = List.of(
            "adler32", "crc24", "crc32",
            "md4", "md5", "ripemd160",
            "sha1", "sha224", "sha256", "sha384", "sha512",
            "sha3-224", "sha3-256", "sha3-384", "sha3-512",
            "xxh3", "xxh32", "xxh64", "xxh128",
            "tcp");

    List<String> listChecksumMethods() {
        List<String> served = new ArrayList<>(
                DIGESTS.keySet().stream().filter(this::canDigest).toList());
        served.addAll(CYCLIC);
        return CATALOGUE_ORDER.stream().filter(served::contains).toList();
    }

    private static final int MURMUR_MIXING_MULTIPLIER = 0xcc9e2d51;

    private static final int MURMUR_COMBINING_MULTIPLIER = 0x1b873593;

    private int mixBlockInto(int running, int block) {
        int mixed = Integer.rotateLeft(block * MURMUR_MIXING_MULTIPLIER, 15)
                * MURMUR_COMBINING_MULTIPLIER;
        return Integer.rotateLeft(running ^ mixed, 13) * 5 + 0xe6546b64;
    }

    private int avalanche(int hash) {
        int mixed = (hash ^ (hash >>> 16)) * 0x85ebca6b;
        mixed = (mixed ^ (mixed >>> 13)) * 0xc2b2ae35;
        return mixed ^ (mixed >>> 16);
    }

    private int readLittleEndianWordAt(byte[] octets, int from) {
        return (octets[from] & 0xFF)
                | ((octets[from + 1] & 0xFF) << 8)
                | ((octets[from + 2] & 0xFF) << 16)
                | ((octets[from + 3] & 0xFF) << 24);
    }

    public int hashWithMurmur(byte[] octets) {
        int hash = 0;
        int wholeWords = octets.length / 4;
        for (int word = 0; word < wholeWords; word++) {
            hash = mixBlockInto(hash, readLittleEndianWordAt(octets, word * 4));
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
        return avalanche(hash ^ octets.length);
    }

    public int hashCaseFolded(byte[] utf8) {
        int hash = 0;
        for (byte each : utf8) {
            hash = mixBlockInto(hash, Character.toLowerCase(each & 0xFF));
        }
        return avalanche(hash ^ utf8.length);
    }

    public static final String RIPEMD_160 = "RIPEMD160";

    public static final String XXH_3 = "XXH3";

    public static final String XXH_32 = "XXH32";

    public static final String XXH_64 = "XXH64";

    public static final String XXH_128 = "XXH128";

    public static final String MD_4 = "MD4";

    private final RipeMd160 ripeMd160 = new RipeMd160();
    private final Md4 md4 = new Md4();
    private final XxHash xxHash = new XxHash();
    private final XxHash3 xxHash3 = new XxHash3();
    private final Lzw lzw = new Lzw();

    private final DigestPort port;

    public Encodings(DigestPort port) {
        this.port = port;
    }

    public byte[] digestOf(byte[] octets, String method) {
        String algorithm = DIGESTS.get(method);
        if (RIPEMD_160.equals(algorithm)) {
            return ripeMd160.of(octets);
        }
        if (XXH_3.equals(algorithm)) {
            return xxHash3.of64MostSignificantByteFirst(octets);
        }
        if (XXH_32.equals(algorithm)) {
            return xxHash.of32MostSignificantByteFirst(octets);
        }
        if (XXH_64.equals(algorithm)) {
            return xxHash.of64MostSignificantByteFirst(octets);
        }
        if (XXH_128.equals(algorithm)) {
            return xxHash3.of128MostSignificantByteFirst(octets);
        }
        if (MD_4.equals(algorithm)) {
            return md4.of(octets);
        }
        try {
            return port.digestOf(method, octets);
        } catch (DigestPort.Refused refused) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, method);
        }
    }

    public byte[] digestWithKey(byte[] octets, String method, byte[] key) {
        try {
            return port.digestWithKey(method, octets, key);
        } catch (DigestPort.Refused refused) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, method);
        }
    }

    public DigestPort.RunningDigest startRunningDigest(String method) {
        try {
            return port.start(method);
        } catch (DigestPort.Refused refused) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, method);
        }
    }

    public boolean canDigest(String method) {
        return JAVA_DIGESTS.contains(method) || port.methods().contains(method);
    }

    public long computeCyclicCheck(byte[] octets, String method) {
        return switch (method) {
            case "crc32" -> computeChecksum(new CRC32(), octets);
            case "adler32" -> computeChecksum(new Adler32(), octets);
            case "crc24" -> computeCrc24(octets);
            case "tcp" -> computeTcpSum(octets);
            default -> throw new IllegalArgumentException(method);
        };
    }

    private long computeChecksum(Checksum running, byte[] octets) {
        running.update(octets, 0, octets.length);
        return running.getValue();
    }

    public long computeChecksumSeed(byte[] octets) {
        return computeCrc24(octets);
    }

    private long computeCrc24(byte[] octets) {
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

    private long computeTcpSum(byte[] octets) {
        int running = 0;
        for (int at = 0; at + 1 < octets.length; at += 2) {
            running += ((octets[at] & 0xFF) << 8) | (octets[at + 1] & 0xFF);
        }
        if (octets.length % 2 != 0) {
            running += octets[octets.length - 1] & 0xFF;
        }
        running = (running >>> 16) + (running & 0xFFFF);
        running += running >>> 16;
        return (~running) & 0xFFFF;
    }

    private static final List<String> COMPRESSIONS_WITH_NO_HEADER =
            List.of("zlib", "gzip", "deflate");

    public static final List<String> COMPRESSIONS =
            List.of("deflate", "zlib", "gzip", "br", "crush", "lz4", "lzav", "lzma", "lzw");

    private static final List<String> COMPRESSIONS_SIZED_BY_THEIR_OWN_DECODER =
            List.of("lz4", "lzav");

    public byte[] compress(byte[] octets, String method, int level) {
        return switch (method) {
            case "gzip" -> new DeflateCompressor(level).wrapInGzip(octets);
            case "zlib" -> new DeflateCompressor(level).wrapInZlib(octets);
            case "deflate" -> new DeflateCompressor(level).deflate(octets);
            case "crush" -> Crush.compress(octets, level);
            case "lzw" -> lzw.compress(octets, level);
            case "lzma" -> Lzma.compress(octets, level);
            case "br" -> Brotli.compress(octets, level);
            case "lz4" -> Lz4.compress(octets, level);
            case "lzav" -> Lzav.compress(octets);
            default -> throw new IllegalArgumentException(method);
        };
    }

    public byte[] decompress(byte[] octets, String method, int wanted) {
        if (octets.length == 0 && COMPRESSIONS_WITH_NO_HEADER.contains(method)) {
            return octets;
        }
        if (COMPRESSIONS_SIZED_BY_THEIR_OWN_DECODER.contains(method)) {
            return "lz4".equals(method) ? Lz4.decompress(octets, wanted) : Lzav.decompress(octets, wanted);
        }
        byte[] whole = switch (method) {
            case "gzip" -> ungzip(octets);
            case "zlib" -> inflate(octets, false);
            case "deflate" -> inflate(octets, true);
            case "crush" -> Crush.decompress(octets, wanted);
            case "lzw" -> lzw.decompress(octets, wanted);
            case "lzma" -> Lzma.decompress(octets, wanted);
            case "br" -> Brotli.decompress(octets, wanted);
            default -> throw new IllegalArgumentException(method);
        };
        return wanted > 0 && whole.length > wanted && cutsShortToSizeAsked(method)
                ? Arrays.copyOf(whole, wanted)
                : whole;
    }

    public boolean cutsShortToSizeAsked(String method) {
        return !COMPRESSIONS_WITH_NO_HEADER.contains(method);
    }

    private static final int GZIP_MAGIC_FIRST = 0x1F;
    private static final int GZIP_MAGIC_SECOND = 0x8B;
    private static final int GZIP_HEADER_LENGTH = 10;
    private static final int GZIP_TRAILER_LENGTH = 8;

    /** Decompresses deflate, zlib or gzip as COMPRESS's built-in method does, whatever an extension registered. */
    public static byte[] inflateAsBuiltIn(byte[] octets, String method) {
        return switch (method) {
            case "gzip" -> ungzip(octets);
            case "zlib" -> inflate(octets, false);
            default -> inflate(octets, true);
        };
    }

    private static byte[] ungzip(byte[] octets) {
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
            from = skipPastNextZero(octets, from);
        }
        if ((flags & 0x10) != 0) {
            from = skipPastNextZero(octets, from);
        }
        if ((flags & 0x02) != 0) {
            from += 2;
        }
        int length = octets.length - from - GZIP_TRAILER_LENGTH;
        if (length < 0) {
            throw new IllegalArgumentException("gzip data ends early");
        }
        return inflate(Arrays.copyOfRange(octets, from, from + length), true);
    }

    private static int skipPastNextZero(byte[] octets, int from) {
        int at = from;
        while (at < octets.length && octets[at] != 0) {
            at++;
        }
        return at + 1;
    }

    private static byte[] inflate(byte[] octets, boolean raw) {
        Inflater inflater = new Inflater(raw);
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
        } catch (DataFormatException notDeflate) {
            throw new IllegalArgumentException("not deflate data");
        } finally {
            inflater.end();
        }
    }

    public boolean cloak(boolean decode, byte[] octets, byte[] key) {
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

    public byte[] hashKey(byte[] key) {
        if (key.length == 0) {
            return key;
        }
        byte[] cycled = new byte[20];
        for (int at = 0; at < cycled.length; at++) {
            cycled[at] = key[at % key.length];
        }
        return digestOf(cycled, "sha1");
    }

    public String textDecodedAs(byte[] octets, Charset charset) {
        boolean bigEndian = "UTF-32BE".equalsIgnoreCase(charset.name());
        if (!bigEndian && !"UTF-32LE".equalsIgnoreCase(charset.name())) {
            return new String(octets, charset);
        }
        return decodeUtf32KeepingLeadingMark(octets, bigEndian);
    }

    private String decodeUtf32KeepingLeadingMark(
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

    String textBehindAnyMark(byte[] octets) {
        Charset markAnnounces;
        int width;
        if (startsWith(octets, 0xEF, 0xBB, 0xBF)) {
            markAnnounces = StandardCharsets.UTF_8;
            width = 3;
        } else if (startsWith(octets, 0xFF, 0xFE, 0x00, 0x00)) {
            markAnnounces = Charset.forName("UTF-32LE");
            width = 4;
        } else if (startsWith(octets, 0x00, 0x00, 0xFE, 0xFF)) {
            markAnnounces = Charset.forName("UTF-32BE");
            width = 4;
        } else if (startsWith(octets, 0xFE, 0xFF)) {
            markAnnounces = StandardCharsets.UTF_16BE;
            width = 2;
        } else if (startsWith(octets, 0xFF, 0xFE)) {
            markAnnounces = StandardCharsets.UTF_16LE;
            width = 2;
        } else {
            return new String(octets, StandardCharsets.UTF_8);
        }
        return new String(octets, width, octets.length - width, markAnnounces);
    }

    private boolean startsWith(byte[] octets, int... expected) {
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

    public Optional<Charset> findCharsetNamed(String asked) {
        String canonical = codepages.getOrDefault(asked.toLowerCase(Locale.ROOT), asked);
        try {
            return Optional.of(Charset.forName(canonical));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
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

    private final Map<String, String> codepages = listSpellingsByName();

    private Map<String, String> listSpellingsByName() {
        Map<String, String> found = new HashMap<>();
        for (String group : REBOL_CODEPAGES.split(";")) {
            String[] halves = group.split(":", 2);
            for (String spelling : halves[1].split(",")) {
                found.put(spelling.toLowerCase(Locale.ROOT), halves[0]);
            }
        }
        return Map.copyOf(found);
    }

    public static final List<String> PNG_FILTERS = List.of("sub", "up", "average", "paeth");

    private static final int FIRST_PNG_FILTER = 1;

    private int predictWithPaeth(
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

    public byte[] pngFilter(byte[] data, int width, int filter, int bytesPerPixel) {
        int rows = data.length / width;
        byte[] out = new byte[data.length];
        byte[] previous = makeZeroLineAbove(width);
        for (int row = 0; row < rows; row++) {
            int from = row * width;
            applyOneLine(data, from, out, from, width, filter, bytesPerPixel,
                    previous, true);
            previous = Arrays.copyOfRange(data, from, from + width);
        }
        return out;
    }

    public byte[] pngUnfilter(
            byte[] data, int width, int namedFilter, int bytesPerPixel) {

        boolean everyLineOpensWithItsOwnFilterByte = namedFilter < 0;
        int stride = everyLineOpensWithItsOwnFilterByte ? width + 1 : width;
        int rows = data.length / stride;
        byte[] out = new byte[everyLineOpensWithItsOwnFilterByte ? data.length - rows : data.length];
        byte[] previous = makeZeroLineAbove(width);
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
            previous = Arrays.copyOfRange(out, into, into + width);
        }
        return out;
    }

    private byte[] makeZeroLineAbove(int width) {
        return new byte[width];
    }

    private void applyOneLine(
            byte[] source, int from, byte[] out, int into, int width,
            int filter, int bytesPerPixel, byte[] previous, boolean filtering) {

        if (filter < FIRST_PNG_FILTER || filter > PNG_FILTERS.size()) {
            return;
        }
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
                        ? predictWithPaeth(
                                left, above, aboveLeft)
                        : above;
                default -> 0;
            };
            out[into + at] = (byte) (filtering ? here - prediction : here + prediction);
        }
    }

    public void swapEndian(byte[] octets, int howFar, int width) {
        for (int at = 0; at + width <= Math.min(howFar, octets.length); at += width) {
            for (int each = 0; each < width / 2; each++) {
                byte held = octets[at + each];
                octets[at + each] = octets[at + width - 1 - each];
                octets[at + width - 1 - each] = held;
            }
        }
    }
}
