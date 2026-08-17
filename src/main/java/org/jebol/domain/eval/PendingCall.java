package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Every refinement the path named, granted or not.
     *
     * <p>A refinement written as a get-word that turned out to be false
     * is still named, and its arguments still come out of the block. The
     * C takes each one and drops it -- {@code if (useArgs) DS_Base[ds] =
     * *DS_POP; else DS_DROP} -- so what a call consumes depends on what
     * was written and not on what was granted.
     */
    private final List<String> named;
    private final List<Parameter> consuming;

    private PendingCall(
            Value callee,
            ContextSlot slot,
            int needed,
            List<Value> supplied,
            boolean infix,
            List<String> refinements,
            List<String> named) {
        this.callee = callee;
        this.slot = slot;
        this.needed = needed;
        this.infix = infix;
        this.refinements = List.copyOf(refinements);
        this.named = List.copyOf(named);
        this.consuming = arrivingParametersOf(callee, this.named);
        this.arguments.addAll(supplied);
    }

    private static List<Parameter> declaredParametersOf(Value callee) {
        List<Parameter> declared = switch (callee) {
            case NativeValue built -> built.parameters();
            case FunctionValue function -> function.parameters();
            case null, default -> List.<Parameter>of();
        };
        return declared.stream().filter(Parameter::consumesAnArgument).toList();
    }

    /**
     * The parameters in the order their values arrive from the block.
     *
     * <p>Which is the order the path wrote its refinements, not the order
     * the function declares them. A refinement nobody asked for takes no
     * value at all and is left out entirely, so counting along this list
     * says what the next value is for.
     *
     * <p>Getting this wrong is subtle rather than loud. A quoted parameter
     * belonging to a refinement written out of order was matched against
     * whichever parameter sat at that position in the declaration, so
     * {@code f/two/one "a" x y 1} evaluated X instead of taking it as
     * written, and failed on a word nobody had set.
     */
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

    /**
     * Whether the argument about to be gathered is taken as written.
     *
     * <p>A literal parameter always is, which is why {@code repeat count 3}
     * works: the counter is a word the loop is about to bind, so looking it
     * up first would fail on a word nobody has set.
     *
     * <p>A soft-quoted parameter is too, unless the caller asked otherwise.
     * A paren, a get-word or a get-path at the call site says "evaluate this
     * one after all", which is what makes it soft: the function declares the
     * default and the caller keeps a way out. The soft one is the {@code
     * 'word} sigil, not the {@code :word} one, which is the way round a real
     * R3 answers and the opposite of how this read until it was asked.
     */
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

    private static boolean optsIntoEvaluation(Value upcoming) {
        return switch (upcoming.datatype()) {
            case PAREN, GET_WORD, GET_PATH -> true;
            default -> false;
        };
    }

    static PendingCall prefix(Value callee, List<String> refinements, List<String> named) {
        return new PendingCall(callee, null, arityOf(callee, named),
                List.of(), false, refinements, named);
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
        return new PendingCall(
                operator, null, 2, List.of(leftOperand), true, List.of(), List.of());
    }

    static PendingCall assignment(ContextSlot slot) {
        return new PendingCall(null, slot, 1, List.of(), false, List.of(), List.of());
    }

    /**
     * Where a set-path puts its value when the place is not a slot.
     *
     * <p>A path may name a position in a series as readily as a field in
     * an object -- `s/1: #"X"` replaces a character -- and a position is
     * not a context slot. So the target is somewhere a value can be put
     * rather than a slot, and a slot is one of the things that is.
     */
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

    /**
     * How many arguments this call takes from the block.
     *
     * <p>An argument belonging to a refinement is only taken when that
     * refinement was asked for, so the count depends on the call site
     * rather than on the function. The native path has always known
     * this; the user-function path counted them all, so
     * `f: func [a /into b] [...]` demanded two arguments from every
     * caller and Rebol's own COLLECT could not be written.
     */
    private static int arityOf(Value callee, List<String> named) {
        return switch (callee) {
            case FunctionValue function ->
                    argumentsWrittenFor(function.parameters(), named);
            case NativeValue built ->
                    argumentsWrittenFor(built.parameters(), named);
            case OperatorValue operator -> operator.arity();
            default -> throw Raised.of(
                    EvaluationFailure.CANNOT_USE,
                    callee.datatype().literalSpelling() + " is not callable");
        };
    }

    /**
     * How many values the call site wrote, granted or not.
     *
     * <p>Named rather than granted, which is the whole of the rule above and
     * which the native path did not obey. A declined refinement's argument is
     * still written and still taken -- {@code append/:part s v 1} with part
     * declined appends and swallows the 1 -- so counting only the granted
     * ones left the extra values standing in the block as expressions of
     * their own.
     *
     * <p>What that cost: REPEND is
     * {@code append/:part/:only/:dup :series reduce :value :length :count},
     * and with all three declined the two spare words were evaluated
     * separately, making the last of them the function's answer. So
     * {@code repend [1 2] [3 4]} came back as NONE, and every port opened by
     * URL failed with no-scheme, because make-port* decodes a URL through
     * REPEND.
     */
    private static int argumentsWrittenFor(
            List<Parameter> parameters, List<String> named) {
        return (int) parameters.stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement()
                        .map(named::contains).orElse(true))
                .count();
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

    /**
     * The gathered arguments, put back into the order the function
     * declares them.
     *
     * <p>They arrive in the order the call site wrote its refinements,
     * which need not be the order the function declares them: {@code
     * sort/compare/skip s 1 3} hands over the comparator first and the
     * record size second, and SORT declares the size first. Every reader
     * downstream counts along the declared order, so the two are lined up
     * here rather than in each of them.
     *
     * <p>{@code Do_Args} in {@code c-do.c} does the same thing from the
     * other end. When the path names a refinement that is not the next
     * one in the spec, it restarts the spec walk at that refinement and
     * fills its arguments from the stream, under a comment reading
     * "refinement out of sequence, resequence arg order".
     */
    List<Value> arguments() {
        if (arguments.size() != consuming.size()
                || (named.size() < 2 && named.size() == refinements.size())) {
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
