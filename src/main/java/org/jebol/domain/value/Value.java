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
        ErrorValue,
        JavaObjectValue {

    /** The datatype this value reports to {@code type?}. */
    Datatype datatype();

    /**
     * Whether this value can be used as a condition at all. Only {@code unset!}
     * cannot: asking whether an absent value is true is a mistake, not a
     * question with an answer.
     */
    default boolean isConditional() {
        return true;
    }

    /**
     * Whether a conditional native treats this value as true. Everything is
     * true except {@code none} and a false {@code logic}.
     */
    default boolean isTruthy() {
        return true;
    }
}
