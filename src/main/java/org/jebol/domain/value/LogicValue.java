package org.jebol.domain.value;

/** {@code true} or {@code false}. The only value whose truth is its content. */
public record LogicValue(boolean truth) implements Value {

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
    public Datatype datatype() {
        return Datatype.LOGIC;
    }

    @Override
    public boolean isTruthy() {
        return truth;
    }

    @Override
    public String toString() {
        return truth ? "true" : "false";
    }
}
