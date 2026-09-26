package org.jebol.domain.eval;

import org.jebol.domain.eval.arithmetic.ValueOperation;
import org.jebol.domain.value.*;


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

    private VectorMath() {
    }

    /** Whether a pair of arguments is arithmetic this class should do. */
    public static boolean isVectorArithmetic(Value left, Value right) {
        return left instanceof VectorValue || right instanceof VectorValue;
    }

    public static Value done(Value left, Value right, ValueOperation operation) {
        if (left instanceof VectorValue first && right instanceof VectorValue second) {
            return elementByElement(first, second, operation);
        }
        if (left instanceof VectorValue only) {
            return everyElementAgainstTheNumberReducedToTheVectorsKind(
                    only, right, operation);
        }
        return everyElementAgainstTheNumberReducedToTheVectorsKind(
                (VectorValue) right, left, operation);
    }

    private static Value everyElementAgainstTheNumberReducedToTheVectorsKind(
            VectorValue vector, Value number, ValueOperation operation) {

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

    private static Value elementByElement(VectorValue left, VectorValue right,
            ValueOperation operation) {

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

    private static void refuseBitwiseOnDecimals(VectorKind kind, ValueOperation operation) {
        if (kind.measures() && operation.isBitwise()) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    org.jebol.domain.value.WordValue.of(operation.spelling()),
                    org.jebol.domain.value.WordValue.of(kind.spelling()));
        }
    }

    private static void refuseElementByZero(ValueOperation operation, boolean isZero) {
        boolean guarded = operation.needsANonZeroDivisor()
                && !operation.isBitwise();
        if (guarded && isZero) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "");
        }
    }

    private static void refuseDivisionByZero(VectorKind kind, ValueOperation operation,
            long truncatedDivisor) {

        boolean guarded = operation.keepsTheSignOfTheDividend()
                || (operation.divides() && !kind.measures());
        if (guarded && truncatedDivisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "");
        }
    }

    private static long counted(VectorKind kind, long held, long against,
            ValueOperation operation) {

        return kind.store(operation.onWholeElements(held, against));
    }

    private static long measured(VectorKind kind, double held, double against,
            ValueOperation operation) {

        return kind.storeMeasured(operation.onMeasuredElements(held, against));
    }
}
