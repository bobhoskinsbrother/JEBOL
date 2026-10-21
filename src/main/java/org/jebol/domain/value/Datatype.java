package org.jebol.domain.value;

import java.util.List;
import java.util.function.Supplier;

public abstract non-sealed class Datatype implements Value, Comparable<Datatype> {

    public static final Datatype TYPE = new TheDatatypeOfDatatypes();

    private final String spelling;

    protected Datatype(String spelling) {
        this.spelling = spelling;
    }

    public String spelling() {
        return spelling;
    }

    public String literalSpelling() {
        return spelling + "!";
    }

    public boolean belongsTo(TypesetValue typeset) {
        return typeset.holds(this);
    }

    public int position() {
        return Catalogue.DATATYPES.findThePositionOf(this);
    }

    public int numberTheCGivesIt() {
        return position() - 1;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public Datatype findTheDatatypeItStandsFor() {
        return this;
    }

    @Override
    public Value make(Value spec, Maker maker) {
        return makeFrom(spec, maker);
    }

    public Value makeFrom(Value spec, Maker maker) {
        refuseToBuildSomethingOutOfNothing(spec);
        return build(Conversion.MAKE, spec, maker);
    }

    public Value convert(Value value, Maker maker) {
        refuseToBuildSomethingOutOfNothing(value);
        return build(Conversion.TO, value, maker);
    }

    protected Value build(Conversion asking, Value from, Maker maker) {
        throw refuse(from);
    }

    protected void refuseToBuildSomethingOutOfNothing(Value from) {
        if (from instanceof NoneValue) {
            throw refuse(from);
        }
    }

    public Value as(Value value) {
        if (value.datatype() == this) {
            return value;
        }
        throw Raised.of(EvaluationFailure.NOT_SAME_CLASS, value.datatype(), this);
    }

    public Value construct(List<Value> contents, Construction construction) {
        return construction.make(this, readSpecificationIn(contents));
    }

    protected Value readSpecificationIn(List<Value> contents) {
        return contents.size() == 1 ? contents.getFirst() : BlockValue.of(contents);
    }

    protected void refuseMoreRoomThanFits(double asked, int bytesAnItemTakes) {
        long theMostItemsThatFit = Integer.MAX_VALUE / bytesAnItemTakes - 1;
        if (asked > theMostItemsThatFit) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    protected Value allocateWithinTheHost(Supplier<Value> allocating) {
        try {
            return allocating.get();
        } catch (OutOfMemoryError nothingLeftToGive) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    public Raised refuse(Value given) {
        return Raised.of(EvaluationFailure.BAD_MAKE_ARG, this, given);
    }

    protected Raised refuseTheConstruction(List<Value> contents) {
        return Raised.of(EvaluationFailure.MALCONSTRUCT, BlockValue.of(contents));
    }

    @Override
    public Value combineBitwise(Value right, BitwiseOperation operation) {
        throw Raised.cannotUse(this, "a bit operation");
    }

    @Override
    public int compareTo(Datatype other) {
        return Integer.compare(position(), other.position());
    }

    @Override
    public String toString() {
        return literalSpelling();
    }

    private static final class TheDatatypeOfDatatypes extends Datatype {

        TheDatatypeOfDatatypes() {
            super("datatype");
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            if (!(from instanceof AnyWordValue word) || !word.spelling().endsWith("!")) {
                throw refuse(from);
            }
            return Catalogue.DATATYPES.findTheDatatypeNamed(word.spelling())
                    .<Value>map(found -> found)
                    .orElseThrow(() -> refuse(from));
        }
    }
}
