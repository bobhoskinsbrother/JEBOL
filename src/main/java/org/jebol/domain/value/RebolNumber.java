package org.jebol.domain.value;

public sealed interface RebolNumber extends Value permits IntegerValue, AnyDecimalValue {

    Value combineWithANumber(Value right, ArithmeticOperation operation);

    boolean mayLoseATime(ArithmeticOperation operation);

    boolean mayMeetADate();

    @Override
    default Value calculate(Value right, ArithmeticOperation operation) {
        return switch (right) {
            case IntegerValue ignored -> combineWithANumber(right, operation);
            case AnyDecimalValue ignored -> combineWithANumber(right, operation);
            case CharacterValue ignored -> combineWithANumber(right, operation);
            case MoneyValue amount -> asMoneyInTheCurrencyOf(amount).orElseThrow()
                    .calculate(amount, operation);
            case TimeValue span
                    when operation.isCommutative() || mayLoseATime(operation) ->
                    new TimeActions(span).combineFromTheLeft(this, operation);
            case DateValue moment
                    when operation.isCommutative() && mayMeetADate() ->
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
