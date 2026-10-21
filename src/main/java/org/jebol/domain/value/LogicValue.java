package org.jebol.domain.value;

import java.util.function.ToLongFunction;

/** {@code true} or {@code false}. The only value whose truth is its content. */
public record LogicValue(boolean truth) implements Value {

    public static final Datatype TYPE = new Datatype("logic") {

        @Override
        protected void refuseToBuildSomethingOutOfNothing(Value from) {
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            return LogicValue.of(from.isTruthy()
                    && !(asking.builds() && from.isZero()));
        }
    };

    private static final long THE_SEED_FALSE_GIVES = 1L;

    @Override
    public Value randomise(RandomDraw draw) {
        return LogicValue.of((draw.next() & 1) == 1);
    }

    @Override
    public long asRandomSeed(ToLongFunction<byte[]> checksumOfTheOctets) {
        return truth ? System.nanoTime() : THE_SEED_FALSE_GIVES;
    }

    @Override
    public Value calculate(Value right, ArithmeticOperation operation) {
        throw Raised.of(EvaluationFailure.EXPECT_VAL,
                WordValue.of("logic!"),
                WordValue.of(right.datatype().literalSpelling()));
    }


    @Override
    public Value combineBitwise(Value right, BitwiseOperation operation) {
        return LogicValue.of(operation.onLogics(truth, readTruthFrom(right)));
    }

    private boolean readTruthFrom(Value right) {
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

    public static LogicValue answerNo() {
        return FALSE;
    }

    @Override
    public java.util.Optional<Value> asDecimal(AnyDecimalValue.AnyDecimalDatatype wanted, Conversion asking) {
        return asking.builds()
                ? java.util.Optional.of(
                        asItStands(wanted, truth() ? 1.0 : 0.0))
                : java.util.Optional.empty();
    }

    @Override
    public Datatype datatype() {
        return TYPE;
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
