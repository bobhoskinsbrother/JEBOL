package org.jebol.domain.value;

/** The value of {@code integer!}, {@code string!} and their siblings. */
public record DatatypeValue(Datatype represents) implements Value {

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
