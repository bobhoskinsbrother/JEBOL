package org.jebol.domain.eval;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * The one comparison every comparison native reaches, ported from
 * {@code Compare_Values} in {@code src/core/n-math.c} and the {@code CT_}
 * function of each datatype it dispatches to.
 *
 * <p>Ten natives, six questions, one answer. The question is carried as a
 * {@link Strictness} and it decides three separate things: whether two
 * different datatypes may be brought together at all, how far two decimals
 * may drift apart and still count as one number, and whether a pairing that
 * cannot be brought together answers false or refuses to answer.
 *
 * <p>That last split is the one worth stating twice. Asking whether a
 * character equals a string answers false; asking whether it is below one
 * raises {@code invalid-compare}. Both go through the same failed coercion
 * and part company on one line of the C.
 *
 * <p>Only two of the four ordering natives ask a question of their own.
 * {@code >} asks {@link Strictness#GREATER} and {@code <=} negates the same
 * answer; {@code >=} asks {@link Strictness#GREATER_OR_EQUAL} and {@code <}
 * negates it. So {@code a < b} is worked out as "not (a >= b)", which is why
 * {@code <} raises on the pairings {@code >} raises on.
 */
public final class Comparison {

    private Comparison() {
    }

    /**
     * How strict a comparison is, as the C's {@code strictness} argument.
     *
     * <p>The numbers are Rebol's own and are load-bearing rather than
     * decorative: the C tests {@code strictness > 1} to decide whether to
     * coerce at all and {@code strictness < 0} to decide whether a failed
     * coercion raises, so the order of these is the behaviour.
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

        /** The two ordering questions, which are the only ones that can refuse. */
        boolean isAboutOrder() {
            return mode < 0;
        }

        /**
         * The two strictest, which answer false for two datatypes rather than
         * bringing them together. {@code strictness > 1} in the C.
         */
        boolean mindsTheDatatype() {
            return mode > 1;
        }
    }

    /**
     * Datatypes whose {@code CT_} function ends in {@code return -1} rather
     * than answering the ordering question, which back in
     * {@code Compare_Values} becomes {@code invalid-compare}.
     *
     * <p>Read from the typeclass column of {@code src/boot/types.reb}, which
     * names the {@code CT_} function each datatype uses, and then from those
     * functions. An error is an object as far as this table is concerned, and
     * so is a module and a port.
     */
    private static final Set<Datatype> REFUSE_TO_BE_ORDERED = Set.of(
            Datatype.UNSET, Datatype.END, Datatype.NONE, Datatype.LOGIC,
            Datatype.BITSET, Datatype.MAP, Datatype.TYPESET,
            Datatype.OBJECT, Datatype.MODULE, Datatype.ERROR, Datatype.PORT,
            Datatype.TASK, Datatype.FRAME, Datatype.IMAGE,
            Datatype.NATIVE, Datatype.FUNCTION, Datatype.OP,
            Datatype.ACTION, Datatype.CLOSURE, Datatype.COMMAND,
            Datatype.JAVA_OBJECT);

    /** The three numbers a time will meet. A money is not among them. */
    private static final Set<Datatype> MEETS_A_TIME =
            Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT);

    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    /**
     * Whether the comparison holds at this strictness.
     *
     * <p>The shape of {@code Compare_Values}: bring the two to one datatype,
     * then ask that datatype's own question. Everything hard is in the
     * bringing together.
     */
    public static boolean holds(Value left, Value right, Strictness strictness) {
        Value first = left;
        Value second = right;
        if (left.datatype() != right.datatype()) {
            if (strictness.mindsTheDatatype()) {
                return false;
            }
            Optional<Value[]> brought = broughtTogether(left, right);
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

    /**
     * The coercion table, one branch per case label in the C's switch on the
     * left value's datatype. Empty where the switch falls through, which is
     * where the caller decides between false and a refusal.
     *
     * <p>The table is not symmetric and must not be made so. A character
     * against an integer takes the character's branch and folds both sides'
     * case; an integer against a character takes the integer's branch and
     * folds nothing. {@code #"A" = 97} is true and {@code 97 = #"A"} is
     * false, and both were confirmed by running them.
     */
    private static Optional<Value[]> broughtTogether(Value left, Value right) {
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
                // The word labels share one test and so do the string ones,
                // which is why a set-word equals a word and a file equals a
                // string of the same letters.
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
            case MONEY -> both(asMoney(left.magnitude(), (MoneyValue) right), right);
            // A character needs no widening: the comparison reads its code
            // point where it would read an integer's magnitude, so the
            // integer's own branch answers about the two numbers.
            case CHAR -> both(left, IntegerValue.of(((CharacterValue) right).codepoint()));
            case TIME -> both(DecimalValue.of(left.magnitude()), asSeconds((TimeValue) right));
            default -> Optional.empty();
        };
    }

    private static Optional<Value[]> fromADecimal(
            DecimalValue left, Value right, Datatype theirs) {

        return switch (theirs) {
            case INTEGER -> both(left, DecimalValue.of(((IntegerValue) right).magnitude()));
            // A money pulls the decimal its way rather than the other way
            // about, which is the only coercion in the table that moves the
            // left value when the right one could have moved instead.
            case MONEY -> both(asMoney(left.quantity(), (MoneyValue) right), right);
            case DECIMAL, PERCENT -> both(left, right);
            case TIME -> both(left, asSeconds((TimeValue) right));
            default -> Optional.empty();
        };
    }

    private static Optional<Value[]> fromAMoney(
            MoneyValue left, Value right, Datatype theirs) {

        return switch (theirs) {
            case INTEGER -> both(left, asMoney(((IntegerValue) right).magnitude(), left));
            case DECIMAL, PERCENT -> both(left, asMoney(((DecimalValue) right).quantity(), left));
            // No time, deliberately. The money branch names three datatypes
            // and the time branch names three, and neither names the other,
            // so `equal? 0:0:1 $1` is false and `$1 < 0:0:2` refuses.
            default -> Optional.empty();
        };
    }

    private static Optional<Value[]> fromATime(TimeValue left, Value right, Datatype theirs) {
        if (!MEETS_A_TIME.contains(theirs)) {
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

    /**
     * A number as a money, carrying the currency of the money it is about to
     * meet.
     *
     * <p>Rebol's {@code deci} holds no currency at all, so a comparison
     * cannot see one. Taking the other side's designator is how a value with
     * no opinion about currency avoids being made to disagree with one that
     * has: {@code USD$1 = 1} is true.
     */
    private static MoneyValue asMoney(double amount, MoneyValue meeting) {
        return new MoneyValue(BigDecimal.valueOf(amount), meeting.currency());
    }

    private static MoneyValue asMoney(long amount, MoneyValue meeting) {
        return new MoneyValue(BigDecimal.valueOf(amount), meeting.currency());
    }

    private static DecimalValue asSeconds(TimeValue time) {
        return DecimalValue.of((double) time.nanoseconds() / NANOSECONDS_PER_SECOND);
    }

    /** The {@code Compare_Types[]} dispatch, once both sides are one datatype. */
    private static boolean atOneDatatype(Value left, Value right, Strictness strictness) {
        return switch (strictness) {
            case EQUAL -> equalValues(left, right, STEPS_ALLOWED_BETWEEN_DECIMALS, APPROVED);
            case EQUIV -> equalValues(left, right, 0, APPROVED);
            case STRICT_EQUAL -> strictlyEqual(left, right);
            case SAME -> isSameValue(left, right);
            case GREATER_OR_EQUAL -> ordersAs(left, right, ordering -> ordering >= 0);
            case GREATER -> ordersAs(left, right, ordering -> ordering > 0);
        };
    }

    // ---- the CT_ functions -----------------------------------------------

    /**
     * How many steps of the floating point representation {@code =} allows
     * two decimals to differ by and still count as the same number.
     *
     * <p>Twenty-one, from {@code CT_Decimal}, where the comment beside it
     * says: "there was 10, but 21 is the minimum to have:
     * (100% // 3% = 1%) == true". An allowance of ten passes every other
     * decimal assertion in Rebol's suite and fails that one, which is how the
     * wrong number survives being tested.
     */
    private static final long STEPS_ALLOWED_BETWEEN_DECIMALS = 21;

    /**
     * Whether the coercion table has already approved this pairing of
     * datatypes, which decides whether the walk below may cross one.
     *
     * <p>Two comparison functions with different rules, and Rebol runs both.
     * {@code Compare_Values} approves five pairings before it compares
     * anything -- the numbers with each other, a time with the non-money
     * numbers, a character with an integer, any word with any word, any
     * string with any string. {@code Cmp_Value} in {@code f-series.c}
     * approves only two: the numbers with each other, and the words with each
     * other. Its one line says so:
     * {@code if ((ANY_NUMBER(s) && ANY_NUMBER(t)) || (ANY_WORD(s) && ANY_WORD(t)))}.
     *
     * <p>So the same two values get different answers depending on which one
     * asked. {@code equal? "a" %a} is true and {@code equal? ["a"] [%a]} is
     * false; {@code equal? 0:0:1 1} is true and {@code equal? [0:0:1] [1]} is
     * false. FIND, SELECT, SWITCH and SORT walk items and get the strict
     * answer; the comparison natives get the loose one. Both confirmed by
     * running them.
     */
    private static final boolean APPROVED = true;

    private static final boolean UNAPPROVED = false;

    /** The four datatypes {@code ANY_NUMBER} covers. A time is not one. */
    private static final Set<Datatype> ANY_NUMBER =
            Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT, Datatype.MONEY);

    /**
     * REBOL's {@code =} as {@code Cmp_Value} asks it: equal, folding case,
     * and a number may meet a number and a word a word.
     *
     * <p>This is the comparison every series function uses -- FIND, SELECT,
     * SWITCH, SORT, UNIQUE and the object field walk. It is stricter about
     * datatypes than the comparison natives are, and deliberately so; see
     * {@link #APPROVED}.
     *
     * <p>All the way down. Folding case for a bare string while comparing a
     * nested one strictly is the kind of split nobody writes on purpose, and
     * nothing catches it until a block holds a string.
     */
    public static boolean looselyEqual(Value left, Value right) {
        return equalValues(left, right, STEPS_ALLOWED_BETWEEN_DECIMALS, UNAPPROVED);
    }

    /**
     * The same comparison, with the decimal allowance chosen by the caller.
     *
     * <p>EQUIV? sits between the two other comparisons and needs zero here:
     * it folds case and lets an integer meet a decimal, exactly as {@code =}
     * does, and then insists on the bits. Passing the allowance down rather
     * than writing a second walk keeps the two from drifting, which matters
     * most inside a block, where the difference would only show once
     * something nested a decimal.
     */
    public static boolean looselyEqual(Value left, Value right, long stepsAllowed) {
        return equalValues(left, right, stepsAllowed, UNAPPROVED);
    }

    private static boolean equalValues(
            Value left, Value right, long stepsAllowed, boolean approved) {

        // A character folds case for this question and not for the ordering
        // one, which is the opposite way round from what the code points
        // suggest. Everything built on this comparison inherits the folding,
        // which is what makes `switch #"a"` take a branch written with a
        // capital.
        if (left instanceof CharacterValue && right instanceof CharacterValue) {
            return foldedCodepointsAgree(left, right);
        }
        if (left instanceof CharacterValue && approved) {
            // Only the comparison natives get here, with an integer on the
            // right that the table let through unwidened. Both sides go
            // through LO_CASE, which is why `#"A" = 97` is true.
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
            // Whether the two are the same number, minding neither the sign
            // of a zero nor the hardware's refusal to call a NaN itself.
            // Both are what the strict == exists to mind, and ordering()
            // cannot answer this: it puts NaN below everything, and it puts
            // -0.0 below 0.0.
            double first = asDouble(left);
            double second = asDouble(right);
            if (Double.isNaN(first) || Double.isNaN(second)) {
                // almost_equal answers `max_diff > 0` for two NaNs, so = says
                // they are one number and EQUIV? says they are not. That is
                // the whole of the difference between the two on this case.
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
        if (left instanceof ObjectValue leftObject && right instanceof ObjectValue rightObject) {
            return sameFields(leftObject, rightObject, stepsAllowed);
        }
        if (left instanceof BlockValue leftBlock && right instanceof BlockValue rightBlock) {
            List<Value> theirs = rightBlock.remaining();
            List<Value> ours = leftBlock.remaining();
            if (ours.size() != theirs.size() || left.datatype() != right.datatype()) {
                return false;
            }
            for (int at = 0; at < ours.size(); at++) {
                // The items go through Cmp_Value however the block was
                // reached, which is why `equal? ["a"] [%a]` is false even
                // though `equal? "a" %a` is true.
                if (!equalValues(ours.get(at), theirs.get(at), stepsAllowed, UNAPPROVED)) {
                    return false;
                }
            }
            return true;
        }
        return left.datatype() == right.datatype() && left.equals(right);
    }

    private static boolean foldedCodepointsAgree(Value left, Value right) {
        return Character.toLowerCase(codepointOf(left)) == Character.toLowerCase(codepointOf(right));
    }

    /**
     * Whether the two are numbers this comparison will put side by side.
     *
     * <p>A time is a number to {@code Compare_Values} and not to
     * {@code Cmp_Value}, which is the whole of why {@code equal? 0:0:1 1} is
     * true and {@code equal? [0:0:1] [1]} is false.
     */
    private static boolean numbersMeet(Value left, Value right, boolean approved) {
        if (approved) {
            return isNumeric(left) && isNumeric(right);
        }
        return ANY_NUMBER.contains(left.datatype()) && ANY_NUMBER.contains(right.datatype())
                || left.datatype() == Datatype.TIME && right.datatype() == Datatype.TIME;
    }

    /**
     * Whether two objects declare the same fields holding equal values.
     *
     * <p>{@code Equal_Object} compares each field with {@code Cmp_Value},
     * which coerces across the four number datatypes, so an object holding
     * the integer 1 equals one holding {@code $1}. It does not go through
     * {@code Compare_Values} -- the C carries a comment saying it ought to --
     * and the difference shows on exactly this case, because {@code Cmp_Value}
     * reads a money's first eight bytes as a whole number and gets the right
     * answer for {@code $1} by luck rather than by rule.
     *
     * <p>So this walks the fields and asks the ordinary comparison about each,
     * which is what the C means and gets right for one value in a thousand.
     */
    private static boolean sameFields(
            ObjectValue left, ObjectValue right, long stepsAllowed) {

        return fieldsAgree(left, right,
                (ours, theirs) -> looselyEqual(ours, theirs, stepsAllowed));
    }

    /**
     * The field walk again, minding the datatype. {@code Equal_Object} is
     * handed {@code mode > 1} as its case flag, and {@code Cmp_Value} reads
     * that flag as "stop coercing".
     */
    private static boolean strictFields(ObjectValue left, ObjectValue right) {
        return fieldsAgree(left, right, Comparison::identicallyEqual);
    }

    /**
     * The names in order, then the values pairwise.
     *
     * <p>The hidden fields are counted and not compared, because a hidden
     * field has no name and no value to compare and is still there. Two
     * objects with the same visible fields and different hidden ones are not
     * equal.
     */
    private static boolean fieldsAgree(
            ObjectValue left, ObjectValue right, java.util.function.BiPredicate<Value, Value> agree) {

        Map<String, Value> ours = left.context().fieldsExcludingSelf();
        Map<String, Value> theirs = right.context().fieldsExcludingSelf();
        if (!ours.keySet().equals(theirs.keySet())
                || left.context().fieldCount() != right.context().fieldCount()) {
            return false;
        }
        return ours.entrySet().stream()
                .allMatch(field -> agree.test(field.getValue(), theirs.get(field.getKey())));
    }

    /**
     * Strict equality, which for decimals means the identical bits.
     *
     * <p>Three comparisons that disagree, all read from {@code CT_Decimal}.
     * The loose = asks whether two values are the same number, so both zeroes
     * are equal and so are two NaNs. This compares the bits and excludes a
     * NaN by name, so the zeroes are not equal and neither are the NaNs.
     * SAME? compares the bits with no exclusion, which makes two NaNs the
     * same value and the two zeroes different ones.
     *
     * <p>No two of the three agree on both cases, which is why each has its
     * own answer written down rather than being derived from another.
     */
    public static boolean strictlyEqual(Value left, Value right) {
        if (left.datatype() != right.datatype()) {
            return false;
        }
        if (left instanceof DecimalValue first && right instanceof DecimalValue second) {
            return !Double.isNaN(first.quantity())
                    && Double.compare(first.quantity(), second.quantity()) == 0;
        }
        // CT_Pair answers the same question at every mode from nought up: it
        // subtracts the halves rather than comparing their bits, so a
        // negative zero half equals a zero one under all four of =, ==,
        // EQUIV? and SAME?. A decimal is the opposite -- the three part
        // company there -- so the pair case cannot be left to the decimal
        // one to answer.
        if (left instanceof PairValue && right instanceof PairValue) {
            return ordering(left, right) == 0;
        }
        // Two tuples holding the same octets differ when one was written
        // longer than the other, which nothing else can see: 1.2.3 and
        // 1.2.3.0 are equal and are not strictly equal. CT_Tuple asks about
        // the length from mode 2 upwards and not below it.
        if (left instanceof TupleValue first && right instanceof TupleValue second) {
            return first.equals(second) && first.segmentCount() == second.segmentCount();
        }
        if (left instanceof ObjectValue first && right instanceof ObjectValue second) {
            return sameFields(first, second, 0) && strictFields(first, second);
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

    /** Whether two values are the same value, minding the datatype exactly. */
    public static boolean identicallyEqual(Value left, Value right) {
        return left.datatype() == right.datatype() && strictlyEqual(left, right);
    }

    /** Whether two values are one thing rather than two equal things. */
    public static boolean isSameValue(Value left, Value right) {
        if (left.datatype() != right.datatype()) {
            return false;
        }
        if (left instanceof SeriesValue first && right instanceof SeriesValue second) {
            return first.sharesStorageWith(second) && first.index() == second.index();
        }
        if (left instanceof WordValue first && right instanceof WordValue second) {
            return first.isSameAs(second);
        }
        // Two decimals are the same value when their bits are identical,
        // which makes two NaNs the same and the two zeroes different -- the
        // reverse of the loose = on both counts.
        if (left instanceof DecimalValue first && right instanceof DecimalValue second) {
            return Double.doubleToRawLongBits(first.quantity())
                    == Double.doubleToRawLongBits(second.quantity());
        }
        // Two objects are the same object only when they are one object.
        // ObjectValue.equals compares fields, which is what EQUAL? wants and
        // the opposite of what this asks: a copy holding identical fields is
        // equal and is not the same.
        if (left instanceof ObjectValue first && right instanceof ObjectValue second) {
            return first.context() == second.context();
        }
        // The same question as == for a tuple, because CT_Tuple treats every
        // mode above 1 alike.
        if (left instanceof TupleValue first && right instanceof TupleValue second) {
            return first.equals(second) && first.segmentCount() == second.segmentCount();
        }
        // And CT_Pair treats every mode from nought up alike, so SAME? on two
        // pairs asks about the halves rather than about the values.
        if (left instanceof PairValue && right instanceof PairValue) {
            return ordering(left, right) == 0;
        }
        return left.equals(right);
    }

    /**
     * Whether two values of one datatype stand in the asked-for order.
     *
     * <p>A datatype whose {@code CT_} function has no ordering branch refuses
     * the question rather than answering it, which is the second way into
     * {@code invalid-compare} and the only one that does not go through a
     * failed coercion. Two objects reach it, and so do two logic values.
     */
    public static boolean ordersAs(Value left, Value right, IntPredicate wanted) {
        if (REFUSE_TO_BE_ORDERED.contains(left.datatype())
                || REFUSE_TO_BE_ORDERED.contains(right.datatype())) {
            throw refusal(left, right);
        }
        return wanted.test(ordering(left, right));
    }

    /**
     * Where the left value sits relative to the right one.
     *
     * <p>A pair orders on its first half and breaks the tie on its second,
     * which {@code Cmp_Pair} does in two lines and which makes {@code <} a
     * total order over pairs after all: {@code 1x2 < 2x1} is true because the
     * x halves decide it before the y halves are looked at. Comparing both
     * halves and requiring both to agree gives false, and was what JEBOL did
     * until the C was read.
     */
    private static int ordering(Value left, Value right) {
        if (left instanceof PairValue leftPair && right instanceof PairValue rightPair) {
            int acrossTheX = signOfTheDifference(leftPair.x(), rightPair.x());
            return acrossTheX != 0
                    ? acrossTheX
                    : signOfTheDifference(leftPair.y(), rightPair.y());
        }
        // A character orders by code point, folding nothing, so `#"a"` is
        // above `#"B"` however the two compare for equality. The ordering
        // path in CT_Char sits below the `mode >= 0` block and never reaches
        // LO_CASE, so this and the equality above disagree on purpose.
        if (left instanceof CharacterValue) {
            return Integer.compare(codepointOf(left), codepointOf(right));
        }
        if (left instanceof IntegerValue leftInteger && right instanceof IntegerValue rightInteger) {
            return Long.compare(leftInteger.magnitude(), rightInteger.magnitude());
        }
        if (isNumeric(left) && isNumeric(right)) {
            double first = asDouble(left);
            double second = asDouble(right);
            // A comparison against NaN orders it below, whichever side it is
            // on, so `1.#NaN < 1` and `1 < 1.#NaN` are both true and neither
            // > holds. That is what a real R3 answers; the JVM's own compare
            // sorts NaN above everything instead.
            if (Double.isNaN(first) || Double.isNaN(second)) {
                return -1;
            }
            return Double.compare(first, second);
        }
        // The operators ask a different question from SORT and must not share
        // its answer, which is why SORT keeps its own rule for NaN above.
        return compareForSorting(left, right, false);
    }

    /**
     * Which way one half sits against another, as {@code Cmp_Pair} works it
     * out: subtract, then take the sign.
     *
     * <p>Not {@code Double.compare}, and the difference is not academic.
     * Subtracting makes a negative zero equal to a zero, so
     * {@code -32767x-32767 % -32767} equals {@code 0x0} although it molds as
     * {@code -0x-0}. {@code Double.compare} puts -0.0 below 0.0 and answers
     * that they are two different pairs.
     *
     * <p>It also makes two infinite halves equal, because the difference is a
     * NaN and a NaN is neither above nor below zero. That is what lets
     * {@code p = p} hold for a pair built out of 1e300.
     */
    private static int signOfTheDifference(double half, double other) {
        double difference = half - other;
        return difference > 0.0 ? 1 : (difference < 0.0 ? -1 : 0);
    }

    /**
     * The default order for SORT: numbers by size, everything else by its
     * text.
     *
     * <p>Case is folded unless {@code /case} was asked for, so "a" and "A"
     * land together rather than every capital coming first.
     */
    public static int compareForSorting(Value left, Value right, boolean mindingCase) {
        if (isNumeric(left) && isNumeric(right)) {
            // Sorting needs a real ordering, and comparison does not provide
            // one. `1.#NaN < 1` and `1 < 1.#NaN` are both true in REBOL, so
            // ordering() answers "less" whichever way round it is asked --
            // fine for the operator and useless for a sort, which needs the
            // two answers to disagree.
            //
            // Confirmed against a real R3: sorting puts every NaN last and
            // treats two of them as equal.
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

    /**
     * Whether two decimals are the same number as far as {@code =} cares.
     *
     * <p>Counted in steps of the representation rather than as a fixed
     * amount, so the allowance scales with the size of the numbers. A fixed
     * tolerance is wrong in two directions at once: far too coarse near zero
     * and far too fine out at a million.
     *
     * <p>Without this, `(0.1 + 0.2) = 0.3` is false and so is
     * `0.5 = cosine 60`, and every test that computes a decimal and compares
     * it with a written-out one fails for what looks like an arithmetic bug.
     *
     * <p>The bit patterns are shifted into one running order first, because
     * the sign bit alone would put the negatives in reverse and leave a gulf
     * between -0.0 and 0.0 that REBOL says is not there. Collapsing the two
     * zeroes is why `equiv? 0.0 -0.0` is true at an allowance of nothing.
     */
    private static boolean nearlyTheSameNumber(double first, double second, long stepsAllowed) {
        long steps = inRunningOrder(first) - inRunningOrder(second);
        return Math.abs(steps) <= stepsAllowed;
    }

    /** A double's bits renumbered so that ordering them orders the numbers. */
    private static long inRunningOrder(double number) {
        long bits = Double.doubleToRawLongBits(number);
        return bits < 0 ? Long.MIN_VALUE - bits : bits;
    }

    /**
     * A code point, read from a character or from an integer standing for one.
     *
     * <p>The C has no conversion here at all: {@code VAL_CHAR} reads the
     * low bits of whichever value it is handed, so an integer beside a
     * character is simply read as a code point. Truncating the same way keeps
     * a number outside Unicode's range from failing where the C would answer.
     */
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
            // A time counts as its seconds, which is what `to integer! 1:00`
            // gives. Widening the parameter checks to accept a time without
            // widening this let it past the door and refused it inside, which
            // is the same error from a less useful place.
            case TimeValue time -> (double) time.nanoseconds() / NANOSECONDS_PER_SECOND;
            case DecimalValue decimal -> decimal.quantity();
            case MoneyValue money -> money.amount().doubleValue();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
    }
}
