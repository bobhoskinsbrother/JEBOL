package org.jebol.domain.value;

import org.jebol.domain.value.sets.MembersKept;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToLongFunction;

public sealed interface Value permits
        RebolNumber,
        UnsetValue,
        NoneValue,
        LogicValue,
        IntegerValue,
        AnyDecimalValue,
        MoneyValue,
        CharacterValue,
        PairValue,
        TupleValue,
        EventValue,
        HandleValue,
        TimeValue,
        DateValue,
        RebolSeries,
        AnyWordValue,
        Datatype,
        TypesetValue,
        AnyFunctionValue,
        ObjectValue,
        PortValue,
        ModuleValue,
        TaskValue,
        MapValue,
        BitsetValue,
        ErrorValue,
        StructValue,
        UtypeValue,
        JavaObjectValue {

    Datatype datatype();

    default Value make(Value spec, Maker maker) {
        return datatype().makeFrom(spec, maker);
    }

    default Optional<Context> fieldsAsContext() {
        return Optional.empty();
    }

    default List<Value> listTheItems() {
        throw Raised.of(EvaluationFailure.CANNOT_USE,
                "cannot walk " + datatype().literalSpelling() + " value");
    }

    default void refuseToBeWrittenWhenItReachesItsContextsOwnSelf() {
    }

    default byte[] asOctets() {
        return Molder.form(this).getBytes(StandardCharsets.UTF_8);
    }

    default Value copy(boolean deeply) {
        return copy(deeply, Copying.WHAT_A_DEEP_COPY_COPIES);
    }

    default Value copy(boolean deeply, Set<Datatype> kinds) {
        return this;
    }

    default Value copyAsMember(boolean deeply, Set<Datatype> kinds) {
        if (!kinds.contains(datatype())) {
            return this;
        }
        return copy(deeply, deeply ? kinds : Copying.NOTHING_INSIDE);
    }

    default long asPosition() {
        throw Raised.of(EvaluationFailure.INVALID_ARG,
                "a position is a number, not " + datatype().literalSpelling());
    }

    default String runTogether() {
        return Molder.form(this);
    }

    default boolean atTail() {
        throw Raised.cannotUseAction(this, "tail?");
    }

    default String writeInHex(HexWidth width) {
        throw Raised.cannotUse(this, "to-hex");
    }

    default Value trim(Trimming trimming) {
        throw Raised.of(EvaluationFailure.CANNOT_USE,
                SetWordValue.of("trim"), datatype());
    }

    default Value pick(int oneBasedPosition) {
        throw Raised.cannotUseAction(this, "pick");
    }

    default Value pickBy(Value selector) {
        if (selector instanceof IntegerValue(long magnitude)) {
            return pick((int) magnitude);
        }
        throw Raised.cannotUseAction(this, "pick");
    }

    default Value reflect(AnyWordValue field) {
        return NoneValue.none();
    }

    default boolean isAnyObject() {
        return fieldsAsContext().isPresent();
    }

    default Value readFieldValue(String field) {
        return fieldsAsContext()
                .filter(fields -> fields.holds(field))
                .map(fields -> fields.ownSlotFor(field).value())
                .orElseGet(NoneValue::none);
    }

    default boolean declaresField(String field) {
        return fieldsAsContext().map(fields -> fields.holds(field)).orElse(false);
    }

    default boolean declaresFieldFindCanReachBy(Value wanted) {
        return wanted instanceof WordValue word
                && declaresField(word.canonical());
    }

    default boolean isProtected() {
        return false;
    }

    default void refuseChangeIfProtected() {
        if (isProtected()) {
            throw new ProtectedFromChange();
        }
    }

    default void requireChangeable() {
        if (isProtected()) {
            throw Raised.of(EvaluationFailure.PROTECTED);
        }
    }

    default Value combineBitwise(Value right, BitwiseOperation operation) {
        throw Raised.of(EvaluationFailure.EXPECT_ARG, this);
    }

    default Value calculate(Value right, ArithmeticOperation operation) {
        throw Raised.cannotUseAction(this, operation.spelling());
    }

    default Value makeAbsolute() {
        throw Raised.cannotUseAction(this, "absolute");
    }

    default Value negated() {
        return IntegerValue.of(0).calculate(this, new Subtract());
    }

    default long asCountOfRepetitions() {
        throw Raised.of(EvaluationFailure.INVALID_TYPE,
                Molder.mold(this) + " is not a count of repetitions");
    }

    default AnyBlockValue repeatInBlock(Value times) {
        return BlockValue.of(List.of(this)).repeatInBlock(times);
    }

    default Value randomise(RandomDraw draw) {
        throw Raised.cannotUseAction(this, "random");
    }

    default Value pickAtRandom(RandomDraw draw) {
        return randomise(draw);
    }

    default long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        throw Raised.cannotUseAction(this, "random");
    }

    default Value holdBetween(Value lowest, Value highest) {
        return this;
    }

    default Value partWayTo(Value destination, double fraction) {
        if (!Numbers.isANumber(this)) {
            throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(this));
        }
        if (!Numbers.isANumber(destination)) {
            throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(destination));
        }
        double from = Numbers.measureTheQuantityOf(this);
        return DecimalValue.of(
                from + (Numbers.measureTheQuantityOf(destination) - from) * fraction);
    }


    default Value refuseArithmetic(Value right) {
        throw Raised.ofUnrelated(this, right);
    }

    default Value asSetWith(Value other, MembersKept keeping, boolean mindingCase) {
        throw Raised.cannotUse(this, "a set operation");
    }

    default Optional<Value[]> combineWith(Value other) {
        return Optional.empty();
    }

    default boolean isEqualTo(Value other, Sameness how) {
        if (Numbers.canBeCompared(this, other, how)) {
            return Numbers.areEqual(this, other, how);
        }
        return datatype() == other.datatype() && equals(other);
    }

    default Optional<Value[]> pairUp(Value mine, Value theirs) {
        return Optional.of(new Value[] {mine, theirs});
    }

    default Optional<IntegerValue> asWholeNumber() {
        return Optional.empty();
    }

    default Optional<AnyDecimalValue> asDecimalNumber() {
        return Optional.empty();
    }

    default Optional<MoneyValue> asMoneyUsingCurrency(MoneyValue other) {
        return Optional.empty();
    }

    default boolean isTruthy() {
        return true;
    }

    default Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return Optional.empty();
    }

    default Value asItStands(AnyDecimalValue.AnyDecimalDatatype wanted, double quantity) {
        return wanted.wrap(quantity);
    }

    default Value countInHundredths(AnyDecimalValue.AnyDecimalDatatype wanted, double quantity) {
        return wanted.wrapHundredths(quantity);
    }

    default boolean isZero() {
        return false;
    }

    default Datatype findDatatypeItStandsFor() {
        return datatype();
    }
}
