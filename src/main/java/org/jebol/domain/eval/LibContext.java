package org.jebol.domain.eval;

import org.jebol.domain.eval.actions.*;
import org.jebol.domain.eval.natives.*;

import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;

public final class LibContext {

    private final Context lib = Context.root();
    private final List<Value> actionsInOrder = new ArrayList<>();
    private final List<Value> nativesInOrder = new ArrayList<>();
    private final VersionNative version = new VersionNative();
    private final BootDeclarations declarations;
    private final GrantedServices grantedServices;
    private final Ports ports;
    private final Encodings encodings;
    private final LocalFileSeparator localFileSeparator;

    public LibContext(BootDeclarations declarations, GrantedServices grantedServices, Ports ports,
            Encodings encodings, LocalFileSeparator localFileSeparator) {
        this.declarations = declarations;
        this.grantedServices = grantedServices;
        this.ports = ports;
        this.encodings = encodings;
        this.localFileSeparator = localFileSeparator;
        registerConstants();
        registerDatatypesAndTypesets();
        registerTheWordsFunctionKindsAreMadeBy();
        registerActions();
        registerNatives();
        registerDatatypePredicates();
        registerExtensions();
        registerOperators();
    }

    public Context context() {
        return lib;
    }

    public AnyBlockValue actionsInOrder() {
        return BlockValue.block(actionsInOrder);
    }

    public AnyBlockValue nativesInOrder() {
        return BlockValue.block(nativesInOrder);
    }

    public VersionNative version() {
        return version;
    }

    private void registerConstants() {
        lib.register("true", LogicValue.yes());
        lib.register("false", LogicValue.no());
        lib.register("none", NoneValue.none());
        lib.register("on", LogicValue.yes());
        lib.register("off", LogicValue.no());
        lib.register("yes", LogicValue.yes());
        lib.register("no", LogicValue.no());
        lib.register("pi", DecimalValue.of(Math.PI));
    }

    private void registerDatatypesAndTypesets() {
        for (Datatype datatype : Datatype.values()) {
            lib.register(datatype.literalSpelling(), DatatypeValue.of(datatype));
        }
        for (Typeset typeset : Typeset.values()) {
            lib.register(typeset.literalSpelling(), TypesetValue.of(typeset));
        }
    }

    private void registerTheWordsFunctionKindsAreMadeBy() {
        lib.register("native", NoneValue.none());
        lib.register("action", NoneValue.none());
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
        registerAction(new ReflectAction(declarations));
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
            declare(predicate, declarations.theSpecEveryDatatypeTestHas());
            lib.register(predicate.nativeName(), predicate);
        }
    }

    private void registerExtensions() {
        registerExtension(new IsAnyTypeNative());
        registerExtension(new IsCopyableNative());
        registerExtension(new IsImmediateNative());
        registerExtension(new IsInternalNative());
        registerExtension(new RequestColorNative(grantedServices));
        registerExtension(new XtestNative());
        registerExtension(new InitTopWindowNative(grantedServices));
        registerExtension(new ShowNative(grantedServices));
        registerExtension(new GuiMetricNative(grantedServices));
    }

    private void registerOperators() {
        List<Value> written = declarations.operatorRows();
        for (int at = 0; at + 1 < written.size(); at += 2) {
            if (written.get(at) instanceof AnyWordValue operator && written.get(at + 1) instanceof AnyWordValue twin) {
                lib.register(operator.spelling(), new OperatorValue(operator.spelling(),
                        registered(twin.spelling(), "operator " + operator.spelling() + " has no prefix twin called ")));
            }
        }
    }

    private void registerAction(DefaultNative action) {
        registerWithItsDeclaredSpec(action);
        actionsInOrder.add(WordValue.of(action.nativeName()));
    }

    private void registerNative(DefaultNative builtIn) {
        registerWithItsDeclaredSpec(builtIn);
        nativesInOrder.add(WordValue.of(builtIn.nativeName()));
    }

    private void registerExtension(DefaultNative extension) {
        registerWithItsDeclaredSpec(extension);
    }

    private void registerWithItsDeclaredSpec(DefaultNative built) {
        declare(built, declarations.specOf(built));
        lib.register(built.nativeName(), built);
    }

    private void declare(DefaultNative built, AnyBlockValue spec) {
        built.declaredBy(spec, new DeclaredArguments(spec).inPlaceOf(built.parametersAsWritten()));
    }

    private Value registered(String spelling, String complaintIfMissing) {
        String canonical = Context.canonicalise(spelling);
        if (!lib.holds(canonical)) {
            throw new IllegalStateException(complaintIfMissing + spelling);
        }
        return lib.ownSlotFor(canonical).value();
    }
}
