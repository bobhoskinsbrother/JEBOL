package org.jebol.domain.eval;

import org.jebol.domain.host.*;
import org.jebol.domain.eval.ports.ActionSentToPort;
import org.jebol.domain.eval.ports.HttpSchemeActor;
import org.jebol.domain.eval.security.PolicyEnforcement;
import org.jebol.domain.value.Construction;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.util.*;
import java.util.function.Predicate;

public final class Evaluator {

    public static final int DEFAULT_MAXIMUM_DEPTH = 10_000;

    public static final int DEFAULT_CHECK_EVERY = 1_000;

    private static final int IDENTITY_BEFORE_FIRST_CIPHER = 2000;

    private final UnfinishedLine output;
    private final Context systemContext;

    private Context runtimeContext;
    private final int maximumDepth;
    private final Interruption interruption;
    private final int checkEvery;

    private final HostPorts ports;

    private BundledModules bundledModules = BundledModules.none();
    private final Construction construction;
    private final MakerSource makerSource;
    private final ErrorWording wording;
    private int lastCipherIdentity = IDENTITY_BEFORE_FIRST_CIPHER;
    private int stepsSinceLastCheck;

    private int framesOpen;

    private final Trace trace = new Trace();

    private final PolicyEnforcement policies = new PolicyEnforcement(this);

    private final Postbox postbox = new Postbox();

    private int extensionsNumbered;

    private static final String EVENT_CALLBACK_IS = "callback";

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

    public record OpenCall(String name, DefinedFunctionValue function, Context locals) {

        public List<String> listSlotNames() {
            List<String> names = new ArrayList<>();
            function.parameters().forEach(parameter -> names.add(parameter.name()));
            names.addAll(function.localNames());
            return names;
        }
    }

    private final Deque<OpenCall> functionsBeingRun = new ArrayDeque<>();

    private final SymbolTable symbols = new SymbolTable();

    private final Deque<Deque<Frame>> walksInProgress = new ArrayDeque<>();

    private String nameOfCallBeingMade = "";

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

    public List<String> nameEveryCallOpenInnermostFirst() {
        return walksInProgress.isEmpty()
                ? List.of()
                : nameEveryCallOpen(walksInProgress.peek());
    }

    public Optional<Value> findCallRunningBelowInnermost() {
        List<Value> running = listCallsRunningInnermostFirst();
        return running.size() < 2 ? Optional.empty() : Optional.of(running.get(1));
    }

    private List<Value> listCallsRunningInnermostFirst() {
        List<Value> running = new ArrayList<>();
        if (walksInProgress.isEmpty()) {
            return running;
        }
        for (Deque<Frame> walk : listEveryWalkInnermostFirst(walksInProgress.peek())) {
            for (Frame open : walk) {
                if (open.callBeingMade != null && open.callBeingMade.isRunning()) {
                    running.add(withoutAskedRefinements(open.callBeingMade.callee()));
                }
            }
            if (!walk.isEmpty() && walk.peekLast().bodyOfAppliedFunction) {
                findFunctionRunningIn(walk.peekLast().context).ifPresent(running::add);
            }
        }
        return running;
    }

    private Value withoutAskedRefinements(Value callee) {
        return callee instanceof NativeValue refined ? refined.withoutAskedRefinements() : callee;
    }

    private Optional<Value> findFunctionRunningIn(Context locals) {
        for (OpenCall open : functionsBeingRun) {
            if (open.locals() == locals) {
                return Optional.of(open.function());
            }
        }
        return Optional.empty();
    }

    private static final String APPLIED_FUNCTION = "-apply-";

    private List<String> nameEveryCallOpen(Deque<Frame> innermostWalk) {
        List<String> named = new ArrayList<>();
        for (Deque<Frame> walk : listEveryWalkInnermostFirst(innermostWalk)) {
            for (Frame open : walk) {
                if (open.callBeingMade != null) {
                    open.callBeingMade.findNameInBacktrace().ifPresent(named::add);
                }
            }
            if (!walk.isEmpty() && walk.peekLast().bodyOfAppliedFunction) {
                named.add(APPLIED_FUNCTION);
            }
        }
        return named;
    }

    private List<Deque<Frame>> listEveryWalkInnermostFirst(Deque<Frame> innermostWalk) {
        List<Deque<Frame>> walks = new ArrayList<>();
        walks.add(innermostWalk);
        walksInProgress.stream()
                .filter(enclosing -> enclosing != innermostWalk)
                .forEach(walks::add);
        return walks;
    }

    public Evaluator(Context systemContext, OutputPort output) {
        this(systemContext, HostPorts.none(), output);
    }

    public Evaluator(Context systemContext, HostPorts ports, OutputPort output) {
        this(systemContext, ports, Construction.refused(), MakerSource.none(), output,
                DEFAULT_MAXIMUM_DEPTH, Interruption.never(), DEFAULT_CHECK_EVERY);
    }

    public Evaluator(
            Context systemContext,
            HostPorts ports,
            Construction construction,
            MakerSource makerSource,
            OutputPort output,
            int maximumDepth,
            Interruption interruption,
            int checkEvery) {
        this.systemContext = systemContext;
        this.ports = ports;
        this.construction = construction;
        this.makerSource = makerSource;
        this.wording = new ErrorWording(systemContext);
        this.output = new UnfinishedLine(output);
        this.maximumDepth = maximumDepth;
        this.interruption = interruption;
        this.checkEvery = checkEvery;
    }

    private OutputPort alsoWritingTo;

    public OutputPort findOutput() {
        if (alsoWritingTo == null) {
            return output;
        }
        OutputPort copyingTo = alsoWritingTo;
        return text -> {
            output.write(text);
            copyingTo.write(text);
        };
    }

    public String readUnfinishedLine() {
        return output.read();
    }

    public void alsoWriteTo(OutputPort second) {
        this.alsoWritingTo = second;
    }

    public void stopEchoing() {
        this.alsoWritingTo = null;
    }

    public HostPorts ports() {
        return ports;
    }

    public void installHostsHttpActor() {
        ports.http().ifPresent(port -> {
            for (String scheme : List.of("http", "https")) {
                findSchemeNamed(scheme).orElseThrow(() -> new IllegalStateException(
                                "the " + scheme + " scheme is not registered yet"))
                        .context().ownSlotFor("actor")
                        .setBeneathAnyProtection(new HttpSchemeActor(scheme, port));
            }
        });
    }

    private Optional<ObjectValue> findSchemeNamed(String name) {
        if (!systemContext.contains("system")) {
            return Optional.empty();
        }
        Value definition = systemContext.findSlotFor("system").value()
                .readFieldValue("schemes").readFieldValue(name);
        return definition instanceof ObjectValue found
                ? Optional.of(found)
                : Optional.empty();
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

    public Maker makeMakerIn(Context where) {
        return makerSource.makeMakerFor(this, where);
    }

    public int nextCipherIdentity() {
        return ++lastCipherIdentity;
    }

    private final CompressionMethods compressionMethods = new CompressionMethods();

    public CompressionMethods compressionMethods() {
        return compressionMethods;
    }

    public Value askActor(ObjectValue actor, ActionSentToPort sent) {
        String named = sent.action().nativeName();
        Value function = actor.context().holds(named)
                ? actor.context().ownSlotFor(named).value()
                : NoneValue.none();
        if (!(function instanceof DefinedFunctionValue able)) {
            throw Raised.of(EvaluationFailure.NO_PORT_ACTION, SetWordValue.of(named));
        }
        return applyFunction(able, sent.layOut(able));
    }

    public Value readSimpleValue(Value given, Context where) {
        if (given instanceof AnyWordValue word && word.looksUpItsDeclaration()) {
            return wordValueIn(word, where);
        }
        if (given instanceof AnyBlockValue path && path.looksUpItsDeclaration()) {
            return pathValueIn(path, where);
        }
        return given;
    }

    public List<Value> reduceLeavingSetWords(AnyBlockValue block) {
        List<Value> results = new ArrayList<>();
        AnyBlockValue at = block;
        while (!at.atTail()) {
            Value here = at.pickFirst();
            if (here instanceof SetWordValue || here instanceof SetPathValue) {
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
        if (systemContext.readValueAt("system", "state") instanceof ObjectValue(Context state)) {
            state.register(field, value);
        }
    }

    public void setSystemOption(String field, Value value) {
        if (systemContext.readValueAt("system", "options") instanceof ObjectValue(Context options)) {
            options.register(field, value);
        }
    }

    public Outcome applyAsHostApplies(String systemFunction) {
        try {
            return new Outcome.Completed(runUnsignalled(() -> applyFunction(
                    systemContext.findSystemFunctionNamed(systemFunction), List.of())));
        } catch (Raised raised) {
            return new Outcome.Raised(raised.error());
        }
    }

    public ErrorValue withCallLocation(ErrorValue error) {
        if (walksInProgress.isEmpty()) {
            return speakHere(error);
        }
        return sayWhereItCameFrom(new Raised(error), walksInProgress.peek()).error();
    }

    public ErrorValue speakHere(ErrorValue error) {
        return error.wording().equals(wording) ? error : error.speakWith(wording);
    }

    public Value applyFunction(Value callee, List<Value> arguments) {
        return switch (callee) {
            case DefinedFunctionValue function -> {
                List<Parameter> parameters = function.parameters();
                checkArgumentTypes(parameters, arguments, APPLIED_FUNCTION);
                Context locals = function.makeFreshCallFrame();
                bindArgumentsPositionally(locals, function, arguments);
                AnyBlockValue running = findBodyThisCallRuns(function, locals);
                lendFrameToFunction(function, locals);
                functionsBeingRun.push(new OpenCall("", function, locals));
                try {
                    yield walk(running, locals, 1, null, APPLIED_FUNCTION_BODY);
                } catch (ReturnSignal returned) {
                    yield returned.value();
                } finally {
                    handBackFrameTakenOverBy(functionsBeingRun.pop());
                }
            }
            case NativeValue built -> runNative(built, arguments, systemContext);
            case OperatorValue operator -> invokeUnderlying(operator, arguments, systemContext);
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    callee.datatype().literalSpelling() + " is not callable");
        };
    }

    public Value applyToCaught(Value handler, Value caught, Value carriedName) {
        if (!(handler instanceof DefinedFunctionValue function)) {
            return applyFunction(handler, List.of(caught, carriedName));
        }
        List<Value> valueAndName = List.of(caught, carriedName);
        checkArgumentTypes(function.parameters(), valueAndName, "catch");
        long positionalArity = function.parameters().stream()
                .filter(Parameter::consumesArgument)
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

    public PolicyEnforcement policies() {
        return policies;
    }

    public Postbox postbox() {
        return postbox;
    }

    public int numberNextExtension() {
        return ++extensionsNumbered;
    }

    public void deliverWhatWasPosted() {
        postbox.deliverEachEvent(posted -> EventCatalogue.findTypeIndexOf(posted.eventType())
                .ifPresent(type -> queueOnSystemPort(
                        new EventValue(type, Set.of(), EventValue.Model.PORT, 0, posted.port()))));
        postbox.deliverEachCallback(callback -> EventCatalogue.findTypeIndexOf(EVENT_CALLBACK_IS)
                .ifPresent(type -> queueOnSystemPort(new EventValue(type, Set.of(),
                        EventValue.Model.CALLBACK, 0, JavaObjectValue.of(callback)))));
    }

    public void queueOnSystemPort(EventValue event) {
        if (findHostPort("system") instanceof PortValue queue) {
            queue.findEventQueue().ifPresent(onIt -> onIt.storage().insertAt(onIt.storage().length() + 1, event));
        }
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
        Context into = findContextStringIsDoneIn();
        return walk(Binder.bindAndDefine(read.values().orElseThrow(), into), into, 1);
    }

    private Context findContextStringIsDoneIn() {
        if (systemContext.readValueAt("system", "contexts", "user") instanceof ObjectValue(Context user)) {
            return user;
        }
        return runtimeContext == null ? systemContext : runtimeContext;
    }

    public void putRuntimeWordsIn(Context context) {
        this.runtimeContext = context;
    }

    public Outcome evaluate(AnyBlockValue code, Context context) {
        try {
            return new Outcome.Completed(runUnsignalled(() -> walk(code, context, 1)));
        } catch (Raised raised) {
            return new Outcome.Raised(raised.error());
        }
    }

    private static Value runUnsignalled(java.util.function.Supplier<Value> walking) {
        try {
            return walking.get();
        } catch (ThrownSignal thrown) {
            throw new Raised(ErrorValue.of(ErrorCategory.THROW, "throw",
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
            throw new Raised(ErrorValue.of(ErrorCategory.THROW, "return",
                    "a return outside a function",
                    returned.value() instanceof UnsetValue
                            ? NoneValue.none()
                            : returned.value()));
        }
    }

    public Value evaluateOrRaise(AnyBlockValue code, Context context) {
        return walk(code, context, 1, null);
    }

    public ObjectValue makeFreshObjectWithin(Context enclosing) {
        ObjectValue built = new ObjectValue(Context.makeChildOf(enclosing));
        built.context().pointItsOwnSelfAt(built);
        return built;
    }

    public ObjectValue evaluateInto(ObjectValue built, AnyBlockValue body) {
        Context fields = built.context();
        body.setWordsFromHere().forEach(fields::refuseFieldSpeltLikeItsOwnSelf);
        body.setWordsFromHere().stream().map(AnyWordValue::spelling).forEach(fields::register);
        evaluateOrRaise(Binder.bindOnly(body, fields, fields.listNamesItBinds()), fields);
        return built;
    }

    public List<Value> evaluateEachOrRaise(AnyBlockValue code, Context context) {
        List<Value> results = new ArrayList<>();
        walk(code, context, 1,
                (produced, startedAt, stoppedBefore) -> results.add(produced));
        return results;
    }

    public AnyBlockValue evaluateEachKeepingLineShape(
            AnyBlockValue code, Context context) {

        BlockStorage built = new BlockStorage();
        walk(code, context, 1, (produced, startedAt, stoppedBefore) -> {
            built.append(produced);
            if (isItemAsWritten(code, startedAt, stoppedBefore)) {
                built.setLineBreakAt(built.length(),
                        code.storage().breaksLineAt(startedAt));
            }
            return true;
        });
        return BlockValue.wrapStorage(built);
    }

    private static boolean isItemAsWritten(
            AnyBlockValue code, int startedAt, int stoppedBefore) {

        return stoppedBefore == startedAt + 1
                && startedAt >= 1
                && startedAt <= code.storageLength()
                && !WORKS_SOMETHING_OUT.contains(
                        code.storage().at(startedAt).datatype());
    }

    private static final Set<Datatype> WORKS_SOMETHING_OUT =
            TypesetValue.ANY_PATH.membersAnd(
                    WordValue.TYPE, SetWordValue.TYPE, GetWordValue.TYPE,
                    LitWordValue.TYPE, ParenValue.TYPE,
                    FunctionValue.TYPE, ClosureValue.TYPE, NativeValue.TYPE,
                    ActionValue.TYPE, OperatorValue.TYPE, CommandValue.TYPE);

    public Value evaluateUntilOrRaise(
            AnyBlockValue code, Context context, Predicate<Value> stopsHere) {
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

    public Step evaluateNextOrRaise(AnyBlockValue code, Context context) {
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

    private Value walk(AnyBlockValue code, Context context, int depth) {
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

    private Value walk(AnyBlockValue code, Context context, int depth, ResultSink sink) {
        return walk(code, context, depth, sink, ORDINARY_BLOCK);
    }

    private static final boolean ORDINARY_BLOCK = false;

    private static final boolean APPLIED_FUNCTION_BODY = true;

    private Value walk(AnyBlockValue code, Context context, int depth, ResultSink sink,
                       boolean bodyOfAppliedFunction) {
        Deque<Frame> frames = new ArrayDeque<>();
        Frame root = new Frame(code, context, depth);
        root.sink = sink;
        root.bodyOfAppliedFunction = bodyOfAppliedFunction;
        frames.push(root);
        int callsOpenBeforeWalk = functionsBeingRun.size();
        walksInProgress.push(frames);
        try {
            return walkFrames(frames);
        } finally {
            walksInProgress.pop();
            while (functionsBeingRun.size() > callsOpenBeforeWalk) {
                handBackFrameTakenOverBy(functionsBeingRun.pop());
            }
        }
    }

    private Value walkFrames(Deque<Frame> frames) {
        try {
            return walkFramesUntilDone(frames);
        } catch (OutOfMemoryError exhausted) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    private Value walkFramesUntilDone(Deque<Frame> frames) {
        while (true) {
            stopIfAsked();
            Frame frame = frames.peek();
            trace.moveToDepth(frames.size() - 1);
            if (frame.expressionStartedAt < 0) {
                frame.expressionStartedAt = frame.position;
            }

            if (frame.stopped || frame.atEnd()) {
                if (!frame.stopped && !frame.pendingCalls.isEmpty()) {
                    PendingCall gathering = frame.pendingCalls.peek();
                    if (gathering.takesNextValueAsWritten()) {
                        deliver(frame, UnsetValue.unset(), frames);
                        continue;
                    }
                    throw Raised.of(EvaluationFailure.NO_ARG,
                            WordValue.of(gathering.nameInArgumentError()),
                            gathering.describeArgumentStillWanted());
                }
                Value finished = frame.lastResult;
                if (frame.functionBody && !functionsBeingRun.isEmpty()) {
                    handBackFrameTakenOverBy(functionsBeingRun.pop());
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
                throw sayWhereItCameFrom(raised, frames);
            }
        }
    }

    private Raised sayWhereItCameFrom(Raised escaping, Deque<Frame> frames) {
        Raised raised = speakHere(escaping);
        if (raised.error().whereChain().isPresent()) {
            return raised;
        }
        List<Value> chain = nameEveryCallOpen(frames).stream()
                .<Value>map(WordValue::of)
                .toList();
        Value nearest = null;
        for (Frame open : listEveryCallOpenInnermostFirst(frames)) {
            PendingCall running = open.callBeingMade;
            if (nearest == null && running != null && running.startedAt() >= 0) {
                nearest = open.code.atIndex(running.startedAt());
            }
        }
        ErrorValue said = raised.error();
        if (nearest != null && said.near().isEmpty()) {
            said = said.placeNear(nearest);
        }
        if (!chain.isEmpty()) {
            said = said.traceThrough(BlockValue.of(chain));
        }
        return said == raised.error() ? raised : new Raised(said);
    }

    private Raised speakHere(Raised escaping) {
        ErrorValue spoken = speakHere(escaping.error());
        return spoken == escaping.error() ? escaping : new Raised(spoken);
    }

    private List<Frame> listEveryCallOpenInnermostFirst(Deque<Frame> frames) {
        List<Frame> open = new ArrayList<>(frames);
        for (Deque<Frame> enclosing : walksInProgress) {
            if (enclosing != frames) {
                open.addAll(enclosing);
            }
        }
        return open;
    }

    private void push(Deque<Frame> frames, AnyBlockValue code, Context context) {
        push(frames, code, context, null);
    }

    private void push(
            Deque<Frame> frames, AnyBlockValue code, Context context, DefinedFunctionValue being) {
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
            lendFrameToFunction(being, context);
            functionsBeingRun.push(new OpenCall(nameOfCallBeingMade, being, context));
            nameOfCallBeingMade = "";
            lastWordCalledThrough = "";
        }
    }

    private Context findFrameThisCallTakesOverFrom(DefinedFunctionValue function) {
        for (OpenCall open : functionsBeingRun) {
            if (open.function() == function) {
                return open.locals();
            }
        }
        return function.declaredWords();
    }

    private void lendFrameToFunction(DefinedFunctionValue being, Context locals) {
        if (being instanceof FunctionValue) {
            findFrameThisCallTakesOverFrom(being).giveWayTo(locals);
        }
    }

    private void handBackFrameTakenOverBy(OpenCall ending) {
        if (ending.function() instanceof ClosureValue) {
            return;
        }
        ending.locals().giveWayTo(null);
        findFrameThisCallTakesOverFrom(ending.function()).giveWayTo(null);
    }

    private static AnyBlockValue findBodyThisCallRuns(
            DefinedFunctionValue function, Context locals) {

        return switch (function) {
            case ClosureValue closure -> Binder.rebindWhatNamedFunction(
                    closure.body(), closure.declaredWords(), locals);
            case FunctionValue plain -> plain.body();
        };
    }

    private StepOutcome takeOneStep(Frame frame, Deque<Frame> frames) {
        Value input = frame.current();
        if (trace.isOn()) {
            trace.line(frame.position, input, frame.context);
        }
        frame.startedThisValueAt = frame.position;
        frame.callBeingMade = null;
        frame.advance();

        return switch (input) {
            case WordValue word -> evaluateWord(frame, frames, word);
            case GetWordValue word -> StepOutcome.of(evaluateGetWord(word));
            case LitWordValue quoted -> StepOutcome.of(quoted.asWord());
            case SetWordValue assigning -> evaluateSetWord(frame, assigning);
            case LitPathValue quoted -> StepOutcome.of(quoted.asPath());
            case ParenValue paren -> {
                push(frames, paren.asBlock(), frame.context);
                yield StepOutcome.waiting();
            }
            case GetPathValue path -> StepOutcome.of(select(path.asPath(), frame.context).value());
            case PathValue path -> evaluatePath(frame, frames, path);
            case SetPathValue path -> evaluateSetPath(frame, path);
            case ErrorValue raised -> throw new Raised(raised);
            default -> input instanceof AnyFunctionValue
                            && !(input instanceof OperatorValue)
                    ? callWithoutName(frame, frames, input)
                    : StepOutcome.of(input);
        };
    }

    private void deliver(Frame frame, Value produced, Deque<Frame> frames) {
        Value carrying = produced;
        while (true) {
            PendingCall waiting = frame.pendingCalls.peek();
            boolean feedingOperator = waiting != null && waiting.isInfix();
            if (!feedingOperator) {
                Optional<OperatorValue> operator = findOperatorAt(frame);
                if (operator.isPresent()) {
                    int wroteOperatorAt = frame.position;
                    frame.advance();
                    PendingCall infix =
                            PendingCall.makeInfix(operator.orElseThrow(), carrying);
                    infix.startAt(wroteOperatorAt,
                            readNameWrittenAt(frame, wroteOperatorAt, OPERATOR_TAKES_NO_REFINEMENT));
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
            requireParameterAccepts(waiting, carrying);
            waiting.accept(carrying);
            if (!waiting.isSatisfied()) {
                return;
            }
            frame.pendingCalls.pop();
            frame.callBeingMade = waiting;
            StepOutcome outcome = invoke(frame, waiting, frames);
            if (isStillRunningHavingPushedBody(outcome)) {
                return;
            }
            StepOutcome.Produced invoked = (StepOutcome.Produced) outcome;
            frame.callBeingMade = null;
            carrying = invoked.value();
        }
    }

    private void requireParameterAccepts(PendingCall waiting, Value arriving) {
        waiting.findParameterBeingFilled()
                .filter(parameter -> isSwitchedOnFor(waiting, parameter))
                .ifPresent(parameter -> refuseUnlessAccepted(
                        parameter, arriving, waiting.nameInArgumentError()));
    }

    private boolean isSwitchedOnFor(PendingCall waiting, Parameter parameter) {
        return parameter.owningRefinement().map(waiting.refinements()::contains).orElse(true);
    }

    private static boolean isStillRunningHavingPushedBody(StepOutcome outcome) {
        return !(outcome instanceof StepOutcome.Produced);
    }

    private Optional<OperatorValue> findOperatorAt(Frame frame) {
        if (frame.atEnd()) {
            return Optional.empty();
        }
        if (!(frame.current() instanceof WordValue word)) {
            return Optional.empty();
        }
        ContextSlot slot = word.binding().findSlotOrNone(word.canonical());
        if (slot == null) {
            return Optional.empty();
        }
        return slot.value() instanceof OperatorValue operator
                ? Optional.of(operator)
                : Optional.empty();
    }

    private StepOutcome callWithoutName(
            Frame frame, Deque<Frame> frames, Value callee) {
        lastWordCalledThrough = "";
        return startCall(frame, frames, callee, List.of());
    }

    private StepOutcome evaluateWord(
            Frame frame, Deque<Frame> frames, AnyWordValue word) {
        ContextSlot slot = resolve(word);
        Value bound = slot.value();
        if (bound instanceof UnsetValue) {
            throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
        }
        if (bound instanceof OperatorValue) {
            boolean atVeryHead = frame.position - 1 <= 1;
            throw atVeryHead
                    ? Raised.of(EvaluationFailure.NO_OP_ARG,
                            "the operator " + word.spelling()
                                    + " has nothing on its left")
                    : Raised.of(EvaluationFailure.MISSING_ARG,
                            "the operator " + word.spelling()
                                    + " has nothing on its left");
        }
        if (!(bound instanceof AnyFunctionValue)) {
            return StepOutcome.of(bound);
        }
        lastWordCalledThrough = word.spelling();
        return startCall(frame, frames, bound, List.of());
    }

    private Value evaluateGetWord(AnyWordValue word) {
        return resolve(word).value();
    }

    private StepOutcome evaluateSetWord(Frame frame, AnyWordValue word) {
        ContextSlot slot = word.binding().findSlotOrNone(word.canonical());
        if (slot == null) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        if (frame.atEnd()) {
            throw Raised.of(EvaluationFailure.NEED_VALUE, word);
        }
        word.refuseToBeWrittenWhenItReachesItsContextsOwnSelf();
        frame.pendingCalls.push(PendingCall.makeAssignment(slot));
        return StepOutcome.waiting();
    }

    private static boolean asksForReEvaluation(Value argument) {
        return switch (argument) {
            case AnyWordValue word -> word.looksUpItsDeclaration();
            case AnyBlockValue path -> path instanceof PathValue;
            default -> argument instanceof AnyFunctionValue;
        };
    }

    public Value wordValueIn(AnyWordValue word, Context context) {
        return resolve(word.isBound() ? word : word.bindTo(context)).value();
    }

    public Value pathValueIn(AnyBlockValue path, Context context) {
        return select(path, context).value();
    }

    private ContextSlot resolve(AnyWordValue word) {
        ContextSlot slot = word.binding().findSlotOrNone(word.canonical());
        if (slot == null) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        return slot;
    }

    private StepOutcome startCall(
            Frame frame, Deque<Frame> frames, Value callee, List<String> refinements) {
        return startCall(frame, frames, callee, refinements, refinements);
    }

    private StepOutcome startCall(
            Frame frame, Deque<Frame> frames, Value callee,
            List<String> refinements, List<String> mentioned) {
        PendingCall call = PendingCall.prefix(callee, refinements, mentioned);
        call.startAt(frame.startedThisValueAt,
                readNameWrittenAt(frame, frame.startedThisValueAt, mentioned.size()));
        if (needsNoArguments(call)) {
            frame.callBeingMade = call;
            StepOutcome outcome = invoke(frame, call, frames);
            if (outcome instanceof StepOutcome.Produced) {
                frame.callBeingMade = null;
            }
            return outcome;
        }
        frame.pendingCalls.push(call);
        return StepOutcome.waiting();
    }

    private static boolean needsNoArguments(
            PendingCall call) {
        return call.isSatisfied();
    }

    private static final int OPERATOR_TAKES_NO_REFINEMENT = 0;

    private String readNameWrittenAt(Frame frame, int position, int refinementsWritten) {
        if (position < frame.code.index() || position > frame.code.storageLength()) {
            return null;
        }
        Value written = frame.code.storage().at(position);
        if (written instanceof AnyWordValue word) {
            return word.spelling();
        }
        if (written instanceof PathValue path) {
            int reachingFunction = path.remaining().size() - refinementsWritten - 1;
            if (reachingFunction >= 0
                    && path.remaining().get(reachingFunction) instanceof AnyWordValue reached) {
                return reached.spelling();
            }
        }
        return null;
    }

    private StepOutcome invoke(Frame frame, PendingCall call, Deque<Frame> frames) {
        if (call.isAssignment()) {
            Value given = call.takeOnlyArgument();
            if (call.slot() != null && given instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NEED_VALUE,
                        SetWordValue.of(call.slot().spelling()));
            }
            if (call.slot() != null && call.slot().isProtected()) {
                throw Raised.of(EvaluationFailure.LOCKED_WORD,
                        (Value) SetWordValue.of(call.slot().spelling()));
            }
            if (call.destination() != null) {
                try {
                    call.destination().accept(given);
                } catch (ProtectedFromChange refused) {
                    throw Raised.of(EvaluationFailure.PROTECTED);
                }
                return StepOutcome.of(given);
            }
            call.slot().setValue(given);
            return StepOutcome.of(given);
        }
        List<Value> ordered = call.listArgumentsInDeclaredOrder();
        return switch (call.callee()) {
            case NativeValue built -> {
                if (trace.isOn()) {
                    trace.call(built.nativeName(), built, ordered);
                }
                Value produced = runNative(built, ordered, frame.context,
                        EVERY_ARGUMENT_IS_CHECKED, call.nameInArgumentError(), call::enter);
                if (trace.isOn()) {
                    trace.traceAnswer(built.nativeName(), produced);
                }
                yield built.nativeName().equals("do")
                        && produced instanceof AnyFunctionValue
                        && !ordered.isEmpty()
                        && asksForReEvaluation(ordered.get(0))
                        ? startCall(frame, frames, produced, List.of())
                        : StepOutcome.of(produced);
            }
            case OperatorValue operator -> StepOutcome.of(invokeUnderlying(
                    operator, ordered, frame.context,
                    call.nameInArgumentError(), call::enter));
            case DefinedFunctionValue function -> {
                nameOfCallBeingMade = lastWordCalledThrough;
                if (trace.isOn()) {
                    trace.call(nameOfCallBeingMade == null
                            ? "?" : nameOfCallBeingMade, function, ordered);
                }
                yield runFunction(frames, function, call, ordered);
            }
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    call.callee().datatype().literalSpelling() + " is not callable");
        };
    }

    private Value invokeUnderlying(
            OperatorValue operator, List<Value> arguments, Context context) {
        return invokeUnderlying(operator, arguments, context, APPLIED_FUNCTION, NOBODY_IS_WAITING_TO_HEAR);
    }

    private static final Runnable NOBODY_IS_WAITING_TO_HEAR = () -> { };

    private Value invokeUnderlying(OperatorValue operator, List<Value> arguments,
            Context context, String calledAs, Runnable onceArgumentsPass) {
        return switch (operator.underlying()) {
            case NativeValue built -> runNative(built, arguments, context,
                    LEFT_OPERAND_IS_NOT_CHECKED, calledAs, onceArgumentsPass);
            case DefinedFunctionValue function -> {
                onceArgumentsPass.run();
                yield applyFunction(function, arguments);
            }
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "operator " + operator.operatorName() + " has no runnable body");
        };
    }

    private static final int EVERY_ARGUMENT_IS_CHECKED = 0;

    private static final int LEFT_OPERAND_IS_NOT_CHECKED = 1;

    private Value runNative(
            NativeValue built, List<Value> arguments, Context context) {
        return runNative(built, arguments, context, EVERY_ARGUMENT_IS_CHECKED,
                APPLIED_FUNCTION, NOBODY_IS_WAITING_TO_HEAR);
    }

    private Value runNative(NativeValue built, List<Value> arguments, Context context,
            int checkedFrom, String calledAs, Runnable onceArgumentsPass) {
        checkArgumentTypes(built, arguments, calledAs, checkedFrom);
        RefinedCallable behaviour = built.behaviour();
        onceArgumentsPass.run();
        nativesCalled++;
        Value produced;
        try {
            Optional<Value> theActorsOwn = ActionSentToPort.answer(
                    built, arguments, built.askedRefinements(), this);
            produced = theActorsOwn.isPresent()
                    ? theActorsOwn.orElseThrow()
                    : behaviour.call(arguments, this, context, built.askedRefinements());
        } catch (ProtectedFromChange refused) {
            throw Raised.of(EvaluationFailure.PROTECTED);
        } catch (SlotIsProtected refused) {
            throw Raised.of(EvaluationFailure.LOCKED_WORD, refused.spelling());
        } catch (StackOverflowError structureWithNoEnd) {
            throw Raised.of(EvaluationFailure.STACK_OVERFLOW);
        } catch (OutOfMemoryError exhausted) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
        if (produced == null) {
            throw new IllegalStateException(
                    built.nativeName() + " returned null; use UnsetValue.unset()");
        }
        return produced;
    }

    private StepOutcome runFunction(
            Deque<Frame> frames, DefinedFunctionValue function, PendingCall call,
            List<Value> arguments) {
        List<String> refinements = call.refinements();
        checkArgumentTypes(function.parameters(),
                refinements::contains, arguments, call.nameInArgumentError());
        call.enter();
        Context locals = function.makeFreshCallFrame();
        fillFrameFromCall(locals, function, refinements, arguments);
        push(frames, findBodyThisCallRuns(function, locals), locals, function);
        return StepOutcome.waiting();
    }

    private void fillFrameFromCall(
            Context locals, DefinedFunctionValue function, List<String> refinements,
            List<Value> arguments) {
        List<Parameter> parameters = function.parameters();
        int at = 0;
        for (int index = 0; index < parameters.size(); index++) {
            Parameter parameter = parameters.get(index);
            if (parameter.kind() == ParameterKind.REFINEMENT) {
                ContextSlot switched = function.frameSlotForParameter(locals, index);
                if (refinements.contains(switched.canonical())) {
                    switched.setValue(LogicValue.yes());
                }
                continue;
            }
            if (!parameter.consumesArgument() || !isGranted(parameter, refinements)) {
                continue;
            }
            if (at < arguments.size()) {
                function.frameSlotForParameter(locals, index).setValue(arguments.get(at));
            }
            at++;
        }
    }

    private boolean isGranted(Parameter parameter, List<String> refinements) {
        return parameter.owningRefinement().map(refinements::contains).orElse(true);
    }

    private void checkArgumentTypes(
            NativeValue built, List<Value> arguments, String calleeName, int checkedFrom) {
        checkArgumentTypes(built.parameters(), built.askedRefinements()::contains,
                arguments, calleeName, checkedFrom);
    }

    private static final Predicate<String> NOTHING_WAS_ASKED_FOR = asked -> false;

    private void checkArgumentTypes(
            List<Parameter> parameters, List<Value> arguments, String calleeName) {
        checkArgumentTypes(parameters, NOTHING_WAS_ASKED_FOR, arguments, calleeName,
                EVERY_ARGUMENT_IS_CHECKED);
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, Predicate<String> asked,
            List<Value> arguments, String calleeName) {
        checkArgumentTypes(parameters, asked, arguments, calleeName, EVERY_ARGUMENT_IS_CHECKED);
    }

    private void checkArgumentTypes(
            List<Parameter> parameters, Predicate<String> asked,
            List<Value> arguments, String calleeName, int checkedFrom) {
        int at = 0;
        for (Parameter parameter : parameters) {
            if (!parameter.consumesArgument()) {
                continue;
            }
            boolean takesArgumentThisCall = parameter.owningRefinement()
                    .map(asked::test).orElse(true);
            if (!takesArgumentThisCall) {
                continue;
            }
            if (at >= arguments.size()) {
                return;
            }
            if (at >= checkedFrom) {
                refuseUnlessAccepted(parameter, arguments.get(at), calleeName);
            }
            at++;
        }
    }

    private void refuseUnlessAccepted(Parameter parameter, Value argument, String calleeName) {
        if (!parameter.accepts(argument.datatype())) {
            throw new Raised(ErrorValue.of(
                    EvaluationFailure.EXPECT_ARG.category(),
                    EvaluationFailure.EXPECT_ARG.errorId(),
                    calleeName + " does not allow "
                            + argument.datatype().literalSpelling()
                            + " for its " + parameter.name() + " argument",
                    WordValue.of(calleeName),
                    parameter.asWrittenInSpec(),
                    argument.datatype()));
        }
    }

    private StepOutcome evaluatePath(
            Frame frame, Deque<Frame> frames, AnyBlockValue path) {
        Selection selection = select(path, frame.context);
        if (!(selection.value() instanceof AnyFunctionValue)) {
            return StepOutcome.of(selection.value());
        }
        return startCall(
                frame, frames,
                refine(selection),
                selection.refinements(), selection.mentioned());
    }

    private Value refine(Selection selection) {
        Value callee = selection.value();
        List<String> mentioned = selection.mentioned();
        if (callee instanceof DefinedFunctionValue written) {
            for (int index = 0; index < mentioned.size(); index++) {
                if (!declaresRefinement(written, mentioned.get(index))) {
                    throw refuseUnknownRefinement(selection, index);
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
                if (systemContext.contains(refinedName)) {
                    return systemContext.findSlotFor(refinedName).value();
                }
                throw refuseUnknownRefinement(selection, index);
            }
        }
        return selection.refinements().isEmpty()
                ? callee
                : built.askFor(Set.copyOf(selection.refinements()));
    }

    private Raised refuseUnknownRefinement(Selection selection, int index) {
        Value calledBy = selection.calledBy() instanceof AnyWordValue word
                ? word.asWord()
                : selection.calledBy();
        return Raised.of(EvaluationFailure.NO_REFINE,
                calledBy, selection.mentionedAsWritten().get(index));
    }

    private static boolean declaresRefinement(DefinedFunctionValue written, String refinement) {
        return written.parameters().stream()
                .anyMatch(parameter -> parameter.kind() == ParameterKind.REFINEMENT
                        && parameter.name().equalsIgnoreCase(refinement));
    }

    private StepOutcome evaluateSetPath(Frame frame, AnyBlockValue path) {
        if (frame.atEnd()) {
            throw Raised.of(EvaluationFailure.NEED_VALUE, path);
        }
        frame.pendingCalls.push(PendingCall.makeAssignmentInto(
                written -> writeThroughPath(frame, path, written)));
        return StepOutcome.waiting();
    }

    private void writeIntoStructAt(
            AnyBlockValue elements, IntegerValue which, StructValue given) {
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

    private void writeThroughPath(Frame frame, AnyBlockValue path, Value written) {
        if (written instanceof UnsetValue) {
            throw Raised.of(EvaluationFailure.NEED_VALUE, path);
        }
        List<Value> segments = path.remaining();
        AnyBlockValue allButLast = PathValue.of(segments.subList(0, segments.size() - 1));
        Slot place = select(allButLast, path, frame.context).slot();
        refusePathIntoSomethingWithNoParts(path, place.value(), segments.size() - 2);
        try {
            writeThroughLastSegment(frame, path, place, written);
        } catch (Raised refused) {
            throw refuseAt(refused, path, segments.getLast());
        }
    }

    private void writeThroughLastSegment(
            Frame frame, AnyBlockValue path, Slot place, Value written) {

        List<Value> segments = path.remaining();
        Value target = place.value();
        Value lastSegment = segments.get(segments.size() - 1);

        if (segments.size() == 3 && lastSegment instanceof IntegerValue(long magnitude1)
                && select(PathValue.of(segments.subList(0, 1)),
                        frame.context).value() instanceof ImageValue image) {
            Value pixelSegment = makeSelectorFor(segments.get(1), frame.context);
            ImagePath.writeOneChannel(
                    image, pixelSegment, (int) magnitude1, written);
            return;
        }

        if (target instanceof EventValue event && lastSegment instanceof AnyWordValue field) {
            place.setValue(EventPath.write(event, field.canonical(), written)
                    .orElseThrow(() -> Raised.of(
                            EvaluationFailure.BAD_PATH_SET, field.spelling())));
            return;
        }
        if (target instanceof GobValue gob && lastSegment instanceof AnyWordValue field) {
            GobPath.write(gob, field, written);
            return;
        }
        if (segments.size() == 3 && segments.get(1) instanceof AnyWordValue pairField
                && select(PathValue.of(segments.subList(0, 1)),
                        frame.context).value() instanceof GobValue holdingPair
                && GobPath.readField(holdingPair, pairField) instanceof PairValue half) {
            GobPath.write(holdingPair, pairField, half.withHalfWritten(
                    makeSelectorFor(lastSegment, frame.context), written));
            return;
        }
        if (target instanceof PathTarget steppedInto) {
            steppedInto.writeThrough(
                    place, makeSelectorFor(lastSegment, frame.context), written);
            return;
        }
        if (segments.size() >= 3 && target instanceof AnyBlockValue elements
                && written instanceof StructValue given
                && lastSegment instanceof IntegerValue which
                && select(PathValue.of(segments.subList(0, segments.size() - 2)),
                        frame.context).value() instanceof StructValue) {
            writeIntoStructAt(elements, which, given);
            return;
        }
        if (target instanceof AnyBlockValue block) {
            Value selector = makeSelectorFor(lastSegment, frame.context);
            if (BlockPath.isNowhereAtAllSoWriteQuietlyDoesNothing(selector)) {
                return;
            }
            int at = BlockPath.findPositionIn(block, selector)
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                            Molder.mold(selector)));
            SeriesSlot.write(block, at, written);
            return;
        }
        if (target instanceof AnyStringValue joining && joining.isALocation()) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a path on a " + joining.datatype().literalSpelling()
                            + " names another one rather than a place to write");
        }
        if (target instanceof VectorValue vector) {
            VectorPath.write(vector, makeSelectorFor(lastSegment, frame.context), written);
            return;
        }
        if (target instanceof ImageValue picture) {
            ImagePath.writeThroughPath(
                    picture, makeSelectorFor(lastSegment, frame.context), written);
            return;
        }
        if (target instanceof StructValue struct) {
            StructPath.write(struct, makeSelectorFor(lastSegment, frame.context), written);
            return;
        }
        if (target instanceof TimeValue time) {
            place.setValue(new WritingIntoTime().write(
                    time, makeSelectorFor(lastSegment, frame.context), written));
            return;
        }
        if ((target instanceof AnyStringValue || target instanceof BinaryValue)
                && makeSelectorFor(lastSegment, frame.context) instanceof IntegerValue counted) {
            new WritingIntoText((RebolSeries) target).write(counted, written);
            return;
        }
        if (target instanceof RebolSeries series
                && makeSelectorFor(lastSegment, frame.context) instanceof IntegerValue(long magnitude)) {
            SeriesSlot.write(series,
                    series.index() + (int) magnitude - 1, written);
            return;
        }
        if (target instanceof BitsetValue set) {
            Value chosen = makeSelectorFor(lastSegment, frame.context);
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
        if (target instanceof ErrorValue raised && lastSegment instanceof AnyWordValue field) {
            if (!ErrorValue.FIELDS.contains(field.canonical())) {
                throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
            }
            raised.write(field.canonical(), written);
            return;
        }
        if (target instanceof EmailValue address
                && makeSelectorFor(lastSegment, frame.context) instanceof AnyWordValue half
                && (half.canonical().equals("user") || half.canonical().equals("host"))) {
            writeEmailPart(address, half, written);
            return;
        }
        if ((target instanceof AnyStringValue || target instanceof BinaryValue)
                && makeSelectorFor(lastSegment, frame.context) instanceof AnyWordValue) {
            throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH);
    }

    private static long countFromSeriesPosition(long index) {
        return index < 0 ? index + 1 : index;
    }

    private Selection select(AnyBlockValue path, Context context) {
        return select(path, path, context);
    }

    private Slot findSlotWithSegment(
            AnyBlockValue asWritten, Slot holder, Value segment, Context context) {

        Value selector = makeSelectorFor(segment, context);
        try {
            return findSlotWith(holder, selector);
        } catch (Raised refused) {
            throw refuseAt(refused, asWritten, segment);
        }
    }

    private Raised refuseAt(Raised refused, AnyBlockValue asWritten, Value segment) {
        String refusal = refused.error().errorId();
        if (refusal.equals(EvaluationFailure.OUT_OF_RANGE.errorId())
                && refused.error().subject().isEmpty()) {
            return Raised.of(EvaluationFailure.OUT_OF_RANGE, segment);
        }
        for (EvaluationFailure saidOfWholePath : WHOLE_PATH_FAILURES) {
            if (refusal.equals(saidOfWholePath.errorId())) {
                return Raised.of(saidOfWholePath, asWritten, segment);
            }
        }
        return refused;
    }

    private static final List<EvaluationFailure> WHOLE_PATH_FAILURES =
            List.of(EvaluationFailure.INVALID_PATH, EvaluationFailure.BAD_PATH_SET);

    private Selection select(AnyBlockValue path, AnyBlockValue asWritten, Context context) {
        List<Value> segments = path.remaining();
        if (segments.isEmpty()) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, "an empty path selects nothing");
        }
        Slot current = selectFirst(segments.get(0), context);
        Value calledBy = segments.get(0);
        List<String> refinements = new ArrayList<>();
        List<String> mentioned = new ArrayList<>();
        List<AnyWordValue> mentionedAsWritten = new ArrayList<>();

        for (int index = 1; index < segments.size(); index++) {
            Value segment = segments.get(index);
            if (current.value() instanceof AnyFunctionValue) {
                AnyWordValue refinement = refinementWord(segment);
                mentioned.add(refinement.canonical());
                mentionedAsWritten.add(refinement);
                if (isSwitchedOn(refinement, context)) {
                    refinements.add(refinement.canonical());
                }
                continue;
            }
            refusePathIntoSomethingWithNoParts(asWritten, current.value(), index - 1);
            current = findSlotWithSegment(asWritten, current, segment, context);
            calledBy = segment;
        }
        return new Selection(current, calledBy, List.copyOf(refinements),
                List.copyOf(mentioned), List.copyOf(mentionedAsWritten));
    }

    private boolean isSwitchedOn(AnyWordValue refinement, Context context) {
        return !(refinement instanceof GetWordValue)
                || resolve(refinement.isBound() ? refinement : refinement.bindTo(context))
                        .value().isTruthy();
    }

    private Slot findSlotWith(Slot holder, Value selector) {
        Value target = holder.value();
        if (target instanceof PathTarget steppedInto) {
            return steppedInto.placeSteppedIntoBy(selector);
        }
        if (target instanceof RebolSeries series
                && selector instanceof IntegerValue position
                && reachesIntoSeries(series, position)) {
            return new SeriesSlot(series, findPositionIn(series, position),
                    selectWith(target, selector));
        }
        return new ComputedSlot(selectWith(target, selector), selector);
    }

    private static boolean reachesIntoSeries(
            RebolSeries series, IntegerValue position) {

        long counted = countFromSeriesPosition(position.magnitude());
        return counted >= 1 && counted <= series.lengthFromHere();
    }

    private static int findPositionIn(RebolSeries series, IntegerValue position) {
        return series.index()
                + (int) countFromSeriesPosition(position.magnitude()) - 1;
    }

    private void refusePathIntoSomethingWithNoParts(
            AnyBlockValue asWritten, Value current, int reachedThroughSegment) {

        if (!HAVE_NO_PARTS_TO_SELECT.contains(current.datatype())) {
            return;
        }
        if (reachedThroughSegment == VARIABLE_PATH_STARTS_FROM) {
            throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                    asWritten, current.datatype());
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                asWritten, asWritten.remaining().get(reachedThroughSegment));
    }

    private static final int VARIABLE_PATH_STARTS_FROM = 0;

    private static final Set<Datatype> HAVE_NO_PARTS_TO_SELECT =
            TypesetValue.ANY_WORD.membersAnd(
                    UnsetValue.TYPE, NoneValue.TYPE, LogicValue.TYPE,
                    IntegerValue.TYPE, DecimalValue.TYPE, PercentValue.TYPE,
                    MoneyValue.TYPE, Datatype.TYPE, TypesetValue.TYPE);

    private Slot selectFirst(Value segment, Context context) {
        if (segment instanceof AnyWordValue word) {
            AnyWordValue bound = word.isBound() ? word : word.bindTo(context);
            ContextSlot slot = resolve(bound);
            if (slot.value() instanceof UnsetValue) {
                throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
            }
            return slot;
        }
        return new ComputedSlot(segment, segment);
    }

    private Value makeSelectorFor(Value segment, Context context) {
        return withAnyFractionTruncated(resolveSegment(segment, context));
    }

    private Value resolveSegment(Value segment, Context context) {
        if (segment instanceof GetWordValue word) {
            return resolve(word.isBound() ? word : word.bindTo(context)).value();
        }
        if (segment instanceof ParenValue paren) {
            return evaluateOrRaise(Binder.bind(paren.asBlock(), context), context);
        }
        return segment;
    }

    private static Value withAnyFractionTruncated(Value selector) {
        return selector instanceof AnyDecimalValue fractional
                ? IntegerValue.of((long) fractional.quantity())
                : selector;
    }

    private AnyWordValue refinementWord(Value segment) {
        if (segment instanceof AnyWordValue word) {
            return word;
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "a refinement must be a word, not " + segment.datatype().literalSpelling());
    }

    private Value selectWith(Value target, Value selector) {
        if (target instanceof PathTarget steppedInto) {
            return steppedInto.stepIntoBy(selector);
        }
        if (target instanceof TimeValue time) {
            return timePart(time, selector);
        }
        if (target instanceof BitsetValue set && selector instanceof CharacterValue(int codepoint1)) {
            return LogicValue.of(set.holds(codepoint1));
        }
        if (target instanceof ErrorValue raised && selector instanceof AnyWordValue field) {
            return raised.readField(field.canonical()).orElseThrow(() ->
                    Raised.of(EvaluationFailure.INVALID_PATH, field.spelling()));
        }
        if (target instanceof AnyStringValue path && path.isALocation()) {
            return joinOntoPath(path, selector);
        }
        if (target instanceof AnyStringValue text && selector instanceof AnyWordValue field) {
            return switch (field.canonical()) {
                case "length" -> IntegerValue.of(text.lengthFromHere());
                case "size" -> IntegerValue.of(
                        text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                case "width" -> IntegerValue.of(terminalWidth(text.text()));
                case "user" -> findEmailNamedBy(text, field).user();
                case "host" -> findEmailNamedBy(text, field).host();
                default -> throw Raised.of(
                        EvaluationFailure.INVALID_PATH, field.spelling());
            };
        }
        if (target instanceof AnyBlockValue block) {
            return BlockPath.read(block, selector);
        }
        if (target instanceof ImageValue image) {
            return ImagePath.read(image, selector);
        }
        if (target instanceof GobValue gob) {
            return GobPath.read(gob, selector);
        }
        if (target instanceof HandleValue handle) {
            if (!(selector instanceof AnyWordValue field)) {
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
                    findHostPort("event"), findHostPort("callback"), findHostPort("input"));
        }
        if (target instanceof VectorValue vector) {
            return VectorPath.read(vector, selector);
        }
        if (target instanceof StructValue struct) {
            return StructPath.read(struct, selector);
        }
        if (target instanceof RebolSeries series && selector instanceof IntegerValue(long magnitude)) {
            long index = countFromSeriesPosition(magnitude);
            if (index < 1 - (series.index() - 1) || index > series.lengthFromHere()) {
                return NoneValue.none();
            }
            return switch (series) {
                case AnyStringValue text -> CharacterValue.of(
                        text.storage().at(text.index() + (int) index - 1));
                case BinaryValue bytes -> IntegerValue.of(
                        bytes.storage().at(bytes.index() + (int) index - 1));
                case AnyBlockValue block -> block.storage().at(
                        block.index() + (int) index - 1);
                case ImageValue pixels -> ImagePath.read(pixels, selector);
                case GobValue gob -> GobPath.read(gob, selector);
                case VectorValue vector -> VectorPath.read(vector, selector);
            };
        }
        if (target instanceof BitsetValue members) {
            return pickFromBitsetForPath(members, selector);
        }
        if (target instanceof CharacterValue(int codepoint)
                && selector instanceof AnyWordValue asked) {
            switch (asked.canonical()) {
                case "width" -> {
                    return IntegerValue.of(
                            CharacterColumns.columnWidth(codepoint));
                }
                case "size" -> {
                    return IntegerValue.of(
                            CharacterColumns.utf8Size(codepoint));
                }
                default -> { }
            }
        }
        throw Raised.of(EvaluationFailure.INVALID_PATH,
                "cannot select " + selector.datatype().literalSpelling()
                        + " from " + target.datatype().literalSpelling());
    }

    private Value pickFromBitsetForPath(BitsetValue members, Value selector) {
        return new BitsetActions(members).pickForPath(selector);
    }

    public Value findHostPort(String scheme) {
        if (!systemContext.contains("system")) {
            return NoneValue.none();
        }
        if (!(systemContext.findSlotFor("system").value() instanceof ObjectValue(Context context1))
                || !context1.holds("ports")) {
            return NoneValue.none();
        }
        if (!(context1.ownSlotFor("ports").value() instanceof ObjectValue(Context context))
                || !context.holds(scheme)) {
            return NoneValue.none();
        }
        return context.ownSlotFor(scheme).value();
    }

    private static Value timePart(TimeValue time, Value selector) {
        long seconds = Math.abs(time.nanoseconds()) / NANOSECONDS_IN_SECOND;
        long fraction = Math.abs(time.nanoseconds()) % NANOSECONDS_IN_SECOND;
        int which = switch (selector) {
            case IntegerValue position -> (int) position.magnitude();
            case AnyWordValue field -> switch (field.canonical()) {
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
                    : DecimalValue.of(seconds % 60 + (double) fraction / NANOSECONDS_IN_SECOND);
            default -> NoneValue.none();
        };
    }

    private static final long NANOSECONDS_IN_SECOND = 1_000_000_000L;

    private record Selection(
            Slot slot, Value calledBy, List<String> refinements,
            List<String> mentioned, List<AnyWordValue> mentionedAsWritten) {

        Value value() {
            return slot.value();
        }
    }

    private static final class Frame {

        private final AnyBlockValue code;
        private final Context context;
        private final int depth;
        private final Deque<PendingCall> pendingCalls = new ArrayDeque<>();

        private int position;
        private Value lastResult = UnsetValue.unset();
        private ResultSink sink;
        private boolean stopped;
        private boolean functionBody;
        private boolean bodyOfAppliedFunction;

        private int expressionStartedAt = -1;

        Frame(AnyBlockValue code, Context context, int depth) {
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

        private PendingCall callBeingMade;
    }

    private static Value joinOntoPath(AnyStringValue path, Value segment) {
        StringBuilder built = new StringBuilder(path.text());
        if (built.isEmpty() || built.charAt(built.length() - 1) != '/') {
            built.append('/');
        }
        String added = segment instanceof AnyStringValue text
                ? text.text()
                : Molder.mold(segment);
        built.append(added.startsWith("/") || added.startsWith("\\")
                ? added.substring(1)
                : added);
        return path.wrap(built.toString());
    }

    private static int terminalWidth(String text) {
        return TerminalWidth.of(text.codePoints().toArray());
    }

    private static EmailValue findEmailNamedBy(AnyStringValue text, AnyWordValue half) {
        if (!(text instanceof EmailValue email)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, half.canonical());
        }
        return email;
    }

    private static void writeEmailPart(EmailValue address, AnyWordValue half, Value written) {
        String replacement = Molder.form(written);
        if (half.canonical().equals("host")) {
            address.rewriteHostAs(replacement);
        } else {
            address.rewriteUserAs(replacement);
        }
    }

    private void bindArgumentsPositionally(
            Context frame, DefinedFunctionValue function, List<Value> arguments) {

        List<Parameter> parameters = function.parameters();
        for (int at = 0; at < parameters.size(); at++) {
            Value supplied = at < arguments.size()
                    ? arguments.get(at)
                    : UnsetValue.unset();
            if (parameters.get(at).kind() != ParameterKind.REFINEMENT) {
                fillParameterSlot(frame, function, at, supplied);
                continue;
            }
            boolean asked = supplied.datatype() != UnsetValue.TYPE && supplied.isTruthy();
            fillParameterSlot(frame, function, at, asked ? LogicValue.of(true) : NoneValue.none());
            while (!asked && at + 1 < parameters.size()
                    && parameters.get(at + 1).kind() != ParameterKind.REFINEMENT) {
                at++;
            }
        }
    }

    private void fillParameterSlot(
            Context frame, DefinedFunctionValue function, int parameterIndex, Value value) {
        if (function.holdsFrameSlotForParameter(parameterIndex)) {
            function.frameSlotForParameter(frame, parameterIndex).setValue(value);
        }
    }
}
