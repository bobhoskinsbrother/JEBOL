package org.jebol.domain.eval;

import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

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

    enum Operation { ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, MODULO }

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
                return characterCombined((CharacterValue) left, right, operation);
            }
        },

        POINTS {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof PairValue || right instanceof PairValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                requireAPairOrAPlainNumber(left);
                requireAPairOrAPlainNumber(right);
                if (operation == Operation.DIVIDE || operation == Operation.REMAINDER
                        || operation == Operation.MODULO) {
                    requireNonZero(firstHalfOf(right));
                    requireNonZero(secondHalfOf(right));
                }
                return PairValue.of(
                        halfCombined(firstHalfOf(left), firstHalfOf(right), operation),
                        halfCombined(secondHalfOf(left), secondHalfOf(right), operation));
            }
        },

        TUPLES {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof TupleValue || right instanceof TupleValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return octetByOctet(left, right, (octet, against, fractional) ->
                        octetCombined(octet, against, fractional, operation));
            }
        },

        DATES {
            @Override
            boolean claims(Value left, Value right) {
                return left instanceof DateValue || right instanceof DateValue;
            }

            @Override
            Value combine(Value left, Value right, Operation operation) {
                return dateCombined(left, right, operation);
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
                return timeCombined(left, right, operation);
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

    private static Value decimalCombined(double left, double right, Operation operation) {
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

    private static Value characterCombined(
            CharacterValue letter, Value right, Operation operation) {

        long other = switch (right) {
            case CharacterValue another -> another.codepoint();
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction -> (long) fraction.quantity();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "a character takes a character or a number, not a "
                            + right.datatype().literalSpelling());
        };
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
        if (operation == Operation.SUBTRACT && right instanceof CharacterValue) {
            return IntegerValue.of(answered);
        }
        return CharacterValue.of(requireACodepoint(answered));
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

    private static int requireACodepoint(long wanted) {
        boolean surrogate = wanted >= 0xD800 && wanted <= 0xDFFF;
        if (wanted < 0 || wanted > CharacterValue.MAXIMUM_CODEPOINT || surrogate) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR, IntegerValue.of(wanted));
        }
        return (int) wanted;
    }

    private static void requireAPairOrAPlainNumber(Value side) {
        if (side instanceof PairValue
                || side instanceof IntegerValue
                || side instanceof DecimalValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                side.datatype().literalSpelling() + " does not go with pair arithmetic");
    }

    private static double halfCombined(double left, double right, Operation operation) {
        return ((DecimalValue) decimalCombined(left, right, operation)).quantity();
    }

    private static long octetCombined(
            long octet, double against, boolean fractional, Operation operation) {

        return switch (operation) {
            case ADD -> octet + (long) against;
            case SUBTRACT -> octet - (long) against;
            case MULTIPLY -> {
                if (octet == 0) {
                    yield 0;
                }
                if (against > 255) {
                    yield 255;
                }
                yield fractional ? (long) (octet * against) : octet * (long) against;
            }
            case DIVIDE -> {
                if (against == 0) {
                    throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
                }
                yield fractional
                        ? (long) roundedHalfAwayFromZero(octet / against)
                        : octet / (long) against;
            }
            case REMAINDER, MODULO -> {
                if ((long) against == 0) {
                    throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
                }
                yield octet % (long) against;
            }
        };
    }

    private static Value dateCombined(Value left, Value right, Operation operation) {
        if (left instanceof DateValue from && right instanceof DateValue to) {
            if (operation != Operation.SUBTRACT) {
                throw Raised.cannotUse(left, "date arithmetic");
            }
            return IntegerValue.of(dayNumberOf(from) - dayNumberOf(to));
        }
        if (operation == Operation.SUBTRACT && !(left instanceof DateValue)) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    WordValue.of(operation.name().toLowerCase(Locale.ROOT) + ":"),
                    DatatypeValue.of(left.datatype()));
        }
        DateValue moment = left instanceof DateValue date ? date : (DateValue) right;
        Value span = left instanceof DateValue ? right : left;
        int sign = operation == Operation.SUBTRACT ? -1 : 1;
        return span.datatype() == Datatype.INTEGER
                ? dateMovedByDays(moment, sign * (long) Comparison.asDouble(span))
                : dateMovedByClock(moment, sign * clockShiftOf(span));
    }

    private static DateValue dateMovedByDays(DateValue moment, long days) {
        LocalDate shifted = LocalDate.ofEpochDay(dayNumberOf(moment) + days);
        return new DateValue(shifted.getYear(), shifted.getMonthValue(),
                shifted.getDayOfMonth(), moment.timeOfDay(), moment.zoneMinutes());
    }

    private static DateValue dateMovedByClock(DateValue moment, long nanoseconds) {
        long shifted = moment.timeOfDay().map(TimeValue::nanoseconds).orElse(0L)
                + nanoseconds;
        LocalDate day = LocalDate.ofEpochDay(dayNumberOf(moment)
                + Math.floorDiv(shifted, TimeValue.NANOSECONDS_PER_DAY));
        return new DateValue(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(
                        Math.floorMod(shifted, TimeValue.NANOSECONDS_PER_DAY))),
                moment.zoneMinutes());
    }

    private static long clockShiftOf(Value span) {
        return span instanceof TimeValue duration
                ? duration.nanoseconds()
                : (long) (Comparison.asDouble(span) * TimeValue.NANOSECONDS_PER_DAY);
    }

    private static Value timeCombined(Value left, Value right, Operation operation) {
        if (!(left instanceof TimeValue) && right instanceof TimeValue duration) {
            return aNumberAgainstATime(left, duration, operation);
        }
        if (right instanceof TimeValue other) {
            return aTimeAgainstATime(left, other, operation);
        }
        if (right instanceof MoneyValue rate) {
            return aTimeAgainstAMoney(left, rate, operation);
        }
        if (right instanceof DecimalValue portion
                && portion.datatype() == Datatype.PERCENT) {
            return aTimeAgainstAProportion(left, portion, operation);
        }
        if (!(right instanceof IntegerValue) && !(right instanceof DecimalValue)) {
            throw notRelatedToATime(operation);
        }
        if (operation == Operation.MULTIPLY || operation == Operation.DIVIDE) {
            long scaled = (long) ((DecimalValue) decimalCombined(
                    nanosecondsOf(left), Comparison.asDouble(right), operation)).quantity();
            return TimeValue.ofNanoseconds(scaled);
        }
        return addedInWholeNanoseconds(left, right, operation);
    }

    private static Raised notRelatedToATime(Operation operation) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(operation.name().toLowerCase(Locale.ROOT)),
                DatatypeValue.of(Datatype.TIME));
    }

    private static Value aTimeAgainstATime(
            Value left, TimeValue right, Operation operation) {

        if (operation == Operation.DIVIDE) {
            requireNonZero(right.nanoseconds());
            return DecimalValue.of(nanosecondsOf(left) / (double) right.nanoseconds());
        }
        if (operation == Operation.MULTIPLY) {
            throw notRelatedToATime(operation);
        }
        return addedInWholeNanoseconds(left, right, operation);
    }

    private static Value aTimeAgainstAMoney(
            Value left, MoneyValue rate, Operation operation) {

        BigDecimal hours = BigDecimal.valueOf(
                nanosecondsOf(left) / (double) TimeValue.NANOSECONDS_PER_HOUR);
        return switch (operation) {
            case MULTIPLY -> MoneyActions.amountCombined(hours, rate.amount(), operation);
            case DIVIDE -> MoneyActions.amountCombined(rate.amount(), hours, operation);
            default -> throw notRelatedToATime(operation);
        };
    }

    private static Value aTimeAgainstAProportion(
            Value left, DecimalValue portion, Operation operation) {

        if (operation != Operation.MULTIPLY) {
            throw notRelatedToATime(operation);
        }
        return TimeValue.ofNanoseconds((long) (nanosecondsOf(left) * portion.quantity()));
    }

    private static Value aNumberAgainstATime(
            Value left, TimeValue right, Operation operation) {

        boolean allowed = switch (operation) {
            case ADD, MULTIPLY -> true;
            case SUBTRACT -> left instanceof IntegerValue;
            case DIVIDE, REMAINDER, MODULO -> false;
        };
        if (!allowed) {
            throw notRelatedToATime(operation);
        }
        if (operation == Operation.SUBTRACT) {
            return addedInWholeNanoseconds(left, right, operation);
        }
        return timeCombined(right, left, operation);
    }

    private static Value addedInWholeNanoseconds(
            Value left, Value right, Operation operation) {

        long ours = wholeNanosecondsOf(left);
        long theirs = wholeNanosecondsOf(right);
        return TimeValue.ofNanoseconds(withinWhatADurationHolds(switch (operation) {
            case ADD -> ours + theirs;
            case SUBTRACT -> ours - theirs;
            default -> {
                requireNonZero(theirs);
                yield operation == Operation.REMAINDER
                        ? ours % theirs
                        : Math.floorMod(ours, theirs);
            }
        }));
    }

    private static double nanosecondsOf(Value value) {
        return value instanceof TimeValue time
                ? time.nanoseconds()
                : Comparison.asDouble(value) * TimeValue.NANOSECONDS_PER_SECOND;
    }

    private static long withinWhatADurationHolds(long nanoseconds) {
        if (nanoseconds < -TimeValue.LONGEST || nanoseconds > TimeValue.LONGEST) {
            throw Raised.of(EvaluationFailure.TYPE_LIMIT, DatatypeValue.of(Datatype.TIME));
        }
        return nanoseconds;
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


    static long dayNumberOf(DateValue date) {
        return LocalDate.of(date.year(), date.month(), date.day()).toEpochDay();
    }

    static long wholeNanosecondsOf(Value value) {
        if (value instanceof TimeValue time) {
            return time.nanoseconds();
        }
        if (value instanceof IntegerValue seconds) {
            return seconds.magnitude() * TimeValue.NANOSECONDS_PER_SECOND;
        }
        return Math.round(Comparison.asDouble(value) * TimeValue.NANOSECONDS_PER_SECOND);
    }

    static void requireNonZero(double divisor) {
        if (divisor == 0.0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    static double firstHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.x() : Comparison.asDouble(value);
    }

    static double secondHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.y() : Comparison.asDouble(value);
    }

    static double roundedHalfAwayFromZero(double amount) {
        return amount < 0 ? -Math.round(-amount) : Math.round(amount);
    }

    static Raised notRelated(Value left, Value right) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(left.datatype().literalSpelling()),
                WordValue.of(right.datatype().literalSpelling()));
    }

    @FunctionalInterface
    interface OctetWork {
        long against(long octet, double amount, boolean fractional);
    }

    static Value octetByOctet(Value left, Value right, OctetWork work) {
        refuseATimeBesideATuple(left, right);
        if (!(left instanceof TupleValue ours)) {
            throw Raised.cannotUse(left, "tuple arithmetic");
        }
        TupleValue theirs = right instanceof TupleValue tuple ? tuple : null;
        if (theirs == null && !Comparison.isNumeric(right)) {
            throw Raised.cannotUse(right, "tuple arithmetic");
        }
        int width = theirs == null
                ? ours.segmentCount()
                : Math.max(ours.segmentCount(), theirs.segmentCount());
        boolean fractional = right.datatype() == Datatype.DECIMAL
                || right.datatype() == Datatype.PERCENT;
        double amount = theirs == null ? Comparison.asDouble(right) : 0;

        int[] answer = new int[width];
        for (int at = 1; at <= width; at++) {
            long worked = work.against(ours.octetAt(at),
                    theirs == null ? amount : theirs.octetAt(at), fractional);
            answer[at - 1] = (int) Math.max(0, Math.min(255, worked));
        }
        return TupleValue.of(answer);
    }

    private static void refuseATimeBesideATuple(Value left, Value right) {
        if (left instanceof TimeValue || right instanceof TimeValue) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    DatatypeValue.of(Datatype.TIME),
                    DatatypeValue.of(Datatype.TUPLE));
        }
    }
}
