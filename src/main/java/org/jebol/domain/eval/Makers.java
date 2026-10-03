package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.Making;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Value;

@FunctionalInterface
public interface Makers {

    Making makingIn(Evaluator evaluator, Context where);

    static Makers none() {
        return (evaluator, where) -> NothingIsMade.INSTANCE;
    }

    final class NothingIsMade implements Making {

        private static final NothingIsMade INSTANCE = new NothingIsMade();

        private NothingIsMade() {
        }

        @Override
        public Value made(Datatype kind, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value madeFromAValueOf(Datatype kind, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value objectLike(ObjectValue prototype, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value derivedFrom(Value function, BlockValue spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value errorFrom(Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value structLike(StructValue prototype, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value eventLike(EventValue prototype, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }
    }
}
