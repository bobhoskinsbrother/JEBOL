package org.jebol.domain.eval;

import org.jebol.domain.read.Construction;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.util.*;
import java.util.function.Predicate;

public final class Evaluator {

    public static final int DEFAULT_MAXIMUM_DEPTH = 10_000;

    public static final int DEFAULT_CHECK_EVERY = 1_000;

    private static final int THE_IDENTITY_BEFORE_THE_FIRST_CIPHER = 2000;

    private final OutputPort output;
    private final Context systemContext;
    private final PathDispatch pathDispatch = new PathDispatch();

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
    private final Construction construction;
    private final MakerSource makerSource;
    private final ErrorWording wording;
    private int lastCipherIdentity = THE_IDENTITY_BEFORE_THE_FIRST_CIPHER;
    private int stepsSinceLastCheck;

    private int framesOpen;

    private final Trace trace = new Trace();

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

    public record OpenCall(String name, FunctionValue function, Context locals) {

        public List<String> slotNames() {
            List<String> names = new ArrayList<>();
            function.parameters().forEach(parameter -> names.add(parameter.name()));
            names.addAll(function.localNames());
            return names;
        }
    }

    private final Deque<OpenCall> functionsBeingRun = new ArrayDeque<>();

    private final SymbolTable symbols = new SymbolTable();

    private final Deque<Deque<Frame>> walksInProgress = new ArrayDeque<>();

    private String nameOfTheCallBeingMade = "";

    private String lastWordCalledThrough = "";

    public java.util.Optional<String> reasonToStop() {
        return interruption.reasonToStop();
    }

    public int framesOpen() {
        return framesOpen;
    }

    public long valuesWalked() {
        return valuesWalked;
    }

    private final Map<UsageLimit, Long> limitsRecorded =
            new java.util.EnumMap<>(UsageLimit.class);

    java.util.Optional<Long> limitRecorded(UsageLimit limit) {
        return java.util.Optional.ofNullable(limitsRecorded.get(limit));
    }

    public void recordLimitAskedFor(UsageLimit limit, long value) {
        limitsRecorded.putIfAbsent(limit, value);
    }

    public List<OpenCall> callsInProgress() {
        return List.copyOf(functionsBeingRun);
    }

    public List<String> everyCallOpenNamedInnermostFirst() {
        return walksInProgress.isEmpty()
                ? List.of()
                : namesOfEveryCallOpen(walksInProgress.peek());
    }

    private static final String AN_APPLIED_FUNCTION = "-apply-";

    private List<String> namesOfEveryCallOpen(Deque<Frame> innermostWalk) {
        List<String> named = new ArrayList<>();
        for (Deque<Frame> walk : everyWalkInnermostFirst(innermostWalk)) {
            for (Frame open : walk) {
                if (open.theCallNearAndWhereAreAbout != null) {
                    open.theCallNearAndWhereAreAbout.nameInTheBacktrace().ifPresent(named::add);
                }
            }
            if (!walk.isEmpty() && walk.peekLast().bodyOfAnAppliedFunction) {
                named.add(AN_APPLIED_FUNCTION);
            }
        }
        return named;
    }

    private List<Deque<Frame>> everyWalkInnermostFirst(Deque<Frame> innermostWalk) {
        List<Deque<Frame>> walks = new ArrayList<>();
        walks.add(innermostWalk);
        walksInProgress.stream()
                .filter(enclosing -> enclosing != innermostWalk)
                .forEach(walks::add);
        return walks;
    }

    public Evaluator(Context systemContext, OutputPort output) {
        this(systemContext, Construction.refused(), MakerSource.none(), output,
                DEFAULT_MAXIMUM_DEPTH, Interruption.never(), DEFAULT_CHECK_EVERY);
    }

    public Evaluator(
            Context systemContext,
            Construction construction,
            MakerSource makerSource,
            OutputPort output,
            int maximumDepth,
            Interruption interruption,
            int checkEvery) {
        this.systemContext = systemContext;
        this.construction = construction;
        this.makerSource = makerSource;
        this.wording = new ErrorWording(systemContext);
        this.output = output;
        this.maximumDepth = maximumDepth;
        this.interruption = interruption;
        this.checkEvery = checkEvery;
    }

    private OutputPort alsoWritingTo;

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

    public void alsoWriteTo(OutputPort second) {
        this.alsoWritingTo = second;
    }

    public void stopEchoing() {
        this.alsoWritingTo = null;
    }

    public ProcessPort processes() {
        return processes;
    }

    public NetworkPort network() {
        return network;
    }

    public void useNetwork(NetworkPort port) {
        this.network = port;
    }

    private ClipboardPort clipboard = ClipboardPort.none();

    public ClipboardPort clipboard() {
        return clipboard;
    }

    public void useClipboard(ClipboardPort port) {
        this.clipboard = port;
    }

    public BundledModules bundledModules() {
        return bundledModules;
    }

    public void useBundledModules(BundledModules bundled) {
        this.bundledModules = bundled;
    }

    public Construction construction() {
        return construction;
    }

    public Maker makerIn(Context where) {
        return makerSource.makerFor(this, where);
    }

    public int nextCipherIdentity() {
        return ++lastCipherIdentity;
    }

    public Optional<Value> theRebolActorsAnswer(String action,
            List<Value> arguments, Set<String> refinements) {

        if (!(arguments.getFirst() instanceof PortValue port)) {
            return Optional.empty();
        }
        port.refuseASpecThatIsNotAnObject();
        port.refuseAnActorThatIsNeitherANativeNorAnObject();
        if (port.hasNoActor()) {
            return Optional.of(NoneValue.none());
        }
        return port.actorWrittenInRebol().map(actor ->
                askTheActor(actor, action, arguments, refinements));
    }

    public Value askTheActor(ObjectValue actor, String action,
            List<Value> arguments, Set<String> refinements) {

        Value theFunction = actor.context().holds(action)
                ? actor.context().ownSlotFor(action).value()
                : NoneValue.none();
        if (!(theFunction instanceof FunctionValue able)) {
            throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                    WordValue.of(action).as(Datatype.SET_WORD));
        }
        return applyFunction(able, laidOutAsTheActorDeclaresThem(able, arguments, refinements));
    }

    private List<Value> laidOutAsTheActorDeclaresThem(
            FunctionValue able, List<Value> arguments, Set<String> refinements) {

        List<Value> laidOut = new ArrayList<>();
        int fromTheNative = 0;
        boolean theseArgumentsWereSupplied = true;
        for (Parameter parameter : able.parameters()) {
            if (parameter.kind() == ParameterKind.REFINEMENT) {
                boolean asked = refinements.contains(parameter.name());
                laidOut.add(LogicValue.of(asked));
                theseArgumentsWereSupplied = asked;
                continue;
            }
            if (!theseArgumentsWereSupplied) {
                laidOut.add(NoneValue.none());
                continue;
            }
            laidOut.add(fromTheNative < arguments.size()
                    ? arguments.get(fromTheNative)
                    : NoneValue.none());
            fromTheNative++;
        }
        return laidOut;
    }

    public Value simpleValueOf(Value given, Context where) {
        if (given instanceof WordValue word
                && (word.datatype() == Datatype.WORD
                        || word.datatype() == Datatype.GET_WORD)) {
            return valueOfWordIn(word, where);
        }
        if (given instanceof BlockValue path
                && (path.datatype() == Datatype.PATH
                        || path.datatype() == Datatype.GET_PATH)) {
            return valueOfPathIn(path, where);
        }
        return given;
    }

    public List<Value> reducedLeavingSetWords(BlockValue block) {
        List<Value> results = new ArrayList<>();
        BlockValue at = block;
        while (!at.atTail()) {
            Value here = at.first();
            if (here.datatype() == Datatype.SET_WORD || here.datatype() == Datatype.SET_PATH) {
                results.add(here);
                at = at.atIndex(at.index() + 1);
                if (at.atTail()) {
                    break;
                }
            }
            Step step = evaluateNextOrRaise(at, systemContext());
            results.add(step.value());
            at = at.atIndex(step.nextIndex());
        }
        return results;
    }

    public void setSystemState(String field, Value value) {
        if (systemContext.valueAt("system", "state") instanceof ObjectValue(Context state)) {
            state.register(field, value);
        }
    }

    public void setSystemOption(String field, Value value) {
        if (systemContext.valueAt("system", "options") instanceof ObjectValue(Context options)) {
            options.register(field, value);
        }
    }

    public Outcome appliedAsTheHostApplies(String systemFunction) {
        try {
            return new Outcome.Completed(unsignalled(() -> applyFunction(
                    systemContext.systemFunctionNamed(systemFunction), List.of())));
        } catch (Raised raised) {
            return new Outcome.Raised(raised.error());
        }
    }

    public ErrorValue madeWhereTheCallIs(ErrorValue error) {
        if (walksInProgress.isEmpty()) {
            return spokenHere(error);
        }
        return sayingWhereItCameFrom(new Raised(error), walksInProgress.peek()).error();
    }

    public ErrorValue spokenHere(ErrorValue error) {
        return error.wording().equals(wording) ? error : error.spokenBy(wording);
    }

    public void useProcesses(ProcessPort port) {
        this.processes = port;
    }

    public ConsolePort console() {
        return console;
    }

    public void useConsole(ConsolePort port) {
        this.console = port;
    }

    public WindowPort windows() {
        return windows;
    }

    public void useWindows(WindowPort port) {
        this.windows = port;
    }

    public ScreenPort screen() {
        return screen;
    }

    public void useScreen(ScreenPort port) {
        this.screen = port;
    }

    public EnvironmentPort environment() {
        return environment;
    }

    public void useEnvironment(EnvironmentPort port) {
        this.environment = port;
    }

    public ImagePort images() {
        return images;
    }

    public void useImages(ImagePort port) {
        this.images = port;
    }

    public FilePort files() {
        return files;
    }

    public void useFiles(FilePort port) {
        this.files = port;
    }

    public Value applyFunction(Value callee, List<Value> arguments) {
        return switch (callee) {
            case FunctionValue function -> {
                List<Parameter> parameters = function.parameters();
                checkArgumentTypes(parameters, arguments, AN_APPLIED_FUNCTION);
                Context locals = Context.childOf(function.closedOver());
                if (!function.closure()) {
                    locals.markAsCallFrameOf(function);
                }
                function.localNames().forEach(
                        name -> locals.register(name, NoneValue.none()));
                bindArgumentsPositionally(locals, parameters, arguments);
                BlockValue running = theBodyThisCallRuns(function, locals);
                theFrameThisCallTakesOverFrom(function).supersededBy(locals);
                functionsBeingRun.push(new OpenCall("", function, locals));
                try {
                    yield walk(running, locals, 1, null, THE_BODY_OF_AN_APPLIED_FUNCTION);
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

    public Context systemContext() {
        return systemContext;
    }

    public SymbolTable symbols() {
        return symbols;
    }

    public TranscodeResult read(String source) {
        TranscodeResult read = Transcoder.transcode(source, construction);
        read.values().ifPresent(values -> symbols.internWhatWasRead(values.remaining()));
        return read;
    }

    public Value evaluateSource(String source) {
        TranscodeResult read = read(source);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        Context into = runtimeContext == null ? systemContext : runtimeContext;
        return walk(Binder.bindAndDefine(read.values().orElseThrow(), into), into, 1);
    }

    public void putRuntimeWordsIn(Context context) {
        this.runtimeContext = context;
    }

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

    public Value evaluateOrRaise(BlockValue code, Context context) {
        return walk(code, context, 1, null);
    }

    public ObjectValue freshObjectWithin(Context enclosing) {
        ObjectValue built = new ObjectValue(Context.childOf(enclosing));
        built.context().register("self", built);
        return built;
    }

    public ObjectValue evaluatedInto(ObjectValue built, BlockValue body) {
        Context fields = built.context();
        body.setWordsFromHere().stream().map(WordValue::spelling).forEach(fields::register);
        evaluateOrRaise(Binder.bindOnly(body, fields, fields.ownFieldNames()), fields);
        return built;
    }

    public List<Value> evaluateEachOrRaise(BlockValue code, Context context) {
        List<Value> results = new ArrayList<>();
        walk(code, context, 1,
                (produced, startedAt, stoppedBefore) -> results.add(produced));
        return results;
    }

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

    private static final Set<Datatype> WORKS_SOMETHING_OUT =
            Typeset.ANY_PATH.membersAnd(
                    Datatype.WORD, Datatype.SET_WORD, Datatype.GET_WORD,
                    Datatype.LIT_WORD, Datatype.PAREN,
                    Datatype.FUNCTION, Datatype.CLOSURE, Datatype.NATIVE,
                    Datatype.ACTION, Datatype.OP, Datatype.COMMAND);

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

    public record Step(Value value, int nextIndex) {
    }

    public Step evaluateNextOrRaise(BlockValue code, Context context) {
        if (code.atTail()) {
            return new Step(UnsetValue.unset(), code.index());
        }
        Frame frame = new Frame(code, context, 1);
        Deque<Frame> frames = new ArrayDeque<>();
        frames.push(frame);
        frame.sink = (produced, startedAt, stoppedBefore) -> false;
        walksInProgress.push(frames);
        Value produced;
        try {
            produced = walkFrames(frames);
        } finally {
            walksInProgress.pop();
        }
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
        return walk(code, context, depth, sink, AN_ORDINARY_BLOCK);
    }

    private static final boolean AN_ORDINARY_BLOCK = false;

    private static final boolean THE_BODY_OF_AN_APPLIED_FUNCTION = true;

    private Value walk(BlockValue code, Context context, int depth, ResultSink sink,
            boolean bodyOfAnAppliedFunction) {
        Deque<Frame> frames = new ArrayDeque<>();
        Frame root = new Frame(code, context, depth);
        root.sink = sink;
        root.bodyOfAnAppliedFunction = bodyOfAnAppliedFunction;
        frames.push(root);
        int callsOpenBeforeTheWalk = functionsBeingRun.size();
        walksInProgress.push(frames);
        try {
            return walkFrames(frames);
        } finally {
            walksInProgress.pop();
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
                    PendingCall gathering = frame.pendingCalls.peek();
                    if (gathering.takesTheNextValueAsWritten()) {
                        deliver(frame, UnsetValue.unset(), frames);
                        continue;
                    }
                    throw Raised.of(EvaluationFailure.NO_ARG,
                            WordValue.of(gathering.nameInAnArgumentError()),
                            gathering.theArgumentStillWanted());
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
                if (takeOneStep(frame, frames) instanceof StepOutcome.Produced(Value value)) {
                    deliver(frame, value, frames);
                }
            } catch (ReturnSignal returning) {
                unwindToFunction(frames, returning);
            } catch (Raised raised) {
                throw sayingWhereItCameFrom(raised, frames);
            }
        }
    }

    private Raised sayingWhereItCameFrom(Raised escaping, Deque<Frame> frames) {
        Raised raised = spokenHere(escaping);
        if (raised.error().whereChain().isPresent()) {
            return raised;
        }
        List<Value> chain = namesOfEveryCallOpen(frames).stream()
                .<Value>map(WordValue::of)
                .toList();
        Value nearest = null;
        for (Frame open : everyCallOpenInnermostFirst(frames)) {
            PendingCall running = open.theCallNearAndWhereAreAbout;
            if (nearest == null && running != null && running.startedAt() >= 0) {
                nearest = open.code.atIndex(running.startedAt());
            }
        }
        ErrorValue said = raised.error();
        if (nearest != null && said.near().isEmpty()) {
            said = said.near(nearest);
        }
        if (!chain.isEmpty()) {
            said = said.raisedThrough(BlockValue.block(chain));
        }
        return said == raised.error() ? raised : new Raised(said);
    }

    private Raised spokenHere(Raised escaping) {
        ErrorValue spoken = spokenHere(escaping.error());
        return spoken == escaping.error() ? escaping : new Raised(spoken);
    }

    private List<Frame> everyCallOpenInnermostFirst(Deque<Frame> frames) {
        List<Frame> open = new ArrayList<>(frames);
        for (Deque<Frame> enclosing : walksInProgress) {
            if (enclosing != frames) {
                open.addAll(enclosing);
            }
        }
        return open;
    }

    private void push(Deque<Frame> frames, BlockValue code, Context context) {
        push(frames, code, context, null);
    }

    private void push(
            Deque<Frame> frames, BlockValue code, Context context, FunctionValue being) {
        Frame parent = frames.peek();
        if (parent.depth >= maximumDepth) {
            throw Raised.of(EvaluationFailure.STACK_OVERFLOW);
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
                            nameWrittenAt(frame, wroteTheOperatorAt, AN_OPERATOR_TAKES_NO_REFINEMENT));
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
            refuseWhatTheParameterBeingFilledWouldNot(waiting, carrying);
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

    private void refuseWhatTheParameterBeingFilledWouldNot(PendingCall waiting, Value arriving) {
        waiting.theParameterBeingFilled()
                .filter(parameter -> isSwitchedOnFor(waiting, parameter))
                .ifPresent(parameter -> refuseUnlessAccepted(
                        parameter, arriving, waiting.nameInAnArgumentError()));
    }

    private boolean isSwitchedOnFor(PendingCall waiting, Parameter parameter) {
        return parameter.owningRefinement().map(waiting.refinements()::contains).orElse(true);
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
            throw Raised.of(EvaluationFailure.NEED_VALUE, word);
        }
        word.refuseToBeWrittenWhenItNamesSelf();
        ContextSlot slot = word.binding().slotFor(word.canonical());
        frame.pendingCalls.push(PendingCall.assignment(slot));
        return StepOutcome.waiting();
    }

    private static boolean asksForReEvaluation(Value argument) {
        return switch (argument) {
            case WordValue word -> word.datatype() == Datatype.WORD
                    || word.datatype() == Datatype.GET_WORD;
            case BlockValue path -> path.datatype() == Datatype.PATH;
            default -> argument.datatype().isAnyFunction();
        };
    }

    public Value valueOfWordIn(WordValue word, Context context) {
        return resolve(word.isBound() ? word : word.boundTo(context)).value();
    }

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
            List<String> refinements, List<String> mentioned) {
        PendingCall call = PendingCall.prefix(callee, refinements, mentioned);
        call.startedAt(frame.startedThisValueAt,
                nameWrittenAt(frame, frame.startedThisValueAt, mentioned.size()));
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
        if (lastSegment instanceof WordValue word
                && word.canonical().equals("self")) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "self is what a context calls itself and cannot be assigned");
        }
    }

    private static final int AN_OPERATOR_TAKES_NO_REFINEMENT = 0;

    private String nameWrittenAt(Frame frame, int position, int refinementsWritten) {
        if (position < frame.code.index() || position > frame.code.storageLength()) {
            return null;
        }
        Value written = frame.code.storage().at(position);
        if (written instanceof WordValue word) {
            return word.spelling();
        }
        if (written instanceof BlockValue path && path.datatype() == Datatype.PATH) {
            int reachingTheFunction = path.remaining().size() - refinementsWritten - 1;
            if (reachingTheFunction >= 0
                    && path.remaining().get(reachingTheFunction) instanceof WordValue reached) {
                return reached.spelling();
            }
        }
        return null;
    }

    private StepOutcome invoke(Frame frame, PendingCall call, Deque<Frame> frames) {
        if (call.isAssignment()) {
            if (call.slot() != null && call.argumentsInDeclaredOrder().get(0).datatype() == Datatype.UNSET) {
                throw Raised.of(EvaluationFailure.NEED_VALUE,
                        WordValue.of(call.slot().spelling(), Datatype.SET_WORD));
            }
            if (call.slot() != null && call.slot().isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD,
                        (Value) WordValue.of(call.slot().spelling(), Datatype.SET_WORD));
            }
            if (call.destination() != null) {
                try {
                    call.destination().accept(call.argumentsInDeclaredOrder().get(0));
                } catch (ProtectedFromChange refused) {
                    throw Raised.of(EvaluationFailure.PROTECTED);
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
                Value produced = runNative(built, call.argumentsInDeclaredOrder(), frame.context,
                        EVERY_ARGUMENT_IS_CHECKED, call.nameInAnArgumentError(), call::enter);
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
            case OperatorValue operator -> StepOutcome.of(invokeUnderlying(
                    operator, call.argumentsInDeclaredOrder(), frame.context,
                    call.nameInAnArgumentError(), call::enter));
            case FunctionValue function -> {
                nameOfTheCallBeingMade = lastWordCalledThrough;
                if (trace.isOn()) {
                    trace.call(nameOfTheCallBeingMade == null
                            ? "?" : nameOfTheCallBeingMade, function, call.argumentsInDeclaredOrder());
                }
                yield runFunction(frames, function, call);
            }
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    call.callee().datatype().literalSpelling() + " is not callable");
        };
    }

    private Value invokeUnderlying(
            OperatorValue operator, List<Value> arguments, Context context) {
        return invokeUnderlying(operator, arguments, context, AN_APPLIED_FUNCTION, NOBODY_IS_WAITING_TO_HEAR);
    }

    private static final Runnable NOBODY_IS_WAITING_TO_HEAR = () -> { };

    private Value invokeUnderlying(OperatorValue operator, List<Value> arguments,
            Context context, String calledAs, Runnable onceTheArgumentsPass) {
        return switch (operator.underlying()) {
            case NativeValue built -> runNative(built, arguments, context,
                    THE_LEFT_OPERAND_IS_NOT_CHECKED, calledAs, onceTheArgumentsPass);
            case FunctionValue function -> {
                onceTheArgumentsPass.run();
                yield applyFunction(function, arguments);
            }
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "operator " + operator.operatorName() + " has no runnable body");
        };
    }

    private static final int EVERY_ARGUMENT_IS_CHECKED = 0;

    private static final int THE_LEFT_OPERAND_IS_NOT_CHECKED = 1;

    private Value runNative(
            NativeValue built, List<Value> arguments, Context context) {
        return runNative(built, arguments, context, EVERY_ARGUMENT_IS_CHECKED,
                AN_APPLIED_FUNCTION, NOBODY_IS_WAITING_TO_HEAR);
    }

    private Value runNative(NativeValue built, List<Value> arguments, Context context,
            int checkedFrom, String calledAs, Runnable onceTheArgumentsPass) {
        checkArgumentTypes(built, arguments, calledAs, checkedFrom);
        RefinedCallable behaviour = built.behaviour();
        onceTheArgumentsPass.run();
        nativesCalled++;
        Value produced;
        try {
            produced = behaviour.call(arguments, this, context, built.askedRefinements());
        } catch (ProtectedFromChange refused) {
            throw Raised.of(EvaluationFailure.PROTECTED);
        } catch (SlotIsProtected refused) {
            throw Raised.of(EvaluationFailure.LOCKED_WORD, refused.spelling());
        }
        if (produced == null) {
            throw new IllegalStateException(
                    built.nativeName() + " returned null; use UnsetValue.unset()");
        }
        return produced;
    }

    private StepOutcome runFunction(Deque<Frame> frames, FunctionValue function, PendingCall call) {
        List<Value> arguments = call.argumentsInDeclaredOrder();
        List<String> refinements = call.refinements();
        checkArgumentTypes(function.parameters(),
                new java.util.HashSet<>(refinements), arguments, call.nameInAnArgumentError());
        call.enter();
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
            locals.register(consuming.get(index).name(), arguments.get(index));
        }

        function.parameters().stream()
                .filter(parameter -> parameter.owningRefinement().isPresent())
                .filter(parameter -> !consuming.contains(parameter))
                .forEach(parameter -> locals.register(parameter.name(), NoneValue.none()));

        function.parameters().stream()
                .filter(parameter -> parameter.kind() == ParameterKind.REFINEMENT)
                .forEach(parameter -> locals.register(
                        parameter.name(),
                        refinements.contains(parameter.name().toLowerCase(Locale.ROOT))
                                ? LogicValue.yes()
                                : NoneValue.none()));

        function.localNames().forEach(name -> locals.register(name, NoneValue.none()));

        push(frames, theBodyThisCallRuns(function, locals), locals, function);
        return StepOutcome.waiting();
    }

    private void checkArgumentTypes(
            NativeValue built, List<Value> arguments, String calleeName, int checkedFrom) {
        checkArgumentTypes(built.parameters(), built.askedRefinements(),
                arguments, calleeName, checkedFrom);
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, List<Value> arguments, String calleeName) {
        checkArgumentTypes(parameters, Set.of(), arguments, calleeName);
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, Set<String> asked,
            List<Value> arguments, String calleeName) {
        checkArgumentTypes(parameters, asked, arguments, calleeName,
                EVERY_ARGUMENT_IS_CHECKED);
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, Set<String> asked,
            List<Value> arguments, String calleeName, int checkedFrom) {
        List<Parameter> consuming = parameters.stream()
                .filter(Parameter::consumesAnArgument)
                .filter(parameter -> parameter.owningRefinement()
                        .map(asked::contains).orElse(true))
                .toList();
        for (int index = checkedFrom;
                index < arguments.size() && index < consuming.size(); index++) {
            refuseUnlessAccepted(consuming.get(index), arguments.get(index), calleeName);
        }
    }

    private void refuseUnlessAccepted(Parameter parameter, Value argument, String calleeName) {
        if (!parameter.accepts(argument.datatype())) {
            throw new Raised(ErrorValue.about(
                    EvaluationFailure.EXPECT_ARG.category(),
                    EvaluationFailure.EXPECT_ARG.errorId(),
                    calleeName + " does not allow "
                            + argument.datatype().literalSpelling()
                            + " for its " + parameter.name() + " argument",
                    WordValue.of(calleeName),
                    parameter.asWrittenInTheSpec(),
                    DatatypeValue.of(argument.datatype())));
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
                refined(selection),
                selection.refinements(), selection.mentioned());
    }

    private Value refined(Selection selection) {
        Value callee = selection.value();
        List<String> mentioned = selection.mentioned();
        if (callee instanceof FunctionValue written) {
            for (int index = 0; index < mentioned.size(); index++) {
                if (!declaresRefinement(written, mentioned.get(index))) {
                    throw noSuchRefinement(selection, index);
                }
            }
            return callee;
        }
        if (!(callee instanceof NativeValue built) || mentioned.isEmpty()) {
            return callee;
        }
        for (int index = 0; index < mentioned.size(); index++) {
            if (!built.declares(mentioned.get(index))) {
                String refinedName = built.nativeName() + "/"
                        + String.join("/", selection.refinements());
                if (systemContext.knows(refinedName)) {
                    return systemContext.slotFor(refinedName).value();
                }
                throw noSuchRefinement(selection, index);
            }
        }
        return selection.refinements().isEmpty()
                ? callee
                : built.askedFor(Set.copyOf(selection.refinements()));
    }

    private Raised noSuchRefinement(Selection selection, int index) {
        Value calledBy = selection.calledBy() instanceof WordValue word
                ? word.as(Datatype.WORD)
                : selection.calledBy();
        return Raised.of(EvaluationFailure.NO_REFINE,
                calledBy, selection.mentionedAsWritten().get(index));
    }

    private static boolean declaresRefinement(FunctionValue written, String refinement) {
        return written.parameters().stream()
                .anyMatch(parameter -> parameter.kind() == ParameterKind.REFINEMENT
                        && parameter.name().equalsIgnoreCase(refinement));
    }

    private StepOutcome evaluateSetPath(Frame frame, BlockValue path) {
        if (frame.atEnd()) {
            throw Raised.of(EvaluationFailure.NEED_VALUE, path);
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
        if (written.datatype() == Datatype.UNSET) {
            throw Raised.of(EvaluationFailure.NEED_VALUE, path);
        }
        List<Value> segments = path.remaining();
        BlockValue allButLast = BlockValue.path(
                segments.subList(0, segments.size() - 1), Datatype.PATH);
        Slot place = select(allButLast, path, frame.context).slot();
        refuseAPathIntoSomethingWithNoParts(path, place.value(), segments.size() - 2);
        try {
            writeThroughTheLastSegment(frame, path, place, written);
        } catch (Raised refused) {
            throw refusedAt(refused, path, segments.getLast());
        }
    }

    private void writeThroughTheLastSegment(
            Frame frame, BlockValue path, Slot place, Value written) {

        List<Value> segments = path.remaining();
        Value target = place.value();
        Value lastSegment = segments.get(segments.size() - 1);
        refuseSelfAsAnInvalidPathRatherThanAGuardedSlot(lastSegment);

        if (segments.size() == 3 && lastSegment instanceof IntegerValue(long magnitude1)
                && select(BlockValue.path(segments.subList(0, 1), Datatype.PATH),
                        frame.context).value() instanceof ImageValue image) {
            Value pixelSegment = selectorFor(segments.get(1), frame.context);
            ImagePath.writeOneChannel(
                    image, pixelSegment, (int) magnitude1, written);
            return;
        }

        if (target instanceof EventValue event && lastSegment instanceof WordValue field) {
            place.setValue(EventPath.written(event, field.canonical(), written)
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
            GobPath.write(holdingPair, pairField, new PairDispatcher().withHalfWritten(
                    half, selectorFor(lastSegment, frame.context), written));
            return;
        }
        Optional<Dispatcher> dispatched = pathDispatch.forDatatype(target.datatype());
        if (dispatched.isPresent()) {
            dispatched.get().writeTo(
                    place, selectorFor(lastSegment, frame.context), written);
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
            SeriesSlot.write(block, at, written);
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
        if (target instanceof TimeValue time) {
            place.setValue(new WritingIntoATime().written(
                    time, selectorFor(lastSegment, frame.context), written));
            return;
        }
        if ((target instanceof StringValue || target instanceof BinaryValue)
                && selectorFor(lastSegment, frame.context) instanceof IntegerValue counted) {
            new WritingIntoText((RebolSeries) target).write(counted, written);
            return;
        }
        if (target instanceof RebolSeries series
                && selectorFor(lastSegment, frame.context) instanceof IntegerValue(long magnitude)) {
            SeriesSlot.write(series,
                    series.index() + (int) magnitude - 1, written);
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
                    throw Raised.of(EvaluationFailure.PROTECTED);
                }
                set.hold(bit, written.isTruthy());
                return;
            }
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
        if ((target instanceof StringValue || target instanceof BinaryValue)
                && selectorFor(lastSegment, frame.context) instanceof WordValue) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH);
    }

    private static long countedFromTheSeriesPosition(long index) {
        return index < 0 ? index + 1 : index;
    }

    private Selection select(BlockValue path, Context context) {
        return select(path, path, context);
    }

    private Slot slotWithTheSegment(
            BlockValue asWritten, Slot holder, Value segment, Context context) {

        Value selector = selectorFor(segment, context);
        try {
            return slotWith(holder, selector);
        } catch (Raised refused) {
            throw refusedAt(refused, asWritten, segment);
        }
    }

    private Raised refusedAt(Raised refused, BlockValue asWritten, Value segment) {
        String refusal = refused.error().errorId();
        if (refusal.equals(EvaluationFailure.OUT_OF_RANGE.errorId())
                && refused.error().subject().isEmpty()) {
            return Raised.of(EvaluationFailure.OUT_OF_RANGE, segment);
        }
        for (EvaluationFailure saidOfTheWholePath : SAID_OF_THE_WHOLE_PATH) {
            if (refusal.equals(saidOfTheWholePath.errorId())) {
                return Raised.of(saidOfTheWholePath, asWritten, segment);
            }
        }
        return refused;
    }

    private static final List<EvaluationFailure> SAID_OF_THE_WHOLE_PATH =
            List.of(EvaluationFailure.INVALID_PATH, EvaluationFailure.BAD_PATH_SET);

    private Selection select(BlockValue path, BlockValue asWritten, Context context) {
        List<Value> segments = path.remaining();
        if (segments.isEmpty()) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, "an empty path selects nothing");
        }
        Slot current = selectFirst(segments.get(0), context);
        Value calledBy = segments.get(0);
        List<String> refinements = new ArrayList<>();
        List<String> mentioned = new ArrayList<>();
        List<WordValue> mentionedAsWritten = new ArrayList<>();

        for (int index = 1; index < segments.size(); index++) {
            Value segment = segments.get(index);
            if (current.value().datatype().isAnyFunction()) {
                WordValue refinement = refinementWordOf(segment);
                mentioned.add(refinement.canonical());
                mentionedAsWritten.add(refinement);
                if (isSwitchedOn(refinement, context)) {
                    refinements.add(refinement.canonical());
                }
                continue;
            }
            refuseAPathIntoSomethingWithNoParts(asWritten, current.value(), index - 1);
            current = slotWithTheSegment(asWritten, current, segment, context);
            calledBy = segment;
        }
        return new Selection(current, calledBy, List.copyOf(refinements),
                List.copyOf(mentioned), List.copyOf(mentionedAsWritten));
    }

    private boolean isSwitchedOn(WordValue refinement, Context context) {
        return refinement.datatype() != Datatype.GET_WORD
                || resolve(refinement.isBound() ? refinement : refinement.boundTo(context))
                        .value().isTruthy();
    }

    private Slot slotWith(Slot holder, Value selector) {
        Value target = holder.value();
        Optional<Dispatcher> dispatched = pathDispatch.forDatatype(target.datatype());
        if (dispatched.isPresent()) {
            return dispatched.get().placeWithin(holder, selector);
        }
        if (target instanceof RebolSeries series
                && selector instanceof IntegerValue position
                && reachesIntoTheSeries(series, position)) {
            return new SeriesSlot(series, positionIn(series, position),
                    selectWith(target, selector));
        }
        return new ComputedSlot(selectWith(target, selector), selector);
    }

    private static boolean reachesIntoTheSeries(
            RebolSeries series, IntegerValue position) {

        long counted = countedFromTheSeriesPosition(position.magnitude());
        return counted >= 1 && counted <= series.lengthFromHere();
    }

    private static int positionIn(RebolSeries series, IntegerValue position) {
        return series.index()
                + (int) countedFromTheSeriesPosition(position.magnitude()) - 1;
    }

    private void refuseAPathIntoSomethingWithNoParts(
            BlockValue asWritten, Value current, int reachedThroughSegment) {

        if (!HAVE_NO_PARTS_TO_SELECT.contains(current.datatype())) {
            return;
        }
        if (reachedThroughSegment == THE_VARIABLE_THE_PATH_STARTS_FROM) {
            throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                    asWritten, DatatypeValue.of(current.datatype()));
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                asWritten, asWritten.remaining().get(reachedThroughSegment));
    }

    private static final int THE_VARIABLE_THE_PATH_STARTS_FROM = 0;

    private static final java.util.Set<Datatype> HAVE_NO_PARTS_TO_SELECT =
            Typeset.ANY_WORD.membersAnd(
                    Datatype.UNSET, Datatype.NONE, Datatype.LOGIC,
                    Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
                    Datatype.MONEY, Datatype.DATATYPE, Datatype.TYPESET);

    private Slot selectFirst(Value segment, Context context) {
        if (segment instanceof WordValue word) {
            WordValue bound = word.isBound() ? word : word.boundTo(context);
            ContextSlot slot = resolve(bound);
            if (slot.value().datatype() == Datatype.UNSET) {
                throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
            }
            return slot;
        }
        return new ComputedSlot(segment, segment);
    }

    private Value selectorFor(Value segment, Context context) {
        return withAnyFractionTruncated(resolvedSegment(segment, context));
    }

    private Value resolvedSegment(Value segment, Context context) {
        if (segment instanceof WordValue word && word.datatype() == Datatype.GET_WORD) {
            return resolve(word.isBound() ? word : word.boundTo(context)).value();
        }
        if (segment instanceof BlockValue paren && paren.datatype() == Datatype.PAREN) {
            return evaluateOrRaise(Binder.bind(paren.as(Datatype.BLOCK), context), context);
        }
        return segment;
    }

    private static Value withAnyFractionTruncated(Value selector) {
        return selector instanceof DecimalValue fractional
                ? IntegerValue.of((long) fractional.quantity())
                : selector;
    }

    private WordValue refinementWordOf(Value segment) {
        if (segment instanceof WordValue word) {
            return word;
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "a refinement must be a word, not " + segment.datatype().literalSpelling());
    }

    private Value selectWith(Value target, Value selector) {
        Optional<Dispatcher> dispatched = pathDispatch.forDatatype(target.datatype());
        if (dispatched.isPresent()) {
            return dispatched.get().readFrom(target, selector);
        }
        if (target instanceof TimeValue time) {
            return partOfATime(time, selector);
        }
        if (target instanceof BitsetValue set && selector instanceof CharacterValue(int codepoint1)) {
            return LogicValue.of(set.holds(codepoint1));
        }
        if (target instanceof ErrorValue raised && selector instanceof WordValue field) {
            return raised.field(field.canonical()).orElseThrow(() ->
                    Raised.of(EvaluationFailure.INVALID_PATH, field.spelling()));
        }
        if (target instanceof StringValue path && joinsItsPathSegments(path)) {
            return joinedOntoPath(path, selector);
        }
        if (target instanceof StringValue text && selector instanceof WordValue field) {
            return switch (field.canonical()) {
                case "length" -> IntegerValue.of(text.lengthFromHere());
                case "size" -> IntegerValue.of(
                        text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                case "width" -> IntegerValue.of(terminalWidthOf(text.text()));
                case "user", "host" -> emailPartOf(text, field.canonical());
                default -> throw Raised.of(
                        EvaluationFailure.INVALID_PATH, field.spelling());
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
            if (!(selector instanceof WordValue field)) {
                throw Raised.of(EvaluationFailure.INVALID_PATH,
                        "a handle is selected by name, not by "
                                + selector.datatype().literalSpelling());
            }
            if (handle.isContext() && field.canonical().equals("type")) {
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
        if (target instanceof RebolSeries series && selector instanceof IntegerValue(long magnitude)) {
            long index = countedFromTheSeriesPosition(magnitude);
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
            return bitsetHoldsForAPath(members, selector);
        }
        if (target instanceof CharacterValue(int codepoint)
                && selector instanceof WordValue asked) {
            switch (asked.canonical()) {
                case "width" -> {
                    return IntegerValue.of(
                            CharacterColumns.widthOf(codepoint));
                }
                case "size" -> {
                    return IntegerValue.of(
                            CharacterColumns.utf8SizeOf(codepoint));
                }
                default -> { }
            }
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot select " + selector.datatype().literalSpelling()
                        + " from " + target.datatype().literalSpelling());
    }

    private Value bitsetHoldsForAPath(BitsetValue members, Value selector) {
        return new BitsetActions(members).heldForAPath(selector);
    }

    public Value hostPort(String scheme) {
        if (!systemContext.knows("system")) {
            return NoneValue.none();
        }
        if (!(systemContext.slotFor("system").value() instanceof ObjectValue(Context context1))
                || !context1.holds("ports")) {
            return NoneValue.none();
        }
        if (!(context1.ownSlotFor("ports").value() instanceof ObjectValue(Context context))
                || !context.holds(scheme)) {
            return NoneValue.none();
        }
        return context.ownSlotFor(scheme).value();
    }

    private static Value partOfATime(TimeValue time, Value selector) {
        long seconds = Math.abs(time.nanoseconds()) / NANOSECONDS_IN_A_SECOND;
        long fraction = Math.abs(time.nanoseconds()) % NANOSECONDS_IN_A_SECOND;
        int which = switch (selector) {
            case IntegerValue position -> (int) position.magnitude();
            case WordValue field -> switch (field.canonical()) {
                case "hour" -> 1;
                case "minute" -> 2;
                case "second" -> 3;
                default -> throw Raised.of(EvaluationFailure.INVALID_PATH,
                        field.spelling());
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

    private record Selection(
            Slot slot, Value calledBy, List<String> refinements,
            List<String> mentioned, List<WordValue> mentionedAsWritten) {

        Value value() {
            return slot.value();
        }
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
        private boolean bodyOfAnAppliedFunction;

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
                frame.register(parameter.name(), supplied);
                continue;
            }
            boolean asked = supplied.datatype() != Datatype.UNSET && supplied.isTruthy();
            frame.register(parameter.name(),
                    asked ? LogicValue.of(true) : NoneValue.none());
            if (asked) {
                continue;
            }
            while (at + 1 < parameters.size()
                    && parameters.get(at + 1).kind() != ParameterKind.REFINEMENT) {
                at++;
                frame.register(parameters.get(at).name(), NoneValue.none());
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
