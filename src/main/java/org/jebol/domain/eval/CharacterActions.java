package org.jebol.domain.eval;

import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public final class CharacterActions {

    private final CharacterValue letter;

    public CharacterActions(CharacterValue letter) {
        this.letter = letter;
    }

    Value combinedWith(Value right, Arithmetic.Operation operation) {
        long other = codepointOfferedBy(right);
        long codepoint = letter.codepoint();
        long answered = switch (operation) {
            case ADD -> codepoint + other;
            case SUBTRACT -> codepoint - other;
            case MULTIPLY -> codepoint * other;
            case DIVIDE -> dividedBy(codepoint, other);
            case REMAINDER -> restOf(codepoint, other);
            case MODULO -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "cannot use that on a character");
        };
        if (operation == Arithmetic.Operation.SUBTRACT && right instanceof CharacterValue) {
            return IntegerValue.of(answered);
        }
        return CharacterValue.of(requireACodepoint(answered));
    }

    private static long codepointOfferedBy(Value right) {
        return switch (right) {
            case CharacterValue another -> another.codepoint();
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction -> (long) fraction.quantity();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "a character takes a character or a number, not a "
                            + right.datatype().literalSpelling());
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
