package org.jebol.domain.value;

public record NoneValue() implements Value {

    private static final NoneValue INSTANCE = new NoneValue();

    private static final long NO_POSITION_AT_ALL = 0;

    public static NoneValue none() {
        return INSTANCE;
    }

    @Override
    public Datatype datatype() {
        return Datatype.NONE;
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
