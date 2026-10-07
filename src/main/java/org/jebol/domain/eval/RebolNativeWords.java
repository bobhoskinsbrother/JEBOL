package org.jebol.domain.eval;

import org.jebol.domain.eval.definition.*;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.read.Construction;
import org.jebol.domain.value.*;

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


    private static Raised refusedByTheHost(String errorId, String because) {
        String reason = because + ", which is "
                + ServiceRefusal.NOT_PRESENT.name()
                        .toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return new Raised(ErrorValue.about(
                ErrorCategory.ACCESS, errorId, reason, StringValue.of(reason)));
    }

    private void registerParse() {
        register(new ParseNative());
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
