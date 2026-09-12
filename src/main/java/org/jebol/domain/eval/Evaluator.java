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

    private Context runtimeContext;
    private final int maximumDepth;
    private final Interruption interruption;
    private final int checkEvery;

    private FilePort files = FilePort.none();

    private EnvironmentPort environment = EnvironmentPort.none();

    private ImagePort images = ImagePort.none();

    private ConsolePort console = ConsolePort.none();

    private WindowPort windows = WindowPort.none();
    private ScreenPort screen = ScreenPort.none();

    private ProcessPort processes = ProcessPort.none();

    private NetworkPort network = NetworkPort.none();
    private BundledModules bundledModules = BundledModules.none();
    private int stepsSinceLastCheck;

    private int framesOpen;

    private final Trace trace = new Trace();

    /** The tracer, for TRACE to set the level on. */
    public Trace tracing() {
        return trace;
    }
    private long valuesWalked;

    private long nativesCalled;
    private long functionsCalled;

    public long nativesCalled() {
        return nativesCalled;
    }

    public long functionsCalled() {
        return functionsCalled;
    }

    record OpenCall(String name, FunctionValue function, Context locals) {

        List<String> slotNames() {
            List<String> names = new ArrayList<>();
            function.parameters().forEach(parameter -> names.add(parameter.name()));
            names.addAll(function.localNames());
            return names;
        }
    }

    private final Deque<OpenCall> functionsBeingRun = new ArrayDeque<>();

    private String nameOfTheCallBeingMade = "";

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

    private final Map<UsageLimit, Long> limitsRecorded =
            new java.util.EnumMap<>(UsageLimit.class);

    java.util.Optional<Long> limitRecorded(UsageLimit limit) {
        return java.util.Optional.ofNullable(limitsRecorded.get(limit));
    }

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

    /**
     * The modules bundled with this build, which BUNDLED reads through.
     *
     * <p>No grant guards it, unlike the filesystem and the network: nothing is
     * reached, and a build cannot be asked for something it does not bundle.
     */
    public BundledModules bundledModules() {
        return bundledModules;
    }

    public void useBundledModules(BundledModules bundled) {
        this.bundledModules = bundled;
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

    /** The image codec the host carries. None, by default. */
    public ImagePort images() {
        return images;
    }

    /** Gives the script an image codec to reach. */
    public void useImages(ImagePort port) {
        this.images = port;
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
                BlockValue running = theBodyThisCallRuns(function, locals);
                theFrameThisCallTakesOverFrom(function).supersededBy(locals);
                functionsBeingRun.push(new OpenCall("", function, locals));
                try {
                    yield evaluateOrRaise(running, locals);
                } catch (ReturnSignal returned) {
                    yield returned.value();
                } finally {
                    handBackTheFrameTakenOverBy(functionsBeingRun.pop());
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
        walk(code, context, 1,
                (produced, startedAt, stoppedBefore) -> results.add(produced));
        return results;
    }

    /**
     * The same walk, keeping the line-break mark each result is entitled to.
     *
     * <p>A mark belongs to the value rather than to the position, so REDUCE
     * keeps one exactly where the value it produced is the item as written.
     * Everything else -- a word, a paren, a call -- hands back something
     * worked out, and a worked-out value has no mark of its own.
     *
     * <p>Deciding it here rather than in REDUCE is what makes it decidable at
     * all: by the time the results are a list, which source item each one came
     * from is gone.
     */
    public BlockValue evaluateEachKeepingTheLineShape(
            BlockValue code, Context context) {

        BlockStorage built = new BlockStorage();
        walk(code, context, 1, (produced, startedAt, stoppedBefore) -> {
            built.append(produced);
            if (theItemAsWritten(code, startedAt, stoppedBefore)) {
                built.setLineBreakAt(built.length(),
                        code.storage().breaksLineAt(startedAt));
            }
            return true;
        });
        return new BlockValue(built, 1, Datatype.BLOCK);
    }

    private static boolean theItemAsWritten(
            BlockValue code, int startedAt, int stoppedBefore) {

        return stoppedBefore == startedAt + 1
                && startedAt >= 1
                && startedAt <= code.storageLength()
                && !WORKS_SOMETHING_OUT.contains(
                        code.storage().at(startedAt).datatype());
    }

    private static final Set<Datatype> WORKS_SOMETHING_OUT = EnumSet.of(
            Datatype.WORD, Datatype.SET_WORD, Datatype.GET_WORD, Datatype.LIT_WORD,
            Datatype.PATH, Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH,
            Datatype.PAREN, Datatype.FUNCTION, Datatype.CLOSURE, Datatype.NATIVE,
            Datatype.ACTION, Datatype.OP, Datatype.COMMAND);

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
        Value last = walk(code, context, 1, (produced, startedAt, stoppedBefore) -> {
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
        frame.sink = (produced, startedAt, stoppedBefore) -> false;
        Value produced = walkFrames(frames);
        return new Step(produced, frame.position);
    }

    private Value walk(BlockValue code, Context context, int depth) {
        return walk(code, context, depth, null);
    }

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

    @FunctionalInterface
    private interface ResultSink {
        boolean accept(Value produced, int startedAt, int stoppedBefore);
    }

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
            if (frame.expressionStartedAt < 0) {
                frame.expressionStartedAt = frame.position;
            }

            if (frame.stopped || frame.atEnd()) {
                if (!frame.stopped && !frame.pendingCalls.isEmpty()) {
                    if (frame.pendingCalls.peek().takesTheNextValueAsWritten()) {
                        deliver(frame, UnsetValue.unset(), frames);
                        continue;
                    }
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
            } catch (Raised raised) {
                throw sayingWhereItCameFrom(raised, frames);
            }
        }
    }

    private Raised sayingWhereItCameFrom(Raised raised, Deque<Frame> frames) {
        List<Value> chain = new ArrayList<>();
        Value nearest = null;
        for (Frame open : frames) {
            List<PendingCall> deepestFirst = new ArrayList<>();
            if (open.theCallNearAndWhereAreAbout != null) {
                deepestFirst.add(open.theCallNearAndWhereAreAbout);
            }
            deepestFirst.addAll(open.pendingCalls);
            for (PendingCall waiting : deepestFirst) {
                if (waiting.calledThrough() != null) {
                    chain.add(WordValue.of(waiting.calledThrough()));
                }
                if (nearest == null && waiting.startedAt() >= 0) {
                    nearest = open.code.atIndex(waiting.startedAt());
                }
            }
        }
        ErrorValue said = raised.error();
        if (nearest != null && said.near().isEmpty()) {
            said = said.near(nearest);
        }
        if (!chain.isEmpty()) {
            List<Value> already = said.whereChain()
                    .filter(BlockValue.class::isInstance)
                    .map(held -> ((BlockValue) held).remaining())
                    .orElseGet(List::of);
            List<Value> together = new ArrayList<>(already);
            together.addAll(chain);
            said = said.raisedThrough(BlockValue.block(together));
        }
        return said == raised.error() ? raised : new Raised(said);
    }

    private void push(Deque<Frame> frames, BlockValue code, Context context) {
        push(frames, code, context, null);
    }

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
            theFrameThisCallTakesOverFrom(being).supersededBy(context);
            functionsBeingRun.push(new OpenCall(nameOfTheCallBeingMade, being, context));
            nameOfTheCallBeingMade = "";
            lastWordCalledThrough = "";
        }
    }

    private java.util.Optional<Context> openFrameOf(FunctionValue function) {
        return functionsBeingRun.stream()
                .filter(call -> call.function() == function)
                .map(OpenCall::locals)
                .findFirst();
    }

    private void handBackTheFrameTakenOverBy(OpenCall ending) {
        ending.locals().supersededBy(null);
        theFrameThisCallTakesOverFrom(ending.function()).supersededBy(null);
    }

    private Context theFrameThisCallTakesOverFrom(FunctionValue function) {
        return openFrameOf(function).orElseGet(function::declaredWords);
    }

    private static BlockValue theBodyThisCallRuns(
            FunctionValue function, Context locals) {

        return function.closure()
                ? Binder.rebindWhatNamedTheFunction(
                        function.body(), function.declaredWords(), locals)
                : function.body();
    }

    private StepOutcome takeOneStep(Frame frame, Deque<Frame> frames) {
        Value input = frame.current();
        if (trace.isOn()) {
            trace.line(frame.position, input, frame.context);
        }
        frame.startedThisValueAt = frame.position;
        frame.theCallNearAndWhereAreAbout = null;
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

    private void deliver(Frame frame, Value produced, Deque<Frame> frames) {
        Value carrying = produced;
        while (true) {
            PendingCall waiting = frame.pendingCalls.peek();
            boolean feedingAnOperator = waiting != null && waiting.isInfix();
            if (!feedingAnOperator) {
                Optional<OperatorValue> operator = operatorAt(frame);
                if (operator.isPresent()) {
                    int wroteTheOperatorAt = frame.position;
                    frame.advance();
                    PendingCall infix =
                            PendingCall.infix(operator.orElseThrow(), carrying);
                    infix.startedAt(wroteTheOperatorAt,
                            nameWrittenAt(frame, wroteTheOperatorAt));
                    frame.pendingCalls.push(infix);
                    return;
                }
            }
            if (frame.pendingCalls.isEmpty()) {
                frame.lastResult = carrying;
                int startedAt = frame.expressionStartedAt;
                frame.expressionStartedAt = -1;
                if (frame.sink != null
                        && !frame.sink.accept(carrying, startedAt, frame.position)) {
                    frame.stopped = true;
                }
                return;
            }
            waiting.accept(carrying);
            if (!waiting.isSatisfied()) {
                return;
            }
            frame.pendingCalls.pop();
            frame.theCallNearAndWhereAreAbout = waiting;
            StepOutcome outcome = invoke(frame, waiting, frames);
            if (itIsStillRunningHavingPushedABody(outcome)) {
                return;
            }
            StepOutcome.Produced invoked = (StepOutcome.Produced) outcome;
            frame.theCallNearAndWhereAreAbout = null;
            carrying = invoked.value();
        }
    }

    private static boolean itIsStillRunningHavingPushedABody(StepOutcome outcome) {
        return !(outcome instanceof StepOutcome.Produced);
    }

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

    private StepOutcome startCall(
            Frame frame, Deque<Frame> frames, Value callee, List<String> refinements) {
        return startCall(frame, frames, callee, refinements, refinements);
    }

    private StepOutcome startCall(
            Frame frame, Deque<Frame> frames, Value callee,
            List<String> refinements, List<String> named) {
        PendingCall call = PendingCall.prefix(callee, refinements, named);
        call.startedAt(frame.startedThisValueAt,
                nameWrittenAt(frame, frame.startedThisValueAt));
        if (aCallNeedingNothingNeverReachesThePendingStack(call)) {
            frame.theCallNearAndWhereAreAbout = call;
            StepOutcome outcome = invoke(frame, call, frames);
            if (outcome instanceof StepOutcome.Produced) {
                frame.theCallNearAndWhereAreAbout = null;
            }
            return outcome;
        }
        frame.pendingCalls.push(call);
        return StepOutcome.waiting();
    }

    private static boolean aCallNeedingNothingNeverReachesThePendingStack(
            PendingCall call) {
        return call.isSatisfied();
    }

    private static void refuseSelfAsAnInvalidPathRatherThanAGuardedSlot(
            Value lastSegment) {
        if (lastSegment instanceof WordValue named
                && named.canonical().equals("self")) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "self is what a context calls itself and cannot be assigned");
        }
    }

    private static String nameWrittenAt(Frame frame, int position) {
        if (position < frame.code.index() || position > frame.code.storageLength()) {
            return null;
        }
        Value written = frame.code.storage().at(position);
        if (written instanceof WordValue word) {
            return word.spelling();
        }
        if (written instanceof BlockValue path && path.datatype() == Datatype.PATH
                && path.remaining().getFirst() instanceof WordValue first) {
            return first.spelling();
        }
        return null;
    }

    private StepOutcome invoke(Frame frame, PendingCall call, Deque<Frame> frames) {
        if (call.isAssignment()) {
            if (call.slot() != null && call.slot().isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD, "the field is protected");
            }
            if (call.destination() != null) {
                try {
                    call.destination().accept(call.argumentsInDeclaredOrder().get(0));
                } catch (ProtectedFromChange refused) {
                    throw Raised.of(EvaluationFailure.PROTECTED,
                            "the value is protected");
                }
                return StepOutcome.of(call.argumentsInDeclaredOrder().get(0));
            }
            call.slot().setValue(call.argumentsInDeclaredOrder().get(0));
            return StepOutcome.of(call.argumentsInDeclaredOrder().get(0));
        }
        return switch (call.callee()) {
            case NativeValue built -> {
                if (trace.isOn()) {
                    trace.call(built.nativeName(), built, call.argumentsInDeclaredOrder());
                }
                Value produced = runNative(built, call.argumentsInDeclaredOrder(), frame.context);
                if (trace.isOn()) {
                    trace.answered(built.nativeName(), produced);
                }
                yield built.nativeName().equals("do")
                        && produced.datatype().isAnyFunction()
                        && !call.argumentsInDeclaredOrder().isEmpty()
                        && asksForReEvaluation(call.argumentsInDeclaredOrder().get(0))
                        ? startCall(frame, frames, produced, List.of())
                        : StepOutcome.of(produced);
            }
            case OperatorValue operator -> StepOutcome.of(
                    invokeUnderlying(operator, call.argumentsInDeclaredOrder(), frame.context));
            case FunctionValue function -> {
                nameOfTheCallBeingMade = lastWordCalledThrough;
                if (trace.isOn()) {
                    trace.call(nameOfTheCallBeingMade == null
                            ? "?" : nameOfTheCallBeingMade, function, call.argumentsInDeclaredOrder());
                }
                yield runFunction(frames, function, call.argumentsInDeclaredOrder(), call.refinements());
            }
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    call.callee().datatype().literalSpelling() + " is not callable");
        };
    }

    private Value invokeUnderlying(
            OperatorValue operator, List<Value> arguments, Context context) {
        return switch (operator.underlying()) {
            case NativeValue built -> runNative(built, arguments, context);
            case FunctionValue function -> applyFunction(function, arguments);
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

        push(frames, theBodyThisCallRuns(function, locals), locals, function);
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
        refuseAPathIntoSomethingWithNoParts(path, target);

        refuseSelfAsAnInvalidPathRatherThanAGuardedSlot(lastSegment);

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
            if (BlockPath.isNowhereAtAllSoAWriteQuietlyDoesNothing(selector)) {
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

    private static void storeUnderKey(MapValue map, Value key, Value written) {
        if (key instanceof NoneValue) {
            return;
        }
        map.put(key, written);
    }

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

    private static long countedFromTheSeriesPosition(long index) {
        return index < 0 ? index + 1 : index;
    }

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
            refuseAPathIntoSomethingWithNoParts(path, current);
            current = selectWith(current, selectorFor(segment, context));
        }
        return new Selection(current, List.copyOf(refinements), List.copyOf(named));
    }

    private static void refuseAPathIntoSomethingWithNoParts(
            BlockValue path, Value current) {

        if (HAVE_NO_PARTS_TO_SELECT.contains(current.datatype())) {
            throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                    path, DatatypeValue.of(current.datatype()));
        }
    }

    private static final java.util.Set<Datatype> HAVE_NO_PARTS_TO_SELECT =
            java.util.Set.of(
                    Datatype.UNSET, Datatype.NONE, Datatype.LOGIC,
                    Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
                    Datatype.MONEY, Datatype.DATATYPE, Datatype.TYPESET,
                    Datatype.WORD, Datatype.SET_WORD, Datatype.GET_WORD,
                    Datatype.LIT_WORD, Datatype.REFINEMENT, Datatype.ISSUE);

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
    public Value hostPort(String named) {
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
            case GobValue gob ->
                    GobPath.pokeWhichInsertsRatherThanReplaces(gob, at, value);
            case VectorValue vector -> vector.storage().set(at,
                    VectorPath.storedFormOf(vector.kind(), value));
        }
    }

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

        private int expressionStartedAt = -1;

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

        private int startedThisValueAt;

        private PendingCall theCallNearAndWhereAreAbout;
    }

    private static boolean joinsItsPathSegments(StringValue text) {
        return text.datatype() == Datatype.FILE || text.datatype() == Datatype.URL;
    }

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

    private static int terminalWidthOf(String text) {
        return TerminalWidth.of(text.codePoints().toArray());
    }

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

    private static java.util.Set<String> namesOwnedBy(FunctionValue function) {
        java.util.Set<String> owned = new java.util.HashSet<>();
        function.parameters().forEach(
                parameter -> owned.add(parameter.name().toLowerCase(Locale.ROOT)));
        function.localNames().forEach(
                name -> owned.add(name.toLowerCase(Locale.ROOT)));
        return owned;
    }
}
