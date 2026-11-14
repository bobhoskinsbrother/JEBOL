package org.jebol.adapter.host;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class Uts46 {

    private static final int VALID = 0;
    private static final int IGNORED = 1;
    private static final int MAPPED = 2;

    private static final int BIDI_L = 0;
    private static final int BIDI_R = 1;
    private static final int BIDI_AL = 2;
    private static final int BIDI_EN = 3;
    private static final int BIDI_ES = 4;
    private static final int BIDI_ET = 5;
    private static final int BIDI_AN = 6;
    private static final int BIDI_CS = 7;
    private static final int BIDI_NSM = 8;
    private static final int BIDI_BN = 9;
    private static final int BIDI_ON = 13;

    private static final int JOINING_D = 2;
    private static final int JOINING_L = 3;
    private static final int JOINING_R = 4;
    private static final int JOINING_T = 5;

    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int FULL_STOP = '.';
    private static final int LAST_ASCII = 0x7F;
    private static final String ACE_PREFIX = "xn--";

    private static final Set<Integer> FORBIDDEN_IN_A_DOMAIN = Set.of(
            0x20, (int) '#', (int) '%', (int) '/', (int) ':', (int) '<', (int) '>', (int) '?', (int) '@',
            (int) '[', (int) '\\', (int) ']', (int) '^', (int) '|', 0x7F);
    private static final int FIRST_PRINTABLE_ASCII = 0x20;

    private static final Set<Integer> RTL_LABEL_ALLOWS = Set.of(
            BIDI_R, BIDI_AL, BIDI_AN, BIDI_EN, BIDI_ES, BIDI_CS, BIDI_ET, BIDI_ON, BIDI_BN, BIDI_NSM);
    private static final Set<Integer> LTR_LABEL_ALLOWS = Set.of(
            BIDI_L, BIDI_EN, BIDI_ES, BIDI_CS, BIDI_ET, BIDI_ON, BIDI_BN, BIDI_NSM);

    private Uts46() {
    }

    public static Optional<String> toAsciiHostName(String host) {
        return toAscii(host).filter(ascii -> !ascii.isEmpty() && ascii.codePoints().noneMatch(Uts46::isForbiddenInADomain));
    }

    public static Optional<String> toAscii(String domain) {
        Optional<String> mapped = mapEachCodePoint(domain);
        if (mapped.isEmpty()) {
            return Optional.empty();
        }
        String normalized = Normalizer.normalize(mapped.get(), Normalizer.Form.NFC);
        List<String> labels = new ArrayList<>();
        for (String label : normalized.split("\\.", -1)) {
            Optional<String> converted = convertAndValidateLabel(label);
            if (converted.isEmpty()) {
                return Optional.empty();
            }
            labels.add(converted.get());
        }
        if (isBidiDomain(labels) && !labels.stream().allMatch(Uts46::satisfiesBidiRule)) {
            return Optional.empty();
        }
        return Optional.of(String.join(".", labels.stream().map(Uts46::encodeLabelToAscii).toList()));
    }

    private static boolean isForbiddenInADomain(int codePoint) {
        return codePoint < FIRST_PRINTABLE_ASCII || FORBIDDEN_IN_A_DOMAIN.contains(codePoint);
    }

    private static Optional<String> mapEachCodePoint(String domain) {
        StringBuilder mapped = new StringBuilder();
        for (int codePoint : domain.codePoints().toArray()) {
            Tables.Mapping entry = Tables.INSTANCE.mappingFor(codePoint);
            switch (entry.status()) {
                case VALID -> mapped.appendCodePoint(codePoint);
                case IGNORED -> {
                }
                case MAPPED -> entry.replacement().codePoints().forEach(mapped::appendCodePoint);
                default -> {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(mapped.toString());
    }

    private static Optional<String> convertAndValidateLabel(String label) {
        if (!label.startsWith(ACE_PREFIX)) {
            return isValidLabel(label) ? Optional.of(label) : Optional.empty();
        }
        if (!isAllAscii(label)) {
            return Optional.empty();
        }
        Optional<String> decoded = Punycode.decode(label.substring(ACE_PREFIX.length()));
        if (decoded.isEmpty() || decoded.get().isEmpty() || isAllAscii(decoded.get()) || !isValidLabel(decoded.get())) {
            return Optional.empty();
        }
        return decoded;
    }

    private static boolean isValidLabel(String label) {
        if (!Normalizer.isNormalized(label, Normalizer.Form.NFC)
                || label.startsWith(ACE_PREFIX)
                || label.indexOf(FULL_STOP) >= 0) {
            return false;
        }
        int[] codePoints = label.codePoints().toArray();
        if (codePoints.length > 0 && isMark(codePoints[0])) {
            return false;
        }
        for (int at = 0; at < codePoints.length; at++) {
            if (Tables.INSTANCE.mappingFor(codePoints[at]).status() != VALID || !satisfiesContextJ(codePoints, at)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean satisfiesContextJ(int[] label, int at) {
        int codePoint = label[at];
        if (codePoint != ZERO_WIDTH_NON_JOINER && codePoint != ZERO_WIDTH_JOINER) {
            return true;
        }
        if (at > 0 && Tables.INSTANCE.isVirama(label[at - 1])) {
            return true;
        }
        return codePoint == ZERO_WIDTH_NON_JOINER && isJoinedEitherSide(label, at);
    }

    private static boolean isJoinedEitherSide(int[] label, int at) {
        int before = at - 1;
        while (before >= 0 && Tables.INSTANCE.findJoiningType(label[before]) == JOINING_T) {
            before--;
        }
        int after = at + 1;
        while (after < label.length && Tables.INSTANCE.findJoiningType(label[after]) == JOINING_T) {
            after++;
        }
        if (before < 0 || after >= label.length) {
            return false;
        }
        int left = Tables.INSTANCE.findJoiningType(label[before]);
        int right = Tables.INSTANCE.findJoiningType(label[after]);
        return (left == JOINING_L || left == JOINING_D) && (right == JOINING_R || right == JOINING_D);
    }

    private static boolean isBidiDomain(List<String> labels) {
        return labels.stream().flatMapToInt(String::codePoints).map(Tables.INSTANCE::findBidiClass)
                .anyMatch(bidi -> bidi == BIDI_R || bidi == BIDI_AL || bidi == BIDI_AN);
    }

    private static boolean satisfiesBidiRule(String label) {
        int[] classes = label.codePoints().map(Tables.INSTANCE::findBidiClass).toArray();
        if (classes.length == 0) {
            return true;
        }
        int last = classes.length - 1;
        while (last > 0 && classes[last] == BIDI_NSM) {
            last--;
        }
        if (classes[0] == BIDI_R || classes[0] == BIDI_AL) {
            return isRightToLeftLabelValid(classes, classes[last]);
        }
        if (classes[0] == BIDI_L) {
            return isLeftToRightLabelValid(classes, classes[last]);
        }
        return false;
    }

    private static boolean isRightToLeftLabelValid(int[] classes, int lastStrong) {
        boolean hasEuropeanNumber = false;
        boolean hasArabicNumber = false;
        for (int bidi : classes) {
            if (!RTL_LABEL_ALLOWS.contains(bidi)) {
                return false;
            }
            hasEuropeanNumber |= bidi == BIDI_EN;
            hasArabicNumber |= bidi == BIDI_AN;
        }
        boolean endsRight = lastStrong == BIDI_R || lastStrong == BIDI_AL || lastStrong == BIDI_EN || lastStrong == BIDI_AN;
        return endsRight && !(hasEuropeanNumber && hasArabicNumber);
    }

    private static boolean isLeftToRightLabelValid(int[] classes, int lastStrong) {
        for (int bidi : classes) {
            if (!LTR_LABEL_ALLOWS.contains(bidi)) {
                return false;
            }
        }
        return lastStrong == BIDI_L || lastStrong == BIDI_EN;
    }

    private static String encodeLabelToAscii(String label) {
        return isAllAscii(label) ? label : ACE_PREFIX + Punycode.encode(label);
    }

    private static boolean isAllAscii(String text) {
        return text.codePoints().allMatch(codePoint -> codePoint <= LAST_ASCII);
    }

    private static final class Punycode {

        private static final int BASE = 36;
        private static final int TMIN = 1;
        private static final int TMAX = 26;
        private static final int SKEW = 38;
        private static final int DAMP = 700;
        private static final int INITIAL_BIAS = 72;
        private static final int INITIAL_N = 128;
        private static final char DELIMITER = '-';
        private static final int DIGITS_BEFORE_LETTERS = 26;
        private static final int LAST_CODE_POINT = 0x10FFFF;

        static Optional<String> decode(String input) {
            int delimiter = input.lastIndexOf(DELIMITER);
            List<Integer> output = new ArrayList<>();
            for (int at = 0; at < Math.max(delimiter, 0); at++) {
                char basic = input.charAt(at);
                if (basic > LAST_ASCII) {
                    return Optional.empty();
                }
                output.add((int) basic);
            }
            int in = delimiter > 0 ? delimiter + 1 : 0;
            long n = INITIAL_N;
            long i = 0;
            int bias = INITIAL_BIAS;
            while (in < input.length()) {
                long oldI = i;
                long weight = 1;
                for (int k = BASE; ; k += BASE) {
                    if (in >= input.length()) {
                        return Optional.empty();
                    }
                    int digit = readDigit(input.charAt(in++));
                    if (digit < 0) {
                        return Optional.empty();
                    }
                    i += digit * weight;
                    if (i > Integer.MAX_VALUE) {
                        return Optional.empty();
                    }
                    int threshold = findThreshold(k, bias);
                    if (digit < threshold) {
                        break;
                    }
                    weight *= BASE - threshold;
                    if (weight > Integer.MAX_VALUE) {
                        return Optional.empty();
                    }
                }
                int size = output.size() + 1;
                bias = adaptBias(i - oldI, size, oldI == 0);
                n += i / size;
                i %= size;
                if (n > LAST_CODE_POINT || (n >= Character.MIN_SURROGATE && n <= Character.MAX_SURROGATE)) {
                    return Optional.empty();
                }
                output.add((int) i, (int) n);
                i++;
            }
            StringBuilder decoded = new StringBuilder();
            output.forEach(decoded::appendCodePoint);
            return Optional.of(decoded.toString());
        }

        static String encode(String label) {
            int[] input = label.codePoints().toArray();
            StringBuilder output = new StringBuilder();
            for (int codePoint : input) {
                if (codePoint <= LAST_ASCII) {
                    output.append((char) codePoint);
                }
            }
            int basicCount = output.length();
            int handled = basicCount;
            if (basicCount > 0) {
                output.append(DELIMITER);
            }
            long n = INITIAL_N;
            long delta = 0;
            int bias = INITIAL_BIAS;
            while (handled < input.length) {
                long next = Long.MAX_VALUE;
                for (int codePoint : input) {
                    if (codePoint >= n && codePoint < next) {
                        next = codePoint;
                    }
                }
                delta += (next - n) * (handled + 1);
                n = next;
                for (int codePoint : input) {
                    if (codePoint < n) {
                        delta++;
                    }
                    if (codePoint == n) {
                        long q = delta;
                        for (int k = BASE; ; k += BASE) {
                            int threshold = findThreshold(k, bias);
                            if (q < threshold) {
                                break;
                            }
                            output.append(writeDigit((int) (threshold + (q - threshold) % (BASE - threshold))));
                            q = (q - threshold) / (BASE - threshold);
                        }
                        output.append(writeDigit((int) q));
                        bias = adaptBias(delta, handled + 1, handled == basicCount);
                        delta = 0;
                        handled++;
                    }
                }
                delta++;
                n++;
            }
            return output.toString();
        }

        private static int findThreshold(int k, int bias) {
            if (k <= bias) {
                return TMIN;
            }
            return Math.min(k - bias, TMAX);
        }

        private static int adaptBias(long delta, int size, boolean isFirst) {
            long scaled = isFirst ? delta / DAMP : delta / 2;
            scaled += scaled / size;
            int k = 0;
            while (scaled > ((BASE - TMIN) * TMAX) / 2) {
                scaled /= BASE - TMIN;
                k += BASE;
            }
            return (int) (k + (BASE - TMIN + 1) * scaled / (scaled + SKEW));
        }

        private static int readDigit(char written) {
            if (written >= '0' && written <= '9') {
                return written - '0' + DIGITS_BEFORE_LETTERS;
            }
            if (written >= 'a' && written <= 'z') {
                return written - 'a';
            }
            if (written >= 'A' && written <= 'Z') {
                return written - 'A';
            }
            return -1;
        }

        private static char writeDigit(int digit) {
            return (char) (digit < DIGITS_BEFORE_LETTERS ? 'a' + digit : '0' + digit - DIGITS_BEFORE_LETTERS);
        }
    }

    private static final class Tables {

        private static final Tables INSTANCE = new Tables();
        private static final int NO_MAPPING = 0xFFFF;

        record Mapping(int status, String replacement) {
        }

        private final int[] mappingStarts;
        private final Mapping[] mappings;
        private final int[] bidiStarts;
        private final byte[] bidiClasses;
        private final int[] joiningStarts;
        private final byte[] joiningTypes;
        private final Set<Integer> viramas;

        private Tables() {
            ByteBuffer mapping = inflate(Uts46Tables.MAPPING);
            int entries = mapping.getInt();
            int[] starts = new int[entries];
            int[] statuses = new int[entries];
            int[] replacementIndexes = new int[entries];
            for (int at = 0; at < entries; at++) {
                starts[at] = readThreeBytes(mapping);
                statuses[at] = mapping.get() & 0xFF;
                replacementIndexes[at] = mapping.getShort() & 0xFFFF;
            }
            String[] pool = new String[mapping.getInt()];
            for (int at = 0; at < pool.length; at++) {
                int length = mapping.get() & 0xFF;
                StringBuilder replacement = new StringBuilder();
                for (int each = 0; each < length; each++) {
                    replacement.appendCodePoint(readThreeBytes(mapping));
                }
                pool[at] = replacement.toString();
            }
            mappingStarts = starts;
            mappings = new Mapping[entries];
            for (int at = 0; at < entries; at++) {
                mappings[at] = new Mapping(statuses[at],
                        replacementIndexes[at] == NO_MAPPING ? "" : pool[replacementIndexes[at]]);
            }
            ByteBuffer bidi = inflate(Uts46Tables.BIDI_CLASSES);
            bidiStarts = new int[bidi.getInt()];
            bidiClasses = new byte[bidiStarts.length];
            readRuns(bidi, bidiStarts, bidiClasses);
            ByteBuffer joining = inflate(Uts46Tables.JOINING_TYPES);
            joiningStarts = new int[joining.getInt()];
            joiningTypes = new byte[joiningStarts.length];
            readRuns(joining, joiningStarts, joiningTypes);
            ByteBuffer virama = inflate(Uts46Tables.VIRAMAS);
            int count = virama.getInt();
            Integer[] found = new Integer[count];
            for (int at = 0; at < count; at++) {
                found[at] = readThreeBytes(virama);
            }
            viramas = Set.of(found);
        }

        Mapping mappingFor(int codePoint) {
            return mappings[findRunHolding(mappingStarts, codePoint)];
        }

        int findBidiClass(int codePoint) {
            return bidiClasses[findRunHolding(bidiStarts, codePoint)];
        }

        int findJoiningType(int codePoint) {
            return joiningTypes[findRunHolding(joiningStarts, codePoint)];
        }

        boolean isVirama(int codePoint) {
            return viramas.contains(codePoint);
        }

        private static int findRunHolding(int[] starts, int codePoint) {
            int found = java.util.Arrays.binarySearch(starts, codePoint);
            return found >= 0 ? found : -found - 2;
        }

        private static void readRuns(ByteBuffer packed, int[] starts, byte[] values) {
            for (int at = 0; at < starts.length; at++) {
                starts[at] = readThreeBytes(packed);
                values[at] = packed.get();
            }
        }

        private static int readThreeBytes(ByteBuffer packed) {
            return (packed.get() & 0xFF) | (packed.get() & 0xFF) << 8 | (packed.get() & 0xFF) << 16;
        }

        private static ByteBuffer inflate(String[] chunks) {
            byte[] compressed = Base64.getDecoder().decode(String.join("", chunks));
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] room = new byte[1 << 16];
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(compressed);
                while (!inflater.finished()) {
                    int produced = inflater.inflate(room);
                    if (produced == 0 && inflater.needsInput()) {
                        throw new IllegalStateException("the UTS #46 tables are cut short");
                    }
                    raw.write(room, 0, produced);
                }
            } catch (DataFormatException corrupt) {
                throw new IllegalStateException("the UTS #46 tables are corrupt", corrupt);
            } finally {
                inflater.end();
            }
            return ByteBuffer.wrap(raw.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        }
    }
}
