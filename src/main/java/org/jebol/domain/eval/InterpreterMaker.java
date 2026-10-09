package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyFunctionValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.DefinedFunctionValue;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.Maker;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.StructSpec;
import org.jebol.domain.value.StructValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class InterpreterMaker implements Maker {

    private final Evaluator evaluator;
    private final Context context;
    private final MakingWithoutEvaluating withoutEvaluating;

    public InterpreterMaker(Evaluator evaluator, Context context,
            MakingWithoutEvaluating withoutEvaluating) {
        this.evaluator = evaluator;
        this.context = context;
        this.withoutEvaluating = withoutEvaluating;
    }

    @Override
    public ObjectValue objectEvaluatedFrom(AnyBlockValue body) {
        return evaluator.evaluatedInto(evaluator.freshObjectWithin(context), body);
    }

    @Override
    public FunctionValue functionBoundFrom(AnyBlockValue spec, AnyBlockValue body) {
        return Binder.functionWithItsBodyBound(spec, body, context);
    }

    @Override
    public Value systemFunctionApplied(String name, Value argument) {
        return evaluator.applyFunction(context.systemFunctionNamed(name), List.of(argument));
    }

    @Override
    public Value simpleValueOf(Value piece) {
        return evaluator.simpleValueOf(piece, context);
    }

    @Override
    public ErrorValue spokenHere(ErrorValue error) {
        return evaluator.spokenHere(error);
    }

    @Override
    public StructSpec.LayoutRegistry structLayouts() {
        return withoutEvaluating.structLayouts();
    }

    @Override
    public Optional<List<Value>> valuesReadFrom(String source) {
        return withoutEvaluating.valuesReadFrom(source);
    }

    @Override
    public AnyBlockValue sourceRead(String source) {
        return withoutEvaluating.sourceRead(source);
    }

    @Override
    public Value makeObjectFrom(ObjectValue prototype, BlockValue body) {
        return evaluator.evaluatedInto(aCopyOf(prototype), body);
    }

    @Override
    public Value objectMergedFrom(ObjectValue prototype, ObjectValue other) {
        return mergedObject(prototype, other);
    }

    private ObjectValue aCopyOf(ObjectValue prototype) {
        ObjectValue built = evaluator.freshObjectWithin(context);
        Context fields = built.context();
        prototype.context().slots().forEach(slot -> fields.register(
                slot.spelling(), Binder.clonedAndRebound(
                        slot.value(), Set.of(prototype.context()), fields)));
        return built;
    }

    private Value mergedObject(ObjectValue prototype, ObjectValue other) {
        Context fields = Context.childOf(context);
        prototype.context().slots()
                .forEach(slot -> fields.register(slot.spelling(), slot.value()));
        other.context().slots()
                .forEach(slot -> fields.register(slot.spelling(), slot.value()));

        ObjectValue merged = new ObjectValue(fields);
        fields.pointItsOwnSelfAt(merged);
        Set<Context> sources = Set.of(prototype.context(), other.context());
        fields.slots().forEach(slot -> fields.register(slot.spelling(),
                Binder.clonedAndRebound(slot.value(), sources, fields)));
        return merged;
    }

    @Override
    public AnyFunctionValue makeFunctionFrom(AnyFunctionValue prototype, AnyBlockValue spec) {
        List<Value> parts = spec.remaining();
        if (parts.isEmpty()) {
            return prototype;
        }
        Value first = parts.getFirst();
        Optional<AnyBlockValue> newSpecification = first instanceof AnyBlockValue given
                ? Optional.of(given)
                : Optional.empty();
        if (newSpecification.isEmpty() && !isTheStarThatMeansKeepIt(first)) {
            throw prototype.refusedAsAPrototypeFor(spec);
        }
        Optional<Value> newBody = parts.size() > 1 ? Optional.of(parts.get(1)) : Optional.empty();
        if (newBody.isPresent() && !takesTheBody(prototype, newBody.get())) {
            throw prototype.refusedAsAPrototypeFor(spec);
        }
        return switch (prototype) {
            case DefinedFunctionValue written -> Binder.functionWithItsBodyBound(
                    newSpecification.orElseGet(() -> asABlock(written.spec())),
                    newBody.map(this::asABlock).orElseGet(() -> asABlock(written.body())),
                    written.closedOver());
            case NativeValue built -> newSpecification
                    .<AnyFunctionValue>map(given -> built.derivedWith(given, FunctionSpec.parametersIn(given)))
                    .orElse(built);
            case OperatorValue operator -> newSpecification
                    .<AnyFunctionValue>map(given -> operatorRespecified(operator, given))
                    .orElse(operator);
        };
    }

    private boolean takesTheBody(AnyFunctionValue prototype, Value body) {
        return prototype instanceof DefinedFunctionValue && body instanceof AnyBlockValue;
    }

    private OperatorValue operatorRespecified(OperatorValue operator, AnyBlockValue specification) {
        return new OperatorValue(operator.operatorName(),
                makeFunctionFrom(operator.underlying(), BlockValue.block(List.of(specification))));
    }

    private AnyBlockValue asABlock(Value half) {
        return half instanceof AnyBlockValue block
                ? block
                : BlockValue.block(List.of());
    }

    private boolean isTheStarThatMeansKeepIt(Value first) {
        return first instanceof AnyWordValue star && star.canonical().equals("*");
    }

    @Override
    public Value makeStructFrom(StructValue prototype, Value spec) {
        StructValue made = prototype.separateCopy();
        if (spec instanceof BinaryValue octets) {
            byte[] bytes = octets.bytesFromHere();
            if (bytes.length < made.size()) {
                throw StructValue.TYPE.refusing(spec);
            }
            made.changeFrom(bytes);
            return made;
        }
        if (!(spec instanceof AnyBlockValue written)) {
            throw StructValue.TYPE.refusing(spec);
        }
        made.startedWith(BlockValue.block(evaluator.reducedLeavingSetWords(written)));
        return made;
    }
}
