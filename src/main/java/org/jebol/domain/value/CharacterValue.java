package org.jebol.domain.value;

import java.util.List;
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
    public Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.of(asItStands(wanted, codepoint));
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new CharacterDatatype();

    private static final class CharacterDatatype extends Datatype {

        private static final int MOST_HEX_DIGITS_SCANNED = 16;

        CharacterDatatype() {
            super("char", Typeset.SCALAR);
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            throw refusingConstruction(contents);
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return switch (from) {
                case CharacterValue already -> already;
                case StringValue text when text.text().isEmpty() -> throw refusing(from);
                case StringValue text -> CharacterValue.of(text.text().codePointAt(0));
                case BinaryValue octets -> characterLeadingThe(octets);
                case IssueValue issue -> characterSpeltInHexBy(issue);
                case IntegerValue whole -> characterAt(whole.magnitude());
                case DecimalValue number -> characterAt((long) number.quantity());
                default -> throw refusing(from);
            };
        }

        private Value characterAt(long asked) {
            if (asked < Integer.MIN_VALUE || asked > Integer.MAX_VALUE) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, IntegerValue.of(asked));
            }
            if (asked < 0 || asked > MAXIMUM_CODEPOINT || isALoneSurrogate(asked)) {
                throw Raised.of(EvaluationFailure.INVALID_CHAR,
                        IntegerValue.of(Integer.toUnsignedLong((int) asked)));
            }
            return CharacterValue.of((int) asked);
        }

        private boolean isALoneSurrogate(long asked) {
            return asked <= Character.MAX_VALUE && Character.isSurrogate((char) asked);
        }

        private Value characterLeadingThe(BinaryValue octets) {
            byte[] bytes = octets.bytesFromHere();
            if (bytes.length == 0) {
                throw refusing(octets);
            }
            int lead = bytes[0] & 0xFF;
            if (lead <= 0x80) {
                return CharacterValue.of(lead);
            }
            int continuations = continuationBytesFollowing(lead);
            if (continuations == 0 || bytes.length <= continuations) {
                throw refusing(octets);
            }
            int decoded = lead & (0x7F >> continuations);
            for (int at = 1; at <= continuations; at++) {
                int following = bytes[at] & 0xFF;
                if ((following & 0xC0) != 0x80) {
                    throw refusing(octets);
                }
                decoded = (decoded << 6) | (following & 0x3F);
            }
            if (decoded > Character.MAX_CODE_POINT) {
                throw refusing(octets);
            }
            return CharacterValue.of(decoded);
        }

        private int continuationBytesFollowing(int lead) {
            if ((lead & 0xE0) == 0xC0) {
                return 1;
            }
            if ((lead & 0xF0) == 0xE0) {
                return 2;
            }
            if ((lead & 0xF8) == 0xF0) {
                return 3;
            }
            return 0;
        }

        private Value characterSpeltInHexBy(AnyWordValue issue) {
            String spelling = issue.spelling();
            if (spelling.isEmpty() || spelling.length() > MOST_HEX_DIGITS_SCANNED) {
                throw refusing(issue);
            }
            long decoded;
            try {
                decoded = Long.parseLong(spelling, 16);
            } catch (NumberFormatException notHexadecimal) {
                throw refusing(issue);
            }
            if (decoded < 0 || decoded > Character.MAX_CODE_POINT) {
                throw refusing(issue);
            }
            return CharacterValue.of((int) decoded);
        }
    }

    @Override
    public String toString() {
        return new String(Character.toChars(codepoint));
    }
}
