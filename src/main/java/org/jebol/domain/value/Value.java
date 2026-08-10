package org.jebol.domain.value;

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
        UnsetValue,
        NoneValue,
        LogicValue,
        IntegerValue,
        DecimalValue,
        MoneyValue,
        CharacterValue,
        PairValue,
        TupleValue,
        TimeValue,
        DateValue,
        SeriesValue,
        WordValue,
        DatatypeValue,
        TypesetValue,
        NativeValue,
        FunctionValue,
        OperatorValue,
        ObjectValue,
        PortValue,
        MapValue,
        BitsetValue,
        ErrorValue,
        JavaObjectValue {

    /** The datatype this value reports to {@code type?}. */
    Datatype datatype();

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
}
