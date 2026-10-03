package org.jebol.domain.eval;

import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.Maker;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Value;

@FunctionalInterface
public interface MakerSource {

    Maker makerFor(Evaluator evaluator, Context where);

    static MakerSource none() {
        return (evaluator, where) -> RefusingMaker.INSTANCE;
    }

    final class RefusingMaker implements Maker {

        private static final RefusingMaker INSTANCE = new RefusingMaker();

        private RefusingMaker() {
        }

        @Override
        public Value make(Datatype kind, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value makeAnotherFrom(Datatype kind, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value makeObjectFrom(ObjectValue prototype, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value makeFunctionFrom(Value function, BlockValue spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value makeErrorFrom(Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value makeStructFrom(StructValue prototype, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }

        @Override
        public Value makeEventFrom(EventValue prototype, Value spec) {
            throw Raised.cannotUse(spec, "make");
        }
    }
}
