package org.jebol.domain.eval.mathpresso;

import java.util.Optional;

import static org.jebol.domain.eval.mathpresso.SseDoubles.HALF;
import static org.jebol.domain.eval.mathpresso.SseDoubles.MAGNITUDE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.ONE;
import static org.jebol.domain.eval.mathpresso.SseDoubles.POSITIVE_INFINITY;
import static org.jebol.domain.eval.mathpresso.SseDoubles.SIGN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.ZERO;
import static org.jebol.domain.eval.mathpresso.SseDoubles.add;
import static org.jebol.domain.eval.mathpresso.SseDoubles.asDouble;
import static org.jebol.domain.eval.mathpresso.SseDoubles.divide;
import static org.jebol.domain.eval.mathpresso.SseDoubles.flag;
import static org.jebol.domain.eval.mathpresso.SseDoubles.isNaN;
import static org.jebol.domain.eval.mathpresso.SseDoubles.multiply;
import static org.jebol.domain.eval.mathpresso.SseDoubles.subtract;

final class CompiledFolding {

    private CompiledFolding() {
    }

    static Optional<Long> foldUnary(MathPressoOperator operator, long value) {
        return Optional.ofNullable(switch (operator) {
            case NEG -> value ^ SIGN;
            case NOT -> flag(!isNaN(value) && asDouble(value) == 0);
            case IS_NAN -> flag(isNaN(value));
            case IS_INF -> flag((value & MAGNITUDE) == POSITIVE_INFINITY);
            case IS_FINITE -> flag((value & POSITIVE_INFINITY) != POSITIVE_INFINITY);
            case SIGN_BIT -> flag(value < 0);
            case ROUND -> roundHalfUp(value);
            case ROUND_EVEN -> SseDoubles.nearestEven(value);
            case TRUNC -> SseDoubles.truncate(value);
            case FLOOR -> SseDoubles.floor(value);
            case CEIL -> SseDoubles.ceil(value);
            case ABS -> value & MAGNITUDE;
            case SQRT -> SseDoubles.squareRoot(value);
            case FRAC -> subtract(value, SseDoubles.floor(value));
            case RECIP -> divide(ONE, value);
            case EXP, LOG, LOG2, LOG10, SIN, COS, TAN, SINH, COSH, TANH, ASIN, ACOS, ATAN ->
                    LibmCalls.call(operator, value);
            default -> null;
        });
    }

    private static long roundHalfUp(long value) {
        long below = SseDoubles.floor(value);
        long fraction = subtract(value, below);
        boolean upwards = !isNaN(fraction) && asDouble(fraction) >= 0.5;
        return add(below, upwards ? ONE : ZERO);
    }

    static Optional<Long> foldBinary(MathPressoOperator operator, long left, long right) {
        boolean unordered = isNaN(left) || isNaN(right);
        double l = asDouble(left);
        double r = asDouble(right);
        return Optional.ofNullable(switch (operator) {
            case EQ -> flag(!unordered && l == r);
            case NE -> flag(unordered || l != r);
            case LT -> flag(!unordered && l < r);
            case LE -> flag(!unordered && l <= r);
            case GT -> flag(!unordered && l > r);
            case GE -> flag(!unordered && l >= r);
            case ADD -> add(left, right);
            case SUB -> subtract(left, right);
            case MUL -> multiply(left, right);
            case DIV -> divide(left, right);
            case MOD -> LibmCalls.remainderOnX87(left, right);
            case AVG -> multiply(add(left, right), HALF);
            case MIN -> isNaN(left) || (!unordered && l < r) ? left : right;
            case MAX -> isNaN(left) || (!unordered && l > r) ? left : right;
            case POW, ATAN2, HYPOT -> LibmCalls.call(operator, left, right);
            case COPY_SIGN -> (left & MAGNITUDE) | (right & SIGN);
            default -> null;
        });
    }
}
