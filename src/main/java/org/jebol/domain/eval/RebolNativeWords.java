package org.jebol.domain.eval;

import org.jebol.domain.eval.definition.*;

import org.jebol.domain.host.HostService;
import org.jebol.domain.read.Construction;
import org.jebol.domain.value.*;

import java.util.*;
import java.util.stream.Stream;


public final class RebolNativeWords {

    private String errorCatalogueSource = "";
    private String operatingSystemName = "JVM";
    private Context systemInternals = Context.root();
    private final BootDeclarations bootDeclarations = new BootDeclarations();
    private final Map<String, DefaultNative> actions = new LinkedHashMap<>();
    private final Map<String, DefaultNative> natives = new LinkedHashMap<>();
    private final Map<String, DefaultNative> datatypePredicates = new LinkedHashMap<>();
    private final Map<String, DefaultNative> additionsToRebol = new LinkedHashMap<>();
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

    private static final int CODEC_HANDLE_IDENTITY = 1000;

    private RebolNativeWords() {
        registerActions();
        registerNatives();
        registerDatatypePredicates();
        registerExtensions();
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

        Stream.of(actions, natives, additionsToRebol)
                .flatMap(registered -> registered.values().stream())
                .forEach(definition -> declare(definition, bootDeclarations.specOf(definition)));
        datatypePredicates.values()
                .forEach(predicate -> declare(predicate, bootDeclarations.theSpecEveryDatatypeTestHas()));
        everyDefinition().forEach(definition -> context.register(definition.nativeName(), definition));
        operatorTwins.forEach((operator, twin) -> context.register(operator, new OperatorValue(operator, definitionCalled(twin))));
        aliases.forEach((alias, originalName) -> context.register(alias, definitionCalled(originalName)));
        return context;
    }

    public Construction construction() {
        return makingAndConverting;
    }

    public Maker makerFor(Evaluator evaluator, Context where) {
        return new InterpreterMaker(evaluator, where, makingAndConverting);
    }

    private void registerActions() {
        registerAction(new AddAction());
        registerAction(new SubtractAction());
        registerAction(new MultiplyAction());
        registerAction(new DivideAction());
        registerAction(new RemainderAction());
        registerAction(new PowerAction());
        registerAction(new BitwiseAndAction());
        registerAction(new BitwiseOrAction());
        registerAction(new BitwiseXorAction());
        registerAction(new NegateAction());
        registerAction(new ComplementAction());
        registerAction(new AbsoluteAction());
        alias("abs", "absolute");
        registerAction(new RoundAction());
        registerAction(new RandomAction(encodings));
        registerAction(new IsOddAction());
        registerAction(new IsEvenAction());
        registerAction(new HeadAction(grantedServices));
        registerAction(new TailAction(grantedServices));
        registerAction(new IsHeadAction());
        registerAction(new IsTailAction(grantedServices));
        registerAction(new IsPastAction());
        registerAction(new NextAction(grantedServices));
        registerAction(new BackAction(grantedServices));
        registerAction(new SkipAction(grantedServices));
        registerAction(new AtAction(grantedServices));
        registerAction(new AtzAction(grantedServices));
        registerAction(new IndexAction(grantedServices));
        registerAction(new IndexzAction(grantedServices));
        registerAction(new LengthAction(grantedServices));
        registerAction(new PickAction());
        registerAction(new FindAction());
        registerAction(new SelectAction());
        registerAction(new ReflectAction(bootDeclarations));
        registerAction(new MakeAction());
        registerAction(new ToAction());
        registerAction(new CopyAction());
        registerAction(new TakeAction(ports.cryptPort()));
        registerAction(new PutAction());
        registerAction(new InsertAction(grantedServices));
        registerAction(new AppendAction(grantedServices));
        registerAction(new RemoveAction());
        registerAction(new ChangeAction());
        registerAction(new PokeAction());
        registerAction(new ClearAction(grantedServices));
        registerAction(new TrimAction());
        registerAction(new SwapAction());
        registerAction(new ReverseAction());
        registerAction(new SortAction());
        registerAction(new CreateAction(grantedServices, ports));
        registerAction(new DeleteAction(grantedServices, ports));
        registerAction(new OpenAction(grantedServices, ports));
        registerAction(new CloseAction(grantedServices, ports));
        registerAction(new ReadAction(grantedServices, ports));
        registerAction(new WriteAction(grantedServices, ports));
        registerAction(new IsOpenAction(grantedServices, ports));
        registerAction(new QueryAction(grantedServices, ports));
        registerAction(new ModifyAction(grantedServices, ports));
        registerAction(new UpdateAction(grantedServices, ports));
        registerAction(new RenameAction(grantedServices, ports));
        registerAction(new FlushAction());
    }

    private void registerNatives() {
        registerNative(new AjoinNative());
        registerNative(new AlsoNative());
        registerNative(new AllNative());
        registerNative(new AnyNative());
        registerNative(new ApplyNative());
        registerNative(new AssertNative());
        registerNative(new AttemptNative());
        registerNative(new BreakNative());
        registerNative(new CaseNative());
        registerNative(new CatchNative());
        registerNative(new CommentNative());
        registerNative(new ComposeNative());
        registerNative(new ObjectNative());
        alias("context", "object");
        registerNative(new ContinueNative());
        registerNative(new DoNative());
        registerNative(new EitherNative());
        registerNative(new ExitNative());
        registerNative(new FindScriptNative());
        registerNative(new ForNative());
        registerNative(new ForAllNative());
        registerNative(new ForeverNative());
        registerNative(new ForEachNative());
        registerNative(new ForSkipNative());
        registerNative(new HaltNative());
        registerNative(new IfNative());
        registerNative(new LoopNative());
        registerNative(new MapEachNative());
        registerNative(new QuitNative());
        registerNative(new ProtectNative());
        registerNative(new UnprotectNative());
        registerNative(new IsProtectedNative());
        registerNative(new RecycleNative());
        registerNative(new ReleaseNative());
        registerNative(new ReduceNative());
        registerNative(new RepeatNative());
        registerNative(new RemoveEachNative());
        registerNative(new ReturnNative());
        registerNative(new SwitchNative());
        registerNative(new ThrowNative());
        registerNative(new TraceNative());
        registerNative(new TryNative());
        registerNative(new UnlessNative());
        registerNative(new UntilNative());
        registerNative(new WhileNative());
        registerNative(new AsNative());
        registerNative(new BindNative());
        registerNative(new UnbindNative());
        registerNative(new ContextOfWordNative());
        registerNative(new ConstructNative());
        registerNative(new DebaseNative(encodings));
        registerNative(new EnbaseNative(encodings));
        registerNative(new DecloakNative(encodings));
        registerNative(new EncloakNative(encodings));
        registerNative(new DelineNative());
        registerNative(new EnlineNative());
        registerNative(new DetabNative());
        registerNative(new EntabNative());
        registerNative(new DifferenceNative());
        registerNative(new ExcludeNative());
        registerNative(new IntersectNative());
        registerNative(new UnionNative());
        registerNative(new UniqueNative());
        registerNative(new LowercaseNative());
        registerNative(new UppercaseNative());
        registerNative(new DehexNative(encodings));
        registerNative(new EnhexNative(encodings));
        registerNative(new GetNative());
        registerNative(new InNative());
        registerNative(new ParseNative());
        registerNative(new SetNative());
        registerNative(new ToHexNative());
        registerNative(new TypeOfNative());
        registerNative(new UnsetNative());
        registerNative(new UtfNative());
        registerNative(new InvalidUtfNative());
        registerNative(new IsValueNative());
        registerNative(new ToValueNative());
        registerNative(new SplitLinesNative());
        registerNative(new PrintNative());
        registerNative(new PrinNative());
        registerNative(new MoldNative());
        registerNative(new FormNative());
        registerNative(new NewLineNative());
        registerNative(new IsNewLineNative());
        registerNative(new ToLocalFileNative(grantedServices, localFileSeparator));
        registerNative(new ToRebolFileNative());
        registerNative(new TranscodeNative());
        registerNative(new EchoNative(grantedServices));
        registerNative(new NowNative(grantedServices));
        registerNative(new WaitNative());
        registerNative(new WakeUpNative());
        registerNative(new WhatDirNative(grantedServices));
        registerNative(new ChangeDirNative(grantedServices));
        registerNative(new FirstNative());
        registerNative(new SecondNative());
        registerNative(new ThirdNative());
        registerNative(new FourthNative());
        registerNative(new FifthNative());
        registerNative(new SixthNative());
        registerNative(new SeventhNative());
        registerNative(new EighthNative());
        registerNative(new NinthNative());
        registerNative(new TenthNative());
        registerNative(new LastNative());
        registerNative(new CosineNative());
        registerNative(new SineNative());
        registerNative(new TangentNative());
        registerNative(new ArccosineNative());
        registerNative(new ArcsineNative());
        registerNative(new ArctangentNative());
        registerNative(new ExponentialNative());
        registerNative(new CommonLogarithmNative());
        registerNative(new BinaryLogarithmNative());
        registerNative(new NaturalLogarithmNative());
        registerNative(new NotNative());
        registerNative(new SquareRootNative());
        registerNative(new ShiftNative());
        registerNative(new IncrementNative());
        registerNative(new DecrementNative());
        registerNative(new FirstPlusNative());
        registerNative(new StackNative());
        registerNative(new ResolveNative());
        registerNative(new GetEnvNative(grantedServices));
        registerNative(new SetEnvNative(grantedServices));
        registerNative(new ListEnvNative(grantedServices));
        registerNative(new CallNative(grantedServices));
        registerNative(new BrowseNative(grantedServices));
        registerNative(new EvokeNative());
        registerNative(new RequestFileNative(grantedServices));
        registerNative(new RequestDirNative(grantedServices));
        registerNative(new RequestPasswordNative(grantedServices));
        registerNative(new IsAsciiNative());
        registerNative(new IsLatin1Native());
        registerNative(new StatsNative());
        registerNative(new DoCodecNative());
        registerNative(new SetSchemeNative());
        registerNative(new LoadExtensionNative());
        registerNative(new DoCommandsNative());
        registerNative(new DsNative());
        registerNative(new DumpNative());
        registerNative(new CheckNative());
        registerNative(new DoCallbackNative());
        registerNative(new LimitUsageNative());
        registerNative(new IsSelflessNative());
        registerNative(new MapEventNative());
        registerNative(new MapGobOffsetNative());
        registerNative(new AsPairNative());
        registerNative(new AsColorNative());
        registerNative(new EqualNative());
        registerNative(new NotEqualNative());
        registerNative(new EquivNative());
        registerNative(new NotEquivNative());
        registerNative(new StrictEqualNative());
        registerNative(new StrictNotEqualNative());
        registerNative(new SameNative());
        registerNative(new GreaterNative());
        registerNative(new GreaterOrEqualNative());
        registerNative(new LesserNative());
        registerNative(new LesserOrEqualNative());
        registerNative(new MinimumNative());
        registerNative(new MaximumNative());
        registerNative(new IsNegativeNative());
        registerNative(new IsPositiveNative());
        registerNative(new IsZeroNative());
        registerNative(version);
        registerNative(new PickzNative());
        registerNative(new PokezNative());
        registerNative(new SwapEndianNative(encodings));
        registerNative(new DidNative());
        alias("true?", "did");
        registerNative(new CollectWordsNative());
        registerNative(new WithNative());
        registerNative(new TruncateNative());
        registerNative(new HashNative());
        registerNative(new ToRealFileNative(grantedServices));
        registerNative(new IsDirectoryNative(grantedServices));
        registerNative(new IsWildcardNative());
        registerNative(new AccessOsNative(grantedServices));
        registerNative(new IsTerminalNative());
        registerNative(new ReadKeyNative(grantedServices));
        registerNative(new ArctangentOfAPointNative());
        registerNative(new CosNative());
        registerNative(new SinNative());
        registerNative(new TanNative());
        registerNative(new AtanNative());
        registerNative(new AsinNative());
        registerNative(new AcosNative());
        registerNative(new ArctangentOfTwoSidesNative());
        registerNative(new SqrtNative());
        registerNative(new IsNumberNative());
        registerNative(new ModNative());
        registerNative(new ModuloNative());
        registerNative(new ShiftLeftNative());
        registerNative(new ShiftRightNative());
        registerNative(new ToRadiansNative());
        registerNative(new ToDegreesNative());
        registerNative(new GreatestCommonDivisorNative());
        registerNative(new LowestCommonMultipleNative());
        registerNative(new FractionNative());
        registerNative(new PrimeNative());
        registerNative(new LerpNative());
        registerNative(new ClampNative());
        registerNative(new IntegerDivideNative());
        registerNative(new DistanceNative());
        registerNative(new FactorialNative());
        registerNative(new ChecksumNative(encodings));
        registerNative(new RegisterNative());
        registerNative(new IsComplementedNative());
        registerNative(new CompressNative(encodings));
        registerNative(new DecompressNative(encodings));
        registerNative(new Rc4Native());
        registerNative(new RsaInitNative());
        registerNative(new RsaNative());
        registerNative(new DhInitNative());
        registerNative(new DhNative());
        registerNative(new EcdhNative());
        registerNative(new GenerateNative());
        registerNative(new EcdsaNative());
        registerNative(new BinaryNative());
        registerNative(new IconvNative(encodings));
        registerNative(new HsvToRgbNative());
        registerNative(new RgbToHsvNative());
        registerNative(new ColorDistanceNative());
        registerNative(new ImageDiffNative());
        registerNative(new TintNative());
        registerNative(new LuminosityNative());
        registerNative(new GrayscaleNative());
        registerNative(new ResizeNative());
        registerNative(new PremultiplyNative());
        registerNative(new BlurNative());
        registerNative(new ImageNative());
        registerNative(new FilterNative(encodings));
        registerNative(new UnfilterNative(encodings));
        registerNative(new DelectNative());
        registerNative(new FormOidNative());
    }

    private void registerDatatypePredicates() {
        for (Datatype datatype : Datatype.values()) {
            DatatypePredicateAction predicate = new DatatypePredicateAction(datatype);
            datatypePredicates.put(predicate.nativeName(), predicate);
        }
    }

    private void registerExtensions() {
        registerAddition(new IsAnyTypeNative());
        registerAddition(new IsCopyableNative());
        registerAddition(new IsImmediateNative());
        registerAddition(new IsInternalNative());
        registerAddition(new RequestColorNative(grantedServices));
        registerAddition(new XtestNative());
        registerAddition(new InitTopWindowNative(grantedServices));
        registerAddition(new ShowNative(grantedServices));
        registerAddition(new GuiMetricNative(grantedServices));
    }

    private ObjectValue systemObject(Context systemContext) {
        Context system = Context.root();
        system.register("catalog", catalog());
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

    private Value catalog() {
        Context catalog = Context.root();
        catalog.register("datatypes", BlockValue.block(Arrays.stream(Datatype.values()).map(datatype -> (Value) DatatypeValue.of(datatype)).toList()));
        catalog.register("structs", registeredStructLayouts);
        catalog.register("actions", wordsInRegistrationOrder(actions));
        catalog.register("natives", wordsInRegistrationOrder(natives));
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

    private BlockValue wordsInRegistrationOrder(Map<String, DefaultNative> registered) {
        return BlockValue.block(registered.keySet().stream().<Value>map(WordValue::of).toList());
    }

    private Value valueFor(Context context) {
        return new ObjectValue(context);
    }

    private void declare(DefaultNative built, BlockValue spec) {
        built.declaredBy(spec, new DeclaredArguments(spec).inPlaceOf(built.parametersAsWritten()));
    }

    private void registerOperator(String spelling, String prefixTwin) {
        if (isUnregistered(prefixTwin)) {
            throw new IllegalStateException("operator " + spelling + " has no prefix twin called " + prefixTwin);
        }
        operatorTwins.put(spelling, prefixTwin);
    }

    private void alias(String alias, String originalName) {
        if (isUnregistered(originalName)) {
            throw new IllegalStateException(alias + " is an alias of " + originalName + ", which is not registered");
        }
        aliases.put(alias, originalName);
    }

    private void registerAction(DefaultNative action) {
        actions.put(action.nativeName(), action);
    }

    private void registerNative(DefaultNative builtIn) {
        natives.put(builtIn.nativeName(), builtIn);
    }

    private void registerAddition(DefaultNative addition) {
        additionsToRebol.put(addition.nativeName(), addition);
    }

    private Stream<DefaultNative> everyDefinition() {
        return Stream.of(actions, natives, datatypePredicates, additionsToRebol)
                .flatMap(registered -> registered.values().stream());
    }

    private boolean isUnregistered(String spelling) {
        return everyDefinition().noneMatch(definition -> definition.nativeName().equals(spelling));
    }

    private DefaultNative definitionCalled(String spelling) {
        return everyDefinition()
                .filter(definition -> definition.nativeName().equals(spelling))
                .findFirst()
                .orElseThrow();
    }

    private List<Value> catalogueEntries() {
        return bootDeclarations.theRowsBelowTheHeaderOf(errorCatalogueSource);
    }

}
