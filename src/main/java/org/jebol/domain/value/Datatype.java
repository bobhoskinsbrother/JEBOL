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
        return Catalogue.DATATYPES.positionOf(this);
    }

    public int numberTheCGivesIt() {
        return position() - 1;
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public Datatype theDatatypeItStandsFor() {
        return this;
    }

    @Override
    public Value make(Value spec, Maker maker) {
        return madeFrom(spec, maker);
    }

    public Value madeFrom(Value spec, Maker maker) {
        refuseToBuildSomethingOutOfNothing(spec);
        return built(Conversion.MAKE, spec, maker);
    }

    public Value convertedFrom(Value value, Maker maker) {
        refuseToBuildSomethingOutOfNothing(value);
        return built(Conversion.TO, value, maker);
    }

    protected Value built(Conversion asking, Value from, Maker maker) {
        throw refusing(from);
    }

    protected void refuseToBuildSomethingOutOfNothing(Value from) {
        if (from instanceof NoneValue) {
            throw refusing(from);
        }
    }

    public Value as(Value value) {
        if (value.datatype() == this) {
            return value;
        }
        throw Raised.of(EvaluationFailure.NOT_SAME_CLASS, value.datatype(), this);
    }

    public Value constructedFrom(List<Value> contents, Construction construction) {
        return construction.madeOf(this, theSpecificationIn(contents));
    }

    protected Value theSpecificationIn(List<Value> contents) {
        return contents.size() == 1 ? contents.getFirst() : BlockValue.block(contents);
    }

    protected void refuseMoreRoomThanFits(double asked, int bytesAnItemTakes) {
        long theMostItemsThatFit = Integer.MAX_VALUE / bytesAnItemTakes - 1;
        if (asked > theMostItemsThatFit) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    protected Value whatTheHostHadRoomFor(Supplier<Value> allocating) {
        try {
            return allocating.get();
        } catch (OutOfMemoryError nothingLeftToGive) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    public Raised refusing(Value given) {
        return Raised.of(EvaluationFailure.BAD_MAKE_ARG, this, given);
    }

    protected Raised refusingConstruction(List<Value> contents) {
        return Raised.of(EvaluationFailure.MALCONSTRUCT, BlockValue.block(contents));
    }

    @Override
    public Value bitwise(Value right, BitwiseOperation operation) {
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
        protected Value built(Conversion asking, Value from, Maker maker) {
            if (!(from instanceof AnyWordValue word) || !word.spelling().endsWith("!")) {
                throw refusing(from);
            }
            return Catalogue.DATATYPES.named(word.spelling())
                    .<Value>map(found -> found)
                    .orElseThrow(() -> refusing(from));
        }
    }
}
