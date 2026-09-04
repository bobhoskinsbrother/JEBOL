package org.jebol.domain.eval;

import org.jebol.domain.value.*;

/**
 * Reading and writing a vector through a path.
 *
 * <p>A number selects an element and a word asks a question, and the two
 * differ in more than what they answer. Reading past either end gives none;
 * writing there is an error. A zero selects nothing in both directions but
 * reads as none and writes as out of range.
 *
 * <p>A negative number counts back from where the value points rather than
 * from the tail, which is the one thing about {@code PD_Vector} that surprises:
 * {@code pick (skip v 2) -1} is the element before the one skip landed on.
 */
public final class VectorPath {

    private VectorPath() {
    }

    public static Value read(VectorValue vector, Value selector) {
        if (selector instanceof WordValue asked) {
            return VectorQuery.field(vector, asked.canonical())
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                            asked.spelling()));
        }
        Integer chosen = positionChosenBy(vector, selector);
        if (chosen == null) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Molder.mold(selector));
        }
        if (chosen < 1 || chosen > vector.storageLength()) {
            return NoneValue.none();
        }
        return vector.elementAt(chosen);
    }

    public static void write(VectorValue vector, Value selector, Value written) {
        if (!(selector instanceof IntegerValue) && !(selector instanceof DecimalValue)) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET, Molder.mold(selector));
        }
        Integer chosen = positionChosenBy(vector, selector);
        if (chosen == null || chosen < 1 || chosen > vector.storageLength()) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(selector));
        }
        try {
            vector.storage().set(chosen, storedFormOf(vector.kind(), written));
        } catch (ProtectedFromChange locked) {
            throw Raised.of(EvaluationFailure.PROTECTED, "vector! is protected");
        }
    }

    /** A number reduced to what this vector holds, or an error a script can catch. */
    public static long storedFormOf(VectorKind kind, Value written) {
        try {
            return kind.storedForm(written);
        } catch (IllegalArgumentException notANumber) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(written));
        }
    }

    /**
     * Where in the whole storage a selector points, counting from one.
     *
     * <p>Null when the selector is not a number at all. Zero and negative
     * answers are left for the caller, because reading and writing differ on
     * what they mean.
     */
    private static Integer positionChosenBy(VectorValue vector, Value selector) {
        long asked;
        if (selector instanceof IntegerValue number) {
            asked = number.magnitude();
        } else if (selector instanceof DecimalValue number) {
            asked = (long) number.quantity();
        } else {
            return null;
        }
        if (asked == 0) {
            return 0;
        }
        if (asked < 0) {
            asked++;
        }
        return (int) (asked + vector.index() - 1);
    }
}
