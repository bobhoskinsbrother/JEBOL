package org.jebol.domain.value;

public final class CharacterActions {

    private final CharacterValue letter;

    public CharacterActions(CharacterValue letter) {
        this.letter = letter;
    }

    private static final long PAST_WIDEST_CODEPOINT_COUNT = 1L << 32;

    public Value combineWith(Value right, ArithmeticOperation operation) {
        long other = readTheCodepointOfferedBy(right);
        long codepoint = letter.codepoint();
        long answered = operation.onCodepoints(codepoint, other);
        if (operation.canSubtractOneFromOther() && right instanceof CharacterValue) {
            return IntegerValue.of(asUnsignedWord(answered));
        }
        return CharacterValue.of(requireCodepoint(answered));
    }

    private static long asUnsignedWord(long answered) {
        return answered < 0
                ? answered + PAST_WIDEST_CODEPOINT_COUNT
                : answered;
    }

    private long readTheCodepointOfferedBy(Value right) {
        return switch (right) {
            case CharacterValue another -> another.codepoint();
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction ->
                    (long) fraction.quantity();
            default -> throw Raised.ofUnrelated(letter, right);
        };
    }

    private static long divide(long codepoint, long other) {
        if (other == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return codepoint / other;
    }

    private static long takeRemainder(long codepoint, long other) {
        if (other == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return codepoint % other;
    }

    static int requireCodepoint(long wanted) {
        boolean surrogate = wanted >= 0xD800 && wanted <= 0xDFFF;
        if (wanted < 0 || wanted > CharacterValue.MAXIMUM_CODEPOINT || surrogate) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR, IntegerValue.of(wanted));
        }
        return (int) wanted;
    }
}
