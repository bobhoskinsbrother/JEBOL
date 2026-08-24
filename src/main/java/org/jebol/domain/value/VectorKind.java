package org.jebol.domain.value;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What one element of a vector is: a width, a signedness, and whether it
 * counts or measures.
 *
 * <p>Ten of them, which is what {@code sys-value.h} encodes in the four low
 * bits of a vector's series size. Two more are declared there and marked "not
 * used" -- eight and sixteen bit floats -- and the C's
 * {@code Get_Vector_Spec_From_Symbol} has no case for either, so a script that
 * asks for one is refused rather than given a narrower float.
 *
 * <p>An element is held as the bits that would be in memory, which is what
 * makes the two things a vector is for work at all. A number too big for the
 * width wraps rather than failing, and TO BINARY! is the stored bytes rather
 * than a conversion.
 */
public enum VectorKind {

    INT8("int8", 8, true, false),
    INT16("int16", 16, true, false),
    INT32("int32", 32, true, false),
    INT64("int64", 64, true, false),
    UINT8("uint8", 8, false, false),
    UINT16("uint16", 16, false, false),
    UINT32("uint32", 32, false, false),
    UINT64("uint64", 64, false, false),
    FLOAT32("float32", 32, true, true),
    FLOAT64("float64", 64, true, true);

    private static final Map<String, VectorKind> BY_NAME = Map.ofEntries(
            Map.entry("int8!", INT8), Map.entry("i8!", INT8),
            Map.entry("int16!", INT16), Map.entry("i16!", INT16),
            Map.entry("int32!", INT32), Map.entry("i32!", INT32),
            Map.entry("int64!", INT64), Map.entry("i64!", INT64),
            Map.entry("uint8!", UINT8), Map.entry("u8!", UINT8),
            Map.entry("byte!", UINT8),
            Map.entry("uint16!", UINT16), Map.entry("u16!", UINT16),
            Map.entry("uint32!", UINT32), Map.entry("u32!", UINT32),
            Map.entry("uint64!", UINT64), Map.entry("u64!", UINT64),
            Map.entry("float32!", FLOAT32), Map.entry("f32!", FLOAT32),
            Map.entry("float!", FLOAT32), Map.entry("single!", FLOAT32),
            Map.entry("float64!", FLOAT64), Map.entry("f64!", FLOAT64),
            Map.entry("double!", FLOAT64));

    private final String settledName;
    private final int bits;
    private final boolean signed;
    private final boolean measuring;

    VectorKind(String settledName, int bits, boolean signed, boolean measuring) {
        this.settledName = settledName;
        this.bits = bits;
        this.signed = signed;
        this.measuring = measuring;
    }

    /**
     * The one spelling this kind molds as, whichever alias built it.
     *
     * <p>Rebol normalises {@code byte!} to {@code uint8!} and {@code double!}
     * to {@code float64!} before it stores anything, so the alias is gone by
     * the time there is a vector to mold.
     */
    public String spelling() {
        return settledName + "!";
    }

    public static Optional<VectorKind> named(String word) {
        return Optional.ofNullable(BY_NAME.get(word.toLowerCase(Locale.ROOT)));
    }

    /** The kind a {@code [kind! width]} pair names, or nothing if there is none. */
    public static Optional<VectorKind> of(boolean wantsDecimals, boolean wantsUnsigned,
            int askedBits) {
        for (VectorKind candidate : values()) {
            if (candidate.measuring == wantsDecimals
                    && candidate.signed != wantsUnsigned
                    && candidate.bits == askedBits) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public int bits() {
        return bits;
    }

    public int bytes() {
        return bits / 8;
    }

    /** Whether elements are decimals rather than whole numbers. */
    public boolean measures() {
        return measuring;
    }

    /**
     * What {@code v/signed} answers.
     *
     * <p>True for every kind but the four unsigned integers, which means a
     * float vector is signed even though it has no sign bit to speak of.
     */
    public boolean isSigned() {
        return signed;
    }

    /** The datatype word {@code v/type} answers: integer! or decimal!. */
    public Datatype elementDatatype() {
        return measuring ? Datatype.DECIMAL : Datatype.INTEGER;
    }

    /**
     * A whole number reduced to what this kind can hold.
     *
     * <p>Truncating rather than refusing is the behaviour: the C assigns
     * through a narrower pointer and lets the machine drop the high bits, so
     * 200 in an {@code int8!} is -56 and -1 in a {@code uint8!} is 255.
     */
    public long store(long number) {
        if (measuring) {
            return storeMeasured(number);
        }
        return switch (this) {
            case INT8 -> (byte) number;
            case INT16 -> (short) number;
            case INT32 -> (int) number;
            case UINT8 -> number & 0xFFL;
            case UINT16 -> number & 0xFFFFL;
            case UINT32 -> number & 0xFFFFFFFFL;
            default -> number;
        };
    }

    /** A decimal reduced to what this kind can hold, truncating towards zero. */
    public long storeMeasured(double number) {
        return switch (this) {
            case FLOAT32 -> Float.floatToRawIntBits((float) number) & 0xFFFFFFFFL;
            case FLOAT64 -> Double.doubleToRawLongBits(number);
            default -> store((long) number);
        };
    }

    /**
     * A REBOL value reduced to one stored element.
     *
     * <p>{@code Set_Vector_Value} takes an integer, a decimal or a character
     * and converts between the first two as the kind needs. Anything else
     * reaches {@code Trap_Arg}, which the caller reports: this is the value
     * layer and knows nothing about errors a script can catch.
     */
    public long storedForm(Value written) {
        return switch (written) {
            case IntegerValue number -> store(number.magnitude());
            case CharacterValue letter -> store(letter.codepoint());
            case DecimalValue number -> storeMeasured(number.quantity());
            default -> throw new IllegalArgumentException(
                    "a vector holds numbers, not " + written.datatype().literalSpelling());
        };
    }

    /**
     * A stored element as a decimal, which is what the statistics work on.
     *
     * <p>The widest unsigned kind is read here as though it were signed, which
     * is what the C does: {@code get_vect_decimal} tests {@code type <= VTUI64}
     * before it tests for an unsigned kind, and that first test already covers
     * every integer kind, so the unsigned branch behind it cannot be reached.
     * Minimum and maximum do not come through here and are unsigned properly.
     */
    public double asDecimal(long stored) {
        return switch (this) {
            case FLOAT32 -> Float.intBitsToFloat((int) stored);
            case FLOAT64 -> Double.longBitsToDouble(stored);
            default -> stored;
        };
    }

    /** What reading one element gives a script: an integer! or a decimal!. */
    public Value read(long stored) {
        return measuring
                ? DecimalValue.of(asDecimal(stored))
                : IntegerValue.of(stored);
    }

    /** The element's bytes as they sit in memory, least significant first. */
    public byte[] octetsOf(long stored) {
        byte[] octets = new byte[bytes()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) (stored >>> (8 * at));
        }
        return octets;
    }

    /**
     * One element read back out of its bytes, least significant first.
     *
     * <p>The bytes are what was stored, so a float's bytes are already its
     * bits and must not go through {@link #storeMeasured} again -- that would
     * read the bit pattern as though it were the number it encodes.
     */
    public long fromOctets(byte[] octets, int at) {
        long gathered = 0;
        for (int step = 0; step < bytes(); step++) {
            gathered |= Byte.toUnsignedLong(octets[at + step]) << (8 * step);
        }
        return measuring ? gathered : store(gathered);
    }
}
