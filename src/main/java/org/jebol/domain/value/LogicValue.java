package org.jebol.domain.value;

/** {@code true} or {@code false}. The only value whose truth is its content. */
public record LogicValue(boolean truth) implements Value {

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        return LogicValue.of(operation.onLogics(truth, aTruthFrom(right)));
    }

    private boolean aTruthFrom(Value right) {
        if (right instanceof LogicValue(boolean theirs)) {
            return theirs;
        }
        throw Raised.of(EvaluationFailure.EXPECT_VAL,
                WordValue.of("logic!"),
                WordValue.of(right.datatype().literalSpelling()));
    }


    private static final LogicValue TRUE = new LogicValue(true);
    private static final LogicValue FALSE = new LogicValue(false);

    public static LogicValue of(boolean truth) {
        return truth ? TRUE : FALSE;
    }

    public static LogicValue yes() {
        return TRUE;
    }

    public static LogicValue no() {
        return FALSE;
    }

    @Override
    public java.util.Optional<Value> asDecimal(Datatype wanted, Conversion asking) {
        return asking.builds()
                ? java.util.Optional.of(
                        asItStands(wanted, truth() ? 1.0 : 0.0))
                : java.util.Optional.empty();
    }

    @Override
    public Datatype datatype() {
        return Datatype.LOGIC;
    }

    @Override
    public boolean isTruthy() {
        return truth;
    }

    @Override
    public String toString() {
        return Boolean.toString(truth);
    }
}
