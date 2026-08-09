package org.jebol.domain.eval;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * Walks a block, left to right, turning values into a result.
 *
 * <p>Evaluation state lives in {@link Frame} objects on the heap rather than
 * in JVM stack frames. That is what lets a runaway script be stopped with an
 * ordinary REBOL error instead of a {@code StackOverflowError}, and it is the
 * part of the design that cannot be retrofitted, so it is here from the
 * start.
 *
 * <p>The one rule that makes REBOL what it is: a value either stands for
 * itself or reaches forward and consumes the values after it. How far it
 * reaches is not known until the word is looked up, which is why nothing can
 * be arranged into a call tree in advance.
 */
public final class Evaluator {

    /** How deep nesting may go before an ordinary error is raised. */
    public static final int DEFAULT_MAXIMUM_DEPTH = 10_000;

    /** How many steps between asking whether the script should stop. */
    public static final int DEFAULT_CHECK_EVERY = 1_000;

    private final Map<String, Callable> behaviours;
    private final OutputPort output;
    private final Context systemContext;
    private final int maximumDepth;
    private final Interruption interruption;
    private final int checkEvery;

    private FilePort files = FilePort.none();
    private int stepsSinceLastCheck;

    public Evaluator(
            Map<String, Callable> behaviours, Context systemContext, OutputPort output) {
        this(behaviours, systemContext, output, DEFAULT_MAXIMUM_DEPTH,
                Interruption.never(), DEFAULT_CHECK_EVERY);
    }

    public Evaluator(
            Map<String, Callable> behaviours,
            Context systemContext,
            OutputPort output,
            int maximumDepth,
            Interruption interruption,
            int checkEvery) {
        this.behaviours = new java.util.HashMap<>(behaviours);
        this.systemContext = systemContext;
        this.output = output;
        this.maximumDepth = maximumDepth;
        this.interruption = interruption;
        this.checkEvery = checkEvery;
    }

    /**
     * Registers a native after construction, which is how a host adds one of
     * its own. Safe because an interpreter is owned by one thread; a host
     * defining a function while a script runs is the same mistake as running
     * two scripts at once.
     */
    public void defineNative(String name, Callable behaviour) {
        behaviours.put(name, behaviour);
    }

    /** Where print and prin send their text. */
    public OutputPort output() {
        return output;
    }

    /** Where a script's reading and writing goes. Nowhere, by default. */
    public FilePort files() {
        return files;
    }

    /** Gives the script a filesystem to reach. */
    public void useFiles(FilePort port) {
        this.files = port;
    }

    /** The context holding the natives, for the ones that evaluate blocks. */
    public Context systemContext() {
        return systemContext;
    }

    /**
     * Reads source text and evaluates it, which is what DO of a string does.
     * A syntax error raises like any other failure, because from the script's
     * point of view it is one.
     */
    public Value evaluateSource(String source) {
        TranscodeResult read = Transcoder.transcode(source);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return walk(Binder.bind(read.values().orElseThrow(), systemContext),
                systemContext, 1);
    }

    /**
     * Evaluates a block and hands back its value, or the error that stopped
     * it. Nothing escapes as a host exception.
     */
    public Outcome evaluate(BlockValue code, Context context) {
        try {
            return new Outcome.Completed(walk(code, context, 1));
        } catch (Raised raised) {
            return new Outcome.Raised(raised.error());
        }
    }

    /**
     * Evaluates a block and returns its value, letting an error propagate.
     * For natives such as IF that evaluate a branch and have nothing useful
     * to do with a failure except pass it on.
     */
    public Value evaluateOrRaise(BlockValue code, Context context) {
        return walk(code, context, 1, null);
    }

    /**
     * Evaluates a block and returns every expression's value rather than only
     * the last. This is REDUCE, and it is the contrast case to DO: the same
     * walk, keeping what it would otherwise discard.
     */
    public List<Value> evaluateEachOrRaise(BlockValue code, Context context) {
        List<Value> results = new ArrayList<>();
        walk(code, context, 1, results::add);
        return results;
    }

    /**
     * Evaluates expressions in order until one satisfies {@code stopsHere},
     * and returns it. Returns the last value if nothing did, or unset for an
     * empty block.
     *
     * <p>This is what ANY and ALL are built from, and stopping matters: an
     * expression after the deciding one is never evaluated, which is what
     * lets {@code all [string? a string? b append a b]} guard the append.
     */
    public Value evaluateUntilOrRaise(
            BlockValue code, Context context, Predicate<Value> stopsHere) {
        List<Value> stopped = new ArrayList<>(1);
        Value last = walk(code, context, 1, produced -> {
            if (stopsHere.test(produced)) {
                stopped.add(produced);
                return false;
            }
            return true;
        });
        return stopped.isEmpty() ? last : stopped.get(0);
    }

    /**
     * One expression's value, and where it left off. REBOL's {@code do/next}.
     *
     * <p>Needed wherever a native has to evaluate part of a block and then
     * decide what to do with the rest. CASE is the reason it exists:
     * {@code case [size < 10 ["small"] ...]} cannot pair values off two at a
     * time, because the condition is however many values the expression
     * happens to be.
     *
     * @param value what the expression produced
     * @param nextIndex the 1-based position after it
     */
    public record Step(Value value, int nextIndex) {
    }

    /** Evaluates the single expression starting at the block's position. */
    public Step evaluateNextOrRaise(BlockValue code, Context context) {
        if (code.atTail()) {
            return new Step(UnsetValue.unset(), code.index());
        }
        Frame frame = new Frame(code, context, 1);
        Deque<Frame> frames = new ArrayDeque<>();
        frames.push(frame);
        frame.sink = produced -> false;
        Value produced = walkFrames(frames);
        return new Step(produced, frame.position);
    }

    /** The value of a block: its last expression's value, or unset if empty. */
    private Value walk(BlockValue code, Context context, int depth) {
        return walk(code, context, depth, null);
    }

    /**
     * Asks whether the script should stop, every so often rather than every
     * step. Checking a clock a million times a second costs more than the
     * bound is worth; a thousand steps is close enough to a deadline that
     * nobody notices the difference.
     */
    private void stopIfAsked() {
        stepsSinceLastCheck++;
        if (stepsSinceLastCheck < checkEvery) {
            return;
        }
        stepsSinceLastCheck = 0;
        interruption.reasonToStop().ifPresent(reason -> {
            throw new Stopped(reason);
        });
    }

    /**
     * Pops frames until the nearest function body, and hands it the returned
     * value. Rethrows if there is none here, because the function being
     * returned from is then one this walk was started inside.
     */
    private void unwindToFunction(Deque<Frame> frames, ReturnSignal returning) {
        while (!frames.isEmpty() && !frames.peek().functionBody) {
            frames.pop();
        }
        if (frames.isEmpty()) {
            throw returning;
        }
        Frame body = frames.peek();
        body.lastResult = returning.value();
        body.stopped = true;
    }

    /** Watches each top-level result; returning false stops the walk. */
    @FunctionalInterface
    private interface ResultSink {
        boolean accept(Value produced);
    }

    /**
     * Walks a block and everything it nests into, keeping frames on a stack of
     * its own.
     *
     * <p>A paren and a function body push a frame rather than calling back
     * into this method, so a script that recurses a thousand deep costs a
     * thousand small objects on the heap instead of a thousand JVM frames.
     * That is what lets the depth limit be a promise rather than a hope, and
     * it is why {@code forever: func [n] [forever n]} reports an error instead
     * of killing the process.
     */
    private Value walk(BlockValue code, Context context, int depth, ResultSink sink) {
        Deque<Frame> frames = new ArrayDeque<>();
        Frame root = new Frame(code, context, depth);
        root.sink = sink;
        frames.push(root);
        return walkFrames(frames);
    }

    private Value walkFrames(Deque<Frame> frames) {
        while (true) {
            stopIfAsked();
            Frame frame = frames.peek();

            if (frame.stopped || frame.atEnd()) {
                if (!frame.stopped && !frame.pendingCalls.isEmpty()) {
                    throw Raised.of(EvaluationFailure.NEED_VALUE,
                            "the block ended while a call was still gathering arguments");
                }
                Value finished = frame.lastResult;
                frames.pop();
                if (frames.isEmpty()) {
                    return finished;
                }
                deliver(frames.peek(), finished, frames);
                continue;
            }

            // A call waiting for a literal argument takes the value as it
            // stands, without looking it up or running it.
            PendingCall gathering = frame.pendingCalls.peek();
            if (gathering != null && gathering.wantsUnevaluated(frame.current())) {
                Value unevaluated = frame.current();
                frame.advance();
                deliver(frame, unevaluated, frames);
                continue;
            }

            try {
                if (takeOneStep(frame, frames) instanceof StepOutcome.Produced produced) {
                    deliver(frame, produced.value(), frames);
                }
            } catch (ReturnSignal returning) {
                unwindToFunction(frames, returning);
            }
        }
    }

    /** Pushes a nested block, refusing if that would nest too deep. */
    private void push(Deque<Frame> frames, BlockValue code, Context context) {
        push(frames, code, context, false);
    }

    private void push(
            Deque<Frame> frames, BlockValue code, Context context, boolean functionBody) {
        Frame parent = frames.peek();
        if (parent.depth >= maximumDepth) {
            throw Raised.of(EvaluationFailure.TOO_DEEP);
        }
        Frame pushed = new Frame(code, context, parent.depth + 1);
        pushed.functionBody = functionBody;
        frames.push(pushed);
    }

    /**
     * Takes the value at the current position and turns it into a result,
     * advancing past whatever it consumed.
     */
    private StepOutcome takeOneStep(Frame frame, Deque<Frame> frames) {
        Value input = frame.current();
        frame.advance();

        return switch (input.datatype()) {
            case WORD -> evaluateWord(frame, frames, (WordValue) input);
            case GET_WORD -> StepOutcome.of(evaluateGetWord((WordValue) input));
            case LIT_WORD -> StepOutcome.of(((WordValue) input).as(Datatype.WORD));
            case LIT_PATH -> StepOutcome.of(((BlockValue) input).as(Datatype.PATH));
            case SET_WORD -> evaluateSetWord(frame, (WordValue) input);
            case PAREN -> {
                push(frames, ((BlockValue) input).as(Datatype.BLOCK), frame.context);
                yield StepOutcome.waiting();
            }
            case PATH -> evaluatePath(frame, frames, (BlockValue) input);
            case SET_PATH -> evaluateSetPath(frame, (BlockValue) input);
            case ERROR -> throw new Raised((ErrorValue) input);
            default -> StepOutcome.of(input);
        };
    }

    /**
     * Hands a produced value to whatever was waiting for it, then keeps going.
     * An operator immediately after the value takes it as a first argument,
     * which is the whole of infix.
     */
    private void deliver(Frame frame, Value produced, Deque<Frame> frames) {
        Value carrying = produced;
        while (true) {
            PendingCall waiting = frame.pendingCalls.peek();
            // An operator already waiting for its right operand takes this
            // value directly. Looking for another operator first would let the
            // second one reach in front of the first, and REBOL has no
            // precedence for it to do that with.
            boolean feedingAnOperator = waiting != null && waiting.isInfix();
            if (!feedingAnOperator) {
                Optional<OperatorValue> operator = operatorAt(frame);
                if (operator.isPresent()) {
                    frame.advance();
                    frame.pendingCalls.push(PendingCall.infix(operator.orElseThrow(), carrying));
                    return;
                }
            }
            if (frame.pendingCalls.isEmpty()) {
                frame.lastResult = carrying;
                if (frame.sink != null && !frame.sink.accept(carrying)) {
                    frame.stopped = true;
                }
                return;
            }
            waiting.accept(carrying);
            if (!waiting.isSatisfied()) {
                return;
            }
            frame.pendingCalls.pop();
            // A user function pushes its body rather than producing now. Its
            // value arrives when that frame finishes, and lands back here.
            if (!(invoke(frame, waiting, frames) instanceof StepOutcome.Produced invoked)) {
                return;
            }
            carrying = invoked.value();
        }
    }

    /** The operator at the current position, if the next value is one. */
    private Optional<OperatorValue> operatorAt(Frame frame) {
        if (frame.atEnd()) {
            return Optional.empty();
        }
        if (!(frame.current() instanceof WordValue word) || word.datatype() != Datatype.WORD) {
            return Optional.empty();
        }
        if (!word.isBound() || !word.binding().knows(word.canonical())) {
            return Optional.empty();
        }
        return word.binding().slotFor(word.canonical()).value()
                instanceof OperatorValue operator
                ? Optional.of(operator)
                : Optional.empty();
    }

    // ---- dispatch --------------------------------------------------------

    private StepOutcome evaluateWord(
            Frame frame, Deque<Frame> frames, WordValue word) {
        ContextSlot slot = resolve(word);
        Value bound = slot.value();
        if (bound.datatype() == Datatype.UNSET) {
            throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
        }
        if (bound.datatype() == Datatype.OP) {
            throw Raised.of(EvaluationFailure.NEED_VALUE,
                    "the operator " + word.spelling() + " has nothing on its left");
        }
        if (!bound.datatype().isAnyFunction()) {
            return StepOutcome.of(bound);
        }
        return startCall(frame, frames, bound, List.of());
    }

    private Value evaluateGetWord(WordValue word) {
        return resolve(word).value();
    }

    private StepOutcome evaluateSetWord(Frame frame, WordValue word) {
        if (!word.isBound() || !word.binding().knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        if (frame.atEnd()) {
            throw Raised.of(EvaluationFailure.NEED_VALUE,
                    word.spelling() + ": has nothing after it to assign");
        }
        ContextSlot slot = word.binding().slotFor(word.canonical());
        if (slot.isProtected()) {
            throw Raised.of(EvaluationFailure.PROTECTED_WORD, word.spelling());
        }
        frame.pendingCalls.push(PendingCall.assignment(slot));
        return StepOutcome.waiting();
    }

    private ContextSlot resolve(WordValue word) {
        if (!word.isBound() || !word.binding().knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        return word.binding().slotFor(word.canonical());
    }

    /**
     * Begins a call. If it needs no arguments it happens now; otherwise it
     * waits for the values that follow.
     */
    private StepOutcome startCall(
            Frame frame, Deque<Frame> frames, Value callee, List<String> refinements) {
        PendingCall call = PendingCall.prefix(callee, refinements);
        if (call.isSatisfied()) {
            return invoke(frame, call, frames);
        }
        frame.pendingCalls.push(call);
        return StepOutcome.waiting();
    }

    private StepOutcome invoke(Frame frame, PendingCall call, Deque<Frame> frames) {
        if (call.isAssignment()) {
            call.slot().setValue(call.arguments().get(0));
            return StepOutcome.of(call.arguments().get(0));
        }
        return switch (call.callee()) {
            case NativeValue built ->
                    StepOutcome.of(runNative(built, call.arguments(), frame.context));
            case OperatorValue operator -> StepOutcome.of(
                    invokeUnderlying(operator, call.arguments(), frame.context));
            case FunctionValue function ->
                    runFunction(frames, function, call.arguments(), call.refinements());
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    call.callee().datatype().literalSpelling() + " is not callable");
        };
    }

    private Value invokeUnderlying(
            OperatorValue operator, List<Value> arguments, Context context) {
        return switch (operator.underlying()) {
            case NativeValue built -> runNative(built, arguments, context);
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "operator " + operator.operatorName() + " has no runnable body");
        };
    }

    private Value runNative(
            NativeValue built, List<Value> arguments, Context context) {
        Callable behaviour = behaviours.get(built.nativeName());
        if (behaviour == null) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "no behaviour registered for " + built.nativeName());
        }
        checkArgumentTypes(built.parameters(), arguments, built.nativeName());
        Value produced = behaviour.call(arguments, this, context);
        if (produced == null) {
            throw new IllegalStateException(
                    built.nativeName() + " returned null; use UnsetValue.unset()");
        }
        return produced;
    }

    /**
     * Runs a user function in a context of its own.
     *
     * <p>The locals context is a child of the one the function was defined in,
     * so a word the function does not name falls through to where it was
     * written rather than to where it was called. That is what makes a
     * function mean the same thing wherever it is passed.
     */
    private StepOutcome runFunction(
            Deque<Frame> frames,
            FunctionValue function,
            List<Value> arguments,
            List<String> refinements) {

        checkArgumentTypes(function.parameters(), arguments, "function");
        Context locals = Context.childOf(function.closedOver());

        List<Parameter> consuming = function.parameters().stream()
                .filter(Parameter::consumesAnArgument)
                .toList();
        for (int index = 0; index < consuming.size(); index++) {
            locals.set(consuming.get(index).name(), arguments.get(index));
        }

        // A refinement the caller did not ask for is none rather than unset,
        // so the body can test it without first asking whether it exists.
        function.parameters().stream()
                .filter(parameter -> parameter.kind() == ParameterKind.REFINEMENT)
                .forEach(parameter -> locals.set(
                        parameter.name(),
                        refinements.contains(parameter.name().toLowerCase(Locale.ROOT))
                                ? LogicValue.yes()
                                : NoneValue.none()));

        // /local words start unset, so assigning to one inside the body binds
        // here rather than reaching out to the caller.
        function.localNames().forEach(locals::define);

        push(frames, Binder.bind(function.body(), locals), locals, true);
        return StepOutcome.waiting();
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, List<Value> arguments, String calleeName) {
        List<Parameter> consuming = parameters.stream()
                .filter(Parameter::consumesAnArgument)
                .toList();
        for (int index = 0; index < arguments.size() && index < consuming.size(); index++) {
            Parameter parameter = consuming.get(index);
            Value argument = arguments.get(index);
            if (!parameter.accepts(argument.datatype())) {
                throw Raised.of(EvaluationFailure.EXPECT_ARG,
                        calleeName + " wanted " + parameter.name() + " to be one of "
                                + parameter.acceptedTypes() + ", got "
                                + argument.datatype().literalSpelling());
            }
        }
    }

    // ---- paths -----------------------------------------------------------

    private StepOutcome evaluatePath(
            Frame frame, Deque<Frame> frames, BlockValue path) {
        Selection selection = select(path, frame.context);
        if (!selection.value().datatype().isAnyFunction()) {
            return StepOutcome.of(selection.value());
        }
        return startCall(
                frame, frames,
                refined(selection.value(), selection.refinements()),
                selection.refinements());
    }

    /**
     * A native with refinements is a different native.
     *
     * <p>{@code copy/part} takes two arguments where {@code copy} takes one,
     * so the refined form is registered under its own name and looked up
     * here. A user function needs none of this: its refinements are
     * parameters and its arity already accounts for them.
     */
    private Value refined(Value callee, List<String> refinements) {
        if (!(callee instanceof NativeValue built) || refinements.isEmpty()) {
            return callee;
        }
        String refinedName = built.nativeName() + "/" + String.join("/", refinements);
        return systemContext.knows(refinedName)
                ? systemContext.slotFor(refinedName).value()
                : callee;
    }

    private StepOutcome evaluateSetPath(Frame frame, BlockValue path) {
        if (frame.atEnd()) {
            throw Raised.of(EvaluationFailure.NEED_VALUE,
                    "a set-path has nothing after it to assign");
        }
        List<Value> segments = path.remaining();
        BlockValue allButLast = BlockValue.path(
                segments.subList(0, segments.size() - 1), Datatype.PATH);
        if (segments.size() == 1) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "a one-segment path has nothing to assign through");
        }
        Value target = select(allButLast, frame.context).value();
        Value lastSegment = segments.get(segments.size() - 1);

        if (target instanceof ObjectValue object && lastSegment instanceof WordValue field) {
            if (!object.context().holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            frame.pendingCalls.push(
                    PendingCall.assignment(object.context().ownSlotFor(field.canonical())));
            return StepOutcome.waiting();
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot assign through " + target.datatype().literalSpelling());
    }

    /** Walks the segments, gathering refinements once a function is reached. */
    private Selection select(BlockValue path, Context context) {
        List<Value> segments = path.remaining();
        if (segments.isEmpty()) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, "an empty path selects nothing");
        }
        Value current = selectFirst(segments.get(0), context);
        List<String> refinements = new ArrayList<>();

        for (int index = 1; index < segments.size(); index++) {
            Value segment = segments.get(index);
            if (current.datatype().isAnyFunction()) {
                refinements.add(refinementNameOf(segment));
                continue;
            }
            current = selectWith(current, selectorFor(segment, context));
        }
        return new Selection(current, List.copyOf(refinements));
    }

    private Value selectFirst(Value segment, Context context) {
        if (segment instanceof WordValue word) {
            WordValue bound = word.isBound() ? word : word.boundTo(context);
            Value held = resolve(bound).value();
            // Blame the word rather than the path. `nothing/here` fails
            // because nothing has no value, and saying "invalid path" would
            // send the reader looking at the wrong end of the expression.
            if (held.datatype() == Datatype.UNSET) {
                throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
            }
            return held;
        }
        return segment;
    }

    private Value selectorFor(Value segment, Context context) {
        if (segment instanceof WordValue word && word.datatype() == Datatype.GET_WORD) {
            return resolve(word.isBound() ? word : word.boundTo(context)).value();
        }
        return segment;
    }

    private String refinementNameOf(Value segment) {
        if (segment instanceof WordValue word) {
            return word.canonical();
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "a refinement must be a word, not " + segment.datatype().literalSpelling());
    }

    /**
     * One selection step. Past the end of a series gives none; a name an
     * object does not have raises, because an object either has that field or
     * the code is wrong.
     */
    private Value selectWith(Value target, Value selector) {
        if (target instanceof ObjectValue object && selector instanceof WordValue field) {
            if (!object.context().holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            return object.context().ownSlotFor(field.canonical()).value();
        }
        if (target instanceof BlockValue block && selector instanceof IntegerValue position) {
            long index = position.magnitude();
            if (index < 1 || index > block.lengthFromHere()) {
                return NoneValue.none();
            }
            return block.storage().at(block.index() + (int) index - 1);
        }
        if (target instanceof SeriesValue series && selector instanceof IntegerValue position) {
            long index = position.magnitude();
            if (index < 1 || index > series.lengthFromHere()) {
                return NoneValue.none();
            }
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot select " + selector.datatype().literalSpelling()
                        + " from " + target.datatype().literalSpelling());
    }

    private record Selection(Value value, List<String> refinements) {
    }

    // ---- the frame -------------------------------------------------------

    /** One block being walked, and whatever is waiting for a value in it. */
    private static final class Frame {

        private final BlockValue code;
        private final Context context;
        private final int depth;
        private final Deque<PendingCall> pendingCalls = new ArrayDeque<>();

        private int position;
        private Value lastResult = UnsetValue.unset();
        private ResultSink sink;
        private boolean stopped;
        private boolean functionBody;

        Frame(BlockValue code, Context context, int depth) {
            this.code = code;
            this.context = context;
            this.depth = depth;
            this.position = code.index();
        }

        boolean atEnd() {
            return position > code.storageLength();
        }

        Value current() {
            return code.storage().at(position);
        }

        void advance() {
            position++;
        }
    }
}
