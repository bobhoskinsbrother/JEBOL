package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class BitsetActions implements Actions {

    private final BitsetValue members;

    public BitsetActions(BitsetValue members) {
        this.members = members;
    }

    @Override
    public Value subject() {
        return members;
    }

    @Override
    public Value poke(Value position, Value written) {
        members.requireChangeable();
        holdAllOf(position, written.isTruthy());
        return members;
    }

    @Override
    public Value clear() {
        members.requireChangeable();
        members.clear();
        return members;
    }

    @Override
    public int measureLength() {
        return bitsReachedOver();
    }

    @Override
    public Value complement() {
        return members.complement();
    }

    @Override
    public Value append(Asked asked) {
        return takeTheBitsOf(asked);
    }

    @Override
    public Value insert(Asked asked) {
        return takeTheBitsOf(asked);
    }

    private Value takeTheBitsOf(Asked asked) {
        members.requireChangeable();
        addAllOf(asked.given());
        return members;
    }

    public boolean holds(Value asked, boolean anyWillDo, boolean eitherCaseWillDo) {
        if (asked instanceof CharacterValue(int codepoint)) {
            return eitherCaseWillDo
                    ? members.holdsEitherCaseOf(codepoint)
                    : members.holds(codepoint);
        }
        if (asked instanceof IntegerValue(long magnitude)) {
            return members.holds(findBitAsked(magnitude));
        }
        return holdsEachOf(collectCodePointsAskedAboutBy(asked), anyWillDo);
    }

    public boolean holds(Value asked) {
        return holds(asked, false, false);
    }

    public Value pickForPath(Value selector) {
        return LogicValue.of(holds(selector));
    }

    public void addAllOf(Value asked) {
        members.holdAll(readWhatIsMeantBy(asked), true);
    }

    public void holdAllOf(Value asked, boolean wanted) {
        members.holdAll(readWhatIsMeantBy(asked), wanted);
    }

    public Value remove(Set<String> refinements, Function<String, Value> theArgumentFor) {
        boolean byKey = refinements.contains("key");
        boolean byPart = refinements.contains("part");
        if (byKey && byPart) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "/key and /part each say what to remove, and only one can");
        }
        if (byKey) {
            members.clearAllDirectly(readWhatIsMeantBy(theArgumentFor.apply("key")));
            return members;
        }
        if (byPart) {
            Value range = theArgumentFor.apply("part");
            refuseWhatNamesNoRange(range);
            members.clearAllDirectly(readWhatIsMeantBy(range));
            return members;
        }
        throw Raised.of(EvaluationFailure.MISSING_ARG,
                "/key or /part must say what to remove from the set");
    }

    private static void refuseWhatNamesNoRange(Value range) {
        boolean allowed = range instanceof AnyBlockValue
                || range instanceof BinaryValue
                || range instanceof CharacterValue
                || range instanceof StringValue;
        if (!allowed) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    Molder.mold(range) + " names no range of members");
        }
    }

    public int bitsReachedOver() {
        return members.octets().length * 8;
    }

    public boolean isEmptySet() {
        byte held = (byte) (members.isComplemented() ? 0xFF : 0);
        for (byte octet : members.octets()) {
            if (octet != held) {
                return false;
            }
        }
        return true;
    }

    public byte[] asOctets() {
        byte[] held = members.octets();
        if (!members.isComplemented()) {
            return held;
        }
        byte[] turned = new byte[held.length];
        for (int at = 0; at < held.length; at++) {
            turned[at] = (byte) ~held[at];
        }
        return turned;
    }

    private boolean holdsEachOf(int[] wanted, boolean anyWillDo) {
        for (int point : wanted) {
            if (members.holds(point) == anyWillDo) {
                return anyWillDo;
            }
        }
        return !anyWillDo;
    }

    public static Value makeFrom(Value source) {
        return switch (source) {
            case AnyStringValue text ->
                    BitsetValue.ofCharacters(text.text().codePoints().toArray());
            case CharacterValue character -> BitsetValue.ofCharacters(character.codepoint());
            case IntegerValue room -> BitsetValue.of(
                    new byte[(findRoomAsked(room.magnitude()) + 7) / 8]);
            case BinaryValue octets -> BitsetValue.of(octets.octetsFromHere());
            case BitsetValue existing -> existing.duplicate();
            case AnyBlockValue members -> fromBlock(members);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG, source);
        };
    }

    public static BitsetValue readWhatIsMeantBy(Value source) {
        if (source instanceof IntegerValue(long magnitude)) {
            return BitsetValue.of(withBitSet(new byte[0], findBitAsked(magnitude)));
        }
        if (!(source instanceof CharacterValue || source instanceof AnyStringValue
                || source instanceof BinaryValue || source instanceof AnyBlockValue)) {
            throw Raised.of(EvaluationFailure.INVALID_TYPE, Molder.mold(source));
        }
        return (BitsetValue) makeFrom(source);
    }

    private static Value fromBlock(AnyBlockValue written) {
        List<Value> items = written.remaining();
        boolean complemented = !items.isEmpty()
                && items.getFirst() instanceof AnyWordValue word
                && word.canonical().equals("not");
        AnyBlockValue rest = complemented ? written.atIndex(written.index() + 1) : written;
        BitsetValue set = BitsetValue.of(collectOctetsNamedBy(rest.remaining(), written));
        return complemented ? set.complement() : set;
    }

    private static byte[] collectOctetsNamedBy(List<Value> specs, AnyBlockValue whole) {
        byte[] octets = new byte[0];
        for (int at = 0; at < specs.size(); at++) {
            Value spec = specs.get(at);
            if (spec instanceof AnyWordValue word && word.canonical().equals("bits")) {
                if (at + 1 >= specs.size()
                        || !(specs.get(at + 1) instanceof BinaryValue held)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(whole));
                }
                octets = withOctetsSet(octets, held.octetsFromHere());
                at++;
            } else if (spec instanceof BinaryValue held) {
                octets = withOctetsSet(octets, held.octetsFromHere());
            } else if (spec instanceof AnyStringValue text) {
                for (int point : text.text().codePoints().toArray()) {
                    octets = withBitSet(octets, point);
                }
            } else if (spec instanceof CharacterValue || spec instanceof IntegerValue) {
                int from = findBitAsked(readTheCodePointOf(spec));
                int to = from;
                if (at + 1 < specs.size()
                        && specs.get(at + 1) instanceof AnyWordValue dash
                        && dash.spelling().equals("-")) {
                    to = findBitAsked(readTheCodePointOf(runEndAt(spec, specs, at + 2)));
                    at += 2;
                }
                if (to < from) {
                    throw Raised.of(EvaluationFailure.PAST_END, String.valueOf(to));
                }
                for (int point = from; point <= to; point++) {
                    octets = withBitSet(octets, point);
                }
            } else {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(whole));
            }
        }
        return octets;
    }

    private static Value runEndAt(Value opening, List<Value> specs, int at) {
        Value closing = at < specs.size() ? specs.get(at) : UnsetValue.unset();
        if (opening.datatype() != closing.datatype()) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(closing));
        }
        return closing;
    }

    private static int[] collectCodePointsAskedAboutBy(Value asked) {
        if (asked instanceof AnyStringValue text) {
            return text.text().codePoints().toArray();
        }
        if (asked instanceof BinaryValue octets) {
            byte[] bytes = octets.octetsFromHere();
            int[] points = new int[bytes.length];
            for (int at = 0; at < bytes.length; at++) {
                points[at] = bytes[at] & 0xFF;
            }
            return points;
        }
        if (asked instanceof AnyBlockValue specs) {
            int[] points = collectCodePointsIn(specs);
            for (int point : points) {
                findBitAsked(point);
            }
            return points;
        }
        throw Raised.of(EvaluationFailure.INVALID_TYPE, Molder.mold(asked));
    }

    private static int[] collectCodePointsIn(AnyBlockValue written) {
        List<Value> items = written.remaining();
        List<Integer> points = new ArrayList<>();
        for (int at = 0; at < items.size(); at++) {
            boolean isRange = at + 2 < items.size()
                    && items.get(at + 1) instanceof AnyWordValue dash
                    && dash.spelling().equals("-");
            if (isRange) {
                int from = readTheCodePointOf(items.get(at));
                int to = readTheCodePointOf(items.get(at + 2));
                for (int point = from; point <= to; point++) {
                    points.add(point);
                }
                at += 2;
                continue;
            }
            if (items.get(at) instanceof CharacterValue
                    || items.get(at) instanceof IntegerValue) {
                points.add(readTheCodePointOf(items.get(at)));
            } else if (items.get(at) instanceof AnyStringValue text) {
                text.text().codePoints().forEach(points::add);
            }
        }
        return points.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int readTheCodePointOf(Value value) {
        return value instanceof CharacterValue(int codepoint)
                ? codepoint
                : (int) Comparison.asDouble(value);
    }

    private static final int MOST_BITS_ALLOWED = 0x0FFFFFFF;

    static int findBitAsked(long codepoint) {
        if (codepoint < 0 || codepoint > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(codepoint));
        }
        return (int) codepoint;
    }

    private static int findRoomAsked(long bits) {
        int asked = findBitAsked(bits);
        if (asked > MOST_BITS_ALLOWED) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, IntegerValue.of(bits));
        }
        return asked;
    }

    private static byte[] withBitSet(byte[] octets, int point) {
        int reaching = point / 8 + 1;
        byte[] grown = octets.length >= reaching
                ? octets
                : Arrays.copyOf(octets, reaching);
        grown[point / 8] |= (byte) (0x80 >> (point % 8));
        return grown;
    }

    private static byte[] withOctetsSet(byte[] octets, byte[] more) {
        byte[] grown = octets.length >= more.length
                ? octets
                : Arrays.copyOf(octets, more.length);
        for (int at = 0; at < more.length; at++) {
            grown[at] |= more[at];
        }
        return grown;
    }

    public static BitsetValue collectCharactersIn(String characters) {
        return BitsetValue.ofCharacters(characters.chars().toArray());
    }

    public static BitsetValue characterRange(int from, int to) {
        int[] codes = new int[to - from + 1];
        for (int at = 0; at < codes.length; at++) {
            codes[at] = from + at;
        }
        return BitsetValue.ofCharacters(codes);
    }

    public static BitsetValue lettersOfBothCases() {
        return join(characterRange('a', 'z'), characterRange('A', 'Z'));
    }

    public static BitsetValue listQuotedPrintableOctets() {
        StringBuilder allowed = new StringBuilder();
        for (int character = 0; character <= LAST_ASCII_CHARACTER; character++) {
            if (character != '=') {
                allowed.append((char) character);
            }
        }
        return collectCharactersIn(allowed.toString());
    }

    private static final int LAST_ASCII_CHARACTER = 127;

    public static BitsetValue join(BitsetValue first, BitsetValue second) {
        byte[] left = first.octets();
        byte[] right = second.octets();
        byte[] both = new byte[Math.max(left.length, right.length)];
        for (int at = 0; at < both.length; at++) {
            both[at] = (byte) ((at < left.length ? left[at] : 0)
                    | (at < right.length ? right[at] : 0));
        }
        return BitsetValue.of(both);
    }
}
