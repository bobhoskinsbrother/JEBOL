package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * The one comparison every comparison native reaches. Ten natives, six
 * questions, one answer: the question is carried as a {@link Strictness}, which
 * decides whether two different datatypes may be brought together at all, how
 * far two decimals may drift apart and still count as one number, and whether a
 * pairing that cannot be brought together answers false or raises
 * {@code invalid-compare}.
 */
public final class Comparison {

    private Comparison() {
    }

    /**
     * How strict a comparison is. The numbers are Rebol's own and are
     * load-bearing: above one stops the coercion, below zero makes a failed
     * coercion raise.
     */
    public enum Strictness {
        /** {@code =}, EQUAL?, and NOT-EQUAL? negated. */
        EQUAL(0),
        /** EQUIV?, and NOT-EQUIV? negated. */
        EQUIV(1),
        /** {@code ==}, STRICT-EQUAL?, and the two {@code !==} spellings negated. */
        STRICT_EQUAL(2),
        /** {@code =?} and SAME?. */
        SAME(3),
        /** {@code >=} and GREATER-OR-EQUAL?, and {@code <} and LESSER? negated. */
        GREATER_OR_EQUAL(-1),
        /** {@code >} and GREATER?, and {@code <=} and LESSER-OR-EQUAL? negated. */
        GREATER(-2);

        private final int mode;

        Strictness(int mode) {
            this.mode = mode;
        }

        boolean isAboutOrder() {
            return mode < 0;
        }

        boolean mindsTheDatatype() {
            return mode > 1;
        }
    }

    private static final Set<Datatype> REFUSE_TO_BE_ORDERED = Set.of(
            Datatype.UNSET, Datatype.END, Datatype.NONE, Datatype.LOGIC,
            Datatype.BITSET, Datatype.MAP, Datatype.TYPESET,
            Datatype.OBJECT, Datatype.MODULE, Datatype.ERROR, Datatype.PORT,
            Datatype.TASK, Datatype.FRAME, Datatype.IMAGE,
            Datatype.NATIVE, Datatype.FUNCTION, Datatype.OP,
            Datatype.ACTION, Datatype.CLOSURE, Datatype.COMMAND,
            Datatype.JAVA_OBJECT);

    private static final Set<Datatype> NUMBERS_A_TIME_WILL_MEET_WHICH_EXCLUDE_MONEY =
            Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT);

    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    /** Whether the comparison holds at this strictness. */
    public static boolean holds(Value left, Value right, Strictness strictness) {
        Value first = left;
        Value second = right;
        if (left.datatype() != right.datatype()) {
            if (strictness.mindsTheDatatype()) {
                return false;
            }
            Optional<Value[]> brought =
                    broughtTogetherBySwitchingOnTheLeftAlone(left, right);
            if (brought.isEmpty()) {
                if (!strictness.isAboutOrder()) {
                    return false;
                }
                throw refusal(left, right);
            }
            first = brought.get()[0];
            second = brought.get()[1];
        }
        return atOneDatatype(first, second, strictness);
    }

    private static Raised refusal(Value left, Value right) {
        return Raised.of(EvaluationFailure.INVALID_COMPARE,
                "cannot compare " + left.datatype().literalSpelling()
                        + " with " + right.datatype().literalSpelling());
    }

    private static Optional<Value[]> broughtTogetherBySwitchingOnTheLeftAlone(
            Value left, Value right) {
        Datatype theirs = right.datatype();
        return switch (left.datatype()) {
            case INTEGER -> fromAnInteger((IntegerValue) left, right, theirs);
            case DECIMAL, PERCENT -> fromADecimal((DecimalValue) left, right, theirs);
            case MONEY -> fromAMoney((MoneyValue) left, right, theirs);
            case CHAR -> theirs == Datatype.INTEGER
                    ? both(left, right)
                    : Optional.empty();
            case TIME -> fromATime((TimeValue) left, right, theirs);
            default -> {
                if (left.datatype().isAnyWord() && theirs.isAnyWord()) {
                    yield both(left, right);
                }
                if (left.datatype().isAnyString() && theirs.isAnyString()) {
                    yield both(left, right);
                }
                yield Optional.empty();
            }
        };
    }

    private static Optional<Value[]> fromAnInteger(
            IntegerValue left, Value right, Datatype theirs) {

        return switch (theirs) {
            case DECIMAL, PERCENT -> both(DecimalValue.of(left.magnitude()), right);
            case MONEY -> both(asMoneyInTheCurrencyItIsMeeting(
                    left.magnitude(), (MoneyValue) right), right);
            case CHAR -> both(left, IntegerValue.of(((CharacterValue) right).codepoint()));
            case TIME -> both(DecimalValue.of(left.magnitude()), asSeconds((TimeValue) right));
            default -> Optional.empty();
        };
    }

    private static Optional<Value[]> fromADecimal(
            DecimalValue left, Value right, Datatype theirs) {

        return switch (theirs) {
            case INTEGER -> both(left, DecimalValue.of(((IntegerValue) right).magnitude()));
            case MONEY -> both(asMoneyInTheCurrencyItIsMeeting(
                    left.quantity(), (MoneyValue) right), right);
            case DECIMAL, PERCENT -> both(left, right);
            case TIME -> both(left, asSeconds((TimeValue) right));
            default -> Optional.empty();
        };
    }

    private static Optional<Value[]> fromAMoney(
            MoneyValue left, Value right, Datatype theirs) {

        return switch (theirs) {
            case INTEGER -> both(left, asMoneyInTheCurrencyItIsMeeting(
                    ((IntegerValue) right).magnitude(), left));
            case DECIMAL, PERCENT -> both(left, asMoneyInTheCurrencyItIsMeeting(
                    ((DecimalValue) right).quantity(), left));
            default -> Optional.empty();
        };
    }

    private static Optional<Value[]> fromATime(TimeValue left, Value right, Datatype theirs) {
        if (!NUMBERS_A_TIME_WILL_MEET_WHICH_EXCLUDE_MONEY.contains(theirs)) {
            return Optional.empty();
        }
        Value theirNumber = theirs == Datatype.INTEGER
                ? DecimalValue.of(((IntegerValue) right).magnitude())
                : right;
        return both(asSeconds(left), theirNumber);
    }

    private static Optional<Value[]> both(Value left, Value right) {
        return Optional.of(new Value[] {left, right});
    }

    private static MoneyValue asMoneyInTheCurrencyItIsMeeting(
            double amount, MoneyValue meeting) {
        return new MoneyValue(BigDecimal.valueOf(amount), meeting.currency());
    }

    private static MoneyValue asMoneyInTheCurrencyItIsMeeting(
            long amount, MoneyValue meeting) {
        return new MoneyValue(BigDecimal.valueOf(amount), meeting.currency());
    }

    private static DecimalValue asSeconds(TimeValue time) {
        return DecimalValue.of((double) time.nanoseconds() / NANOSECONDS_PER_SECOND);
    }

    private static boolean atOneDatatype(Value left, Value right, Strictness strictness) {
        return switch (strictness) {
            case EQUAL -> equalValues(left, right, STEPS_ALLOWED_BETWEEN_DECIMALS,
                    THE_COERCION_TABLE_APPROVED_THIS_PAIRING);
            case EQUIV -> equalValues(left, right, 0,
                    THE_COERCION_TABLE_APPROVED_THIS_PAIRING);
            case STRICT_EQUAL -> strictlyEqual(left, right);
            case SAME -> isSameValue(left, right);
            case GREATER_OR_EQUAL -> ordersAs(left, right, ordering -> ordering >= 0);
            case GREATER -> ordersAs(left, right, ordering -> ordering > 0);
        };
    }

    private static final long STEPS_ALLOWED_BETWEEN_DECIMALS = 21;

    private static final long STEPS_ALLOWED_INSIDE_A_SERIES = 10;

    private static final boolean THE_COERCION_TABLE_APPROVED_THIS_PAIRING = true;

    private static final boolean IT_DID_NOT = false;

    private static final Set<Datatype> ANY_NUMBER_WHICH_EXCLUDES_A_TIME =
            Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT, Datatype.MONEY);

    /**
     * REBOL's {@code =} as the series functions ask it: equal, folding case,
     * and a number may meet a number and a word a word. This is what FIND,
     * SELECT, SWITCH, SORT, UNIQUE and the object field walk use, and it is
     * stricter about datatypes than the comparison natives are.
     */
    public static boolean looselyEqual(Value left, Value right) {
        return equalValues(left, right, STEPS_ALLOWED_INSIDE_A_SERIES, IT_DID_NOT);
    }

    /**
     * The same comparison, with the decimal allowance chosen by the caller.
     * EQUIV? needs zero: it folds case and lets an integer meet a decimal
     * exactly as {@code =} does, and then insists on the bits.
     */
    public static boolean looselyEqual(Value left, Value right, long stepsAllowed) {
        return equalValues(left, right, stepsAllowed, IT_DID_NOT);
    }

    private static boolean equalValues(
            Value left, Value right, long stepsAllowed, boolean approved) {

        if (left instanceof HandleValue first && right instanceof HandleValue second) {
            return first.isEqualHandleTo(second);
        }
        if (left instanceof VectorValue first && right instanceof VectorValue second) {
            return orderingOfVectors(first, second) == 0;
        }
        if (left instanceof StructValue first && right instanceof StructValue second) {
            return first.holdsTheSameAs(second);
        }

        if (left instanceof CharacterValue && right instanceof CharacterValue) {
            return foldedCodepointsAgree(left, right);
        }
        if (left instanceof CharacterValue && approved) {
            return foldedCodepointsAgree(left, right);
        }
        if (left instanceof StringValue leftText && right instanceof StringValue rightText) {
            return (approved || leftText.datatype() == rightText.datatype())
                    && leftText.equalsIgnoringCase(rightText);
        }
        if (left instanceof WordValue leftWord && right instanceof WordValue rightWord) {
            return leftWord.namesSameAs(rightWord);
        }
        if (numbersMeet(left, right, approved)) {
            double first = asDouble(left);
            double second = asDouble(right);
            if (Double.isNaN(first) || Double.isNaN(second)) {
                return Double.isNaN(first) && Double.isNaN(second) && stepsAllowed > 0;
            }
            if (left instanceof DecimalValue || right instanceof DecimalValue) {
                return nearlyTheSameNumber(first, second, stepsAllowed);
            }
            return ordering(left, right) == 0;
        }
        if (left instanceof PairValue && right instanceof PairValue) {
            return ordering(left, right) == 0;
        }
        if (left instanceof DateValue && right instanceof DateValue) {
            return ordering(left, right) == 0;
        }
        if (left instanceof ObjectValue leftObject && right instanceof ObjectValue rightObject) {
            return sameFields(leftObject, rightObject);
        }
        if (left instanceof BlockValue leftBlock && right instanceof BlockValue rightBlock) {
            List<Value> theirs = rightBlock.remaining();
            List<Value> ours = leftBlock.remaining();
            if (ours.size() != theirs.size() || left.datatype() != right.datatype()) {
                return false;
            }
            for (int at = 0; at < ours.size(); at++) {
                if (!looselyEqual(ours.get(at), theirs.get(at))) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof MapValue ours && right instanceof MapValue theirs) {
            return sameKeysAgainstLooselyEqualValues(ours, theirs);
        }
        return left.datatype() == right.datatype() && left.equals(right);
    }

    private static boolean sameKeysAgainstLooselyEqualValues(
            MapValue ours, MapValue theirs) {
        if (ours.pairCount() != theirs.pairCount()) {
            return false;
        }
        for (Value key : ours.keys()) {
            if (!theirs.holds(key, MINDING_CASE)
                    || !looselyEqual(ours.select(key, MINDING_CASE),
                            theirs.select(key, MINDING_CASE))) {
                return false;
            }
        }
        return true;
    }

    private static final boolean MINDING_CASE = true;

    private static boolean foldedCodepointsAgree(Value left, Value right) {
        return Character.toLowerCase(codepointOf(left)) == Character.toLowerCase(codepointOf(right));
    }

    private static boolean numbersMeet(Value left, Value right, boolean approved) {
        if (approved) {
            return isNumeric(left) && isNumeric(right);
        }
        return ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(left.datatype())
                && ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(right.datatype())
                || left.datatype() == Datatype.TIME && right.datatype() == Datatype.TIME;
    }

    private static boolean sameFields(ObjectValue left, ObjectValue right) {
        return fieldsAgree(left, right, Comparison::looselyEqual);
    }

    private static boolean strictFields(ObjectValue left, ObjectValue right) {
        return fieldsAgree(left, right, Comparison::identicallyEqual);
    }

    private static boolean fieldsAgree(
            ObjectValue left, ObjectValue right, java.util.function.BiPredicate<Value, Value> agree) {

        Map<String, Value> ours = left.context().fieldsExcludingSelf();
        Map<String, Value> theirs = right.context().fieldsExcludingSelf();
        if (!ours.keySet().equals(theirs.keySet())
                || theyHideDifferentNumbersOfFields(left, right)) {
            return false;
        }
        return ours.entrySet().stream()
                .allMatch(field -> agree.test(field.getValue(), theirs.get(field.getKey())));
    }

    private static boolean theyHideDifferentNumbersOfFields(
            ObjectValue left, ObjectValue right) {
        return left.context().fieldCount() != right.context().fieldCount();
    }

    /**
     * Strict equality, which for decimals means the identical bits with a NaN
     * excluded by name -- so the two zeroes are not equal and neither are two
     * NaNs. Loose equality and SAME? each answer both of those differently.
     */
    public static boolean strictlyEqual(Value left, Value right) {
        if (left.datatype() != right.datatype()) {
            return false;
        }
        if (left instanceof DecimalValue first && right instanceof DecimalValue second) {
            return !Double.isNaN(first.quantity())
                    && Double.compare(first.quantity(), second.quantity()) == 0;
        }
        if (left instanceof PairValue && right instanceof PairValue) {
            return ordering(left, right) == 0;
        }
        if (left instanceof TupleValue first && right instanceof TupleValue second) {
            return first.equals(second) && first.segmentCount() == second.segmentCount();
        }
        if (left instanceof DateValue first && right instanceof DateValue second) {
            return sameDateBitsAndTime(first, second);
        }
        if (left instanceof ObjectValue first && right instanceof ObjectValue second) {
            return strictFields(first, second);
        }
        if (left instanceof BlockValue first && right instanceof BlockValue second) {
            List<Value> ours = first.remaining();
            List<Value> theirs = second.remaining();
            if (ours.size() != theirs.size()) {
                return false;
            }
            for (int at = 0; at < ours.size(); at++) {
                if (!identicallyEqual(ours.get(at), theirs.get(at))) {
                    return false;
                }
            }
            return true;
        }
        return left.equals(right);
    }

    private static boolean sameDateBitsAndTime(DateValue first, DateValue second) {
        return first.year() == second.year()
                && first.month() == second.month()
                && first.day() == second.day()
                && first.timeOfDay().equals(second.timeOfDay())
                && aMissingZoneCountsAsZero(first).equals(aMissingZoneCountsAsZero(second));
    }

    private static Integer aMissingZoneCountsAsZero(DateValue date) {
        return date.zoneMinutes().orElse(0);
    }

    /**
     * What {@code ==} asks about the items of a block and what FIND/CASE asks
     * about the item it is looking for: minding the datatype and the case of a
     * string. It is not the same question {@code ==} asks about two values on
     * their own -- a decimal still gets the ten-step allowance here, so
     * {@code [1.0] == [1.0000000000000022]} is true where
     * {@code 1.0 == 1.0000000000000022} is false.
     */
    public static boolean identicallyEqual(Value left, Value right) {
        if (left.datatype() != right.datatype()) {
            return false;
        }
        if (left instanceof DecimalValue first && right instanceof DecimalValue second) {
            return bothAreNotANumber(first, second)
                    || nearlyTheSameNumber(first.quantity(), second.quantity(),
                            STEPS_ALLOWED_INSIDE_A_SERIES);
        }
        return strictlyEqual(left, right);
    }

    private static boolean bothAreNotANumber(DecimalValue first, DecimalValue second) {
        return Double.isNaN(first.quantity()) && Double.isNaN(second.quantity());
    }

    /**
     * Whether two values are one thing rather than two equal things. Everything
     * that holds its contents somewhere answers by where, not by what, so a map
     * is never the same value as its own copy.
     */
    public static boolean isSameValue(Value left, Value right) {
        if (left.datatype() != right.datatype()) {
            return false;
        }
        if (left instanceof HandleValue first && right instanceof HandleValue second) {
            return first.isTheSameHandleAs(second);
        }
        if (left instanceof SeriesValue first && right instanceof SeriesValue second) {
            return first.sharesStorageWith(second) && first.index() == second.index();
        }
        if (left instanceof WordValue first && right instanceof WordValue second) {
            return first.isSameAs(second);
        }
        if (left instanceof DecimalValue first && right instanceof DecimalValue second) {
            return Double.doubleToRawLongBits(first.quantity())
                    == Double.doubleToRawLongBits(second.quantity());
        }
        if (left instanceof ObjectValue first && right instanceof ObjectValue second) {
            return first.context() == second.context();
        }
        if (left instanceof MapValue || left instanceof BitsetValue) {
            return left == right;
        }
        if (left instanceof TupleValue first && right instanceof TupleValue second) {
            return first.equals(second) && first.segmentCount() == second.segmentCount();
        }
        if (left instanceof PairValue && right instanceof PairValue) {
            return ordering(left, right) == 0;
        }
        return left.equals(right);
    }

    /**
     * Whether two values of one datatype stand in the asked-for order. A
     * datatype with no ordering of its own -- an object, a logic value, a map
     * -- raises {@code invalid-compare} rather than answering false.
     */
    public static boolean ordersAs(Value left, Value right, IntPredicate wanted) {
        if (REFUSE_TO_BE_ORDERED.contains(left.datatype())
                || REFUSE_TO_BE_ORDERED.contains(right.datatype())) {
            throw refusal(left, right);
        }
        return wanted.test(ordering(left, right));
    }

    private static int ordering(Value left, Value right) {
        if (left instanceof HandleValue first && right instanceof HandleValue second) {
            return first.compareWith(second);
        }
        if (left instanceof VectorValue first && right instanceof VectorValue second) {
            return orderingOfVectors(first, second);
        }
        if (left instanceof PairValue leftPair && right instanceof PairValue rightPair) {
            int theXHalvesDecideItFirst =
                    signOfTheDifference(leftPair.x(), rightPair.x());
            return theXHalvesDecideItFirst != 0
                    ? theXHalvesDecideItFirst
                    : signOfTheDifference(leftPair.y(), rightPair.y());
        }
        if (left instanceof BlockValue leftBlock && right instanceof BlockValue rightBlock) {
            return orderingOfBlocks(leftBlock, rightBlock);
        }
        if (left instanceof CharacterValue) {
            return Integer.compare(codepointOf(left), codepointOf(right));
        }
        if (left instanceof IntegerValue leftInteger && right instanceof IntegerValue rightInteger) {
            return Long.compare(leftInteger.magnitude(), rightInteger.magnitude());
        }
        if (isNumeric(left) && isNumeric(right)) {
            double first = asDouble(left);
            double second = asDouble(right);
            if (Double.isNaN(first) || Double.isNaN(second)) {
                return -1;
            }
            return Double.compare(first, second);
        }
        return compareForSorting(left, right, false);
    }

    private static int orderingOfBlocks(BlockValue left, BlockValue right) {
        List<Value> ours = left.remaining();
        List<Value> theirs = right.remaining();
        for (int at = 0; at < ours.size(); at++) {
            if (at == theirs.size()) {
                return 1;
            }
            int theFirstItemThatTellsThemApart =
                    orderingInsideASeries(ours.get(at), theirs.get(at));
            if (theFirstItemThatTellsThemApart != 0) {
                return theFirstItemThatTellsThemApart;
            }
        }
        return theShorterBlockIsTheLesserOne(ours, theirs);
    }

    private static int theShorterBlockIsTheLesserOne(
            List<Value> ours, List<Value> theirs) {
        return ours.size() == theirs.size() ? 0 : -1;
    }

    private static int orderingInsideASeries(Value left, Value right) {
        if (!ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(left.datatype())
                || !ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(right.datatype())) {
            return compareForSorting(left, right, false);
        }
        if (left instanceof DecimalValue || right instanceof DecimalValue) {
            double first = asDouble(left);
            double second = asDouble(right);
            return nearlyTheSameNumber(first, second, STEPS_ALLOWED_INSIDE_A_SERIES)
                    ? 0
                    : Double.compare(first, second);
        }
        return ordering(left, right);
    }

    private static int orderingOfVectors(VectorValue left, VectorValue right) {
        if (left.kind().measures() != right.kind().measures()) {
            throw Raised.of(EvaluationFailure.NOT_SAME_TYPE,
                    left.kind().spelling() + " against " + right.kind().spelling());
        }
        return left.compareWith(right);
    }

    private static int signOfTheDifference(double half, double other) {
        double difference = half - other;
        return difference > 0.0 ? 1 : (difference < 0.0 ? -1 : 0);
    }

    /**
     * The default order for SORT: numbers by size, dates by the instant they
     * name, everything else by its text, with case folded unless {@code /case}
     * was asked for.
     */
    public static int compareForSorting(Value left, Value right, boolean mindingCase) {
        if (left instanceof DateValue first && right instanceof DateValue second) {
            return first.moment().compareTo(second.moment());
        }
        if (isNumeric(left) && isNumeric(right)) {
            boolean leftIsNaN = Double.isNaN(asDouble(left));
            boolean rightIsNaN = Double.isNaN(asDouble(right));
            if (leftIsNaN || rightIsNaN) {
                return leftIsNaN == rightIsNaN ? 0 : (leftIsNaN ? 1 : -1);
            }
            if (left instanceof IntegerValue first && right instanceof IntegerValue second) {
                return Long.compare(first.magnitude(), second.magnitude());
            }
            return Double.compare(asDouble(left), asDouble(right));
        }
        return mindingCase
                ? Molder.form(left).compareTo(Molder.form(right))
                : Molder.form(left).compareToIgnoreCase(Molder.form(right));
    }

    private static boolean nearlyTheSameNumber(double first, double second, long stepsAllowed) {
        long steps = inRunningOrder(first) - inRunningOrder(second);
        return Math.abs(steps) <= stepsAllowed;
    }

    private static long inRunningOrder(double number) {
        long bits = Double.doubleToRawLongBits(number);
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }

    private static int codepointOf(Value value) {
        return value instanceof CharacterValue character
                ? character.codepoint()
                : (int) ((IntegerValue) value).magnitude();
    }

    /** The four number datatypes, and a time, which counts as its seconds. */
    public static boolean isNumeric(Value value) {
        return value.datatype().isNumber()
                || value.datatype() == Datatype.MONEY
                || value.datatype() == Datatype.TIME;
    }

    /** The number this value is, for the arithmetic and the comparison alike. */
    public static double asDouble(Value value) {
        return switch (value) {
            case IntegerValue integer -> integer.magnitude();
            case TimeValue time -> (double) time.nanoseconds() / NANOSECONDS_PER_SECOND;
            case DecimalValue decimal -> decimal.quantity();
            case MoneyValue money -> money.amount().doubleValue();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }
}
