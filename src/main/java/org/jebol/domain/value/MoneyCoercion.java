package org.jebol.domain.value;

public final class MoneyCoercion {

    public boolean meetsMoney(Value one, Value other) {
        return (one instanceof MoneyValue && becomesMoney(other))
                || (other instanceof MoneyValue && becomesMoney(one));
    }

    private boolean becomesMoney(Value value) {
        return value instanceof MoneyValue || value instanceof IntegerValue || value instanceof AnyDecimalValue;
    }

    public Deci asDeci(Value value) {
        return switch (value) {
            case MoneyValue money -> money.asDeci();
            case IntegerValue(long magnitude) -> new Deci(magnitude);
            case AnyDecimalValue decimal -> new Deci(decimal.quantity());
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG, value);
        };
    }

    public int compareOrder(Value left, Value right) {
        Deci ours = asDeci(left);
        Deci theirs = asDeci(right);
        if (ours.isEqualTo(theirs)) {
            return 0;
        }
        return theirs.isLesserOrEqualTo(ours) ? 1 : -1;
    }

    public int compareSortingOrder(Value left, Value right) {
        boolean cmpValueComparesAmounts = right instanceof MoneyValue
                && (left instanceof MoneyValue || left instanceof AnyDecimalValue);
        if (!cmpValueComparesAmounts) {
            return left.datatype().compareTo(right.datatype());
        }
        Deci ours = asDeci(left);
        Deci theirs = asDeci(right);
        if (ours.isEqualTo(theirs)) {
            return 0;
        }
        return ours.isLesserOrEqualTo(theirs) ? -1 : 1;
    }
}
