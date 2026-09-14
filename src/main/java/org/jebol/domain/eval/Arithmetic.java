package org.jebol.domain.eval;

import org.jebol.domain.date.DateArithmetic;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;
import org.jebol.domain.value.WordValue;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * The one arithmetic every arithmetic native reaches. Five operations --
 * ADD, SUBTRACT, MULTIPLY, DIVIDE and REMAINDER -- over a dozen datatypes
 * that each combine differently, plus the three division conventions MOD
 * and MODULO ask for.
 *
 * <p>Which rules apply is decided by the pair of operands rather than by
 * either one alone, and the order matters: a vector beside an integer is
 * vector arithmetic, a character beside an integer is character arithmetic,
 * and a money beside a time bills an hourly rate. {@link Kind} names each of
 * those pairings once, in the order REBOL tries them, and the first that
 * claims a pair owns it.
 */
public final class Arithmetic {

    /**
     * Which way the sign of a remainder falls, which is the whole difference
     * between REBOL's three: {@code //} and MOD leave it following the
     * dividend, MODULO never answers a negative, and MODULO/FLOOR follows
     * the divisor.
     */
    public enum Division {
        SIGN_FOLLOWS_THE_DIVIDEND,
        NEVER_NEGATIVE,
        SIGN_FOLLOWS_THE_DIVISOR
    }

    private Arithmetic() {
    }

    /** ADD, and what {@code +} evaluates to. */
    public static Value sum(Value left, Value right) {
        return combined(left, right, Operation.ADD);
    }

    /** SUBTRACT, and what {@code -} evaluates to. */
    public static Value difference(Value left, Value right) {
        return combined(left, right, Operation.SUBTRACT);
    }

    /** MULTIPLY, and what {@code *} evaluates to. */
    public static Value product(Value left, Value right) {
        return combined(left, right, Operation.MULTIPLY);
    }

    /** DIVIDE, and what {@code /} evaluates to. */
    public static Value quotient(Value left, Value right) {
        return combined(left, right, Operation.DIVIDE);
    }

    /** REMAINDER, and what {@code //} evaluates to. */
    public static Value remainder(Value left, Value right) {
        return combined(left, right, Operation.REMAINDER);
    }

    /** INTEGER-DIVIDE: a whole quotient, with the fraction thrown away. */
    public static Value wholeQuotient(Value dividend, Value divisor) {
        long by = (long) Comparison.asDouble(divisor);
        requireNonZero(by);
        return IntegerValue.of((long) Comparison.asDouble(dividend) / by);
    }

    /** MOD and MODULO, which differ only in where they put the sign. */
    public static Value rest(Value dividend, Value divisor, Division definition) {
        if (dividend instanceof IntegerValue whole && divisor instanceof IntegerValue by) {
            return IntegerValue.of(
                    wholeRest(whole.magnitude(), by.magnitude(), definition));
        }
        double first = asMagnitude(dividend);
        double second = asMagnitude(divisor);
        requireNonZero(second);
        if (definition == Division.SIGN_FOLLOWS_THE_DIVIDEND) {
            return likeTheDividend(dividend, first % second);
        }
        double by = definition == Division.NEVER_NEGATIVE
                ? Math.abs(second) : second;
        double rest = ((first % by) + by) % by;
        return likeTheDividend(dividend, negligibleAgainstItsOperands(rest, first, by)
                ? 0.0
                : rest);
    }

    private static long wholeRest(long dividend, long divisor, Division definition) {
        requireNonZero(divisor);
        long rest = dividend % divisor;
        return switch (definition) {
            case SIGN_FOLLOWS_THE_DIVIDEND -> rest;
            case NEVER_NEGATIVE -> rest < 0 ? rest + Math.abs(divisor) : rest;
            case SIGN_FOLLOWS_THE_DIVISOR -> rest != 0 && (rest < 0) != (divisor < 0)
                    ? rest + divisor
                    : rest;
        };
    }

    /**
     * Which of the six a datatype is being asked for.
     *
     * <p>Public because it is the contract between the dispatcher and each
     * datatype's own arm, and those now live in their own packages. A date
     * has to be told which operation it is being asked to do.
     */
    public enum Operation { ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, MODULO }

    private static Value combined(Value left, Value right, Operation operation) {
        return theKindThatClaims(left, right).combine(left, right, operation);
    }

    private static Kind theKindThatClaims(Value left, Value right) {
        return Arrays.stream(Kind.values())
                .filter(kind -> kind.claims(left, right))
                .findFirst()
                .orElse(Kind.FRACTIONS);
    }

    /**
     * The pairings REBOL knows, in the order it tries them. Declaration
     * order is the priority: a vector beside a pair is vector arithmetic
     * because VECTORS is asked first, and FRACTIONS is what two plain
     * numbers fall through to when nothing above has claimed them.
     */
    private enum Kind {

        VECTORS {
            @Override
            boolean claims(Value left, Value right) {
                return VectorMath.isVectorArithmetic(left, right);
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                VectorMath.Operation asked = vectorOperationFor(operation);
                boolean orderMatters = asked != VectorMath.Operation.ADD
                        && asked != VectorMath.Operation.MULTIPLY;
                if (asked == null || (!(left instanceof VectorValue) && orderMatters)) {
                    throw notRelated(left, right);
                }
                Value other = left instanceof VectorValue ? right : left;
                if (!(other instanceof VectorValue)
                        && !(other instanceof IntegerValue)
                        && !(other instanceof DecimalValue)) {
                    throw notRelated(left, right);
                }
                return VectorMath.done(left, right, asked);
            }
        },

        CHARACTERS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof CharacterValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return new CharacterActions((CharacterValue) left)
                        .combinedWith(right, operation);
            }
        },

        POINTS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof PairValue || right instanceof PairValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return new PairActions(left).combinedWith(right, operation);
            }
        },

        TUPLES {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof TupleValue || right instanceof TupleValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return new TupleActions(left).combinedWith(right, operation);
            }
        },

        DATES {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof DateValue || right instanceof DateValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return left instanceof DateValue moment
                        ? new DateArithmetic(moment).combinedWith(right, operation)
                        : new DateArithmetic((DateValue) right).takenBy(left, operation);
            }
        },

        A_NUMBER_AND_A_CHARACTER {
            @Override
            boolean claims(Value left, Value right) {
                return right instanceof CharacterValue
                        && (left instanceof IntegerValue || left instanceof DecimalValue);
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                CharacterValue letter = (CharacterValue) right;
                Value asNumber = left instanceof IntegerValue
                        ? IntegerValue.of(letter.codepoint())
                        : DecimalValue.of(letter.codepoint());
                Value plainer = left instanceof DecimalValue quantity
                        ? DecimalValue.of(quantity.quantity())
                        : left;
                return Arithmetic.combined(plainer, asNumber, operation);
            }
        },

        AMOUNTS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof MoneyValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return new MoneyActions((MoneyValue) left).combinedWith(right, operation);
            }
        },

        DURATIONS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof TimeValue || right instanceof TimeValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return left instanceof TimeValue span
                        ? new TimeActions(span).combinedWith(right, operation)
                        : new TimeActions((TimeValue) right).takenBy(left, operation);
            }
        },

        A_NUMBER_AND_AN_AMOUNT {
            @Override
            boolean claims(Value left, Value right) {
                return right instanceof MoneyValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return new MoneyActions(MoneyValue.of(MoneyActions.asBigDecimal(left)))
                        .combinedWith(right, operation);
            }
        },

        WHOLE_NUMBERS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof IntegerValue && right instanceof IntegerValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return integerCombined(((IntegerValue) left).magnitude(),
                        ((IntegerValue) right).magnitude(), operation);
            }
        },

        FRACTIONS {
            @Override
            boolean claims(Value left, Value right) {
                return false;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return decimalCombined(Comparison.asDouble(left),
                        Comparison.asDouble(right), operation, true);
            }
        };

        abstract boolean claims(Value left, Value right);

        abstract Value combine(Value left, Value right, Operation operation);
    }

    private static VectorMath.Operation vectorOperationFor(Operation operation) {
        return switch (operation) {
            case ADD -> VectorMath.Operation.ADD;
            case SUBTRACT -> VectorMath.Operation.SUBTRACT;
            case MULTIPLY -> VectorMath.Operation.MULTIPLY;
            case DIVIDE -> VectorMath.Operation.DIVIDE;
            case REMAINDER -> VectorMath.Operation.REMAINDER;
            case MODULO -> null;
        };
    }

    private static Value integerCombined(long left, long right, Operation operation) {
        try {
            return switch (operation) {
                case ADD -> IntegerValue.of(Math.addExact(left, right));
                case SUBTRACT -> IntegerValue.of(Math.subtractExact(left, right));
                case MULTIPLY -> IntegerValue.of(Math.multiplyExact(left, right));
                case DIVIDE -> {
                    requireNonZero(right);
                    yield left % right == 0
                            ? IntegerValue.of(left / right)
                            : DecimalValue.of((double) left / right);
                }
                case REMAINDER -> {
                    requireNonZero(right);
                    yield IntegerValue.of(left % right);
                }
                case MODULO -> IntegerValue.of(
                        wholeRest(left, right, Division.NEVER_NEGATIVE));
            };
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW, overflowed.getMessage());
        }
    }

    static Value decimalCombined(double left, double right, Operation operation) {
        return decimalCombined(left, right, operation, false);
    }

    private static Value decimalCombined(
            double left, double right, Operation operation, boolean infinitiesAllowed) {
        return switch (operation) {
            case ADD -> DecimalValue.of(left + right);
            case SUBTRACT -> DecimalValue.of(left - right);
            case MULTIPLY -> DecimalValue.of(left * right);
            case DIVIDE -> {
                if (!infinitiesAllowed) {
                    requireNonZero(right);
                }
                yield DecimalValue.of(left / right);
            }
            case REMAINDER -> {
                requireNonZero(right);
                yield DecimalValue.of(left % right);
            }
            case MODULO -> {
                requireNonZero(right);
                double rest = left % right;
                yield DecimalValue.of(rest < 0 ? rest + Math.abs(right) : rest);
            }
        };
    }


    private static Value likeTheDividend(Value dividend, double magnitude) {
        return switch (dividend) {
            case CharacterValue ignored -> CharacterValue.of((int) magnitude);
            case TimeValue ignored -> TimeValue.ofNanoseconds((long) magnitude);
            case MoneyValue ignored -> MoneyValue.of(new BigDecimal((long) magnitude));
            case IntegerValue ignored -> IntegerValue.of((long) magnitude);
            default -> DecimalValue.of(magnitude);
        };
    }

    private static boolean negligibleAgainstItsOperands(
            double rest, double dividend, double divisor) {

        return nearlyTheSame(dividend, dividend - rest)
                || nearlyTheSame(divisor, divisor + rest);
    }

    private static final long STEPS_MODULUS_ALLOWS = 10;

    static boolean nearlyTheSame(double first, double second) {
        return Comparison.looselyEqual(
                DecimalValue.of(first), DecimalValue.of(second), STEPS_MODULUS_ALLOWS);
    }

    static double asMagnitude(Value value) {
        return switch (value) {
            case CharacterValue character -> character.codepoint();
            case TimeValue time -> time.nanoseconds();
            default -> Comparison.asDouble(value);
        };
    }


    static void requireNonZero(double divisor) {
        if (divisor == 0.0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    static Raised notRelated(Value left, Value right) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(left.datatype().literalSpelling()),
                WordValue.of(right.datatype().literalSpelling()));
    }
}
