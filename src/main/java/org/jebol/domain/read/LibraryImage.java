package org.jebol.domain.read;

import org.jebol.domain.eval.compression.Octets;
import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class LibraryImage {

    private LibraryImage() {
    }

    private static final byte[] MAGIC = {'J', 'L', 'I', 'B'};

    private static final int FORMAT_VERSION = 1;

    private static final int BLOCK = 1;
    private static final int PAREN = 2;
    private static final int PATH = 3;
    private static final int SET_PATH = 4;
    private static final int GET_PATH = 5;
    private static final int LIT_PATH = 6;
    private static final int HASH = 7;
    private static final int WORD = 10;
    private static final int SET_WORD = 11;
    private static final int GET_WORD = 12;
    private static final int LIT_WORD = 13;
    private static final int REFINEMENT = 14;
    private static final int ISSUE = 15;
    private static final int STRING = 20;
    private static final int BINARY = 21;
    private static final int CHARACTER = 22;
    private static final int INTEGER = 23;
    private static final int DECIMAL = 24;
    private static final int PERCENT = 25;
    private static final int MONEY = 26;
    private static final int TIME = 27;
    private static final int DATE = 28;
    private static final int PAIR = 29;
    private static final int TUPLE = 30;
    private static final int LOGIC = 31;
    private static final int NONE = 32;
    private static final int UNSET = 33;
    private static final int FILE = 34;
    private static final int EMAIL = 35;
    private static final int URL = 36;
    private static final int TAG = 37;
    private static final int REF = 38;
    private static final int BITSET = 39;
    private static final int MAP = 40;
    private static final int DATATYPE = 41;

    public static byte[] asBytes(Map<String, AnyBlockValue> files) {
        Octets body = new Octets();
        body.write(MAGIC, 0, MAGIC.length);
        writeVarint(body, FORMAT_VERSION);
        writeVarint(body, files.size());
        for (Map.Entry<String, AnyBlockValue> file : files.entrySet()) {
            writeText(body, file.getKey());
            writeValue(body, file.getValue());
        }
        return deflated(body.toArray());
    }

    public static Map<String, AnyBlockValue> treesIn(byte[] image) {
        byte[] body = inflated(image);
        Cursor cursor = new Cursor(body);
        byte[] magic = cursor.bytesOf(MAGIC.length);
        for (int at = 0; at < MAGIC.length; at++) {
            if (magic[at] != MAGIC[at]) {
                throw new IllegalArgumentException("not a JEBOL library image");
            }
        }
        int version = (int) cursor.varint();
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "library image is version " + version + ", and this build reads " + FORMAT_VERSION);
        }
        int count = (int) cursor.varint();
        Map<String, AnyBlockValue> files = new LinkedHashMap<>(count);
        for (int at = 0; at < count; at++) {
            String name = cursor.text();
            files.put(name, (AnyBlockValue) cursor.value());
        }
        return files;
    }

    private static void writeValue(Octets out, Value value) {
        switch (value) {
            case BlockValue block -> writeBlock(out, BLOCK, block);
            case ParenValue paren -> writeBlock(out, PAREN, paren);
            case PathValue path -> writeBlock(out, PATH, path);
            case SetPathValue path -> writeBlock(out, SET_PATH, path);
            case GetPathValue path -> writeBlock(out, GET_PATH, path);
            case LitPathValue path -> writeBlock(out, LIT_PATH, path);
            case HashValue block -> writeBlock(out, HASH, block);
            case WordValue word -> writeWord(out, WORD, word);
            case SetWordValue word -> writeWord(out, SET_WORD, word);
            case GetWordValue word -> writeWord(out, GET_WORD, word);
            case LitWordValue word -> writeWord(out, LIT_WORD, word);
            case RefinementValue word -> writeWord(out, REFINEMENT, word);
            case IssueValue word -> writeWord(out, ISSUE, word);
            case AnyStringValue text -> {
                out.write(tagForTheStringKindOf(text));
                writeText(out, text.text());
            }
            case BinaryValue octets -> {
                out.write(BINARY);
                byte[] held = octets.octetsFromHere();
                writeVarint(out, held.length);
                out.write(held, 0, held.length);
            }
            case BitsetValue set -> {
                out.write(BITSET);
                out.write(set.isComplemented() ? 1 : 0);
                out.write(set.isProtected() ? 1 : 0);
                byte[] held = set.octets();
                writeVarint(out, held.length);
                out.write(held, 0, held.length);
            }
            case MapValue map -> {
                out.write(MAP);
                out.write(map.isProtected() ? 1 : 0);
                List<Value> flattened = map.flattened();
                writeVarint(out, flattened.size());
                for (Value member : flattened) {
                    writeValue(out, member);
                }
            }
            case Datatype datatype -> {
                out.write(DATATYPE);
                writeText(out, datatype.literalSpelling());
            }
            case CharacterValue letter -> {
                out.write(CHARACTER);
                writeVarint(out, letter.codepoint());
            }
            case IntegerValue whole -> {
                out.write(INTEGER);
                writeVarint(out, zigzag(whole.magnitude()));
            }
            case PercentValue fraction -> {
                out.write(PERCENT);
                writeDouble(out, fraction.quantity());
            }
            case DecimalValue fraction -> {
                out.write(DECIMAL);
                writeDouble(out, fraction.quantity());
            }
            case MoneyValue money -> writeMoney(out, money);
            case TimeValue moment -> {
                out.write(TIME);
                writeVarint(out, zigzag(moment.nanoseconds()));
            }
            case DateValue date -> writeDate(out, date);
            case PairValue pair -> {
                out.write(PAIR);
                writeDouble(out, pair.x());
                writeDouble(out, pair.y());
            }
            case TupleValue tuple -> {
                out.write(TUPLE);
                int[] segments = tuple.segments();
                writeVarint(out, segments.length);
                for (int segment : segments) {
                    writeVarint(out, segment);
                }
            }
            case LogicValue truth -> {
                out.write(LOGIC);
                out.write(truth.truth() ? 1 : 0);
            }
            case NoneValue ignored -> out.write(NONE);
            case UnsetValue ignored -> out.write(UNSET);
            default -> throw new IllegalStateException(
                    "the library uses a " + value.datatype().literalSpelling()
                            + ", which the image has no way to write");
        }
    }

    private static void writeBlock(Octets out, int tag, AnyBlockValue block) {
        out.write(tag);
        List<Value> items = block.remaining();
        writeVarint(out, items.size());
        writeLineBreaks(out, block.storage(), items.size());
        for (Value item : items) {
            writeValue(out, item);
        }
    }

    private static void writeLineBreaks(Octets out, BlockStorage storage, int size) {
        int flags = size + 1;
        writeVarint(out, flags);
        int packed = 0;
        int bits = 0;
        for (int at = 1; at <= flags; at++) {
            if (storage.breaksLineAt(at)) {
                packed |= 1 << bits;
            }
            if (++bits == 8) {
                out.write(packed);
                packed = 0;
                bits = 0;
            }
        }
        if (bits > 0) {
            out.write(packed);
        }
    }

    private static void writeWord(Octets out, int tag, AnyWordValue word) {
        out.write(tag);
        writeText(out, word.spelling());
    }

    private static void writeMoney(Octets out, MoneyValue money) {
        out.write(MONEY);
        writeText(out, money.amount().toString());
        writeVarint(out, money.currency().isPresent() ? 1 : 0);
        money.currency().ifPresent(currency -> writeText(out, currency));
        out.write(money.negative() ? 1 : 0);
    }

    private static void writeDate(Octets out, DateValue date) {
        out.write(DATE);
        writeVarint(out, zigzag(date.year()));
        writeVarint(out, date.month());
        writeVarint(out, date.day());
        writeVarint(out, date.timeOfDay().isPresent() ? 1 : 0);
        date.timeOfDay().ifPresent(moment -> writeVarint(out, zigzag(moment.nanoseconds())));
        writeVarint(out, date.zoneMinutes().isPresent() ? 1 : 0);
        date.zoneMinutes().ifPresent(minutes -> writeVarint(out, zigzag(minutes)));
    }

    private static final class Cursor {

        private final byte[] body;
        private int at;

        Cursor(byte[] body) {
            this.body = body;
        }

        long varint() {
            long answer = 0;
            int shift = 0;
            while (true) {
                int octet = body[at++] & 0xFF;
                answer |= (long) (octet & 0x7F) << shift;
                if ((octet & 0x80) == 0) {
                    return answer;
                }
                shift += 7;
            }
        }

        byte[] bytesOf(int count) {
            byte[] answer = new byte[count];
            System.arraycopy(body, at, answer, 0, count);
            at += count;
            return answer;
        }

        String text() {
            return new String(bytesOf((int) varint()), java.nio.charset.StandardCharsets.UTF_8);
        }

        double number() {
            long bits = 0;
            for (int shift = 0; shift < 64; shift += 8) {
                bits |= (long) (body[at++] & 0xFF) << shift;
            }
            return Double.longBitsToDouble(bits);
        }

        Value value() {
            int tag = body[at++] & 0xFF;
            return switch (tag) {
                case BLOCK, PAREN, PATH, SET_PATH, GET_PATH, LIT_PATH, HASH -> readBlock(tag);
                case WORD, SET_WORD, GET_WORD, LIT_WORD, REFINEMENT, ISSUE -> readWord(tag);
                case STRING, FILE, EMAIL, URL, TAG, REF ->
                        stringDatatypeFor(tag).holding(text());
                case BINARY -> new BinaryValue(new BinaryStorage(bytesOf((int) varint())), 1);
                case BITSET -> readBitset();
                case MAP -> readMap();
                case DATATYPE -> datatypeNamed(text());
                case CHARACTER -> CharacterValue.of((int) varint());
                case INTEGER -> IntegerValue.of(unzigzag(varint()));
                case DECIMAL -> DecimalValue.of(number());
                case PERCENT -> PercentValue.of(number());
                case MONEY -> readMoney();
                case TIME -> TimeValue.ofNanoseconds(unzigzag(varint()));
                case DATE -> readDate();
                case PAIR -> PairValue.of(number(), number());
                case TUPLE -> readTuple();
                case LOGIC -> LogicValue.of(varint() != 0);
                case NONE -> NoneValue.none();
                case UNSET -> UnsetValue.unset();
                default -> throw new IllegalArgumentException("unknown image tag " + tag);
            };
        }

        private Value readBitset() {
            boolean complemented = body[at++] != 0;
            boolean protectedFromChange = body[at++] != 0;
            BitsetValue set = BitsetValue.of(bytesOf((int) varint()));
            set = complemented ? set.complemented() : set;
            set.protectFromChange(protectedFromChange);
            return set;
        }

        private Value readMap() {
            boolean protectedFromChange = body[at++] != 0;
            int members = (int) varint();
            List<Value> flattened = new ArrayList<>(members);
            for (int member = 0; member < members; member++) {
                flattened.add(value());
            }
            MapValue map = MapValue.of(flattened);
            map.protectFromChange(protectedFromChange);
            return map;
        }

        private Value readBlock(int tag) {
            int size = (int) varint();
            List<Boolean> marks = readLineBreaks(size);
            List<Value> items = new ArrayList<>(size);
            for (int item = 0; item < size; item++) {
                items.add(value());
            }
            BlockStorage storage = new BlockStorage(items);
            storage.setLineBreaksFromHead(marks);
            return blockDatatypeFor(tag).holding(storage, 1);
        }

        private List<Boolean> readLineBreaks(int size) {
            int flags = (int) varint();
            List<Boolean> marks = new ArrayList<>(size);
            int packed = 0;
            int bits = 8;
            for (int at = 1; at <= flags; at++) {
                if (bits == 8) {
                    packed = body[this.at++] & 0xFF;
                    bits = 0;
                }
                boolean marked = ((packed >> bits) & 1) != 0;
                if (at <= size) {
                    marks.add(marked);
                }
                bits++;
            }
            return marks;
        }

        private Value readWord(int tag) {
            AnyWordValue.AnyWordDatatype type = switch (tag) {
                case WORD -> WordValue.TYPE;
                case SET_WORD -> SetWordValue.TYPE;
                case GET_WORD -> GetWordValue.TYPE;
                case LIT_WORD -> LitWordValue.TYPE;
                case REFINEMENT -> RefinementValue.TYPE;
                case ISSUE -> IssueValue.TYPE;
                default -> throw new IllegalArgumentException("unknown word tag " + tag);
            };
            return type.spelt(text());
        }

        private Value readMoney() {
            BigDecimal amount = new BigDecimal(text());
            Optional<String> currency = varint() != 0
                    ? Optional.of(text())
                    : Optional.empty();
            boolean negative = body[at++] != 0;
            return new MoneyValue(amount, currency, negative);
        }

        private Value readDate() {
            int year = (int) unzigzag(varint());
            int month = (int) varint();
            int day = (int) varint();
            Optional<TimeValue> timeOfDay = varint() != 0
                    ? Optional.of(TimeValue.ofNanoseconds(unzigzag(varint())))
                    : Optional.empty();
            Optional<Integer> zone = varint() != 0
                    ? Optional.of((int) unzigzag(varint()))
                    : Optional.empty();
            return new DateValue(year, month, day, timeOfDay, zone);
        }

        private Value readTuple() {
            int count = (int) varint();
            int[] segments = new int[count];
            for (int at = 0; at < count; at++) {
                segments[at] = (int) varint();
            }
            return TupleValue.of(segments);
        }
    }

    private static AnyBlockValue.AnyBlockDatatype blockDatatypeFor(int tag) {
        return switch (tag) {
            case BLOCK -> BlockValue.TYPE;
            case PAREN -> ParenValue.TYPE;
            case PATH -> PathValue.TYPE;
            case SET_PATH -> SetPathValue.TYPE;
            case GET_PATH -> GetPathValue.TYPE;
            case LIT_PATH -> LitPathValue.TYPE;
            case HASH -> HashValue.TYPE;
            default -> throw new IllegalArgumentException("unknown block tag " + tag);
        };
    }

    private static int tagForTheStringKindOf(AnyStringValue text) {
        Datatype kind = text.datatype();
        if (kind == FileValue.TYPE) {
            return FILE;
        }
        if (kind == EmailValue.TYPE) {
            return EMAIL;
        }
        if (kind == UrlValue.TYPE) {
            return URL;
        }
        if (kind == TagValue.TYPE) {
            return TAG;
        }
        if (kind == RefValue.TYPE) {
            return REF;
        }
        return STRING;
    }

    private static AnyStringValue.AnyStringDatatype stringDatatypeFor(int tag) {
        return switch (tag) {
            case STRING -> StringValue.TYPE;
            case FILE -> FileValue.TYPE;
            case EMAIL -> EmailValue.TYPE;
            case URL -> UrlValue.TYPE;
            case TAG -> TagValue.TYPE;
            case REF -> RefValue.TYPE;
            default -> throw new IllegalArgumentException("unknown string tag " + tag);
        };
    }

    private static Value datatypeNamed(String spelling) {
        Optional<Datatype> named = Catalogue.DATATYPES.named(spelling);
        if (named.isPresent()) {
            return named.get();
        }
        return Catalogue.DATATYPES.typesetNamed(spelling)
                .map(one -> (Value) one)
                .orElseThrow(() -> new IllegalArgumentException(
                        "no datatype is named " + spelling));
    }

    private static void writeText(Octets out, String text) {
        byte[] octets = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarint(out, octets.length);
        out.write(octets, 0, octets.length);
    }

    private static void writeVarint(Octets out, long value) {
        long remaining = value;
        while (true) {
            int octet = (int) (remaining & 0x7F);
            remaining >>>= 7;
            if (remaining == 0) {
                out.write(octet);
                return;
            }
            out.write(octet | 0x80);
        }
    }

    private static void writeDouble(Octets out, double number) {
        long bits = Double.doubleToLongBits(number);
        for (int shift = 0; shift < 64; shift += 8) {
            out.write((int) (bits >> shift) & 0xFF);
        }
    }

    private static long zigzag(long value) {
        return value >= 0 ? value << 1 : ~(value << 1);
    }

    private static long unzigzag(long zigzagged) {
        return (zigzagged >>> 1) ^ -(zigzagged & 1);
    }

    private static byte[] deflated(byte[] body) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(body);
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

    private static byte[] inflated(byte[] image) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(image);
            Octets into = new Octets();
            byte[] page = new byte[8192];
            while (!inflater.finished()) {
                int written = inflater.inflate(page);
                into.write(page, 0, written);
                if (written == 0 && !inflater.finished()
                        && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("the library image ends early");
                }
            }
            return into.toArray();
        } catch (DataFormatException notDeflate) {
            throw new IllegalArgumentException("the library image is not deflate data");
        } finally {
            inflater.end();
        }
    }
}
