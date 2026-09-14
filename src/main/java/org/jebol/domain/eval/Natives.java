package org.jebol.domain.eval;

import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.parse.Parser;
import org.jebol.domain.parse.StringParser;
import org.jebol.domain.read.SyntaxFailure;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.DoublePredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The built-in function set, and the context that holds it.
 *
 * <p>Every native gathers arguments, type-checks them and raises exactly as a
 * user function does. Nothing about being built in changes how it is called,
 * which is what lets {@code :print} be assigned to another word and called
 * through it.
 *
 * <p>Every operator has a prefix twin doing the same work, so {@code 1 + 2}
 * and {@code add 1 2} are one behaviour reached two ways.
 */
public final class Natives {

    private final RebolRandom randomness = new RebolRandom();

    private final Map<String, RefinedCallable> behaviours = new LinkedHashMap<>();
    private final Map<String, NativeValue> definitions = new LinkedHashMap<>();
    private final Map<String, String> operatorTwins = new LinkedHashMap<>();

    private final Context runState = Context.root();

    private Context systemInternals = Context.root();

    private final MapValue registeredStructLayouts = MapValue.empty();

    private static final Map<String, String[]> DATATYPE_SPECS = datatypeSpecs();

    private static Map<String, String[]> datatypeSpecs() {
        Map<String, String[]> specs = new LinkedHashMap<>();
        Object[] table = {
            "end", new String[] {"internal marker for end of block", "internal"},
            "unset", new String[] {"no value returned or set", "internal"},
            "none", new String[] {"no value represented", "scalar"},
            "logic", new String[] {"boolean true or false", "scalar"},
            "integer", new String[] {"64 bit integer", "scalar"},
            "decimal", new String[] {"64bit floating point number (IEEE standard)", "scalar"},
            "percent", new String[] {"special form of decimals (used mainly for layout)", "scalar"},
            "money", new String[] {"high precision decimals with denomination (opt)", "scalar"},
            "char", new String[] {"8bit and 16bit character", "scalar"},
            "pair", new String[] {"two dimensional point or size", "scalar"},
            "tuple", new String[] {"sequence of small integers (colors, versions, IP)", "scalar"},
            "time", new String[] {"time of day or duration", "scalar"},
            "date", new String[] {"day, month, year, time of day, and timezone", "scalar"},
            "binary", new String[] {"string series of bytes", "string"},
            "string", new String[] {"string series of characters", "string"},
            "file", new String[] {"file name or path", "string"},
            "email", new String[] {"email address", "string"},
            "ref", new String[] {"reference", "string"},
            "url", new String[] {"uniform resource locator or identifier", "string"},
            "tag", new String[] {"markup string (HTML or XML)", "string"},
            "bitset", new String[] {"set of bit flags", "string"},
            "image", new String[] {"RGB image with alpha channel", "vector"},
            "vector", new String[] {"high performance arrays (single datatype)", "vector"},
            "block", new String[] {"series of values", "block"},
            "paren", new String[] {"automatically evaluating block", "block"},
            "path", new String[] {"refinements to functions, objects, files", "block"},
            "set-path", new String[] {"definition of a path's value", "block"},
            "get-path", new String[] {"the value of a path", "block"},
            "lit-path", new String[] {"literal path value", "block"},
            "hash", new String[] {"series of values (using hash table)", "block"},
            "map", new String[] {"name-value pairs (hash associative)", "block"},
            "datatype", new String[] {"type of datatype", "symbol"},
            "typeset", new String[] {"set of datatypes", "opt-object"},
            "word", new String[] {"word (symbol or variable)", "word"},
            "set-word", new String[] {"definition of a word's value", "word"},
            "get-word", new String[] {"the value of a word (variable)", "word"},
            "lit-word", new String[] {"literal word value", "word"},
            "refinement", new String[] {"variation of meaning or location", "word"},
            "issue", new String[] {"identifying marker word", "word"},
            "native", new String[] {"direct CPU evaluated function", "function"},
            "action", new String[] {"datatype native function (standard polymorphic)", "function"},
            "rebcode", new String[] {"virtual machine function", "block"},
            "command", new String[] {"special dispatch-based function", "function"},
            "op", new String[] {"infix operator (special evaluation exception)", "function"},
            "closure", new String[] {"function with persistent locals (indefinite extent)", "function"},
            "function", new String[] {"interpreted function (user-defined or mezzanine)", "function"},
            "frame", new String[] {"internal context frame", "internal"},
            "object", new String[] {"context of names with values", "object"},
            "module", new String[] {"loadable context of code and data", "object"},
            "error", new String[] {"errors and throws", "object"},
            "task", new String[] {"evaluation environment", "object"},
            "port", new String[] {"external series, an I/O channel", "object"},
            "gob", new String[] {"graphical object", "opt-object"},
            "event", new String[] {"user interface event (efficiently sized)", "opt-object"},
            "handle", new String[] {"arbitrary internal object or value", "internal"},
            "struct", new String[] {"native structure definition", "block"},
            "library", new String[] {"external library reference", "internal"},
            "utype", new String[] {"user defined datatype", "object"},
        };
        for (int at = 0; at + 1 < table.length; at += 2) {
            specs.put((String) table[at], (String[]) table[at + 1]);
        }
        return Map.copyOf(specs);
    }

    /**
     * Forgets what the interpreter's own setup did.
     *
     * <p>Loading the prelude and the borrowed library catches errors of
     * its own, and a script must not see those as the last thing that
     * went wrong. Called once building is finished.
     */
    public void forgetStartupState() {
        runState.set("last-error", NoneValue.none());
        runState.set("last-result", NoneValue.none());
    }

    private Natives() {
        defineArithmetic();
        defineComparison();
        defineControl();
        defineFunctionMaking();
        defineNonLocalExit();
        defineObjects();
        defineLoops();
        defineReflection();
        defineSeries();
        defineStrings();
        defineConversion();
        defineEncodings();
        defineInterpreterState();
        definePorts();
        defineParse();
        defineLayout();
        defineScreen();
        defineOutput();
        defineOperators();
    }

    private void defineOperators() {
        defineOperator("!=", "not-equal?");
        defineOperator("!==", "strict-not-equal?");
        defineOperator("%", "remainder");
        defineOperator("%%", "modulo");
        defineOperator("&", "and~");
        defineOperator("*", "multiply");
        defineOperator("**", "power");
        defineOperator("+", "add");
        defineOperator("-", "subtract");
        defineOperator("/", "divide");
        defineOperator("//", "integer-divide");
        defineOperator("<", "lesser?");
        defineOperator("<<", "shift-left");
        defineOperator("<=", "lesser-or-equal?");
        defineOperator("<>", "not-equal?");
        defineOperator("=", "equal?");
        defineOperator("==", "strict-equal?");
        defineOperator("=?", "same?");
        defineOperator(">", "greater?");
        defineOperator(">=", "greater-or-equal?");
        defineOperator(">>", "shift-right");
        defineOperator("and", "and~");
        defineOperator("or", "or~");
        defineOperator("xor", "xor~");
        defineOperator("|", "or~");
    }

    private Set<HostService> grantedServices = Set.of();

    private char localFileSeparator = '/';

    /** Tells the natives what this machine puts between path parts. */
    public void useFileSeparator(char separator) {
        this.localFileSeparator = separator;
    }

    private String bootLauncher = "";

    /** Tells the natives what starts this interpreter from a shell. */
    public void useBootLauncher(String launcherPath) {
        this.bootLauncher = launcherPath;
    }

    private String operatingSystemName = "JVM";

    public void useOperatingSystemNamed(String operatingSystem) {
        this.operatingSystemName = operatingSystem;
    }

    private String errorCatalogueSource = "";

    /** Tells the natives what the vendored errors.reb says. */
    public void useErrorCatalogue(String source) {
        this.errorCatalogueSource = source;
    }

    private String functionDeclarationSource = "";

    /** Tells the natives what Rebol's own declaration files say. */
    public void useFunctionDeclarations(String... sources) {
        this.functionDeclarationSource = String.join("\n", sources);
        this.declaredSpecs = null;
    }

    /** The natives with a set of host services granted. */
    public static Natives standard(Set<HostService> granted) {
        Natives natives = standard();
        natives.grantedServices = Set.copyOf(granted);
        return natives;
    }

    /**
     * Narrows what is granted to exactly this set.
     *
     * <p>Called once, when the interpreter has finished building itself. The
     * library may read the clock while it loads and a script may not unless the
     * host said so, and the two are not the same question: loading Rebol's own
     * files is part of making the language, and the grants are about what a
     * script can reach once there is one. See decision 19.
     *
     * <p>The screen is granted while loading for the same reason and it looks
     * more alarming than it is. {@code view-funcs.reb} ends by calling
     * INIT-VIEW-SYSTEM, which takes a root gob and opens the event port, and
     * without that the file stops on its last line and defines none of VIEW,
     * UNVIEW or DO-EVENTS. Nothing reaches a screen: the port an interpreter
     * starts with has no display, so the root gob is sized at nothing and
     * SHOW is never called. Once this runs, a script with no grant cannot
     * call any of the three commands.
     */
    public void grantOnly(Set<HostService> granted) {
        this.grantedServices = Set.copyOf(granted);
    }

    private void requireService(HostService service) {
        if (grantedServices.contains(service)) {
            return;
        }
        throw Raised.of(EvaluationFailure.NO_SERVICE,
                service.name().toLowerCase(java.util.Locale.ROOT)
                        + " is " + ServiceRefusal.NOT_GRANTED.name()
                                .toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    private static final java.util.Set<String> FIELDS_THE_OPERATING_SYSTEM_ANSWERS =
            java.util.Set.of("uid", "euid", "gid", "egid", "pid");

    private static final int TERMINATE = 15;

    private Value signalled(Value asked) {
        long process;
        int signal;
        if (asked instanceof IntegerValue only) {
            process = only.magnitude();
            signal = TERMINATE;
        } else {
            List<Value> pair = ((BlockValue) asked).remaining();
            if (pair.size() != 2
                    || !(pair.get(0) instanceof IntegerValue whichProcess)
                    || !(pair.get(1) instanceof IntegerValue chosen)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(asked));
            }
            process = whichProcess.magnitude();
            signal = (int) chosen.magnitude();
        }
        requireService(HostService.PROCESSES);
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
        java.util.Optional<ProcessHandle> found = ProcessHandle.of(process);
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
        requireService(HostService.FILES);
        return throughPort(() -> LogicValue.of(evaluator.files().isDirectory(path)))
                .isTruthy();
    }

    private static Value refuseExtensionPoint(String extensionPoint) {
        throw Raised.of(EvaluationFailure.NO_SERVICE,
                extensionPoint + " calls code written in C, which is "
                        + ServiceRefusal.NEVER_PORTABLE.name()
                                .toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    public static Natives standard() {
        return new Natives();
    }

    private static BlockValue typeNames(String... spellings) {
        return BlockValue.block(Arrays.stream(spellings)
                .map(spelling -> (Value) WordValue.of(spelling + "!"))
                .toList());
    }

    private static final List<String> ACTION_NAMES = ActionNames.inDeclarationOrder();

    private static BlockValue typeNamesWithoutSuffix(String... spellings) {
        return BlockValue.block(Arrays.stream(spellings)
                .<Value>map(WordValue::of).toList());
    }

    private ObjectValue systemObject(Context systemContext) {
        Context catalog = Context.root();
        catalog.set("datatypes", BlockValue.block(
                Arrays.stream(Datatype.values())
                        .map(datatype -> (Value) DatatypeValue.of(datatype))
                        .toList()));

        catalog.set("reflectors", BlockValue.block(List.of(
                WordValue.of("spec"),
                typeNames("any-function", "any-object", "vector", "datatype", "struct"),
                WordValue.of("body"),
                typeNames("any-function", "any-object", "map", "struct"),
                WordValue.of("words"),
                typeNames("any-function", "any-object", "map", "date", "handle", "struct"),
                WordValue.of("values"), typeNames("any-object", "map", "struct"),
                WordValue.of("types"), typeNames("any-function"),
                WordValue.of("title"), typeNames("any-function", "datatype", "module"))));

        Context bitsets = Context.root();
        bitsets.set("crlf", BitsetValue.ofCharacters('\r', '\n'));
        bitsets.set("space", BitsetValue.ofCharacters(' ', '\t'));
        bitsets.set("whitespace", BitsetValue.ofCharacters(' ', '\t', '\r', '\n'));
        bitsets.set("numeric", BitsetActions.rangeOfCharacters('0', '9'));
        bitsets.set("alpha", BitsetActions.lettersOfBothCases());
        bitsets.set("alpha-numeric", BitsetActions.together(
                BitsetActions.lettersOfBothCases(),
                BitsetActions.rangeOfCharacters('0', '9')));
        bitsets.set("hex-digits", BitsetActions.together(
                BitsetActions.rangeOfCharacters('0', '9'),
                BitsetActions.together(BitsetActions.rangeOfCharacters('a', 'f'),
                        BitsetActions.rangeOfCharacters('A', 'F'))));
        bitsets.set("plus-minus", BitsetValue.ofCharacters('+', '-'));
        bitsets.set("not-crlf",
                BitsetValue.ofCharacters('\r', '\n').complemented());
        bitsets.set("uri", BitsetActions.charactersIn(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                        + "!#$&'()*+,-./:;=?@_~"));
        bitsets.set("uri-component", BitsetActions.charactersIn(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                        + "!'()*-._~"));
        bitsets.set("quoted-printable", BitsetActions.quotedPrintableOctets());
        catalog.set("bitsets", new ObjectValue(bitsets));

        catalog.set("structs", registeredStructLayouts);

        catalog.set("actions", BlockValue.block(ACTION_NAMES.stream()
                .filter(definitions::containsKey)
                .<Value>map(WordValue::of).toList()));

        catalog.set("natives", BlockValue.block(definitions.keySet().stream()
                .filter(spelling -> !ACTION_NAMES.contains(spelling))
                .sorted()
                .<Value>map(WordValue::of).toList()));

        catalog.set("boot-flags", typeNamesWithoutSuffix(
                "script", "args", "do", "import", "version", "debug", "secure",
                "help", "vers", "quiet", "verbose", "secure-min", "secure-max",
                "trace", "halt", "cgi", "boot-level", "no-window", "no-color",
                "legacy-repl"));

        catalog.set("ciphers", BlockValue.block(CryptPort.catalogue()));

        catalog.set("filters", BlockValue.block(
                THE_FILTERS.stream().<Value>map(WordValue::of).toList()));

        catalog.set("elliptic-curves", BlockValue.block(
                EllipticCurveKey.curveNamesInTheCataloguesOrder().stream()
                        .<Value>map(WordValue::of).toList()));

        catalog.set("handles", BlockValue.block(List.of(
                WordValue.of(RC4_HANDLE_TYPE), WordValue.of(DHM_HANDLE_TYPE),
                WordValue.of(RSA_HANDLE_TYPE), WordValue.of(ECDH_HANDLE_TYPE),
                WordValue.of("codec"))));

        catalog.set("event-types", EventCatalogue.typesBlock());
        catalog.set("event-keys", EventCatalogue.keysBlock());

        catalog.set("checksums", BlockValue.block(
                Encodings.checksumMethods().stream()
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
        options.set("quiet", LogicValue.no());
        options.set("no-color", LogicValue.no());
        options.set("binary-base", IntegerValue.of(16));
        options.set("decimal-digits", IntegerValue.of(15));
        options.set("probe-limit", IntegerValue.of(16000));
        options.set("http-redirects", IntegerValue.of(10));
        options.set("default-suffix", StringValue.of(".reb", Datatype.FILE));
        options.set("home", StringValue.of(
                System.getProperty("user.home", "") + "/", Datatype.FILE));
        options.set("boot", bootLauncher.isEmpty()
                ? NoneValue.none()
                : StringValue.of(bootLauncher, Datatype.FILE));
        options.set("path", StringValue.of(
                System.getProperty("user.dir", "") + "/", Datatype.FILE));
        options.set("data", StringValue.of(
                System.getProperty("user.home", "") + "/.jebol/", Datatype.FILE));

        Context state = runState;
        Context policies = Context.root();
        for (String policy : new String[] {
                "file", "net", "eval", "memory", "secure", "protect", "debug",
                "envr", "call", "browse", "extension"}) {
            policies.set(policy, TupleValue.of(new int[] {0, 0, 0}));
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
                    ErrorWording.say(category.spelling(), name.spelling(),
                            fields.get(pair + 1));
                }
            }
            errors.set(category.spelling(), new ObjectValue(inside));
        }
        catalog.set("errors", new ObjectValue(errors));

        Context system = Context.root();
        system.set("catalog", new ObjectValue(catalog));
        system.set("options", new ObjectValue(options));
        system.set("state", new ObjectValue(state));
        system.set("version", TupleValue.of(VERSION_PARTS));
        system.set("platform", WordValue.of(operatingSystemName));
        system.set("product", WordValue.of("core"));
        system.set("license", NoneValue.none());

        Context build = Context.root();
        for (String field : new String[] {
                "os", "os-version", "abi", "sys", "arch", "libc", "vendor",
                "target", "compiler", "date", "git"}) {
            build.set(field, NoneValue.none());
        }
        system.set("build", new ObjectValue(build));

        Context whoIsRunningIt = Context.root();
        whoIsRunningIt.set("name", NoneValue.none());
        whoIsRunningIt.set("data", MapValue.of(List.of()));
        system.set("user", new ObjectValue(whoIsRunningIt));

        Context dialects = Context.root();
        for (String dialect : new String[] {
                "secure", "draw", "effect", "text", "rebcode"}) {
            dialects.set(dialect, NoneValue.none());
        }
        system.set("dialects", new ObjectValue(dialects));

        Context aboutTheScript = Context.root();
        for (String field : new String[] {
                "title", "header", "parent", "path", "args"}) {
            aboutTheScript.set(field, NoneValue.none());
        }
        system.set("script", new ObjectValue(aboutTheScript));

        Context modules = Context.root();
        modules.set("help", NoneValue.none());
        system.set("modules", new ObjectValue(modules));

        Context locale = Context.root();
        for (String field : new String[] {
                "language", "language*", "locale", "locale*"}) {
            locale.set(field, NoneValue.none());
        }
        locale.set("months", BlockValue.block(java.util.stream.Stream.of(
                        "January", "February", "March", "April", "May", "June",
                        "July", "August", "September", "October", "November",
                        "December")
                .<Value>map(StringValue::of).toList()));
        locale.set("days", BlockValue.block(java.util.stream.Stream.of(
                        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
                        "Saturday", "Sunday")
                .<Value>map(StringValue::of).toList()));
        system.set("locale", new ObjectValue(locale));
        Context codecs = Context.root();
        for (int at = 0; at < Codecs.REGISTERED.size(); at++) {
            String codec = Codecs.REGISTERED.get(at);
            codecs.set(codec, HandleValue.function(
                    "codec", CODEC_HANDLE_IDENTITY + at, WordValue.of(codec)));
        }
        system.set("codecs", new ObjectValue(codecs));
        Context console = Context.root();
        console.set("history", BlockValue.block(List.of()));
        console.set("current", NoneValue.none());
        system.set("console", new ObjectValue(console));

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

    /** What the evaluator dispatches on: native name to behaviour. */
    public Map<String, RefinedCallable> behaviours() {
        return Map.copyOf(behaviours);
    }

    /**
     * Where the sys files define their words.
     *
     * <p>Only meaningful after {@link #asContext()} has run, which is where
     * the system object and its three contexts are assembled.
     */
    public Context systemInternals() {
        return systemInternals;
    }

    /** A fresh context holding every native, and the operators alongside. */
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

    public int nativeCount() {
        return definitions.size();
    }

    public int operatorCount() {
        return operatorTwins.size();
    }

    private void define(String name, List<Parameter> parameters, Callable behaviour) {
        define(name, parameters, Set.of(),
                (arguments, evaluator, context, refinements) ->
                        behaviour.call(arguments, evaluator, context));
    }

    private void define(String name, List<Parameter> parameters,
            Set<String> refinements, RefinedCallable behaviour) {
        definitions.put(name, new NativeValue(name, parameters, refinements, Set.of()));
        behaviours.put(name, behaviour);
    }

    private void defineOperator(String spelling, String prefixTwin) {
        if (!definitions.containsKey(prefixTwin)) {
            throw new IllegalStateException(
                    "operator " + spelling + " has no prefix twin called " + prefixTwin);
        }
        operatorTwins.put(spelling, prefixTwin);
    }

    private static List<Parameter> takesCombinable(String... names) {
        Set<Datatype> combinable = Set.of(Datatype.LOGIC, Datatype.INTEGER, Datatype.CHAR,
                Datatype.TUPLE, Datatype.BINARY, Datatype.BITSET, Datatype.TYPESET,
                Datatype.DATATYPE, Datatype.PAIR, Datatype.VECTOR);
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, combinable));
        }
        return parameters;
    }

    private static List<Parameter> takes(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name));
        }
        return parameters;
    }

    private static final Set<Datatype> ANYTHING = Typeset.ANY_TYPE.members();

    private static Set<Datatype> anythingAtAll() {
        Set<Datatype> accepted = EnumSet.copyOf(ANYTHING);
        accepted.add(Datatype.UNSET);
        return Set.copyOf(accepted);
    }

    private static List<Parameter> takesAnything(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, ANYTHING));
        }
        return parameters;
    }

    private static List<Parameter> withBitsets(List<Parameter> parameters) {
        List<Parameter> widened = new ArrayList<>(parameters);
        Parameter first = widened.getFirst();
        Set<Datatype> accepted = EnumSet.copyOf(first.acceptedTypes());
        accepted.add(Datatype.BITSET);
        widened.set(0, Parameter.required(first.name(), accepted));
        return widened;
    }

    private static List<Parameter> takesOnlyNumbers(String... names) {
        Set<Datatype> numbers = Set.of(
                Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT);
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, numbers));
        }
        return parameters;
    }

    private static List<Parameter> takesWholeNumbersAndDecimals(String... names) {
        Set<Datatype> numbers = Set.of(Datatype.INTEGER, Datatype.DECIMAL);
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, numbers));
        }
        return parameters;
    }

    private static List<Parameter> takesNumbers(String... names) {
        Set<Datatype> numbers = Set.of(
                Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
                Datatype.MONEY, Datatype.PAIR, Datatype.TUPLE,
                Datatype.TIME, Datatype.DATE, Datatype.CHAR, Datatype.VECTOR);
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, numbers));
        }
        return parameters;
    }

    private void defineArithmetic() {
        define("add", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) ->
                        Arithmetic.sum(arguments.get(0), arguments.get(1)));
        define("subtract", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) ->
                        Arithmetic.difference(arguments.get(0), arguments.get(1)));
        define("multiply", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) ->
                        Arithmetic.product(arguments.get(0), arguments.get(1)));
        define("divide", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) ->
                        Arithmetic.quotient(arguments.get(0), arguments.get(1)));
        define("remainder", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) ->
                        Arithmetic.remainder(arguments.get(0), arguments.get(1)));
        define("square-root", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.sqrt(Comparison.asDouble(arguments.get(0)))));
        define("sqrt", List.of(Parameter.required("value", Set.of(Datatype.DECIMAL))),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.sqrt(Comparison.asDouble(arguments.get(0)))));
        define("now", List.of(),
                Set.of("year", "month", "day", "time", "zone", "date",
                        "weekday", "yearday", "precise", "utc"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.CLOCK);
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

        define("forever", List.of(Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    BlockValue body = (BlockValue) arguments.getFirst();
                    Value last = NoneValue.none();
                    try {
                        while (true) {
                            last = oneRoundCatchingContinue(evaluator,body, context);
                        }
                    } catch (LoopSignal stopped) {
                        return stopped.answer();
                    }
                });

        define("seventh", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 7));
        define("eighth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 8));
        define("ninth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 9));
        define("tenth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 10));

        define("trace", List.of(Parameter.required("mode",
                        Set.of(Datatype.INTEGER, Datatype.LOGIC))),
                Set.of("back", "function"),
                (arguments, evaluator, context, refinements) -> {
                    Value mode = arguments.getFirst();
                    Trace tracing = evaluator.tracing();
                    tracing.writeTo(evaluator.output());
                    if (refinements.contains("back")) {
                        if (mode instanceof IntegerValue lines) {
                            tracing.showTheLastAndStopTracing(
                                    (int) lines.magnitude());
                            return UnsetValue.unset();
                        }
                        tracing.keepRatherThanPrint(mode.isTruthy());
                    } else {
                        tracing.keepRatherThanPrint(false);
                    }
                    int wanted = mode instanceof IntegerValue level
                            ? (int) level.magnitude()
                            : (mode.isTruthy() ? Trace.EVERYTHING : 0);
                    tracing.level(wanted, refinements.contains("function"));
                    return UnsetValue.unset();
                });

        define("load-extension", List.of(
                        Parameter.required("name", Set.of(Datatype.FILE, Datatype.BINARY)),
                        Parameter.belongingTo("dispatch", "function", Set.of(Datatype.HANDLE))),
                Set.of("dispatch"),
                (arguments, evaluator, context, refinements) ->
                        refuseExtensionPoint("load-extension"));
        define("do-callback", takes("callback"),
                (arguments, evaluator, context) -> refuseExtensionPoint("do-callback"));
        define("do-commands", takes("commands"),
                (arguments, evaluator, context) -> refuseExtensionPoint("do-commands"));
        define("access-os", List.of(
                        Parameter.required("field", Set.of(Datatype.WORD)),
                        Parameter.belongingTo("set", "value",
                                Set.of(Datatype.INTEGER, Datatype.BLOCK))),
                Set.of("set"),
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

        define("arctangent2", List.of(Parameter.required("point", Set.of(Datatype.PAIR))),
                Set.of("radians"),
                (arguments, evaluator, context, refinements) -> {
                    PairValue point = (PairValue) arguments.getFirst();
                    double angle = Math.atan2(point.y(), point.x());
                    return DecimalValue.of(refinements.contains("radians")
                            ? angle
                            : Math.toDegrees(angle));
                });
        define("log-e", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.log(Comparison.asDouble(arguments.get(0)))));
        define("log-10", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.log10(Comparison.asDouble(arguments.get(0)))));
        define("exp", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.exp(Comparison.asDouble(arguments.get(0)))));
        define("fraction", List.of(Parameter.required("number",
                        Set.of(Datatype.DECIMAL))),
                (arguments, evaluator, context) -> {
                    double whole = Comparison.asDouble(arguments.get(0));
                    return DecimalValue.of(whole - (long) whole);
                });
        define("log-2", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.log(Comparison.asDouble(arguments.get(0))) / Math.log(2)));
        define("sine", takesOnlyNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        withoutTheNoiseNearZero(
                                Math.sin(inRadians(arguments.get(0), refinements)))));
        define("cosine", takesOnlyNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        withoutTheNoiseNearZero(
                                Math.cos(inRadians(arguments.get(0), refinements)))));
        define("tangent", takesOnlyNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) ->
                        DecimalValue.of(tangentOf(inRadians(arguments.get(0), refinements))));
        define("arcsine", takesOnlyNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        refinements.contains("radians")
                                ? Math.asin(Comparison.asDouble(arguments.get(0)))
                                : Math.toDegrees(Math.asin(Comparison.asDouble(arguments.get(0))))));
        define("arccosine", takesOnlyNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        refinements.contains("radians")
                                ? Math.acos(Comparison.asDouble(arguments.get(0)))
                                : Math.toDegrees(Math.acos(Comparison.asDouble(arguments.get(0))))));
        define("arctangent", takesOnlyNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        refinements.contains("radians")
                                ? Math.atan(Comparison.asDouble(arguments.get(0)))
                                : Math.toDegrees(Math.atan(Comparison.asDouble(arguments.get(0))))));

        define("abs", List.of(Parameter.required("value", MEASURABLE)),
                (arguments, evaluator, context) -> magnitudeOf(arguments.get(0)));
        define("absolute", List.of(Parameter.required("value", MEASURABLE)),
                (arguments, evaluator, context) -> magnitudeOf(arguments.get(0)));

        define("random", List.of(Parameter.required("value")),
                Set.of("seed", "only", "secure"),
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
                                raiseCannotUse(other, "random");
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
                        default -> raiseCannotUse(arguments.get(0), "random");
                    };
                });

        define("complement?", List.of(Parameter.required("value", Set.of(Datatype.BITSET))),
                (arguments, evaluator, context) -> LogicValue.of(
                        ((BitsetValue) arguments.getFirst()).isComplemented()));

        define("complement", List.of(Parameter.required("value", Set.of(
                        Datatype.LOGIC, Datatype.INTEGER, Datatype.TUPLE,
                        Datatype.BINARY, Datatype.BITSET, Datatype.TYPESET,
                        Datatype.IMAGE))),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case LogicValue truth -> LogicValue.of(!truth.truth());
                    case IntegerValue whole -> IntegerValue.of(~whole.magnitude());
                    case TypesetValue kinds -> new TypesetActions(kinds).complemented();
                    case TupleValue tuple -> new TupleActions(tuple).complemented();
                    case Value subject when Actions.of(subject).isPresent() ->
                            Actions.of(subject).orElseThrow().complemented();
                    default -> raiseWrongArgument(
                            arguments.get(0), "complement", "logic or integer");
                });

        defineRadianFunction("sin", Math::sin);
        defineRadianFunction("cos", Math::cos);
        defineRadianFunction("tan", Math::tan);
        defineRadianFunction("asin", Math::asin);
        defineRadianFunction("acos", Math::acos);
        defineRadianFunction("atan", Math::atan);
        defineRadianFunction("sqrt", Math::sqrt);
        define("atan2", List.of(
                        Parameter.required("y", Set.of(Datatype.DECIMAL)),
                        Parameter.required("x", Set.of(Datatype.DECIMAL))),
                (arguments, evaluator, context) -> DecimalValue.of(Math.atan2(
                        Comparison.asDouble(arguments.get(0)), Comparison.asDouble(arguments.get(1)))));

        define("to-degrees", takesWholeNumbersAndDecimals("radians"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.toDegrees(Comparison.asDouble(arguments.get(0)))));
        define("to-radians", takesWholeNumbersAndDecimals("degrees"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.toRadians(Comparison.asDouble(arguments.get(0)))));

        define("gcd", takesWholeNumbers("first", "second"),
                (arguments, evaluator, context) -> IntegerValue.of(greatestCommonDivisor(
                        wholeNumberOf(arguments.get(0), "gcd"),
                        wholeNumberOf(arguments.get(1), "gcd"))));
        define("lcm", takesWholeNumbers("first", "second"),
                (arguments, evaluator, context) -> {
                    long first = wholeNumberOf(arguments.get(0), "lcm");
                    long second = wholeNumberOf(arguments.get(1), "lcm");
                    long divisor = greatestCommonDivisor(first, second);
                    return IntegerValue.of(divisor == 0
                            ? 0
                            : Math.abs(first / divisor * second));
                });
        define("prime?", takesWholeNumbers("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        isPrime(wholeNumberOf(arguments.get(0), "prime?"))));
        define("integer-divide", takesNumbers("dividend", "divisor"),
                (arguments, evaluator, context) ->
                        Arithmetic.wholeQuotient(arguments.get(0), arguments.get(1)));

        define("clamp", List.of(
                        Parameter.required("value", CLAMPABLE),
                        Parameter.required("minimum", CLAMPABLE),
                        Parameter.required("maximum", CLAMPABLE)),
                (arguments, evaluator, context) -> heldInsideTheRange(
                        arguments.get(0), arguments.get(1), arguments.get(2)));

        define("distance", List.of(
                        Parameter.required("value1", Set.of(Datatype.PAIR)),
                        Parameter.required("value2", Set.of(Datatype.PAIR))),
                Set.of("taxicab"),
                (arguments, evaluator, context, refinements) -> betweenTwoPoints(
                        (PairValue) arguments.get(0), (PairValue) arguments.get(1),
                        refinements.contains("taxicab")));

        define("factorial", takesWholeNumbers("value"),
                (arguments, evaluator, context) -> theFactorialOf(
                        wholeNumberOf(arguments.get(0), "factorial")));

        define("power", List.of(Parameter.required("base"), Parameter.required("exponent")),
                (arguments, evaluator, context) -> {
                    if (arguments.get(0) instanceof TupleValue tuple) {
                        return raiseCannotUse(tuple, "power");
                    }
                    if (!Comparison.isNumeric(arguments.get(0)) || !Comparison.isNumeric(arguments.get(1))) {
                        return raiseWrongArgument(arguments.get(0), "power", "number");
                    }
                    return DecimalValue.of(
                            Math.pow(Comparison.asDouble(arguments.get(0)), Comparison.asDouble(arguments.get(1))));
                });

        define("negate", withBitsets(takesNumbers("value")),
                (arguments, evaluator, context) -> arguments.getFirst()
                        instanceof BitsetValue members
                        ? members.complemented()
                        : Arithmetic.difference(IntegerValue.of(0), arguments.get(0)));

        define("maximum", takesComparable("value1", "value2"),
                (arguments, evaluator, context) ->
                        extreme(arguments.get(0), arguments.get(1), true));
        define("minimum", takesComparable("value1", "value2"),
                (arguments, evaluator, context) ->
                        extreme(arguments.get(0), arguments.get(1), false));

        define("and~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        Combining.bitwise(arguments.get(0), arguments.get(1),
                                Combining.Bitwise.AND));
        define("or~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        Combining.bitwise(arguments.get(0), arguments.get(1),
                                Combining.Bitwise.OR));
        define("xor~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        Combining.bitwise(arguments.get(0), arguments.get(1),
                                Combining.Bitwise.XOR));

        define("lerp", List.of(Parameter.required("value1"),
                        Parameter.required("value2"), Parameter.required("fraction")),
                (arguments, evaluator, context) -> interpolated(
                        arguments.get(0), arguments.get(1), arguments.get(2)));

        define("mod", List.of(
                        Parameter.required("dividend", DIVISIBLE),
                        Parameter.required("divisor", DIVISIBLE)),
                (arguments, evaluator, context) -> Arithmetic.rest(
                        arguments.get(0), arguments.get(1),
                        Arithmetic.Division.SIGN_FOLLOWS_THE_DIVIDEND));

        define("modulo", List.of(
                        Parameter.required("dividend", DIVISIBLE),
                        Parameter.required("divisor", DIVISIBLE)),
                Set.of("floor"),
                (arguments, evaluator, context, refinements) -> Arithmetic.rest(
                        arguments.get(0), arguments.get(1),
                        refinements.contains("floor")
                                ? Arithmetic.Division.SIGN_FOLLOWS_THE_DIVISOR
                                : Arithmetic.Division.NEVER_NEGATIVE));

        define("shift-left", List.of(
                        Parameter.required("value", Set.of(Datatype.INTEGER)),
                        Parameter.required("bits", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> shifted(arguments, true));
        define("shift-right", List.of(
                        Parameter.required("value", Set.of(Datatype.INTEGER)),
                        Parameter.required("bits", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> shifted(arguments, false));

    }

    private void defineRadianFunction(String name, java.util.function.DoubleUnaryOperator work) {
        define(name, List.of(Parameter.required("value", Set.of(Datatype.DECIMAL))),
                (arguments, evaluator, context) -> DecimalValue.of(
                        work.applyAsDouble(Comparison.asDouble(arguments.get(0)))));
    }

    private static List<Parameter> takesWholeNumbers(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, Set.of(Datatype.INTEGER)));
        }
        return parameters;
    }

    private static long greatestCommonDivisor(long first, long second) {
        long left = Math.abs(first);
        long right = Math.abs(second);
        while (right != 0) {
            long rest = left % right;
            left = right;
            right = rest;
        }
        return left;
    }

    private static boolean isPrime(long candidate) {
        if (candidate < 2) {
            return false;
        }
        for (long divisor = 2; divisor * divisor <= candidate; divisor++) {
            if (candidate % divisor == 0) {
                return false;
            }
        }
        return true;
    }

    private static final Set<Datatype> MEASURABLE = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
            Datatype.MONEY, Datatype.TIME, Datatype.PAIR);

    private static final Set<Datatype> DIVISIBLE = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
            Datatype.MONEY, Datatype.CHAR, Datatype.TIME);

    private static double inRadians(Value angle, Set<String> refinements) {
        double given = Comparison.asDouble(angle);
        return refinements.contains("radians") ? given : Math.toRadians(given);
    }

    private static double withoutTheNoiseNearZero(double answer) {
        return Math.abs(answer) < Math.ulp(1.0) ? 0.0 : answer;
    }

    private static double tangentOf(double radians) {
        if (Arithmetic.nearlyTheSame(Math.abs(radians), Math.PI / 2.0)) {
            return radians < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return Math.tan(radians);
    }

    private static Value magnitudeOf(Value value) {
        return switch (value) {
            case IntegerValue whole -> {
                if (whole.magnitude() == Long.MIN_VALUE) {
                    throw Raised.of(EvaluationFailure.OVERFLOW,
                            "there is no positive counterpart to " + whole.magnitude());
                }
                yield IntegerValue.of(Math.abs(whole.magnitude()));
            }
            case DecimalValue quantity -> quantity.quantity() == 0.0
                    ? quantity
                    : DecimalValue.of(Math.abs(quantity.quantity()));
            case PairValue pair -> PairValue.of(Math.abs(pair.x()), Math.abs(pair.y()));
            case TimeValue time -> TimeValue.ofNanoseconds(Math.abs(time.nanoseconds()));
            case MoneyValue money -> MoneyValue.of(money.amount().abs());
            case CharacterValue character -> character;
            default -> raiseCannotUse(value, "abs");
        };
    }

    private static long shiftedKeepingTheSign(long value, long places) {
        if (places < 0) {
            long rightwards = -places;
            return rightwards >= Long.SIZE ? value >> (Long.SIZE - 1) : value >> rightwards;
        }
        if (places >= Long.SIZE) {
            if (value != 0) {
                throw Raised.of(EvaluationFailure.OVERFLOW,
                        "shifting " + value + " left by " + places + " loses every bit");
            }
            return 0;
        }
        long largestThatFits = Long.MIN_VALUE >>> places;
        long magnitude = value < 0 ? -value : value;
        if (Long.compareUnsigned(largestThatFits, magnitude) <= 0) {
            if (Long.compareUnsigned(largestThatFits, magnitude) < 0 || value >= 0) {
                throw Raised.of(EvaluationFailure.OVERFLOW,
                        "shifting " + value + " left by " + places + " leaves the range");
            }
            return Long.MIN_VALUE;
        }
        return value << places;
    }

    private static long bitsShifted(long value, long places) {
        if (Math.abs(places) >= Long.SIZE) {
            return 0;
        }
        return places >= 0 ? value << places : value >>> -places;
    }

    private static Value shifted(List<Value> arguments, boolean leftwards) {
        long value = ((IntegerValue) arguments.get(0)).magnitude();
        long count = ((IntegerValue) arguments.get(1)).magnitude();
        if (count < 0) {
            return IntegerValue.of(value);
        }
        return IntegerValue.of(leftwards ? value << count : value >> count);
    }

    private static List<Parameter> takesComparable(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name));
        }
        return parameters;
    }

    private static Value extreme(Value left, Value right, boolean wantingLarger) {
        if (left instanceof PairValue leftPair && right instanceof PairValue rightPair) {
            return PairValue.of(
                    furtherOf(leftPair.x(), rightPair.x(), wantingLarger),
                    furtherOf(leftPair.y(), rightPair.y(), wantingLarger));
        }
        boolean takeLeft = wantingLarger
                ? Comparison.compareForSorting(left, right, false) >= 0
                : Comparison.compareForSorting(left, right, false) <= 0;
        return takeLeft ? left : right;
    }

    private static double furtherOf(double left, double right, boolean wantingLarger) {
        return wantingLarger ? Math.max(left, right) : Math.min(left, right);
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
                    Encodings.checksumSeedOf(text.text().getBytes(StandardCharsets.UTF_8));
            case BinaryValue bytes -> Encodings.checksumSeedOf(bytes.octetsFromHere());
            case TupleValue tuple -> Encodings.checksumSeedOf(shownOctetsOf(tuple));
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

    private static Value interpolated(Value from, Value to, Value fraction) {
        double walked = Math.max(0, Math.min(1, Comparison.asDouble(fraction)));
        if (Comparison.isNumeric(from) && !(from instanceof TupleValue) && !(from instanceof PairValue)) {
            if (!Comparison.isNumeric(to) || to instanceof TupleValue || to instanceof PairValue) {
                throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(to));
            }
            return DecimalValue.of(alongTheWay(Comparison.asDouble(from), Comparison.asDouble(to), walked));
        }
        if (from instanceof TupleValue start) {
            if (!(to instanceof TupleValue end)) {
                throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(to));
            }
            int width = Math.max(start.segmentCount(), end.segmentCount());
            int[] octets = new int[width];
            for (int at = 1; at <= width; at++) {
                octets[at - 1] = (int) alongTheWay(start.octetAt(at), end.octetAt(at), walked);
            }
            return TupleValue.of(octets);
        }
        if (from instanceof PairValue start) {
            if (!(to instanceof PairValue end)) {
                throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(to));
            }
            return PairValue.of(alongTheWay(start.x(), end.x(), walked),
                    alongTheWay(start.y(), end.y(), walked));
        }
        throw Raised.of(EvaluationFailure.TYPE_MISMATCH, Molder.mold(from));
    }

    private static double alongTheWay(double from, double to, double fraction) {
        return from + (to - from) * fraction;
    }

    private static Value reversedOctets(TupleValue tuple, int howMany) {
        if (howMany < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Integer.toString(howMany));
        }
        int width = Math.min(howMany, tuple.segmentCount());
        int[] octets = tuple.segments();
        for (int at = 0; at < width / 2; at++) {
            int held = octets[at];
            octets[at] = octets[width - at - 1];
            octets[width - at - 1] = held;
        }
        return TupleValue.of(octets);
    }

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

    private static Value aDurationOfSeconds(Value value) {
        double seconds = Comparison.asDouble(value);
        if (seconds < -MOST_SECONDS_A_DURATION_HOLDS
                || seconds > MOST_SECONDS_A_DURATION_HOLDS) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, value);
        }
        return TimeValue.ofNanoseconds(TimeActions.wholeNanosecondsOf(value));
    }

    private static final double MOST_SECONDS_A_DURATION_HOLDS = 9_223_372_036.0;

    private static Value aTaskMadeFrom(Value value) {
        if (!(value instanceof BlockValue given) || given.datatype() != Datatype.BLOCK) {
            return raiseBadMakeArg(value, "task!");
        }
        List<Value> written = given.remaining();
        if (written.isEmpty() || !(written.getFirst() instanceof BlockValue spec)
                || spec.datatype() != Datatype.BLOCK) {
            return TaskValue.running(given);
        }
        if (written.size() < 2 || !(written.get(1) instanceof BlockValue body)
                || body.datatype() != Datatype.BLOCK) {
            return raiseBadMakeArg(value, "task!");
        }
        TaskValue task = TaskValue.running(body);
        List<Value> fields = spec.remaining();
        for (int at = 0; at + 1 < fields.size(); at++) {
            if (fields.get(at) instanceof WordValue field
                    && field.datatype() == Datatype.SET_WORD
                    && task.context().holds(field.canonical())) {
                task.context().set(field.canonical(), fields.get(at + 1));
            }
        }
        return task;
    }

    private static Value aTimeMadeFrom(Value value) {
        return switch (value) {
            case TimeValue already -> already;
            case StringValue written when value.datatype() == Datatype.STRING ->
                    theTimeScannedFrom(written.text(), written);
            case BlockValue parts when value.datatype() == Datatype.BLOCK
                    || value.datatype() == Datatype.PAREN ->
                    aTimeOfHoursMinutesAndSeconds(parts);
            case Value number when number.datatype() == Datatype.INTEGER
                    || number.datatype() == Datatype.DECIMAL ->
                    aDurationOfSeconds(number);
            default -> raiseBadMakeArg(value, "time!");
        };
    }

    private static final long MOST_SECONDS_A_TIME_HOLDS = 9_223_372_036L;

    private static Value aTimeOfHoursMinutesAndSeconds(BlockValue parts) {
        List<Value> given = parts.remaining();
        if (given.isEmpty() || given.size() > 3
                || !(given.getFirst() instanceof IntegerValue hours)) {
            return raiseBadMakeArg(parts, "time!");
        }
        boolean negated = hours.magnitude() < 0;
        long seconds = whatFitsInThirtyTwoBits(Math.abs(hours.magnitude()), parts) * 3600L;
        double fraction = 0.0;
        for (int at = 1; at < given.size(); at++) {
            if (seconds > MOST_SECONDS_A_TIME_HOLDS) {
                return raiseBadMakeArg(parts, "time!");
            }
            Value part = given.get(at);
            if (at == 2 && part.datatype() == Datatype.DECIMAL) {
                fraction = ((DecimalValue) part).quantity();
                if (seconds + (long) fraction + 1 > MOST_SECONDS_A_TIME_HOLDS) {
                    return raiseBadMakeArg(parts, "time!");
                }
                break;
            }
            if (!(part instanceof IntegerValue whole) || whole.magnitude() < 0) {
                return raiseBadMakeArg(parts, "time!");
            }
            seconds += whatFitsInThirtyTwoBits(whole.magnitude(), parts)
                    * (at == 1 ? 60L : 1L);
        }
        if (seconds > MOST_SECONDS_A_TIME_HOLDS) {
            return raiseBadMakeArg(parts, "time!");
        }
        long nanoseconds = seconds * TimeValue.NANOSECONDS_PER_SECOND
                + Math.round(fraction * TimeValue.NANOSECONDS_PER_SECOND);
        return TimeValue.ofNanoseconds(negated ? -nanoseconds : nanoseconds);
    }

    private static long whatFitsInThirtyTwoBits(long magnitude, Value about) {
        if (magnitude > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, about);
        }
        return magnitude;
    }

    private static final int LONGEST_TIME_A_STRING_MAY_SPELL = 30;

    private static Value theTimeScannedFrom(String text, Value given) {
        String content = theRunOfCharactersBetweenTheSpacesOf(text);
        Long nanoseconds = timeScannedFrom(content);
        if (nanoseconds == null) {
            return raiseBadMakeArg(given, "time!");
        }
        return TimeValue.ofNanoseconds(nanoseconds);
    }

    private static String theRunOfCharactersBetweenTheSpacesOf(String text) {
        int from = 0;
        while (from < text.length() && isSpaceOrTab(text.charAt(from))) {
            from++;
        }
        int to = from;
        while (to < text.length() && !isSpaceOrTab(text.charAt(to))) {
            if (text.charAt(to) > ASCII_ENDS_AT) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
            }
            to++;
        }
        if (to == from) {
            throw Raised.of(EvaluationFailure.TOO_SHORT, text);
        }
        if (to - from > LONGEST_TIME_A_STRING_MAY_SPELL) {
            throw Raised.of(EvaluationFailure.TOO_LONG, text);
        }
        for (int after = to; after < text.length(); after++) {
            if (!isSpaceOrTab(text.charAt(after))) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
            }
        }
        return text.substring(from, to);
    }

    private static final char ASCII_ENDS_AT = 127;

    private static Long timeScannedFrom(String content) {
        ScanningATime scanning = new ScanningATime(content);
        return scanning.readsATime() ? scanning.nanoseconds() : null;
    }

    private static Value scalarOf(Value value) {
        return value instanceof TimeValue time
                ? DecimalValue.of(time.nanoseconds())
                : value;
    }

    private static Value timeBetween(DateValue from, DateValue to) {
        long days = DateActions.dayNumberOf(from) - DateActions.dayNumberOf(to);
        return TimeValue.ofNanoseconds(days * TimeValue.NANOSECONDS_PER_DAY);
    }

    private void defineComparison() {
        asksAbout("equal?", Comparison.Strictness.EQUAL, true);
        asksAbout("not-equal?", Comparison.Strictness.EQUAL, false);
        asksAbout("equiv?", Comparison.Strictness.EQUIV, true);
        asksAbout("not-equiv?", Comparison.Strictness.EQUIV, false);
        asksAbout("strict-equal?", Comparison.Strictness.STRICT_EQUAL, true);
        asksAbout("strict-not-equal?", Comparison.Strictness.STRICT_EQUAL, false);
        asksAboutOrder("greater-or-equal?", Comparison.Strictness.GREATER_OR_EQUAL, true);
        asksAboutOrder("lesser?", Comparison.Strictness.GREATER_OR_EQUAL, false);
        asksAboutOrder("greater?", Comparison.Strictness.GREATER, true);
        asksAboutOrder("lesser-or-equal?", Comparison.Strictness.GREATER, false);
        asksAbout("same?", Comparison.Strictness.SAME, true);
    }

    private void asksAbout(String name, Comparison.Strictness strictness, boolean asAsked) {
        define(name, takesAnything("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(asAsked
                        == Comparison.holds(arguments.get(0), arguments.get(1), strictness)));
    }

    private void asksAboutOrder(String name, Comparison.Strictness strictness, boolean asAsked) {
        define(name, takes("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(asAsked
                        == Comparison.holds(arguments.get(0), arguments.get(1), strictness)));
    }

    private void defineControl() {
        define("if", List.of(Parameter.required("condition", ANYTHING),
                        Parameter.required("branch", ANYTHING)),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    if (!arguments.get(0).isTruthy()) {
                        return NoneValue.none();
                    }
                    return branchTaken(arguments.get(1), evaluator, context, refinements);
                });

        define("either", List.of(Parameter.required("condition", ANYTHING),
                        Parameter.required("true-branch", ANYTHING),
                        Parameter.required("false-branch", ANYTHING)),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> branchTaken(
                        arguments.get(0).isTruthy() ? arguments.get(1) : arguments.get(2),
                        evaluator, context, refinements));

        define("not", takesAnything("value"),
                (arguments, evaluator, context) -> {
                    return LogicValue.of(!arguments.get(0).isTruthy());
                });

        define("do", List.of(Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("args", "arg", Set.of()),
                        Parameter.belongingTo("next", "var", Set.of(Datatype.WORD))),
                Set.of("next", "args"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("args") && arguments.size() > 1) {
                        Value given = argumentFor("args", List.of("args", "next"),
                                arguments, refinements, 1);
                        recordTheScriptArguments(evaluator, given);
                    }
                    if (refinements.contains("next") && arguments.size() > 1
                            && arguments.getLast() instanceof WordValue var) {
                        Value value = arguments.getFirst();
                        BlockValue stepping = switch (value) {
                            case BlockValue b when b.datatype() == Datatype.BLOCK
                                    || b.datatype() == Datatype.PAREN -> b;
                            case StringValue s when s.datatype() == Datatype.STRING ->
                                    loadedForStepping(s.text(), context);
                            default -> null;
                        };
                        if (stepping == null) {
                            slotOf(var).setValue(NoneValue.none());
                            return value;
                        }
                        if (stepping.atTail()) {
                            slotOf(var).setValue(stepping);
                            return UnsetValue.unset();
                        }
                        Evaluator.Step taken =
                                evaluator.evaluateNextOrRaise(stepping, context);
                        slotOf(var).setValue(stepping.atIndex(taken.nextIndex()));
                        return taken.value();
                    }
                    return switch (arguments.getFirst()) {
                        case BlockValue block when block.datatype() == Datatype.BLOCK
                                || block.datatype() == Datatype.PAREN ->
                                evaluator.evaluateOrRaise(block, context);
                        case StringValue address when address.datatype() == Datatype.FILE
                                || address.datatype() == Datatype.URL ->
                                runAsAScript(address, evaluator);
                        case StringValue text -> {
                            try {
                                yield evaluator.evaluateSource(text.text());
                            } catch (ReturnSignal returned) {
                                yield returned.value();
                            }
                        }
                        case BinaryValue bytes ->
                                doneAsAScript(bytes, evaluator, context);
                        case ErrorValue built -> throw new Raised(built);
                        case WordValue word when word.datatype() == Datatype.WORD
                                || word.datatype() == Datatype.GET_WORD ->
                                evaluator.valueOfWordIn(word, context);
                        case WordValue quoted when quoted.datatype() == Datatype.LIT_WORD ->
                                quoted.as(Datatype.WORD);
                        case BlockValue quoted when quoted.datatype() == Datatype.LIT_PATH ->
                                quoted.as(Datatype.PATH);
                        case BlockValue path when path.datatype() == Datatype.PATH ->
                                evaluator.valueOfPathIn(path, context);
                        case WordValue assigning
                                when assigning.datatype() == Datatype.SET_WORD ->
                                raiseHalfAnExpression(assigning);
                        case BlockValue assigning
                                when assigning.datatype() == Datatype.SET_PATH ->
                                raiseHalfAnExpression(assigning);
                        default -> arguments.getFirst();
                    };
                });

        define("any", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    BlockValue block = (BlockValue) arguments.get(0);
                    BlockValue at = block;
                    while (!at.atTail()) {
                        Evaluator.Step step =
                                evaluator.evaluateNextOrRaise(at, context);
                        at = at.atIndex(step.nextIndex());
                        if (step.value() instanceof UnsetValue) {
                            continue;
                        }
                        if (step.value().isTruthy()) {
                            return step.value();
                        }
                    }
                    return NoneValue.none();
                });

        define("all", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    BlockValue at = (BlockValue) arguments.get(0);
                    Value last = UnsetValue.unset();
                    while (!at.atTail()) {
                        Evaluator.Step step =
                                evaluator.evaluateNextOrRaise(at, context);
                        at = at.atIndex(step.nextIndex());
                        if (step.value() instanceof UnsetValue) {
                            continue;
                        }
                        if (!step.value().isTruthy()) {
                            return NoneValue.none();
                        }
                        last = step.value();
                    }
                    return last;
                });

        define("unless", List.of(Parameter.required("condition", ANYTHING),
                        Parameter.required("branch", ANYTHING)),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0).isTruthy()) {
                        return NoneValue.none();
                    }
                    return branchTaken(arguments.get(1), evaluator, context, refinements);
                });

        define("switch", List.of(Parameter.required("value"),
                        Parameter.required("choices", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("default", "fallback", Set.of(Datatype.BLOCK))),
                Set.of("case", "default", "all"),
                (arguments, evaluator, context, refinements) -> {
                    List<Value> choices = ((BlockValue) arguments.get(1)).remaining();
                    boolean runsThemAll = refinements.contains("all");
                    boolean matchedSomething = false;
                    Value lastBranchTaken = NoneValue.none();
                    for (int at = 0; at < choices.size(); at++) {
                        if (isExactlyABlock(choices.get(at))) {
                            continue;
                        }
                        boolean chosen = refinements.contains("case")
                                ? choices.get(at).equals(arguments.get(0))
                                : Comparison.looselyEqual(choices.get(at), arguments.get(0));
                        if (!chosen) {
                            continue;
                        }
                        int branchAt = at;
                        while (branchAt < choices.size()
                                && !isExactlyABlock(choices.get(branchAt))) {
                            branchAt++;
                        }
                        if (branchAt >= choices.size()) {
                            break;
                        }
                        matchedSomething = true;
                        lastBranchTaken = evaluator.evaluateOrRaise(
                                (BlockValue) choices.get(branchAt), context);
                        if (!runsThemAll) {
                            return lastBranchTaken;
                        }
                        at = branchAt;
                    }
                    if (matchedSomething) {
                        return lastBranchTaken;
                    }
                    Value fallback = argumentFor(
                            "default", List.of("default"), arguments, refinements, 2);
                    if (fallback instanceof BlockValue branch) {
                        return evaluator.evaluateOrRaise(branch, context);
                    }
                    return NoneValue.none();
                });

        define("case", List.of(Parameter.required("choices", Set.of(Datatype.BLOCK))),
                Set.of("all"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue choices = (BlockValue) arguments.get(0);
                    BlockValue at = choices;
                    boolean runsThemAll = refinements.contains("all");
                    Value lastTaken = NoneValue.none();

                    while (!at.atTail()) {
                        Evaluator.Step condition = evaluator.evaluateNextOrRaise(at, context);
                        BlockValue afterCondition = at.atIndex(condition.nextIndex());
                        if (!condition.value().isTruthy()) {
                            at = afterCondition.atTail()
                                    ? afterCondition
                                    : afterCondition.atIndex(afterCondition.index() + 1);
                            continue;
                        }
                        if (afterCondition.atTail()) {
                            return LogicValue.of(true);
                        }
                        Evaluator.Step branch =
                                evaluator.evaluateNextOrRaise(afterCondition, context);
                        lastTaken = branch.value() instanceof BlockValue block
                                ? evaluator.evaluateOrRaise(block, context)
                                : branch.value();
                        at = afterCondition.atIndex(branch.nextIndex());
                        if (!runsThemAll || at.atTail()) {
                            return lastTaken;
                        }
                    }
                    return NoneValue.none();
                });

        define("attempt", List.of(
                        Parameter.required("block", Set.of(Datatype.BLOCK, Datatype.PAREN))),
                Set.of("safer"),
                (arguments, evaluator, context, refinements) -> {
                    try {
                        return evaluator.evaluateOrRaise(
                                (BlockValue) arguments.getFirst(), context);
                    } catch (Raised raised) {
                        return NoneValue.none();
                    } catch (ThrownSignal | LoopSignal | ContinueSignal | ReturnSignal escaping) {
                        if (!refinements.contains("safer")) {
                            throw escaping;
                        }
                        return NoneValue.none();
                    }
                });

        define("try", List.of(
                        Parameter.required("block", Set.of(Datatype.BLOCK, Datatype.PAREN)),
                        Parameter.belongingTo("with", "handler", Set.of())),
                Set.of("all", "with"),
                (arguments, evaluator, context, refinements) -> {
                    runState.set("last-error", NoneValue.none());
                    Value failure;
                    try {
                        return evaluator.evaluateOrRaise(
                                (BlockValue) arguments.getFirst(), context);
                    } catch (Raised raised) {
                        failure = raised.error();
                    } catch (ThrownSignal thrown) {
                        if (!refinements.contains("all")) {
                            throw thrown;
                        }
                        failure = ErrorValue.about(ErrorCategory.THROW, "throw",
                                "a throw that nothing caught",
                                thrown.value(),
                                thrown.name().<Value>map(WordValue::of)
                                        .orElseGet(NoneValue::none),
                                NoneValue.none());
                    } catch (LoopSignal stopped) {
                        if (!refinements.contains("all")) {
                            throw stopped;
                        }
                        failure = ErrorValue.of(ErrorCategory.THROW, "break",
                                "a break outside a loop");
                    } catch (ContinueSignal skipped) {
                        if (!refinements.contains("all")) {
                            throw skipped;
                        }
                        failure = ErrorValue.of(ErrorCategory.THROW, "continue",
                                "a continue outside a loop");
                    } catch (ReturnSignal returned) {
                        if (!refinements.contains("all")) {
                            throw returned;
                        }
                        failure = ErrorValue.about(ErrorCategory.THROW, "return",
                                "a return outside a function",
                                returned.value() instanceof UnsetValue
                                        ? NoneValue.none()
                                        : returned.value());
                    }
                    runState.set("last-error", failure);
                    if (refinements.contains("with")) {
                        Value handler = arguments.getLast();
                        return handler instanceof BlockValue block
                                ? evaluator.evaluateOrRaise(block, context)
                                : evaluator.applyFunction(handler, List.of(failure));
                    }
                    return failure;
                });
    }


    private void defineNonLocalExit() {
        define("return", takesAnything("value"),
                (arguments, evaluator, context) -> {
                    throw new ReturnSignal(arguments.get(0));
                });

        define("exit", List.of(),
                (arguments, evaluator, context) -> {
                    throw new ReturnSignal(UnsetValue.unset());
                });

        define("throw", List.of(Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("name", "word", Set.of(Datatype.WORD))),
                Set.of("name"),
                (arguments, evaluator, context, refinements) -> {
                    throw new ThrownSignal(arguments.getFirst(),
                            refinements.contains("name") && arguments.size() > 1
                                    ? ((WordValue) arguments.get(1)).canonical()
                                    : null);
                });

        define("catch", List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("name", "word",
                                Set.of(Datatype.WORD, Datatype.BLOCK)),
                        Parameter.belongingTo("with", "callback", Set.of())),
                Set.of("name", "all", "quit", "with"),
                (arguments, evaluator, context, refinements) -> {
                    Value handled;
                    Value carriedName = NoneValue.none();
                    try {
                        return evaluator.evaluateOrRaise(
                                (BlockValue) arguments.getFirst(), context);
                    } catch (ThrownSignal thrown) {
                        boolean catchesQuitOnly = refinements.contains("quit")
                                && !refinements.contains("name")
                                && !refinements.contains("all");
                        if (catchesQuitOnly
                                || (!refinements.contains("all")
                                        && !answersTo(thrown,
                                                expectedNames(arguments, refinements)))) {
                            throw thrown;
                        }
                        handled = thrown.value();
                        carriedName = thrown.name()
                                .<Value>map(WordValue::of)
                                .orElseGet(NoneValue::none);
                    } catch (QuitRequested quit) {
                        if (!refinements.contains("quit")) {
                            throw quit;
                        }
                        handled = quit.answer();
                        runState.set("quit?", LogicValue.of(true));
                    } catch (HaltRequested halted) {
                        if (!refinements.contains("quit")) {
                            throw halted;
                        }
                        handled = UnsetValue.unset();
                    }
                    runState.set("last-result", handled);
                    if (refinements.contains("with")) {
                        Value handler = arguments.getLast();
                        if (handler instanceof BlockValue block) {
                            Value answered = evaluator.evaluateOrRaise(block, context);
                            runState.set("last-result", answered);
                            return answered;
                        }
                        return evaluator.applyToCaught(handler, handled, carriedName);
                    }
                    return handled;
                });
    }

    private void defineFunctionMaking() {
        Transcoder.buildFunctionsWith(
                (spec, body) -> makeFunction(spec, body, Context.root()));
        Transcoder.makeValuesWith(this::constructionOf);
        define("func", List.of(
                        Parameter.required("spec", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> makeFunction(
                        (BlockValue) arguments.get(0),
                        (BlockValue) arguments.get(1),
                        context));

        define("function", List.of(
                        Parameter.required("spec", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("with", "object", Set.of())),
                Set.of("with", "extern"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue spec = (BlockValue) arguments.get(0);
                    BlockValue body = (BlockValue) arguments.get(1);
                    Context inside = context;
                    if (refinements.contains("with") && arguments.size() > 2) {
                        Value given = arguments.get(2);
                        inside = given instanceof ObjectValue object
                                ? object.context()
                                : ((ObjectValue) makeObject(evaluator, context,
                                        Optional.empty(), (BlockValue) given)).context();
                    }
                    List<Value> combined = new ArrayList<>(spec.remaining());
                    List<Value> assigned = new ArrayList<>();
                    gatherWords(body, true, true, assigned);
                    Context enclosing = inside;
                    assigned.removeIf(word -> enclosing != context
                            && enclosing.holds(((WordValue) word).canonical()));
                    if (!assigned.isEmpty()) {
                        combined.add(WordValue.of("local", Datatype.REFINEMENT));
                        combined.addAll(assigned);
                    }
                    return makeFunction(BlockValue.block(combined), body, inside);
                });
    }

    private static Value makeFunction(BlockValue spec, BlockValue body, Context context) {
        return withItsBodyBound(new FunctionValue(
                spec,
                body,
                FunctionSpec.parametersIn(spec),
                FunctionSpec.localNamesIn(spec),
                context));
    }

    static FunctionValue withItsBodyBound(FunctionValue made) {
        made.declaredWords().markAsCallFrameOf(made);
        Set<String> declared = theNamesDeclaredBy(made);
        declared.forEach(made.declaredWords()::define);
        Binder.bindEachInPlace(made.body(), made.declaredWords(), declared);
        return made;
    }

    private static Set<String> theNamesDeclaredBy(FunctionValue function) {
        Set<String> declared = new java.util.HashSet<>();
        function.parameters().forEach(
                parameter -> declared.add(Context.canonicalise(parameter.name())));
        function.localNames().forEach(
                name -> declared.add(Context.canonicalise(name)));
        return declared;
    }

    private Value madeFrom(
            Value prototype, Value body, Evaluator evaluator, Context context) {

        return switch (prototype) {
            case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT
                    && body instanceof NoneValue ->
                    raiseBadMakeArg(body, "object!");
            case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT
                    && !(body instanceof BlockValue) ->
                    makeObject(evaluator, context, Optional.empty(),
                            BlockValue.block(List.of()));
            case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT ->
                    makeObject(evaluator, context, Optional.empty(), (BlockValue) body);
            case ObjectValue original when body instanceof ObjectValue other ->
                    mergedObject(original, other, context);
            case ObjectValue original ->
                    makeObject(evaluator, context, Optional.of(original), (BlockValue) body);
            case DatatypeValue wanted when wanted.represents() == Datatype.MAP ->
                    mapMadeFrom(body);
            case DatatypeValue wanted when wanted.represents() == Datatype.BITSET ->
                    BitsetActions.madeFrom(body);
            case DatatypeValue wanted when wanted.represents() == Datatype.PAIR ->
                    asPair(body);
            case DatatypeValue wanted when wanted.represents() == Datatype.FUNCTION ->
                    functionFrom(body, context);
            case DatatypeValue wanted when wanted.represents() == Datatype.CLOSURE ->
                    functionFrom(body, context) instanceof FunctionValue made
                            ? made.asClosure()
                            : NoneValue.none();
            case DatatypeValue wanted when wanted.represents() == Datatype.OP ->
                    operatorFrom(body, context);
            case DatatypeValue wanted when wanted.represents() == Datatype.ERROR ->
                    errorFromSpec(body, evaluator, context);
            case ErrorValue original when body instanceof StringValue ->
                    errorFromSpec(body, evaluator, context);
            case DatatypeValue wanted when wanted.represents() == Datatype.MODULE ->
                    moduleFromSpec(body, evaluator, context);
            case NativeValue original when body instanceof BlockValue given ->
                    derivedFunction(original, given);
            case FunctionValue original when body instanceof BlockValue given ->
                    derivedFunction(original, given);
            case SeriesValue original -> makeOfDatatype(
                    DatatypeValue.of(original.datatype()), body, evaluator, context);
            case EventValue original -> EventPath.made(original, body,
                    value -> simpleValueOf(value, evaluator, context));
            case StructValue original ->
                    structLikeThePrototype(original, body, evaluator);
            case DatatypeValue wanted -> makeOfDatatype(wanted, body, evaluator, context);
            default -> makeOfDatatype(
                    DatatypeValue.of(prototype.datatype()), body, evaluator, context);
        };
    }

    private void defineObjects() {
        define("make", takesAnything("prototype", "body"),
                (arguments, evaluator, context) ->
                        madeFrom(arguments.get(0), arguments.get(1), evaluator, context));

        define("construct", List.of(
                        Parameter.required("body", Set.of(Datatype.BLOCK,
                                Datatype.STRING, Datatype.BINARY)),
                        Parameter.belongingTo("with", "object", Set.of(Datatype.OBJECT))),
                Set.of("only", "with"),
                (arguments, evaluator, context, refinements) -> {
                    Context built = Context.childOf(evaluator.systemContext());
                    Value body = arguments.getFirst();
                    List<Value> items = body instanceof BlockValue block
                            ? block.remaining()
                            : headerFieldsIn(body instanceof StringValue text
                                    ? text.text()
                                    : textOfBytes((BinaryValue) body));
                    if (refinements.contains("with") && arguments.size() > 1
                            && arguments.get(1) instanceof ObjectValue prototype) {
                        prototype.context().fieldsExcludingSelf()
                                .forEach(built::set);
                    }
                    constructInto(built, items, refinements.contains("only"));
                    return new ObjectValue(built);
                });

        define("context?", List.of(Parameter.required("word", Typeset.ANY_WORD.members())),
                (arguments, evaluator, context) -> {
                    WordValue word = (WordValue) arguments.getFirst();
                    if (!word.isBound() || word.binding().isALoopFrame()) {
                        return NoneValue.none();
                    }
                    if (word.binding().functionOwningThisFrame() != null) {
                        if (word.binding().callHasEnded()) {
                            return evaluator.systemContext().knows("do")
                                    ? evaluator.systemContext().slotFor("do").value()
                                    : NoneValue.none();
                        }
                        return word.binding().functionOwningThisFrame();
                    }
                    return new ObjectValue(word.binding());
                });

        define("resolve", List.of(
                        Parameter.required("target", Typeset.ANY_OBJECT.members()),
                        Parameter.required("source", Typeset.ANY_OBJECT.members()),
                        Parameter.belongingTo("only", "from",
                                Set.of(Datatype.BLOCK, Datatype.INTEGER))),
                Set.of("only", "all", "extend"),
                (arguments, evaluator, context, refinements) -> {
                    Context into = fieldsOf(arguments.getFirst());
                    if (into.isClosedToNewNames()) {
                        throw Raised.of(EvaluationFailure.PROTECTED, "resolve");
                    }
                    return resolvedFrom(into, fieldsOf(arguments.get(1)),
                            arguments.getFirst(), refinements,
                            argumentFor("only", List.of("only"),
                                    arguments, refinements, 2));
                });

        define("use", List.of(
                        Parameter.required("words", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    Context scope = Context.childOf(context);
                    for (Value item : ((BlockValue) arguments.get(0)).remaining()) {
                        if (!(item instanceof WordValue word)) {
                            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                    "use names words, not "
                                            + item.datatype().literalSpelling());
                        }
                        scope.define(word.spelling());
                    }
                    return evaluator.evaluateOrRaise(
                            Binder.bind((BlockValue) arguments.get(1), scope), scope);
                });

        define("context", List.of(Parameter.required("body", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> makeObject(
                        evaluator, context, Optional.empty(), (BlockValue) arguments.get(0)));

        define("in", List.of(
                        Parameter.required("object", Set.of(Datatype.OBJECT, Datatype.ERROR,
                                Datatype.PORT, Datatype.MODULE, Datatype.TASK,
                                Datatype.BLOCK)),
                        Parameter.required("word", Set.of(Datatype.WORD, Datatype.LIT_WORD,
                                Datatype.GET_WORD, Datatype.SET_WORD, Datatype.REFINEMENT,
                                Datatype.ISSUE, Datatype.BLOCK, Datatype.PAREN))),
                (arguments, evaluator, context) -> {
                    if (arguments.getFirst() instanceof BlockValue searched
                            && searched.datatype() != Datatype.PATH) {
                        return firstHolderIn(searched, arguments.get(1), evaluator, context);
                    }
                    Context frame = contextOf(arguments.getFirst());
                    if (arguments.get(1) instanceof BlockValue body) {
                        return Binder.bindInPlace(body, frame);
                    }
                    WordValue word = (WordValue) arguments.get(1);
                    if (!frame.holds(word.canonical())) {
                        return NoneValue.none();
                    }
                    return word.boundTo(frame);
                });

        define("apply", List.of(Parameter.required("func"),
                        Parameter.required("block", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue given = (BlockValue) arguments.get(1);
                    List<Value> supplied = refinements.contains("only")
                            ? new ArrayList<>(given.remaining())
                            : new ArrayList<>(
                                    evaluator.evaluateEachOrRaise(given, context));
                    Value callee = arguments.get(0);
                    while (callee instanceof NativeValue builtIn
                            && builtIn.nativeName().equals("do")
                            && !supplied.isEmpty()
                            && supplied.getFirst().datatype().isAnyFunction()) {
                        callee = supplied.removeFirst();
                    }
                    if (callee instanceof NativeValue builtIn
                            && !builtIn.declaredRefinements().isEmpty()) {
                        return applyWithRefinements(builtIn, supplied, evaluator);
                    }
                    int wanted = (int) arityOf(callee);
                    List<Value> exactly = new ArrayList<>(
                            supplied.subList(0, Math.min(wanted, supplied.size())));
                    while (exactly.size() < wanted) {
                        exactly.add(NoneValue.none());
                    }
                    return evaluator.applyFunction(callee, exactly);
                });

        define("assert", List.of(Parameter.required("conditions", Set.of(Datatype.BLOCK))),
                Set.of("type"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("type")) {
                        return assertedTypes(
                                (BlockValue) arguments.get(0), evaluator, context);
                    }
                    return everyConditionHeld(
                            (BlockValue) arguments.getFirst(), evaluator, context);
                });

        define("hash", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> IntegerValue.of(
                        Molder.mold(arguments.get(0)).hashCode()));

        define("collect-words", List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("ignore", "words",
                                anyObjectOr(Datatype.BLOCK, Datatype.NONE)),
                        Parameter.belongingTo("as", "type", Set.of(Datatype.DATATYPE))),
                Set.of("deep", "set", "ignore", "as"),
                (arguments, evaluator, context, refinements) -> {
                    List<Value> found = new ArrayList<>();
                    gatherWords((BlockValue) arguments.get(0), refinements.contains("deep"),
                            refinements.contains("set"), found);
                    Value ignoring = argumentFor(
                            "ignore", List.of("ignore", "as"), arguments, refinements, 1);
                    if (ignoring != null && !(ignoring instanceof NoneValue)) {
                        Set<String> known = namesIn(ignoring);
                        found.removeIf(word -> word instanceof WordValue spelt
                                && known.contains(spelt.canonical()));
                    }
                    if (refinements.contains("as")) {
                        Value wanted = argumentFor(
                                "as", List.of("ignore", "as"), arguments, refinements, 1);
                        if (!(wanted instanceof DatatypeValue wantedType)
                                || !ANY_WORD_DATATYPES.contains(wantedType.represents())) {
                            throw Raised.of(EvaluationFailure.BAD_FUNC_ARG, "as");
                        }
                        found.replaceAll(word -> word instanceof WordValue spelt
                                ? spelt.as(wantedType.represents())
                                : word);
                    }
                    return BlockValue.block(found);
                });

        define("new-line", List.of(
                        Parameter.required("position", Set.of(Datatype.BLOCK, Datatype.PAREN)),
                        Parameter.required("value"),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("all", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue block = (BlockValue) arguments.get(0);
                    boolean wanted = arguments.get(1).isTruthy();
                    int stride = -1;
                    if (refinements.contains("all")) {
                        stride = 1;
                    }
                    if (refinements.contains("skip") && arguments.size() > 2
                            && arguments.get(2) instanceof IntegerValue size) {
                        if (size.magnitude() < 1) {
                            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                    Molder.mold(size));
                        }
                        stride = (int) size.magnitude();
                    }
                    for (int n = 0; block.index() + n <= block.storageLength(); n++) {
                        boolean marking = stride < 0
                                ? wanted
                                : wanted ^ (n % stride != 0);
                        block.storage().setLineBreakAt(block.index() + n, marking);
                        if (stride < 0) {
                            break;
                        }
                    }
                    return block;
                });
        define("new-line?", List.of(
                        Parameter.required("position", Set.of(Datatype.BLOCK, Datatype.PAREN))),
                (arguments, evaluator, context) -> {
                    BlockValue block = (BlockValue) arguments.get(0);
                    return LogicValue.of(block.storage().breaksLineAt(block.index()));
                });

        define("object", List.of(Parameter.required("spec", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> makeObject(
                        evaluator, context, Optional.empty(), (BlockValue) arguments.get(0)));

        define("with", List.of(
                        Parameter.required("context", Set.of(Datatype.OBJECT)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    Context inside = ((ObjectValue) arguments.get(0)).context();
                    return evaluator.evaluateOrRaise(
                            Binder.bindWhatTheTargetHoldsItself(
                                    (BlockValue) arguments.get(1), inside),
                            context);
                });

        define("selfless?", List.of(Parameter.required("context")),
                (arguments, evaluator, context) -> LogicValue.of(
                        !(arguments.get(0) instanceof ObjectValue object)
                                || !object.context().holds("self")));

        define("protected?", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> LogicValue.of(switch (arguments.get(0)) {
                    case BlockValue path when isAPath(path) -> {
                        ContextSlot field = fieldNamedBy(path.remaining());
                        yield field != null && field.isProtected();
                    }
                    case BlockValue block -> block.storage().isProtected();
                    case StringValue text -> text.storage().isProtected();
                    case BinaryValue bytes -> bytes.storage().isProtected();
                    case MapValue map -> map.isProtected();
                    case ObjectValue object -> object.context().slots().stream()
                            .anyMatch(ContextSlot::isProtected);
                    case WordValue word -> word.isBound()
                            && word.binding().knows(word.canonical())
                            && word.binding().slotFor(word.canonical()).isProtected();
                    default -> false;
                }));

        define("unbind", List.of(Parameter.required("word", aBlockOrAnyWord())),
                Set.of("deep"),
                (arguments, evaluator, context, refinements) ->
                        unbound(arguments.get(0), refinements.contains("deep")));

        define("bind", List.of(
                        Parameter.required("word"),
                        Parameter.required("target")),
                Set.of("copy", "only", "new", "set"),
                (arguments, evaluator, context, refinements) -> {
                    Context target = arguments.get(1) instanceof WordValue word
                            ? boundContextOf(word)
                            : fieldsOf(arguments.get(1));
                    if (target == null) {
                        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                "bind wanted an object or a bound word, not "
                                        + arguments.get(1).datatype().literalSpelling());
                    }
                    if (arguments.get(0) instanceof WordValue word) {
                        if (refinements.contains("new") || refinements.contains("set")) {
                            target.define(word.canonical());
                        }
                        if (!(arguments.get(1) instanceof WordValue)) {
                            if (!target.holds(word.canonical())) {
                                throw Raised.of(EvaluationFailure.NOT_IN_CONTEXT,
                                        word.spelling());
                            }
                            return word.boundTo(target);
                        }
                        if (target.declaresItRelatively(word.canonical())) {
                            return word.boundTo(target);
                        }
                        if (!target.knows(word.canonical())) {
                            throw Raised.of(EvaluationFailure.NOT_IN_CONTEXT,
                                    word.spelling());
                        }
                        return word.boundTo(target.holderOf(word.canonical()));
                    }
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseWrongArgument(arguments.get(0), "bind", "word or block");
                    }
                    boolean deeply = !refinements.contains("only");
                    if (refinements.contains("new") || refinements.contains("set")) {
                        defineFreshWordsOf(
                                block, target, refinements.contains("set"), deeply);
                    }
                    return refinements.contains("copy")
                            ? Binder.bindACopyOfWhatTheTargetHoldsItself(
                                    block, target, deeply)
                            : Binder.bindWhatTheTargetHoldsItself(block, target, deeply);
                });
    }

    private static Value makeObject(
            Evaluator evaluator,
            Context enclosing,
            Optional<ObjectValue> prototype,
            BlockValue body) {

        Context fields = Context.childOf(enclosing);
        ObjectValue built = new ObjectValue(fields);
        fields.set("self", built);

        prototype.ifPresent(existing -> existing.context().slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .forEach(slot -> fields.set(
                        slot.spelling(), clonedAndRebound(slot.value(),
                                Set.of(existing.context()), fields))));

        declaredFieldsIn(body).forEach(fields::define);

        evaluator.evaluateOrRaise(
                Binder.bindOnly(body, fields, itsOwnFieldNames(fields)), fields);
        return built;
    }

    private static Set<String> itsOwnFieldNames(Context fields) {
        return fields.slots().stream()
                .map(ContextSlot::canonical)
                .collect(Collectors.toSet());
    }

    private static Value mergedObject(
            ObjectValue prototype, ObjectValue other, Context enclosing) {

        Context fields = Context.childOf(enclosing);
        prototype.context().slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .forEach(slot -> fields.set(slot.spelling(), slot.value()));
        other.context().slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .forEach(slot -> fields.set(slot.spelling(), slot.value()));

        ObjectValue merged = new ObjectValue(fields);
        fields.set("self", merged);
        Set<Context> sources = Set.of(prototype.context(), other.context());
        fields.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .toList()
                .forEach(slot -> fields.set(slot.spelling(),
                        clonedAndRebound(slot.value(), sources, fields)));
        return merged;
    }

    private static Value clonedAndRebound(Value value, Set<Context> from, Context into) {
        return switch (value) {
            case WordValue word -> word.isBound() && from.contains(word.binding())
                    ? word.boundTo(into)
                    : word;
            case BlockValue block -> {
                List<Value> items = new ArrayList<>();
                for (Value item : block.remaining()) {
                    items.add(clonedAndRebound(item, from, into));
                }
                yield BlockValue.block(items).as(block.datatype());
            }
            case StringValue text -> StringValue.of(text.text(), text.datatype());
            case BinaryValue bytes -> copiedBytes(bytes, bytes.lengthFromHere());
            case MapValue map -> {
                MapValue cloned = map.copy();
                for (Value key : cloned.keys()) {
                    cloned.put(key, clonedAndRebound(cloned.select(key), from, into));
                }
                yield cloned;
            }
            case FunctionValue function -> withItsBodyBound(new FunctionValue(
                    function.spec(),
                    (BlockValue) clonedAndRebound(function.body(), from, into),
                    function.parameters(), function.localNames(), into));
            default -> value;
        };
    }

    private static List<String> declaredFieldsIn(BlockValue body) {
        return body.remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .filter(word -> word.datatype() == Datatype.SET_WORD)
                .map(WordValue::spelling)
                .toList();
    }

    private void defineLoops() {
        define("loop", List.of(
                        Parameter.required("count", Typeset.NUMBER.members()),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    long passes = ((IntegerValue) arguments.get(0)).magnitude();
                    BlockValue body = (BlockValue) arguments.get(1);
                    Value last = NoneValue.none();
                    try {
                        for (long pass = 0; pass < passes; pass++) {
                            last = oneRoundCatchingContinue(evaluator,body, evaluator.systemContext());
                        }
                    } catch (LoopSignal stopped) {
                        return stopped.answer();
                    }
                    return last;
                });

        define("repeat", List.of(
                        Parameter.softQuoted("counter"),
                        Parameter.required("count", WHAT_REPEAT_COUNTS_BY),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    WordValue counter = (WordValue) arguments.get(0);
                    BlockValue body = (BlockValue) arguments.get(2);
                    if (arguments.get(1) instanceof PairValue grid) {
                        return repeatedOverGrid(evaluator, context, counter, grid, body);
                    }
                    if (arguments.get(1) instanceof NoneValue nothing) {
                        return nothing;
                    }
                    if (arguments.get(1) instanceof SeriesValue walked) {
                        return countedLoop(evaluator, context, counter, body,
                                index -> walked.atIndex(walked.index() + (int) index),
                                walked.lengthFromHere());
                    }
                    long passes = (long) Arithmetic.asMagnitude(arguments.get(1));
                    return countedLoop(
                            evaluator, context, counter, body,
                            index -> IntegerValue.of(index + 1), passes);
                });

        define("while", List.of(
                        Parameter.required("condition", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    BlockValue condition = (BlockValue) arguments.get(0);
                    BlockValue body = (BlockValue) arguments.get(1);
                    Value last = NoneValue.none();
                    try {
                        while (theTruthInWhatALoopTests(evaluator.evaluateOrRaise(
                                condition, evaluator.systemContext()))) {
                            last = oneRoundCatchingContinue(evaluator,body, evaluator.systemContext());
                        }
                    } catch (LoopSignal stopped) {
                        return stopped.answer();
                    }
                    return last;
                });

        define("until", List.of(Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    BlockValue body = (BlockValue) arguments.get(0);
                    Value last;
                    try {
                        do {
                            last = oneRoundCatchingContinue(evaluator,body, evaluator.systemContext());
                        } while (!theTruthInWhatALoopTests(last));
                    } catch (LoopSignal stopped) {
                        return stopped.answer();
                    }
                    return last;
                });

        define("for", List.of(
                        Parameter.softQuoted("counter"),
                        Parameter.required("start"),
                        Parameter.required("end"),
                        Parameter.required("step"),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> steppedLoop(
                        evaluator,
                        context,
                        (WordValue) arguments.get(0),
                        arguments.get(1),
                        arguments.get(2),
                        arguments.get(3),
                        (BlockValue) arguments.get(4)));

        define("foreach", List.of(
                        Parameter.softQuoted("target"),
                        Parameter.required("series"),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> forEachLoop(
                        evaluator,
                        context,
                        arguments.get(0),
                        arguments.get(1),
                        (BlockValue) arguments.get(2)));

        define("remove-each", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("series",
                                Set.of(Datatype.BLOCK, Datatype.BINARY,
                                        Datatype.STRING, Datatype.MAP, Datatype.VECTOR)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                Set.of("count"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(1) instanceof MapValue map) {
                        return removedEachPairFrom(
                                map, arguments, refinements, evaluator, context);
                    }
                    if (arguments.get(1) instanceof SeriesValue other
                            && !(other instanceof BlockValue)) {
                        return removedEachFromDecidingForwardsThenRewriting(
                                other, arguments, refinements, evaluator, context);
                    }
                    BlockValue series = (BlockValue) arguments.get(1);
                    Context locals = Context.loopFrameOf(context);
                    List<WordValue> names = loopNamesIn(arguments.get(0), "remove-each");
                    names.forEach(name -> locals.define(name.spelling()));
                    BlockValue bound = Binder.bind((BlockValue) arguments.get(2), locals);
                    List<Value> items = series.remaining();
                    List<Value> kept = new ArrayList<>();
                    int taken = 0;
                    int at = 0;
                    Value stoppedWith = null;
                    while (at < items.size()) {
                        int reached = setLoopNamesFillingWithNonePastTheEnd(
                                locals, names, items, at, series);
                        int through = Math.min(reached, items.size());
                        boolean drop;
                        try {
                            drop = evaluator.evaluateOrRaise(bound, locals).isTruthy();
                        } catch (LoopSignal stopped) {
                            kept.addAll(items.subList(at, items.size()));
                            stoppedWith = stopped.answer();
                            break;
                        }
                        if (drop) {
                            taken += through - at;
                        } else {
                            kept.addAll(items.subList(at, through));
                        }
                        at = reached;
                    }
                    int had = series.lengthFromHere();
                    for (int removed = 0; removed < had; removed++) {
                        series.storage().removeAt(series.index());
                    }
                    for (int back = kept.size(); back > 0; back--) {
                        series.storage().insertAt(series.index(), kept.get(back - 1));
                    }
                    if (stoppedWith != null && !(stoppedWith instanceof UnsetValue)) {
                        return stoppedWith;
                    }
                    return refinements.contains("count")
                            ? IntegerValue.of(taken)
                            : series;
                });

        define("map-each", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("series", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    Context locals = Context.loopFrameOf(context);
                    List<WordValue> names = loopNamesIn(arguments.get(0), "map-each");
                    names.forEach(name -> locals.define(name.spelling()));
                    BlockValue bound = Binder.bind(
                            (BlockValue) arguments.get(2), locals);
                    List<Value> items = itemsOf(arguments.get(1));
                    List<Value> gathered = new ArrayList<>();
                    int at = 0;
                    while (at < items.size()) {
                        at = setLoopNamesFillingWithNonePastTheEnd(
                                locals, names, items, at, arguments.get(1));
                        Value made = evaluator.evaluateOrRaise(bound, locals);
                        if (!(made instanceof UnsetValue)) {
                            gathered.add(made);
                        }
                    }
                    return BlockValue.block(gathered);
                });

        define("forskip", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("size",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> walkBySteps(
                        evaluator,
                        (WordValue) arguments.get(0),
                        (int) Comparison.asDouble(arguments.get(1)),
                        (BlockValue) arguments.get(2)));

        define("forall", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> walkBySteps(
                        evaluator,
                        (WordValue) arguments.get(0),
                        1,
                        (BlockValue) arguments.get(1)));

        define("continue", List.of(),
                (arguments, evaluator, context) -> {
                    throw ContinueSignal.instance();
                });

        define("break", List.of(Parameter.belongingTo("return", "value", ANYTHING)),
                Set.of("return"),
                (arguments, evaluator, context, refinements) -> {
                    throw refinements.contains("return") && !arguments.isEmpty()
                            ? LoopSignal.breakingWith(arguments.getFirst())
                            : LoopSignal.breaking();
                });
    }

    private static Value countedLoop(
            Evaluator evaluator,
            Context within,
            WordValue counter,
            BlockValue body,
            java.util.function.LongFunction<Value> valueAt,
            long passes) {

        Context locals = Context.loopFrameOf(within);
        locals.define(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);
        Value last = NoneValue.none();
        try {
            for (long pass = 0; pass < passes; pass++) {
                locals.set(counter.spelling(), valueAt.apply(pass));
                last = oneRoundCatchingContinue(evaluator,bound, locals);
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }

    private static Value repeatedOverGrid(
            Evaluator evaluator, Context within, WordValue counter,
            PairValue grid, BlockValue body) {

        Context locals = Context.loopFrameOf(within);
        locals.define(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);
        long across = (long) grid.x();
        long down = (long) grid.y();
        Value last = NoneValue.none();
        try {
            for (long onDown = 1; onDown <= down; onDown++) {
                for (long onAcross = 1; onAcross <= across; onAcross++) {
                    locals.set(counter.spelling(), PairValue.of(onAcross, onDown));
                    last = oneRoundCatchingContinue(evaluator,bound, locals);
                }
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }

    private static Value steppedLoop(
            Evaluator evaluator,
            Context within,
            WordValue counter,
            Value start,
            Value end,
            Value step,
            BlockValue body) {

        if (Comparison.asDouble(step) == 0.0) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "a for loop with a step of zero would never end");
        }
        rejectCharacterBound(start);
        rejectCharacterBound(end);

        Context locals = Context.loopFrameOf(within);
        locals.define(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);

        if (start instanceof SeriesValue series) {
            return steppedOverSeries(evaluator, locals, counter, series, end, step, bound);
        }
        if (start instanceof IntegerValue from
                && end instanceof IntegerValue to
                && step instanceof IntegerValue by) {
            return steppedOverWholeNumbers(evaluator, locals, counter,
                    from.magnitude(), to.magnitude(), by.magnitude(), bound);
        }
        return steppedOverRealNumbers(evaluator, locals, counter,
                Comparison.asDouble(start), Comparison.asDouble(end),
                Comparison.asDouble(step), bound);
    }

    private static Value steppedOverWholeNumbers(
            Evaluator evaluator, Context locals, WordValue counter,
            long from, long to, long stepBy, BlockValue body) {

        Value last = NoneValue.none();
        try {
            long at = from;
            while (stepBy > 0 ? at <= to : at >= to) {
                locals.set(counter.spelling(), IntegerValue.of(at));
                last = oneRoundCatchingContinue(evaluator,body, locals);
                at = steppedOrOverflowed(at, stepBy);
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }

    private static long steppedOrOverflowed(long at, long stepBy) {
        try {
            return Math.addExact(at, stepBy);
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "a for loop counter stepped past the integer range");
        }
    }

    private static Value steppedOverRealNumbers(
            Evaluator evaluator, Context locals, WordValue counter,
            double from, double to, double stepBy, BlockValue body) {

        Value last = NoneValue.none();
        try {
            for (double at = from; stepBy > 0 ? at <= to : at >= to; at += stepBy) {
                locals.set(counter.spelling(), DecimalValue.of(at));
                last = oneRoundCatchingContinue(evaluator,body, locals);
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }

    private static Value steppedOverSeries(
            Evaluator evaluator, Context locals, WordValue counter,
            SeriesValue series, Value end, Value step, BlockValue body) {

        int tail = series.storageLength() + 1;
        int endIndex = end instanceof SeriesValue other
                ? other.index()
                : (int) Arithmetic.asMagnitude(end);
        endIndex = Math.max(0, Math.min(endIndex, tail));
        long stepBy = (long) Arithmetic.asMagnitude(step);
        Value last = NoneValue.none();
        try {
            int at = series.index();
            while (stepBy > 0 ? at <= endIndex : at >= endIndex) {
                locals.set(counter.spelling(), series.atIndex(at));
                last = oneRoundCatchingContinue(evaluator,body, locals);
                int landedAt = locals.slotFor(counter.canonical()).value()
                        instanceof SeriesValue moved ? moved.index() : at;
                at = (int) (landedAt + stepBy);
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }

    private static void rejectCharacterBound(Value bound) {
        if (bound instanceof CharacterValue character) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "for does not step a character range, and " + character
                            + " is a character");
        }
    }

    private static Value forEachLoop(
            Evaluator evaluator, Context within,
            Value target, Value series, BlockValue body) {

        List<WordValue> names = loopNamesIn(target, "foreach");
        List<WordValue> taking = namesThatTakeAValue(names);
        MapActions.refuseMoreNamesThanAPairHas(series, taking);
        Supplier<List<Value>> itemsAsTheyStandNow =
                () -> keysOnly(series, taking.size());

        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.define(name.spelling()));
        BlockValue bound = Binder.bind(body, locals);
        Value last = NoneValue.none();

        try {
            int at = 0;
            List<Value> items = itemsAsTheyStandNow.get();
            while (at < items.size()) {
                at = setLoopNamesFillingWithNonePastTheEnd(
                        locals, names, items, at, series);
                last = oneRoundCatchingContinue(evaluator,bound, locals);
                items = itemsAsTheyStandNow.get();
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }


    private static List<WordValue> loopNamesIn(Value target, String nativeName) {
        if (!(target instanceof BlockValue block)) {
            if (target instanceof WordValue single) {
                return List.of(single);
            }
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    nativeName + " walks with a word or a block of words, not a "
                            + target.datatype().literalSpelling());
        }
        if (block.lengthFromHere() == 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, block);
        }
        List<WordValue> names = new ArrayList<>(block.lengthFromHere());
        for (Value item : block.remaining()) {
            if (!(item instanceof WordValue name)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        nativeName + " walks with words, and " + Molder.mold(item)
                                + " is not one");
            }
            names.add(name);
        }
        return List.copyOf(names);
    }

    private static int setLoopNamesFillingWithNonePastTheEnd(
            Context locals, List<WordValue> names, List<Value> items,
            int at, Value walked) {

        int reached = at;
        for (WordValue name : names) {
            if (name.datatype() == Datatype.SET_WORD) {
                locals.set(name.spelling(), positionWithin(walked, reached));
                continue;
            }
            locals.set(name.spelling(),
                    reached < items.size() ? items.get(reached) : NoneValue.none());
            reached++;
        }
        return reached == at ? at + 1 : reached;
    }

    private static Value positionWithin(Value walked, int reached) {
        if (!(walked instanceof SeriesValue series)) {
            return walked;
        }
        return series.atIndex(Math.min(
                series.index() + reached, series.storageLength() + 1));
    }

    private static List<WordValue> namesThatTakeAValue(List<WordValue> names) {
        return names.stream()
                .filter(name -> name.datatype() != Datatype.SET_WORD)
                .toList();
    }

    private static List<Value> keysOnly(Value series, int howManyNames) {
        if (howManyNames != 1) {
            return itemsOf(series);
        }
        return switch (series) {
            case ObjectValue object -> object.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .<Value>map(slot -> WordValue.of(slot.spelling()))
                    .toList();
            case MapValue map -> map.keys();
            default -> itemsOf(series);
        };
    }

    private static Value walkBySteps(
            Evaluator evaluator, WordValue word, int step, BlockValue body) {

        ContextSlot slot = slotOf(word);
        if (slot.value() instanceof NoneValue nothing) {
            return nothing;
        }
        if (!(slot.value() instanceof SeriesValue start)) {
            return raiseCannotUse(slot.value(), "forall");
        }
        if (step == 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a step of zero would never reach the end");
        }
        Datatype walkingA = start.datatype();
        if (step < 0 && start.index() > start.storageLength()) {
            slot.setValue(start.atIndex(start.storageLength() + 1 + step));
        }
        Value last = NoneValue.none();
        try {
            while (slot.value() instanceof SeriesValue here
                    && here.index() >= 1 && here.index() <= here.storageLength()) {
                last = oneRoundCatchingContinue(evaluator,body, evaluator.systemContext());
                if (!(slot.value() instanceof SeriesValue moved)
                        || moved.datatype() != walkingA) {
                    return raiseCannotUse(slot.value(), "forall");
                }
                if (!steppedOnwards(slot, moved, step)) {
                    break;
                }
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        slot.setValue(start);
        return last;
    }

    private static boolean steppedOnwards(
            ContextSlot slot, SeriesValue moved, int step) {

        int next = moved.index() + step;
        if (next > moved.storageLength() && step < 0) {
            next = moved.storageLength() + 1 + step;
        }
        if (next < 1 || next > moved.storageLength()) {
            return false;
        }
        slot.setValue(moved.atIndex(next));
        return true;
    }

    private static List<Value> fieldsAndValuesOf(Context fields) {
        return fields.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .<Value>mapMulti((slot, accept) -> {
                    accept.accept(WordValue.of(slot.spelling()).boundTo(fields));
                    accept.accept(slot.value());
                })
                .toList();
    }

    private static List<Value> itemsOf(Value series) {
        return switch (series) {
            case BlockValue block -> block.remaining();
            case GobValue gob -> gob.storage().pane()
                    .subList(Math.min(gob.index() - 1, gob.storage().length()),
                            gob.storage().length());
            case StringValue text -> text.text().codePoints()
                    .mapToObj(codepoint -> (Value) CharacterValue.of(codepoint))
                    .toList();
            case ObjectValue object -> fieldsAndValuesOf(object.context());
            case PortValue port -> fieldsAndValuesOf(port.context());
            case ModuleValue module -> fieldsAndValuesOf(module.context());
            case BinaryValue binary -> {
                List<Value> octets = new ArrayList<>(binary.lengthFromHere());
                for (int at = 0; at < binary.lengthFromHere(); at++) {
                    octets.add(IntegerValue.of(binary.storage().at(binary.index() + at)));
                }
                yield List.copyOf(octets);
            }
            case MapValue map -> map.walkable();
            case VectorValue vector -> vector.remaining();
            case ImageValue picture -> {
                List<Value> pixels = new ArrayList<>(picture.lengthFromHere());
                for (int at = picture.index(); at <= picture.storageLength(); at++) {
                    int[] channels = picture.storage().pixelAt(at);
                    pixels.add(TupleValue.of(
                            channels[0], channels[1], channels[2], channels[3]));
                }
                yield List.copyOf(pixels);
            }
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "cannot walk " + series.datatype().literalSpelling() + " value");
        };
    }

    private static Value loaded(Value source, boolean unwrapSingle) {
        if (source instanceof BlockValue sources && sources.datatype() == Datatype.BLOCK) {
            List<Value> answers = new ArrayList<>();
            for (Value each : sources.remaining()) {
                answers.add(loaded(each, unwrapSingle));
            }
            return BlockValue.block(answers);
        }
        TranscodeResult read = Transcoder.transcode(textToLoad(source));
        BlockValue values = read.values().orElseThrow(
                () -> new Raised(read.error().orElseThrow()));
        return unwrapSingle && values.remaining().size() == 1
                ? values.first()
                : values;
    }

    private static String textToLoad(Value source) {
        if (source instanceof StringValue text) {
            return text.text();
        }
        if (!(source instanceof BinaryValue bytes)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "load reads a string, a binary or a block of either, not "
                            + source.datatype().literalSpelling());
        }
        String text = strictlyUtf8(bytes.octetsFromHere());
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static String strictlyUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException notText) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS,
                    "the bytes given to load are not valid UTF-8 text");
        }
    }

    private void defineReflection() {
        define("load", takes("source"), Set.of("all"),
                (arguments, evaluator, context, refinements) -> loaded(
                        arguments.get(0), !refinements.contains("all")));

        define("quote", List.of(Parameter.hardQuoted("value")),
                (arguments, evaluator, context) -> arguments.get(0));

        define("shift", List.of(
                        Parameter.required("value", Set.of(Datatype.INTEGER)),
                        Parameter.required("places", Set.of(Datatype.INTEGER))),
                Set.of("logical"),
                (arguments, evaluator, context, refinements) -> IntegerValue.of(
                        refinements.contains("logical")
                                ? bitsShifted(
                                        ((IntegerValue) arguments.get(0)).magnitude(),
                                        ((IntegerValue) arguments.get(1)).magnitude())
                                : shiftedKeepingTheSign(
                                        ((IntegerValue) arguments.get(0)).magnitude(),
                                        ((IntegerValue) arguments.get(1)).magnitude())));

        define("odd?", List.of(Parameter.required("number")),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0) instanceof PairValue pair
                                ? bothHalves(pair, half -> isOdd(roundedHalfUp(half)))
                                : Math.abs(roundedWholeOf(arguments.get(0), "odd?") % 2) == 1));
        define("even?", List.of(Parameter.required("number")),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0) instanceof PairValue pair
                                ? bothHalves(pair, half -> !isOdd(roundedHalfUp(half)))
                                : roundedWholeOf(arguments.get(0), "even?") % 2 == 0));

        define("object?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.OBJECT));
        define("map?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.MAP));
        define("lit-word?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.LIT_WORD));
        define("set-word?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.SET_WORD));
        define("get-word?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.GET_WORD));
        define("refinement?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.REFINEMENT));
        define("ref?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.REF));

        define("datatype?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.DATATYPE));
        define("type?", takesAnything("value"), Set.of("word"),
                (arguments, evaluator, context, refinements) -> refinements.contains("word")
                        ? WordValue.of(arguments.get(0).datatype().literalSpelling())
                        : DatatypeValue.of(arguments.get(0).datatype()));
        define("unset?", takesAnything("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.UNSET));
        define("none?", takesAnything("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.NONE));
        define("error?", takesAnything("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.ERROR));
        for (Datatype datatype : Datatype.values()) {
            Datatype asked = datatype;
            define(datatype.spelling() + "?", takesAnything("value"),
                    (arguments, evaluator, context) -> LogicValue.of(
                            arguments.get(0).datatype() == asked));
        }
        for (Typeset typeset : Typeset.values()) {
            Typeset asked = typeset;
            define(typeset.spelling() + "?", takesAnything("value"),
                    (arguments, evaluator, context) -> LogicValue.of(
                            asked.members().contains(arguments.get(0).datatype())));
        }
        define("true?", takesAnything("value"),
                (arguments, evaluator, context) -> LogicValue.of(arguments.get(0).isTruthy()));
        define("did", takesAnything("value"),
                (arguments, evaluator, context) -> LogicValue.of(arguments.get(0).isTruthy()));

        define("number?", List.of(Parameter.required("value", anythingAtAll())),
                (arguments, evaluator, context) -> LogicValue.of(switch (arguments.get(0)) {
                    case DecimalValue quantity -> !Double.isNaN(quantity.quantity());
                    case IntegerValue whole -> true;
                    case MoneyValue amount -> true;
                    default -> false;
                }));

        defineCodepointRange("ascii?", 0x7F);
        defineCodepointRange("latin1?", 0xFF);
        define("form-oid", List.of(Parameter.required("oid", Set.of(Datatype.BINARY))),
                (arguments, evaluator, context) -> StringValue.of(objectIdentifierWritten(
                        ((BinaryValue) arguments.getFirst()).octetsFromHere())));

        define("binary", List.of(
                        Parameter.required("ctx", Set.of(Datatype.OBJECT,
                                Datatype.BINARY, Datatype.INTEGER, Datatype.NONE)),
                        Parameter.belongingTo("init", "spec", Set.of(Datatype.BINARY,
                                Datatype.INTEGER, Datatype.NONE)),
                        Parameter.belongingTo("write", "data",
                                Set.of(Datatype.BINARY, Datatype.BLOCK)),
                        Parameter.belongingTo("read", "code", Set.of(Datatype.WORD,
                                Datatype.BLOCK, Datatype.INTEGER, Datatype.BINARY)),
                        Parameter.belongingTo("into", "out", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("with", "num", Set.of(Datatype.INTEGER))),
                Set.of("init", "write", "read", "into", "with"),
                (arguments, evaluator, context, refinements) ->
                        theBinaryDialect(arguments, refinements, evaluator, context));

        define("register", List.of(
                        Parameter.hardQuoted("name"),
                        Parameter.required("value", Set.of(Datatype.STRUCT))),
                (arguments, evaluator, context) ->
                        structLayoutFiledUnder(arguments));

        define("xtest", List.of(),
                (arguments, evaluator, context) -> {
                    throw Raised.of(EvaluationFailure.FEATURE_NA,
                            "xtest exercises the C's own handle structures");
                });

        define("premultiply", List.of(
                        Parameter.required("image", Set.of(Datatype.IMAGE))),
                (arguments, evaluator, context) -> {
                    ImageOperations.premultiply((ImageValue) arguments.getFirst());
                    return arguments.getFirst();
                });

        define("blur", List.of(
                        Parameter.required("image", Set.of(Datatype.IMAGE)),
                        Parameter.required("radius", Typeset.NUMBER.members())),
                (arguments, evaluator, context) -> {
                    ImageOperations.blur((ImageValue) arguments.getFirst(),
                            (int) Math.round(Arithmetic.asMagnitude(arguments.get(1))));
                    return arguments.getFirst();
                });

        define("resize", List.of(
                        Parameter.required("image", Set.of(Datatype.IMAGE)),
                        Parameter.required("size", Set.of(Datatype.PAIR,
                                Datatype.PERCENT, Datatype.INTEGER)),
                        Parameter.belongingTo("filter", "name",
                                Set.of(Datatype.WORD, Datatype.INTEGER)),
                        Parameter.belongingTo("blur", "factor", Typeset.NUMBER.members())),
                Set.of("filter", "blur"),
                (arguments, evaluator, context, refinements) ->
                        resizedImage(arguments, refinements));

        define("image-diff", List.of(
                        Parameter.required("a", Set.of(Datatype.IMAGE)),
                        Parameter.required("b", Set.of(Datatype.IMAGE)),
                        Parameter.belongingTo("part", "offset", Set.of(Datatype.PAIR)),
                        Parameter.belongingTo("part", "size", Set.of(Datatype.PAIR))),
                Set.of("part"),
                (arguments, evaluator, context, refinements) ->
                        DecimalValue.percent(theDifferenceBetweenImages(
                                arguments, refinements)));

        define("image", List.of(
                        Parameter.belongingTo("load", "src-file",
                                Set.of(Datatype.FILE, Datatype.BINARY)),
                        Parameter.belongingTo("save", "dst-file",
                                Set.of(Datatype.NONE, Datatype.FILE, Datatype.BINARY)),
                        Parameter.belongingTo("save", "dst-image",
                                Set.of(Datatype.NONE, Datatype.IMAGE)),
                        Parameter.belongingTo("frame", "num", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("as", "type", Set.of(Datatype.WORD))),
                Set.of("load", "save", "frame", "as"),
                (arguments, evaluator, context, refinements) ->
                        theHostsImageCodec(arguments, evaluator, refinements));

        define("generate", List.of(Parameter.required("type", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) ->
                        theKeyGenerateWouldHaveMade((WordValue) arguments.getFirst()));

        define("ecdh", List.of(
                        Parameter.required("key",
                                Set.of(Datatype.HANDLE, Datatype.NONE)),
                        Parameter.belongingTo("init", "type", Set.of(Datatype.WORD)),
                        Parameter.belongingTo("secret", "public-key",
                                Set.of(Datatype.BINARY))),
                Set.of("init", "curve", "public", "secret"),
                (arguments, evaluator, context, refinements) ->
                        ellipticExchange(arguments, refinements));

        define("ecdsa", List.of(
                        Parameter.required("key",
                                Set.of(Datatype.HANDLE, Datatype.BINARY)),
                        Parameter.required("hash", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("verify", "signature",
                                Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("curve", "type", Set.of(Datatype.WORD))),
                Set.of("sign", "verify", "curve"),
                (arguments, evaluator, context, refinements) ->
                        ellipticSignature(arguments, refinements));

        define("dh-init", List.of(
                        Parameter.required("g", Set.of(Datatype.BINARY)),
                        Parameter.required("p", Set.of(Datatype.BINARY))),
                (arguments, evaluator, context) -> DiffieHellmanKey.generatedFor(
                                ((BinaryValue) arguments.get(0)).octetsFromHere(),
                                ((BinaryValue) arguments.get(1)).octetsFromHere())
                        .<Value>map(key -> HandleValue.context(DHM_HANDLE_TYPE,
                                nextCipherIdentity(), JavaObjectValue.of(key)))
                        .orElseGet(NoneValue::none));

        define("dh", List.of(
                        Parameter.required("dh-key", Set.of(Datatype.HANDLE)),
                        Parameter.belongingTo("secret", "public-key",
                                Set.of(Datatype.BINARY))),
                Set.of("public", "secret"),
                (arguments, evaluator, context, refinements) ->
                        modularExchange(arguments, refinements));

        define("rsa-init", List.of(
                        Parameter.required("n", Set.of(Datatype.BINARY)),
                        Parameter.required("e", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("private", "d", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("private", "p", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("private", "q", Set.of(Datatype.BINARY))),
                Set.of("private"),
                (arguments, evaluator, context, refinements) ->
                        rsaKeyBuiltFrom(arguments, refinements));

        define("rsa", List.of(
                        Parameter.required("rsa-key", Set.of(Datatype.HANDLE)),
                        Parameter.required("data", anyStringOr(Datatype.BINARY)),
                        Parameter.belongingTo("verify", "signature", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("hash", "algorithm",
                                Set.of(Datatype.WORD, Datatype.NONE))),
                Set.of("encrypt", "decrypt", "sign", "verify", "hash", "oaep", "pss"),
                (arguments, evaluator, context, refinements) ->
                        rsaOperation(arguments, refinements));

        define("rc4", List.of(
                        Parameter.belongingTo("key", "crypt-key", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("stream", "ctx", Set.of(Datatype.HANDLE)),
                        Parameter.belongingTo("stream", "data", Set.of(Datatype.BINARY))),
                Set.of("key", "stream"),
                (arguments, evaluator, context, refinements) -> {
                    int streamBeginsAt = refinements.contains("key") ? 1 : 0;
                    if (refinements.contains("stream")) {
                        return encipheredThroughTheStreamInPlace(
                                (HandleValue) arguments.get(streamBeginsAt),
                                (BinaryValue) arguments.get(streamBeginsAt + 1));
                    }
                    if (refinements.contains("key")) {
                        return HandleValue.context(RC4_HANDLE_TYPE,
                                nextCipherIdentity(),
                                JavaObjectValue.of(
                                        StreamCipher.keyedWithAnEmptyKeyAcceptedAsAny(
                                                ((BinaryValue) arguments.getFirst())
                                                        .octetsFromHere())));
                    }
                    return UnsetValue.unset();
                });

        define("utf?", List.of(Parameter.required("data", Set.of(Datatype.BINARY))),
                (arguments, evaluator, context) -> IntegerValue.of(
                        byteOrderMarkOf(((BinaryValue) arguments.getFirst())
                                .octetsFromHere())));

        define("invalid-utf?", List.of(Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("utf", "num", Set.of(Datatype.INTEGER))),
                Set.of("utf"),
                (arguments, evaluator, context, refinements) -> {
                    BinaryValue bytes = (BinaryValue) arguments.get(0);
                    int trouble = firstMalformedUtf8(bytes);
                    return trouble < 0 ? NoneValue.none() : bytes.atIndex(trouble);
                });

        define("negative?", takesNumbers("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0) instanceof PairValue pair
                                ? bothHalves(pair, half -> half < 0)
                                : Comparison.asDouble(arguments.get(0)) < 0));
        define("positive?", takesNumbers("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0) instanceof PairValue pair
                                ? bothHalves(pair, half -> half > 0)
                                : Comparison.asDouble(arguments.get(0)) > 0));
        define("zero?", takesAnything("value"),
                (arguments, evaluator, context) ->
                        LogicValue.of(isTheZeroOfItsDatatype(arguments.getFirst())));

        define("value?", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    WordValue word = (WordValue) arguments.get(0);
                    boolean known = word.isBound() && word.binding().knows(word.canonical());
                    return LogicValue.of(known
                            && !word.binding().slotFor(word.canonical()).holdsUnset());
                });

        define("unset", List.of(Parameter.required("word",
                        Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.NONE))),
                (arguments, evaluator, context) -> {
                    if (arguments.get(0) instanceof NoneValue nothing) {
                        return nothing;
                    }
                    switch (arguments.get(0)) {
                        case WordValue word -> {
                            Evaluator.refuseToWriteTheNameAnObjectAnswersToItselfBy(word);
                            slotOf(word).setValue(UnsetValue.unset());
                        }
                        case BlockValue words -> {
                            words.remaining().forEach(
                                    Evaluator::refuseToWriteTheNameAnObjectAnswersToItselfBy);
                            for (Value item : words.remaining()) {
                                if (item instanceof WordValue word) {
                                    slotOf(word).setValue(UnsetValue.unset());
                                }
                            }
                        }
                        default -> { }
                    }
                    return UnsetValue.unset();
                });

        define("protect", List.of(Parameter.required("target")),
                Set.of("deep", "words", "values", "hide", "lock"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("hide")
                            && arguments.getFirst() instanceof WordValue word) {
                        slotOf(word).hide(true);
                        return arguments.getFirst();
                    }
                    if (refinements.contains("hide") && refinements.contains("words")
                            && arguments.getFirst() instanceof BlockValue names
                            && names.datatype() == Datatype.BLOCK) {
                        hideEachWordIn(names);
                        return arguments.getFirst();
                    }
                    if (!protectFieldNamedBy(arguments.getFirst(), true, refinements)) {
                        if (refinements.contains("hide")) {
                            throw Raised.of(EvaluationFailure.BAD_REFINES,
                                    "protect/hide needs a word");
                        }
                        protectNamed(arguments.getFirst(), true, refinements);
                        setProtection(arguments.get(0), true, refinements.contains("deep"),
                                refinements.contains("words"));
                    }
                    return arguments.getFirst();
                });

        define("unprotect", List.of(Parameter.required("target")),
                Set.of("deep", "words", "values"),
                (arguments, evaluator, context, refinements) -> {
                    if (!protectFieldNamedBy(arguments.getFirst(), false, refinements)) {
                        protectNamed(arguments.getFirst(), false, refinements);
                        setProtection(arguments.get(0), false, refinements.contains("deep"),
                                refinements.contains("words"));
                    }
                    return arguments.getFirst();
                });

        define("delect", List.of(
                        Parameter.required("dialect", Set.of(Datatype.OBJECT)),
                        Parameter.required("input", Set.of(Datatype.BLOCK)),
                        Parameter.required("output", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("in", "where", Set.of(Datatype.BLOCK))),
                Set.of("in", "all"),
                (arguments, evaluator, context, refinements) -> {
                    requireChangeable(arguments.get(2));
                    return Delect.read(
                            (ObjectValue) arguments.getFirst(),
                            (BlockValue) arguments.get(1),
                            (BlockValue) arguments.get(2),
                            refinements.contains("all"),
                            evaluator, context);
                });

        defineSet();
    }

    private static final Set<Datatype> PATH_SHAPED = Set.of(
            Datatype.PATH, Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH);

    private static Value writtenThroughPath(BlockValue path, Value supplied) {
        List<Value> segments = path.remaining();
        if (segments.size() < 2 || !(segments.getFirst() instanceof WordValue head)) {
            return raiseCannotUse(path, "set");
        }
        Value holder = slotOf(head).value();
        for (int at = 1; at < segments.size() - 1; at++) {
            if (!(segments.get(at) instanceof WordValue field)
                    || !(holder instanceof ObjectValue object)
                    || !object.context().holds(field.canonical())) {
                return raiseCannotUse(path, "set");
            }
            holder = object.context().slotFor(field.canonical()).value();
        }
        if (!(segments.getLast() instanceof WordValue field)
                || !(holder instanceof ObjectValue object)) {
            return raiseCannotUse(path, "set");
        }
        if (!object.context().holds(field.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, field.spelling());
        }
        object.context().ownSlotFor(field.canonical()).setValue(supplied);
        return supplied;
    }

    private void defineSet() {
        define("set", List.of(
                        Parameter.required("target", NAME_SHAPED),
                        Parameter.required("value", ANYTHING)),
                Set.of("any", "only", "some"),
                (arguments, evaluator, context, refinements) -> {
                    Value target = arguments.getFirst();
                    Value supplied = arguments.get(1);
                    refuseUnassignableName(target, EvaluationFailure.EXPECT_ARG);
                    Evaluator.refuseToWriteTheNameAnObjectAnswersToItselfBy(target);
                    if (!refinements.contains("any")
                            && supplied.datatype() == Datatype.UNSET) {
                        throw Raised.of(EvaluationFailure.NEED_VALUE, target);
                    }
                    if (target instanceof WordValue word) {
                        slotOf(word).setValue(supplied);
                        return supplied;
                    }
                    if (target instanceof BlockValue path
                            && PATH_SHAPED.contains(path.datatype())) {
                        return writtenThroughPath(path, supplied);
                    }
                    List<Value> names = switch (target) {
                        case BlockValue words -> words.remaining();
                        case ObjectValue object -> object.context().slots().stream()
                                .filter(slot -> !slot.canonical().equals("self"))
                                .<Value>map(slot -> WordValue.of(slot.spelling())
                                        .boundTo(object.context()))
                                .toList();
                        default -> null;
                    };
                    if (names != null) {
                        names.forEach(name -> refuseUnassignableName(
                                name, EvaluationFailure.INVALID_ARG));
                        names.forEach(
                                Evaluator::refuseToWriteTheNameAnObjectAnswersToItselfBy);
                    }
                    if (names == null) {
                        return raiseCannotUse(target, "set");
                    }
                    boolean anyValue = refinements.contains("any");
                    if (target instanceof ObjectValue into
                            && supplied instanceof ObjectValue from
                            && !refinements.contains("only")) {
                        setFieldsFromObjectMatchedByName(into, from, refinements);
                        return supplied;
                    }
                    List<Value> values = !refinements.contains("only")
                            && supplied instanceof BlockValue block
                            ? block.remaining()
                            : null;
                    if (!anyValue && values != null) {
                        for (int index = 0; index < names.size()
                                && index < values.size(); index++) {
                            if (values.get(index).datatype() == Datatype.UNSET) {
                                throw Raised.of(EvaluationFailure.NEED_VALUE,
                                        names.get(index));
                            }
                        }
                    }
                    for (int index = 0; index < names.size(); index++) {
                        if (values != null && index >= values.size()
                                && refinements.contains("some")) {
                            break;
                        }
                        Value assigned = values == null
                                ? supplied
                                : index < values.size() ? values.get(index) : NoneValue.none();
                        if (refinements.contains("some") && assigned instanceof NoneValue) {
                            continue;
                        }
                        slotOf((WordValue) names.get(index)).setValue(assigned);
                    }
                    return supplied;
                });

        define("take",
                List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "count", Set.of())),
                Set.of("part", "last", "deep", "all"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof NoneValue nothing) {
                        return nothing;
                    }
                    if (arguments.get(0) instanceof PortValue port
                            && port.schemeName().equals("crypt")) {
                        refuseAClosedCipherPort(port);
                        CryptPort.update(port);
                        return CryptPort.read(port);
                    }
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "take");
                    }
                    if (refinements.contains("all")) {
                        return takeSeveral(series, series.lengthFromHere());
                    }
                    if (!refinements.contains("part")) {
                        Value taken = refinements.contains("last")
                                && series.lengthFromHere() > 0
                                ? takeOne(series.atIndex(
                                        series.index() + series.lengthFromHere() - 1))
                                : takeOne(series);
                        return deepenedIfAsked(taken, refinements);
                    }
                    if (arguments.size() > 1 && arguments.get(1) instanceof SeriesValue upTo) {
                        return deepenedIfAsked(takeSeveral(earlierOf(series, upTo),
                                Math.abs(upTo.index() - series.index())), refinements);
                    }
                    long wanted = arguments.size() > 1
                            ? countUpTo(series, arguments.get(1))
                            : 1;
                    if (wanted < 0) {
                        long back = Math.min(-wanted, series.index() - 1L);
                        series = series.atIndex((int) (series.index() - back));
                        wanted = back;
                    } else {
                        wanted = Math.min(wanted, series.lengthFromHere());
                    }
                    if (refinements.contains("last")) {
                        int tail = series.storageLength() + 1;
                        long from = Math.max(1, tail - wanted);
                        return deepenedIfAsked(
                                takeSeveral(series.atIndex((int) from), wanted), refinements);
                    }
                    return deepenedIfAsked(takeSeveral(series, wanted), refinements);
                });

        define("ajoin", List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("with", "separator", ANYTHING)),
                Set.of("all", "with"),
                (arguments, evaluator, context, refinements) -> {
                    List<Value> all = evaluator.evaluateEachOrRaise(
                            (BlockValue) arguments.get(0), context);
                    List<Value> pieces = all.stream()
                            .filter(piece -> refinements.contains("all")
                                    || !(piece instanceof NoneValue
                                            || piece instanceof UnsetValue))
                            .toList();
                    String separator = refinements.contains("with") && arguments.size() > 1
                            ? Molder.form(arguments.get(1))
                            : "";
                    Datatype kind = all.isEmpty() ? Datatype.STRING : switch (
                            all.getFirst().datatype()) {
                        case FILE, URL, EMAIL, REF -> all.getFirst().datatype();
                        default -> Datatype.STRING;
                    };
                    return StringValue.of(pieces.stream()
                            .map(Natives::runTogether)
                            .collect(Collectors.joining(separator)), kind);
                });

        define("poke", List.of(Parameter.required("series",
                                Set.of(Datatype.BLOCK, Datatype.PAREN, Datatype.HASH,
                                        Datatype.PATH, Datatype.SET_PATH,
                                        Datatype.GET_PATH, Datatype.LIT_PATH,
                                        Datatype.STRING, Datatype.FILE, Datatype.URL,
                                        Datatype.TAG, Datatype.EMAIL, Datatype.REF,
                                        Datatype.BINARY, Datatype.MAP, Datatype.BITSET,
                                        Datatype.PORT, Datatype.GOB, Datatype.IMAGE,
                                        Datatype.VECTOR)),
                        Parameter.required("index"),
                        Parameter.required("value", ANYTHING)),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("poke", arguments, Set.of(), evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    if (arguments.get(0) instanceof VectorValue vector) {
                        VectorPath.write(vector, arguments.get(1), arguments.get(2));
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof GobValue gob) {
                        GobPath.pokeWhichInsertsRatherThanReplaces(
                                gob, (int) positionPokedAt(arguments.get(1)),
                                arguments.get(2));
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof ImageValue image) {
                        ImagePath.write(image,
                                (int) positionPokedAt(arguments.get(1)),
                                arguments.get(2));
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof BitsetValue members) {
                        requireChangeable(members);
                        new BitsetActions(members).holdAllOf(
                                arguments.get(1), arguments.get(2).isTruthy());
                        return members;
                    }
                    if (arguments.get(0) instanceof MapValue map) {
                        map.put(arguments.get(1), arguments.get(2), false);
                        return arguments.get(2);
                    }
                    long at = positionPokedAt(arguments.get(1));
                    if (arguments.get(0) instanceof SeriesValue series
                            && (at < 1 || at > series.lengthFromHere())) {
                        throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                "poke at " + at + " on a series of "
                                        + series.lengthFromHere());
                    }
                    if (arguments.get(0) instanceof StringValue text) {
                        int codepoint = switch (arguments.get(2)) {
                            case CharacterValue letter -> letter.codepoint();
                            case IntegerValue number
                                    when number.magnitude() >= 0
                                    && number.magnitude() <= CharacterValue.MAXIMUM_CODEPOINT ->
                                    (int) number.magnitude();
                            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                                    "poke into a string takes a character or a "
                                            + "codepoint, not a "
                                            + arguments.get(2).datatype()
                                                    .literalSpelling());
                        };
                        text.storage().set(text.index() + (int) at - 1, codepoint);
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof BinaryValue bytes
                            && arguments.get(2) instanceof IntegerValue octet) {
                        bytes.storage().set(bytes.index() + (int) at - 1,
                                asAnOctet(octet));
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof BinaryValue bytes
                            && arguments.get(2) instanceof CharacterValue character) {
                        if (character.codepoint() > 0xFF) {
                            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                    character.codepoint() + " does not fit in a byte");
                        }
                        bytes.storage().set(bytes.index() + (int) at - 1,
                                character.codepoint());
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof BitsetValue set) {
                        set.hold((int) at, arguments.get(2).isTruthy());
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof MapValue map) {
                        map.put(arguments.get(1), arguments.get(2));
                        return arguments.get(2);
                    }
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseCannotUse(arguments.get(0), "poke");
                    }
                    block.storage().set(block.index() + (int) at - 1, arguments.get(2));
                    return arguments.get(2);
                });

        define("difference", List.of(
                        Parameter.required("first",
                                setOperandOr(Datatype.BLOCK, Datatype.DATE)),
                        Parameter.required("second",
                                setOperandOr(Datatype.BLOCK, Datatype.DATE)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof TypesetValue
                            || arguments.get(0) instanceof BitsetValue
                            || arguments.get(0) instanceof MapValue) {
                        return Combining.sets(arguments.get(0), arguments.get(1),
                                Combining.Sets.DIFFERENCE,
                                refinements.contains("case"), 1);
                    }
                    if (arguments.get(0) instanceof DateValue from
                            && arguments.get(1) instanceof DateValue to) {
                        return timeBetween(from, to);
                    }
                    Value width = argumentFor("skip", List.of("skip"), arguments,
                            refinements, 2);
                    int stride = width instanceof IntegerValue wanted
                            ? (int) Math.max(1, wanted.magnitude())
                            : 1;
                    return Combining.sets(arguments.get(0), arguments.get(1),
                            Combining.Sets.DIFFERENCE,
                            refinements.contains("case"), stride);
                });

        define("reflect", List.of(Parameter.required("value"),
                        Parameter.required("field", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    String field = ((WordValue) arguments.get(1)).canonical();
                    if (arguments.getFirst() instanceof VectorValue vector) {
                        return "spec".equals(field)
                                ? VectorQuery.specOf(vector)
                                : VectorQuery.field(vector, field).orElseThrow(
                                        () -> Raised.of(EvaluationFailure.INVALID_ARG,
                                                arguments.get(1)));
                    }
                    if (arguments.getFirst() instanceof StructValue struct) {
                        return whatAStructReflects(struct, field, arguments.get(1));
                    }
                    if (arguments.getFirst() instanceof DatatypeValue asked) {
                        String[] described = DATATYPE_SPECS.get(
                                asked.represents().spelling());
                        if (described == null) {
                            return NoneValue.none();
                        }
                        return switch (field) {
                            case "title" -> StringValue.of(described[0]);
                            case "type" -> WordValue.of(described[1]);
                            case "spec" -> {
                                Context fields = Context.root();
                                fields.set("title", StringValue.of(described[0]));
                                fields.set("type", WordValue.of(described[1]));
                                yield new ObjectValue(fields);
                            }
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof TaskValue task) {
                        return switch (field) {
                            case "body" -> blockOfFieldsAndValues(task.context());
                            case "words" -> BlockValue.block(
                                    task.context().fieldsExcludingSelf().keySet().stream()
                                            .<Value>map(WordValue::of).toList());
                            case "values" -> BlockValue.block(List.copyOf(
                                    task.context().fieldsExcludingSelf().values()));
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof ModuleValue module) {
                        return switch (field) {
                            case "spec" -> module.header();
                            case "title" -> module.headerField("title");
                            case "body" -> blockOfFieldsAndValues(module.context());
                            case "words" -> BlockValue.block(
                                    module.context().fieldsExcludingSelf().keySet().stream()
                                            .<Value>map(WordValue::of).toList());
                            case "values" -> BlockValue.block(List.copyOf(
                                    module.context().fieldsExcludingSelf().values()));
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof ErrorValue raised) {
                        return switch (field) {
                            case "words" -> BlockValue.block(ErrorValue.FIELDS.stream()
                                    .<Value>map(WordValue::of).toList());
                            case "values" -> BlockValue.block(ErrorValue.FIELDS.stream()
                                    .map(name -> raised.field(name).orElseGet(NoneValue::none))
                                    .toList());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof MapValue map) {
                        return switch (field) {
                            case "words" -> BlockValue.block(map.keys());
                            case "values" -> BlockValue.block(map.values());
                            case "body" ->
                                    aPairToALine(BlockValue.block(map.flattened()));
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof HandleValue held) {
                        return switch (field) {
                            case "words" -> BlockValue.block(
                                    List.of(WordValue.of("type")));
                            case "values" -> BlockValue.block(
                                    List.of(held.isContext()
                                            ? WordValue.of(held.typeName())
                                            : NoneValue.none()));
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof FunctionValue written) {
                        return switch (field) {
                            case "spec" -> written.spec();
                            case "body" -> copied(written.body(), true);
                            case "words" -> wordsNamedIn(written.spec());
                            case "types" -> typesetsOf(written.parameters(), Set.of());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof NativeValue built) {
                        return switch (field) {
                            case "spec" -> specOf(built);
                            case "body" -> NoneValue.none();
                            case "words" -> wordsNamedIn(specOf(built));
                            case "types" -> typesetsOf(
                                    built.parameters(), built.declaredRefinements());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof OperatorValue operator
                            && operator.underlying() instanceof NativeValue behind) {
                        return switch (field) {
                            case "spec" -> specOf(behind);
                            case "words" -> wordsNamedIn(specOf(behind));
                            case "types" -> typesetsOf(
                                    behind.parameters(), behind.declaredRefinements());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof OperatorValue operator
                            && operator.underlying() instanceof FunctionValue behind) {
                        return switch (field) {
                            case "spec" -> behind.spec();
                            case "body" -> copied(behind.body(), true);
                            case "words" -> wordsNamedIn(behind.spec());
                            case "types" -> typesetsOf(behind.parameters(), Set.of());
                            default -> NoneValue.none();
                        };
                    }
                    if (!(arguments.get(0) instanceof ObjectValue object)) {
                        return NoneValue.none();
                    }
                    return switch (field) {
                        case "body" -> blockOfFieldsAndValues(object.context());
                        case "words" -> BlockValue.block(object.context().slots().stream()
                                .filter(slot -> !slot.canonical().equals("self"))
                                .<Value>map(slot -> WordValue.of(slot.spelling()))
                                .toList());
                        case "values" -> BlockValue.block(object.context().slots().stream()
                                .filter(slot -> !slot.canonical().equals("self"))
                                .map(ContextSlot::value)
                                .toList());
                        default -> NoneValue.none();
                    };
                });

        define("put", List.of(Parameter.required("target"),
                        Parameter.required("key", ANYTHING),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("put", arguments, refinements, evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    switch (arguments.get(0)) {
                        case MapValue map -> map.put(arguments.get(1), arguments.get(2),
                                refinements.contains("case"));
                        case ObjectValue object when arguments.get(1) instanceof WordValue field -> {
                            refuseHiddenField(object, field);
                            if (object.context().isClosedToNewNames()) {
                                throw Raised.of(EvaluationFailure.PROTECTED, "put");
                            }
                            object.context().set(field.spelling(), arguments.get(2));
                        }
                        case ObjectValue object -> throw Raised.of(
                                EvaluationFailure.INVALID_ARG,
                                Molder.mold(arguments.get(1))
                                        + " is not a word an object can hold a field under");
                        case BlockValue block -> {
                            List<Value> items = block.remaining();
                            boolean mindingCase = refinements.contains("case");
                            int stride = refinements.contains("skip")
                                    ? Math.max(1, (int) ((IntegerValue) arguments
                                            .get(arguments.size() - 1)).magnitude())
                                    : 1;
                            int found = -1;
                            for (int at = 0; at < items.size(); at += stride) {
                                if (mindingCase
                                        ? Comparison.identicallyEqual(
                                                items.get(at), arguments.get(1))
                                        : Comparison.looselyEqual(
                                                items.get(at), arguments.get(1))) {
                                    found = at;
                                    break;
                                }
                            }
                            if (found < 0) {
                                block.storage().append(arguments.get(1));
                                block.storage().append(arguments.get(2));
                            } else if (found + 1 >= items.size()) {
                                block.storage().append(arguments.get(2));
                            } else {
                                block.storage().set(
                                        block.index() + found + 1, arguments.get(2));
                            }
                        }
                        default -> raiseCannotUse(arguments.get(0), "put");
                    }
                    return arguments.get(2);
                });

        define("select", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("with", "wild", Set.of(Datatype.STRING)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip", "any", "only", "last", "part", "same", "with",
                        "reverse"),
                (arguments, evaluator, context, refinements) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("select", arguments, refinements, evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    if (arguments.get(0) instanceof MapValue map) {
                        return map.select(arguments.get(1),
                                refinements.contains("case"));
                    }
                    if (arguments.get(0) instanceof VectorValue) {
                        return raiseCannotUse(arguments.get(0), "select");
                    }
                    if (arguments.get(0) instanceof NoneValue) {
                        return NoneValue.none();
                    }
                    if (isAnyObject(arguments.getFirst())) {
                        if (!objectHasFieldToFind(arguments.getFirst(), arguments.get(1))) {
                            return NoneValue.none();
                        }
                        String field = ((WordValue) arguments.get(1)).canonical();
                        return arguments.getFirst() instanceof ErrorValue raised
                                ? raised.field(field).orElseGet(NoneValue::none)
                                : fieldsOf(arguments.getFirst())
                                        .ownSlotFor(field).value();
                    }
                    refuseUnbyteableNeedle(arguments.getFirst(), arguments.get(1), "select");
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "select");
                    }
                    long stride = searchStride(arguments, refinements);
                    boolean subOneForwardStride = refinements.contains("skip")
                            && stride < 1 && !refinements.contains("reverse");
                    if (subOneForwardStride) {
                        if (series instanceof BlockValue) {
                            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                    "select/skip needs a record width of at least "
                                            + "one, not " + stride);
                        }
                        return NoneValue.none();
                    }
                    int limit = searchLimit(series, arguments, refinements);
                    Wildcards wildcards = Wildcards.readFrom(argumentFor(
                            "with", SEARCH_ARGUMENTS, arguments, refinements, 2));
                    int found = positionSearched(series, arguments.get(1), refinements,
                            limit, stride, wildcards);
                    if (found < 0) {
                        return NoneValue.none();
                    }
                    List<Value> items = itemsOf(series.head());
                    int end = searchEnd(series, items, limit);
                    int after = found - 1
                            + matchLength(series, arguments.get(1), refinements,
                                    found, wildcards, end);
                    if (after >= end) {
                        return NoneValue.none();
                    }
                    return items.get(after);
                });

        define("get", List.of(Parameter.required("word")),
                Set.of("any"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof BlockValue path
                            && path.datatype() == Datatype.PATH) {
                        return evaluator.evaluateOrRaise(
                                BlockValue.block(List.of(path)), context);
                    }
                    if (arguments.get(0) instanceof ObjectValue object) {
                        return BlockValue.block(List.copyOf(
                                object.context().fieldsExcludingSelf().values()));
                    }
                    if (!(arguments.get(0) instanceof WordValue word)) {
                        return arguments.get(0);
                    }
                    Value held = slotOf(word).value();
                    if (held instanceof UnsetValue && !refinements.contains("any")) {
                        throw Raised.of(EvaluationFailure.NO_VALUE,
                                ((WordValue) arguments.get(0)).spelling() + " has no value");
                    }
                    return held;
                });
    }

    private static Set<String> expectedNames(
            List<Value> arguments, Set<String> refinements) {

        if (!refinements.contains("name") || arguments.size() < 2) {
            return Set.of();
        }
        return switch (arguments.get(1)) {
            case WordValue single -> Set.of(single.canonical());
            case BlockValue several -> several.remaining().stream()
                    .filter(WordValue.class::isInstance)
                    .map(WordValue.class::cast)
                    .map(WordValue::canonical)
                    .collect(java.util.stream.Collectors.toSet());
            default -> Set.of();
        };
    }

    private static boolean answersTo(ThrownSignal thrown, Set<String> expected) {
        return thrown.name()
                .map(expected::contains)
                .orElseGet(expected::isEmpty);
    }

    private void defineStepper(String spelling, int step) {
        define(spelling, List.of(Parameter.hardQuoted("word")),
                (arguments, evaluator, context) -> {
                    ContextSlot slot = slotOf((WordValue) arguments.getFirst());
                    Value before = slot.value();
                    slot.setValue(switch (before) {
                        case IntegerValue whole -> IntegerValue.of(whole.magnitude() + step);
                        case CharacterValue letter ->
                                CharacterValue.of(letter.codepoint() + step);
                        case SeriesValue series -> series.atIndex(
                                clampToSeries(series, series.index() + step));
                        default -> raiseCannotUse(before, spelling);
                    });
                    return before;
                });
    }

    private static Value namedConstant(Value value) {
        if (!(value instanceof WordValue word) || word.datatype() != Datatype.WORD) {
            return value;
        }
        return switch (word.canonical()) {
            case "none" -> NoneValue.none();
            case "true", "on", "yes" -> LogicValue.of(true);
            case "false", "off", "no" -> LogicValue.of(false);
            default -> value;
        };
    }

    private static void constructInto(Context built, List<Value> items, boolean asWritten) {
        List<WordValue> waiting = new ArrayList<>();
        for (Value item : items) {
            if (item instanceof WordValue name && name.datatype() == Datatype.SET_WORD) {
                waiting.add(name);
                continue;
            }
            Value held = asWritten ? item : namedConstant(item);
            if (!asWritten && held instanceof UnsetValue) {
                held = NoneValue.none();
            }
            for (WordValue name : waiting) {
                built.set(name.spelling(), held);
            }
            waiting.clear();
        }
        for (WordValue name : waiting) {
            if (asWritten) {
                if (!built.knows(name.canonical())) {
                    built.define(name.spelling());
                }
            } else {
                built.set(name.spelling(), NoneValue.none());
            }
        }
    }

    private static List<Value> headerFieldsIn(String header) {
        List<Value> fields = new ArrayList<>();
        String[] lines = header.split("\n", -1);
        for (int at = 0; at < lines.length; at++) {
            String line = lines[at].endsWith("\r")
                    ? lines[at].substring(0, lines[at].length() - 1)
                    : lines[at];
            int colon = colonAfterAName(line);
            if (colon < 0) {
                continue;
            }
            StringBuilder value = new StringBuilder(line.substring(colon + 1).stripLeading());
            while (at + 1 < lines.length && startsWithSpaceOrTab(lines[at + 1])) {
                at++;
                String continued = lines[at].endsWith("\r")
                        ? lines[at].substring(0, lines[at].length() - 1)
                        : lines[at];
                value.append(' ').append(continued.stripLeading());
            }
            fields.add(WordValue.of(line.substring(0, colon).strip(), Datatype.SET_WORD));
            fields.add(StringValue.of(value.toString()));
        }
        return fields;
    }

    private static int colonAfterAName(String line) {
        String name = line.stripLeading();
        if (name.isEmpty() || !Character.isLetter(name.charAt(0))) {
            return -1;
        }
        int at = 0;
        while (at < name.length() && (Character.isLetterOrDigit(name.charAt(at))
                || name.charAt(at) == '.' || name.charAt(at) == '-'
                || name.charAt(at) == '_')) {
            at++;
        }
        return at < name.length() && name.charAt(at) == ':'
                ? at + (line.length() - name.length())
                : -1;
    }

    private static boolean startsWithSpaceOrTab(String line) {
        return !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t');
    }

    private static String textOfBytes(BinaryValue bytes) {
        byte[] held = new byte[bytes.lengthFromHere()];
        for (int at = 0; at < held.length; at++) {
            held[at] = (byte) bytes.storage().at(bytes.index() + at);
        }
        return new String(held, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void refuseUnbyteableNeedle(
            Value haystack, Value needle, String nativeName) {

        if (!(haystack instanceof BinaryValue) || !(needle instanceof IntegerValue whole)) {
            return;
        }
        if (whole.magnitude() < 0 || whole.magnitude() > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    nativeName + " on a binary wanted a byte, not " + whole.magnitude());
        }
    }

    private static Value errorFromSpec(Value spec, Evaluator evaluator, Context context) {
        Value theSpecAsWritten = spec;
        boolean fromAnObject = spec instanceof ObjectValue;
        if (spec instanceof ObjectValue already) {
            spec = BlockValue.block(setWordsAndValuesOf(already.context()));
        } else if (spec instanceof BlockValue body && body.datatype() == Datatype.BLOCK) {
            Value built = makeObject(evaluator, context, Optional.empty(), body);
            if (built instanceof ObjectValue holder) {
                spec = BlockValue.block(setWordsAndValuesOf(holder.context()));
            }
        }
        if (!(spec instanceof BlockValue fields)) {
            if (!(spec instanceof StringValue written)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, spec);
            }
            return new ErrorValue(ErrorCategory.USER, "message",
                    written.text(), Optional.of(spec),
                    Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(),
                    new LinkedHashMap<>());
        }
        List<Value> items = fields.remaining();
        ErrorCategory category = ErrorCategory.USER;
        String errorId = "user-error";
        String typeWordAsSpelled = "";
        boolean namedAType = false;
        boolean namedAnId = false;
        Value unknownId = NoneValue.none();
        Optional<Value> subject = Optional.empty();
        Optional<Value> second = Optional.empty();
        Optional<Value> third = Optional.empty();
        for (int at = 0; at + 1 < items.size(); at += 2) {
            if (!(items.get(at) instanceof WordValue name)
                    || name.datatype() != Datatype.SET_WORD) {
                continue;
            }
            String said = items.get(at + 1) instanceof WordValue spelled
                    ? spelled.canonical()
                    : Molder.form(items.get(at + 1));
            Value asWritten = items.get(at + 1);
            switch (name.canonical()) {
                case "type" -> {
                    namedAType = true;
                    typeWordAsSpelled = asWritten instanceof WordValue spelled
                            ? spelled.spelling()
                            : said;
                    category = ErrorCategory.named(said).orElseThrow(() ->
                            Raised.of(EvaluationFailure.INVALID_ARG, asWritten));
                }
                case "id" -> {
                    namedAnId = true;
                    errorId = said;
                    unknownId = asWritten;
                }
                case "arg1" -> subject = Optional.of(items.get(at + 1));
                case "arg2" -> second = Optional.of(items.get(at + 1));
                case "arg3" -> third = Optional.of(items.get(at + 1));
                default -> { }
            }
        }
        if (!namedAType || !namedAnId) {
            throw new Raised(ErrorValue.of(ErrorCategory.INTERNAL,
                    "invalid-error", "an error spec names a type and an id"));
        }
        refuseAnErrorTheCatalogueHasNot(
                category, errorId, unknownId, theSpecAsWritten, fromAnObject);
        ErrorValue built = new ErrorValue(category, errorId, errorId, subject,
                second, third, Optional.empty(), Optional.empty(),
                new LinkedHashMap<>());
        built.write("type", WordValue.of(typeWordAsSpelled));
        return built;
    }

    private static void refuseAnErrorTheCatalogueHasNot(
            ErrorCategory category, String errorId, Value asWritten, Value spec,
            boolean fromAnObject) {

        if (!ErrorCatalogue.idsIn(category.spelling()).contains(errorId)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asWritten);
        }
        if (!fromAnObject && ErrorCatalogue.codeFor(category.spelling(), errorId)
                < LOWEST_CODE_AN_ERROR_CATALOGUE_ENTRY_HAS) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, spec);
        }
    }

    private static boolean theTruthInWhatALoopTests(Value tested) {
        if (tested instanceof UnsetValue) {
            throw Raised.of(EvaluationFailure.NO_RETURN);
        }
        return tested.isTruthy();
    }

    private static Value oneRoundCatchingContinue(
            Evaluator evaluator, BlockValue body, Context where) {
        try {
            return evaluator.evaluateOrRaise(body, where);
        } catch (ContinueSignal skipped) {
            return NoneValue.none();
        }
    }

    private static int headerStartsIn(String text) {
        String lowered = text.toLowerCase(java.util.Locale.ROOT);
        for (int at = lowered.indexOf("rebol"); at >= 0;
                at = lowered.indexOf("rebol", at + 1)) {
            if (!onlySpacesBefore(text, at) || !bracketFollows(text, at + "rebol".length())) {
                continue;
            }
            return at;
        }
        return -1;
    }

    private static boolean onlySpacesBefore(String text, int at) {
        for (int back = at - 1; back >= 0; back--) {
            char letter = text.charAt(back);
            if (letter == '\n') {
                return true;
            }
            if (!Character.isWhitespace(letter) && letter != '\uFEFF') {
                return false;
            }
        }
        return true;
    }

    private static boolean bracketFollows(String text, int at) {
        int forward = at;
        while (forward < text.length() && Character.isWhitespace(text.charAt(forward))) {
            forward++;
        }
        return forward < text.length() && text.charAt(forward) == '[';
    }

    private static final Set<Datatype> NAME_SHAPED = Set.of(
            Datatype.WORD, Datatype.LIT_WORD, Datatype.SET_WORD, Datatype.GET_WORD,
            Datatype.ISSUE, Datatype.REFINEMENT,
            Datatype.PATH, Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH,
            Datatype.BLOCK, Datatype.OBJECT);

    private static void refuseUnassignableName(Value name, EvaluationFailure failure) {
        if (name.datatype() == Datatype.ISSUE || name.datatype() == Datatype.REFINEMENT) {
            throw Raised.of(failure,
                    "set cannot assign to a " + name.datatype().literalSpelling());
        }
    }

    private static void refuseContradictoryTrim(Value series, Set<String> refinements) {
        boolean oneEnd = refinements.contains("head") || refinements.contains("tail");
        boolean everywhere = refinements.contains("all") || refinements.contains("with");
        if (oneEnd && everywhere) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "trim was told which end to work on and to work everywhere");
        }
        boolean aboutText = refinements.contains("with")
                || refinements.contains("auto")
                || refinements.contains("lines");
        if (aboutText && !(series instanceof StringValue)) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "trim/with, /auto and /lines are about text, and this is a "
                            + series.datatype().literalSpelling());
        }
    }

    private static Value trimmedBlock(BlockValue block, Set<String> refinements) {
        List<Value> items = new ArrayList<>(block.remaining());
        if (refinements.contains("all")) {
            items.removeIf(NoneValue.class::isInstance);
        } else {
            boolean fromHead = !refinements.contains("tail");
            boolean fromTail = !refinements.contains("head");
            while (fromHead && !items.isEmpty() && items.getFirst() instanceof NoneValue) {
                items.removeFirst();
            }
            while (fromTail && !items.isEmpty() && items.getLast() instanceof NoneValue) {
                items.removeLast();
            }
        }
        for (int at = block.storageLength(); at >= block.index(); at--) {
            block.storage().removeAt(at);
        }
        for (int at = items.size(); at > 0; at--) {
            block.storage().insertAt(block.index(), items.get(at - 1));
        }
        return block;
    }

    private static Value trimmedObject(Value subject) {
        Context fields = subject instanceof ErrorValue raised
                ? errorAsAContext(raised)
                : fieldsOf(subject);
        Context kept = Context.root();
        for (ContextSlot slot : fields.slots()) {
            if (slot.canonical().equals("self")
                    || slot.value() instanceof NoneValue
                    || slot.value() instanceof UnsetValue) {
                continue;
            }
            kept.set(slot.spelling(), slot.value());
        }
        return new ObjectValue(kept);
    }

    private static Set<Integer> unwantedCodePoints(Value characters) {
        return switch (characters) {
            case CharacterValue character -> Set.of(character.codepoint());
            case IntegerValue whole -> Set.of((int) whole.magnitude());
            case StringValue text -> text.text().codePoints().boxed()
                    .collect(java.util.stream.Collectors.toSet());
            case BinaryValue bytes -> {
                Set<Integer> octets = new java.util.HashSet<>();
                for (byte octet : bytes.octetsFromHere()) {
                    octets.add(octet & 0xFF);
                }
                yield octets;
            }
            default -> Set.of();
        };
    }

    private static Context errorAsAContext(ErrorValue raised) {
        Context fields = Context.root();
        for (String name : ErrorValue.FIELDS) {
            fields.set(name, raised.field(name).orElseGet(NoneValue::none));
        }
        return fields;
    }

    private static Value trimmedBinary(BinaryValue bytes, Set<String> refinements) {
        List<Integer> kept = new ArrayList<>();
        for (int at = 0; at < bytes.lengthFromHere(); at++) {
            kept.add(bytes.storage().at(bytes.index() + at));
        }
        if (refinements.contains("all")) {
            kept.removeIf(octet -> octet == 0);
        } else {
            boolean neitherEndNamed =
                    !refinements.contains("head") && !refinements.contains("tail");
            boolean fromHead = refinements.contains("head") || neitherEndNamed;
            boolean fromTail = refinements.contains("tail") || neitherEndNamed;
            while (fromHead && !kept.isEmpty() && kept.getFirst() == 0) {
                kept.removeFirst();
            }
            while (fromTail && !kept.isEmpty() && kept.getLast() == 0) {
                kept.removeLast();
            }
        }
        for (int at = bytes.storageLength(); at >= bytes.index(); at--) {
            bytes.storage().removeAt(at);
        }
        for (int at = kept.size(); at > 0; at--) {
            bytes.storage().insertAt(bytes.index(), kept.get(at - 1));
        }
        return bytes;
    }

    private static Set<Datatype> anyStringOrCharacter() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.ANY_STRING.members());
        accepted.add(Datatype.CHAR);
        return Set.copyOf(accepted);
    }

    private void defineCaseChange(
            String name, java.util.function.UnaryOperator<String> change) {

        define(name, List.of(
                        Parameter.required("text", anyStringOrCharacter()),
                        Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER))),
                Set.of("part"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof CharacterValue letter) {
                        return theOneCharacterChanged(letter, change);
                    }
                    StringValue text = (StringValue) arguments.getFirst();
                    if (!refinements.contains("part")) {
                        return rewrittenInPlace(text, change);
                    }
                    Value limit = argumentFor("part", List.of("part"), arguments, refinements, 1);
                    long wanted = limit instanceof IntegerValue asked
                            ? asked.magnitude()
                            : text.lengthFromHere();
                    StringValue changingFrom =
                            (StringValue) theRunReachingBackIfNegative(text, wanted);
                    int changing = (int) Math.max(0, Math.min(Math.abs(wanted),
                            changingFrom.lengthFromHere()));
                    rewrittenInPlace(changingFrom, whole -> {
                        String front = theFirstCodePointsOf(whole, changing);
                        return change.apply(front) + whole.substring(front.length());
                    });
                    return text;
                });
    }

    private Value theOneCharacterChanged(
            CharacterValue letter, java.util.function.UnaryOperator<String> change) {

        String changed = change.apply(
                new String(Character.toChars(letter.codepoint())));
        return changed.codePointCount(0, changed.length()) == 1
                ? CharacterValue.of(changed.codePointAt(0))
                : letter;
    }

    private static Value rewrittenInPlace(
            StringValue text, java.util.function.UnaryOperator<String> change) {

        int[] replacement = change.apply(text.text()).codePoints().toArray();
        int from = text.index();
        for (int at = text.storageLength(); at >= from; at--) {
            text.storage().removeAt(at);
        }
        for (int at = replacement.length; at > 0; at--) {
            text.storage().insertAt(from, replacement[at - 1]);
        }
        return text;
    }

    private static ContextSlot slotOf(WordValue word) {
        if (!word.isBound() || !word.binding().knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        return word.binding().slotFor(word.canonical());
    }

    private void defineSeries() {
        define("length?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> theRebolActorsAnswer(
                        "length?", arguments, Set.of(), evaluator)
                .orElseGet(() -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case PortValue queued when theEventQueueOf(queued).isPresent() ->
                            IntegerValue.of(theEventQueueOf(queued).orElseThrow()
                                    .lengthFromHere());
                    case TupleValue tuple -> IntegerValue.of(tuple.shownCount());
                    case WordValue word -> IntegerValue.of(
                            word.spelling().codePointCount(0, word.spelling().length()));
                    case Value subject when Actions.of(subject).isPresent() ->
                            IntegerValue.of(Actions.of(subject).orElseThrow().length());
                    case SeriesValue series -> IntegerValue.of(series.lengthFromHere());
                    case ModuleValue module ->
                            IntegerValue.of(module.context().fieldCount());
                    case PortValue port when isAFilePort(port) ->
                            lengthLeftInTheFile(port, evaluator);
                    case PortValue port ->
                            IntegerValue.of(port.context().fieldCount());
                    case StructValue struct -> IntegerValue.of(struct.size());
                    default -> raiseWrongArgument(arguments.get(0), "length?", "series");
                }));

        define("first", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 1));
        define("second", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 2));
        define("third", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 3));
        define("pick", List.of(Parameter.required("series"),
                        Parameter.required("index")),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("pick", arguments, Set.of(), evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    Optional<BlockValue> queue = theEventQueueOf(arguments.getFirst());
                    if (queue.isPresent()) {
                        return pickFrom(queue.get(), arguments.get(1));
                    }
                    return arguments.get(1) instanceof LogicValue chosen
                            && !(arguments.getFirst() instanceof BitsetValue)
                            ? pick(arguments.get(0), chosen.truth() ? 1 : 2)
                            : pickFrom(arguments.get(0), arguments.get(1));
                });

        define("atz", List.of(Parameter.required("series"),
                        Parameter.required("position",
                                Set.of(Datatype.INTEGER, Datatype.PAIR))),
                (arguments, evaluator, context) -> {
                    if (arguments.getFirst() instanceof PortValue port
                            && isAFilePort(port)) {
                        return movedWithinTheFile(port, evaluator,
                                (long) Arithmetic.asMagnitude(arguments.get(1)));
                    }
                    if (!(arguments.getFirst() instanceof SeriesValue series)) {
                        return raiseWrongArgument(arguments.getFirst(), "atz", "series");
                    }
                    return series.atIndex(clampedPosition(series,
                            positionAskedFor(series, arguments.get(1), false) + 1));
                });
        define("indexz?", List.of(Parameter.required("series", positionable())),
                Set.of("xy"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case ImageValue picture when refinements.contains("xy") ->
                            whereItStandsInThePicture(picture, 0);
                    case PortValue port when isAFilePort(port) -> {
                        refuseAClosedPosition(port);
                        yield IntegerValue.of(SeekableFilePort.positionOf(port));
                    }
                    case SeriesValue series -> IntegerValue.of(series.index() - 1);
                    default -> raiseCannotUse(arguments.get(0), "indexz?");
                });
        define("pickz", List.of(Parameter.required("series"),
                        Parameter.required("index", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    if (arguments.getFirst() instanceof BitsetValue) {
                        return pickFrom(arguments.getFirst(), arguments.get(1));
                    }
                    int wanted = (int) ((IntegerValue) arguments.get(1)).magnitude();
                    return pick(arguments.get(0), wanted >= 0 ? wanted + 1 : wanted);
                });

        define("past?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? LogicValue.of(series.index() > series.storageLength() + 1)
                        : raiseWrongArgument(arguments.get(0), "past?", "series"));

        define("swap", List.of(Parameter.required("series"), Parameter.required("with")),
                (arguments, evaluator, context) -> {
                    if (arguments.get(0) instanceof GobValue gob) {
                        return raiseCannotUse(gob, "swap");
                    }
                    if (arguments.get(0) instanceof StringValue here
                            && arguments.get(1) instanceof StringValue there) {
                        if (!here.atTail() && !there.atTail()) {
                            int mine = here.storage().at(here.index());
                            here.storage().set(here.index(), there.storage().at(there.index()));
                            there.storage().set(there.index(), mine);
                        }
                        return here;
                    }
                    if (arguments.get(0) instanceof BinaryValue here
                            && arguments.get(1) instanceof BinaryValue there) {
                        if (!here.atTail() && !there.atTail()) {
                            int mine = here.storage().at(here.index());
                            here.storage().set(here.index(), there.storage().at(there.index()));
                            there.storage().set(there.index(), mine);
                        }
                        return here;
                    }
                    if (!(arguments.get(0) instanceof BlockValue here)
                            || !(arguments.get(1) instanceof BlockValue there)) {
                        return raiseWrongArgument(arguments.get(0), "swap", "series");
                    }
                    if (!here.atTail() && !there.atTail()) {
                        Value mine = here.storage().at(here.index());
                        here.storage().set(here.index(), there.storage().at(there.index()));
                        there.storage().set(there.index(), mine);
                    }
                    return here;
                });

        define("sixth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 6));

        define("first+", List.of(Parameter.softQuoted("word")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof WordValue word)
                            || !word.isBound()
                            || !word.binding().knows(word.canonical())) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG, arguments.get(0));
                    }
                    ContextSlot slot = word.binding().slotFor(word.canonical());
                    if (!(slot.value() instanceof SeriesValue series)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                WordValue.of(word.spelling()));
                    }
                    Value first = pick(series, 1);
                    if (!series.atTail()) {
                        slot.setValue(series.atIndex(series.index() + 1));
                    }
                    return first;
                });

        define("head?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? LogicValue.of(series.atHead())
                        : raiseCannotUse(arguments.get(0), "head?"));
        define("tail?", List.of(Parameter.required("series", SERIES_LIKE)),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case NoneValue ignored -> LogicValue.yes();
                    case MapValue map -> LogicValue.of(map.pairCount() == 0);
                    case TypesetValue kinds ->
                            LogicValue.of(kinds.members().isEmpty());
                    case BitsetValue members ->
                            LogicValue.of(members.octets().length == 0);
                    case ObjectValue object -> LogicValue.of(
                            object.context().slots().stream()
                                    .allMatch(slot -> slot.canonical().equals("self")));
                    case ModuleValue module -> LogicValue.of(
                            module.context().slots().stream()
                                    .allMatch(slot -> slot.canonical().equals("self")));
                    case PortValue port when isAFilePort(port) ->
                            LogicValue.of(theFileIsAtItsEnd(port, evaluator));
                    case SeriesValue series -> LogicValue.of(series.atTail());
                    default -> raiseCannotUse(arguments.get(0), "tail?");
                });
        define("next", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.getFirst()) {
                    case PortValue port when isAFilePort(port) ->
                            movedWithinTheFile(port, evaluator,
                                    SeekableFilePort.positionOf(port) + 1);
                    case SeriesValue series -> (Value) series.atIndex(Math.min(
                            series.index() + 1, series.storageLength() + 1));
                    default -> raiseCannotUse(arguments.get(0), "next");
                });
        define("head", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.getFirst()) {
                    case PortValue port when isAFilePort(port) ->
                            movedWithinTheFile(port, evaluator, 0);
                    case SeriesValue series -> (Value) series.head();
                    default -> raiseCannotUse(arguments.get(0), "head");
                });
        define("tail", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.getFirst()) {
                    case PortValue port when isAFilePort(port) ->
                            movedWithinTheFile(port, evaluator,
                                    ((IntegerValue) wholeSizeOfTheFile(port, evaluator))
                                            .magnitude());
                    case SeriesValue series -> (Value) series.tail();
                    default -> raiseCannotUse(arguments.get(0), "tail");
                });
        define("index?", List.of(Parameter.required("series", positionable())),
                Set.of("xy"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case ImageValue picture when refinements.contains("xy") ->
                            whereItStandsInThePicture(picture, 1);
                    case PortValue port when isAFilePort(port) -> {
                        refuseAClosedPosition(port);
                        yield IntegerValue.of(SeekableFilePort.positionOf(port) + 1);
                    }
                    case GobValue gob -> IntegerValue.of(gob.positionCountedAsUnsigned());
                    case SeriesValue series -> IntegerValue.of(series.index());
                    default -> raiseCannotUse(arguments.get(0), "index?");
                });

        define("append", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("dup", "count", DUP_COUNT)),
                Set.of("part", "only", "dup"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case PortValue queued when theEventQueueOf(queued).isPresent() ->
                            queuedOnThePort(queued, theEventQueueOf(queued).orElseThrow(),
                                    arguments.get(1), true);
                    case PortValue openFile when isAFilePort(openFile) ->
                            appendedToTheFileBehind(
                                    openFile, arguments.get(1), evaluator, refinements);
                    case Value subject when Actions.of(subject).isPresent() ->
                            Actions.of(subject).orElseThrow().append(askedOf(
                                    subject, arguments, refinements, evaluator, context));
                    default -> raiseCannotUse(arguments.get(0), "append");
                });

        define("last", List.of(Parameter.required("value", aSeriesATupleOrAGob())),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case TupleValue parts ->
                            IntegerValue.of(parts.octetAt(parts.segmentCount()));
                    case SeriesValue series ->
                            pick((Value) series, series.lengthFromHere());
                    default -> raiseCannotUse(arguments.get(0), "last");
                });

        define("back", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.getFirst()) {
                    case PortValue port when isAFilePort(port) ->
                            movedWithinTheFile(port, evaluator,
                                    SeekableFilePort.positionOf(port) - 1);
                    case SeriesValue series ->
                            (Value) series.atIndex(Math.max(1, series.index() - 1));
                    default -> raiseCannotUse(arguments.get(0), "back");
                });

        defineStepper("++", 1);
        defineStepper("--", -1);

        define("truncate", List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "count", PART_LIMIT)),
                Set.of("part"),
                (arguments, evaluator, context, refinements) -> {
                    if (!(arguments.getFirst() instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.getFirst(), "truncate");
                    }
                    removeFrom(series, 1, series.index() - 1);
                    SeriesValue kept = series.atIndex(1);
                    if (refinements.contains("part") && arguments.size() > 1) {
                        long wanted = ((IntegerValue) arguments.get(1)).magnitude();
                        removeFrom(kept, (int) wanted + 1,
                                (int) (kept.lengthFromHere() - wanted));
                    }
                    return kept;
                });

        define("skip", List.of(
                        Parameter.required("series"),
                        Parameter.required("offset",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL,
                                        Datatype.PERCENT, Datatype.LOGIC,
                                        Datatype.PAIR))),
                (arguments, evaluator, context) -> {
                    if (arguments.getFirst() instanceof PortValue port
                            && isAFilePort(port)) {
                        return movedWithinTheFile(port, evaluator,
                                SeekableFilePort.positionOf(port)
                                        + (long) Arithmetic.asMagnitude(arguments.get(1)));
                    }
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "skip");
                    }
                    long by = positionAskedFor(series, arguments.get(1), false);
                    return series instanceof GobValue gob
                            ? gob.atIndex((int) (gob.index() + by))
                            : series.atIndex(clampToSeries(series, series.index() + by));
                });

        define("at", List.of(
                        Parameter.required("series"),
                        Parameter.required("index",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL,
                                        Datatype.PERCENT, Datatype.LOGIC,
                                        Datatype.PAIR))),
                (arguments, evaluator, context) -> {
                    if (arguments.getFirst() instanceof PortValue port
                            && isAFilePort(port)) {
                        return movedWithinTheFile(port, evaluator,
                                (long) Arithmetic.asMagnitude(arguments.get(1)) - 1);
                    }
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "at");
                    }
                    long wanted = positionAskedFor(series, arguments.get(1), true);
                    return series.atIndex(clampToSeries(series, series.index() + wanted - 1));
                });

        define("copy", List.of(Parameter.required("value", copyable()),
                        Parameter.belongingTo("part", "limit", Set.of()),
                        Parameter.belongingTo("types", "kinds",
                                Set.of(Datatype.TYPESET, Datatype.DATATYPE))),
                Set.of("part", "deep", "types"),
                (arguments, evaluator, context, refinements) -> {
                    Optional<Value> itsOwn = theRebolActorsAnswer(
                            "copy", List.of(arguments.getFirst()),
                            refinements, evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    Value original = arguments.getFirst();
                    boolean deeply = refinements.contains("deep");
                    Set<Datatype> kinds = whichDatatypesToCopy(arguments, refinements);
                    if (original instanceof StructValue struct) {
                        if (!refinements.isEmpty()) {
                            throw Raised.of(EvaluationFailure.BAD_REFINES,
                                    "copy on a struct takes no refinements at all");
                        }
                        return struct.separateCopy();
                    }
                    if (!refinements.contains("part")) {
                        return copied(original, deeply, kinds);
                    }
                    if (hasNoOrderToTakeTheFirstSoManyOf(original)) {
                        throw Raised.of(EvaluationFailure.BAD_REFINES,
                                "/part names the first so many of something with an "
                                        + "order, and a "
                                        + original.datatype().literalSpelling()
                                        + " has none");
                    }
                    if (!(original instanceof SeriesValue series)) {
                        return raiseCannotUse(original, "copy");
                    }
                    Value limit = argumentFor("part", List.of("part", "types"),
                            arguments, refinements, 1);
                    if (series instanceof ImageValue picture
                            && limit instanceof PairValue shape) {
                        return ImageSeries.rectangleCopiedFrom(picture,
                                (int) shape.x(), (int) shape.y());
                    }
                    return copiedFront(series, limit, deeply, kinds);
                });

        define("find",
                List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("with", "wild", Set.of(Datatype.STRING)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("tail", "last", "only", "case", "any", "same", "part",
                        "with", "skip", "reverse", "match"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof NoneValue) {
                        return NoneValue.none();
                    }
                    if (isAnyObject(arguments.getFirst())) {
                        return objectHasFieldToFind(arguments.getFirst(), arguments.get(1))
                                ? LogicValue.of(true)
                                : NoneValue.none();
                    }
                    if (arguments.getFirst() instanceof MapValue map) {
                        return map.storedKeyLike(arguments.get(1),
                                refinements.contains("case"));
                    }
                    if (arguments.get(0) instanceof BitsetValue bitset) {
                        return LogicValue.of(new BitsetActions(bitset).holds(
                                arguments.get(1),
                                refinements.contains("any"),
                                !refinements.contains("case")));
                    }
                    if (arguments.get(0) instanceof TypesetValue typeset) {
                        return LogicValue.of(arguments.get(1) instanceof DatatypeValue wanted
                                && typeset.holds(wanted.represents()));
                    }
                    if (arguments.get(0) instanceof GobValue searched) {
                        if (!(arguments.get(1) instanceof GobValue wanted)) {
                            return NoneValue.none();
                        }
                        int at = searched.storage().positionOf(wanted.storage());
                        return at == 0 ? NoneValue.none() : searched.atIndex(at);
                    }
                    if (arguments.get(0) instanceof ImageValue picture) {
                        return thePixelFoundIn(picture, arguments.get(1), refinements);
                    }
                    if (!(arguments.get(0) instanceof SeriesValue series)
                            || series instanceof VectorValue) {
                        return raiseCannotUse(arguments.get(0), "find");
                    }
                    int limit = searchLimit(series, arguments, refinements);
                    long stride = searchStride(arguments, refinements);
                    Wildcards wildcards = Wildcards.readFrom(argumentFor(
                            "with", SEARCH_ARGUMENTS, arguments, refinements, 2));
                    refuseUnbyteableNeedle(arguments.getFirst(), arguments.get(1), "find");
                    boolean subOneForwardStride = refinements.contains("skip")
                            && stride < 1 && !refinements.contains("reverse");
                    if (subOneForwardStride) {
                        if (series instanceof BlockValue) {
                            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                    "find/skip needs a record width of at least "
                                            + "one, not " + stride);
                        }
                        return NoneValue.none();
                    }
                    int found = positionSearched(series, arguments.get(1), refinements,
                            limit, stride, wildcards);
                    if (found < 0 || (refinements.contains("match") && found != series.index())) {
                        return NoneValue.none();
                    }
                    return series.atIndex(refinements.contains("tail")
                            ? found + matchLength(
                                    series, arguments.get(1), refinements, found, wildcards,
                                    searchEnd(series, itemsOf(series.head()), limit))
                            : found);
                });

        define("insert", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("dup", "count", DUP_COUNT)),
                Set.of("only", "part", "dup"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case PortValue queued when theEventQueueOf(queued).isPresent() ->
                            queuedOnThePort(queued, theEventQueueOf(queued).orElseThrow(),
                                    arguments.get(1), false);
                    case Value subject when Actions.of(subject).isPresent() ->
                            Actions.of(subject).orElseThrow().insert(askedOf(
                                    subject, arguments, refinements, evaluator, context));
                    default -> raiseCannotUse(arguments.get(0), "insert");
                });

        define("remove", List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "count", REMOVE_RANGE),
                        Parameter.belongingTo("key", "which", Set.of())),
                Set.of("part", "key"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof NoneValue nothing) {
                        return nothing;
                    }
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("remove", arguments, refinements, evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    if (theEventQueueOf(arguments.getFirst()).isPresent()) {
                        throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                                WordValue.of("remove").as(Datatype.SET_WORD));
                    }
                    if (arguments.get(0) instanceof MapValue map) {
                        if (refinements.contains("key")) {
                            map.remove(arguments.get(1));
                        }
                        return map;
                    }
                    if (arguments.get(0) instanceof BitsetValue members) {
                        requireChangeable(members);
                        return new BitsetActions(members).removed(refinements,
                                refinement -> argumentFor(refinement,
                                        List.of("part", "key"),
                                        arguments, refinements, 1));
                    }
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseWrongArgument(arguments.get(0), "remove", "series");
                    }
                    if (refinements.contains("key") && series instanceof BlockValue pairs) {
                        removeKeyedPair(pairs, argumentFor(
                                "key", List.of("part", "key"), arguments, refinements, 1));
                        return series;
                    }
                    if (refinements.contains("key")) {
                        throw Raised.of(EvaluationFailure.FEATURE_NA,
                                "/key removes from a map or a bitset, not a series");
                    }
                    long howMany = howManyWanted(series, arguments, refinements, 1).orElse(1L);
                    SeriesValue removingFrom = theRunReachingBackIfNegative(series, howMany);
                    for (long dropped = 0; dropped < Math.abs(howMany)
                            && !removingFrom.atTail(); dropped++) {
                        removeOneAt(removingFrom, removingFrom.index());
                    }
                    return removingFrom;
                });

        define("reverse", List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER))),
                Set.of("part"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("part")
                            && arguments.getFirst() instanceof SeriesValue series) {
                        Value limit = argumentFor(
                                "part", List.of("part"), arguments, refinements, 1);
                        return reversedFrontInPlace(series, limit);
                    }
                    if (arguments.get(0) instanceof TupleValue tuple) {
                        Value limit = refinements.contains("part")
                                ? argumentFor("part", List.of("part"), arguments,
                                        refinements, 1)
                                : IntegerValue.of(tuple.segmentCount());
                        return reversedOctets(tuple, (int) Arithmetic.asMagnitude(limit));
                    }
                    if (arguments.get(0) instanceof PairValue pair) {
                        return pair.reversed();
                    }
                    if (arguments.get(0) instanceof GobValue gob) {
                        gob.storage().turnRound();
                        return gob;
                    }
                    if (arguments.get(0) instanceof StringValue text) {
                        return reversedTextACharacterAtATime(text);
                    }
                    if (arguments.get(0) instanceof BinaryValue bytes) {
                        return reversedBytes(bytes);
                    }
                    if (arguments.get(0) instanceof VectorValue vector) {
                        return reversedFrontInPlace(vector, IntegerValue.of(
                                vector.lengthFromHere()));
                    }
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseWrongArgument(arguments.get(0), "reverse", "series");
                    }
                    List<MarkedItem> slots =
                            theMarkedItemsOf(block, block.lengthFromHere());
                    Collections.reverse(slots);
                    writeBackMarkedItems(block, slots);
                    return block;
                });

        define("change", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("dup", "count", DUP_COUNT)),
                Set.of("part", "only", "dup"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof StructValue struct) {
                        refuseUnfinishedRefinements(refinements, "change");
                        return structChangedBy(struct, arguments.get(1));
                    }
                    if (arguments.get(0) instanceof ImageValue picture) {
                        return ImageSeries.changed(picture, arguments.get(1),
                                howManyTimesOver(arguments, refinements),
                                refinements.contains("only"),
                                theShapeOfTheRectangle(arguments, refinements));
                    }
                    if (refinements.contains("part") && arguments.size() > 2
                            && arguments.get(0) instanceof SeriesValue stranded) {
                        Value replacement = copied(arguments.get(1),
                                arguments.get(1) instanceof BlockValue);
                        long taking = countUpTo(stranded, arguments.get(2));
                        SeriesValue series = clampedToTail(stranded);
                        if (taking < 0) {
                            long back = Math.min(-taking, series.index() - 1L);
                            series = series.atIndex((int) (series.index() - back));
                            taking = back;
                        }
                        for (long gone = 0; gone < taking && !series.atTail(); gone++) {
                            removeOneAt(series, series.index());
                        }
                        int before = series.storageLength();
                        insertInto(series, replacement);
                        return series.atIndex(
                                series.index() + series.storageLength() - before);
                    }
                    if (arguments.get(0) instanceof GobValue gob) {
                        refuseUnfinishedRefinements(refinements, "change");
                        GobPath.pokeWhichInsertsRatherThanReplaces(
                                gob, gob.index(), arguments.get(1));
                        return gob.atIndex(gob.index() + 1);
                    }
                    Value replacing = duplicated(
                            arguments.get(1), arguments, refinements);
                    if (arguments.get(0) instanceof BinaryValue bytes) {
                        int[] octets = SeriesContents.octetsContributedBy(replacing);
                        for (int at = 0; at < octets.length; at++) {
                            int where = bytes.index() + at;
                            if (where > bytes.storage().length()) {
                                bytes.storage().append(octets[at]);
                            } else {
                                bytes.storage().set(where, octets[at]);
                            }
                        }
                        return bytes.atIndex(bytes.index() + octets.length);
                    }
                    if (arguments.get(0) instanceof StringValue strandedText) {
                        StringValue text = (StringValue) clampedToTail(strandedText);
                        String replacement = replacing instanceof BlockValue several
                                ? runTogether(several)
                                : Molder.form(replacing);
                        int[] letters = replacement.codePoints().toArray();
                        int overwritten = Math.min(letters.length, text.lengthFromHere());
                        for (int gone = 0; gone < overwritten; gone++) {
                            text.storage().removeAt(text.index());
                        }
                        for (int at = 0; at < letters.length; at++) {
                            text.storage().insertAt(text.index() + at, letters[at]);
                        }
                        return text.atIndex(text.index() + letters.length);
                    }
                    if (arguments.get(0) instanceof VectorValue strandedVector) {
                        return changedElements(
                                (VectorValue) clampedToTail(strandedVector),
                                arguments, refinements);
                    }
                    if (!(arguments.get(0) instanceof BlockValue strandedBlock)) {
                        return raiseCannotUse(arguments.get(0), "change");
                    }
                    BlockValue block = (BlockValue) clampedToTail(strandedBlock);
                    BlockValue spread = !refinements.contains("only")
                            && replacing instanceof BlockValue several
                            && several.datatype() == Datatype.BLOCK
                            ? several
                            : null;
                    List<Value> replacements = spread == null
                            ? List.of(replacing)
                            : spread.remaining();
                    for (int at = 0; at < replacements.size(); at++) {
                        int where = block.index() + at;
                        if (where <= block.storageLength()) {
                            block.storage().set(where, replacements.get(at));
                        } else {
                            block.storage().insertAt(where, replacements.get(at));
                        }
                        block.storage().setLineBreakAt(where, spread != null
                                && spread.storage().breaksLineAt(spread.index() + at));
                    }
                    return block.atIndex(block.index() + replacements.size());
                });

        define("clear", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case PortValue port when isAFilePort(port) ->
                            truncatedAtThePosition(port, evaluator);
                    case PortValue queued when theEventQueueOf(queued).isPresent() -> {
                        BlockValue queue = theEventQueueOf(queued).orElseThrow();
                        while (queue.storage().length() > 0) {
                            queue.storage().removeAt(1);
                        }
                        yield queued;
                    }
                    case Value subject when Actions.of(subject).isPresent() ->
                            Actions.of(subject).orElseThrow().cleared();
                    case StructValue struct -> {
                        struct.clear();
                        yield struct;
                    }
                    default -> raiseCannotUse(arguments.get(0), "clear");
                });

        define("sort",
                List.of(Parameter.required("series", Typeset.SERIES.members()),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("compare", "comparator", Set.of()),
                        Parameter.belongingTo("part", "count", PART_LIMIT)),
                Set.of("case", "compare", "skip", "reverse", "all", "part", "unstable"),
                (arguments, evaluator, context, refinements) -> {
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "sort");
                    }
                    List<String> declared = List.of("skip", "compare", "part");
                    Value skipSize = argumentFor("skip", declared, arguments, refinements);
                    Value partCount = argumentFor("part", declared, arguments, refinements);
                    Value comparator = argumentFor("compare", declared, arguments, refinements);
                    if (series instanceof VectorValue vector) {
                        if (refinements.contains("skip") || refinements.contains("compare")) {
                            throw Raised.of(EvaluationFailure.FEATURE_NA,
                                    "sort/skip and sort/compare on a vector!");
                        }
                        return sortedElements(vector,
                                partCount instanceof IntegerValue asked
                                        ? (int) Math.min(asked.magnitude(),
                                                vector.lengthFromHere())
                                        : vector.lengthFromHere(),
                                refinements.contains("reverse"));
                    }
                    int howMany = partCount instanceof IntegerValue wanted
                            ? (int) Math.min(wanted.magnitude(), series.lengthFromHere())
                            : series.lengthFromHere();
                    howMany = Math.max(0, howMany);
                    if (howMany <= 1) {
                        return series;
                    }
                    int stride = skipSize instanceof IntegerValue size
                            ? (int) size.magnitude()
                            : 1;
                    if (refinements.contains("skip")
                            && (stride < 1 || stride > howMany || howMany % stride != 0)) {
                        throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                "a record width of " + stride + " does not divide "
                                        + howMany);
                    }
                    if (comparator instanceof IntegerValue column
                            && (!refinements.contains("skip")
                                    || column.magnitude() < 1
                                    || column.magnitude() > stride)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "there is no column " + Molder.mold(comparator)
                                        + " to sort by");
                    }
                    if (refinements.contains("all")
                            && !(comparator == null
                                    || comparator.datatype().isAnyFunction())) {
                        throw Raised.of(EvaluationFailure.BAD_REFINES,
                                "sort/all compares whole records, so a column "
                                        + "has nothing left to say");
                    }
                    return sorted(series, stride, comparator,
                            refinements.contains("case"),
                            refinements.contains("reverse"),
                            refinements.contains("all"),
                            howMany, evaluator,
                            refinements.contains("unstable")
                                    || series instanceof BinaryValue);
                });

        defineSetOperation("intersect", Combining.Sets.INTERSECT);
        defineSetOperation("union", Combining.Sets.UNION);
        defineSetOperation("exclude", Combining.Sets.EXCLUDE);
        define("unique", List.of(
                        Parameter.required("set1", Set.of(
                                Datatype.BLOCK, Datatype.STRING, Datatype.BITSET,
                                Datatype.TYPESET, Datatype.MAP)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    Value width = argumentFor("skip", List.of("skip"), arguments,
                            refinements, 1);
                    int stride = width instanceof IntegerValue wanted
                            ? (int) Math.max(1, wanted.magnitude())
                            : 1;
                    return Combining.sets(
                            arguments.getFirst(), arguments.getFirst(),
                            Combining.Sets.UNION, refinements.contains("case"), stride);
                });

        define("fourth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 4));
        define("fifth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 5));

        define("reduce", List.of(Parameter.required("block"),
                        Parameter.belongingTo("into", "target", Typeset.ANY_BLOCK.members()),
                        Parameter.belongingTo("only", "words", Set.of())),
                Set.of("into", "only", "no-set"),
                (arguments, evaluator, context, refinements) -> {
                    Value source = arguments.getFirst();
                    Value target = refinements.contains("into") && arguments.size() > 1
                            ? argumentFor("into", List.of("into"), arguments, refinements, 1)
                            : null;

                    BlockValue reduced;
                    if (source instanceof BlockValue toReduce
                            && (toReduce.datatype() == Datatype.BLOCK
                                    || toReduce.datatype() == Datatype.PAREN)) {
                        if (refinements.contains("no-set")) {
                            reduced = BlockValue.block(
                                    reducedLeavingSetWords(toReduce, evaluator));
                        } else if (refinements.contains("only")) {
                            reduced = BlockValue.block(reducedOnlyWords(toReduce, evaluator,
                                    argumentFor("only", List.of("into", "only"),
                                            arguments, refinements, 1)));
                        } else {
                            reduced = evaluator.evaluateEachKeepingTheLineShape(
                                    toReduce, evaluator.systemContext());
                        }
                    } else if (target == null) {
                        return source;
                    } else {
                        reduced = BlockValue.block(List.of(source));
                    }

                    if (!(target instanceof BlockValue into)) {
                        return reduced.as(
                                source.datatype() == Datatype.PAREN
                                        ? Datatype.PAREN
                                        : Datatype.BLOCK);
                    }
                    List<Value> results = reduced.remaining();
                    into.storage().spliceInAt(into.index(), results,
                            reduced.storage(), reduced.index());
                    return into.atIndex(into.index() + results.size());
                });

        define("compose", List.of(Parameter.required("block"),
                        Parameter.belongingTo("into", "out", Typeset.ANY_BLOCK.members())),
                Set.of("only", "deep", "into"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof MapValue template) {
                        return composedMap(template, evaluator, context,
                                refinements.contains("only"),
                                refinements.contains("deep"));
                    }
                    BlockValue built =
                            arguments.getFirst() instanceof BlockValue template
                                    ? composed(template, evaluator, context,
                                            refinements.contains("only"),
                                            refinements.contains("deep"))
                                    : BlockValue.block(List.of(arguments.getFirst()));
                    if (!(arguments.getFirst() instanceof BlockValue)
                            && (!refinements.contains("into") || arguments.size() < 2)) {
                        return arguments.getFirst();
                    }
                    if (!refinements.contains("into") || arguments.size() < 2) {
                        return built;
                    }
                    BlockValue target = (BlockValue) arguments.get(1);
                    List<Value> items = built.remaining();
                    target.storage().spliceInAt(target.index(), items,
                            built.storage(), built.index());
                    return target.atIndex(target.index() + items.size());
                });

        define("transcode",
                List.of(Parameter.required("source"),
                        Parameter.belongingTo("line", "count", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("part", "length", Set.of(Datatype.INTEGER))),
                Set.of("one", "error", "next", "part", "line", "only"),
                (arguments, evaluator, context, refinements) ->
                        SourceReading.asAskedFor(arguments, refinements).answer());

        define("round",
                List.of(Parameter.required("value"),
                        Parameter.belongingTo("to", "multiple", Set.of())),
                Set.of("to", "down", "even", "half-down", "floor", "ceiling",
                        "half-ceiling"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof TimeValue time) {
                        return roundedTime(time, refinements.contains("to")
                                ? arguments.get(arguments.size() - 1)
                                : null, refinements);
                    }
                    if (arguments.get(0) instanceof PairValue pair) {
                        return PairValue.of(
                                roundedHalfAway(pair.x()), roundedHalfAway(pair.y()));
                    }
                    double value = Comparison.asDouble(arguments.get(0));
                    if (!refinements.contains("to")) {
                        return roundedKeepingTheDatatype(
                                arguments.get(0), roundedBy(value, refinements));
                    }
                    Value step = arguments.get(arguments.size() - 1);
                    double multiple = Comparison.asDouble(step);
                    if (multiple == 0) {
                        if (step.datatype() == Datatype.INTEGER
                                && arguments.get(0).datatype() == Datatype.INTEGER) {
                            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
                        }
                        return roundedToTheScalesDatatype(step, value);
                    }
                    double rounded = roundedHalfAway(value / multiple) * multiple;
                    return roundedToTheScalesDatatype(step, rounded);
                });

    }

    private static Value roundedTime(
            TimeValue time, Value scale, Set<String> refinements) {

        if (scale == null) {
            return TimeValue.ofNanoseconds(Math.round(
                    (double) time.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND)
                    * TimeValue.NANOSECONDS_PER_SECOND);
        }
        if (scale instanceof TimeValue step) {
            return TimeValue.ofNanoseconds(step.nanoseconds() == 0
                    ? time.nanoseconds()
                    : Math.round((double) time.nanoseconds() / step.nanoseconds())
                            * step.nanoseconds());
        }
        double stepSeconds = Comparison.asDouble(scale);
        double seconds = (double) time.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND;
        double rounded = stepSeconds == 0
                ? seconds
                : Math.round(seconds / stepSeconds) * stepSeconds;
        return scale instanceof IntegerValue
                ? IntegerValue.of(Math.round(rounded))
                : DecimalValue.of(rounded);
    }

    private static Value roundedKeepingTheDatatype(Value subject, double rounded) {
        return switch (subject) {
            case MoneyValue amount -> amount.amounting(BigDecimal.valueOf(rounded));
            case DecimalValue quantity when quantity.datatype() == Datatype.PERCENT ->
                    DecimalValue.percent(rounded);
            case IntegerValue whole -> IntegerValue.of((long) rounded);
            default -> DecimalValue.of(rounded);
        };
    }

    private static Value roundedToTheScalesDatatype(Value scale, double rounded) {
        return switch (scale) {
            case MoneyValue amount -> amount.amounting(BigDecimal.valueOf(rounded));
            case IntegerValue whole -> IntegerValue.of((long) rounded);
            case DecimalValue quantity when quantity.datatype() == Datatype.PERCENT ->
                    DecimalValue.percent(toFifteenDigits(rounded));
            default -> DecimalValue.of(toFifteenDigits(rounded));
        };
    }

    private static double toFifteenDigits(double rounded) {
        return new BigDecimal(rounded).round(new java.math.MathContext(15)).doubleValue();
    }

    private static BlockValue composed(
            BlockValue template, Evaluator evaluator, Context context,
            boolean keepingBlocksWhole, boolean goingDeep) {
        BlockStorage built = new BlockStorage();
        int reading = template.index();
        for (Value item : template.remaining()) {
            boolean asWritten = true;
            if (!(item instanceof BlockValue paren) || paren.datatype() != Datatype.PAREN) {
                if (goingDeep && item instanceof BlockValue nested
                        && nested.datatype() == Datatype.BLOCK) {
                    built.append(composed(
                            nested, evaluator, context, keepingBlocksWhole, true));
                } else if (goingDeep && item instanceof MapValue nested) {
                    built.append(composedMap(
                            nested, evaluator, context, keepingBlocksWhole, true));
                } else {
                    built.append(goingDeep ? aBlockShapeCopiedWhole(item) : item);
                }
            } else {
                asWritten = false;
                for (Value produced : evaluator.evaluateEachOrRaise(
                        paren.as(Datatype.BLOCK), context)) {
                    if (produced instanceof UnsetValue) {
                        continue;
                    }
                    if (!keepingBlocksWhole && produced instanceof BlockValue spliced
                            && spliced.datatype() == Datatype.BLOCK) {
                        built.spliceInAt(built.length() + 1, spliced.remaining(),
                                spliced.storage(), spliced.index());
                    } else {
                        built.append(produced);
                    }
                }
            }
            if (asWritten && template.storage().breaksLineAt(reading)) {
                built.setLineBreakAt(built.length(), true);
            }
            reading++;
        }
        return new BlockValue(built, 1, Datatype.BLOCK);
    }

    private static Value aBlockShapeCopiedWhole(Value item) {
        return item instanceof BlockValue shaped
                ? new BlockValue(new BlockStorage(shaped.remaining()), 1, shaped.datatype())
                : item;
    }

    private static MapValue composedMap(
            MapValue template, Evaluator evaluator, Context context,
            boolean keepingBlocksWhole, boolean goingDeep) {

        return new MapActions(template).composedThrough(held -> {
            if (held instanceof BlockValue paren
                    && paren.datatype() == Datatype.PAREN) {
                return evaluator.evaluateOrRaise(paren.as(Datatype.BLOCK), context);
            }
            if (goingDeep && held instanceof BlockValue nested
                    && nested.datatype() == Datatype.BLOCK) {
                return composed(nested, evaluator, context, keepingBlocksWhole, true);
            }
            if (goingDeep && held instanceof MapValue nested) {
                return composedMap(nested, evaluator, context, keepingBlocksWhole, true);
            }
            return held;
        });
    }

    private static String textOfSource(Value source) {
        return switch (source) {
            case StringValue given -> given.text();
            case BinaryValue given -> strictlyUtf8(given.octetsFromHere());
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "transcode reads text, not " + source.datatype().literalSpelling());
        };
    }

    private record SourceReading(
            String whole,
            String readable,
            long firstLine,
            Transcoder.Extent extent,
            boolean countingLines,
            boolean handingBackFailures,
            boolean answeringTheValueAlone,
            boolean sourceWasBinary,
            Value source) {

        static SourceReading asAskedFor(List<Value> arguments, Set<String> refinements) {
            String whole = textOfSource(arguments.getFirst());
            return new SourceReading(
                    whole,
                    refinements.contains("part")
                            ? boundedTo(whole, charactersPermittedIn(arguments, refinements))
                            : whole,
                    firstLineNumberIn(arguments, refinements),
                    extentAskedFor(refinements),
                    refinements.contains("line"),
                    refinements.contains("error"),
                    refinements.contains("one"),
                    arguments.getFirst() instanceof BinaryValue,
                    arguments.getFirst());
        }

        private static Transcoder.Extent extentAskedFor(Set<String> refinements) {
            if (refinements.contains("only")) {
                return Transcoder.Extent.THE_FIRST_VALUE_AT_EVERY_DEPTH;
            }
            if (refinements.contains("next") || refinements.contains("one")) {
                return Transcoder.Extent.THE_FIRST_VALUE;
            }
            return Transcoder.Extent.THE_WHOLE_SOURCE;
        }

        private static String boundedTo(String whole, int charactersPermitted) {
            return whole.substring(0, Math.min(whole.length(), charactersPermitted));
        }

        Value answer() {
            Transcoder.Reading reading = Transcoder.read(readable, firstLine, extent);
            List<Value> values = valuesWithAnyFailureBesideThem(reading);
            if (stopsBeforeTheEndOfTheSource() && values.isEmpty()) {
                return handedBackOrRaised(pastEnd());
            }
            if (answeringTheValueAlone) {
                return values.getFirst();
            }
            return BlockValue.block(stopsBeforeTheEndOfTheSource()
                    ? withWhatWasLeftUnread(values, reading)
                    : values);
        }

        private boolean stopsBeforeTheEndOfTheSource() {
            return extent != Transcoder.Extent.THE_WHOLE_SOURCE || handingBackFailures;
        }

        private List<Value> valuesWithAnyFailureBesideThem(Transcoder.Reading reading) {
            if (reading.whyItStopped().isEmpty()) {
                return reading.valuesReadBeforeStopping();
            }
            ErrorValue failure =
                    reading.whyItStopped().orElseThrow().error().orElseThrow();
            if (!handingBackFailures) {
                throw new Raised(failure);
            }
            List<Value> keptWithTheFailure =
                    new ArrayList<>(reading.valuesReadBeforeStopping());
            keptWithTheFailure.add(failure);
            return keptWithTheFailure;
        }

        private List<Value> withWhatWasLeftUnread(
                List<Value> values, Transcoder.Reading reading) {

            List<Value> answer = new ArrayList<>(values);
            String left = skippingCodePoints(whole, reading.endedAtCodePoint());
            answer.add(switch (source) {
                case BinaryValue bytes -> bytes.atIndex(bytes.index()
                        + utf8LengthOf(whole) - utf8LengthOf(left));
                case StringValue text ->
                        text.atIndex(text.index() + whole.length() - left.length());
                default -> remainderOf(left, sourceWasBinary);
            });
            if (countingLines) {
                answer.add(IntegerValue.of(reading.lineEndedOn()));
            }
            return answer;
        }

        private static int utf8LengthOf(String text) {
            return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        private Value handedBackOrRaised(ErrorValue failure) {
            if (handingBackFailures) {
                return failure;
            }
            throw new Raised(failure);
        }

        private static ErrorValue pastEnd() {
            return ErrorValue.of(SyntaxFailure.PAST_END.category(),
                    SyntaxFailure.PAST_END.errorId(),
                    SyntaxFailure.PAST_END.description());
        }
    }

    private static final List<String> TRANSCODE_ARGUMENT_ORDER = List.of("line", "part");

    private static final long THE_FIRST_LINE_OF_ANY_SOURCE = 1;

    private static long firstLineNumberIn(List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("line")) {
            return THE_FIRST_LINE_OF_ANY_SOURCE;
        }
        long asked = transcodeArgument("line", arguments, refinements);
        if (asked < THE_FIRST_LINE_OF_ANY_SOURCE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a source's first line is line one or later, not " + asked);
        }
        return asked;
    }

    private static int charactersPermittedIn(
            List<Value> arguments, Set<String> refinements) {

        long asked = transcodeArgument("part", arguments, refinements);
        if (asked < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a read cannot be bounded to fewer than no characters, and "
                            + asked + " is fewer");
        }
        return (int) Math.min(asked, Integer.MAX_VALUE);
    }

    private static long transcodeArgument(
            String refinement, List<Value> arguments, Set<String> refinements) {

        return (long) Arithmetic.asMagnitude(argumentFor(
                refinement, TRANSCODE_ARGUMENT_ORDER, arguments, refinements, 1));
    }

    private static Value transcodedText(String text) {
        return transcodedText(text, 1);
    }

    private static Value transcodedText(String text, long firstLine) {
        TranscodeResult read = Transcoder.transcode(text, firstLine);
        return read.values().orElseThrow(() -> new Raised(read.error().orElseThrow()));
    }

    private static String skippingCodePoints(String whole, int howMany) {
        int[] codepoints = whole.codePoints().toArray();
        int taken = Math.min(howMany, codepoints.length);
        return new String(codepoints, taken, codepoints.length - taken);
    }

    private static Value remainderOf(String left, boolean asBytes) {
        if (!asBytes) {
            return StringValue.of(left);
        }
        byte[] octets = left.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int[] asNumbers = new int[octets.length];
        for (int at = 0; at < octets.length; at++) {
            asNumbers[at] = octets[at] & 0xFF;
        }
        return BinaryValue.of(asNumbers);
    }

    private static final List<String> SEARCH_ARGUMENTS = List.of("part", "with", "skip");

    private static int positionSearched(
            SeriesValue series, Value wanted, Set<String> refinements,
            int limit, long stride, Wildcards wildcards) {
        boolean forcedToSingleStep = refinements.contains("reverse")
                || refinements.contains("last");
        return forcedToSingleStep || stride == 1 || stride == 0
                ? positionOfMatch(series, wanted, refinements, limit, wildcards)
                : positionOfMatchInRecords(
                        series, wanted, refinements, (int) stride, limit, wildcards);
    }

    private static int searchLimit(
            SeriesValue series, List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("part")) {
            return Integer.MAX_VALUE;
        }
        return (int) countUpTo(series, argumentFor(
                "part", SEARCH_ARGUMENTS, arguments, refinements, 2));
    }

    private static long searchStride(List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("skip")) {
            return 1;
        }
        return ((IntegerValue) argumentFor(
                "skip", SEARCH_ARGUMENTS, arguments, refinements, 2)).magnitude();
    }

    private static int searchEnd(SeriesValue series, List<Value> items, int limit) {
        return limit < 0
                ? items.size()
                : (int) Math.min(items.size(), (long) series.index() - 1 + limit);
    }

    private static int positionOfMatch(
            SeriesValue series, Value wanted, Set<String> refinements, int limit,
            Wildcards wildcards) {

        boolean lookingBehind = refinements.contains("reverse");
        boolean takingTheLast = refinements.contains("last");
        List<Value> items = itemsOf(series.head());
        int here = series.index() - 1;
        int needleWidth = widthOfNeedle(series, wanted, refinements);

        int end = searchEnd(series, items, limit);
        int step = 1;
        int start = here;
        int at = here;
        if (lookingBehind || takingTheLast) {
            step = -1;
            if (takingTheLast) {
                start = here;
                at = end - needleWidth;
            } else {
                start = 0;
                at = here - 1;
            }
        }

        for (; at >= start && at < end; at += step) {
            if (matchesHere(series, items, at, wanted, refinements, wildcards, end)) {
                return at + 1;
            }
            if (refinements.contains("match")) {
                break;
            }
        }
        return -1;
    }

    private static int widthOfNeedle(
            SeriesValue series, Value wanted, Set<String> refinements) {

        if (refinements.contains("only")) {
            return 1;
        }
        if (series instanceof BlockValue) {
            return wanted instanceof BlockValue run && run.datatype() == Datatype.BLOCK
                    ? run.remaining().size()
                    : 1;
        }
        if (wanted instanceof BitsetValue) {
            return 1;
        }
        if (wanted instanceof CharacterValue && !(series instanceof BinaryValue)) {
            return 1;
        }
        return itemsOfNeedle(series, wanted).size();
    }

    private static boolean matchesHere(
            SeriesValue series, List<Value> items, int at, Value wanted,
            Set<String> refinements, Wildcards wildcards, int end) {

        if (series instanceof StringValue text && refinements.contains("any")) {
            return patternEnd(text.head().text(), at, end, Molder.form(wanted),
                    refinements.contains("case"), wildcards) >= 0;
        }

        if ((wanted instanceof DatatypeValue || wanted instanceof TypesetValue)
                && !refinements.contains("only")) {
            return wanted instanceof DatatypeValue wantedType
                    ? items.get(at).datatype() == wantedType.represents()
                    : ((TypesetValue) wanted).holds(items.get(at).datatype());
        }
        if (wanted instanceof BlockValue run
                && run.datatype() == Datatype.BLOCK
                && !refinements.contains("only")) {
            return runMatchesAt(items, at, run.remaining(), refinements.contains("same"));
        }
        if (refinements.contains("same")
                && !(series instanceof StringValue)
                && !(series instanceof BinaryValue)) {
            return refinements.contains("only")
                    ? Comparison.isSameValue(items.get(at), wanted)
                    : sameRunAt(items, at, wanted);
        }
        if (series instanceof StringValue || series instanceof BinaryValue) {
            return textRunMatchesAt(series, items, at, wanted, refinements);
        }
        if (wanted instanceof BitsetValue members) {
            return items.get(at) instanceof CharacterValue character
                    && members.holds(character.codepoint());
        }
        return matches(items.get(at), wanted, refinements.contains("case"));
    }

    private static boolean textRunMatchesAt(
            SeriesValue series, List<Value> items, int at, Value wanted,
            Set<String> refinements) {

        if (wanted instanceof BitsetValue members) {
            return items.get(at) instanceof CharacterValue character
                    && members.holds(character.codepoint());
        }
        List<Value> run = itemsOfNeedle(series, wanted);
        if (at + run.size() > items.size()) {
            return false;
        }
        boolean mindingCase = refinements.contains("case") || refinements.contains("same");
        for (int step = 0; step < run.size(); step++) {
            if (!matches(items.get(at + step), run.get(step), mindingCase)) {
                return false;
            }
        }
        return true;
    }

    private static List<Value> theBytesThatSpell(Value wanted) {
        if (wanted instanceof CharacterValue letter
                && letter.codepoint() <= 0xFF) {
            return List.of(IntegerValue.of(letter.codepoint()));
        }
        String text = wanted instanceof CharacterValue letter
                ? new String(Character.toChars(letter.codepoint()))
                : ((StringValue) wanted).text();
        List<Value> octets = new ArrayList<>();
        for (byte octet : text.getBytes(StandardCharsets.UTF_8)) {
            octets.add(IntegerValue.of(octet & 0xFF));
        }
        return octets;
    }

    private static List<Value> itemsOfNeedle(SeriesValue series, Value wanted) {
        if (series instanceof BinaryValue && wanted instanceof BinaryValue bytes) {
            return itemsOf(bytes);
        }
        if (series instanceof BinaryValue
                && (wanted instanceof CharacterValue || wanted instanceof StringValue)) {
            return theBytesThatSpell(wanted);
        }
        if (wanted instanceof CharacterValue letter) {
            return List.of(letter);
        }
        if (series instanceof BinaryValue && wanted instanceof IntegerValue byteValue) {
            return List.of(byteValue);
        }
        return Molder.form(wanted).codePoints()
                .<Value>mapToObj(series instanceof BinaryValue
                        ? IntegerValue::of
                        : CharacterValue::of)
                .toList();
    }

    private static boolean runMatchesAt(
            List<Value> items, int at, List<Value> run, boolean mindingIdentity) {

        if (at + run.size() > items.size()) {
            return false;
        }
        for (int step = 0; step < run.size(); step++) {
            boolean same = mindingIdentity
                    ? Comparison.isSameValue(items.get(at + step), run.get(step))
                    : Comparison.looselyEqual(items.get(at + step), run.get(step));
            if (!same) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameRunAt(List<Value> items, int at, Value wanted) {
        List<Value> run = wanted instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block.remaining()
                : List.of(wanted);
        if (at + run.size() > items.size()) {
            return false;
        }
        for (int step = 0; step < run.size(); step++) {
            if (!Comparison.isSameValue(items.get(at + step), run.get(step))) {
                return false;
            }
        }
        return true;
    }

    private static int positionOfMatchInRecords(
            SeriesValue series, Value wanted, Set<String> refinements, int stride,
            int limit, Wildcards wildcards) {
        boolean backwards = stride < 0 || refinements.contains("reverse")
                || refinements.contains("last");
        int width = Math.abs(stride);
        List<Value> items = itemsOf(series.head());
        int from = backwards ? series.index() - 2 : series.index() - 1;
        int end = searchEnd(series, items, limit);

        for (int at = from; at >= 0 && at < end; at += backwards ? -width : width) {
            if (matchesAtRecord(series, items, at, wanted, refinements, wildcards, end)) {
                return at + 1;
            }
        }
        return -1;
    }

    private static boolean matchesAtRecord(
            SeriesValue series, List<Value> items, int at, Value wanted,
            Set<String> refinements, Wildcards wildcards, int end) {
        if (series instanceof StringValue text && refinements.contains("any")) {
            return patternEnd(text.head().text(), at, end, Molder.form(wanted),
                    refinements.contains("case"), wildcards) >= 0;
        }
        if (wanted instanceof BitsetValue members) {
            return items.get(at) instanceof CharacterValue character
                    && members.holds(character.codepoint());
        }
        if (wanted instanceof BlockValue run
                && run.datatype() == Datatype.BLOCK
                && !refinements.contains("only")) {
            return runMatchesAt(items, at, run.remaining(), refinements.contains("same"));
        }
        if (wanted instanceof StringValue needle && !refinements.contains("only")) {
            int[] sought = (needle.datatype() == Datatype.STRING
                    ? needle.text()
                    : Molder.form(needle)).codePoints().toArray();
            if (at + sought.length > items.size()) {
                return false;
            }
            for (int step = 0; step < sought.length; step++) {
                if (!(items.get(at + step) instanceof CharacterValue character)
                        || Character.toLowerCase(character.codepoint())
                                != Character.toLowerCase(sought[step])) {
                    return false;
                }
            }
            return true;
        }
        return matches(items.get(at), wanted, refinements.contains("case"));
    }

    private record Wildcards(char anyRun, char oneCharacter) {

        private static final Wildcards STARS_AND_QUESTION_MARKS = new Wildcards('*', '?');

        static Wildcards readFrom(Value given) {
            if (!(given instanceof StringValue chosen)) {
                return STARS_AND_QUESTION_MARKS;
            }
            String characters = chosen.text();
            return new Wildcards(
                    characters.isEmpty() ? '*' : characters.charAt(0),
                    characters.length() < 2 ? '?' : characters.charAt(1));
        }
    }

    private static int patternEnd(
            String within, int from, int upTo, String pattern, boolean mindingCase,
            Wildcards wildcards) {
        if (pattern.isEmpty()) {
            return from;
        }
        char first = pattern.charAt(0);
        if (first == wildcards.anyRun()) {
            if (pattern.length() == 1) {
                return upTo;
            }
            for (int taken = from; taken <= upTo; taken++) {
                int end = patternEnd(
                        within, taken, upTo, pattern.substring(1), mindingCase, wildcards);
                if (end >= 0) {
                    return end;
                }
            }
            return -1;
        }
        if (from >= upTo) {
            return -1;
        }
        if (first != wildcards.oneCharacter()
                && !sameCharacter(within.charAt(from), first, mindingCase)) {
            return -1;
        }
        return patternEnd(
                within, from + 1, upTo, pattern.substring(1), mindingCase, wildcards);
    }

    private static boolean sameCharacter(char left, char right, boolean mindingCase) {
        return mindingCase
                ? left == right
                : Character.toLowerCase(left) == Character.toLowerCase(right);
    }

    private static boolean matchesRun(List<Value> haystack, int at, List<Value> needle) {
        return matchesRun(haystack, at, needle, false);
    }

    private static boolean matchesRun(
            List<Value> haystack, int at, List<Value> needle, boolean identically) {

        for (int step = 0; step < needle.size(); step++) {
            Value here = haystack.get(at + step);
            Value sought = needle.get(step);
            boolean fits = identically
                    ? Comparison.isSameValue(here, sought)
                    : Comparison.looselyEqual(here, sought);
            if (!fits) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(Value item, Value wanted, boolean mindingCase) {
        if (item instanceof HandleValue found && wanted instanceof HandleValue looking) {
            return found.compareWith(looking) == 0;
        }
        return mindingCase ? Comparison.identicallyEqual(item, wanted) : Comparison.looselyEqual(item, wanted);
    }

    private static int matchLength(
            SeriesValue series, Value wanted, Set<String> refinements, int found,
            Wildcards wildcards, int end) {
        if (series instanceof StringValue patterned && refinements.contains("any")) {
            String within = patterned.head().text();
            int from = found - 1;
            int reached = patternEnd(within, from, end,
                    Molder.form(wanted), refinements.contains("case"), wildcards);
            return reached < 0 ? 1 : reached - from;
        }
        if (wanted instanceof BitsetValue) {
            return 1;
        }
        if (series instanceof BinaryValue && wanted instanceof BinaryValue run) {
            return run.lengthFromHere();
        }
        if (series instanceof BinaryValue
                && (wanted instanceof CharacterValue || wanted instanceof StringValue)) {
            return theBytesThatSpell(wanted).size();
        }
        if (series instanceof BlockValue
                && wanted instanceof BlockValue run
                && run.datatype() == Datatype.BLOCK
                && !refinements.contains("only")) {
            return run.remaining().size();
        }
        return series instanceof StringValue && !refinements.contains("only")
                ? theCharactersIn(Molder.form(wanted))
                : 1;
    }

    private static int theCharactersIn(String text) {
        return text.codePointCount(0, text.length());
    }

    private static Value argumentFor(
            String refinement, List<String> declaredOrder,
            List<Value> arguments, Set<String> asked) {
        return argumentFor(refinement, declaredOrder, arguments, asked, 1);
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

    private static Value sorted(SeriesValue series, int stride, Value comparator,
            boolean mindingCase, boolean reversed, boolean wholeRecord,
            int howMany, Evaluator evaluator, boolean unstably) {
        int step = Math.max(1, stride);
        List<Value> items = itemsOf(series).subList(
                0, Math.min(howMany, itemsOf(series).size()));
        List<List<Value>> records = new ArrayList<>();
        Map<List<Value>, Integer> whereEachRecordBegan = new IdentityHashMap<>();
        for (int at = 0; at + step <= items.size(); at += step) {
            List<Value> record = List.copyOf(items.subList(at, at + step));
            whereEachRecordBegan.put(record, at);
            records.add(record);
        }
        java.util.Comparator<List<Value>> ordering = (left, right) -> {
            int order = wholeRecord && comparator == null
                    ? compareWholeRecords(left, right, mindingCase)
                    : compareRecords(
                            left, right, comparator, mindingCase, wholeRecord, series, evaluator);
            return reversed ? -order : order;
        };
        if (unstably) {
            SymmetryPartitionSort.sort(records, ordering);
        } else {
            records = mergeSortedTakingFromTheLeftUnlessOutOfOrder(
                    records, ordering);
        }

        if (series instanceof BlockValue block) {
            putTheRecordsBackWithTheirMarks(block, records, whereEachRecordBegan, step);
            return series;
        }
        List<Value> ordered = records.stream().flatMap(List::stream).toList();
        for (int at = 0; at < ordered.size(); at++) {
            if (series instanceof StringValue text
                    && ordered.get(at) instanceof CharacterValue character) {
                text.storage().set(text.index() + at, character.codepoint());
            } else if (series instanceof BinaryValue bytes
                    && ordered.get(at) instanceof IntegerValue octet) {
                bytes.storage().set(bytes.index() + at, (int) octet.magnitude());
            }
        }
        return series;
    }

    private static void putTheRecordsBackWithTheirMarks(
            BlockValue block, List<List<Value>> records,
            Map<List<Value>, Integer> whereEachRecordBegan, int step) {

        List<Boolean> marksBefore = new ArrayList<>(records.size() * step);
        for (int at = 0; at < records.size() * step; at++) {
            marksBefore.add(block.storage().breaksLineAt(block.index() + at));
        }
        int landing = 0;
        for (List<Value> record : records) {
            int cameFrom = whereEachRecordBegan.get(record);
            for (int within = 0; within < record.size(); within++) {
                block.storage().set(block.index() + landing, record.get(within));
                block.storage().setLineBreakAt(block.index() + landing,
                        marksBefore.get(cameFrom + within));
                landing++;
            }
        }
    }

    private static int compareRecords(List<Value> left, List<Value> right,
            Value comparator, boolean mindingCase, boolean wholeRecord,
            SeriesValue series, Evaluator evaluator) {

        if (comparator instanceof IntegerValue column) {
            return compareByColumns(left, right, List.of(column), mindingCase);
        }
        if (comparator instanceof BlockValue columns) {
            return compareByColumns(left, right, columns.remaining(), mindingCase);
        }
        if (comparator == null) {
            return Comparison.compareForSorting(left.getFirst(), right.getFirst(), mindingCase);
        }
        return wholeRecord
                ? askComparatorTheOtherWayRound(comparator,
                        lentRecordOf(series, left), lentRecordOf(series, right), evaluator)
                : askComparatorTheOtherWayRound(comparator,
                        lentElementOf(series, left.getFirst()),
                        lentElementOf(series, right.getFirst()), evaluator);
    }

    private static Value lentRecordOf(SeriesValue series, List<Value> record) {
        if (series instanceof BinaryValue) {
            int[] octets = new int[record.size()];
            for (int at = 0; at < record.size(); at++) {
                octets[at] = (int) ((IntegerValue) record.get(at)).magnitude();
            }
            return BinaryValue.of(octets);
        }
        if (series instanceof StringValue) {
            StringBuilder characters = new StringBuilder();
            for (Value element : record) {
                characters.appendCodePoint(((CharacterValue) element).codepoint());
            }
            return StringValue.of(characters.toString());
        }
        return lentRecord(record);
    }

    private static Value lentElementOf(SeriesValue series, Value element) {
        return series instanceof BinaryValue && element instanceof IntegerValue octet
                ? CharacterValue.of((int) octet.magnitude())
                : element;
    }

    private static <T> List<T> mergeSortedTakingFromTheLeftUnlessOutOfOrder(
            List<T> items, Comparator<T> order) {
        if (items.size() < 2) {
            return items;
        }
        int half = items.size() / 2;
        List<T> front = mergeSortedTakingFromTheLeftUnlessOutOfOrder(
                new ArrayList<>(items.subList(0, half)), order);
        List<T> back = mergeSortedTakingFromTheLeftUnlessOutOfOrder(
                new ArrayList<>(items.subList(half, items.size())), order);
        List<T> merged = new ArrayList<>(items.size());
        int here = 0;
        int there = 0;
        while (here < front.size() && there < back.size()) {
            if (order.compare(front.get(here), back.get(there)) <= 0) {
                merged.add(front.get(here));
                here++;
            } else {
                merged.add(back.get(there));
                there++;
            }
        }
        merged.addAll(front.subList(here, front.size()));
        merged.addAll(back.subList(there, back.size()));
        return merged;
    }

    private static BlockValue lentRecord(List<Value> record) {
        BlockValue lent = BlockValue.block(record);
        lent.storage().protectFromChange(true);
        return lent;
    }

    private static int compareWholeRecords(
            List<Value> left, List<Value> right, boolean mindingCase) {

        for (int at = 0; at < Math.min(left.size(), right.size()); at++) {
            int order = Comparison.compareForSorting(left.get(at), right.get(at), mindingCase);
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareByColumns(List<Value> left, List<Value> right,
            List<Value> columns, boolean mindingCase) {
        for (Value asked : columns) {
            if (!(asked instanceof IntegerValue column)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "a column to sort by is a number, not "
                                + asked.datatype().literalSpelling());
            }
            int at = (int) column.magnitude() - 1;
            if (at < 0 || at >= left.size() || at >= right.size()) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "there is no column " + column.magnitude() + " to sort by");
            }
            int ordering = Comparison.compareForSorting(left.get(at), right.get(at), mindingCase);
            if (ordering != 0) {
                return ordering;
            }
        }
        return 0;
    }

    private static int askComparatorTheOtherWayRound(
            Value comparator, Value left, Value right, Evaluator evaluator) {
        Value answer = evaluator.applyFunction(comparator, List.of(right, left));
        if (answer instanceof LogicValue truth) {
            return truth.truth() ? 1 : -1;
        }
        if (Comparison.isNumeric(answer)) {
            double amount = Comparison.asDouble(answer);
            return amount > 0 ? 1 : amount == 0 ? 0 : -1;
        }
        return -1;
    }

    private static Value takeOne(SeriesValue series) {
        if (series.lengthFromHere() == 0) {
            return NoneValue.none();
        }
        Value taken = itemsOf(series).getFirst();
        removeFrom(series, series.index(), 1);
        return taken;
    }

    private static Value deepenedIfAsked(Value taken, Set<String> refinements) {
        return refinements.contains("deep") && !(taken instanceof ObjectValue)
                ? copied(taken, taken instanceof BlockValue)
                : taken;
    }

    private static Value takeSeveral(SeriesValue series, long wanted) {
        int from = Math.min(series.index(), series.storageLength() + 1);
        int howMany;
        if (wanted >= 0) {
            howMany = (int) Math.min(wanted, series.lengthFromHere());
        } else {
            howMany = (int) Math.min(-wanted, from - 1L);
            from -= howMany;
        }
        List<Value> taken = List.copyOf(
                itemsOf(series.head()).subList(from - 1, from - 1 + howMany));
        removeFrom(series, from, howMany);
        return switch (series) {
            case StringValue text -> StringValue.of(taken.stream()
                    .map(Molder::form).collect(Collectors.joining()), text.datatype());
            case BinaryValue bytes -> BinaryValue.of(taken.stream()
                    .mapToInt(item -> (int) ((IntegerValue) item).magnitude()).toArray());
            case BlockValue block -> BlockValue.block(taken).as(block.datatype());
            case ImageValue image -> takenPixels(image, taken);
            case GobValue ignored -> BlockValue.block(taken);
            case VectorValue vector -> vectorHolding(vector.kind(), taken);
        };
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

    private static List<Value> numbersAddedBy(VectorKind kind, List<Value> arguments,
            Set<String> refinements, boolean limitingTheSource) {

        List<Value> once = limitingTheSource && refinements.contains("part")
                ? numbersOfferedTo(kind, arguments.get(1),
                        partCountFor(arguments, refinements))
                : numbersContributedTo(kind, arguments.get(1));
        Value times = argumentFor("dup", List.of("part", "dup"), arguments, refinements, 2);
        long rounds = refinements.contains("dup") && times instanceof IntegerValue counted
                ? counted.magnitude()
                : 1;
        List<Value> added = new ArrayList<>();
        for (long round = 0; round < rounds; round++) {
            added.addAll(once);
        }
        return added;
    }

    private static VectorValue sortedElements(VectorValue vector, int howMany,
            boolean backwards) {

        int sorting = Math.max(0, howMany);
        long[] front = new long[sorting];
        for (int at = 0; at < sorting; at++) {
            front[at] = vector.storage().at(vector.index() + at);
        }
        VectorQuery.sortAscending(vector.kind(), front);
        for (int at = 0; at < sorting; at++) {
            vector.storage().set(vector.index() + at,
                    backwards ? front[sorting - 1 - at] : front[at]);
        }
        return vector;
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

    private static Value changedElements(VectorValue vector, List<Value> arguments,
            Set<String> refinements) {

        List<Value> numbers = numbersAddedBy(vector.kind(), arguments, refinements, false);
        int asked = refinements.contains("part")
                ? partCountFor(arguments, refinements)
                : numbers.size();
        int removing = Math.max(0, Math.min(asked, vector.lengthFromHere()));
        for (int gone = 0; gone < removing; gone++) {
            vector.storage().removeAt(vector.index());
        }
        for (int at = numbers.size(); at > 0; at--) {
            vector.storage().insertAt(vector.index(),
                    VectorPath.storedFormOf(vector.kind(), numbers.get(at - 1)));
        }
        return vector.atIndex(vector.index() + numbers.size());
    }

    static List<Value> numbersOfferedTo(VectorKind kind, Value value, int limit) {
        if (!(value instanceof SeriesValue source)) {
            return numbersContributedTo(kind, value);
        }
        SeriesValue run = theRunReachingBackIfNegative(source, limit);
        long wanted = limit >= 0 ? limit : source.index() - run.index();
        if (run instanceof BinaryValue bytes) {
            return numbersSpeltByWithTheOddBytesDropped(kind, bytes,
                    (int) Math.min(wanted, bytes.lengthFromHere()));
        }
        List<Value> offered = numbersContributedTo(kind, run);
        return offered.subList(0, (int) Math.min(wanted, offered.size()));
    }

    private static VectorValue vectorHolding(VectorKind kind, List<Value> numbers) {
        VectorStorage made = new VectorStorage(kind, 0);
        numbers.forEach(number -> made.append(VectorPath.storedFormOf(kind, number)));
        return new VectorValue(made, 1);
    }

    private static ImageValue takenPixels(ImageValue image, List<Value> taken) {
        ImageValue made = ImageValue.of(taken.size(), taken.isEmpty() ? 0 : 1);
        for (int at = 1; at <= taken.size(); at++) {
            ImagePath.write(made, at, taken.get(at - 1));
        }
        return made;
    }

    private static void removeFrom(SeriesValue series, int oneBasedIndex, int howMany) {
        for (int removed = 0; removed < howMany; removed++) {
            switch (series) {
                case BlockValue block -> block.storage().removeAt(oneBasedIndex);
                case StringValue text -> text.storage().removeAt(oneBasedIndex);
                case BinaryValue bytes -> bytes.storage().removeAt(oneBasedIndex);
                case ImageValue image -> image.storage().removeFrom(oneBasedIndex, 1);
                case GobValue gob -> gob.storage().removeChildren(oneBasedIndex, 1);
                case VectorValue vector -> vector.storage().removeAt(oneBasedIndex);
            }
        }
    }

    private static double roundedHalfAway(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static boolean isTheZeroOfItsDatatype(Value value) {
        if (value instanceof BitsetValue members) {
            return new BitsetActions(members).isTheEmptySet();
        }
        if (value instanceof PairValue pair) {
            return pair.x() == 0 && pair.y() == 0;
        }
        if (value instanceof TupleValue segments) {
            return Arrays.stream(segments.segments()).allMatch(part -> part == 0);
        }
        if (value instanceof CharacterValue letter) {
            return letter.codepoint() == 0;
        }
        if (value instanceof TimeValue clock) {
            return clock.nanoseconds() == 0;
        }
        if (value instanceof MoneyValue amount) {
            return new MoneyActions(amount).isNoAmountAtAll();
        }
        return Comparison.isNumeric(value) && Comparison.asDouble(value) == 0.0;
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

    private void defineCodepointRange(String name, int highest) {
        define(name, List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case CharacterValue character ->
                            LogicValue.of(character.codepoint() <= highest);
                    case StringValue text ->
                            LogicValue.of(text.text().codePoints().allMatch(
                                    codepoint -> codepoint <= highest));
                    default -> raiseWrongArgument(arguments.get(0), name, "string or character");
                });
    }

    private static long arityOf(Value callee) {
        return switch (callee) {
            case NativeValue built -> built.parameters().stream()
                    .filter(Parameter::consumesAnArgument)
                    .filter(parameter -> parameter.owningRefinement().isEmpty())
                    .count();
            case FunctionValue function -> function.parameters().size();
            case OperatorValue operator -> arityOf(operator.underlying());
            default -> 0;
        };
    }

    private void defineTabbing(String name, boolean toTabs) {
        define(name, List.of(
                        Parameter.required("text", anyStringOr(Datatype.BINARY)),
                        Parameter.belongingTo("size", "width", Set.of(Datatype.INTEGER))),
                Set.of("size"),
                (arguments, evaluator, context, refinements) -> {
                    int width = refinements.contains("size") && arguments.size() > 1
                            ? (int) ((IntegerValue) arguments.get(1)).magnitude()
                            : 4;
                    Value given = arguments.getFirst();
                    String text = given instanceof BinaryValue octets
                            ? new String(octets.octetsFromHere(), StandardCharsets.ISO_8859_1)
                            : ((StringValue) given).text();
                    String tabbed = toTabs
                            ? text.replace(" ".repeat(width), "\t")
                            : text.replace("\t", " ".repeat(width));
                    return given instanceof BinaryValue
                            ? binaryOfBytes(tabbed.getBytes(StandardCharsets.ISO_8859_1))
                            : StringValue.of(tabbed, textDatatypeOf(given));
                });
    }

    private static void defineFreshWordsOf(
            BlockValue block, Context target, boolean settersOnly, boolean deeply) {

        List<Value> words = new ArrayList<>();
        gatherWords(block, deeply, settersOnly, words);
        words.forEach(word -> target.define(((WordValue) word).canonical()));
    }

    private static void gatherWords(
            BlockValue block, boolean deeply, boolean settersOnly, List<Value> found) {
        for (Value item : block.remaining()) {
            if (item instanceof BlockValue nested) {
                if (deeply) {
                    gatherWords(nested, true, settersOnly, found);
                }
                continue;
            }
            if (!(item instanceof WordValue word)) {
                continue;
            }
            if (settersOnly && word.datatype() != Datatype.SET_WORD) {
                continue;
            }
            WordValue plain = WordValue.of(word.spelling());
            if (found.stream().noneMatch(seen -> seen instanceof WordValue already
                    && already.canonical().equals(plain.canonical()))) {
                found.add(plain);
            }
        }
    }

    private List<Value> catalogueEntries() {
        try {
            TranscodeResult read = Transcoder.transcode(errorCatalogueSource);
            List<Value> values = read.values().orElseThrow().remaining();
            return values.subList(2, values.size());
        } catch (RuntimeException unreadable) {
            return List.of();
        }
    }

    private Map<String, BlockValue> declaredSpecs;

    private Map<String, BlockValue> declaredSpecs() {
        if (declaredSpecs != null) {
            return declaredSpecs;
        }
        Map<String, BlockValue> found = new LinkedHashMap<>();
        try {
            TranscodeResult read = Transcoder.transcode(functionDeclarationSource);
            List<Value> values = read.values().map(BlockValue::remaining).orElse(List.of());
            for (int at = 0; at + 2 < values.size(); at++) {
                if (values.get(at) instanceof WordValue declaring
                        && declaring.datatype() == Datatype.SET_WORD
                        && values.get(at + 1) instanceof WordValue kind
                        && (kind.canonical().equals("native")
                                || kind.canonical().equals("action"))
                        && values.get(at + 2) instanceof BlockValue spec
                        && spec.datatype() == Datatype.BLOCK) {
                    found.putIfAbsent(declaring.canonical(), spec);
                }
            }
        } catch (RuntimeException unreadable) {
            found.clear();
        }
        declaredSpecs = Map.copyOf(found);
        return declaredSpecs;
    }

    private Value applyWithRefinements(
            NativeValue builtIn, List<Value> supplied, Evaluator evaluator) {

        Set<String> asked = new LinkedHashSet<>();
        List<Value> beforeAnyRefinement = new ArrayList<>();
        Map<String, List<Value>> belongingTo = new LinkedHashMap<>();
        String reading = null;
        int at = 0;
        for (Value word : wordsOfBuiltIn(builtIn)) {
            Value next = at < supplied.size() ? supplied.get(at) : NoneValue.none();
            at++;
            if (word instanceof WordValue marker
                    && marker.datatype() == Datatype.REFINEMENT) {
                reading = marker.canonical();
                belongingTo.putIfAbsent(reading, new ArrayList<>());
                if (next.isTruthy()) {
                    asked.add(reading);
                }
            } else if (reading == null) {
                beforeAnyRefinement.add(next);
            } else {
                belongingTo.get(reading).add(next);
            }
        }

        NativeValue refined = builtIn.askedFor(asked);
        Deque<Value> plain = new ArrayDeque<>(beforeAnyRefinement);
        Map<String, Deque<Value>> refinementArguments = new LinkedHashMap<>();
        belongingTo.forEach((name, values) ->
                refinementArguments.put(name, new ArrayDeque<>(values)));

        List<Value> arguments = new ArrayList<>();
        for (Parameter parameter : refined.parameters()) {
            if (!parameter.consumesAnArgument()) {
                continue;
            }
            Optional<String> owner = parameter.owningRefinement();
            if (owner.isEmpty()) {
                arguments.add(plain.isEmpty() ? NoneValue.none() : plain.removeFirst());
            } else if (asked.contains(owner.get())) {
                Deque<Value> waiting = refinementArguments
                        .getOrDefault(owner.get(), new ArrayDeque<>());
                arguments.add(waiting.isEmpty() ? NoneValue.none() : waiting.removeFirst());
            }
        }
        return evaluator.applyFunction(refined, arguments);
    }

    private List<Value> wordsOfBuiltIn(NativeValue builtIn) {
        return wordsNamedIn(specOf(builtIn)) instanceof BlockValue words
                ? words.remaining()
                : List.of();
    }

    private Value specOf(NativeValue built) {
        if (built.ownSpec().isPresent()) {
            return built.ownSpec().orElseThrow();
        }
        BlockValue declared = declaredSpecs().get(built.nativeName());
        if (declared != null) {
            return declared;
        }
        if (ActionNames.testsADatatype(built.nativeName())) {
            return THE_SPEC_EVERY_DATATYPE_TEST_HAS;
        }
        return specBlockOf(built.parameters());
    }

    private static final BlockValue THE_SPEC_EVERY_DATATYPE_TEST_HAS =
            BlockValue.block(List.of(
                    StringValue.of("Returns TRUE if it is this type."),
                    WordValue.of("value"),
                    BlockValue.block(List.of(WordValue.of("any-type!")))));

    private static Value wordsNamedIn(Value spec) {
        if (!(spec instanceof BlockValue written)) {
            return NoneValue.none();
        }
        return BlockValue.block(written.remaining().stream()
                .filter(item -> item instanceof WordValue word && NAMES_A_PARAMETER
                        .contains(word.datatype()))
                .toList());
    }

    private static final Set<Datatype> NAMES_A_PARAMETER = Set.of(
            Datatype.WORD, Datatype.REFINEMENT, Datatype.LIT_WORD, Datatype.GET_WORD);

    private static Value doneAsAScript(
            BinaryValue bytes, Evaluator evaluator, Context context) {
        Value loadHeader = systemInternalFunction(
                evaluator.systemContext(), "load-header");
        Value read = evaluator.applyFunction(loadHeader, List.of(bytes));
        if (read instanceof WordValue why) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, why.spelling());
        }
        List<Value> parts = ((BlockValue) read).remaining();
        if (parts.getFirst() instanceof ObjectValue header
                && header.context().holds("needs")
                && header.context().slotFor("needs").value()
                        instanceof TupleValue wanted
                && !interpreterMeets(wanted, evaluator)) {
            throw new Raised(ErrorValue.of(SyntaxFailure.NEEDS.category(),
                    SyntaxFailure.NEEDS.errorId(),
                    SyntaxFailure.NEEDS.description()));
        }
        String body = parts.get(1) instanceof BinaryValue mark
                && parts.get(2) instanceof BinaryValue remaining
                && mark.sharesStorageWith(remaining)
                ? strictlyUtf8(spanOfOctets(mark, remaining.index()))
                : strictlyUtf8(((BinaryValue) parts.get(1)).octetsFromHere());
        try {
            return evaluator.evaluateSource(body);
        } catch (ReturnSignal returned) {
            return returned.value();
        }
    }

    private static Value runAsAScript(StringValue address, Evaluator evaluator) {
        Value doStar = systemInternalFunction(evaluator.systemContext(), "do*");
        return evaluator.applyFunction(doStar, List.of(address));
    }

    private static boolean interpreterMeets(TupleValue wanted, Evaluator evaluator) {
        Value version = pathInto(evaluator.systemContext(), "system", "version");
        return version instanceof TupleValue own
                && !Comparison.holds(wanted, own, Comparison.Strictness.GREATER);
    }

    private static byte[] spanOfOctets(BinaryValue from, int endIndex) {
        int howMany = Math.max(0, endIndex - from.index());
        byte[] span = new byte[howMany];
        for (int at = 0; at < howMany; at++) {
            span[at] = (byte) from.storage().at(from.index() + at);
        }
        return span;
    }

    private static BlockValue loadedForStepping(String source, Context context) {
        TranscodeResult read = Transcoder.transcode(source);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return Binder.bindAndDefine(read.values().orElseThrow(), context);
    }

    static SeriesValue clampedToTail(SeriesValue series) {
        int tail = series.storageLength() + 1;
        return series.index() > tail ? series.atIndex(tail) : series;
    }

    private static Value insertInto(SeriesValue stranded, Value value) {
        SeriesValue series = clampedToTail(stranded);
        switch (series) {
            case BlockValue block -> {
                BlockValue added = value instanceof BlockValue given ? given : null;
                block.storage().spliceInAt(block.index(),
                        added == null ? List.of(value) : added.remaining(),
                        added == null ? null : added.storage(),
                        added == null ? 1 : added.index());
            }
            case StringValue text -> {
                int[] added = Molder.form(value).codePoints().toArray();
                for (int at = 0; at < added.length; at++) {
                    text.storage().insertAt(text.index() + at, added[at]);
                }
            }
            case ImageValue image -> insertPixels(image, value);
            case GobValue gob -> new GobActions(gob)
                    .givenTheChildrenOf(value, gob.positionWithinThePane());
            case VectorValue vector -> {
                List<Value> numbers = numbersContributedTo(vector.kind(), value);
                for (int at = numbers.size(); at > 0; at--) {
                    vector.storage().insertAt(vector.index(),
                            VectorPath.storedFormOf(vector.kind(), numbers.get(at - 1)));
                }
            }
            case BinaryValue bytes -> {
                int[] octets = SeriesContents.octetsContributedBy(value);
                for (int at = octets.length; at > 0; at--) {
                    bytes.storage().insertAt(bytes.index(), octets[at - 1]);
                }
            }
        }
        return series.head();
    }

    private static int partCountFor(List<Value> arguments, Set<String> refinements) {
        Value limit = argumentFor(
                "part", List.of("part", "dup"), arguments, refinements, 2);
        if (limit instanceof IntegerValue wanted) {
            return (int) wanted.magnitude();
        }
        if (limit instanceof SeriesValue upTo
                && arguments.get(1) instanceof SeriesValue from
                && from.sharesStorageWith(upTo)) {
            return Math.abs(upTo.index() - from.index());
        }
        return -1;
    }

    private static void removeOneAt(SeriesValue series, int index) {
        switch (series) {
            case BlockValue block -> block.storage().removeAt(index);
            case StringValue text -> text.storage().removeAt(index);
            case BinaryValue bytes -> bytes.storage().removeAt(index);
            case ImageValue image -> image.storage().removeFrom(index, 1);
            case GobValue gob -> gob.storage().removeChildren(index, 1);
            case VectorValue vector -> vector.storage().removeAt(index);
        }
    }

    private static Value reversedTextACharacterAtATime(StringValue text) {
        int[] forwards = text.text().codePoints().toArray();
        for (int at = 0; at < forwards.length; at++) {
            text.storage().set(text.index() + at, forwards[forwards.length - 1 - at]);
        }
        return text;
    }

    private static Value reversedBytes(BinaryValue bytes) {
        List<Integer> forwards = new ArrayList<>();
        for (int at = bytes.index(); at <= bytes.storageLength(); at++) {
            forwards.add(bytes.storage().at(at));
        }
        for (int at = 0; at < forwards.size(); at++) {
            bytes.storage().set(bytes.index() + at, forwards.get(forwards.size() - 1 - at));
        }
        return bytes;
    }

    private static Value removedEachFromDecidingForwardsThenRewriting(
            SeriesValue series, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context within) {

        refuseIfProtected(series);
        Context locals = Context.loopFrameOf(within);
        WordValue word = (WordValue) arguments.getFirst();
        locals.define(word.spelling());
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        List<Value> kept = new ArrayList<>();
        int taken = 0;
        for (int at = series.index(); at <= series.storageLength(); at++) {
            Value item = switch (series) {
                case BinaryValue bytes -> IntegerValue.of(bytes.storage().at(at));
                case VectorValue numbers -> numbers.elementAt(at);
                default -> CharacterValue.of(((StringValue) series).storage().at(at));
            };
            locals.set(word.spelling(), item);
            if (evaluator.evaluateOrRaise(body, locals).isTruthy()) {
                taken++;
            } else {
                kept.add(item);
            }
        }
        for (int at = series.storageLength(); at >= series.index(); at--) {
            removeFrom(series, at, 1);
        }
        for (int at = 0; at < kept.size(); at++) {
            insertOneInto(series, series.index() + at, kept.get(at));
        }
        return refinements.contains("count") ? IntegerValue.of(taken) : series;
    }

    private static void insertOneInto(SeriesValue series, int at, Value item) {
        switch (series) {
            case BinaryValue bytes ->
                    bytes.storage().insertAt(at, (int) ((IntegerValue) item).magnitude());
            case VectorValue numbers ->
                    numbers.storage().insertAt(at, ((IntegerValue) item).magnitude());
            case StringValue text ->
                    text.storage().insertAt(at, ((CharacterValue) item).codepoint());
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE, "remove-each");
        }
    }

    private static Value removedEachPairFrom(
            MapValue map, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context within) {

        requireChangeable(map);
        List<WordValue> names = loopNamesIn(arguments.getFirst(), "remove-each");
        MapActions.refuseMoreNamesThanAPairHas(map, namesThatTakeAValue(names));
        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.define(name.spelling()));
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        List<Value> pairs = map.walkable();
        List<Value> takeOut = new ArrayList<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            setLoopNamesFillingWithNonePastTheEnd(locals, names, pairs, at, map);
            if (evaluator.evaluateOrRaise(body, locals).isTruthy()) {
                takeOut.add(pairs.get(at));
            }
        }
        takeOut.forEach(map::remove);
        return refinements.contains("count")
                ? IntegerValue.of(takeOut.size())
                : map;
    }

    private static void refuseIfProtected(SeriesValue series) {
        boolean guarded = switch (series) {
            case BlockValue block -> block.storage().isProtected();
            case StringValue text -> text.storage().isProtected();
            case BinaryValue bytes -> bytes.storage().isProtected();
            case ImageValue image -> image.storage().isProtected();
            case GobValue ignored -> false;
            case VectorValue vector -> vector.storage().isProtected();
        };
        if (guarded) {
            throw new org.jebol.domain.value.ProtectedFromChange();
        }
    }

    private static Value duplicated(
            Value value, List<Value> arguments, Set<String> refinements) {

        Value times = argumentFor(
                "dup", List.of("part", "dup"), arguments, refinements, 2);
        if (times == null) {
            return value;
        }
        BlockValue spread = value instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block
                : null;
        List<Value> pieces = spread == null ? List.of(value) : spread.remaining();
        BlockStorage repeated = new BlockStorage();
        for (long round = 0; round < wholeCountOf(times); round++) {
            repeated.spliceInAt(repeated.length() + 1, pieces,
                    spread == null ? null : spread.storage(),
                    spread == null ? 1 : spread.index());
        }
        return new BlockValue(repeated, 1, Datatype.BLOCK);
    }

    private static long wholeCountOf(Value times) {
        return switch (times) {
            case IntegerValue count -> count.magnitude();
            case DecimalValue fraction when fraction.datatype() != Datatype.PERCENT ->
                    (long) Comparison.asDouble(fraction);
            default -> throw Raised.of(EvaluationFailure.INVALID_TYPE,
                    Molder.mold(times) + " is not a count of repetitions");
        };
    }

    private static String withoutCommonIndent(String text) {
        String[] lines = text.split("\n", -1);
        int firstContentLine = 0;
        while (firstContentLine < lines.length && lines[firstContentLine].isBlank()) {
            firstContentLine++;
        }
        int indent = firstContentLine < lines.length
                ? lines[firstContentLine].length()
                        - lines[firstContentLine].stripLeading().length()
                : 0;
        StringBuilder trimmed = new StringBuilder();
        for (int at = firstContentLine; at < lines.length; at++) {
            String line = lines[at];
            int take = Math.min(indent, line.length() - line.stripLeading().length());
            trimmed.append(line.substring(take));
            if (at + 1 < lines.length) {
                trimmed.append('\n');
            }
        }
        return trimmed.toString();
    }

    private static String trimmedEachLine(String text) {
        String afterLead = text.stripLeading();
        String core = afterLead.stripTrailing();
        boolean endedWithLineFeed =
                afterLead.substring(core.length()).indexOf('\n') >= 0;
        String[] lines = core.split("\n", -1);
        StringBuilder joined = new StringBuilder();
        for (int at = 0; at < lines.length; at++) {
            if (at > 0) {
                joined.append('\n');
            }
            joined.append(lines[at].strip());
        }
        if (endedWithLineFeed) {
            joined.append('\n');
        }
        return joined.toString();
    }

    private static Set<String> namesIn(Value source) {
        return switch (source) {
            case BlockValue words -> words.remaining().stream()
                    .filter(WordValue.class::isInstance)
                    .map(WordValue.class::cast)
                    .map(WordValue::canonical)
                    .collect(java.util.stream.Collectors.toSet());
            case ObjectValue object -> object.context().slots().stream()
                    .map(ContextSlot::canonical)
                    .collect(java.util.stream.Collectors.toSet());
            default -> Set.of();
        };
    }

    private static Set<Datatype> aSeriesATupleOrAGob() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.SERIES.members());
        accepted.add(Datatype.TUPLE);
        accepted.add(Datatype.GOB);
        return Set.copyOf(accepted);
    }

    private static final Set<Datatype> SERIES_LIKE = EnumSet.of(
            Datatype.STRING, Datatype.FILE, Datatype.URL, Datatype.EMAIL,
            Datatype.TAG, Datatype.REF, Datatype.BINARY,
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH, Datatype.SET_PATH,
            Datatype.GET_PATH, Datatype.LIT_PATH, Datatype.HASH,
            Datatype.PORT, Datatype.BITSET,
            Datatype.TYPESET, Datatype.MAP, Datatype.GOB, Datatype.IMAGE,
            Datatype.VECTOR);

    private static final Set<Datatype> PARSEABLE = EnumSet.of(
            Datatype.BINARY, Datatype.STRING, Datatype.FILE, Datatype.EMAIL,
            Datatype.REF, Datatype.URL, Datatype.TAG, Datatype.IMAGE,
            Datatype.VECTOR, Datatype.BLOCK, Datatype.PAREN, Datatype.PATH,
            Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH,
            Datatype.HASH);

    private void defineSetOperation(String name, Combining.Sets how) {
        define(name, List.of(
                        Parameter.required("first", setOperandOr(Datatype.BLOCK)),
                        Parameter.required("second", setOperandOr(Datatype.BLOCK)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    Value width = argumentFor("skip", List.of("skip"), arguments, refinements, 2);
                    return Combining.sets(arguments.get(0), arguments.get(1), how,
                            refinements.contains("case"), recordWidthOf(width));
                });
    }

    private static int recordWidthOf(Value width) {
        if (!(width instanceof IntegerValue wanted)) {
            return 1;
        }
        if (wanted.magnitude() < 1) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(wanted));
        }
        return (int) wanted.magnitude();
    }

    private static Value copied(Value original, boolean deeply) {
        return copied(original, deeply, DEEP_COPIED);
    }

    private record MarkedItem(Value value, boolean breaksLine) {
    }

    private static List<MarkedItem> theMarkedItemsOf(BlockValue block, int howMany) {
        List<MarkedItem> slots = new ArrayList<>(howMany);
        for (int at = 0; at < howMany; at++) {
            slots.add(new MarkedItem(
                    block.storage().at(block.index() + at),
                    block.storage().breaksLineAt(block.index() + at)));
        }
        return slots;
    }

    private static void writeBackMarkedItems(BlockValue block, List<MarkedItem> slots) {
        for (int at = 0; at < slots.size(); at++) {
            block.storage().set(block.index() + at, slots.get(at).value());
            block.storage().setLineBreakAt(
                    block.index() + at, slots.get(at).breaksLine());
        }
    }

    static BlockValue laidOutLike(BlockValue source, BlockStorage built) {
        built.takeLineBreaksFrom(source.storage(), source.index());
        return new BlockValue(built, 1, source.datatype());
    }

    private static final Set<Datatype> DEEP_COPIED = EnumSet.of(
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH, Datatype.SET_PATH,
            Datatype.GET_PATH, Datatype.LIT_PATH, Datatype.HASH,
            Datatype.STRING, Datatype.FILE, Datatype.URL, Datatype.EMAIL,
            Datatype.TAG, Datatype.REF, Datatype.BINARY, Datatype.BITSET,
            Datatype.MAP, Datatype.FUNCTION);

    private static Set<Datatype> whichDatatypesToCopy(
            List<Value> arguments, Set<String> refinements) {

        if (!refinements.contains("types")) {
            return refinements.contains("deep") ? DEEP_COPIED : EnumSet.noneOf(Datatype.class);
        }
        Value kinds = arguments.getLast();
        return switch (kinds) {
            case DatatypeValue one -> EnumSet.of(one.represents());
            case TypesetValue several -> EnumSet.copyOf(several.members());
            default -> DEEP_COPIED;
        };
    }

    private static Value copied(Value original, boolean deeply, Set<Datatype> kinds) {
        if (!kinds.contains(original.datatype()) && original != null
                && !(original instanceof SeriesValue) && !(original instanceof MapValue)
                && !(original instanceof BitsetValue)
                && !(original instanceof ErrorValue)
                && !(original instanceof ObjectValue)) {
            return original;
        }
        return switch (original) {
            case BlockValue block -> laidOutLike(block, new BlockStorage(
                    block.remaining().stream()
                            .map(item -> memberCopiedFrom(item, deeply, kinds))
                            .toList()));
            case StringValue text -> StringValue.of(text.text(), text.datatype());
            case BinaryValue binary -> copiedBytes(binary, binary.lengthFromHere());
            case VectorValue vector -> copiedElements(vector, vector.lengthFromHere());
            case BitsetValue members -> members.duplicate();
            case ImageValue picture ->
                    copiedPixelsAsWholeRows(picture, picture.lengthFromHere());
            case MapValue pairs -> {
                List<Value> flattened = pairs.flattened();
                List<Value> copiedPairs = new ArrayList<>(flattened.size());
                for (int at = 0; at < flattened.size(); at++) {
                    boolean isaValueRatherThanAKey = at % 2 == 1;
                    copiedPairs.add(isaValueRatherThanAKey
                            ? memberCopiedFrom(flattened.get(at), deeply, kinds)
                            : flattened.get(at));
                }
                yield MapValue.of(copiedPairs);
            }
            case ObjectValue object -> {
                Context fields = Context.root();
                ObjectValue duplicate = new ObjectValue(fields);
                fields.set("self", duplicate);
                object.context().slots().stream()
                        .filter(slot -> !slot.canonical().equals("self"))
                        .forEach(slot -> fields.set(slot.spelling(),
                                memberCopiedFrom(slot.value(), deeply, kinds)));
                yield duplicate;
            }
            case ErrorValue raised -> anErrorWhoseFieldsAreItsOwn(raised, deeply, kinds);
            default -> original;
        };
    }

    private static boolean hasNoOrderToTakeTheFirstSoManyOf(Value subject) {
        return subject instanceof ObjectValue || subject instanceof ErrorValue
                || subject instanceof ModuleValue || subject instanceof PortValue;
    }

    private static Value anErrorWhoseFieldsAreItsOwn(
            ErrorValue raised, boolean deeply, Set<Datatype> kinds) {

        Map<String, Value> fields = new LinkedHashMap<>();
        for (String name : ErrorValue.FIELDS) {
            raised.field(name).ifPresent(held ->
                    fields.put(name, memberCopiedFrom(held, deeply, kinds)));
        }
        return new ErrorValue(raised.category(), raised.errorId(), raised.message(),
                raised.subject(), raised.secondArgument(), raised.thirdArgument(),
                raised.near(), raised.whereChain(), fields);
    }

    private static Value memberCopiedFrom(Value member, boolean deeply, Set<Datatype> kinds) {
        if (!kinds.contains(member.datatype())) {
            return member;
        }
        return copied(member, deeply, deeply ? kinds : NOTHING_INSIDE);
    }

    private static final Set<Datatype> NOTHING_INSIDE = EnumSet.noneOf(Datatype.class);

    private static String theFirstCodePointsOf(String text, int wanted) {
        int taking = Math.min(wanted, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, taking));
    }

    private static Value copiedFront(
            SeriesValue series, Value limit, boolean deeply, Set<Datatype> kinds) {
        long wanted = countUpTo(series, limit);
        SeriesValue from = limit instanceof SeriesValue upTo
                ? earlierOf(series, upTo)
                : series;
        if (wanted < 0) {
            int behind = from.index() - 1;
            int reaching = (int) Math.min(-wanted, behind);
            from = from.atIndex(from.index() - reaching);
            wanted = reaching;
        }
        int taking = (int) Math.max(0, Math.min(wanted, from.lengthFromHere()));
        return switch (from) {
            case BlockValue block -> laidOutLike(block, new BlockStorage(
                    block.remaining().subList(0, taking).stream()
                            .map(item -> deeply && kinds.contains(item.datatype())
                                    ? copied(item, true, kinds)
                                    : item)
                            .toList()));
            case StringValue text -> StringValue.of(
                    theFirstCodePointsOf(text.text(), taking), text.datatype());
            case BinaryValue bytes -> copiedBytes(bytes, taking);
            case ImageValue image -> copiedPixelsAsWholeRows(image, taking);
            case GobValue gob -> raiseCannotUse(gob, "copy");
            case VectorValue vector -> copiedElements(vector, taking);
        };
    }

    private static VectorValue copiedElements(VectorValue vector, int howMany) {
        VectorStorage made = new VectorStorage(vector.kind(), 0);
        for (int at = 0; at < howMany; at++) {
            made.append(vector.storage().at(vector.index() + at));
        }
        return new VectorValue(made, 1);
    }

    private static BinaryValue copiedBytes(BinaryValue bytes, int howMany) {
        BinaryStorage copiedStorage = new BinaryStorage();
        for (int at = 0; at < howMany; at++) {
            copiedStorage.append(bytes.storage().at(bytes.index() + at));
        }
        return new BinaryValue(copiedStorage, 1);
    }

    private static Value branchTaken(
            Value branch, Evaluator evaluator, Context context, Set<String> refinements) {

        return branch instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                && !refinements.contains("only")
                ? evaluator.evaluateOrRaise(block, context)
                : branch;
    }

    private static Value reversedFrontInPlace(SeriesValue series, Value limit) {
        int howMany = limit instanceof IntegerValue wanted
                ? (int) Math.max(0, Math.min(wanted.magnitude(), series.lengthFromHere()))
                : series.lengthFromHere();
        return switch (series) {
            case BlockValue block -> {
                List<MarkedItem> front = theMarkedItemsOf(block, howMany);
                Collections.reverse(front);
                writeBackMarkedItems(block, front);
                yield block;
            }
            case GobValue gob -> raiseCannotUse(gob, "reverse/part");
            case VectorValue vector -> {
                for (int at = 0; at < howMany / 2; at++) {
                    int near = vector.index() + at;
                    int far = vector.index() + howMany - 1 - at;
                    long held = vector.storage().at(near);
                    vector.storage().set(near, vector.storage().at(far));
                    vector.storage().set(far, held);
                }
                yield vector;
            }
            case ImageValue image -> {
                for (int at = 0; at < howMany / 2; at++) {
                    int[] near = image.pixelAt(at + 1);
                    int[] far = image.pixelAt(howMany - at);
                    writePixel(image, at + 1, far);
                    writePixel(image, howMany - at, near);
                }
                yield image;
            }
            case StringValue text -> rewrittenInPlace(text, whole ->
                    new StringBuilder(whole.substring(0, howMany)).reverse()
                            + whole.substring(howMany));
            case BinaryValue bytes -> {
                int[] front = new int[howMany];
                for (int at = 0; at < howMany; at++) {
                    front[at] = bytes.storage().at(bytes.index() + howMany - 1 - at);
                }
                for (int at = 0; at < howMany; at++) {
                    bytes.storage().set(bytes.index() + at, front[at]);
                }
                yield bytes;
            }
        };
    }

    private static void removeKeyedPair(BlockValue pairs, Value key) {
        List<Value> items = pairs.remaining();
        for (int at = 0; at + 1 < items.size(); at += 2) {
            if (Comparison.identicallyEqual(items.get(at), key)) {
                pairs.storage().removeAt(pairs.index() + at);
                pairs.storage().removeAt(pairs.index() + at);
                return;
            }
        }
    }

    static void refuseHiddenField(ObjectValue object, Value target) {
        List<Value> names = target instanceof BlockValue pairs
                ? pairs.remaining()
                : List.of(target);
        for (Value name : names) {
            if (name instanceof WordValue word
                    && object.context().everySlot().stream().anyMatch(
                            slot -> slot.isHidden()
                                    && slot.canonical().equals(word.canonical()))) {
                throw Raised.of(EvaluationFailure.HIDDEN, word.spelling());
            }
        }
    }

    private static List<Value> reducedOnlyWords(
            BlockValue block, Evaluator evaluator, Value exceptions) {

        Set<String> kept = exceptions instanceof BlockValue excepted
                ? excepted.remaining().stream()
                        .filter(WordValue.class::isInstance)
                        .map(word -> ((WordValue) word).canonical())
                        .collect(java.util.stream.Collectors.toSet())
                : Set.of();
        List<Value> results = new ArrayList<>();
        for (Value item : block.remaining()) {
            if (item instanceof WordValue word && word.datatype() == Datatype.WORD
                    && !kept.contains(word.canonical())) {
                Value held = slotOf(word).value();
                if (held instanceof UnsetValue) {
                    throw Raised.of(EvaluationFailure.NO_VALUE, word.spelling());
                }
                results.add(held);
            } else {
                results.add(item);
            }
        }
        return results;
    }

    private static List<Value> reducedLeavingSetWords(BlockValue block, Evaluator evaluator) {
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
            Evaluator.Step step = evaluator.evaluateNextOrRaise(at, evaluator.systemContext());
            results.add(step.value());
            at = at.atIndex(step.nextIndex());
        }
        return results;
    }

    private static void hideEachWordIn(BlockValue names) {
        names.remaining().stream()
                .filter(WordValue.class::isInstance)
                .map(WordValue.class::cast)
                .forEach(word -> slotOf(word).hide(true));
    }

    private static final List<String> CONSOLE_MEASUREMENTS =
            List.of("window-cols", "window-rows", "buffer-cols", "buffer-rows");

    private static final int COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE = 80;

    private static int measureOfTheConsole(String measurement) {
        return measurement.equals("window-cols")
                ? COLUMNS_A_TERMINAL_IS_ASSUMED_TO_HAVE
                : 0;
    }

    private static boolean isAFilePort(PortValue port) {
        return port.schemeName().equals("file") || port.schemeName().equals("dir");
    }

    private Value lengthLeftInTheFile(PortValue port, Evaluator evaluator) {
        refuseAClosedPosition(port);
        requireService(HostService.FILES);
        return throughPort(() ->
                SeekableFilePort.lengthLeft(evaluator.files(), port));
    }

    private static void refuseAClosedPosition(PortValue port) {
        if (!port.isOpen()) {
            throw Raised.of(EvaluationFailure.NOT_OPEN,
                    SeekableFilePort.pathOf(port));
        }
    }

    private Value wholeSizeOfTheFile(PortValue port, Evaluator evaluator) {
        requireService(HostService.FILES);
        return throughPort(() -> SeekableFilePort.wholeSize(evaluator.files(), port));
    }

    private boolean theFileIsAtItsEnd(PortValue port, Evaluator evaluator) {
        refuseAClosedPosition(port);
        requireService(HostService.FILES);
        return ((LogicValue) throughPort(() -> LogicValue.of(
                SeekableFilePort.atTail(evaluator.files(), port)))).truth();
    }


    private Value movedWithinTheFile(PortValue port, Evaluator evaluator, long to) {
        refuseAClosedPosition(port);
        requireService(HostService.FILES);
        long size = ((IntegerValue) wholeSizeOfTheFile(port, evaluator)).magnitude();
        SeekableFilePort.moveTo(port, Math.max(0, Math.min(to, size)));
        return port;
    }

    private Value truncatedAtThePosition(PortValue port, Evaluator evaluator) {
        refuseAClosedPosition(port);
        refuseAPortOpenedOnlyToRead(port, EvaluationFailure.WRITE_ERROR);
        requireService(HostService.FILES);
        return throughPort(() -> {
            String path = SeekableFilePort.pathOf(port);
            byte[] whole = evaluator.files().readBytes(path);
            long keeping = Math.min(SeekableFilePort.positionOf(port), whole.length);
            evaluator.files().write(path, Arrays.copyOf(whole, (int) keeping));
            return port;
        });
    }

    private Value readFromTheFileBehind(
            PortValue port, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {

        requireService(HostService.FILES);
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
        if (seek instanceof IntegerValue where) {
            SeekableFilePort.moveTo(port, where.magnitude());
        }
        Value part = refinements.contains("part")
                ? argumentFor("part", List.of("part", "seek"), arguments, refinements, 1)
                : null;
        if (part instanceof IntegerValue wanted
                && wanted.magnitude() < 0
                && -wanted.magnitude() > SeekableFilePort.positionOf(port)) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, part);
        }
        Value read = throughPort(() -> SeekableFilePort.readFrom(
                evaluator.files(), port,
                part instanceof IntegerValue wanted ? wanted.magnitude() : null));
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
                ? BlockValue.block(linesOfDroppingExactlyOneTrailingEmptyLine(
                        text.orElseThrow()))
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
        return grantedServices.contains(HostService.ENVIRONMENT);
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
                    .anyMatch(name -> FileReading.matchesTheWholeOf(
                            FileReading.withoutItsSlash(name), pattern));
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

    private static void refuseASeriesCarryingAZero(Value series) {
        if (theWholeSeriesCarriesAZero(series)) {
            throw Raised.of(EvaluationFailure.BAD_SERIES);
        }
    }

    private static boolean theWholeSeriesCarriesAZero(Value series) {
        return switch (series) {
            case StringValue text -> text.head().text().indexOf(0) >= 0;
            case BinaryValue octets -> {
                for (byte one : octets.head().octetsFromHere()) {
                    if (one == 0) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> false;
        };
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

    private static boolean carriesProtection(Value value) {
        return value instanceof SeriesValue
                || value instanceof ObjectValue
                || value instanceof MapValue;
    }

    private static boolean isAPath(BlockValue block) {
        return block.datatype() == Datatype.PATH
                || block.datatype() == Datatype.LIT_PATH
                || block.datatype() == Datatype.GET_PATH
                || block.datatype() == Datatype.SET_PATH;
    }

    private static boolean protectFieldNamedBy(
            Value target, boolean protectedNow, Set<String> refinements) {

        if (!(target instanceof BlockValue path) || !isAPath(path)) {
            return false;
        }
        if (refinements.contains("values")) {
            return false;
        }
        ContextSlot field = fieldNamedBy(path.remaining());
        if (field == null) {
            return true;
        }
        if (refinements.contains("hide")) {
            field.hide(protectedNow);
            return true;
        }
        if (protectedNow) {
            field.protectFromAssignment();
        } else {
            field.allowAssignment();
        }
        if (refinements.contains("deep") && carriesProtection(field.value())) {
            setProtection(field.value(), protectedNow, true, refinements.contains("words"));
        }
        return true;
    }

    private static ContextSlot fieldNamedBy(List<Value> segments) {
        if (segments.size() < 2 || !(segments.getFirst() instanceof WordValue start)
                || !start.isBound() || !start.binding().knows(start.canonical())) {
            return null;
        }
        Value reached = start.binding().slotFor(start.canonical()).value();
        for (int at = 1; at < segments.size() - 1; at++) {
            if (!(reached instanceof ObjectValue step)
                    || !(segments.get(at) instanceof WordValue between)
                    || !step.context().holds(between.canonical())) {
                return null;
            }
            reached = step.context().ownSlotFor(between.canonical()).value();
        }
        if (!(reached instanceof ObjectValue holder)
                || !(segments.getLast() instanceof WordValue last)
                || !holder.context().holds(last.canonical())) {
            return null;
        }
        return holder.context().ownSlotFor(last.canonical());
    }

    private static boolean protectNamed(
            Value target, boolean protectedNow, Set<String> refinements) {

        boolean values = refinements.contains("values");
        boolean words = refinements.contains("words");
        if (!(values || words)) {
            return false;
        }
        List<Value> items = switch (target) {
            case BlockValue block when !isAPath(block) -> block.remaining();
            case WordValue only -> List.of(only);
            default -> List.of();
        };
        if (items.isEmpty()) {
            return false;
        }
        for (Value item : items) {
            ContextSlot slot = slotNamedInAList(item);
            if (slot == null) {
                continue;
            }
            if (values && carriesProtection(slot.value())) {
                setProtection(slot.value(), protectedNow,
                        refinements.contains("deep"));
            }
            if (words && refinements.contains("deep")
                    && carriesProtection(slot.value())) {
                setProtection(slot.value(), protectedNow, true, true);
            }
            if (words) {
                if (!protectedNow) {
                    slot.allowAssignment();
                } else if (refinements.contains("lock")) {
                    slot.protectForGood();
                } else {
                    slot.protectFromAssignment();
                }
            }
        }
        return true;
    }

    private static ContextSlot slotNamedInAList(Value item) {
        if (item instanceof BlockValue path && isAPath(path)) {
            return fieldNamedBy(path.remaining());
        }
        if (item instanceof WordValue word && word.isBound()
                && word.binding().knows(word.canonical())) {
            return word.binding().slotFor(word.canonical());
        }
        return null;
    }

    private static void setProtection(Value target, boolean protectedNow, boolean deeply) {
        setProtection(target, protectedNow, deeply, false);
    }

    private static void setProtection(
            Value target, boolean protectedNow, boolean deeply, boolean onlyTheWords) {
        switch (target) {
            case BlockValue block -> {
                block.storage().protectFromChange(protectedNow);
                if (deeply) {
                    block.remaining().stream()
                            .filter(item -> item instanceof SeriesValue
                                    || item instanceof ObjectValue)
                            .forEach(item -> setProtection(item, protectedNow, true));
                }
            }
            case StringValue text -> text.storage().protectFromChange(protectedNow);
            case BinaryValue bytes -> bytes.storage().protectFromChange(protectedNow);
            case MapValue map -> map.protectFromChange(protectedNow);
            case ObjectValue object -> {
                if (!onlyTheWords) {
                    object.context().closeToNewNames(protectedNow);
                }
                object.context().slots().forEach(slot -> {
                if (protectedNow) {
                    slot.protectFromAssignment();
                } else {
                    slot.allowAssignment();
                }
                if (deeply && !slot.canonical().equals("self")
                        && (slot.value() instanceof SeriesValue
                                || slot.value() instanceof ObjectValue)) {
                    setProtection(slot.value(), protectedNow, true);
                }
                });
            }
            case WordValue word -> {
                if (protectedNow) {
                    slotOf(word).protectFromAssignment();
                } else {
                    slotOf(word).allowAssignment();
                }
                if (deeply && carriesProtection(slotOf(word).value())) {
                    setProtection(slotOf(word).value(), protectedNow, true, onlyTheWords);
                }
            }
            case BitsetValue members -> members.protectFromChange(protectedNow);
            case VectorValue vector -> vector.storage().protectFromChange(protectedNow);
            default -> raiseCannotUse(target, "protect");
        }
    }

    /**
     * Reads /PART, /ONLY and /DUP once, so that the arm the action lands in
     * is handed an answered question rather than the plumbing.
     */
    private static Asked askedOf(
            Value subject, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context context) {

        return Asked.reading(subject, arguments.get(1), refinements,
                refinement -> argumentFor(refinement,
                        List.of("part", "dup"), arguments, refinements, 2),
                evaluator, context);
    }

    static void requireChangeable(Value series) {
        boolean refused = switch (series) {
            case BlockValue block -> block.storage().isProtected();
            case StringValue text -> text.storage().isProtected();
            case BinaryValue bytes -> bytes.storage().isProtected();
            case MapValue map -> map.isProtected();
            case BitsetValue members -> members.isProtected();
            default -> false;
        };
        if (refused) {
            throw Raised.of(EvaluationFailure.PROTECTED,
                    series.datatype().literalSpelling() + " is protected");
        }
    }

    private static long countUpTo(SeriesValue series, Value howMuch) {
        if (howMuch instanceof IntegerValue count) {
            if (count.magnitude() < Integer.MIN_VALUE
                    || count.magnitude() > Integer.MAX_VALUE) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(count));
            }
            return count.magnitude();
        }
        if (howMuch instanceof DecimalValue count
                && count.datatype() != Datatype.PERCENT) {
            return (long) count.quantity();
        }
        if (!(howMuch instanceof SeriesValue upTo)
                || !series.sharesStorageWith(upTo)) {
            throw Raised.of(EvaluationFailure.INVALID_PART, Molder.mold(howMuch));
        }
        return Math.abs(upTo.index() - series.index());
    }

    private static SeriesValue earlierOf(SeriesValue series, SeriesValue other) {
        return other.index() < series.index() ? other : series;
    }

    private static int clampedPosition(SeriesValue series, long wanted) {
        return (int) Math.max(1, Math.min(wanted, series.storageLength() + 1));
    }

    private static Value pickFrom(Value target, Value selector) {
        return switch (target) {
            case BitsetValue members -> new BitsetActions(members).heldForAPath(selector);
            case MapValue map -> map.select(selector);
            case DateValue date -> DateParts.of(date, selector);
            case TimeValue time -> pickTimePart(time, selector);
            case GobValue gob -> GobPath.childOf(gob, positionPickedFrom(selector));
            default -> selector instanceof IntegerValue position
                    ? pick(target, (int) position.magnitude())
                    : raiseCannotUse(target, "pick");
        };
    }

    private static Value pickTimePart(TimeValue time, Value selector) {
        long seconds = Math.abs(time.nanoseconds()) / TimeValue.NANOSECONDS_PER_SECOND;
        long fraction = Math.abs(time.nanoseconds()) % TimeValue.NANOSECONDS_PER_SECOND;
        String part = selector instanceof WordValue asked
                ? asked.canonical()
                : positionAsTimePartName(selector);
        return switch (part) {
            case "hour" -> IntegerValue.of(seconds / 3600);
            case "minute" -> IntegerValue.of(seconds / 60 % 60);
            case "second" -> fraction == 0
                    ? IntegerValue.of(seconds % 60)
                    : DecimalValue.of(
                            seconds % 60 + (double) fraction / TimeValue.NANOSECONDS_PER_SECOND);
            default -> NoneValue.none();
        };
    }

    private static String positionAsTimePartName(Value selector) {
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

    private static Value pick(Value target, int oneBasedIndex) {
        if (target instanceof TupleValue tuple) {
            return oneBasedIndex < 1 || oneBasedIndex > tuple.shownCount()
                    ? NoneValue.none()
                    : IntegerValue.of(tuple.octetAt(oneBasedIndex));
        }
        if (target instanceof PairValue pair) {
            return pair.halfAt(oneBasedIndex).orElseGet(NoneValue::none);
        }
        if (target instanceof GobValue gob) {
            return GobPath.childOf(gob, oneBasedIndex);
        }
        if (!(target instanceof SeriesValue series)) {
            return raiseCannotUse(target, "pick");
        }
        if (oneBasedIndex == 0) {
            return NoneValue.none();
        }
        int counted = oneBasedIndex < 0 ? oneBasedIndex + 1 : oneBasedIndex;
        int at = series.index() + counted - 1;
        if (at < 1 || at > series.storageLength()) {
            return NoneValue.none();
        }
        return switch (series) {
            case BlockValue block -> block.storage().at(at);
            case StringValue string -> CharacterValue.of(string.storage().at(at));
            case BinaryValue binary -> IntegerValue.of(binary.storage().at(at));
            case ImageValue image -> ImagePath.read(image.head(), IntegerValue.of(at));
            case GobValue gob -> GobPath.childOf(gob.head(), at);
            case VectorValue vector -> vector.elementAt(at);
        };
    }

    private static long positionPokedAt(Value given) {
        return switch (given) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction -> (long) fraction.quantity();
            case NoneValue ignored -> 0;
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a position is a number, not "
                            + given.datatype().literalSpelling());
        };
    }

    private static long positionPickedFrom(Value given) {
        return positionPokedAt(given);
    }

    private static void refuseUnfinishedRefinements(Set<String> refinements, String what) {
        for (String unfinished : List.of("part", "only", "dup")) {
            if (refinements.contains(unfinished)) {
                throw Raised.of(EvaluationFailure.FEATURE_NA,
                        what + "/" + unfinished + " on a gob is not implemented");
            }
        }
    }

    private static Value madeGob(Value from, Evaluator evaluator, Context context) {
        if (from instanceof GobValue cloned) {
            return new GobValue(cloned.storage().copyWithoutPane(), 1);
        }
        GobValue made = GobValue.empty();
        if (from instanceof PairValue size) {
            made.storage().size(size);
            return made;
        }
        if (from instanceof BlockValue spec && spec.datatype() == Datatype.BLOCK) {
            fillGobFromSpec(made, spec.remaining(), evaluator, context);
            return made;
        }
        throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                "a gob is made from a block, a gob or a pair, not "
                        + from.datatype().literalSpelling());
    }

    private static void fillGobFromSpec(
            GobValue gob, List<Value> spec, Evaluator evaluator, Context context) {
        for (int at = 0; at < spec.size(); at += 2) {
            Value name = spec.get(at);
            if (!(name instanceof WordValue field)
                    || field.datatype() != Datatype.SET_WORD) {
                throw Raised.of(EvaluationFailure.EXPECT_VAL,
                        DatatypeValue.of(Datatype.SET_WORD),
                        DatatypeValue.of(name.datatype()));
            }
            Value given = at + 1 < spec.size() ? spec.get(at + 1) : UnsetValue.unset();
            if (given.datatype() == Datatype.UNSET
                    || given.datatype() == Datatype.SET_WORD) {
                throw Raised.of(EvaluationFailure.NEED_VALUE, field.spelling());
            }
            Value written = simpleValueOf(given, evaluator, context);
            if (!GobPath.accepted(gob.storage(), field.canonical(), written)) {
                throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                        WordValue.of(field.spelling()),
                        DatatypeValue.of(written.datatype()));
            }
        }
    }

    private static final int DEEPEST_GOB_WALK = 1000;

    private static Value mappedInwards(GobValue from, PairValue point) {
        GobValue reached = from;
        double takenX = 0;
        double takenY = 0;
        for (int depth = 0; depth < DEEPEST_GOB_WALK; depth++) {
            GobValue entered = null;
            for (int at = reached.storage().length(); at >= 1 && entered == null; at--) {
                if (!(reached.storage().childAt(at) instanceof GobValue child)) {
                    continue;
                }
                double left = takenX + child.storage().offset().x();
                double top = takenY + child.storage().offset().y();
                if (point.x() >= left
                        && point.x() < left + child.storage().size().x()
                        && point.y() >= top
                        && point.y() < top + child.storage().size().y()) {
                    takenX = left;
                    takenY = top;
                    entered = child;
                }
            }
            if (entered == null) {
                break;
            }
            reached = entered;
        }
        return gobAndPoint(reached,
                PairValue.of(point.x() - takenX, point.y() - takenY));
    }

    private static Value mappedOutwards(GobValue from, PairValue point) {
        GobValue reached = from;
        double addedX = point.x();
        double addedY = point.y();
        for (int depth = 0; depth < DEEPEST_GOB_WALK
                && reached.storage().parent() != null; depth++) {
            addedX += reached.storage().offset().x();
            addedY += reached.storage().offset().y();
            reached = new GobValue(reached.storage().parent(), 1);
        }
        return gobAndPoint(reached, PairValue.of(addedX, addedY));
    }

    private static Value mappedEvent(EventValue event) {
        if (!(event.attached() instanceof GobValue gob)
                || !event.has(EventValue.Flag.HAS_XY)) {
            return event;
        }
        Value reached = mappedInwards(gob,
                PairValue.of(event.offsetX(), event.offsetY()));
        List<Value> gobAndPoint = ((BlockValue) reached).remaining();
        PairValue inside = (PairValue) gobAndPoint.get(1);
        return event
                .withAttached(EventValue.Model.GUI, gobAndPoint.getFirst())
                .withData(EventValue.packedOffset(
                                (int) Math.round(inside.x()), (int) Math.round(inside.y())),
                        EventValue.Flag.HAS_XY);
    }

    private static Value wokenPort(PortValue port, Value event, Evaluator evaluator) {
        if (!port.context().holds("awake")) {
            return LogicValue.yes();
        }
        Value awake = port.context().ownSlotFor("awake").value();
        if (!awake.datatype().isAnyFunction()) {
            return LogicValue.yes();
        }
        Value said = evaluator.applyFunction(awake, List.of(event));
        return LogicValue.of(said instanceof LogicValue answered && answered.truth());
    }

    private static Value howLongToWaitAmong(List<Value> waitedOn) {
        return waitedOn.stream()
                .filter(each -> each instanceof IntegerValue
                        || each instanceof DecimalValue
                        || each instanceof TimeValue)
                .findFirst()
                .orElse(NoneValue.none());
    }

    private static Value whicheverPortWoke(
            List<Value> waitedOn, Evaluator evaluator) {

        if (!(evaluator.hostPort("system") instanceof PortValue queue)
                || !queue.fieldNamed("awake").datatype().isAnyFunction()) {
            return NoneValue.none();
        }
        BlockValue ports = BlockValue.block(new ArrayList<>(waitedOn));
        while (true) {
            Value said = evaluator.applyFunction(
                    queue.fieldNamed("awake"), List.of(queue, ports));
            if (said instanceof LogicValue answered && answered.truth()) {
                return theFirstWokenAmongEmptyingTheWakeList(waitedOn, queue);
            }
            if (!(said instanceof LogicValue)) {
                theWakeListOf(queue).ifPresent(Natives::emptied);
                return NoneValue.none();
            }
        }
    }

    private static Value theFirstWokenAmongEmptyingTheWakeList(
            List<Value> waitedOn, PortValue queue) {
        List<Value> woken = theWakeListOf(queue)
                .map(BlockValue::remaining)
                .orElse(List.of());
        Value answer = waitedOn.stream()
                .filter(one -> one instanceof PortValue && woken.contains(one))
                .findFirst()
                .orElse(NoneValue.none());
        theWakeListOf(queue).ifPresent(Natives::emptied);
        return answer;
    }

    private static Optional<BlockValue> theWakeListOf(PortValue queue) {
        return queue.fieldNamed("data") instanceof BlockValue list
                ? Optional.of(list)
                : Optional.empty();
    }

    private static void emptied(BlockValue list) {
        while (list.storage().length() > 0) {
            list.storage().removeAt(1);
        }
    }

    private static void queueWhatHappenedTo(
            PortValue port, String happened, Evaluator evaluator) {

        if (!(evaluator.hostPort("system") instanceof PortValue queue)) {
            return;
        }
        theEventQueueOf(queue).ifPresent(onIt -> onIt.storage().insertAt(
                onIt.storage().length() + 1,
                new EventValue(EventCatalogue.typeIndexOf(happened).orElseThrow(),
                        Set.of(), EventValue.Model.PORT, 0, port)));
    }

    private static Value waitedOnTheScreen(PortValue port, Evaluator evaluator) {
        while (theScreenStillHasSomethingToSay(evaluator)) {
            for (ScreenEvent reported : evaluator.screen().takeQueuedEvents()) {
                if (wokenPort(port, guiEventFor(reported), evaluator)
                        instanceof LogicValue said && said.truth()) {
                    return NoneValue.none();
                }
            }
            if (!theScreenStillHasSomethingToSay(evaluator)) {
                return NoneValue.none();
            }
            sleepInterruptibly(SCREEN_POLL_MILLISECONDS, evaluator);
        }
        return NoneValue.none();
    }

    private static boolean theScreenStillHasSomethingToSay(Evaluator evaluator) {
        Value root = pathInto(
                evaluator.systemContext(), "system", "view", "screen-gob");
        return root instanceof GobValue gob && gob.storage().length() > 0;
    }

    private static final long SCREEN_POLL_MILLISECONDS = 10;

    private static EventValue guiEventFor(ScreenEvent reported) {
        return EventValue.fresh()
                .withType(EventCatalogue.typeIndexOf(reported.kind().spelling())
                        .orElse(0))
                .withAttached(EventValue.Model.GUI,
                        reported.window() == null
                                ? NoneValue.none()
                                : reported.window());
    }

    private static final int CODEC_HANDLE_IDENTITY = 1000;

    private static Value ranCodec(HandleValue handle, WordValue action, Value data) {
        if (!handle.typeName().equals("codec")) {
            throw Raised.of(EvaluationFailure.INVALID_HANDLE,
                    "a codec was wanted, not a " + handle.typeName() + " handle");
        }
        Codecs.Action asked = switch (action.canonical()) {
            case "identify" -> Codecs.Action.IDENTIFY;
            case "decode" -> Codecs.Action.DECODE;
            case "encode" -> Codecs.Action.ENCODE;
            default -> throw Raised.of(
                    EvaluationFailure.INVALID_ARG, action.spelling());
        };
        if (asked == Codecs.Action.ENCODE) {
            if (!(data instanceof ImageValue)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "encoding takes an image, not a "
                                + data.datatype().literalSpelling());
            }
        } else if (!(data instanceof BinaryValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "decoding takes a binary, not a "
                            + data.datatype().literalSpelling());
        }
        Codecs.Answer answered = Codecs.run(
                ((WordValue) handle.payload()).canonical(), asked, data);
        if (answered.error() != 0 && answered.kind() != Codecs.Answer.Kind.CHECK) {
            throw Raised.of(EvaluationFailure.BAD_MEDIA,
                    action.spelling() + " is not something this codec does");
        }
        return answered.value();
    }

    private static Value gobAndPoint(GobValue reached, PairValue point) {
        return BlockValue.block(List.of(reached, point));
    }

    private static void writePixel(ImageValue image, int pixel, int[] channels) {
        image.storage().setColourAt(pixel, channels[0], channels[1], channels[2]);
        image.storage().setAlphaAt(pixel, channels[3]);
    }

    private static Value insertPixels(ImageValue image, Value value) {
        List<int[]> pixels = new ArrayList<>();
        if (value instanceof ImageValue added) {
            for (int at = 1; at <= added.lengthFromHere(); at++) {
                pixels.add(added.pixelAt(at));
            }
        } else if (value instanceof TupleValue colour) {
            int[] parts = colour.segments();
            pixels.add(new int[] {
                    parts.length > 0 ? parts[0] : 0,
                    parts.length > 1 ? parts[1] : 0,
                    parts.length > 2 ? parts[2] : 0,
                    parts.length > 3 ? parts[3] : 0xFF});
        } else {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "an image takes a pixel or another image, not "
                            + value.datatype().literalSpelling());
        }
        for (int at = 0; at < pixels.size(); at++) {
            int[] channels = pixels.get(at);
            image.storage().insertAt(image.index() + at,
                    channels[0], channels[1], channels[2], channels[3]);
        }
        return image.atIndex(image.index() + pixels.size());
    }

    private static ImageValue copiedPixelsAsWholeRows(ImageValue image, int howMany) {
        int taking = Math.max(0, Math.min(howMany, image.lengthFromHere()));
        int wideEnoughForARow = Math.max(1, image.storage().wide());
        int wide = taking <= wideEnoughForARow ? taking : wideEnoughForARow;
        int high = wide == 0 ? 0
                : taking <= wideEnoughForARow ? 1 : taking / wideEnoughForARow;
        ImageStorage into = ImageStorage.of(wide, high);
        for (int at = 1; at <= wide * high; at++) {
            int[] channels = image.pixelAt(at);
            into.setColourAt(at, channels[0], channels[1], channels[2]);
            into.setAlphaAt(at, channels[3]);
        }
        return new ImageValue(into, 1);
    }

    private static Set<Datatype> setOperandOr(Datatype... alsoAccepted) {
        Set<Datatype> accepted = EnumSet.of(
                Datatype.BITSET, Datatype.TYPESET, Datatype.STRING, Datatype.MAP);
        accepted.addAll(List.of(alsoAccepted));
        return Set.copyOf(accepted);
    }

    private static double roundedBy(double value, Set<String> refinements) {
        if (refinements.contains("down")) {
            return value < 0 ? Math.ceil(value) : Math.floor(value);
        }
        if (refinements.contains("floor")) {
            return Math.floor(value);
        }
        if (refinements.contains("ceiling")) {
            return Math.ceil(value);
        }
        if (refinements.contains("even")) {
            return Math.rint(value);
        }
        double fraction = Math.abs(value - (long) value);
        if (refinements.contains("half-down") && fraction == 0.5) {
            return value < 0 ? Math.ceil(value) : Math.floor(value);
        }
        if (refinements.contains("half-ceiling") && fraction == 0.5) {
            return Math.ceil(value);
        }
        return roundedHalfAway(value);
    }

    private static int clampToSeries(SeriesValue series, long wanted) {
        return (int) Math.max(1, Math.min(wanted, series.storageLength() + 1L));
    }

    private static Value raiseCannotUse(Value value, String nativeName) {
        throw Raised.cannotUse(value, nativeName);
    }

    private void defineEncodings() {
        define("enhex", List.of(
                        Parameter.required("value", anyStringOr(Datatype.BINARY)),
                        Parameter.belongingTo("escape", "char", Set.of(Datatype.CHAR)),
                        Parameter.belongingTo("except", "unescaped", Set.of(Datatype.BITSET))),
                Set.of("escape", "except", "uri"),
                (arguments, evaluator, context, refinements) -> {
                    Value value = arguments.getFirst();
                    char escape = escapeCharacterIn(arguments, refinements);
                    java.util.function.IntPredicate keep = unescapedSetFor(
                            value, arguments, refinements);
                    byte[] encoded = Encodings.percentEncoded(
                            octetsOf(value), keep, escape,
                            refinements.contains("uri"));
                    return value instanceof BinaryValue
                            ? binaryOfBytes(encoded)
                            : StringValue.of(
                                    new String(encoded, StandardCharsets.UTF_8),
                                    textDatatypeOf(value));
                });

        define("dehex", List.of(
                        Parameter.required("value", anyStringOr(Datatype.BINARY)),
                        Parameter.belongingTo("escape", "char", Set.of(Datatype.CHAR))),
                Set.of("escape", "uri"),
                (arguments, evaluator, context, refinements) -> {
                    Value value = arguments.getFirst();
                    byte[] decoded = Encodings.percentDecoded(
                            textOf(value), escapeCharacterIn(arguments, refinements),
                            refinements.contains("uri"));
                    return value instanceof BinaryValue
                            ? binaryOfBytes(decoded)
                            : StringValue.of(
                                    new String(decoded, StandardCharsets.UTF_8),
                                    textDatatypeOf(value));
                });

        define("enbase", List.of(
                        Parameter.required("value",
                                anyStringOr(Datatype.BINARY, Datatype.INTEGER)),
                        Parameter.required("base", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("part", "limit",
                                anyStringOr(Datatype.BINARY, Datatype.INTEGER))),
                Set.of("url", "part", "flat"),
                (arguments, evaluator, context, refinements) -> {
                    int base = (int) ((IntegerValue) arguments.get(1)).magnitude();
                    requireAKnownBase(base);
                    byte[] octets = arguments.getFirst() instanceof IntegerValue number
                            ? boundedByAnyPart(asFewBytesAsHoldIt(number.magnitude()),
                                    arguments, refinements)
                            : theUnitsAskedFor(
                                    arguments.getFirst(), arguments, refinements);
                    String encoded;
                    try {
                        encoded = Encodings.enbase(
                                octets, base, refinements.contains("url"));
                    } catch (ArithmeticException tooWideForANumber) {
                        throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                arguments.getFirst());
                    }
                    return StringValue.of(refinements.contains("flat")
                            ? encoded
                            : Encodings.brokenIntoLines(encoded, base, octets.length));
                });

        define("debase", List.of(
                        Parameter.required("value", anyStringOr(Datatype.BINARY)),
                        Parameter.required("base", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("part", "limit",
                                anyStringOr(Datatype.BINARY, Datatype.INTEGER))),
                Set.of("url", "part"),
                (arguments, evaluator, context, refinements) -> {
                    int base = (int) ((IntegerValue) arguments.get(1)).magnitude();
                    requireAKnownBase(base);
                    try {
                        return binaryOfBytes(Encodings.debase(
                                boundedTextByAnyPart(
                                        textOf(arguments.getFirst()),
                                        arguments, refinements),
                                base, refinements.contains("url")));
                    } catch (IllegalArgumentException malformed) {
                        throw Raised.of(EvaluationFailure.INVALID_DATA,
                                malformed.getMessage());
                    }
                });

        define("checksum", List.of(
                        Parameter.required("data", CHECKSUMMABLE),
                        Parameter.required("method", Set.of(Datatype.WORD)),
                        Parameter.belongingTo("with", "spec",
                                anyStringOr(Datatype.BINARY, Datatype.INTEGER)),
                        Parameter.belongingTo("part", "length", PART_LIMIT)),
                Set.of("with", "part"),
                (arguments, evaluator, context, refinements) -> {
                    String method = ((WordValue) arguments.get(1)).canonical();
                    if (arguments.getFirst().datatype() == Datatype.FILE) {
                        return theContentsOfThatFileSummed(
                                arguments.getFirst(), method, evaluator, refinements);
                    }
                    byte[] octets = partOfOctets(arguments.getFirst(),
                            octetsOf(arguments.getFirst()), arguments, refinements, 2);
                    Value spec = refinements.contains("with")
                            ? argumentFor("with", List.of("with", "part"),
                                    arguments, refinements, 2)
                            : null;
                    if (Encodings.DIGESTS.containsKey(method)) {
                        if (spec instanceof IntegerValue) {
                            throw Raised.of(EvaluationFailure.BAD_REFINE, spec);
                        }
                        return binaryOfBytes(spec == null
                                ? Encodings.digestOf(octets, method)
                                : Encodings.keyedDigestOf(octets, method, octetsOf(spec)));
                    }
                    if (Encodings.CYCLIC.contains(method)) {
                        if (spec != null) {
                            throw Raised.of(EvaluationFailure.BAD_REFINES);
                        }
                        return IntegerValue.of(Encodings.cyclicOf(octets, method));
                    }
                    if (HASH_INTO_A_TABLE.equals(method)) {
                        return IntegerValue.of(hashedIntoATable(
                                arguments.getFirst(), spec));
                    }
                    throw Raised.of(EvaluationFailure.INVALID_ARG, method);
                });

        define("compress", List.of(
                        Parameter.required("data", COMPRESSIBLE),
                        Parameter.required("method", Set.of(Datatype.WORD)),
                        Parameter.belongingTo("part", "length", PART_LIMIT),
                        Parameter.belongingTo("level", "lvl", Set.of(Datatype.INTEGER))),
                Set.of("part", "level"),
                (arguments, evaluator, context, refinements) -> {
                    String method = requireAKnownCompression(arguments.get(1));
                    Value level = refinements.contains("level")
                            ? argumentFor("level", List.of("part", "level"),
                                    arguments, refinements, 2)
                            : null;
                    return binaryOfBytes(Encodings.compressed(
                            partOfOctets(arguments.getFirst(),
                                    octetsOf(arguments.getFirst()),
                                    arguments, refinements, 2),
                            method,
                            level instanceof IntegerValue asked
                                    ? (int) asked.magnitude()
                                    : java.util.zip.Deflater.DEFAULT_COMPRESSION));
                });

        define("decompress", List.of(
                        Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.required("method", Set.of(Datatype.WORD)),
                        Parameter.belongingTo("part", "length", COUNT_OR_POSITION),
                        Parameter.belongingTo("size", "bytes", Set.of(Datatype.INTEGER))),
                Set.of("part", "size"),
                (arguments, evaluator, context, refinements) -> {
                    String method = requireAKnownCompression(arguments.get(1));
                    try {
                        Value wanted = refinements.contains("size")
                                ? argumentFor("size", List.of("part", "size"),
                                        arguments, refinements, 2)
                                : null;
                        return binaryOfBytes(Encodings.decompressed(
                                partOfOctets(arguments.getFirst(),
                                        octetsOf(arguments.getFirst()),
                                        arguments, refinements, 2),
                                method,
                                wanted instanceof IntegerValue asked
                                        ? (int) asked.magnitude()
                                        : 0));
                    } catch (IllegalArgumentException notCompressed) {
                        throw Raised.of(EvaluationFailure.BAD_PRESS,
                                notCompressed.getMessage());
                    }
                });

        defineCloak("encloak", false);
        defineCloak("decloak", true);

        define("iconv", List.of(
                        Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.required("codepage", characterSetNames()),
                        Parameter.belongingTo("to", "target", characterSetNames())),
                Set.of("to"),
                (arguments, evaluator, context, refinements) -> {
                    byte[] octets = ((BinaryValue) arguments.getFirst()).octetsFromHere();
                    java.nio.charset.Charset from = characterSetFor(arguments.get(1));
                    String text = Encodings.textDecodedAs(octets, from);
                    if (!refinements.contains("to")) {
                        return StringValue.of(text);
                    }
                    Value target = argumentFor("to", List.of("to"),
                            arguments, refinements, 2);
                    java.nio.charset.Charset into = characterSetFor(target);
                    return java.nio.charset.StandardCharsets.UTF_8.equals(into)
                            ? StringValue.of(text)
                            : binaryOfBytes(text.getBytes(into));
                });

        define("filter", List.of(
                        Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.required("width", Typeset.NUMBER.members()),
                        Parameter.required("type",
                                Set.of(Datatype.INTEGER, Datatype.WORD)),
                        Parameter.belongingTo("skip", "bpp", Set.of(Datatype.INTEGER))),
                Set.of("skip"),
                (arguments, evaluator, context, refinements) -> {
                    byte[] data = ((BinaryValue) arguments.getFirst()).octetsFromHere();
                    int width = (int) Comparison.asDouble(arguments.get(1));
                    int bpp = bytesPerPixelIn(arguments, refinements, 3);
                    requirePngGeometry(width, bpp, data.length);
                    return binaryOfBytes(Encodings.pngFiltered(
                            data, width, pngFilterNamedBy(arguments.get(2)), bpp));
                });

        define("unfilter", List.of(
                        Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.required("width", Typeset.NUMBER.members()),
                        Parameter.belongingTo("as", "type",
                                Set.of(Datatype.INTEGER, Datatype.WORD)),
                        Parameter.belongingTo("skip", "bpp", Set.of(Datatype.INTEGER))),
                Set.of("as", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    byte[] data = ((BinaryValue) arguments.getFirst()).octetsFromHere();
                    int width = (int) Comparison.asDouble(arguments.get(1));
                    boolean filterGiven = refinements.contains("as");
                    int bpp = bytesPerPixelIn(arguments, refinements, 2);
                    requirePngGeometry(filterGiven ? width : width + 1, bpp, data.length);
                    int filter = -1;
                    if (filterGiven) {
                        filter = pngFilterNamedBy(argumentFor("as",
                                List.of("as", "skip"), arguments, refinements, 2));
                    }
                    return binaryOfBytes(Encodings.pngUnfiltered(
                            data, width, filter, bpp));
                });

        define("swap-endian", List.of(
                        Parameter.required("value", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("width", "bytes", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("part", "range", COUNT_OR_POSITION)),
                Set.of("width", "part"),
                (arguments, evaluator, context, refinements) -> {
                    BinaryValue bytes = (BinaryValue) arguments.getFirst();
                    Value asked = refinements.contains("width")
                            ? argumentFor("width", List.of("width", "part"),
                                    arguments, refinements, 1)
                            : null;
                    int width = asked instanceof IntegerValue given
                            ? (int) given.magnitude()
                            : 2;
                    byte[] octets = bytes.octetsFromHere();
                    int reach = refinements.contains("part")
                            ? (int) Math.max(0, Math.min(octets.length, countUpTo(bytes,
                                    argumentFor("part", List.of("width", "part"),
                                            arguments, refinements, 1))))
                            : octets.length;
                    try {
                        Encodings.swapEndian(octets, reach - reach % width, width);
                    } catch (IllegalArgumentException badWidth) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "swap-endian takes a width of 2, 4 or 8");
                    }
                    for (int at = 0; at < octets.length; at++) {
                        bytes.storage().set(bytes.index() + at, octets[at] & 0xFF);
                    }
                    return bytes;
                });
    }

    private void defineCloak(String name, boolean decode) {
        define(name, List.of(
                        Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.required("key", Set.of(Datatype.STRING,
                                Datatype.BINARY, Datatype.INTEGER))),
                Set.of("with"),
                (arguments, evaluator, context, refinements) -> {
                    BinaryValue data = (BinaryValue) arguments.getFirst();
                    refuseIfProtected(data);
                    byte[] octets = data.octetsFromHere();
                    if (!Encodings.cloak(decode, octets, keyBytesFor(
                            arguments.get(1), refinements.contains("with")))) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                name + " needs a key with bytes in it");
                    }
                    for (int at = 0; at < octets.length; at++) {
                        data.storage().set(data.index() + at, octets[at] & 0xFF);
                    }
                    return data;
                });
    }

    private static byte[] keyBytesFor(Value key, boolean asItStands) {
        if (key instanceof IntegerValue whole) {
            return Encodings.hashedKey(Long.toString(whole.magnitude())
                    .getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = key instanceof BinaryValue octets
                ? octets.octetsFromHere()
                : ((StringValue) key).text().getBytes(StandardCharsets.UTF_8);
        return asItStands ? bytes : Encodings.hashedKey(bytes);
    }

    private static Set<Datatype> characterSetNames() {
        return Set.of(Datatype.WORD, Datatype.INTEGER, Datatype.TAG, Datatype.STRING);
    }

    private static java.nio.charset.Charset characterSetFor(Value asked) {
        String spelling = switch (asked) {
            case WordValue word -> word.canonical();
            case StringValue text -> text.text();
            default -> Molder.form(asked);
        };
        java.nio.charset.Charset found = Encodings.charsetNamed(spelling);
        if (found == null) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, spelling);
        }
        return found;
    }

    private static void setFieldsFromObjectMatchedByName(
            ObjectValue into, ObjectValue from, Set<String> refinements) {

        boolean anyValue = refinements.contains("any");
        boolean onlySome = refinements.contains("some");
        for (ContextSlot slot : into.context().slots()) {
            if (slot.canonical().equals("self")
                    || !from.context().holds(slot.canonical())) {
                continue;
            }
            Value supplied = from.context().ownSlotFor(slot.canonical()).value();
            if (!anyValue && supplied.datatype() == Datatype.UNSET) {
                continue;
            }
            boolean targetHoldsSomething = slot.value().datatype() != Datatype.NONE
                    && slot.value().datatype() != Datatype.UNSET;
            boolean sourceHoldsNothing = supplied.datatype() == Datatype.NONE
                    || supplied.datatype() == Datatype.UNSET;
            if (onlySome && targetHoldsSomething && sourceHoldsNothing) {
                continue;
            }
            slot.setValue(supplied);
        }
        for (ContextSlot slot : into.context().slots()) {
            if (slot.canonical().equals("self")
                    || !from.context().holds(slot.canonical())) {
                continue;
            }
            slot.setValue(clonedAndRebound(slot.value(),
                    Set.of(from.context()), into.context()));
        }
    }

    private static int pngFilterNamedBy(Value asked) {
        if (asked instanceof IntegerValue whole) {
            int which = (int) whole.magnitude();
            if (which < 0 || which >= Encodings.PNG_FILTERS.size()) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, "filter type");
            }
            return which;
        }
        int found = Encodings.PNG_FILTERS.indexOf(((WordValue) asked).canonical());
        if (found < 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    ((WordValue) asked).spelling());
        }
        return found;
    }

    private static int bytesPerPixelIn(
            List<Value> arguments, Set<String> refinements, int where) {

        Value asked = refinements.contains("skip")
                ? argumentFor("skip", List.of("as", "skip"),
                        arguments, refinements, where)
                : null;
        return asked instanceof IntegerValue given ? (int) given.magnitude() : 1;
    }

    private static void requirePngGeometry(int width, int bytesPerPixel, int length) {
        if (width <= 1 || width > length) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, "width " + width);
        }
        if (bytesPerPixel < 1 || bytesPerPixel > width) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "bytes per pixel " + bytesPerPixel);
        }
    }

    private static void requireAKnownBase(int base) {
        if (!Encodings.BASES.contains(base)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "base " + base + " is not 2, 16, 36, 64 or 85");
        }
    }

    private static String requireAKnownCompression(Value method) {
        String asked = ((WordValue) method).canonical();
        if (Encodings.COMPRESSIONS_ELSEWHERE.contains(asked)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA, asked);
        }
        if (!Encodings.COMPRESSIONS.contains(asked)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
        return asked;
    }

    private static char escapeCharacterIn(
            List<Value> arguments, Set<String> refinements) {
        Value asked = refinements.contains("escape")
                ? argumentFor("escape", List.of("escape", "except"),
                        arguments, refinements, 1)
                : null;
        return asked instanceof CharacterValue given
                ? (char) given.codepoint()
                : '%';
    }

    private static java.util.function.IntPredicate unescapedSetFor(
            Value value, List<Value> arguments, Set<String> refinements) {

        if (refinements.contains("except")) {
            Value asked = argumentFor("except", List.of("escape", "except"),
                    arguments, refinements, 1);
            if (asked instanceof BitsetValue members) {
                return octet -> Encodings.setHolds(members, octet);
            }
        }
        return value.datatype() == Datatype.FILE || value.datatype() == Datatype.URL
                ? Encodings::uriKeeps
                : Encodings::uriComponentKeeps;
    }

    private static byte[] octetsOf(Value value) {
        return switch (value) {
            case BinaryValue bytes -> bytes.octetsFromHere();
            case IntegerValue whole -> java.nio.ByteBuffer.allocate(8)
                    .putLong(whole.magnitude()).array();
            case StringValue written -> written.text().getBytes(StandardCharsets.UTF_8);
            default -> Molder.form(value).getBytes(StandardCharsets.UTF_8);
        };
    }

    private static String environmentNameIn(Value asked) {
        return asked instanceof WordValue word
                ? word.spelling()
                : ((StringValue) asked).text();
    }

    private static String textOf(Value value) {
        return switch (value) {
            case BinaryValue bytes ->
                    new String(bytes.octetsFromHere(), StandardCharsets.UTF_8);
            case StringValue written -> written.text();
            default -> Molder.form(value);
        };
    }

    private static Datatype textDatatypeOf(Value value) {
        return value.datatype().isAnyString() ? value.datatype() : Datatype.STRING;
    }

    private static byte[] partOfOctets(
            Value source, byte[] octets, List<Value> arguments,
            Set<String> refinements, int where) {

        return howManyWanted(source, arguments, refinements, where)
                .map(count -> count < 0
                        ? theOctetsBehind(source, octets, -count)
                        : Arrays.copyOf(octets,
                                (int) Math.min(count, octets.length)))
                .orElse(octets);
    }

    private static byte[] theOctetsBehind(Value source, byte[] octets, long count) {
        if (!(source instanceof SeriesValue positioned)) {
            return new byte[0];
        }
        int landsOn = (int) Math.max(1, positioned.index() - count);
        byte[] fromThere = octetsOf(positioned.atIndex(landsOn));
        return Arrays.copyOf(fromThere, fromThere.length - octets.length);
    }

    private static final String HASH_INTO_A_TABLE = "hash";

    private static long hashedIntoATable(Value value, Value size) {
        if (size == null) {
            throw Raised.of(EvaluationFailure.MISSING_ARG);
        }
        if (!(size instanceof IntegerValue asked)) {
            throw Raised.of(EvaluationFailure.BAD_REFINE, size);
        }
        long slots = Math.max(1, asked.magnitude()) & 0xFFFFFFFFL;
        long hash = Integer.toUnsignedLong(hashOfValue(value));
        return slots == 0 ? hash : hash % slots;
    }

    private static int hashOfValue(Value value) {
        return value instanceof BinaryValue bytes
                ? Encodings.murmurOf(bytes.octetsFromHere())
                : Encodings.caseFoldedHashOf(octetsOf(value))
                        ^ value.datatype().ordinal();
    }

    private static Value whereItStandsInThePicture(ImageValue picture, int countingFrom) {
        int across = picture.storage().wide();
        if (across <= 0) {
            return IntegerValue.of(picture.index() - 1 + countingFrom);
        }
        int stepsIn = picture.index() - 1;
        return PairValue.of(stepsIn % across + countingFrom, stepsIn / across + countingFrom);
    }

    private static Set<Datatype> anyStringOr(Datatype... alsoAccepted) {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.ANY_STRING.members());
        accepted.addAll(List.of(alsoAccepted));
        return Set.copyOf(accepted);
    }

    private void defineInterpreterState() {
        define("version", List.of(), Set.of("data"),
                (arguments, evaluator, context, refinements) ->
                        refinements.contains("data")
                                ? TupleValue.of(VERSION_PARTS)
                                : StringValue.of(VERSION_TEXT));

        define("pokez", List.of(
                        Parameter.required("series", pokeableDatatypes()),
                        Parameter.required("index", Set.of(Datatype.INTEGER)),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    long index = ((IntegerValue) arguments.get(1)).magnitude();
                    boolean shifts = index >= 0
                            && !(arguments.getFirst() instanceof BitsetValue);
                    return evaluator.applyFunction(
                            libraryFunction(context, "poke"),
                            List.of(arguments.getFirst(),
                                    IntegerValue.of(shifts ? index + 1 : index),
                                    arguments.get(2)));
                });

        define("to-real-file", List.of(Parameter.required("path",
                        Set.of(Datatype.FILE, Datatype.STRING))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> {
                        String resolved = evaluator.files().canonicalPathOf(
                                ((StringValue) arguments.getFirst()).text());
                        return resolved == null
                                ? NoneValue.none()
                                : StringValue.of(resolved, Datatype.FILE);
                    });
                });

        define("recycle", List.of(
                        Parameter.belongingTo("ballast", "size", Set.of(Datatype.INTEGER))),
                Set.of("off", "on", "ballast", "torture", "pools"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("off")) {
                        return UnsetValue.unset();
                    }
                    return IntegerValue.of(SeriesMemory.collectNow());
                });

        define("stats", List.of(
                        Parameter.belongingTo("dump-series", "pool-id",
                                Set.of(Datatype.INTEGER))),
                Set.of("show", "profile", "timer", "evals", "dump-series"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("dump-series")) {
                        return NoneValue.none();
                    }
                    if (refinements.contains("timer")) {
                        return TimeValue.ofNanoseconds(System.nanoTime() - startedAt);
                    }
                    if (refinements.contains("evals")) {
                        return IntegerValue.of(evaluator.valuesWalked());
                    }
                    if (refinements.contains("profile")) {
                        return filledInProfile(evaluator);
                    }
                    return IntegerValue.of(SeriesMemory.bytesHeld());
                });

        define("echo", List.of(Parameter.required("target",
                        Set.of(Datatype.FILE, Datatype.NONE, Datatype.LOGIC))),
                (arguments, evaluator, context) -> {
                    evaluator.stopEchoing();
                    Value target = arguments.getFirst();
                    if (target instanceof NoneValue || !target.isTruthy()) {
                        return UnsetValue.unset();
                    }
                    requireService(HostService.FILES);
                    String path = target instanceof StringValue address
                            ? address.text()
                            : "output.txt";
                    FilePort files = evaluator.files();
                    return throughPort(() -> {
                        files.write(path, new byte[0]);
                        evaluator.alsoWriteTo(text -> files.appendTo(
                                path, text.getBytes(StandardCharsets.UTF_8)));
                        return UnsetValue.unset();
                    });
                });

        define("tty?", List.of(),
                (arguments, evaluator, context) ->
                        LogicValue.of(evaluator.console().isATerminal()));

        define("wait", List.of(Parameter.required("value",
                        waitableDatatypes())),
                Set.of("all", "only"),
                (arguments, evaluator, context, refinements) -> {
                    Value asked = arguments.getFirst();
                    if (asked instanceof PortValue port
                            && port.schemeName().equals("event")) {
                        return waitedOnTheScreen(port, evaluator);
                    }
                    List<Value> waitedOn = asked instanceof BlockValue block
                            ? evaluator.evaluateEachOrRaise(block, context)
                            : List.of(asked);
                    if (whicheverPortWoke(waitedOn, evaluator)
                            instanceof PortValue woken) {
                        return woken;
                    }
                    asked = howLongToWaitAmong(waitedOn);
                    if (!(asked instanceof IntegerValue || asked instanceof DecimalValue
                            || asked instanceof TimeValue)) {
                        return NoneValue.none();
                    }
                    long milliseconds = asked instanceof TimeValue clock
                            ? clock.nanoseconds() / 1_000_000L
                            : (long) (1000 * Comparison.asDouble(asked));
                    sleepInterruptibly(Math.max(0, milliseconds), evaluator);
                    return NoneValue.none();
                });

        define("read-key", List.of(),
                (arguments, evaluator, context) -> {
                    requireService(HostService.CONSOLE);
                    int code = evaluator.console().readKey();
                    runState.set("control?", LogicValue.of(false));
                    runState.set("shift?", LogicValue.of(false));
                    runState.set("alt?", LogicValue.of(false));
                    return code < 0
                            ? NoneValue.none()
                            : CharacterValue.of(code);
                });

        define("halt", List.of(),
                (arguments, evaluator, context) -> {
                    throw new HaltRequested();
                });

        define("do-codec", List.of(
                        Parameter.required("handle", Set.of(Datatype.HANDLE)),
                        Parameter.required("action", Set.of(Datatype.WORD)),
                        Parameter.required("data", Set.of(Datatype.BINARY,
                                Datatype.IMAGE, Datatype.STRING))),
                (arguments, evaluator, context) -> ranCodec(
                        (HandleValue) arguments.get(0),
                        (WordValue) arguments.get(1),
                        arguments.get(2)));

        define("release", List.of(Parameter.required("handle", Set.of(Datatype.HANDLE))),
                (arguments, evaluator, context) -> {
                    HandleValue handle = (HandleValue) arguments.get(0);
                    if (handle.payload() instanceof JavaObjectValue carried
                            && carried.held().orElse(null)
                                    instanceof AKeyThatCanBeReleased key) {
                        key.release();
                    }
                    return LogicValue.of(handle.isContext());
                });

        define("map-event", List.of(Parameter.required("event", Set.of(Datatype.EVENT))),
                (arguments, evaluator, context) -> mappedEvent(
                        (EventValue) arguments.get(0)));

        define("wake-up", List.of(
                        Parameter.required("port", Set.of(Datatype.PORT)),
                        Parameter.required("event", Set.of(Datatype.EVENT))),
                (arguments, evaluator, context) -> wokenPort(
                        (PortValue) arguments.get(0), arguments.get(1), evaluator));

        define("map-gob-offset", List.of(
                        Parameter.required("gob", Set.of(Datatype.GOB)),
                        Parameter.required("xy", Set.of(Datatype.PAIR))),
                Set.of("reverse"),
                (arguments, evaluator, context, refinements) -> {
                    GobValue from = (GobValue) arguments.get(0);
                    PairValue point = (PairValue) arguments.get(1);
                    return refinements.contains("reverse")
                            ? mappedOutwards(from, point)
                            : mappedInwards(from, point);
                });

        define("as-color", List.of(
                        Parameter.required("r", Typeset.NUMBER.members()),
                        Parameter.required("g", Typeset.NUMBER.members()),
                        Parameter.required("b", Typeset.NUMBER.members())),
                (arguments, evaluator, context) -> TupleValue.of(
                        colourByteOfRoundingNotTruncating(arguments.get(0)),
                        colourByteOfRoundingNotTruncating(arguments.get(1)),
                        colourByteOfRoundingNotTruncating(arguments.get(2))));

        define("grayscale", List.of(Parameter.required("target",
                        Set.of(Datatype.TUPLE, Datatype.IMAGE))),
                (arguments, evaluator, context) -> overEveryColour(arguments.getFirst(),
                        parts -> IntegerValue.of(
                                Colours.grey(parts[0], parts[1], parts[2])),
                        parts -> {
                            int grey = Colours.grey(parts[0], parts[1], parts[2]);
                            return new int[] {grey, grey, grey};
                        }));

        define("luminosity", List.of(Parameter.required("target",
                        Set.of(Datatype.TUPLE, Datatype.IMAGE))),
                Set.of("luma"),
                (arguments, evaluator, context, refinements) -> {
                    boolean luma = refinements.contains("luma");
                    return overEveryColour(arguments.getFirst(),
                            parts -> IntegerValue.of(
                                    Colours.luminosityTruncatedRatherThanRounded(
                                            parts[0], parts[1], parts[2], luma)),
                            parts -> {
                                int grey = Colours.luminosityTruncatedRatherThanRounded(
                                        parts[0], parts[1], parts[2], luma);
                                return new int[] {grey, grey, grey};
                            });
                });

        define("hsv-to-rgb", List.of(Parameter.required("hsv", Set.of(Datatype.TUPLE))),
                (arguments, evaluator, context) -> recolouredTuple(
                        (TupleValue) arguments.getFirst(),
                        parts -> Colours.hsvToRgb(parts[0], parts[1], parts[2])));
        define("rgb-to-hsv", List.of(Parameter.required("rgb", Set.of(Datatype.TUPLE))),
                (arguments, evaluator, context) -> recolouredTuple(
                        (TupleValue) arguments.getFirst(),
                        parts -> Colours.rgbToHsv(parts[0], parts[1], parts[2])));

        define("color-distance", List.of(
                        Parameter.required("a", Set.of(Datatype.TUPLE)),
                        Parameter.required("b", Set.of(Datatype.TUPLE))),
                (arguments, evaluator, context) -> DecimalValue.of(Colours.perceptionDistance(
                        threeParts((TupleValue) arguments.get(0)),
                        threeParts((TupleValue) arguments.get(1)))));

        define("tint", List.of(
                        Parameter.required("target", Set.of(Datatype.TUPLE, Datatype.IMAGE)),
                        Parameter.required("rgb", Set.of(Datatype.TUPLE)),
                        Parameter.required("amount", Typeset.NUMBER.members())),
                (arguments, evaluator, context) -> {
                    int[] mixture = threeParts((TupleValue) arguments.get(1));
                    double amount = Comparison.asDouble(arguments.get(2));
                    return overEveryColour(arguments.getFirst(),
                            parts -> TupleValue.of(Colours.tinted(parts, mixture, amount)),
                            parts -> Colours.tinted(parts, mixture, amount));
                });

        define("limit-usage", List.of(
                        Parameter.required("field", Set.of(Datatype.WORD)),
                        Parameter.required("limit", Typeset.NUMBER.members())),
                (arguments, evaluator, context) -> {
                    UsageLimit which = switch (
                            ((WordValue) arguments.getFirst()).canonical()) {
                        case "eval" -> UsageLimit.EVALUATIONS;
                        case "memory" -> UsageLimit.MEMORY_BYTES;
                        default -> null;
                    };
                    if (which != null) {
                        evaluator.recordLimitAskedFor(which,
                                (long) Comparison.asDouble(arguments.get(1)));
                    }
                    return UnsetValue.unset();
                });

        define("ds", List.of(),
                (arguments, evaluator, context) -> {
                    printTheFrameStack(evaluator);
                    return UnsetValue.unset();
                });

        define("dump", List.of(Parameter.required("value")),
                Set.of("fmt"),
                (arguments, evaluator, context, refinements) -> arguments.getFirst());

        define("check", List.of(Parameter.required("series", Typeset.SERIES.members())),
                (arguments, evaluator, context) -> {
                    refuseASeriesCarryingAZero(arguments.getFirst());
                    return arguments.getFirst();
                });

        define("evoke", List.of(Parameter.required("chant",
                        Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    List<Value> chants = arguments.getFirst() instanceof BlockValue several
                            ? several.remaining()
                            : List.of(arguments.getFirst());
                    for (int at = 0; at < chants.size(); at++) {
                        at += obeyAnsweringHowManyValuesItTook(
                                chants.get(at), evaluator);
                    }
                    return UnsetValue.unset();
                });

        define("stack", List.of(Parameter.required("offset", Set.of(Datatype.INTEGER))),
                Set.of("block", "word", "func", "args", "size", "depth", "limit"),
                (arguments, evaluator, context, refinements) -> {
                    int offset = (int) ((IntegerValue) arguments.getFirst()).magnitude();
                    int callsOpen = evaluator.callsInProgress().size();
                    if (offset < 0 || offset > callsOpen) {
                        return NoneValue.none();
                    }
                    if (refinements.contains("word")) {
                        if (offset == 0) {
                            return WordValue.of("stack");
                        }
                        return evaluator.functionBeingRun(offset - 1)
                                .filter(name -> !name.isEmpty())
                                .<Value>map(WordValue::of)
                                .orElseGet(NoneValue::none);
                    }
                    if (refinements.contains("depth")) {
                        return IntegerValue.of(callsOpen + 1);
                    }
                    if (refinements.contains("limit")) {
                        return IntegerValue.of(Evaluator.DEFAULT_MAXIMUM_DEPTH);
                    }
                    if (refinements.contains("size")) {
                        return IntegerValue.of((callsOpen + 1) * FRAME_VALUE_UNITS);
                    }
                    List<Value> backtrace = new ArrayList<>();
                    if (offset == 0) {
                        backtrace.add(WordValue.of("stack"));
                    }
                    for (int at = Math.max(0, offset - 1); at < callsOpen; at++) {
                        evaluator.functionBeingRun(at)
                                .filter(name -> !name.isEmpty())
                                .ifPresent(name -> backtrace.add(WordValue.of(name)));
                    }
                    return BlockValue.block(backtrace);
                });
    }

    private static final String FRAME_LINE = "%nSTACK[%d] %s[%d] %s";

    private static final String SLOT_LINE = "\t%s: %s";

    private static final int SLOT_MOLD_LIMIT = 72;

    private static final String NO_NAME = "?";

    private void printTheFrameStack(Evaluator evaluator) {
        List<Evaluator.OpenCall> open = evaluator.callsInProgress();
        int slotsInUse = (open.size() + 1) * FRAME_VALUE_UNITS;
        evaluator.output().writeLine(String.format(FRAME_LINE,
                slotsInUse, "ds", 0, Datatype.NATIVE.literalSpelling()));
        slotsInUse -= FRAME_VALUE_UNITS;
        for (Evaluator.OpenCall call : open) {
            List<String> slots = call.slotNames();
            evaluator.output().writeLine(String.format(FRAME_LINE,
                    slotsInUse,
                    call.name().isEmpty() ? NO_NAME : call.name(),
                    slots.size(),
                    call.function().datatype().literalSpelling()));
            for (String slot : slots) {
                evaluator.output().writeLine(String.format(SLOT_LINE, slot,
                        moldedWithin(call.locals().slotFor(
                                Context.canonicalise(slot)).value(), SLOT_MOLD_LIMIT)));
            }
            slotsInUse -= FRAME_VALUE_UNITS;
        }
    }

    private static final Set<Datatype> WHAT_REPEAT_COUNTS_BY = whatRepeatCountsBy();

    private static Set<Datatype> whatRepeatCountsBy() {
        Set<Datatype> accepted = new java.util.HashSet<>(Typeset.NUMBER.members());
        accepted.addAll(Typeset.SERIES.members());
        accepted.add(Datatype.PAIR);
        accepted.add(Datatype.NONE);
        return Set.copyOf(accepted);
    }

    private static final Set<String> SCHEMES_THIS_BUILD_SERVES =
            Set.of("console", "tcp", "dns", "event", "checksum", "file", "dir",
                    "crypt");

    private static void startTheCipherBehindBlankingTheKeyInTheSpec(PortValue port) {
        if (CryptPort.isWorking(port)) {
            throw Raised.of(EvaluationFailure.ALREADY_OPEN,
                    port.fieldNamed("spec") instanceof ObjectValue spec
                            ? valueInSpec(spec, "ref")
                            : NoneValue.none());
        }
        if (!(port.fieldNamed("spec") instanceof ObjectValue spec)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, port);
        }
        String algorithm = valueInSpec(spec, "algorithm") instanceof WordValue word
                ? word.canonical()
                : "";
        if (!CryptPort.serves(algorithm)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, spec);
        }
        CryptPort.start(port, algorithm,
                valueInSpec(spec, "direction") instanceof WordValue wanted
                        && wanted.canonical().equals("decrypt"),
                octetsInSpec(spec, "key"), octetsInSpec(spec, "init-vector"));
        spec.context().set("key", NoneValue.none());
        spec.context().set("init-vector", NoneValue.none());
    }

    private static Value valueInSpec(ObjectValue spec, String field) {
        return spec.context().holds(field)
                ? spec.context().ownSlotFor(field).value()
                : NoneValue.none();
    }

    private static byte[] octetsInSpec(ObjectValue spec, String field) {
        return switch (valueInSpec(spec, field)) {
            case BinaryValue octets -> octets.octetsFromHere();
            case StringValue text -> text.text()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            default -> new byte[0];
        };
    }

    private static void refuseAClosedCipherPort(PortValue port) {
        if (CryptPort.isWorking(port)) {
            return;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN,
                port.fieldNamed("spec") instanceof ObjectValue spec
                        ? valueInSpec(spec, "ref")
                        : NoneValue.none());
    }


    private static final String RC4_HANDLE_TYPE = "rc4";

    private static final String RSA_HANDLE_TYPE = "rsa";

    private static final String DHM_HANDLE_TYPE = "dhm";

    private static final String ECDH_HANDLE_TYPE = "ecdh";

    private static final List<String> ECDH_ACTIONS =
            List.of("init", "curve", "public", "secret");

    private static Value theBinaryDialect(List<Value> arguments,
            Set<String> refinements, Evaluator evaluator, Context context) {
        ObjectValue held = theDialectContextOf(arguments.getFirst());
        if (refinements.contains("init")) {
            restartedWith(held, argumentFor("init",
                    DIALECT_OPTIONAL_ARGUMENTS, arguments, refinements, 1));
        }
        if (refinements.contains("write")) {
            requireChangeable(arguments.getFirst());
            writeThroughTheDialect(held,
                    dialectBlockIn(arguments, refinements, "write"),
                    item -> valueLookedUpThroughItsOwnBinding(
                            item, evaluator, context));
            if (arguments.getFirst() instanceof BinaryValue given) {
                laidBackInto(given, cursorNamed(held, "buffer").head());
            }
        }
        if (refinements.contains("read")) {
            return readThroughTheDialect(held,
                    dialectCodeIn(arguments, refinements),
                    item -> valueLookedUpThroughItsOwnBinding(
                            item, evaluator, context),
                    theCountGivenWith(arguments, refinements),
                    argumentFor("into", DIALECT_OPTIONAL_ARGUMENTS,
                            arguments, refinements));
        }
        return held;
    }

    private static Value laidInto(Value target, List<Value> read) {
        if (!(target instanceof BlockValue into)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(target));
        }
        int at = into.index();
        for (Value value : read) {
            into.storage().insertAt(at, value);
            at++;
        }
        return into.atIndex(at);
    }

    private static final List<String> DIALECT_OPTIONAL_ARGUMENTS =
            List.of("init", "write", "read", "into", "with");

    private static ObjectValue theDialectContextOf(Value given) {
        if (given instanceof ObjectValue existing
                && existing.context().knows("buffer")
                && existing.context().slotFor("buffer").value() instanceof BinaryValue) {
            return existing;
        }
        return (ObjectValue) theDialectContextFor(
                bufferOfTheDialectContext(given));
    }

    private static void restartedWith(ObjectValue held, Value replacement) {
        BinaryValue fresh = replacement instanceof BinaryValue given
                ? BinaryValue.of(bytesAsOctetValues(given.octetsFromHere()))
                : BinaryValue.of();
        held.context().set("buffer", fresh);
        held.context().set("buffer-write", fresh);
    }

    private static int[] bytesAsOctetValues(byte[] octets) {
        int[] widened = new int[octets.length];
        for (int at = 0; at < octets.length; at++) {
            widened[at] = octets[at] & 0xFF;
        }
        return widened;
    }

    private static void laidBackInto(BinaryValue given, BinaryValue written) {
        BinaryStorage storage = given.storage();
        byte[] octets = written.octetsFromHere();
        while (storage.length() > octets.length) {
            storage.removeAt(storage.length());
        }
        for (int at = 1; at <= octets.length; at++) {
            if (at <= storage.length()) {
                storage.set(at, octets[at - 1] & 0xFF);
            } else {
                storage.append(octets[at - 1] & 0xFF);
            }
        }
    }

    private static BinaryValue cursorNamed(ObjectValue held, String field) {
        return held.context().knows(field)
                && held.context().slotFor(field).value() instanceof BinaryValue at
                ? at
                : BinaryValue.of();
    }

    private static Value valueLookedUpThroughItsOwnBinding(
            Value item, Evaluator evaluator, Context context) {
        boolean fetches = item instanceof WordValue word
                        && word.datatype() == Datatype.GET_WORD
                || item instanceof BlockValue path
                        && path.datatype() == Datatype.GET_PATH;
        if (!fetches) {
            return item;
        }
        return evaluator.evaluateOrRaise(BlockValue.block(List.of(item)), context);
    }

    private static BinaryValue bufferOfTheDialectContext(Value given) {
        if (given instanceof BinaryValue bytes) {
            return bytes;
        }
        if (given instanceof ObjectValue object
                && object.context().knows("buffer")
                && object.context().slotFor("buffer").value()
                        instanceof BinaryValue held) {
            return held;
        }
        return BinaryValue.of();
    }

    private static Value theDialectContextFor(BinaryValue buffer) {
        Context made = Context.root();
        made.set("type", WordValue.of("bincode"));
        made.set("buffer", buffer);
        made.set("buffer-write", buffer);
        made.set("r-mask", IntegerValue.of(0));
        made.set("w-mask", IntegerValue.of(0));
        return new ObjectValue(made);
    }

    private static List<Value> dialectBlockIn(
            List<Value> arguments, Set<String> refinements, String which) {
        int at = refinements.contains("init") ? 2 : 1;
        if (which.equals("read") && refinements.contains("write")) {
            at++;
        }
        Value given = arguments.get(at);
        return given instanceof BlockValue block
                ? block.remaining()
                : List.of(given);
    }

    private static void writeThroughTheDialect(ObjectValue held,
            List<Value> dialect, UnaryOperator<Value> lookedUp) {

        BinaryValue writing = cursorNamed(held, "buffer-write");
        List<Integer> octets = octetsOfTheBuffer(writing.head());
        Bincode.Cursor cursor = new Bincode.Cursor(octets, writing.index() - 1);
        Bincode.write(cursor, new Bincode.Script(dialect, lookedUp),
                Natives::secondsSinceTheEpoch, Natives::nameTheValueRead);
        BinaryValue written = BinaryValue.of(
                cursor.octets().stream().mapToInt(Integer::intValue).toArray());
        held.context().set("buffer",
                written.atIndex(cursorNamed(held, "buffer").index()));
        held.context().set("buffer-write", written.atIndex(cursor.at() + 1));
    }

    private static Value theseManyBytesRead(ObjectValue held, BinaryValue reading,
            Bincode.Cursor cursor, IntegerValue howMany, Value into) {

        if (into != null && !(into instanceof NoneValue)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    "reading a count of bytes into a block");
        }
        List<Value> read = Bincode.read(cursor,
                new Bincode.Script(List.of(WordValue.of("bytes"), howMany),
                        UnaryOperator.identity()),
                Natives::nameTheValueRead);
        held.context().set("buffer", reading.atIndex(cursor.at() + 1));
        return read.getFirst();
    }

    private static Value readThroughTheDialect(ObjectValue held, Value asked,
            UnaryOperator<Value> lookedUp, Value count, Value into) {

        BinaryValue reading = cursorNamed(held, "buffer");
        Bincode.Cursor cursor = new Bincode.Cursor(
                octetsOfTheBuffer(reading.head()), reading.index() - 1,
                bitsAlreadyTakenIn(held));
        Value theBlockItself = lookedUp.apply(asked);
        if (theBlockItself instanceof IntegerValue howMany) {
            return theseManyBytesRead(held, reading, cursor, howMany, into);
        }
        List<Value> codes = new ArrayList<>(codesWrittenIn(theBlockItself));
        if (!(count instanceof NoneValue)) {
            codes.add(count);
        }
        List<Value> read = Bincode.read(cursor,
                new Bincode.Script(codes, lookedUp), Natives::nameTheValueRead);
        held.context().set("r-mask", IntegerValue.of(cursor.bitsTaken()));
        if (cursor.cropped() > 0) {
            shortenedFromTheFront(held, cursor);
        } else {
            held.context().set("buffer", reading.atIndex(cursor.at() + 1));
        }
        return into == null || into instanceof NoneValue
                ? shapedLikeTheAsking(theBlockItself, read)
                : laidInto(into, read);
    }

    private static void shortenedFromTheFront(
            ObjectValue held, Bincode.Cursor cursor) {
        int writingWas = cursorNamed(held, "buffer-write").index();
        BinaryValue shortened = BinaryValue.of(
                cursor.octets().stream().mapToInt(Integer::intValue).toArray());
        held.context().set("buffer", shortened.atIndex(cursor.at() + 1));
        held.context().set("buffer-write",
                shortened.atIndex(Math.max(1, writingWas - cursor.cropped())));
    }

    private static int bitsAlreadyTakenIn(ObjectValue held) {
        return held.context().knows("r-mask")
                && held.context().slotFor("r-mask").value() instanceof IntegerValue taken
                ? (int) taken.magnitude()
                : 0;
    }

    private static void nameTheValueRead(WordValue word, Value read) {
        if (!word.isBound() || !word.binding().knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        ContextSlot slot = word.binding().slotFor(word.canonical());
        if (slot.isProtected()) {
            throw Raised.of(EvaluationFailure.LOCKED_WORD, word.spelling());
        }
        slot.setValue(read);
    }

    private static long secondsSinceTheEpoch() {
        return Instant.now().getEpochSecond();
    }

    private static byte[] asFewBytesAsHoldIt(long number) {
        byte[] whole = new byte[Long.BYTES];
        for (int at = 0; at < Long.BYTES; at++) {
            whole[at] = (byte) (number >> (Long.BYTES - 1 - at) * 8);
        }
        if (number < 0) {
            return whole;
        }
        int from = 0;
        while (from < Long.BYTES - 1 && whole[from] == 0) {
            from++;
        }
        return Arrays.copyOfRange(whole, from, Long.BYTES);
    }

    private static List<Integer> octetsOfTheBuffer(BinaryValue buffer) {
        List<Integer> octets = new ArrayList<>();
        for (byte octet : buffer.octetsFromHere()) {
            octets.add(octet & 0xFF);
        }
        return octets;
    }

    private static List<Value> codesWrittenIn(Value asked) {
        return asked instanceof BlockValue block ? block.remaining() : List.of(asked);
    }

    private static Value shapedLikeTheAsking(Value asked, List<Value> values) {
        if (asked instanceof BlockValue) {
            return BlockValue.block(values);
        }
        return values.isEmpty() ? NoneValue.none() : values.getFirst();
    }

    private static Value theCountGivenWith(
            List<Value> arguments, Set<String> refinements) {
        return refinements.contains("with")
                ? argumentFor("with", DIALECT_OPTIONAL_ARGUMENTS,
                        arguments, refinements, 1)
                : NoneValue.none();
    }

    private static Value dialectCodeIn(
            List<Value> arguments, Set<String> refinements) {
        int at = refinements.contains("init") ? 2 : 1;
        if (refinements.contains("write")) {
            at++;
        }
        return arguments.get(at);
    }

    private Value structLayoutFiledUnder(List<Value> arguments) {
        if (!(arguments.getFirst() instanceof WordValue name)) {
            return raiseWrongArgument(arguments.getFirst(), "register", "name");
        }
        StructValue given = (StructValue) arguments.get(1);
        if (name.datatype() == Datatype.SET_WORD) {
            slotOf(name).setValue(given);
        }
        MapValue catalogue = registeredStructLayouts;
        WordValue filedAs = WordValue.of(name.spelling());
        Value alreadyThere = catalogue.select(filedAs);
        if (alreadyThere instanceof BlockValue held) {
            if (!held.equals(given.spec().declaration())) {
                throw Raised.of(EvaluationFailure.ALREADY_USED, name.spelling());
            }
            return given;
        }
        catalogue.put(filedAs, given.spec().declaration());
        return given;
    }

    private static MapValue structCatalogueOf(Evaluator evaluator) {
        return pathInto(evaluator.systemContext(), "system", "catalog", "structs")
                instanceof MapValue catalogue
                ? catalogue
                : MapValue.empty();
    }

    private static Value resizedImage(
            List<Value> arguments, Set<String> refinements) {

        ImageValue image = (ImageValue) arguments.getFirst();
        int wasWide = image.storage().wide();
        int wasHigh = image.storage().high();
        Value asked = arguments.get(1);
        refuseAFilterTheCatalogueHasNot(arguments, refinements);
        int wide;
        int high;
        if (asked instanceof PairValue size) {
            wide = (int) size.x();
            high = (int) size.y();
            if (wide == 0 && high == 0) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(asked));
            }
            if (wide == 0) {
                wide = scaledFrom(high, wasWide, wasHigh);
            }
            if (high == 0) {
                high = scaledFrom(wide, wasHigh, wasWide);
            }
        } else if (asked instanceof DecimalValue portion
                && portion.datatype() == Datatype.PERCENT) {
            wide = (int) Math.round(wasWide * portion.quantity());
            high = (int) Math.round(wasHigh * portion.quantity());
        } else {
            wide = (int) Math.round(Arithmetic.asMagnitude(asked));
            high = scaledFrom(wide, wasHigh, wasWide);
        }
        if (wide <= 0 || high <= 0) {
            throw Raised.of(EvaluationFailure.NO_CREATE,
                    DatatypeValue.of(Datatype.IMAGE));
        }
        return ImageOperations.resized(image, wide, high);
    }

    private static int scaledFrom(int given, int toKeep, int against) {
        return against == 0 ? 0 : (given * toKeep) / against;
    }

    private static void refuseAFilterTheCatalogueHasNot(
            List<Value> arguments, Set<String> refinements) {

        if (!refinements.contains("filter")) {
            return;
        }
        Value asked = argumentFor("filter", List.of("filter", "blur"),
                arguments, refinements, 2);
        boolean known = asked instanceof WordValue word
                && THE_FILTERS.stream().anyMatch(word.canonical()::equalsIgnoreCase);
        if (!known) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
    }

    static final List<String> THE_FILTERS = List.of(
            "Point", "Box", "Triangle", "Hermite", "Hanning", "Hamming",
            "Blackman", "Gaussian", "Quadratic", "Cubic", "Catrom",
            "Mitchell", "Lanczos", "Bessel", "Sinc");

    private static Value theHostsImageCodec(List<Value> arguments,
            Evaluator evaluator, Set<String> refinements) {

        if (refinements.contains("load")) {
            return imageLoaded(arguments, evaluator, refinements);
        }
        if (refinements.contains("save")) {
            return imageSaved(arguments, evaluator, refinements);
        }
        return UnsetValue.unset();
    }

    private static final List<String> IMAGE_REFINEMENTS =
            List.of("load", "save", "frame", "as");
    private static final List<Integer> IMAGE_ARGUMENT_COUNTS = List.of(1, 2, 1, 1);

    private static Value imageArgument(
            String refinement, int which, List<Value> arguments, Set<String> asked) {

        if (!asked.contains(refinement)) {
            return null;
        }
        int at = 0;
        for (int step = 0; step < IMAGE_REFINEMENTS.size(); step++) {
            if (IMAGE_REFINEMENTS.get(step).equals(refinement)) {
                return at + which < arguments.size() ? arguments.get(at + which) : null;
            }
            if (asked.contains(IMAGE_REFINEMENTS.get(step))) {
                at += IMAGE_ARGUMENT_COUNTS.get(step);
            }
        }
        return null;
    }

    private static String imageCodecNamed(List<Value> arguments,
            Evaluator evaluator, Set<String> refinements) {

        Value asked = imageArgument("as", 0, arguments, refinements);
        String type = asked instanceof WordValue word ? word.canonical() : "";
        boolean known = evaluator.images().knows(type);
        if (asked != null && !known) {
            throw Raised.of(EvaluationFailure.BAD_FUNC_ARG, asked);
        }
        return type;
    }

    private static Value imageLoaded(List<Value> arguments,
            Evaluator evaluator, Set<String> refinements) {

        String type = imageCodecNamed(arguments, evaluator, refinements);
        Value source = imageArgument("load", 0, arguments, refinements);
        Value frame = imageArgument("frame", 0, arguments, refinements);
        int which = frame instanceof IntegerValue counted
                ? (int) counted.magnitude()
                : 1;
        ImagePort.Pixels read = whatTheCodecMadeOf(
                source, type, which, evaluator);
        if (read == null) {
            throw source instanceof BinaryValue
                    ? Raised.of(EvaluationFailure.NO_CODEC, IntegerValue.of(0))
                    : Raised.of(EvaluationFailure.CANNOT_OPEN, source);
        }
        return imageOf(read);
    }

    private static ImagePort.Pixels whatTheCodecMadeOf(
            Value source, String type, int frame, Evaluator evaluator) {

        byte[] encoded;
        try {
            encoded = source instanceof BinaryValue bytes
                    ? bytes.octetsFromHere()
                    : evaluator.files().readBytes(((StringValue) source).text());
        } catch (FilePort.Denied unreadable) {
            return null;
        }
        return evaluator.images().decoded(encoded, type, frame);
    }

    private static Value imageOf(ImagePort.Pixels read) {
        ImageValue image = ImageValue.of(read.wide(), read.high());
        byte[] rgba = read.rgba();
        for (int pixel = 1; pixel <= read.wide() * read.high(); pixel++) {
            int at = (pixel - 1) * 4;
            image.storage().setColourAt(pixel,
                    rgba[at] & 0xFF, rgba[at + 1] & 0xFF, rgba[at + 2] & 0xFF);
            image.storage().setAlphaAt(pixel, rgba[at + 3] & 0xFF);
        }
        return image;
    }

    private static Value imageSaved(List<Value> arguments,
            Evaluator evaluator, Set<String> refinements) {

        String type = imageCodecNamed(arguments, evaluator, refinements);
        Value destination = imageArgument("save", 0, arguments, refinements);
        Value given = imageArgument("save", 1, arguments, refinements);
        if (!(given instanceof ImageValue image)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(given));
        }
        byte[] written = evaluator.images().encoded(theWholeOf(image), type);
        if (written == null) {
            throw Raised.of(EvaluationFailure.NO_CODEC, IntegerValue.of(0));
        }
        if (destination instanceof StringValue address
                && destination.datatype() == Datatype.FILE) {
            evaluator.files().write(address.text(), written);
            return destination;
        }
        if (destination instanceof BinaryValue holding) {
            return filledWithTheEncodedBytes(holding, written);
        }
        return binaryOfBytes(written);
    }

    private static Value filledWithTheEncodedBytes(
            BinaryValue destination, byte[] written) {

        BinaryStorage storage = destination.storage();
        while (storage.length() >= destination.index()) {
            storage.removeAt(destination.index());
        }
        for (byte octet : written) {
            storage.append(octet & 0xFF);
        }
        return destination;
    }

    private static ImagePort.Pixels theWholeOf(ImageValue image) {
        return new ImagePort.Pixels(image.storage().wide(), image.storage().high(),
                everyPixelOf(image.head()));
    }

    private static Value theKeyGenerateWouldHaveMade(WordValue curveNamed) {
        if (!EllipticCurveKey.curveNamesInTheCataloguesOrder().contains(curveNamed.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, curveNamed.spelling());
        }
        return BinaryValue.of(0);
    }

    private static Value modularExchange(List<Value> arguments, Set<String> refinements) {
        if (refinements.contains("public") && refinements.contains("secret")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "dh publishes or agrees, not both");
        }
        DiffieHellmanKey key = modularKeyHeldBy(arguments.getFirst());
        if (key == null || key.released()) {
            return NoneValue.none();
        }
        if (refinements.contains("public")) {
            return BinaryValue.of(unsignedOctets(
                    key.publishedPaddedToTheWidthOfThePrime()));
        }
        if (refinements.contains("secret")) {
            return secretAgreedBetween(key,
                    ((BinaryValue) arguments.get(1)).octetsFromHere());
        }
        return NoneValue.none();
    }

    private static Value secretAgreedBetween(DiffieHellmanKey key, byte[] peersValue) {
        return key.agreedWith(peersValue)
                .<Value>map(secret -> BinaryValue.of(unsignedOctets(secret)))
                .orElseGet(NoneValue::none);
    }

    private static DiffieHellmanKey modularKeyHeldBy(Value given) {
        return given instanceof HandleValue held
                && DHM_HANDLE_TYPE.equals(held.typeName())
                && held.payload() instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof DiffieHellmanKey key
                ? key
                : null;
    }

    private static Value rsaKeyBuiltFrom(List<Value> arguments, Set<String> refinements) {
        byte[] modulus = ((BinaryValue) arguments.get(0)).octetsFromHere();
        byte[] publicExponent = ((BinaryValue) arguments.get(1)).octetsFromHere();
        java.util.Optional<RsaKey> built = refinements.contains("private")
                ? RsaKey.privateKeyFrom(modulus, publicExponent,
                        ((BinaryValue) arguments.get(2)).octetsFromHere(),
                        ((BinaryValue) arguments.get(3)).octetsFromHere(),
                        ((BinaryValue) arguments.get(4)).octetsFromHere())
                : RsaKey.publicKeyFrom(modulus, publicExponent);
        return built.<Value>map(key -> HandleValue.context(RSA_HANDLE_TYPE,
                        nextCipherIdentity(), JavaObjectValue.of(key)))
                .orElseGet(NoneValue::none);
    }

    private static Value ellipticExchange(List<Value> arguments, Set<String> refinements) {
        refuseUnlessExactlyOneOf(ECDH_ACTIONS, refinements, "ecdh");
        if (refinements.contains("init")) {
            return curveKeyMadeOn(arguments.getFirst(),
                    ((WordValue) arguments.get(1)).canonical());
        }
        EllipticCurveKey key = curveKeyHeldBy(arguments.getFirst());
        if (key == null || key.released()) {
            return NoneValue.none();
        }
        if (refinements.contains("curve")) {
            return WordValue.of(key.curveName());
        }
        if (refinements.contains("public")) {
            return BinaryValue.of(unsignedOctets(key.publishedPoint()));
        }
        if (refinements.contains("secret")) {
            return secretAgreedBetween(key, peersPointGivenTo(arguments, refinements));
        }
        return UnsetValue.unset();
    }

    private static Value curveKeyMadeOn(Value given, String curveName) {
        EllipticCurveKey standing = curveKeyHeldBy(given);
        if (standing != null) {
            return standing.startAgainOn(curveName) ? given : NoneValue.none();
        }
        return EllipticCurveKey.onCurve(curveName)
                .<Value>map(key -> HandleValue.context(ECDH_HANDLE_TYPE,
                        nextCipherIdentity(), JavaObjectValue.of(key)))
                .orElseGet(NoneValue::none);
    }

    private static Value secretAgreedBetween(EllipticCurveKey key, byte[] peersPoint) {
        return key.agreedWith(peersPoint)
                .<Value>map(secret -> BinaryValue.of(unsignedOctets(secret)))
                .orElseGet(NoneValue::none);
    }

    private static byte[] peersPointGivenTo(
            List<Value> arguments, Set<String> refinements) {
        int at = refinements.contains("init") ? 2 : 1;
        return ((BinaryValue) arguments.get(at)).octetsFromHere();
    }

    private static Value ellipticSignature(
            List<Value> arguments, Set<String> refinements) {
        byte[] hash = ((BinaryValue) arguments.get(1)).octetsFromHere();
        if (refinements.contains("curve")
                && arguments.getFirst() instanceof BinaryValue published) {
            return EllipticCurveKey.aPublishedPointVerifies(
                    published.octetsFromHere(),
                    ((WordValue) argumentFor("curve", List.of("verify", "curve"),
                            arguments, refinements, 2)).canonical(),
                    hash,
                    ((BinaryValue) arguments.get(2)).octetsFromHere())
                    ? LogicValue.yes()
                    : NoneValue.none();
        }
        EllipticCurveKey key = curveKeyHeldBy(arguments.getFirst());
        if (key == null || key.released()) {
            return NoneValue.none();
        }
        return refinements.contains("verify")
                ? trueOrNoneWhetherTheSignatureHolds(key, hash,
                        ((BinaryValue) arguments.get(2)).octetsFromHere())
                : signatureOver(key, hash);
    }

    private static Value trueOrNoneWhetherTheSignatureHolds(
            EllipticCurveKey key, byte[] hash, byte[] signature) {
        return key.verifies(hash, signature) ? LogicValue.yes() : NoneValue.none();
    }

    private static Value signatureOver(EllipticCurveKey key, byte[] hash) {
        return key.signed(hash)
                .<Value>map(signature -> BinaryValue.of(unsignedOctets(signature)))
                .orElseGet(NoneValue::none);
    }

    private static void refuseUnlessExactlyOneOf(
            List<String> actions, Set<String> refinements, String nativeName) {
        if (actions.stream().filter(refinements::contains).count() > 1) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    nativeName + " does one thing per call");
        }
    }

    private static EllipticCurveKey curveKeyHeldBy(Value given) {
        return given instanceof HandleValue held
                && ECDH_HANDLE_TYPE.equals(held.typeName())
                && held.payload() instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof EllipticCurveKey key
                ? key
                : null;
    }

    private static final List<String> RSA_ACTIONS =
            List.of("encrypt", "decrypt", "sign", "verify");

    private static Value rsaOperation(List<Value> arguments, Set<String> refinements) {
        List<String> asked = RSA_ACTIONS.stream().filter(refinements::contains).toList();
        boolean padded = refinements.contains("oaep") || refinements.contains("pss");
        if (asked.size() > 1 || ((padded || refinements.contains("hash")) && asked.isEmpty())) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "rsa does one thing per call");
        }
        if (!(arguments.getFirst() instanceof HandleValue held)
                || !RSA_HANDLE_TYPE.equals(held.typeName())
                || !(held.payload() instanceof JavaObjectValue carried)
                || !(carried.held().orElse(null) instanceof RsaKey key)) {
            throw Raised.of(EvaluationFailure.INVALID_HANDLE,
                    arguments.getFirst() instanceof HandleValue other
                            ? other.typeName() : "rsa-key");
        }
        if (asked.isEmpty()) {
            return NoneValue.none();
        }
        String action = asked.getFirst();
        if (!key.canDecryptAndSign() && (action.equals("decrypt") || action.equals("sign"))) {
            return NoneValue.none();
        }
        byte[] data = octetsOf(arguments.get(1));
        try {
            return switch (action) {
                case "encrypt" -> BinaryValue.of(unsignedOctets(
                        key.enciphered(data, refinements.contains("oaep"))));
                case "decrypt" -> BinaryValue.of(unsignedOctets(
                        key.deciphered(data, refinements.contains("oaep"))));
                case "sign" -> BinaryValue.of(unsignedOctets(key.signed(data,
                        digestNamedIn(arguments, refinements),
                        refinements.contains("pss"))));
                default -> LogicValue.of(key.verifies(data,
                        signatureGivenTo(arguments, refinements),
                        digestNamedIn(arguments, refinements),
                        refinements.contains("pss")));
            };
        } catch (Exception refused) {
            return NoneValue.none();
        }
    }

    private static String digestNamedIn(List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("hash")) {
            return "sha256";
        }
        Value asked = arguments.get(refinements.contains("verify") ? 3 : 2);
        return asked instanceof WordValue digest ? digest.canonical() : "sha256";
    }

    private static byte[] signatureGivenTo(List<Value> arguments, Set<String> refinements) {
        return arguments.get(2) instanceof BinaryValue signature
                ? signature.octetsFromHere()
                : new byte[0];
    }

    private static int[] unsignedOctets(byte[] octets) {
        int[] widened = new int[octets.length];
        for (int at = 0; at < octets.length; at++) {
            widened[at] = octets[at] & 0xFF;
        }
        return widened;
    }

    private static final int CIPHER_HANDLE_IDENTITY = 2000;

    private static final java.util.concurrent.atomic.AtomicInteger CIPHER_IDENTITIES =
            new java.util.concurrent.atomic.AtomicInteger(CIPHER_HANDLE_IDENTITY);

    private static int nextCipherIdentity() {
        return CIPHER_IDENTITIES.incrementAndGet();
    }

    private static Value encipheredThroughTheStreamInPlace(
            HandleValue held, BinaryValue data) {
        if (!RC4_HANDLE_TYPE.equals(held.typeName())
                || !(held.payload() instanceof JavaObjectValue carried)
                || !(carried.held().orElse(null) instanceof StreamCipher cipher)) {
            throw Raised.of(EvaluationFailure.INVALID_HANDLE, held.typeName());
        }
        requireChangeable(data);
        for (int at = data.index(); at <= data.storageLength(); at++) {
            data.storage().set(at, data.storage().at(at)
                    ^ cipher.nextKeystreamByteAdvancingThePermutation());
        }
        return data;
    }

    private static final int ARCS_PACKED_INTO_THE_FIRST_BYTE = 40;

    private static final int GROUP_BITS = 7;
    private static final int GROUP_MASK = 0x7F;
    private static final int MORE_GROUPS_FOLLOW = 0x80;

    private static String objectIdentifierWritten(byte[] encoded) {
        if (encoded.length == 0) {
            return "";
        }
        StringBuilder written = new StringBuilder();
        int first = encoded[0] & 0xFF;
        written.append(first / ARCS_PACKED_INTO_THE_FIRST_BYTE)
                .append('.')
                .append(first % ARCS_PACKED_INTO_THE_FIRST_BYTE);
        long group = 0;
        for (int at = 1; at < encoded.length; at++) {
            int octet = encoded[at] & 0xFF;
            group = (group << GROUP_BITS) + (octet & GROUP_MASK);
            if ((octet & MORE_GROUPS_FOLLOW) == 0) {
                written.append('.').append(group);
                group = 0;
            }
        }
        return written.toString();
    }

    private static String moldedWithin(Value value, int width) {
        String written = Molder.mold(value);
        return written.length() <= width ? written : written.substring(0, width);
    }

    private static final Set<String> DEBUG_ONLY_CHANTS = Set.of(
            "crash-dump", "watch-recycle", "watch-alloc",
            "watch-obj-copy", "watch-expand", "crash");

    private static final String EVOKE_HELP = """
            Evoke values:
            [stack-size n]

            1: check memory pools
            2: check bind table
            """;

    private int obeyAnsweringHowManyValuesItTook(Value chant, Evaluator evaluator) {
        if (chant instanceof WordValue word) {
            if (DEBUG_ONLY_CHANTS.contains(word.canonical())) {
                throw Raised.of(EvaluationFailure.FEATURE_NA, word.spelling());
            }
            if (word.canonical().equals("stack-size")) {
                return 1;
            }
            if (word.canonical().equals("delect")) {
                return 0;
            }
            evaluator.output().write(EVOKE_HELP);
            return 0;
        }
        if (chant instanceof IntegerValue which
                && (which.magnitude() < 0 || which.magnitude() > 2)) {
            evaluator.output().write(EVOKE_HELP);
        }
        return 0;
    }

    private Value filledInProfile(Evaluator evaluator) {
        Value standing = pathInto(evaluator.systemContext(), "system", "standard", "stats");
        if (!(standing instanceof ObjectValue profile)) {
            return NoneValue.none();
        }
        Context fields = profile.context();
        setIfPresent(fields, "timer",
                TimeValue.ofNanoseconds(System.nanoTime() - startedAt));
        setIfPresent(fields, "evals", IntegerValue.of(evaluator.valuesWalked()));
        setIfPresent(fields, "eval-natives", IntegerValue.of(evaluator.nativesCalled()));
        setIfPresent(fields, "eval-functions", IntegerValue.of(evaluator.functionsCalled()));
        return profile;
    }

    private static void setIfPresent(Context fields, String name, Value written) {
        if (fields.holds(name)) {
            fields.set(name, written);
        }
    }

    private static Set<Datatype> waitableDatatypes() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.NUMBER.members());
        accepted.addAll(List.of(Datatype.TIME, Datatype.PORT,
                Datatype.BLOCK, Datatype.NONE));
        return Set.copyOf(accepted);
    }

    private static void sleepInterruptibly(long milliseconds, Evaluator evaluator) {
        long slice = 50;
        long remaining = milliseconds;
        while (remaining > 0) {
            if (evaluator.reasonToStop().isPresent()) {
                return;
            }
            try {
                Thread.sleep(Math.min(slice, remaining));
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return;
            }
            remaining -= slice;
        }
    }

    private static final int FRAME_VALUE_UNITS = 8;

    private final long startedAt = System.nanoTime();

    private static final String VERSION_TEXT = "3.22.5";
    private static final int[] VERSION_PARTS = {3, 22, 5};

    private static Set<Datatype> pokeableDatatypes() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.SERIES.members());
        accepted.add(Datatype.BITSET);
        accepted.add(Datatype.TUPLE);
        return Set.copyOf(accepted);
    }

    private void defineStrings() {
        define("find-script", List.of(Parameter.required("script", Set.of(Datatype.BINARY))),
                (arguments, evaluator, context) -> {
                    BinaryValue script = (BinaryValue) arguments.getFirst();
                    int at = headerStartsIn(script.asText());
                    return at < 0 ? NoneValue.none() : script.atIndex(script.index() + at);
                });
        define("split-lines", List.of(Parameter.required("value", Set.of(Datatype.STRING))),
                (arguments, evaluator, context) -> {
                    String whole = ((StringValue) arguments.getFirst()).text();
                    if (whole.isEmpty()) {
                        return BlockValue.block(List.of());
                    }
                    return BlockValue.block(Arrays.stream(whole.split("\r?\n", -1))
                            .<Value>map(StringValue::of)
                            .toList());
                });
        define("wildcard?", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> LogicValue.of(
                        ((StringValue) arguments.getFirst()).text().chars()
                                .anyMatch(letter -> letter == '*' || letter == '?')));
        defineCaseChange("uppercase", text -> text.toUpperCase(Locale.ROOT));
        defineCaseChange("lowercase", text -> text.toLowerCase(Locale.ROOT));
        define("trim", List.of(
                        Parameter.required("text", Set.of(
                                Datatype.STRING, Datatype.FILE, Datatype.URL,
                                Datatype.EMAIL, Datatype.TAG, Datatype.REF,
                                Datatype.BINARY, Datatype.BLOCK, Datatype.PAREN,
                                Datatype.PATH, Datatype.SET_PATH, Datatype.GET_PATH,
                                Datatype.LIT_PATH, Datatype.HASH,
                                Datatype.OBJECT, Datatype.ERROR, Datatype.MODULE)),
                        Parameter.belongingTo("with", "characters", Set.of())),
                Set.of("head", "tail", "auto", "lines", "all", "with"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof ObjectValue
                            || arguments.getFirst() instanceof ModuleValue
                            || arguments.getFirst() instanceof ErrorValue) {
                        if (!refinements.isEmpty()) {
                            throw Raised.of(EvaluationFailure.BAD_REFINES,
                                    "trim on an object takes no refinements");
                        }
                        return trimmedObject(arguments.getFirst());
                    }
                    refuseContradictoryTrim(arguments.getFirst(), refinements);
                    if (arguments.getFirst() instanceof BlockValue block) {
                        return trimmedBlock(block, refinements);
                    }
                    if (arguments.getFirst() instanceof BinaryValue bytes) {
                        return trimmedBinary(bytes, refinements);
                    }
                    boolean oneEndOnly =
                            refinements.contains("head") != refinements.contains("tail");
                    return rewrittenInPlace(
                            (StringValue) arguments.getFirst(), text -> {
                        if (refinements.contains("with") && arguments.size() > 1) {
                            Set<Integer> unwanted = unwantedCodePoints(arguments.get(1));
                            StringBuilder kept = new StringBuilder();
                            text.codePoints()
                                    .filter(letter -> !unwanted.contains(letter))
                                    .forEach(kept::appendCodePoint);
                            return kept.toString();
                        }
                        if (refinements.contains("all")) {
                            return text.replaceAll("\\s", "");
                        }
                        if (refinements.contains("lines")) {
                            return text.strip().replaceAll("\\s+", " ");
                        }
                        String indented = refinements.contains("auto")
                                ? withoutCommonIndent(text)
                                : text;
                        if (!oneEndOnly) {
                            boolean bothEndsNamed = refinements.contains("head");
                            return refinements.contains("auto") || bothEndsNamed
                                    ? indented.strip()
                                    : trimmedEachLine(indented);
                        }
                        return refinements.contains("head")
                                ? indented.stripLeading()
                                : indented.stripTrailing();
                    });
                });
    }

    private static final Set<Datatype> SHARES_ITS_BRANCH_WITH_MAKE = Set.of(
            Datatype.ERROR, Datatype.FUNCTION, Datatype.CLOSURE, Datatype.STRUCT);

    private static Value objectConvertedFrom(Value value) {
        if (!(value instanceof ErrorValue raised)) {
            return raiseBadMakeArg(value, "object!");
        }
        if (raised.field("code").orElseGet(NoneValue::none) instanceof IntegerValue code
                && code.magnitude() < LOWEST_CODE_AN_ERROR_CATALOGUE_ENTRY_HAS) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, value);
        }
        Context fields = Context.childOf(Context.root());
        for (String name : ErrorValue.FIELDS) {
            fields.set(name, raised.field(name).orElseGet(NoneValue::none));
        }
        return new ObjectValue(fields);
    }

    private static final int LOWEST_CODE_AN_ERROR_CATALOGUE_ENTRY_HAS = 100;

    private static Value moduleConvertedFrom(Value value) {
        if (!(value instanceof BlockValue parts)
                || parts.datatype() != Datatype.BLOCK
                || parts.remaining().isEmpty()) {
            return raiseBadMakeArg(value, "module!");
        }
        List<Value> given = parts.remaining();
        if (!(given.getFirst() instanceof ObjectValue specification)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, given.getFirst());
        }
        if (given.size() < 2 || !(given.get(1) instanceof ObjectValue body)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    given.size() < 2 ? given.getFirst() : given.get(1));
        }
        return new ModuleValue(body.context(), specification);
    }

    private void defineConversion() {
        define("to", takesAnything("type", "value"),
                (arguments, evaluator, context) -> {
                    DatatypeValue wanted = arguments.getFirst() instanceof DatatypeValue asked
                            ? asked
                            : DatatypeValue.of(arguments.getFirst().datatype());
                    if (wanted.represents() == Datatype.EVENT) {
                        return EventPath.made(wanted, arguments.get(1),
                                value -> simpleValueOf(value, evaluator, context));
                    }
                    if (SHARES_ITS_BRANCH_WITH_MAKE.contains(wanted.represents())) {
                        return madeFrom(wanted, arguments.get(1), evaluator, context);
                    }
                    if (wanted.represents() == Datatype.OBJECT) {
                        return objectConvertedFrom(arguments.get(1));
                    }
                    if (wanted.represents() == Datatype.MODULE) {
                        return moduleConvertedFrom(arguments.get(1));
                    }
                    return converted(Conversion.TO, arguments.getFirst(), arguments.get(1));
                });

        define("as-pair", takesOnlyNumbers("x", "y"),
                (arguments, evaluator, context) -> PairValue.of(
                        Comparison.asDouble(arguments.get(0)), Comparison.asDouble(arguments.get(1))));

        define("to-hex", List.of(
                        Parameter.required("value", Set.of(
                                Datatype.INTEGER, Datatype.CHAR, Datatype.TUPLE)),
                        Parameter.belongingTo("size", "width", Set.of(Datatype.INTEGER))),
                Set.of("size"),
                (arguments, evaluator, context, refinements) -> {
                    OptionalLong width =
                            refinements.contains("size") && arguments.size() > 1
                            ? OptionalLong.of(
                                    ((IntegerValue) arguments.get(1)).magnitude())
                            : OptionalLong.empty();
                    width.ifPresent(Natives::refuseASizeItCannotWrite);
                    return WordValue.of(switch (arguments.getFirst()) {
                        case TupleValue tuple -> hexOfEachSegment(tuple, width);
                        case CharacterValue character -> hexSizedToItsMagnitude(
                                character.codepoint(), width);
                        default -> hexSixteenWide(
                                ((IntegerValue) arguments.getFirst()).magnitude(), width);
                    }, Datatype.ISSUE);
                });

        defineTabbing("entab", true);
        defineTabbing("detab", false);


        define("deline", List.of(Parameter.required("text", Typeset.ANY_STRING.members())),
                Set.of("lines"),
                (arguments, evaluator, context, refinements) -> {
                    StringValue text = (StringValue) arguments.getFirst();
                    if (refinements.contains("lines")) {
                        return BlockValue.block(
                                linesOfDroppingExactlyOneTrailingEmptyLine(
                                        text.text()));
                    }
                    return rewrittenInPlace(
                            text, Natives::withOneLineFeedPerEnding);
                });
        define("enline", List.of(Parameter.required("text",
                        Set.of(Datatype.STRING, Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    if (arguments.getFirst() instanceof BlockValue) {
                        throw Raised.of(EvaluationFailure.NOT_DONE,
                                "joining a block of lines is not written yet");
                    }
                    return rewrittenInPlace((StringValue) arguments.getFirst(),
                            Natives::withOneLineFeedPerEnding);
                });

        define("as", List.of(
                        Parameter.required("type", asTypeOrExample()),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    Datatype wanted = arguments.get(0) instanceof DatatypeValue asked
                            ? asked.represents()
                            : arguments.get(0).datatype();
                    Value value = arguments.get(1);
                    if (value.datatype() == wanted) {
                        return value;
                    }
                    if (value instanceof BlockValue block && wanted.isAnyBlock()) {
                        return block.as(wanted);
                    }
                    if (value instanceof StringValue text && wanted.isAnyString()) {
                        return text.as(wanted);
                    }
                    throw Raised.of(EvaluationFailure.NOT_SAME_CLASS,
                            value.datatype().literalSpelling() + " and "
                                    + wanted.literalSpelling() + " hold different things");
                });

    }

    private Value functionFrom(Value given, Context context) {
        if (!(given instanceof BlockValue parts)) {
            return raiseBadMakeArg(given, "function!");
        }
        List<Value> items = parts.remaining();
        if (items.size() < 2
                || !(items.get(0) instanceof BlockValue spec)
                || !(items.get(1) instanceof BlockValue body)) {
            return raiseBadMakeArg(given, "function!");
        }
        return makeFunction(spec, body, context);
    }

    private Value operatorFrom(Value given, Context context) {
        Value dispatching = given instanceof BlockValue parts
                ? functionFrom(parts, context)
                : given;
        if (!dispatching.datatype().isAnyFunction()
                || howManyArgumentsBeforeAnyRefinement(dispatching) != 2) {
            return raiseBadMakeArg(given, "op!");
        }
        return new OperatorValue(AN_OPERATOR_NOBODY_HAS_NAMED, dispatching);
    }

    private static int howManyArgumentsBeforeAnyRefinement(Value dispatching) {
        List<Parameter> declared = switch (dispatching) {
            case FunctionValue function -> function.parameters();
            case NativeValue built -> built.parameters();
            case OperatorValue operator ->
                    List.of(Parameter.required("a"), Parameter.required("b"));
            default -> List.<Parameter>of();
        };
        int counted = 0;
        for (Parameter parameter : declared) {
            if (parameter.kind() == ParameterKind.REFINEMENT) {
                return counted;
            }
            if (parameter.kind() != ParameterKind.RETURN_TYPE) {
                counted++;
            }
        }
        return counted;
    }

    private static final String AN_OPERATOR_NOBODY_HAS_NAMED = "?";

    private static Value derivedFunction(Value original, BlockValue given) {
        List<Value> parts = given.remaining();
        if (parts.isEmpty()) {
            return original;
        }
        Value first = parts.getFirst();
        boolean keepingTheSpecification = isTheStarThatMeansKeepIt(first);
        if (!keepingTheSpecification && !(first instanceof BlockValue)) {
            return raiseCannotUse(given, "make on a function");
        }
        Value replacementBody = parts.size() > 1 ? parts.get(1) : NoneValue.none();
        if (original instanceof NativeValue && replacementBody instanceof BlockValue) {
            return raiseCannotUse(given, "make");
        }
        if (!(original instanceof FunctionValue written)) {
            return original instanceof NativeValue built && !keepingTheSpecification
                    ? built.derivedWith((BlockValue) first,
                            FunctionSpec.parametersIn((BlockValue) first))
                    : original;
        }
        BlockValue spec = keepingTheSpecification
                ? asABlock(written.spec())
                : (BlockValue) first;
        BlockValue body = replacementBody instanceof BlockValue replacement
                ? replacement
                : asABlock(written.body());
        return withItsBodyBound(new FunctionValue(
                spec, body, FunctionSpec.parametersIn(spec),
                FunctionSpec.localNamesIn(spec), written.closedOver()));
    }

    private static BlockValue asABlock(Value half) {
        return half instanceof BlockValue block
                ? block
                : BlockValue.block(List.of());
    }

    private static boolean isTheStarThatMeansKeepIt(Value first) {
        return first instanceof WordValue star && star.canonical().equals("*");
    }

    private static Value madeImage(Value from) {
        if (from instanceof ImageValue original) {
            return new ImageValue(original.storage().copy(), 1);
        }
        if (from instanceof PairValue size) {
            return ImageValue.of(sideOfClampedBelowAndRefusedAbove(size.x()),
                    sideOfClampedBelowAndRefusedAbove(size.y()));
        }
        if (from instanceof BlockValue parts && !parts.remaining().isEmpty()) {
            return imageFromParts(parts);
        }
        return raiseMalconstruct(from);
    }

    private static int sideOfClampedBelowAndRefusedAbove(double given) {
        int side = (int) given;
        if (side > ImageStorage.LONGEST_SIDE) {
            throw Raised.of(EvaluationFailure.SIZE_LIMIT,
                    DatatypeValue.of(Datatype.IMAGE));
        }
        return Math.max(side, 0);
    }

    private static Value imageFromParts(BlockValue specification) {
        List<Value> parts = specification.remaining();
        if (!(parts.getFirst() instanceof PairValue size)) {
            return raiseMalconstruct(specification);
        }
        ImageValue made = ImageValue.of(
                sideThatCanExist(size.x(), specification),
                sideThatCanExist(size.y(), specification));
        int at = 1;
        if (at < parts.size() && parts.get(at) instanceof BinaryValue colours) {
            fillColoursFrom(made, colours);
            at++;
            if (at < parts.size() && parts.get(at) instanceof BinaryValue alphas) {
                fillAlphasFrom(made, alphas);
                at++;
            }
            if (at < parts.size() && parts.get(at) instanceof IntegerValue start) {
                made = made.standingAt(aPositionOfAtLeastOne(start));
                at++;
            }
        } else if (at < parts.size() && parts.get(at) instanceof TupleValue colour) {
            fillWith(made, colour);
            at++;
            if (at < parts.size() && parts.get(at) instanceof IntegerValue alpha) {
                for (int pixel = 1; pixel <= made.storageLength(); pixel++) {
                    made.storage().setAlphaAt(pixel, (int) alpha.magnitude() & 0xFF);
                }
                at++;
            }
        }
        return at == parts.size() ? made : raiseMalconstruct(specification);
    }

    private static int sideThatCanExist(double given, BlockValue specification) {
        if (given < 0 || given > ImageStorage.LONGEST_SIDE) {
            throw Raised.of(EvaluationFailure.MALCONSTRUCT,
                    Molder.mold(specification));
        }
        return (int) given;
    }

    private static int aPositionOfAtLeastOne(IntegerValue start) {
        if (start.magnitude() < 1) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, start);
        }
        return (int) Math.min(start.magnitude(), Integer.MAX_VALUE);
    }

    private static final int WIDEST_ROW_OF_ITS_OWN_LENGTH = 100;

    private static final int WIDEST_HUNDRED_WIDE_PICTURE = 10000;

    private static final int A_ROW_OF_A_BIG_PICTURE = 500;

    private static final int BYTES_A_PIXEL = 4;

    private static Value imageConvertedFrom(Value value) {
        if (value instanceof ImageValue already) {
            return new ImageValue(already.storage().copy(), 1);
        }
        if (!(value instanceof BinaryValue bytes)) {
            throw Raised.of(EvaluationFailure.INVALID_TYPE, value.datatype().literalSpelling());
        }
        int pixels = bytes.lengthFromHere() / BYTES_A_PIXEL;
        if (pixels == 0) {
            return raiseBadMakeArg(value, "image!");
        }
        int across = pixels < WIDEST_ROW_OF_ITS_OWN_LENGTH
                ? pixels
                : pixels < WIDEST_HUNDRED_WIDE_PICTURE
                        ? WIDEST_ROW_OF_ITS_OWN_LENGTH
                        : A_ROW_OF_A_BIG_PICTURE;
        int down = pixels / across;
        if (across * down < pixels) {
            down++;
        }
        ImageValue made = ImageValue.of(across, down);
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int at = bytes.index() + (pixel - 1) * BYTES_A_PIXEL;
            made.storage().setColourAt(pixel,
                    bytes.storage().at(at),
                    bytes.storage().at(at + 1),
                    bytes.storage().at(at + 2));
            made.storage().setAlphaAt(pixel, bytes.storage().at(at + 3));
        }
        return made;
    }

    private static void fillColoursFrom(ImageValue made, BinaryValue colours) {
        int pixels = Math.min(made.storageLength(), colours.lengthFromHere() / 3);
        for (int pixel = 1; pixel <= pixels; pixel++) {
            int at = colours.index() + (pixel - 1) * 3;
            made.storage().setColourAt(pixel,
                    colours.storage().at(at),
                    colours.storage().at(at + 1),
                    colours.storage().at(at + 2));
        }
    }

    private static void fillAlphasFrom(ImageValue made, BinaryValue alphas) {
        int pixels = Math.min(made.storageLength(), alphas.lengthFromHere());
        for (int pixel = 1; pixel <= pixels; pixel++) {
            made.storage().setAlphaAt(pixel,
                    alphas.storage().at(alphas.index() + pixel - 1));
        }
    }

    private static void fillWith(ImageValue made, TupleValue colour) {
        int[] parts = colour.segments();
        for (int pixel = 1; pixel <= made.storageLength(); pixel++) {
            made.storage().setColourAt(pixel,
                    parts.length > 0 ? parts[0] : 0,
                    parts.length > 1 ? parts[1] : 0,
                    parts.length > 2 ? parts[2] : 0);
            if (parts.length > 3) {
                made.storage().setAlphaAt(pixel, parts[3]);
            }
        }
    }

    private static double theDifferenceBetweenImages(
            List<Value> arguments, Set<String> refinements) {

        ImageValue first = (ImageValue) arguments.get(0);
        ImageValue second = (ImageValue) arguments.get(1);
        if (!refinements.contains("part")) {
            return ImageOperations.differenceBetween(first, second);
        }
        PairValue corner = (PairValue) arguments.get(2);
        PairValue size = (PairValue) arguments.get(3);
        return ImageOperations.differenceOverTheRectangle(first, second,
                (int) corner.x(), (int) corner.y(), (int) size.x(), (int) size.y());
    }

    private static Value raiseMalconstruct(Value from) {
        throw Raised.of(EvaluationFailure.MALCONSTRUCT,
                Molder.mold(from));
    }

    private static long positionAskedFor(SeriesValue series, Value given, boolean fromOne) {
        if (given instanceof PairValue coordinate) {
            if (!(series instanceof ImageValue image)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        series.datatype().literalSpelling()
                                + " has no width, so a pair names no position in it");
            }
            return ((long) coordinate.y() - (fromOne ? 1 : 0)) * image.storage().wide()
                    + (long) coordinate.x();
        }
        return switch (given) {
            case IntegerValue number -> number.magnitude();
            case DecimalValue number -> (long) number.quantity();
            case LogicValue yesOrNo -> yesOrNo.isTruthy() ? 1 : 2;
            default -> 1;
        };
    }

    private static int colourByteOfRoundingNotTruncating(Value given) {
        double number = switch (given) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction -> fraction.datatype() == Datatype.PERCENT
                    ? fraction.quantity() * 255.0 + 0.5
                    : fraction.quantity() + 0.5;
            default -> 0;
        };
        return Math.max(0, Math.min(255, (int) number));
    }

    private static int[] threeParts(TupleValue colour) {
        int[] parts = colour.segments();
        return new int[] {
                parts.length > 0 ? parts[0] : 0,
                parts.length > 1 ? parts[1] : 0,
                parts.length > 2 ? parts[2] : 0};
    }

    private static Value recolouredTuple(
            TupleValue colour, java.util.function.UnaryOperator<int[]> formula) {
        int[] made = colour.segments().clone();
        int[] recoloured = formula.apply(threeParts(colour));
        for (int octet = 0; octet < Math.min(made.length, recoloured.length); octet++) {
            made[octet] = recoloured[octet];
        }
        return TupleValue.of(made);
    }

    private static Value overEveryColour(
            Value target,
            Function<int[], Value> ofAColour,
            java.util.function.UnaryOperator<int[]> ofAPixel) {
        if (target instanceof TupleValue colour) {
            return ofAColour.apply(threeParts(colour));
        }
        ImageValue image = (ImageValue) target;
        for (int pixel = 1; pixel <= image.lengthFromHere(); pixel++) {
            int[] channels = image.pixelAt(pixel);
            int[] recoloured = ofAPixel.apply(new int[] {
                    channels[0], channels[1], channels[2]});
            image.storage().setColourAt(image.index() + pixel - 1,
                    recoloured[0], recoloured[1], recoloured[2]);
        }
        return image;
    }

    private static Value simpleValueOf(Value given, Evaluator evaluator, Context context) {
        if (given instanceof WordValue word
                && (word.datatype() == Datatype.WORD
                        || word.datatype() == Datatype.GET_WORD)) {
            return evaluator.valueOfWordIn(word, context);
        }
        if (given instanceof BlockValue path
                && (path.datatype() == Datatype.PATH
                        || path.datatype() == Datatype.GET_PATH)) {
            return evaluator.valueOfPathIn(path, context);
        }
        return given;
    }

    private static Value madeVector(Value from, Evaluator evaluator, Context context) {
        if (from instanceof IntegerValue counted || from instanceof DecimalValue) {
            long howMany = from instanceof IntegerValue whole
                    ? whole.magnitude()
                    : (long) ((DecimalValue) from).quantity();
            if (howMany < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(from));
            }
            return VectorSpec.ofSize((int) howMany);
        }
        if (from instanceof BinaryValue bytes) {
            return VectorSpec.ofOctets(bytes);
        }
        if (from instanceof VectorValue already) {
            return copiedElements(already, already.lengthFromHere());
        }
        if (from instanceof BlockValue spec) {
            return VectorSpec.readMakeSpec(spec.remaining(),
                            written -> simpleValueOf(written, evaluator, context))
                    .orElseThrow(() -> Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                            Datatype.VECTOR.literalSpelling()));
        }
        throw Raised.of(EvaluationFailure.BAD_MAKE_ARG, Datatype.VECTOR.literalSpelling());
    }


    private static BlockValue aPairToALine(BlockValue pairs) {
        for (int at = 1; at <= pairs.storageLength(); at += 2) {
            pairs.storage().setLineBreakAt(at, true);
        }
        return pairs;
    }

    private static Value dateReadFrom(StringValue written) {
        return Transcoder.transcode(written.text()).values()
                .map(BlockValue::remaining)
                .filter(read -> read.size() == 1 && read.getFirst() instanceof DateValue)
                .map(List::getFirst)
                .orElseGet(() -> raiseBadMakeArg(written, "date!"));
    }

    private static Value blockTypeBuilt(Conversion asking, Datatype wanted, Value from) {
        if (from instanceof BlockValue given) {
            return laidOutLike(given, new BlockStorage(given.remaining())).as(wanted);
        }
        if (from instanceof MapValue pairs) {
            return aPairToALine(BlockValue.block(pairs.flattened())).as(wanted);
        }
        if (isAnyObject(from)) {
            return blockOfFieldsAndValues(fieldsOf(from)).as(wanted);
        }
        if (from instanceof VectorValue numbers) {
            return BlockValue.block(numbers.remaining()).as(wanted);
        }
        if (asking.builds()) {
            if (from.datatype() == Datatype.INTEGER
                    || from.datatype() == Datatype.DECIMAL) {
                return BlockValue.block(List.of()).as(wanted);
            }
        } else if (wrapsIntoWhatTheCallerAskedFor(wanted)) {
            return from instanceof TypesetValue kinds
                    && (wanted == Datatype.BLOCK || wanted == Datatype.PAREN)
                    ? BlockValue.block(kinds.members().stream()
                            .sorted().<Value>map(DatatypeValue::of).toList()).as(wanted)
                    : BlockValue.block(from).as(wanted);
        }
        if (from.datatype() == Datatype.STRING && from instanceof StringValue text) {
            return sourceReadFromStoppingAtANoughtByte(text.text(), wanted);
        }
        if (from instanceof BinaryValue octets) {
            return sourceReadFromStoppingAtANoughtByte(
                    textDecodedFrom(octets), wanted);
        }
        if (from.datatype() == Datatype.PAIR) {
            return BlockValue.block(List.of()).as(wanted);
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(from));
    }

    private static boolean wrapsIntoWhatTheCallerAskedFor(Datatype wanted) {
        return wanted == Datatype.BLOCK || wanted == Datatype.PAREN || wanted.isAnyPath();
    }

    private static List<Value> setWordsAndValuesOf(Context fields) {
        return fields.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .flatMap(slot -> java.util.stream.Stream.of(
                        (Value) WordValue.of(slot.spelling(), Datatype.SET_WORD),
                        slot.value()))
                .toList();
    }

    private static BlockValue blockOfFieldsAndValues(Context fields) {
        BlockValue block = BlockValue.block(setWordsAndValuesOf(fields));
        for (int at = 1; at <= block.storageLength(); at += 2) {
            block.storage().setLineBreakAt(at, true);
        }
        return block;
    }

    private static Value sourceReadFromStoppingAtANoughtByte(
            String source, Datatype wanted) {
        int endsAt = source.indexOf('\0');
        TranscodeResult read = Transcoder.transcode(
                endsAt < 0 ? source : source.substring(0, endsAt));
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return read.values().orElseThrow().as(wanted);
    }

    private static final long MICROSECONDS_A_SECOND = 1_000_000L;
    private static final long MICROSECONDS_A_DAY = 86_400L * MICROSECONDS_A_SECOND;

    private static Value dateAtTheTimestamp(long microseconds) {
        long dayNumber = Math.floorDiv(microseconds, MICROSECONDS_A_DAY);
        long withinTheDay = Math.floorMod(microseconds, MICROSECONDS_A_DAY);
        java.time.LocalDate day = java.time.LocalDate.ofEpochDay(dayNumber);
        return DateValue.of(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                TimeValue.ofNanoseconds(withinTheDay * 1_000L));
    }

    private static Value dateFromParts(List<Value> parts) {
        if (parts.isEmpty()) {
            return raiseBadMakeArg(BlockValue.block(parts), "date!");
        }
        int afterTheCalendar = parts.getFirst() instanceof DateValue ? 1 : 3;
        if (parts.size() < afterTheCalendar) {
            return raiseBadMakeArg(BlockValue.block(parts), "date!");
        }
        DateValue calendar = parts.getFirst() instanceof DateValue already
                ? already
                : calendarDayIn(parts);
        List<Value> after = parts.subList(afterTheCalendar, parts.size());
        int clockTakes = howManyPartsTheClockTakes(after);
        if (after.size() < clockTakes) {
            return raiseBadMakeArg(BlockValue.block(parts), "date!");
        }
        Optional<TimeValue> clock = clockTakes == 0
                ? Optional.empty()
                : Optional.of(clockIn(after.subList(0, clockTakes), parts));
        return new DateValue(calendar.year(), calendar.month(), calendar.day(), clock,
                zoneAfterTheClock(after.subList(clockTakes, after.size()), parts));
    }

    private static DateValue calendarDayIn(List<Value> parts) {
        if (parts.get(0) instanceof IntegerValue first
                && parts.get(1) instanceof IntegerValue monthPart
                && parts.get(2) instanceof IntegerValue third) {
            int day = (int) first.magnitude();
            int year = (int) third.magnitude();
            if (day > 99) {
                year = day;
                day = (int) third.magnitude();
            }
            try {
                return DateValue.of(year, (int) monthPart.magnitude(), day);
            } catch (IllegalArgumentException namesNoDay) {
                raiseBadMakeArg(BlockValue.block(parts), "date!");
            }
        }
        return (DateValue) raiseBadMakeArg(BlockValue.block(parts), "date!");
    }

    private static int howManyPartsTheClockTakes(List<Value> after) {
        if (after.isEmpty()) {
            return 0;
        }
        if (after.getFirst() instanceof TimeValue) {
            return 1;
        }
        return after.getFirst() instanceof IntegerValue ? 3 : 0;
    }

    private static TimeValue clockIn(List<Value> written, List<Value> whole) {
        if (written.size() == 1 && written.getFirst() instanceof TimeValue already) {
            return already;
        }
        if (!(written.get(0) instanceof IntegerValue hour)
                || !(written.get(1) instanceof IntegerValue minute)
                || !(written.get(2) instanceof IntegerValue
                        || written.get(2) instanceof DecimalValue)) {
            return (TimeValue) raiseBadMakeArg(BlockValue.block(whole), "date!");
        }
        double second = Comparison.asDouble(written.get(2));
        if (hour.magnitude() < 0 || hour.magnitude() > 23
                || minute.magnitude() < 0 || minute.magnitude() >= 60
                || second < 0 || second >= 60.0) {
            return (TimeValue) raiseBadMakeArg(BlockValue.block(whole), "date!");
        }
        return TimeValue.ofNanoseconds(
                hour.magnitude() * SECONDS_AN_HOUR * TimeValue.NANOSECONDS_PER_SECOND
                        + minute.magnitude() * SECONDS_A_MINUTE * TimeValue.NANOSECONDS_PER_SECOND
                        + Math.round(second * TimeValue.NANOSECONDS_PER_SECOND));
    }

    private static final long SECONDS_A_MINUTE = 60L;
    private static final long SECONDS_AN_HOUR = 3600L;

    private static Optional<Integer> zoneAfterTheClock(
            List<Value> left, List<Value> whole) {
        if (left.isEmpty()) {
            return Optional.empty();
        }
        if (left.size() > 1 || !(left.getFirst() instanceof TimeValue offset)) {
            raiseBadMakeArg(BlockValue.block(whole), "date!");
        }
        long minutes = ((TimeValue) left.getFirst()).nanoseconds()
                / (SECONDS_A_MINUTE * TimeValue.NANOSECONDS_PER_SECOND);
        if (Math.abs(minutes) > FURTHEST_ZONE_MINUTES) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a zone reaches fifteen hours either side of UTC");
        }
        return Optional.of((int) minutes);
    }

    private static final long FURTHEST_ZONE_MINUTES = 15 * 60L;

    private static Value timeFromParts(List<Value> parts) {
        if (parts.isEmpty() || parts.size() > 3
                || !(parts.get(0) instanceof IntegerValue hours)) {
            return raiseBadMakeArg(BlockValue.block(parts), "time!");
        }
        boolean negative = hours.magnitude() < 0;
        long seconds = Math.abs(hours.magnitude()) * 3600;
        long nanoseconds = 0;
        if (parts.size() > 1) {
            if (!(parts.get(1) instanceof IntegerValue minutes)
                    || minutes.magnitude() < 0) {
                return raiseBadMakeArg(BlockValue.block(parts), "time!");
            }
            seconds += minutes.magnitude() * 60;
        }
        if (parts.size() > 2) {
            switch (parts.get(2)) {
                case IntegerValue whole when whole.magnitude() >= 0 ->
                        seconds += whole.magnitude();
                case DecimalValue fraction -> {
                    seconds += (long) fraction.quantity();
                    nanoseconds = Math.round(
                            (fraction.quantity() - (long) fraction.quantity())
                                    * 1_000_000_000L);
                }
                default -> {
                    return raiseBadMakeArg(BlockValue.block(parts), "time!");
                }
            }
        }
        long total = seconds * 1_000_000_000L + nanoseconds;
        return TimeValue.ofNanoseconds(negative ? -total : total);
    }

    private static Value structChangedBy(StructValue struct, Value given) {
        if (given instanceof BlockValue written) {
            startedWith(struct, written);
            return struct;
        }
        if (!(given instanceof BinaryValue octets)) {
            return raiseWrongArgument(given, "change", "value");
        }
        if (!struct.acceptsRawBytes()) {
            throw Raised.of(EvaluationFailure.PROTECTED,
                    "this struct holds a REBOL value, and raw bytes would land on it");
        }
        struct.changeFrom(bytesFromHere(octets));
        return struct;
    }

    private static Value whatAStructReflects(
            StructValue struct, String asked, Value written) {
        return switch (asked) {
            case "spec" -> struct.spec().declaration();
            case "words", "keys" -> BlockValue.block(struct.fieldNames());
            case "values" -> BlockValue.block(struct.fieldValues());
            case "body" -> BlockValue.block(struct.body());
            default -> raiseCannotUse(written, "reflect struct!");
        };
    }

    private StructSpec.LayoutRegistry structLayoutsKnown() {
        return name -> registeredStructLayouts.select(WordValue.of(name))
                instanceof BlockValue layout
                ? Optional.of(layout)
                : Optional.empty();
    }

    private Value structMadeFrom(Value from) {
        if (!(from instanceof BlockValue given)) {
            return raiseBadMakeArg(from, "struct!");
        }
        List<Value> written = given.remaining();
        boolean carriesInitialValues = written.size() == 2
                && written.get(0) instanceof BlockValue
                && written.get(1) instanceof BlockValue;
        BlockValue layout = carriesInitialValues
                ? (BlockValue) written.getFirst()
                : given;
        StructValue made = StructValue.of(
                structLaidOutBy(layout, structLayoutsKnown()));
        if (carriesInitialValues) {
            startedWith(made, written.get(1));
        }
        return made;
    }

    private static StructSpec structLaidOutBy(
            BlockValue layout, StructSpec.LayoutRegistry registry) {
        try {
            return StructSpec.of(layout, registry);
        } catch (StructLayoutRefused refused) {
            throw refused.malconstructed()
                    ? Raised.of(EvaluationFailure.MALCONSTRUCT, Molder.mold(layout))
                    : Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(layout));
        }
    }

    private static Value structLikeThePrototype(StructValue prototype, Value given,
            Evaluator evaluator) {
        StructValue made = prototype.separateCopy();
        if (given instanceof BinaryValue octets) {
            byte[] bytes = bytesFromHere(octets);
            if (bytes.length < made.size()) {
                return raiseBadMakeArg(given, "struct!");
            }
            made.changeFrom(bytes);
            return made;
        }
        if (!(given instanceof BlockValue written)) {
            return raiseBadMakeArg(given, "struct!");
        }
        startedWith(made, BlockValue.block(
                reducedLeavingSetWords(written, evaluator)));
        return made;
    }

    private static void startedWith(StructValue made, Value given) {
        if (given instanceof BinaryValue octets) {
            made.changeFrom(bytesFromHere(octets));
            return;
        }
        BlockValue written = (BlockValue) given;
        try {
            made.initialiseFrom(written);
        } catch (StructLayoutRefused refused) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(written));
        }
    }

    private Value constructionOf(Datatype datatype, Value specification) {
        if (datatype == Datatype.DATE
                && !(specification instanceof BlockValue
                        || specification instanceof DateValue)) {
            return raiseBadMakeArg(specification, "date!");
        }
        return makeOfDatatype(DatatypeValue.of(datatype), specification,
                null, Context.root());
    }

    private Value portMadeFrom(Value from, Evaluator evaluator, Context context) {
        if (!CAN_NAME_A_SCHEME.contains(from.datatype())) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, from);
        }
        Value built = evaluator.applyFunction(
                systemInternalFunction(context, "make-port*"), List.of(from));
        if (!(built instanceof PortValue port)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, from);
        }
        return port;
    }

    private static final Set<Datatype> CAN_NAME_A_SCHEME = Set.of(
            Datatype.FILE, Datatype.URL, Datatype.BLOCK,
            Datatype.OBJECT, Datatype.WORD, Datatype.PORT);

    private Value makeOfDatatype(
            DatatypeValue wanted, Value from, Evaluator evaluator, Context context) {
        if (wanted.represents() == Datatype.STRUCT) {
            return structMadeFrom(from);
        }
        if (wanted.represents() == Datatype.IMAGE) {
            return madeImage(from);
        }
        if (wanted.represents() == Datatype.GOB) {
            return madeGob(from, evaluator, context);
        }
        if (wanted.represents() == Datatype.EVENT) {
            return EventPath.made(wanted, from,
                    value -> simpleValueOf(value, evaluator, context));
        }
        if (wanted.represents() == Datatype.VECTOR) {
            refuseMoreRoomThanASeriesCounts(Datatype.VECTOR, from);
            return whatTheHostHadRoomFor(() -> madeVector(from, evaluator, context));
        }
        if (wanted.represents() == Datatype.PORT) {
            return portMadeFrom(from, evaluator, context);
        }
        if (wanted.represents() == Datatype.DATE
                && (from instanceof BlockValue || from instanceof DateValue)) {
            return dateFromParts(from instanceof BlockValue parts
                    ? parts.remaining()
                    : List.of(from));
        }
        if (from instanceof BlockValue parts && wanted.represents() == Datatype.TIME) {
            return timeFromParts(parts.remaining());
        }
        refuseToBuildSomethingOutOfNothing(wanted.represents(), from);
        refuseRoomForLessThanNothing(wanted.represents(), from);
        refuseMoreRoomThanASeriesCounts(wanted.represents(), from);
        if (wanted.represents().isAnyBlock()) {
            return whatTheHostHadRoomFor(() ->
                    blockTypeBuilt(Conversion.MAKE, wanted.represents(), from));
        }
        if (wanted.represents().isSeries()
                && (from.datatype() == Datatype.INTEGER
                        || from.datatype() == Datatype.DECIMAL)) {
            int asked = (int) Math.max(0,
                    Math.min(Integer.MAX_VALUE, (long) Comparison.asDouble(from)));
            return whatTheHostHadRoomFor(() ->
                    wanted.represents() == Datatype.BINARY
                            ? new BinaryValue(new BinaryStorage(asked), 1)
                            : new StringValue(
                                    StringStorage.withRoomFor(asked), 1,
                                    wanted.represents()));
        }
        return converted(Conversion.MAKE, wanted, from);
    }

    private static long bitsOfRightAligned(BinaryValue binary) {
        int howMany = binary.lengthFromHere();
        long bits = 0;
        for (int at = Math.max(0, howMany - Long.BYTES); at < howMany; at++) {
            bits = (bits << 8) | (binary.storage().at(binary.index() + at) & 0xFFL);
        }
        return bits;
    }

    private static byte[] bytesFromHere(BinaryValue binary) {
        int howMany = binary.lengthFromHere();
        byte[] bytes = new byte[howMany];
        for (int at = 0; at < howMany; at++) {
            bytes[at] = (byte) binary.storage().at(binary.index() + at);
        }
        return bytes;
    }

    private static Value binaryOfBytes(byte[] bytes) {
        int[] octets = new int[bytes.length];
        for (int at = 0; at < bytes.length; at++) {
            octets[at] = bytes[at] & 0xFF;
        }
        return BinaryValue.of(octets);
    }

    private static String withOneLineFeedPerEnding(String text) {
        StringBuilder standardised = new StringBuilder(text.length());
        int at = 0;
        while (at < text.length()) {
            char here = text.charAt(at++);
            if (here == '\n' || here == '\r') {
                if (at < text.length() && text.charAt(at) == theOtherEnding(here)) {
                    at++;
                }
                here = '\n';
            }
            standardised.append(here);
        }
        return standardised.toString();
    }

    private static char theOtherEnding(char one) {
        return one == '\n' ? '\r' : '\n';
    }

    private static List<Value> linesOfDroppingExactlyOneTrailingEmptyLine(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        String[] split = text.replace("\r\n", "\n").split("\n", -1);
        int howMany = split.length > 0 && split[split.length - 1].isEmpty()
                ? split.length - 1
                : split.length;
        List<Value> lines = new ArrayList<>(howMany);
        for (int at = 0; at < howMany; at++) {
            lines.add(StringValue.of(split[at]));
        }
        return lines;
    }

    private static Value addressBuiltFrom(BlockValue parts) {
        List<Value> written = parts.remaining();
        if (written.isEmpty()) {
            return raiseBadMakeArg(parts, Datatype.EMAIL.literalSpelling());
        }
        String user = Molder.form(written.getFirst());
        if (written.size() == 1) {
            return StringValue.of(user, Datatype.EMAIL);
        }
        String host = written.subList(1, written.size()).stream()
                .map(Molder::form)
                .collect(Collectors.joining("."));
        return StringValue.of(user + "@" + host, Datatype.EMAIL);
    }

    private static Value urlBuiltFrom(BlockValue parts) {
        List<Value> written = parts.remaining();
        if (written.isEmpty()) {
            return raiseBadMakeArg(parts, Datatype.URL.literalSpelling());
        }
        String scheme = Molder.form(written.getFirst());
        String rest = written.subList(1, written.size()).stream()
                .map(Molder::form)
                .collect(Collectors.joining("/"));
        return StringValue.of(scheme + "://" + rest, Datatype.URL);
    }

    private static Value bytesOfEach(BlockValue block) {
        List<Value> items = block.remaining();
        int[] octets = new int[items.size()];
        for (int at = 0; at < items.size(); at++) {
            if (!(items.get(at) instanceof IntegerValue whole)) {
                return raiseCannotUse(items.get(at), "to binary!");
            }
            octets[at] = (int) (whole.magnitude() & 0xFF);
        }
        return BinaryValue.of(octets);
    }

    private enum Conversion {
        MAKE, TO;

        boolean builds() {
            return this == MAKE;
        }
    }

    private static void refuseToBuildSomethingOutOfNothing(Datatype wanted, Value from) {
        if (from.datatype() != Datatype.NONE
                || wanted == Datatype.UNSET
                || wanted == Datatype.NONE
                || wanted == Datatype.LOGIC
                || wanted.isAnyBlock()) {
            return;
        }
        raiseBadMakeArg(from, wanted.literalSpelling());
    }

    private static void refuseRoomForLessThanNothing(Datatype wanted, Value from) {
        if (!wanted.isSeries()
                || from.datatype() != Datatype.INTEGER
                        && from.datatype() != Datatype.DECIMAL) {
            return;
        }
        if (Comparison.asDouble(from) < 0) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, from.toString());
        }
    }

    private static final int BYTES_A_SLOT_TAKES = 32;

    private static void refuseMoreRoomThanASeriesCounts(Datatype wanted, Value from) {
        if (!wanted.isSeries() && wanted != Datatype.MAP) {
            return;
        }
        if (from.datatype() != Datatype.INTEGER && from.datatype() != Datatype.DECIMAL) {
            return;
        }
        double asked = Comparison.asDouble(from);
        if (asked > theMostItemsThatFitIn(wanted)) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    private static long theMostItemsThatFitIn(Datatype wanted) {
        return Integer.MAX_VALUE / bytesPerItemOf(wanted) - 1;
    }

    private static final int BYTES_A_VECTORS_NUMBER_TAKES = 4;

    private static int bytesPerItemOf(Datatype wanted) {
        if (wanted == Datatype.VECTOR) {
            return BYTES_A_VECTORS_NUMBER_TAKES;
        }
        return wanted.isAnyBlock() || wanted == Datatype.MAP ? BYTES_A_SLOT_TAKES : 1;
    }

    private static Value whatTheHostHadRoomFor(java.util.function.Supplier<Value> allocating) {
        try {
            return allocating.get();
        } catch (OutOfMemoryError nothingLeftToGive) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
    }

    private static Value converted(Conversion asking, Value type, Value value) {
        DatatypeValue wanted = type instanceof DatatypeValue asked
                ? asked
                : DatatypeValue.of(type.datatype());
        refuseToBuildSomethingOutOfNothing(wanted.represents(), value);
        return switch (wanted.represents()) {
            case UNSET -> UnsetValue.unset();
            case NONE -> NoneValue.none();
            case VECTOR -> switch (value) {
                case VectorValue already -> already;
                case BinaryValue octets -> VectorSpec.ofOctets(octets);
                case BlockValue block -> VectorSpec.readMakeSpec(
                                block.remaining(), java.util.function.UnaryOperator.identity())
                        .<Value>map(made -> made)
                        .orElseGet(() -> raiseCannotUse(value, "to vector!"));
                default -> raiseCannotUse(value, "to vector!");
            };
            case INTEGER -> wholeNumberFrom(asking, value);
            case DECIMAL, PERCENT -> decimalBuiltFrom(asking, wanted.represents(), value);
            case STRING -> value instanceof BinaryValue octets
                    ? StringValue.of(textDecodedFrom(octets))
                    : StringValue.of(textForAString(value));
            case EMAIL -> value instanceof BlockValue parts
                    ? addressBuiltFrom(parts)
                    : value instanceof BinaryValue octets
                            ? StringValue.of(textDecodedFrom(octets), Datatype.EMAIL)
                            : StringValue.of(textForAString(value), Datatype.EMAIL);
            case URL -> value instanceof BlockValue parts
                    ? urlBuiltFrom(parts)
                    : value instanceof BinaryValue octets
                            ? StringValue.of(textDecodedFrom(octets), Datatype.URL)
                            : StringValue.of(textForAString(value), Datatype.URL);
            case FILE, TAG, REF -> value instanceof BinaryValue octets
                    ? StringValue.of(textDecodedFrom(octets), wanted.represents())
                    : StringValue.of(textForAString(value), wanted.represents());
            case BINARY -> binaryBuiltFrom(value);
            case WORD, SET_WORD, GET_WORD, LIT_WORD, REFINEMENT, ISSUE ->
                    wordFrom(value, wanted.represents());
            case BLOCK, PAREN, HASH, PATH, SET_PATH, GET_PATH, LIT_PATH ->
                    blockTypeBuilt(asking, wanted.represents(), value);
            case MAP -> {
                if (value instanceof IntegerValue || value instanceof DecimalValue) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG,
                            "to map! wants pairs, and a number is room for pairs "
                                    + "rather than any: make map! reads it that way");
                }
                yield mapMadeFrom(value);
            }
            case DATE -> switch (value) {
                case DateValue already -> already;
                case IntegerValue seconds ->
                        dateAtTheTimestamp(seconds.magnitude() * MICROSECONDS_A_SECOND);
                case DecimalValue seconds -> dateAtTheTimestamp(
                        (long) (seconds.quantity() * MICROSECONDS_A_SECOND));
                case BlockValue parts -> dateFromParts(parts.remaining());
                case StringValue written -> dateReadFrom(written);
                default -> raiseBadMakeArg(value, "date!");
            };
            case CHAR -> asCharacter(value);
            case PAIR -> asPair(value);
            case MONEY -> asMoney(asking, value);
            case PORT -> value instanceof ObjectValue built
                    ? new PortValue(built.context())
                    : raiseBadMakeArg(value, "port!");
            case MODULE -> moduleFromHeaderAndWords(value);
            case TASK -> asking.builds() ? aTaskMadeFrom(value) : raiseBadMakeArg(value, "task!");
            case BITSET -> BitsetActions.madeFrom(value);
            case TYPESET -> switch (value) {
                case TypesetValue already -> already;
                case BlockValue block when block.datatype() == Datatype.BLOCK ->
                        TypesetValue.of(TypesetActions.datatypesNamedIn(block));
                default -> raiseBadMakeArg(value, "typeset!");
            };
            case TIME -> aTimeMadeFrom(value);
            case TUPLE -> tupleFrom(value);
            case LOGIC -> LogicValue.of(countsAsTrue(asking, value));
            case DATATYPE -> value instanceof WordValue word
                    ? datatypeNamed(word, value)
                    : raiseBadMakeArg(value, "datatype!");
            case IMAGE -> imageConvertedFrom(value);
            default -> raiseCannotUse(value, "to " + wanted.represents().literalSpelling());
        };
    }

    private static boolean countsAsTrue(Conversion asking, Value value) {
        return value.isTruthy() && !(asking.builds() && isNothingAtAll(value));
    }

    private static boolean isNothingAtAll(Value value) {
        return switch (value) {
            case IntegerValue whole -> whole.magnitude() == 0;
            case DecimalValue number -> number.quantity() == 0.0;
            case MoneyValue amount -> amount.amount().signum() == 0;
            default -> false;
        };
    }

    private static Value binaryBuiltFrom(Value value) {
        return switch (value) {
            case BinaryValue already -> already;
            case StringValue text when text.datatype() != Datatype.ISSUE ->
                    binaryOfBytes(text.text().getBytes(StandardCharsets.UTF_8));
            case IntegerValue whole -> binaryOfBytes(
                    java.nio.ByteBuffer.allocate(Long.BYTES)
                            .putLong(whole.magnitude()).array());
            case DecimalValue fractional when fractional.datatype() == Datatype.DECIMAL ->
                    binaryOfBytes(java.nio.ByteBuffer.allocate(Long.BYTES)
                            .putLong(Double.doubleToRawLongBits(
                                    fractional.quantity())).array());
            case MoneyValue amount -> binaryOfBytes(amount.toBytes());
            case BlockValue block when block.datatype() == Datatype.BLOCK ->
                    bytesOfEach(block);
            case VectorValue vector -> binaryOfBytes(vector.octetsFromHere());
            case StructValue struct -> binaryOfBytes(struct.octets());
            case TupleValue segments -> binaryOfBytes(octetsOf(segments));
            case BitsetValue members ->
                    binaryOfBytes(new BitsetActions(members).asOctets());
            case ImageValue picture -> binaryOfBytes(everyPixelOf(picture));
            case CharacterValue letter -> binaryOfBytes(
                    Character.toString(letter.codepoint())
                            .getBytes(StandardCharsets.UTF_8));
            default -> raiseInvalidArgument(value);
        };
    }

    private static byte[] octetsOf(TupleValue segments) {
        byte[] octets = new byte[segments.segmentCount()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) segments.octetAt(at + 1);
        }
        return octets;
    }

    private static byte[] everyPixelOf(ImageValue picture) {
        byte[] octets = new byte[picture.storageLength() * PIXEL_PARTS];
        for (int pixel = 0; pixel < picture.storageLength(); pixel++) {
            int[] parts = picture.pixelAt(pixel + 1);
            for (int part = 0; part < PIXEL_PARTS; part++) {
                octets[pixel * PIXEL_PARTS + part] = (byte) parts[part];
            }
        }
        return octets;
    }

    private static final int PIXEL_PARTS = 4;

    private static Value raiseInvalidArgument(Value value) {
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(value));
    }

    private static Value decimalBuiltFrom(
            Conversion asking, Datatype wanted, Value value) {
        return switch (value) {
            case DecimalValue number -> asItStands(wanted, number.quantity());
            case IntegerValue whole -> asItStands(wanted, whole.magnitude());
            case MoneyValue amount -> asItStands(wanted, amount.amount().doubleValue());
            case CharacterValue letter -> asItStands(wanted, letter.codepoint());
            case LogicValue truth -> asking.builds()
                    ? asItStands(wanted, truth.truth() ? 1.0 : 0.0)
                    : raiseBadMakeArg(value, wanted.literalSpelling());
            case TimeValue clock -> asHundredths(wanted,
                    (double) clock.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND);
            case DateValue moment -> asHundredths(wanted, secondsSinceTheEpoch(moment));
            case BinaryValue bits -> asHundredths(wanted,
                    Double.longBitsToDouble(bitsOfRightAligned(bits)));
            case StringValue text when text.datatype() == Datatype.STRING ->
                    asHundredths(wanted, decimalReadFrom(text, wanted));
            case BlockValue parts -> asHundredths(wanted, mantissaTimesTenTo(parts, wanted));
            default -> raiseBadMakeArg(value, wanted.literalSpelling());
        };
    }

    private static Value asItStands(Datatype wanted, double quantity) {
        return wanted == Datatype.PERCENT
                ? DecimalValue.percent(quantity)
                : DecimalValue.of(quantity);
    }

    private static Value asHundredths(Datatype wanted, double quantity) {
        return wanted == Datatype.PERCENT
                ? DecimalValue.percent(quantity / 100.0)
                : DecimalValue.of(quantity);
    }

    private static double decimalReadFrom(StringValue text, Datatype wanted) {
        String qualified = qualifiedNumberIn(
                text.text(), "a number", MOST_FRACTION_CHARACTERS);
        return decimalScannedFrom(qualified, wanted == Datatype.PERCENT)
                .orElseThrow(() -> Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                        "cannot make a " + wanted.literalSpelling()
                                + " out of \"" + text.text() + "\""));
    }

    private static double mantissaTimesTenTo(BlockValue parts, Datatype wanted) {
        List<Value> both = parts.remaining();
        if (both.size() != 2) {
            raiseBadMakeArg(parts, wanted.literalSpelling());
        }
        double scaled = numberInTheBlock(both.get(0), wanted);
        double exponent = numberInTheBlock(both.get(1), wanted);
        while (exponent >= 1) {
            exponent--;
            scaled *= 10.0;
        }
        while (exponent <= -1) {
            exponent++;
            scaled /= 10.0;
        }
        return scaled;
    }

    private static double numberInTheBlock(Value part, Datatype wanted) {
        if (part instanceof IntegerValue whole) {
            return whole.magnitude();
        }
        if (part instanceof DecimalValue number) {
            return number.quantity();
        }
        raiseBadMakeArg(part, wanted.literalSpelling());
        return 0;
    }

    private static Value wholeNumberFrom(Conversion asking, Value value) {
        return switch (value) {
            case IntegerValue whole -> whole;
            case LogicValue truth -> asking.builds()
                    ? IntegerValue.of(truth.truth() ? 1 : 0)
                    : raiseBadMakeArg(value, "integer!");
            case WordValue issue when issue.datatype() == Datatype.ISSUE ->
                    hexNumberIn(issue);
            case StringValue text -> parseInteger(text.text());
            case CharacterValue character -> IntegerValue.of(character.codepoint());
            case BinaryValue bytes -> IntegerValue.of(bitsOfRightAligned(bytes));
            case DateValue moment -> IntegerValue.of(instantOf(moment));
            case DecimalValue number -> wholeNumberWithinRange(number.quantity());
            case MoneyValue amount -> IntegerValue.of(amount.amount().longValue());
            case TimeValue clock -> IntegerValue.of(clock.nanoseconds() / TimeValue.NANOSECONDS_PER_SECOND);
            default -> raiseBadMakeArg(value, "integer!");
        };
    }

    private static Value wholeNumberWithinRange(double quantity) {
        if (Double.isNaN(quantity)
                || quantity < -TOO_LARGE_FOR_A_WHOLE_NUMBER
                || quantity >= TOO_LARGE_FOR_A_WHOLE_NUMBER) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "no whole number is what " + quantity + " names");
        }
        return IntegerValue.of((long) quantity);
    }

    private static final int MOST_HEX_DIGITS = 16;

    private static Value hexNumberIn(WordValue issue) {
        String digits = issue.spelling();
        if (digits.isEmpty() || digits.length() > MOST_HEX_DIGITS) {
            return raiseBadMakeArg(issue, "integer!");
        }
        try {
            return IntegerValue.of(Long.parseUnsignedLong(digits, 16));
        } catch (NumberFormatException notHexAtAll) {
            return raiseBadMakeArg(issue, "integer!");
        }
    }

    private static long instantOf(DateValue moment) {
        return Math.round(secondsSinceTheEpoch(moment));
    }

    private static double secondsSinceTheEpoch(DateValue when) {
        DateValue.Moment moment = when.moment();
        return (double) moment.dayNumber() * (TimeValue.NANOSECONDS_PER_DAY / TimeValue.NANOSECONDS_PER_SECOND)
                + (double) moment.nanosecondsIntoTheDay() / TimeValue.NANOSECONDS_PER_SECOND;
    }

    private static Value wordNamed(String spelling, Datatype kind) {
        if (spelling.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT,
                    "a " + kind.literalSpelling() + " needs a spelling");
        }
        return WordValue.of(spelling, kind);
    }

    private static Value wordFrom(Value value, Datatype kind) {
        if (value instanceof WordValue word) {
            return WordValue.of(word.spelling(), kind);
        }
        if (value instanceof LogicValue truth) {
            return WordValue.of(truth.truth() ? "true" : "false", kind);
        }
        if (value instanceof CharacterValue letter) {
            return WordValue.of(theWordASingleCharacterSpells(letter), kind);
        }
        String spelling = switch (value) {
            case StringValue text -> text.text();
            case DatatypeValue asked -> asked.represents().literalSpelling();
            default -> null;
        };
        if (spelling == null) {
            return raiseWrongArgument(value, "to " + kind.literalSpelling(), "string");
        }
        return WordValue.of(spellingReadAs(spelling, kind), kind);
    }

    private static final String THE_PUNCTUATION_THAT_SPELLS_A_WORD_ALONE = "!%&*+-./<=>?^`|~";

    private static final int THE_FIRST_CODE_POINT_THE_SCANNER_TAKES_FOR_A_LETTER = 128;

    private static String theWordASingleCharacterSpells(CharacterValue letter) {
        if (!spellsAWordAlone(letter.codepoint())) {
            throw Raised.of(EvaluationFailure.BAD_CHAR, letter);
        }
        return Character.toString(letter.codepoint());
    }

    private static boolean spellsAWordAlone(int codepoint) {
        return codepoint >= THE_FIRST_CODE_POINT_THE_SCANNER_TAKES_FOR_A_LETTER
                || Character.isLetter(codepoint)
                || THE_PUNCTUATION_THAT_SPELLS_A_WORD_ALONE.indexOf(codepoint) >= 0;
    }

    private static String spellingReadAs(String text, Datatype kind) {
        int from = 0;
        while (from < text.length() && isLexicalSpace(text.charAt(from))) {
            from++;
        }
        int end = from;
        while (end < text.length() && !isLexicalSpace(text.charAt(end))) {
            end++;
        }
        String trimmed = text.substring(from, end);
        if (trimmed.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT);
        }
        for (int after = end; after < text.length(); after++) {
            if (!isSpaceOrTab(text.charAt(after))) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS);
            }
        }
        List<Value> read;
        try {
            read = Transcoder.transcode(kind == Datatype.ISSUE ? "#" + trimmed : trimmed)
                    .values()
                    .map(BlockValue::remaining)
                    .orElse(List.of());
        } catch (RuntimeException unreadable) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS);
        }
        Datatype wanted = kind == Datatype.ISSUE ? Datatype.ISSUE : Datatype.WORD;
        if (read.size() != 1 || !(read.getFirst() instanceof WordValue word)
                || word.datatype() != wanted
                || !word.spelling().equals(trimmed)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS);
        }
        return word.spelling();
    }

    private static Value tupleFrom(Value value) {
        return switch (value) {
            case TupleValue already -> already;
            case StringValue text -> tupleScannedFrom(text.text(), value);
            case BlockValue segments -> tupleOfSegments(segments);
            case BinaryValue octets -> tupleOfOctets(octets);
            case WordValue issue when issue.datatype() == Datatype.ISSUE ->
                    tupleOfHexPairs(issue.spelling(), value);
            default -> raiseBadMakeArg(value, "tuple!");
        };
    }

    private static Value tupleOfSegments(BlockValue segments) {
        List<Value> items = segments.remaining();
        if (items.size() > TupleValue.MAXIMUM_SEGMENTS) {
            return raiseBadMakeArg(segments, "tuple!");
        }
        int[] octets = new int[items.size()];
        for (int at = 0; at < items.size(); at++) {
            octets[at] = octetOf(items.get(at), segments);
        }
        return TupleValue.of(octets);
    }

    private static int octetOf(Value item, Value whole) {
        long number = switch (item) {
            case IntegerValue whole64 -> whole64.magnitude();
            case CharacterValue letter -> letter.codepoint();
            case DecimalValue fractional -> Math.round(Math.abs(fractional.quantity()))
                    * (fractional.quantity() < 0 ? -1 : 1);
            default -> {
                raiseBadMakeArg(whole, "tuple!");
                yield 0;
            }
        };
        if (number < 0 || number > 255) {
            raiseBadMakeArg(whole, "tuple!");
        }
        return (int) number;
    }

    private static Value tupleOfOctets(BinaryValue octets) {
        int width = Math.min(octets.lengthFromHere(), TupleValue.MAXIMUM_SEGMENTS);
        int[] kept = new int[width];
        for (int at = 0; at < width; at++) {
            kept[at] = octets.storage().at(octets.index() + at) & 0xFF;
        }
        return TupleValue.of(kept);
    }

    private static Value tupleOfHexPairs(String digits, Value original) {
        if (digits.length() % 2 != 0 || digits.length() / 2 > TupleValue.MAXIMUM_SEGMENTS) {
            return raiseBadMakeArg(original, "tuple!");
        }
        int[] octets = new int[digits.length() / 2];
        for (int at = 0; at < octets.length; at++) {
            try {
                octets[at] = Integer.parseInt(digits.substring(at * 2, at * 2 + 2), 16);
            } catch (NumberFormatException notHexadecimal) {
                return raiseBadMakeArg(original, "tuple!");
            }
        }
        return TupleValue.of(octets);
    }

    private static Value tupleScannedFrom(String text, Value original) {
        String[] parts = text.split("\\.", -1);
        if (text.isEmpty() || parts.length > TupleValue.MAXIMUM_SEGMENTS) {
            return raiseBadMakeArg(original, "tuple!");
        }
        int width = Math.max(parts.length, TupleValue.MINIMUM_SHOWN_SEGMENTS);
        int[] octets = new int[width];
        for (int at = 0; at < parts.length; at++) {
            if (parts[at].isEmpty() && at == parts.length - 1) {
                break;
            }
            int written;
            try {
                written = Integer.parseInt(parts[at].trim());
            } catch (NumberFormatException notANumber) {
                return raiseBadMakeArg(original, "tuple!");
            }
            if (written < 0 || written > 255) {
                return raiseBadMakeArg(original, "tuple!");
            }
            octets[at] = written;
        }
        return TupleValue.of(octets);
    }

    private static Value datatypeNamed(WordValue word, Value original) {
        for (Datatype candidate : Datatype.values()) {
            if (candidate.literalSpelling().equalsIgnoreCase(word.spelling())) {
                return DatatypeValue.of(candidate);
            }
        }
        return raiseBadMakeArg(original, "datatype!");
    }

    private static Value asCharacter(Value value) {
        if (value instanceof CharacterValue already) {
            return already;
        }
        if (value instanceof StringValue text) {
            if (text.text().isEmpty()) {
                return raiseBadMakeArg(value, "char!");
            }
            return CharacterValue.of(text.text().codePointAt(0));
        }
        if (value instanceof BinaryValue octets) {
            return characterLeadingThe(octets);
        }
        if (value instanceof WordValue issue && issue.datatype() == Datatype.ISSUE) {
            return characterSpeltInHexBy(issue);
        }
        if (!(value instanceof IntegerValue || value instanceof DecimalValue)) {
            return raiseBadMakeArg(value, "char!");
        }
        return characterAt(Comparison.asDouble(value));
    }

    private static Value characterAt(double codepoint) {
        long asked = (long) codepoint;
        if (asked < 0 || asked > CharacterValue.MAXIMUM_CODEPOINT
                || isaLoneSurrogate(asked)) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR, IntegerValue.of(asked));
        }
        return CharacterValue.of((int) asked);
    }

    private static boolean isaLoneSurrogate(long asked) {
        return asked <= Character.MAX_VALUE && Character.isSurrogate((char) asked);
    }

    private static Value characterLeadingThe(BinaryValue octets) {
        byte[] bytes = bytesFromHere(octets);
        if (bytes.length == 0) {
            return raiseBadMakeArg(octets, "char!");
        }
        int lead = bytes[0] & 0xFF;
        if (lead <= 0x80) {
            return CharacterValue.of(lead);
        }
        int continuations = continuationBytesFollowing(lead);
        if (continuations == 0 || bytes.length <= continuations) {
            return raiseBadMakeArg(octets, "char!");
        }
        int codepoint = lead & (0x7F >> continuations);
        for (int at = 1; at <= continuations; at++) {
            int following = bytes[at] & 0xFF;
            if ((following & 0xC0) != 0x80) {
                return raiseBadMakeArg(octets, "char!");
            }
            codepoint = (codepoint << 6) | (following & 0x3F);
        }
        if (codepoint > Character.MAX_CODE_POINT) {
            return raiseBadMakeArg(octets, "char!");
        }
        return CharacterValue.of(codepoint);
    }

    private static int continuationBytesFollowing(int lead) {
        if ((lead & 0xE0) == 0xC0) {
            return 1;
        }
        if ((lead & 0xF0) == 0xE0) {
            return 2;
        }
        if ((lead & 0xF8) == 0xF0) {
            return 3;
        }
        return 0;
    }

    private static Value characterSpeltInHexBy(WordValue issue) {
        String spelling = issue.spelling();
        if (spelling.isEmpty() || spelling.length() > MOST_HEX_DIGITS_SCANNED) {
            return raiseBadMakeArg(issue, "char!");
        }
        long codepoint;
        try {
            codepoint = Long.parseLong(spelling, 16);
        } catch (NumberFormatException notHexadecimal) {
            return raiseBadMakeArg(issue, "char!");
        }
        if (codepoint < 0 || codepoint > Character.MAX_CODE_POINT) {
            return raiseBadMakeArg(issue, "char!");
        }
        return CharacterValue.of((int) codepoint);
    }

    private static final int MOST_HEX_DIGITS_SCANNED = 16;

    private static Value asPair(Value value) {
        return switch (value) {
            case PairValue pair -> pair;
            case IntegerValue whole -> PairValue.square(whole.magnitude());
            case DecimalValue quantity when quantity.datatype() != Datatype.PERCENT ->
                    PairValue.square(quantity.quantity());
            case StringValue text -> readPair(text.text());
            case BlockValue block when block.datatype() == Datatype.BLOCK ->
                    pairOf(block.remaining());
            default -> raiseBadMakeArg(value, "pair!");
        };
    }

    private static Value asMoney(Conversion asking, Value value) {
        return MoneyActions.withinTheDeciRange(switch (value) {
            case MoneyValue already -> already;
            case IntegerValue whole -> MoneyValue.of(BigDecimal.valueOf(whole.magnitude()));
            case DecimalValue quantity ->
                    MoneyValue.of(BigDecimal.valueOf(quantity.quantity()));
            case StringValue text -> readMoney(text.text());
            case BinaryValue bytes -> MoneyValue.fromBytes(bytesFromHere(bytes));
            case LogicValue truth -> asking.builds()
                    ? MoneyValue.of(truth.truth() ? BigDecimal.ONE : BigDecimal.ZERO)
                    : (MoneyValue) raiseBadMakeArg(value, "money!");
            default -> (MoneyValue) raiseBadMakeArg(value, "money!");
        });
    }

    private static MoneyValue readMoney(String text) {
        String written = qualifiedNumberIn(text, "a money", MOST_FRACTION_CHARACTERS);
        return amountWithoutTheCurrencyMark(written)
                .flatMap(Natives::numberRewrittenForTheJvm)
                .map(plain -> MoneyValue.of(new BigDecimal(plain)))
                .orElseGet(() -> (MoneyValue)
                        raiseBadMakeArg(StringValue.of(text), "money!"));
    }

    private static Optional<String> amountWithoutTheCurrencyMark(String written) {
        if (written.startsWith("$")) {
            String amount = written.substring(1);
            return amount.startsWith("-") || amount.startsWith("+")
                    ? Optional.empty()
                    : Optional.of(amount);
        }
        boolean signedThenMarked = written.length() > 1
                && (written.charAt(0) == '-' || written.charAt(0) == '+')
                && written.charAt(1) == '$';
        return Optional.of(signedThenMarked
                ? written.charAt(0) + written.substring(2)
                : written);
    }

    private static Value pairOf(List<Value> halves) {
        if (halves.size() != 2) {
            return raiseBadMakeArg(BlockValue.block(halves), "pair!");
        }
        return PairValue.of(Comparison.asDouble(halves.get(0)), Comparison.asDouble(halves.get(1)));
    }

    private static Value readPair(String text) {
        List<Value> read = Transcoder.transcode(text).values()
                .map(BlockValue::remaining)
                .orElse(List.of());
        if (read.size() != 1 || !(read.get(0) instanceof PairValue pair)) {
            return raiseBadMakeArg(StringValue.of(text), "pair!");
        }
        return pair;
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

    private static boolean isANumberButNotAPercentageWhichIsNoRoomAtAll(Value given) {
        return given instanceof IntegerValue
                || (given instanceof DecimalValue
                        && given.datatype() != Datatype.PERCENT);
    }

    private static Value mapMadeFrom(Value given) {
        if (isANumberButNotAPercentageWhichIsNoRoomAtAll(given)) {
            MapActions.refuseRoomForFewerThanNoPairs(given);
            refuseMoreRoomThanASeriesCounts(Datatype.MAP, given);
            return MapValue.empty();
        }
        List<Value> pairs = MapActions.pairsOffered(given);
        return pairs == null
                ? raiseBadMakeArg(given, "map!")
                : MapActions.madeFrom(given, pairs);
    }

    private static Value raiseHalfAnExpression(Value assigning) {
        throw Raised.of(EvaluationFailure.INVALID_ARG,
                Molder.mold(assigning) + " assigns, and there is nothing here to assign");
    }

    private static int asAnOctet(IntegerValue number) {
        long wanted = number.magnitude();
        if (wanted < 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        if (wanted > 255) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    wanted + " is not a byte: a binary holds 0 to 255");
        }
        return (int) wanted;
    }


    private static Value thePixelFoundIn(
            ImageValue picture, Value wanted, Set<String> refinements) {

        if (!ImageSeries.couldBeAPixel(wanted)) {
            return NoneValue.none();
        }
        int at = ImageSeries.positionOf(picture, wanted,
                refinements.contains("match"), refinements.contains("only"));
        if (at == 0) {
            return NoneValue.none();
        }
        return picture.atIndex(refinements.contains("tail") ? at + 1 : at);
    }

    private static Value theShapeOfTheRectangle(
            List<Value> arguments, Set<String> refinements) {

        return refinements.contains("part")
                ? argumentFor("part", List.of("part", "dup"), arguments, refinements, 2)
                : NoneValue.none();
    }

    private static long howManyTimesOver(
            List<Value> arguments, Set<String> refinements) {

        Value times = argumentFor("dup", List.of("part", "dup"),
                arguments, refinements, 2);
        return refinements.contains("dup") && times instanceof IntegerValue counted
                ? Math.max(0, counted.magnitude())
                : 1;
    }

    private static Value raiseBadMakeArg(Value value, String wanted) {
        throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                theDatatypeItselfRatherThanItsName(wanted), value);
    }

    private static Value theDatatypeItselfRatherThanItsName(String wanted) {
        return Datatype.named(wanted)
                .<Value>map(DatatypeValue::of)
                .orElseGet(() -> WordValue.of(wanted));
    }

    private static Value raiseWrongArgument(Value value, String nativeName, String wanted) {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                nativeName + " wanted a " + wanted + ", not a "
                        + value.datatype().literalSpelling());
    }

    private static boolean bothHalves(PairValue pair, DoublePredicate asked) {
        return asked.test(pair.x()) && asked.test(pair.y());
    }

    private static long roundedHalfUp(double half) {
        return (long) Math.floor(half + 0.5);
    }

    private static boolean isOdd(long whole) {
        return (whole & 1L) != 0L;
    }

    private static String qualifiedNumberIn(String text, String reading, int mostCharacters) {
        int start = 0;
        while (start < text.length() && isLexicalSpace(text.charAt(start))) {
            start++;
        }
        int past = start;
        while (past < text.length() && !isLexicalSpace(text.charAt(past))) {
            if (text.charAt(past) > MOST_LETTERS_ARE_ONE_BYTE) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS,
                        "\"" + text + "\" holds a character a number may not");
            }
            past++;
            if (past - start > mostCharacters) {
                throw Raised.of(EvaluationFailure.TOO_LONG,
                        "\"" + text + "\" is longer than a written number may be");
            }
        }
        if (past == start) {
            throw Raised.of(EvaluationFailure.TOO_SHORT,
                    "there is nothing in \"" + text + "\" to read as " + reading);
        }
        for (int after = past; after < text.length(); after++) {
            if (!isSpaceOrTab(text.charAt(after))) {
                throw Raised.of(EvaluationFailure.INVALID_CHARS,
                        "\"" + text + "\" has more than one value in it");
            }
        }
        return text.substring(start, past);
    }

    private static final char MOST_LETTERS_ARE_ONE_BYTE = 127;

    private static boolean isLexicalSpace(char letter) {
        return (letter <= ' ' || letter == MOST_LETTERS_ARE_ONE_BYTE)
                && letter != '\n' && letter != '\r';
    }

    private static boolean isSpaceOrTab(char letter) {
        return letter == ' ' || letter == '\t';
    }

    private static final int MOST_WHOLE_NUMBER_CHARACTERS = 25;

    private static final int MOST_FRACTION_CHARACTERS = 24;

    private static final Pattern WRITTEN_DECIMAL = Pattern.compile(
            "[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]*)?");

    private static final Pattern EMPTY_EXPONENT = Pattern.compile("[eE][+-]?$");

    private static OptionalDouble decimalScannedFrom(String written, boolean percentAllowed) {
        String body = written;
        if (body.endsWith("%")) {
            if (!percentAllowed) {
                return OptionalDouble.empty();
            }
            body = body.substring(0, body.length() - 1);
        }
        OptionalDouble endless = endlessNumberIn(body.replace("'", ""));
        if (endless.isPresent()) {
            return endless;
        }
        return numberRewrittenForTheJvm(body)
                .map(plain -> OptionalDouble.of(Double.parseDouble(plain)))
                .orElseGet(OptionalDouble::empty);
    }

    private static Optional<String> numberRewrittenForTheJvm(String written) {
        String body = written.replace("'", "").replaceFirst(",", ".");
        return WRITTEN_DECIMAL.matcher(body).matches()
                ? Optional.of(EMPTY_EXPONENT.matcher(body).replaceFirst(""))
                : Optional.empty();
    }

    private static OptionalDouble endlessNumberIn(String body) {
        int hash = body.indexOf('#');
        if (hash < 0) {
            return OptionalDouble.empty();
        }
        boolean negative = body.charAt(0) == '-';
        String afterTheHash = body.substring(hash + 1);
        if (afterTheHash.equalsIgnoreCase("INF")) {
            return OptionalDouble.of(negative
                    ? Double.NEGATIVE_INFINITY
                    : Double.POSITIVE_INFINITY);
        }
        return afterTheHash.equalsIgnoreCase("NAN")
                ? OptionalDouble.of(Double.NaN)
                : OptionalDouble.empty();
    }

    private static Value parseInteger(String text) {
        String withoutSeparators = qualifiedNumberIn(
                text, "an integer", MOST_WHOLE_NUMBER_CHARACTERS).replace("'", "");
        try {
            return IntegerValue.of(Long.parseLong(withoutSeparators));
        } catch (NumberFormatException notAWholeNumber) {
            return truncatedDecimal(withoutSeparators, text);
        }
    }

    private static Value truncatedDecimal(String candidate, String original) {
        if (candidate.indexOf('.') < 0) {
            throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                    "cannot read \"" + original + "\" as an integer");
        }
        try {
            double asNumber = Double.parseDouble(candidate);
            if (!(Math.abs(asNumber) < TOO_LARGE_FOR_A_WHOLE_NUMBER)) {
                throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                        "\"" + original + "\" is outside the range of a whole number");
            }
            return IntegerValue.of((long) asNumber);
        } catch (NumberFormatException notANumberEither) {
            throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                    "cannot read \"" + original + "\" as an integer");
        }
    }

    private static final double TOO_LARGE_FOR_A_WHOLE_NUMBER = 9.223372036854776E18;

    private static long roundedWholeOf(Value value, String nativeName) {
        if (value instanceof MoneyValue amount) {
            return amount.amount().longValue();
        }
        if (value instanceof CharacterValue letter) {
            return letter.codepoint();
        }
        if (value instanceof TimeValue time) {
            return time.nanoseconds() / 1_000_000_000L;
        }
        if (value instanceof DateValue date) {
            return date.day();
        }
        if (value instanceof DecimalValue fractional) {
            double magnitude = fractional.quantity();
            if (Math.abs(magnitude) >= 9007199254740992.0) {
                return 0;
            }
            return (long) (magnitude < 0
                    ? -Math.floor(-magnitude + 0.5)
                    : Math.floor(magnitude + 0.5));
        }
        return wholeNumberOf(value, nativeName);
    }

    private static long wholeNumberOf(Value value, String nativeName) {
        if (value instanceof IntegerValue whole) {
            return whole.magnitude();
        }
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                nativeName + " takes a whole number, not "
                        + value.datatype().literalSpelling());
    }

    private static Optional<Long> howManyWanted(
            List<Value> arguments, Set<String> refinements, int where) {
        return howManyWanted(NoneValue.none(), arguments, refinements, where);
    }

    private static SeriesValue theRunReachingBackIfNegative(
            SeriesValue series, long wanted) {

        if (wanted >= 0) {
            return series;
        }
        int reaching = (int) Math.min(-wanted, series.index() - 1L);
        return series.atIndex(series.index() - reaching);
    }

    private static Optional<Long> howManyWanted(
            Value source, List<Value> arguments, Set<String> refinements, int where) {
        Value count = argumentFor(
                "part", List.of("part", "dup"), arguments, refinements, where);
        if (count instanceof IntegerValue wanted) {
            long magnitude = wanted.magnitude();
            if (magnitude > Integer.MAX_VALUE || magnitude < Integer.MIN_VALUE) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Long.toString(magnitude));
            }
            return Optional.of(magnitude);
        }
        if (count instanceof DecimalValue fraction
                && fraction.datatype() != Datatype.PERCENT) {
            return Optional.of((long) Comparison.asDouble(fraction));
        }
        if (count instanceof DecimalValue || count instanceof PairValue) {
            throw Raised.of(EvaluationFailure.INVALID_PART, Molder.mold(count));
        }
        if (count instanceof SeriesValue upTo) {
            if (!(source instanceof SeriesValue from)
                    || from.datatype() != upTo.datatype()
                    || !from.sharesStorageWith(upTo)) {
                throw Raised.of(EvaluationFailure.INVALID_PART, "part");
            }
            return Optional.of((long) (upTo.index() - from.index()));
        }
        return Optional.empty();
    }

    private static List<Value> partOf(
            BlockValue block, List<Value> arguments, Set<String> refinements) {

        return howManyWanted(block, arguments, refinements, 2)
                .map(count -> {
                    List<Value> whole = block.head().remaining();
                    int here = block.index() - 1;
                    int from = count >= 0 ? here : (int) Math.max(0, here + count);
                    int to = count >= 0
                            ? (int) Math.min(whole.size(), here + count)
                            : here;
                    return whole.subList(Math.min(from, whole.size()),
                            Math.max(Math.min(to, whole.size()), Math.min(from, whole.size())));
                })
                .orElseGet(block::remaining);
    }

    private static void refuseASizeItCannotWrite(long width) {
        if (width <= 0 || width > 0xFFFFFFFFL) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, IntegerValue.of(width));
        }
    }

    private static String hexOfEachSegment(TupleValue tuple, OptionalLong width) {
        StringBuilder hex = new StringBuilder();
        for (int segment : tuple.segments()) {
            hex.append("%02X".formatted(segment));
        }
        for (int padded = tuple.segmentCount();
                padded < TupleValue.MINIMUM_SHOWN_SEGMENTS; padded++) {
            hex.append("00");
        }
        if (width.isEmpty()) {
            return hex.toString();
        }
        long kept = Math.min(width.getAsLong(), 2L * tuple.segmentCount());
        return hex.substring(0, (int) Math.min(kept, hex.length()));
    }

    private static String hexSizedToItsMagnitude(int codepoint, OptionalLong width) {
        int digits = codepoint <= 0xFF ? 2
                : codepoint <= 0xFFFF ? 4
                : codepoint <= 0xFFFFFF ? 6
                : 8;
        return trimmedToWidth("%016X".formatted((long) codepoint),
                width.isPresent() ? atMostSixteen(width.getAsLong()) : digits);
    }

    private static String hexSixteenWide(long magnitude, OptionalLong width) {
        String hex = "%016X".formatted(magnitude);
        return width.isEmpty()
                ? hex
                : trimmedToWidth(hex, atMostSixteen(width.getAsLong()));
    }

    private static int atMostSixteen(long width) {
        return (int) Math.min(width, 16L);
    }

    private static String trimmedToWidth(String hex, int width) {
        return hex.substring(Math.max(0, hex.length() - width));
    }

    private static byte[] boundedByAnyPart(
            byte[] octets, List<Value> arguments, Set<String> refinements) {
        return howManyWanted(arguments.getFirst(), arguments, refinements, 2)
                .map(count -> Arrays.copyOf(octets,
                        (int) Math.max(0, Math.min(count, octets.length))))
                .orElse(octets);
    }

    private static byte[] theUnitsAskedFor(
            Value value, List<Value> arguments, Set<String> refinements) {

        Optional<Long> asked = howManyWanted(value, arguments, refinements, 2);
        if (asked.isPresent() && asked.get() < 0) {
            return theUnitsBehind(value, -asked.get());
        }
        int howMany = asked.map(count -> (int) Math.max(0, count))
                .orElse(SeriesContents.EVERY_ONE);
        return toBytes(SeriesContents.octetsContributedBy(value, howMany));
    }

    private static byte[] theUnitsBehind(Value value, long count) {
        if (!(value instanceof SeriesValue positioned)) {
            return new byte[0];
        }
        int reachedBack = (int) Math.min(count, positioned.index() - 1);
        return toBytes(SeriesContents.octetsContributedBy(
                positioned.atIndex(positioned.index() - reachedBack), reachedBack));
    }

    private static byte[] toBytes(int[] octets) {
        byte[] bytes = new byte[octets.length];
        for (int at = 0; at < octets.length; at++) {
            bytes[at] = (byte) octets[at];
        }
        return bytes;
    }

    private static String boundedTextByAnyPart(
            String text, List<Value> arguments, Set<String> refinements) {
        Optional<Long> asked =
                howManyWanted(arguments.getFirst(), arguments, refinements, 2);
        if (asked.isPresent() && asked.get() < 0) {
            return theTextBehind(arguments.getFirst(), -asked.get());
        }
        return asked.map(count -> text.substring(0,
                        (int) Math.max(0, Math.min(count, text.length()))))
                .orElse(text);
    }

    private static String theTextBehind(Value value, long count) {
        if (!(value instanceof SeriesValue positioned)) {
            return "";
        }
        int reachedBack = (int) Math.min(count, positioned.index() - 1);
        String whole = textOf(positioned.atIndex(positioned.index() - reachedBack));
        return whole.substring(0, Math.min(reachedBack, whole.length()));
    }

    private static boolean isExactlyABlock(Value value) {
        return value instanceof BlockValue && value.datatype() == Datatype.BLOCK;
    }

    private static Value unbound(Value value, boolean deeply) {
        if (value instanceof WordValue word) {
            return WordValue.of(word.spelling(), word.datatype());
        }
        if (value instanceof BlockValue block) {
            loosenInPlace(block, deeply);
            return block;
        }
        return value;
    }

    private static void loosenInPlace(BlockValue block, boolean deeply) {
        for (int at = block.index(); at <= block.storageLength(); at++) {
            Value item = block.storage().at(at);
            if (item instanceof WordValue word) {
                block.storage().rebindAt(at,
                        WordValue.of(word.spelling(), word.datatype()));
            } else if (deeply && item instanceof BlockValue nested) {
                loosenInPlace(nested, true);
            }
        }
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
            Evaluator.refuseToWriteTheNameAnObjectAnswersToItselfBy(field);
        }
    }

    private static String runTogether(Value value) {
        if (value.datatype().isAnyPath() && value instanceof BlockValue path) {
            return path.remaining().stream()
                    .map(Natives::runTogether)
                    .collect(Collectors.joining("/"));
        }
        if (value instanceof BlockValue block) {
            return block.remaining().stream()
                    .map(Natives::runTogether)
                    .collect(Collectors.joining());
        }
        return Molder.form(value);
    }

    private static String textForAString(Value value) {
        return value instanceof StringValue already
                ? already.text()
                : runTogether(value);
    }

    private void definePorts() {
        define("read", List.of(
                        Parameter.required("source",
                                Set.of(Datatype.FILE, Datatype.PORT, Datatype.URL,
                                        Datatype.BLOCK, Datatype.WORD)),
                        Parameter.belongingTo("part", "length",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT)),
                        Parameter.belongingTo("seek", "index",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT))),
                Set.of("part", "seek", "string", "binary", "lines", "all"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof PortValue port) {
                        refuseAPortWhoseSpecIsNotAnObject(port);
                        return isAFilePort(port)
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
                    requireService(HostService.FILES);
                    return throughPort(() -> FileReading
                            .asAskedForAt(behindTheUrl.orElseGet(() ->
                                    ((StringValue) arguments.getFirst()).text()),
                                    arguments, refinements)
                            .answerThrough(evaluator.files()));
                });

        define("write", List.of(
                        Parameter.required("destination",
                                Set.of(Datatype.FILE, Datatype.PORT, Datatype.URL,
                                        Datatype.BLOCK, Datatype.WORD)),
                        Parameter.required("data"),
                        Parameter.belongingTo("part", "length",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT)),
                        Parameter.belongingTo("seek", "index",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT)),
                        Parameter.belongingTo("allow", "access", Set.of(Datatype.BLOCK))),
                Set.of("part", "seek", "append", "allow", "lines", "binary", "all"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.getFirst() instanceof PortValue port) {
                        refuseAPortWhoseSpecIsNotAnObject(port);
                        return writeToPort(port, arguments.get(1), evaluator,
                                arguments, refinements);
                    }
                    if (routesToAScheme(arguments.getFirst())) {
                        return writeToPort(
                                portOpenedFor(arguments.getFirst(), evaluator, context),
                                arguments.get(1), evaluator, arguments, refinements);
                    }
                    requireService(HostService.FILES);
                    return throughPort(() -> {
                        FileWriting.asAskedFor(arguments, refinements)
                                .performThrough(evaluator.files());
                        return arguments.getFirst();
                    });
                });

        define("to-local-file", List.of(Parameter.required("path",
                        Set.of(Datatype.FILE, Datatype.STRING))),
                Set.of("full"),
                (arguments, evaluator, context, refinements) -> {
                    String path = ((StringValue) arguments.getFirst()).text();
                    boolean resolvingDots = refinements.contains("full");
                    String from = "";
                    if (resolvingDots && !path.startsWith("/")) {
                        requireService(HostService.WORKING_DIRECTORY);
                        from = ((StringValue) throughPort(() -> StringValue.of(
                                evaluator.files().workingDirectory()))).text();
                    }
                    return StringValue.of(
                            localPathOf(from + path, resolvingDots, localFileSeparator));
                });

        define("to-rebol-file", List.of(Parameter.required("path",
                        Set.of(Datatype.FILE, Datatype.STRING))),
                (arguments, evaluator, context) -> StringValue.of(
                        oneSlashPerRunOfSeparators(
                                ((StringValue) arguments.getFirst()).text()),
                        Datatype.FILE));

        define("call", List.of(
                        Parameter.required("command",
                                Set.of(Datatype.STRING, Datatype.BLOCK, Datatype.FILE,
                                        Datatype.EMAIL, Datatype.REF, Datatype.TAG,
                                        Datatype.URL)),
                        Parameter.belongingTo("input", "in",
                                Set.of(Datatype.STRING, Datatype.BINARY,
                                        Datatype.FILE, Datatype.NONE)),
                        Parameter.belongingTo("output", "out",
                                Set.of(Datatype.STRING, Datatype.BINARY,
                                        Datatype.FILE, Datatype.NONE)),
                        Parameter.belongingTo("error", "err",
                                Set.of(Datatype.STRING, Datatype.BINARY,
                                        Datatype.FILE, Datatype.NONE))),
                Set.of("wait", "console", "shell", "info", "input", "output", "error"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.PROCESSES);
                    ProgramCalling calling = ProgramCalling.asAskedFor(
                            arguments, refinements, evaluator, context);
                    return throughPort(() ->
                            calling.answerThrough(evaluator.processes(), evaluator));
                });

        define("input", List.of(), Set.of("hide"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.CONSOLE);
                    return throughPort(() -> {
                        String line = refinements.contains("hide")
                                ? evaluator.console().readHiddenLine()
                                : evaluator.console().readLine();
                        return line == null ? NoneValue.none() : StringValue.of(line);
                    });
                });

        define("ask", List.of(Parameter.required("question", Typeset.SERIES.members())),
                Set.of("hide"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.CONSOLE);
                    return throughPort(() -> {
                        evaluator.output().write(
                                ((StringValue) arguments.getFirst()).text());
                        String line = refinements.contains("hide")
                                ? evaluator.console().readHiddenLine()
                                : evaluator.console().readLine();
                        return line == null ? NoneValue.none() : StringValue.of(line);
                    });
                });

        define("get-env", List.of(Parameter.required("name",
                        Set.of(Datatype.STRING, Datatype.WORD, Datatype.LIT_WORD))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.ENVIRONMENT);
                    return throughPort(() -> {
                        String held = evaluator.environment()
                                .valueOf(environmentNameIn(arguments.getFirst()));
                        return held == null ? NoneValue.none() : StringValue.of(held);
                    });
                });

        define("list-env", List.of(),
                (arguments, evaluator, context) -> {
                    requireService(HostService.ENVIRONMENT);
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
                                Set.of(Datatype.STRING, Datatype.WORD, Datatype.LIT_WORD)),
                        Parameter.required("value",
                                Set.of(Datatype.STRING, Datatype.NONE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.ENVIRONMENT);
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
                    requireService(HostService.WORKING_DIRECTORY);
                    return throughPort(() -> StringValue.of(
                            evaluator.files().workingDirectory(), Datatype.FILE));
                });

        define("change-dir", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.WORKING_DIRECTORY);
                    String asked = ((StringValue) arguments.getFirst()).text();
                    return throughPort(() -> {
                        evaluator.files().changeDirectory(asked);
                        sayWhereTheInterpreterIsStanding(evaluator);
                        return StringValue.of(
                                evaluator.files().workingDirectory(), Datatype.FILE);
                    });
                });

        define("make-dir", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                Set.of("deep"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> {
                        evaluator.files().makeDirectory(
                                ((StringValue) arguments.getFirst()).text(),
                                refinements.contains("deep"));
                        return arguments.getFirst();
                    });
                });

        define("create", List.of(Parameter.required("path",
                        Set.of(Datatype.FILE, Datatype.URL))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
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
                        Set.of(Datatype.FILE, Datatype.URL))),
                (arguments, evaluator, context) -> {
                    Value target = arguments.getFirst();
                    Optional<String> behindTheUrl =
                            theFileNamedByAUrl(target, evaluator, context);
                    if (behindTheUrl.isEmpty() && target.datatype() != Datatype.FILE) {
                        throw schemeRefusal("deletes through", target);
                    }
                    requireService(HostService.FILES);
                    String path = behindTheUrl.orElseGet(
                            () -> ((StringValue) target).text());
                    Value itsPort = evaluator.applyFunction(
                            systemInternalFunction(context, "make-port*"), List.of(target));
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
                        Parameter.required("from", Set.of(Datatype.FILE, Datatype.BLOCK,
                                Datatype.PORT, Datatype.URL)),
                        Parameter.required("to", Set.of(Datatype.FILE, Datatype.BLOCK,
                                Datatype.PORT, Datatype.URL))),
                (arguments, evaluator, context) -> {
                    for (Value end : List.of(arguments.getFirst(), arguments.get(1))) {
                        if (end.datatype() != Datatype.FILE) {
                            throw schemeRefusal("renames", end);
                        }
                    }
                    requireService(HostService.FILES);
                    return movedOrRefusedByTheName(evaluator, arguments);
                });

        define("read-dir", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> BlockValue.block(
                            evaluator.files().namesIn(
                                    ((StringValue) arguments.getFirst()).text()).stream()
                                    .<Value>map(name -> StringValue.of(name, Datatype.FILE))
                                    .toList()));
                });

        define("exists?", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> LogicValue.of(
                            evaluator.files().exists(
                                    ((StringValue) arguments.get(0)).text())));
                });

        define("dir?", List.of(Parameter.required("target",
                        Set.of(Datatype.FILE, Datatype.URL, Datatype.NONE))),
                Set.of("check"),
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
                        Parameter.required("scheme", Set.of(Datatype.OBJECT))),
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
                        Parameter.belongingTo("allow", "access", Set.of(Datatype.BLOCK))),
                Set.of("new", "read", "write", "seek", "allow"),
                (arguments, evaluator, context, refinements) -> {
                    Value built = arguments.getFirst() instanceof PortValue already
                            ? already
                            : evaluator.applyFunction(
                                    systemInternalFunction(context, "make-port*"),
                                    List.of(arguments.getFirst()));
                    if (!(built instanceof PortValue port)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "nothing knows how to open that");
                    }
                    refuseAnActorThatIsNeitherAWordNorAnObject(port);
                    Optional<ObjectValue> written = theActorWrittenInRebol(port);
                    if (written.isPresent()) {
                        return askTheActor(port, written.get(), "open",
                                List.of(port), refinements, evaluator);
                    }
                    requireServiceForScheme(port.schemeName());
                    if (port.schemeName().equals("tcp")) {
                        connectTheTcpPort(port, evaluator);
                    }
                    if (port.schemeName().equals("crypt")) {
                        startTheCipherBehindBlankingTheKeyInTheSpec(port);
                    }
                    if (port.schemeName().equals("checksum")) {
                        ChecksumPort.startEvenOnAnAlreadyOpenPort(
                                port, ChecksumPort.methodOf(port));
                    }
                    if (isAFilePort(port)) {
                        openTheFileBehind(port, evaluator, refinements);
                    }
                    theEventQueueOf(port);
                    markOpenWhateverTheActorLeftInState(port);
                    return port;
                });

        define("update", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("update", arguments, Set.of(), evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    PortValue port = (PortValue) arguments.getFirst();
                    if (port.schemeName().equals("checksum")) {
                        ChecksumPort.digestSoFarLeftInTheDataFieldAsWell(port);
                        return port;
                    }
                    if (port.schemeName().equals("crypt")) {
                        refuseAClosedCipherPort(port);
                        CryptPort.update(port);
                        return port;
                    }
                    return NoneValue.none();
                });

        define("flush", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    evaluator.output().flush();
                    return arguments.getFirst();
                });

        define("open?", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("open?", arguments, Set.of(), evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    PortValue port = (PortValue) arguments.getFirst();
                    if (port.schemeName().equals("crypt")) {
                        refuseAClosedCipherPort(port);
                    }
                    return LogicValue.of(port.isOpen());
                });

        define("close", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("close", arguments, Set.of(), evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    PortValue port = (PortValue) arguments.getFirst();
                    if (port.schemeName().equals("crypt")) {
                        refuseAClosedCipherPort(port);
                        CryptPort.stop(port);
                    }
                    handBackTheConnectionBehind(port);
                    port.markOpen(false);
                    if (port.schemeName().equals("checksum")) {
                        ChecksumPort.stop(port);
                    }
                    return port;
                });

        define("modify", List.of(
                        Parameter.required("target", Set.of(Datatype.PORT, Datatype.FILE)),
                        Parameter.required("field", Set.of(Datatype.WORD, Datatype.NONE)),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    Optional<Value> itsOwn =
                            theRebolActorsAnswer("modify", arguments, Set.of(), evaluator);
                    if (itsOwn.isPresent()) {
                        return itsOwn.get();
                    }
                    if (arguments.getFirst() instanceof PortValue aPort
                            && aPort.schemeName().equals("crypt")) {
                        refuseAClosedCipherPort(aPort);
                        if (!(arguments.get(1) instanceof WordValue setting)) {
                            return aPort;
                        }
                        return CryptPort.modify(aPort, setting.canonical(),
                                arguments.get(2));
                    }
                    if (!(arguments.get(1) instanceof WordValue mode)
                            || !CONSOLE_MODES.contains(mode.canonical())) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "a port mode is echo, line or error");
                    }
                    if (!(arguments.get(2) instanceof LogicValue)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "a port mode is set to true or false");
                    }
                    if (arguments.getFirst() instanceof PortValue port) {
                        port.setField(mode.canonical(), arguments.get(2));
                    }
                    return arguments.get(2);
                });

        define("browse", List.of(Parameter.required("url",
                        Set.of(Datatype.URL, Datatype.FILE, Datatype.NONE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.WINDOWS);
                    return throughWindow(() -> {
                        if (!(arguments.getFirst() instanceof StringValue target)) {
                            return NoneValue.none();
                        }
                        evaluator.windows().browse(target.text());
                        return NoneValue.none();
                    });
                });

        define("request-file", List.of(
                        Parameter.belongingTo("file", "name", Set.of(Datatype.FILE)),
                        Parameter.belongingTo("title", "text", Set.of(Datatype.STRING)),
                        Parameter.belongingTo("filter", "list", Set.of(Datatype.BLOCK))),
                Set.of("save", "multi", "file", "title", "filter"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.WINDOWS);
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
                        Parameter.belongingTo("title", "text", Set.of(Datatype.STRING)),
                        Parameter.belongingTo("dir", "name", Set.of(Datatype.FILE))),
                Set.of("title", "dir", "keep"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.WINDOWS);
                    return throughWindow(() -> evaluator.windows().chooseDirectory(
                                    textOfArgument(arguments, refinements,
                                            List.of("title", "dir"), "dir"),
                                    textOfArgument(arguments, refinements,
                                            List.of("title", "dir"), "title"))
                            .<Value>map(where -> StringValue.of(where, Datatype.FILE))
                            .orElseGet(NoneValue::none));
                });

        define("request-color", List.of(
                        Parameter.belongingTo("default", "color", Set.of(Datatype.TUPLE))),
                Set.of("default"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.WINDOWS);
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
                    requireService(HostService.WINDOWS);
                    return throughWindow(() -> evaluator.windows()
                            .askForPassword()
                            .<Value>map(StringValue::of)
                            .orElseGet(NoneValue::none));
                });

        define("query", List.of(
                        Parameter.required("target", Set.of(Datatype.FILE, Datatype.DATE,
                                Datatype.HANDLE, Datatype.PORT, Datatype.URL,
                                Datatype.BLOCK, Datatype.WORD, Datatype.VECTOR)),
                        Parameter.required("field",
                                Set.of(Datatype.WORD, Datatype.BLOCK,
                                        Datatype.NONE, Datatype.DATATYPE))),
                Set.of("mode"),
                (arguments, evaluator, context, refinements) -> {
                    Optional<Value> itsOwn = theRebolActorsAnswer(
                            "query", arguments, refinements, evaluator);
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
                                DateParts.partNames(),
                                part -> DateParts.of(date, WordValue.of(part)));
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
                    if (target instanceof PortValue openFile && isAFilePort(openFile)) {
                        requireService(HostService.FILES);
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
                    requireService(HostService.FILES);
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
                        systemInternalFunction(context, "make-port*"), List.of(target));
        Value described = built instanceof PortValue port
                ? pathInto(port.context(), "scheme", "info")
                : NoneValue.none();
        if (!(described instanceof ObjectValue itsInfo)) {
            return BlockValue.block(List.of());
        }
        return BlockValue.block(itsInfo.context().slots().stream()
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
                resolved = slotOf(word).value();
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

    private Value readFromPort(PortValue port, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {

        Optional<Value> itsOwn = theRebolActorsAnswer(
                "read", withThePortInFront(port, arguments), refinements, evaluator);
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> lineReadFromTheConsole(evaluator);
            case "bundled" -> theSourceOfABundledModule(port, evaluator);
            case "tcp" -> bytesReadFromTheConnection(port, evaluator);
            case "dns" -> addressesOfTheNameThePortNames(port, evaluator);
            case "checksum" ->
                    ChecksumPort.digestSoFarLeftInTheDataFieldAsWell(port);
            case "crypt" -> {
                refuseAClosedCipherPort(port);
                yield CryptPort.read(port);
            }
            default -> throw Raised.of(EvaluationFailure.NO_SERVICE,
                    "nothing here reads the " + port.schemeName() + " scheme");
        };
    }

    private Value lineReadFromTheConsole(Evaluator evaluator) {
        requireService(HostService.CONSOLE);
        return throughPort(() -> {
            String line = evaluator.console().readLine();
            return line == null ? NoneValue.none() : StringValue.of(line);
        });
    }

    private Value theSourceOfABundledModule(PortValue port, Evaluator evaluator) {
        return theNameABundledUrlAsksFor(port)
                .flatMap(name -> evaluator.bundledModules().sourceOf(name))
                .map(source -> (Value) BinaryValue.of(unsignedOctets(source)))
                .orElseThrow(() -> Raised.of(EvaluationFailure.CANNOT_OPEN,
                        port.fieldNamed("spec") instanceof ObjectValue spec
                                ? valueInSpec(spec, "ref")
                                : NoneValue.none()));
    }

    private static Optional<String> theNameABundledUrlAsksFor(PortValue port) {
        if (!(port.fieldNamed("spec") instanceof ObjectValue spec)
                || !(valueInSpec(spec, "host") instanceof StringValue host)
                || !(valueInSpec(spec, "path") instanceof NoneValue)
                || !(valueInSpec(spec, "target") instanceof NoneValue)) {
            return Optional.empty();
        }
        return host.text().isEmpty() ? Optional.empty() : Optional.of(host.text());
    }

    private Value bytesReadFromTheConnection(PortValue port, Evaluator evaluator) {
        requireService(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughNetwork(() -> {
            BinaryValue arrived = BinaryValue.of(unsignedOctets(connection.read()));
            addToThePortsData(port, arrived);
            queueWhatHappenedTo(port,
                    arrived.lengthFromHere() == 0 ? "close" : "read", evaluator);
            return arrived;
        });
    }

    private static void addToThePortsData(PortValue port, BinaryValue arrived) {
        if (!(port.fieldNamed("data") instanceof BinaryValue held)) {
            port.setField("data", arrived);
            return;
        }
        for (int at = arrived.index(); at <= arrived.storageLength(); at++) {
            held.storage().append(arrived.storage().at(at));
        }
    }

    private Value addressesOfTheNameThePortNames(PortValue port, Evaluator evaluator) {
        requireService(HostService.NETWORK);
        String hostName = hostNamedBy(port);
        return throughNetwork(() -> {
            List<String> found = evaluator.network().addressesFor(hostName);
            return found.isEmpty()
                    ? NoneValue.none()
                    : BlockValue.block(found.stream()
                            .<Value>map(StringValue::of).toList());
        });
    }

    private static Context contextOf(Value value) {
        return switch (value) {
            case ObjectValue object -> object.context();
            case PortValue port -> port.context();
            case ModuleValue module -> module.context();
            case ErrorValue raised -> contextOfError(raised);
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "in wanted an object, an error, a port or a block, not "
                            + value.datatype().literalSpelling());
        };
    }

    private static Context contextOfError(ErrorValue raised) {
        Context fields = Context.root();
        for (String name : ErrorValue.FIELDS) {
            raised.field(name).ifPresent(value -> fields.set(name, value));
        }
        return fields;
    }

    private static Value firstHolderIn(
            BlockValue searched, Value wanted, Evaluator evaluator, Context context) {

        if (!(wanted instanceof WordValue word) || wanted instanceof BlockValue) {
            return raiseWrongArgument(wanted, "in", "word");
        }
        for (Value item : searched.remaining()) {
            Value resolved = item instanceof WordValue bound && bound.isBound()
                    ? evaluator.evaluateOrRaise(BlockValue.block(List.of(bound)), context)
                    : item;
            if (resolved instanceof ObjectValue object
                    && object.context().holds(word.canonical())) {
                return word.boundTo(object.context());
            }
        }
        return NoneValue.none();
    }

    private static Value assertedTypes(
            BlockValue pairs, Evaluator evaluator, Context context) {

        List<Value> items = pairs.remaining();
        for (int at = 0; at < items.size(); at += 2) {
            Value subject = items.get(at);
            Value held = switch (subject) {
                case WordValue word when word.datatype() == Datatype.WORD ->
                        evaluator.evaluateOrRaise(
                                BlockValue.block(List.of(subject)), context);
                case BlockValue path when path.datatype() == Datatype.PATH ->
                        evaluator.evaluateOrRaise(
                                BlockValue.block(List.of(subject)), context);
                default -> {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, subject);
                }
            };
            if (at + 1 >= items.size()) {
                throw Raised.of(EvaluationFailure.MISSING_ARG);
            }
            if (!isOfType(held, items.get(at + 1), context)) {
                throw Raised.of(EvaluationFailure.WRONG_TYPE, subject);
            }
        }
        return LogicValue.of(true);
    }

    private static Value everyConditionHeld(
            BlockValue conditions, Evaluator evaluator, Context context) {

        BlockValue at = conditions;
        while (!at.atTail()) {
            Evaluator.Step step = evaluator.evaluateNextOrRaise(at, context);
            at = at.atIndex(step.nextIndex());
            if (!step.value().isTruthy()) {
                throw Raised.of(EvaluationFailure.ASSERT_FAILED,
                        BlockValue.block(conditions.remaining()));
            }
        }
        return LogicValue.of(true);
    }

    private static boolean isOfType(Value held, Value type, Context context) {
        return switch (type) {
            case DatatypeValue wanted -> held.datatype() == wanted.represents();
            case TypesetValue set -> set.holds(held.datatype());
            case WordValue word -> {
                Value resolved = context.knows(word.canonical())
                        ? context.slotFor(word.canonical()).value()
                        : NoneValue.none();
                yield resolved != type && isOfType(held, resolved, context);
            }
            case BlockValue any -> any.remaining().stream()
                    .anyMatch(one -> isOfType(held, one, context));
            default -> raiseWrongArgumentBoolean(type);
        };
    }

    private static boolean raiseWrongArgumentBoolean(Value type) {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                "assert/type wants a datatype, not "
                        + type.datatype().literalSpelling());
    }

    private static final Set<Datatype> A_REFINEMENTS_SLOT =
            EnumSet.of(Datatype.NONE, Datatype.LOGIC);

    private static Value typesetsOf(
            List<Parameter> parameters, Set<String> refinementsApart) {
        List<Value> types = new ArrayList<>();
        Set<String> woven = new java.util.LinkedHashSet<>();
        for (Parameter parameter : parameters) {
            parameter.owningRefinement().ifPresent(owner -> {
                if (refinementsApart.contains(owner) && woven.add(owner)) {
                    types.add(TypesetValue.of(A_REFINEMENTS_SLOT));
                }
            });
            types.add(parameter.kind() == ParameterKind.REFINEMENT
                    ? TypesetValue.of(A_REFINEMENTS_SLOT)
                    : TypesetValue.of(parameter.acceptedTypes().isEmpty()
                            ? Typeset.ANY_TYPE.members()
                            : parameter.acceptedTypes()));
        }
        for (String leftover : refinementsApart) {
            if (!woven.contains(leftover)) {
                types.add(TypesetValue.of(A_REFINEMENTS_SLOT));
            }
        }
        return BlockValue.block(types);
    }

    private static Value specBlockOf(List<Parameter> parameters) {
        List<Value> spec = new ArrayList<>();
        for (Parameter parameter : parameters) {
            switch (parameter.kind()) {
                case REFINEMENT -> spec.add(
                        WordValue.of(parameter.name(), Datatype.REFINEMENT));
                case HARD_QUOTED -> spec.add(
                        WordValue.of(parameter.name(), Datatype.GET_WORD));
                case SOFT_QUOTED -> spec.add(
                        WordValue.of(parameter.name(), Datatype.LIT_WORD));
                case RETURN_TYPE -> spec.add(
                        WordValue.of("return", Datatype.SET_WORD));
                default -> spec.add(WordValue.of(parameter.name()));
            }
            if (!parameter.acceptedTypes().isEmpty()
                    && !parameter.acceptedTypes().equals(Typeset.ANY_TYPE.members())) {
                spec.add(BlockValue.block(parameter.acceptedTypes().stream()
                        .sorted(java.util.Comparator.comparing(Datatype::spelling))
                        .<Value>map(type -> WordValue.of(type.literalSpelling()))
                        .toList()));
            }
        }
        return BlockValue.block(spec);
    }

    private static Context boundContextOf(WordValue word) {
        return word.isBound() ? word.binding() : null;
    }

    private static byte[] withSurrogatePairsJoined(byte[] bytes) {
        byte[] joined = new byte[bytes.length];
        int written = 0;
        int at = 0;
        while (at < bytes.length) {
            int high = surrogateAt(bytes, at, 0xA0);
            int low = high < 0 ? -1 : surrogateAt(bytes, at + 3, 0xB0);
            if (low < 0) {
                joined[written] = bytes[at];
                written++;
                at++;
                continue;
            }
            written = fourBytesOf(joined, written,
                    0x10000 + ((high - 0xD800) << 10) + (low - 0xDC00));
            at += 6;
        }
        return Arrays.copyOf(joined, written);
    }

    private static int surrogateAt(byte[] bytes, int at, int leadingHalf) {
        if (at + 2 >= bytes.length || (bytes[at] & 0xFF) != 0xED) {
            return -1;
        }
        int second = bytes[at + 1] & 0xFF;
        int third = bytes[at + 2] & 0xFF;
        if (second < leadingHalf || second >= leadingHalf + 0x10
                || third < 0x80 || third > 0xBF) {
            return -1;
        }
        return 0xD000 | (second & 0x3F) << 6 | third & 0x3F;
    }

    private static int fourBytesOf(byte[] joined, int written, int codepoint) {
        joined[written] = (byte) (0xF0 | codepoint >> 18);
        joined[written + 1] = (byte) (0x80 | codepoint >> 12 & 0x3F);
        joined[written + 2] = (byte) (0x80 | codepoint >> 6 & 0x3F);
        joined[written + 3] = (byte) (0x80 | codepoint & 0x3F);
        return written + 4;
    }

    private static String textDecodedFrom(BinaryValue octets) {
        byte[] bytes = octets.octetsFromHere();
        int marked = byteOrderMarkOf(bytes);
        if (marked != 0) {
            return textBehindTheMark(bytes, marked);
        }
        java.nio.charset.CharsetDecoder strictly =
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        bytes = withSurrogatePairsJoined(bytes);
        java.nio.ByteBuffer reading = java.nio.ByteBuffer.wrap(bytes);
        java.nio.CharBuffer written = java.nio.CharBuffer.allocate(bytes.length + 1);
        java.nio.charset.CoderResult stopped = strictly.decode(reading, written, true);
        if (stopped.isError()) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, binaryOfBytes(
                    Arrays.copyOfRange(bytes, reading.position(), bytes.length)));
        }
        strictly.flush(written);
        return written.flip().toString();
    }

    private static String textBehindTheMark(byte[] bytes, int marked) {
        java.nio.charset.Charset theMarkAnnounces = switch (marked) {
            case 8 -> StandardCharsets.UTF_8;
            case 16 -> StandardCharsets.UTF_16BE;
            case -16 -> StandardCharsets.UTF_16LE;
            case 32 -> java.nio.charset.Charset.forName("UTF-32BE");
            default -> java.nio.charset.Charset.forName("UTF-32LE");
        };
        int width = Math.abs(marked) == 8 ? 3 : Math.abs(marked) / 8;
        try {
            return theMarkAnnounces.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, width, bytes.length - width))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException notText) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, "binary");
        }
    }

    private static int byteOrderMarkOf(byte[] octets) {
        if (startsWith(octets, 0xEF, 0xBB, 0xBF)) {
            return 8;
        }
        if (startsWith(octets, 0xFE, 0xFF)) {
            return 16;
        }
        if (startsWith(octets, 0xFF, 0xFE)) {
            return startsWith(octets, 0xFF, 0xFE, 0x00, 0x00) ? -32 : -16;
        }
        if (startsWith(octets, 0x00, 0x00, 0xFE, 0xFF)) {
            return 32;
        }
        return 0;
    }

    private static boolean startsWith(byte[] octets, int... expected) {
        if (octets.length < expected.length) {
            return false;
        }
        for (int at = 0; at < expected.length; at++) {
            if ((octets[at] & 0xFF) != expected[at]) {
                return false;
            }
        }
        return true;
    }

    private static final Set<Datatype> ANY_WORD_DATATYPES = Typeset.ANY_WORD.members();

    private static Set<Datatype> aBlockOrAnyWord() {
        Set<Datatype> accepted = EnumSet.copyOf(ANY_WORD_DATATYPES);
        accepted.add(Datatype.BLOCK);
        return Set.copyOf(accepted);
    }

    private static Set<Datatype> anyObjectOr(Datatype... alsoAccepted) {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.ANY_OBJECT.members());
        accepted.addAll(List.of(alsoAccepted));
        return Set.copyOf(accepted);
    }

    private static final Set<Datatype> PART_LIMIT = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(Datatype.INTEGER, Datatype.DECIMAL,
                    Datatype.PERCENT, Datatype.PAIR),
            Arrays.stream(Datatype.values()).filter(Datatype::isSeries))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final Set<Datatype> COUNT_OR_POSITION = PART_LIMIT.stream()
            .filter(accepted -> accepted != Datatype.PAIR)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final Set<Datatype> CHECKSUMMABLE =
            Set.of(Datatype.BINARY, Datatype.STRING, Datatype.FILE);

    private static final Set<Datatype> COMPRESSIBLE =
            Set.of(Datatype.BINARY, Datatype.STRING);

    private static final Set<Datatype> REMOVE_RANGE = java.util.stream.Stream.concat(
            PART_LIMIT.stream(), java.util.stream.Stream.of(Datatype.CHAR))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final Set<Datatype> DUP_COUNT = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT, Datatype.PAIR);

    private static final Set<String> CONSOLE_MODES = Set.of("echo", "line", "error");

    private static Value libraryFunction(Context context, String name) {
        if (!context.knows(name)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, name);
        }
        return context.slotFor(name).value();
    }

    private static boolean isAnyObject(Value value) {
        return value instanceof ErrorValue || fieldsOf(value) != null;
    }

    private static boolean objectHasFieldToFind(Value subject, Value wanted) {
        if (!(wanted instanceof WordValue word) || word.datatype() != Datatype.WORD) {
            return false;
        }
        String field = word.canonical();
        if (field.equals("self")) {
            return false;
        }
        if (subject instanceof ErrorValue) {
            return ErrorValue.FIELDS.contains(field);
        }
        return fieldsOf(subject).holds(field);
    }

    private static Context fieldsOf(Value value) {
        return switch (value) {
            case ObjectValue object -> object.context();
            case ModuleValue module -> module.context();
            case PortValue port -> port.context();
            case ErrorValue error -> {
                Context fields = Context.root();
                for (String name : ErrorValue.FIELDS) {
                    fields.set(name, error.field(name).orElseGet(NoneValue::none));
                }
                yield fields;
            }
            default -> null;
        };
    }

    private static Value theContentsOfThatFileSummed(
            Value file, String method, Evaluator evaluator, Set<String> refinements) {

        if (!Encodings.DIGESTS.containsKey(method)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA);
        }
        if (refinements.contains("part") || refinements.contains("with")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES);
        }
        Context library = evaluator.systemContext();
        if (!library.knows(FILE_CHECKSUM)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA);
        }
        return evaluator.applyFunction(library.slotFor(FILE_CHECKSUM).value(),
                List.of(file, WordValue.of(method)));
    }

    private static final String FILE_CHECKSUM = "file-checksum";

    private static Value systemInternalFunction(Context context, String name) {
        if (!(pathInto(context, "system", "contexts", "sys")
                instanceof ObjectValue internals)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, name);
        }
        if (!internals.context().knows(name)) {
            throw Raised.of(EvaluationFailure.BAD_SYS_FUNC, UnsetValue.unset());
        }
        Value held = internals.context().slotFor(name).value();
        if (!(held instanceof FunctionValue || held instanceof NativeValue
                || held instanceof OperatorValue)) {
            throw Raised.of(EvaluationFailure.BAD_SYS_FUNC, held);
        }
        return held;
    }

    private static void recordTheScriptArguments(Evaluator evaluator, Value given) {
        if (pathInto(evaluator.systemContext(), "system", "script")
                instanceof ObjectValue script) {
            script.context().set("args", given);
        }
    }

    private static int firstMalformedUtf8(BinaryValue bytes) {
        int end = bytes.storageLength() + 1;
        int at = bytes.index();
        while (at < end) {
            int width = utf8SequenceWidth(bytes.storage().at(at) & 0xFF);
            if (width > 0 && at + width <= end
                    && continuesCorrectly(bytes, at, width)) {
                at += width;
                continue;
            }
            if (isSurrogateHalfAt(bytes, at, end) && !isLowSurrogateAt(bytes, at)
                    && isSurrogateHalfAt(bytes, at + 3, end)
                    && isLowSurrogateAt(bytes, at + 3)) {
                at += 6;
                continue;
            }
            return at;
        }
        return -1;
    }

    private static int utf8SequenceWidth(int lead) {
        if (lead < 0x80) {
            return 1;
        }
        if (lead < 0xC2 || lead > 0xF4) {
            return 0;
        }
        if (lead < 0xE0) {
            return 2;
        }
        return lead < 0xF0 ? 3 : 4;
    }

    private static boolean continuesCorrectly(BinaryValue bytes, int at, int width) {
        int lead = bytes.storage().at(at) & 0xFF;
        if (width == 1) {
            return true;
        }
        for (int step = 1; step < width; step++) {
            int following = bytes.storage().at(at + step) & 0xFF;
            if (following < 0x80 || following > 0xBF) {
                return false;
            }
        }
        int second = bytes.storage().at(at + 1) & 0xFF;
        if (lead == 0xE0 && second < 0xA0) {
            return false;
        }
        if (lead == 0xED && second > 0x9F) {
            return false;
        }
        if (lead == 0xF0 && second < 0x90) {
            return false;
        }
        return lead != 0xF4 || second <= 0x8F;
    }

    private static boolean isSurrogateHalfAt(BinaryValue bytes, int at, int end) {
        if (at + 3 > end || (bytes.storage().at(at) & 0xFF) != 0xED) {
            return false;
        }
        int second = bytes.storage().at(at + 1) & 0xFF;
        int third = bytes.storage().at(at + 2) & 0xFF;
        return second >= 0xA0 && second <= 0xBF && third >= 0x80 && third <= 0xBF;
    }

    private static boolean isLowSurrogateAt(BinaryValue bytes, int at) {
        return (bytes.storage().at(at + 1) & 0xFF) >= 0xB0;
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

    private record WordsToResolve(int startAt, Set<String> spellings, boolean limited) {

        static WordsToResolve everything() {
            return new WordsToResolve(1, Set.of(), false);
        }

        boolean allows(String canonical) {
            return !limited || spellings.contains(canonical);
        }
    }

    private static WordsToResolve wordsToResolve(
            Context into, List<ContextSlot> targetSlots,
            Set<String> refinements, Value onlyThese) {

        if (!refinements.contains("only")) {
            return WordsToResolve.everything();
        }
        if (onlyThese instanceof IntegerValue position) {
            int startAt = Math.max(1, (int) position.magnitude());
            if (startAt > targetSlots.size()) {
                return new WordsToResolve(startAt, Set.of(), true);
            }
            return new WordsToResolve(startAt,
                    targetSlots.subList(startAt - 1, targetSlots.size()).stream()
                            .map(ContextSlot::canonical)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    true);
        }
        if (onlyThese instanceof BlockValue only) {
            return new WordsToResolve(1, only.remaining().stream()
                    .filter(word -> word instanceof WordValue spelled
                            && (spelled.datatype() == Datatype.WORD
                                    || spelled.datatype() == Datatype.SET_WORD))
                    .map(word -> ((WordValue) word).canonical())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    true);
        }
        return WordsToResolve.everything();
    }

    private static Value resolvedFrom(
            Context into, Context from, Value target,
            Set<String> refinements, Value onlyThese) {

        List<ContextSlot> targetSlots = into.slots();
        WordsToResolve writable =
                wordsToResolve(into, targetSlots, refinements, onlyThese);
        Map<String, Value> available = new LinkedHashMap<>();
        for (ContextSlot slot : from.slots()) {
            if (!slot.canonical().equals("self")) {
                available.put(slot.canonical(), slot.value());
            }
        }

        for (int at = writable.startAt(); at <= targetSlots.size(); at++) {
            ContextSlot slot = targetSlots.get(at - 1);
            if (slot.canonical().equals("self") || slot.isProtected()) {
                continue;
            }
            boolean known = available.containsKey(slot.canonical());
            if (!writable.allows(slot.canonical())
                    || (!known && !writable.limited())) {
                continue;
            }
            if (!refinements.contains("all") && !(slot.value() instanceof UnsetValue)) {
                continue;
            }
            slot.setValue(known ? available.get(slot.canonical()) : UnsetValue.unset());
        }

        if (refinements.contains("extend")) {
            available.forEach((name, value) -> {
                if (!into.holds(name) && writable.allows(name)) {
                    into.set(name, value);
                }
            });
        }
        return target;
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
                systemInternalFunction(context, "make-port*"), List.of(address));
        if (!(built instanceof PortValue port)) {
            throw schemeRefusal("writes", address);
        }
        if (theActorWrittenInRebol(port).isPresent()) {
            return port;
        }
        requireServiceForScheme(port.schemeName());
        port.markOpen(true);
        if (port.schemeName().equals("checksum")) {
            ChecksumPort.startEvenOnAnAlreadyOpenPort(
                    port, ChecksumPort.methodOf(port));
        }
        return port;
    }

    private Optional<String> theFileNamedByAUrl(
            Value target, Evaluator evaluator, Context context) {

        if (target.datatype() != Datatype.URL) {
            return Optional.empty();
        }
        PortValue routed = portOpenedFor(target, evaluator, context);
        return isAFilePort(routed)
                ? Optional.of(SeekableFilePort.pathOf(routed))
                : Optional.empty();
    }

    private static Raised schemeRefusal(String verbs, Value routed) {
        return Raised.of(EvaluationFailure.NO_SERVICE,
                "nothing here " + verbs + " " + schemeNameOf(routed));
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
        refuseAClosedCipherPort(port);
        if (!(data instanceof BinaryValue octets)) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    port.fieldNamed("spec") instanceof ObjectValue spec
                            ? valueInSpec(spec, "ref")
                            : NoneValue.none());
        }
        CryptPort.write(port, octets.octetsFromHere());
        return port;
    }

    private Value writeToPort(PortValue port, Value data, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {
        Optional<Value> itsOwn = theRebolActorsAnswer(
                "write", List.of(port, data), refinements, evaluator);
        if (itsOwn.isPresent()) {
            return itsOwn.get();
        }
        return switch (port.schemeName()) {
            case "console" -> writtenToTheConsole(port, data, evaluator);
            case "tcp" -> sentDownTheConnection(port, data, evaluator);
            case "checksum" -> summedIntoThePort(port, data, arguments, refinements);
            case "crypt" -> encipheredIntoThePort(port, data);
            case "file" -> writtenToTheFileBehind(
                    port, data, evaluator, arguments, refinements);
            default -> throw schemeRefusal("writes", port);
        };
    }

    private static void refuseAPortOpenedOnlyToRead(
            PortValue port, EvaluationFailure failure) {

        if (!SeekableFilePort.mayWriteThrough(port)) {
            throw Raised.of(failure, StringValue.of(
                    SeekableFilePort.pathOf(port), Datatype.FILE));
        }
    }

    private Value appendedToTheFileBehind(
            PortValue port, Value data, Evaluator evaluator, Set<String> refinements) {

        if (refinements.contains("dup") || refinements.contains("only")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "append on a file port is a write, and takes no dup or only");
        }
        return writtenToTheFileBehind(
                port, data, evaluator, List.of(), Set.of("append"));
    }

    private Value writtenToTheFileBehind(
            PortValue port, Value data, Evaluator evaluator,
            List<Value> arguments, Set<String> refinements) {

        requireService(HostService.FILES);
        if (port.isOpen()) {
            refuseAPortOpenedOnlyToRead(port, EvaluationFailure.READ_ONLY);
        }
        if (refinements.contains("append")) {
            SeekableFilePort.moveTo(port, throughPort(
                    () -> SeekableFilePort.wholeSize(evaluator.files(), port))
                    instanceof IntegerValue size ? size.magnitude() : 0);
        }
        Value seek = refinements.contains("seek")
                ? argumentFor("seek", FileWriting.ARGUMENT_ORDER,
                        arguments, refinements, 2)
                : null;
        if (seek instanceof IntegerValue where) {
            SeekableFilePort.moveTo(port, where.magnitude());
        }
        byte[] octets = octetsOf(data);
        Value part = refinements.contains("part")
                ? argumentFor("part", FileWriting.ARGUMENT_ORDER,
                        arguments, refinements, 2)
                : null;
        if (part instanceof IntegerValue wanted) {
            octets = Arrays.copyOf(octets,
                    (int) Math.max(0, Math.min(wanted.magnitude(), octets.length)));
        }
        byte[] written = octets;
        return throughPort(() -> {
            SeekableFilePort.writeAt(evaluator.files(), port, written);
            return StringValue.of(SeekableFilePort.pathOf(port), Datatype.FILE);
        });
    }

    private Value summedIntoThePort(PortValue port, Value data,
            List<Value> arguments, Set<String> refinements) {
        if (!(data instanceof BinaryValue || data instanceof StringValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(data));
        }
        if (!port.isOpen()) {
            port.markOpen(true);
            ChecksumPort.startEvenOnAnAlreadyOpenPort(
                    port, ChecksumPort.methodOf(port));
        }
        SeriesValue written = (SeriesValue) data;
        ChecksumPort.add(port,
                octetsOf(written.head()),
                written.index() - 1,
                wholeNumberAsked("seek", arguments, refinements),
                wholeNumberAsked("part", arguments, refinements));
        return port;
    }

    private static final List<String> WRITE_OPTIONAL_ARGUMENTS =
            List.of("part", "seek", "allow");

    private static Long wholeNumberAsked(
            String refinement, List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains(refinement)) {
            return null;
        }
        Value asked = argumentFor(refinement, WRITE_OPTIONAL_ARGUMENTS,
                arguments, refinements, 2);
        return Comparison.isNumeric(asked)
                ? (long) Comparison.asDouble(asked)
                : null;
    }

    private Value writtenToTheConsole(
            PortValue port, Value data, Evaluator evaluator) {
        requireService(HostService.CONSOLE);
        evaluator.output().write(Molder.form(data));
        return port;
    }

    private Value sentDownTheConnection(
            PortValue port, Value data, Evaluator evaluator) {

        requireService(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughNetwork(() -> {
            connection.write(octetsOf(data));
            queueWhatHappenedTo(port, "wrote", evaluator);
            return port;
        });
    }

    private static Set<Datatype> asTypeOrExample() {
        Set<Datatype> accepted = EnumSet.of(Datatype.DATATYPE);
        accepted.addAll(Typeset.ANY_BLOCK.members());
        accepted.addAll(Typeset.ANY_STRING.members());
        return Set.copyOf(accepted);
    }

    private static Set<Datatype> copyable() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.SERIES.members());
        accepted.addAll(Typeset.ANY_FUNCTION.members());
        accepted.addAll(Set.of(Datatype.ACTION, Datatype.CLOSURE,
                Datatype.COMMAND, Datatype.REBCODE, Datatype.STRUCT));
        accepted.add(Datatype.PORT);
        accepted.add(Datatype.MAP);
        accepted.add(Datatype.OBJECT);
        accepted.add(Datatype.BITSET);
        accepted.add(Datatype.ERROR);
        return Set.copyOf(accepted);
    }

    private static Set<Datatype> positionable() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.SERIES.members());
        accepted.add(Datatype.PORT);
        accepted.add(Datatype.NONE);
        accepted.add(Datatype.GOB);
        return Set.copyOf(accepted);
    }

    private static Value pathInto(Context context, String... names) {
        Value reached = context.knows(names[0])
                ? context.slotFor(names[0]).value()
                : NoneValue.none();
        for (int step = 1; step < names.length; step++) {
            if (!(reached instanceof ObjectValue holder)
                    || !holder.context().holds(names[step])) {
                return NoneValue.none();
            }
            reached = holder.context().ownSlotFor(names[step]).value();
        }
        return reached;
    }

    private static Value moduleFromSpec(
            Value spec, Evaluator evaluator, Context context) {
        if (!(spec instanceof BlockValue given)) {
            return raiseBadMakeArg(spec, "module!");
        }
        Value built = evaluator.applyFunction(
                systemInternalFunction(context, "make-module*"), List.of(given));
        if (!(built instanceof ModuleValue module)) {
            throw Raised.of(EvaluationFailure.INVALID_SPEC, "module!");
        }
        return module;
    }

    private static Value moduleFromHeaderAndWords(Value value) {
        if (!(value instanceof BlockValue parts)) {
            return raiseBadMakeArg(value, "module!");
        }
        List<Value> given = parts.remaining();
        if (given.size() < 2
                || !(given.get(0) instanceof ObjectValue header)
                || !(given.get(1) instanceof ObjectValue words)) {
            return raiseBadMakeArg(value, "module!");
        }
        return new ModuleValue(words.context(), header);
    }

    private static Optional<ObjectValue> theActorWrittenInRebol(PortValue port) {
        return port.fieldNamed("actor") instanceof ObjectValue actor
                ? Optional.of(actor)
                : Optional.empty();
    }

    private Value askTheActor(PortValue port, ObjectValue actor, String action,
            List<Value> arguments, Set<String> refinements, Evaluator evaluator) {

        Value theFunction = actor.context().holds(action)
                ? actor.context().ownSlotFor(action).value()
                : NoneValue.none();
        if (!(theFunction instanceof FunctionValue able)) {
            throw Raised.of(EvaluationFailure.NO_PORT_ACTION,
                    WordValue.of(action).as(Datatype.SET_WORD));
        }
        return evaluator.applyFunction(able,
                laidOutAsTheActorDeclaresThem(able, arguments, refinements));
    }

    private static List<Value> laidOutAsTheActorDeclaresThem(
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

    private static List<Value> withThePortInFront(
            PortValue port, List<Value> arguments) {

        List<Value> asTheActorTakesThem = new ArrayList<>(arguments);
        asTheActorTakesThem.set(0, port);
        return asTheActorTakesThem;
    }

    private Optional<Value> theRebolActorsAnswer(String action,
            List<Value> arguments, Set<String> refinements, Evaluator evaluator) {

        if (!(arguments.getFirst() instanceof PortValue port)) {
            return Optional.empty();
        }
        refuseAPortWhoseSpecIsNotAnObject(port);
        refuseAnActorThatIsNeitherAWordNorAnObject(port);
        return theActorWrittenInRebol(port).map(actor ->
                askTheActor(port, actor, action, arguments, refinements, evaluator));
    }

    private static void refuseAPortWhoseSpecIsNotAnObject(PortValue port) {
        if (!(port.fieldNamed("spec") instanceof ObjectValue)) {
            throw Raised.of(EvaluationFailure.INVALID_PORT);
        }
    }

    private static void refuseAnActorThatIsNeitherAWordNorAnObject(PortValue port) {
        Value actor = port.fieldNamed("actor");
        if (actor instanceof ObjectValue || actor instanceof WordValue
                || actor instanceof NoneValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.INVALID_ACTOR);
    }

    private void requireServiceForScheme(String scheme) {
        switch (scheme) {
            case "console" -> theSchemeReachesNothingOutside();
            case "file", "dir" -> requireService(HostService.FILES);
            case "tcp", "dns" -> requireService(HostService.NETWORK);
            case "event" -> requireService(HostService.WINDOWS);
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
                    .filter(name -> matchesTheWholeOf(withoutItsSlash(name), pattern))
                    .<Value>map(name -> StringValue.of(name, Datatype.FILE))
                    .toList());
        }

        private static String withoutItsSlash(String name) {
            return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        }

        private static boolean matchesTheWholeOf(String name, String pattern) {
            return patternEnd(name, 0, name.length(), pattern, true,
                    Wildcards.STARS_AND_QUESTION_MARKS) == name.length();
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
                return Optional.of(withOneLineFeedPerEnding(
                        strictlyDecodedByItsMark(bytes)));
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
                        bytes, 4, java.nio.charset.Charset.forName("UTF-32LE"));
            }
            if (startsWith(bytes, 0x00, 0x00, 0xFE, 0xFF)) {
                return strictlyDecoded(
                        bytes, 4, java.nio.charset.Charset.forName("UTF-32BE"));
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
                byte[] bytes, int from, java.nio.charset.Charset charset)
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
            if (data instanceof CharacterValue character) {
                return utf8(Character.toString(character.codepoint()));
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
        if (port.fieldNamed("spec") instanceof ObjectValue spec
                && spec.context().holds("port")
                && spec.context().ownSlotFor("port").value()
                        instanceof IntegerValue given) {
            return (int) given.magnitude();
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

    private static final Set<String> THE_SCHEMES_THAT_ARE_QUEUES =
            Set.of("system", "event", "callback");

    private static Optional<BlockValue> theEventQueueOf(Value value) {
        if (!(value instanceof PortValue port)
                || !THE_SCHEMES_THAT_ARE_QUEUES.contains(port.schemeName())) {
            return Optional.empty();
        }
        if (!(port.fieldNamed("state") instanceof BlockValue queue)) {
            BlockValue made = BlockValue.block(List.of());
            port.setField("state", made);
            return Optional.of(made);
        }
        return Optional.of(queue);
    }

    private static Value queuedOnThePort(
            PortValue port, BlockValue queue, Value happening, boolean atTheEnd) {

        if (!(happening instanceof EventValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, happening);
        }
        queue.storage().insertAt(
                atTheEnd ? queue.storage().length() + 1 : queue.index(), happening);
        return port;
    }

    private static void markOpenWhateverTheActorLeftInState(PortValue port) {
        if (!port.isOpen()) {
            port.markOpen(true);
        }
    }

    private static void handBackTheConnectionBehind(PortValue port) {
        if (port.fieldNamed("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            open.close();
        }
    }

    private static NetworkPort.Connection connectionBehind(PortValue port) {
        if (port.fieldNamed("state") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            return open;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    private static String hostNamedBy(PortValue port) {
        if (port.fieldNamed("spec") instanceof ObjectValue spec) {
            if (spec.context().holds("host")
                    && spec.context().ownSlotFor("host").value()
                            instanceof StringValue host) {
                return host.text();
            }
            if (spec.context().holds("ref")
                    && spec.context().ownSlotFor("ref").value()
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
            throw new Raised(denied.subject().isEmpty()
                    ? ErrorValue.of(ErrorCategory.ACCESS,
                            denied.errorId(), denied.getMessage())
                    : ErrorValue.about(ErrorCategory.ACCESS,
                            denied.errorId(), denied.getMessage(),
                            StringValue.of(denied.subject(), Datatype.FILE),
                            IntegerValue.of(OPEN_FAILED)));
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

    private void defineParse() {
        define("parse", List.of(Parameter.required("input", PARSEABLE),
                        Parameter.required("rule")),
                Set.of("case"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(1)) {
                    case BlockValue rule -> arguments.get(0) instanceof StringValue
                            || arguments.get(0) instanceof BinaryValue
                            ? StringParser.answer(evaluator, context,
                                    (SeriesValue) arguments.get(0), rule,
                                    refinements.contains("case"))
                            : Parser.answer(evaluator, context, arguments.get(0), rule,
                                    refinements.contains("case"));
                    default -> {
                        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                "parse needs a rule block, not "
                                        + arguments.get(1).datatype().literalSpelling());
                    }
                });

        define("split", List.of(Parameter.required("input"), Parameter.required("delimiters")),
                (arguments, evaluator, context) -> splitOn(
                        arguments.get(0), arguments.get(1)));
    }

    private static final Set<Datatype> CLAMPABLE = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
            Datatype.TUPLE, Datatype.PAIR, Datatype.MONEY);

    private static Value heldInsideTheRange(Value value, Value lowest, Value highest) {
        if (value.datatype() != lowest.datatype()
                || value.datatype() != highest.datatype()) {
            throw Raised.of(EvaluationFailure.TYPE_MISMATCH,
                    value.datatype().literalSpelling()
                            + " cannot be clamped between "
                            + lowest.datatype().literalSpelling() + " and "
                            + highest.datatype().literalSpelling());
        }
        return switch (value) {
            case IntegerValue whole -> IntegerValue.of(Math.max(
                    ((IntegerValue) lowest).magnitude(),
                    Math.min(((IntegerValue) highest).magnitude(), whole.magnitude())));
            case DecimalValue fraction -> new DecimalValue(
                    clipped(fraction.quantity(),
                            ((DecimalValue) lowest).quantity(),
                            ((DecimalValue) highest).quantity()),
                    fraction.datatype());
            case PairValue point -> PairValue.of(
                    clipped(point.x(), ((PairValue) lowest).x(), ((PairValue) highest).x()),
                    clipped(point.y(), ((PairValue) lowest).y(), ((PairValue) highest).y()));
            case TupleValue parts -> clampedTuple(
                    parts, (TupleValue) lowest, (TupleValue) highest);
            case MoneyValue amount -> new MoneyActions(amount).heldBetween(
                    (MoneyValue) lowest, (MoneyValue) highest);
            default -> value;
        };
    }

    private static Value clampedTuple(
            TupleValue value, TupleValue lowest, TupleValue highest) {

        int[] octets = value.segments();
        int[] held = new int[octets.length];
        for (int at = 0; at < octets.length; at++) {
            int low = at < lowest.segments().length ? lowest.segments()[at] : 0;
            int high = at < highest.segments().length ? highest.segments()[at] : 0;
            held[at] = Math.max(low, Math.min(high, octets[at]));
        }
        return TupleValue.of(held);
    }

    private static double clipped(double value, double lowest, double highest) {
        return Math.max(lowest, Math.min(highest, value));
    }

    private static Value betweenTwoPoints(
            PairValue from, PairValue to, boolean alongTheStreets) {

        double across = from.x() - to.x();
        double down = from.y() - to.y();
        return DecimalValue.of(alongTheStreets
                ? Math.abs(across) + Math.abs(down)
                : Math.hypot(across, down));
    }

    private static final int LARGEST_EXACT_FACTORIAL = 20;
    private static final int LARGEST_FACTORIAL_AT_ALL = 170;

    private static Value theFactorialOf(long value) {
        if (value < 0 || value > LARGEST_FACTORIAL_AT_ALL) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "factorial takes 0 to " + LARGEST_FACTORIAL_AT_ALL
                            + " and was given " + value);
        }
        if (value > LARGEST_EXACT_FACTORIAL) {
            double approximate = 1;
            for (long each = 2; each <= value; each++) {
                approximate *= each;
            }
            return DecimalValue.of(approximate);
        }
        long exact = 1;
        for (long each = 2; each <= value; each++) {
            exact *= each;
        }
        return IntegerValue.of(exact);
    }

    private static Value splitOn(Value input, Value rule) {
        if (!(input instanceof StringValue text)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "split takes a string, not " + input.datatype().literalSpelling());
        }
        String delimiters = switch (rule) {
            case StringValue given -> given.text();
            case CharacterValue given -> Character.toString(given.codepoint());
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "split needs delimiters, not " + rule.datatype().literalSpelling());
        };
        List<Value> pieces = new ArrayList<>();
        if (text.text().isEmpty()) {
            return BlockValue.block(pieces);
        }
        StringBuilder piece = new StringBuilder();
        for (int codepoint : text.text().codePoints().toArray()) {
            if (delimiters.indexOf(codepoint) >= 0) {
                pieces.add(StringValue.of(piece.toString()));
                piece.setLength(0);
                continue;
            }
            piece.appendCodePoint(codepoint);
        }
        pieces.add(StringValue.of(piece.toString()));
        return BlockValue.block(pieces);
    }

    /** Deliberately empty: a real 3.22.1 has no LAYOUT either. Do not stub it. */
    private void defineLayout() {
    }

    private void defineScreen() {
        define("init-top-window",
                List.of(Parameter.required("gob", Set.of(Datatype.GOB))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.WINDOWS);
                    return theRootGobTakenBy(evaluator.screen(), arguments.getFirst());
                });

        define("gui-metric",
                List.of(Parameter.required("keyword", Set.of(Datatype.WORD)),
                        Parameter.belongingTo("set", "val", ANYTHING),
                        Parameter.belongingTo("display", "idx", Set.of(Datatype.INTEGER))),
                Set.of("set", "display"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.WINDOWS);
                    return measurementOf(evaluator.screen(),
                            metricNamedBy(arguments.getFirst()),
                            displayAskedFor(arguments, refinements));
                });

        define("show",
                List.of(Parameter.required("gob",
                        Set.of(Datatype.GOB, Datatype.NONE, Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.WINDOWS);
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
        if (!(written instanceof IntegerValue index)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "a display is numbered with an integer, not "
                            + written.datatype().literalSpelling());
        }
        return (int) index.magnitude();
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
        return evaluator.systemContext().slotFor("system").value()
                        instanceof ObjectValue system
                && system.context().slotFor("options").value()
                        instanceof ObjectValue options
                && options.context().slotFor("binary-base").value()
                        instanceof IntegerValue base
                ? (int) base.magnitude()
                : 16;
    }

    private void defineOutput() {
        define("mold", List.of(Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER))),
                Set.of("all", "only", "flat", "part"),
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
                            && arguments.get(1) instanceof IntegerValue limit) {
                        return StringValue.of(Molder.moldWithin(arguments.getFirst(),
                                (int) Math.max(0, limit.magnitude()), how));
                    }
                    return StringValue.of(how.apply(arguments.getFirst()));
                });
        define("form", takesAnything("value"),
                (arguments, evaluator, context) -> StringValue.of(Molder.form(arguments.get(0))));

        define("quit", List.of(Parameter.belongingTo("return", "value", Set.of())),
                Set.of("now", "return"),
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
