package org.jebol.domain.value;

public sealed interface RebolNumber extends Value permits IntegerValue, AnyDecimalValue {

    Value combineWithNumber(Value right, ArithmeticOperation operation);

    boolean canDeductTime(ArithmeticOperation operation);

    boolean canBeUsedToCalculateDate();

    @Override
    default Value calculate(Value right, ArithmeticOperation operation) {
        return switch (right) {
            case IntegerValue ignored -> combineWithNumber(right, operation);
            case AnyDecimalValue ignored -> combineWithNumber(right, operation);
            case CharacterValue ignored -> combineWithNumber(right, operation);
            case MoneyValue amount -> asMoneyUsingCurrency(amount).orElseThrow()
                    .calculate(amount, operation);
            case TimeValue span
                    when operation.isCommutative() || canDeductTime(operation) ->
                    new TimeActions(span).combineFromLeft(this, operation);
            case DateValue moment
                    when operation.isCommutative() && canBeUsedToCalculateDate() ->
                    new DateArithmetic(moment).takeFrom(this, operation);
            case PairValue point when operation.isCommutative() ->
                    point.calculate(this, operation);
            case TupleValue octets when operation.isCommutative() ->
                    octets.calculate(this, operation);
            case VectorValue vector when operation.isCommutative() ->
                    VectorMath.calculate(this, vector, operation);
            default -> refuseTheArithmetic(right);
        };
    }
}
