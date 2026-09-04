package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.function.LongBinaryOperator;

/**
 * Arithmetic across a whole vector at once.
 *
 * <p>Two shapes, and the C keeps them apart: {@code Math_Op_Vector} applies
 * one number to every element, and {@code Math_Op_Vector_Vector} works two
 * vectors element by element as far as the shorter one goes. Both answer a
 * fresh vector and leave what they were given alone.
 *
 * <p>Nothing here guards against a number that will not fit. Reducing the
 * answer to the vector's width is the behaviour rather than a failure mode,
 * so an {@code int8!} vector wraps and says nothing about it.
 */
public final class VectorMath {

    /** The eight operations {@code REBTYPE(Vector)} sends this way. */
    public enum Operation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, AND, OR, XOR;

        boolean isBitwise() {
            return this == AND || this == OR || this == XOR;
        }
    }

    private VectorMath() {
    }

    /** Whether a pair of arguments is arithmetic this class should do. */
    public static boolean isVectorArithmetic(Value left, Value right) {
        return left instanceof VectorValue || right instanceof VectorValue;
    }

    public static Value done(Value left, Value right, Operation operation) {
        if (left instanceof VectorValue first && right instanceof VectorValue second) {
            return elementByElement(first, second, operation);
        }
        if (left instanceof VectorValue only) {
            return everyElementAgainst(only, right, operation);
        }
        return everyElementAgainst((VectorValue) right, left, operation);
    }

    /**
     * One number applied to every element from the vector's position on.
     *
     * <p>The number is reduced to the vector's own kind first, which is why
     * multiplying an {@code int8!} vector by 2.4 doubles it: the C truncates
     * the decimal to an integer before the loop starts, so the four tenths are
     * gone before any element sees them.
     */
    private static Value everyElementAgainst(VectorValue vector, Value number,
            Operation operation) {

        VectorKind kind = vector.kind();
        refuseBitwiseOnDecimals(kind, operation);
        double asDecimal = number instanceof DecimalValue fraction
                ? fraction.quantity()
                : ((IntegerValue) number).magnitude();
        long asWholeNumber = (long) asDecimal;
        refuseDivisionByZero(kind, operation, asWholeNumber);

        VectorStorage answer = new VectorStorage(kind, vector.lengthFromHere());
        for (int at = 0; at < vector.lengthFromHere(); at++) {
            long held = vector.storage().at(vector.index() + at);
            answer.set(at + 1, kind.measures()
                    ? measured(kind, kind.asDecimal(held), asDecimal, operation)
                    : counted(kind, held, asWholeNumber, operation));
        }
        return new VectorValue(answer, 1);
    }

    /**
     * Two vectors of the same kind, element by element.
     *
     * <p>The same kind is not the same as the same size: the C compares the
     * whole four-bit encoding, so an {@code int8!} and a {@code uint8!} vector
     * are as incompatible as an {@code int8!} and a {@code float64!} one.
     */
    private static Value elementByElement(VectorValue left, VectorValue right,
            Operation operation) {

        VectorKind kind = left.kind();
        if (kind != right.kind()) {
            throw Raised.of(EvaluationFailure.VECTOR_NOT_COMPATIBLE,
                    kind.spelling() + " and " + right.kind().spelling());
        }
        refuseBitwiseOnDecimals(kind, operation);
        int shared = Math.min(left.lengthFromHere(), right.lengthFromHere());
        VectorStorage answer = new VectorStorage(kind, shared);
        for (int at = 0; at < shared; at++) {
            long ours = left.storage().at(left.index() + at);
            long theirs = right.storage().at(right.index() + at);
            if (kind.measures()) {
                double divisor = kind.asDecimal(theirs);
                refuseElementByZero(operation, divisor == 0.0);
                answer.set(at + 1,
                        measured(kind, kind.asDecimal(ours), divisor, operation));
            } else {
                refuseElementByZero(operation, theirs == 0);
                answer.set(at + 1, counted(kind, ours, theirs, operation));
            }
        }
        return new VectorValue(answer, 1);
    }

    private static void refuseBitwiseOnDecimals(VectorKind kind, Operation operation) {
        if (kind.measures() && operation.isBitwise()) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    org.jebol.domain.value.WordValue.of(
                            operation.name().toLowerCase(java.util.Locale.ROOT)),
                    org.jebol.domain.value.WordValue.of(kind.spelling()));
        }
    }

    /**
     * The same refusal between two vectors, where every element is its own
     * divisor.
     *
     * <p>{@code VEC_OP_LOOP_NO_ZERO} tests each element of the divisor at its
     * own type, so a measuring vector is guarded here where a plain zero
     * divisor is not: the loop is written once and used for every kind.
     */
    private static void refuseElementByZero(Operation operation, boolean isZero) {
        boolean guarded = operation == Operation.DIVIDE
                || operation == Operation.REMAINDER;
        if (guarded && isZero) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "");
        }
    }

    /**
     * The two guards, which differ, and the difference is deliberate in the C.
     *
     * <p>Dividing tests {@code i == 0 && bits <= VTUI64}, so a measuring vector
     * divided by zero is left to the machine and answers infinity. The
     * remainder tests {@code i == 0} alone and refuses whatever the vector
     * holds. Both read the divisor already truncated to a whole number, which
     * is why dividing by a half is dividing by zero.
     */
    private static void refuseDivisionByZero(VectorKind kind, Operation operation,
            long truncatedDivisor) {

        boolean guarded = operation == Operation.REMAINDER
                || (operation == Operation.DIVIDE && !kind.measures());
        if (guarded && truncatedDivisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "");
        }
    }

    private static long counted(VectorKind kind, long held, long against,
            Operation operation) {

        LongBinaryOperator arithmetic = switch (operation) {
            case ADD -> Long::sum;
            case SUBTRACT -> (ours, theirs) -> ours - theirs;
            case MULTIPLY -> (ours, theirs) -> ours * theirs;
            case DIVIDE -> (ours, theirs) -> ours / theirs;
            case REMAINDER -> (ours, theirs) -> ours % theirs;
            case AND -> (ours, theirs) -> ours & theirs;
            case OR -> (ours, theirs) -> ours | theirs;
            case XOR -> (ours, theirs) -> ours ^ theirs;
        };
        return kind.store(arithmetic.applyAsLong(held, against));
    }

    private static long measured(VectorKind kind, double held, double against,
            Operation operation) {

        double answer = switch (operation) {
            case ADD -> held + against;
            case SUBTRACT -> held - against;
            case MULTIPLY -> held * against;
            case DIVIDE -> held / against;
            case REMAINDER -> held % against;
            default -> held;
        };
        return kind.storeMeasured(answer);
    }
}
