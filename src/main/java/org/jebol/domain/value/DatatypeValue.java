package org.jebol.domain.value;

/** The value of {@code integer!}, {@code string!} and their siblings. */
public record DatatypeValue(Datatype represents) implements Value {

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
        throw Raised.cannotUse(this, "a bit operation");
    }


    public static DatatypeValue of(Datatype represents) {
        return new DatatypeValue(represents);
    }

    @Override
    public Datatype datatype() {
        return Datatype.DATATYPE;
    }

    @Override
    public String toString() {
        return represents.literalSpelling();
    }
}
