package org.jebol.domain.value;

import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * A single Unicode scalar value.
 *
 * <p>A codepoint rather than a Java {@code char}, because a {@code char} is a
 * UTF-16 code unit and half of an astral character is not a character.
 */
public record CharacterValue(int codepoint) implements Value {

    private static final int FIRST_SURROGATE = 0xD800;
    private static final int LAST_SURROGATE = 0xDFFF;

    @Override
    public Value randomised(RandomDraw draw) {
        return codepoint == 0 ? this : CharacterValue.of(aValidCodepointDrawnBy(draw));
    }

    private int aValidCodepointDrawnBy(RandomDraw draw) {
        while (true) {
            int picked = 1 + draw.below(codepoint);
            boolean surrogate = picked >= FIRST_SURROGATE && picked <= LAST_SURROGATE;
            if (!surrogate && picked <= MAXIMUM_CODEPOINT) {
                return picked;
            }
        }
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return codepoint;
    }

    @Override
    public Value absolute() {
        return this;
    }

    @Override
    public String writtenInHex(HexWidth width) {
        return width.sixteenDigitsKeptToTheRight(codepoint, digitsItsMagnitudeNeeds());
    }

    private int digitsItsMagnitudeNeeds() {
        return codepoint <= 0xFF ? 2
                : codepoint <= 0xFFFF ? 4
                : codepoint <= 0xFFFFFF ? 6
                : 8;
    }


    @Override
    public Value arithmetic(Value right, ArithmeticOperation operation) {
        return new CharacterActions(this).combinedWith(right, operation);
    }


    @Override
    public boolean equalTo(Value other, Sameness how) {
        if (other instanceof CharacterValue || how.theyWereBroughtTogetherFirst()) {
            return foldedTo(codepoint) == foldedTo(codepointOf(other));
        }
        return false;
    }

    private static int foldedTo(int codepoint) {
        return Character.toLowerCase(codepoint);
    }

    private static int codepointOf(Value value) {
        return value instanceof CharacterValue(int theirs)
                ? theirs
                : (int) ((IntegerValue) value).magnitude();
    }

    @Override
    public Optional<IntegerValue> asWholeNumber() {
        return Optional.of(IntegerValue.of(codepoint));
    }

    @Override
    public Optional<Value[]> broughtTogetherWith(Value other) {
        return other instanceof IntegerValue
                ? both(asWholeNumber().orElseThrow(), other)
                : Optional.empty();
    }

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return CharacterValue.of(
                (int) operation.onWholeElements(codepoint, aWholeNumberFrom(right)));
    }

    private long aWholeNumberFrom(Value right) {
        return switch (right) {
            case CharacterValue(int other) -> other;
            case IntegerValue(long magnitude) -> magnitude;
            default -> throw Raised.notRelated(this, right);
        };
    }


    public static final int MAXIMUM_CODEPOINT = 0x10FFFF;

    public CharacterValue {
        if (codepoint < 0 || codepoint > MAXIMUM_CODEPOINT) {
            throw new IllegalArgumentException(
                    "codepoint out of range: " + codepoint);
        }
        if (Character.isSurrogate((char) codepoint) && codepoint <= Character.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "a lone surrogate is not a character: " + codepoint);
        }
    }

    public static CharacterValue of(int codepoint) {
        return new CharacterValue(codepoint);
    }

    @Override
    public java.util.Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return java.util.Optional.of(asItStands(wanted, codepoint));
    }

    @Override
    public Datatype datatype() {
        return Datatype.CHAR;
    }

    @Override
    public String toString() {
        return new String(Character.toChars(codepoint));
    }
}
