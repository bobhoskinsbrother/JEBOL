package org.jebol.domain.eval;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyFunctionValue;
import org.jebol.domain.value.BlockValue;
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
    public Value make(Datatype datatype, Value specification) {
        return datatype.makeFrom(specification, this);
    }

    @Override
    public Value makeAFunction(AnyBlockValue spec, AnyBlockValue body) {
        return bindAFunction(spec, body);
    }

    @Override
    public Value makeObjectFrom(ObjectValue prototype, BlockValue body) {
        throw Raised.cannotUseTheAction(body, "make");
    }

    @Override
    public Value mergeAnObject(ObjectValue prototype, ObjectValue other) {
        throw Raised.cannotUseTheAction(other, "make");
    }

    @Override
    public Value makeFunctionFrom(AnyFunctionValue prototype, AnyBlockValue spec) {
        throw Raised.cannotUseTheAction(spec, "make");
    }

    @Override
    public Value makeCommandFrom(AnyBlockValue definition) {
        throw Raised.cannotUseTheAction(definition, "make");
    }

    @Override
    public Value makeStructFrom(StructValue prototype, Value spec) {
        throw Raised.cannotUseTheAction(spec, "make");
    }

    @Override
    public ObjectValue evaluateAnObject(AnyBlockValue body) {
        throw Raised.cannotUseTheAction(body, "make");
    }

    @Override
    public FunctionValue bindAFunction(AnyBlockValue spec, AnyBlockValue body) {
        return Binder.bindTheFunctionsBody(spec, body, Context.root());
    }

    @Override
    public Value applyASystemFunction(String name, Value argument) {
        throw Raised.cannotUseTheAction(argument, "make");
    }

    @Override
    public Value readASimpleValue(Value piece) {
        return piece;
    }

    @Override
    public ErrorValue speakHere(ErrorValue error) {
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
    public Optional<List<Value>> readTheValuesFrom(String source) {
        return Transcoder.transcode(source).values().map(AnyBlockValue::remaining);
    }

    @Override
    public AnyBlockValue readSource(String source) {
        TranscodeResult read = Transcoder.transcode(source, this);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return read.values().orElseThrow();
    }
}
