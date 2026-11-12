package org.jebol.domain.value;

public record EndValue() implements Value {

    public static final Datatype TYPE = new Datatype("end") {
    };

    private static final EndValue INSTANCE = new EndValue();

    public static EndValue end() {
        return INSTANCE;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public String toString() {
        return "end";
    }
}
