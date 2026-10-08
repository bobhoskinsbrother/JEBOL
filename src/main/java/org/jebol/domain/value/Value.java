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
        AnyWordValue,
        DatatypeValue,
        TypesetValue,
        DeclaresParameters,
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

    Datatype datatype();

    default Value make(Value spec, Maker maker) {
        return maker.makeAnotherFrom(datatype(), spec);
    }

    default Optional<Context> fieldsAsAContext() {
        return Optional.empty();
    }

    default List<Value> items() {
        throw Raised.of(EvaluationFailure.CANNOT_USE,
                "cannot walk " + datatype().literalSpelling() + " value");
    }

    default void refuseToBeWrittenWhenItNamesSelf() {
    }

    default byte[] asOctets() {
        return Molder.form(this).getBytes(StandardCharsets.UTF_8);
    }

    default Value copied(boolean deeply) {
        return copied(deeply, Copying.WHAT_A_DEEP_COPY_COPIES);
    }

    default Value copied(boolean deeply, Set<Datatype> kinds) {
        return this;
    }

    default Value copiedAsAMember(boolean deeply, Set<Datatype> kinds) {
        if (!kinds.contains(datatype())) {
            return this;
        }
        return copied(deeply, deeply ? kinds : Copying.NOTHING_INSIDE);
    }

    default long asPosition() {
        throw Raised.of(EvaluationFailure.INVALID_ARG,
                "a position is a number, not " + datatype().literalSpelling());
    }

    default String runTogether() {
        return Molder.form(this);
    }

    default boolean atTail() {
        throw Raised.cannotUseTheAction(this, "tail?");
    }

    default String writtenInHex(HexWidth width) {
        throw Raised.cannotUse(this, "to-hex");
    }

    default Value trimmed(Trimming trimming) {
        throw Raised.of(EvaluationFailure.CANNOT_USE,
                SetWordValue.of("trim"), DatatypeValue.of(datatype()));
    }

    default Value picked(int oneBasedPosition) {
        throw Raised.cannotUseTheAction(this, "pick");
    }

    default Value pickedBy(Value selector) {
        if (selector instanceof IntegerValue(long magnitude)) {
            return picked((int) magnitude);
        }
        throw Raised.cannotUseTheAction(this, "pick");
    }

    default Value reflected(AnyWordValue field) {
        return NoneValue.none();
    }

    default boolean isAnyObject() {
        return fieldsAsAContext().isPresent();
    }

    default Value fieldValue(String field) {
        return fieldsAsAContext()
                .filter(fields -> fields.holds(field))
                .map(fields -> fields.ownSlotFor(field).value())
                .orElseGet(NoneValue::none);
    }

    default boolean declaresTheField(String field) {
        return fieldsAsAContext().map(fields -> fields.holds(field)).orElse(false);
    }

    default boolean declaresAFieldFindCanReachBy(Value wanted) {
        return wanted instanceof AnyWordValue word
                && word.datatype() == Datatype.WORD
                && !word.canonical().equals("self")
                && declaresTheField(word.canonical());
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

    default Value bitwise(Value right, BitwiseOperation operation) {
        throw Raised.of(EvaluationFailure.EXPECT_ARG, this);
    }

    default Value arithmetic(Value right, ArithmeticOperation operation) {
        throw Raised.cannotUseTheAction(this, operation.spelling());
    }

    default Value absolute() {
        throw Raised.cannotUseTheAction(this, "absolute");
    }

    default Value negated() {
        return IntegerValue.of(0).arithmetic(this, new Subtract());
    }

    default long asCountOfRepetitions() {
        throw Raised.of(EvaluationFailure.INVALID_TYPE,
                Molder.mold(this) + " is not a count of repetitions");
    }

    default BlockValue repeatedInABlock(Value times) {
        return BlockValue.block(List.of(this)).repeatedInABlock(times);
    }

    default Value randomised(RandomDraw draw) {
        throw Raised.cannotUseTheAction(this, "random");
    }

    default Value pickedAtRandom(RandomDraw draw) {
        return randomised(draw);
    }

    default long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        throw Raised.cannotUseTheAction(this, "random");
    }

    default Value heldBetween(Value lowest, Value highest) {
        return this;
    }

    default Value partWayTo(Value destination, double fraction) {
        if (!Numbers.isANumber(this)) {
            throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(this));
        }
        if (!Numbers.isANumber(destination)) {
            throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(destination));
        }
        double from = Numbers.quantityOf(this);
        return DecimalValue.of(
                from + (Numbers.quantityOf(destination) - from) * fraction);
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

    default boolean isTruthy() {
        return true;
    }

    default Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return Optional.empty();
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
