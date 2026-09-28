package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.util.Optional;

/**
 * A REBOL value.
 *
 * <p>Sealed, so that every place which dispatches on datatype can be checked
 * for exhaustiveness by the compiler rather than by inspection. The variants
 * mirror {@code spec/values.allium} one for one.
 *
 * <p>Conditional truth is defined here rather than on each variant that cares,
 * because every native asking "is this true?" must get the same answer. Only
 * {@link NoneValue} and a false {@link LogicValue} are false. Zero is true, an
 * empty string is true, an empty block is true.
 */
public sealed interface Value permits
        RebolNumber,
        UnsetValue,
        NoneValue,
        LogicValue,
        IntegerValue,
        DecimalValue,
        MoneyValue,
        CharacterValue,
        PairValue,
        TupleValue,
        EventValue,
        HandleValue,
        TimeValue,
        DateValue,
        RebolSeries,
        WordValue,
        DatatypeValue,
        TypesetValue,
        NativeValue,
        FunctionValue,
        OperatorValue,
        ObjectValue,
        PortValue,
        ModuleValue,
        TaskValue,
        MapValue,
        BitsetValue,
        ErrorValue,
        StructValue,
        JavaObjectValue {

    /** The datatype this value reports to {@code type?}. */
    Datatype datatype();

    default Value bitwise(Value right, BitwiseOperation operation) {
        throw Raised.of(EvaluationFailure.EXPECT_ARG, this);
    }

    default Value arithmetic(Value right, ArithmeticOperation operation) {
        throw Raised.cannotUse(this, operation.spelling());
    }

    default Value refuseTheArithmetic(Value right) {
        throw Raised.notRelated(this, right);
    }

    default Value asASetWith(Value other, MembersKept keeping, boolean mindingCase) {
        throw Raised.cannotUse(this, "a set operation");
    }

    default Optional<Value[]> broughtTogetherWith(Value other) {
        return Optional.empty();
    }

    default boolean equalTo(Value other, Sameness how) {
        if (Numbers.theyMayBeCompared(this, other, how)) {
            return Numbers.areEqual(this, other, how);
        }
        return datatype() == other.datatype() && equals(other);
    }

    default Optional<Value[]> both(Value mine, Value theirs) {
        return Optional.of(new Value[] {mine, theirs});
    }

    default Optional<IntegerValue> asWholeNumber() {
        return Optional.empty();
    }

    default Optional<DecimalValue> asDecimalNumber() {
        return Optional.empty();
    }

    default Optional<MoneyValue> asMoneyInTheCurrencyOf(MoneyValue other) {
        return Optional.empty();
    }

    /**
     * Whether a conditional native treats this value as true.
     *
     * <p>Everything is true except none and a false logic. That is
     * {@code IS_FALSE} in Rebol's {@code sys-value.h}, and it never asks
     * whether a value is unset -- thus an unset is true here, and
     * `if () [1]` answers 1 rather than failing.
     */
    default boolean isTruthy() {
        return true;
    }

    default Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return java.util.Optional.empty();
    }

    default Value asItStands(Datatype wanted, double quantity) {
        return wanted == Datatype.PERCENT
                ? DecimalValue.percent(quantity)
                : DecimalValue.of(quantity);
    }

    default Value inHundredths(Datatype wanted, double quantity) {
        return wanted == Datatype.PERCENT
                ? DecimalValue.percent(quantity / 100.0)
                : DecimalValue.of(quantity);
    }
}
