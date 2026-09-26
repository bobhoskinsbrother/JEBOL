package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

final class PendingCall {

    private final Value callee;
    private final ContextSlot slot;
    private final List<Value> arguments = new ArrayList<>();
    private final int needed;
    private final boolean infix;
    private final List<String> refinements;

    private final List<String> mentioned;
    private final List<Parameter> consuming;

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
        this.consuming = arrivingParametersOf(callee, this.mentioned);
        this.arguments.addAll(supplied);
    }

    private int startedAt = -1;
    private String calledThrough;

    void startedAt(int position, String name) {
        this.startedAt = position;
        this.calledThrough = name;
    }

    int startedAt() {
        return startedAt;
    }

    String calledThrough() {
        return calledThrough;
    }

    private static List<Parameter> declaredParametersOf(Value callee) {
        List<Parameter> declared = switch (callee) {
            case NativeValue built -> built.parameters();
            case FunctionValue function -> function.parameters();
            case null, default -> List.of();
        };
        return declared.stream().filter(Parameter::consumesAnArgument).toList();
    }

    private static List<Parameter> arrivingParametersOf(
            Value callee, List<String> asked) {

        List<Parameter> declared = declaredParametersOf(callee);
        List<Parameter> arriving = new ArrayList<>(declared.stream()
                .filter(parameter -> parameter.owningRefinement().isEmpty())
                .toList());
        for (String refinement : asked) {
            declared.stream()
                    .filter(parameter -> parameter.owningRefinement()
                            .filter(refinement::equals).isPresent())
                    .forEach(arriving::add);
        }
        return List.copyOf(arriving);
    }

    boolean wantsUnevaluated(Value upcoming) {
        int position = arguments.size();
        if (position >= consuming.size()) {
            return false;
        }
        return switch (consuming.get(position).kind()) {
            case HARD_QUOTED -> true;
            case SOFT_QUOTED -> !optsIntoEvaluation(upcoming);
            default -> false;
        };
    }

    boolean takesTheNextValueAsWritten() {
        int position = arguments.size();
        return position < consuming.size()
                && switch (consuming.get(position).kind()) {
                    case HARD_QUOTED, SOFT_QUOTED -> true;
                    default -> false;
                };
    }

    private static boolean optsIntoEvaluation(Value upcoming) {
        return switch (upcoming.datatype()) {
            case PAREN, GET_WORD, GET_PATH -> true;
            default -> false;
        };
    }

    static PendingCall prefix(Value callee, List<String> refinements, List<String> mentioned) {
        return new PendingCall(callee, null, arityOf(callee, mentioned),
                List.of(), false, refinements, mentioned);
    }

    List<String> refinements() {
        return refinements;
    }

    static PendingCall infix(OperatorValue operator, Value leftOperand) {
        return new PendingCall(
                operator, null, 2, List.of(leftOperand), true, List.of(), List.of());
    }

    static PendingCall assignment(ContextSlot slot) {
        return new PendingCall(null, slot, 1, List.of(), false, List.of(), List.of());
    }

    private java.util.function.Consumer<Value> destination;

    static PendingCall assignmentInto(java.util.function.Consumer<Value> destination) {
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

    private static int arityOf(Value callee, List<String> mentioned) {
        return switch (callee) {
            case FunctionValue function ->
                    argumentsWrittenGrantedOrNotFor(function.parameters(), mentioned);
            case NativeValue built ->
                    argumentsWrittenGrantedOrNotFor(built.parameters(), mentioned);
            case OperatorValue operator -> operator.arity();
            default -> throw Raised.of(
                    EvaluationFailure.CANNOT_USE,
                    callee.datatype().literalSpelling() + " is not callable");
        };
    }

    private static int argumentsWrittenGrantedOrNotFor(
            List<Parameter> parameters, List<String> mentioned) {
        return (int) parameters.stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement()
                        .map(mentioned::contains).orElse(true))
                .count();
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

    List<Value> argumentsInDeclaredOrder() {
        if (arguments.size() != consuming.size()
                || (mentioned.size() < 2
                        && mentioned.size() == refinements.size())) {
            return List.copyOf(arguments);
        }
        List<Parameter> declared = declaredParametersOf(callee);
        List<Value> lined = new ArrayList<>(arguments.size());
        for (Parameter wanted : declared) {
            boolean granted = wanted.owningRefinement()
                    .map(refinements::contains).orElse(true);
            int at = consuming.indexOf(wanted);
            if (granted && at >= 0) {
                lined.add(arguments.get(at));
            }
        }
        return List.copyOf(lined);
    }
}
