package org.jebol.domain.value;

import java.util.Optional;

/**
 * A single Unicode scalar value.
 *
 * <p>A codepoint rather than a Java {@code char}, because a {@code char} is a
 * UTF-16 code unit and half of an astral character is not a character.
 */
public record CharacterValue(int codepoint) implements Value {

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
