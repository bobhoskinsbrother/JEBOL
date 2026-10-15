package org.jebol.domain.value;

import org.jebol.domain.eval.ExtensionDispatcher;
import org.jebol.domain.eval.RefinedCallable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CommandValue extends DefaultNative {

    public static final Datatype TYPE = new AnyFunctionDatatype("command") {

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            return spec instanceof AnyBlockValue definition && opensWithASpec(definition)
                    ? maker.makeCommandFrom(definition)
                    : super.madeFrom(spec, maker);
        }

        @Override
        public Value convertedFrom(Value value, Maker maker) {
            return value instanceof AnyBlockValue definition && opensWithASpec(definition)
                    ? maker.makeCommandFrom(definition)
                    : super.convertedFrom(value, maker);
        }

        private boolean opensWithASpec(AnyBlockValue definition) {
            List<Value> parts = definition.remaining();
            return parts.size() >= THE_FEWEST_PARTS_A_FUNCTION_IS_MADE_FROM
                    && parts.getFirst() instanceof AnyBlockValue;
        }
    };

    private static final int THE_FEWEST_PARTS_A_FUNCTION_IS_MADE_FROM = 2;

    private static final int THE_MOST_WORDS_A_COMMAND_CARRIES = 15;

    private final List<Parameter> declared;

    private final PathTargetWithFields extension;

    private final long index;

    public CommandValue(AnyBlockValue spec, List<Parameter> declared,
            PathTargetWithFields extension, long index) {
        this.declared = List.copyOf(declared);
        this.extension = extension;
        this.index = index;
        declaredBy(spec, declared);
    }

    @Override
    public String nativeName() {
        return "command";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return declared;
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return declared.stream()
                .filter(parameter -> parameter.kind() == ParameterKind.REFINEMENT)
                .map(Parameter::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (declared.size() > THE_MOST_WORDS_A_COMMAND_CARRIES) {
                throw Raised.of(EvaluationFailure.BAD_COMMAND);
            }
            return theDispatcher()
                    .orElseThrow(() -> Raised.of(EvaluationFailure.BAD_EXTENSION, (Value) extension))
                    .dispatch(index, arguments, refinements, evaluator, context);
        };
    }

    public Optional<ExtensionDispatcher> theDispatcher() {
        List<ContextSlot> fields = extension.context().slots();
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return fields.getFirst().value() instanceof HandleValue handle
                ? new DispatcherHandle(handle).dispatcher()
                : Optional.empty();
    }
}
