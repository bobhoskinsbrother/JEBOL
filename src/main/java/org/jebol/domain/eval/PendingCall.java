package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PendingCall {

    private final Value callee;
    private final ContextSlot slot;
    private final List<Value> arguments = new ArrayList<>();
    private final int needed;
    private final boolean infix;
    private final List<String> refinements;

    private final List<String> mentioned;

    private final List<Parameter> parameters;

    private PendingCall(
            Value callee,
            ContextSlot slot,
            int needed,
            List<Value> supplied,
            boolean infix,
            List<String> refinements,
            List<String> mentioned) {
        this.callee = callee;
        this.slot = slot;
        this.needed = needed;
        this.infix = infix;
        this.refinements = List.copyOf(refinements);
        this.mentioned = List.copyOf(mentioned);
        this.parameters = readTheParametersOf(callee);
        this.arguments.addAll(supplied);
    }

    private int startedAt = -1;
    private String calledThrough;

    void startAt(int position, String name) {
        this.startedAt = position;
        this.calledThrough = name;
    }

    int startedAt() {
        return startedAt;
    }

    private boolean entered;

    void enter() {
        entered = true;
    }

    Optional<String> findTheNameInTheBacktrace() {
        if (!entered) {
            return Optional.empty();
        }
        if (calledThrough != null) {
            return Optional.of(calledThrough);
        }
        return callee instanceof AnyFunctionValue
                ? Optional.of(UNNAMED_CALL)
                : Optional.empty();
    }

    Optional<Parameter> findTheParameterBeingFilled() {
        return Optional.ofNullable(consumeAt(arguments.size()));
    }

    Value describeArgumentStillWanted() {
        Parameter wanted = consumeAt(arguments.size());
        return wanted != null ? wanted.asWrittenInSpec() : NoneValue.none();
    }

    String nameInArgumentError() {
        return calledThrough != null ? calledThrough : UNNAMED_CALL;
    }

    private static final String UNNAMED_CALL = "-unnamed-";

    private List<Parameter> consuming;

    private Parameter consumeAt(int index) {
        List<Parameter> sequence = listConsumingSequence();
        return index < sequence.size() ? sequence.get(index) : null;
    }

    boolean wantsUnevaluated(Value upcoming) {
        Parameter taking = consumeAt(arguments.size());
        if (taking == null) {
            return false;
        }
        return switch (taking.kind()) {
            case HARD_QUOTED -> true;
            case SOFT_QUOTED -> !optsIntoEvaluation(upcoming);
            default -> false;
        };
    }

    boolean takesNextValueAsWritten() {
        Parameter taking = consumeAt(arguments.size());
        return taking != null
                && switch (taking.kind()) {
                    case HARD_QUOTED, SOFT_QUOTED -> true;
                    default -> false;
                };
    }

    private static boolean optsIntoEvaluation(Value upcoming) {
        return upcoming instanceof GetWordValue
                || upcoming instanceof ParenValue
                || upcoming instanceof GetPathValue;
    }

    static PendingCall prefix(Value callee, List<String> refinements, List<String> mentioned) {
        return new PendingCall(callee, null, countArgumentsCallTakes(callee, mentioned),
                List.of(), false, refinements, mentioned);
    }

    List<String> refinements() {
        return refinements;
    }

    static PendingCall makeInfix(OperatorValue operator, Value leftOperand) {
        return new PendingCall(
                operator, null, 2, List.of(leftOperand), true, List.of(), List.of());
    }

    static PendingCall makeAssignment(ContextSlot slot) {
        return new PendingCall(null, slot, 1, List.of(), false, List.of(), List.of());
    }

    private java.util.function.Consumer<Value> destination;

    static PendingCall makeAssignmentInto(java.util.function.Consumer<Value> destination) {
        PendingCall call = new PendingCall(
                null, null, 1, List.of(), false, List.of(), List.of());
        call.destination = destination;
        return call;
    }

    java.util.function.Consumer<Value> destination() {
        return destination;
    }

    boolean isInfix() {
        return infix;
    }

    private static int countArgumentsCallTakes(Value callee, List<String> mentioned) {
        return switch (callee) {
            case DefinedFunctionValue function ->
                    countArgumentsWritten(function.parameters(), mentioned);
            case NativeValue built ->
                    countArgumentsWritten(built.parameters(), mentioned);
            case OperatorValue operator -> operator.arity();
            default -> throw Raised.of(
                    EvaluationFailure.CANNOT_USE,
                    callee.datatype().literalSpelling() + " is not callable");
        };
    }

    private static List<Parameter> readTheParametersOf(Value callee) {
        return switch (callee) {
            case DefinedFunctionValue function -> function.parameters();
            case NativeValue built -> built.parameters();
            case OperatorValue operator -> readTheParametersOf(operator.underlying());
            case null, default -> List.of();
        };
    }

    private static int countArgumentsWritten(
            List<Parameter> parameters, List<String> mentioned) {
        int counted = 0;
        for (Parameter parameter : parameters) {
            if (parameter.consumesArgument()
                    && parameter.owningRefinement().map(mentioned::contains).orElse(true)) {
                counted++;
            }
        }
        return counted;
    }

    void accept(Value argument) {
        arguments.add(argument);
    }

    boolean isSatisfied() {
        return arguments.size() >= needed;
    }

    boolean isAssignment() {
        if (destination != null) {
            return true;
        }
        return slot != null;
    }

    Value callee() {
        return callee;
    }

    ContextSlot slot() {
        return slot;
    }

    Value takeOnlyArgument() {
        return arguments.get(0);
    }

    List<Value> listTheArgumentsInDeclaredOrder() {
        if (arguments.size() != consumingCount()
                || (mentioned.size() < 2
                        && mentioned.size() == refinements.size())) {
            return List.copyOf(arguments);
        }
        List<Value> lined = new ArrayList<>(arguments.size());
        List<Parameter> sequence = listConsumingSequence();
        for (Parameter wanted : listWhatIsConsumedInDeclaredOrder()) {
            boolean granted = wanted.owningRefinement()
                    .map(refinements::contains).orElse(true);
            int at = sequence.indexOf(wanted);
            if (granted && at >= 0) {
                lined.add(arguments.get(at));
            }
        }
        return List.copyOf(lined);
    }

    private int consumingCount() {
        return listConsumingSequence().size();
    }

    private List<Parameter> listWhatIsConsumedInDeclaredOrder() {
        List<Parameter> declared = new ArrayList<>(parameters.size());
        for (Parameter parameter : parameters) {
            if (parameter.consumesArgument()) {
                declared.add(parameter);
            }
        }
        return declared;
    }

    private List<Parameter> listConsumingSequence() {
        if (consuming == null) {
            List<Parameter> sequence = new ArrayList<>(parameters.size());
            for (Parameter parameter : parameters) {
                if (parameter.consumesArgument() && parameter.owningRefinement().isEmpty()) {
                    sequence.add(parameter);
                }
            }
            for (String refinement : mentioned) {
                for (Parameter parameter : parameters) {
                    if (parameter.consumesArgument()
                            && parameter.owningRefinement().filter(refinement::equals).isPresent()) {
                        sequence.add(parameter);
                    }
                }
            }
            consuming = sequence;
        }
        return consuming;
    }
}
