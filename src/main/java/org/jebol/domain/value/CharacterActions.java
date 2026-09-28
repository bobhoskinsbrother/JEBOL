package org.jebol.domain.value;

public final class CharacterActions {

    private final CharacterValue letter;

    public CharacterActions(CharacterValue letter) {
        this.letter = letter;
    }

    private static final long PAST_THE_WIDEST_CODEPOINT_COUNT = 1L << 32;

    public Value combinedWith(Value right, ArithmeticOperation operation) {
        long other = codepointOfferedBy(right);
        long codepoint = letter.codepoint();
        long answered = operation.onCodepoints(codepoint, other);
        if (operation.subtractsOneFromTheOther() && right instanceof CharacterValue) {
            return IntegerValue.of(asAnUnsignedWord(answered));
        }
        return CharacterValue.of(requireACodepoint(answered));
    }

    private static long asAnUnsignedWord(long answered) {
        return answered < 0
                ? answered + PAST_THE_WIDEST_CODEPOINT_COUNT
                : answered;
    }

    private long codepointOfferedBy(Value right) {
        return switch (right) {
            case CharacterValue another -> another.codepoint();
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction when fraction.datatype() == Datatype.DECIMAL ->
                    (long) fraction.quantity();
            default -> throw Raised.notRelated(letter, right);
        };
    }

    private static long dividedBy(long codepoint, long other) {
        if (other == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return codepoint / other;
    }

    private static long restOf(long codepoint, long other) {
        if (other == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return codepoint % other;
    }

    static int requireACodepoint(long wanted) {
        boolean surrogate = wanted >= 0xD800 && wanted <= 0xDFFF;
        if (wanted < 0 || wanted > CharacterValue.MAXIMUM_CODEPOINT || surrogate) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR, IntegerValue.of(wanted));
        }
        return (int) wanted;
    }
}
