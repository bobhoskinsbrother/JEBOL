package org.jebol.domain.eval;

import org.jebol.domain.date.DateOrder;
import org.jebol.domain.value.*;

import java.util.HashSet;
import java.util.List;
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

        boolean mindsDatatype() {
            return mode > 1;
        }
    }

    private static final Set<Datatype> REFUSE_TO_BE_ORDERED = collectEverythingIn(
            TypesetValue.ANY_OBJECT, TypesetValue.ANY_FUNCTION,
            UnsetValue.TYPE, EndValue.TYPE, NoneValue.TYPE, LogicValue.TYPE,
            BitsetValue.TYPE, MapValue.TYPE, TypesetValue.TYPE,
            FrameValue.TYPE, ImageValue.TYPE, JavaObjectValue.TYPE);

    private static Set<Datatype> collectEverythingIn(
            TypesetValue first, TypesetValue second, Datatype... alsoRefusing) {

        Set<Datatype> refusing = new HashSet<>(first.members());
        refusing.addAll(second.members());
        refusing.addAll(Set.of(alsoRefusing));
        return Set.copyOf(refusing);
    }

    /** Whether the comparison holds at this strictness. */
    public static boolean holds(Value left, Value right, Strictness strictness) {
        Value first = left;
        Value second = right;
        if (left.datatype() != right.datatype()) {
            if (strictness.mindsDatatype()) {
                return false;
            }
            Optional<Value[]> brought = bringTogether(left, right);
            if (brought.isEmpty()) {
                if (!strictness.isAboutOrder()) {
                    return false;
                }
                throw refuse(left, right);
            }
            first = brought.get()[0];
            second = brought.get()[1];
        }
        return atOneDatatype(first, second, strictness);
    }

    private static Optional<Value[]> bringTogether(Value left, Value right) {
        return left.combineWith(right)
                .or(() -> right.combineWith(left).map(Comparison::turnRound));
    }

    private static Value[] turnRound(Value[] pair) {
        return new Value[] {pair[1], pair[0]};
    }

    private static Raised refuse(Value left, Value right) {
        return Raised.of(EvaluationFailure.INVALID_COMPARE,
                left.datatype(), right.datatype());
    }

    private static boolean atOneDatatype(Value left, Value right, Strictness strictness) {
        return switch (strictness) {
            case EQUAL -> areEqualValues(left, right, STEPS_ALLOWED_BETWEEN_DECIMALS,
                    THE_COERCION_TABLE_APPROVED_THIS_PAIRING);
            case EQUIV -> areEqualValues(left, right, 0,
                    THE_COERCION_TABLE_APPROVED_THIS_PAIRING);
            case STRICT_EQUAL -> areStrictlyEqual(left, right);
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
            TypesetValue.NUMBER.membersAnd(MoneyValue.TYPE);

    /**
     * REBOL's {@code =} as the series functions ask it: equal, folding case,
     * and a number may meet a number and a word a word. This is what FIND,
     * SELECT, SWITCH, SORT, UNIQUE and the object field walk use, and it is
     * stricter about datatypes than the comparison natives are.
     */
    public static boolean areLooselyEqual(Value left, Value right) {
        return areEqualValues(left, right, STEPS_ALLOWED_INSIDE_A_SERIES, IT_DID_NOT);
    }

    /**
     * The same comparison, with the decimal allowance chosen by the caller.
     * EQUIV? needs zero: it folds case and lets an integer meet a decimal
     * exactly as {@code =} does, and then insists on the bits.
     */
    public static boolean areLooselyEqual(Value left, Value right, long stepsAllowed) {
        return areEqualValues(left, right, stepsAllowed, IT_DID_NOT);
    }

    private static boolean areEqualValues(
            Value left, Value right, long stepsAllowed, boolean approved) {

        return left.isEqualTo(right, approved
                ? new Sameness(stepsAllowed, true)
                : Sameness.allowStepsInsideSeries(stepsAllowed));
    }






    private static boolean haveStrictlyEqualFields(ObjectValue left, ObjectValue right) {
        return left.agreesInEveryField(right, Comparison::areIdenticallyEqual);
    }

    /**
     * Strict equality, which for decimals means the identical bits with a NaN
     * excluded by name -- so the two zeroes are not equal and neither are two
     * NaNs. Loose equality and SAME? each answer both of those differently.
     */
    public static boolean areStrictlyEqual(Value left, Value right) {
        if (left.datatype() != right.datatype()) {
            return false;
        }
        if (left instanceof AnyDecimalValue first && right instanceof AnyDecimalValue second) {
            return !Double.isNaN(first.quantity())
                    && Double.compare(first.quantity(), second.quantity()) == 0;
        }
        if (left instanceof MoneyValue first && right instanceof MoneyValue second) {
            return first.asDeci().isEqualTo(second.asDeci());
        }
        if (left instanceof PairValue && right instanceof PairValue) {
            return compareOrder(left, right) == 0;
        }
        if (left instanceof TupleValue first && right instanceof TupleValue second) {
            return first.equals(second) && first.segmentCount() == second.segmentCount();
        }
        if (left instanceof DateValue first && right instanceof DateValue second) {
            return DateOrder.same(first, second);
        }
        if (left instanceof ObjectValue first && right instanceof ObjectValue second) {
            return haveStrictlyEqualFields(first, second);
        }
        if (left instanceof AnyBlockValue first && right instanceof AnyBlockValue second) {
            List<Value> ours = first.remaining();
            List<Value> theirs = second.remaining();
            if (ours.size() != theirs.size()) {
                return false;
            }
            for (int at = 0; at < ours.size(); at++) {
                if (!areIdenticallyEqual(ours.get(at), theirs.get(at))) {
                    return false;
                }
            }
            return true;
        }
        return left.equals(right);
    }

    /**
     * What {@code ==} asks about the items of a block and what FIND/CASE asks
     * about the item it is looking for: minding the datatype and the case of a
     * string. It is not the same question {@code ==} asks about two values on
     * their own -- a decimal still gets the ten-step allowance here, so
     * {@code [1.0] == [1.0000000000000022]} is true where
     * {@code 1.0 == 1.0000000000000022} is false.
     */
    public static boolean areIdenticallyEqual(Value left, Value right) {
        if (left.datatype() != right.datatype()) {
            return false;
        }
        if (left instanceof AnyDecimalValue first && right instanceof AnyDecimalValue second) {
            return areBothNotNumber(first, second)
                    || areNearlySameNumber(first.quantity(), second.quantity(),
                            STEPS_ALLOWED_INSIDE_A_SERIES);
        }
        return areStrictlyEqual(left, right);
    }

    private static boolean areBothNotNumber(AnyDecimalValue first, AnyDecimalValue second) {
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
            return first.isSameHandleAs(second);
        }
        if (left instanceof RebolSeries first && right instanceof RebolSeries second) {
            return first.sharesStorageWith(second) && first.index() == second.index();
        }
        if (left instanceof AnyWordValue first && right instanceof AnyWordValue second) {
            return first.isSameAs(second);
        }
        if (left instanceof AnyDecimalValue first && right instanceof AnyDecimalValue second) {
            return Double.doubleToRawLongBits(first.quantity())
                    == Double.doubleToRawLongBits(second.quantity());
        }
        if (left instanceof MoneyValue first && right instanceof MoneyValue second) {
            return first.asDeci().isSameAs(second.asDeci());
        }
        if (left instanceof ObjectValue(Context context1) && right instanceof ObjectValue(Context context)) {
            return context1 == context;
        }
        if (left instanceof MapValue || left instanceof BitsetValue) {
            return left == right;
        }
        if (left instanceof TupleValue first && right instanceof TupleValue second) {
            return first.equals(second) && first.segmentCount() == second.segmentCount();
        }
        if (left instanceof PairValue && right instanceof PairValue) {
            return compareOrder(left, right) == 0;
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
            throw refuse(left, right);
        }
        return wanted.test(compareOrder(left, right));
    }

    private static int compareOrder(Value left, Value right) {
        if (left instanceof HandleValue first && right instanceof HandleValue second) {
            return first.compareWith(second);
        }
        if (left instanceof VectorValue first && right instanceof VectorValue second) {
            return compareVectors(first, second);
        }
        if (left instanceof PairValue leftPair && right instanceof PairValue rightPair) {
            return comparePairs(leftPair, rightPair);
        }
        if (left instanceof TupleValue leftTuple && right instanceof TupleValue rightTuple) {
            return compareTuples(leftTuple, rightTuple);
        }
        if (left instanceof AnyBlockValue leftBlock && right instanceof AnyBlockValue rightBlock) {
            return compareBlocks(leftBlock, rightBlock);
        }
        if (left instanceof CharacterValue) {
            return Integer.compare(readTheCodepointOf(left), readTheCodepointOf(right));
        }
        if (left instanceof IntegerValue(long magnitude1) && right instanceof IntegerValue(long magnitude)) {
            return Long.compare(magnitude1, magnitude);
        }
        if (MONEY.meetsMoney(left, right)) {
            return MONEY.compareOrder(left, right);
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

    private static final MoneyCoercion MONEY = new MoneyCoercion();

    private static int compareBlocks(AnyBlockValue left, AnyBlockValue right) {
        List<Value> ours = left.remaining();
        List<Value> theirs = right.remaining();
        for (int at = 0; at < ours.size(); at++) {
            if (at == theirs.size()) {
                return 1;
            }
            int theFirstItemThatTellsThemApart =
                    compareInsideSeries(ours.get(at), theirs.get(at));
            if (theFirstItemThatTellsThemApart != 0) {
                return theFirstItemThatTellsThemApart;
            }
        }
        return compareByLength(ours, theirs);
    }

    private static int compareByLength(
            List<Value> ours, List<Value> theirs) {
        return ours.size() == theirs.size() ? 0 : -1;
    }

    private static int compareInsideSeries(Value left, Value right) {
        if (!ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(left.datatype())
                || !ANY_NUMBER_WHICH_EXCLUDES_A_TIME.contains(right.datatype())) {
            return compareForSorting(left, right, false);
        }
        if (left instanceof AnyDecimalValue || right instanceof AnyDecimalValue) {
            double first = asDouble(left);
            double second = asDouble(right);
            return areNearlySameNumber(first, second, STEPS_ALLOWED_INSIDE_A_SERIES)
                    ? 0
                    : Double.compare(first, second);
        }
        return compareOrder(left, right);
    }

    private static int comparePairs(PairValue left, PairValue right) {
        int theXHalvesDecideItFirst = signOfTheDifference(left.x(), right.x());
        return theXHalvesDecideItFirst != 0
                ? theXHalvesDecideItFirst
                : signOfTheDifference(left.y(), right.y());
    }

    private static int compareTuples(TupleValue left, TupleValue right) {
        int asFarAsTheLongerOfThem =
                Math.max(left.segmentCount(), right.segmentCount());
        for (int at = 1; at <= asFarAsTheLongerOfThem; at++) {
            int difference = left.readOctetAt(at) - right.readOctetAt(at);
            if (difference != 0) {
                return difference > 0 ? 1 : -1;
            }
        }
        return 0;
    }

    private static int compareVectors(VectorValue left, VectorValue right) {
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
     * name, pairs and tuples by their parts, everything else by its text, with
     * case folded unless {@code /case} was asked for.
     *
     * <p>A pair or a tuple compared by its text is the wrong answer to a
     * question nobody asked. The C has one comparison for both SORT and the
     * ordering natives, so the only right default is the one they give.
     */
    public static int compareForSorting(Value left, Value right, boolean mindingCase) {
        if (left instanceof DateValue first && right instanceof DateValue second) {
            return DateOrder.compare(first, second);
        }
        if (left instanceof PairValue first && right instanceof PairValue second) {
            return comparePairs(first, second);
        }
        if (left instanceof TupleValue first && right instanceof TupleValue second) {
            return compareTuples(first, second);
        }
        if (MONEY.meetsMoney(left, right)) {
            return MONEY.compareSortingOrder(left, right);
        }
        if (isNumeric(left) && isNumeric(right)) {
            boolean leftIsNaN = Double.isNaN(asDouble(left));
            boolean rightIsNaN = Double.isNaN(asDouble(right));
            if (leftIsNaN || rightIsNaN) {
                return leftIsNaN == rightIsNaN ? 0 : (leftIsNaN ? 1 : -1);
            }
            if (left instanceof IntegerValue(long magnitude1) && right instanceof IntegerValue(long magnitude)) {
                return Long.compare(magnitude1, magnitude);
            }
            return Double.compare(asDouble(left), asDouble(right));
        }
        return mindingCase
                ? Molder.form(left).compareTo(Molder.form(right))
                : Molder.form(left).compareToIgnoreCase(Molder.form(right));
    }

    private static boolean areNearlySameNumber(double first, double second, long stepsAllowed) {
        long steps = placeInRunningOrder(first) - placeInRunningOrder(second);
        return Math.abs(steps) <= stepsAllowed;
    }

    private static long placeInRunningOrder(double number) {
        long bits = Double.doubleToRawLongBits(number);
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }

    private static int readTheCodepointOf(Value value) {
        return value instanceof CharacterValue(int codepoint)
                ? codepoint
                : (int) ((IntegerValue) value).magnitude();
    }

    /** The four number datatypes, and a time, which counts as its seconds. */
    public static boolean isNumeric(Value value) {
        return value instanceof IntegerValue
                || value instanceof AnyDecimalValue
                || value instanceof MoneyValue
                || value instanceof TimeValue;
    }

    /** The number this value is, for the arithmetic and the comparison alike. */
    public static double asDouble(Value value) {
        return switch (value) {
            case IntegerValue integer -> integer.magnitude();
            case TimeValue time -> time.asSeconds().quantity();
            case AnyDecimalValue decimal -> decimal.quantity();
            case MoneyValue money -> money.asDeci().toDouble();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }
}
