package org.jebol.domain.eval;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyFunctionValue;
import org.jebol.domain.value.Construction;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.Maker;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StructSpec;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;

public final class MakingWithoutEvaluating implements Maker, Construction {

    private final MapValue registeredStructLayouts;

    public MakingWithoutEvaluating(MapValue registeredStructLayouts) {
        this.registeredStructLayouts = registeredStructLayouts;
    }

    @Override
    public Value madeOf(Datatype datatype, Value specification) {
        return datatype.madeFrom(specification, this);
    }

    @Override
    public Value functionMadeFrom(AnyBlockValue spec, AnyBlockValue body) {
        return functionBoundFrom(spec, body);
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
        return Binder.functionWithItsBodyBound(spec, body, Context.root());
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
        return layoutName -> registeredStructLayouts.select(WordValue.of(layoutName))
                instanceof AnyBlockValue layout
                ? Optional.of(layout)
                : Optional.empty();
    }

    @Override
    public Optional<List<Value>> valuesReadFrom(String source) {
        return Transcoder.transcode(source).values().map(AnyBlockValue::remaining);
    }

    @Override
    public AnyBlockValue sourceRead(String source) {
        TranscodeResult read = Transcoder.transcode(source, this);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return read.values().orElseThrow();
    }
}
