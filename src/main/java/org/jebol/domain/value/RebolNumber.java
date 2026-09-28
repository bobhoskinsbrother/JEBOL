package org.jebol.domain.value;

public sealed interface RebolNumber extends Value permits IntegerValue, DecimalValue {

    Value combinedWithANumber(Value right, ArithmeticOperation operation);

    boolean mayLoseATime(ArithmeticOperation operation);

    boolean mayMeetADate();

    @Override
    default Value arithmetic(Value right, ArithmeticOperation operation) {
        return switch (right) {
            case IntegerValue ignored -> combinedWithANumber(right, operation);
            case DecimalValue ignored -> combinedWithANumber(right, operation);
            case CharacterValue ignored -> combinedWithANumber(right, operation);
            case MoneyValue amount -> asMoneyInTheCurrencyOf(amount).orElseThrow()
                    .arithmetic(amount, operation);
            case TimeValue span
                    when operation.isCommutative() || mayLoseATime(operation) ->
                    new TimeActions(span).takenBy(this, operation);
            case DateValue moment
                    when operation.isCommutative() && mayMeetADate() ->
                    new DateArithmetic(moment).takenBy(this, operation);
            case PairValue point when operation.isCommutative() ->
                    point.arithmetic(this, operation);
            case TupleValue octets when operation.isCommutative() ->
                    octets.arithmetic(this, operation);
            case VectorValue vector when operation.isCommutative() ->
                    VectorMath.done(this, vector, operation);
            default -> refuseTheArithmetic(right);
        };
    }
}
