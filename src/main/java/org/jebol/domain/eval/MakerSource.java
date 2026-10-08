package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyFunctionValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.Maker;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StructSpec;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

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
        public Value makeObjectFrom(ObjectValue prototype, Value spec) {
            throw Raised.cannotUseTheAction(spec, "make");
        }

        @Override
        public Value makeFunctionFrom(AnyFunctionValue prototype, AnyBlockValue spec) {
            throw Raised.cannotUseTheAction(spec, "make");
        }

        @Override
        public Value makeStructFrom(StructValue prototype, Value spec) {
            throw Raised.cannotUseTheAction(spec, "make");
        }

        @Override
        public ObjectValue objectEvaluatedFrom(AnyBlockValue body) {
            throw Raised.cannotUseTheAction(body, "make");
        }

        @Override
        public FunctionValue functionBoundFrom(AnyBlockValue spec, AnyBlockValue body) {
            throw Raised.cannotUseTheAction(spec, "make");
        }

        @Override
        public Value systemFunctionApplied(String name, Value argument) {
            throw Raised.cannotUseTheAction(argument, "make");
        }

        @Override
        public Value simpleValueOf(Value piece) {
            return piece;
        }

        @Override
        public ErrorValue spokenHere(ErrorValue error) {
            return error;
        }

        @Override
        public StructSpec.LayoutRegistry structLayouts() {
            return layoutName -> Optional.empty();
        }

        @Override
        public Optional<List<Value>> valuesReadFrom(String source) {
            return Optional.empty();
        }

        @Override
        public AnyBlockValue sourceRead(String source) {
            throw Raised.cannotUseTheAction(StringValue.of(source), "to");
        }
    }
}
