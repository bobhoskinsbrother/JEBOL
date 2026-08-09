package org.jebol.domain.value;

/** The value of {@code number!}, {@code series!} and their siblings. */
public record TypesetValue(Typeset represents) implements Value {

    public static TypesetValue of(Typeset represents) {
        return new TypesetValue(represents);
    }

    @Override
    public Datatype datatype() {
        return Datatype.TYPESET;
    }

    @Override
    public String toString() {
        return represents.literalSpelling();
    }
}
