package org.jebol.domain.value;

public final class UtypeValue {

    public static final Datatype TYPE = new Datatype("utype") {

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, spec);
        }

        @Override
        public Value convertedFrom(Value value, Maker maker) {
            throw Raised.of(EvaluationFailure.INVALID_TYPE, this);
        }
    };

    private UtypeValue() {
    }
}
