package org.jebol.domain.eval;

import org.jebol.domain.eval.definition.*;

import org.jebol.domain.host.HostService;
import org.jebol.domain.read.Construction;
import org.jebol.domain.value.*;

import java.util.*;


public final class RebolNativeWords {

    private String errorCatalogueSource = "";
    private String operatingSystemName = "JVM";
    private Context systemInternals = Context.root();
    private final BootDeclarations bootDeclarations = new BootDeclarations();
    private final Map<String, DefaultNative> definitions = new LinkedHashMap<>();
    private final Map<String, String> operatorTwins = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Context runState = Context.root();
    private final MapValue registeredStructLayouts = MapValue.empty();
    private final MakingAndConverting makingAndConverting = new MakingAndConverting(registeredStructLayouts);
    private final Encodings encodings = new Encodings();
    private final VersionNative version = new VersionNative();
    private final GrantedServices grantedServices = new GrantedServices();
    private final Ports ports = new Ports(grantedServices);
    private final LocalFileSeparator localFileSeparator = new LocalFileSeparator();

    private static final List<String> ACTION_NAMES = ActionNames.inDeclarationOrder();
    private static final int CODEC_HANDLE_IDENTITY = 1000;

    private RebolNativeWords() {
        registerBinaryMathAndLogicActions();
        registerUnaryActions();
        registerSeriesNavigationActions();
        registerSeriesExtractionActions();
        registerSeriesSearchActions();
        registerMakingCopyingAndModifyingActions();
        registerPortActions();
        registerDatatypePredicates();
        registerControlNatives();
        registerLoopNatives();
        registerDataNatives();
        registerSetNatives();
        registerStringNatives();
        registerDialectNatives();
        registerMathNatives();
        registerInputAndOutputNatives();
        registerSystemNatives();
        registerCryptographyNatives();
        registerImageNatives();
        registerSeriesNatives();
        registerExtensionNatives();
        registerScreenCommands();
    }

    public static RebolNativeWords standard() {
        return new RebolNativeWords();
    }

    public static RebolNativeWords standard(Set<HostService> granted) {
        RebolNativeWords natives = standard();
        natives.grantOnly(granted);
        return natives;
    }

    public void forgetStartupState() {
        runState.register("last-error", NoneValue.none());
        runState.register("last-result", NoneValue.none());
    }

    public void useOperatorTable(String source) {
        operatorTwins.clear();
        List<Value> written = bootDeclarations.theRowsBelowTheHeaderOf(source);
        for (int at = 0; at + 1 < written.size(); at += 2) {
            if (written.get(at) instanceof WordValue operator && written.get(at + 1) instanceof WordValue twin) {
                registerOperator(operator.spelling(), twin.spelling());
            }
        }
    }

    public void useFileSeparator(char separator) {
        localFileSeparator.use(separator);
    }

    public void useOperatingSystemNamed(String operatingSystem) {
        this.operatingSystemName = operatingSystem;
    }

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

    public void grantOnly(Set<HostService> granted) {
        grantedServices.grantOnly(granted);
    }

    public Context systemInternals() {
        return systemInternals;
    }

    public Context asContext() {
        Context context = Context.root();
        context.register("true", LogicValue.yes());
        context.register("false", LogicValue.no());
        context.register("none", NoneValue.none());
        context.register("on", LogicValue.yes());
        context.register("off", LogicValue.no());
        context.register("yes", LogicValue.yes());
        context.register("no", LogicValue.no());
        context.register("pi", DecimalValue.of(Math.PI));

        for (Datatype datatype : Datatype.values()) {
            context.register(datatype.literalSpelling(), DatatypeValue.of(datatype));
        }
        for (Typeset typeset : Typeset.values()) {
            context.register(typeset.literalSpelling(), TypesetValue.of(typeset));
        }
        context.register("system", systemObject(context));

        definitions.values().forEach(this::declareItsSpec);
        definitions.forEach(context::register);
        operatorTwins.forEach((operator, twin) -> context.register(operator, new OperatorValue(operator, definitions.get(twin))));
        aliases.forEach((alias, originalName) -> context.register(alias, definitions.get(originalName)));
        return context;
    }

    public Construction construction() {
        return makingAndConverting;
    }

    public Maker makerFor(Evaluator evaluator, Context where) {
        return new InterpreterMaker(evaluator, where, makingAndConverting);
    }

    private void registerBinaryMathAndLogicActions() {
        register(new AddAction());
        register(new SubtractAction());
        register(new MultiplyAction());
        register(new DivideAction());
        register(new RemainderAction());
        register(new PowerAction());
        register(new BitwiseAndAction());
        register(new BitwiseOrAction());
        register(new BitwiseXorAction());
    }

    private void registerUnaryActions() {
        register(new NegateAction());
        register(new ComplementAction());
        register(new AbsoluteAction());
        alias("abs", "absolute");
        register(new RoundAction());
        register(new RandomAction(encodings));
        register(new IsOddAction());
        register(new IsEvenAction());
    }

    private void registerSeriesNavigationActions() {
        register(new HeadAction(grantedServices));
        register(new TailAction(grantedServices));
        register(new IsHeadAction());
        register(new IsTailAction(grantedServices));
        register(new IsPastAction());
        register(new NextAction(grantedServices));
        register(new BackAction(grantedServices));
        register(new SkipAction(grantedServices));
        register(new AtAction(grantedServices));
        register(new AtzAction(grantedServices));
        register(new IndexAction(grantedServices));
        register(new IndexzAction(grantedServices));
        register(new LengthAction(grantedServices));
    }

    private void registerSeriesExtractionActions() {
        register(new PickAction());
    }

    private void registerSeriesSearchActions() {
        register(new FindAction());
        register(new SelectAction());
        register(new ReflectAction(bootDeclarations));
    }

    private void registerMakingCopyingAndModifyingActions() {
        register(new MakeAction());
        register(new ToAction());
        register(new CopyAction());
        register(new TakeAction(ports.cryptPort()));
        register(new PutAction());
        register(new InsertAction(grantedServices));
        register(new AppendAction(grantedServices));
        register(new RemoveAction());
        register(new ChangeAction());
        register(new PokeAction());
        register(new ClearAction(grantedServices));
        register(new TrimAction());
        register(new SwapAction());
        register(new ReverseAction());
        register(new SortAction());
    }

    private void registerPortActions() {
        register(new CreateAction(grantedServices, ports));
        register(new DeleteAction(grantedServices, ports));
        register(new OpenAction(grantedServices, ports));
        register(new CloseAction(grantedServices, ports));
        register(new ReadAction(grantedServices, ports));
        register(new WriteAction(grantedServices, ports));
        register(new IsOpenAction(grantedServices, ports));
        register(new QueryAction(grantedServices, ports));
        register(new ModifyAction(grantedServices, ports));
        register(new UpdateAction(grantedServices, ports));
        register(new RenameAction(grantedServices, ports));
        register(new FlushAction());
    }

    private void registerDatatypePredicates() {
        for (Datatype datatype : Datatype.values()) {
            register(new DatatypePredicateAction(datatype));
        }
        register(new IsAnyTypeNative());
        register(new IsCopyableNative());
        register(new IsImmediateNative());
        register(new IsInternalNative());
    }

    private void registerControlNatives() {
        register(new IfNative());
        register(new EitherNative());
        register(new UnlessNative());
        register(new SwitchNative());
        register(new CaseNative());
        register(new AnyNative());
        register(new AllNative());
        register(new DoNative());
        register(new ReduceNative());
        register(new ComposeNative());
        register(new ApplyNative());
        register(new AlsoNative());
        register(new CommentNative());
        register(new AttemptNative());
        register(new TryNative());
        register(new CatchNative());
        register(new ThrowNative());
        register(new ReturnNative());
        register(new ExitNative());
        register(new BreakNative());
        register(new ContinueNative());
        register(new ProtectNative());
        register(new UnprotectNative());
        register(new IsProtectedNative());
        register(new DidNative());
        alias("true?", "did");
        register(new ObjectNative());
        alias("context", "object");
        register(new TraceNative());
    }

    private void registerLoopNatives() {
        register(new LoopNative());
        register(new RepeatNative());
        register(new WhileNative());
        register(new UntilNative());
        register(new ForeverNative());
        register(new ForNative());
        register(new ForEachNative());
        register(new ForAllNative());
        register(new ForSkipNative());
        register(new MapEachNative());
        register(new RemoveEachNative());
    }

    private void registerDataNatives() {
        register(new SetNative());
        register(new GetNative());
        register(new UnsetNative());
        register(new IsValueNative());
        register(new ToValueNative());
        register(new TypeOfNative());
        register(new AsNative());
        register(new AsPairNative());
        register(new AsColorNative());
        register(new BindNative());
        register(new UnbindNative());
        register(new InNative());
        register(new ContextOfWordNative());
        register(new ResolveNative());
        register(new CollectWordsNative());
        register(new WithNative());
        register(new AssertNative());
        register(new NotNative());
        register(new HashNative());
        register(new IsAsciiNative());
        register(new IsLatin1Native());
        register(new DumpNative());
        register(new MapEventNative());
        register(new MapGobOffsetNative());
        register(new TruncateNative());
    }

    private void registerSetNatives() {
        register(new DifferenceNative());
        register(new ExcludeNative());
        register(new IntersectNative());
        register(new UnionNative());
        register(new UniqueNative());
    }

    private void registerStringNatives() {
        register(new AjoinNative());
        register(new ConstructNative());
        register(new FindScriptNative());
        register(new SplitLinesNative());
        register(new UppercaseNative());
        register(new LowercaseNative());
        register(new ToHexNative());
        register(new EntabNative());
        register(new DetabNative());
        register(new DelineNative());
        register(new EnlineNative());
        register(new UtfNative());
        register(new InvalidUtfNative());
        register(new EnhexNative(encodings));
        register(new DehexNative(encodings));
        register(new EnbaseNative(encodings));
        register(new DebaseNative(encodings));
        register(new EncloakNative(encodings));
        register(new DecloakNative(encodings));
        register(new ChecksumNative(encodings));
        register(new CompressNative(encodings));
        register(new DecompressNative(encodings));
        register(new IconvNative(encodings));
        register(new FormOidNative());
    }

    private void registerDialectNatives() {
        register(new ParseNative());
        register(new TranscodeNative());
        register(new DelectNative());
        register(new BinaryNative());
    }

    private void registerMathNatives() {
        register(new SineNative());
        register(new CosineNative());
        register(new TangentNative());
        register(new ArcsineNative());
        register(new ArccosineNative());
        register(new ArctangentNative());
        register(new ArctangentOfAPointNative());
        register(new ArctangentOfTwoSidesNative());
        register(new SinNative());
        register(new CosNative());
        register(new TanNative());
        register(new AsinNative());
        register(new AcosNative());
        register(new AtanNative());
        register(new ToDegreesNative());
        register(new ToRadiansNative());
        register(new SquareRootNative());
        register(new SqrtNative());
        register(new ExponentialNative());
        register(new NaturalLogarithmNative());
        register(new CommonLogarithmNative());
        register(new BinaryLogarithmNative());
        register(new IntegerDivideNative());
        register(new ModNative());
        register(new ModuloNative());
        register(new GreatestCommonDivisorNative());
        register(new LowestCommonMultipleNative());
        register(new PrimeNative());
        register(new FactorialNative());
        register(new FractionNative());
        register(new ClampNative());
        register(new LerpNative());
        register(new DistanceNative());
        register(new ShiftNative());
        register(new ShiftLeftNative());
        register(new ShiftRightNative());
        register(new IsComplementedNative());
        register(new IsNumberNative());
        register(new IsNegativeNative());
        register(new IsPositiveNative());
        register(new IsZeroNative());
        register(new MaximumNative());
        register(new MinimumNative());
        register(new EqualNative());
        register(new NotEqualNative());
        register(new EquivNative());
        register(new NotEquivNative());
        register(new StrictEqualNative());
        register(new StrictNotEqualNative());
        register(new SameNative());
        register(new GreaterNative());
        register(new GreaterOrEqualNative());
        register(new LesserNative());
        register(new LesserOrEqualNative());
    }

    private void registerInputAndOutputNatives() {
        register(new PrintNative());
        register(new PrinNative());
        register(new MoldNative());
        register(new FormNative());
        register(new NewLineNative());
        register(new IsNewLineNative());
        register(new EchoNative(grantedServices));
        register(new IsTerminalNative());
        register(new ReadKeyNative(grantedServices));
        register(new WaitNative());
        register(new WakeUpNative());
        register(new NowNative(grantedServices));
        register(new ToLocalFileNative(grantedServices, localFileSeparator));
        register(new ToRebolFileNative());
        register(new ToRealFileNative(grantedServices));
        register(new WhatDirNative(grantedServices));
        register(new ChangeDirNative(grantedServices));
        register(new IsDirectoryNative(grantedServices));
        register(new IsWildcardNative());
        register(new GetEnvNative(grantedServices));
        register(new SetEnvNative(grantedServices));
        register(new ListEnvNative(grantedServices));
        register(new CallNative(grantedServices));
        register(new BrowseNative(grantedServices));
        register(new AccessOsNative(grantedServices));
        register(new RequestFileNative(grantedServices));
        register(new RequestDirNative(grantedServices));
        register(new RequestPasswordNative(grantedServices));
        register(new RequestColorNative(grantedServices));
        register(new SetSchemeNative());
    }

    private void registerSystemNatives() {
        register(version);
        register(new QuitNative());
        register(new HaltNative());
        register(new RecycleNative());
        register(new ReleaseNative());
        register(new StatsNative());
        register(new StackNative());
        register(new CheckNative());
        register(new DsNative());
        register(new EvokeNative());
        register(new LimitUsageNative());
        register(new IsSelflessNative());
        register(new DoCodecNative());
        register(new RegisterNative());
    }

    private void registerCryptographyNatives() {
        register(new Rc4Native());
        register(new RsaInitNative());
        register(new RsaNative());
        register(new DhInitNative());
        register(new DhNative());
        register(new EcdhNative());
        register(new EcdsaNative());
        register(new GenerateNative());
    }

    private void registerImageNatives() {
        register(new ImageNative());
        register(new ImageDiffNative());
        register(new ResizeNative());
        register(new BlurNative());
        register(new PremultiplyNative());
        register(new TintNative());
        register(new GrayscaleNative());
        register(new LuminosityNative());
        register(new ColorDistanceNative());
        register(new HsvToRgbNative());
        register(new RgbToHsvNative());
        register(new FilterNative(encodings));
        register(new UnfilterNative(encodings));
    }

    private void registerSeriesNatives() {
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
        register(new LastNative());
        register(new FirstPlusNative());
        register(new IncrementNative());
        register(new DecrementNative());
        register(new PickzNative());
        register(new PokezNative());
        register(new SwapEndianNative(encodings));
    }

    private void registerExtensionNatives() {
        register(new LoadExtensionNative());
        register(new DoCommandsNative());
        register(new DoCallbackNative());
        register(new XtestNative());
    }

    private void registerScreenCommands() {
        register(new InitTopWindowNative(grantedServices));
        register(new ShowNative(grantedServices));
        register(new GuiMetricNative(grantedServices));
    }

    private ObjectValue systemObject(Context systemContext) {
        Context system = Context.root();
        system.register("catalog", catalog(definitions));
        system.register("options", options());
        system.register("state", state());
        system.register("version", version.numbered());
        system.register("platform", WordValue.of(operatingSystemName));
        system.register("product", WordValue.of("core"));
        system.register("license", NoneValue.none());
        system.register("modules", modules());
        system.register("codecs", codecs());

        Context internals = Context.childOf(systemContext);
        systemContext.register("native", NoneValue.none());
        systemContext.register("action", NoneValue.none());
        Context contexts = Context.root();
        contexts.register("lib", new ObjectValue(systemContext));
        contexts.register("sys", new ObjectValue(internals));
        contexts.register("root", NoneValue.none());
        system.register("contexts", new ObjectValue(contexts));
        this.systemInternals = internals;
        return new ObjectValue(system);
    }

    private Value codecs() {
        Context codecs = Context.root();
        for (int at = 0; at < Codecs.REGISTERED.size(); at++) {
            String codec = Codecs.REGISTERED.get(at);
            codecs.register(codec, HandleValue.function("codec", CODEC_HANDLE_IDENTITY + at, WordValue.of(codec)));
        }
        return valueFor(codecs);
    }

    private Value modules() {
        Context modules = Context.root();
        modules.register("help", NoneValue.none());
        return valueFor(modules);
    }

    private Value state() {
        Context state = runState;
        Context policies = Context.root();
        for (String policy : new String[]{"file", "net", "eval", "memory", "secure", "protect", "debug", "envr", "call", "browse", "extension"}) {
            policies.register(policy, TupleValue.of(0, 0, 0));
        }
        state.register("policies", new ObjectValue(policies));
        for (String field : new String[]{"note", "confirm-policy", "control?", "shift?", "alt?", "quit?"}) {
            state.register(field, NoneValue.none());
        }
        state.register("wait-list", BlockValue.block(List.of()));
        state.register("last-error", NoneValue.none());
        state.register("last-result", NoneValue.none());
        return valueFor(state);
    }

    private Value options() {
        Context options = Context.root();
        for (String field : new String[]{"boot", "path", "home", "data", "modules", "flags", "script", "args", "do-arg", "import", "debug", "secure", "version", "boot-level", "domain-name", "module-paths", "result-types"}) {
            options.register(field, NoneValue.none());
        }
        options.register("flags", BlockValue.block(List.of(LogicValue.yes())));
        options.register("home", StringValue.of(System.getProperty("user.home", "") + "/", Datatype.FILE));
        options.register("boot", NoneValue.none());
        options.register("path", StringValue.of(System.getProperty("user.dir", "") + "/", Datatype.FILE));
        options.register("data", StringValue.of(System.getProperty("user.home", "") + "/.jebol/", Datatype.FILE));
        return valueFor(options);
    }

    private Value errors() {
        Context errors = Context.root();
        List<Value> catalogued = catalogueEntries();
        for (int at = 0; at + 1 < catalogued.size(); at += 2) {
            if (!(catalogued.get(at) instanceof WordValue category) || category.datatype() != Datatype.SET_WORD || !(catalogued.get(at + 1) instanceof BlockValue body)) {
                continue;
            }
            Context inside = Context.root();
            List<Value> fields = body.remaining();
            for (int pair = 0; pair + 1 < fields.size(); pair += 2) {
                if (fields.get(pair) instanceof WordValue name && name.datatype() == Datatype.SET_WORD) {
                    inside.register(name.spelling(), fields.get(pair + 1));
                }
            }
            errors.register(category.spelling(), new ObjectValue(inside));
        }
        return valueFor(errors);
    }

    private Value catalog(Map<String, DefaultNative> definitions) {
        Context catalog = Context.root();
        catalog.register("datatypes", BlockValue.block(Arrays.stream(Datatype.values()).map(datatype -> (Value) DatatypeValue.of(datatype)).toList()));
        catalog.register("structs", registeredStructLayouts);
        catalog.register("actions", actions(definitions));
        catalog.register("natives", definitions(definitions));
        catalog.register("ciphers", BlockValue.block(ports.cryptPort().catalogue()));
        catalog.register("filters", BlockValue.block(ResizeNative.THE_FILTERS.stream().<Value>map(WordValue::of).toList()));
        catalog.register("elliptic-curves", BlockValue.block(EllipticCurveKey.curveNamesInTheCataloguesOrder().stream().<Value>map(WordValue::of).toList()));
        catalog.register("handles", BlockValue.block(List.of(WordValue.of(CipherNative.RC4_HANDLE_TYPE), WordValue.of(CipherNative.DHM_HANDLE_TYPE), WordValue.of(CipherNative.RSA_HANDLE_TYPE), WordValue.of(CipherNative.ECDH_HANDLE_TYPE), WordValue.of("codec"))));
        catalog.register("checksums", BlockValue.block(encodings.checksumMethods().stream().<Value>map(WordValue::of).toList()));
        catalog.register("compressions", BlockValue.block(Encodings.COMPRESSIONS.stream().<Value>map(WordValue::of).toList()));
        catalog.register("file-types", BlockValue.block(List.of(StringValue.of(".txt", Datatype.FILE), WordValue.of("text"), StringValue.of(".html", Datatype.FILE), WordValue.of("markup"), StringValue.of(".htm", Datatype.FILE), WordValue.of("markup"), StringValue.of(".qoi", Datatype.FILE), WordValue.of("qoi"))));
        catalog.register("errors", errors());
        return valueFor(catalog);
    }

    private BlockValue definitions(Map<String, DefaultNative> definitions) {
        return BlockValue.block(definitions.keySet().stream().filter(spelling -> !ACTION_NAMES.contains(spelling)).sorted().<Value>map(WordValue::of).toList());
    }

    private BlockValue actions(Map<String, DefaultNative> definitions) {
        return BlockValue.block(ACTION_NAMES.stream().filter(definitions::containsKey).<Value>map(WordValue::of).toList());
    }

    private Value valueFor(Context context) {
        return new ObjectValue(context);
    }

    private void declareItsSpec(DefaultNative built) {
        BlockValue spec = bootDeclarations.specOf(built);
        built.declaredBy(spec, new DeclaredArguments(spec).inPlaceOf(built.parametersAsWritten()));
    }

    private void registerOperator(String spelling, String prefixTwin) {
        if (!definitions.containsKey(prefixTwin)) {
            throw new IllegalStateException("operator " + spelling + " has no prefix twin called " + prefixTwin);
        }
        operatorTwins.put(spelling, prefixTwin);
    }

    private void alias(String alias, String originalName) {
        if (!definitions.containsKey(originalName)) {
            throw new IllegalStateException(alias + " is an alias of " + originalName + ", which is not registered");
        }
        aliases.put(alias, originalName);
    }

    private void register(DefaultNative function) {
        definitions.put(function.nativeName(), function);
    }

    private List<Value> catalogueEntries() {
        return bootDeclarations.theRowsBelowTheHeaderOf(errorCatalogueSource);
    }

}
