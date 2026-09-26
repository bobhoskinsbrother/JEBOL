package org.jebol.domain.eval;

import org.jebol.domain.date.DateArithmetic;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
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

public final class Arithmetic {

    public enum Division {
        SIGN_FOLLOWS_THE_DIVIDEND,
        NEVER_NEGATIVE,
        SIGN_FOLLOWS_THE_DIVISOR
    }

    private Arithmetic() {
    }

    public static Value sum(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.theOneCalled("add"));
    }

    public static Value difference(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.theOneCalled("subtract"));
    }

    public static Value product(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.theOneCalled("multiply"));
    }

    public static Value quotient(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.theOneCalled("divide"));
    }

    public static Value remainder(Value left, Value right) {
        return combined(left, right, ArithmeticOperation.theOneCalled("remainder"));
    }

    public static Value wholeQuotient(Value dividend, Value divisor) {
        long by = (long) Comparison.asDouble(divisor);
        requireNonZero(by);
        return IntegerValue.of((long) Comparison.asDouble(dividend) / by);
    }

    public static Value rest(Value dividend, Value divisor, Division definition) {
        if (dividend instanceof IntegerValue(long magnitude1) && divisor instanceof IntegerValue(long magnitude)) {
            return IntegerValue.of(
                    wholeRest(magnitude1, magnitude, definition));
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

    private static Value combined(Value left, Value right, ArithmeticOperation operation) {
        return theKindThatClaims(left, right).combine(left, right, operation);
    }

    private static Kind theKindThatClaims(Value left, Value right) {
        return Arrays.stream(Kind.values())
                .filter(kind -> kind.claims(left, right))
                .findFirst()
                .orElse(Kind.FRACTIONS);
    }

    private enum Kind {

        VECTORS {
            @Override
            boolean claims(Value left, Value right) {
                return VectorMath.isVectorArithmetic(left, right);
            }

            @Override
            Value combine(Value left, Value right, ArithmeticOperation operation) {
                if (!operation.worksOnVectors()
                        || (!(left instanceof VectorValue) && !operation.isCommutative())) {
                    throw notRelated(left, right);
                }
                Value other = left instanceof VectorValue ? right : left;
                if (!(other instanceof VectorValue)
                        && !(other instanceof IntegerValue)
                        && !(other instanceof DecimalValue)) {
                    throw notRelated(left, right);
                }
                return VectorMath.done(left, right, operation);
            }
        },

        CHARACTERS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof CharacterValue;
            }

            @Override
            Value combine(Value left, Value right, ArithmeticOperation operation) {
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
            Value combine(Value left, Value right, ArithmeticOperation operation) {
                return new PairActions(left).combinedWith(right, operation);
            }
        },

        TUPLES {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof TupleValue || right instanceof TupleValue;
            }

            @Override
            Value combine(Value left, Value right, ArithmeticOperation operation) {
                return new TupleActions(left).combinedWith(right, operation);
            }
        },

        DATES {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof DateValue || right instanceof DateValue;
            }

            @Override
            Value combine(Value left, Value right, ArithmeticOperation operation) {
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
            Value combine(Value left, Value right, ArithmeticOperation operation) {
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
            Value combine(Value left, Value right, ArithmeticOperation operation) {
                return new MoneyActions((MoneyValue) left).combinedWith(right, operation);
            }
        },

        DURATIONS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof TimeValue || right instanceof TimeValue;
            }

            @Override
            Value combine(Value left, Value right, ArithmeticOperation operation) {
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
            Value combine(Value left, Value right, ArithmeticOperation operation) {
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
            Value combine(Value left, Value right, ArithmeticOperation operation) {
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
            Value combine(Value left, Value right, ArithmeticOperation operation) {
                return operation.onFractions(Comparison.asDouble(left),
                        Comparison.asDouble(right), true);
            }
        };

        abstract boolean claims(Value left, Value right);

        abstract Value combine(Value left, Value right, ArithmeticOperation operation);
    }





    static Value decimalCombined(
            double left, double right, ArithmeticOperation operation) {
        return operation.onFractions(left, right);
    }

    static Value integerCombined(long left, long right, ArithmeticOperation operation) {
        return operation.onWholeNumbers(left, right);
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
