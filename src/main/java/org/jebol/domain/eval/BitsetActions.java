package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

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
    public Value cleared() {
        Natives.requireChangeable(members);
        members.clear();
        return members;
    }

    @Override
    public int length() {
        return bitsReachedOver();
    }

    @Override
    public Value complemented() {
        return members.complemented();
    }

    @Override
    public Value append(Asked asked) {
        return givenTheBitsOf(asked);
    }

    @Override
    public Value insert(Asked asked) {
        return givenTheBitsOf(asked);
    }

    private Value givenTheBitsOf(Asked asked) {
        Natives.requireChangeable(members);
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
            return members.holds(bitAsked(magnitude));
        }
        return holdsEachOf(codePointsAskedAboutBy(asked), anyWillDo);
    }

    public boolean holds(Value asked) {
        return holds(asked, false, false);
    }

    public Value heldForAPath(Value selector) {
        return LogicValue.of(holds(selector));
    }

    public void addAllOf(Value asked) {
        members.holdAll(meantBy(asked), true);
    }

    public void holdAllOf(Value asked, boolean wanted) {
        members.holdAll(meantBy(asked), wanted);
    }

    public Value removed(Set<String> refinements, Function<String, Value> theArgumentFor) {
        boolean byKey = refinements.contains("key");
        boolean byPart = refinements.contains("part");
        if (byKey && byPart) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "/key and /part each say what to remove, and only one can");
        }
        if (byKey) {
            members.clearAllDirectly(meantBy(theArgumentFor.apply("key")));
            return members;
        }
        if (byPart) {
            Value range = theArgumentFor.apply("part");
            refuseWhatNamesNoRange(range);
            members.clearAllDirectly(meantBy(range));
            return members;
        }
        throw Raised.of(EvaluationFailure.MISSING_ARG,
                "/key or /part must say what to remove from the set");
    }

    private static void refuseWhatNamesNoRange(Value range) {
        boolean allowed = range instanceof BlockValue
                || range instanceof BinaryValue
                || range instanceof CharacterValue
                || (range instanceof StringValue && range.datatype() == Datatype.STRING);
        if (!allowed) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    Molder.mold(range) + " names no range of members");
        }
    }

    public int bitsReachedOver() {
        return members.octets().length * 8;
    }

    public boolean isTheEmptySet() {
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

    public static Value madeFrom(Value source) {
        return switch (source) {
            case StringValue text ->
                    BitsetValue.ofCharacters(text.text().codePoints().toArray());
            case CharacterValue character -> BitsetValue.ofCharacters(character.codepoint());
            case IntegerValue room -> BitsetValue.of(
                    new byte[(bitAsked(room.magnitude()) + 7) / 8]);
            case BinaryValue octets -> BitsetValue.of(octets.octetsFromHere());
            case BitsetValue existing -> existing.duplicate();
            case BlockValue members -> fromBlock(members);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    Molder.mold(source) + " names no characters a set could hold");
        };
    }

    public static BitsetValue meantBy(Value source) {
        if (source instanceof IntegerValue(long magnitude)) {
            return BitsetValue.of(withBitSet(new byte[0], bitAsked(magnitude)));
        }
        if (!(source instanceof CharacterValue || source instanceof StringValue
                || source instanceof BinaryValue || source instanceof BlockValue)) {
            throw Raised.of(EvaluationFailure.INVALID_TYPE, Molder.mold(source));
        }
        return (BitsetValue) madeFrom(source);
    }

    private static Value fromBlock(BlockValue written) {
        List<Value> items = written.remaining();
        boolean complemented = !items.isEmpty()
                && items.getFirst() instanceof WordValue word
                && word.canonical().equals("not");
        BlockValue rest = complemented ? written.atIndex(written.index() + 1) : written;
        BitsetValue set = BitsetValue.of(octetsNamedBy(rest.remaining(), written));
        return complemented ? set.complemented() : set;
    }

    private static byte[] octetsNamedBy(List<Value> specs, BlockValue whole) {
        byte[] octets = new byte[0];
        for (int at = 0; at < specs.size(); at++) {
            Value spec = specs.get(at);
            if (spec instanceof WordValue word && word.canonical().equals("bits")) {
                if (at + 1 >= specs.size()
                        || !(specs.get(at + 1) instanceof BinaryValue held)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(whole));
                }
                octets = withOctetsSet(octets, held.octetsFromHere());
                at++;
            } else if (spec instanceof BinaryValue held) {
                octets = withOctetsSet(octets, held.octetsFromHere());
            } else if (spec instanceof StringValue text) {
                for (int point : text.text().codePoints().toArray()) {
                    octets = withBitSet(octets, point);
                }
            } else if (spec instanceof CharacterValue || spec instanceof IntegerValue) {
                int from = bitAsked(codePointOf(spec));
                int to = from;
                if (at + 1 < specs.size()
                        && specs.get(at + 1) instanceof WordValue dash
                        && dash.spelling().equals("-")) {
                    to = bitAsked(codePointOf(farEndOfTheRun(spec, specs, at + 2)));
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

    private static Value farEndOfTheRun(Value opening, List<Value> specs, int at) {
        Value closing = at < specs.size() ? specs.get(at) : UnsetValue.unset();
        if (opening.datatype() != closing.datatype()) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(closing));
        }
        return closing;
    }

    private static int[] codePointsAskedAboutBy(Value asked) {
        if (asked instanceof StringValue text) {
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
        if (asked instanceof BlockValue specs) {
            int[] points = codePointsIn(specs);
            for (int point : points) {
                bitAsked(point);
            }
            return points;
        }
        throw Raised.of(EvaluationFailure.INVALID_TYPE, Molder.mold(asked));
    }

    private static int[] codePointsIn(BlockValue written) {
        List<Value> items = written.remaining();
        List<Integer> points = new ArrayList<>();
        for (int at = 0; at < items.size(); at++) {
            boolean isRange = at + 2 < items.size()
                    && items.get(at + 1) instanceof WordValue dash
                    && dash.spelling().equals("-");
            if (isRange) {
                int from = codePointOf(items.get(at));
                int to = codePointOf(items.get(at + 2));
                for (int point = from; point <= to; point++) {
                    points.add(point);
                }
                at += 2;
                continue;
            }
            if (items.get(at) instanceof CharacterValue
                    || items.get(at) instanceof IntegerValue) {
                points.add(codePointOf(items.get(at)));
            } else if (items.get(at) instanceof StringValue text) {
                text.text().codePoints().forEach(points::add);
            }
        }
        return points.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int codePointOf(Value value) {
        return value instanceof CharacterValue(int codepoint)
                ? codepoint
                : (int) Comparison.asDouble(value);
    }

    static int bitAsked(long codepoint) {
        if (codepoint < 0 || codepoint > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    codepoint + " names no bit a set could hold");
        }
        return (int) codepoint;
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

    public static BitsetValue charactersIn(String characters) {
        return BitsetValue.ofCharacters(characters.chars().toArray());
    }

    public static BitsetValue rangeOfCharacters(int from, int to) {
        int[] codes = new int[to - from + 1];
        for (int at = 0; at < codes.length; at++) {
            codes[at] = from + at;
        }
        return BitsetValue.ofCharacters(codes);
    }

    public static BitsetValue lettersOfBothCases() {
        return together(rangeOfCharacters('a', 'z'), rangeOfCharacters('A', 'Z'));
    }

    public static BitsetValue quotedPrintableOctets() {
        StringBuilder allowed = new StringBuilder();
        for (int character = 0; character <= LAST_ASCII_CHARACTER; character++) {
            if (character != '=') {
                allowed.append((char) character);
            }
        }
        return charactersIn(allowed.toString());
    }

    private static final int LAST_ASCII_CHARACTER = 127;

    public static BitsetValue together(BitsetValue first, BitsetValue second) {
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
