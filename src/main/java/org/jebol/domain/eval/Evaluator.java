package org.jebol.domain.eval;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.util.*;
import java.util.function.Predicate;

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
    private ScreenPort screen = ScreenPort.none();

    /** How a script starts another program. Not at all, by default. */
    private ProcessPort processes = ProcessPort.none();

    /**
     * Where a script's network reaches. Nothing until a host says otherwise,
     * for the same reason the filesystem is nothing until then.
     */
    private NetworkPort network = NetworkPort.none();
    private int stepsSinceLastCheck;

    /**
     * How many frames are open right now, and how many values have been
     * walked since this interpreter started.
     *
     * <p>Both are what STACK/DEPTH and STATS/EVALS answer. They are readable
     * at all only because evaluation state lives in frames on the heap rather
     * than in JVM stack frames -- see decision 1. An implementation using the
     * host's own stack could not answer either question.
     */
    private int framesOpen;

    /**
     * Evaluation tracing, off until TRACE turns it on.
     *
     * <p>Held here because the three hooks the C places -- before a value, at a
     * call, at a return -- are all inside this walk, and because the level is a
     * property of the run rather than of the native that set it.
     */
    private final Trace trace = new Trace();

    /** The tracer, for TRACE to set the level on. */
    public Trace tracing() {
        return trace;
    }
    private long valuesWalked;

    /** How many native and user-function calls have been made. */
    private long nativesCalled;
    private long functionsCalled;

    public long nativesCalled() {
        return nativesCalled;
    }

    public long functionsCalled() {
        return functionsCalled;
    }

    /**
     * A call whose body is running: the word it was made through, the function
     * itself, and the context holding what it was called with.
     *
     * <p>The name is empty for a call no word made -- a function value standing
     * in a block -- and that is a fact about the call rather than a value that
     * went missing.
     *
     * <p>The function and its locals are kept because DS prints them: one line
     * naming the word, the argument count and the datatype, then a line per
     * argument with its value. A stack of names alone could answer the first
     * field and nothing else.
     */
    record OpenCall(String name, FunctionValue function, Context locals) {

        /**
         * Every name the frame holds a value for, in declaration order.
         *
         * <p>What the C walks: {@code args = BLK_HEAD(VAL_FUNC_ARGS(...))} and
         * then every word from the first to the tail, so refinements and locals
         * are printed beside the ordinary arguments rather than left out.
         */
        List<String> slotNames() {
            List<String> names = new ArrayList<>();
            function.parameters().forEach(parameter -> names.add(parameter.name()));
            names.addAll(function.localNames());
            return names;
        }
    }

    /** The calls being run, innermost first. What STACK/WORD and DS read. */
    private final Deque<OpenCall> functionsBeingRun = new ArrayDeque<>();

    /**
     * The name of the call whose body is about to be pushed.
     *
     * <p>Set by the caller of push immediately before it, because push takes
     * the body rather than the call and the name is not recoverable from a
     * block. Empty when a body is being run for something with no name, such
     * as a block handed to DO.
     */
    private String nameOfTheCallBeingMade = "";

    /**
     * The last word a call was started through.
     *
     * <p>Held between the word being looked up and the function's body being
     * pushed, because those are two steps and only the first knows the name.
     * Cleared as it is taken, so a call made on a value rather than through a
     * word does not inherit the previous call's name.
     */
    private String lastWordCalledThrough = "";

    /**
     * Why the script should stop, or empty to carry on.
     *
     * <p>Exposed so a native that blocks can ask. WAIT is the only one: it
     * sleeps, and a sleep that ignored the deadline would outlive the bounds
     * the host set and break the promise that running too long arrives as an
     * outcome rather than as a hung thread.
     */
    public java.util.Optional<String> reasonToStop() {
        return interruption.reasonToStop();
    }

    /** How many frames are open right now. */
    public int framesOpen() {
        return framesOpen;
    }

    /** How many values this interpreter has walked. Only ever rises. */
    public long valuesWalked() {
        return valuesWalked;
    }

    /**
     * What LIMIT-USAGE recorded, per limit, and once each.
     *
     * <p>`Eval_Limit` and `PG_Mem_Limit` in the C, and set-once for the same
     * reason: `if (Eval_Limit == 0) Eval_Limit = ...` writes only into a zero.
     *
     * <p>Nothing reads it yet and nothing can, which is Rebol's arrangement
     * rather than an unfinished one. See the native.
     */
    private final Map<UsageLimit, Long> limitsRecorded =
            new java.util.EnumMap<>(UsageLimit.class);

    java.util.Optional<Long> limitRecorded(UsageLimit limit) {
        return java.util.Optional.ofNullable(limitsRecorded.get(limit));
    }

    /** Records a limit the first time it is asked for, and never after. */
    void recordLimitAskedFor(UsageLimit limit, long value) {
        limitsRecorded.putIfAbsent(limit, value);
    }

    /**
     * The name of the function being run, counting back from the innermost.
     *
     * <p>An offset past the outermost answers nothing, because a caller
     * walking outwards has to be able to reach the end.
     */
    public java.util.Optional<String> functionBeingRun(int offsetOutwards) {
        List<OpenCall> open = new ArrayList<>(functionsBeingRun);
        return offsetOutwards < 0 || offsetOutwards >= open.size()
                ? java.util.Optional.empty()
                : java.util.Optional.of(open.get(offsetOutwards).name());
    }

    /**
     * Every call whose body is running, innermost first.
     *
     * <p>What DS prints. A native reaches the frames this way rather than being
     * handed the walk's own deque, which belongs to the walk.
     */
    List<OpenCall> callsInProgress() {
        return List.copyOf(functionsBeingRun);
    }

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

    /**
     * Where a copy of everything written also goes, when ECHO asked for one.
     *
     * <p>Null when nothing is echoing, which is the ordinary case, so the
     * common path stays one field read rather than a call through a wrapper.
     */
    private OutputPort alsoWritingTo;

    /** Where print and prin send their text. */
    public OutputPort output() {
        if (alsoWritingTo == null) {
            return output;
        }
        OutputPort copyingTo = alsoWritingTo;
        return text -> {
            output.write(text);
            copyingTo.write(text);
        };
    }

    /**
     * Sends a copy of everything written to a second place as well.
     *
     * <p>What ECHO is: "Copies console output to a file." The original port
     * still receives everything, because echoing is a copy and not a
     * redirection -- a script that echoes still prints.
     */
    public void alsoWriteTo(OutputPort second) {
        this.alsoWritingTo = second;
    }

    /** Stops echoing. ECHO of none or false. */
    public void stopEchoing() {
        this.alsoWritingTo = null;
    }

    /** How a script starts another program. Not at all, by default. */
    public ProcessPort processes() {
        return processes;
    }

    public NetworkPort network() {
        return network;
    }

    public void useNetwork(NetworkPort port) {
        this.network = port;
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

    /** Where a script puts a gob tree. On no screen at all, by default. */
    public ScreenPort screen() {
        return screen;
    }

    /** Gives the script a screen to draw a gob tree on. */
    public void useScreen(ScreenPort port) {
        this.screen = port;
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
                if (!function.closure()) {
                    locals.markAsCallFrameOf(function);
                }
                List<Parameter> parameters = function.parameters();
                function.localNames().forEach(
                        name -> locals.set(name, NoneValue.none()));
                bindArgumentsPositionally(locals, parameters, arguments);
                try {
                    yield evaluateOrRaise(
                            Binder.bindOnly(function.body(), locals,
                                    namesOwnedBy(function)),
                            locals);
                } catch (ReturnSignal returned) {
                    yield returned.value();
                } finally {
                    locals.markCallEnded();
                }
            }
            case NativeValue built -> runNative(built, arguments, systemContext);
            case OperatorValue operator -> invokeUnderlying(operator, arguments, systemContext);
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    callee.datatype().literalSpelling() + " is not callable");
        };
    }

    /**
     * Runs a CATCH/WITH function handler on the caught value and its name.
     *
     * <p>The C type-checks the handler's first parameter against the value and
     * its second against the name before calling, and fills any surplus
     * parameter with none rather than unset. So a handler whose parameter
     * refuses the caught value raises expect-arg, and a handler with more
     * parameters than value-and-name sees the rest as none.
     */
    public Value applyToCaught(Value handler, Value caught, Value carriedName) {
        if (!(handler instanceof FunctionValue function)) {
            return applyFunction(handler, List.of(caught, carriedName));
        }
        List<Value> valueAndName = List.of(caught, carriedName);
        checkArgumentTypes(function.parameters(), valueAndName, "catch");
        long positionalArity = function.parameters().stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement().isEmpty())
                .count();
        List<Value> padded = new ArrayList<>(valueAndName);
        while (padded.size() < positionalArity) {
            padded.add(NoneValue.none());
        }
        return applyFunction(handler, padded);
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
            return new Outcome.Completed(unsignalled(() -> walk(code, context, 1)));
        } catch (Raised raised) {
            return new Outcome.Raised(raised.error());
        }
    }

    /**
     * Runs the outermost walk, turning a control-flow signal that nothing
     * caught into the error the C reports for it.
     *
     * <p>BREAK, CONTINUE, RETURN and THROW travel as Java exceptions here,
     * which is how a loop catches one without every native in between having to
     * hand it back. When there is no loop and no function, the signal arrives
     * at the top -- and the C has an error for each: `break: {no loop to
     * break}`, `continue:`, `return:` and `throw:`, the whole of the Throw
     * category in {@code boot/errors.reb}.
     *
     * <p>So this is where they stop being signals. {@code spec/embed.allium}
     * says nothing a script does may reach the host as a throwable, and
     * `do reduce [p 7]` with a BREAK path in P threw {@code LoopSignal} out of
     * the interpreter.
     *
     * <p>TRY does not do this itself, and that is deliberate: the C's TRY
     * traps errors and lets a thrown value past, so `try [break]` still ends
     * the script. Only TRY/ALL disarms one, and it makes the same four errors
     * this does.
     */
    private static Value unsignalled(java.util.function.Supplier<Value> walking) {
        try {
            return walking.get();
        } catch (ThrownSignal thrown) {
            throw new Raised(ErrorValue.about(ErrorCategory.THROW, "throw",
                    "a throw that nothing caught",
                    thrown.value(),
                    thrown.name().<Value>map(WordValue::of).orElseGet(NoneValue::none),
                    NoneValue.none()));
        } catch (LoopSignal stopped) {
            throw new Raised(ErrorValue.of(ErrorCategory.THROW, "break",
                    "a break outside a loop"));
        } catch (ContinueSignal skipped) {
            throw new Raised(ErrorValue.of(ErrorCategory.THROW, "continue",
                    "a continue outside a loop"));
        } catch (ReturnSignal returned) {
            throw new Raised(ErrorValue.about(ErrorCategory.THROW, "return",
                    "a return outside a function",
                    returned.value() instanceof UnsetValue
                            ? NoneValue.none()
                            : returned.value()));
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

    /**
     * Evaluates the single expression starting at the block's position.
     *
     * <p>A RETURN, BREAK or THROW raised by that expression flies on rather
     * than being turned into an error here. The caller is a native part way
     * through a block that is itself part way through a function -- ALL, ANY
     * and CASE -- so the frame that should catch the signal is still above
     * this one on the stack. Disarming it here made {@code all [return 1]}
     * answer "a return outside a function" from inside a function, which is
     * what stopped the borrowed ENCODE at its first line.
     */
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
        valuesWalked++;
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
     *
     * <p>The frames themselves are local and go when this returns, however it
     * returns. The record of which calls are open is not: it is a field, so a
     * raise that unwinds past the loop leaves every call it passed through
     * still recorded. That made STACK/DEPTH climb by one for every error a
     * script caught and never come back down, and the same entries were what
     * DS printed. Closing the record here rather than where a frame is popped
     * covers the exceptional way out as well as the ordinary one.
     */
    private Value walk(BlockValue code, Context context, int depth, ResultSink sink) {
        Deque<Frame> frames = new ArrayDeque<>();
        Frame root = new Frame(code, context, depth);
        root.sink = sink;
        frames.push(root);
        int callsOpenBeforeTheWalk = functionsBeingRun.size();
        try {
            return walkFrames(frames);
        } finally {
            while (functionsBeingRun.size() > callsOpenBeforeTheWalk) {
                handBackTheFrameTakenOverBy(functionsBeingRun.pop());
            }
        }
    }

    private Value walkFrames(Deque<Frame> frames) {
        while (true) {
            stopIfAsked();
            Frame frame = frames.peek();
            trace.nowAtDepth(frames.size() - 1);

            if (frame.stopped || frame.atEnd()) {
                if (!frame.stopped && !frame.pendingCalls.isEmpty()) {
                    throw Raised.of(EvaluationFailure.NO_ARG,
                            "the block ended while a call was still gathering arguments");
                }
                Value finished = frame.lastResult;
                if (frame.functionBody && !functionsBeingRun.isEmpty()) {
                    handBackTheFrameTakenOverBy(functionsBeingRun.pop());
                }
                if (frame.functionBody) {
                    frame.context.markCallEnded();
                }
                frames.pop();
                framesOpen = frames.size();
                if (frames.isEmpty()) {
                    return finished;
                }
                deliver(frames.peek(), finished, frames);
                continue;
            }

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
        push(frames, code, context, null);
    }

    /**
     * Pushes a block, and records the call when the block is a function's body.
     *
     * <p>{@code being} is the function whose body this is, or absent for an
     * ordinary nested block: a paren, a loop body, anything DO was handed. Only
     * a function's body opens a frame that STACK and DS can be asked about.
     */
    private void push(
            Deque<Frame> frames, BlockValue code, Context context, FunctionValue being) {
        Frame parent = frames.peek();
        if (parent.depth >= maximumDepth) {
            throw Raised.of(EvaluationFailure.TOO_DEEP);
        }
        Frame pushed = new Frame(code, context, parent.depth + 1);
        pushed.functionBody = being != null;
        frames.push(pushed);
        framesOpen = frames.size();
        if (being != null) {
            functionsCalled++;
            openFrameOf(being).ifPresent(outer -> outer.supersededBy(context));
            functionsBeingRun.push(new OpenCall(nameOfTheCallBeingMade, being, context));
            nameOfTheCallBeingMade = "";
            lastWordCalledThrough = "";
        }
    }

    /**
     * The innermost frame of a function that is still running, if there is one.
     *
     * <p>{@code while (frame != VAL_WORD_FRAME(DSF_WORD(dsf))) dsf =
     * PRIOR_DSF(dsf);} in {@code Get_Var}, which walks out from the innermost
     * call. Here it is asked at the two moments the answer changes -- a call
     * of the same function beginning, and one ending -- so the frames can
     * point at each other and the walk itself never has to be repeated.
     */
    private java.util.Optional<Context> openFrameOf(FunctionValue function) {
        return functionsBeingRun.stream()
                .filter(call -> call.function() == function)
                .map(OpenCall::locals)
                .findFirst();
    }

    private void handBackTheFrameTakenOverBy(OpenCall ending) {
        ending.locals().supersededBy(null);
        openFrameOf(ending.function()).ifPresent(outer -> outer.supersededBy(null));
    }

    /**
     * Takes the value at the current position and turns it into a result,
     * advancing past whatever it consumed.
     */
    private StepOutcome takeOneStep(Frame frame, Deque<Frame> frames) {
        Value input = frame.current();
        if (trace.isOn()) {
            trace.line(frame.position, input, frame.context);
        }
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
            case GET_PATH -> StepOutcome.of(
                    select(((BlockValue) input).as(Datatype.PATH), frame.context).value());
            case PATH -> evaluatePath(frame, frames, (BlockValue) input);
            case SET_PATH -> evaluateSetPath(frame, (BlockValue) input);
            case ERROR -> throw new Raised((ErrorValue) input);
            default -> input.datatype().isAnyFunction()
                            && input.datatype() != Datatype.OP
                    ? calledWithoutAName(frame, frames, input)
                    : StepOutcome.of(input);
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

    /**
     * Calls a function value that no word named.
     *
     * <p>The name is cleared first, because there is not one: `if (!word) word =
     * ROOT_NONAME;` in the C, and STACK/WORD answers none for such a frame. Left
     * as it was, the frame would report whichever word was called before it.
     */
    private StepOutcome calledWithoutAName(
            Frame frame, Deque<Frame> frames, Value callee) {
        lastWordCalledThrough = "";
        return startCall(frame, frames, callee, List.of());
    }

    private StepOutcome evaluateWord(
            Frame frame, Deque<Frame> frames, WordValue word) {
        ContextSlot slot = resolve(word);
        Value bound = slot.value();
        if (bound.datatype() == Datatype.UNSET) {
            throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
        }
        if (bound.datatype() == Datatype.OP) {
            boolean atTheVeryHead = frame.position - 1 <= 1;
            throw atTheVeryHead
                    ? Raised.of(EvaluationFailure.NO_OP_ARG,
                            "the operator " + word.spelling()
                                    + " has nothing on its left")
                    : Raised.of(EvaluationFailure.MISSING_ARG,
                            "the operator " + word.spelling()
                                    + " has nothing on its left");
        }
        if (!bound.datatype().isAnyFunction()) {
            return StepOutcome.of(bound);
        }
        lastWordCalledThrough = word.spelling();
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

    /**
     * Whether DO of this argument asks for what it finds to be re-evaluated.
     *
     * <p>{@code VAL_SET_OPT(value, OPTS_REVAL)} is set on four of DO's arms and
     * not on the rest: a function value handed over directly, a path, a word,
     * and a get-word. So {@code do 'f} calls F, taking its arguments from after
     * the DO, while {@code do [f]} evaluates the block and answers whatever came
     * out -- even when that is a function value.
     *
     * <p>The difference is the whole of `do 'a` where A is a function of no
     * arguments: R3 answers "OK" and not the function.
     */
    private static boolean asksForReEvaluation(Value argument) {
        return switch (argument) {
            case WordValue named -> named.datatype() == Datatype.WORD
                    || named.datatype() == Datatype.GET_WORD;
            case BlockValue path -> path.datatype() == Datatype.PATH;
            default -> argument.datatype().isAnyFunction();
        };
    }

    /**
     * What a word holds, without calling it.
     *
     * <p>{@code *D_RET = *Get_Var(value);} in DO. A function value is answered
     * rather than called: DO marks it {@code OPTS_REVAL} and the evaluator
     * takes its arguments from what follows the DO, which is a different thing
     * from calling it here with none.
     */
    public Value valueOfWordIn(WordValue word, Context context) {
        return resolve(word.isBound() ? word : word.boundTo(context)).value();
    }

    /**
     * What a path reads, without calling what it finds.
     *
     * <p>{@code Do_Path(&value, 0);} in DO, which is the same walk a path in a
     * block takes.
     */
    public Value valueOfPathIn(BlockValue path, Context context) {
        return select(path, context).value();
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
            if (call.slot() != null && call.slot().isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD, "the field is protected");
            }
            if (call.destination() != null) {
                try {
                    call.destination().accept(call.arguments().get(0));
                } catch (ProtectedFromChange refused) {
                    throw Raised.of(EvaluationFailure.PROTECTED,
                            "the value is protected");
                }
                return StepOutcome.of(call.arguments().get(0));
            }
            call.slot().setValue(call.arguments().get(0));
            return StepOutcome.of(call.arguments().get(0));
        }
        return switch (call.callee()) {
            case NativeValue built -> {
                if (trace.isOn()) {
                    trace.call(built.nativeName(), built, call.arguments());
                }
                Value produced = runNative(built, call.arguments(), frame.context);
                if (trace.isOn()) {
                    trace.answered(built.nativeName(), produced);
                }
                yield built.nativeName().equals("do")
                        && produced.datatype().isAnyFunction()
                        && !call.arguments().isEmpty()
                        && asksForReEvaluation(call.arguments().get(0))
                        ? startCall(frame, frames, produced, List.of())
                        : StepOutcome.of(produced);
            }
            case OperatorValue operator -> StepOutcome.of(
                    invokeUnderlying(operator, call.arguments(), frame.context));
            case FunctionValue function -> {
                nameOfTheCallBeingMade = lastWordCalledThrough;
                if (trace.isOn()) {
                    trace.call(nameOfTheCallBeingMade == null
                            ? "?" : nameOfTheCallBeingMade, function, call.arguments());
                }
                yield runFunction(frames, function, call.arguments(), call.refinements());
            }
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
        nativesCalled++;
        Value produced;
        try {
            produced = behaviour.call(arguments, this, context, built.askedRefinements());
        } catch (ProtectedFromChange refused) {
            throw Raised.of(EvaluationFailure.PROTECTED, built.nativeName());
        } catch (SlotIsProtected refused) {
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

        checkArgumentTypes(function.parameters(),
                new java.util.HashSet<>(refinements), arguments, "function");
        Context locals = Context.childOf(function.closedOver());
        if (!function.closure()) {
            locals.markAsCallFrameOf(function);
        }

        List<Parameter> consuming = function.parameters().stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement()
                        .map(refinements::contains).orElse(true))
                .toList();
        for (int index = 0; index < consuming.size() && index < arguments.size(); index++) {
            locals.set(consuming.get(index).name(), arguments.get(index));
        }

        function.parameters().stream()
                .filter(parameter -> parameter.owningRefinement().isPresent())
                .filter(parameter -> !consuming.contains(parameter))
                .forEach(parameter -> locals.set(parameter.name(), NoneValue.none()));

        function.parameters().stream()
                .filter(parameter -> parameter.kind() == ParameterKind.REFINEMENT)
                .forEach(parameter -> locals.set(
                        parameter.name(),
                        refinements.contains(parameter.name().toLowerCase(Locale.ROOT))
                                ? LogicValue.yes()
                                : NoneValue.none()));

        function.localNames().forEach(name -> locals.set(name, NoneValue.none()));

        push(frames,
                Binder.bindOnly(function.body(), locals, namesOwnedBy(function)),
                locals, function);
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
        List<Parameter> consuming = parameters.stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement()
                        .map(asked::contains).orElse(true))
                .toList();
        for (int index = 0; index < arguments.size() && index < consuming.size(); index++) {
            Parameter parameter = consuming.get(index);
            Value argument = arguments.get(index);
            if (!parameter.accepts(argument.datatype())) {
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
     * here. A function written in REBOL needs none of that: its refinements
     * are parameters and its arity already accounts for them.
     *
     * <p>A refinement no native has raises rather than being dropped. This
     * fell back to the plain native until pinning that {@code parse/all}
     * must raise, which meant every misspelled refinement in every script
     * ran quietly as though it had been left off, and code written against
     * an older REBOL went on looking like it worked.
     *
     * <p>It said the same of a REBOL-defined function and drew the wrong
     * conclusion: needing no <em>lookup</em> is not needing no <em>check</em>.
     * A refinement that is not one of its parameters is not a parameter it can
     * fill, and every one of those ran quietly -- {@code f/nope 1} answering 1,
     * {@code pad/left "ab" 5} padding on the right. That is every function in
     * the borrowed library and every function a script writes.
     */
    private Value refined(Value callee, List<String> refinements) {
        if (refinements.isEmpty()) {
            return callee;
        }
        if (callee instanceof FunctionValue written) {
            for (String refinement : refinements) {
                if (!declaresRefinement(written, refinement)) {
                    throw Raised.of(EvaluationFailure.NO_REFINE,
                            "this function has no /" + refinement + " refinement");
                }
            }
            return callee;
        }
        if (!(callee instanceof NativeValue built)) {
            return callee;
        }
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

    /** Whether a REBOL-defined function takes this refinement. */
    private static boolean declaresRefinement(FunctionValue written, String refinement) {
        return written.parameters().stream()
                .anyMatch(parameter -> parameter.kind() == ParameterKind.REFINEMENT
                        && parameter.name().equalsIgnoreCase(refinement));
    }

    private StepOutcome evaluateSetPath(Frame frame, BlockValue path) {
        if (frame.atEnd()) {
            throw Raised.of(EvaluationFailure.NEED_VALUE,
                    "a set-path has nothing after it to assign");
        }
        frame.pendingCalls.push(PendingCall.assignmentInto(
                written -> writeThroughPath(frame, path, written)));
        return StepOutcome.waiting();
    }

    /**
     * {@code s/field/2: other} where the field is an array of structs.
     *
     * <p>Reading such a field gives a block, because no vector holds structs,
     * and writing one of its slots would ordinarily replace the slot. It must
     * copy bytes instead: the structs in that block point into the parent's
     * own bytes, and replacing a slot would leave the parent unchanged while
     * appearing to have worked.
     *
     * <p>{@code PD_Struct} does this in its {@code STRUCT_TYPE_STRUCT} arm,
     * where it can see both that the block came from a struct field and that a
     * struct is being written. The walk here loses the first of those, so the
     * path is resolved one segment shorter to ask again.
     */
    private void writeIntoOneStructOfAnArray(
            BlockValue elements, IntegerValue which, StructValue given) {
        List<Value> each = elements.remaining();
        int chosen = (int) which.magnitude();
        if (chosen < 1 || chosen > each.size()
                || !(each.get(chosen - 1) instanceof StructValue slot)) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET, Molder.mold(which));
        }
        if (slot.size() != given.size()) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
        }
        slot.changeFrom(given.octets());
    }

    private void writeThroughPath(Frame frame, BlockValue path, Value written) {
        List<Value> segments = path.remaining();
        BlockValue allButLast = BlockValue.path(
                segments.subList(0, segments.size() - 1), Datatype.PATH);
        if (segments.size() == 1) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "a one-segment path has nothing to assign through");
        }
        Value target = select(allButLast, frame.context).value();
        Value lastSegment = segments.get(segments.size() - 1);

        if (segments.size() == 3 && lastSegment instanceof IntegerValue channel
                && select(BlockValue.path(segments.subList(0, 1), Datatype.PATH),
                        frame.context).value() instanceof ImageValue image) {
            Value pixelSegment = selectorFor(segments.get(1), frame.context);
            ImagePath.writeOneChannel(
                    image, pixelSegment, (int) channel.magnitude(), written);
            return;
        }

        if (target instanceof EventValue event && lastSegment instanceof WordValue field
                && segments.size() == 2
                && segments.getFirst() instanceof WordValue holder) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            slot.setValue(EventPath.written(event, field.canonical(), written)
                    .orElseThrow(() -> Raised.of(
                            EvaluationFailure.BAD_PATH_SET, field.spelling())));
            return;
        }
        if (target instanceof GobValue gob && lastSegment instanceof WordValue field) {
            GobPath.write(gob, field, written);
            return;
        }
        if (segments.size() == 3 && segments.get(1) instanceof WordValue pairField
                && select(BlockValue.path(segments.subList(0, 1), Datatype.PATH),
                        frame.context).value() instanceof GobValue holdingPair
                && GobPath.field(holdingPair, pairField) instanceof PairValue half) {
            GobPath.write(holdingPair, pairField,
                    withHalfWritten(half, lastSegment, written));
            return;
        }
        if (contextBehind(target) instanceof Context fields
                && selectorFor(lastSegment, frame.context) instanceof WordValue field) {
            if (!fields.holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            ContextSlot slot = fields.ownSlotFor(field.canonical());
            if (slot.isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD,
                        "the field is protected");
            }
            slot.setValue(written);
            return;
        }
        if (segments.size() >= 3 && target instanceof BlockValue elements
                && written instanceof StructValue given
                && lastSegment instanceof IntegerValue which
                && select(BlockValue.path(
                        segments.subList(0, segments.size() - 2), Datatype.PATH),
                        frame.context).value() instanceof StructValue) {
            writeIntoOneStructOfAnArray(elements, which, given);
            return;
        }
        if (target instanceof BlockValue block) {
            Value selector = selectorFor(lastSegment, frame.context);
            if (BlockPath.isNowhereAtAll(selector)) {
                return;
            }
            int at = BlockPath.positionOf(block, selector)
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                            Molder.mold(selector)));
            replaceInSeries(block, at, written);
            return;
        }
        if (target instanceof StringValue joining && joinsItsPathSegments(joining)) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a path on a " + joining.datatype().literalSpelling()
                            + " names another one rather than a place to write");
        }
        if (target instanceof VectorValue vector) {
            VectorPath.write(vector, selectorFor(lastSegment, frame.context), written);
            return;
        }
        if (target instanceof ImageValue picture) {
            ImagePath.writeThroughPath(
                    picture, selectorFor(lastSegment, frame.context), written);
            return;
        }
        if (target instanceof StructValue struct) {
            StructPath.write(struct, selectorFor(lastSegment, frame.context), written);
            return;
        }
        if (target instanceof SeriesValue series
                && selectorFor(lastSegment, frame.context)
                        instanceof IntegerValue where) {
            replaceInSeries(series,
                    series.index() + (int) where.magnitude() - 1, written);
            return;
        }
        if (target instanceof TupleValue tuple && lastSegment instanceof IntegerValue where
                && segments.getFirst() instanceof WordValue holder && segments.size() == 2) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            slot.setValue(withOctetWritten(tuple, (int) where.magnitude(), written));
            return;
        }
        if (target instanceof BitsetValue set) {
            Value chosen = selectorFor(lastSegment, frame.context);
            Integer bit = switch (chosen) {
                case CharacterValue letter -> letter.codepoint();
                case IntegerValue number -> (int) number.magnitude();
                default -> null;
            };
            if (bit != null) {
                if (set.isProtected()) {
                    throw Raised.of(EvaluationFailure.PROTECTED,
                            "bitset! is protected");
                }
                set.hold(bit, written.isTruthy());
                return;
            }
        }
        if (target instanceof PairValue pair && segments.size() == 2
                && segments.getFirst() instanceof WordValue holder) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            slot.setValue(withHalfWritten(pair, lastSegment, written));
            return;
        }
        if (target instanceof DateValue date && segments.size() == 2
                && segments.getFirst() instanceof WordValue holder) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            slot.setValue(DateParts.written(date, lastSegment, written));
            return;
        }
        if (target instanceof MapValue map) {
            if (map.isProtected()) {
                throw Raised.of(EvaluationFailure.PROTECTED, "map is protected");
            }
            storeUnderKey(map, selectorFor(lastSegment, frame.context), written);
            return;
        }
        if (target instanceof ErrorValue raised && lastSegment instanceof WordValue field) {
            // An error is an object -- boot/types.reb gives error! the object
            // path handler -- so a field write is ordinary rather than a
            // special case anybody had to allow. A field the frame has not got
            // is PE_BAD_SELECT, which reads as invalid-path.
            if (!ErrorValue.FIELDS.contains(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            raised.write(field.canonical(), written);
            return;
        }
        if (target instanceof StringValue address
                && address.datatype() == Datatype.EMAIL
                && selectorFor(lastSegment, frame.context) instanceof WordValue half
                && (half.canonical().equals("user") || half.canonical().equals("host"))) {
            writeEmailPart(address, half.canonical(), written);
            return;
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot assign through " + target.datatype().literalSpelling());
    }

    /**
     * Storing a value under a key, where a key of none stores nothing.
     *
     * <p>Two lines of the C make it so, one in each layer. {@code if
     * (IS_NONE(pvs->select)) return PE_NONE;} in the path handler, and {@code
     * if (IS_NONE(key)) return NOT_FOUND;} in the lookup underneath it, which
     * is the line that also stops a none key from being created.
     *
     * <p>The caller is not told. {@code PE_NONE} is what a read of a missing
     * key answers too, and nothing downstream of it looks at whether the write
     * happened, so the only evidence is that the map is the length it was.
     * Raising here instead would be our invention.
     */
    private static void storeUnderKey(MapValue map, Value key, Value written) {
        if (key instanceof NoneValue) {
            return;
        }
        map.put(key, written);
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

    /**
     * A path index as PICK counts one, which reaches behind the position.
     *
     * <p>A series carries a position, and a negative index counts back from
     * it: {@code s: tail "ab"} makes {@code s/-1} the last character and
     * {@code s/-2} the one before. There is no nought, so counting runs
     * ...-2, -1, 1, 2... and the negative side is one shorter than it looks.
     *
     * <p>PICK already did this and a path did not, so {@code pick s -2} and
     * {@code s/-2} disagreed about the same series -- and a path is the form
     * a caller reaches for first.
     */
    private static long countedFromTheSeriesPosition(long index) {
        return index < 0 ? index + 1 : index;
    }

    /** Walks the segments, gathering refinements once a function is reached. */
    private Selection select(BlockValue path, Context context) {
        List<Value> segments = path.remaining();
        if (segments.isEmpty()) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, "an empty path selects nothing");
        }
        Value current = selectFirst(segments.get(0), context);
        List<String> refinements = new ArrayList<>();
        List<String> named = new ArrayList<>();

        for (int index = 1; index < segments.size(); index++) {
            Value segment = segments.get(index);
            if (current.datatype().isAnyFunction()) {
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
        if (selector instanceof DecimalValue fractional) {
            selector = IntegerValue.of((long) fractional.quantity());
        }
        if (target instanceof MapValue map) {
            return map.select(selector);
        }
        if (target instanceof TupleValue tuple && selector instanceof IntegerValue position) {
            long at = position.magnitude();
            return at < 1 || at > tuple.shownCount()
                    ? NoneValue.none()
                    : IntegerValue.of(tuple.octetAt((int) at));
        }
        if (target instanceof TimeValue time) {
            return partOfATime(time, selector);
        }
        if (target instanceof DateValue date) {
            return DateParts.of(date, selector);
        }
        if (target instanceof BitsetValue set && selector instanceof CharacterValue letter) {
            return LogicValue.of(set.holds(letter.codepoint()));
        }
        if (target instanceof PairValue pair) {
            Optional<Value> half = switch (selector) {
                case IntegerValue position -> pair.halfAt((int) position.magnitude());
                case WordValue name -> pair.half(name.canonical());
                default -> Optional.empty();
            };
            return half.orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                    "a pair has an x half, a y half and an area, and nothing else"));
        }
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
        if (target instanceof PortValue port && selector instanceof WordValue field) {
            if (!port.context().holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            return port.context().ownSlotFor(field.canonical()).value();
        }
        if (target instanceof ModuleValue module && selector instanceof WordValue field) {
            if (!module.context().holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            return module.context().ownSlotFor(field.canonical()).value();
        }
        if (target instanceof StringValue path && joinsItsPathSegments(path)) {
            return joinedOntoPath(path, selector);
        }
        if (target instanceof StringValue text && selector instanceof WordValue named) {
            return switch (named.canonical()) {
                case "length" -> IntegerValue.of(text.lengthFromHere());
                case "size" -> IntegerValue.of(
                        text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                case "width" -> IntegerValue.of(terminalWidthOf(text.text()));
                case "user", "host" -> emailPartOf(text, named.canonical());
                default -> throw Raised.of(
                        EvaluationFailure.INVALID_PATH, named.spelling());
            };
        }
        if (target instanceof BlockValue block) {
            return BlockPath.read(block, selector);
        }
        if (target instanceof ImageValue image) {
            return ImagePath.read(image, selector);
        }
        if (target instanceof GobValue gob) {
            return GobPath.read(gob, selector);
        }
        if (target instanceof HandleValue handle) {
            if (!(selector instanceof WordValue named)) {
                throw Raised.of(EvaluationFailure.INVALID_PATH,
                        "a handle is selected by name, not by "
                                + selector.datatype().literalSpelling());
            }
            if (handle.isContext() && named.canonical().equals("type")) {
                return WordValue.of(handle.typeName());
            }
            return NoneValue.none();
        }
        if (target instanceof EventValue event) {
            return EventPath.read(event, selector,
                    hostPort("event"), hostPort("callback"), hostPort("input"));
        }
        if (target instanceof VectorValue vector) {
            return VectorPath.read(vector, selector);
        }
        if (target instanceof StructValue struct) {
            return StructPath.read(struct, selector);
        }
        if (target instanceof SeriesValue series && selector instanceof IntegerValue position) {
            long index = countedFromTheSeriesPosition(position.magnitude());
            if (index < 1 - (series.index() - 1) || index > series.lengthFromHere()) {
                return NoneValue.none();
            }
            return switch (series) {
                case StringValue text -> CharacterValue.of(
                        text.storage().at(text.index() + (int) index - 1));
                case BinaryValue bytes -> IntegerValue.of(
                        bytes.storage().at(bytes.index() + (int) index - 1));
                case BlockValue block -> block.storage().at(
                        block.index() + (int) index - 1);
                case ImageValue pixels -> ImagePath.read(pixels, selector);
                case GobValue gob -> GobPath.read(gob, selector);
                case VectorValue vector -> VectorPath.read(vector, selector);
            };
        }
        if (target instanceof BitsetValue members) {
            return Natives.bitsetHoldsForAPath(members, selector);
        }
        if (target instanceof CharacterValue character
                && selector instanceof WordValue asked) {
            switch (asked.canonical()) {
                case "width" -> {
                    return IntegerValue.of(
                            CharacterColumns.widthOf(character.codepoint()));
                }
                case "size" -> {
                    return IntegerValue.of(
                            CharacterColumns.utf8SizeOf(character.codepoint()));
                }
                default -> { }
            }
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot select " + selector.datatype().literalSpelling()
                        + " from " + target.datatype().literalSpelling());
    }

    /**
     * The slots behind a value, for the four datatypes that have them.
     *
     * <p>{@code boot/types.reb} gives an object, a module, an error and a port the
     * same {@code object} path handler, so all four read and write their fields the
     * same way. Three of them are here: an error's fields are a record rather than a
     * context, which is why it is missing and why writing one is still a gap.
     */
    private static Context contextBehind(Value target) {
        return switch (target) {
            case ObjectValue object -> object.context();
            case PortValue port -> port.context();
            case ModuleValue module -> module.context();
            default -> null;
        };
    }

    /**
     * A field of {@code system/ports}, or none.
     *
     * <p>Three of an event's seven models answer one of these for {@code e/port}:
     * `*val = *Get_System(SYS_PORTS, PORTS_EVENT)` and the same for the callback
     * and console ports. Read live rather than resolved once, because the host
     * fills those fields after the boot and a script can read one before and after.
     *
     * <p>All three are none until a window system fills them, in a stock console
     * 3.22.1 as much as here. Rebol's own event test guards its port case with
     * `if system/ports/event [...]` for that reason.
     */
    private Value hostPort(String named) {
        if (!systemContext.knows("system")) {
            return NoneValue.none();
        }
        if (!(systemContext.slotFor("system").value() instanceof ObjectValue system)
                || !system.context().holds("ports")) {
            return NoneValue.none();
        }
        if (!(system.context().ownSlotFor("ports").value() instanceof ObjectValue ports)
                || !ports.context().holds(named)) {
            return NoneValue.none();
        }
        return ports.context().ownSlotFor(named).value();
    }

    /**
     * A part of a time, named or numbered.
     *
     * <p>{@code PD_Time} takes the two kinds of selector down different roads
     * and they end differently. A word that is not one of the three parts is
     * {@code PE_BAD_SELECT}, which reads as invalid-path; a number outside the
     * three is {@code PE_NONE}, which reads as none. So {@code t/100} is
     * nothing and {@code t/hours} is a mistake.
     *
     * <p>The seconds are a whole number only while they are whole. Once there
     * is a fraction the answer is a decimal --
     * {@code if (tf.n == 0) SET_INTEGER(...) else SET_DECIMAL(...)}.
     */
    private static Value partOfATime(TimeValue time, Value selector) {
        long seconds = Math.abs(time.nanoseconds()) / NANOSECONDS_IN_A_SECOND;
        long fraction = Math.abs(time.nanoseconds()) % NANOSECONDS_IN_A_SECOND;
        int which = switch (selector) {
            case IntegerValue position -> (int) position.magnitude();
            case WordValue named -> switch (named.canonical()) {
                case "hour" -> 1;
                case "minute" -> 2;
                case "second" -> 3;
                default -> throw Raised.of(EvaluationFailure.INVALID_PATH,
                        named.spelling());
            };
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH,
                    Molder.mold(selector));
        };
        return switch (which) {
            case 1 -> IntegerValue.of(seconds / 3600);
            case 2 -> IntegerValue.of(seconds / 60 % 60);
            case 3 -> fraction == 0
                    ? IntegerValue.of(seconds % 60)
                    : DecimalValue.of(seconds % 60 + (double) fraction / NANOSECONDS_IN_A_SECOND);
            default -> NoneValue.none();
        };
    }

    private static final long NANOSECONDS_IN_A_SECOND = 1_000_000_000L;

    private static void replaceInSeries(SeriesValue series, int at, Value value) {
        switch (series) {
            case BlockValue block -> block.storage().set(at, value);
            case StringValue text -> text.storage().set(at,
                    value instanceof CharacterValue character
                            ? character.codepoint()
                            : Molder.form(value).codePointAt(0));
            case BinaryValue bytes -> bytes.storage().set(at, octetFrom(value));
            case ImageValue image -> ImagePath.write(image, at, value);
            case GobValue gob -> GobPath.poke(gob, at, value);
            case VectorValue vector -> vector.storage().set(at,
                    VectorPath.storedFormOf(vector.kind(), value));
        }
    }

    /**
     * A value as a byte, refusing a number that will not fit in one.
     *
     * <p>Two different refusals, and the C makes the distinction on purpose.
     * A number too big for a byte is out of range: `if (c > 0xff)
     * Trap_Range(val);`. A negative one never reaches that line, because it
     * fails the check above it and comes back as `PE_BAD_SET` -- the value is
     * the wrong thing for the place rather than a byte that is too large.
     */
    private static int octetFrom(Value value) {
        if (!(value instanceof IntegerValue number)) {
            return 0;
        }
        long wanted = number.magnitude();
        if (wanted < 0) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        if (wanted > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        return (int) wanted;
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
        int kept = position > tuple.shownCount() ? position : tuple.segmentCount();
        return TupleValue.of(java.util.Arrays.copyOf(octets, kept));
    }

    private record Selection(
            Value value, List<String> refinements, List<String> named) {
    }

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

    /**
     * Whether a path segment lengthens this value rather than reading into it.
     *
     * <p>The Path column of {@code boot/types.reb} names a handler of their
     * own for exactly two datatypes: {@code file} for a file and for a URL,
     * and {@code *} -- the string typeclass -- for a string, an email and a
     * tag.
     */
    private static boolean joinsItsPathSegments(StringValue text) {
        return text.datatype() == Datatype.FILE || text.datatype() == Datatype.URL;
    }

    /**
     * A file or URL with one more segment on the end, from {@code PD_File}.
     *
     * <p>A slash goes in between unless the left side already ends with one,
     * and an empty left side gets one too -- so joining onto nothing gives a
     * rooted path. One leading slash or backslash on the segment is dropped,
     * which is what keeps a double slash out of the middle. The answer takes
     * its datatype from the left, thus a URL stays a URL.
     *
     * <p>A segment that is not text is molded, so a number joins as its digits
     * and a word as its spelling. That is why {@code %a/length} is a file
     * named length: a file has no path form that asks about its own text.
     */
    private static Value joinedOntoPath(StringValue path, Value segment) {
        StringBuilder built = new StringBuilder(path.text());
        if (built.isEmpty() || built.charAt(built.length() - 1) != '/') {
            built.append('/');
        }
        String added = segment instanceof StringValue text
                ? text.text()
                : Molder.mold(segment);
        built.append(added.startsWith("/") || added.startsWith("\\")
                ? added.substring(1)
                : added);
        return StringValue.of(built.toString(), path.datatype());
    }

    /**
     * How many terminal columns a string occupies.
     *
     * <p>{@code Length_As_Terminal_Width} in the C. Not the same as the
     * codepoint count: an East Asian wide character takes two columns and a
     * combining mark takes none, which is what makes a padded field line up
     * when the text is not Latin.
     */
    private static int terminalWidthOf(String text) {
        return TerminalWidth.of(text.codePoints().toArray());
    }

    /**
     * The user or host half of an email, split at the at-sign.
     *
     * <p>Only an email answers these, which the C checks first:
     * {@code if (!IS_EMAIL(pvs->value)) return PE_BAD_SELECT;}. A host half
     * that is not there answers none, and a user half that is not there is
     * the whole string.
     */
    private static Value emailPartOf(StringValue text, String half) {
        if (text.datatype() != Datatype.EMAIL) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, half);
        }
        String whole = text.head().text();
        int at = whole.indexOf('@');
        if (half.equals("host")) {
            return at < 0
                    ? NoneValue.none()
                    : StringValue.of(whole.substring(at + 1));
        }
        return StringValue.of(at < 0 ? whole : whole.substring(0, at));
    }

    /**
     * Writing half an address back, which rewrites the storage in place.
     *
     * <p>{@code Modify_String(A_CHANGE, ...)} over the half being replaced, so
     * every other name for the same address sees the new one. Setting the host
     * of an address that has no {@code @} adds one: the C appends the
     * character and then appends the value behind it, which is how
     * {@code e/host: %rebol.tech} turns a bare word into an address.
     */
    private static void writeEmailPart(StringValue text, String half, Value written) {
        if (text.datatype() != Datatype.EMAIL) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET, half);
        }
        String whole = text.text();
        int at = whole.indexOf('@');
        String replacement = Molder.form(written);
        String rebuilt = half.equals("host")
                ? (at < 0 ? whole + "@" + replacement
                        : whole.substring(0, at + 1) + replacement)
                : replacement + (at < 0 ? "" : whole.substring(at));
        StringStorage storage = text.storage();
        while (storage.length() > 0) {
            storage.removeAt(1);
        }
        rebuilt.codePoints().forEach(storage::append);
    }

    /**
     * Fills a frame from a positional argument list, as APPLY supplies one.
     *
     * <p>{@code Apply_Block}'s validation loop, which does three things a
     * plain assignment does not:
     *
     * <pre>
     * if (IS_REFINEMENT(args)) {
     *     if (IS_FALSE(val)) {
     *         SET_NONE(val);
     *         while (TRUE) {          // and none out the args that follow
     *             val++; args++;
     *             if (IS_END(args) || IS_REFINEMENT(args)) break;
     *             SET_NONE(val);
     *         }
     *         continue;
     *     }
     *     SET_TRUE(val);
     * }
     * </pre>
     *
     * <p>A refinement holds logic true or none, never the value that was
     * passed for it, and a refinement that is off makes its own arguments
     * none. An ordinary argument nobody supplied holds unset, which is a
     * value the body can test.
     *
     * <p>Giving an unsupplied refinement unset instead is what stopped
     * Rebol's IMPORT: LOAD opens with `assert/type [local none!]`, and the
     * `/local` refinement read unset.
     */
    private static void bindArgumentsPositionally(
            Context frame, List<Parameter> parameters, List<Value> arguments) {

        for (int at = 0; at < parameters.size(); at++) {
            Parameter parameter = parameters.get(at);
            Value supplied = at < arguments.size()
                    ? arguments.get(at)
                    : UnsetValue.unset();
            if (parameter.kind() != ParameterKind.REFINEMENT) {
                frame.set(parameter.name(), supplied);
                continue;
            }
            boolean asked = supplied.datatype() != Datatype.UNSET && supplied.isTruthy();
            frame.set(parameter.name(),
                    asked ? LogicValue.of(true) : NoneValue.none());
            if (asked) {
                continue;
            }
            while (at + 1 < parameters.size()
                    && parameters.get(at + 1).kind() != ParameterKind.REFINEMENT) {
                at++;
                frame.set(parameters.get(at).name(), NoneValue.none());
            }
        }
    }

    /**
     * The names a function owns: its arguments, its refinements and its locals.
     *
     * <p>What a call rebinds, and nothing else. Every other word in the body
     * keeps the binding it was written with.
     */
    private static java.util.Set<String> namesOwnedBy(FunctionValue function) {
        java.util.Set<String> owned = new java.util.HashSet<>();
        function.parameters().forEach(
                parameter -> owned.add(parameter.name().toLowerCase(Locale.ROOT)));
        function.localNames().forEach(
                name -> owned.add(name.toLowerCase(Locale.ROOT)));
        return owned;
    }
}
