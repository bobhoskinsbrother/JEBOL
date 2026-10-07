package org.jebol.domain.eval;

import org.jebol.domain.eval.definition.*;

import org.jebol.domain.host.HostService;
import org.jebol.domain.read.Construction;
import org.jebol.domain.value.*;

import java.util.*;

import static java.util.Set.of;

public final class RebolNativeWords {

    private final BootDeclarations bootDeclarations = new BootDeclarations();
    private final Map<String, RefinedCallable> behaviours = new LinkedHashMap<>();
    private final Map<String, NativeValue> definitions = new LinkedHashMap<>();
    private final Map<String, String> operatorTwins = new LinkedHashMap<>();
    private final Context runState = Context.root();
    private Context systemInternals = Context.root();
    private final MapValue registeredStructLayouts = MapValue.empty();
    private final MakingAndConverting makingAndConverting =
            new MakingAndConverting(registeredStructLayouts);
    private final Encodings encodings = new Encodings();
    private final VersionNative version = new VersionNative();

    public void forgetStartupState() {
        runState.set("last-error", NoneValue.none());
        runState.set("last-result", NoneValue.none());
    }

    private RebolNativeWords() {
        registerArithmeticFunctions();
        registerTheRemainingNatives();
        registerComparators();
        registerConditionalFunctions();
        registerNonLocalExit();
        registerObjects();
        registerLoops();
        registerReflection();
        registerSetting();
        registerSeries();
        registerStrings();
        registerConversion();
        registerEncodings();
        registerInterpreterState();
        registerPorts();
        registerParse();
        registerScreen();
        registerOutput();
    }

    public void useOperatorTable(String source) {
        operatorTwins.clear();
        List<Value> written = bootDeclarations.theRowsBelowTheHeaderOf(source);
        for (int at = 0; at + 1 < written.size(); at += 2) {
            if (written.get(at) instanceof WordValue operator
                    && written.get(at + 1) instanceof WordValue twin) {
                registerOperator(operator.spelling(), twin.spelling());
            }
        }
    }

    private final GrantedServices grantedServices = new GrantedServices();

    private final Ports ports = new Ports(grantedServices);

    private final LocalFileSeparator localFileSeparator = new LocalFileSeparator();

    public void useFileSeparator(char separator) {
        localFileSeparator.use(separator);
    }

    private String operatingSystemName = "JVM";

    public void useOperatingSystemNamed(String operatingSystem) {
        this.operatingSystemName = operatingSystem;
    }

    private String errorCatalogueSource = "";

    public void useErrorCatalogue(String source) {
        this.errorCatalogueSource = source;
    }

    public void useDatatypeSpecs(String source) {
        bootDeclarations.useDatatypeSpecs(source);
    }

    public void useModeTable(String source) {
        ports.useModeTable(source);
    }

    public void useFunctionDeclarations(String... sources) {
        bootDeclarations.useFunctionDeclarations(String.join("\n", sources));
    }

    public static RebolNativeWords standard(Set<HostService> granted) {
        RebolNativeWords natives = standard();
        natives.grantOnly(granted);
        return natives;
    }

    public void grantOnly(Set<HostService> granted) {
        grantedServices.grantOnly(granted);
    }


    public static RebolNativeWords standard() {
        return new RebolNativeWords();
    }

    private static final List<String> ACTION_NAMES = ActionNames.inDeclarationOrder();

    private ObjectValue systemObject(Context systemContext) {
        Context catalog = Context.root();
        catalog.set("datatypes", BlockValue.block(
                Arrays.stream(Datatype.values())
                        .map(datatype -> (Value) DatatypeValue.of(datatype))
                        .toList()));

        catalog.set("structs", registeredStructLayouts);

        catalog.set("actions", BlockValue.block(ACTION_NAMES.stream()
                .filter(definitions::containsKey)
                .<Value>map(WordValue::of).toList()));

        catalog.set("natives", BlockValue.block(definitions.keySet().stream()
                .filter(spelling -> !ACTION_NAMES.contains(spelling))
                .sorted()
                .<Value>map(WordValue::of).toList()));

        catalog.set("ciphers", BlockValue.block(ports.cryptPort().catalogue()));

        catalog.set("filters", BlockValue.block(
                ResizeNative.THE_FILTERS.stream().<Value>map(WordValue::of).toList()));

        catalog.set("elliptic-curves", BlockValue.block(
                EllipticCurveKey.curveNamesInTheCataloguesOrder().stream()
                        .<Value>map(WordValue::of).toList()));

        catalog.set("handles", BlockValue.block(List.of(
                WordValue.of(CipherNative.RC4_HANDLE_TYPE),
                WordValue.of(CipherNative.DHM_HANDLE_TYPE),
                WordValue.of(CipherNative.RSA_HANDLE_TYPE),
                WordValue.of(CipherNative.ECDH_HANDLE_TYPE),
                WordValue.of("codec"))));

        catalog.set("checksums", BlockValue.block(
                encodings.checksumMethods().stream()
                        .<Value>map(WordValue::of).toList()));
        catalog.set("compressions", BlockValue.block(
                Encodings.COMPRESSIONS.stream()
                        .<Value>map(WordValue::of).toList()));

        catalog.set("file-types", BlockValue.block(List.of(
                StringValue.of(".txt", Datatype.FILE), WordValue.of("text"),
                StringValue.of(".html", Datatype.FILE), WordValue.of("markup"),
                StringValue.of(".htm", Datatype.FILE), WordValue.of("markup"),
                StringValue.of(".qoi", Datatype.FILE), WordValue.of("qoi"))));

        Context options = Context.root();
        for (String field : new String[] {
                "boot", "path", "home", "data", "modules", "flags", "script",
                "args", "do-arg", "import", "debug", "secure", "version",
                "boot-level", "domain-name", "module-paths", "result-types"}) {
            options.set(field, NoneValue.none());
        }
        options.set("flags", BlockValue.block(List.of(LogicValue.yes())));
        options.set("home", StringValue.of(
                System.getProperty("user.home", "") + "/", Datatype.FILE));
        options.set("boot", NoneValue.none());
        options.set("path", StringValue.of(
                System.getProperty("user.dir", "") + "/", Datatype.FILE));
        options.set("data", StringValue.of(
                System.getProperty("user.home", "") + "/.jebol/", Datatype.FILE));

        Context state = runState;
        Context policies = Context.root();
        for (String policy : new String[] {
                "file", "net", "eval", "memory", "secure", "protect", "debug",
                "envr", "call", "browse", "extension"}) {
            policies.set(policy, TupleValue.of(0, 0, 0));
        }
        state.set("policies", new ObjectValue(policies));
        for (String field : new String[] {
                "note", "confirm-policy", "control?", "shift?", "alt?", "quit?"}) {
            state.set(field, NoneValue.none());
        }
        state.set("wait-list", BlockValue.block(List.of()));
        state.set("last-error", NoneValue.none());
        state.set("last-result", NoneValue.none());

        Context errors = Context.root();
        List<Value> catalogued = catalogueEntries();
        for (int at = 0; at + 1 < catalogued.size(); at += 2) {
            if (!(catalogued.get(at) instanceof WordValue category)
                    || category.datatype() != Datatype.SET_WORD
                    || !(catalogued.get(at + 1) instanceof BlockValue body)) {
                continue;
            }
            Context inside = Context.root();
            List<Value> fields = body.remaining();
            for (int pair = 0; pair + 1 < fields.size(); pair += 2) {
                if (fields.get(pair) instanceof WordValue name
                        && name.datatype() == Datatype.SET_WORD) {
                    inside.set(name.spelling(), fields.get(pair + 1));
                }
            }
            errors.set(category.spelling(), new ObjectValue(inside));
        }
        catalog.set("errors", new ObjectValue(errors));

        Context system = Context.root();
        system.set("catalog", new ObjectValue(catalog));
        system.set("options", new ObjectValue(options));
        system.set("state", new ObjectValue(state));
        system.set("version", version.numbered());
        system.set("platform", WordValue.of(operatingSystemName));
        system.set("product", WordValue.of("core"));
        system.set("license", NoneValue.none());

        Context modules = Context.root();
        modules.set("help", NoneValue.none());
        system.set("modules", new ObjectValue(modules));

        Context codecs = Context.root();
        for (int at = 0; at < Codecs.REGISTERED.size(); at++) {
            String codec = Codecs.REGISTERED.get(at);
            codecs.set(codec, HandleValue.function(
                    "codec", CODEC_HANDLE_IDENTITY + at, WordValue.of(codec)));
        }
        system.set("codecs", new ObjectValue(codecs));

        Context internals = Context.childOf(systemContext);
        systemContext.set("native", NoneValue.none());
        systemContext.set("action", NoneValue.none());
        Context contexts = Context.root();
        contexts.set("lib", new ObjectValue(systemContext));
        contexts.set("sys", new ObjectValue(internals));
        contexts.set("root", NoneValue.none());
        system.set("contexts", new ObjectValue(contexts));
        this.systemInternals = internals;
        return new ObjectValue(system);
    }

    public Map<String, RefinedCallable> behaviours() {
        return Map.copyOf(behaviours);
    }

    public Context systemInternals() {
        return systemInternals;
    }

    public Context asContext() {
        Context context = Context.root();
        context.set("true", LogicValue.yes());
        context.set("false", LogicValue.no());
        context.set("none", NoneValue.none());
        context.set("on", LogicValue.yes());
        context.set("off", LogicValue.no());
        context.set("yes", LogicValue.yes());
        context.set("no", LogicValue.no());
        context.set("pi", DecimalValue.of(Math.PI));

        for (Datatype datatype : Datatype.values()) {
            context.set(datatype.literalSpelling(), DatatypeValue.of(datatype));
        }
        for (Typeset typeset : Typeset.values()) {
            context.set(typeset.literalSpelling(), TypesetValue.of(typeset));
        }
        context.set("system", systemObject(context));

        Map<String, NativeValue> carryingTheirSpecs = new LinkedHashMap<>();
        definitions.forEach((name, built) -> carryingTheirSpecs.put(name, carryingItsSpec(built)));
        carryingTheirSpecs.forEach(context::set);
        operatorTwins.forEach((operator, twin) ->
                context.set(operator, new OperatorValue(operator, carryingTheirSpecs.get(twin))));
        return context;
    }

    private NativeValue carryingItsSpec(NativeValue built) {
        BlockValue spec = bootDeclarations.specOf(built);
        return built.derivedWith(spec, new DeclaredArguments(spec).inPlaceOf(built.parameters()));
    }

    private void registerOperator(String spelling, String prefixTwin) {
        if (!definitions.containsKey(prefixTwin)) {
            throw new IllegalStateException(
                    "operator " + spelling + " has no prefix twin called " + prefixTwin);
        }
        operatorTwins.put(spelling, prefixTwin);
    }

    private void register(NativeDefinition function) {
        String name = function.name();
        definitions.put(name, new NativeValue(name, function.parameters(), function.refinements(), of()));
        behaviours.put(name, function.behaviour());
    }


    private void registerArithmeticFunctions() {
        register(new AddAction());
        register(new SubtractAction());
        register(new MultiplyAction());
        register(new DivideAction());
        register(new RemainderAction());
        register(new SquareRootNative());
        register(new SineNative());
        register(new CosineNative());
        register(new TangentNative());
        register(new ArcsineNative());
        register(new ArccosineNative());
        register(new ArctangentNative());
        register(new NaturalLogarithmNative());
        register(new CommonLogarithmNative());
        register(new BinaryLogarithmNative());
        register(new ExponentialNative());
        register(new AbsoluteAction());
        register(new ToDegreesNative());
        register(new ToRadiansNative());
        register(new IntegerDivideNative());
        register(new AbsAction());
        register(new SinNative());
        register(new CosNative());
        register(new TanNative());
        register(new AsinNative());
        register(new AcosNative());
        register(new AtanNative());
        register(new SqrtNative());
        register(new GreatestCommonDivisorNative());
        register(new LowestCommonMultipleNative());
        register(new PrimeNative());
        register(new FactorialNative());
        register(new ArctangentOfAPointNative());
        register(new ArctangentOfTwoSidesNative());
        register(new FractionNative());
        register(new IsComplementedNative());
        register(new ComplementAction());
        register(new ClampNative());
        register(new DistanceNative());
        register(new PowerAction());
        register(new NegateAction());
        register(new MaximumNative());
        register(new MinimumNative());
        register(new BitwiseAndAction());
        register(new BitwiseOrAction());
        register(new BitwiseXorAction());
        register(new LerpNative());
        register(new ModNative());
        register(new ModuloNative());
        register(new ShiftLeftNative());
        register(new ShiftRightNative());
    }

    private void registerTheRemainingNatives() {
        register(new NowNative(grantedServices));
        register(new AlsoNative());
        register(new CommentNative());
        register(new ToValueNative());
        register(new TraceNative());
        register(new LoadExtensionNative());
        register(new DoCallbackNative());
        register(new DoCommandsNative());
        register(new AccessOsNative(grantedServices));
        register(new RandomAction(encodings));
    }

    private void registerComparators() {
        register(new EqualNative());
        register(new NotEqualNative());
        register(new EquivNative());
        register(new NotEquivNative());
        register(new StrictEqualNative());
        register(new StrictNotEqualNative());
        register(new GreaterOrEqualNative());
        register(new LesserNative());
        register(new GreaterNative());
        register(new LesserOrEqualNative());
        register(new SameNative());
    }

    private void registerConditionalFunctions() {
        register(new IfNative());
        register(new EitherNative());
        register(new NotNative());
        register(new DoNative());
        register(new AnyNative());
        register(new AllNative());
        register(new UnlessNative());
        register(new SwitchNative());
        register(new CaseNative());
        register(new AttemptNative());
        register(new TryNative());
    }


    private void registerNonLocalExit() {
        register(new ReturnNative());
        register(new ExitNative());
        register(new ThrowNative());
        register(new CatchNative());
    }

    public Construction construction() {
        return makingAndConverting;
    }

    public Maker makerFor(Evaluator evaluator, Context where) {
        return new InterpreterMaker(evaluator, where, makingAndConverting);
    }

    private void registerObjects() {
        register(new MakeAction());
        register(new ConstructNative());
        register(new ContextOfWordNative());
        register(new ResolveNative());
        register(new ContextNative());
        register(new InNative());
        register(new ApplyNative());
        register(new AssertNative());
        register(new HashNative());
        register(new CollectWordsNative());
        register(new NewLineNative());
        register(new IsNewLineNative());
        register(new ObjectNative());
        register(new WithNative());
        register(new IsSelflessNative());
        register(new IsProtectedNative());
        register(new UnbindNative());
        register(new BindNative());
    }

    private void registerLoops() {
        register(new LoopNative());
        register(new RepeatNative());
        register(new WhileNative());
        register(new UntilNative());
        register(new ForeverNative());
        register(new ForNative());
        register(new ForEachNative());
        register(new RemoveEachNative());
        register(new MapEachNative());
        register(new ForSkipNative());
        register(new ForAllNative());
        register(new ContinueNative());
        register(new BreakNative());
    }



    private void registerReflection() {
        register(new ShiftNative());
        register(new IsOddAction());
        register(new IsEvenAction());
        register(new TypeOfNative());
        for (Datatype datatype : Datatype.values()) {
            register(new DatatypePredicateAction(datatype));
        }
        register(new IsAnyTypeNative());
        register(new IsCopyableNative());
        register(new IsImmediateNative());
        register(new IsInternalNative());
        register(new IsTrueNative());
        register(new DidNative());
        register(new IsNumberNative());
        register(new IsAsciiNative());
        register(new IsLatin1Native());
        register(new FormOidNative());
        register(new BinaryNative());
        register(new RegisterNative());
        register(new XtestNative());
        register(new PremultiplyNative());
        register(new BlurNative());
        register(new ResizeNative());
        register(new ImageDiffNative());
        register(new ImageNative());
        register(new GenerateNative());
        register(new EcdhNative());
        register(new EcdsaNative());
        register(new DhInitNative());
        register(new DhNative());
        register(new RsaInitNative());
        register(new RsaNative());
        register(new Rc4Native());
        register(new UtfNative());
        register(new InvalidUtfNative());
        register(new IsNegativeNative());
        register(new IsPositiveNative());
        register(new IsZeroNative());
        register(new IsValueNative());
        register(new UnsetNative());
        register(new ProtectNative());
        register(new UnprotectNative());
        register(new DelectNative());
    }

    private void registerSetting() {
        register(new SetNative());
        register(new TakeAction(ports.cryptPort()));
        register(new AjoinNative());
        register(new PokeAction());
        register(new DifferenceNative());
        register(new ReflectAction(bootDeclarations));
        register(new PutAction());
        register(new SelectAction());
        register(new GetNative());
    }


    private void registerSeries() {
        register(new LengthAction(grantedServices));
        register(new FirstNative());
        register(new SecondNative());
        register(new ThirdNative());
        register(new FourthNative());
        register(new FifthNative());
        register(new SixthNative());
        register(new SeventhNative());
        register(new EighthNative());
        register(new NinthNative());
        register(new TenthNative());
        register(new PickAction());
        register(new PickzNative());
        register(new LastNative());
        register(new FirstPlusNative());
        register(new SwapAction());
        register(new IncrementNative());
        register(new DecrementNative());
        register(new TruncateNative());
        register(new AtzAction(grantedServices));
        register(new IndexzAction(grantedServices));
        register(new IsPastAction());
        register(new IsHeadAction());
        register(new IsTailAction(grantedServices));
        register(new NextAction(grantedServices));
        register(new HeadAction(grantedServices));
        register(new TailAction(grantedServices));
        register(new IndexAction(grantedServices));

        register(new AppendAction(grantedServices));
        register(new InsertAction(grantedServices));
        register(new ClearAction(grantedServices));
        register(new RemoveAction());
        register(new ChangeAction());
        register(new ReverseAction());
        register(new CopyAction());
        register(new FindAction());
        register(new SortAction());
        register(new BackAction(grantedServices));
        register(new SkipAction(grantedServices));
        register(new AtAction(grantedServices));

        register(new IntersectNative());
        register(new UnionNative());
        register(new ExcludeNative());
        register(new UniqueNative());
        register(new ReduceNative());
        register(new ComposeNative());
        register(new TranscodeNative());
        register(new RoundAction());
    }


    static List<Value> numbersContributedTo(VectorKind kind, Value value) {
        if (value instanceof VectorValue source) {
            return source.remaining();
        }
        if (value instanceof BlockValue block) {
            return block.remaining();
        }
        if (value instanceof BinaryValue bytes) {
            return numbersSpeltByWithTheOddBytesDropped(
                    kind, bytes, bytes.lengthFromHere());
        }
        return List.of(value);
    }

    private static List<Value> numbersSpeltByWithTheOddBytesDropped(
            VectorKind kind, BinaryValue bytes, int taking) {

        int wholeNumbers = Math.max(0, taking) / kind.bytes();
        if (wholeNumbers == 0) {
            throw Raised.of(EvaluationFailure.INVALID_DATA, bytes);
        }
        byte[] octets = bytes.octetsFromHere();
        List<Value> numbers = new ArrayList<>();
        for (int number = 0; number < wholeNumbers; number++) {
            numbers.add(kind.read(kind.fromOctets(octets, number * kind.bytes())));
        }
        return numbers;
    }


    static List<Value> numbersOfferedTo(VectorKind kind, Value value, int limit) {
        if (!(value instanceof RebolSeries source)) {
            return numbersContributedTo(kind, value);
        }
        RebolSeries run = source.reachingBackIfNegative(limit);
        long wanted = limit >= 0 ? limit : source.index() - run.index();
        if (run instanceof BinaryValue bytes) {
            return numbersSpeltByWithTheOddBytesDropped(kind, bytes,
                    (int) Math.min(wanted, bytes.lengthFromHere()));
        }
        List<Value> offered = numbersContributedTo(kind, run);
        return offered.subList(0, (int) Math.min(wanted, offered.size()));
    }


    static Value bitsetHoldsForAPath(BitsetValue members, Value selector) {
        return new BitsetActions(members).heldForAPath(selector);
    }

    private List<Value> catalogueEntries() {
        return bootDeclarations.theRowsBelowTheHeaderOf(errorCatalogueSource);
    }



    private static final int CODEC_HANDLE_IDENTITY = 1000;


    private void registerEncodings() {
        register(new EnhexNative(encodings));
        register(new DehexNative(encodings));
        register(new EnbaseNative(encodings));
        register(new DebaseNative(encodings));
        register(new ChecksumNative(encodings));
        register(new CompressNative(encodings));
        register(new DecompressNative(encodings));
        register(new EncloakNative(encodings));
        register(new DecloakNative(encodings));
        register(new IconvNative(encodings));
        register(new FilterNative(encodings));
        register(new UnfilterNative(encodings));
        register(new SwapEndianNative(encodings));
    }


    private void registerInterpreterState() {
        register(version);
        register(new PokezNative());
        register(new ToRealFileNative(grantedServices));
        register(new RecycleNative());
        register(new StatsNative());
        register(new EchoNative(grantedServices));
        register(new IsTerminalNative());
        register(new WaitNative());
        register(new ReadKeyNative(grantedServices));
        register(new HaltNative());
        register(new DoCodecNative());
        register(new ReleaseNative());
        register(new MapEventNative());
        register(new WakeUpNative());
        register(new MapGobOffsetNative());
        register(new AsColorNative());
        register(new GrayscaleNative());
        register(new LuminosityNative());
        register(new HsvToRgbNative());
        register(new RgbToHsvNative());
        register(new ColorDistanceNative());
        register(new TintNative());
        register(new LimitUsageNative());
        register(new DsNative());
        register(new DumpNative());
        register(new CheckNative());
        register(new EvokeNative());
        register(new StackNative());
    }


    private void registerStrings() {
        register(new FindScriptNative());
        register(new SplitLinesNative());
        register(new IsWildcardNative());
        register(new UppercaseNative());
        register(new LowercaseNative());
        register(new TrimAction());
    }

    private void registerConversion() {
        register(new ToAction());
        register(new AsPairNative());
        register(new ToHexNative());
        register(new EntabNative());
        register(new DetabNative());
        register(new DelineNative());
        register(new EnlineNative());
        register(new AsNative());
    }




    static void refuseTheObjectsOwnSelfBeforeAnyFieldIsAdded(
            ObjectValue object, List<Value> pairs) {
        for (int at = 0; at + 1 < pairs.size(); at += 2) {
            refuseTheSelfTheObjectAlreadyHas(object, pairs.get(at));
        }
    }

    static void refuseTheSelfTheObjectAlreadyHas(ObjectValue object, Value field) {
        if (object.context().holds("self")) {
            field.refuseToBeWrittenWhenItNamesSelf();
        }
    }


    private void registerPorts() {
        register(new ReadAction(grantedServices, ports));
        register(new WriteAction(grantedServices, ports));
        register(new ToLocalFileNative(grantedServices, localFileSeparator));
        register(new ToRebolFileNative());
        register(new CallNative(grantedServices));
        register(new GetEnvNative(grantedServices));
        register(new ListEnvNative(grantedServices));
        register(new SetEnvNative(grantedServices));
        register(new WhatDirNative(grantedServices));
        register(new ChangeDirNative(grantedServices));
        register(new CreateAction(grantedServices, ports));
        register(new DeleteAction(grantedServices, ports));
        register(new RenameAction(grantedServices, ports));
        register(new IsDirectoryNative(grantedServices));
        register(new SetSchemeNative());
        register(new OpenAction(grantedServices, ports));
        register(new UpdateAction(grantedServices, ports));
        register(new FlushAction());
        register(new IsOpenAction(grantedServices, ports));
        register(new CloseAction(grantedServices, ports));
        register(new ModifyAction(grantedServices, ports));
        register(new BrowseNative(grantedServices));
        register(new RequestFileNative(grantedServices));
        register(new RequestDirNative(grantedServices));
        register(new RequestColorNative(grantedServices));
        register(new RequestPasswordNative(grantedServices));
        register(new QueryAction(grantedServices, ports));
    }


    private void registerParse() {
        register(new ParseNative());
    }

    private void registerScreen() {
        register(new InitTopWindowNative(grantedServices));
        register(new GuiMetricNative(grantedServices));
        register(new ShowNative(grantedServices));
    }

    private void registerOutput() {
        register(new MoldNative());
        register(new FormNative());
        register(new QuitNative());
        register(new PrintNative());
        register(new PrinNative());
    }
}
