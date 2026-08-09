package org.jebol.domain.eval;

import java.util.ArrayList;
import java.util.List;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.Value;

/**
 * Something in the block that is waiting for values: a call gathering its
 * arguments, or a set-word waiting for the thing it will store.
 *
 * <p>Holding these on the frame rather than recursing is what keeps
 * evaluation state on the heap. A chain like {@code add 1 add 2 3} stacks two
 * of these and unwinds them as values arrive.
 */
final class PendingCall {

    private final Value callee;
    private final ContextSlot slot;
    private final List<Value> arguments = new ArrayList<>();
    private final int needed;
    private final boolean infix;
    private final List<String> refinements;
    private final List<Parameter> consuming;

    private PendingCall(
            Value callee,
            ContextSlot slot,
            int needed,
            List<Value> supplied,
            boolean infix,
            List<String> refinements) {
        this.callee = callee;
        this.slot = slot;
        this.needed = needed;
        this.infix = infix;
        this.refinements = List.copyOf(refinements);
        this.consuming = consumingParametersOf(callee);
        this.arguments.addAll(supplied);
    }

    private static List<Parameter> consumingParametersOf(Value callee) {
        List<Parameter> declared = switch (callee) {
            case NativeValue built -> built.parameters();
            case FunctionValue function -> function.parameters();
            case null, default -> List.<Parameter>of();
        };
        return declared.stream().filter(Parameter::consumesAnArgument).toList();
    }

    /**
     * Whether the argument about to be gathered is taken as written.
     *
     * <p>A literal parameter always is, which is why {@code repeat count 3}
     * works: the counter is a word the loop is about to bind, so looking it
     * up first would fail on a word nobody has set.
     *
     * <p>A soft-literal parameter is too, unless the caller asked otherwise.
     * A paren, a get-word or a get-path at the call site says "evaluate this
     * one after all", which is what makes it soft: the function declares the
     * default and the caller keeps a way out.
     */
    boolean wantsUnevaluated(Value upcoming) {
        int position = arguments.size();
        if (position >= consuming.size()) {
            return false;
        }
        return switch (consuming.get(position).kind()) {
            case LITERAL -> true;
            case SOFT_LITERAL -> !optsIntoEvaluation(upcoming);
            default -> false;
        };
    }

    private static boolean optsIntoEvaluation(Value upcoming) {
        return switch (upcoming.datatype()) {
            case PAREN, GET_WORD, GET_PATH -> true;
            default -> false;
        };
    }

    static PendingCall prefix(Value callee, List<String> refinements) {
        return new PendingCall(callee, null, arityOf(callee), List.of(), false, refinements);
    }

    /** Which refinements the call site asked for, from a path such as sum/average. */
    List<String> refinements() {
        return refinements;
    }

    /**
     * An operator, which arrives with its left operand already in hand and
     * takes only its right from the block.
     */
    static PendingCall infix(OperatorValue operator, Value leftOperand) {
        return new PendingCall(operator, null, 2, List.of(leftOperand), true, List.of());
    }

    static PendingCall assignment(ContextSlot slot) {
        return new PendingCall(null, slot, 1, List.of(), false, List.of());
    }

    /**
     * Whether this is an operator waiting for its right operand.
     *
     * <p>The distinction decides how far to the right an operator reaches. An
     * operator takes a single value; a prefix function takes a whole
     * expression. That is why {@code 2 + 3 * 4} is 20, with the addition
     * finishing before the multiplication starts, while {@code add 1 2 * 3} is
     * 7, with the multiplication finishing inside the second argument.
     */
    boolean isInfix() {
        return infix;
    }

    private static int arityOf(Value callee) {
        return switch (callee) {
            case NativeValue built -> built.arity();
            case FunctionValue function -> function.arity();
            case OperatorValue operator -> operator.arity();
            default -> throw Raised.of(
                    EvaluationFailure.CANNOT_USE,
                    callee.datatype().literalSpelling() + " is not callable");
        };
    }

    void accept(Value argument) {
        arguments.add(argument);
    }

    boolean isSatisfied() {
        return arguments.size() >= needed;
    }

    /**
     * Whether this is a set-word waiting for a value rather than a call
     * waiting for arguments. The two were told apart by which of two fields
     * was null, which is a sum type wearing a disguise.
     */
    boolean isAssignment() {
        return slot != null;
    }

    Value callee() {
        return callee;
    }

    ContextSlot slot() {
        return slot;
    }

    List<Value> arguments() {
        return List.copyOf(arguments);
    }
}
