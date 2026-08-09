package org.jebol.domain.value;

/**
 * A single Unicode scalar value.
 *
 * <p>A codepoint rather than a Java {@code char}, because a {@code char} is a
 * UTF-16 code unit and half of an astral character is not a character.
 */
public record CharacterValue(int codepoint) implements Value {

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
    public Datatype datatype() {
        return Datatype.CHAR;
    }

    @Override
    public String toString() {
        return new String(Character.toChars(codepoint));
    }
}
