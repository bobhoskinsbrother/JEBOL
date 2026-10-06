package org.jebol.domain.eval;

import org.jebol.domain.eval.definition.*;

import org.jebol.domain.date.part.DatePart;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.parse.Parser;
import org.jebol.domain.read.Construction;
import org.jebol.domain.value.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Set.of;

public final class RebolNativeWords {

    private final RebolRandom randomness = new RebolRandom();
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
    private final ChecksumPort checksumPort = new ChecksumPort(encodings);
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

    private char localFileSeparator = '/';

    public void useFileSeparator(char separator) {
        this.localFileSeparator = separator;
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

    private String modeTableSource = "";

    public void useModeTable(String source) {
        this.modeTableSource = source;
        this.consoleModes = null;
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

    private static final java.util.Set<String> FIELDS_THE_OPERATING_SYSTEM_ANSWERS =
            Set.of("uid", "euid", "gid", "egid", "pid");

    private static final int TERMINATE = 15;

    private Value signalled(Value asked) {
        long process;
        int signal;
        if (asked instanceof IntegerValue(long magnitude2)) {
            process = magnitude2;
            signal = TERMINATE;
        } else {
            List<Value> pair = ((BlockValue) asked).remaining();
            if (pair.size() != 2
                    || !(pair.get(0) instanceof IntegerValue(long magnitude1))
                    || !(pair.get(1) instanceof IntegerValue(long magnitude))) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(asked));
            }
            process = magnitude1;
            signal = (int) magnitude;
        }
        grantedServices.require(HostService.PROCESSES);
        return LogicValue.of(endProcess(process, signal));
    }

    private static Value movedOrRefusedByTheName(
            Evaluator evaluator, List<Value> arguments) {
        StringValue from = (StringValue) arguments.getFirst();
        try {
            evaluator.files().rename(from.text(), ((StringValue) arguments.get(1)).text());
        } catch (FilePort.Denied refused) {
            throw Raised.of(EvaluationFailure.NO_RENAME, from);
        }
        return arguments.get(1);
    }

    private static boolean endProcess(long process, int signal) {
        Optional<ProcessHandle> found = ProcessHandle.of(process);
        if (found.isEmpty()) {
            throw Raised.of(EvaluationFailure.PROCESS_NOT_FOUND, IntegerValue.of(process));
        }
        ProcessHandle running = found.get();
        boolean ended = signal == TERMINATE
                ? running.destroy()
                : running.destroyForcibly();
        if (!ended) {
            throw Raised.of(EvaluationFailure.PERMISSION_DENIED, String.valueOf(process));
        }
        return true;
    }

    private static boolean endsTheWayADirectoryIsWritten(String path) {
        char last = path.charAt(path.length() - 1);
        return last == '/' || last == '\\';
    }

    private boolean liesOnTheDiskAsADirectory(Evaluator evaluator, String path) {
        grantedServices.require(HostService.FILES);
        return throughPort(() -> LogicValue.of(evaluator.files().isDirectory(path)))
                .isTruthy();
    }

    private static Value refuseExtensionPoint(String extensionPoint) {
        throw Raised.of(EvaluationFailure.NO_SERVICE,
                extensionPoint + " calls code written in C, which is "
                        + ServiceRefusal.NEVER_PORTABLE.name()
                                .toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
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

        catalog.set("ciphers", BlockValue.block(CryptPort.catalogue()));

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
        Context bootFlags = Context.root();
        for (String flag : new String[] {
                "script", "args", "do", "import", "version", "debug", "secure",
                "help", "vers", "quiet", "verbose", "secure-min", "secure-max",
                "trace", "halt", "cgi", "boot-level", "no-window", "no-color",
                "legacy-repl"}) {
            bootFlags.set(flag, LogicValue.no());
        }
        options.set("flags", new ObjectValue(bootFlags));
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

        definitions.forEach(context::set);
        operatorTwins.forEach((operator, twin) ->
                context.set(operator, new OperatorValue(operator, definitions.get(twin))));
        return context;
    }

    private void define(String name, List<Parameter> parameters, Callable behaviour) {
        define(name, parameters, of(),
                (arguments, evaluator, context, refinements) ->
                        behaviour.call(arguments, evaluator, context));
    }

    private void define(String name, List<Parameter> parameters,
            Set<String> refinements, RefinedCallable behaviour) {
        definitions.put(name, new NativeValue(name, parameters, refinements, of()));
        behaviours.put(name, behaviour);
    }

    private void registerOperator(String spelling, String prefixTwin) {
        if (!definitions.containsKey(prefixTwin)) {
            throw new IllegalStateException(
                    "operator " + spelling + " has no prefix twin called " + prefixTwin);
        }
        operatorTwins.put(spelling, prefixTwin);
    }

    private static List<Parameter> takes(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name));
        }
        return parameters;
    }

    private static final Set<Datatype> ANYTHING = Typeset.ANY_TYPE.members();

    private static List<Parameter> takesAnything(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, ANYTHING));
        }
        return parameters;
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
        register(new WhetherComplementedNative());
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
        define("now", List.of(),
                of("year", "month", "day", "time", "zone", "date",
                        "weekday", "yearday", "precise", "utc"),
                (arguments, evaluator, context, refinements) -> {
                    grantedServices.require(HostService.CLOCK);
                    return whatTheClockSays(refinements);
                });

        define("also", takesAnything("value1", "value2"),
                (arguments, evaluator, context) -> arguments.getFirst());

        define("comment", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> UnsetValue.unset());

        define("to-value", takesAnything("value"),
                (arguments, evaluator, context) ->
                        arguments.getFirst() instanceof UnsetValue
                                ? NoneValue.none()
                                : arguments.getFirst());


        define("trace", List.of(Parameter.required("mode",
                        of(Datatype.INTEGER, Datatype.LOGIC))),
                of("back", "function"),
                (arguments, evaluator, context, refinements) -> {
                    Value mode = arguments.getFirst();
                    Trace tracing = evaluator.tracing();
                    tracing.writeTo(evaluator.output());
                    if (refinements.contains("back")) {
                        if (mode instanceof IntegerValue(long magnitude)) {
                            tracing.showTheLastAndStopTracing(
                                    (int) magnitude);
                            return UnsetValue.unset();
                        }
                        tracing.keepRatherThanPrint(mode.isTruthy());
                    } else {
                        tracing.keepRatherThanPrint(false);
                    }
                    int wanted = mode instanceof IntegerValue(long magnitude)
                            ? (int) magnitude
                            : (mode.isTruthy() ? Trace.EVERYTHING : 0);
                    tracing.level(wanted, refinements.contains("function"));
                    return UnsetValue.unset();
                });

        define("load-extension", List.of(
                        Parameter.required("name", of(Datatype.FILE, Datatype.BINARY)),
                        Parameter.belongingTo("dispatch", "function", of(Datatype.HANDLE))),
                of("dispatch"),
                (arguments, evaluator, context, refinements) ->
                        refuseExtensionPoint("load-extension"));
        define("do-callback", takes("callback"),
                (arguments, evaluator, context) -> refuseExtensionPoint("do-callback"));
        define("do-commands", takes("commands"),
                (arguments, evaluator, context) -> refuseExtensionPoint("do-commands"));
        define("access-os", List.of(
                        Parameter.required("field", of(Datatype.WORD)),
                        Parameter.belongingTo("set", "value",
                                of(Datatype.INTEGER, Datatype.BLOCK))),
                of("set"),
                (arguments, evaluator, context, refinements) -> {
                    WordValue field = (WordValue) arguments.getFirst();
                    if (!FIELDS_THE_OPERATING_SYSTEM_ANSWERS.contains(field.canonical())) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG, field.spelling());
                    }
                    if (!"pid".equals(field.canonical())) {
                        throw Raised.of(EvaluationFailure.NOT_HERE, field.spelling());
                    }
                    if (!refinements.contains("set")) {
                        return IntegerValue.of(ProcessHandle.current().pid());
                    }
                    return signalled(arguments.get(1));
                });

        define("random", List.of(Parameter.required("value")),
                of("seed", "only", "secure"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("seed")) {
                        return seededBy(arguments.get(0));
                    }
                    return switch (arguments.get(0)) {
                        case IntegerValue whole -> IntegerValue.of(
                                randomLongUpTo(whole.magnitude()));
                        case DecimalValue quantity -> DecimalValue.of(
                                randomFraction() * quantity.quantity());
                        case BlockValue other when other.datatype() != Datatype.BLOCK ->
                                throw Raised.cannotUse(other, "random");
                        case BlockValue block when refinements.contains("only") ->
                                block.remaining().isEmpty()
                                        ? NoneValue.none()
                                        : block.remaining().get(randomness.below(
                                                block.remaining().size()));
                        case BlockValue block -> shuffled(block);
                        case StringValue text when refinements.contains("only") ->
                                oneCharacterPickedAtRandomByByteNotByCharacter(
                                        text);
                        case StringValue text -> shuffledTextInPlace(text);
                        case BinaryValue bytes when refinements.contains("only") ->
                                oneOctetPickedAtRandom(bytes);
                        case BinaryValue bytes -> shuffledBytes(bytes);
                        case VectorValue ignored when refinements.contains("only") ->
                                raiseRefinementAVectorHasNoUseFor();
                        case TupleValue tuple -> randomisedOctets(tuple);
                        case PairValue point -> randomisedHalves(point);
                        case CharacterValue letter -> letter.codepoint() == 0
                                ? letter
                                : CharacterValue.of(aValidCodepointUpTo(
                                        letter.codepoint()));
                        case TimeValue span -> TimeValue.ofNanoseconds(
                                randomLongUpTo(span.nanoseconds()));
                        case DateValue when -> randomisedDate(when);
                        case LogicValue ignored ->
                                LogicValue.of((randomness.next() & 1) == 1);
                        case VectorValue vector -> shuffledElements(vector);
                        default -> throw Raised.cannotUse(arguments.get(0), "random");
                    };
                });

    }

    private int aValidCodepointUpTo(int limit) {
        while (true) {
            int picked = 1 + randomness.below(limit);
            boolean surrogate = picked >= 0xD800 && picked <= 0xDFFF;
            if (!surrogate && picked <= CharacterValue.MAXIMUM_CODEPOINT) {
                return picked;
            }
        }
    }

    private Value seededBy(Value chosen) {
        randomness.seed(switch (chosen) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue quantity -> Double.doubleToRawLongBits(quantity.quantity());
            case CharacterValue letter -> letter.codepoint();
            case StringValue text ->
                    encodings.checksumSeedOf(text.text().getBytes(StandardCharsets.UTF_8));
            case BinaryValue bytes -> encodings.checksumSeedOf(bytes.octetsFromHere());
            case TupleValue tuple -> encodings.checksumSeedOf(shownOctetsOf(tuple));
            case TimeValue span -> span.nanoseconds();
            case DateValue day -> seedPackedFrom(day);
            case PairValue point -> halvesSideBySide(point);
            case LogicValue truth -> truth.truth() ? System.nanoTime() : 1L;
            case BlockValue ignored -> raiseRefinementNoArmAccepts();
            case VectorValue ignored -> raiseRefinementNoArmAccepts();
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "random/seed has nothing to make a seed out of a "
                            + chosen.datatype().literalSpelling());
        });
        return UnsetValue.unset();
    }

    private static byte[] shownOctetsOf(TupleValue tuple) {
        byte[] octets = new byte[tuple.shownCount()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) tuple.octetAt(at + 1);
        }
        return octets;
    }

    private static long seedPackedFrom(DateValue day) {
        long dayOfYear = day.day() + dayCountBeforeMonth(day.year(), day.month());
        long nanoseconds = day.timeOfDay().map(TimeValue::nanoseconds).orElse(0L);
        return ((long) day.year() << 48) + (dayOfYear << 32) + nanoseconds;
    }

    private static long dayCountBeforeMonth(int year, int month) {
        long days = 0;
        for (int earlier = 1; earlier < month; earlier++) {
            days += java.time.YearMonth.of(year, earlier).lengthOfMonth();
        }
        return days;
    }

    private static long halvesSideBySide(PairValue point) {
        long lower = Integer.toUnsignedLong(Float.floatToRawIntBits((float) point.x()));
        long upper = Integer.toUnsignedLong(Float.floatToRawIntBits((float) point.y()));
        return (upper << 32) | lower;
    }

    private static long raiseRefinementNoArmAccepts() {
        throw Raised.of(EvaluationFailure.BAD_REFINES,
                "random/seed makes no seed out of this");
    }

    private static Value raiseRefinementAVectorHasNoUseFor() {
        throw Raised.of(EvaluationFailure.BAD_REFINES,
                "random/only does not pick one element out of a vector");
    }

    private Value oneCharacterPickedAtRandomByByteNotByCharacter(StringValue text) {
        byte[] octets = text.text().getBytes(StandardCharsets.UTF_8);
        if (octets.length == 0) {
            return NoneValue.none();
        }
        int at = steppedBackToACharacterBoundary(octets, randomness.below(octets.length));
        return CharacterValue.of(
                new String(octets, at, octets.length - at, StandardCharsets.UTF_8)
                        .codePointAt(0));
    }

    private Value oneOctetPickedAtRandom(BinaryValue bytes) {
        byte[] octets = bytes.octetsFromHere();
        if (octets.length == 0) {
            return NoneValue.none();
        }
        int at = steppedBackToACharacterBoundary(octets, randomness.below(octets.length));
        return IntegerValue.of(octets[at] & 0xFF);
    }

    private static int steppedBackToACharacterBoundary(byte[] octets, int landedOn) {
        int at = landedOn;
        while (at > 0 && (octets[at] & 0xC0) == 0x80) {
            at--;
        }
        return at;
    }

    private long randomLongUpTo(long limit) {
        if (limit == 0) {
            return 0;
        }
        long span = Math.abs(limit);
        if (Long.compareUnsigned(span, GENERATOR_RANGE) > 0) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "random cannot draw evenly from a number larger than "
                            + "two to the sixty-second");
        }
        long lastExactMultiple =
                GENERATOR_RANGE - Long.remainderUnsigned(GENERATOR_RANGE, span) - 1;
        long drawn;
        do {
            drawn = randomness.next();
        } while (Long.compareUnsigned(drawn, lastExactMultiple) > 0);
        long picked = 1 + Long.remainderUnsigned(drawn, span);
        return limit < 0 ? -picked : picked;
    }

    private double randomFraction() {
        return (double) randomness.next() / (double) GENERATOR_RANGE;
    }

    private static final long GENERATOR_RANGE = 1L << 62;

    private Value randomisedDate(DateValue when) {
        java.time.LocalDate drawn = java.time.LocalDate
                .of((int) randomLongUpTo(when.year()), 1, 1)
                .plusMonths(randomLongUpTo(MONTHS_A_YEAR))
                .plusDays(randomLongUpTo(LONGEST_MONTH));
        return when.timeOfDay().isEmpty()
                ? DateValue.of(drawn.getYear(), drawn.getMonthValue(), drawn.getDayOfMonth())
                : new DateValue(drawn.getYear(), drawn.getMonthValue(),
                        drawn.getDayOfMonth(),
                        java.util.Optional.of(TimeValue.ofNanoseconds(
                                randomLongUpTo(TimeValue.NANOSECONDS_PER_DAY))),
                        when.zoneMinutes());
    }

    private static final int MONTHS_A_YEAR = 12;
    private static final int LONGEST_MONTH = 31;


    private Value randomisedOctets(TupleValue tuple) {
        int[] octets = tuple.segments();
        for (int at = 0; at < octets.length; at++) {
            if (octets[at] != 0) {
                octets[at] = randomness.belowWithoutNarrowing(octets[at] + 1);
            }
        }
        return TupleValue.of(octets);
    }

    private Value randomisedHalves(PairValue point) {
        return PairValue.of(randomisedHalf(point.x()), randomisedHalf(point.y()));
    }

    private double randomisedHalf(double half) {
        long bound = (long) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, half));
        if (bound == 0) {
            return 0;
        }
        return randomLongUpTo(bound);
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

    private void
    registerObjects() {
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
        register(new WhetherANewLineNative());
        register(new ObjectNative());
        register(new WithNative());
        register(new WhetherSelflessNative());
        register(new WhetherProtectedNative());
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
        register(new WhetherOddAction());
        register(new WhetherEvenAction());
        register(new TypeOfNative());
        for (Datatype datatype : Datatype.values()) {
            register(new DatatypePredicateAction(datatype));
        }
        register(new WhetherAnyTypeNative());
        register(new WhetherCopyableNative());
        register(new WhetherImmediateNative());
        register(new WhetherInternalNative());
        register(new WhetherTrueNative());
        register(new DidNative());
        register(new WhetherANumberNative());
        register(new WhetherAsciiNative());
        register(new WhetherLatin1Native());
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
        register(new WhetherNegativeNative());
        register(new WhetherPositiveNative());
        register(new WhetherZeroNative());
        register(new WhetherAValueNative());
        register(new UnsetNative());
        register(new ProtectNative());
        register(new UnprotectNative());
        register(new DelectNative());
    }

    private void registerSetting() {
        register(new SetNative());
        register(new TakeAction());
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
        register(new WhetherPastAction());
        register(new WhetherHeadAction());
        register(new WhetherTailAction(grantedServices));
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


    private static Value argumentFor(
            String refinement, List<String> declaredOrder,
            List<Value> arguments, Set<String> asked, int firstRefinementArgument) {

        if (!asked.contains(refinement)) {
            return null;
        }
        int at = firstRefinementArgument;
        for (String earlier : declaredOrder) {
            if (earlier.equals(refinement)) {
                return at < arguments.size() ? arguments.get(at) : null;
            }
            if (asked.contains(earlier)) {
                at++;
            }
        }
        return null;
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


    private VectorValue shuffledElements(VectorValue vector) {
        for (int remaining = vector.lengthFromHere(); remaining > 1; remaining--) {
            int chosen = vector.index() + randomness.below(remaining);
            int last = vector.index() + remaining - 1;
            long held = vector.storage().at(chosen);
            vector.storage().set(chosen, vector.storage().at(last));
            vector.storage().set(last, held);
        }
        return vector;
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

    private <T> void shuffleTheWayTheCDoes(List<T> items) {
        for (int remaining = items.size(); remaining > 1;) {
            int chosen = randomness.below(remaining);
            remaining--;
            T held = items.get(chosen);
            items.set(chosen, items.get(remaining));
            items.set(remaining, held);
        }
    }

    private Value shuffled(BlockValue block) {
        List<Value> items = new ArrayList<>(block.remaining());
        shuffleTheWayTheCDoes(items);
        for (int at = 0; at < items.size(); at++) {
            block.storage().set(block.index() + at, items.get(at));
        }
        return block;
    }

    private Value shuffledTextInPlace(StringValue text) {
        List<Integer> letters = new ArrayList<>();
        for (int at = text.index(); at <= text.storageLength(); at++) {
            letters.add(text.storage().at(at));
        }
        shuffleTheWayTheCDoes(letters);
        for (int at = 0; at < letters.size(); at++) {
            text.storage().set(text.index() + at, letters.get(at));
        }
        return text;
    }

    private Value shuffledBytes(BinaryValue bytes) {
        List<Integer> octets = new ArrayList<>();
        for (int at = bytes.index(); at <= bytes.storageLength(); at++) {
            octets.add(bytes.storage().at(at));
        }
        shuffleTheWayTheCDoes(octets);
        for (int at = 0; at < octets.size(); at++) {
            bytes.storage().set(bytes.index() + at, octets.get(at));
        }
        return bytes;
    }

    private List<Value> catalogueEntries() {
        return bootDeclarations.theRowsBelowTheHeaderOf(errorCatalogueSource);
    }

    private SequencedSet<String> consoleModes;

    private static final String THE_CONSOLE_MODE_HEADING = "*console-modes*";

    private SequencedSet<String> consoleModes() {
        if (consoleModes == null) {
            consoleModes = theConsoleModesInTheModeTable();
        }
        return consoleModes;
    }

    private SequencedSet<String> theConsoleModesInTheModeTable() {
        List<Value> rows = bootDeclarations.theRowsBelowTheHeaderOf(modeTableSource);
        for (int at = 0; at + 1 < rows.size(); at++) {
            if (rows.get(at) instanceof WordValue name
                    && name.canonical().equals(THE_CONSOLE_MODE_HEADING)
                    && rows.get(at + 1) instanceof BlockValue listed) {
                return theWordsIn(listed);
            }
        }
        return new LinkedHashSet<>();
    }

    private static SequencedSet<String> theWordsIn(BlockValue listed) {
        SequencedSet<String> named = new LinkedHashSet<>();
        for (Value each : listed.remaining()) {
            if (each instanceof WordValue word) {
                named.add(word.canonical());
            }
        }
        return named;
    }


    private static final Set<Datatype> WHAT_PARSE_TAKES = Typeset.SERIES.members();


    private static final List<String> CONSOLE_MEASUREMENTS =
            List.of("window-cols", "window-rows", "buffer-cols", "buffer-rows");

    private static final int COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE = 80;

    private static int measureOfTheConsole(String measurement) {
        return measurement.equals("window-cols")
                ? COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE
                : 0;
    }

    private Value readFromTheFileBehind(
            PortValue port, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {

        grantedServices.require(HostService.FILES);
        String path = SeekableFilePort.pathOf(port);
        if (port.schemeName().equals("dir")) {
            return throughPort(() -> SeekableFilePort.namesIn(evaluator.files(), path));
        }
        boolean wasClosed = !port.isOpen();
        if (wasClosed) {
            port.markOpen(true);
            SeekableFilePort.moveTo(port, 0);
        }
        Value seek = refinements.contains("seek")
                ? argumentFor("seek", List.of("part", "seek"), arguments, refinements, 1)
                : null;
        if (seek instanceof IntegerValue(long magnitude2)) {
            SeekableFilePort.moveTo(port, magnitude2);
        }
        Value part = refinements.contains("part")
                ? argumentFor("part", List.of("part", "seek"), arguments, refinements, 1)
                : null;
        if (part instanceof IntegerValue(long magnitude1)
                && magnitude1 < 0
                && -magnitude1 > SeekableFilePort.positionOf(port)) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, part);
        }
        Value read = throughPort(() -> SeekableFilePort.readFrom(
                evaluator.files(), port,
                part instanceof IntegerValue(long magnitude) ? magnitude : null));
        if (wasClosed) {
            port.markOpen(false);
        }
        return asTextWhereAskedFor(read, refinements);
    }

    private static Value asTextWhereAskedFor(Value read, Set<String> refinements) {
        if (!(read instanceof BinaryValue bytes)
                || !(refinements.contains("string") || refinements.contains("lines"))) {
            return read;
        }
        Optional<String> text = FileReading.decodedUtfText(bytes.octetsFromHere());
        if (text.isEmpty()) {
            return read;
        }
        return refinements.contains("lines")
                ? BlockValue.block(StringValue.of(text.orElseThrow())
                        .linesDroppingOneTrailingEmptyLine())
                : StringValue.of(text.orElseThrow());
    }

    private void openTheFileBehind(
            PortValue port, Evaluator evaluator, Set<String> refinements) {

        String path = SeekableFilePort.pathOf(port);
        if (port.schemeName().equals("dir")) {
            if (!((LogicValue) throughPort(() -> LogicValue.of(
                    somethingIsThereFor(path, evaluator.files())))).truth()) {
                throw Raised.of(EvaluationFailure.CANNOT_OPEN,
                        StringValue.of(path, Datatype.FILE));
            }
            SeekableFilePort.moveTo(port, 0);
            return;
        }
        refuseANewFileNobodyMayWriteTo(refinements, path);
        boolean alreadyThere = ((LogicValue) throughPort(() ->
                LogicValue.of(evaluator.files().exists(path)))).truth();
        if (!mayWrite(refinements)) {
            if (!alreadyThere) {
                throw Raised.of(EvaluationFailure.CANNOT_OPEN,
                        StringValue.of(path, Datatype.FILE));
            }
        } else if (!alreadyThere || emptiesWhatIsThere(refinements)) {
            throughPort(() -> {
                evaluator.files().write(path, new byte[0]);
                return NoneValue.none();
            });
        }
        SeekableFilePort.openedAt(port, 0, mayWrite(refinements));
    }

    private void sayWhereTheInterpreterIsStanding(Evaluator evaluator) {
        if (!thereIsAnEnvironmentToWriteTo()) {
            return;
        }
        evaluator.environment().nameHolds(
                "PWD", evaluator.files().workingDirectory());
    }

    private boolean thereIsAnEnvironmentToWriteTo() {
        return grantedServices.allow(HostService.ENVIRONMENT);
    }

    private static boolean somethingIsThereFor(String path, FilePort files) {
        if (!FileReading.holdsAWildcard(path)) {
            return files.exists(path);
        }
        int lastSeparator = path.lastIndexOf('/');
        String directory = path.substring(0, lastSeparator + 1);
        String pattern = path.substring(lastSeparator + 1);
        if (FileReading.holdsAWildcard(directory)) {
            return false;
        }
        try {
            return files.namesIn(directory.isEmpty() ? "." : directory).stream()
                    .anyMatch(listed -> Wildcards.STARS_AND_QUESTION_MARKS.matchTheWholeOf(
                            FileReading.withoutItsSlash(listed), pattern));
        } catch (RuntimeException nothingThere) {
            return false;
        }
    }

    private static void refuseANewFileNobodyMayWriteTo(
            Set<String> refinements, String path) {
        if (refinements.contains("new") && !mayWrite(refinements)) {
            throw Raised.of(EvaluationFailure.BAD_FILE_MODE,
                    StringValue.of(path, Datatype.FILE));
        }
    }

    private static boolean mayWrite(Set<String> refinements) {
        return refinements.contains("write") || namesNeitherWay(refinements);
    }

    private static boolean mayRead(Set<String> refinements) {
        return refinements.contains("read") || namesNeitherWay(refinements);
    }

    private static boolean namesNeitherWay(Set<String> refinements) {
        return !refinements.contains("read") && !refinements.contains("write");
    }

    private static boolean emptiesWhatIsThere(Set<String> refinements) {
        return refinements.contains("new")
                || !(mayRead(refinements) || refinements.contains("seek"));
    }


    private static void queueWhatHappenedTo(
            PortValue port, String happened, Evaluator evaluator) {

        if (!(evaluator.hostPort("system") instanceof PortValue queue)) {
            return;
        }
        queue.eventQueue().ifPresent(onIt -> onIt.storage().insertAt(
                onIt.storage().length() + 1,
                new EventValue(EventCatalogue.typeIndexOf(happened).orElseThrow(),
                        of(), EventValue.Model.PORT, 0, port)));
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


    private static String environmentNameIn(Value asked) {
        return asked instanceof WordValue word
                ? word.spelling()
                : ((StringValue) asked).text();
    }

    private void registerInterpreterState() {
        register(version);
        register(new PokezNative());
        register(new ToRealFileNative(grantedServices));
        register(new RecycleNative());
        register(new StatsNative());
        register(new EchoNative(grantedServices));
        register(new WhetherATerminalNative());
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


    private static final Set<String> SCHEMES_THIS_BUILD_SERVES =
            of("console", "tcp", "dns", "event", "checksum", "file", "dir",
                    "crypt");

    private static void startTheCipherBehindBlankingTheKeyInTheSpec(PortValue port) {
        if (CryptPort.isWorking(port)) {
            throw Raised.of(EvaluationFailure.ALREADY_OPEN,
                    port.fieldValue("spec") instanceof ObjectValue spec
                            ? spec.fieldValue("ref")
                            : NoneValue.none());
        }
        if (!(port.fieldValue("spec") instanceof ObjectValue spec)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, port);
        }
        String algorithm = spec.fieldValue("algorithm") instanceof WordValue word
                ? word.canonical()
                : "";
        if (!CryptPort.serves(algorithm)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, spec);
        }
        CryptPort.start(port, algorithm,
                spec.fieldValue("direction") instanceof WordValue wanted
                        && wanted.canonical().equals("decrypt"),
                octetsInSpec(spec, "key"), octetsInSpec(spec, "init-vector"));
        spec.context().set("key", NoneValue.none());
        spec.context().set("init-vector", NoneValue.none());
    }

    private static byte[] octetsInSpec(ObjectValue spec, String field) {
        return switch (spec.fieldValue(field)) {
            case BinaryValue octets -> octets.octetsFromHere();
            case StringValue text -> text.text()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            default -> new byte[0];
        };
    }


    private void registerStrings() {
        register(new FindScriptNative());
        register(new SplitLinesNative());
        register(new WhetherWildcardNative());
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


    private static Value whatTheClockSays(Set<String> refinements) {
        boolean precise = refinements.contains("precise");
        long asked = refinements.size() - (precise ? 1 : 0);
        if (asked > 1) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "now answers one part of the clock at a time, and was asked for "
                            + asked);
        }
        java.time.ZonedDateTime here = java.time.ZonedDateTime.now();
        if (!precise) {
            here = here.withNano(0);
        }
        if (refinements.contains("utc")) {
            java.time.ZonedDateTime there =
                    here.withZoneSameInstant(java.time.ZoneOffset.UTC);
            return dateWithZone(there, 0);
        }
        int offsetMinutes = here.getOffset().getTotalSeconds() / 60;
        if (refinements.contains("date")) {
            return DateValue.of(here.getYear(), here.getMonthValue(), here.getDayOfMonth());
        }
        if (refinements.contains("time")) {
            return TimeValue.ofNanoseconds(here.toLocalTime().toNanoOfDay());
        }
        if (refinements.contains("zone")) {
            return TimeValue.ofNanoseconds(offsetMinutes * 60L * TimeValue.NANOSECONDS_PER_SECOND);
        }
        if (refinements.contains("weekday")) {
            return IntegerValue.of(here.getDayOfWeek().getValue());
        }
        if (refinements.contains("yearday")) {
            return IntegerValue.of(here.getDayOfYear());
        }
        if (refinements.contains("year")) {
            return IntegerValue.of(here.getYear());
        }
        if (refinements.contains("month")) {
            return IntegerValue.of(here.getMonthValue());
        }
        if (refinements.contains("day")) {
            return IntegerValue.of(here.getDayOfMonth());
        }
        return dateWithZone(here, offsetMinutes);
    }

    private static DateValue dateWithZone(java.time.ZonedDateTime moment, int offsetMinutes) {
        return new DateValue(moment.getYear(), moment.getMonthValue(),
                moment.getDayOfMonth(),
                java.util.Optional.of(
                        TimeValue.ofNanoseconds(moment.toLocalTime().toNanoOfDay())),
                java.util.Optional.of(offsetMinutes));
    }


    private static boolean isExactlyAString(Value value) {
        return value instanceof StringValue && value.datatype() == Datatype.STRING;
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
        define("read", List.of(
                        Parameter.required("source",
                                of(Datatype.FILE, Datatype.PORT, Datatype.URL,
                                        Datatype.BLOCK, Datatype.WORD)),
                        Parameter.belongingTo("part", "length",
                                Typeset.NUMBER.members()),
                        Parameter.belongingTo("seek", "index",
                                Typeset.NUMBER.members())),
                of("part", "seek", "string", "binary", "lines", "all"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof PortValue port) {
                        port.refuseASpecThatIsNotAnObject();
                        return port.isAFile()
                                ? readFromTheFileBehind(
                                        port, evaluator, arguments, refinements)
                                : readFromPort(port, evaluator, arguments, refinements);
                    }
                    Optional<String> behindTheUrl =
                            theFileNamedByAUrl(arguments.getFirst(), evaluator, context);
                    if (behindTheUrl.isEmpty() && routesToAScheme(arguments.getFirst())) {
                        return readFromPort(
                                portOpenedFor(arguments.getFirst(), evaluator, context),
                                evaluator, arguments, refinements);
                    }
                    grantedServices.require(HostService.FILES);
                    return throughPort(() -> FileReading
                            .asAskedForAt(behindTheUrl.orElseGet(() ->
                                    ((StringValue) arguments.getFirst()).text()),
                                    arguments, refinements)
                            .answerThrough(evaluator.files()));
                });

        define("write", List.of(
                        Parameter.required("destination",
                                of(Datatype.FILE, Datatype.PORT, Datatype.URL,
                                        Datatype.BLOCK, Datatype.WORD)),
                        Parameter.required("data"),
                        Parameter.belongingTo("part", "length",
                                Typeset.NUMBER.members()),
                        Parameter.belongingTo("seek", "index",
                                Typeset.NUMBER.members()),
                        Parameter.belongingTo("allow", "access", of(Datatype.BLOCK))),
                of("part", "seek", "append", "allow", "lines", "binary", "all"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof PortValue port) {
                        port.refuseASpecThatIsNotAnObject();
                        return writeToPort(port, arguments.get(1), evaluator,
                                arguments, refinements);
                    }
                    if (routesToAScheme(arguments.getFirst())) {
                        return writeToPort(
                                portOpenedFor(arguments.getFirst(), evaluator, context),
                                arguments.get(1), evaluator, arguments, refinements);
                    }
                    grantedServices.require(HostService.FILES);
                    return throughPort(() -> {
                        FileWriting.asAskedFor(arguments, refinements)
                                .performThrough(evaluator.files());
                        return arguments.getFirst();
                    });
                });

        define("to-local-file", List.of(Parameter.required("path",
                        of(Datatype.FILE, Datatype.STRING))),
                of("full"),
                (arguments, evaluator, context, refinements) -> {
                    String path = ((StringValue) arguments.getFirst()).text();
                    boolean resolvingDots = refinements.contains("full");
                    String from = "";
                    if (resolvingDots && !path.startsWith("/")) {
                        grantedServices.require(HostService.WORKING_DIRECTORY);
                        from = ((StringValue) throughPort(() -> StringValue.of(
                                evaluator.files().workingDirectory()))).text();
                    }
                    return StringValue.of(
                            localPathOf(from + path, resolvingDots, localFileSeparator));
                });

        define("to-rebol-file", List.of(Parameter.required("path",
                        of(Datatype.FILE, Datatype.STRING))),
                (arguments, evaluator, context) -> StringValue.of(
                        oneSlashPerRunOfSeparators(
                                ((StringValue) arguments.getFirst()).text()),
                        Datatype.FILE));

        define("call", List.of(
                        Parameter.required("command",
                                Typeset.ANY_STRING.membersAnd(Datatype.BLOCK)),
                        Parameter.belongingTo("input", "in",
                                of(Datatype.STRING, Datatype.BINARY,
                                        Datatype.FILE, Datatype.NONE)),
                        Parameter.belongingTo("output", "out",
                                of(Datatype.STRING, Datatype.BINARY,
                                        Datatype.FILE, Datatype.NONE)),
                        Parameter.belongingTo("error", "err",
                                of(Datatype.STRING, Datatype.BINARY,
                                        Datatype.FILE, Datatype.NONE))),
                of("wait", "console", "shell", "info", "input", "output", "error"),
                (arguments, evaluator, context, refinements) -> {
                    grantedServices.require(HostService.PROCESSES);
                    ProgramCalling calling = ProgramCalling.asAskedFor(
                            arguments, refinements, evaluator, context);
                    return throughPort(() ->
                            calling.answerThrough(evaluator.processes(), evaluator));
                });



        define("get-env", List.of(Parameter.required("name",
                        of(Datatype.STRING, Datatype.WORD, Datatype.LIT_WORD))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.ENVIRONMENT);
                    return throughPort(() -> {
                        String held = evaluator.environment()
                                .valueOf(environmentNameIn(arguments.getFirst()));
                        return held == null ? NoneValue.none() : StringValue.of(held);
                    });
                });

        define("list-env", List.of(),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.ENVIRONMENT);
                    return throughPort(() -> {
                        List<Value> pairs = new ArrayList<>();
                        evaluator.environment().all().entrySet().stream()
                                .sorted(java.util.Map.Entry.comparingByKey())
                                .forEach(one -> {
                                    pairs.add(StringValue.of(one.getKey()));
                                    pairs.add(StringValue.of(one.getValue()));
                                });
                        return MapValue.of(pairs);
                    });
                });

        define("set-env", List.of(
                        Parameter.required("name",
                                of(Datatype.STRING, Datatype.WORD, Datatype.LIT_WORD)),
                        Parameter.required("value",
                                of(Datatype.STRING, Datatype.NONE))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.ENVIRONMENT);
                    Value given = arguments.get(1);
                    return throughPort(() -> {
                        evaluator.environment().nameHolds(
                                environmentNameIn(arguments.getFirst()),
                                given instanceof StringValue held ? held.text() : null);
                        return given;
                    });
                });

        define("what-dir", List.of(),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.WORKING_DIRECTORY);
                    return throughPort(() -> StringValue.of(
                            evaluator.files().workingDirectory(), Datatype.FILE));
                });

        define("change-dir", List.of(Parameter.required("path", of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.WORKING_DIRECTORY);
                    String asked = ((StringValue) arguments.getFirst()).text();
                    return throughPort(() -> {
                        evaluator.files().changeDirectory(asked);
                        sayWhereTheInterpreterIsStanding(evaluator);
                        return StringValue.of(
                                evaluator.files().workingDirectory(), Datatype.FILE);
                    });
                });


        define("create", List.of(Parameter.required("path",
                        of(Datatype.FILE, Datatype.URL))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.FILES);
                    return throughPort(() -> {
                        String path = ((StringValue) arguments.getFirst()).text();
                        if (path.endsWith("/")) {
                            evaluator.files().makeDirectory(path, false);
                        } else {
                            evaluator.files().write(path, new byte[0]);
                        }
                        return arguments.getFirst();
                    });
                });

        define("delete", List.of(Parameter.required("path",
                        of(Datatype.FILE, Datatype.URL))),
                (arguments, evaluator, context) -> {
                    Value target = arguments.getFirst();
                    Optional<String> behindTheUrl =
                            theFileNamedByAUrl(target, evaluator, context);
                    if (behindTheUrl.isEmpty() && target.datatype() != Datatype.FILE) {
                        throw schemeRefusal("delete", "deletes through", target);
                    }
                    grantedServices.require(HostService.FILES);
                    String path = behindTheUrl.orElseGet(
                            () -> ((StringValue) target).text());
                    Value itsPort = evaluator.applyFunction(
                            context.systemFunctionNamed("make-port*"), List.of(target));
                    try {
                        if (!evaluator.files().delete(path)) {
                            return LogicValue.of(false);
                        }
                    } catch (FilePort.Denied refused) {
                        throw Raised.of(EvaluationFailure.NO_DELETE, target);
                    }
                    return itsPort;
                });

        define("rename", List.of(
                        Parameter.required("from", of(Datatype.FILE, Datatype.BLOCK,
                                Datatype.PORT, Datatype.URL)),
                        Parameter.required("to", of(Datatype.FILE, Datatype.BLOCK,
                                Datatype.PORT, Datatype.URL))),
                (arguments, evaluator, context) -> {
                    for (Value end : List.of(arguments.getFirst(), arguments.get(1))) {
                        if (end.datatype() != Datatype.FILE) {
                            throw schemeRefusal("rename", "renames", end);
                        }
                    }
                    grantedServices.require(HostService.FILES);
                    return movedOrRefusedByTheName(evaluator, arguments);
                });

        define("read-dir", List.of(Parameter.required("path", of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.FILES);
                    return throughPort(() -> BlockValue.block(
                            evaluator.files().namesIn(
                                    ((StringValue) arguments.getFirst()).text()).stream()
                                    .<Value>map(name -> StringValue.of(name, Datatype.FILE))
                                    .toList()));
                });


        define("dir?", List.of(Parameter.required("target",
                        of(Datatype.FILE, Datatype.URL, Datatype.NONE))),
                of("check"),
                (arguments, evaluator, context, refinements) -> {
                    Value target = arguments.getFirst();
                    if (!(target instanceof StringValue address)
                            || address.text().isEmpty()) {
                        return LogicValue.of(false);
                    }
                    if (refinements.contains("check")
                            && target.datatype() == Datatype.FILE
                            && liesOnTheDiskAsADirectory(evaluator, address.text())) {
                        return LogicValue.of(true);
                    }
                    return LogicValue.of(endsTheWayADirectoryIsWritten(address.text()));
                });

        define("set-scheme", List.of(
                        Parameter.required("scheme", of(Datatype.OBJECT))),
                (arguments, evaluator, context) -> {
                    ObjectValue scheme = (ObjectValue) arguments.getFirst();
                    Value given = scheme.context().holds("name")
                            ? scheme.context().ownSlotFor("name").value()
                            : NoneValue.none();
                    if (!(given instanceof WordValue name)
                            || !SCHEMES_THIS_BUILD_SERVES.contains(name.canonical())) {
                        return NoneValue.none();
                    }
                    scheme.context().set("actor", WordValue.of(name.canonical()));
                    return LogicValue.of(true);
                });

        define("port?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.getFirst() instanceof PortValue));

        define("open", List.of(Parameter.required("spec"),
                        Parameter.belongingTo("allow", "access", of(Datatype.BLOCK))),
                of("new", "read", "write", "seek", "allow"),
                (arguments, evaluator, context, refinements) -> {
                    Value built = arguments.getFirst() instanceof PortValue already
                            ? already
                            : evaluator.applyFunction(
                                    context.systemFunctionNamed("make-port*"),
                                    List.of(arguments.getFirst()));
                    if (!(built instanceof PortValue port)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "nothing knows how to open that");
                    }
                    port.refuseAnActorThatIsNeitherAWordNorAnObject();
                    Optional<ObjectValue> written = port.actorWrittenInRebol();
                    if (written.isPresent()) {
                        return evaluator.askTheActor(written.get(), "open",
                                List.of(port), refinements);
                    }
                    requireServiceForScheme(port.schemeName());
                    if (port.schemeName().equals("tcp")) {
                        connectTheTcpPort(port, evaluator);
                    }
                    if (port.schemeName().equals("udp")) {
                        bindTheDatagramPort(port, evaluator);
                    }
                    if (port.schemeName().equals("crypt")) {
                        startTheCipherBehindBlankingTheKeyInTheSpec(port);
                    }
                    if (port.schemeName().equals("checksum")) {
                        checksumPort.startEvenOnAnAlreadyOpenPort(
                                port, checksumPort.methodOf(port));
                    }
                    if (port.isAFile()) {
                        openTheFileBehind(port, evaluator, refinements);
                    }
                    port.eventQueue();
                    markOpenWhateverTheActorLeftInState(port);
                    return port;
                });

        define("update", List.of(Parameter.required("port", of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            evaluator.theRebolActorsAnswer("update", arguments, of());
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    PortValue port = (PortValue) arguments.getFirst();
                    if (port.schemeName().equals("checksum")) {
                        checksumPort.digestSoFarLeftInTheDataFieldAsWell(port);
                        return port;
                    }
                    if (port.schemeName().equals("crypt")) {
                        CryptPort.refuseWhenClosed(port);
                        CryptPort.update(port);
                        return port;
                    }
                    return NoneValue.none();
                });

        define("flush", List.of(Parameter.required("port", of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    evaluator.output().flush();
                    return arguments.getFirst();
                });

        define("open?", List.of(Parameter.required("port", of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            evaluator.theRebolActorsAnswer("open?", arguments, of());
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    PortValue port = (PortValue) arguments.getFirst();
                    if (port.schemeName().equals("crypt")) {
                        CryptPort.refuseWhenClosed(port);
                    }
                    return LogicValue.of(port.isOpen());
                });

        define("close", List.of(Parameter.required("port", of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            evaluator.theRebolActorsAnswer("close", arguments, of());
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    PortValue port = (PortValue) arguments.getFirst();
                    if (port.schemeName().equals("crypt")) {
                        CryptPort.refuseWhenClosed(port);
                        CryptPort.stop(port);
                    }
                    handBackTheConnectionBehind(port);
                    port.markOpen(false);
                    if (port.schemeName().equals("checksum")) {
                        checksumPort.stop(port);
                    }
                    return port;
                });

        define("modify", List.of(
                        Parameter.required("target", of(Datatype.PORT, Datatype.FILE)),
                        Parameter.required("field", of(Datatype.WORD, Datatype.NONE)),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            evaluator.theRebolActorsAnswer("modify", arguments, of());
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    if (arguments.getFirst() instanceof PortValue aPort
                            && aPort.schemeName().equals("crypt")) {
                        CryptPort.refuseWhenClosed(aPort);
                        if (!(arguments.get(1) instanceof WordValue setting)) {
                            return aPort;
                        }
                        return CryptPort.modify(aPort, setting.canonical(),
                                arguments.get(2));
                    }
                    Value asked = arguments.get(1);
                    if (!(asked instanceof WordValue mode)
                            || !consoleModes().contains(mode.canonical())) {
                        throw Raised.of(EvaluationFailure.BAD_FILE_MODE, asked);
                    }
                    if (!(arguments.get(2) instanceof LogicValue)) {
                        throw Raised.of(EvaluationFailure.INVALID_VALUE_FOR,
                                arguments.get(2), mode);
                    }
                    if (arguments.getFirst() instanceof PortValue port) {
                        port.setField(mode.canonical(), arguments.get(2));
                    }
                    return arguments.get(2);
                });

        define("browse", List.of(Parameter.required("url",
                        of(Datatype.URL, Datatype.FILE, Datatype.NONE))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.WINDOWS);
                    return throughWindow(() -> {
                        if (!(arguments.getFirst() instanceof StringValue target)) {
                            return NoneValue.none();
                        }
                        evaluator.windows().browse(target.text());
                        return NoneValue.none();
                    });
                });

        define("request-file", List.of(
                        Parameter.belongingTo("file", "name", of(Datatype.FILE)),
                        Parameter.belongingTo("title", "text", of(Datatype.STRING)),
                        Parameter.belongingTo("filter", "list", of(Datatype.BLOCK))),
                of("save", "multi", "file", "title", "filter"),
                (arguments, evaluator, context, refinements) -> {
                    grantedServices.require(HostService.WINDOWS);
                    List<String> filters = filterPairsIn(arguments, refinements);
                    return throughWindow(() -> {
                        List<String> chosen = evaluator.windows().chooseFiles(
                                refinements.contains("save"),
                                refinements.contains("multi"),
                                textOfArgument(arguments, refinements,
                                        List.of("file", "title", "filter"), "file"),
                                textOfArgument(arguments, refinements,
                                        List.of("file", "title", "filter"), "title"),
                                filters);
                        if (refinements.contains("multi")) {
                            return BlockValue.block(chosen.stream()
                                    .<Value>map(one -> StringValue.of(one, Datatype.FILE))
                                    .toList());
                        }
                        return chosen.isEmpty()
                                ? NoneValue.none()
                                : StringValue.of(chosen.getFirst(), Datatype.FILE);
                    });
                });

        define("request-dir", List.of(
                        Parameter.belongingTo("title", "text", of(Datatype.STRING)),
                        Parameter.belongingTo("dir", "name", of(Datatype.FILE))),
                of("title", "dir", "keep"),
                (arguments, evaluator, context, refinements) -> {
                    grantedServices.require(HostService.WINDOWS);
                    return throughWindow(() -> evaluator.windows().chooseDirectory(
                                    textOfArgument(arguments, refinements,
                                            List.of("title", "dir"), "dir"),
                                    textOfArgument(arguments, refinements,
                                            List.of("title", "dir"), "title"))
                            .<Value>map(where -> StringValue.of(where, Datatype.FILE))
                            .orElseGet(NoneValue::none));
                });

        define("request-color", List.of(
                        Parameter.belongingTo("default", "color", of(Datatype.TUPLE))),
                of("default"),
                (arguments, evaluator, context, refinements) -> {
                    grantedServices.require(HostService.WINDOWS);
                    return throughWindow(() -> {
                        Optional<int[]> suggested = refinements.contains("default")
                                        && arguments.getFirst() instanceof TupleValue given
                                ? Optional.of(given.segments())
                                : Optional.empty();
                        return evaluator.windows().chooseColour(suggested)
                                .<Value>map(TupleValue::of)
                                .orElseGet(NoneValue::none);
                    });
                });

        define("request-password", List.of(),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.WINDOWS);
                    return throughWindow(() -> evaluator.windows()
                            .askForPassword()
                            .<Value>map(StringValue::of)
                            .orElseGet(NoneValue::none));
                });

        define("query", List.of(
                        Parameter.required("target", of(Datatype.FILE, Datatype.DATE,
                                Datatype.HANDLE, Datatype.PORT, Datatype.URL,
                                Datatype.BLOCK, Datatype.WORD, Datatype.VECTOR)),
                        Parameter.required("field",
                                of(Datatype.WORD, Datatype.BLOCK,
                                        Datatype.NONE, Datatype.DATATYPE))),
                of("mode"),
                (arguments, evaluator, context, refinements) -> {
                    Optional<Value> itsOwn = evaluator.theRebolActorsAnswer(
                            "query", arguments, refinements);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    Value target = arguments.getFirst();
                    Value field = arguments.get(1);
                    if (target instanceof VectorValue vector) {
                        return queriedVector(vector, field, evaluator);
                    }
                    if (target instanceof DateValue date) {
                        return questionedByField(field, evaluator,
                                DatePart.partNames(),
                                part -> DatePart.readFrom(date, WordValue.of(part)));
                    }
                    if (target instanceof HandleValue handle) {
                        return questionedByField(field, evaluator,
                                List.of("type"),
                                part -> WordValue.of(handle.typeName()));
                    }
                    if (target instanceof PortValue console
                            && console.schemeName().equals("console")) {
                        if (field instanceof WordValue asked
                                && !asked.canonical().equals("words")
                                && !CONSOLE_MEASUREMENTS.contains(asked.canonical())) {
                            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
                        }
                        return questionedByField(field, evaluator,
                                CONSOLE_MEASUREMENTS,
                                part -> IntegerValue.of(measureOfTheConsole(part)));
                    }
                    if (field instanceof NoneValue) {
                        return theNamesThatPortMayBeAskedFor(target, evaluator, context);
                    }
                    if (target instanceof PortValue openFile && openFile.isAFile()) {
                        grantedServices.require(HostService.FILES);
                        return throughPort(() -> queryAnswerFor(
                                evaluator.files().informationAbout(
                                        SeekableFilePort.pathOf(openFile)),
                                field, evaluator));
                    }
                    Optional<String> behindTheUrl =
                            theFileNamedByAUrl(target, evaluator, context);
                    if (behindTheUrl.isEmpty()
                            && (target instanceof PortValue || routesToAScheme(target))) {
                        throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                                WordValue.of("query").as(Datatype.SET_WORD));
                    }
                    grantedServices.require(HostService.FILES);
                    String path = behindTheUrl.orElseGet(
                            () -> ((StringValue) target).text());
                    if (path.isEmpty()) {
                        return NoneValue.none();
                    }
                    return throughPort(() -> queryAnswerFor(
                            evaluator.files().informationAbout(path), field,
                            evaluator));
                });
    }

    private static Value queryAnswerFor(
            java.util.Optional<FileInformation> found, Value field,
            Evaluator evaluator) {

        if (found.isEmpty()) {
            return NoneValue.none();
        }
        FileInformation about = found.get();
        if (field instanceof BlockValue wanted) {
            List<Value> answer = new ArrayList<>();
            for (Value item : wanted.remaining()) {
                if (!(item instanceof WordValue asked)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG,
                            "a query field is a word, not "
                                    + item.datatype().literalSpelling());
                }
                if (asked.datatype() != Datatype.GET_WORD) {
                    answer.add(asked.as(Datatype.SET_WORD));
                }
                answer.add(queryFieldOf(about, asked));
            }
            return BlockValue.block(answer);
        }
        if (field instanceof WordValue asked) {
            return queryFieldOf(about, asked);
        }
        return everythingKnownAbout(about);
    }

    private static Value queryFieldOf(FileInformation about, WordValue asked) {
        return switch (asked.canonical()) {
            case "size" -> about.size().<Value>map(IntegerValue::of).orElseGet(NoneValue::none);
            case "type" -> WordValue.of(about.isDirectory() ? "dir" : "file");
            case "date", "modified" -> asDateValue(about.modified());
            case "accessed" -> asDateValue(about.accessed());
            case "created" -> asDateValue(about.created());
            case "name" -> StringValue.of(about.name(), Datatype.FILE);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    asked.spelling() + " is not a field a file has");
        };
    }

    private Value theNamesThatPortMayBeAskedFor(
            Value target, Evaluator evaluator, Context context) {

        Value built = target instanceof PortValue already
                ? already
                : evaluator.applyFunction(
                        context.systemFunctionNamed("make-port*"), List.of(target));
        Value described = built instanceof PortValue(Context context1)
                ? context1.valueAt("scheme", "info")
                : NoneValue.none();
        if (!(described instanceof ObjectValue(Context context1))) {
            return BlockValue.block(List.of());
        }
        return BlockValue.block(context1.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .<Value>map(slot -> WordValue.of(slot.spelling()))
                .toList());
    }

    private static Value everythingKnownAbout(FileInformation about) {
        Context fields = Context.root();
        fields.set("name", StringValue.of(about.name(), Datatype.FILE));
        fields.set("size", about.size().<Value>map(IntegerValue::of).orElseGet(NoneValue::none));
        fields.set("type", WordValue.of(about.isDirectory() ? "dir" : "file"));
        fields.set("date", asDateValue(about.modified()));
        fields.set("modified", asDateValue(about.modified()));
        fields.set("accessed", asDateValue(about.accessed()));
        fields.set("created", asDateValue(about.created()));
        return new ObjectValue(fields);
    }

    private static Value asDateValue(java.util.Optional<java.time.Instant> moment) {
        return moment.<Value>map(when -> {
            java.time.LocalDateTime local = java.time.LocalDateTime.ofInstant(
                    when, java.time.ZoneOffset.UTC);
            return DateValue.of(local.getYear(), local.getMonthValue(), local.getDayOfMonth(),
                    TimeValue.of(local.getHour(), local.getMinute(), local.getSecond(), 0));
        }).orElseGet(NoneValue::none);
    }

    private record ProgramCalling(
            List<String> command,
            boolean readByTheShell,
            boolean attachedToTheHostsConsole,
            boolean waits,
            boolean answersAnObject,
            Optional<Value> input,
            Optional<Value> output,
            Optional<Value> errors) {

        private static final List<String> ARGUMENT_ORDER =
                List.of("input", "output", "error");

        static ProgramCalling asAskedFor(
                List<Value> arguments, Set<String> refinements,
                Evaluator evaluator, Context context) {

            Optional<Value> input = redirection("input", arguments, refinements);
            Optional<Value> output = redirection("output", arguments, refinements);
            Optional<Value> errors = redirection("error", arguments, refinements);
            boolean aSeriesIsAtOneEnd =
                    isASeries(input) || isASeries(output) || isASeries(errors);
            return new ProgramCalling(
                    commandWordsOf(arguments.getFirst(), evaluator, context),
                    refinements.contains("shell"),
                    refinements.contains("console"),
                    refinements.contains("wait") || aSeriesIsAtOneEnd,
                    refinements.contains("info"),
                    input, output, errors);
        }

        private static Optional<Value> redirection(
                String refinement, List<Value> arguments, Set<String> refinements) {
            return Optional.ofNullable(
                    argumentFor(refinement, ARGUMENT_ORDER, arguments, refinements, 1));
        }

        private static boolean isASeries(Optional<Value> redirection) {
            return redirection
                    .filter(value -> value instanceof BinaryValue
                            || value.datatype() == Datatype.STRING)
                    .isPresent();
        }

        private static List<String> commandWordsOf(
                Value command, Evaluator evaluator, Context context) {
            if (!(command instanceof BlockValue block)
                    || command.datatype() != Datatype.BLOCK) {
                return List.of(((StringValue) command).text());
            }
            List<Value> items = block.remaining();
            if (items.isEmpty()) {
                throw Raised.of(EvaluationFailure.TOO_SHORT,
                        "a command needs at least the program's name");
            }
            return items.stream()
                    .map(item -> commandWordOf(item, evaluator, context))
                    .toList();
        }

        private static String commandWordOf(
                Value item, Evaluator evaluator, Context context) {
            Value resolved = item;
            if (item instanceof WordValue word
                    && word.datatype() == Datatype.GET_WORD) {
                resolved = word.boundSlot().value();
            } else if (item instanceof BlockValue path
                    && path.datatype() == Datatype.GET_PATH) {
                resolved = evaluator.evaluateOrRaise(
                        BlockValue.block(List.of(path)), context);
            }
            return switch (resolved) {
                case StringValue text -> text.text();
                case WordValue word when word.datatype() == Datatype.WORD ->
                        word.spelling();
                default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                        Molder.mold(resolved) + " names nothing a command line can hold");
            };
        }

        Value answerThrough(ProcessPort port, Evaluator evaluator) {
            ProcessPort.ProgramResult result = port.run(toStart(evaluator));
            output.ifPresent(buffer -> result.capturedOutput()
                    .ifPresent(bytes -> appendedInto(buffer, bytes)));
            errors.ifPresent(buffer -> result.capturedError()
                    .ifPresent(bytes -> appendedInto(buffer, bytes)));
            if (answersAnObject) {
                return informationObject(result, evaluator);
            }
            if (result.refusalMessage().isPresent()) {
                throw Raised.of(EvaluationFailure.CALL_FAIL,
                        result.refusalMessage().orElseThrow());
            }
            if (waits && result.exitCode().isEmpty()) {
                throw Raised.of(EvaluationFailure.CALL_FAIL,
                        "the host waited for the program and answered no exit code");
            }
            return IntegerValue.of(waits
                    ? result.exitCode().orElseThrow()
                    : result.processNumber());
        }

        private ProcessPort.ProgramToStart toStart(Evaluator evaluator) {
            return new ProcessPort.ProgramToStart(
                    command, readByTheShell, attachedToTheHostsConsole, waits,
                    inputKindOf(input), pipedBytesOf(input), fileOf(input, evaluator),
                    outputKindOf(output), fileOf(output, evaluator),
                    outputKindOf(errors), fileOf(errors, evaluator),
                    whatTheChildInherits(evaluator),
                    whereTheChildStarts(evaluator));
        }

        private static Optional<String> whereTheChildStarts(Evaluator evaluator) {
            try {
                return Optional.of(evaluator.files()
                        .hostPathOf(evaluator.files().workingDirectory()));
            } catch (FilePort.Denied noFilesystem) {
                return Optional.empty();
            }
        }

        private static java.util.Map<String, String> whatTheChildInherits(
                Evaluator evaluator) {

            try {
                return evaluator.environment().all();
            } catch (FilePort.Denied noEnvironment) {
                return java.util.Map.of();
            }
        }

        private static ProcessPort.ProgramInput inputKindOf(Optional<Value> redirection) {
            if (redirection.isEmpty()) {
                return ProcessPort.ProgramInput.THE_HOSTS_OWN;
            }
            return switch (redirection.orElseThrow()) {
                case BinaryValue piped -> ProcessPort.ProgramInput.SUPPLIED_BYTES;
                case StringValue text when text.datatype() == Datatype.STRING ->
                        ProcessPort.ProgramInput.SUPPLIED_BYTES;
                case StringValue address -> ProcessPort.ProgramInput.A_FILES_CONTENTS;
                default -> ProcessPort.ProgramInput.NOTHING_AT_ALL;
            };
        }

        private static ProcessPort.ProgramOutput outputKindOf(Optional<Value> redirection) {
            if (redirection.isEmpty()) {
                return ProcessPort.ProgramOutput.THE_HOSTS_OWN;
            }
            return switch (redirection.orElseThrow()) {
                case BinaryValue captured -> ProcessPort.ProgramOutput.CAPTURED;
                case StringValue text when text.datatype() == Datatype.STRING ->
                        ProcessPort.ProgramOutput.CAPTURED;
                case StringValue address -> ProcessPort.ProgramOutput.INTO_A_FILE;
                default -> ProcessPort.ProgramOutput.DISCARDED;
            };
        }

        private static Optional<byte[]> pipedBytesOf(Optional<Value> redirection) {
            return redirection.map(value -> switch (value) {
                case BinaryValue binary -> binary.octetsFromHere();
                case StringValue text when text.datatype() == Datatype.STRING ->
                        text.text().getBytes(StandardCharsets.UTF_8);
                default -> null;
            });
        }

        private static Optional<String> fileOf(
                Optional<Value> redirection, Evaluator evaluator) {
            return redirection
                    .filter(value -> value.datatype() == Datatype.FILE)
                    .map(value -> whereReadWouldResolveIt(
                            ((StringValue) value).text(), evaluator));
        }

        private static String whereReadWouldResolveIt(String path, Evaluator evaluator) {
            try {
                return evaluator.files().hostPathOf(path);
            } catch (FilePort.Denied noDirectoryToAsk) {
                return path;
            }
        }

        private static void appendedInto(Value buffer, byte[] bytes) {
            switch (buffer) {
                case BinaryValue binary -> {
                    for (byte octet : bytes) {
                        binary.storage().append(octet & 0xFF);
                    }
                }
                case StringValue text when text.datatype() == Datatype.STRING ->
                        new String(bytes, StandardCharsets.UTF_8)
                                .codePoints().forEach(text.storage()::append);
                default -> { }
            }
        }

        private Value informationObject(
                ProcessPort.ProgramResult result, Evaluator evaluator) {
            Context fields = Context.childOf(evaluator.systemContext());
            ObjectValue built = new ObjectValue(fields);
            fields.set("self", built);
            fields.set("id", IntegerValue.of(result.processNumber()));
            if (waits && result.exitCode().isPresent()) {
                fields.set("exit-code",
                        IntegerValue.of(result.exitCode().orElseThrow()));
            }
            result.refusalMessage().ifPresent(message ->
                    fields.set("error", StringValue.of(message)));
            return built;
        }
    }

    private Value whatTheOperatorLastCopied(PortValue port, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {

        grantedServices.require(HostService.CLIPBOARD);
        String copied = theClipboardsAnswer(evaluator);
        if (refinements.contains("part") && arguments.size() > 1
                && arguments.get(1) instanceof IntegerValue(long wanted)) {
            copied = copied.substring(0, (int) Math.min(
                    Math.max(wanted, 0), copied.length()));
        }
        port.setField("data", StringValue.of(copied));
        if (refinements.contains("lines")) {
            return BlockValue.block(copied.lines()
                    .<Value>map(StringValue::of).toList());
        }
        return StringValue.of(copied);
    }

    private static String theClipboardsAnswer(Evaluator evaluator) {
        try {
            return evaluator.clipboard().read();
        } catch (ClipboardPort.Unreachable unreachable) {
            throw Raised.of(EvaluationFailure.READ_ERROR, unreachable.getMessage());
        }
    }

    private Value readFromPort(PortValue port, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {

        Optional<Value> itsOwn = evaluator.theRebolActorsAnswer(
                "read", withThePortInFront(port, arguments), refinements);
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> lineReadFromTheConsole(evaluator);
            case "clipboard" ->
                    whatTheOperatorLastCopied(port, evaluator, arguments, refinements);
            case "bundled" -> theSourceOfABundledModule(port, evaluator);
            case "tcp" -> bytesReadFromTheConnection(port, evaluator);
            case "udp" -> oneDatagramReadInto(port, evaluator);
            case "dns" -> addressesOfTheNameThePortNames(port, evaluator);
            case "checksum" ->
                    checksumPort.digestSoFarLeftInTheDataFieldAsWell(port);
            case "crypt" -> {
                CryptPort.refuseWhenClosed(port);
                yield CryptPort.read(port);
            }
            default -> throw Raised.of(EvaluationFailure.NO_SERVICE,
                    "nothing here reads the " + port.schemeName() + " scheme");
        };
    }

    private Value lineReadFromTheConsole(Evaluator evaluator) {
        grantedServices.require(HostService.CONSOLE);
        return throughPort(() -> {
            String line = evaluator.console().readLine();
            return line == null ? NoneValue.none() : StringValue.of(line);
        });
    }

    private Value theSourceOfABundledModule(PortValue port, Evaluator evaluator) {
        return theNameABundledUrlAsksFor(port)
                .flatMap(name -> evaluator.bundledModules().sourceOf(name))
                .map(source -> (Value) BinaryValue.ofBytes(source))
                .orElseThrow(() -> Raised.of(EvaluationFailure.CANNOT_OPEN,
                        port.fieldValue("spec") instanceof ObjectValue spec
                                ? spec.fieldValue("ref")
                                : NoneValue.none()));
    }

    private static Optional<String> theNameABundledUrlAsksFor(PortValue port) {
        if (!(port.fieldValue("spec") instanceof ObjectValue spec)
                || !(spec.fieldValue("host") instanceof StringValue host)
                || !(spec.fieldValue("path") instanceof NoneValue)
                || !(spec.fieldValue("target") instanceof NoneValue)) {
            return Optional.empty();
        }
        return host.text().isEmpty() ? Optional.empty() : Optional.of(host.text());
    }

    private Value bytesReadFromTheConnection(PortValue port, Evaluator evaluator) {
        grantedServices.require(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughNetwork(() -> {
            BinaryValue arrived = BinaryValue.ofBytes(connection.read());
            addToThePortsData(port, arrived);
            queueWhatHappenedTo(port,
                    arrived.lengthFromHere() == 0 ? "close" : "read", evaluator);
            return arrived;
        });
    }

    private static void addToThePortsData(PortValue port, BinaryValue arrived) {
        if (!(port.fieldValue("data") instanceof BinaryValue held)) {
            port.setField("data", arrived);
            return;
        }
        for (int at = arrived.index(); at <= arrived.storageLength(); at++) {
            held.storage().append(arrived.storage().at(at));
        }
    }

    private Value addressesOfTheNameThePortNames(PortValue port, Evaluator evaluator) {
        grantedServices.require(HostService.NETWORK);
        String hostName = hostNamedBy(port);
        return throughNetwork(() -> {
            List<String> found = evaluator.network().addressesFor(hostName);
            return found.isEmpty()
                    ? NoneValue.none()
                    : BlockValue.block(found.stream()
                            .<Value>map(StringValue::of).toList());
        });
    }


    private static String oneSlashPerRunOfSeparators(String path) {
        StringBuilder built = new StringBuilder(path.length());
        boolean afterASeparator = false;
        for (int at = 0; at < path.length(); at++) {
            char letter = path.charAt(at);
            if (letter != '/' && letter != '\\') {
                built.append(letter);
                afterASeparator = false;
            } else if (!afterASeparator) {
                built.append('/');
                afterASeparator = true;
            }
        }
        return built.toString();
    }

    private static String localPathOf(String path, boolean resolvingDots, char separator) {
        StringBuilder built = new StringBuilder();
        int at = 0;
        while (at < path.length()) {
            if (resolvingDots) {
                at = pastAnyDots(path, at, built, separator);
            }
            while (at < path.length()) {
                char letter = path.charAt(at);
                at++;
                if (letter == '/') {
                    if (built.isEmpty()
                            || built.charAt(built.length() - 1) != separator) {
                        built.append(separator);
                    }
                    break;
                }
                built.append(letter);
            }
        }
        return built.toString();
    }

    private static int pastAnyDots(
            String path, int at, StringBuilder built, char separator) {
        if (at >= path.length() || path.charAt(at) != '.') {
            return at;
        }
        boolean twoDots = at + 1 < path.length() && path.charAt(at + 1) == '.';
        int after = at + (twoDots ? 2 : 1);
        boolean wholeSegment = after >= path.length() || path.charAt(after) == '/';
        if (!wholeSegment) {
            return at;
        }
        if (twoDots) {
            backOutOneDirectory(built, separator);
        }
        return after;
    }

    private static void backOutOneDirectory(StringBuilder built, char separator) {
        int length = built.length() > 2 ? built.length() - 2 : 0;
        while (length > 0 && built.charAt(length) != separator) {
            length--;
        }
        built.setLength(length);
        built.append(separator);
    }

    private Value queriedVector(VectorValue vector, Value field, Evaluator evaluator) {
        if (field instanceof NoneValue) {
            return BlockValue.block(
                    VectorQuery.FIELDS.stream().<Value>map(WordValue::of).toList());
        }
        if (field instanceof WordValue only) {
            return VectorQuery.field(vector, only.canonical())
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG, only));
        }
        if (field instanceof BlockValue asked) {
            List<Value> answer = new ArrayList<>();
            for (Value item : asked.remaining()) {
                if (!(item instanceof WordValue each)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, item);
                }
                if (each.datatype() != Datatype.GET_WORD) {
                    answer.add(each.as(Datatype.SET_WORD));
                }
                answer.add(VectorQuery.field(vector, each.canonical()).orElseThrow(
                        () -> Raised.of(EvaluationFailure.INVALID_ARG, each)));
            }
            return BlockValue.block(answer);
        }
        Context fields = Context.childOf(evaluator.systemContext());
        ObjectValue described = new ObjectValue(fields);
        fields.set("self", described);
        for (String name : VectorQuery.FIELDS) {
            fields.set(name, VectorQuery.field(vector, name).orElseGet(NoneValue::none));
        }
        return described;
    }

    private Value questionedByField(
            Value field, Evaluator evaluator, List<String> partNames,
            Function<String, Value> partOf) {
        return switch (field) {
            case WordValue only when only.canonical().equals("words") ->
                    namesAsWords(partNames);
            case WordValue only -> oneKnownPart(only, partNames, partOf);
            case BlockValue asked -> {
                List<Value> answer = new ArrayList<>();
                for (Value item : asked.remaining()) {
                    if (!(item instanceof WordValue each)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                Molder.mold(item) + " names no part");
                    }
                    if (each.datatype() != Datatype.GET_WORD) {
                        answer.add(each.as(Datatype.SET_WORD));
                    }
                    answer.add(oneKnownPart(each, partNames, partOf));
                }
                yield BlockValue.block(answer);
            }
            case NoneValue names -> namesAsWords(partNames);
            default -> {
                Context fields = Context.childOf(evaluator.systemContext());
                ObjectValue built = new ObjectValue(fields);
                fields.set("self", built);
                for (String part : partNames) {
                    fields.set(part, partOf.apply(part));
                }
                yield built;
            }
        };
    }

    private static Value namesAsWords(List<String> partNames) {
        return BlockValue.block(
                partNames.stream().<Value>map(WordValue::of).toList());
    }

    private static Value oneKnownPart(WordValue asked, List<String> partNames,
            Function<String, Value> partOf) {
        if (!partNames.contains(asked.canonical())) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "query has no " + asked.canonical() + " to answer here");
        }
        return partOf.apply(asked.canonical());
    }

    private static boolean routesToAScheme(Value source) {
        return source.datatype() == Datatype.URL
                || source.datatype() == Datatype.BLOCK
                || source instanceof WordValue;
    }

    private PortValue portOpenedFor(Value address, Evaluator evaluator, Context context) {
        Value built = evaluator.applyFunction(
                context.systemFunctionNamed("make-port*"), List.of(address));
        if (!(built instanceof PortValue port)) {
            throw schemeRefusal("write", "writes", address);
        }
        if (port.actorWrittenInRebol().isPresent()) {
            return port;
        }
        requireServiceForScheme(port.schemeName());
        port.markOpen(true);
        if (port.schemeName().equals("checksum")) {
            checksumPort.startEvenOnAnAlreadyOpenPort(
                    port, checksumPort.methodOf(port));
        }
        return port;
    }

    private Optional<String> theFileNamedByAUrl(
            Value target, Evaluator evaluator, Context context) {

        if (target.datatype() != Datatype.URL) {
            return Optional.empty();
        }
        PortValue routed = portOpenedFor(target, evaluator, context);
        return routed.isAFile()
                ? Optional.of(SeekableFilePort.pathOf(routed))
                : Optional.empty();
    }

    private static final Set<String> THE_SCHEMES_THIS_BUILD_SERVES_ITSELF = of(
            "console", "clipboard", "file", "dir", "tcp", "dns", "event",
            "system", "callback", "bundled", "checksum", "crypt");

    private static Raised schemeRefusal(String verb, String verbs, Value routed) {
        if (THE_SCHEMES_THIS_BUILD_SERVES_ITSELF.contains(theSchemeWordOf(routed))) {
            return Raised.of(EvaluationFailure.NO_PORT_ACTION,
                    WordValue.of(verb).as(Datatype.SET_WORD));
        }
        return Raised.of(EvaluationFailure.NO_SERVICE,
                "nothing here " + verbs + " " + schemeNameOf(routed));
    }

    private static String theSchemeWordOf(Value routed) {
        return switch (routed) {
            case WordValue word -> word.canonical();
            case PortValue port -> port.schemeName();
            case StringValue written -> written.text().split(":", 2)[0];
            default -> "";
        };
    }

    private static String schemeNameOf(Value routed) {
        return switch (routed) {
            case WordValue word -> "the " + word.canonical() + " scheme";
            case BlockValue specification -> Molder.mold(specification);
            case PortValue port -> "the " + port.schemeName() + " scheme";
            default -> "the " + ((StringValue) routed).text().split(":", 2)[0]
                    + " scheme";
        };
    }

    private Value encipheredIntoThePort(PortValue port, Value data) {
        CryptPort.refuseWhenClosed(port);
        if (!(data instanceof BinaryValue octets)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    port.fieldValue("spec") instanceof ObjectValue spec
                            ? spec.fieldValue("ref")
                            : NoneValue.none());
        }
        CryptPort.write(port, octets.octetsFromHere());
        return port;
    }

    private Value writeToPort(PortValue port, Value data, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {
        Optional<Value> itsOwn = evaluator.theRebolActorsAnswer(
                "write", List.of(port, data), refinements);
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> writtenToTheConsole(port, data, evaluator);
            case "clipboard" -> putOnTheClipboard(port, data, evaluator);
            case "tcp" -> sentDownTheConnection(port, data, evaluator);
            case "udp" -> oneDatagramSentFrom(port, data, evaluator);
            case "checksum" -> summedIntoThePort(port, data, arguments, refinements);
            case "crypt" -> encipheredIntoThePort(port, data);
            case "file" -> new OpenFile(port, evaluator.files(), grantedServices).written(data,
                    Optional.ofNullable(argumentFor("seek", FileWriting.ARGUMENT_ORDER,
                            arguments, refinements, 2)),
                    Optional.ofNullable(argumentFor("part", FileWriting.ARGUMENT_ORDER,
                            arguments, refinements, 2)),
                    refinements.contains("append"));
            default -> throw schemeRefusal("write", "writes", port);
        };
    }

    private Value putOnTheClipboard(PortValue port, Value data, Evaluator evaluator) {
        grantedServices.require(HostService.CLIPBOARD);
        String text = switch (data) {
            case StringValue written -> written.text();
            case BinaryValue bytes -> new String(
                    bytes.octetsFromHere(), StandardCharsets.UTF_8);
            default -> throw Raised.of(EvaluationFailure.INVALID_PORT_ARG, data);
        };
        try {
            evaluator.clipboard().write(text);
        } catch (ClipboardPort.Unreachable unreachable) {
            throw Raised.of(EvaluationFailure.WRITE_ERROR, unreachable.getMessage());
        }
        return port;
    }

    private Value summedIntoThePort(PortValue port, Value data,
            List<Value> arguments, Set<String> refinements) {
        if (!(data instanceof BinaryValue || data instanceof StringValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(data));
        }
        if (!port.isOpen()) {
            port.markOpen(true);
            checksumPort.startEvenOnAnAlreadyOpenPort(
                    port, checksumPort.methodOf(port));
        }
        RebolSeries written = (RebolSeries) data;
        byte[] whole = written.head().asOctets();
        long from = checksumPort.seekedFrom(whole, written.index() - 1,
                wholeNumberAsked("seek", arguments, refinements).orElse(0L));
        wholeNumberAsked("part", arguments, refinements).ifPresentOrElse(
                wanted -> checksumPort.addPart(port, whole, from, wanted),
                () -> checksumPort.add(port, whole, from));
        return port;
    }

    private static final List<String> WRITE_OPTIONAL_ARGUMENTS =
            List.of("part", "seek", "allow");

    private Optional<Long> wholeNumberAsked(
            String refinement, List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains(refinement)) {
            return Optional.empty();
        }
        Value asked = argumentFor(refinement, WRITE_OPTIONAL_ARGUMENTS,
                arguments, refinements, 2);
        return Comparison.isNumeric(asked)
                ? Optional.of((long) Comparison.asDouble(asked))
                : Optional.empty();
    }

    private Value writtenToTheConsole(
            PortValue port, Value data, Evaluator evaluator) {
        grantedServices.require(HostService.CONSOLE);
        evaluator.output().write(Molder.form(data));
        return port;
    }

    private Value sentDownTheConnection(
            PortValue port, Value data, Evaluator evaluator) {

        grantedServices.require(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughNetwork(() -> {
            connection.write(data.asOctets());
            queueWhatHappenedTo(port, "wrote", evaluator);
            return port;
        });
    }


    private static List<Value> withThePortInFront(
            PortValue port, List<Value> arguments) {

        List<Value> asTheActorTakesThem = new ArrayList<>(arguments);
        asTheActorTakesThem.set(0, port);
        return asTheActorTakesThem;
    }

    private void requireServiceForScheme(String scheme) {
        switch (scheme) {
            case "console" -> theSchemeReachesNothingOutside();
            case "file", "dir" -> grantedServices.require(HostService.FILES);
            case "tcp", "dns", "udp" -> grantedServices.require(HostService.NETWORK);
            case "event" -> grantedServices.require(HostService.WINDOWS);
            case "clipboard" -> grantedServices.require(HostService.CLIPBOARD);
            case "system", "callback", "bundled" -> theSchemeReachesNothingOutside();
            case "checksum", "crypt" -> theSchemeReachesNothingOutside();
            default -> {
                throw Raised.of(EvaluationFailure.NO_SERVICE,
                        scheme.isEmpty()
                                ? "that port has no scheme"
                                : "nothing here serves the " + scheme + " scheme");
            }
        }
    }

    private static void theSchemeReachesNothingOutside() {
    }

    private record FileReading(
            String path,
            Optional<Long> bound,
            Optional<Long> position,
            boolean answersText,
            boolean answersLines) {

        private static final List<String> ARGUMENT_ORDER = List.of("part", "seek");

        static FileReading asAskedForAt(
                String path, List<Value> arguments, Set<String> refinements) {

            return new FileReading(
                    path,
                    numberFor("part", arguments, refinements),
                    refusingANegative(numberFor("seek", arguments, refinements)),
                    refinements.contains("string"),
                    refinements.contains("lines"));
        }

        private static Optional<Long> numberFor(
                String refinement, List<Value> arguments, Set<String> refinements) {
            Value asked = argumentFor(refinement, ARGUMENT_ORDER, arguments, refinements, 1);
            return asked == null
                    ? Optional.empty()
                    : Optional.of((long) Arithmetic.asMagnitude(asked));
        }

        private static Optional<Long> refusingANegative(Optional<Long> asked) {
            if (asked.isPresent() && asked.orElseThrow() < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                        "a read cannot start before the beginning, and "
                                + asked.orElseThrow() + " is before it");
            }
            return asked;
        }

        Value answerThrough(FilePort files) {
            if (holdsAWildcard(path)) {
                return namesMatchingThePattern(files);
            }
            if (path.endsWith("/") || files.isDirectory(path)) {
                return namesWithin(files);
            }
            byte[] chosen = theBytesAskedFor(files.readBytes(path));
            if (answersLines || answersText) {
                Optional<String> text = decodedUtfText(chosen);
                if (text.isPresent()) {
                    return answersLines
                            ? linesOf(text.orElseThrow())
                            : StringValue.of(text.orElseThrow());
                }
            }
            return new BinaryValue(new BinaryStorage(chosen), 1);
        }

        private Value namesWithin(FilePort files) {
            return BlockValue.block(files.namesIn(path).stream()
                    .<Value>map(name -> StringValue.of(name, Datatype.FILE))
                    .toList());
        }

        private Value namesMatchingThePattern(FilePort files) {
            int lastSeparator = path.lastIndexOf('/');
            String directory = path.substring(0, lastSeparator + 1);
            String pattern = path.substring(lastSeparator + 1);
            if (holdsAWildcard(directory)) {
                return BlockValue.block(List.of());
            }
            List<String> names;
            try {
                names = files.namesIn(directory.isEmpty() ? "." : directory);
            } catch (RuntimeException nothingThere) {
                return BlockValue.block(List.of());
            }
            return BlockValue.block(names.stream()
                    .filter(listed -> Wildcards.STARS_AND_QUESTION_MARKS.matchTheWholeOf(
                            withoutItsSlash(listed), pattern))
                    .<Value>map(name -> StringValue.of(name, Datatype.FILE))
                    .toList());
        }

        private static String withoutItsSlash(String name) {
            return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        }


        private static boolean holdsAWildcard(String path) {
            return path.indexOf('*') >= 0 || path.indexOf('?') >= 0;
        }


        private byte[] theBytesAskedFor(byte[] whole) {
            int from = (int) Math.min(position.orElse(0L), whole.length);
            if (bound.isEmpty()) {
                return Arrays.copyOfRange(whole, from, whole.length);
            }
            long asked = bound.orElseThrow();
            if (asked >= 0) {
                int to = (int) Math.min(from + asked, whole.length);
                return Arrays.copyOfRange(whole, from, to);
            }
            long backwards = -asked;
            if (backwards > from) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                        "a backwards read of " + backwards
                                + " reaches before the file's start");
            }
            return Arrays.copyOfRange(whole, (int) (from - backwards), from);
        }

        private static Optional<String> decodedUtfText(byte[] bytes) {
            try {
                return Optional.of(StringValue.of(strictlyDecodedByItsMark(bytes))
                        .withOneLineFeedPerEnding().text());
            } catch (java.nio.charset.CharacterCodingException undecodable) {
                return Optional.empty();
            }
        }

        private static String strictlyDecodedByItsMark(byte[] bytes)
                throws java.nio.charset.CharacterCodingException {
            if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
                return strictlyDecoded(bytes, 3, StandardCharsets.UTF_8);
            }
            if (startsWith(bytes, 0xFF, 0xFE, 0x00, 0x00)) {
                return strictlyDecoded(
                        bytes, 4, Charset.forName("UTF-32LE"));
            }
            if (startsWith(bytes, 0x00, 0x00, 0xFE, 0xFF)) {
                return strictlyDecoded(
                        bytes, 4, Charset.forName("UTF-32BE"));
            }
            if (startsWith(bytes, 0xFE, 0xFF)) {
                return strictlyDecoded(bytes, 2, StandardCharsets.UTF_16BE);
            }
            if (startsWith(bytes, 0xFF, 0xFE)) {
                return strictlyDecoded(bytes, 2, StandardCharsets.UTF_16LE);
            }
            return strictlyDecoded(bytes, 0, StandardCharsets.UTF_8);
        }

        private static String strictlyDecoded(
                byte[] bytes, int from, Charset charset)
                throws java.nio.charset.CharacterCodingException {
            return charset.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, from, bytes.length - from))
                    .toString();
        }

        private static boolean startsWith(byte[] bytes, int... mark) {
            if (bytes.length < mark.length) {
                return false;
            }
            for (int at = 0; at < mark.length; at++) {
                if ((bytes[at] & 0xFF) != mark[at]) {
                    return false;
                }
            }
            return true;
        }

        private static Value linesOf(String text) {
            List<Value> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (int at = 0; at < text.length(); at++) {
                char letter = text.charAt(at);
                if (letter == '\n' || letter == '\r') {
                    lines.add(StringValue.of(line.toString()));
                    line.setLength(0);
                } else {
                    line.append(letter);
                }
            }
            if (!line.isEmpty()) {
                lines.add(StringValue.of(line.toString()));
            }
            return BlockValue.block(lines);
        }
    }

    private record FileWriting(
            String path,
            Value data,
            Optional<Long> bound,
            Optional<Long> position,
            boolean atTheEnd,
            boolean oneValuePerLine) {

        private static final List<String> ARGUMENT_ORDER = List.of("part", "seek", "allow");

        static FileWriting asAskedFor(List<Value> arguments, Set<String> refinements) {
            return new FileWriting(
                    ((StringValue) arguments.getFirst()).text(),
                    arguments.get(1),
                    refusingANegative("bounded to", numberFor("part", arguments, refinements)),
                    refusingANegative("written at", numberFor("seek", arguments, refinements)),
                    refinements.contains("append"),
                    refinements.contains("lines"));
        }

        private static Optional<Long> numberFor(
                String refinement, List<Value> arguments, Set<String> refinements) {
            Value asked = argumentFor(refinement, ARGUMENT_ORDER, arguments, refinements, 2);
            return asked == null
                    ? Optional.empty()
                    : Optional.of((long) Arithmetic.asMagnitude(asked));
        }

        private static Optional<Long> refusingANegative(String what, Optional<Long> asked) {
            if (asked.isPresent() && asked.orElseThrow() < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                        "a write cannot be " + what + " a place before the start, and "
                                + asked.orElseThrow() + " is one");
            }
            return asked;
        }

        void performThrough(FilePort files) {
            byte[] bytes = bytesToWrite();
            if (position.isPresent()) {
                files.writeAt(path, clippedToTheFileSize(files), bytes);
            } else if (atTheEnd) {
                files.appendTo(path, bytes);
            } else {
                files.write(path, bytes);
            }
        }

        private long clippedToTheFileSize(FilePort files) {
            long size = files.informationAbout(path)
                    .flatMap(FileInformation::size)
                    .orElse(0L);
            return Math.min(position.orElseThrow(), size);
        }

        private byte[] bytesToWrite() {
            if (data instanceof BinaryValue binary) {
                return withTheLineFeedByteLinesAsks(
                        boundedOctets(binary.octetsFromHere()));
            }
            if (data instanceof CharacterValue(int codepoint)) {
                return utf8(Character.toString(codepoint));
            }
            if (data instanceof BlockValue block && oneValuePerLine) {
                return utf8(eachValueFormedOnItsOwnLine(block));
            }
            return utf8(withTheLineFeedLinesAsks(boundedText(asTextToWrite())));
        }

        private String asTextToWrite() {
            return isExactlyAString(data)
                    ? ((StringValue) data).text()
                    : Molder.mold(data);
        }

        private String eachValueFormedOnItsOwnLine(BlockValue block) {
            return block.remaining().stream()
                    .map(each -> Molder.form(each) + "\n")
                    .collect(Collectors.joining());
        }

        private String withTheLineFeedLinesAsks(String text) {
            return oneValuePerLine ? text + "\n" : text;
        }

        private byte[] withTheLineFeedByteLinesAsks(byte[] octets) {
            if (!oneValuePerLine) {
                return octets;
            }
            byte[] fed = Arrays.copyOf(octets, octets.length + 1);
            fed[octets.length] = '\n';
            return fed;
        }

        private String boundedText(String text) {
            if (bound.isEmpty()) {
                return text;
            }
            int codePoints = text.codePointCount(0, text.length());
            int kept = (int) Math.min(bound.orElseThrow(), codePoints);
            return text.substring(0, text.offsetByCodePoints(0, kept));
        }

        private byte[] boundedOctets(byte[] octets) {
            if (bound.isEmpty() || bound.orElseThrow() >= octets.length) {
                return octets;
            }
            return Arrays.copyOf(octets, (int) (long) bound.orElseThrow());
        }

        private static byte[] utf8(String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final int OPEN_FAILED = 3;

    private static final int WHATEVER_NUMBER_THE_MACHINE_HAS_FREE = 0;

    private void bindTheDatagramPort(PortValue port, Evaluator evaluator) {
        int number = theSpecNamesSomewhereToSendTo(port)
                ? WHATEVER_NUMBER_THE_MACHINE_HAS_FREE
                : portNumberOf(port);
        throughNetwork(() -> {
            NetworkPort.Datagrams bound = evaluator.network().bindTo(number);
            port.setField("state", JavaObjectValue.of(bound));
            return port;
        });
    }

    private static boolean theSpecNamesSomewhereToSendTo(PortValue port) {
        return !theHostToSendTo(port).isEmpty();
    }

    private static String theHostToSendTo(PortValue port) {
        if (port.fieldValue("spec") instanceof ObjectValue(Context context)
                && context.holds("host")
                && context.ownSlotFor("host").value() instanceof StringValue host) {
            return host.text();
        }
        return "";
    }

    private static NetworkPort.Datagrams datagramsBehind(PortValue port) {
        if (port.fieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Datagrams bound) {
            return bound;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    private Value oneDatagramReadInto(PortValue port, Evaluator evaluator) {
        grantedServices.require(HostService.NETWORK);
        NetworkPort.Datagrams bound = datagramsBehind(port);
        return throughNetwork(() -> {
            BinaryValue arrived = BinaryValue.ofBytes(bound.receive());
            addToThePortsData(port, arrived);
            queueWhatHappenedTo(port, "read", evaluator);
            return port;
        });
    }

    private Value oneDatagramSentFrom(PortValue port, Value data, Evaluator evaluator) {
        grantedServices.require(HostService.NETWORK);
        byte[] bytes = switch (data) {
            case StringValue written -> written.text().getBytes(StandardCharsets.UTF_8);
            case BinaryValue carried -> carried.octetsFromHere();
            default -> throw Raised.of(EvaluationFailure.INVALID_PORT_ARG, data);
        };
        NetworkPort.Datagrams bound = datagramsBehind(port);
        return throughNetwork(() -> {
            bound.sendTo(theHostToSendTo(port), portNumberOf(port), bytes);
            port.setField("data", NoneValue.none());
            queueWhatHappenedTo(port, "wrote", evaluator);
            return port;
        });
    }

    private void connectTheTcpPort(PortValue port, Evaluator evaluator) {
        String host = hostNamedBy(port);
        int number = portNumberOf(port);
        throughNetwork(() -> {
            NetworkPort.Connection made = evaluator.network().connectTo(host, number);
            port.setField("state", JavaObjectValue.of(made));
            queueWhatHappenedTo(port, "connect", evaluator);
            return port;
        });
    }

    private static int portNumberOf(PortValue port) {
        if (port.fieldValue("spec") instanceof ObjectValue(Context context)
                && context.holds("port")
                && context.ownSlotFor("port").value() instanceof IntegerValue(long magnitude)) {
            return (int) magnitude;
        }
        return NetworkPort.wellKnownPortFor(port.schemeName()).orElse(0);
    }

    private static Value throughNetwork(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (NetworkPort.Refused refused) {
            throw new Raised(ErrorValue.about(ErrorCategory.ACCESS,
                    refused.errorId(), refused.getMessage(),
                    StringValue.of(refused.subject()),
                    IntegerValue.of(OPEN_FAILED)));
        }
    }

    private static void markOpenWhateverTheActorLeftInState(PortValue port) {
        if (!port.isOpen()) {
            port.markOpen(true);
        }
    }

    private static void handBackTheConnectionBehind(PortValue port) {
        if (port.fieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            open.close();
        }
    }

    private static NetworkPort.Connection connectionBehind(PortValue port) {
        if (port.fieldValue("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            return open;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    private static String hostNamedBy(PortValue port) {
        if (port.fieldValue("spec") instanceof ObjectValue(Context context)) {
            if (context.holds("host")
                    && context.ownSlotFor("host").value()
                            instanceof StringValue host) {
                return host.text();
            }
            if (context.holds("ref")
                    && context.ownSlotFor("ref").value()
                            instanceof StringValue reference) {
                String written = reference.text();
                int afterScheme = written.indexOf("://");
                return afterScheme < 0 ? written : written.substring(afterScheme + 3);
            }
        }
        return "";
    }

    private static Value throughPort(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (FilePort.Denied denied) {
            throw denied.raised();
        }
    }

    private static Value throughWindow(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (WindowPort.Denied denied) {
            throw refusedByTheHost(denied.errorId(), denied.getMessage());
        }
    }

    private static Raised refusedByTheHost(String errorId, String because) {
        String reason = because + ", which is "
                + ServiceRefusal.NOT_PRESENT.name()
                        .toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return new Raised(ErrorValue.about(
                ErrorCategory.ACCESS, errorId, reason, StringValue.of(reason)));
    }

    private static Optional<String> textOfArgument(
            List<Value> arguments, Set<String> refinements,
            List<String> declaredOrder, String refinement) {

        return argumentFor(refinement, declaredOrder, arguments, refinements, 0)
                instanceof StringValue text
                ? Optional.of(text.text())
                : Optional.empty();
    }

    private static List<String> filterPairsIn(
            List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("filter")) {
            return List.of();
        }
        BlockValue listed = (BlockValue) arguments.stream()
                .filter(BlockValue.class::isInstance)
                .findFirst()
                .orElseThrow();
        List<Value> items = listed.remaining();
        if (items.size() % 2 != 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a filter list pairs a name with a pattern, and "
                            + items.size() + " items do not pair up");
        }
        return items.stream().map(Molder::form).toList();
    }

    private void registerParse() {
        define("parse", List.of(Parameter.required("input", WHAT_PARSE_TAKES),
                        Parameter.required("rule")),
                of("case"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(1)) {
                    case BlockValue rule -> Parser.over(evaluator, context,
                            arguments.get(0), refinements.contains("case"))
                            .answerFor(rule);
                    default -> {
                        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                "parse needs a rule block, not "
                                        + arguments.get(1).datatype().literalSpelling());
                    }
                });

    }

    private void registerScreen() {
        define("init-top-window",
                List.of(Parameter.required("gob", of(Datatype.GOB))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.WINDOWS);
                    return theRootGobTakenBy(evaluator.screen(), arguments.getFirst());
                });

        define("gui-metric",
                List.of(Parameter.required("keyword", of(Datatype.WORD)),
                        Parameter.belongingTo("set", "val", ANYTHING),
                        Parameter.belongingTo("display", "idx", of(Datatype.INTEGER))),
                of("set", "display"),
                (arguments, evaluator, context, refinements) -> {
                    grantedServices.require(HostService.WINDOWS);
                    return measurementOf(evaluator.screen(),
                            metricNamedBy(arguments.getFirst()),
                            displayAskedFor(arguments, refinements));
                });

        define("show",
                List.of(Parameter.required("gob",
                        of(Datatype.GOB, Datatype.NONE, Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    grantedServices.require(HostService.WINDOWS);
                    return whatWasShown(evaluator.screen(), arguments.getFirst());
                });
    }

    private static Value theRootGobTakenBy(ScreenPort screen, Value given) {
        if (!(given instanceof GobValue root)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "init-top-window takes a gob, not "
                            + given.datatype().literalSpelling());
        }
        ScreenPort.takeAsTheRoot(screen, root);
        return NoneValue.none();
    }

    private static Value measurementOf(
            ScreenPort screen, ScreenMetric metric, int display) {

        if (metric.isACount()) {
            return IntegerValue.of(screen.displayCount());
        }
        if (screen.hasADisplay() && !servesDisplay(screen, display)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "there is no display " + display);
        }
        if (!screen.hasADisplay()) {
            return PairValue.of(0, 0);
        }
        return screen.measure(metric, display);
    }

    private static boolean servesDisplay(ScreenPort screen, int display) {
        return display >= 0 && display < screen.displayCount();
    }

    private static ScreenMetric metricNamedBy(Value asked) {
        if (!(asked instanceof WordValue word)
                || word.datatype() != Datatype.WORD) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "gui-metric takes a word, not "
                            + asked.datatype().literalSpelling());
        }
        return ScreenMetric.named(word.canonical()).orElseThrow(() ->
                Raised.of(EvaluationFailure.INVALID_ARG,
                        "no host serves the metric " + word.canonical()));
    }

    private static int displayAskedFor(List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("display")) {
            return 0;
        }
        Value written = arguments.getLast();
        if (!(written instanceof IntegerValue(long magnitude))) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "a display is numbered with an integer, not "
                            + written.datatype().literalSpelling());
        }
        return (int) magnitude;
    }

    private static Value whatWasShown(ScreenPort screen, Value given) {
        if (given instanceof GobValue gob) {
            throughScreen(() -> {
                screen.show(gob);
                return NoneValue.none();
            });
        }
        return given;
    }

    private static Value throughScreen(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (ScreenPort.Denied denied) {
            throw refusedByTheHost(denied.errorId(), denied.getMessage());
        }
    }

    private static String forOutput(Value value, Evaluator evaluator) {
        if (!(value instanceof BlockValue block)) {
            return Molder.form(value);
        }
        return evaluator.evaluateEachOrRaise(block, evaluator.systemContext()).stream()
                .map(Molder::form)
                .collect(Collectors.joining(" "));
    }

    private static int binaryBaseNamedBy(Evaluator evaluator) {
        return evaluator.systemContext().slotFor("system").value() instanceof ObjectValue(Context context1)
                && context1.slotFor("options").value() instanceof ObjectValue(Context context)
                && context.slotFor("binary-base").value() instanceof IntegerValue(long magnitude)
                ? (int) magnitude
                : 16;
    }

    private void registerOutput() {
        define("mold", List.of(Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "limit", of(Datatype.INTEGER))),
                of("all", "only", "flat", "part"),
                (arguments, evaluator, context, refinements) -> {
                    Function<Value, String> written =
                            refinements.contains("only")
                                    && arguments.getFirst() instanceof BlockValue block
                                    && block.datatype() == Datatype.BLOCK
                            ? value -> Molder.moldOnly((BlockValue) value)
                            : refinements.contains("all")
                                    ? Molder::moldAll
                                    : Molder::mold;
                    Function<Value, String> inTheSystemBase = value ->
                            Molder.writingBinariesInBase(binaryBaseNamedBy(evaluator),
                                    () -> written.apply(value));
                    Function<Value, String> how = refinements.contains("flat")
                            ? value -> Molder.flattened(
                                    () -> inTheSystemBase.apply(value))
                            : inTheSystemBase;
                    if (refinements.contains("part") && arguments.size() > 1
                            && arguments.get(1) instanceof IntegerValue(long magnitude)) {
                        return StringValue.of(Molder.moldWithin(arguments.getFirst(),
                                (int) Math.max(0, magnitude), how));
                    }
                    return StringValue.of(how.apply(arguments.getFirst()));
                });
        define("form", takesAnything("value"),
                (arguments, evaluator, context) -> StringValue.of(Molder.form(arguments.get(0))));

        define("quit", List.of(Parameter.belongingTo("return", "value", of())),
                of("now", "return"),
                (arguments, evaluator, context, refinements) -> {
                    throw new QuitRequested(refinements.contains("return")
                            ? arguments.getFirst()
                            : UnsetValue.unset());
                });
        define("print", takesAnything("value"),
                (arguments, evaluator, context) -> {
                    evaluator.output().writeLine(forOutput(arguments.get(0), evaluator));
                    return UnsetValue.unset();
                });
        define("prin", takesAnything("value"),
                (arguments, evaluator, context) -> {
                    evaluator.output().write(forOutput(arguments.get(0), evaluator));
                    return UnsetValue.unset();
                });
        define("make-error", takes("id", "message"),
                (arguments, evaluator, context) -> ErrorValue.script(
                        Molder.form(arguments.get(0)), Molder.form(arguments.get(1))));
    }
}
