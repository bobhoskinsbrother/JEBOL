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
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.ModuleValue;
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
import org.jebol.domain.value.CharacterColumns;
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
                if (!function.closure()) {
                    locals.markAsCallFrameOf(function);
                }
                List<Parameter> parameters = function.parameters();
                // Every parameter is defined, whether or not a value came
                // for it. A name the body mentions and nobody supplied
                // holds unset, which is a value; leaving it undefined
                // makes the body fail on a missing word instead.
                // The /local words too. The walk defines them and this did
                // not, so a function reached this way failed on its own first
                // local -- and MAKE-PORT* declares three.
                function.localNames().forEach(
                        name -> locals.set(name, NoneValue.none()));
                bindArgumentsPositionally(locals, parameters, arguments);
                try {
                    yield evaluateOrRaise(
                            Binder.bindOnly(function.body(), locals,
                                    namesOwnedBy(function)),
                            locals);
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

    /** Evaluates the single expression starting at the block's position. */
    public Step evaluateNextOrRaise(BlockValue code, Context context) {
        if (code.atTail()) {
            return new Step(UnsetValue.unset(), code.index());
        }
        Frame frame = new Frame(code, context, 1);
        Deque<Frame> frames = new ArrayDeque<>();
        frames.push(frame);
        frame.sink = produced -> false;
        Value produced = unsignalled(() -> walkFrames(frames));
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
            // The outermost block is depth zero, as `Eval_Depth()` counts it,
            // so a trace of a top-level expression is not indented.
            trace.nowAtDepth(frames.size() - 1);

            if (frame.stopped || frame.atEnd()) {
                if (!frame.stopped && !frame.pendingCalls.isEmpty()) {
                    throw Raised.of(EvaluationFailure.NO_ARG,
                            "the block ended while a call was still gathering arguments");
                }
                Value finished = frame.lastResult;
                if (frame.functionBody && !functionsBeingRun.isEmpty()) {
                    functionsBeingRun.pop();
                }
                frames.pop();
                framesOpen = frames.size();
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
            functionsBeingRun.push(new OpenCall(nameOfTheCallBeingMade, being, context));
            nameOfTheCallBeingMade = "";
            lastWordCalledThrough = "";
        }
    }

    /**
     * Takes the value at the current position and turns it into a result,
     * advancing past whatever it consumed.
     */
    private StepOutcome takeOneStep(Frame frame, Deque<Frame> frames) {
        Value input = frame.current();
        // `if (Trace_Flags) Trace_Line(block, index, value);` sits exactly here
        // in Do_Next, before the value is evaluated: what TRACE prints is what
        // the evaluator is about to do, not what it did.
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
            // A function value standing in a block is called, exactly as a
            // word holding one is. The Evaluator column of boot/types.reb says
            // `function` for a native, an action, a function, a closure and a
            // command, and `ET_FUNCTION` is the branch that makes the call --
            // the same branch a word arrives at, since ET_WORD ends with `if
            // (ANY_FUNC(value)) goto reval;`.
            //
            // Rebol's own library leans on this. ALL-OF and ANY-OF build their
            // bodies with `reduce [:unless to paren! test ...]`, so what
            // FOREACH runs has the UNLESS value at the head and no word
            // anywhere. Left inert, the whole block reduced to its last value
            // and both functions answered true for everything.
            //
            // An operator is the exception, and the C makes it one in the same
            // table: its evaluator is `operator`, so it takes the value on its
            // left rather than the values on its right. One at the head of an
            // expression has nothing on its left.
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
            // `if (DSP <= 0 || index == 0) Trap1(RE_NO_OP_ARG, word);` -- the
            // friendly error names the operator only at the very head of the
            // series. Reached mid-series, the C grabs a bogus stack value and
            // the imbalance surfaces as missing-arg, so `do next [1 <> 0]`
            // says missing-arg where `do "<> 0"` says no-op-arg.
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
        // The word the call is being made through, for STACK/WORD. Recorded
        // here because this is the only place that knows it: a PendingCall
        // carries the function value, and a function value has no name.
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
            // A protected slot refuses as a REBOL error rather than as a
            // host exception. The set-word path checked this and the
            // set-path one did not, so assigning into a protected object
            // escaped the interpreter entirely.
            if (call.slot() != null && call.slot().isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD, "the field is protected");
            }
            if (call.destination() != null) {
                // A protected container refuses as a REBOL error too. The
                // storage layer knows only that the change is not allowed and
                // says so by throwing; turned into an error here, a script that
                // writes through a path into a protected block can catch it
                // like any other failure. Left alone, it reached the host as a
                // Java exception, which spec/embed.allium forbids outright.
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
                // DO given a function calls it, taking the arguments from
                // the block after it. DO cannot do that itself: it is a
                // native of fixed arity and cannot go on to consume the
                // arguments its own argument wants. So the call carries
                // on from here, where the frame the arguments are in is
                // still to hand.
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
                // The name the call was made through, for STACK/WORD. A call
                // made on a value rather than through a word has no name, and
                // answers none rather than inventing one.
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

        checkArgumentTypes(function.parameters(),
                new java.util.HashSet<>(refinements), arguments, "function");
        Context locals = Context.childOf(function.closedOver());
        if (!function.closure()) {
            locals.markAsCallFrameOf(function);
        }

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

        // /local words start as NONE, not unset. In R3 they are the arguments
        // of the /local refinement, and an argument belonging to a refinement
        // nobody asked for is set to none: `for (; n < len; n++) DS_PUSH_NONE`
        // in Apply_Block, and the same in the walk.
        //
        // The difference is not cosmetic. Rebol's own LOAD is one CASE/ALL
        // whose fourth clause is `none? body [body: source]`, and CASE/ALL
        // evaluates every clause -- so a local read before it is written is
        // ordinary REBOL, and reading unset raises where reading none does
        // not. Every caller of LOAD failed on it.
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
        // The right side runs before the path is walked -- `index =
        // Do_Next(block, index+1, 0);` then `Do_Path(&word, DS_TOP);` -- so
        // a paren in the path reads what the assignment just computed:
        // `b/(c: 2): c + 1` computes with the old c and then selects with
        // the new one.
        frame.pendingCalls.push(PendingCall.assignmentInto(
                written -> writeThroughPath(frame, path, written)));
        return StepOutcome.waiting();
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

        // One byte of one pixel, written by number: `img/1/2: 100`. The C keeps
        // this inside the image's own path handler, where it can see that the
        // path continues and that a value is being set:
        //
        //     if (pvs->setval && IS_INTEGER(pvs->path+1)) { ... }
        //
        // It has to be here rather than after the walk, because the walk would
        // have turned `img/1` into a tuple and a tuple is a value rather than a
        // place. Guarded exactly as the C guards it: 1 to 4 are red, green, blue
        // and alpha, a third segment is refused, and only a byte may be written.
        if (segments.size() == 3 && lastSegment instanceof IntegerValue channel
                && select(BlockValue.path(segments.subList(0, 1), Datatype.PATH),
                        frame.context).value() instanceof ImageValue image) {
            Value pixelSegment = selectorFor(segments.get(1), frame.context);
            ImagePath.writeOneChannel(
                    image, pixelSegment, (int) channel.magnitude(), written);
            return;
        }

        // An event's fields, which a path is also the only way to reach. The event
        // is a value cell rather than shared storage, so the new one goes back into
        // the word that held the old -- the same shape a pair needs, and for the
        // same reason.
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
        // A gob's own fields, which a path is the only way to reach: `if
        // (!Set_GOB_Var(gob, pvs->select, pvs->setval)) return PE_BAD_SET;`.
        if (target instanceof GobValue gob && lastSegment instanceof WordValue field) {
            GobPath.write(gob, field, written);
            return;
        }
        // One half of a pair field: `g/size/x: 5`. PD_Gob reads the whole pair
        // out, lets the next segment write a half of the copy, and puts the copy
        // back:
        //
        //     if (pvs->setval && IS_PAIR(pvs->store)) {
        //         REBVAL *sel = pvs->select;
        //         pvs->value = pvs->store;
        //         Next_Path(pvs);
        //         Set_GOB_Var(gob, sel, pvs->store);
        //     }
        //
        // Which is why a gob is the one thing here whose pair fields can be
        // written a half at a time. A pair held in a word cannot: nothing owns it
        // to put the new one back into.
        if (segments.size() == 3 && segments.get(1) instanceof WordValue pairField
                && select(BlockValue.path(segments.subList(0, 1), Datatype.PATH),
                        frame.context).value() instanceof GobValue holdingPair
                && GobPath.field(holdingPair, pairField) instanceof PairValue half) {
            GobPath.write(holdingPair, pairField,
                    withHalfWritten(half, lastSegment, written));
            return;
        }
        // An object, a port, a module and an error all take the object path
        // handler: `boot/types.reb` names `object` in the Path column of all four.
        // So a field of any of them can be written, and the read above already
        // treated them alike -- writing was the half that had only the first.
        //
        // A port needs it as much as an object does. `p/awake: func [event] [...]`
        // is how a scheme says what to do when an event arrives, and WAKE-UP reads
        // that field back; refusing the write left the field readable and unset for
        // ever.
        if (contextBehind(target) instanceof Context fields
                && selectorFor(lastSegment, frame.context) instanceof WordValue field) {
            if (!fields.holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            ContextSlot slot = fields.ownSlotFor(field.canonical());
            // A protected slot refuses as a REBOL error rather than as a
            // host exception, exactly as a set-word does.
            if (slot.isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD,
                        "the field is protected");
            }
            slot.setValue(written);
            return;
        }
        // A block takes every kind of selector a read takes, and writes over
        // whatever the read would have answered. So `b: [a 1] b/a: 9` leaves
        // [a 9]: the name finds the item after it, and that item is what is
        // written.
        //
        // A selector that finds nothing refuses rather than appending, which is
        // the one place a write and a read part company:
        //     if (n < 0 || (REBCNT)n >= VAL_TAIL(pvs->value)) {
        //         if (pvs->setval) return PE_BAD_SELECT;
        //         return PE_NONE;
        //     }
        // and PE_BAD_SELECT is `invalid-path`. The C keeps the open question
        // beside the function -- "a/not-found: 10 error or append?" -- and
        // answers it this way.
        if (target instanceof BlockValue block) {
            Value selector = selectorFor(lastSegment, frame.context);
            // Position zero is the exception, and it is one because the C tests
            // for it before it looks at whether this is a write:
            // `if (i == 0) return PE_NONE;`. So `b/0: 5` quietly does nothing
            // where `b/zz: 5` refuses, and a caller cannot tell the first from
            // a write that worked.
            if (BlockPath.isNowhereAtAll(selector)) {
                return;
            }
            int at = BlockPath.positionOf(block, selector)
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                            Molder.mold(selector)));
            replaceInSeries(block, at, written);
            return;
        }
        // A file and a URL refuse the write outright, which is the first line
        // of PD_File: `if (pvs->setval) return PE_BAD_SET;`. A path on a file
        // names a longer path rather than a place inside the one it started
        // from, so there is nothing there to write to -- not even a character
        // at a number, which every other member of the string family allows.
        if (target instanceof StringValue joining && joinsItsPathSegments(joining)) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a path on a " + joining.datatype().literalSpelling()
                            + " names another one rather than a place to write");
        }
        // A path names a position in a series as readily as a field in
        // an object, and assigning through it replaces what is there.
        // Rebol's own base-defs.reb rewrites strings this way, having no
        // REPLACE that early in its boot.
        if (target instanceof SeriesValue series && lastSegment instanceof IntegerValue where) {
            replaceInSeries(series,
                    series.index() + (int) where.magnitude() - 1, written);
            return;
        }
        // A tuple is a value rather than a series, so writing an octet
        // makes a new tuple and puts it back where the old one came
        // from. Only a word may be written through, which is what R3
        // reaches too: everything else holds a copy.
        if (target instanceof TupleValue tuple && lastSegment instanceof IntegerValue where
                && segments.getFirst() instanceof WordValue holder && segments.size() == 2) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            slot.setValue(withOctetWritten(tuple, (int) where.magnitude(), written));
            return;
        }
        // A set is changed in place rather than replaced, because a parse rule
        // that already names the word has to see the change. Rebol's own
        // url-parser copies the URI set and then adds the percent sign to it.
        // The segment is evaluated first. `b/(#"a"): true` names the
        // character in a paren, which is the only way a path reaches a
        // character the source did not spell out -- and Rebol's own
        // url-parser writes it exactly that way.
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
        // A pair is a value rather than a series, so writing a half makes a
        // new pair and puts it back where the old one came from -- the same
        // shape a tuple needs, and for the same reason. Rebol writes through
        // the value in place; the observable end of it is that `p: 1x1`
        // followed by `p/x: 0` leaves p as 0x1, and that holds either way.
        if (target instanceof PairValue pair && segments.size() == 2
                && segments.getFirst() instanceof WordValue holder) {
            ContextSlot slot = resolve(
                    holder.isBound() ? holder : holder.boundTo(frame.context));
            slot.setValue(withHalfWritten(pair, lastSegment, written));
            return;
        }
        // A map takes a write through a path, and the key may be any value.
        // `PD_Map` puts the whole question in two lines:
        //     if (IS_END(pvs->path+1)) val = pvs->setval;
        //     n = Find_Entry(VAL_SERIES(data), pvs->select, val, FALSE);
        // with the type restriction on keys removed on purpose -- the C keeps
        // the old check commented out beside the issue that dropped it.
        //
        // Refusing it stopped eight of Rebol's own files, `prot-http.reb` and
        // half the codecs among them: a map is how each of them holds its own
        // state, and `m/key: value` is how it writes it.
        if (target instanceof MapValue map) {
            // A protected map refuses the write, and refuses it first:
            // `if (pvs->setval) TRAP_PROTECT(VAL_SERIES(data));` is the
            // handler's opening line, before the key is even read.
            if (map.isProtected()) {
                throw Raised.of(EvaluationFailure.PROTECTED, "map is protected");
            }
            storeUnderKey(map, selectorFor(lastSegment, frame.context), written);
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
        if (target instanceof DateValue date) {
            return DateParts.of(date, selector);
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
        // And a module, for the same reason. Its words are the ones its body
        // defined, so a path reads what the module keeps to itself as readily
        // as what it publishes: a module's own code has to reach its own
        // helpers, and being private means not escaping rather than not
        // existing.
        if (target instanceof ModuleValue module && selector instanceof WordValue field) {
            if (!module.context().holds(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            return module.context().ownSlotFor(field.canonical()).value();
        }
        // A file and a URL join instead of selecting, which is PD_File in
        // t-string.c and the Path column of boot/types.reb: `file` for those
        // two and `*` for the rest of the string family. So this must come
        // before the string reading below, or `%a/length` would answer a
        // number instead of naming a file.
        if (target instanceof StringValue path && joinsItsPathSegments(path)) {
            return joinedOntoPath(path, selector);
        }
        // A string takes four word selectors, and PD_String in t-string.c is
        // where they are: LENGTH counts codepoints, SIZE counts bytes, WIDTH
        // counts terminal columns, and USER and HOST split an email at its
        // at-sign. Anything else is PE_BAD_SELECT.
        //
        // WIDTH is not a curiosity. Rebol's own FORMAT starts with
        // `plen: p/width` to learn how many columns its padding occupies,
        // and every caller of FORMAT stopped there -- which is the whole of
        // mezz-banner.reb.
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
        // An image has a handler of its own, and it has to come before the
        // series branch below: `PD_Image` takes a word for the shape and the
        // channels, and a pair as a coordinate, neither of which a series
        // position means.
        if (target instanceof ImageValue image) {
            return ImagePath.read(image, selector);
        }
        // And a gob has one for the same reason. A word names a field of the gob
        // rather than anything about its position, and a number names a child in
        // its pane -- so neither form is the general series question.
        if (target instanceof GobValue gob) {
            return GobPath.read(gob, selector);
        }
        // And an event, which is neither a series nor a container: `PD_Event`
        // answers a word and refuses everything else, because there is no position
        // in a value cell to name.
        // A handle, which mostly answers nothing. `PD_Handle` ends "for the data
        // handles, return NONE on get", so a codec's fields are all none -- and a
        // context handle answers one word, `type`, plus whatever its own registered
        // getter adds. Nothing here registers one.
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
                // Unreachable: the branch above answers for an image, and it
                // stands before this one for the reason given there.
                case ImageValue pixels -> ImagePath.read(pixels, selector);
                case GobValue gob -> GobPath.read(gob, selector);
            };
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
            // No arm for an error, whose fields are a record here rather than a
            // context. `boot/types.reb` gives it the object path handler too, so
            // writing one is a real gap -- but it is a gap in how an error is held
            // and not one this branch can close.
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
            // A number that will not fit in a byte is refused rather than
            // truncated: `if (VAL_INT64(arg) < 0 || VAL_INT64(arg) > 255)
            // Trap_Range(arg);`. Truncating is the silent kind of wrong --
            // `a/1: 400` stored 144 and answered 400.
            case BinaryValue bytes -> bytes.storage().set(at, octetFrom(value));
            // A pixel takes a tuple as its colour and an integer as its alpha
            // alone, which is what `PD_Image` writes: `*dp = (*dp & 0xffffff) |
            // (n << 24)` for the integer case.
            case ImageValue image -> ImagePath.write(image, at, value);
            // A gob's pane holds gobs and nothing else, and POKE is the only way
            // in: `if (!IS_GOB(arg)) goto is_arg_error;` and then an insert.
            case GobValue gob -> GobPath.poke(gob, at, value);
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
        int columns = 0;
        for (int at = 0; at < text.length(); ) {
            int code = text.codePointAt(at);
            at += Character.charCount(code);
            columns += columnsFor(code);
        }
        return columns;
    }

    private static int columnsFor(int code) {
        if (Character.getType(code) == Character.NON_SPACING_MARK
                || Character.getType(code) == Character.ENCLOSING_MARK
                || Character.getType(code) == Character.COMBINING_SPACING_MARK) {
            return 0;
        }
        return switch (Character.UnicodeScript.of(code)) {
            case HAN, HIRAGANA, KATAKANA, HANGUL -> 2;
            default -> isWideBlock(code) ? 2 : 1;
        };
    }

    /** The ranges the C treats as double width outside the CJK scripts. */
    private static boolean isWideBlock(int code) {
        return (code >= 0x1100 && code <= 0x115F)      // Hangul Jamo
                || (code >= 0x2E80 && code <= 0x303E)  // CJK radicals and symbols
                || (code >= 0xFE30 && code <= 0xFE6F)  // CJK compatibility forms
                || (code >= 0xFF00 && code <= 0xFF60)  // fullwidth forms
                || (code >= 0xFFE0 && code <= 0xFFE6)
                || (code >= 0x1F300 && code <= 0x1F64F) // emoji
                || (code >= 0x1F900 && code <= 0x1F9FF);
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
        String whole = text.text();
        int at = whole.indexOf('@');
        if (half.equals("host")) {
            return at < 0
                    ? NoneValue.none()
                    : StringValue.of(whole.substring(at + 1), Datatype.EMAIL);
        }
        return StringValue.of(
                at < 0 ? whole : whole.substring(0, at), Datatype.EMAIL);
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
            // Off, so everything belonging to it is none rather than
            // whatever happened to sit at that position.
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
