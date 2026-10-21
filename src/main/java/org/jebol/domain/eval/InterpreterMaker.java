package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyFunctionValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CommandValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.DefinedFunctionValue;
import org.jebol.domain.value.DispatcherHandle;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PathTargetWithFields;
import org.jebol.domain.value.Raised;
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
    public ObjectValue evaluateAnObject(AnyBlockValue body) {
        return evaluator.evaluateInto(evaluator.makeFreshObjectWithin(context), body);
    }

    @Override
    public FunctionValue bindAFunction(AnyBlockValue spec, AnyBlockValue body) {
        return Binder.bindTheFunctionsBody(spec, body, context);
    }

    @Override
    public Value applyASystemFunction(String name, Value argument) {
        return evaluator.applyFunction(context.findSystemFunctionNamed(name), List.of(argument));
    }

    @Override
    public Value readASimpleValue(Value piece) {
        return evaluator.readASimpleValue(piece, context);
    }

    @Override
    public ErrorValue speakHere(ErrorValue error) {
        return evaluator.speakHere(error);
    }

    @Override
    public StructSpec.LayoutRegistry structLayouts() {
        return withoutEvaluating.structLayouts();
    }

    @Override
    public Optional<List<Value>> readTheValuesFrom(String source) {
        return withoutEvaluating.readTheValuesFrom(source);
    }

    @Override
    public AnyBlockValue readSource(String source) {
        return withoutEvaluating.readSource(source);
    }

    @Override
    public Value makeObjectFrom(ObjectValue prototype, BlockValue body) {
        return evaluator.evaluateInto(copyTheObject(prototype), body);
    }

    @Override
    public Value mergeAnObject(ObjectValue prototype, ObjectValue other) {
        return mergeTheObjects(prototype, other);
    }

    private ObjectValue copyTheObject(ObjectValue prototype) {
        ObjectValue built = evaluator.makeFreshObjectWithin(context);
        Context fields = built.context();
        prototype.context().slots().forEach(slot -> fields.register(
                slot.spelling(), Binder.cloneAndRebind(
                        slot.value(), Set.of(prototype.context()), fields)));
        return built;
    }

    private Value mergeTheObjects(ObjectValue prototype, ObjectValue other) {
        Context fields = Context.makeChildOf(context);
        prototype.context().slots()
                .forEach(slot -> fields.register(slot.spelling(), slot.value()));
        other.context().slots()
                .forEach(slot -> fields.register(slot.spelling(), slot.value()));

        ObjectValue merged = new ObjectValue(fields);
        fields.pointItsOwnSelfAt(merged);
        Set<Context> sources = Set.of(prototype.context(), other.context());
        fields.slots().forEach(slot -> fields.register(slot.spelling(),
                Binder.cloneAndRebind(slot.value(), sources, fields)));
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
            throw prototype.refuseAsAPrototypeFor(spec);
        }
        Optional<Value> newBody = parts.size() > 1 ? Optional.of(parts.get(1)) : Optional.empty();
        if (newBody.isPresent() && !takesTheBody(prototype, newBody.get())) {
            throw prototype.refuseAsAPrototypeFor(spec);
        }
        return switch (prototype) {
            case DefinedFunctionValue written -> Binder.bindTheFunctionsBody(
                    newSpecification.orElseGet(() -> asABlock(written.spec())),
                    newBody.map(this::asABlock).orElseGet(() -> asABlock(written.body())),
                    written.closedOver());
            case NativeValue built -> newSpecification
                    .<AnyFunctionValue>map(given -> built.deriveWith(given, FunctionSpec.readTheParametersIn(given)))
                    .orElse(built);
            case OperatorValue operator -> newSpecification
                    .<AnyFunctionValue>map(given -> respecifyTheOperator(operator, given))
                    .orElse(operator);
        };
    }

    @Override
    public Value makeCommandFrom(AnyBlockValue definition) {
        List<Value> parts = definition.remaining();
        if (parts.size() != PARTS_OF_A_COMMAND_DEFINITION
                || !(parts.getFirst() instanceof AnyBlockValue spec)
                || !(parts.get(1) instanceof PathTargetWithFields extension)
                || !hasAnExtensionHandleFirst(extension)
                || !(parts.get(2) instanceof IntegerValue(long index))
                || index > THE_LARGEST_COMMAND_INDEX) {
            throw Raised.of(EvaluationFailure.BAD_FUNC_DEF, definition);
        }
        CommandValue made = new CommandValue(spec, FunctionSpec.readTheParametersIn(spec), extension, index);
        if (made.findTheDispatcher().isEmpty()) {
            throw Raised.of(EvaluationFailure.BAD_EXTENSION, definition);
        }
        return made;
    }

    private static final int PARTS_OF_A_COMMAND_DEFINITION = 3;

    private static final long THE_LARGEST_COMMAND_INDEX = 0xFFFF;

    private boolean hasAnExtensionHandleFirst(PathTargetWithFields extension) {
        List<ContextSlot> fields = extension.context().slots();
        return !fields.isEmpty()
                && fields.getFirst().value() instanceof HandleValue handle
                && handle.typeName().equals(DispatcherHandle.THE_KIND_A_DISPATCHER_IS);
    }

    private boolean takesTheBody(AnyFunctionValue prototype, Value body) {
        return prototype instanceof DefinedFunctionValue && body instanceof AnyBlockValue;
    }

    private OperatorValue respecifyTheOperator(OperatorValue operator, AnyBlockValue specification) {
        return new OperatorValue(operator.operatorName(),
                makeFunctionFrom(operator.underlying(), BlockValue.of(List.of(specification))));
    }

    private AnyBlockValue asABlock(Value half) {
        return half instanceof AnyBlockValue block
                ? block
                : BlockValue.of(List.of());
    }

    private boolean isTheStarThatMeansKeepIt(Value first) {
        return first instanceof AnyWordValue star && star.canonical().equals("*");
    }

    @Override
    public Value makeStructFrom(StructValue prototype, Value spec) {
        StructValue made = prototype.separateCopy();
        if (spec instanceof BinaryValue octets) {
            byte[] bytes = octets.readBytesFromHere();
            if (bytes.length < made.size()) {
                throw StructValue.TYPE.refuse(spec);
            }
            made.changeFrom(bytes);
            return made;
        }
        if (!(spec instanceof AnyBlockValue written)) {
            throw StructValue.TYPE.refuse(spec);
        }
        made.startWith(BlockValue.of(evaluator.reduceLeavingSetWords(written)));
        return made;
    }
}
