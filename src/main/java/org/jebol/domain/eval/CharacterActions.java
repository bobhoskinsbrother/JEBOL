package org.jebol.domain.eval;

import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

/**
 * What a character does when an action is performed on it, which is what
 * {@code REBTYPE(Char)} answers in {@code t-char.c}.
 *
 * <p>A character is arithmetic on its codepoint and answers a character
 * again, with one exception that gives the whole thing its point: one
 * character taken from another answers how far apart they are, as a number,
 * because the distance between two letters is not itself a letter.
 *
 * <p>Whatever comes back has to be a codepoint that exists. Running off
 * either end of Unicode, or landing on a surrogate half, is an invalid
 * character rather than a character nobody can print.
 */
public final class CharacterActions {

    private final CharacterValue letter;

    public CharacterActions(CharacterValue letter) {
        this.letter = letter;
    }

    /** ADD, SUBTRACT, MULTIPLY, DIVIDE and REMAINDER on the codepoint. */
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

    /** What a character will take on the right, which is itself or a number. */
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

    /**
     * A codepoint Unicode actually has. The surrogate range is refused as well
     * as the two ends, because a surrogate half on its own is a piece of an
     * encoding rather than a character.
     */
    static int requireACodepoint(long wanted) {
        boolean surrogate = wanted >= 0xD800 && wanted <= 0xDFFF;
        if (wanted < 0 || wanted > CharacterValue.MAXIMUM_CODEPOINT || surrogate) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR, IntegerValue.of(wanted));
        }
        return (int) wanted;
    }
}
