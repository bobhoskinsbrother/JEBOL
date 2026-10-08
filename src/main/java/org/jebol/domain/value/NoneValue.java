package org.jebol.domain.value;

public record NoneValue() implements Value {

    public static final Datatype TYPE = new Datatype("none") {

        @Override
        protected void refuseToBuildSomethingOutOfNothing(Value from) {
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            return none();
        }
    };

    private static final NoneValue INSTANCE = new NoneValue();

    private static final long NO_POSITION_AT_ALL = 0;

    public static NoneValue none() {
        return INSTANCE;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public boolean atTail() {
        return true;
    }

    @Override
    public boolean isTruthy() {
        return false;
    }

    @Override
    public long asPosition() {
        return NO_POSITION_AT_ALL;
    }

    @Override
    public String toString() {
        return "none";
    }
}
