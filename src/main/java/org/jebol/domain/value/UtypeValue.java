package org.jebol.domain.value;

import java.util.List;

/**
 * A user-defined type: the value {@code make utype! [[spec] [body]]} builds.
 *
 * <p>It does nothing, which is the C's own state rather than something left
 * undone here. {@code t-utype.c}'s header says NOT IMPLEMENTED: the
 * specification is only checked for being a block, the body is collected for
 * its set-words and their values are never set, and the object the value
 * holds is {@code system/standard/utype} with every action still none. So
 * every action traps through the ordinary parameter checks, and equality is
 * false in every mode.
 *
 * <p>That last one is why {@link #equals(Object)} answers false as well.
 * {@code CT_Utype} returns FALSE whatever it is asked, so {@code u = u},
 * {@code u == u} and {@code same? u u} are all false -- this is the one value
 * in REBOL that is not equal to itself. The three comparison modes reach
 * different code, and the two that do not consult {@link #isEqualTo} fall
 * through to {@code equals}, so both have to answer false for a script to see
 * what a real R3 shows it.
 */
public record UtypeValue() implements Value {

    public static final Datatype TYPE = new UtypeDatatype();

    private static final class UtypeDatatype extends Datatype {

        UtypeDatatype() {
            super("utype");
        }

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            if (!isSpecificationAndBody(spec)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, spec);
            }
            return new UtypeValue();
        }

        @Override
        public Value convert(Value value, Maker maker) {
            throw Raised.of(EvaluationFailure.INVALID_TYPE, this);
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            throw refuseTheConstruction(contents);
        }

        private boolean isSpecificationAndBody(Value spec) {
            if (!(spec instanceof AnyBlockValue definition)) {
                return false;
            }
            List<Value> written = definition.remaining();
            return written.size() >= 2
                    && written.get(0) instanceof AnyBlockValue
                    && written.get(1) instanceof AnyBlockValue;
        }
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public boolean isEqualTo(Value other, Sameness how) {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return false;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
