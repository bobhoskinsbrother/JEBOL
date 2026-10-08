package org.jebol.domain.eval;

import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.UnicodeCases;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

public final class ValueHash {

    private static final int MURMUR_C1 = 0xcc9e2d51;

    private static final int MURMUR_C2 = 0x1b873593;

    private static final int CHARACTER_MULTIPLIER = 506832829;

    private static final int CHARACTER_SHIFT = 18;

    private static final long NO_TIME = Long.MIN_VALUE;

    private static final long NANOSECONDS_IN_A_SECOND = 1_000_000_000L;

    private static final long NANOSECONDS_IN_A_MINUTE = 60 * NANOSECONDS_IN_A_SECOND;

    private static final int ZONE_MINUTES_PER_STEP = 15;

    private static final int LOGIC_BASE = 2;

    private static final int TUPLE_BYTES_IN_A_WORD = 4;

    private static final int CRC_INITIAL_SHIFT = 16;

    private static final int CRC_MASK = 0xFFFFFF;

    private static final int CRC_POLYNOMIAL = 0x864CFB;

    private static final int CRC_HIGH_BIT = 0x800000;

    private final SymbolTable symbols;

    public ValueHash(SymbolTable symbols) {
        this.symbols = symbols;
    }

    public long of(Value value) {
        return Integer.toUnsignedLong(hashed(value));
    }

    private int hashed(Value value) {
        return switch (value) {
            case AnyWordValue word -> symbols.canonOf(word.spelling());
            case AnyStringValue text -> ofText(text) ^ typeNumberOf(text);
            case BinaryValue bytes -> murmur(bytes.octetsFromHere());
            case AnyBlockValue block -> ofBlock(block);
            case LogicValue logic -> LOGIC_BASE + (logic.truth() ? 1 : 0);
            case IntegerValue whole -> ofSixtyFourBits(whole.magnitude());
            case DecimalValue decimal ->
                    ofSixtyFourBits(Double.doubleToRawLongBits(decimal.quantity()));
            case CharacterValue character -> ofCharacter(character.codepoint());
            case MoneyValue money -> ofMoney(money);
            case TimeValue time -> ofTime(time.nanoseconds());
            case DateValue date -> ofDate(date);
            case TupleValue tuple -> ofTuple(tuple.segments());
            case PairValue pair -> Float.floatToRawIntBits((float) pair.x())
                    ^ Float.floatToRawIntBits((float) pair.y());
            case DatatypeValue datatype -> crcOfAWord(datatype.represents().literalSpelling());
            default -> finalMix(typeNumberOf(value));
        };
    }

    private int typeNumberOf(Value value) {
        return value.datatype().ordinal();
    }

    private int ofText(AnyStringValue text) {
        byte[] heldAsUtf8 = text.text().getBytes(StandardCharsets.UTF_8);
        int hash = 0;
        for (byte each : heldAsUtf8) {
            hash = blockMix(hash, UnicodeCases.TABLES.lower(each & 0xFF));
        }
        return finalMix(hash ^ heldAsUtf8.length);
    }

    private int ofBlock(AnyBlockValue block) {
        int hash = blockMix(0, typeNumberOf(block));
        int length = 0;
        for (Value item : block.remaining()) {
            hash = blockMix(hash, hashed(item));
            length++;
        }
        return finalMix(hash ^ length);
    }

    private int ofSixtyFourBits(long bits) {
        return finalMix((int) (bits >>> Integer.SIZE) ^ (int) bits);
    }

    private int ofCharacter(int codepoint) {
        long lowered = Integer.toUnsignedLong(UnicodeCases.TABLES.lower(codepoint));
        return (int) ((lowered * CHARACTER_MULTIPLIER) >> CHARACTER_SHIFT);
    }

    private int ofMoney(MoneyValue money) {
        BigInteger significand = money.amount().unscaledValue().abs();
        int lowest = significand.intValue();
        int middle = significand.shiftRight(Integer.SIZE).intValue();
        int highest = significand.shiftRight(2 * Integer.SIZE).intValue() & 0x7FFFFF;
        int signAndExponent = (money.negative() ? 1 << 23 : 0) | ((-money.amount().scale() & 0xFF) << 24);
        return lowest ^ middle ^ (highest | signAndExponent);
    }

    private int ofTime(long nanoseconds) {
        return (int) (nanoseconds ^ (nanoseconds / NANOSECONDS_IN_A_SECOND));
    }

    private int ofDate(DateValue date) {
        int zoneMinutes = date.zoneMinutes().orElse(0);
        long time = date.timeOfDay()
                .map(local -> local.nanoseconds() - zoneMinutes * NANOSECONDS_IN_A_MINUTE)
                .orElse(NO_TIME);
        int zoneSteps = zoneMinutes / ZONE_MINUTES_PER_STEP;
        int bits = (zoneSteps & 0x7F)
                | ((date.day() & 0x1F) << 7)
                | ((date.month() & 0xF) << 12)
                | ((date.year() & 0xFFFF) << 16);
        return ofTime(time) ^ bits;
    }

    private int ofTuple(int[] segments) {
        if (segments.length > TUPLE_BYTES_IN_A_WORD) {
            byte[] bytes = new byte[segments.length];
            for (int at = 0; at < segments.length; at++) {
                bytes[at] = (byte) segments[at];
            }
            return murmur(bytes);
        }
        int word = 0;
        for (int at = 0; at < segments.length; at++) {
            word |= (segments[at] & 0xFF) << (Byte.SIZE * at);
        }
        return finalMix(word);
    }

    private int murmur(byte[] bytes) {
        int hash = 0;
        int whole = bytes.length / TUPLE_BYTES_IN_A_WORD * TUPLE_BYTES_IN_A_WORD;
        for (int at = 0; at < whole; at += TUPLE_BYTES_IN_A_WORD) {
            hash = blockMix(hash, (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8
                    | (bytes[at + 2] & 0xFF) << 16 | (bytes[at + 3] & 0xFF) << 24);
        }
        int tail = 0;
        for (int at = bytes.length - 1; at >= whole; at--) {
            tail = (tail << Byte.SIZE) | (bytes[at] & 0xFF);
        }
        if (bytes.length > whole) {
            tail *= MURMUR_C1;
            tail = Integer.rotateLeft(tail, 16);
            tail *= MURMUR_C2;
            hash ^= tail;
        }
        return finalMix(hash ^ bytes.length);
    }

    private int blockMix(int hash, int key) {
        int mixed = Integer.rotateLeft(key * MURMUR_C1, 15) * MURMUR_C2;
        return Integer.rotateLeft(hash ^ mixed, 13) * 5 + 0xe6546b64;
    }

    private int finalMix(int hash) {
        int mixed = hash ^ (hash >>> 16);
        mixed *= 0x85ebca6b;
        mixed ^= mixed >>> 13;
        mixed *= 0xc2b2ae35;
        return mixed ^ (mixed >>> 16);
    }

    private int crcOfAWord(String spelling) {
        byte[] bytes = spelling.getBytes(StandardCharsets.UTF_8);
        int hash = bytes.length + (UnicodeCases.TABLES.lower(bytes[0] & 0xFF) & 0xFF);
        for (int character : spelling.codePoints().toArray()) {
            int lowered = UnicodeCases.TABLES.lower(character) & 0xFF;
            int entry = ((hash >>> CRC_INITIAL_SHIFT) ^ lowered) & 0xFF;
            hash = ((hash << Byte.SIZE) & CRC_MASK) ^ crcTableEntry(entry);
        }
        return hash;
    }

    private int crcTableEntry(int byteValue) {
        int accumulated = 0;
        int data = byteValue << CRC_INITIAL_SHIFT;
        for (int bit = 0; bit < Byte.SIZE; bit++) {
            accumulated = ((data ^ accumulated) & CRC_HIGH_BIT) != 0
                    ? (accumulated << 1) ^ CRC_POLYNOMIAL
                    : accumulated << 1;
            data <<= 1;
        }
        return accumulated & CRC_MASK;
    }
}
