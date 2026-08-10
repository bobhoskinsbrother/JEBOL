package org.jebol.domain.eval;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.ProtectedFromChange;
import org.jebol.domain.value.SlotIsProtected;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.StringValue;
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

    private final Map<String, RefinedCallable> behaviours;
    private final OutputPort output;
    private final Context systemContext;

    /** Where run-time source puts its words. The library, until told. */
    private Context runtimeContext;
    private final int maximumDepth;
    private final Interruption interruption;
    private final int checkEvery;

    private FilePort files = FilePort.none();

    /** The names the host was started with. None, by default. */
    private EnvironmentPort environment = EnvironmentPort.none();

    /** Where a script reads a line from the operator. Nowhere, by default. */
    private ConsolePort console = ConsolePort.none();

    /** Where a script puts a window on a screen. Nowhere, by default. */
    private WindowPort windows = WindowPort.none();

    /** How a script starts another program. Not at all, by default. */
    private ProcessPort processes = ProcessPort.none();
    private int stepsSinceLastCheck;

    public Evaluator(
            Map<String, RefinedCallable> behaviours, Context systemContext,
            OutputPort output) {
        this(behaviours, systemContext, output, DEFAULT_MAXIMUM_DEPTH,
                Interruption.never(), DEFAULT_CHECK_EVERY);
    }

    public Evaluator(
            Map<String, RefinedCallable> behaviours,
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
        behaviours.put(name, (arguments, evaluator, context, refinements) ->
                behaviour.call(arguments, evaluator, context));
    }

    /** Where print and prin send their text. */
    public OutputPort output() {
        return output;
    }

    /** How a script starts another program. Not at all, by default. */
    public ProcessPort processes() {
        return processes;
    }

    /** Gives the script a way to start another program. */
    public void useProcesses(ProcessPort port) {
        this.processes = port;
    }

    /** Where a script reads a line from the operator. Nowhere, by default. */
    public ConsolePort console() {
        return console;
    }

    /** Gives the script a console to read. */
    public void useConsole(ConsolePort port) {
        this.console = port;
    }

    /** Where a script puts a window on a screen. Nowhere, by default. */
    public WindowPort windows() {
        return windows;
    }

    /** Gives the script a screen to put a window on. */
    public void useWindows(WindowPort port) {
        this.windows = port;
    }

    /** The names the host was started with. None, by default. */
    public EnvironmentPort environment() {
        return environment;
    }

    /** Gives the script an environment to read. */
    public void useEnvironment(EnvironmentPort port) {
        this.environment = port;
    }

    /** Where a script's reading and writing goes. Nowhere, by default. */
    public FilePort files() {
        return files;
    }

    /** Gives the script a filesystem to reach. */
    public void useFiles(FilePort port) {
        this.files = port;
    }

    /**
     * Calls a function value with arguments the caller already has.
     *
     * <p>For natives that take a function as an argument, such as
     * {@code sort/compare}. Everything else reaches a function through the
     * walk, which gathers its arguments from the block; here there is no
     * block and the arguments are in hand.
     */
    public Value applyFunction(Value callee, List<Value> arguments) {
        return switch (callee) {
            case FunctionValue function -> {
                Context locals = Context.childOf(function.closedOver());
                List<Parameter> parameters = function.parameters();
                // Every parameter is defined, whether or not a value came
                // for it. A name the body mentions and nobody supplied
                // holds unset, which is a value; leaving it undefined
                // makes the body fail on a missing word instead.
                // The /local words too. The walk defines them and this did
                // not, so a function reached this way failed on its own first
                // local -- and MAKE-PORT* declares three.
                function.localNames().forEach(locals::define);
                for (int at = 0; at < parameters.size(); at++) {
                    locals.set(parameters.get(at).name(), at < arguments.size()
                            ? arguments.get(at)
                            : UnsetValue.unset());
                }
                try {
                    yield evaluateOrRaise(Binder.bind(function.body(), locals), locals);
                } catch (ReturnSignal returned) {
                    // A function reached this way returns the same way as
                    // one reached by name. Without this the signal
                    // escaped the interpreter as a Java exception, which
                    // spec/embed.allium says cannot happen.
                    yield returned.value();
                }
            }
            case NativeValue built -> runNative(built, arguments, systemContext);
            case OperatorValue operator -> invokeUnderlying(operator, arguments, systemContext);
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    callee.datatype().literalSpelling() + " is not callable");
        };
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
        // Every word gets a slot, whether or not anything knew it
        // before. `Do_String` in c-do.c binds with BIND_ALL for exactly
        // this reason: source arriving at run time has to be able to
        // name something new, and `do "total: 1"` is the whole of the
        // case. Bound the ordinary way, TOTAL is a word nobody has
        // defined and the assignment fails.
        Context into = runtimeContext == null ? systemContext : runtimeContext;
        return walk(Binder.bindAndDefine(read.values().orElseThrow(), into), into, 1);
    }

    /**
     * Where words that arrive at run time are given their slots.
     *
     * <p>{@code system/contexts/user} in the C. Set by whoever built the
     * interpreter; without it, source read at run time binds into the
     * library, which works and puts a script's own names among the
     * built-in ones.
     */
    public void putRuntimeWordsIn(Context context) {
        this.runtimeContext = context;
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
                    throw Raised.of(EvaluationFailure.NO_ARG,
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
            // A get-path selects through the path and stops there: it
            // does not call what it reaches. `o/f` calls the function
            // and `:o/f` hands it back, which is the same difference a
            // get-word makes for a plain word. Without this the path
            // stood for itself, and BIND given one had a get-path where
            // an object was wanted.
            case GET_PATH -> StepOutcome.of(
                    select(((BlockValue) input).as(Datatype.PATH), frame.context).value());
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
            throw Raised.of(EvaluationFailure.NO_OP_ARG,
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
            throw Raised.of(EvaluationFailure.LOCKED_WORD, word.spelling());
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
        return startCall(frame, frames, callee, refinements, refinements);
    }

    private StepOutcome startCall(
            Frame frame, Deque<Frame> frames, Value callee,
            List<String> refinements, List<String> named) {
        PendingCall call = PendingCall.prefix(callee, refinements, named);
        if (call.isSatisfied()) {
            return invoke(frame, call, frames);
        }
        frame.pendingCalls.push(call);
        return StepOutcome.waiting();
    }

    private StepOutcome invoke(Frame frame, PendingCall call, Deque<Frame> frames) {
        if (call.isAssignment()) {
            // A protected slot refuses as a REBOL error rather than as a
            // host exception. The set-word path checked this and the
            // set-path one did not, so assigning into a protected object
            // escaped the interpreter entirely.
            if (call.slot() != null && call.slot().isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD, "the field is protected");
            }
            if (call.destination() != null) {
                call.destination().accept(call.arguments().get(0));
                return StepOutcome.of(call.arguments().get(0));
            }
            call.slot().setValue(call.arguments().get(0));
            return StepOutcome.of(call.arguments().get(0));
        }
        return switch (call.callee()) {
            case NativeValue built -> {
                Value produced = runNative(built, call.arguments(), frame.context);
                // DO given a function calls it, taking the arguments from
                // the block after it. DO cannot do that itself: it is a
                // native of fixed arity and cannot go on to consume the
                // arguments its own argument wants. So the call carries
                // on from here, where the frame the arguments are in is
                // still to hand.
                yield built.nativeName().equals("do")
                        && produced.datatype().isAnyFunction()
                        && !call.arguments().isEmpty()
                        && call.arguments().get(0).datatype().isAnyFunction()
                        ? startCall(frame, frames, produced, List.of())
                        : StepOutcome.of(produced);
            }
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
        RefinedCallable behaviour = behaviours.get(built.nativeName());
        if (behaviour == null) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "no behaviour registered for " + built.nativeName());
        }
        checkArgumentTypes(built, arguments, built.nativeName());
        Value produced;
        try {
            produced = behaviour.call(arguments, this, context, built.askedRefinements());
        } catch (ProtectedFromChange refused) {
            // The value layer knows only that the change is not allowed.
            // Turned into a REBOL error here, where the native's name is
            // still known, so TRY can catch it like any other failure.
            throw Raised.of(EvaluationFailure.PROTECTED, built.nativeName());
        } catch (SlotIsProtected refused) {
            // A refused assignment to a name, which REBOL reports
            // differently from a refused change to a container.
            throw Raised.of(EvaluationFailure.LOCKED_WORD, refused.spelling());
        }
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

        // Only the arguments the call site actually supplied line up with
        // the values: a parameter belonging to a refinement nobody asked
        // for takes none of them. Lining up against every declared
        // parameter put the first supplied value into a refinement's slot
        // and left the real ones short.
        List<Parameter> consuming = function.parameters().stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement()
                        .map(refinements::contains).orElse(true))
                .toList();
        for (int index = 0; index < consuming.size() && index < arguments.size(); index++) {
            locals.set(consuming.get(index).name(), arguments.get(index));
        }

        // And a refinement's argument that was not supplied is none, so
        // the body can read it without asking whether it exists.
        // Owning a refinement is what makes it a refinement's argument;
        // the kind stays NORMAL, because how it consumes its value does
        // not change. Filtering on the kind matched nothing.
        function.parameters().stream()
                .filter(parameter -> parameter.owningRefinement().isPresent())
                .filter(parameter -> !consuming.contains(parameter))
                .forEach(parameter -> locals.set(parameter.name(), NoneValue.none()));

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
            NativeValue built, List<Value> arguments, String calleeName) {
        checkArgumentTypes(built.parameters(), built.askedRefinements(),
                arguments, calleeName);
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, List<Value> arguments, String calleeName) {
        checkArgumentTypes(parameters, Set.of(), arguments, calleeName);
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, Set<String> asked,
            List<Value> arguments, String calleeName) {
        // A refinement's argument is only there when the refinement was
        // asked for, so a parameter belonging to an absent one must not
        // line up against a value meant for the next parameter along.
        List<Parameter> consuming = parameters.stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement()
                        .map(asked::contains).orElse(true))
                .toList();
        for (int index = 0; index < arguments.size() && index < consuming.size(); index++) {
            Parameter parameter = consuming.get(index);
            Value argument = arguments.get(index);
            if (!parameter.accepts(argument.datatype())) {
                // Rebol words this failure with three arguments:
                //   expect-arg: [:arg1 {does not allow} :arg3 {for its}
                //                :arg2 {argument}]
                // So arg1 is the function, arg2 the parameter and arg3 the
                // datatype that was refused. All three go in as values,
                // because Rebol's own suite asserts on arg3 directly and no
                // amount of message text will satisfy that.
                throw new Raised(ErrorValue.about(
                        EvaluationFailure.EXPECT_ARG.category(),
                        EvaluationFailure.EXPECT_ARG.errorId(),
                        calleeName + " does not allow "
                                + argument.datatype().literalSpelling()
                                + " for its " + parameter.name() + " argument",
                        WordValue.of(calleeName),
                        WordValue.of(parameter.name()),
                        DatatypeValue.of(argument.datatype())));
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
                selection.refinements(), selection.named());
    }

    /**
     * A native with refinements is a different native.
     *
     * <p>{@code copy/part} takes two arguments where {@code copy} takes one,
     * so the refined form is registered under its own name and looked up
     * here. A user function needs none of this: its refinements are
     * parameters and its arity already accounts for them.
     *
     * <p>A refinement no native has raises rather than being dropped. This
     * fell back to the plain native until pinning that {@code parse/all}
     * must raise, which meant every misspelled refinement in every script
     * ran quietly as though it had been left off, and code written against
     * an older REBOL went on looking like it worked.
     */
    private Value refined(Value callee, List<String> refinements) {
        if (!(callee instanceof NativeValue built) || refinements.isEmpty()) {
            return callee;
        }
        // A refined native is the same native, told what was asked for.
        // Each combination used to need its own registration, which is why
        // parse/all worked and transcode/one/error did not.
        for (String refinement : refinements) {
            if (!built.declares(refinement)) {
                String refinedName = built.nativeName() + "/" + String.join("/", refinements);
                if (systemContext.knows(refinedName)) {
                    return systemContext.slotFor(refinedName).value();
                }
                throw Raised.of(EvaluationFailure.NO_REFINE,
                        built.nativeName() + " has no /" + refinement + " refinement");
            }
        }
        return built.askedFor(Set.copyOf(refinements));
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
        // A path names a position in a series as readily as a field in
        // an object, and assigning through it replaces what is there.
        // Rebol's own base-defs.reb rewrites strings this way, having no
        // REPLACE that early in its boot.
        if (target instanceof SeriesValue series && lastSegment instanceof IntegerValue where) {
            int at = series.index() + (int) where.magnitude() - 1;
            frame.pendingCalls.push(PendingCall.assignmentInto(
                    value -> replaceInSeries(series, at, value)));
            return StepOutcome.waiting();
        }
        // A tuple is a value rather than a series, so writing an octet
        // makes a new tuple and puts it back where the old one came
        // from. Only a word may be written through, which is what R3
        // reaches too: everything else holds a copy.
        if (target instanceof TupleValue tuple && lastSegment instanceof IntegerValue where
                && segments.getFirst() instanceof WordValue holder && segments.size() == 2) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            frame.pendingCalls.push(PendingCall.assignmentInto(
                    value -> slot.setValue(
                            withOctetWritten(tuple, (int) where.magnitude(), value))));
            return StepOutcome.waiting();
        }
        // A set is changed in place rather than replaced, because a parse rule
        // that already names the word has to see the change. Rebol's own
        // url-parser copies the URI set and then adds the percent sign to it.
        // The segment is evaluated first. `b/(#"a"): true` names the
        // character in a paren, which is the only way a path reaches a
        // character the source did not spell out -- and Rebol's own
        // url-parser writes it exactly that way.
        if (target instanceof BitsetValue set
                && selectorFor(lastSegment, frame.context)
                        instanceof CharacterValue letter) {
            frame.pendingCalls.push(PendingCall.assignmentInto(
                    value -> set.hold(letter.codepoint(), value.isTruthy())));
            return StepOutcome.waiting();
        }
        // A pair is a value rather than a series, so writing a half makes a
        // new pair and puts it back where the old one came from -- the same
        // shape a tuple needs, and for the same reason. Rebol writes through
        // the value in place; the observable end of it is that `p: 1x1`
        // followed by `p/x: 0` leaves p as 0x1, and that holds either way.
        if (target instanceof PairValue pair && segments.size() == 2
                && segments.getFirst() instanceof WordValue holder) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            frame.pendingCalls.push(PendingCall.assignmentInto(
                    value -> slot.setValue(withHalfWritten(pair, lastSegment, value))));
            return StepOutcome.waiting();
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot assign through " + target.datatype().literalSpelling());
    }

    /**
     * A pair with one half replaced, as {@code PD_Pair} writes it.
     *
     * <p>Two refusals, both {@code PE_BAD_SET} and so both {@code
     * bad-path-set}. The segment has to be a half rather than the derived
     * AREA, which has nothing to write to. And the value has to be an
     * integer or a decimal: {@code PD_Pair} tests for those two and refuses
     * everything else, so a pair cannot be written into a pair's half.
     *
     * <p>{@code bad-path-set} rather than {@code invalid-path}, because the
     * path is fine and the write is not.
     */
    private static PairValue withHalfWritten(PairValue pair, Value segment, Value written) {
        double replacement = switch (written) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue quantity -> quantity.quantity();
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a pair half holds a number, not "
                            + written.datatype().literalSpelling());
        };
        return switch (segment) {
            case WordValue name when PairValue.isWritableHalf(name.canonical()) ->
                    pair.withHalf(name.canonical(), replacement);
            case IntegerValue position when position.magnitude() == 1
                    || position.magnitude() == 2 ->
                    pair.withHalfAt((int) position.magnitude(), replacement);
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a pair has an x half and a y half, and nothing else to write");
        };
    }

    /** Walks the segments, gathering refinements once a function is reached. */
    private Selection select(BlockValue path, Context context) {
        List<Value> segments = path.remaining();
        if (segments.isEmpty()) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, "an empty path selects nothing");
        }
        Value current = selectFirst(segments.get(0), context);
        List<String> refinements = new ArrayList<>();
        // Every refinement the path names, granted or not. A refinement
        // that was named and turned down still takes its arguments from
        // the block and drops them, so the count of values a call
        // consumes depends on what was written rather than on what was
        // granted.
        List<String> named = new ArrayList<>();

        for (int index = 1; index < segments.size(); index++) {
            Value segment = segments.get(index);
            if (current.datatype().isAnyFunction()) {
                // `insert/:only` takes the refinement when ONLY is true
                // and leaves it off otherwise. Without it a function that
                // passes its own refinements on has to branch and write
                // the call twice, which is why Rebol's own library uses
                // the form throughout.
                if (segment instanceof WordValue asked
                        && asked.datatype() == Datatype.GET_WORD) {
                    named.add(asked.canonical());
                    if (resolve(asked.isBound() ? asked : asked.boundTo(context))
                            .value().isTruthy()) {
                        refinements.add(asked.canonical());
                    }
                    continue;
                }
                refinements.add(refinementNameOf(segment));
                named.add(refinementNameOf(segment));
                continue;
            }
            current = selectWith(current, selectorFor(segment, context));
        }
        return new Selection(current, List.copyOf(refinements), List.copyOf(named));
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
        // A paren segment is evaluated and its value used as the
        // selector. It is the only way a path reaches something the
        // source did not spell out: `data/(k)` names the field K holds.
        if (segment instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return evaluateOrRaise(Binder.bind(paren.as(Datatype.BLOCK), context), context);
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
     *
     * <p>A map goes the series way rather than the object way: a key it has
     * not got gives none. A map is asked about keys it may not have, which is
     * the whole difference between the two.
     */
    private Value selectWith(Value target, Value selector) {
        // A decimal index truncates towards zero, so `b/1.6` is the first
        // item. The opposite of EVEN?, which rounds; both were checked
        // against a real R3 rather than made consistent by assumption.
        //
        // Done first so it covers everything a number can index -- a
        // series, a pair, a tuple -- rather than only the branch it was
        // first needed in.
        if (selector instanceof DecimalValue fractional) {
            selector = IntegerValue.of((long) fractional.quantity());
        }
        if (target instanceof MapValue map) {
            return map.select(selector);
        }
        // A pair answers to two spellings for each half: p/x and p/1 are
        // the same question. Only one of the two is obvious from 1x2.
        // A tuple is read by position, and past either end gives none
        // -- the way a series behaves rather than the way an object
        // does, even though neither is a series.
        if (target instanceof TupleValue tuple && selector instanceof IntegerValue position) {
            long at = position.magnitude();
            // As far as the shown length rather than the kept one. The
            // octets behind the kept ones are zeros, so a tuple made
            // from the block [1] answers zero for its second and third.
            return at < 1 || at > tuple.shownCount()
                    ? NoneValue.none()
                    : IntegerValue.of(tuple.octetAt((int) at));
        }
        // A time and a date are read by the name of the part wanted.
        // A time raises for a part it has not got; a date answers none.
        // The two are not the same rule with different field names, and
        // reading them as one was worth four assertions.
        if (target instanceof TimeValue time) {
            long seconds = time.nanoseconds() / 1_000_000_000L;
            String part = selector instanceof WordValue named
                    ? named.canonical()
                    : positionAsTimePart(selector);
            return switch (part) {
                case "hour" -> IntegerValue.of(seconds / 3600);
                case "minute" -> IntegerValue.of(seconds / 60 % 60);
                case "second" -> IntegerValue.of(seconds % 60);
                default -> throw Raised.of(EvaluationFailure.INVALID_PATH,
                        Molder.mold(selector));
            };
        }
        if (target instanceof DateValue date && selector instanceof WordValue part) {
            return switch (part.canonical()) {
                case "year" -> IntegerValue.of(date.year());
                case "month" -> IntegerValue.of(date.month());
                case "day" -> IntegerValue.of(date.day());
                case "date" -> date;
                default -> NoneValue.none();
            };
        }
        // A path reads one bit of a set and answers a logic, never none:
        // `SET_LOGIC(pvs->store, Check_Bits(...))` in PD_Bitset. The two look
        // alike in a condition and part company under NONE? and LOGIC?.
        if (target instanceof BitsetValue set && selector instanceof CharacterValue letter) {
            return LogicValue.of(set.holds(letter.codepoint()));
        }
        if (target instanceof PairValue pair) {
            Optional<Value> half = switch (selector) {
                case IntegerValue position -> pair.halfAt((int) position.magnitude());
                case WordValue name -> pair.half(name.canonical());
                default -> Optional.empty();
            };
            // Three named segments and two positions: x, y, area, 1 and 2.
            // AREA is derived rather than stored, which is why it reads and
            // cannot be written.
            return half.orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                    "a pair has an x half, a y half and an area, and nothing else"));
        }
        // An error is read like an object and is not one, which is how
        // REBOL code asks which failure happened. A field it has not got
        // raises rather than answering none, because an error either has
        // the field or the code asking is wrong.
        if (target instanceof ErrorValue raised && selector instanceof WordValue named) {
            return raised.field(named.canonical()).orElseThrow(() ->
                    Raised.of(EvaluationFailure.INVALID_PATH, named.spelling()));
        }
        if (target instanceof ObjectValue object && selector instanceof WordValue field) {
            if (!object.context().holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            return object.context().ownSlotFor(field.canonical()).value();
        }
        // A port is an object underneath, thus a path reads its fields the
        // same way. Rebol's own INPUT reads `port/scheme/name` and
        // MAKE-PORT* writes every field of one.
        if (target instanceof PortValue port && selector instanceof WordValue field) {
            if (!port.context().holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            return port.context().ownSlotFor(field.canonical()).value();
        }
        if (target instanceof BlockValue block && selector instanceof IntegerValue position) {
            long index = position.magnitude();
            if (index < 1 || index > block.lengthFromHere()) {
                return NoneValue.none();
            }
            return block.storage().at(block.index() + (int) index - 1);
        }
        // A string gives the character and a binary gives the byte. This
        // used to check the bounds and then fall through to the failure
        // below, so every index that was in range raised and every one
        // that was not answered none -- exactly backwards.
        if (target instanceof SeriesValue series && selector instanceof IntegerValue position) {
            long index = position.magnitude();
            if (index < 1 || index > series.lengthFromHere()) {
                return NoneValue.none();
            }
            return switch (series) {
                case StringValue text -> CharacterValue.of(
                        text.storage().at(text.index() + (int) index - 1));
                case BinaryValue bytes -> IntegerValue.of(
                        bytes.storage().at(bytes.index() + (int) index - 1));
                case BlockValue block -> block.storage().at(
                        block.index() + (int) index - 1);
            };
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot select " + selector.datatype().literalSpelling()
                        + " from " + target.datatype().literalSpelling());
    }

    /** A time answers to positions as well as names, as a pair does. */
    private static String positionAsTimePart(Value selector) {
        if (!(selector instanceof IntegerValue position)) {
            return "";
        }
        return switch ((int) position.magnitude()) {
            case 1 -> "hour";
            case 2 -> "minute";
            case 3 -> "second";
            default -> "";
        };
    }

    private static void replaceInSeries(SeriesValue series, int at, Value value) {
        switch (series) {
            case BlockValue block -> block.storage().set(at, value);
            case StringValue text -> text.storage().set(at,
                    value instanceof CharacterValue character
                            ? character.codepoint()
                            : Molder.form(value).codePointAt(0));
            case BinaryValue bytes -> bytes.storage().set(at,
                    value instanceof IntegerValue octet ? (int) octet.magnitude() : 0);
        }
    }

    /**
     * A tuple with one octet written, which may lengthen or shorten it.
     *
     * <p>The set branch of {@code PD_Tuple} in {@code t-tuple.c}. Three
     * rules, none of them shared with anything else that writes:
     *
     * <p>A number is clamped rather than refused, so writing 300 stores
     * 255 and writing -10 stores 0. Every way of building a tuple refuses
     * the same numbers, which makes this the one place a value out of
     * range gets in.
     *
     * <p>Writing past the end lengthens the tuple, and the octets skipped
     * over were already zeros, so setting the fifth octet of 1.2.3 gives
     * 1.2.3.0.5.
     *
     * <p>Writing NONE cuts the tuple short at that position and zeros
     * what followed. It is the only way to shorten one.
     */
    private static Value withOctetWritten(TupleValue tuple, int position, Value written) {
        if (position < 1 || position > TupleValue.MAXIMUM_SEGMENTS) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Integer.toString(position));
        }
        if (written instanceof NoneValue) {
            int[] shortened = new int[position - 1];
            for (int at = 1; at < position; at++) {
                shortened[at - 1] = tuple.octetAt(at);
            }
            return TupleValue.of(shortened);
        }
        if (!(written instanceof IntegerValue) && !(written instanceof DecimalValue)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Molder.mold(written));
        }
        long amount = written instanceof IntegerValue whole
                ? whole.magnitude()
                : (long) ((DecimalValue) written).quantity();
        int[] octets = tuple.octetsToTwelve();
        octets[position - 1] = (int) Math.max(0, Math.min(255, amount));
        // The length grows only when the write lands past what the tuple
        // shows, so writing the second octet of a tuple that keeps one
        // leaves it keeping one and the written octet unreachable.
        int kept = position > tuple.shownCount() ? position : tuple.segmentCount();
        return TupleValue.of(java.util.Arrays.copyOf(octets, kept));
    }

    private record Selection(
            Value value, List<String> refinements, List<String> named) {
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
