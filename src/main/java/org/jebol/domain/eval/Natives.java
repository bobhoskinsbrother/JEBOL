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
import java.util.*;
import java.util.function.DoublePredicate;
import java.util.function.Supplier;
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

    /**
     * What just happened, readable through {@code system/state}.
     *
     * <p>Held here because the natives that write it -- TRY, CATCH and
     * QUIT -- are defined long before the system object is assembled, and
     * a handler written as a block has no other way to reach the value it
     * is handling.
     */
    private final Context runState = Context.root();

    /**
     * The library's own private helpers, reachable as
     * {@code system/contexts/sys}.
     *
     * <p>Built when the system object is assembled and handed to the
     * interpreter, which loads the sys files into it. A child of the library
     * context, so a helper defined here still sees every standard function.
     */
    private Context systemInternals = Context.root();

    /**
     * The map REGISTER files struct layouts in, held here as well as in the
     * SYSTEM object.
     *
     * <p>Both halves need it and only one of them has an evaluator. MAKE is
     * called with one and can walk {@code system/catalog/structs}; the reader
     * is handed a maker with no evaluator at all, and a field written
     * {@code [struct! pair8!]} inside a construction has to resolve just the
     * same. Keeping the map itself is what lets both ask the same question.
     */
    private final MapValue registeredStructLayouts = MapValue.empty();

    /**
     * What each datatype says about itself, as {@code reflect} answers it.
     *
     * <p>Title and category, taken verbatim from a real R3 rather than
     * written out here: the wording is data a script can compare against,
     * and inventing it would make every such comparison wrong. The
     * category is R3's own grouping -- scalar, series, block, word,
     * function, object and the rest -- and it is not derivable from the
     * datatypes JEBOL has, because it names families JEBOL has not built.
     */
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

    /**
     * Which kinds of host service the script may ask for.
     *
     * <p>Empty unless a host said otherwise. A native that reaches
     * outside the interpreter asks this first, and raises rather than
     * answering when the answer is no: a READ that quietly gives none
     * reads as an empty file, and a script cannot tell the two apart.
     */
    private Set<HostService> grantedServices = Set.of();

    /**
     * What this machine puts between the parts of a path.
     *
     * <p>A slash unless the host says otherwise. The domain cannot ask
     * the machine itself, thus the application tells it: a separator is a
     * fact about where the code runs and not about the language.
     */
    private char localFileSeparator = '/';

    /** Tells the natives what this machine puts between path parts. */
    public void useFileSeparator(char separator) {
        this.localFileSeparator = separator;
    }

    /**
     * What {@code system/options/boot} names: something that starts this
     * very interpreter.
     *
     * <p>The C's boot is the running executable. JEBOL's is a JVM plus a
     * classpath, and only the application knows how to package those into
     * one path, so it tells the domain rather than the domain finding out.
     */
    private String bootLauncher = "";

    /** Tells the natives what starts this interpreter from a shell. */
    public void useBootLauncher(String launcherPath) {
        this.bootLauncher = launcherPath;
    }

    /**
     * The text of errors.reb, handed in by whoever can read files.
     *
     * <p>The catalogue is data the domain interprets, not a file the
     * domain reads: transcoding it is language work and fetching it is
     * not.
     */
    private String errorCatalogueSource = "";

    /** Tells the natives what the vendored errors.reb says. */
    public void useErrorCatalogue(String source) {
        this.errorCatalogueSource = source;
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

    /**
     * Refuses a host service the script may not have.
     *
     * <p>The error names the service and says which of three things
     * happened, because the first two can change between one run and the
     * next and the third never does.
     */
    private void requireService(HostService service) {
        if (grantedServices.contains(service)) {
            return;
        }
        throw Raised.of(EvaluationFailure.NO_SERVICE,
                service.name().toLowerCase(java.util.Locale.ROOT)
                        + " is " + ServiceRefusal.NOT_GRANTED.name()
                                .toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    /**
     * Refuses a native that exists to call code written in C.
     *
     * <p>No grant turns these on, thus the grant is not even looked at. A
     * JVM can be made to call a shared library and the result stops being
     * portable, which is the one thing JEBOL is for.
     */
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
                    || !(pair.get(0) instanceof IntegerValue named)
                    || !(pair.get(1) instanceof IntegerValue chosen)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(asked));
            }
            process = named.magnitude();
            signal = (int) chosen.magnitude();
        }
        requireService(HostService.PROCESSES);
        return LogicValue.of(endProcess(process, signal));
    }

    private static boolean endProcess(long process, int signal) {
        java.util.Optional<ProcessHandle> found = ProcessHandle.of(process);
        if (found.isEmpty()) {
            throw Raised.of(EvaluationFailure.PERMISSION_DENIED, String.valueOf(process));
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

    private static Value refuseExtensionPoint(String named) {
        throw Raised.of(EvaluationFailure.NO_SERVICE,
                named + " calls code written in C, which is "
                        + ServiceRefusal.NEVER_PORTABLE.name()
                                .toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
    }

    public static Natives standard() {
        return new Natives();
    }

    /**
     * The SYSTEM object: what the interpreter knows about itself.
     *
     * <p>Its catalogue lists the datatypes in the order they are numbered,
     * which is how a script asks what exists rather than being told.
     */
    private static BlockValue typeNames(String... spellings) {
        return BlockValue.block(java.util.Arrays.stream(spellings)
                .map(spelling -> (Value) WordValue.of(spelling + "!"))
                .toList());
    }

    /**
     * The sixty names actions.reb declares, in the order it declares them.
     *
     * <p>An action is the polymorphic kind of function, with an arm per
     * datatype, and the list is a fact about Rebol's declarations rather than
     * about this code. JEBOL answers native! for all of them, so nothing here
     * could tell them apart without being told.
     */
    private static final List<String> ACTION_NAMES = List.of(
            "add", "subtract", "multiply", "divide", "remainder", "power",
            "and~", "or~", "xor~", "negate", "complement", "absolute", "round",
            "random", "odd?", "even?", "head", "tail", "head?", "tail?",
            "past?", "next", "back", "skip", "at", "atz", "index?", "indexz?",
            "length?", "pick", "find", "select", "reflect", "make", "to",
            "copy", "take", "put", "insert", "append", "remove", "change",
            "poke", "clear", "trim", "swap", "reverse", "sort", "create",
            "delete", "open", "close", "read", "write", "open?", "query",
            "modify", "update", "rename", "flush");

    /** A block of plain words, where typeNames would add a datatype suffix. */
    private static BlockValue typeNamesWithoutSuffix(String... spellings) {
        return BlockValue.block(java.util.Arrays.stream(spellings)
                .<Value>map(WordValue::of).toList());
    }

    /** A set holding exactly the characters of a string. */
    private static BitsetValue charactersIn(String characters) {
        return BitsetValue.ofCharacters(characters.chars().toArray());
    }

    /**
     * The octets a quoted-printable body may carry as they stand.
     *
     * <p>sysobj.reb writes it as sixteen bytes of mostly-ones. Read back, it
     * is every octet except the three the encoding must escape.
     */
    private static BitsetValue quotedPrintableOctets() {
        StringBuilder allowed = new StringBuilder();
        for (int octet = 0; octet < 256; octet++) {
            if (octet != 0x3D && octet != 0x3A && octet != 0x2E) {
                allowed.append((char) octet);
            }
        }
        return charactersIn(allowed.toString());
    }

    private static BitsetValue rangeOfCharacters(int from, int to) {
        int[] codes = new int[to - from + 1];
        for (int at = 0; at < codes.length; at++) {
            codes[at] = from + at;
        }
        return BitsetValue.ofCharacters(codes);
    }

    private static BitsetValue lettersOfBothCases() {
        return together(rangeOfCharacters('a', 'z'), rangeOfCharacters('A', 'Z'));
    }

    private static BitsetValue together(BitsetValue first, BitsetValue second) {
        byte[] left = first.octets();
        byte[] right = second.octets();
        byte[] both = new byte[Math.max(left.length, right.length)];
        for (int at = 0; at < both.length; at++) {
            both[at] = (byte) ((at < left.length ? left[at] : 0)
                    | (at < right.length ? right[at] : 0));
        }
        return BitsetValue.of(both);
    }

    private ObjectValue systemObject(Context systemContext) {
        Context catalog = Context.root();
        catalog.set("datatypes", BlockValue.block(
                java.util.Arrays.stream(Datatype.values())
                        .map(datatype -> (Value) DatatypeValue.of(datatype))
                        .toList()));

        // The lists sysobj.reb declares, less vector! wherever it appears,
        // because there is no such datatype here to name. base-defs.reb
        // generates SPEC-OF, BODY-OF and the rest straight from this, so a
        // datatype left out is a datatype the generated function refuses --
        // which is how WORDS-OF came to turn away the handle whose only
        // readable field is its type.
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
        bitsets.set("numeric", rangeOfCharacters('0', '9'));
        bitsets.set("alpha", lettersOfBothCases());
        bitsets.set("alpha-numeric", together(lettersOfBothCases(), rangeOfCharacters('0', '9')));
        bitsets.set("hex-digits", together(rangeOfCharacters('0', '9'),
                together(rangeOfCharacters('a', 'f'), rangeOfCharacters('A', 'F'))));
        bitsets.set("plus-minus", BitsetValue.ofCharacters('+', '-'));
        bitsets.set("not-crlf",
                BitsetValue.ofCharacters('\r', '\n').complemented());
        bitsets.set("uri", charactersIn(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                        + "!#$&'()*+,-./:;=?@_~"));
        bitsets.set("uri-component", charactersIn(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                        + "!'()*-._~"));
        bitsets.set("quoted-printable", quotedPrintableOctets());
        catalog.set("bitsets", new ObjectValue(bitsets));

        // Filled by REGISTER rather than at boot: sysobj.reb declares it
        // `make map! []` with the comment "filled using `register` native
        // function", so an empty map here is the finished state and not a
        // gap.
        catalog.set("structs", registeredStructLayouts);

        // The two halves of the function set this interpreter carries in its
        // host language. The split is Rebol's declaration rather than a fact
        // about the code here -- JEBOL answers native! for both where a real
        // R3 answers action! for the sixty -- so actions.reb is the authority
        // for which name is which.
        catalog.set("actions", BlockValue.block(ACTION_NAMES.stream()
                .filter(definitions::containsKey)
                .<Value>map(WordValue::of).toList()));

        catalog.set("natives", BlockValue.block(definitions.keySet().stream()
                .filter(named -> !ACTION_NAMES.contains(named))
                .sorted()
                .<Value>map(WordValue::of).toList()));

        // What a boot flag may be, rather than what was passed. sysobj.reb
        // says so on the line above it: "Official list of
        // system/options/flags that can appear".
        catalog.set("boot-flags", typeNamesWithoutSuffix(
                "script", "args", "do", "import", "version", "debug", "secure",
                "help", "vers", "quiet", "verbose", "secure-min", "secure-max",
                "trace", "halt", "cgi", "boot-level", "no-window", "no-color",
                "legacy-repl"));

        // Empty, and empty is the answer rather than a missing field. A real
        // 3.22.1 lists forty-two ciphers and fifteen resize filters, and both
        // describe a port this build has not got: there is no block cipher
        // here, and RESIZE samples one way with no choice of filter. Naming
        // them would be the catalogue lying about what asking for one does,
        // which is worse than an empty list -- a script reads a catalogue so
        // it need not guess.
        catalog.set("ciphers", BlockValue.block(List.of()));
        catalog.set("filters", BlockValue.block(List.of()));

        catalog.set("elliptic-curves", BlockValue.block(
                EllipticCurveKey.curveNames().stream()
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
                StringValue.of(".htm", Datatype.FILE), WordValue.of("markup"))));

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
        system.set("platform", WordValue.of("JVM"));
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
        // The words a date needs when it is written for a person to read.
        // sysobj.reb holds both lists and the week begins at Monday there,
        // which is what DATE's WEEKDAY counts from.
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
            String named = Codecs.REGISTERED.get(at);
            codecs.set(named, HandleValue.function(
                    "codec", CODEC_HANDLE_IDENTITY + at, WordValue.of(named)));
        }
        system.set("codecs", new ObjectValue(codecs));
        // What the console is doing: the line being edited and the ones
        // already entered. Both stay as they are until a console adapter
        // fills them, which is the state a program with no console is in.
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
        // Declared and none, which is what a real 3.22.1 answers. Worth
        // having as a field rather than absent: code walking the contexts
        // finds three names, one of them holding nothing, instead of a path
        // that fails.
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

    /**
     * A native that takes refinements, told which of them arrived.
     *
     * <p>The refinements are declared once here rather than a registration
     * per combination. FIND takes nine, and one entry per combination would
     * be five hundred entries for one native.
     */
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

    /**
     * The datatypes AND, OR and XOR accept, taken from actions.reb.
     *
     * <p>Every one of them is a run of bits or a truth. A decimal is not
     * on the list and neither is a string, so both are refused before the
     * operation is reached.
     */
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

    /**
     * What {@code [any-type!]} declares: every datatype, unset included. A
     * bare parameter refuses unset, so the natives whose C spec says
     * any-type! carry this set instead.
     */
    private static final Set<Datatype> ANYTHING = Typeset.ANY_TYPE.members();

    private static List<Parameter> takesAnything(String... names) {
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, ANYTHING));
        }
        return parameters;
    }

    /** The same parameters, with bitset! allowed for the first one. */
    private static List<Parameter> withBitsets(List<Parameter> parameters) {
        List<Parameter> widened = new ArrayList<>(parameters);
        Parameter first = widened.getFirst();
        Set<Datatype> accepted = EnumSet.copyOf(first.acceptedTypes());
        accepted.add(Datatype.BITSET);
        widened.set(0, Parameter.required(first.name(), accepted));
        return widened;
    }

    /** {@code number!} and nothing else: integer!, decimal! and percent!. */
    private static List<Parameter> takesOnlyNumbers(String... names) {
        Set<Datatype> numbers = Set.of(
                Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT);
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, numbers));
        }
        return parameters;
    }

    /** {@code [integer! decimal!]}, which is {@code number!} without percent. */
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
                (arguments, evaluator, context) -> arithmetic(arguments, Operation.ADD));
        define("subtract", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> arithmetic(arguments, Operation.SUBTRACT));
        define("multiply", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> arithmetic(arguments, Operation.MULTIPLY));
        define("divide", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> arithmetic(arguments, Operation.DIVIDE));
        define("remainder", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> arithmetic(arguments, Operation.REMAINDER));
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
                            last = oneRound(evaluator, body, context);
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
                            tracing.showTheLast((int) lines.magnitude());
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
                                oneCharacterPickedAtRandom(text);
                        case StringValue text -> shuffledText(text);
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
                    case BitsetValue members -> members.complemented();
                    case BinaryValue bytes -> newBytesEachFlipped(bytes);
                    case ImageValue image -> newImageEachChannelFlipped(image);
                    case TypesetValue kinds -> complementOfTypeset(kinds);
                    case TupleValue tuple -> flippedOctets(tuple);
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
                (arguments, evaluator, context) -> {
                    long divisor = (long) Comparison.asDouble(arguments.get(1));
                    requireNonZero(divisor);
                    return IntegerValue.of((long) Comparison.asDouble(arguments.get(0)) / divisor);
                });

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
                        : arithmetic(List.of(IntegerValue.of(0), arguments.get(0)),
                                Operation.SUBTRACT));

        define("maximum", takesComparable("value1", "value2"),
                (arguments, evaluator, context) ->
                        extreme(arguments.get(0), arguments.get(1), true));
        define("minimum", takesComparable("value1", "value2"),
                (arguments, evaluator, context) ->
                        extreme(arguments.get(0), arguments.get(1), false));

        define("and~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        combined(arguments.get(0), arguments.get(1), Bitwise.AND));
        define("or~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        combined(arguments.get(0), arguments.get(1), Bitwise.OR));
        define("xor~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        combined(arguments.get(0), arguments.get(1), Bitwise.XOR));

        define("lerp", List.of(Parameter.required("value1"),
                        Parameter.required("value2"), Parameter.required("fraction")),
                (arguments, evaluator, context) -> interpolated(
                        arguments.get(0), arguments.get(1), arguments.get(2)));

        define("mod", List.of(
                        Parameter.required("dividend", DIVISIBLE),
                        Parameter.required("divisor", DIVISIBLE)),
                (arguments, evaluator, context) -> remainderOf(
                        arguments.get(0), arguments.get(1), Division.TRUNCATED));

        define("modulo", List.of(
                        Parameter.required("dividend", DIVISIBLE),
                        Parameter.required("divisor", DIVISIBLE)),
                Set.of("floor"),
                (arguments, evaluator, context, refinements) -> remainderOf(
                        arguments.get(0), arguments.get(1),
                        refinements.contains("floor") ? Division.FLOORED : Division.EUCLIDEAN));

        define("shift-left", List.of(
                        Parameter.required("value", Set.of(Datatype.INTEGER)),
                        Parameter.required("bits", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> shifted(arguments, true));
        define("shift-right", List.of(
                        Parameter.required("value", Set.of(Datatype.INTEGER)),
                        Parameter.required("bits", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> shifted(arguments, false));

    }

    private enum Operation { ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER, MODULO }

    private enum Bitwise { AND, OR, XOR }

    /**
     * One of the short trigonometric names: radians in, decimals only.
     *
     * <p>The long-named twin takes degrees and accepts a whole number.
     * Neither is defined in terms of the other, because they are not the
     * same function.
     */
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

    /** What ABS measures: everything with a magnitude and a sign. */
    private static final Set<Datatype> MEASURABLE = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
            Datatype.MONEY, Datatype.TIME, Datatype.PAIR);

    /** What MOD divides: numbers, and the three things measured like them. */
    private static final Set<Datatype> DIVISIBLE = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
            Datatype.MONEY, Datatype.CHAR, Datatype.TIME);

    /**
     * A value's magnitude as a number, for the types MOD measures.
     *
     * <p>A character counts as its code point and a time as its
     * nanoseconds, which is what makes `#"a" %% 3` and `10:0 %% 3:0`
     * mean anything at all.
     */
    private static double asMagnitude(Value value) {
        return switch (value) {
            case CharacterValue character -> character.codepoint();
            case TimeValue time -> time.nanoseconds();
            default -> Comparison.asDouble(value);
        };
    }

    /**
     * The angle in radians, whichever way the caller wrote it.
     *
     * <p>{@code Trig_Value} converts by hand rather than calling the library,
     * and the range reduction it does first has no effect on the answer for an
     * ordinary angle. What matters is the constant: the C uses its own
     * {@code pi1} rather than the platform's.
     */
    private static double inRadians(Value angle, Set<String> refinements) {
        double given = Comparison.asDouble(angle);
        return refinements.contains("radians") ? given : Math.toRadians(given);
    }

    /**
     * A sine or cosine with the noise near zero taken out.
     *
     * <p>{@code if (fabs(dval) < DBL_EPSILON) dval = 0.0;} in both natives.
     * One step of the representation at 1.0, which is the smallest difference
     * a double can tell from nothing, so anything below it is nothing.
     */
    private static double withoutTheNoiseNearZero(double answer) {
        return Math.abs(answer) < Math.ulp(1.0) ? 0.0 : answer;
    }

    /**
     * A tangent, infinite at a right angle.
     *
     * <p>{@code if (Eq_Decimal(fabs(dval), pi1 / 2.0))} answers the infinity,
     * and {@code Eq_Decimal} allows ten steps of the representation. So "at a
     * right angle" is a question with an allowance rather than an exact test,
     * which is why {@code tangent 89.99999999999987} is 1.#INF and not the
     * very large finite number the hardware computes.
     */
    private static double tangentOf(double radians) {
        if (nearlyTheSame(Math.abs(radians), Math.PI / 2.0)) {
            return radians < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        return Math.tan(radians);
    }

    /**
     * Which of the three definitions of division a remainder follows.
     *
     * <p>Four names and three answers. REMAINDER, {@code %} and MOD are all
     * {@link #TRUNCATED}; MODULO and {@code %%} are {@link #EUCLIDEAN};
     * MODULO/FLOOR is {@link #FLOORED}. All four agree on every pair of
     * positive numbers, so a wrong pairing passes every test written without a
     * negative in it.
     */
    private enum Division {
        /** Sign follows the dividend, so {@code -7 % 3} is -1. */
        TRUNCATED,
        /** Never negative, so {@code -7 %% 3} is 2 and {@code -7 %% -3} is 2. */
        EUCLIDEAN,
        /** Sign follows the divisor, so {@code modulo/floor -7 -3} is -1. */
        FLOORED
    }

    /**
     * A remainder under one of the three definitions, ported from
     * {@code modulus} in {@code n-math.c}.
     *
     * <p>Two whole numbers stay whole all the way through, which matters at
     * the ends of the range where a double could not tell neighbouring whole
     * numbers apart. Everything else goes through a double, as the C does.
     */
    private static Value remainderOf(Value dividend, Value divisor, Division definition) {
        if (dividend instanceof IntegerValue whole && divisor instanceof IntegerValue by) {
            long dividedBy = by.magnitude();
            requireNonZero(dividedBy);
            long rest = whole.magnitude() % dividedBy;
            return IntegerValue.of(switch (definition) {
                case TRUNCATED -> rest;
                case EUCLIDEAN -> rest < 0 ? rest + Math.abs(dividedBy) : rest;
                case FLOORED -> rest != 0 && (rest < 0) != (dividedBy < 0)
                        ? rest + dividedBy
                        : rest;
            });
        }
        double first = asMagnitude(dividend);
        double second = asMagnitude(divisor);
        requireNonZero(second);
        if (definition == Division.TRUNCATED) {
            return likeTheDividend(dividend, first % second);
        }
        double by = definition == Division.EUCLIDEAN ? Math.abs(second) : second;
        double rest = ((first % by) + by) % by;
        return likeTheDividend(dividend, negligibleAgainstItsOperands(rest, first, by)
                ? 0.0
                : rest);
    }

    /**
     * Whether a remainder is too small to make any difference to the numbers
     * it came from, in which case both non-truncated definitions call it zero.
     *
     * <p>{@code if (almost_equal(a, a - m, 10) || almost_equal(b, b + m, 10))
     * m = 0.0;} -- the question is not whether the answer is small but
     * whether it is visible at the scale of its own operands. So
     * {@code modulo 562949953421311.25 1} is 0.0 even though the answer 0.25
     * is not small in absolute terms: it makes no difference to a dividend
     * that large.
     *
     * <p>MOD does not ask this, which is how the two are told apart:
     * {@code mod 562949953421311.25 1} is 0.25.
     *
     * <p>Ten steps rather than the twenty-one EQUAL? allows. The C passes the
     * number by hand here rather than going through {@code Eq_Decimal}'s
     * default, so the two allowances are separate numbers that happen to have
     * been the same once.
     */
    private static boolean negligibleAgainstItsOperands(
            double rest, double dividend, double divisor) {

        return nearlyTheSame(dividend, dividend - rest)
                || nearlyTheSame(divisor, divisor + rest);
    }

    private static final long STEPS_MODULUS_ALLOWS = 10;

    private static boolean nearlyTheSame(double first, double second) {
        return Comparison.looselyEqual(
                DecimalValue.of(first), DecimalValue.of(second), STEPS_MODULUS_ALLOWS);
    }

    private static Value likeTheDividend(Value dividend, double magnitude) {
        return switch (dividend) {
            case CharacterValue ignored -> CharacterValue.of((int) magnitude);
            case TimeValue ignored -> TimeValue.ofNanoseconds((long) magnitude);
            case MoneyValue ignored -> MoneyValue.of(
                    new BigDecimal((long) magnitude));
            case IntegerValue ignored -> IntegerValue.of((long) magnitude);
            default -> DecimalValue.of(magnitude);
        };
    }

    /**
     * A value's magnitude, keeping its datatype.
     *
     * <p>Negative zero comes back as itself: the sign of a zero is not
     * part of its magnitude, and R3 leaves it alone. And the most
     * negative whole number has no positive counterpart, so it overflows
     * rather than wrapping to itself.
     */
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

    /**
     * SHIFT without /LOGICAL: keeps the sign and loses no bit off the top.
     *
     * <p>Four cases, and the C spells each of them out. Shifting left, a count
     * of sixty-four or more raises unless the value is already zero; below
     * sixty-four, it raises when the magnitude would not fit. Shifting right, a
     * count of sixty-four or more repeats the sign bit -- so -1 for a negative
     * value and 0 for anything else, not zero for both; below sixty-four it is
     * an ordinary signed shift.
     *
     * <p>The one exception in the overflow check is the most negative whole
     * number, which is reachable: it is the only value with no positive
     * counterpart, so it is the only value the exception can be about.
     */
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

    /**
     * SHIFT/LOGICAL: moves the bits and refuses nothing.
     *
     * <p>Every case the plain form raises on, this answers, which is why the
     * two refinements of one native need separate code. A count of sixty-four
     * or more answers zero from either end, because the bits have all gone.
     */
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

    /**
     * The larger or smaller of two values, half by half when they are
     * pairs. Two pairs are compared on each axis separately, so the answer
     * may be a pair that neither argument was.
     */
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

    private static Value combined(Value left, Value right, Bitwise operation) {
        if (VectorMath.isVectorArithmetic(left, right)) {
            if (!(left instanceof VectorValue)) {
                throw notRelated(left, right);
            }
            return VectorMath.done(left, right, switch (operation) {
                case AND -> VectorMath.Operation.AND;
                case OR -> VectorMath.Operation.OR;
                case XOR -> VectorMath.Operation.XOR;
            });
        }
        if (left instanceof LogicValue leftTruth && right instanceof LogicValue rightTruth) {
            boolean ours = leftTruth.isTruthy();
            boolean theirs = rightTruth.isTruthy();
            return LogicValue.of(switch (operation) {
                case AND -> ours && theirs;
                case OR -> ours || theirs;
                case XOR -> ours ^ theirs;
            });
        }
        if (left instanceof PairValue leftPair) {
            return PairValue.of(
                    combinedBits(roundedHalfUp(leftPair.x()),
                            roundedHalfUp(firstHalfOf(right)), operation),
                    combinedBits(roundedHalfUp(leftPair.y()),
                            roundedHalfUp(secondHalfOf(right)), operation));
        }
        if (left instanceof TupleValue) {
            return tupleCombined(left, right, operation);
        }
        if (left instanceof BinaryValue first && right instanceof BinaryValue second) {
            return combinedOctets(first, second, operation);
        }
        return IntegerValue.of(combinedBits(
                wholeNumberOf(left, "and"), wholeNumberOf(right, "and"), operation));
    }

    /**
     * Two binaries combined octet by octet, as long as the longer of them.
     *
     * <p>{@code Xandor_Binary} walks the longer and wraps its index into the
     * shorter -- {@code if (i == mt) i = 0} -- so one octet against four is
     * that octet four times rather than one octet and three zeros. Which side
     * was written first makes no difference: the C picks the longer as the one
     * it walks before it looks at anything else, so all three operations come
     * out the same either way round.
     */
    private static Value combinedOctets(
            BinaryValue left, BinaryValue right, Bitwise operation) {

        BinaryValue longer = left.lengthFromHere() >= right.lengthFromHere() ? left : right;
        BinaryValue shorter = longer == left ? right : left;
        int cycle = shorter.lengthFromHere();
        int[] combined = new int[longer.lengthFromHere()];
        for (int at = 0; at < combined.length; at++) {
            int theirs = cycle == 0 ? 0 : shorter.storage().at(shorter.index() + at % cycle);
            combined[at] = (int) combinedBits(
                    longer.storage().at(longer.index() + at), theirs, operation) & 0xFF;
        }
        return BinaryValue.of(combined);
    }

    private static long combinedBits(long left, long right, Bitwise operation) {
        return switch (operation) {
            case AND -> left & right;
            case OR -> left | right;
            case XOR -> left ^ right;
        };
    }

    /**
     * Arithmetic with a character on the left, as {@code REBTYPE(Char)} does it.
     *
     * <p>The right operand becomes a plain number first -- a character gives its
     * codepoint, a whole number itself, a decimal its truncation, and anything
     * else is refused -- and then the operation runs on codepoints. The answer
     * is a character, so it has to be one: {@code if (IS_INVALID_CHAR(chr))
     * Trap1(RE_INVALID_CHAR, ...)} refuses a result past the last codepoint or
     * inside the surrogate range rather than wrapping it.
     */
    private static Value characterArithmetic(
            CharacterValue letter, Value right, Operation operation) {

        long other = switch (right) {
            case CharacterValue another -> another.codepoint();
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction -> (long) fraction.quantity();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "a character takes a character or a number, not a "
                            + right.datatype().literalSpelling());
        };
        long codepoint = letter.codepoint();
        long answered = switch (operation) {
            case ADD -> codepoint + other;
            case SUBTRACT -> codepoint - other;
            case MULTIPLY -> codepoint * other;
            case DIVIDE -> dividedBy(codepoint, other);
            case REMAINDER -> remainderOf(codepoint, other);
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "cannot use that on a character");
        };
        if (operation == Operation.SUBTRACT && right instanceof CharacterValue) {
            return IntegerValue.of(answered);
        }
        return CharacterValue.of(requireACodepoint(answered));
    }

    private static long dividedBy(long codepoint, long other) {
        if (other == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return codepoint / other;
    }

    private static long remainderOf(long codepoint, long other) {
        if (other == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
        return codepoint % other;
    }

    /**
     * A codepoint between one and the limit that a character can hold.
     *
     * <p>`do { chr = 1 + (Random_Int(secure) % chr); } while
     * (IS_INVALID_CHAR(chr));` -- the loop is there because the surrogate range
     * is inside the span being picked from and no character may hold one.
     */
    private int aValidCodepointUpTo(int limit) {
        while (true) {
            int picked = 1 + randomness.below(limit);
            boolean surrogate = picked >= 0xD800 && picked <= 0xDFFF;
            if (!surrogate && picked <= MAXIMUM_CODEPOINT) {
                return picked;
            }
        }
    }

    /**
     * Where a seed comes from, which is a different answer for every datatype.
     *
     * <p>Each {@code A_RANDOM} arm that accepts {@code /seed} decides for
     * itself what sixty-four bits to hand {@code Set_Random}, and no two of
     * them agree. A number seeds with itself. A decimal seeds with its IEEE
     * bit pattern rather than its value, so {@code random/seed 1.5} and
     * {@code random/seed 1} start different sequences. A string, a binary and
     * a tuple seed with a twenty-four bit checksum of their bytes. A time
     * seeds with its nanoseconds, and a date packs its year, its day of the
     * year and its time into one number. A pair seeds with the raw bits of
     * its two single-precision halves side by side, which is what the C's
     * union makes {@code VAL_INT64} read.
     *
     * <p>Logic is the odd one: true seeds from the clock and false seeds with
     * one, so {@code random/seed true} is the way a script asks for a sequence
     * nobody can predict.
     *
     * <p>A block and a vector have no arm for it at all and answer
     * {@code bad-refines}, which is the C declining rather than failing.
     */
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

    /**
     * One character out of a string, the way {@code /only} picks it.
     *
     * <p>{@code index += Random_Int(secure) % (tail - index)} followed by a
     * step back to a character boundary, and both halves are byte offsets
     * into the UTF-8 the string is stored as. That makes the pick uneven on
     * purpose or by accident: every byte is equally likely to be landed on,
     * so a character written in three bytes comes up three times as often as
     * one written in a single byte. Measured on a real Rebol before this was
     * written -- six thousand picks out of {@code "aéb"} gave the two
     * one-byte letters about fifteen hundred each and the two-byte letter
     * about three thousand.
     */
    private Value oneCharacterPickedAtRandom(StringValue text) {
        byte[] octets = text.text().getBytes(StandardCharsets.UTF_8);
        if (octets.length == 0) {
            return NoneValue.none();
        }
        int at = steppedBackToACharacterBoundary(octets, randomness.below(octets.length));
        return CharacterValue.of(
                new String(octets, at, octets.length - at, StandardCharsets.UTF_8)
                        .codePointAt(0));
    }

    /**
     * One octet out of a binary, which shares the string's arm and its quirk.
     *
     * <p>The step back to a character boundary is in the same {@code case}
     * label, with nothing to say a binary is not text, so an octet between
     * {@code 80} and {@code BF} is never the answer whenever an octet below
     * it could be stepped back to. {@code random/only #{4180}} on a real
     * Rebol answers 65 six thousand times out of six thousand.
     */
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

    /**
     * A whole number between one and the limit, keeping its sign.
     *
     * <p>{@code Random_Range} in {@code f-random.c}, and the two lines that
     * are easy to leave out are the two that Rebol's own test measures.
     *
     * <p>The first is the rejection loop. Taking the remainder of a number
     * drawn from nought up to two to the sixty-second gives a distribution
     * that leans towards the low end whenever the limit does not divide that
     * range evenly, and the lean is large: over a limit two thirds of the
     * range, the bottom half of the answers come up twice as often as the top
     * half. So the C throws away every draw above the last exact multiple of
     * the limit and draws again. Rebol's test asks for ten thousand numbers
     * under such a limit and asserts that half of them land in the top half,
     * which is a statement that the rejection is there.
     *
     * <p>The second is the refusal. A limit past two to the sixty-second is
     * larger than the generator's whole range, so no rejection limit exists
     * for it, and the C answers {@code overflow} rather than a number it
     * cannot draw evenly.
     */
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

    /**
     * A fraction between zero and one.
     *
     * <p>{@code Random_Dec} divides one of the same numbers by the modulus, so
     * a seeded decimal follows the same sequence as everything else.
     */
    private double randomFraction() {
        return (double) randomness.next() / (double) GENERATOR_RANGE;
    }

    /** {@code #define MM ((REBI64)1<<62)}, the modulus the generator counts to. */
    private static final long GENERATOR_RANGE = 1L << 62;

    /**
     * A date with every part randomised, and only the year drawn against the
     * date it was asked about.
     *
     * <p>{@code year = Random_Range(year, num); month = Random_Range(12, num);
     * day = Random_Range(31, num);} -- so the month and the day are drawn over
     * their whole ranges and the date being randomised has no say in either.
     * Drawing them against its own month and day instead meant an August date
     * could never come back in September, and a real Rebol answers February
     * for one readily.
     *
     * <p>The C then falls into {@code Normalize_Date}, which carries a day
     * past the end of its month into the next one, and the month and day it
     * carries are counted from zero where the drawn numbers start at one. So
     * the first of January plus that many months and that many days is the
     * same walk, and it can only land on a date that exists.
     *
     * <p>Without the carry, a February drawn together with a thirtieth threw
     * an {@code IllegalArgumentException} out of the value class and stopped
     * the interpreter. Rebol's own suite asks for a hundred random dates in a
     * row and this went unnoticed until the day of the month made it likely.
     */
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
                                randomLongUpTo(NANOSECONDS_A_DAY))),
                        when.zoneMinutes());
    }

    private static final int MONTHS_A_YEAR = 12;
    private static final int LONGEST_MONTH = 31;

    /** A number as a codepoint, refusing one no character can hold. */
    private static int requireACodepoint(long wanted) {
        boolean surrogate = wanted >= 0xD800 && wanted <= 0xDFFF;
        if (wanted < 0 || wanted > MAXIMUM_CODEPOINT || surrogate) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR,
                    wanted + " is not a character");
        }
        return (int) wanted;
    }

    /**
     * Arithmetic where one side is a vector, and the refusal where it is on
     * the wrong side.
     *
     * <p>A number on the left only reaches the vector for ADD and MULTIPLY.
     * The other operations never get there, because a number's own arm is what
     * dispatches and it forwards only the two whose answer does not depend on
     * which side is which. {@code 10 - v} is therefore not "v subtracted from
     * ten" but no operation at all.
     */
    private static Value vectorArithmetic(Value left, Value right, Operation operation) {
        VectorMath.Operation asked = switch (operation) {
            case ADD -> VectorMath.Operation.ADD;
            case SUBTRACT -> VectorMath.Operation.SUBTRACT;
            case MULTIPLY -> VectorMath.Operation.MULTIPLY;
            case DIVIDE -> VectorMath.Operation.DIVIDE;
            case REMAINDER -> VectorMath.Operation.REMAINDER;
            default -> null;
        };
        boolean orderMatters = asked != VectorMath.Operation.ADD
                && asked != VectorMath.Operation.MULTIPLY;
        if (asked == null || (!(left instanceof VectorValue) && orderMatters)) {
            throw notRelated(left, right);
        }
        Value other = left instanceof VectorValue ? right : left;
        if (!(other instanceof VectorValue)
                && !(other instanceof IntegerValue) && !(other instanceof DecimalValue)) {
            throw notRelated(left, right);
        }
        return VectorMath.done(left, right, asked);
    }

    private static Raised notRelated(Value left, Value right) {
        return Raised.of(EvaluationFailure.NOT_RELATED,
                WordValue.of(left.datatype().literalSpelling()),
                WordValue.of(right.datatype().literalSpelling()));
    }

    /**
     * Integer arithmetic raises on overflow rather than wrapping. The JVM
     * wraps silently, which is the worst available behaviour: a wrong answer
     * that looks like a right one.
     */
    private static Value arithmetic(List<Value> arguments, Operation operation) {
        Value left = arguments.get(0);
        Value right = arguments.get(1);

        if (VectorMath.isVectorArithmetic(left, right)) {
            return vectorArithmetic(left, right, operation);
        }
        if (left instanceof CharacterValue letter) {
            return characterArithmetic(letter, right, operation);
        }
        if (left instanceof PairValue || right instanceof PairValue) {
            return pairArithmetic(left, right, operation);
        }
        if (left instanceof TupleValue || right instanceof TupleValue) {
            return tupleArithmetic(left, right, operation);
        }
        if (left instanceof DateValue || right instanceof DateValue) {
            return dateArithmetic(left, right, operation);
        }
        if (right instanceof CharacterValue letter
                && (left instanceof IntegerValue || left instanceof DecimalValue)) {
            Value asNumber = left instanceof IntegerValue
                    ? IntegerValue.of(letter.codepoint())
                    : DecimalValue.of(letter.codepoint());
            Value plainer = left instanceof DecimalValue quantity
                    ? DecimalValue.of(quantity.quantity())
                    : left;
            return arithmetic(List.of(plainer, asNumber), operation);
        }
        if (left instanceof MoneyValue amount) {
            return moneyArithmetic(amount, right, operation);
        }
        if (left instanceof TimeValue || right instanceof TimeValue) {
            return timeArithmetic(left, right, operation);
        }
        if (right instanceof MoneyValue) {
            return moneyArithmetic(MoneyValue.of(asBigDecimal(left)), right, operation);
        }
        if (left instanceof IntegerValue leftInteger && right instanceof IntegerValue rightInteger) {
            return integerArithmetic(leftInteger.magnitude(), rightInteger.magnitude(), operation);
        }
        return decimalArithmetic(Comparison.asDouble(left), Comparison.asDouble(right), operation, true);
    }

    private static Value integerArithmetic(long left, long right, Operation operation) {
        try {
            return switch (operation) {
                case ADD -> IntegerValue.of(Math.addExact(left, right));
                case SUBTRACT -> IntegerValue.of(Math.subtractExact(left, right));
                case MULTIPLY -> IntegerValue.of(Math.multiplyExact(left, right));
                case DIVIDE -> {
                    requireNonZero(right);
                    yield left % right == 0
                            ? IntegerValue.of(left / right)
                            : DecimalValue.of((double) left / right);
                }
                case REMAINDER -> {
                    requireNonZero(right);
                    yield IntegerValue.of(left % right);
                }
                case MODULO -> {
                    requireNonZero(right);
                    long rest = left % right;
                    yield IntegerValue.of(rest < 0 ? rest + Math.abs(right) : rest);
                }
            };
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW, overflowed.getMessage());
        }
    }

    private static Value decimalArithmetic(double left, double right, Operation operation) {
        return decimalArithmetic(left, right, operation, false);
    }

    private static Value decimalArithmetic(
            double left, double right, Operation operation, boolean infinitiesAllowed) {
        return switch (operation) {
            case ADD -> DecimalValue.of(left + right);
            case SUBTRACT -> DecimalValue.of(left - right);
            case MULTIPLY -> DecimalValue.of(left * right);
            case DIVIDE -> {
                if (!infinitiesAllowed) {
                    requireNonZero(right);
                }
                yield DecimalValue.of(left / right);
            }
            case REMAINDER -> {
                requireNonZero(right);
                yield DecimalValue.of(left % right);
            }
            case MODULO -> {
                requireNonZero(right);
                double rest = left % right;
                yield DecimalValue.of(rest < 0 ? rest + Math.abs(right) : rest);
            }
        };
    }

    /**
     * Each half is worked out on its own. Where one side is a single
     * number rather than a pair, that number applies to both halves, so
     * {@code 1x2 + 1} is {@code 2x3}. This is the only place in the
     * language where an operand is spread across a value rather than
     * widened to meet it.
     */
    private static Value pairArithmetic(Value left, Value right, Operation operation) {
        requireAPairOrAPlainNumber(left);
        requireAPairOrAPlainNumber(right);
        if (operation == Operation.DIVIDE || operation == Operation.REMAINDER
                || operation == Operation.MODULO) {
            requireNonZero(firstHalfOf(right));
            requireNonZero(secondHalfOf(right));
        }
        return PairValue.of(
                halfArithmetic(firstHalfOf(left), firstHalfOf(right), operation),
                halfArithmetic(secondHalfOf(left), secondHalfOf(right), operation));
    }

    /**
     * Arithmetic on a pair takes a pair or a plain number and nothing else.
     *
     * <p>{@code REBTYPE(Pair)} names three datatypes for the other side --
     * pair, integer, and decimal or percent -- and calls
     * {@code Trap_Math_Args} for anything else. A money and a time are the
     * two that look like they ought to work: both are numbers elsewhere in
     * the language, and neither is a number here.
     */
    private static void requireAPairOrAPlainNumber(Value side) {
        if (side instanceof PairValue
                || side instanceof IntegerValue
                || side instanceof DecimalValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                side.datatype().literalSpelling() + " does not go with pair arithmetic");
    }

    private static double halfArithmetic(double left, double right, Operation operation) {
        return ((DecimalValue) decimalArithmetic(left, right, operation)).quantity();
    }

    private static double firstHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.x() : Comparison.asDouble(value);
    }

    private static double secondHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.y() : Comparison.asDouble(value);
    }

    /**
     * Arithmetic on a tuple is arithmetic on each octet, and every octet
     * clamps to a byte.
     *
     * <p>Clamping rather than wrapping is the part worth knowing:
     * {@code 255.255.255 + 1} is unchanged, not {@code 0.0.0}. An
     * implementation that wraps is right for every value except the ones
     * at the edge, which are exactly the ones a colour or a version
     * number reaches. Nothing here ever raises for range.
     *
     * <p>A single number applies to every octet, as it does for a pair.
     * Where the other side is a tuple, the longer of the two lengths wins
     * and the shorter one contributes zeros.
     *
     * <p>The loop is {@code REBTYPE(Tuple)} in {@code t-tuple.c}. Two
     * guards in it are not obvious. A zero octet is left alone by a
     * multiplication whatever the factor, and a factor above 255
     * saturates before being multiplied out rather than after, so no
     * intermediate ever leaves the range of a machine integer.
     */
    private static Value tupleArithmetic(Value left, Value right, Operation operation) {
        return octetByOctet(left, right, (octet, against, fractional) ->
                switch (operation) {
                    case ADD -> octet + (long) against;
                    case SUBTRACT -> octet - (long) against;
                    case MULTIPLY -> {
                        if (octet == 0) {
                            yield 0;
                        }
                        if (against > 255) {
                            yield 255;
                        }
                        yield fractional ? (long) (octet * against) : octet * (long) against;
                    }
                    case DIVIDE -> {
                        if (against == 0) {
                            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
                        }
                        yield fractional
                                ? (long) roundedHalfAwayFromZero(octet / against)
                                : octet / (long) against;
                    }
                    case REMAINDER, MODULO -> {
                        if ((long) against == 0) {
                            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
                        }
                        yield octet % (long) against;
                    }
                });
    }

    /** AND, OR and XOR over a tuple, which run on the whole integer. */
    private static Value tupleCombined(Value left, Value right, Bitwise operation) {
        return octetByOctet(left, right, (octet, against, fractional) ->
                combinedBits(octet, (long) against, operation));
    }

    /** One octet against one number, before the clamp every branch shares. */
    @FunctionalInterface
    private interface OctetWork {
        long against(long octet, double amount, boolean fractional);
    }

    /**
     * The loop every tuple operation shares: octet by octet, then clamped.
     *
     * <p>Where the right side is a tuple each octet meets its opposite
     * number and the longer of the two lengths wins. Where it is a single
     * number, that number meets every octet.
     */
    private static Value octetByOctet(Value left, Value right, OctetWork work) {
        if (!(left instanceof TupleValue ours)) {
            return raiseCannotUse(left, "tuple arithmetic");
        }
        TupleValue theirs = right instanceof TupleValue tuple ? tuple : null;
        if (theirs == null && !Comparison.isNumeric(right)) {
            return raiseCannotUse(right, "tuple arithmetic");
        }
        int width = theirs == null
                ? ours.segmentCount()
                : Math.max(ours.segmentCount(), theirs.segmentCount());
        boolean fractional = right.datatype() == Datatype.DECIMAL
                || right.datatype() == Datatype.PERCENT;
        double amount = theirs == null ? Comparison.asDouble(right) : 0;

        int[] answer = new int[width];
        for (int at = 1; at <= width; at++) {
            long worked = work.against(ours.octetAt(at),
                    theirs == null ? amount : theirs.octetAt(at), fractional);
            answer[at - 1] = (int) Math.max(0, Math.min(255, worked));
        }
        return TupleValue.of(answer);
    }

    /** Half away from zero, which is what {@code Round_Dec} does by default. */
    private static double roundedHalfAwayFromZero(double amount) {
        return amount < 0 ? -Math.round(-amount) : Math.round(amount);
    }

    /**
     * A value a fraction of the way from one to another.
     *
     * <p>The fraction is clamped to nought and one, so LERP never walks
     * past either end however far the fraction reaches. Both ends must be
     * the same kind of thing: a number with a number, a tuple with a
     * tuple, a pair with a pair, and nothing else. A tuple against a pair
     * is a type mismatch rather than something to widen.
     *
     * <p>Not built out of the ordinary arithmetic, which would round each
     * octet of a tuple and clamp it. {@code REBNATIVE(lerp)} casts each
     * octet to a byte instead, so a walk that lands on 83.5 gives 83.
     */
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

    /**
     * The first few octets turned round, leaving the rest where they were.
     *
     * <p>A plain REVERSE turns round every kept octet, so a tuple made
     * from the string "1" keeps 1.0.0 and reverses to 0.0.1. The zeros
     * behind the kept ones take no part, which is what stops a short
     * tuple from growing when it is reversed.
     */
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

    /** Every kept octet flipped, which is what COMPLEMENT does to a tuple. */
    private static Value newImageEachChannelFlipped(ImageValue image) {
        ImageStorage flipped = ImageStorage.of(
                image.storage().wide(), image.storage().high());
        for (int pixel = 1; pixel <= image.storage().length(); pixel++) {
            int[] channels = image.storage().pixelAt(pixel);
            flipped.setColourAt(pixel,
                    ~channels[0] & 0xFF, ~channels[1] & 0xFF, ~channels[2] & 0xFF);
            flipped.setAlphaAt(pixel, ~channels[3] & 0xFF);
        }
        return new ImageValue(flipped, 1);
    }

    private static Value newBytesEachFlipped(BinaryValue bytes) {
        byte[] flipped = bytes.octetsFromHere();
        for (int at = 0; at < flipped.length; at++) {
            flipped[at] = (byte) ~flipped[at];
        }
        return new BinaryValue(new BinaryStorage(flipped), 1);
    }

    private static Value flippedOctets(TupleValue tuple) {
        int[] octets = tuple.segments();
        for (int at = 0; at < octets.length; at++) {
            octets[at] = 255 - octets[at];
        }
        return TupleValue.of(octets);
    }

    /** Each octet replaced by one no greater than itself. */
    private Value randomisedOctets(TupleValue tuple) {
        int[] octets = tuple.segments();
        for (int at = 0; at < octets.length; at++) {
            if (octets[at] != 0) {
                octets[at] = randomness.belowWithoutNarrowing(octets[at] + 1);
            }
        }
        return TupleValue.of(octets);
    }

    /**
     * Each half randomised on its own, between one and that half.
     *
     * <p>{@code A_RANDOM} calls {@code Random_Range((REBINT)x1, ...)} on each
     * half, so the half is read as a machine integer before anything else
     * happens. An infinite half becomes a number that way rather than
     * refusing, which is the only reason
     * {@code random as-pair 1e300 -1e300} can be a pair at all -- and it is
     * why Rebol's own assertion about it asks only that the answer is
     * finite.
     */
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

    /**
     * Arithmetic on a time, in nanoseconds throughout.
     *
     * <p>A bare number is seconds -- not minutes and not hours, which is
     * the guess to get wrong. Scaling by a number is the exception:
     * {@code 1:00 * 2} doubles the duration rather than adding two
     * seconds, because there is nothing else multiplying by a count
     * could mean.
     *
     * <p>A time may be negative. It is a duration rather than a clock
     * reading, so there is no floor at midnight.
     */
    private static Value timeArithmetic(Value left, Value right, Operation operation) {
        if (operation == Operation.MULTIPLY || operation == Operation.DIVIDE) {
            long scaled = (long) ((DecimalValue) decimalArithmetic(
                    nanosecondsOf(left), Comparison.asDouble(scalarOf(right)), operation)).quantity();
            return TimeValue.ofNanoseconds(scaled);
        }
        double worked = ((DecimalValue) decimalArithmetic(
                nanosecondsOf(left), nanosecondsOf(right), operation)).quantity();
        return TimeValue.ofNanoseconds((long) worked);
    }

    private static final long NANOSECONDS_A_SECOND = 1_000_000_000L;
    private static final long NANOSECONDS_A_DAY = 86_400L * NANOSECONDS_A_SECOND;

    /** A number counts as seconds when it meets a time. */
    private static double nanosecondsOf(Value value) {
        return value instanceof TimeValue time
                ? time.nanoseconds()
                : Comparison.asDouble(value) * NANOSECONDS_A_SECOND;
    }

    private static Value scalarOf(Value value) {
        return value instanceof TimeValue time
                ? DecimalValue.of(time.nanoseconds())
                : value;
    }

    /**
     * Arithmetic on a date, where three kinds of right-hand side mean three
     * different units.
     *
     * <p>A whole number is days and moves the calendar alone, so the clock and
     * the zone come through untouched. A time is a duration and moves the
     * clock, carrying into the day when it runs past midnight. A decimal is a
     * fraction of a day and moves the clock as well, which is why
     * {@code 20-Sep-2021/12:00 + 1.9} lands two days later at 9:36 rather than
     * one day later at noon.
     *
     * <p>The C reaches each of those through a separate {@code type ==} arm and
     * ends every one of them at {@code Normalize_Date(day, month, year, tz)},
     * with the {@code tz} it read off the original. Losing the time and the
     * zone here turned {@code + 1} into a bare day, and reading a time as a
     * count of days put {@code 20-Sep-2021 + 1:00} five years out.
     *
     * <p>Subtracting two dates is the odd one out and answers a whole number,
     * because the difference of two moments is a span. {@code Diff_Date} counts
     * days and never looks at the clock, so two dates two hours apart differ by
     * nothing at all.
     */
    private static Value dateArithmetic(Value left, Value right, Operation operation) {
        if (left instanceof DateValue from && right instanceof DateValue to) {
            if (operation != Operation.SUBTRACT) {
                return raiseCannotUse(left, "date arithmetic");
            }
            return IntegerValue.of(dayNumberOf(from) - dayNumberOf(to));
        }
        DateValue moment = left instanceof DateValue date ? date : (DateValue) right;
        Value span = left instanceof DateValue ? right : left;
        int sign = operation == Operation.SUBTRACT ? -1 : 1;
        return span.datatype() == Datatype.INTEGER
                ? dateMovedByDays(moment, sign * (long) Comparison.asDouble(span))
                : dateMovedByClock(moment, sign * clockShiftOf(span));
    }

    /**
     * How far a span moves a date's clock, in nanoseconds.
     *
     * <p>A time is itself. Anything else is a fraction of a day, which is the
     * one place a bare number beside a date is not a count of days:
     * {@code + 0.5} is twelve hours on.
     */
    private static long clockShiftOf(Value span) {
        return span instanceof TimeValue duration
                ? duration.nanoseconds()
                : (long) (Comparison.asDouble(span) * NANOSECONDS_A_DAY);
    }

    /** The same moment on a different day, clock and zone carried across. */
    private static DateValue dateMovedByDays(DateValue moment, long days) {
        java.time.LocalDate shifted =
                java.time.LocalDate.ofEpochDay(dayNumberOf(moment) + days);
        return new DateValue(shifted.getYear(), shifted.getMonthValue(),
                shifted.getDayOfMonth(), moment.timeOfDay(), moment.zoneMinutes());
    }

    /**
     * A date moved along its own clock, carrying into the day either way.
     *
     * <p>A date with no time counts as its midnight and comes back carrying
     * one, which is {@code if (secs == NO_TIME) secs = 0} in the C: adding an
     * hour to a bare day gives {@code 20-Sep-2021/1:00} rather than the day
     * back unchanged.
     */
    private static DateValue dateMovedByClock(DateValue moment, long nanoseconds) {
        long shifted = moment.timeOfDay().map(TimeValue::nanoseconds).orElse(0L)
                + nanoseconds;
        java.time.LocalDate day = java.time.LocalDate.ofEpochDay(
                dayNumberOf(moment) + Math.floorDiv(shifted, NANOSECONDS_A_DAY));
        return new DateValue(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                Optional.of(TimeValue.ofNanoseconds(
                        Math.floorMod(shifted, NANOSECONDS_A_DAY))),
                moment.zoneMinutes());
    }

    /**
     * DIFFERENCE between two dates, which is a span of time rather than a set.
     *
     * <p>The one pairing in the set operations that is not about membership at
     * all. It reads the other way round from subtraction -- {@code difference
     * 1-Jan 2-Jan} is minus a day where {@code 2-Jan - 1-Jan} is one -- and it
     * answers a time! rather than a count of days.
     */
    private static Value timeBetween(DateValue from, DateValue to) {
        long days = dayNumberOf(from) - dayNumberOf(to);
        return TimeValue.ofNanoseconds(days * 24L * 60L * 60L * 1_000_000_000L);
    }

    private static long dayNumberOf(DateValue date) {
        return java.time.LocalDate.of(date.year(), date.month(), date.day()).toEpochDay();
    }

    /**
     * Arithmetic on a money, with whatever the other side turns out to be.
     *
     * <p>{@code REBTYPE(Money)} widens the right side and then does the sum
     * in {@code deci}, so the answer is always a money -- there is no branch
     * out of the switch that changes the datatype. {@code $4 / $4} is $1 and
     * not the plain 1 the division suggests.
     */
    private static Value moneyArithmetic(MoneyValue amount, Value other, Operation operation) {
        return withinTheDeciRange((MoneyValue) moneyArithmetic(
                amount.amount(), widenedToMeetMoney(other, operation), operation));
    }

    /**
     * The other side of a money sum, as a decimal amount.
     *
     * <p>Four datatypes widen and one is a special case. An integer, a
     * decimal, a percent and a money all become the amount they name. A time
     * becomes its count of hours, and only for a multiplication: the C's test
     * is {@code IS_TIME(arg) && action == A_MULTIPLY}, so the same argument
     * is taken by one operation and refused by the other four.
     *
     * <p>Hours rather than seconds. {@code VAL_TIME(arg) * NANO / 3600.0}
     * turns nanoseconds into seconds and then into hours, so 1:30:0 is 1.5
     * and {@code $5 * 1:30:0} is $7.5. Reading it as seconds gives $27000 --
     * a wage calculation wrong by a factor of 3600, and wrong quietly.
     */
    private static BigDecimal widenedToMeetMoney(Value other, Operation operation) {
        if (other instanceof TimeValue span) {
            if (operation != Operation.MULTIPLY) {
                throw Raised.of(EvaluationFailure.NOT_RELATED,
                        "only multiplication takes a time on the right of a money");
            }
            return BigDecimal.valueOf(
                    (double) span.nanoseconds() / NANOSECONDS_AN_HOUR);
        }
        if (other instanceof MoneyValue
                || other instanceof IntegerValue
                || other instanceof DecimalValue) {
            return asBigDecimal(other);
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                other.datatype().literalSpelling() + " does not go with money arithmetic");
    }

    private static final double NANOSECONDS_AN_HOUR = 3_600_000_000_000.0;

    private static Value moneyArithmetic(BigDecimal left, BigDecimal right, Operation operation) {
        return switch (operation) {
            case ADD -> MoneyValue.of(left.add(right));
            case SUBTRACT -> MoneyValue.of(left.subtract(right));
            case MULTIPLY -> MoneyValue.of(left.multiply(right, MoneyValue.ARITHMETIC));
            case DIVIDE -> {
                requireNonZero(right.doubleValue());
                yield MoneyValue.of(left.divide(right, MoneyValue.ARITHMETIC));
            }
            case REMAINDER -> {
                requireNonZero(right.doubleValue());
                yield MoneyValue.of(left.remainder(right, MoneyValue.ARITHMETIC));
            }
            case MODULO -> {
                requireNonZero(right.doubleValue());
                BigDecimal rest = left.remainder(right, MoneyValue.ARITHMETIC);
                yield MoneyValue.of(rest.signum() < 0 ? rest.add(right.abs()) : rest);
            }
        };
    }

    private static void requireNonZero(double divisor) {
        if (divisor == 0.0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    private static BigDecimal asBigDecimal(Value value) {
        return switch (value) {
            case MoneyValue money -> money.amount();
            case IntegerValue integer -> BigDecimal.valueOf(integer.magnitude());
            case DecimalValue decimal -> BigDecimal.valueOf(decimal.quantity());
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
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

    /**
     * One equality native: the strictness it asks about, and whether it
     * reports the answer or its opposite. Equality takes any value, unset
     * included, where an ordering refuses unset at the argument check.
     */
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
                        case WordValue named when named.datatype() == Datatype.WORD
                                || named.datatype() == Datatype.GET_WORD ->
                                evaluator.valueOfWordIn(named, context);
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
                        Evaluator.Step step = evaluator.evaluateNextOrRaise(
                                at, evaluator.systemContext());
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
                        Evaluator.Step step = evaluator.evaluateNextOrRaise(
                                at, evaluator.systemContext());
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

    /**
     * Natives that evaluate a block do so in the context that block already
     * carries, because binding happened when the block was made rather than
     * when it is run.
     */

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
        return new FunctionValue(
                spec,
                body,
                FunctionSpec.parametersIn(spec),
                FunctionSpec.localNamesIn(spec),
                context);
    }

    private void defineObjects() {
        define("make", takesAnything("prototype", "body"),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT
                            && arguments.get(1) instanceof NoneValue ->
                            raiseBadMakeArg(arguments.get(1), "object!");
                    case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT
                            && !(arguments.get(1) instanceof BlockValue) ->
                            makeObject(evaluator, context, Optional.empty(),
                                    BlockValue.block(List.of()));
                    case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT ->
                            makeObject(evaluator, context, Optional.empty(),
                                    (BlockValue) arguments.get(1));
                    case ObjectValue prototype when arguments.get(1) instanceof ObjectValue other ->
                            mergedObject(prototype, other, context);
                    case ObjectValue prototype ->
                            makeObject(evaluator, context, Optional.of(prototype),
                                    (BlockValue) arguments.get(1));
                    case DatatypeValue wanted when wanted.represents() == Datatype.MAP ->
                            mapMadeFrom(arguments.get(1));
                    case DatatypeValue wanted when wanted.represents() == Datatype.BITSET ->
                            bitsetOf(arguments.get(1));
                    case DatatypeValue wanted when wanted.represents() == Datatype.PAIR ->
                            asPair(arguments.get(1));
                    case DatatypeValue wanted when wanted.represents() == Datatype.FUNCTION ->
                            functionFrom(arguments.get(1), context);
                    case DatatypeValue wanted when wanted.represents() == Datatype.CLOSURE ->
                            functionFrom(arguments.get(1), context)
                                    instanceof FunctionValue made
                                    ? made.asClosure()
                                    : NoneValue.none();
                    case DatatypeValue wanted when wanted.represents() == Datatype.ERROR ->
                            errorFromSpec(arguments.get(1), evaluator, context);
                    case ErrorValue prototype
                            when arguments.get(1) instanceof StringValue ->
                            errorFromSpec(arguments.get(1), evaluator, context);
                    case DatatypeValue wanted when wanted.represents() == Datatype.MODULE ->
                            moduleFromSpec(arguments.get(1), evaluator, context);
                    case NativeValue original when arguments.get(1) instanceof BlockValue given ->
                            derivedFunction(original, given);
                    case FunctionValue original when arguments.get(1) instanceof BlockValue given ->
                            derivedFunction(original, given);
                    case SeriesValue prototype ->
                            makeOfDatatype(DatatypeValue.of(prototype.datatype()),
                                    arguments.get(1), evaluator, context);
                    case EventValue prototype -> EventPath.made(prototype,
                            arguments.get(1),
                            value -> simpleValueOf(value, evaluator, context));
                    case StructValue prototype ->
                            structLikeThePrototype(prototype, arguments.get(1), evaluator);
                    case DatatypeValue wanted ->
                            makeOfDatatype(wanted, arguments.get(1), evaluator, context);
                    default -> makeOfDatatype(
                            DatatypeValue.of(arguments.getFirst().datatype()),
                            arguments.get(1), evaluator, context);
                });

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
                    for (Value named : ((BlockValue) arguments.get(0)).remaining()) {
                        if (!(named instanceof WordValue word)) {
                            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                    "use names words, not "
                                            + named.datatype().literalSpelling());
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
                    while (callee instanceof NativeValue named
                            && named.nativeName().equals("do")
                            && !supplied.isEmpty()
                            && supplied.getFirst().datatype().isAnyFunction()) {
                        callee = supplied.removeFirst();
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
                    Value held = evaluator.evaluateOrRaise(
                            (BlockValue) arguments.get(0), context);
                    if (!held.isTruthy()) {
                        throw Raised.of(EvaluationFailure.ASSERT_FAILED,
                                Molder.mold(arguments.get(0)) + " did not hold");
                    }
                    return LogicValue.of(true);
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
                        found.removeIf(word -> word instanceof WordValue named
                                && known.contains(named.canonical()));
                    }
                    if (refinements.contains("as")) {
                        Value wanted = argumentFor(
                                "as", List.of("ignore", "as"), arguments, refinements, 1);
                        if (!(wanted instanceof DatatypeValue named)
                                || !ANY_WORD_DATATYPES.contains(named.represents())) {
                            throw Raised.of(EvaluationFailure.BAD_FUNC_ARG, "as");
                        }
                        found.replaceAll(word -> word instanceof WordValue spelt
                                ? spelt.as(named.represents())
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
                            Binder.bind((BlockValue) arguments.get(1), inside), inside);
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
                    case ObjectValue object -> object.context().slots().stream()
                            .anyMatch(ContextSlot::isProtected);
                    case WordValue word -> word.isBound()
                            && word.binding().knows(word.canonical())
                            && word.binding().slotFor(word.canonical()).isProtected();
                    default -> false;
                }));

        define("unbind", List.of(Parameter.required("word")),
                Set.of("deep"),
                (arguments, evaluator, context, refinements) ->
                        unbound(arguments.get(0), refinements.contains("deep")));

        define("bind", List.of(
                        Parameter.required("word"),
                        Parameter.required("target")),
                Set.of("copy", "only", "new", "set"),
                (arguments, evaluator, context, refinements) -> {
                    Context target = arguments.get(1) instanceof WordValue named
                            ? boundContextOf(named)
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
                        if (!target.knows(word.canonical())) {
                            throw Raised.of(EvaluationFailure.NOT_IN_CONTEXT,
                                    word.spelling());
                        }
                        return word.boundTo(target.holderOf(word.canonical()));
                    }
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseWrongArgument(arguments.get(0), "bind", "word or block");
                    }
                    if (refinements.contains("new") || refinements.contains("set")) {
                        defineFreshWordsOf(block, target, refinements.contains("set"));
                    }
                    return refinements.contains("copy")
                            ? Binder.bind(block, target)
                            : Binder.bindInPlace(block, target);
                });
    }

    /**
     * Builds an object: a context of its own, hanging beneath where it was
     * written so a word it does not define still means what it meant there.
     *
     * <p>Its fields are the set-words in its body, defined before the body
     * runs so that a function in it can see a field declared after it. The
     * body is rebound to the new context and then evaluated, which is why a
     * later field can be computed from an earlier one.
     */
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

        evaluator.evaluateOrRaise(Binder.bind(body, fields), fields);
        return built;
    }

    /**
     * One object holding the prototype's fields with another's written
     * over them, and that other's extra fields added.
     *
     * <p>Every method is rehomed to the result, so it reads the merged
     * values rather than the ones it was written against. A method left
     * closed over its original would answer from the wrong object and,
     * worse, write into it when called on this one.
     *
     * <p>The order matters only in that both objects' fields are in place
     * before any method is asked anything: a method the second object
     * brought may need a field the prototype has never heard of.
     */
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

    /**
     * A value cloned into a new object, with every word bound to the old
     * object rebound to the new one -- and only those words.
     *
     * <p>{@code Copy_Deep_Values} with {@code TS_CLONE}, then
     * {@code Rebind_Block} whose condition is {@code VAL_WORD_FRAME(data) ==
     * src_frame}: series and maps are copied so the clone shares no storage,
     * and a word bound anywhere else -- the library, an enclosing function --
     * keeps the binding it was written with. Rebinding by name instead
     * captured globals whose names an object happened to share, so a cloned
     * method read the object's field where its author wrote the library's.
     */
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
            case FunctionValue function -> new FunctionValue(
                    function.spec(),
                    (BlockValue) clonedAndRebound(function.body(), from, into),
                    function.parameters(), function.localNames(), into);
            default -> value;
        };
    }

    /** The set-words in a body, which are the fields the object will have. */
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
                            last = oneRound(evaluator, body, evaluator.systemContext());
                        }
                    } catch (LoopSignal stopped) {
                        return stopped.answer();
                    }
                    return last;
                });

        define("repeat", List.of(
                        Parameter.softQuoted("counter"),
                        Parameter.required("count", Set.of(Datatype.INTEGER, Datatype.PAIR)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    WordValue counter = (WordValue) arguments.get(0);
                    BlockValue body = (BlockValue) arguments.get(2);
                    if (arguments.get(1) instanceof PairValue grid) {
                        return repeatedOverGrid(evaluator, context, counter, grid, body);
                    }
                    long passes = ((IntegerValue) arguments.get(1)).magnitude();
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
                        while (evaluator.evaluateOrRaise(
                                condition, evaluator.systemContext()).isTruthy()) {
                            last = oneRound(evaluator, body, evaluator.systemContext());
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
                            last = oneRound(evaluator, body, evaluator.systemContext());
                        } while (!last.isTruthy());
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
                        return removedEachFrom(other, arguments, evaluator, context);
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
                        int reached = setLoopNames(locals, names, items, at, series);
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
                        at = setLoopNames(
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

    /** REPEAT and FOREACH: bind a word, run the body, repeat. */
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
                last = oneRound(evaluator, bound, locals);
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
                    last = oneRound(evaluator, bound, locals);
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
                last = oneRound(evaluator, body, locals);
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
                last = oneRound(evaluator, body, locals);
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
                : (int) asMagnitude(end);
        endIndex = Math.max(0, Math.min(endIndex, tail));
        long stepBy = (long) asMagnitude(step);
        Value last = NoneValue.none();
        try {
            int at = series.index();
            while (stepBy > 0 ? at <= endIndex : at >= endIndex) {
                locals.set(counter.spelling(), series.atIndex(at));
                last = oneRound(evaluator, body, locals);
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

    /**
     * FOREACH.
     *
     * <p>{@code while (index < (tail = SERIES_TAIL(series)))} in
     * {@code Loop_Each}: the assignment is inside the condition, so the walk
     * asks the series how long it is at the top of every round rather than
     * once at the start. Anything the body appends is therefore walked too,
     * and Rebol's own test asserts it -- a map gains a key in the middle of a
     * walk over it and the sum the walk answers includes that key's value.
     * The same is true of a block, which was checked before this was written:
     * {@code foreach x b [append b 9]} keeps going until something breaks it.
     */
    private static Value forEachLoop(
            Evaluator evaluator, Context within,
            Value target, Value series, BlockValue body) {

        List<WordValue> names = loopNamesIn(target, "foreach");
        List<WordValue> taking = namesThatTakeAValue(names);
        refuseMoreNamesThanAPairHas(series, taking);
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
                at = setLoopNames(locals, names, items, at, series);
                last = oneRound(evaluator, bound, locals);
                items = itemsAsTheyStandNow.get();
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }

    /**
     * A walk over pairs takes one name or two, and refuses a third.
     *
     * <p>{@code else Trap_Arg(words);} on the third name, reached only for an
     * object or a map. A pair has two halves and there is nothing for a third
     * name to be set to; setting it to none would make a malformed loop look
     * like a working one. A block has no such limit, because a block is
     * whatever width the caller says it is.
     */
    private static void refuseMoreNamesThanAPairHas(
            Value series, List<WordValue> names) {

        if (names.size() > 2 && (series instanceof MapValue || series instanceof ObjectValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a walk over " + series.datatype().literalSpelling()
                            + " takes a key and a value, and " + names.size()
                            + " names is one more than a pair has");
        }
    }

    /**
     * The words a walk sets each round, whichever way they were written.
     *
     * <p>One name or a block of them: {@code 'word [word! block!]} in every one
     * of the four walks, because {@code Init_Loop} reads the list before
     * {@code Loop_Each} looks at which walk is running. So MAP-EACH takes a
     * block for the same reason FOREACH does, and refusing one here threw a
     * Java exception out of the interpreter rather than answering anything.
     *
     * <p>Anything that is not a word in the block is refused where the C
     * refuses it, {@code else Trap_Arg(words);}, rather than being skipped.
     */
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

    /**
     * Setting the walk's words for one round, filling with none past the end.
     *
     * <p>{@code else SET_NONE(vars);} where the walk has passed the tail. So a
     * three-item block walked two names at a time runs twice rather than once,
     * and the second round's second name holds none. Dropping the short round
     * is the convenient reading and it is not what the C does: REMOVE-EACH over
     * `[1 2 3]` with two names reaches the 3.
     */
    private static int setLoopNames(
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

    /**
     * Where a walk has got to, as the thing a set-word is handed.
     *
     * <p>The series itself standing at the current item, which is what makes
     * {@code insert here handler} work: it shares storage with what is being
     * walked. An object or a map is handed over whole instead, because neither
     * is walked by index -- {@code if (ANY_OBJECT(value) || IS_MAP(value))
     * *vars = *value;}.
     */
    private static Value positionWithin(Value walked, int reached) {
        if (!(walked instanceof SeriesValue series)) {
            return walked;
        }
        return series.atIndex(Math.min(
                series.index() + reached, series.storageLength() + 1));
    }

    /**
     * The names that take a value out of the series, which is not all of them.
     *
     * <p>A set-word takes none, so it does not count towards how wide a round
     * is. Counting it would make {@code foreach [p: v] [a b c]} step two at a
     * time and walk half the block.
     */
    private static List<WordValue> namesThatTakeAValue(List<WordValue> names) {
        return names.stream()
                .filter(name -> name.datatype() != Datatype.SET_WORD)
                .toList();
    }

    /**
     * What a walk steps over, given how many names it walks with.
     *
     * <p>One name over an object or a map walks the keys alone. For a map that
     * is the index mask -- {@code *vars = *BLK_SKIP(series, index & ~1)} keeps
     * reading the key while the walk steps two slots at a time -- and for an
     * object it is the same shape by hand.
     */
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

    /**
     * Walking a word that holds a series, a step at a time.
     *
     * <p>{@code Loop_All} in {@code n-loop.c}, which serves FORALL with a step
     * of one and FORSKIP with the step it was given. What it does with the word
     * is the part worth reading twice.
     *
     * <p>A word holding none answers none. A word holding anything that is not a
     * series is refused. A negative step starting at the tail walks backwards
     * from the last item rather than doing nothing. And the word goes back where
     * it started on the way out -- unless BREAK left the loop, which returns
     * before the line that restores it.
     */
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
        int at = start.index();
        if (step < 0 && at > start.storageLength()) {
            at = start.storageLength() + 1 + step;
        }
        Value last = NoneValue.none();
        try {
            while (at >= 1 && at <= start.storageLength()) {
                slot.setValue(start.atIndex(at));
                last = oneRound(evaluator, body, evaluator.systemContext());
                at += step;
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        slot.setValue(start);
        return last;
    }

    /** A series as a list of its values, whatever kind of series it is. */
    private static List<Value> itemsOf(Value series) {
        return switch (series) {
            case BlockValue block -> block.remaining();
            case GobValue gob -> gob.storage().pane()
                    .subList(Math.min(gob.index() - 1, gob.storage().length()),
                            gob.storage().length());
            case StringValue text -> text.text().codePoints()
                    .mapToObj(codepoint -> (Value) CharacterValue.of(codepoint))
                    .toList();
            case ObjectValue object -> object.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .<Value>mapMulti((slot, accept) -> {
                        accept.accept(WordValue.of(slot.spelling())
                                .boundTo(object.context()));
                        accept.accept(slot.value());
                    })
                    .toList();
            case BinaryValue binary -> {
                List<Value> octets = new ArrayList<>(binary.lengthFromHere());
                for (int at = 0; at < binary.lengthFromHere(); at++) {
                    octets.add(IntegerValue.of(binary.storage().at(binary.index() + at)));
                }
                yield List.copyOf(octets);
            }
            case MapValue map -> map.walkable();
            case VectorValue vector -> vector.remaining();
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "cannot walk " + series.datatype().literalSpelling() + " value");
        };
    }

    /**
     * Source text read into values.
     *
     * <p>A syntax failure comes back as an ordinary error! that the script
     * can catch, rather than as a host exception, because the reader's
     * failures are values in this language like any other.
     */
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

    /**
     * The source text LOAD was given, whatever it arrived as.
     *
     * <p>A binary is bytes of UTF-8. A byte order mark at the front is
     * dropped rather than read, because it marks the encoding rather than
     * being part of the text, and leaving it in makes the first value a
     * string nothing can match.
     */
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

    /** Bytes as UTF-8, refusing any that do not decode -- R3's invalid-chars. */
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

        define("number?", takes("value"),
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
                            (int) Math.round(asMagnitude(arguments.get(1))));
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
                        resizedImage(arguments));

        define("image-diff", List.of(
                        Parameter.required("a", Set.of(Datatype.IMAGE)),
                        Parameter.required("b", Set.of(Datatype.IMAGE)),
                        Parameter.belongingTo("part", "offset", Set.of(Datatype.PAIR)),
                        Parameter.belongingTo("part", "size", Set.of(Datatype.PAIR))),
                Set.of("part"),
                (arguments, evaluator, context, refinements) ->
                        DecimalValue.percent(ImageOperations.differenceBetween(
                                (ImageValue) arguments.get(0),
                                (ImageValue) arguments.get(1))));

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
                        theOperatingSystemsImageCodec(refinements));

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
                    // No required arguments, so the list holds exactly the
                    // asked-for refinements' arguments in declaration order:
                    // crypt-key first when /KEY was named, then ctx and data
                    // when /STREAM was.
                    int streamBeginsAt = refinements.contains("key") ? 1 : 0;
                    if (refinements.contains("stream")) {
                        return encipheredThroughTheStream(
                                (HandleValue) arguments.get(streamBeginsAt),
                                (BinaryValue) arguments.get(streamBeginsAt + 1));
                    }
                    if (refinements.contains("key")) {
                        return HandleValue.context(RC4_HANDLE_TYPE,
                                nextCipherIdentity(),
                                JavaObjectValue.of(StreamCipher.keyedWith(
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
                        case WordValue named -> slotOf(named).setValue(UnsetValue.unset());
                        case BlockValue named -> {
                            for (Value item : named.remaining()) {
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
                            && arguments.getFirst() instanceof WordValue named) {
                        slotOf(named).hide(true);
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

    /** SET of a path walks object fields and writes the last one. */
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
                    if (target instanceof WordValue word) {
                        slotOf(word).setValue(supplied);
                        return supplied;
                    }
                    if (target instanceof BlockValue path
                            && PATH_SHAPED.contains(path.datatype())) {
                        if (!refinements.contains("any")
                                && supplied.datatype() == Datatype.UNSET) {
                            throw Raised.of(EvaluationFailure.NEED_VALUE, "set");
                        }
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
                    }
                    if (names == null) {
                        return raiseCannotUse(target, "set");
                    }
                    boolean anyValue = refinements.contains("any");
                    if (!anyValue && supplied.datatype() == Datatype.UNSET) {
                        throw Raised.of(EvaluationFailure.NEED_VALUE, "set");
                    }
                    if (target instanceof ObjectValue into
                            && supplied instanceof ObjectValue from
                            && !refinements.contains("only")) {
                        setFieldsFromObject(into, from, refinements);
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
                                throw Raised.of(EvaluationFailure.NEED_VALUE, "set");
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
                    List<Value> pieces = evaluator.evaluateEachOrRaise(
                                    (BlockValue) arguments.get(0), context).stream()
                            .filter(piece -> refinements.contains("all")
                                    || !(piece instanceof NoneValue
                                            || piece instanceof UnsetValue))
                            .toList();
                    String separator = refinements.contains("with") && arguments.size() > 1
                            ? Molder.form(arguments.get(1))
                            : "";
                    List<Value> all = evaluator.evaluateEachOrRaise(
                            (BlockValue) arguments.get(0), context);
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
                    if (arguments.get(0) instanceof VectorValue vector) {
                        VectorPath.write(vector, arguments.get(1), arguments.get(2));
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof GobValue gob) {
                        GobPath.poke(gob, (int) positionPokedAt(arguments.get(1)),
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
                        members.holdAll(
                                (BitsetValue) bitsMeantBy(arguments.get(1)),
                                arguments.get(2).isTruthy());
                        return arguments.get(2);
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
                                    && number.magnitude() <= MAXIMUM_CODEPOINT ->
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
                        return combined(arguments, Combination.DIFFERENCE);
                    }
                    if (arguments.get(0) instanceof DateValue from
                            && arguments.get(1) instanceof DateValue to) {
                        return timeBetween(from, to);
                    }
                    boolean mindingCase = refinements.contains("case");
                    List<Value> ours = ((BlockValue) arguments.get(0)).remaining();
                    List<Value> theirs = ((BlockValue) arguments.get(1)).remaining();
                    List<Value> only = new ArrayList<>();
                    ours.stream().filter(item -> theirs.stream()
                            .noneMatch(other -> matches(item, other, mindingCase)))
                            .forEach(only::add);
                    theirs.stream().filter(item -> ours.stream()
                            .noneMatch(other -> matches(item, other, mindingCase)))
                            .forEach(only::add);
                    return BlockValue.block(only);
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
                    if (arguments.getFirst() instanceof DatatypeValue named) {
                        String[] described = DATATYPE_SPECS.get(
                                named.represents().spelling());
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
                    if (arguments.get(0) instanceof ModuleValue module) {
                        return switch (field) {
                            case "spec" -> module.header();
                            case "title" -> module.headerField("title");
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
                            case "body" -> BlockValue.block(map.flattened());
                            default -> NoneValue.none();
                        };
                    }
                    // A handle publishes its type and nothing else, so that is
                    // the whole of what WORDS-OF and VALUES-OF find on one.
                    // `PD_Handle` serves the same single field through a path.
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
                            case "types" -> typesetsOf(written.parameters(), Set.of());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof NativeValue built) {
                        return switch (field) {
                            case "spec" -> specBlockOf(built.parameters());
                            case "body" -> NoneValue.none();
                            case "types" -> typesetsOf(
                                    built.parameters(), built.declaredRefinements());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof OperatorValue operator
                            && operator.underlying() instanceof NativeValue behind) {
                        return switch (field) {
                            case "spec" -> specBlockOf(behind.parameters());
                            case "types" -> typesetsOf(
                                    behind.parameters(), behind.declaredRefinements());
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
                    Wildcards wildcards = Wildcards.named(argumentFor(
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
                    if (!(arguments.get(0) instanceof WordValue named)) {
                        return arguments.get(0);
                    }
                    Value held = slotOf(named).value();
                    if (held instanceof UnsetValue && !refinements.contains("any")) {
                        throw Raised.of(EvaluationFailure.NO_VALUE,
                                ((WordValue) arguments.get(0)).spelling() + " has no value");
                    }
                    return held;
                });
    }

    /** The names a CATCH was told to expect, empty when it was told none. */
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

    /**
     * Whether a CATCH expecting these names may take this throw.
     *
     * <p>Strict both ways: an unnamed CATCH takes only an unnamed throw,
     * and a named one takes only a throw of a name it listed.
     */
    private static boolean answersTo(ThrownSignal thrown, Set<String> expected) {
        return thrown.name()
                .map(expected::contains)
                .orElseGet(expected::isEmpty);
    }

    /**
     * ++ or --, which change a word in place and answer its old value.
     *
     * <p>An integer moves by the step; a series moves that many positions.
     * Both are the same idea, because a position is an ordinary value.
     */
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

    /**
     * The value a word names when it is one of the three constants, and
     * the word itself otherwise.
     *
     * <p>CONSTRUCT reads NONE, TRUE and FALSE this way although it
     * evaluates nothing else, which is what /ONLY exists to switch off.
     */
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

    /**
     * Fills a context from a block of set-words and values, evaluating
     * nothing.
     *
     * <p>{@code Do_Construct} in {@code c-do.c}. Set-words are held back
     * until a value arrives and then all of them take it, which is what
     * makes {@code a: b: 1} give both fields the same value. A value with
     * no set-word waiting for it is dropped, and a set-word with nothing
     * after it is left holding nothing.
     *
     * <p>{@code /only} is {@code Do_Min_Construct}, which is the same walk
     * without the seven named words being turned into values.
     */
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

    /**
     * An internet-style header read as set-word and text pairs.
     *
     * <p>{@code Scan_Net_Header} in {@code l-types.c}. A field name runs
     * up to a colon and the rest of the line is its value, so
     * {@code "a: 1 b: yes"} is one field and not two: only a newline
     * starts another.
     *
     * <p>A line beginning with whitespace continues the one before it,
     * joined by a single space however far it is indented. That is what a
     * header means by folding a long line, and it is why the value has to
     * be rebuilt rather than taken as a span of the source.
     */
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

    /** Where a field's colon is, or -1 when the line names no field. */
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

    /** A binary read as the UTF-8 text it holds. */
    private static String textOfBytes(BinaryValue bytes) {
        byte[] held = new byte[bytes.lengthFromHere()];
        for (int at = 0; at < held.length; at++) {
            held[at] = (byte) bytes.storage().at(bytes.index() + at);
        }
        return new String(held, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Refuses a search of a binary for a number no byte could hold.
     *
     * <p>Nothing to do for any other kind of series, or for a needle that
     * is not a whole number: a binary can be searched for a binary or a
     * character too, and neither has this problem.
     */
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

    /**
     * The error a MAKE ERROR! spec asks for.
     *
     * <p>A block names a type and an id from the catalogue. A `code:`
     * field in it is ignored, because the code follows from the type and
     * letting a caller set it would let an error claim a category its own
     * code contradicts.
     *
     * <p>Anything else is a User error carrying the value as its first
     * argument, which is how a script raises something of its own without
     * needing a catalogue entry at all.
     */
    private static Value errorFromSpec(Value spec, Evaluator evaluator, Context context) {
        if (spec instanceof BlockValue body && body.datatype() == Datatype.BLOCK) {
            Value built = makeObject(evaluator, context, Optional.empty(), body);
            if (built instanceof ObjectValue holder) {
                spec = BlockValue.block(setWordsAndValuesOf(holder.context()));
            }
        }
        if (!(spec instanceof BlockValue fields)) {
            return new ErrorValue(ErrorCategory.USER, "message",
                    Molder.form(spec), Optional.of(spec),
                    Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(),
                    new java.util.LinkedHashMap<>());
        }
        List<Value> items = fields.remaining();
        ErrorCategory category = ErrorCategory.USER;
        String errorId = "user-error";
        boolean namedAType = false;
        boolean namedAnId = false;
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
            switch (name.canonical()) {
                case "type" -> {
                    namedAType = true;
                    category = ErrorCategory.named(said).orElse(category);
                }
                case "id" -> {
                    namedAnId = true;
                    errorId = said;
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
        return new ErrorValue(category, errorId, errorId, subject, second, third,
                Optional.empty(), Optional.empty(), new java.util.LinkedHashMap<>());
    }

    /**
     * One turn of a loop, with CONTINUE caught.
     *
     * <p>Caught here and not around the whole loop: a CONTINUE that
     * reached the outer catch would end the loop, which is what BREAK is
     * for. A round that was cut short answers none, so a loop whose last
     * round continued answers none rather than whatever the round before
     * it happened to leave.
     */
    private static Value oneRound(Evaluator evaluator, BlockValue body, Context where) {
        try {
            return evaluator.evaluateOrRaise(body, where);
        } catch (ContinueSignal skipped) {
            return NoneValue.none();
        }
    }

    /**
     * Where a script header starts in some text, or -1.
     *
     * <p>The word REBOL, in any case, then spaces, then an open bracket.
     * It has to begin a line: anything but spaces before it on the same
     * line means it is not a header. A byte order mark counts as a space,
     * because it marks the encoding and is not part of the text.
     */
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

    /** Whether the line up to here holds nothing but spaces. */
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

    /** Whether spaces and then an open bracket follow. */
    private static boolean bracketFollows(String text, int at) {
        int forward = at;
        while (forward < text.length() && Character.isWhitespace(text.charAt(forward))) {
            forward++;
        }
        return forward < text.length() && text.charAt(forward) == '[';
    }

    /**
     * The shapes SET will take, from {@code natives.reb}: a word, a lit-word,
     * any path, a block or an object.
     *
     * <p>An issue and a refinement are word datatypes and so pass this check;
     * {@link #refuseUnassignableName} turns them away afterwards. Two guards
     * for one question, because the datatype does not tell them apart.
     */
    private static final Set<Datatype> NAME_SHAPED = Set.of(
            Datatype.WORD, Datatype.LIT_WORD, Datatype.SET_WORD, Datatype.GET_WORD,
            Datatype.ISSUE, Datatype.REFINEMENT,
            Datatype.PATH, Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH,
            Datatype.BLOCK, Datatype.OBJECT);

    /**
     * Refuses a name SET cannot assign to.
     *
     * <p>An issue and a refinement are both {@link WordValue} underneath,
     * so nothing about the shape of the value stops them being assigned;
     * only the datatype says so. The caller chooses the failure, because
     * a wrong argument to SET and a wrong item inside a block SET was
     * given are two different mistakes and R3 gives them two ids.
     */
    private static void refuseUnassignableName(Value name, EvaluationFailure failure) {
        if (name.datatype() == Datatype.ISSUE || name.datatype() == Datatype.REFINEMENT) {
            throw Raised.of(failure,
                    "set cannot assign to a " + name.datatype().literalSpelling());
        }
    }

    /**
     * Refuses a TRIM whose refinements ask for two different things.
     *
     * <p>Two quarrels, and both are bad-refines. /HEAD and /TAIL say which
     * end to work on while /ALL and /WITH say to work everywhere, so one
     * of each leaves nothing coherent to do. And /WITH, /AUTO and /LINES
     * are about text, so a binary or a block refuses all three.
     *
     * <p>/AUTO is not in the first quarrel: `trim/auto/tail` is an
     * ordinary call.
     */
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

    /** A block with its nones dropped, from whichever end was asked for. */
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

    /**
     * An object with its empty fields dropped, as a new object.
     *
     * <p>{@code Trim_Object} builds a fresh frame holding the fields whose value
     * is past none -- `if (VAL_TYPE(val) > REB_NONE && !VAL_GET_OPT(word,
     * OPTS_HIDE))` -- so a field holding none or unset goes, and so does a
     * hidden one. The original is left alone, which is the opposite of TRIM on a
     * series and is why this answers a value rather than the argument.
     *
     * <p>A module and an error trim the same way, being objects underneath.
     */
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

    /**
     * The characters TRIM/WITH takes out, by datatype rather than by mold.
     *
     * <p>An integer is one code point, so `trim/with s 97` removes the
     * letter a and not the digits 9 and 7 -- molding the integer gave its
     * decimal spelling. A char is its own code point, a string each of its
     * characters, and a none nothing.
     */
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

    /** An error's fields as a context, so the object arms can read one. */
    private static Context errorAsAContext(ErrorValue raised) {
        Context fields = Context.root();
        for (String name : ErrorValue.FIELDS) {
            fields.set(name, raised.field(name).orElseGet(NoneValue::none));
        }
        return fields;
    }

    /** A binary with its zero bytes dropped, from whichever end was asked for. */
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

    /**
     * UPPERCASE or LOWERCASE, which differ only in which way they go.
     *
     * <p>/PART changes the first few characters from where the series is
     * and leaves the rest, counting from the position rather than from
     * the head. The whole series comes back either way, positioned where
     * it was, because these change it in place.
     */
    private void defineCaseChange(
            String name, java.util.function.UnaryOperator<String> change) {

        define(name, List.of(
                        Parameter.required("text", Set.of(Datatype.STRING)),
                        Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER))),
                Set.of("part"),
                (arguments, evaluator, context, refinements) -> {
                    StringValue text = (StringValue) arguments.getFirst();
                    if (!refinements.contains("part")) {
                        return rewritten(text, change);
                    }
                    Value limit = argumentFor("part", List.of("part"), arguments, refinements, 1);
                    int howMany = limit instanceof IntegerValue wanted
                            ? (int) Math.max(0, Math.min(wanted.magnitude(), text.lengthFromHere()))
                            : text.lengthFromHere();
                    int changing = howMany;
                    return rewritten(text, whole -> change.apply(whole.substring(0, changing))
                            + whole.substring(changing));
                });
    }

    /**
     * A string rewritten in place, and answered.
     *
     * <p>UPPERCASE, LOWERCASE and TRIM change the string they were given
     * rather than building a new one, so a caller holding it sees the
     * change. Going through the storage is also what makes them refuse a
     * protected string without a check of their own.
     */
    private static Value rewritten(
            StringValue text, java.util.function.UnaryOperator<String> change) {

        String replacement = change.apply(text.text());
        int from = text.index();
        for (int at = text.storageLength(); at >= from; at--) {
            text.storage().removeAt(at);
        }
        for (int at = replacement.length(); at > 0; at--) {
            text.storage().insertAt(from, replacement.codePointAt(at - 1));
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
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case MapValue map -> IntegerValue.of(map.pairCount());
                    case TupleValue tuple -> IntegerValue.of(tuple.shownCount());
                    case WordValue word -> IntegerValue.of(
                            word.spelling().codePointCount(0, word.spelling().length()));
                    case SeriesValue series -> IntegerValue.of(series.lengthFromHere());
                    case ObjectValue object ->
                            IntegerValue.of(object.context().fieldCount());
                    case ModuleValue module ->
                            IntegerValue.of(module.context().fieldCount());
                    case PortValue port ->
                            IntegerValue.of(port.context().fieldCount());
                    case BitsetValue set -> IntegerValue.of(set.octets().length * 8);
                    case StructValue struct -> IntegerValue.of(struct.size());
                    default -> raiseWrongArgument(arguments.get(0), "length?", "series");
                });

        define("first", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 1));
        define("second", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 2));
        define("third", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 3));
        define("pick", List.of(Parameter.required("series"),
                        Parameter.required("index")),
                (arguments, evaluator, context) -> arguments.get(1)
                        instanceof LogicValue chosen
                        && !(arguments.getFirst() instanceof BitsetValue)
                        ? pick(arguments.get(0), chosen.truth() ? 1 : 2)
                        : pickFrom(arguments.get(0), arguments.get(1)));

        define("atz", List.of(Parameter.required("series"),
                        Parameter.required("position", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? (Value) series.atIndex(clampedPosition(series,
                                ((IntegerValue) arguments.get(1)).magnitude() + 1))
                        : raiseWrongArgument(arguments.get(0), "atz", "series"));
        define("indexz?", List.of(Parameter.required("series", positionable())),
                Set.of("xy"),
                (arguments, evaluator, context, refinements) ->
                        arguments.get(0) instanceof SeriesValue series
                                ? IntegerValue.of(series.index() - 1)
                                : raiseCannotUse(arguments.get(0), "indexz?"));
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

        define("first+", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseWrongArgument(arguments.get(0), "first+", "series");
                    }
                    Value first = pick(series, 1);
                    if (!series.atTail()) {
                        removeFrom(series, series.index(), 1);
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
                    case SeriesValue series -> LogicValue.of(series.atTail());
                    default -> raiseCannotUse(arguments.get(0), "tail?");
                });
        define("next", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? (Value) series.atIndex(Math.min(
                                series.index() + 1, series.storageLength() + 1))
                        : raiseCannotUse(arguments.get(0), "next"));
        define("head", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? (Value) series.head()
                        : raiseCannotUse(arguments.get(0), "head"));
        define("tail", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? (Value) series.tail()
                        : raiseCannotUse(arguments.get(0), "tail"));
        define("index?", List.of(Parameter.required("series", positionable())),
                Set.of("xy"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case SeriesValue series -> IntegerValue.of(series.index());
                    default -> raiseCannotUse(arguments.get(0), "index?");
                });

        define("append", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("dup", "count", DUP_COUNT)),
                Set.of("part", "only", "dup"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case BlockValue block -> {
                        if (duplicated(arguments.get(1), arguments, refinements)
                                instanceof BlockValue added
                                && added.datatype() == Datatype.BLOCK
                                && !refinements.contains("only")) {
                            firstFew(arguments.get(1), added.remaining(),
                                    arguments, refinements, 2)
                                    .forEach(block.storage()::append);
                        } else {
                            block.storage().append(arguments.get(1));
                        }
                        yield block.head();
                    }
                    case BinaryValue bytes -> {
                        for (int octet : octetsContributedBy(
                                duplicated(arguments.get(1), arguments, refinements),
                                partCountFor(arguments, refinements))) {
                            bytes.storage().append(octet);
                        }
                        yield bytes.head();
                    }
                    case ObjectValue object ->
                            objectGainingFields(object, arguments, refinements, "append");
                    case MapValue map ->
                            addPairsToMap(map, arguments, refinements, "append");
                    case BitsetValue members -> {
                        requireChangeable(members);
                        members.holdAll((BitsetValue) bitsMeantBy(arguments.get(1)), true);
                        yield members;
                    }
                    case StringValue string -> {
                        Value adding = duplicated(
                                arguments.get(1), arguments, refinements);
                        String text = adding instanceof BlockValue added
                                && added.datatype() == Datatype.BLOCK
                                ? runTogether(added)
                                : Molder.form(adding);
                        int wanted = howManyWanted(
                                arguments.get(1), arguments, refinements, 2)
                                .map(count -> Math.min(count.intValue(), text.length()))
                                .orElse(text.length());
                        text.substring(0, wanted).codePoints()
                                .forEach(string.storage()::append);
                        yield string.head();
                    }
                    case GobValue gob -> {
                        refuseUnfinishedRefinements(refinements, "append");
                        insertChildren(gob, gob.storage().length() + 1,
                                arguments.get(1));
                        yield gob;
                    }
                    case VectorValue vector -> {
                        for (Value number : numbersAddedBy(
                                vector.kind(), arguments, refinements, true)) {
                            vector.storage().append(
                                    VectorPath.storedFormOf(vector.kind(), number));
                        }
                        yield vector.head();
                    }
                    default -> raiseCannotUse(arguments.get(0), "append");
                });

        define("last", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? pick((Value) series, series.lengthFromHere())
                        : raiseCannotUse(arguments.get(0), "last"));

        define("back", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? (Value) series.atIndex(Math.max(1, series.index() - 1))
                        : raiseCannotUse(arguments.get(0), "back"));

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
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "skip");
                    }
                    long by = positionAskedFor(series, arguments.get(1), false);
                    return series.atIndex(clampToSeries(series, series.index() + by));
                });

        define("at", List.of(
                        Parameter.required("series"),
                        Parameter.required("index",
                                Set.of(Datatype.INTEGER, Datatype.DECIMAL,
                                        Datatype.PERCENT, Datatype.LOGIC,
                                        Datatype.PAIR))),
                (arguments, evaluator, context) -> {
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
                    if (!(original instanceof SeriesValue series)) {
                        return raiseCannotUse(original, "copy");
                    }
                    Value limit = argumentFor("part", List.of("part", "types"),
                            arguments, refinements, 1);
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
                        return map.storedKeyLike(arguments.get(1));
                    }
                    if (arguments.get(0) instanceof BitsetValue bitset) {
                        return LogicValue.of(bitsetHolds(bitset, arguments.get(1),
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
                    if (!(arguments.get(0) instanceof SeriesValue series)
                            || series instanceof VectorValue) {
                        return raiseCannotUse(arguments.get(0), "find");
                    }
                    int limit = searchLimit(series, arguments, refinements);
                    long stride = searchStride(arguments, refinements);
                    Wildcards wildcards = Wildcards.named(argumentFor(
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
                    case BitsetValue members -> {
                        requireChangeable(members);
                        members.holdAll((BitsetValue) bitsMeantBy(arguments.get(1)), true);
                        yield members;
                    }
                    case ObjectValue object ->
                            objectGainingFields(object, arguments, refinements, "insert");
                    case BlockValue stranded -> {
                        BlockValue block = (BlockValue) clampedToTail(stranded);
                        if (duplicated(arguments.get(1), arguments, refinements)
                                instanceof BlockValue added
                                && added.datatype() == Datatype.BLOCK
                                && !refinements.contains("only")) {
                            List<Value> items = partOf(added, arguments, refinements);
                            for (int at = items.size(); at > 0; at--) {
                                block.storage().insertAt(block.index(), items.get(at - 1));
                            }
                            yield block.atIndex(block.index() + items.size());
                        }
                        block.storage().insertAt(block.index(), arguments.get(1));
                        yield block.atIndex(block.index() + 1);
                    }
                    case StringValue strandedText -> {
                        StringValue text = (StringValue) clampedToTail(strandedText);
                        String added = textContributedBy(arguments, refinements);
                        for (int at = 0; at < added.length(); at++) {
                            text.storage().insertAt(text.index() + at, added.charAt(at));
                        }
                        yield text.atIndex(text.index() + added.length());
                    }
                    case BinaryValue strandedBytes -> {
                        BinaryValue bytes = (BinaryValue) clampedToTail(strandedBytes);
                        int[] octets = octetsContributedBy(
                                duplicated(arguments.get(1), arguments, refinements),
                                partCountFor(arguments, refinements));
                        for (int at = octets.length; at > 0; at--) {
                            bytes.storage().insertAt(bytes.index(), octets[at - 1]);
                        }
                        yield bytes.atIndex(bytes.index() + octets.length);
                    }
                    case MapValue map ->
                            addPairsToMap(map, arguments, refinements, "insert");
                    case GobValue gob -> {
                        refuseUnfinishedRefinements(refinements, "insert");
                        insertChildren(gob, gob.index(), arguments.get(1));
                        yield gob;
                    }
                    case VectorValue strandedVector -> {
                        VectorValue vector = (VectorValue) clampedToTail(strandedVector);
                        List<Value> numbers = numbersAddedBy(
                                vector.kind(), arguments, refinements, true);
                        for (int at = numbers.size(); at > 0; at--) {
                            vector.storage().insertAt(vector.index(),
                                    VectorPath.storedFormOf(
                                            vector.kind(), numbers.get(at - 1)));
                        }
                        yield vector.atIndex(vector.index() + numbers.size());
                    }
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
                    if (arguments.get(0) instanceof MapValue map) {
                        if (refinements.contains("key")) {
                            map.remove(arguments.get(1));
                        }
                        return map;
                    }
                    if (arguments.get(0) instanceof BitsetValue members) {
                        return membersCleared(members, arguments, refinements);
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
                    for (long dropped = 0; dropped < howMany && !series.atTail(); dropped++) {
                        removeOneAt(series, series.index());
                    }
                    return series;
                });

        define("reverse", List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER))),
                Set.of("part"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("part")
                            && arguments.getFirst() instanceof SeriesValue series) {
                        Value limit = argumentFor(
                                "part", List.of("part"), arguments, refinements, 1);
                        return reversedFront(series, limit);
                    }
                    if (arguments.get(0) instanceof TupleValue tuple) {
                        Value limit = refinements.contains("part")
                                ? argumentFor("part", List.of("part"), arguments,
                                        refinements, 1)
                                : IntegerValue.of(tuple.segmentCount());
                        return reversedOctets(tuple, (int) asMagnitude(limit));
                    }
                    if (arguments.get(0) instanceof PairValue pair) {
                        return pair.reversed();
                    }
                    if (arguments.get(0) instanceof GobValue gob) {
                        gob.storage().turnRound();
                        return gob;
                    }
                    if (arguments.get(0) instanceof StringValue text) {
                        return reversedText(text);
                    }
                    if (arguments.get(0) instanceof BinaryValue bytes) {
                        return reversedBytes(bytes);
                    }
                    if (arguments.get(0) instanceof VectorValue vector) {
                        return reversedFront(vector, IntegerValue.of(
                                vector.lengthFromHere()));
                    }
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseWrongArgument(arguments.get(0), "reverse", "series");
                    }
                    List<Value> items = new ArrayList<>(block.remaining());
                    Collections.reverse(items);
                    for (int at = 0; at < items.size(); at++) {
                        block.storage().set(block.index() + at, items.get(at));
                    }
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
                        GobPath.poke(gob, gob.index(), arguments.get(1));
                        return gob.atIndex(gob.index() + 1);
                    }
                    Value replacing = duplicated(
                            arguments.get(1), arguments, refinements);
                    if (arguments.get(0) instanceof BinaryValue bytes) {
                        int[] octets = octetsContributedBy(replacing, -1);
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
                        int overwritten = Math.min(replacement.length(), text.lengthFromHere());
                        for (int gone = 0; gone < overwritten; gone++) {
                            text.storage().removeAt(text.index());
                        }
                        for (int at = 0; at < replacement.length(); at++) {
                            text.storage().insertAt(text.index() + at, replacement.charAt(at));
                        }
                        return text.atIndex(text.index() + replacement.length());
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
                    List<Value> replacements = replacing instanceof BlockValue several
                            && refinements.contains("dup")
                            ? several.remaining()
                            : List.of(arguments.get(1));
                    for (int at = 0; at < replacements.size(); at++) {
                        int where = block.index() + at;
                        if (where <= block.storageLength()) {
                            block.storage().set(where, replacements.get(at));
                        } else {
                            block.storage().insertAt(where, replacements.get(at));
                        }
                    }
                    return block.atIndex(block.index() + replacements.size());
                });

        define("clear", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case BitsetValue members -> {
                        requireChangeable(members);
                        members.clear();
                        yield members;
                    }
                    case MapValue map -> {
                        requireChangeable(map);
                        map.clear();
                        yield map;
                    }
                    case BlockValue block -> {
                        while (block.storage().length() >= block.index()) {
                            block.storage().removeAt(block.index());
                        }
                        yield block;
                    }
                    case StringValue text0 -> {
                        while (text0.storage().length() >= text0.index()) {
                            text0.storage().removeAt(text0.index());
                        }
                        yield text0;
                    }
                    case BinaryValue bytes -> {
                        while (bytes.storage().length() >= bytes.index()) {
                            bytes.storage().removeAt(bytes.index());
                        }
                        yield bytes;
                    }
                    case GobValue gob -> {
                        gob.storage().removeChildren(gob.index(),
                                gob.storage().length() - gob.index() + 1);
                        yield gob;
                    }
                    case VectorValue vector -> {
                        vector.storage().clearFrom(vector.index());
                        yield vector;
                    }
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

        defineSetOperation("intersect", Combination.INTERSECT);
        defineSetOperation("union", Combination.UNION);
        defineSetOperation("exclude", Combination.EXCLUDE);
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
                    return combined(
                            List.of(arguments.getFirst(), arguments.getFirst()),
                            Combination.UNION, refinements.contains("case"), stride);
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

                    List<Value> results;
                    if (source instanceof BlockValue toReduce
                            && (toReduce.datatype() == Datatype.BLOCK
                                    || toReduce.datatype() == Datatype.PAREN)) {
                        if (refinements.contains("no-set")) {
                            results = reducedLeavingSetWords(toReduce, evaluator);
                        } else if (refinements.contains("only")) {
                            results = reducedOnlyWords(toReduce, evaluator,
                                    argumentFor("only", List.of("into", "only"),
                                            arguments, refinements, 1));
                        } else {
                            results = evaluator.evaluateEachOrRaise(
                                    toReduce, evaluator.systemContext());
                        }
                    } else if (target == null) {
                        return source;
                    } else {
                        results = List.of(source);
                    }

                    if (!(target instanceof BlockValue into)) {
                        return BlockValue.block(results).as(
                                source.datatype() == Datatype.PAREN
                                        ? Datatype.PAREN
                                        : Datatype.BLOCK);
                    }
                    for (int at = results.size(); at > 0; at--) {
                        into.storage().insertAt(into.index(), results.get(at - 1));
                    }
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
                    List<Value> built =
                            arguments.getFirst() instanceof BlockValue template
                                    ? composed(template, evaluator, context,
                                            refinements.contains("only"),
                                            refinements.contains("deep"))
                                    : List.of(arguments.getFirst());
                    if (!(arguments.getFirst() instanceof BlockValue)
                            && (!refinements.contains("into") || arguments.size() < 2)) {
                        return arguments.getFirst();
                    }
                    if (!refinements.contains("into") || arguments.size() < 2) {
                        return BlockValue.block(built);
                    }
                    BlockValue target = (BlockValue) arguments.get(1);
                    for (int at = built.size(); at > 0; at--) {
                        target.storage().insertAt(target.index(), built.get(at - 1));
                    }
                    return target.atIndex(target.index() + built.size());
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
                    if (arguments.get(0) instanceof TimeValue time) {
                        return TimeValue.ofNanoseconds(time.nanoseconds());
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
                        return roundedToTheScalesDatatype(step, value);
                    }
                    double rounded = roundedHalfAway(value / multiple) * multiple;
                    return roundedToTheScalesDatatype(step, rounded);
                });

    }

    /**
     * A rounded number, keeping the datatype of the number that was rounded.
     *
     * <p>Plain ROUND with no scale keeps the subject's datatype, so
     * {@code round $1.5} is a money and {@code round 50.5%} is a percent. Each
     * datatype's {@code A_ROUND} ends at its own {@code setDec} or its own
     * {@code SET_TYPE}, which is what makes this the rule rather than the
     * exception.
     */
    private static Value roundedKeepingTheDatatype(Value subject, double rounded) {
        return switch (subject) {
            case MoneyValue amount -> amount.amounting(BigDecimal.valueOf(rounded));
            case DecimalValue quantity when quantity.datatype() == Datatype.PERCENT ->
                    DecimalValue.percent(rounded);
            case IntegerValue whole -> IntegerValue.of((long) rounded);
            default -> DecimalValue.of(rounded);
        };
    }

    /**
     * A rounded number, taking the datatype of the scale rather than of the
     * subject.
     *
     * <p>This is the surprising half of ROUND and it is the same in all three
     * of {@code t-money.c}, {@code t-decimal.c} and {@code t-integer.c}: with
     * a scale, the answer is the scale's datatype. So
     * {@code round/to $1.333 .01} is the decimal 1.33 rather than a money, and
     * {@code round/to $0.5 1} is the integer 1. A money scale pulls the answer
     * the other way, so {@code round/to 0.5 $1} is a money.
     *
     * <p>Reading it as "keep the subject's datatype and let the scale say how
     * far to round" is the natural guess, and it disagrees on every mixed
     * call.
     */
    private static Value roundedToTheScalesDatatype(Value scale, double rounded) {
        return switch (scale) {
            case MoneyValue amount -> amount.amounting(BigDecimal.valueOf(rounded));
            case IntegerValue whole -> IntegerValue.of((long) rounded);
            case DecimalValue quantity when quantity.datatype() == Datatype.PERCENT ->
                    DecimalValue.percent(toFifteenDigits(rounded));
            default -> DecimalValue.of(toFifteenDigits(rounded));
        };
    }

    /**
     * A rounded answer trimmed back to the fifteen digits MOLD would show.
     *
     * <p>Dividing by the scale and multiplying back puts noise in the low
     * bits, so `round/to $1.333 .01` computes 1.3299999999999999 and has to
     * be brought back to 1.33 before anything compares it.
     */
    private static double toFifteenDigits(double rounded) {
        return new BigDecimal(rounded).round(new java.math.MathContext(15)).doubleValue();
    }

    /**
     * A block with its parens evaluated and everything else as written.
     *
     * <p>A paren that produces a block has its contents spliced in rather
     * than the block itself, which is what makes COMPOSE useful for
     * building a block out of pieces and not only for filling in single
     * values. {@code /only} turns that off and keeps the block whole.
     */
    private static List<Value> composed(
            BlockValue template, Evaluator evaluator, Context context,
            boolean keepingBlocksWhole, boolean goingDeep) {
        List<Value> built = new ArrayList<>();
        for (Value item : template.remaining()) {
            if (!(item instanceof BlockValue paren) || paren.datatype() != Datatype.PAREN) {
                if (goingDeep && item instanceof BlockValue nested
                        && nested.datatype() == Datatype.BLOCK) {
                    built.add(BlockValue.block(composed(
                            nested, evaluator, context, keepingBlocksWhole, true)));
                    continue;
                }
                if (goingDeep && item instanceof MapValue nested) {
                    built.add(composedMap(
                            nested, evaluator, context, keepingBlocksWhole, true));
                    continue;
                }
                built.add(item);
                continue;
            }
            for (Value produced : evaluator.evaluateEachOrRaise(
                    paren.as(Datatype.BLOCK), context)) {
                if (produced instanceof UnsetValue) {
                    continue;
                }
                if (!keepingBlocksWhole && produced instanceof BlockValue spliced
                        && spliced.datatype() == Datatype.BLOCK) {
                    built.addAll(spliced.remaining());
                } else {
                    built.add(produced);
                }
            }
        }
        return built;
    }

    /**
     * A map composed: keys stay as written, a paren value is evaluated with
     * splicing always suppressed, and /DEEP reaches blocks and maps stored
     * inside. {@code Compose_Block} pushes keys raw and recurses on
     * {@code IS_BLOCK(value) || IS_MAP(value)}.
     */
    private static MapValue composedMap(
            MapValue template, Evaluator evaluator, Context context,
            boolean keepingBlocksWhole, boolean goingDeep) {
        List<Value> pairs = new ArrayList<>();
        for (Value key : template.keys()) {
            pairs.add(key);
            Value held = template.select(key);
            if (held instanceof BlockValue paren
                    && paren.datatype() == Datatype.PAREN) {
                pairs.add(evaluator.evaluateOrRaise(
                        paren.as(Datatype.BLOCK), context));
            } else if (goingDeep && held instanceof BlockValue nested
                    && nested.datatype() == Datatype.BLOCK) {
                pairs.add(BlockValue.block(composed(
                        nested, evaluator, context, keepingBlocksWhole, true)));
            } else if (goingDeep && held instanceof MapValue nested) {
                pairs.add(composedMap(
                        nested, evaluator, context, keepingBlocksWhole, true));
            } else {
                pairs.add(held);
            }
        }
        return MapValue.of(pairs);
    }

    /**
     * Source text read into values, with no binding.
     *
     * <p>Takes a string or a binary, because a script that has read a file
     * has a binary and a script that built the text has a string.
     */
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
            return ErrorValue.of(ErrorCategory.SYNTAX,
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

        return (long) asMagnitude(argumentFor(
                refinement, TRANSCODE_ARGUMENT_ORDER, arguments, refinements, 1));
    }

    private static Value transcodedText(String text) {
        return transcodedText(text, 1);
    }

    private static Value transcodedText(String text, long firstLine) {
        TranscodeResult read = Transcoder.transcode(text, firstLine);
        return read.values().orElseThrow(() -> new Raised(read.error().orElseThrow()));
    }

    /**
     * The rest of {@code whole} after the first {@code howMany} code points.
     *
     * <p>Not {@code substring}, which counts UTF-16 units. The two part company at
     * the first character above the Basic Multilingual Plane, which mold-test.r3 has
     * and which cost sixty-six assertions when {@code topLevelSpans} got it wrong.
     */
    private static String skippingCodePoints(String whole, int howMany) {
        int[] codepoints = whole.codePoints().toArray();
        int taken = Math.min(howMany, codepoints.length);
        return new String(codepoints, taken, codepoints.length - taken);
    }

    /** What is left unread, as the kind of series that was handed in. */
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

    /** The order FIND and SELECT declare their three arguments in. */
    private static final List<String> SEARCH_ARGUMENTS = List.of("part", "with", "skip");

    /**
     * Where a needle sits, for FIND and SELECT alike.
     *
     * <p>{@code case A_FIND: case A_SELECT:} is one arm in {@code t-block.c}
     * and again in {@code t-string.c}, so the whole of the search is shared
     * and the two part company only in what they do with the answer.
     */
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

    /** How far along /PART asked the search to look, or as far as it goes. */
    private static int searchLimit(
            SeriesValue series, List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("part")) {
            return Integer.MAX_VALUE;
        }
        return (int) countUpTo(series, argumentFor(
                "part", SEARCH_ARGUMENTS, arguments, refinements, 2));
    }

    /** The record width /SKIP asked for, or one item at a time. */
    private static long searchStride(List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("skip")) {
            return 1;
        }
        return ((IntegerValue) argumentFor(
                "skip", SEARCH_ARGUMENTS, arguments, refinements, 2)).magnitude();
    }

    /**
     * Where a search stops: the tail, or the /PART limit if it is nearer.
     *
     * <p>{@code tail = index + Partial1(value, range)} in the C. The limit
     * is counted from the position rather than from the head, so the same
     * range asks for less of a series that has already been walked into.
     */
    private static int searchEnd(SeriesValue series, List<Value> items, int limit) {
        return limit < 0
                ? items.size()
                : (int) Math.min(items.size(), (long) series.index() - 1 + limit);
    }

    /**
     * Where a needle sits in a series, as a one-based index, or -1.
     *
     * <p>Copied from {@code Find_Block} in Rebol's {@code t-block.c} and
     * {@code find_string} in {@code t-string.c}. The structure follows
     * the C: a start, an end, a step, and one loop per kind of needle.
     *
     * <p>The C sets the walk this way. /REVERSE and /LAST both make the
     * step negative. /LAST starts at {@code end - len} and walks back to
     * the position. /REVERSE starts one before the position and walks
     * back to the head, thus it is the only search that may answer a
     * place the series has already passed.
     *
     * <p>/MATCH breaks out of the loop after the first item, thus it asks
     * whether the needle is here rather than anywhere ahead.
     */
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

    /**
     * How many items a needle takes up.
     *
     * <p>The C computes this before the search, because /LAST starts at
     * {@code end - len} and a run of three cannot start in the last two
     * places. /ONLY makes any needle one item.
     */
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
        if (wanted instanceof BitsetValue || wanted instanceof CharacterValue) {
            return 1;
        }
        return itemsOfNeedle(series, wanted).size();
    }

    /**
     * Whether the needle matches starting at this item.
     *
     * <p>A shape when /ANY asked for one, the same values when /SAME did,
     * and an ordinary run otherwise.
     */
    private static boolean matchesHere(
            SeriesValue series, List<Value> items, int at, Value wanted,
            Set<String> refinements, Wildcards wildcards, int end) {

        if (series instanceof StringValue text && refinements.contains("any")) {
            return patternEnd(text.head().text(), at, end, Molder.form(wanted),
                    refinements.contains("case"), wildcards) >= 0;
        }

        if ((wanted instanceof DatatypeValue || wanted instanceof TypesetValue)
                && !refinements.contains("only")) {
            return wanted instanceof DatatypeValue named
                    ? items.get(at).datatype() == named.represents()
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

    /**
     * Whether a needle's text sits here, character for character.
     *
     * <p>A string and a binary both hold items that a needle can be
     * turned into: a character or a byte. A bitset asks about one item
     * and matches whatever it holds.
     */
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

    /** A needle as the items the series it is searched in holds. */
    private static List<Value> itemsOfNeedle(SeriesValue series, Value wanted) {
        if (series instanceof BinaryValue && wanted instanceof BinaryValue bytes) {
            return itemsOf(bytes);
        }
        if (wanted instanceof CharacterValue letter) {
            return List.of(series instanceof BinaryValue
                    ? IntegerValue.of(letter.codepoint())
                    : letter);
        }
        if (series instanceof BinaryValue && wanted instanceof IntegerValue byteValue) {
            return List.of(byteValue);
        }
        return Molder.form(wanted).chars()
                .<Value>mapToObj(series instanceof BinaryValue
                        ? IntegerValue::of
                        : CharacterValue::of)
                .toList();
    }

    /** Whether a run of values sits here, one for one. */
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

    /**
     * Whether the very same values sit here, one for one.
     *
     * <p>`Compare_Values(value, val, 3)` in the C, and 3 is "same
     * (identical bits)" by its own comment. That is identity and not
     * equality: two objects holding the same fields are equal and are not
     * the same object, thus /SAME finds the one that was handed in and
     * not the copy beside it.
     */
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

    /**
     * Where a match starts, looking only at the first item of each record.
     *
     * <p>A negative width walks backwards from the position toward the
     * head, which is how a caller searches what it has already passed.
     */
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

    /**
     * Whether the needle matches starting at this record.
     *
     * <p>A run when the needle is a run, so a three-character needle can
     * match records of two -- but only where the run begins exactly where
     * a record does. A match that starts mid-record is passed over.
     */
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
            String sought = needle.datatype() == Datatype.STRING
                    ? needle.text()
                    : Molder.form(needle);
            if (at + sought.length() > items.size()) {
                return false;
            }
            for (int step = 0; step < sought.length(); step++) {
                if (!(items.get(at + step) instanceof CharacterValue character)
                        || Character.toLowerCase(character.codepoint())
                                != Character.toLowerCase(sought.charAt(step))) {
                    return false;
                }
            }
            return true;
        }
        return matches(items.get(at), wanted, refinements.contains("case"));
    }

    /** Everything from the head up to the position, for a reverse search. */
    private static List<Value> itemsBeforeHere(SeriesValue series) {
        return itemsOf(series.head()).subList(0, series.index() - 1);
    }

    /**
     * The two characters a needle may use to stand for others.
     *
     * <p>{@code c_some} and {@code c_one} in {@code Find_Str_Str_Any}. The
     * first stands for any run of characters including none, the second for
     * exactly one, and /ANY is what turns them on at all.
     *
     * <p>/WITH names them itself. That is the only way to search for a
     * needle holding a star of its own, because renaming the run character
     * leaves the star an ordinary letter again.
     */
    private record Wildcards(char anyRun, char oneCharacter) {

        private static final Wildcards STARS_AND_QUESTION_MARKS = new Wildcards('*', '?');

        /**
         * What /WITH named, keeping the default for whatever it left out.
         *
         * <p>The C reads the first character as the run one and the second
         * as the single one, each behind its own bounds check. So a
         * one-character /WITH renames the star and leaves the question mark
         * standing, and an empty one changes nothing.
         */
        static Wildcards named(Value given) {
            if (!(given instanceof StringValue chosen)) {
                return STARS_AND_QUESTION_MARKS;
            }
            String characters = chosen.text();
            return new Wildcards(
                    characters.isEmpty() ? '*' : characters.charAt(0),
                    characters.length() < 2 ? '?' : characters.charAt(1));
        }
    }

    /**
     * Where a wildcard match ends, or -1 if it does not match.
     *
     * <p>Needed as well as whether it matched, because how much a wildcard
     * took varies: /tail cannot land a fixed distance along the way it can
     * for a plain needle. A star tries its shortest length first, so the
     * end reported is the earliest one that works.
     *
     * <p>{@code upTo} is where the search stops, which /PART may bring in
     * from the tail. A wildcard match may not run past it -- {@code while (n
     * < len && pos < tail)} in the C -- and a star at the end of the pattern
     * takes exactly as far as it: {@code pos = (skip > 0) ? tail : start;}.
     * A plain needle is bounded by its own length instead, so that one may
     * run past the range and still match.
     */
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

    /**
     * Whether a run of values matches, element by element.
     *
     * <p>/SAME asks each element to be the same value rather than an
     * equal one, and the two part company on numbers of different
     * datatypes: 1.0 equals 1 and is not the same as it. In
     * `[1.0 3 1 3 1.0 2.0 1 2]` a loose search for [1 2] finds the
     * decimals at position five and a same search finds the integers at
     * seven.
     */
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

    /** How far past the match /tail lands, which a substring makes more than one. */
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
        if (series instanceof BlockValue
                && wanted instanceof BlockValue run
                && run.datatype() == Datatype.BLOCK
                && !refinements.contains("only")) {
            return run.remaining().size();
        }
        return series instanceof StringValue && !refinements.contains("only")
                ? Molder.form(wanted).length()
                : 1;
    }

    /**
     * The argument belonging to a refinement, or null when it was not
     * asked for.
     *
     * <p>Only asked-for refinements contribute arguments, so a position in
     * the list depends on which of the earlier ones were named.
     */
    private static Value argumentFor(
            String refinement, List<String> declaredOrder,
            List<Value> arguments, Set<String> asked) {
        return argumentFor(refinement, declaredOrder, arguments, asked, 1);
    }

    /**
     * As above, but told where the refinement arguments begin.
     *
     * <p>They follow the required ones, so a native taking two required
     * arguments has its first refinement argument at index two. Assuming
     * index one made APPEND/DUP read the value it was appending as the
     * count.
     */
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

    /**
     * A series sorted in place, in records of {@code stride} items.
     *
     * <p>Stable, so equal keys keep the order they arrived in. A record is
     * compared by its first item, which is what keeps a flat block of pairs
     * paired.
     */
    private static Value sorted(SeriesValue series, int stride, Value comparator,
            boolean mindingCase, boolean reversed, boolean wholeRecord,
            int howMany, Evaluator evaluator, boolean unstably) {
        int step = Math.max(1, stride);
        List<Value> items = itemsOf(series).subList(
                0, Math.min(howMany, itemsOf(series).size()));
        List<List<Value>> records = new ArrayList<>();
        for (int at = 0; at + step <= items.size(); at += step) {
            records.add(List.copyOf(items.subList(at, at + step)));
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
            records = mergeSorted(records, ordering);
        }

        List<Value> ordered = records.stream().flatMap(List::stream).toList();
        for (int at = 0; at < ordered.size(); at++) {
            if (series instanceof BlockValue block) {
                block.storage().set(block.index() + at, ordered.get(at));
            } else if (series instanceof StringValue text
                    && ordered.get(at) instanceof CharacterValue character) {
                text.storage().set(text.index() + at, character.codepoint());
            } else if (series instanceof BinaryValue bytes
                    && ordered.get(at) instanceof IntegerValue octet) {
                bytes.storage().set(bytes.index() + at, (int) octet.magnitude());
            }
        }
        return series;
    }

    /**
     * How two records order, by whatever /compare was given.
     *
     * <p>An integer names a column of each record rather than being a
     * function, and a block names several to try in turn. Without /skip
     * there are no records, so a column number means nothing and saying so
     * beats picking one.
     */
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
                ? askComparator(comparator,
                        lentRecordOf(series, left), lentRecordOf(series, right), evaluator)
                : askComparator(comparator,
                        lentElementOf(series, left.getFirst()),
                        lentElementOf(series, right.getFirst()), evaluator);
    }

    /**
     * A whole record handed to a /compare function, in the shape the C hands
     * it: a binary series lends a binary, a string a string, anything else a
     * block. So a comparator asking {@code binary? x} of a sorted binary sees
     * a binary rather than a block of byte numbers.
     */
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

    /**
     * A single element handed to a /compare function without /ALL: a byte of a
     * binary is lent as the character of that code point, the way the C hands
     * a char rather than the byte's number.
     */
    private static Value lentElementOf(SeriesValue series, Value element) {
        return series instanceof BinaryValue && element instanceof IntegerValue octet
                ? CharacterValue.of((int) octet.magnitude())
                : element;
    }

    /**
     * A merge sort that takes from the left whenever the two are not out
     * of order.
     *
     * <p>{@code stable_sort} in {@code f-stablemerge-sort.c}, and it has
     * to be written out rather than handed to the JVM's sort. The JVM's
     * is stable for a comparator that behaves like one, and a REBOL
     * comparator need not: a plain predicate such as {@code [a &lt; b]}
     * answers "left first" for a pair either way round, which is a
     * contradiction as far as a sort is concerned. The C's merge takes
     * from the left run whenever the comparison is at or below zero, so a
     * contradiction of that shape leaves the order alone. TimSort reads
     * the same contradiction as a descending run and turns it round.
     *
     * <p>The whole of the difference shows up as records with equal keys
     * coming back shuffled, which is not obviously a defect until
     * something sorts twice to order by two keys.
     */
    private static <T> List<T> mergeSorted(List<T> items, Comparator<T> order) {
        if (items.size() < 2) {
            return items;
        }
        int half = items.size() / 2;
        List<T> front = mergeSorted(new ArrayList<>(items.subList(0, half)), order);
        List<T> back = mergeSorted(
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

    /**
     * A record handed to a comparator, which may read it and not change it.
     *
     * <p>The C lends the same two blocks to every comparison and locks
     * them, because a comparator that grew one would corrupt the next
     * call. JEBOL builds a fresh block each time and still locks it, so
     * that a comparator written against a real R3 fails here in the same
     * way rather than quietly working.
     */
    private static BlockValue lentRecord(List<Value> record) {
        BlockValue lent = BlockValue.block(record);
        lent.storage().protectFromChange(true);
        return lent;
    }

    /**
     * Two records compared element by element, which is what /ALL asks
     * for.
     *
     * <p>Without it a record is ordered by its first element alone, so
     * `sort/skip [4 3 4 1] 2` leaves both records where they were --
     * their first elements are equal and the sort is stable.
     */
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
        for (Value named : columns) {
            if (!(named instanceof IntegerValue column)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "a column to sort by is a number, not "
                                + named.datatype().literalSpelling());
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

    /**
     * What a /compare function says about two values.
     *
     * <p>Two things here are what {@code Compare_Call} does and neither
     * is guessable.
     *
     * <p>The two values go in the other way round. The comparator asking
     * about the pair (first, second) is handed (second, first), so a
     * comparator written {@code [a > b]} counts down.
     *
     * <p>The answer starts at -1 and stays there unless the comparator
     * gave a true logic or a number at or above zero. So false means
     * "the one on the left comes first" rather than "these two are
     * equal", and only a numeric zero is a tie. That is what makes a
     * plain strict predicate stable: two equal items answer false, which
     * says leave the pair as it is.
     */
    private static int askComparator(
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

    /** The first item, removed. NONE when there is none. */
    private static Value takeOne(SeriesValue series) {
        if (series.lengthFromHere() == 0) {
            return NoneValue.none();
        }
        Value taken = itemsOf(series).getFirst();
        removeFrom(series, series.index(), 1);
        return taken;
    }

    /**
     * Several items, removed, as a series of the same kind.
     *
     * <p>A negative count takes backwards from the position toward the
     * head, so at the head it takes nothing at all.
     */
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

    /**
     * The numbers a value adds to a vector, whatever shape it arrived in.
     *
     * <p>{@code Modify_Vector} takes four: another vector contributes its
     * elements from where it points, a block its values, a binary the elements
     * its bytes spell at the target's own width, and anything else is one
     * number. A binary whose length is not a whole number of elements is
     * {@code invalid-data} rather than a partial read.
     */
    private static List<Value> numbersContributedTo(VectorKind kind, Value value) {
        if (value instanceof VectorValue source) {
            return source.remaining();
        }
        if (value instanceof BlockValue block) {
            return block.remaining();
        }
        if (value instanceof BinaryValue bytes) {
            return numbersSpeltBy(kind, bytes);
        }
        return List.of(value);
    }

    private static List<Value> numbersSpeltBy(VectorKind kind, BinaryValue bytes) {
        byte[] octets = bytes.octetsFromHere();
        if (octets.length % kind.bytes() != 0) {
            throw Raised.of(EvaluationFailure.INVALID_DATA, Molder.mold(bytes));
        }
        List<Value> numbers = new ArrayList<>();
        for (int at = 0; at < octets.length; at += kind.bytes()) {
            numbers.add(kind.read(kind.fromOctets(octets, at)));
        }
        return numbers;
    }

    /**
     * The numbers one of the three modifying actions adds, /PART and /DUP
     * applied.
     *
     * <p>/PART counts what the source offers, and for a binary it counts bytes
     * rather than elements: {@code append/part v #{0304} 1} adds one byte,
     * which is one number in an {@code int8!} vector and half of one in an
     * {@code int16!}. Half of one is invalid data. CHANGE does not come
     * through here with a limit at all, because its /PART counts what to
     * remove from the target instead.
     */
    private static List<Value> numbersAddedBy(VectorKind kind, List<Value> arguments,
            Set<String> refinements, boolean limitingTheSource) {

        int limit = limitingTheSource ? partCountFor(arguments, refinements) : -1;
        List<Value> once = numbersOfferedTo(kind, arguments.get(1), limit);
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

    /** The first few numbers put in order, in place, from the position. */
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

    /** The numbers shuffled where they are, which is what RANDOM does to a series. */
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

    /**
     * CHANGE on a vector: as many taken out as are put in, unless /PART said
     * otherwise.
     *
     * <p>/PART counts what to remove from the vector rather than what to take
     * from the source, which is the one place the three modifying actions read
     * the refinement differently. The C makes the same distinction in a single
     * line: {@code Partial1((action == A_CHANGE) ? value : arg, ...)}.
     */
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

    private static List<Value> numbersOfferedTo(VectorKind kind, Value value, int limit) {
        if (value instanceof BinaryValue bytes) {
            int offered = bytes.lengthFromHere();
            int taking = limit < 0 ? offered : Math.min(limit, offered);
            if (taking % kind.bytes() != 0) {
                throw Raised.of(EvaluationFailure.INVALID_DATA, Molder.mold(bytes));
            }
            byte[] octets = bytes.octetsFromHere();
            List<Value> numbers = new ArrayList<>();
            for (int at = 0; at < taking; at += kind.bytes()) {
                numbers.add(kind.read(kind.fromOctets(octets, at)));
            }
            return numbers;
        }
        List<Value> offered = numbersContributedTo(kind, value);
        return limit < 0 || limit >= offered.size()
                ? offered
                : offered.subList(0, Math.max(0, limit));
    }

    /** A fresh vector of one kind holding numbers taken from another. */
    private static VectorValue vectorHolding(VectorKind kind, List<Value> numbers) {
        VectorStorage made = new VectorStorage(kind, 0);
        numbers.forEach(number -> made.append(VectorPath.storedFormOf(kind, number)));
        return new VectorValue(made, 1);
    }

    /**
     * The pixels TAKE removed, as an image of their own.
     *
     * <p>A row rather than a rectangle: `Reset_Height` derives the height from
     * the count and the width, and what was taken has no width of its own until
     * something gives it one.
     */
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

    /** Half away from zero, which is what REBOL rounds and a JVM does not. */
    private static double roundedHalfAway(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Whether a value is the zero of its own datatype.
     *
     * <p>{@code if (type >= REB_INTEGER && type <= REB_TIME)} is a range over
     * the datatype table, so it takes in the char, the pair and the tuple as
     * well as the four numbers: a zero pair and a zero tuple are zero, and so
     * is the null character.
     *
     * <p>A bitset is the one that is not a comparison at all.
     * {@code Is_Zero_Bitset} asks whether every byte is what an empty set
     * would hold -- nought, or {@code 0xFF} where the set is written as a
     * complement -- so {@code complement make bitset! #{FF}} is zero because
     * it holds nothing, while a complemented charset is not.
     *
     * <p>Everything else answers no rather than being refused, which is why
     * the declared argument is a bare {@code value} and a string simply says
     * false. Taking numbers only made ZERO? raise on the datatype the question
     * was written for.
     */
    private static boolean isTheZeroOfItsDatatype(Value value) {
        if (value instanceof BitsetValue members) {
            byte held = (byte) (members.isComplemented() ? 0xFF : 0);
            for (byte octet : members.octets()) {
                if (octet != held) {
                    return false;
                }
            }
            return true;
        }
        if (value instanceof PairValue pair) {
            return pair.x() == 0 && pair.y() == 0;
        }
        if (value instanceof TupleValue segments) {
            return java.util.Arrays.stream(segments.segments()).allMatch(part -> part == 0);
        }
        if (value instanceof CharacterValue letter) {
            return letter.codepoint() == 0;
        }
        if (value instanceof TimeValue clock) {
            return clock.nanoseconds() == 0;
        }
        if (value instanceof MoneyValue amount) {
            return amount.amount().signum() == 0;
        }
        return Comparison.isNumeric(value) && Comparison.asDouble(value) == 0.0;
    }

    /**
     * The bits a value names when it is handed to a set that already exists,
     * which is what APPEND, INSERT, REMOVE and CLEAR each ask for.
     *
     * <p>One arm differs from the set MAKE builds, and only one. A number
     * given to {@code make bitset!} asks for room -- {@code make bitset! 8} is
     * eight bits of nothing -- while the same number given to {@code append}
     * names the bit to turn on. {@code Make_Bitset} sizes and stops; the C
     * says so in as many words, "nothing more to do". {@code Set_Bits} reaches
     * {@code Set_Bit(bset, n, set)}.
     *
     * <p>Reading both through one helper made {@code alter bs 1} report that
     * it had added a bit and leave the set exactly as it was, because the
     * number had been read as a request for one bit of room.
     *
     * <p>Every other shape means the same thing to both, so they share the
     * rest.
     */
    private static Value bitsMeantBy(Value source) {
        if (source instanceof IntegerValue point) {
            return BitsetValue.of(withBitSet(new byte[0], bitAsked(point.magnitude())));
        }
        if (!(source instanceof CharacterValue || source instanceof StringValue
                || source instanceof BinaryValue || source instanceof BlockValue)) {
            throw Raised.of(EvaluationFailure.INVALID_TYPE, Molder.mold(source));
        }
        return bitsetOf(source);
    }

    /** A bitset holding every character code in what it is given. */
    private static Value bitsetOf(Value source) {
        return switch (source) {
            case StringValue text -> BitsetValue.ofCharacters(text.text().codePoints().toArray());
            case CharacterValue character ->
                    BitsetValue.ofCharacters(character.codepoint());
            case IntegerValue room -> BitsetValue.of(
                    new byte[(bitAsked(room.magnitude()) + 7) / 8]);
            case BinaryValue octets -> BitsetValue.of(octets.octetsFromHere());
            case BitsetValue existing -> existing.duplicate();
            case BlockValue members -> bitsetFromBlock(members);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    Molder.mold(source) + " names no characters a set could hold");
        };
    }

    /**
     * A bitset from a block, which may open with the word NOT.
     *
     * <p>Two things the block form can do that a bare string or binary
     * cannot. The word NOT at the head complements the set:
     *
     * <pre>
     * val = VAL_BLK_DATA(val);
     * if (IS_SAME_WORD(val, SYM_NOT)) {
     *     BITS_NOT(bset) = TRUE;
     *     val++;
     * }
     * </pre>
     *
     * <p>And a binary inside it supplies the octets whole rather than naming
     * code points one at a time, which is how a large set is written without
     * listing it. Rebol's own JSON codec writes both at once:
     * {@code to bitset! [not #{FFFFFFFF2000000000000008}]} is every character
     * except the control codes, the double quote and the backslash.
     *
     * <p>Both were dropped here, so that set came out empty and every
     * character of a string was escaped as a code point.
     */
    private static Value bitsetFromBlock(BlockValue members) {
        List<Value> items = members.remaining();
        boolean complemented = !items.isEmpty()
                && items.getFirst() instanceof WordValue word
                && word.canonical().equals("not");
        BlockValue rest = complemented ? members.atIndex(members.index() + 1) : members;
        List<Value> named = rest.remaining();
        BitsetValue set = BitsetValue.of(octetsNamedBy(named, members));
        return complemented ? set.complemented() : set;
    }

    /**
     * The octets a block of bitset specs names, laid one over another.
     *
     * <p>{@code Set_Bits} walks the block and turns bits on as it goes, so
     * every spec adds to what came before rather than replacing it:
     * {@code [1 - 3 #{80}]} is the range and the byte together. Reading only a
     * lone binary meant two of them cancelled to nothing.
     *
     * <p>Five shapes are understood. A char or a number names one bit, and a
     * dash between two of them names the run. A string names one bit per
     * character. A binary supplies the octets whole, which is how a large set
     * is written without listing it. And the word {@code bits} in front of a
     * binary is the same thing said out loud.
     *
     * <p>A spec that is none of those is an invalid argument, which is what
     * makes a trailing {@code not} an error: the word only means the
     * complement at the head of the block, and anywhere else it names nothing.
     */
    private static byte[] octetsNamedBy(List<Value> specs, BlockValue whole) {
        byte[] octets = new byte[0];
        for (int at = 0; at < specs.size(); at++) {
            Value spec = specs.get(at);
            if (spec instanceof WordValue named && named.canonical().equals("bits")) {
                if (at + 1 >= specs.size()
                        || !(specs.get(at + 1) instanceof BinaryValue held)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(whole));
                }
                octets = withOctetsSet(octets, held.octetsFromHere());
                at++;
            } else if (spec instanceof BinaryValue held) {
                octets = withOctetsSet(octets, held.octetsFromHere());
            } else if (spec instanceof StringValue text) {
                for (int point : text.text().codePoints().toArray()) {
                    octets = withBitSet(octets, point);
                }
            } else if (spec instanceof CharacterValue || spec instanceof IntegerValue) {
                int from = bitAsked((long) codePointOf(spec));
                int to = from;
                if (at + 1 < specs.size()
                        && specs.get(at + 1) instanceof WordValue dash
                        && dash.spelling().equals("-")) {
                    to = bitAsked((long) codePointOf(farEndOfTheRun(spec, specs, at + 2)));
                    at += 2;
                }
                if (to < from) {
                    throw Raised.of(EvaluationFailure.PAST_END, String.valueOf(to));
                }
                for (int point = from; point <= to; point++) {
                    octets = withBitSet(octets, point);
                }
            } else {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(whole));
            }
        }
        return octets;
    }

    /**
     * The value closing a run, which has to be the same kind as the one that
     * opened it.
     *
     * <p>The C asks the question twice and each time about one type: a char
     * opening a run reaches {@code if (IS_CHAR(val))} and an integer reaches
     * {@code if (IS_INTEGER(val))}, and either failing is {@code Trap_Arg}. So
     * {@code [#"a" - 5]} is an invalid argument rather than the run from
     * {@code a} to five, and a dash with nothing after it is the same error
     * against the end of the block.
     */
    private static Value farEndOfTheRun(Value opening, List<Value> specs, int at) {
        Value closing = at < specs.size() ? specs.get(at) : UnsetValue.unset();
        if (opening.datatype() != closing.datatype()) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(closing));
        }
        return closing;
    }

    /** The octets with one more bit turned on, grown if they do not reach it. */
    private static byte[] withBitSet(byte[] octets, int point) {
        int reaching = point / 8 + 1;
        byte[] grown = octets.length >= reaching
                ? octets
                : java.util.Arrays.copyOf(octets, reaching);
        grown[point / 8] |= (byte) (0x80 >> (point % 8));
        return grown;
    }

    /** The octets with another set's laid over them, grown to fit. */
    private static byte[] withOctetsSet(byte[] octets, byte[] more) {
        byte[] grown = octets.length >= more.length
                ? octets
                : java.util.Arrays.copyOf(octets, more.length);
        for (int at = 0; at < more.length; at++) {
            grown[at] |= more[at];
        }
        return grown;
    }

    /**
     * The code points a block of bitset members names.
     *
     * <p>Three ways to name one: a character, a number naming it by code
     * point, and a dash between two characters meaning everything
     * between. The range is the one that matters -- it is the only way
     * to write a large set at all, and dropping it silently gave a set
     * holding just the two ends.
     */
    private static int[] codePointsIn(BlockValue members) {
        List<Value> items = members.remaining();
        List<Integer> points = new ArrayList<>();
        for (int at = 0; at < items.size(); at++) {
            boolean isRange = at + 2 < items.size()
                    && items.get(at + 1) instanceof WordValue dash
                    && dash.spelling().equals("-");
            if (isRange) {
                int from = codePointOf(items.get(at));
                int to = codePointOf(items.get(at + 2));
                for (int point = from; point <= to; point++) {
                    points.add(point);
                }
                at += 2;
                continue;
            }
            if (items.get(at) instanceof CharacterValue || items.get(at) instanceof IntegerValue) {
                points.add(codePointOf(items.get(at)));
            } else if (items.get(at) instanceof StringValue text) {
                text.text().codePoints().forEach(points::add);
            }
        }
        return points.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int codePointOf(Value value) {
        return value instanceof CharacterValue character
                ? character.codepoint()
                : (int) Comparison.asDouble(value);
    }

    /**
     * Whether a bitset holds what it is being asked about, which is
     * {@code Check_Bits} and is the one arm PICK and FIND share:
     * {@code case A_PICK: case A_FIND:}.
     *
     * <p>Five things may be asked and only one of them was answered. A char
     * and an integer each name one code point -- the integer being a code
     * point is why {@code pick charset "a" 97} is true. A string or a binary
     * asks about every character in it. A block asks about every one it names,
     * ranges included, which is the same grammar that builds a set in the
     * first place and so is read by the same walk.
     *
     * <p>Every one of them has to be held, unless {@code /any} was asked for
     * and then one will do. Answering only a char left every other form
     * quietly false, which reads as "the set does not hold it" rather than as
     * a question that was never asked.
     *
     * <p>A char FIND asks about matches either case unless {@code /case} was
     * given, and a number naming the same code point never does. The C spells
     * out all three conditions at once --
     * {@code IS_CHAR(arg) && action == A_FIND && !D_REF(ARG_FIND_CASE)} -- so
     * {@code find charset [#"A"] #"a"} is true and
     * {@code find charset [#"A"] 97} is false.
     */
    private static boolean bitsetHolds(
            BitsetValue members, Value asked, boolean anyWillDo, boolean eitherCaseWillDo) {
        if (asked instanceof CharacterValue letter) {
            return eitherCaseWillDo
                    ? members.holdsEitherCaseOf(letter.codepoint())
                    : members.holds(letter.codepoint());
        }
        if (asked instanceof IntegerValue codepoint) {
            return members.holds(bitAsked(codepoint.magnitude()));
        }
        return holdsEachOf(members, codePointsAskedAboutBy(asked), anyWillDo);
    }

    private static boolean bitsetHolds(BitsetValue members, Value asked, boolean anyWillDo) {
        return bitsetHolds(members, asked, anyWillDo, false);
    }

    /**
     * A bit a caller named, or {@code out-of-range} where no bit has that
     * number.
     *
     * <p>{@code Int32s(val, 0)} is the C asking for a whole number no smaller
     * than nought and raising rather than returning one. Below zero indexes
     * before the first byte, so leaving it to the array made a negative escape
     * as a Java exception where a script should have caught an error --
     * Rebol's own suite asks exactly that of the most negative number there
     * is, and notes that Red answers differently.
     */
    private static int bitAsked(long codepoint) {
        if (codepoint < 0 || codepoint > Integer.MAX_VALUE) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    codepoint + " names no bit a set could hold");
        }
        return (int) codepoint;
    }

    /**
     * What a path through a bitset answers, which is the question PICK asks.
     *
     * <p>{@code bs/3} and {@code pick bs 3} are one thing, because the C sends
     * a path selection to {@code Pick_Path} and a bitset's PICK arm is what
     * answers it. Raising {@code invalid-path} instead made a path the one way
     * of asking a bitset something that did not work.
     */
    static Value bitsetHoldsForAPath(BitsetValue members, Value selector) {
        return LogicValue.of(bitsetHolds(members, selector, false));
    }

    private static int[] codePointsAskedAboutBy(Value asked) {
        if (asked instanceof StringValue text) {
            return text.text().codePoints().toArray();
        }
        if (asked instanceof BinaryValue octets) {
            byte[] bytes = octets.octetsFromHere();
            int[] points = new int[bytes.length];
            for (int at = 0; at < bytes.length; at++) {
                points[at] = bytes[at] & 0xFF;
            }
            return points;
        }
        if (asked instanceof BlockValue specs) {
            int[] points = codePointsIn(specs);
            for (int point : points) {
                bitAsked(point);
            }
            return points;
        }
        throw Raised.of(EvaluationFailure.INVALID_TYPE, Molder.mold(asked));
    }

    private static boolean holdsEachOf(BitsetValue members, int[] wanted, boolean anyWillDo) {
        for (int point : wanted) {
            if (members.holds(point) == anyWillDo) {
                return anyWillDo;
            }
        }
        return !anyWillDo;
    }

    /**
     * A shuffle done Rebol's way rather than the JVM's.
     *
     * <p>Both are Fisher-Yates and they are not the same shuffle. Rebol walks
     * down from the end taking {@code Random_Int % n} each time, and Java's
     * {@code Collections.shuffle} draws differently and consumes a different
     * number of values. With the generator now matching, this is the other
     * half of making {@code random/seed 1} reproduce Rebol's own answers.
     */
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

    /**
     * A string shuffled in place, the way a block is.
     *
     * <p>Building a new string instead leaves the caller's own string
     * untouched, which is the whole point of shuffling one, and skips the
     * refusal a protected string is owed.
     */
    private Value shuffledText(StringValue text) {
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

    /** A binary shuffled in place, exactly as a string is. */
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

    /**
     * A question about whether every character fits a range.
     *
     * <p>Empty answers true rather than false, which is the useful way
     * round: a guard asking "is this safe to write as ASCII" wants yes
     * for nothing at all.
     */
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

    /** How many arguments a callable consumes, refinements aside. */
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

    /** Spaces to tabs, or tabs to spaces, at a stop of the given width. */
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

    /** Gives the context a slot for every word the block uses. */
    private static void defineFreshWordsOf(BlockValue block, Context target, boolean settersOnly) {
        List<Value> words = new ArrayList<>();
        gatherWords(block, true, settersOnly, words);
        words.forEach(word -> target.define(((WordValue) word).canonical()));
    }

    /** Every word a block uses, unique and in the order first seen. */
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
            if (found.stream().noneMatch(seen -> seen.equals(plain))) {
                found.add(plain);
            }
        }
    }

    /**
     * The bytes a value contributes when it goes into a binary.
     *
     * <p>{@code Join_Binary} in {@code s-make.c}, and the branches of
     * {@code Modify_String} that run when the target is a binary. One
     * rule underneath all of it: text becomes its UTF-8 bytes, so a
     * character above the ASCII range contributes several bytes rather
     * than one. Writing the code point straight in gives one byte and is
     * right for every ASCII character, which is what makes it hard to
     * notice.
     *
     * <p>{@code howMany} is a {@code /part} count of the source, or a
     * negative number for all of it. It counts characters of the source
     * and the encoding happens afterwards, so one character of U+2190
     * still contributes three bytes. A character value is not a series
     * and ignores the count entirely.
     */
    private static int[] octetsContributedBy(Value value, int howMany) {
        List<Integer> octets = new ArrayList<>();
        gatherOctets(value, howMany, octets);
        int[] gathered = new int[octets.size()];
        for (int at = 0; at < gathered.length; at++) {
            gathered[at] = octets.get(at);
        }
        return gathered;
    }

    private static void gatherOctets(Value value, int howMany, List<Integer> into) {
        switch (value) {
            case BinaryValue source -> {
                int taking = howMany < 0
                        ? source.lengthFromHere()
                        : Math.min(howMany, source.lengthFromHere());
                for (int at = 0; at < taking; at++) {
                    into.add(source.storage().at(source.index() + at) & 0xFF);
                }
            }
            case StringValue text -> {
                String held = text.text();
                int taking = howMany < 0
                        ? held.length()
                        : Math.min(howMany, held.length());
                addUtf8(held.substring(0, taking), into);
            }
            case CharacterValue letter ->
                    addUtf8(Character.toString(letter.codepoint()), into);
            case IntegerValue whole -> {
                if (whole.magnitude() < 0 || whole.magnitude() > 255) {
                    throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                            "a byte is 0 to 255, not " + whole.magnitude());
                }
                into.add((int) whole.magnitude());
            }
            case TupleValue tuple -> {
                for (int at = 1; at <= tuple.segmentCount(); at++) {
                    into.add(tuple.octetAt(at));
                }
            }
            case BlockValue several -> {
                List<Value> items = several.remaining();
                int taking = howMany < 0 ? items.size() : Math.min(howMany, items.size());
                for (int at = 0; at < taking; at++) {
                    if (items.get(at) instanceof BlockValue nested) {
                        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                nested.datatype().literalSpelling()
                                        + " cannot go into a binary");
                    }
                    gatherOctets(items.get(at), -1, into);
                }
            }
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " cannot go into a binary");
        }
    }

    private static void addUtf8(String text, List<Integer> into) {
        for (byte encoded : text.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            into.add(encoded & 0xFF);
        }
    }

    /**
     * The error catalogue as errors.reb declares it: category set-words each
     * holding a block of id set-words and their message templates. Read from
     * the source {@link #useErrorCatalogue} handed in, past its own header.
     */
    private List<Value> catalogueEntries() {
        try {
            TranscodeResult read = Transcoder.transcode(errorCatalogueSource);
            List<Value> values = read.values().orElseThrow().remaining();
            return values.subList(2, values.size());
        } catch (RuntimeException unreadable) {
            return List.of();
        }
    }

    /**
     * DO of a binary, run as the script it is: the header is read by
     * sys/load-header, its length bounds the body, an unmet needs refuses,
     * and a top-level RETURN unwinds to the DO.
     */
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
            throw new Raised(ErrorValue.of(ErrorCategory.SYNTAX,
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

    /** Whether this interpreter's version reaches what needs: asks for. */
    private static boolean interpreterMeets(TupleValue wanted, Evaluator evaluator) {
        Value version = pathInto(evaluator.systemContext(), "system", "version");
        return version instanceof TupleValue own
                && !Comparison.holds(wanted, own, Comparison.Strictness.GREATER);
    }

    /** The bytes between a position and an end index in the same binary. */
    private static byte[] spanOfOctets(BinaryValue from, int endIndex) {
        int howMany = Math.max(0, endIndex - from.index());
        byte[] span = new byte[howMany];
        for (int at = 0; at < howMany; at++) {
            span[at] = (byte) from.storage().at(from.index() + at);
        }
        return span;
    }

    /** A string DO/NEXT steps through, loaded and bound the way DO loads it. */
    private static BlockValue loadedForStepping(String source, Context context) {
        TranscodeResult read = Transcoder.transcode(source);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return Binder.bindAndDefine(read.values().orElseThrow(), context);
    }

    /**
     * A position stranded past the tail, brought back to the tail. The C's
     * common action setup does it for every series action -- {@code if
     * (index > tail) VAL_INDEX(value) = index = tail;} -- so a change or an
     * insert at such a position appends instead of failing.
     */
    private static SeriesValue clampedToTail(SeriesValue series) {
        int tail = series.storageLength() + 1;
        return series.index() > tail ? series.atIndex(tail) : series;
    }

    /** Putting a value in at the position, whichever kind of series. */
    private static Value insertInto(SeriesValue stranded, Value value) {
        SeriesValue series = clampedToTail(stranded);
        switch (series) {
            case BlockValue block -> {
                List<Value> items = value instanceof BlockValue added
                        ? added.remaining()
                        : List.of(value);
                for (int at = items.size(); at > 0; at--) {
                    block.storage().insertAt(block.index(), items.get(at - 1));
                }
            }
            case StringValue text -> {
                String added = Molder.form(value);
                for (int at = 0; at < added.length(); at++) {
                    text.storage().insertAt(text.index() + at, added.charAt(at));
                }
            }
            case ImageValue image -> insertPixels(image, value);
            case GobValue gob -> insertChildren(gob, gob.index(), value);
            case VectorValue vector -> {
                List<Value> numbers = numbersContributedTo(vector.kind(), value);
                for (int at = numbers.size(); at > 0; at--) {
                    vector.storage().insertAt(vector.index(),
                            VectorPath.storedFormOf(vector.kind(), numbers.get(at - 1)));
                }
            }
            case BinaryValue bytes -> {
                int[] octets = octetsContributedBy(value, -1);
                for (int at = octets.length; at > 0; at--) {
                    bytes.storage().insertAt(bytes.index(), octets[at - 1]);
                }
            }
        }
        return series.head();
    }

    /**
     * How much of the source a /PART limit asks for, as a count, or -1 for all
     * of it.
     *
     * <p>INSERT and APPEND both declare it in the same place, so the two read
     * it the same way rather than each working out where it sits.
     *
     * <p>A limit may be a number or a position, and a position means "up to
     * here". Reading only the number turned {@code insert/part output a b}
     * into an insert of everything from {@code a} onwards, which is how REWORD
     * over a binary came to answer its whole template with the substitutions
     * appended to it.
     */
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

    /** Removing one item, whichever kind of series holds it. */
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

    private static Value reversedText(StringValue text) {
        String forwards = text.text();
        for (int at = 0; at < forwards.length(); at++) {
            text.storage().set(text.index() + at,
                    forwards.charAt(forwards.length() - 1 - at));
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

    /**
     * REMOVE-EACH over a series that is not a block.
     *
     * <p>A binary yields its bytes and a string its characters, and each
     * is removed where the body answers true. Going through the storage
     * is also what makes a protected series refuse.
     */
    private static Value removedEachFrom(
            SeriesValue series, List<Value> arguments, Evaluator evaluator,
            Context within) {

        refuseIfProtected(series);
        Context locals = Context.loopFrameOf(within);
        WordValue word = (WordValue) arguments.getFirst();
        locals.define(word.spelling());
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        for (int at = series.storageLength(); at >= series.index(); at--) {
            Value item = switch (series) {
                case BinaryValue bytes -> IntegerValue.of(bytes.storage().at(at));
                case VectorValue numbers -> numbers.elementAt(at);
                default -> CharacterValue.of(((StringValue) series).storage().at(at));
            };
            locals.set(word.spelling(), item);
            if (evaluator.evaluateOrRaise(body, locals).isTruthy()) {
                removeFrom(series, at, 1);
            }
        }
        return series;
    }

    /**
     * REMOVE-EACH over a map: pairs out, and pairs counted.
     *
     * <p>The count is halved -- {@code SET_INTEGER(DS_RETURN, IS_MAP(value) ?
     * index / 2 : index);} -- for the same reason APPEND/PART is halved. A
     * caller counting values would be told it removed twice what it removed.
     *
     * <p>The keys are gathered before any are taken out. Removing while walking
     * would be reading a map that is changing underneath, and the result would
     * depend on where in its storage each key happened to sit.
     */
    private static Value removedEachPairFrom(
            MapValue map, List<Value> arguments, Set<String> refinements,
            Evaluator evaluator, Context within) {

        requireChangeable(map);
        List<WordValue> names = loopNamesIn(arguments.getFirst(), "remove-each");
        refuseMoreNamesThanAPairHas(map, namesThatTakeAValue(names));
        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.define(name.spelling()));
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        List<Value> pairs = map.walkable();
        List<Value> takeOut = new ArrayList<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            setLoopNames(locals, names, pairs, at, map);
            if (evaluator.evaluateOrRaise(body, locals).isTruthy()) {
                takeOut.add(pairs.get(at));
            }
        }
        takeOut.forEach(map::remove);
        return refinements.contains("count")
                ? IntegerValue.of(takeOut.size())
                : map;
    }

    /** Refuses a change to a protected series before attempting it. */
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

    /**
     * The text a modifying action is adding, /PART and /DUP applied.
     *
     * <p>APPEND worked this out and INSERT did not, so {@code insert/dup} put
     * one copy in however many were asked for. PAD is four lines of REBOL with
     * this call in the middle of it, which is how a missing refinement showed
     * up as a string one character short.
     */
    private static String textContributedBy(
            List<Value> arguments, Set<String> refinements) {

        Value adding = duplicated(arguments.get(1), arguments, refinements);
        String written = adding instanceof BlockValue added
                && added.datatype() == Datatype.BLOCK
                ? runTogether(added)
                : Molder.form(adding);
        return howManyWanted(arguments.get(1), arguments, refinements, 2)
                .map(count -> written.substring(0,
                        Math.max(0, Math.min(count.intValue(), written.length()))))
                .orElse(written);
    }

    /**
     * What is being added, repeated as many times as /DUP asked.
     *
     * <p>A block being spliced repeats as a whole, so
     * `append/dup [1] [2 3] 2` adds 2 3 2 3. A count of zero adds
     * nothing, which is what makes /DUP usable with a computed number.
     */
    private static Value duplicated(
            Value value, List<Value> arguments, Set<String> refinements) {

        Value times = argumentFor(
                "dup", List.of("part", "dup"), arguments, refinements, 2);
        if (times == null) {
            return value;
        }
        List<Value> pieces = value instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block.remaining()
                : List.of(value);
        List<Value> repeated = new ArrayList<>();
        for (long round = 0; round < wholeCountOf(times); round++) {
            repeated.addAll(pieces);
        }
        return BlockValue.block(repeated);
    }

    /** What {@code Int32} makes of a count: whole, truncated, or refused. */
    private static long wholeCountOf(Value times) {
        return switch (times) {
            case IntegerValue count -> count.magnitude();
            case DecimalValue fraction when fraction.datatype() != Datatype.PERCENT ->
                    (long) Comparison.asDouble(fraction);
            default -> throw Raised.of(EvaluationFailure.INVALID_TYPE,
                    Molder.mold(times) + " is not a count of repetitions");
        };
    }

    /**
     * Every line with the first line's indentation taken off the front.
     *
     * <p>What TRIM/AUTO means: the shallowest indentation becomes none
     * and anything deeper keeps the difference, so a block of code pasted
     * into a string comes back with its shape intact.
     */
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

    /**
     * The true default TRIM: the whole string's leading and trailing
     * whitespace goes, then each line loses its own leading indentation and
     * trailing whitespace, and one line feed survives at the end if the
     * trimmed tail held one. {@code trim_head_tail} with neither flag.
     */
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

    /** The words a block or an object names, for COLLECT-WORDS/IGNORE. */
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

    /**
     * What TAIL? admits: `series [series! gob! port! bitset! typeset!
     * map!]`, actions.reb line 143.
     *
     * <p>Written out rather than taken as "every series", because the
     * list is what decides whether `tail? none` refuses. NONE and OBJECT
     * are deliberately absent: EMPTY? is the same action under a spec
     * that adds them -- mezz-series.reb writes `make :tail? [...]` with
     * the wider list -- so the body below answers for an object while
     * this list turns one away at the word TAIL? itself.
     */
    private static final Set<Datatype> SERIES_LIKE = EnumSet.of(
            Datatype.STRING, Datatype.FILE, Datatype.URL, Datatype.EMAIL,
            Datatype.TAG, Datatype.REF, Datatype.BINARY,
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH, Datatype.SET_PATH,
            Datatype.GET_PATH, Datatype.LIT_PATH, Datatype.HASH,
            Datatype.PORT, Datatype.BITSET,
            Datatype.TYPESET, Datatype.MAP, Datatype.GOB, Datatype.IMAGE,
            Datatype.VECTOR);

    /**
     * UNION, INTERSECT or EXCLUDE, which differ only in what they keep.
     *
     * <p>/CASE stops the case folding and /SKIP reads both series as
     * records of a fixed width, comparing whole records rather than
     * single items.
     */
    private void defineSetOperation(String name, Combination how) {
        define(name, List.of(
                        Parameter.required("first", setOperandOr(Datatype.BLOCK)),
                        Parameter.required("second", setOperandOr(Datatype.BLOCK)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    Value width = argumentFor("skip", List.of("skip"), arguments, refinements, 2);
                    return combined(arguments, how, refinements.contains("case"),
                            recordWidthOf(width));
                });
    }

    /**
     * A /SKIP record width, refusing one that is not a width at all.
     *
     * <p>Clamping to one turned {@code union/skip [2 1] [2 1] -2} into a
     * perfectly ordinary call over single items, so a caller who worked the
     * number out wrongly was told nothing. A record cannot be shorter than one
     * item and the C says so with {@code out-of-range}.
     */
    private static int recordWidthOf(Value width) {
        if (!(width instanceof IntegerValue wanted)) {
            return 1;
        }
        if (wanted.magnitude() < 1) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(wanted));
        }
        return (int) wanted.magnitude();
    }

    /**
     * A series copied, and what it holds copied too when asked.
     *
     * <p>Anything that is not a series comes back as it was, because a
     * number has nothing to copy and refusing it would make COPY unusable
     * on a block holding one.
     */
    private static Value copied(Value original, boolean deeply) {
        return copied(original, deeply, DEEP_COPIED);
    }

    /**
     * Which datatypes COPY duplicates rather than shares.
     *
     * <p>`TS_DEEP_COPIED` is the standard set -- every series and a map, less
     * the four the C names as not copied: `TS_NOT_COPIED (TYPESET(REB_IMAGE) |
     * TYPESET(REB_VECTOR) | TYPESET(REB_TASK) | TYPESET(REB_PORT))`. An image is
     * shared because copying one is expensive, and a port because two ports on
     * one connection would be two ways to close it.
     */
    private static final Set<Datatype> DEEP_COPIED = EnumSet.of(
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH, Datatype.SET_PATH,
            Datatype.GET_PATH, Datatype.LIT_PATH, Datatype.HASH,
            Datatype.STRING, Datatype.FILE, Datatype.URL, Datatype.EMAIL,
            Datatype.TAG, Datatype.REF, Datatype.BINARY, Datatype.BITSET,
            Datatype.MAP, Datatype.FUNCTION);

    /**
     * The datatypes /TYPES named, or the standard set when it was not asked.
     *
     * <p>`types |= CP_DEEP | (D_REF(ARG_COPY_TYPES) ? 0 : TS_DEEP_COPIED);` --
     * so naming any set replaces the standard one rather than adding to it, and
     * that is the whole point of the refinement: `copy/deep/types b string!`
     * reaches every level and copies only the strings it finds.
     */
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
                && !(original instanceof ObjectValue)) {
            return original;
        }
        return switch (original) {
            case BlockValue block -> new BlockValue(new BlockStorage(
                    block.remaining().stream()
                            .map(item -> memberCopiedFrom(item, deeply, kinds))
                            .toList()),
                    1, block.datatype());
            case StringValue text -> StringValue.of(text.text(), text.datatype());
            case BinaryValue binary -> copiedBytes(binary, binary.lengthFromHere());
            case VectorValue vector -> copiedElements(vector, vector.lengthFromHere());
            case BitsetValue members -> members.duplicate();
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
            default -> original;
        };
    }

    /**
     * What a container puts back in place of one of the things it held.
     *
     * <p>Two refinements and two questions, and they are not the same one.
     * {@code /types} says which datatypes are duplicated rather than shared,
     * and {@code /deep} says whether to go on doing it inside whatever was
     * duplicated. The C keeps them apart in one line --
     * {@code if (deep) types |= CP_DEEP | (types_given ? types :
     * TS_DEEP_COPIED);} -- so asking for types alone reaches one level down
     * and no further.
     *
     * <p>That is why {@code copy/types m string!} gives a map whose strings
     * are its own and whose nested map is still shared, and why every string
     * inside that nested map is untouched: the map was not copied, so there
     * was nothing to go into.
     *
     * <p>A plain COPY names no datatypes at all, which is how it stays
     * shallow without a separate branch saying so.
     */
    private static Value memberCopiedFrom(Value member, boolean deeply, Set<Datatype> kinds) {
        if (!kinds.contains(member.datatype())) {
            return member;
        }
        return copied(member, deeply, deeply ? kinds : NOTHING_INSIDE);
    }

    /**
     * What a shallow copy duplicates inside whatever it just duplicated, which
     * is nothing.
     *
     * <p>{@code if ((types & CP_DEEP) != 0)} guards the recursion, so without
     * {@code /deep} a copied member is copied and its own contents are shared.
     * That is the difference between {@code copy/types m object!}, where the
     * object comes back new and the object inside it is the original, and
     * {@code copy/deep/types m object!}, where both are new.
     */
    private static final Set<Datatype> NOTHING_INSIDE = EnumSet.noneOf(Datatype.class);

    /** The first few of a series, copied, and deeply when asked. */
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
            case BlockValue block -> new BlockValue(new BlockStorage(
                    block.remaining().subList(0, taking).stream()
                            .map(item -> deeply && kinds.contains(item.datatype())
                                    ? copied(item, true, kinds)
                                    : item)
                            .toList()),
                    1, block.datatype());
            case StringValue text -> StringValue.of(
                    text.text().substring(0, taking), text.datatype());
            case BinaryValue bytes -> copiedBytes(bytes, taking);
            case ImageValue image -> copiedPixels(image, taking);
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

    /**
     * A branch taken: run when it is a block, handed back when it is anything
     * else.
     *
     * <p>{@code if (IS_BLOCK(D_ARG(2)) && !D_REF(3)) { DO_BLK(...); } else
     * return R_ARG2;} -- so IF, UNLESS and EITHER take any value as a branch
     * and only a block means "do this". That is what lets
     * {@code if false "text"} stand where a value is wanted, and it is why
     * {@code reduce [{abc} if false {def} {ghi}]} is three items rather than
     * an error about a string where a block was expected.
     */
    private static Value branchTaken(
            Value branch, Evaluator evaluator, Context context, Set<String> refinements) {

        return branch instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                && !refinements.contains("only")
                ? evaluator.evaluateOrRaise(block, context)
                : branch;
    }

    /**
     * The first few of a series turned round, in place.
     *
     * <p>Built by copying out the front, reversing that, and putting it
     * back one at a time, because the series has to end up the same
     * series: a caller holding it must see the change.
     */
    private static Value reversedFront(SeriesValue series, Value limit) {
        int howMany = limit instanceof IntegerValue wanted
                ? (int) Math.max(0, Math.min(wanted.magnitude(), series.lengthFromHere()))
                : series.lengthFromHere();
        return switch (series) {
            case BlockValue block -> {
                List<Value> front = new ArrayList<>(block.remaining().subList(0, howMany));
                Collections.reverse(front);
                for (int at = 0; at < howMany; at++) {
                    block.storage().set(block.index() + at, front.get(at));
                }
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
            case StringValue text -> rewritten(text, whole ->
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

    /**
     * Takes a key and its value out of a block read as pairs.
     *
     * <p>Only an odd place holds a key, so `remove/key [a b b c] 'c` finds
     * nothing: the c there is a value. `'b` finds the pair at the third
     * place and leaves [a b].
     *
     * <p>The key is matched exactly. A word folds case everywhere else in
     * REBOL and not here, so `'B` does not find `b`.
     */
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

    /**
     * Refuses adding a field the object is keeping to itself.
     *
     * <p>A hidden field is invisible from outside, so nothing outside may
     * write over it either. Left unchecked, an APPEND naming one would
     * quietly replace what the object was hiding -- and the object's own
     * code, which can still see it, would find something else there.
     */
    private static void refuseHiddenField(ObjectValue object, Value named) {
        List<Value> names = named instanceof BlockValue pairs
                ? pairs.remaining()
                : List.of(named);
        for (Value name : names) {
            if (name instanceof WordValue word
                    && object.context().everySlot().stream().anyMatch(
                            slot -> slot.isHidden()
                                    && slot.canonical().equals(word.canonical()))) {
                throw Raised.of(EvaluationFailure.HIDDEN, word.spelling());
            }
        }
    }

    /**
     * A block with only its words and paths worked out.
     *
     * <p>{@code Reduce_Only} in the C. Every other value is copied as it
     * stands, thus a block of data keeps its shape and only the names in
     * it are looked up.
     *
     * <p>A word named in the list is left alone as well, which is how a
     * caller keeps one name out of the reduction. None as the list means
     * no exceptions.
     */
    private static List<Value> reducedOnlyWords(
            BlockValue block, Evaluator evaluator, Value exceptions) {

        Set<String> kept = exceptions instanceof BlockValue named
                ? named.remaining().stream()
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

    /**
     * A block reduced with its set-words left where they stand.
     *
     * <p>REDUCE/NO-SET keeps the set-word or set-path and reduces only
     * what follows it, assigning nothing, so `[x: 1 + 2]` becomes
     * `[x: 3]`. Plain REDUCE performs the assignment and drops the
     * set-word, leaving `[3]`.
     */
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

    /** Whether protection means anything for this kind of value. */
    private static boolean carriesProtection(Value value) {
        return value instanceof SeriesValue || value instanceof ObjectValue;
    }

    /** Whether a block is one of the path shapes rather than a plain block. */
    private static boolean isAPath(BlockValue block) {
        return block.datatype() == Datatype.PATH
                || block.datatype() == Datatype.LIT_PATH
                || block.datatype() == Datatype.GET_PATH
                || block.datatype() == Datatype.SET_PATH;
    }

    /**
     * Protects the field a path names, and answers whether it was a path.
     *
     * <p>A path is a block whose items are words, so leaving it to the
     * block handling protects whatever each segment is bound to where it
     * was written -- the enclosing word rather than the field inside the
     * object. `protect/words/deep 'o/o` then makes every later
     * `o: something` raise locked-word, and the tests after it run
     * against an object nobody meant to keep.
     *
     * <p>A path that names nothing protects nothing and raises nothing:
     * a missing field, or a segment that is a number and so cannot be
     * walked into, both leave the state alone. Confirmed against a real
     * R3, which answers the path either way.
     */
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

    /**
     * The slot a path's segments lead to, or null if they lead nowhere.
     *
     * <p>Null rather than an empty slot, because there is no such thing
     * as a slot that holds nothing safely -- a caller has to decide what
     * an unreachable path means, and here it means do nothing at all.
     */
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

    /**
     * PROTECT/VALUES and PROTECT/WORDS, which take a block of words.
     *
     * <p>The two are complements and neither does the other's job.
     * /VALUES protects what each word holds, so changing the series
     * raises `protected` and reassigning the word is fine. /WORDS
     * protects the slots, so reassigning raises `locked-word` and
     * changing the series is fine.
     *
     * <p>Answers whether it did anything, so the caller can fall through
     * to protecting the block itself when neither refinement was asked.
     */
    private static boolean protectNamed(
            Value target, boolean protectedNow, Set<String> refinements) {

        boolean values = refinements.contains("values");
        boolean words = refinements.contains("words");
        if (!(values || words)) {
            return false;
        }
        List<Value> items = switch (target) {
            case BlockValue named when !isAPath(named) -> named.remaining();
            case WordValue only -> List.of(only);
            default -> List.of();
        };
        if (items.isEmpty()) {
            return false;
        }
        for (Value item : items) {
            if (!(item instanceof WordValue word) || !word.isBound()
                    || !word.binding().knows(word.canonical())) {
                continue;
            }
            ContextSlot slot = word.binding().slotFor(word.canonical());
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

    /**
     * Protects or unprotects a value, and its contents when asked.
     *
     * <p>Three separate things carry protection: a word's slot, an
     * object's fields and a series' storage. Protecting the word that
     * holds a block does not protect the block, which is what makes
     * `protect b` and `protect 'b` different requests.
     */
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

    /** Refuses a change to a series that was protected from changing. */
    private static void requireChangeable(Value series) {
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

    /**
     * How much /part asked for, given a count or a position.
     *
     * <p>A position means "as far as there", and the two positions may
     * be in either order: the span is between them, taken from whichever
     * comes first. So `take/part s skip s 1` and `take/part skip s 1 s`
     * are the same request, and the same position twice is a span of
     * nothing.
     *
     * <p>The from-whichever-comes-first part is what makes this a span
     * rather than a direction, and it is why the count can never be
     * negative here even when the argument is behind the series.
     */
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

    /**
     * Whichever of two positions comes first.
     *
     * <p>A span runs from the earlier position, so the two may be given
     * either way round and mean the same thing.
     */
    private static SeriesValue earlierOf(SeriesValue series, SeriesValue other) {
        return other.index() < series.index() ? other : series;
    }

    /** A position clamped to the series, which is what ATZ and SKIP do. */
    private static int clampedPosition(SeriesValue series, long wanted) {
        return (int) Math.max(1, Math.min(wanted, series.storageLength() + 1));
    }

    /**
     * What PICK answers, which is not always about a position.
     *
     * <p>Four datatypes read PICK as a question about a field rather than an
     * index, and each says so in its own dispatcher. A bitset asks whether it
     * holds the value: `case A_PICK: case A_FIND:` share one arm. A map asks
     * what a key holds, and the C comments the case with "same as SELECT for
     * MAP! datatype". A date and a time send it to `Pick_Path`, which is the
     * same field selection a path does -- so `pick 1-Jan-2000 'year` and
     * `1-Jan-2000/year` are one question.
     */
    private static Value pickFrom(Value target, Value selector) {
        return switch (target) {
            case BitsetValue members -> LogicValue.of(bitsetHolds(members, selector, false));
            case MapValue map -> map.select(selector);
            case DateValue date -> DateParts.of(date, selector);
            case TimeValue time -> pickTimePart(time, selector);
            case GobValue gob -> GobPath.childOf(gob, positionPickedFrom(selector));
            default -> selector instanceof IntegerValue position
                    ? pick(target, (int) position.magnitude())
                    : raiseCannotUse(target, "pick");
        };
    }

    /** A time's part, by name or by position, as a path reads one. */
    private static Value pickTimePart(TimeValue time, Value selector) {
        long seconds = time.nanoseconds() / 1_000_000_000L;
        String part = selector instanceof WordValue named
                ? named.canonical()
                : positionAsTimePartName(selector);
        return switch (part) {
            case "hour" -> IntegerValue.of(seconds / 3600);
            case "minute" -> IntegerValue.of(seconds / 60 % 60);
            case "second" -> IntegerValue.of(seconds % 60);
            default -> NoneValue.none();
        };
    }

    /** A time answers to positions as well as names: 1 is the hour. */
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

    /**
     * A number a position was given as, refusing anything else.
     *
     * <p>{@code Get_Num_Arg} in the C: an integer, a decimal it truncates, or a
     * none it reads as zero. Anything else is {@code Trap_Arg}, which is
     * {@code invalid-arg} -- the error a caller gets for `poke gob 'offset 1x1`.
     */
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

    /** The same reading, for PICK, whose arm refuses the same way. */
    private static long positionPickedFrom(Value given) {
        return positionPokedAt(given);
    }

    /**
     * Refuses the three refinements a gob's arms will not carry out.
     *
     * <p>{@code if (DS_REF(AN_PART) || DS_REF(AN_ONLY) || DS_REF(AN_DUP))
     * Trap0(RE_NOT_DONE);} on APPEND, INSERT and CHANGE. Rebol's own
     * {@code not-done} is "reserved for future use (or not yet implemented)", so
     * this is the C saying it never got round to them rather than that they mean
     * nothing here.
     */
    private static void refuseUnfinishedRefinements(Set<String> refinements, String what) {
        for (String unfinished : List.of("part", "only", "dup")) {
            if (refinements.contains(unfinished)) {
                throw Raised.of(EvaluationFailure.FEATURE_NA,
                        what + "/" + unfinished + " on a gob is not implemented");
            }
        }
    }

    /**
     * Puts one child or a block of them into a pane.
     *
     * <p>{@code if (IS_GOB(arg)) len = 1; else if (IS_BLOCK(arg)) { len =
     * VAL_BLK_LEN(arg); ... } else goto is_arg_error;} -- and the error is
     * {@code Trap_Types(RE_EXPECT_VAL, REB_GOB, VAL_TYPE(arg))}, which names the
     * datatype the pane wanted rather than the argument position.
     */
    private static Value insertChildren(GobValue gob, int at, Value value) {
        List<Value> children = switch (value) {
            case GobValue only -> List.<Value>of(only);
            case BlockValue block when block.datatype() == Datatype.BLOCK ->
                    block.remaining();
            default -> throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    "a pane holds gobs, not " + value.datatype().literalSpelling());
        };
        int goesAt = at;
        for (Value child : children) {
            if (!(child instanceof GobValue one)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "a pane holds gobs, not "
                                + child.datatype().literalSpelling());
            }
            gob.storage().insertChild(goesAt, one);
            goesAt = Math.min(goesAt + 1, gob.storage().length() + 1);
        }
        return gob;
    }

    /**
     * MAKE GOB!, in its three forms.
     *
     * <p>A block is a spec of set-word and value pairs. A gob is cloned without
     * its pane or its parent. A pair is a size and nothing else. Everything else
     * is {@code Trap_Make(REB_GOB, arg)}.
     *
     * <p>The fields start where {@code Make_Gob} leaves them, which is a hundred
     * square and opaque rather than empty.
     */
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

    /**
     * The spec block a gob is made from: {@code Set_GOB_Vars}.
     *
     * <p>Walked in pairs, and each pair is checked twice before it is used. The
     * name must be a set-word -- {@code Trap2(RE_EXPECT_VAL, Get_Type
     * (REB_SET_WORD), Of_Type(var))} -- and the value must be there and must not
     * be another set-word, which is what catches `[data: size: 10x10]`: a spec
     * that reads as two fields is one field with no value.
     */
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

    /**
     * How deep a gob tree is walked before the walk gives up.
     *
     * <p>{@code REBINT max_depth = 1000; // avoid infinite loops} in both
     * directions. A gob tree can hold itself -- nothing stops a script appending
     * a gob to its own descendant -- so the count is the only thing between
     * MAP-GOB-OFFSET and a hang.
     */
    private static final int DEEPEST_GOB_WALK = 1000;

    /**
     * The deepest gob holding a point, and the point in that gob's coordinates.
     *
     * <p>{@code Map_Gob_Inner}. Two details decide what it answers, and both are
     * in the loop rather than in the name.
     *
     * <p>The pane is searched <em>backwards</em>: {@code gop = GOB_HEAD(gob) + len
     * - 1} and then {@code gop--}. So where two children overlap, the one added
     * last wins -- which is what "topmost" means on a screen.
     *
     * <p>And the rectangle is half-open: {@code xo >= x + GOB_X} together with
     * {@code xo < x + GOB_X + GOB_W}. A point on a gob's left edge is inside it
     * and a point on its right edge belongs to whatever is next along.
     */
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

    /**
     * The outermost gob, and the point seen from there.
     *
     * <p>The /REVERSE arm, which is a plain climb: {@code xo += GOB_X(gob); gob =
     * GOB_PARENT(gob);} until there is no parent left.
     *
     * <p>It also stops at a gob flagged as a window, and nothing here can set that
     * flag: {@code GOBF_WINDOW} is not one of the nine words {@code Gob_Flag_Words}
     * accepts, so only the host's own windowing code raises it. Until a host does,
     * the climb always reaches the root.
     */
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

    /**
     * An event rewritten to name the deepest gob under its point.
     *
     * <p>Two conditions have to hold before anything happens: {@code if (gob &&
     * GET_FLAG(VAL_EVENT_FLAGS(val), EVF_HAS_XY))}. So an event with no gob and a
     * key event with no offset both go straight through, which is what lets a
     * caller run every event through this without asking what kind it is.
     *
     * <p>{@code ROUND_TO_INT} is what puts the walk's floating result back into an
     * event's two shorts, and a gob's offset is a float pair -- so a child at
     * 1.6x1.6 moves the point by 2 and not by 1.
     *
     * <p>The HAS_DATA branch reaches for {@code OS_Get_Gob_Root()} under
     * {@code #ifdef REB_VIEW}, so in a build with no window system it leaves the
     * gob null and this does nothing. Which is what a console build does too.
     */
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

    /**
     * Whether a port is finished waiting, having been told about an event.
     *
     * <p>Two steps. The port's UPDATE action runs first, and only when its actor is
     * a native: `if (IS_NATIVE(val)) Do_Port_Action(D_ARG(1), A_UPDATE);`, whose
     * comment says why -- "makes the port object fully consistent with internal
     * native structures (e.g. the actual length of data read)". Every actor a
     * scheme here installs is REBOL rather than C, so nothing takes that step, and
     * replicating the condition rather than the body is the faithful thing.
     *
     * <p>Then the AWAKE function, if there is one, is called with the event. The
     * answer has to be a logic <em>and</em> be true: `if (!(IS_LOGIC(val) &&
     * VAL_LOGIC(val))) return R_FALSE;`. A truthy non-logic does not count, which a
     * reading of "if the awake function says so" would get wrong.
     */
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

    /**
     * WAIT on the event port: where the screen's queue becomes handler calls.
     *
     * <p>This is the one place a script's own thread takes what the screen's
     * thread put down, and it is why the queue exists at all. An interpreter
     * is owned by one thread, and that is what lets series share mutable
     * storage with nothing synchronising them, so a toolkit's listener must
     * never run a handler where it stands.
     *
     * <p>It returns when the port's AWAKE says so. REBOL's own AWAKE, written
     * in {@code init-view-system}, ends with {@code tail?
     * system/view/screen-gob} -- true exactly when the last window has closed.
     * That is what makes a script ending in VIEW a program rather than a
     * statement.
     *
     * <p>A screen with nothing open does not wait at all, and a run under a
     * deadline is ended by it: the pause between drains goes through the same
     * interruptible sleep every long-running native uses, so a granted screen
     * is not a way past the bounds a host set.
     */
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

    /**
     * Whether there is any point waiting: a screen with no window open has
     * nothing left to report and nobody left to close.
     */
    private static boolean theScreenStillHasSomethingToSay(Evaluator evaluator) {
        Value root = pathInto(
                evaluator.systemContext(), "system", "view", "screen-gob");
        return root instanceof GobValue gob && gob.storage().length() > 0;
    }

    /** How long to pause between drains of the screen's queue. */
    private static final long SCREEN_POLL_MILLISECONDS = 10;

    /** One reported event as the {@code event!} a handler reads. */
    private static EventValue guiEventFor(ScreenEvent reported) {
        return EventValue.fresh()
                .withType(EventCatalogue.typeIndexOf(reported.kind().spelling())
                        .orElse(0))
                .withAttached(EventValue.Model.GUI,
                        reported.window() == null
                                ? NoneValue.none()
                                : reported.window());
    }

    /**
     * Where the codec handles' identities start.
     *
     * <p>A function handle's identity is its pointer in the C, which is what
     * {@code same?} compares and what {@code Cmp_Handle} sorts on. Any distinct
     * numbers do the same job, and starting them away from zero keeps them from
     * colliding with a context handle's index when one exists.
     */
    private static final int CODEC_HANDLE_IDENTITY = 1000;

    /**
     * DO-CODEC: one codec, one action, one piece of data.
     *
     * <p>The action word is checked before the data is, and each of the three wants
     * something different. IDENTIFY and DECODE both want a binary --
     * `if (!IS_BINARY(val)) Trap1(RE_INVALID_ARG, val);`, which the two share by
     * falling through. ENCODE wants an image and nothing else.
     *
     * <p>So a string is on the declared list and is refused by every arm, which is
     * the declaration and the arm disagreeing on purpose: the spec was widened for
     * a codec that could take one and none of them does.
     */
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

    /** The two-item block both walks answer: `Return_Gob_Pair`. */
    private static Value gobAndPoint(GobValue reached, PairValue point) {
        return BlockValue.block(List.of(reached, point));
    }

    /** Writes a pixel given as red, green, blue, alpha. */
    private static void writePixel(ImageValue image, int pixel, int[] channels) {
        image.storage().setColourAt(pixel, channels[0], channels[1], channels[2]);
        image.storage().setAlphaAt(pixel, channels[3]);
    }

    /**
     * Puts pixels into an image at its position.
     *
     * <p>What `Modify_Image` does for INSERT and APPEND, in the one shape a
     * script can reach without the /part and /dup rectangle arithmetic: a tuple
     * is one pixel and an image contributes its own, and the height follows the
     * new count.
     */
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

    /** The first pixels of an image, as an image of the same width. */
    private static ImageValue copiedPixels(ImageValue image, int howMany) {
        ImageStorage into = ImageStorage.of(
                Math.max(1, Math.min(howMany, image.storage().wide())), 0);
        ImageValue made = new ImageValue(into, 1);
        for (int at = 1; at <= howMany; at++) {
            int[] channels = image.pixelAt(at);
            into.insertAt(at, channels[0], channels[1], channels[2], channels[3]);
        }
        return made;
    }

    private enum Combination { INTERSECT, UNION, EXCLUDE, DIFFERENCE }

    /**
     * A set operation over the characters of a string.
     *
     * <p>The same rules the block path uses, over codepoints instead of values,
     * and the answer keeps the datatype of the first argument: a set operation
     * on two files answers a file. The characters are compared without regard to
     * case unless /CASE was asked for, which is what {@code Find_Str_Char} does
     * with the flag.
     */
    private static Value combinedText(
            Value left, Value right, Combination how, boolean mindingCase, int stride) {

        List<Value> ours = charactersOf(left);
        List<Value> theirs = charactersOf(right);
        List<List<Value>> first = inRecords(ours, stride);
        List<List<Value>> second = inRecords(theirs, stride);
        StringBuilder kept = new StringBuilder();
        List<List<Value>> keptRecords = new ArrayList<>();
        for (List<Value> candidate : first) {
            boolean inSecond = second.stream()
                    .anyMatch(other -> sameRecord(other, candidate, mindingCase));
            boolean wanted = how == Combination.UNION || how == Combination.DIFFERENCE
                    ? !inSecond || how == Combination.UNION
                    : how == Combination.INTERSECT == inSecond;
            if (wanted && keptRecords.stream()
                    .noneMatch(already -> sameRecord(already, candidate, mindingCase))) {
                keptRecords.add(candidate);
            }
        }
        if (how == Combination.UNION || how == Combination.DIFFERENCE) {
            for (List<Value> candidate : second) {
                boolean inFirst = first.stream()
                        .anyMatch(other -> sameRecord(other, candidate, mindingCase));
                if ((how == Combination.UNION || !inFirst) && keptRecords.stream()
                        .noneMatch(already -> sameRecord(already, candidate, mindingCase))) {
                    keptRecords.add(candidate);
                }
            }
        }
        keptRecords.stream().flatMap(List::stream).forEach(item ->
                kept.appendCodePoint(((CharacterValue) item).codepoint()));
        Datatype datatype = left instanceof StringValue text
                ? text.datatype()
                : Datatype.STRING;
        return StringValue.of(kept.toString(), datatype);
    }

    /** A string as a list of its characters, and anything else as itself. */
    private static List<Value> charactersOf(Value value) {
        if (!(value instanceof StringValue text)) {
            return value instanceof BlockValue block ? block.remaining() : List.of(value);
        }
        return text.text().codePoints()
                .<Value>mapToObj(CharacterValue::of)
                .toList();
    }

    /**
     * A set operation over two maps, which the C reads as their keys.
     *
     * <p>`set1 [block! string! bitset! typeset! map!]`, and a map's members are
     * its keys: the pairs come back with the keys the operation kept.
     */
    private static Value combinedMaps(
            Value left, Value right, Combination how, boolean mindingCase) {

        MapValue ours = left instanceof MapValue map ? map : MapValue.empty();
        MapValue theirs = right instanceof MapValue map ? map : MapValue.empty();
        MapValue kept = MapValue.empty();
        for (Value key : ours.keys()) {
            boolean inTheirs = theirs.holds(key);
            boolean wanted = switch (how) {
                case INTERSECT -> inTheirs;
                case UNION -> true;
                case EXCLUDE, DIFFERENCE -> !inTheirs;
            };
            if (wanted) {
                kept.put(key, ours.select(key));
            }
        }
        if (how == Combination.UNION || how == Combination.DIFFERENCE) {
            for (Value key : theirs.keys()) {
                if (how == Combination.UNION || !ours.holds(key)) {
                    kept.put(key, theirs.select(key));
                }
            }
        }
        return kept;
    }

    /**
     * Two typesets combined, as the four operators in n-sets.c combine them.
     *
     * <p>DIFFERENCE is the one worth naming: it is a symmetric difference,
     * `^=`, so it keeps what is in one set or the other but not both. EXCLUDE
     * is the asymmetric one, `&= ~`. The two agree whenever the second set is
     * contained in the first, which is why a test written with either passes
     * and the distinction stays hidden.
     */
    private static Value combinedTypesets(
            TypesetValue ours, TypesetValue theirs, Combination how) {

        Set<Datatype> mine = ours.members();
        Set<Datatype> yours = theirs.members();
        Set<Datatype> result = EnumSet.noneOf(Datatype.class);
        for (Datatype each : Datatype.values()) {
            boolean inMine = mine.contains(each);
            boolean inYours = yours.contains(each);
            boolean kept = switch (how) {
                case UNION -> inMine || inYours;
                case INTERSECT -> inMine && inYours;
                case DIFFERENCE -> inMine ^ inYours;
                case EXCLUDE -> inMine && !inYours;
            };
            if (kept) {
                result.add(each);
            }
        }
        return TypesetValue.of(Set.copyOf(result));
    }

    /**
     * What COMPLEMENT accepts besides a logic and a number.
     *
     * <p>`complement | value<logic! integer! tuple! binary! bitset! typeset!>`.
     */
    private static Set<Datatype> complementableDatatypes() {
        return Set.of(Datatype.LOGIC, Datatype.INTEGER, Datatype.TUPLE,
                Datatype.BINARY, Datatype.BITSET, Datatype.TYPESET);
    }

    /**
     * What the set operations take: {@code [block! string! bitset! typeset!
     * map!]}, and a date for DIFFERENCE alone.
     *
     * <p>A binary is not among them. Leaving it in let one through to a body
     * that casts to a block, so {@code difference #{01} #{02}} came out of the
     * interpreter as a Java class-cast rather than as an error a script can
     * catch. The declaration is what decides that, not the body.
     */
    private static Set<Datatype> setOperandOr(Datatype... alsoAccepted) {
        Set<Datatype> accepted = EnumSet.of(
                Datatype.BITSET, Datatype.TYPESET, Datatype.STRING, Datatype.MAP);
        accepted.addAll(List.of(alsoAccepted));
        return Set.copyOf(accepted);
    }

    /**
     * Every datatype a typeset does not hold.
     *
     * <p>`VAL_TYPESET(val) = ~VAL_TYPESET(val)` -- one line, and it covers
     * every datatype the build knows rather than only the ones mentioned so
     * far. So the complement of a typeset holding one datatype holds all the
     * others, and `find complement make typeset! [block!] integer!` is true.
     */
    private static TypesetValue complementOfTypeset(TypesetValue members) {
        Set<Datatype> rest = EnumSet.allOf(Datatype.class);
        rest.removeAll(members.members());
        return TypesetValue.of(Set.copyOf(rest));
    }

    private static BitsetValue complementOf(BitsetValue members) {
        byte[] held = members.octets();
        byte[] rest = new byte[Math.max(held.length, 32)];
        for (int at = 0; at < rest.length; at++) {
            rest[at] = (byte) ~(at < held.length ? held[at] : 0);
        }
        return BitsetValue.of(rest);
    }

    private static BitsetValue combinedBitsets(
            BitsetValue ours, BitsetValue theirs, Combination how) {
        byte[] left = ours.octets();
        byte[] right = theirs.octets();
        byte[] both = new byte[Math.max(left.length, right.length)];
        for (int at = 0; at < both.length; at++) {
            int mine = at < left.length ? left[at] & 0xFF : 0;
            int yours = at < right.length ? right[at] & 0xFF : 0;
            both[at] = (byte) switch (how) {
                case UNION -> mine | yours;
                case INTERSECT -> mine & yours;
                case EXCLUDE -> mine & ~yours;
                case DIFFERENCE -> mine ^ yours;
            };
        }
        return BitsetValue.of(both);
    }

    /**
     * The set operations, which keep the order they found things in rather
     * than sorting, because a block is ordered and the answer should be too.
     */
    private static Value combined(List<Value> arguments, Combination how) {
        return combined(arguments, how, false, 1);
    }

    private static Value combined(
            List<Value> arguments, Combination how, boolean mindingCase, int stride) {
        if (arguments.get(0) instanceof BitsetValue ours
                && arguments.get(1) instanceof BitsetValue theirs) {
            return combinedBitsets(ours, theirs, how);
        }
        if (arguments.get(0) instanceof TypesetValue oursByType
                && arguments.get(1) instanceof TypesetValue theirsByType) {
            return combinedTypesets(oursByType, theirsByType, how);
        }
        if (arguments.get(0) instanceof StringValue || arguments.get(1) instanceof StringValue) {
            return combinedText(arguments.get(0), arguments.get(1), how,
                    mindingCase, stride);
        }
        if (arguments.get(0) instanceof MapValue || arguments.get(1) instanceof MapValue) {
            return combinedMaps(arguments.get(0), arguments.get(1), how, mindingCase);
        }
        if (!(arguments.get(0) instanceof BlockValue)
                || !(arguments.get(1) instanceof BlockValue)) {
            return raiseCannotUse(arguments.get(0) instanceof BlockValue
                    ? arguments.get(1) : arguments.get(0), "a set operation");
        }
        List<List<Value>> first = inRecords(((BlockValue) arguments.get(0)).remaining(), stride);
        List<List<Value>> second = inRecords(((BlockValue) arguments.get(1)).remaining(), stride);
        List<List<Value>> result = new ArrayList<>();

        for (List<Value> candidate : first) {
            boolean inSecond = second.stream()
                    .anyMatch(other -> sameRecord(other, candidate, mindingCase));
            boolean wanted = switch (how) {
                case INTERSECT -> inSecond;
                case UNION, EXCLUDE, DIFFERENCE ->
                        how == Combination.UNION || !inSecond;
            };
            if (wanted && result.stream()
                    .noneMatch(kept -> sameRecord(kept, candidate, mindingCase))) {
                result.add(candidate);
            }
        }
        if (how == Combination.UNION) {
            for (List<Value> candidate : second) {
                if (result.stream()
                        .noneMatch(kept -> sameRecord(kept, candidate, mindingCase))) {
                    result.add(candidate);
                }
            }
        }
        return BlockValue.block(result.stream().flatMap(List::stream).toList());
    }

    /**
     * A flat list read as records of a fixed width.
     *
     * <p>A width of one gives one record per item, which is what makes the
     * plain call and the /SKIP call one piece of code. A short record at
     * the end is kept rather than dropped, because dropping it would lose
     * data the caller can see is there.
     */
    private static List<List<Value>> inRecords(List<Value> items, int stride) {
        List<List<Value>> records = new ArrayList<>();
        for (int at = 0; at < items.size(); at += stride) {
            records.add(items.subList(at, Math.min(at + stride, items.size())));
        }
        return records;
    }

    /**
     * Whether two records count as the same one.
     *
     * <p>The first field decides and the rest are carried along, so
     * `union/skip [1 2 1 3] [1 2] 2` is [1 2]: the record [1 3] has the
     * same key as one already kept and goes. Comparing whole records
     * instead keeps both, which is the answer for a plain UNION and not
     * for this one.
     */
    private static boolean sameRecord(
            List<Value> ours, List<Value> theirs, boolean mindingCase) {

        if (ours.isEmpty() || theirs.isEmpty()) {
            return ours.isEmpty() && theirs.isEmpty();
        }
        return mindingCase
                ? Comparison.identicallyEqual(ours.getFirst(), theirs.getFirst())
                : Comparison.looselyEqual(ours.getFirst(), theirs.getFirst());
    }

    /**
     * A number rounded the way the refinements asked.
     *
     * <p>Six modes, each measured against a real R3 rather than reasoned
     * about, because they disagree in more places than the names suggest.
     * /DOWN and /FLOOR agree on positives and part company on negatives;
     * /HALF-DOWN and /HALF-CEILING agree everywhere except on a half.
     *
     * <p>The default is half away from zero, which is what REBOL does and
     * what a JVM does not.
     */
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

    /** Keeps a computed position inside the series it belongs to. */
    private static int clampToSeries(SeriesValue series, long wanted) {
        return (int) Math.max(1, Math.min(wanted, series.storageLength() + 1L));
    }

    private static Value raiseCannotUse(Value value, String nativeName) {
        throw new Raised(ErrorValue.about(
                ErrorCategory.SCRIPT, "cannot-use",
                "cannot use " + nativeName + " on "
                        + value.datatype().literalSpelling() + " value",
                WordValue.of(nativeName),
                DatatypeValue.of(value.datatype())));
    }

    /**
     * Turning bytes into text and back: ENHEX, DEHEX, ENBASE, DEBASE,
     * CHECKSUM, COMPRESS, DECOMPRESS and SWAP-ENDIAN.
     *
     * <p>Every spec here is the one declared in the C, verbatim. The work is
     * in {@link Encodings}, which knows nothing about REBOL values; these are
     * the thinnest wrapper that reaches it.
     */
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
                    String encoded = Encodings.percentEncoded(
                            octetsOf(value), keep, escape,
                            refinements.contains("uri"));
                    return StringValue.of(encoded, textDatatypeOf(value));
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
                    byte[] octets = boundedByAnyPart(
                            octetsOf(arguments.getFirst()), arguments, refinements);
                    String encoded = Encodings.enbase(
                            octets, base, refinements.contains("url"));
                    return StringValue.of(refinements.contains("flat")
                            ? encoded
                            : Encodings.brokenIntoLines(encoded));
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
                    byte[] octets = partOfOctets(arguments.getFirst(),
                            octetsOf(arguments.getFirst()), arguments, refinements, 2);
                    if (Encodings.CYCLIC.contains(method)) {
                        return IntegerValue.of(Encodings.cyclicOf(octets, method));
                    }
                    if (!Encodings.DIGESTS.containsKey(method)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG, method);
                    }
                    Value key = refinements.contains("with")
                            ? argumentFor("with", List.of("with", "part"),
                                    arguments, refinements, 2)
                            : null;
                    return binaryOfBytes(key == null || key instanceof IntegerValue
                            ? Encodings.digestOf(octets, method)
                            : Encodings.keyedDigestOf(octets, method, octetsOf(key)));
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
                        return binaryOfBytes(Encodings.decompressed(
                                partOfOctets(arguments.getFirst(),
                                        octetsOf(arguments.getFirst()),
                                        arguments, refinements, 2),
                                method));
                    } catch (IllegalArgumentException notCompressed) {
                        throw Raised.of(EvaluationFailure.INVALID_DATA,
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
                    boolean named = refinements.contains("as");
                    int bpp = bytesPerPixelIn(arguments, refinements, 2);
                    requirePngGeometry(named ? width : width + 1, bpp, data.length);
                    int filter = -1;
                    if (named) {
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

    /**
     * ENCLOAK and DECLOAK, which are one function and a direction.
     *
     * <p>Defined together because the C defines them together: both call
     * `Cloak` and differ in its first argument. Writing them apart would
     * invite the two to drift.
     */
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

    /**
     * The key bytes CLOAK actually scrambles against.
     *
     * <p>An integer is spelled out in decimal and then always hashed, whatever
     * /WITH said. The C overrides the refinement rather than honouring it --
     * `INT_TO_STR(VAL_INT64(val), dst); ... as_is = FALSE;` -- because the
     * digits are not bytes a caller chose.
     */
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

    /** What names a character set: a word, a string, a tag or a number. */
    private static Set<Datatype> characterSetNames() {
        return Set.of(Datatype.WORD, Datatype.INTEGER, Datatype.TAG, Datatype.STRING);
    }

    /**
     * The character set a value names, refusing one the host has not got.
     *
     * <p>A tag's own text, not its molded form: `<utf8>` names utf8, and the
     * angle brackets are how it was written rather than part of the name.
     * Going through FORM kept them and refused every tag.
     */
    private static java.nio.charset.Charset characterSetFor(Value named) {
        String spelling = switch (named) {
            case WordValue word -> word.canonical();
            case StringValue text -> text.text();
            default -> Molder.form(named);
        };
        java.nio.charset.Charset found = Encodings.charsetNamed(spelling);
        if (found == null) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, spelling);
        }
        return found;
    }

    /**
     * SET with an object on both sides: matched by name, not by position.
     *
     * <p>The only shape of SET where position plays no part. Each word of the
     * target takes the value the source holds for that same word; a word the
     * source has not got is left as it was, and a word only the source has is
     * ignored. The C walks the target's words and looks each one up:
     *
     * <pre>
     * tmp = Find_Word_Value(VAL_OBJ_FRAME(val), VAL_WORD_SYM(word));
     * if (tmp) {
     *     if (IS_UNSET(tmp) &amp;&amp; not_any) goto next_obj_val;
     *     if (ref_some &amp;&amp; VAL_TYPE(obj_val) &gt; REB_NONE
     *             &amp;&amp; VAL_TYPE(tmp) &lt;= REB_NONE) goto next_obj_val;
     *     *obj_val = *tmp;
     * }
     * </pre>
     *
     * <p>Two skips, and each is a refinement's whole meaning. Without /ANY an
     * unset in the source is passed over rather than copied, so the target
     * keeps a real value instead of losing it to nothing. With /SOME a source
     * value of none is passed over when the target already holds something
     * more than none, which is what makes /SOME "fill in the gaps".
     */
    private static void setFieldsFromObject(
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

    /**
     * Which of the five PNG filters a value names.
     *
     * <p>A word or its number, and nothing else. An unknown one is refused
     * rather than treated as "none", because a filter read wrongly corrupts
     * every line after it and answers no error.
     */
    private static int pngFilterNamedBy(Value named) {
        if (named instanceof IntegerValue whole) {
            int which = (int) whole.magnitude();
            if (which < 0 || which >= Encodings.PNG_FILTERS.size()) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, "filter type");
            }
            return which;
        }
        int found = Encodings.PNG_FILTERS.indexOf(((WordValue) named).canonical());
        if (found < 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    ((WordValue) named).spelling());
        }
        return found;
    }

    /** /SKIP names the bytes per pixel, and one is the default. */
    private static int bytesPerPixelIn(
            List<Value> arguments, Set<String> refinements, int where) {

        Value asked = refinements.contains("skip")
                ? argumentFor("skip", List.of("as", "skip"),
                        arguments, refinements, where)
                : null;
        return asked instanceof IntegerValue given ? (int) given.magnitude() : 1;
    }

    /**
     * The geometry check both filters do before touching a byte.
     *
     * <pre>
     * if ((REBINT)width &lt;= 1 || width &gt; bytes) Trap1(RE_INVALID_ARG, val_width);
     * if (bpp &lt; 1 || bpp &gt; width) Trap1(RE_INVALID_ARG, val_bpp);
     * </pre>
     */
    private static void requirePngGeometry(int width, int bytesPerPixel, int length) {
        if (width <= 1 || width > length) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, "width " + width);
        }
        if (bytesPerPixel < 1 || bytesPerPixel > width) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "bytes per pixel " + bytesPerPixel);
        }
    }

    /** ENBASE and DEBASE know five bases and refuse the rest. */
    private static void requireAKnownBase(int base) {
        if (!Encodings.BASES.contains(base)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        "base " + base + " is not 2, 16, 36, 64 or 85");
        }
    }

    private static String requireAKnownCompression(Value method) {
        String named = ((WordValue) method).canonical();
        if (!Encodings.COMPRESSIONS.contains(named)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, named);
        }
        return named;
    }

    /** The escape character, which is a percent sign unless /ESCAPE says. */
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

    /**
     * Which bytes ENHEX leaves alone.
     *
     * <p>/EXCEPT names the set outright. Without it the set follows the
     * datatype, which the C states in the spec itself: "By default it is URI
     * bitset when value is file or url, else URI-Component".
     */
    private static java.util.function.IntPredicate unescapedSetFor(
            Value value, List<Value> arguments, Set<String> refinements) {

        if (refinements.contains("except")) {
            Value asked = argumentFor("except", List.of("escape", "except"),
                    arguments, refinements, 1);
            if (asked instanceof BitsetValue named) {
                return octet -> Encodings.setHolds(named, octet);
            }
        }
        return value.datatype() == Datatype.FILE || value.datatype() == Datatype.URL
                ? Encodings::uriKeeps
                : Encodings::uriComponentKeeps;
    }

    /** The bytes of a value: a binary as they stand, text as UTF-8. */
    private static byte[] octetsOf(Value value) {
        return switch (value) {
            case BinaryValue bytes -> bytes.octetsFromHere();
            case IntegerValue whole -> java.nio.ByteBuffer.allocate(8)
                    .putLong(whole.magnitude()).array();
            default -> Molder.form(value).getBytes(StandardCharsets.UTF_8);
        };
    }

    /** The text of a value: a string as it stands, a binary read as UTF-8. */
    private static String textOf(Value value) {
        return value instanceof BinaryValue bytes
                ? new String(bytes.octetsFromHere(), StandardCharsets.UTF_8)
                : Molder.form(value);
    }

    /** The datatype the answer keeps, so a url stays a url. */
    private static Datatype textDatatypeOf(Value value) {
        return value.datatype().isAnyString() ? value.datatype() : Datatype.STRING;
    }

    /**
     * /PART, applied to the bytes rather than to the value. The source is
     * what a series-valued limit is measured against, so
     * {@code checksum/part mark 'sha1 remaining} reads the span between the
     * two positions -- which is how sys-load verifies a script checksum.
     */
    private static byte[] partOfOctets(
            Value source, byte[] octets, List<Value> arguments,
            Set<String> refinements, int where) {

        return howManyWanted(source, arguments, refinements, where)
                .map(count -> java.util.Arrays.copyOf(octets,
                        (int) Math.max(0, Math.min(count, octets.length))))
                .orElse(octets);
    }

    /** The any-string datatypes, plus whatever else a spec names beside them. */
    private static Set<Datatype> anyStringOr(Datatype... alsoAccepted) {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.ANY_STRING.members());
        accepted.addAll(List.of(alsoAccepted));
        return Set.copyOf(accepted);
    }

    /**
     * The functions that ask the interpreter about itself.
     *
     * <p>VERSION, POKEZ, TO-REAL-FILE, RECYCLE, STATS, HALT and STACK. Each
     * spec is the one declared in the C, verbatim.
     *
     * <p>STACK is the interesting one. It is answerable at all only because
     * evaluation state lives in frames on the heap rather than in JVM stack
     * frames -- see decision 1 -- so an implementation built on the host's own
     * stack could not offer it.
     */
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
                    String path = target instanceof StringValue named
                            ? named.text()
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
                (arguments, evaluator, context) ->
                        LogicValue.of(((HandleValue) arguments.get(0)).isContext()));

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
                        colourByteOf(arguments.get(0)),
                        colourByteOf(arguments.get(1)),
                        colourByteOf(arguments.get(2))));

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
                                    Colours.luminosity(parts[0], parts[1], parts[2], luma)),
                            parts -> {
                                int grey = Colours.luminosity(
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
                (arguments, evaluator, context) -> DecimalValue.of(Colours.distance(
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
                (arguments, evaluator, context) -> arguments.getFirst());

        define("evoke", List.of(Parameter.required("chant",
                        Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    List<Value> chants = arguments.getFirst() instanceof BlockValue several
                            ? several.remaining()
                            : List.of(arguments.getFirst());
                    for (int at = 0; at < chants.size(); at++) {
                        at += obey(chants.get(at), evaluator);
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

    /** `"^/STACK[%d] %s[%d] %s"` in the `stack` block of boot/strings.reb. */
    private static final String FRAME_LINE = "%nSTACK[%d] %s[%d] %s";

    /** `"\t%s: %72r"` -- a tab, the slot's name, and its value molded. */
    private static final String SLOT_LINE = "\t%s: %s";

    /** The width `%72r` allows a molded value in a stack dump. */
    private static final int SLOT_MOLD_LIMIT = 72;

    /** What the C prints where a call was made through no word at all. */
    private static final String NO_NAME = "?";

    /**
     * Prints the frame stack, innermost frame first, as {@code Dump_Stack} does.
     *
     * <p>One line per open frame and one per slot under it. The C recurses into
     * {@code PRIOR_DSF(dsf)} after printing the frame it is on, so the order is
     * the order a caller reads a backtrace in.
     *
     * <p>The number in brackets is the C's data stack pointer at that frame: how
     * many value slots are in use up to and including it, so it falls as the
     * walk goes outwards. Measured here the way STACK/SIZE measures the same
     * stack, because both read DSP in the C and two natives disagreeing about
     * one stack would be worse than either being rough.
     *
     * <p>The first line is DS's own call. {@code Dump_Stack(0, 0)} starts at
     * DSF, the frame of the call being made, and prints before it tests
     * {@code if (dsf > 0)} -- so a real 3.22.1 opens with {@code ds[0]
     * native!} whether or not anything else is open. A native opens no frame
     * here, so the line is composed rather than walked, which is the same
     * compensation STACK makes when it answers 'stack at offset zero. The two
     * disagreed until now: STACK named itself and this printed a nameless
     * placeholder, so the frames answered and the frames printed described
     * different stacks.
     */
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

    /**
     * The schemes this build has an actor for.
     *
     * <p>A scheme is a doorway to something outside the interpreter, and
     * registering one that leads nowhere is worse than leaving it out: a
     * script reads {@code system/schemes} to find what it can open, and a
     * name there is a promise. So this lists what JEBOL really serves and
     * grows only when an actor does.
     */
    private static final Set<String> SCHEMES_THIS_BUILD_SERVES =
            Set.of("console", "tcp", "dns", "event");

    /** The types a cipher context publishes. */
    private static final String RC4_HANDLE_TYPE = "rc4";

    private static final String RSA_HANDLE_TYPE = "rsa";

    private static final String DHM_HANDLE_TYPE = "dhm";

    private static final String ECDH_HANDLE_TYPE = "ecdh";

    /** The four things ECDH does, exactly one of which a caller may name. */
    private static final List<String> ECDH_ACTIONS =
            List.of("init", "curve", "public", "secret");

    /**
     * The binary dialect: lay numbers into bytes, or read them back.
     *
     * <p>A protocol is a sequence of fields of stated widths, and writing one
     * by hand means shifting and masking at every field. The dialect says the
     * widths instead, which is why {@code prot-tls.reb} is built on it.
     *
     * <p>The context can be an object made earlier, a binary to work on
     * directly, or a number of bytes to make room for. All three end up as
     * bytes and a position.
     */
    private static Value theBinaryDialect(List<Value> arguments,
            Set<String> refinements, Evaluator evaluator, Context context) {
        BinaryValue buffer = bufferOfTheDialectContext(arguments.getFirst());
        if (refinements.contains("write")) {
            return writtenThroughTheDialect(buffer,
                    dialectBlockIn(arguments, refinements, "write").stream()
                            .map(item -> valueLookedUp(item, evaluator, context))
                            .toList());
        }
        if (refinements.contains("read")) {
            Value asked = dialectCodeIn(arguments, refinements);
            return readThroughTheDialect(buffer, eachValueLookedUp(asked,
                    evaluator, context));
        }
        return theDialectContextFor(buffer);
    }

    /**
     * The dialect with its get-words and get-paths resolved, everything else
     * as written.
     *
     * <p>The block arrives unevaluated, which is what lets a code be named
     * rather than computed. But a caller writing a protocol has values in
     * hand -- a length worked out a line earlier, a constant from a table --
     * and the get sigil is how they reach the dialect: {@code [UI16 :length]}
     * writes the number LENGTH holds, where {@code [UI16 length]} would be an
     * error because LENGTH is not a code.
     *
     * <p>Resolved here rather than inside the dialect, because looking a word
     * up is the language's work and the dialect's job is bytes.
     */
    private static Value eachValueLookedUp(
            Value asked, Evaluator evaluator, Context context) {
        if (!(asked instanceof BlockValue block)) {
            return valueLookedUp(asked, evaluator, context);
        }
        return BlockValue.block(block.remaining().stream()
                .map(item -> valueLookedUp(item, evaluator, context))
                .toList());
    }

    private static Value valueLookedUp(
            Value item, Evaluator evaluator, Context context) {
        boolean fetches = item instanceof WordValue word
                        && word.datatype() == Datatype.GET_WORD
                || item instanceof BlockValue path
                        && path.datatype() == Datatype.GET_PATH;
        if (!fetches) {
            return item;
        }
        return evaluator.evaluateOrRaise(
                Binder.bind(BlockValue.block(List.of(item)), context), context);
    }

    /** Where a dialect context keeps its bytes. */
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

    /**
     * A context object shaped like {@code system/standard/bincode}, so what
     * BINARY answers can be handed back to it.
     */
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

    /**
     * Writes through the dialect and answers the context, not the bytes.
     *
     * <p>The context, because a caller writing a protocol writes field after
     * field and each call has to be able to take the last one's answer. The
     * bytes are in its BUFFER, which is where the next call reads them from.
     */
    private static Value writtenThroughTheDialect(
            BinaryValue buffer, List<Value> dialect) {
        List<Integer> octets = octetsOfTheBuffer(buffer);
        Bincode.Cursor cursor = new Bincode.Cursor(octets, octets.size());
        Bincode.write(cursor, dialect);
        return theDialectContextFor(BinaryValue.of(
                cursor.octets().stream().mapToInt(Integer::intValue).toArray()));
    }

    /**
     * Reads through the dialect, answering the shape the asking had.
     *
     * <p>A block of codes answers a block; a single word answers that one
     * value. The shape follows the asking because that is what makes the
     * dialect bearable to write against -- {@code binary/read ctx 'UI16} is a
     * caller saying "one number, please", and prot-tls.reb reads a field that
     * way inside a loop and appends the answer straight into a list, where a
     * block of one would quietly nest.
     */
    private static Value readThroughTheDialect(BinaryValue buffer, Value asked) {
        List<Long> read = Bincode.read(
                new Bincode.Cursor(octetsOfTheBuffer(buffer), 0),
                codesWrittenIn(asked));
        return shapedLikeTheAsking(asked,
                read.stream().<Value>map(IntegerValue::of).toList());
    }

    /** The buffer's bytes as the dialect works on them: unsigned, and growable. */
    private static List<Integer> octetsOfTheBuffer(BinaryValue buffer) {
        List<Integer> octets = new ArrayList<>();
        for (byte octet : buffer.octetsFromHere()) {
            octets.add(octet & 0xFF);
        }
        return octets;
    }

    /** A block of codes as written, or the one code that was. */
    private static List<Value> codesWrittenIn(Value asked) {
        return asked instanceof BlockValue block ? block.remaining() : List.of(asked);
    }

    /**
     * The answer shaped the way the asking was.
     *
     * <p>A block of codes answers a block; a single word answers that one
     * value. Which is what makes the dialect bearable to write against --
     * prot-tls.reb reads one field inside a loop and appends the answer
     * straight into a list, where a block of one would quietly nest.
     */
    private static Value shapedLikeTheAsking(Value asked, List<Value> values) {
        if (asked instanceof BlockValue) {
            return BlockValue.block(values);
        }
        return values.isEmpty() ? NoneValue.none() : values.getFirst();
    }

    /** The code a read was given, as written rather than wrapped. */
    private static Value dialectCodeIn(
            List<Value> arguments, Set<String> refinements) {
        int at = refinements.contains("init") ? 2 : 1;
        if (refinements.contains("write")) {
            at++;
        }
        return arguments.get(at);
    }

    /**
     * REGISTER: a struct's layout filed in the catalogue under a name.
     *
     * <p>A layout describes how bytes are arranged, and code laying that
     * description over a binary wants the description rather than an instance
     * of it. So the catalogue is a map from names to layouts, and this is how
     * one gets in.
     *
     * <p>The same layout under the same name again does nothing, so a file
     * loaded twice does not fail on its second pass. A different layout under
     * a name already taken is refused, because a name here is how other code
     * finds a layout and replacing one quietly would change what that code
     * reads without it knowing.
     *
     * <p>The name is quoted, and a set-word is set here rather than by the
     * evaluator: {@code register pair8!: make struct! [...]} both files the
     * layout and leaves {@code pair8!} holding the struct, which is what makes
     * the name usable as a prototype afterwards.
     */
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

    /**
     * RESIZE's new size, from a pair, a percentage or a width.
     *
     * <p>An integer is a width and the height follows from it, keeping the
     * shape: the declaration says so -- "integer value is used as width" --
     * and a resize that squashed a photograph because only one number was
     * given would be a surprise nobody wants.
     */
    private static Value resizedImage(List<Value> arguments) {
        ImageValue image = (ImageValue) arguments.getFirst();
        int wasWide = image.storage().wide();
        int wasHigh = image.storage().high();
        Value asked = arguments.get(1);
        int wide;
        int high;
        if (asked instanceof PairValue size) {
            wide = (int) size.x();
            high = (int) size.y();
        } else if (asked instanceof DecimalValue portion
                && portion.datatype() == Datatype.PERCENT) {
            wide = (int) Math.round(wasWide * portion.quantity());
            high = (int) Math.round(wasHigh * portion.quantity());
        } else {
            wide = (int) Math.round(asMagnitude(asked));
            high = wasWide == 0 ? 0 : Math.max(1, (wide * wasHigh) / wasWide);
        }
        if (wide <= 0 || high <= 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(asked));
        }
        return ImageOperations.resized(image, wide, high);
    }

    /**
     * IMAGE reaches the operating system's own encoder, which this build has
     * not got.
     *
     * <p>The same answer the C gives where {@code INCLUDE_IMAGE_OS_CODEC} is
     * undefined -- {@code Trap0(RE_FEATURE_NA)} -- and it is only defined for
     * Windows and macOS there, as the declaration's own summary says. Asked
     * for nothing it answers unset, because the C's branches are all on
     * refinements and it falls out of the bottom.
     *
     * <p>Not a gap to fill with an encoder of JEBOL's own: the codec family
     * in {@code system/codecs} is where a portable one belongs, and this
     * native is the shim onto a platform's.
     */
    private static Value theOperatingSystemsImageCodec(Set<String> refinements) {
        if (refinements.contains("load") || refinements.contains("save")) {
            throw Raised.of(EvaluationFailure.FEATURE_NA,
                    "image encoding through the operating system");
        }
        return UnsetValue.unset();
    }

    /**
     * What GENERATE answers: a single zero byte, whatever curve was named.
     *
     * <p>Copied rather than finished, and the C shows why on its own lines.
     * {@code mbedtls_ecdsa_genkey} is commented out, the group is loaded as
     * SECP192R1 whichever curve was asked for, and the point written out is
     * one nobody set -- so a real 3.22.1 answers the point at infinity for
     * every curve in the catalogue.
     *
     * <p>The declaration is why finishing it here would be wrong rather than
     * generous. The answer is one binary with nowhere in it for a private
     * key, so a GENERATE that really made a pair would hand back the public
     * half and discard the private half, which is an answer nothing can use.
     * ECDH/INIT already makes a usable elliptic-curve key.
     *
     * <p>The curve name is still checked, which is the part of it that works.
     */
    private static Value theKeyGenerateWouldHaveMade(WordValue curveNamed) {
        if (!EllipticCurveKey.curveNames().contains(curveNamed.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, curveNamed.spelling());
        }
        return BinaryValue.of(0);
    }

    /**
     * Diffie-Hellman over a modular group: publish, or agree.
     *
     * <p>Neither refinement answers none. The C reaches {@code return R_RET}
     * having never written the return slot, so a real 3.22.1 hands back
     * whatever that memory held -- a binary of nothing in particular, and a
     * crash when two contexts were built in one expression.
     */
    private static Value modularExchange(List<Value> arguments, Set<String> refinements) {
        if (refinements.contains("public") && refinements.contains("secret")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "dh publishes or agrees, not both");
        }
        DiffieHellmanKey key = modularKeyHeldBy(arguments.getFirst());
        if (key == null) {
            return NoneValue.none();
        }
        if (refinements.contains("public")) {
            return BinaryValue.of(unsignedOctets(key.published()));
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

    /**
     * The modular key a handle carries, or null when it carries something
     * else.
     *
     * <p>Null rather than a refusal, because the C declines rather than
     * raising here and doubts itself in the same line: {@code return R_NONE;
     * //or? Trap0(RE_INVALID_HANDLE);}
     */
    private static DiffieHellmanKey modularKeyHeldBy(Value given) {
        return given instanceof HandleValue held
                && DHM_HANDLE_TYPE.equals(held.typeName())
                && held.payload() instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof DiffieHellmanKey key
                ? key
                : null;
    }

    /** An RSA context from raw numbers, or none when they do not form a key. */
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

    /**
     * ECDH: whichever one thing the call named.
     *
     * <p>Exactly one, which is why the count is taken before anything else is
     * looked at. /INIT is apart from the other three because it is the only
     * one that makes a context rather than using one.
     */
    private static Value ellipticExchange(List<Value> arguments, Set<String> refinements) {
        refuseUnlessExactlyOneOf(ECDH_ACTIONS, refinements, "ecdh");
        if (refinements.contains("init")) {
            return curveKeyMadeOn(((WordValue) arguments.get(1)).canonical());
        }
        EllipticCurveKey key = curveKeyHeldBy(arguments.getFirst());
        if (key == null) {
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

    /** A fresh context on a named curve, or none where there is no such curve. */
    private static Value curveKeyMadeOn(String curveName) {
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

    /**
     * Where the peer's point sits in the argument list.
     *
     * <p>After the key, and after /INIT's curve name when that was asked for
     * as well. It cannot be in practice, because naming two actions is
     * refused, but the position is worked out rather than assumed: reading
     * index two unconditionally cost four tests that had a perfectly good
     * secret to agree on.
     */
    private static byte[] peersPointGivenTo(
            List<Value> arguments, Set<String> refinements) {
        int at = refinements.contains("init") ? 2 : 1;
        return ((BinaryValue) arguments.get(at)).octetsFromHere();
    }

    /**
     * ECDSA: a signature over a hash, or whether one holds.
     *
     * <p>Signing is what a call with neither refinement does, which is what a
     * real 3.22.1 does rather than a choice made here.
     */
    private static Value ellipticSignature(
            List<Value> arguments, Set<String> refinements) {
        EllipticCurveKey key = curveKeyHeldBy(arguments.getFirst());
        if (key == null) {
            return NoneValue.none();
        }
        byte[] hash = ((BinaryValue) arguments.get(1)).octetsFromHere();
        return refinements.contains("verify")
                ? whetherTheSignatureHolds(key, hash,
                        ((BinaryValue) arguments.get(2)).octetsFromHere())
                : signatureOver(key, hash);
    }

    /**
     * TRUE or NONE, not TRUE or FALSE.
     *
     * <p>The declaration says "returns true or false" and a real 3.22.1
     * answers none for a signature that does not hold. It matters because
     * {@code if ecdsa/verify ...} reads the same either way and a comparison
     * against FALSE does not.
     */
    private static Value whetherTheSignatureHolds(
            EllipticCurveKey key, byte[] hash, byte[] signature) {
        return key.verifies(hash, signature) ? LogicValue.yes() : NoneValue.none();
    }

    private static Value signatureOver(EllipticCurveKey key, byte[] hash) {
        return key.signed(hash)
                .<Value>map(signature -> BinaryValue.of(unsignedOctets(signature)))
                .orElseGet(NoneValue::none);
    }

    /** Refuses a call that named two of a set of actions meant to be exclusive. */
    private static void refuseUnlessExactlyOneOf(
            List<String> actions, Set<String> refinements, String nativeName) {
        if (actions.stream().filter(refinements::contains).count() > 1) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    nativeName + " does one thing per call");
        }
    }

    /**
     * The curve key a handle carries, or null when it carries something else.
     *
     * <p>Null rather than a refusal because both natives that ask answer none
     * for a handle they cannot use, as the C does.
     */
    private static EllipticCurveKey curveKeyHeldBy(Value given) {
        return given instanceof HandleValue held
                && ECDH_HANDLE_TYPE.equals(held.typeName())
                && held.payload() instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof EllipticCurveKey key
                ? key
                : null;
    }

    /** The four things RSA does, exactly one of which a caller must name. */
    private static final List<String> RSA_ACTIONS =
            List.of("encrypt", "decrypt", "sign", "verify");

    /**
     * One RSA operation, or nothing when the context cannot perform it.
     *
     * <p>The refusals divide in a way worth keeping straight. Naming two
     * actions, or a padding refinement with no action, is {@code
     * Trap0(RE_BAD_REFINES)} and raises before anything is looked at. A
     * handle of another type raises too. But a public-only context asked to
     * decrypt or sign answers none, as RSA-INIT answers none for numbers that
     * are not a key -- so a caller has to test the answer rather than trust
     * that no error meant success.
     */
    private static Value rsaOperation(List<Value> arguments, Set<String> refinements) {
        List<String> named = RSA_ACTIONS.stream().filter(refinements::contains).toList();
        boolean padded = refinements.contains("oaep") || refinements.contains("pss");
        if (named.size() > 1 || ((padded || refinements.contains("hash")) && named.isEmpty())) {
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
        if (named.isEmpty()) {
            return NoneValue.none();
        }
        String action = named.getFirst();
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

    /** The digest /HASH named, or the SHA-256 the C falls back to. */
    private static String digestNamedIn(List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("hash")) {
            return "sha256";
        }
        Value named = arguments.get(refinements.contains("verify") ? 3 : 2);
        return named instanceof WordValue digest ? digest.canonical() : "sha256";
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

    /**
     * Identities for cipher contexts, kept apart from the codecs' block.
     *
     * <p>Two handles are the same handle when they share an identity, so
     * every context made needs one of its own: a caller holding two ciphers
     * has to be able to tell them apart even though EQUAL? compares only
     * their type.
     */
    private static final int CIPHER_HANDLE_IDENTITY = 2000;

    private static final java.util.concurrent.atomic.AtomicInteger CIPHER_IDENTITIES =
            new java.util.concurrent.atomic.AtomicInteger(CIPHER_HANDLE_IDENTITY);

    private static int nextCipherIdentity() {
        return CIPHER_IDENTITIES.incrementAndGet();
    }

    /**
     * Enciphers a binary where it stands and answers that same binary.
     *
     * <p>{@code RC4_crypt(ctx, data, data, len)} reads and writes one buffer
     * and {@code DS_RET_VALUE(val_data)} hands back the argument, so a caller
     * holding the binary sees it change and there is no copy to compare
     * against. A handle registered under another type is refused by name
     * rather than read as a permutation, which would encipher something and
     * give an answer nobody could trace.
     */
    private static Value encipheredThroughTheStream(HandleValue held, BinaryValue data) {
        if (!RC4_HANDLE_TYPE.equals(held.typeName())
                || !(held.payload() instanceof JavaObjectValue carried)
                || !(carried.held().orElse(null) instanceof StreamCipher cipher)) {
            throw Raised.of(EvaluationFailure.INVALID_HANDLE, held.typeName());
        }
        requireChangeable(data);
        for (int at = data.index(); at <= data.storageLength(); at++) {
            data.storage().set(at, data.storage().at(at) ^ cipher.nextKeystreamByte());
        }
        return data;
    }

    /**
     * How many arcs the first byte of an object identifier carries, and what
     * it is divided by to find them.
     *
     * <p>{@code oid[0] / 40} and {@code oid[0] % 40} in {@code n-oid.c}. It
     * is why every identifier a script meets begins 0, 1 or 2: a first arc of
     * 3 would need a byte of 120 or more, and 2 is as far as one byte reaches
     * before the division carries past what the registry allots.
     */
    private static final int ARCS_PACKED_INTO_THE_FIRST_BYTE = 40;

    /** The seven bits of a base-128 group, and the bit that says more follow. */
    private static final int GROUP_BITS = 7;
    private static final int GROUP_MASK = 0x7F;
    private static final int MORE_GROUPS_FOLLOW = 0x80;

    /**
     * An object identifier written the way people write one: its arcs
     * separated by full stops.
     *
     * <p>{@code n-oid.c}. Two rules make the encoding. The first byte holds
     * two arcs rather than one, and every byte after is base 128, seven bits
     * at a time, with the high bit set on all but the last of its group.
     *
     * <p>A group whose last byte never arrives contributes nothing: the
     * accumulator is written out only when a byte turns up with its high bit
     * clear, so a truncated identifier reads as a shorter whole one rather
     * than refusing.
     *
     * <p>A long accumulates where the C uses a 32-bit unsigned and guards
     * against wrapping. The guard cannot fire before the accumulator has
     * taken more bytes than any real identifier carries, and answering the
     * argument unchanged as the C does there would hand back a binary where
     * every other path answers a string.
     */
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

    /** A value molded and cut to a width, the way the C's `%72r` does. */
    private static String moldedWithin(Value value, int width) {
        String written = Molder.mold(value);
        return written.length() <= width ? written : written.substring(0, width);
    }

    /**
     * The chants EVOKE has and this build cannot perform.
     *
     * <p>All six are inside {@code #ifdef DEBUG}, and the {@code #else} gives
     * them one line between them: {@code Trap0(RE_FEATURE_NA)}. So a released
     * 3.22.1 refuses these too, and refuses them by name rather than pretending
     * to have done something.
     */
    private static final Set<String> DEBUG_ONLY_CHANTS = Set.of(
            "crash-dump", "watch-recycle", "watch-alloc",
            "watch-obj-copy", "watch-expand", "crash");

    /**
     * The list of chants, printed for anything the dialect does not know.
     *
     * <p>Assembled by the preprocessor in the C, so a release build lists
     * `stack-size` and the two numbered checks and not the watch chants: those
     * sit inside the same {@code #ifdef DEBUG} that refuses them.
     */
    private static final String EVOKE_HELP = """
            Evoke values:
            [stack-size n]

            1: check memory pools
            2: check bind table
            """;

    /**
     * Does what one chant asks, or says why it cannot, and answers how many
     * values after it the chant took as its own.
     *
     * <p>Three answers and no more. A debug-only chant raises `feature-na`.
     * A chant naming something this interpreter does by itself -- `stack-size`,
     * which grows a buffer the C also grows unasked, and the numbered checks
     * over pools that cannot be wrong here -- is accepted and needs nothing
     * done. Anything else prints the list, which is the C's own default.
     *
     * <p>`stack-size` takes the value after it. The C steps over it without
     * counting it, and then reads one value past the end of the block; the
     * stepping is the behaviour and the overrun is not, so the count comes back
     * here and the walk stays inside the block.
     */
    private int obey(Value chant, Evaluator evaluator) {
        if (chant instanceof WordValue named) {
            if (DEBUG_ONLY_CHANTS.contains(named.canonical())) {
                throw Raised.of(EvaluationFailure.FEATURE_NA, named.spelling());
            }
            if (named.canonical().equals("stack-size")) {
                return 1;
            }
            if (named.canonical().equals("delect")) {
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

    /**
     * system/standard/stats, refreshed in place and answered.
     *
     * <p>Four of the thirteen fields are real measurements here: the timer,
     * the count of values walked, and the counts of native and function calls.
     * The other nine name Rebol's own series pool -- series-made, series-freed,
     * series-expanded, series-bytes, series-recycled, made-blocks,
     * made-objects, recycles, collisions -- and JEBOL has no such pool. Those
     * are left at zero.
     *
     * <p>Zero is not a claim that nothing was allocated. It is the absence of
     * a figure, and it reads the same as one, which is worth knowing before
     * trusting a profile taken here. What the JVM allocates is the JVM's
     * business and is not countable per REBOL value without instrumenting
     * every constructor.
     */
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

    /** Writes a field only when the object has it, so a short prototype is safe. */
    private static void setIfPresent(Context fields, String name, Value written) {
        if (fields.holds(name)) {
            fields.set(name, written);
        }
    }

    /** What WAIT accepts: `value [number! time! port! block! none!]`. */
    private static Set<Datatype> waitableDatatypes() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.NUMBER.members());
        accepted.addAll(List.of(Datatype.TIME, Datatype.PORT,
                Datatype.BLOCK, Datatype.NONE));
        return Set.copyOf(accepted);
    }

    /**
     * Sleeps, in slices, asking between each whether the script should stop.
     *
     * <p>One long sleep would outlive the bounds the host set: the evaluator
     * checks the deadline between steps, and a sleeping thread takes no steps.
     * A script that waited an hour under a one-second limit would run for the
     * hour, which breaks the promise that running too long arrives as an
     * outcome rather than as a hung thread.
     */
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

    /**
     * Roughly how many values a frame occupies, for STACK/SIZE and for DS.
     *
     * <p>Both, because the C reads the same DSP for both and a caller who asks
     * one and then the other has to be told the same thing.
     */
    private static final int FRAME_VALUE_UNITS = 8;

    /** When this interpreter started, for STATS/TIMER. */
    private final long startedAt = System.nanoTime();

    /**
     * The REBOL version JEBOL implements, as text and as the tuple /DATA
     * answers.
     *
     * <p>Not a version number of JEBOL's own. A script reads
     * {@code system/version} to decide which of the language's features it may
     * use, and {@code struct-test.r3} wraps all 188 of its assertions in
     * {@code if system/version >= 3.19.1}. Answering a number below every
     * guard in the suite made those blocks skip, which reads as a passing
     * file that ran nothing.
     */
    private static final String VERSION_TEXT = "3.22.5";
    private static final int[] VERSION_PARTS = {3, 22, 5};

    /** What POKE and POKEZ will write into. */
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
                    return BlockValue.block(java.util.Arrays.stream(whole.split("\r?\n", -1))
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
                    return rewritten((StringValue) arguments.getFirst(), text -> {
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

    private void defineConversion() {
        define("to", takesAnything("type", "value"),
                (arguments, evaluator, context) -> {
                    if (arguments.get(0) instanceof DatatypeValue wanted
                            && wanted.represents() == Datatype.EVENT) {
                        return EventPath.made(wanted, arguments.get(1),
                                value -> simpleValueOf(value, evaluator, context));
                    }
                    return converted(Conversion.TO, arguments.get(0), arguments.get(1));
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
                    java.util.OptionalInt width =
                            refinements.contains("size") && arguments.size() > 1
                            ? java.util.OptionalInt.of(
                                    (int) ((IntegerValue) arguments.get(1)).magnitude())
                            : java.util.OptionalInt.empty();
                    return WordValue.of(switch (arguments.getFirst()) {
                        case TupleValue tuple -> hexOfEachSegment(tuple);
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
                        return BlockValue.block(linesOf(text.text()));
                    }
                    return rewritten(text, whole -> whole.replace("\r\n", "\n"));
                });
        define("enline", List.of(Parameter.required("text",
                        Set.of(Datatype.STRING, Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    if (arguments.getFirst() instanceof BlockValue) {
                        throw Raised.of(EvaluationFailure.NOT_DONE,
                                "joining a block of lines is not written yet");
                    }
                    return rewritten((StringValue) arguments.getFirst(),
                            whole -> whole.replace("\r\n", "\n"));
                });

        define("as", List.of(
                        Parameter.required("type", asTypeOrExample()),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    Datatype wanted = arguments.get(0) instanceof DatatypeValue named
                            ? named.represents()
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

    /**
     * A function from one block holding a spec block and a body block.
     *
     * <p>Anything else is refused rather than guessed at: a spec with no
     * body would give a function that answers unset, which is a thing
     * somebody meant to write and not a thing to infer.
     */
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

    /**
     * A function with another's behaviour and a new declared interface.
     *
     * <p>The given block holds a spec block. Anything else is a body
     * where a spec was wanted, and R3 refuses it rather than guessing.
     */
    private static Value derivedFunction(Value original, BlockValue given) {
        List<Value> parts = given.remaining();
        if (parts.isEmpty() || !(parts.getFirst() instanceof BlockValue spec)) {
            return raiseCannotUse(given, "make on a function");
        }
        if (original instanceof NativeValue && parts.size() > 1) {
            return raiseCannotUse(given, "make");
        }
        List<Parameter> parameters = FunctionSpec.parametersIn(spec);
        return switch (original) {
            case NativeValue built -> new NativeValue(built.nativeName(), parameters);
            case FunctionValue written -> new FunctionValue(
                    spec, written.body(), parameters,
                    FunctionSpec.localNamesIn(spec), written.closedOver());
            default -> raiseCannotUse(original, "make");
        };
    }

    /**
     * MAKE IMAGE!, in the four forms `t-image.c` accepts.
     *
     * <p>An image is copied. A pair is a size, and the image it makes is opaque
     * white -- `CLEAR_IMAGE` is a memset of 0xFF and the comment beside it says
     * so. A block is a size followed by its contents, which `Create_Image` reads
     * in a fixed order: a binary of RGB triples, then a binary of alpha bytes,
     * then a starting index; or a tuple to fill with, then an alpha to fill with;
     * or a block of tuples, one a pixel.
     *
     * <p>Anything else is `malconstruct`, which is the fall-through of every
     * branch: `Trap1(RE_MALCONSTRUCT, arg)`.
     */
    private static Value madeImage(Value from) {
        if (from instanceof ImageValue original) {
            return new ImageValue(original.storage().copy(), 1);
        }
        if (from instanceof PairValue size) {
            return ImageValue.of(sideOf(size.x()), sideOf(size.y()));
        }
        if (from instanceof BlockValue parts && !parts.remaining().isEmpty()) {
            return imageFromParts(parts.remaining());
        }
        return raiseMalconstruct(from);
    }

    /**
     * A side of a new image, clamped below and refused above.
     *
     * <p>`w = MAX(w, 0)` for a negative one, and `if (w > 0xFFFF || h > 0xFFFF)
     * Trap1(RE_SIZE_LIMIT, ...)` for one too big. The two go different ways on
     * purpose: a negative size is a mistake with an obvious reading and an
     * oversized one is not.
     */
    private static int sideOf(double given) {
        int side = (int) given;
        if (side > ImageStorage.LONGEST_SIDE) {
            throw Raised.of(EvaluationFailure.SIZE_LIMIT, "image!");
        }
        return Math.max(side, 0);
    }

    /** `Create_Image`: a size, and then whichever contents follow it. */
    private static Value imageFromParts(List<Value> parts) {
        if (!(parts.getFirst() instanceof PairValue size)) {
            return raiseMalconstruct(parts.getFirst());
        }
        ImageValue made = ImageValue.of(sideOf(size.x()), sideOf(size.y()));
        int at = 1;
        if (at < parts.size() && parts.get(at) instanceof BinaryValue colours) {
            fillColoursFrom(made, colours);
            at++;
            if (at < parts.size() && parts.get(at) instanceof BinaryValue alphas) {
                fillAlphasFrom(made, alphas);
                at++;
            }
            if (at < parts.size() && parts.get(at) instanceof IntegerValue start) {
                made = made.atIndex(Math.max(1, (int) start.magnitude()));
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
        } else if (at < parts.size() && parts.get(at) instanceof BlockValue tuples) {
            fillFromTuples(made, tuples.remaining());
            at++;
        }
        return at == parts.size() ? made : raiseMalconstruct(parts.get(at));
    }

    /** `Bin_To_RGB`: three bytes a pixel, and the alpha already there is kept. */
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

    /** `Bin_To_Channel(..., SYM_ALPHA)`: one byte a pixel. */
    private static void fillAlphasFrom(ImageValue made, BinaryValue alphas) {
        int pixels = Math.min(made.storageLength(), alphas.lengthFromHere());
        for (int pixel = 1; pixel <= pixels; pixel++) {
            made.storage().setAlphaAt(pixel,
                    alphas.storage().at(alphas.index() + pixel - 1));
        }
    }

    /**
     * `Fill_Rect` with the tuple, keeping the alpha when the tuple had none.
     *
     * <p>The last argument of `Fill_Rect` is `VAL_TUPLE_LEN(block) == 3`, which
     * is how a three-part tuple leaves the alpha alone and a four-part one
     * writes it.
     */
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

    /** `Tuples_To_RGBA`: the pixels one by one, as far as either side reaches. */
    private static void fillFromTuples(ImageValue made, List<Value> tuples) {
        int pixels = Math.min(made.storageLength(), tuples.size());
        for (int pixel = 1; pixel <= pixels; pixel++) {
            if (!(tuples.get(pixel - 1) instanceof TupleValue colour)) {
                raiseMalconstruct(tuples.get(pixel - 1));
                return;
            }
            int[] parts = colour.segments();
            made.storage().setColourAt(pixel,
                    parts.length > 0 ? parts[0] : 0,
                    parts.length > 1 ? parts[1] : 0,
                    parts.length > 2 ? parts[2] : 0);
            made.storage().setAlphaAt(pixel, parts.length > 3 ? parts[3] : 0xFF);
        }
    }

    private static Value raiseMalconstruct(Value from) {
        throw Raised.of(EvaluationFailure.MALCONSTRUCT,
                Molder.mold(from));
    }

    /**
     * How far along a navigation action was asked to go.
     *
     * <p>An integer, a decimal or a logic is the offset itself, and every series
     * reads them the same way. A pair is a coordinate and only an image can read
     * one: {@code diff = ((y - 1) * wide + x)} for AT, and without the 1 for SKIP
     * and ATZ, because one counts from one and the others from zero.
     */
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

    /**
     * A colour part as a byte, the three ways `arg_to_byte` reads one.
     *
     * <p>An integer as itself, a decimal rounded -- and rounding is the surprise,
     * because every other decimal-to-integer conversion in the C truncates -- and
     * a percent as a fraction of 255. Then clamped at both ends.
     */
    private static int colourByteOf(Value given) {
        double number = switch (given) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue fraction -> fraction.datatype() == Datatype.PERCENT
                    ? fraction.quantity() * 255.0 + 0.5
                    : fraction.quantity() + 0.5;
            default -> 0;
        };
        return Math.max(0, Math.min(255, (int) number));
    }

    /** The first three parts of a tuple, which is what a colour is. */
    private static int[] threeParts(TupleValue colour) {
        int[] parts = colour.segments();
        return new int[] {
                parts.length > 0 ? parts[0] : 0,
                parts.length > 1 ? parts[1] : 0,
                parts.length > 2 ? parts[2] : 0};
    }

    /**
     * The tuple, recoloured in place and answered.
     *
     * <p>`return R_ARG1` after writing through `(REBCLR*)VAL_TUPLE(...)`. A tuple
     * here is immutable, so the answer is a new one with the same identity as far
     * as a script can tell -- except that the C's caller keeps its own copy
     * changed, which a script sees only if it held the tuple in a word. Pinned in
     * the tests either way.
     */
    private static Value recolouredTuple(
            TupleValue colour, java.util.function.UnaryOperator<int[]> formula) {
        int[] made = formula.apply(threeParts(colour));
        return TupleValue.of(made);
    }

    /**
     * One formula over a colour or over every pixel of an image.
     *
     * <p>The two arms of each of these natives: `if (IS_TUPLE(value))` answers
     * something about the colour, and the else walks the image from its position
     * -- `len = VAL_IMAGE_LEN(value)`, `rgba = VAL_IMAGE_DATA(value)` -- writing
     * each pixel and answering the image itself.
     */
    private static Value overEveryColour(
            Value target,
            java.util.function.Function<int[], Value> ofAColour,
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

    /**
     * A spec block's value, with a word or a path resolved first.
     *
     * <p>{@code Get_Simple_Value}: "Does easy lookup, else just returns the value
     * as is." A word or a get-word becomes what it holds and a path becomes what
     * it reads; everything else is left alone.
     *
     * <p>Both spec walkers that build a datatype from set-word pairs call it --
     * {@code Set_GOB_Vars} and {@code Set_Event_Vars} -- and without it a spec can
     * only carry values the source spelled out. Rebol's own gob test relies on it:
     * `g2: make gob! [size: g1/size]` under "simple paths inside GOB".
     *
     * <p>Easy is the operative word. A function is answered rather than called and
     * a paren is left as a paren, so a spec block is data with names in it rather
     * than code.
     */
    private static Value simpleValueOf(Value given, Evaluator evaluator, Context context) {
        if (given instanceof WordValue named
                && (named.datatype() == Datatype.WORD
                        || named.datatype() == Datatype.GET_WORD)) {
            return evaluator.valueOfWordIn(named, context);
        }
        if (given instanceof BlockValue path
                && (path.datatype() == Datatype.PATH
                        || path.datatype() == Datatype.GET_PATH)) {
            return evaluator.valueOfPathIn(path, context);
        }
        return given;
    }

    /**
     * MAKE VECTOR!, from a count, a spec block or a binary.
     *
     * <p>A count is a length of signed 32-bit zeros and a negative one is out
     * of range rather than a bad argument, which is the C's {@code Int32s(arg,
     * 0)} refusing before {@code Make_Vector} is reached. Everything else that
     * will not read is a bad argument.
     */
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


    /**
     * A date read out of a string, which is the lexer's job rather than a
     * conversion of its own.
     *
     * <p>{@code Scan_Date} is what the C reaches for here, and it is the same
     * scanner a date literal in source goes through.
     */
    private static Value dateReadFrom(StringValue written) {
        return Transcoder.transcode(written.text()).values()
                .map(BlockValue::remaining)
                .filter(read -> read.size() == 1 && read.getFirst() instanceof DateValue)
                .map(List::getFirst)
                .orElseGet(() -> raiseBadMakeArg(written, "date!"));
    }

    /**
     * The block shapes built either way, which is the whole of
     * {@code Make_Block_Type} including the {@code make} flag it is handed.
     *
     * <p>MAKE and TO differ here and JEBOL had them the same. TO wraps
     * whatever it is given, so {@code to block! #"a"} is {@code [#"a"]} and
     * {@code to block! "1 2"} is the one-item block holding that string.
     * MAKE takes a list of shapes and refuses the rest, so
     * {@code make block! #"a"} is an error, {@code make block! 4.0} is an
     * empty block because a number is room rather than a value, and
     * {@code make block! "1 2"} reads the text as source and answers
     * {@code [1 2]}.
     *
     * <p>Four shapes answer the same to both, because a block is what they
     * already are underneath: another block, a map, an object and a vector.
     *
     * <p>Only TO reaches the typeset arm, and only for a block or a paren.
     * Only MAKE reaches the room, source-text and pair arms, because TO has
     * answered by then -- except for a hash, which is a block shape in the
     * typeset sense and not in the range {@code ANY_BLOCK_TYPE} tests, so
     * {@code to hash! 4} falls through the lot of them and is refused.
     */
    private static Value blockTypeBuilt(Conversion asking, Datatype wanted, Value from) {
        if (from instanceof BlockValue given) {
            return BlockValue.block(given.remaining()).as(wanted);
        }
        if (from instanceof MapValue pairs) {
            return BlockValue.block(pairs.flattened()).as(wanted);
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
            return sourceReadFrom(text.text(), wanted);
        }
        if (from instanceof BinaryValue octets) {
            return sourceReadFrom(textDecodedFrom(octets), wanted);
        }
        if (from.datatype() == Datatype.PAIR) {
            return BlockValue.block(List.of()).as(wanted);
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(from));
    }

    /**
     * Whether TO puts a lone value inside this shape rather than refusing it.
     *
     * <p>{@code ANY_BLOCK_TYPE} is a range test over the datatype table --
     * block to lit-path -- and hash sits one past the end of it. So hash is
     * an any-block! for the typeset and is not one here, and that single row
     * of the table is the whole of the difference.
     */
    private static boolean wrapsIntoWhatTheCallerAskedFor(Datatype wanted) {
        return wanted == Datatype.BLOCK || wanted == Datatype.PAREN || wanted.isAnyPath();
    }

    /**
     * An object's fields as {@code name: value} pairs, which is
     * {@code Make_Object_Block} in the mode that asks for both.
     *
     * <p>SELF is slot zero and the C starts counting at one, so it is left
     * out of every place this is asked for: BODY-OF, the block a conversion
     * answers, and the spec an error is built from.
     */
    private static List<Value> setWordsAndValuesOf(Context fields) {
        return fields.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .flatMap(slot -> java.util.stream.Stream.of(
                        (Value) WordValue.of(slot.spelling(), Datatype.SET_WORD),
                        slot.value()))
                .toList();
    }

    /**
     * The same pairs as a block, each one on a line of its own.
     *
     * <p>{@code Make_Object_Block} sets the line flag on every set-word it
     * writes -- {@code VAL_SET_LINE(value)} on the line after it makes one --
     * so the block molds a field to a line rather than all of them in a row.
     * That is a property of the block and not of how it is later printed,
     * which is why building it without the flags made
     * {@code to block! make object! [a: 1]} mold on one line where a real
     * Rebol takes three.
     */
    private static BlockValue blockOfFieldsAndValues(Context fields) {
        BlockValue block = BlockValue.block(setWordsAndValuesOf(fields));
        for (int at = 1; at <= block.storageLength(); at += 2) {
            block.storage().setLineBreakAt(at, true);
        }
        return block;
    }

    /**
     * Source text read into values, stopping where the source stops.
     *
     * <p>{@code Scan_Source} is handed bytes and a nought byte ends them,
     * which is the C's own convention for where a string finishes rather than
     * anything about the reader. So {@code make block! #{31 00 32}} is
     * {@code [1]} and not {@code [1 2]}, and it holds for text as much as for
     * bytes because both reach the scanner the same way.
     *
     * <p>Reading past it made the nought a character in its own right, so an
     * empty source came back as a block holding one of them.
     */
    private static Value sourceReadFrom(String source, Datatype wanted) {
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

    /**
     * The date a Unix timestamp names, counted in microseconds.
     *
     * <p>{@code Timestamp_To_Date} and {@code Timestamp_Decimal_To_Date} are
     * the same walk at two precisions, so this is the second one and the
     * whole-second form multiplies up to reach it.
     *
     * <p>Microseconds rather than nanoseconds is the C's own choice, and its
     * comment says why: a decimal count of seconds multiplied out to
     * nanoseconds does not land where it should. The remainder is then scaled
     * back up, so the time is exact to a microsecond and zero below that.
     *
     * <p>The zone is zero. A timestamp names an instant and not a place, so
     * there is nothing to offset it by.
     */
    private static Value dateAtTheTimestamp(long microseconds) {
        long dayNumber = Math.floorDiv(microseconds, MICROSECONDS_A_DAY);
        long withinTheDay = Math.floorMod(microseconds, MICROSECONDS_A_DAY);
        java.time.LocalDate day = java.time.LocalDate.ofEpochDay(dayNumber);
        return DateValue.of(day.getYear(), day.getMonthValue(), day.getDayOfMonth(),
                TimeValue.ofNanoseconds(withinTheDay * 1_000L));
    }

    /**
     * A date from a block of parts, which is what MAKE and the construction
     * syntax both come through.
     *
     * <p>{@code MT_Date}. Day, month, then year -- except that the first
     * number is read as the year when it is over ninety-nine, so
     * {@code make date! [2000 1 1]} and {@code make date! [1 1 2000]} are
     * the same day and neither is ambiguous. A time may follow as three more
     * numbers, and the month, the day and February in a common year are all
     * checked, so a bad block is refused rather than rounded.
     */
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

    /**
     * The calendar day three numbers name.
     *
     * <p>Day, month, year -- except that a first number over ninety-nine is
     * the year instead, so {@code [2000 1 1]} and {@code [1 1 2000]} are the
     * same day and neither is ambiguous.
     */
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

    /**
     * How many of the parts after the calendar day the clock accounts for.
     *
     * <p>One where it is written as a time and three where it is written as
     * hours, minutes and seconds. Anything else leaves the clock unread, and
     * whatever is there has to answer to the zone or be refused.
     */
    private static int howManyPartsTheClockTakes(List<Value> after) {
        if (after.isEmpty()) {
            return 0;
        }
        if (after.getFirst() instanceof TimeValue) {
            return 1;
        }
        return after.getFirst() instanceof IntegerValue ? 3 : 0;
    }

    /**
     * The clock, with each part held to its own bound.
     *
     * <p>{@code if (hour > 23 || minute >= 60 || second >= 60.0) return
     * FALSE;} -- so a date will not take the twenty-fourth hour even though a
     * time! will. That is the whole difference between
     * {@code make time! [24 0 0]}, which is a day's worth of hours, and
     * {@code make date! [2000 2 1 24 0 0]}, which is no date at all.
     *
     * <p>The seconds may be written with a fraction and the other two may not.
     */
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
                hour.magnitude() * SECONDS_AN_HOUR * NANOSECONDS_A_SECOND
                        + minute.magnitude() * SECONDS_A_MINUTE * NANOSECONDS_A_SECOND
                        + Math.round(second * NANOSECONDS_A_SECOND));
    }

    private static final long SECONDS_A_MINUTE = 60L;
    private static final long SECONDS_AN_HOUR = 3600L;

    /**
     * The zone, which is one more time after the clock.
     *
     * <p>That a block may hold two times and the second is not another clock
     * is the part worth saying. Anything left after it is a refusal:
     * {@code if (!IS_END(arg)) return FALSE;} -- a part the grammar cannot
     * account for is not something to step over.
     */
    private static Optional<Integer> zoneAfterTheClock(
            List<Value> left, List<Value> whole) {
        if (left.isEmpty()) {
            return Optional.empty();
        }
        if (left.size() > 1 || !(left.getFirst() instanceof TimeValue offset)) {
            raiseBadMakeArg(BlockValue.block(whole), "date!");
        }
        long minutes = ((TimeValue) left.getFirst()).nanoseconds()
                / (SECONDS_A_MINUTE * NANOSECONDS_A_SECOND);
        if (Math.abs(minutes) > FURTHEST_ZONE_MINUTES) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "a zone reaches fifteen hours either side of UTC");
        }
        return Optional.of((int) minutes);
    }

    /** {@code MAX_ZONE} is sixty quarter-hours, which is fifteen of them. */
    private static final long FURTHEST_ZONE_MINUTES = 15 * 60L;

    /**
     * A time from a block of parts: hours, then minutes, then seconds.
     *
     * <p>{@code Make_Time}'s block branch. The hours carry the sign for the
     * whole span and the seconds may be fractional, so {@code [-1 30 0]} is
     * minus an hour and a half rather than an hour less thirty minutes.
     */
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

    /**
     * CHANGE on a struct: fields from a block, or bytes from a binary.
     *
     * <p>The two are not the same operation. A block names or lists fields and
     * goes through the same initialiser MAKE uses, so it can write a word or a
     * decimal into a field of the right kind. A binary is copied over the
     * bytes as far as the shorter of the two reaches, which is why changing a
     * two-byte struct with three bytes writes two and drops the third.
     *
     * <p>A struct carrying a live REBOL value refuses the binary form
     * outright: the C keeps such a value in the bytes themselves and will not
     * let arbitrary data land on top of one.
     */
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

    /**
     * The four things a struct answers about itself.
     *
     * <p>{@code A_REFLECT} in {@code REBTYPE(Struct)} takes WORDS, VALUES,
     * BODY and SPEC and refuses everything else, so KEYS-OF reaches this as
     * WORDS and there is no fifth question to ask. The spec is the layout
     * block and the other three are read out of the bytes.
     */
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

    /**
     * Where a field written {@code [struct! some-name]} finds its layout.
     *
     * <p>REGISTER files a layout block in {@code system/catalog/structs} under
     * a name, and this is the other half of that. It is handed to the value
     * layer as a lookup rather than as the catalogue itself, because a struct
     * must not know that the SYSTEM object exists.
     */
    private StructSpec.LayoutRegistry structLayoutsKnown() {
        return name -> registeredStructLayouts.select(WordValue.of(name))
                instanceof BlockValue layout
                ? Optional.of(layout)
                : Optional.empty();
    }

    /**
     * MAKE STRUCT!, which is {@code MT_Struct}.
     *
     * <p>One block is a layout on its own. Two blocks are a layout and the
     * values to start it with, which is the shape construction syntax reads:
     * {@code #(struct! [a [uint8!]] [a: 1])}. A layout can never itself be two
     * blocks, because after its optional attributes it must be a word and then
     * a block, so the two shapes cannot be confused.
     */
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

    /**
     * MAKE on a struct rather than on the datatype: the prototype's bytes,
     * copied, with whatever the block says written over them.
     *
     * <p>The block is reduced with its set-words left standing, which is what
     * {@code Reduce_Block_No_Set} does, so {@code make proto! [3 * 10 4 * 10]}
     * writes thirty and forty while {@code [b: 3 * 10]} still names a field.
     */
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

    /**
     * The initial values a block gives, written into a struct as they stand.
     *
     * <p>Nothing is evaluated here, which is the whole of what
     * {@code MT_Struct} does with its second block. Only MAKE on an existing
     * struct reduces first, and it reduces before calling this. That is why
     * {@code #(struct! [a [uint8!]] [random 10])} is a malconstruct rather
     * than a struct holding a random number: RANDOM arrives as a word, and a
     * word cannot go in a {@code uint8!} field.
     */
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

    /**
     * A construct built the way the reader builds one, which is
     * {@code Make_Dispatch} rather than MAKE.
     *
     * <p>The two are the same arm for nearly every datatype, and the table is
     * what says so, which is why the reader hands everything here instead of
     * keeping a list of its own. A date is where they part. {@code MT_Date}
     * reads a date or a block of parts and nothing else, while the count of
     * seconds since 1970 is in the MAKE arm one level above it. So
     * {@code make date! 1} is the first second of 1970 and {@code #(date! 1)}
     * does not read at all.
     */
    private Value constructionOf(Datatype datatype, Value specification) {
        if (datatype == Datatype.DATE
                && !(specification instanceof BlockValue
                        || specification instanceof DateValue)) {
            return raiseBadMakeArg(specification, "date!");
        }
        return makeOfDatatype(DatatypeValue.of(datatype), specification,
                null, Context.root());
    }

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
            return madeVector(from, evaluator, context);
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
        if (wanted.represents().isAnyBlock()) {
            return blockTypeBuilt(Conversion.MAKE, wanted.represents(), from);
        }
        if (wanted.represents().isSeries()
                && (from.datatype() == Datatype.INTEGER
                        || from.datatype() == Datatype.DECIMAL)) {
            int asked = (int) Math.max(0,
                    Math.min(Integer.MAX_VALUE, (long) Comparison.asDouble(from)));
            return wanted.represents() == Datatype.BINARY
                    ? new BinaryValue(new BinaryStorage(asked), 1)
                    : new StringValue(
                            StringStorage.withRoomFor(asked), 1, wanted.represents());
        }
        return converted(Conversion.MAKE, wanted, from);
    }

    /**
     * A binary read as the eight bytes of a double, right-aligned.
     *
     * <p>Shorter than eight is padded at the front, so #{01} is the
     * smallest subnormal rather than the number one. Longer than eight
     * keeps the last eight, which is the same rule seen from the other
     * end.
     */
    private static long bitsOf(BinaryValue binary) {
        int howMany = binary.lengthFromHere();
        long bits = 0;
        for (int at = Math.max(0, howMany - Long.BYTES); at < howMany; at++) {
            bits = (bits << 8) | (binary.storage().at(binary.index() + at) & 0xFFL);
        }
        return bits;
    }

    /** A binary's bytes from its current position, as the JVM counts them. */
    private static byte[] bytesFromHere(BinaryValue binary) {
        int howMany = binary.lengthFromHere();
        byte[] bytes = new byte[howMany];
        for (int at = 0; at < howMany; at++) {
            bytes[at] = (byte) binary.storage().at(binary.index() + at);
        }
        return bytes;
    }

    /** Signed JVM bytes as a binary value, which counts them unsigned. */
    private static Value binaryOfBytes(byte[] bytes) {
        int[] octets = new int[bytes.length];
        for (int at = 0; at < bytes.length; at++) {
            octets[at] = bytes[at] & 0xFF;
        }
        return BinaryValue.of(octets);
    }

    /**
     * Text as its lines, with a line ending reading as an ending rather than
     * as the start of an empty line.
     *
     * <p>Java's own split does one of two wrong things: with no limit it drops
     * every trailing empty line, so two blank lines come back as none, and
     * with a limit of -1 it keeps the one after the last ending, so a file
     * that ends properly gains a line it has not got. Dropping exactly one is
     * the rule, and nothing at all is no lines rather than one empty one.
     */
    private static List<Value> linesOf(String text) {
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

    /**
     * An address built from a block: a user, then a host in dotted parts.
     *
     * <p>{@code make email! [aaa bbb cc]} is {@code aaa@bbb.cc}. The first
     * item is the whole of the user and everything after it is a label of the
     * host, which is why two items give no dot and three give one. One item is
     * a user with no host at all, and an empty block names nobody.
     */
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

    /**
     * A url built from a block: a scheme, then the path it names.
     *
     * <p>{@code make url! [http]} is {@code http://} and {@code make url!
     * [http www.rebol.com %reboldoc.html]} is the whole address. The scheme
     * takes the two slashes whether or not anything follows it, and every item
     * after it is one segment of the path.
     */
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

    /** A block of whole numbers as one byte each, refusing anything else. */
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

    /**
     * Which of MAKE and TO is asking, because the two are not one operation
     * and a handful of datatypes tell them apart.
     *
     * <p>MAKE builds, so it reads a number as room for values and a logic as
     * one or zero. TO converts, so it wraps whatever it is given and refuses
     * a logic outright. {@code T_Integer} says why in as many words: no
     * integer is uniquely representative of true.
     *
     * <p>The C carries the same distinction as the {@code make} flag it hands
     * {@code Make_Block_Type} and as the {@code action != A_MAKE} it tests in
     * the scalar arms.
     */
    private enum Conversion {
        MAKE, TO;

        boolean builds() {
            return this == MAKE;
        }
    }

    /**
     * Nothing is not an empty something.
     *
     * <p>{@code make string! none} is an error where {@code make string! 0}
     * is an empty string, and Rebol's own suite asserts that for all
     * fifty-seven datatypes in one go. Three of them answer rather than
     * refuse: UNSET and NONE answer their own single value, and LOGIC reads
     * none as false.
     *
     * <p>The block shapes are left out because they answer for themselves.
     * MAKE refuses none there as an invalid argument rather than a bad make
     * argument, and TO does not refuse it at all -- {@code to block! none} is
     * {@code [#(none)]}.
     */
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

    /** A value converted to whatever datatype was named. */
    private static Value converted(Conversion asking, Value type, Value value) {
        if (!(type instanceof DatatypeValue wanted)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "to needs a datatype, not " + type.datatype().literalSpelling());
        }
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
            case BITSET -> bitsetOf(value);
            case TYPESET -> value instanceof BlockValue named
                    && named.datatype() == Datatype.BLOCK
                    ? TypesetValue.of(datatypesNamedIn(named))
                    : raiseBadMakeArg(value, "typeset!");
            case TIME -> TimeValue.ofNanoseconds(
                    (long) (Comparison.asDouble(value) * NANOSECONDS_A_SECOND));
            case TUPLE -> tupleFrom(value);
            case LOGIC -> LogicValue.of(countsAsTrue(asking, value));
            case DATATYPE -> value instanceof WordValue named
                    ? datatypeNamed(named, value)
                    : raiseBadMakeArg(value, "datatype!");
            default -> raiseCannotUse(value, "to " + wanted.represents().literalSpelling());
        };
    }

    /**
     * Whether a value counts as true, which MAKE and TO answer differently
     * for a number that is nothing.
     *
     * <p>The C leaves a note where it decides, and it is the clearest
     * statement anywhere of what separates the two. TO falls in line with the
     * rest of the interpreter, where everything that is not none and not
     * false is true, so {@code to logic! 0} is true. MAKE takes more liberties
     * with the meaning of its argument and lets a zero be false, so
     * {@code make logic! 0} is false.
     */
    private static boolean countsAsTrue(Conversion asking, Value value) {
        return value.isTruthy() && !(asking.builds() && isNothingAtAll(value));
    }

    /** Zero, in each of the four datatypes that MAKE LOGIC! reads as false. */
    private static boolean isNothingAtAll(Value value) {
        return switch (value) {
            case IntegerValue whole -> whole.magnitude() == 0;
            case DecimalValue number -> number.quantity() == 0.0;
            case MoneyValue amount -> amount.amount().signum() == 0;
            default -> false;
        };
    }

    /**
     * A binary from whatever was offered, which is {@code make_binary} and is
     * a list of datatypes rather than a rule.
     *
     * <p>Reading it as a rule is what went wrong here. Anything with bytes
     * underneath looks convertible, and four datatypes that have bytes are not
     * on the list: a percent, a paren, a path and an issue are all refused
     * where the decimal, block, string and word they resemble are taken. The C
     * says so by naming its cases and giving everything else {@code ser = 0},
     * which becomes {@code Trap_Arg} and an invalid argument.
     *
     * <p>MAKE and TO part company on one line. A number is room for bytes to
     * MAKE and the eight bytes of a big-endian whole number to TO, and that
     * split is handled before this is reached.
     *
     * <p>A tuple keeps its own length rather than the three it shows, so
     * {@code to binary! 1.1.1} is three bytes and {@code 1.2.3.4.5} is five.
     * A bitset that was written as a complement answers the complement of its
     * bytes. An image answers four bytes a pixel, red green blue and alpha.
     */
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
            case BitsetValue members -> binaryOfBytes(members.isComplemented()
                    ? eachByteTurnedOver(members.octets())
                    : members.octets());
            case ImageValue picture -> binaryOfBytes(pixelsOf(picture));
            case CharacterValue letter -> binaryOfBytes(
                    Character.toString(letter.codepoint())
                            .getBytes(StandardCharsets.UTF_8));
            default -> raiseInvalidArgument(value);
        };
    }

    /**
     * Every byte inverted, which is {@code Complement_Binary}.
     *
     * <p>A complemented bitset keeps the bytes of what it leaves out and a
     * flag saying to read them the other way round, so asking it for its
     * octets gives the same answer either way. The turning has to happen
     * here, and not doing it made {@code to binary! complement charset "a"}
     * answer the set it is the complement of.
     */
    private static byte[] eachByteTurnedOver(byte[] octets) {
        byte[] turned = new byte[octets.length];
        for (int at = 0; at < octets.length; at++) {
            turned[at] = (byte) ~octets[at];
        }
        return turned;
    }

    /** A tuple's own octets, however many of them it is keeping. */
    private static byte[] octetsOf(TupleValue segments) {
        byte[] octets = new byte[segments.segmentCount()];
        for (int at = 0; at < octets.length; at++) {
            octets[at] = (byte) segments.octetAt(at + 1);
        }
        return octets;
    }

    /** An image as four bytes a pixel, which is {@code Image_To_RGBA}. */
    private static byte[] pixelsOf(ImageValue picture) {
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

    /**
     * A decimal or a percent from whatever was offered, which is one switch in
     * {@code T_Decimal} serving both.
     *
     * <p>The two part company only at the end, and only for some of the
     * sources. A number-like one reaches {@code setDec} and is taken as the
     * value itself, so {@code to percent! 4} is 400%. The rest fall through
     * {@code if (type == REB_PERCENT) d1 /= 100.0} and are taken as a count of
     * hundredths, so ten hours is 36,000 seconds and 36000% rather than a
     * hundred times that. Which group a source belongs to is not guessable and
     * is read off the {@code goto} it ends on.
     *
     * <p>A logic is MAKE's alone. Only a plain string is read as text -- a
     * file, a tag or a url is refused, which is the difference between
     * {@code case REB_STRING} and {@code ANY_STR}, and JEBOL holds all of them
     * in the same class so the datatype has to be asked.
     */
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
                    (double) clock.nanoseconds() / NANOSECONDS_A_SECOND);
            case DateValue moment -> asHundredths(wanted, secondsSinceTheEpoch(moment));
            case BinaryValue bits -> asHundredths(wanted,
                    Double.longBitsToDouble(bitsOf(bits)));
            case StringValue text when text.datatype() == Datatype.STRING ->
                    asHundredths(wanted, decimalReadFrom(text, wanted));
            case BlockValue parts -> asHundredths(wanted, mantissaTimesTenTo(parts, wanted));
            default -> raiseBadMakeArg(value, wanted.literalSpelling());
        };
    }

    /** A number taken as the value itself, which is {@code goto setDec}. */
    private static Value asItStands(Datatype wanted, double quantity) {
        return wanted == Datatype.PERCENT
                ? DecimalValue.percent(quantity)
                : DecimalValue.of(quantity);
    }

    /**
     * A number taken as a count of hundredths when a percent was asked for,
     * which is the {@code break} that falls into the division by a hundred.
     * For a decimal the two are the same thing.
     */
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

    /**
     * A block of exactly two read as a mantissa and an exponent, so
     * {@code make decimal! [1 2]} is a hundred and {@code [1 -2]} is a
     * hundredth.
     *
     * <p>The C multiplies and divides by ten in a loop rather than raising a
     * power, and its own comment calls that funky. It is kept because the two
     * do not agree in the last bits, and because the loop stops while the
     * exponent is still between minus one and one -- which quietly truncates a
     * fractional exponent toward zero.
     */
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

    /**
     * A whole number made from whatever was offered.
     *
     * <p>Each source counts as a number in its own way. A time is its
     * seconds and a date is its instant, both from the start of 1970. A
     * binary is one big-endian whole number, so {@code #{01}} is 1 --
     * the opposite of TO DECIMAL!, which reads the same bytes as the raw
     * bits of a double.
     *
     * <p>Anything with no number in it fails as bad-make-arg rather than
     * expect-arg. The distinction is not cosmetic: expect-arg says the
     * caller passed the wrong kind of thing to a function, and a script
     * catching it would be catching a different mistake from the one
     * made here.
     *
     * <p>A logic is the one source only MAKE will read, and the C leaves a
     * note where it refuses TO: no integer is uniquely representative of
     * true, so converting one is a question with no answer, where building
     * one from true is a choice that can be made and is -- one and zero.
     */
    private static Value wholeNumberFrom(Conversion asking, Value value) {
        return switch (value) {
            case IntegerValue whole -> whole;
            case LogicValue truth -> asking.builds()
                    ? IntegerValue.of(truth.truth() ? 1 : 0)
                    : raiseBadMakeArg(value, "integer!");
            case WordValue named when named.datatype() == Datatype.ISSUE ->
                    hexNumberIn(named);
            case StringValue text -> parseInteger(text.text());
            case CharacterValue character -> IntegerValue.of(character.codepoint());
            case BinaryValue bytes -> IntegerValue.of(bitsOf(bytes));
            case DateValue moment -> IntegerValue.of(instantOf(moment));
            case DecimalValue number -> wholeNumberWithinRange(number.quantity());
            case MoneyValue amount -> IntegerValue.of(amount.amount().longValue());
            case TimeValue clock -> IntegerValue.of(clock.nanoseconds() / NANOSECONDS_A_SECOND);
            default -> raiseBadMakeArg(value, "integer!");
        };
    }

    /**
     * A decimal as a whole number, or an overflow where it names none.
     *
     * <p>{@code if (VAL_DECIMAL(val) < MIN_D64 || VAL_DECIMAL(val) >= MAX_D64
     * || isnan(VAL_DECIMAL(val))) Trap0(RE_OVERFLOW);} -- so a not-a-number
     * overflows as surely as an endless one does, and all three are refused
     * before the cast rather than after it. Casting first saturates in
     * silence, which turned an infinity into the largest whole number there
     * is and a not-a-number into nothing at all.
     *
     * <p>The two bounds are not a mirror image and the C's own comparisons say
     * why: below the floor is out and *at* the ceiling is out, so the most
     * negative whole number converts and the most positive does not.
     */
    private static Value wholeNumberWithinRange(double quantity) {
        if (Double.isNaN(quantity)
                || quantity < -TOO_LARGE_FOR_A_WHOLE_NUMBER
                || quantity >= TOO_LARGE_FOR_A_WHOLE_NUMBER) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "no whole number is what " + quantity + " names");
        }
        return IntegerValue.of((long) quantity);
    }

    /**
     * The longest run of hex digits a whole number holds, which is
     * {@code MAX_HEX_LEN}.
     */
    private static final int MOST_HEX_DIGITS = 16;

    /**
     * An issue read as a hexadecimal number, which is what makes
     * {@code to integer! #FF} 255 rather than a refusal.
     *
     * <p>{@code Scan_Hex} says the rule in its own header: it scans while the
     * characters are valid and fails if there are more of them than will fit.
     * So seventeen digits is an error rather than the first sixteen of them,
     * and a character that is not a digit is an error wherever it appears --
     * {@code #-1} fails on the minus before it reaches the one.
     *
     * <p>Sixteen digits fill the number and run past the top of it:
     * {@code #FFFFFFFFFFFFFFFF} is minus one, not an overflow.
     */
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

    /**
     * A date counted in whole seconds from the start of 1970.
     *
     * <p>A date without a time of day counts as its midnight, which is
     * what makes {@code to integer! 1-Jan-2000} a round number of days.
     *
     * <p>A fraction of a second rounds rather than truncating, so
     * {@code 12:46:41.7} is the second after {@code 12:46:41} and not the
     * same one.
     */
    private static long instantOf(DateValue moment) {
        return Math.round(secondsSinceTheEpoch(moment));
    }

    /**
     * The instant a date names, counted in seconds from the start of 1970.
     *
     * <p>The date works out where it sits on the line and this only reads it
     * off in the unit a timestamp is written in.
     */
    private static double secondsSinceTheEpoch(DateValue when) {
        DateValue.Moment moment = when.moment();
        return (double) moment.dayNumber() * (NANOSECONDS_A_DAY / NANOSECONDS_A_SECOND)
                + (double) moment.nanosecondsIntoTheDay() / NANOSECONDS_A_SECOND;
    }

    /**
     * A word of the given spelling, refusing an empty one.
     *
     * <p>A word has to be called something. Building one from empty text
     * used to reach {@link WordValue} and fail there as a Java exception,
     * which is the one thing {@code spec/embed.allium} says a script
     * cannot cause. A real R3 answers too-short.
     */
    private static Value wordNamed(String spelling, Datatype kind) {
        if (spelling.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT,
                    "a " + kind.literalSpelling() + " needs a spelling");
        }
        return WordValue.of(spelling, kind);
    }

    /**
     * A word of the wanted kind, from whatever was handed over.
     *
     * <p>{@code A_TO} in {@code t-word.c}. A word of another kind is
     * simply retyped and keeps its spelling, which is how code builds an
     * assignment it did not spell out. Everything else has to be read.
     *
     * <p>Text is run past the reader and refused unless the whole of it
     * comes back as a single word. Without that check {@code to word! "a
     * b"} builds a word no reader can load again, and the mistake shows
     * up somewhere else entirely: in a file that will not read back.
     */
    private static Value wordFrom(Value value, Datatype kind) {
        if (value instanceof WordValue word) {
            return WordValue.of(word.spelling(), kind);
        }
        if (value instanceof LogicValue truth) {
            return WordValue.of(truth.truth() ? "true" : "false", kind);
        }
        String spelling = switch (value) {
            case CharacterValue letter -> Character.toString(letter.codepoint());
            case StringValue text -> text.text();
            case DatatypeValue named -> named.represents().literalSpelling();
            default -> null;
        };
        if (spelling == null) {
            return raiseWrongArgument(value, "to " + kind.literalSpelling(), "string");
        }
        return WordValue.of(spellingReadAs(spelling, kind), kind);
    }

    /**
     * The spelling a piece of text gives, or a failure.
     *
     * <p>Trailing spaces and tabs are dropped rather than held against
     * it: the reader takes the word and stops, and what is left over is
     * whitespace. Anything else left over means the text was more than
     * one thing, or something other than a word, and neither can be a
     * word's name.
     *
     * <p>An issue takes a laxer rule -- {@code Scan_Issue} rather than
     * {@code Scan_Word} -- which is what lets one hold a version number
     * or a reference with dots and pluses in it.
     */
    private static String spellingReadAs(String text, Datatype kind) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
            end--;
        }
        String trimmed = text.substring(0, end);
        if (trimmed.isEmpty()) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
        }
        if (trimmed.codePoints().anyMatch(letter -> letter < 0x20 || letter == 0x7F)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
        }
        List<Value> read;
        try {
            read = Transcoder.transcode(kind == Datatype.ISSUE ? "#" + trimmed : trimmed)
                    .values()
                    .map(BlockValue::remaining)
                    .orElse(List.of());
        } catch (RuntimeException unreadable) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
        }
        Datatype wanted = kind == Datatype.ISSUE ? Datatype.ISSUE : Datatype.WORD;
        if (read.size() != 1 || !(read.getFirst() instanceof WordValue word)
                || word.datatype() != wanted
                || !word.spelling().equals(trimmed)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
        }
        return word.spelling();
    }

    /**
     * A tuple from one of the five things one can be made of.
     *
     * <p>Five sources and no sixth, taken from {@code A_TO} in
     * {@code t-tuple.c}: another tuple, a string, a block, an issue and a
     * binary. A number is not one of them, which surprises callers more
     * than anything else here.
     *
     * <p>No two of the five agree on length. A string is padded up to
     * three, a block keeps exactly what it holds, a binary longer than
     * twelve is cut short, and an issue longer than twelve raises. The
     * last two sit next to each other in the C and still disagree.
     */
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

    /**
     * A tuple from a block of numbers, keeping exactly what the block held.
     *
     * <p>{@code MT_Tuple}. Each item must be a whole octet already: a
     * number outside 0 to 255 is refused rather than clamped, which is
     * the opposite of what writing through a path does. A decimal rounds
     * half away from zero, so {@code [0.5]} gives 1.
     *
     * <p>Nothing is padded, so {@code [1]} gives a tuple keeping one
     * octet. It shows as 1.0.0 and is not strictly equal to a written
     * 1.0.0, because that one keeps three.
     */
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

    /** One octet of a block being made into a tuple, or a failure. */
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

    /**
     * A tuple from a binary, one octet per byte and never more than twelve.
     *
     * <p>The only over-long source that does not raise: the C clamps the
     * length and reads that many bytes.
     */
    private static Value tupleOfOctets(BinaryValue octets) {
        int width = Math.min(octets.lengthFromHere(), TupleValue.MAXIMUM_SEGMENTS);
        int[] kept = new int[width];
        for (int at = 0; at < width; at++) {
            kept[at] = octets.storage().at(octets.index() + at) & 0xFF;
        }
        return TupleValue.of(kept);
    }

    /**
     * A tuple from an issue, read as pairs of hexadecimal digits.
     *
     * <p>{@code #010203} is 1.2.3. An odd count of digits has a pair with
     * nothing to go in it and raises, and more than twelve pairs raises
     * rather than being cut short.
     */
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

    /**
     * A tuple from a string, which is the one source with a floor of three.
     *
     * <p>{@code Scan_Tuple} counts the dots to decide the length, then
     * raises that length to three whatever it counted. So {@code "1"}
     * gives a tuple keeping three octets and {@code [1]} gives one
     * keeping one, and the two are equal without being the same.
     */
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

    /**
     * The datatype a word names, or a failure if it names none.
     *
     * <p>The word carries its exclamation mark, so {@code 'integer!} is
     * what arrives here and {@code 'integer} is not a datatype.
     */
    private static Value datatypeNamed(WordValue named, Value original) {
        for (Datatype candidate : Datatype.values()) {
            if (candidate.literalSpelling().equalsIgnoreCase(named.spelling())) {
                return DatatypeValue.of(candidate);
            }
        }
        return raiseBadMakeArg(original, "datatype!");
    }

    /**
     * A character made from whatever was offered.
     *
     * <p>A string gives its first character rather than being refused
     * for having more than one, and a decimal truncates. A code point
     * outside what Unicode defines fails as an ACCESS error rather than
     * a script one, which is not where you would look for it.
     */
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

    /**
     * The character a number names, or {@code invalid-char} where it names
     * none.
     *
     * <p>Two ranges name nothing. Below zero and above the last code point is
     * the obvious one. The surrogates are not: {@code D800} to {@code DFFF}
     * are reserved for writing a large code point as a pair and are not
     * characters on their own, so a real Rebol refuses all 2,048 of them.
     *
     * <p>The value class already knew that and said so by throwing, which
     * escaped the interpreter as a Java exception and stopped it dead where a
     * script should have caught an error. Asking before building is what makes
     * it a REBOL error instead.
     */
    private static Value characterAt(double codepoint) {
        long asked = (long) codepoint;
        if (asked < 0 || asked > CharacterValue.MAXIMUM_CODEPOINT
                || isaLoneSurrogate(asked)) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR, IntegerValue.of(asked));
        }
        return CharacterValue.of((int) asked);
    }

    /**
     * Whether a number is one of the 2,048 reserved for writing a large code
     * point as a pair.
     *
     * <p>The range test comes first because narrowing to a char truncates:
     * {@code 0x1D800} would keep only its low half and look like a surrogate
     * when it is an ordinary character well past them.
     */
    private static boolean isaLoneSurrogate(long asked) {
        return asked <= Character.MAX_VALUE && Character.isSurrogate((char) asked);
    }

    /**
     * The character a binary opens with, decoded as UTF-8 where it has to be.
     *
     * <p>{@code t-char.c} tests {@code *bp > 0x80} rather than
     * {@code >= 0x80}, so a lone {@code #{80}} is code point 128 and a lone
     * {@code #{81}} is refused for being a continuation byte with nothing in
     * front of it. Bytes after the first sequence are ignored, which is why
     * this decodes the leading sequence itself instead of handing the whole
     * array to a decoder.
     */
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

    /**
     * The character an issue spells in hexadecimal, as {@code #61} spells
     * {@code #"a"}.
     *
     * <p>The C scans the whole spelling and refuses anything that is not
     * hexadecimal throughout, so {@code #zz} is a bad make argument rather
     * than a character built from the digits it happened to hold.
     */
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

    /** {@code MAX_HEX_LEN} in {@code reb-c.h}. */
    private static final int MOST_HEX_DIGITS_SCANNED = 16;

    /**
     * The datatypes a block names.
     *
     * <p>A block literal holds the words `integer!` and `string!`, not
     * datatype values -- the reader gives words and only evaluation turns
     * them into datatypes. Filtering for datatype values instead found
     * none and built a typeset of nothing that looked like a typeset.
     */
    private static Set<Datatype> datatypesNamedIn(BlockValue named) {
        Set<Datatype> found = EnumSet.noneOf(Datatype.class);
        for (Value item : named.remaining()) {
            if (!namedTypesAddedFrom(item, found)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, Molder.mold(item));
            }
        }
        return found;
    }

    /**
     * Whether an item named any datatype at all, adding whatever it named.
     *
     * <p>An item that names none is an invalid argument, not something to step
     * over. Stepping over it is what made {@code make typeset! [1 2]} into a
     * typeset of nothing that still answered {@code typeset?} -- the emptiness
     * was the only sign anything had gone wrong, and an empty typeset is a
     * thing a caller can legitimately ask for.
     *
     * <p>A word that names nothing counts the same way, which is why the two
     * lookups are asked whether they found something rather than told to add
     * it if they did.
     */
    private static boolean namedTypesAddedFrom(Value item, Set<Datatype> found) {
        if (item instanceof DatatypeValue datatype) {
            found.add(datatype.represents());
            return true;
        }
        if (item instanceof TypesetValue typeset) {
            found.addAll(typeset.members());
            return true;
        }
        if (!(item instanceof WordValue word)) {
            return false;
        }
        String spelling = word.spelling();
        Optional<Datatype> one = Datatype.named(spelling);
        one.ifPresent(found::add);
        Optional<Typeset> family = Typeset.named(spelling.endsWith("!")
                ? spelling.substring(0, spelling.length() - 1)
                : spelling);
        family.ifPresent(members -> found.addAll(members.members()));
        return one.isPresent() || family.isPresent();
    }

    /**
     * A pair made from whatever was offered.
     *
     * <p>A single number fills both halves, so {@code to pair! 5} is
     * {@code 5x5}. A block must hold exactly two numbers: one is refused
     * rather than filled in, and three is refused rather than trimmed. A
     * string goes through the reader, so the text {@code "1x2"} becomes
     * the pair it spells.
     */
    private static Value asPair(Value value) {
        return switch (value) {
            case PairValue pair -> pair;
            case IntegerValue whole -> PairValue.square(whole.magnitude());
            case DecimalValue quantity -> PairValue.square(quantity.quantity());
            case StringValue text -> readPair(text.text());
            case BlockValue block -> pairOf(block.remaining());
            default -> raiseBadMakeArg(value, "pair!");
        };
    }

    /**
     * A money made from whatever was offered.
     *
     * <p>Seven datatypes and no more. A money is handed straight back. An
     * integer, a decimal and a percent become the amount they name, and a
     * percent names its fraction rather than its printed number, so
     * {@code make money! 100%} is $1. A string goes through the reader. A
     * binary is the twelve byte {@code deci} form. A logic is $1 or $0.
     *
     * <p>An issue is refused, and the refusal is a decision rather than a
     * gap: {@code t-money.c} carries the case label commented out with the
     * issue number that removed it. Writing a money in hexadecimal reads like
     * the obvious use for an issue, and Rebol decided against it.
     *
     * <p>A logic is MAKE's alone, which makes money the fourth datatype to
     * draw the line there after integer, decimal and percent. The reason is
     * the same one {@code T_Integer} writes down: no amount is uniquely
     * representative of true, so converting one is a question with no answer,
     * where building a pound from true is a choice that can be made.
     */
    private static Value asMoney(Conversion asking, Value value) {
        return withinTheDeciRange(switch (value) {
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

    /**
     * The amount, or an overflow if it is one a {@code deci} cannot hold.
     *
     * <p>Twenty-six significant digits and a power of ten inside a signed
     * byte. Checked here rather than on construction so that the failure is a
     * REBOL error a script can catch, which is what the C raises.
     */
    private static MoneyValue withinTheDeciRange(MoneyValue amount) {
        if (!amount.isWithinTheDeciRange()) {
            throw Raised.of(EvaluationFailure.OVERFLOW,
                    "a money holds twenty-six digits and a power of ten from -128 to 127");
        }
        return amount;
    }

    /**
     * A money read out of text, which is {@code Scan_Money}: one currency mark
     * is allowed and stripped, and what is left has to be a number.
     *
     * <p>The mark may follow a sign and may not precede one, so {@code "-$1"}
     * reads and {@code "$-1"} does not. Putting a mark on unconditionally made
     * {@code to money! "$1"} into {@code "$$1"}, which lexes as nothing at all.
     *
     * <p>The text is qualified first, the same way a decimal's is, which is
     * what makes the two accept exactly the same characters. Rebol's own suite
     * measures that set for both and gets the same answer twice.
     */
    private static MoneyValue readMoney(String text) {
        String written = qualifiedNumberIn(text, "a money", MOST_FRACTION_CHARACTERS);
        return amountWithoutTheCurrencyMark(written)
                .flatMap(Natives::numberRewrittenForTheJvm)
                .map(plain -> MoneyValue.of(new BigDecimal(plain)))
                .orElseGet(() -> (MoneyValue)
                        raiseBadMakeArg(StringValue.of(text), "money!"));
    }

    /**
     * The amount with its one allowed currency mark taken off, or nothing
     * where the mark is not somewhere a mark may be.
     *
     * <p>A sign comes before the mark and never after it, so {@code "-$1"} is
     * minus a pound and {@code "$-1"} is not a money at all. That is the only
     * asymmetry, and it is why this moves the sign across rather than looking
     * for a mark wherever it happens to sit.
     */
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

    /**
     * What the clock says, in the part NOW was asked for.
     *
     * <p>One reading answers all ten questions, which is why only one of them
     * may be asked: {@code Assert_Max_Refines(ds, D_REF(9) ? 2 : 1); // prevent
     * too many refines like: now/year/month}. /PRECISE is exempt because it
     * says how to read the clock rather than which part to answer.
     *
     * <p>The parts are read from the local date rather than from the instant
     * underneath -- {@code Adjust_Date_Zone(ret, FALSE)} for every part
     * refinement -- so within an hour of midnight the day this answers is the
     * local day and not the UTC one.
     */
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
            return TimeValue.ofNanoseconds(offsetMinutes * 60L * NANOSECONDS_A_SECOND);
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

    /** A moment as a date carrying the time of day and an offset in minutes. */
    private static DateValue dateWithZone(java.time.ZonedDateTime moment, int offsetMinutes) {
        return new DateValue(moment.getYear(), moment.getMonthValue(),
                moment.getDayOfMonth(),
                java.util.Optional.of(
                        TimeValue.ofNanoseconds(moment.toLocalTime().toNanoOfDay())),
                java.util.Optional.of(offsetMinutes));
    }

    /**
     * A map made from whatever MAKE MAP! was given.
     *
     * <p>Five things and no others. A block, a paren or another map hold pairs
     * already: {@code if (!(IS_BLOCK(data) || IS_MAP(data) || IS_PAREN(data)))
     * return FALSE;}. An object is turned into a block of its fields first --
     * {@code Set_Block(arg, Make_Object_Block(...)); goto map_from_block;} --
     * and a number is room rather than content.
     *
     * <p>Everything else is {@code Trap_Make}, which matters: a string walked
     * one character at a time would make {@code make map! "ab"} into a map of
     * a to b, and a caller who passed the wrong thing would never find out.
     */
    private static Value mapMadeFrom(Value given) {
        if (given instanceof IntegerValue || given instanceof DecimalValue) {
            if (Comparison.asDouble(given) < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                        "a map cannot have room for " + Molder.form(given) + " pairs");
            }
            return MapValue.empty();
        }
        List<Value> pairs = switch (given) {
            case MapValue already -> already.flattened();
            case ObjectValue object -> object.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .<Value>mapMulti((slot, accept) -> {
                        accept.accept(WordValue.of(slot.spelling()));
                        accept.accept(slot.value());
                    })
                    .toList();
            case BlockValue block when block.datatype() == Datatype.BLOCK
                    || block.datatype() == Datatype.PAREN -> block.remaining();
            default -> null;
        };
        if (pairs == null) {
            return raiseBadMakeArg(given, "map!");
        }
        if (pairs.size() % 2 != 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a map needs a value for every key, and this has "
                            + pairs.size() + " items");
        }
        return MapValue.of(pairs);
    }

    /**
     * Refuses a set-word or a set-path handed to DO on its own.
     *
     * <p>`case REB_SET_WORD: case REB_SET_PATH: Trap_Arg(value);` -- there is
     * nothing after it to assign, so the caller has handed over half an
     * expression rather than something to evaluate.
     */
    private static Value raiseHalfAnExpression(Value assigning) {
        throw Raised.of(EvaluationFailure.INVALID_ARG,
                Molder.mold(assigning) + " assigns, and there is nothing here to assign");
    }

    /**
     * A number as a byte, refusing one that will not fit.
     *
     * <p>`if (VAL_INT64(arg) < 0 || VAL_INT64(arg) > 255) Trap_Range(arg);`
     * wherever a number reaches a binary. Truncating instead is the silent
     * kind of wrong: writing 300 stored 44 and answered 300, so the caller was
     * told the write had happened as asked.
     */
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

    /** The largest codepoint a character can hold, as MAX_CHAR does. */
    private static final long MAXIMUM_CODEPOINT = 0x10FFFF;

    private static Value raiseBadMakeArg(Value value, String wanted) {
        throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                "cannot make a " + wanted + " out of "
                        + value.datatype().literalSpelling());
    }

    private static Value raiseWrongArgument(Value value, String nativeName, String wanted) {
        throw Raised.of(EvaluationFailure.EXPECT_ARG,
                nativeName + " wanted a " + wanted + ", not a "
                        + value.datatype().literalSpelling());
    }

    /** Whether a question about a pair is true of both its halves. */
    private static boolean bothHalves(PairValue pair, DoublePredicate asked) {
        return asked.test(pair.x()) && asked.test(pair.y());
    }

    /**
     * A pair half as the whole number the bit operations and the parity
     * questions read it as.
     *
     * <p>{@code ROUND_TO_INT} is {@code (REBINT)(floor(d + 0.5))}, so it
     * rounds to the nearest and sends a half upwards rather than away from
     * zero: 2.5 becomes 3 and -2.5 becomes -2. Everything that reads a pair
     * half as an integer goes through it -- AND, OR, XOR, EVEN? and ODD? --
     * so there is one rule here and not five.
     */
    private static long roundedHalfUp(double half) {
        return (long) Math.floor(half + 0.5);
    }

    private static boolean isOdd(long whole) {
        return (whole & 1L) != 0L;
    }

    /**
     * Text made ready for a number scanner, which is {@code Qualify_String}.
     *
     * <p>One run of characters is taken and everything after it must be
     * space or tab, which is how {@code "1 2"} is two values rather than a
     * number that failed to read. What may come before the run is a wider set
     * than what may come after it, and the two tests have different names in
     * the C for that reason.
     *
     * <p>Nothing at all is {@code too-short} rather than a scan that failed,
     * more than twenty-four characters is {@code too-long} before anything
     * tries to read them, and a letter that needs more than one byte is
     * {@code invalid-chars}. Four outcomes a script can tell apart, decided by
     * the text rather than by the caller, and in this order.
     */
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

    /** Above this, a letter takes more than one byte and no number may hold it. */
    private static final char MOST_LETTERS_ARE_ONE_BYTE = 127;

    /**
     * Whitespace as the lexer counts it, which is what a number may be
     * preceded by.
     *
     * <p>{@code IS_LEX_SPACE} asks whether the character has no entry in the
     * lexer's map at all, and the control characters have none. A line feed
     * and a carriage return do have one, so they are the two below space that
     * a number may not sit behind.
     */
    private static boolean isLexicalSpace(char letter) {
        return (letter <= ' ' || letter == MOST_LETTERS_ARE_ONE_BYTE)
                && letter != '\n' && letter != '\r';
    }

    /**
     * Whitespace as {@code IS_SPACE} counts it, which is what may follow a
     * number and is only these two.
     *
     * <p>{@code White_Chars} gives every character below thirty-three the low
     * bit and gives the second bit to space and tab alone, and this is the
     * test that asks for the second. So a number may have a line feed in front
     * of it and not behind it, and Rebol's own suite measures exactly that
     * difference by building every one-character suffix that will go on the
     * end of a "1".
     */
    private static boolean isSpaceOrTab(char letter) {
        return letter == ' ' || letter == '\t';
    }

    /**
     * How long a written number may be, which is one character longer for a
     * whole number than for a fraction.
     *
     * <p>{@code MAX_INT_LEN} is 25 and the decimal arm passes a literal 24, so
     * the two are not the same limit and cannot share a constant. The odd one
     * out matters: {@code "9'223'372'036'854'775'807"} is twenty-five
     * characters with its separators, and reading it as a decimal's limit made
     * the largest whole number there is too long to write down.
     */
    private static final int MOST_WHOLE_NUMBER_CHARACTERS = 25;

    private static final int MOST_FRACTION_CHARACTERS = 24;

    /**
     * {@code Scan_Decimal}'s grammar, once the digit separators are gone.
     *
     * <p>Not the one {@code Double.parseDouble} accepts, which is why the text
     * is matched against this before it is handed over. An exponent may carry
     * no digits at all -- {@code "1e"} is one, because the C copies the E into
     * its buffer and lets {@code strtod} stop there.
     */
    private static final Pattern WRITTEN_DECIMAL = Pattern.compile(
            "[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]*)?");

    /** An exponent with nothing after it, which reads as no exponent at all. */
    private static final Pattern EMPTY_EXPONENT = Pattern.compile("[eE][+-]?$");

    /**
     * A decimal read out of text, which is {@code Scan_Decimal} and not the
     * JVM's own parser.
     *
     * <p>Three differences earn the port. A comma is a decimal point, so
     * {@code "1,5"} is 1.5. An apostrophe is a digit separator and is dropped,
     * so {@code "1'000"} is a thousand. And a trailing percent sign is allowed
     * only when a percent is being read: that is the {@code dec_only} flag,
     * and it is the whole of why {@code to decimal! "50%"} is refused while
     * {@code to percent! "50%"} is fifty percent.
     *
     * <p>Rebol's own suite pins the accepted characters exactly. It builds
     * every one-character suffix {@code to-decimal} will take and asserts the
     * set is tab, space, apostrophe, comma, full stop, the ten digits and the
     * two spellings of E.
     */
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

    /**
     * The same number written the way the JVM's parsers expect it, or nothing
     * where {@code Scan_Decimal}'s grammar does not accept it at all.
     *
     * <p>Two of Rebol's spellings have to be translated rather than merely
     * allowed. An apostrophe separates digits and is dropped. A comma stands
     * in for the decimal point and becomes one, and only the first does --
     * a second comma is then a second point, which the grammar refuses.
     *
     * <p>Shared with money so that the two accept the same characters, which
     * is a thing Rebol's own suite measures separately for each and gets the
     * same answer for twice.
     */
    private static Optional<String> numberRewrittenForTheJvm(String written) {
        String body = written.replace("'", "").replaceFirst(",", ".");
        return WRITTEN_DECIMAL.matcher(body).matches()
                ? Optional.of(EMPTY_EXPONENT.matcher(body).replaceFirst(""))
                : Optional.empty();
    }

    /**
     * The written forms of an endless number and of one that is not a number,
     * which the scanner looks for in the middle of reading digits.
     *
     * <p>Whatever came before the hash is thrown away, which is why
     * {@code "1#INF"} is infinity rather than a failure: the C has already
     * copied those digits into its buffer and abandons them where it meets the
     * hash. Only the sign of the very first character survives.
     */
    private static OptionalDouble endlessNumberIn(String body) {
        int hash = body.indexOf('#');
        if (hash < 0) {
            return OptionalDouble.empty();
        }
        boolean negative = body.charAt(0) == '-';
        String named = body.substring(hash + 1);
        if (named.equalsIgnoreCase("INF")) {
            return OptionalDouble.of(negative
                    ? Double.NEGATIVE_INFINITY
                    : Double.POSITIVE_INFINITY);
        }
        return named.equalsIgnoreCase("NAN")
                ? OptionalDouble.of(Double.NaN)
                : OptionalDouble.empty();
    }

    /**
     * Reading an integer out of text, failing in the three ways R3 does.
     *
     * <p>A quote is a digit separator, so {@code "1'000"} is 1000, and a
     * decimal point truncates toward zero rather than being refused.
     */
    private static Value parseInteger(String text) {
        String withoutSeparators = qualifiedNumberIn(
                text, "an integer", MOST_WHOLE_NUMBER_CHARACTERS).replace("'", "");
        try {
            return IntegerValue.of(Long.parseLong(withoutSeparators));
        } catch (NumberFormatException notAWholeNumber) {
            return truncatedDecimal(withoutSeparators, text);
        }
    }

    /**
     * Text with a decimal point in it, read as a whole number by throwing the
     * fraction away, and refused otherwise.
     *
     * <p>The point has to be there. {@code Scan_Integer} fails on a whole
     * number too large for a machine word and the decimal scan that follows
     * needs a point, so {@code to integer! "1e5"} is refused while
     * {@code to integer! "1.5e3"} is 1500. Odd, and it is the rule.
     *
     * <p>Refusing a number outside the range rather than saturating at the
     * largest one is the point of the check. Saturating gives a result that is
     * a number, is in range, and is not the number the text said -- which is
     * how {@code to integer! "11111111111111111111111"} used to answer the
     * largest whole number instead of raising.
     */
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

    /**
     * Where a double stops fitting in a signed machine word.
     *
     * <p>Written as a double so the comparison is one a double can answer:
     * casting the other way saturates silently, which is the behaviour being
     * guarded against.
     */
    private static final double TOO_LARGE_FOR_A_WHOLE_NUMBER = 9.223372036854776E18;

    /**
     * The whole number EVEN? and ODD? are really being asked about.
     *
     * <p>A decimal rounds half away from zero, the same rule ROUND uses,
     * so 1.5 is even and 2.5 is odd. Truncating instead agrees on every
     * whole decimal and disagrees on every half, which makes it the
     * dangerous wrong answer rather than the obvious one.
     *
     * <p>A money truncates rather than rounding, because {@code A_EVENQ} in
     * {@code t-money.c} reads it through {@code deci_to_int} and that throws
     * the fraction away. So the two datatypes disagree on a half, and each
     * follows its own C.
     */
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

    /**
     * How many items a /part count asked for, if it asked.
     *
     * <p>The count sits at a different place in each native's argument
     * list, so the caller says where rather than this guessing: APPEND
     * takes a value before it and REMOVE does not.
     */
    private static Optional<Long> howManyWanted(
            List<Value> arguments, Set<String> refinements, int where) {
        return howManyWanted(NoneValue.none(), arguments, refinements, where);
    }

    /**
     * How much /PART asked for, counted from the value being read.
     *
     * <p>/PART takes a position as readily as a count, and then means "up to
     * here". {@code Partial1} decides it in three lines, and the last one is
     * the reason the source has to be passed in:
     *
     * <pre>
     * if (is_ser &amp;&amp; VAL_TYPE(sval) == VAL_TYPE(lval) &amp;&amp; VAL_SERIES(sval) == VAL_SERIES(lval))
     *     len = (REBINT)VAL_INDEX(lval) - (REBINT)VAL_INDEX(sval);
     * else
     *     Trap1(RE_INVALID_PART, lval);
     * </pre>
     *
     * <p>The position must be into the same series, so the difference of the
     * two indexes is a length. A position into some other series names no
     * length at all and is refused rather than guessed at.
     *
     * <p>Rebol's own JSON codec copies a matched run this way:
     * {@code mark1: some normal-chars mark2: (append/part output mark1 mark2)}.
     * Refusing a string where a count was declared stopped TO-JSON on every
     * string it was given.
     */
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

    /**
     * How much of a block /PART asked for, counting either way.
     *
     * <p>A positive count takes that many from the position forward. A
     * negative one takes that many from the position backward, which is
     * how `append/part obj tail [a 1 b 2] -2` reads the last pair: from
     * the tail there is nothing ahead, so counting forward answers
     * nothing at all.
     */
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

    /**
     * Putting key and value pairs into a map, which is what APPEND and INSERT
     * both do to one.
     *
     * <p>The checks run in the order the C runs them, and the C says why on
     * the line above the first: "Check must be in this order (to avoid
     * checking a non-series value)". So a protected map refuses a call before
     * anyone looks at what the call was trying to add.
     */
    private static Value addPairsToMap(
            MapValue map, List<Value> arguments,
            Set<String> refinements, String nativeName) {

        requireChangeable(map);
        if (!(arguments.get(1) instanceof BlockValue pairs)
                || pairs.datatype() != Datatype.BLOCK) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    nativeName + " puts pairs into a map, and needs a block of them, "
                            + "not a " + arguments.get(1).datatype().literalSpelling());
        }
        if (refinements.contains("dup")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    nativeName + "/dup means nothing for a map, where adding a key "
                            + "twice over leaves one key");
        }
        List<Value> wanted = pairsWantedBy(pairs, arguments, refinements);
        for (int at = 0; at + 1 < wanted.size(); at += 2) {
            map.put(wanted.get(at), wanted.get(at + 1));
        }
        return map;
    }

    /**
     * Which of a block's values a map is being asked to take.
     *
     * <p>/PART counts pairs rather than values, and the halving is one line:
     * {@code len >>= 1; // part must be number of key/value pairs}. So a /PART
     * of one asks for half a pair and gets nothing, and a /PART of three adds
     * as much as a /PART of two.
     *
     * <p>An odd count loses its last value for the same reason the loop drops
     * a trailing key: {@code NOT_END(val) && NOT_END(val+1)} needs both halves
     * of a pair before it will take a step.
     */
    private static List<Value> pairsWantedBy(
            BlockValue pairs, List<Value> arguments, Set<String> refinements) {

        List<Value> whole = pairs.head().remaining();
        int here = pairs.index() - 1;
        long asked = howManyWanted(pairs, arguments, refinements, 2)
                .orElse((long) (whole.size() - here));
        int from = here;
        int count;
        if (asked >= 0) {
            count = (int) Math.min(asked, whole.size() - here);
        } else {
            count = (int) Math.min(-asked, here);
            from = here - count;
        }
        count -= count % 2;
        return whole.subList(from, from + count);
    }

    private static String hexOfEachSegment(TupleValue tuple) {
        StringBuilder hex = new StringBuilder();
        for (int segment : tuple.segments()) {
            hex.append("%02X".formatted(segment));
        }
        for (int padded = tuple.segmentCount();
                padded < TupleValue.MINIMUM_SHOWN_SEGMENTS; padded++) {
            hex.append("00");
        }
        return hex.toString();
    }

    private static String hexSizedToItsMagnitude(
            int codepoint, java.util.OptionalInt width) {
        int digits = codepoint <= 0xFF ? 2
                : codepoint <= 0xFFFF ? 4
                : codepoint <= 0xFFFFFF ? 6
                : 8;
        return trimmedToWidth("%016X".formatted((long) codepoint),
                width.orElse(digits));
    }

    private static String hexSixteenWide(long magnitude, java.util.OptionalInt width) {
        String hex = "%016X".formatted(magnitude);
        return width.isPresent() ? trimmedToWidth(hex, width.getAsInt()) : hex;
    }

    private static String trimmedToWidth(String hex, int width) {
        return hex.substring(Math.max(0, hex.length() - width));
    }

    private static byte[] boundedByAnyPart(
            byte[] octets, List<Value> arguments, Set<String> refinements) {
        return howManyWanted(arguments.getFirst(), arguments, refinements, 2)
                .map(count -> java.util.Arrays.copyOf(octets,
                        (int) Math.max(0, Math.min(count, octets.length))))
                .orElse(octets);
    }

    private static String boundedTextByAnyPart(
            String text, List<Value> arguments, Set<String> refinements) {
        return howManyWanted(arguments.getFirst(), arguments, refinements, 2)
                .map(count -> text.substring(0,
                        (int) Math.max(0, Math.min(count, text.length()))))
                .orElse(text);
    }

    private static boolean isExactlyABlock(Value value) {
        return value instanceof BlockValue && value.datatype() == Datatype.BLOCK;
    }

    private static Value unbound(Value value, boolean deeply) {
        if (value instanceof WordValue word) {
            return WordValue.of(word.spelling(), word.datatype());
        }
        if (value instanceof BlockValue block) {
            List<Value> loosened = block.remaining().stream()
                    .map(item -> deeply || item instanceof WordValue
                            ? unbound(item, deeply)
                            : item)
                    .toList();
            return new BlockValue(new BlockStorage(loosened), 1, block.datatype());
        }
        return value;
    }

    private static boolean isExactlyAString(Value value) {
        return value instanceof StringValue && value.datatype() == Datatype.STRING;
    }

    private static Value membersCleared(
            BitsetValue members, List<Value> arguments, Set<String> refinements) {
        requireChangeable(members);
        if (refinements.contains("key") && refinements.contains("part")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    "/key and /part each say what to remove, and only one can");
        }
        if (refinements.contains("key")) {
            members.clearAllDirectly((BitsetValue) bitsMeantBy(argumentFor(
                    "key", List.of("part", "key"), arguments, refinements, 1)));
            return members;
        }
        if (refinements.contains("part")) {
            Value range = argumentFor(
                    "part", List.of("part", "key"), arguments, refinements, 1);
            if (!(range instanceof BlockValue || range instanceof BinaryValue
                    || range instanceof CharacterValue
                    || isExactlyAString(range))) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        Molder.mold(range) + " names no range of members");
            }
            members.clearAllDirectly((BitsetValue) bitsMeantBy(range));
            return members;
        }
        throw Raised.of(EvaluationFailure.MISSING_ARG,
                "/key or /part must say what to remove from the set");
    }

    private static Value objectGainingFields(
            ObjectValue object, List<Value> arguments,
            Set<String> refinements, String verb) {
        if (object.context().isClosedToNewNames()) {
            throw Raised.of(EvaluationFailure.PROTECTED, verb);
        }
        refuseHiddenField(object, arguments.get(1));
        if (arguments.get(1) instanceof WordValue only) {
            object.context().set(only.canonical(), UnsetValue.unset());
            return object;
        }
        List<Value> pairs = duplicated(arguments.get(1), arguments, refinements)
                instanceof BlockValue added
                ? partOf(added, arguments, refinements)
                : List.of(arguments.get(1));
        for (int at = 0; at + 1 < pairs.size(); at += 2) {
            if (pairs.get(at) instanceof WordValue field) {
                object.context().set(field.canonical(), pairs.get(at + 1));
            }
        }
        return object;
    }

    private static List<Value> firstFew(
            Value source, List<Value> items, List<Value> arguments,
            Set<String> refinements, int where) {
        return howManyWanted(source, arguments, refinements, where)
                .map(count -> items.subList(0,
                        (int) Math.max(0, Math.min(count, items.size()))))
                .orElse(items);
    }

    /**
     * A value as text with nothing between its parts, which is what
     * TO-STRING means and FORM does not.
     *
     * <p>The two agree on every value that is not a series, which is how
     * they came to be conflated here: {@code to-string [1 2 3]} is "123"
     * and {@code form [1 2 3]} is "1 2 3". Nesting makes no difference to
     * the running together, so {@code to-string [1 [2 3]]} is also "123".
     *
     * <p>A path keeps its slashes, because it is a block underneath and the
     * block arm would otherwise run its segments together: {@code a/b} is
     * {@code "a/b"} and not {@code "ab"}.
     *
     * <p>A tag keeps its brackets here. This is FORM, and AJOIN and COMBINE
     * both come through it -- Rebol's own suite asserts
     * {@code ajoin [<a> "b" 3]} is {@code "<a>b3"}. Only TO STRING! takes the
     * brackets off, and it does that in its own arm rather than here.
     */
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

    /**
     * The text {@code make_string} takes from a value, which is not quite what
     * FORM gives.
     *
     * <p>{@code ANY_STR(arg)} is copied as it stands, and that one arm is the
     * whole of the difference: {@code to string! <tag>} is {@code "tag"} where
     * {@code form <tag>} keeps the brackets. Everything else falls through to
     * {@code Form_Value} with the TIGHT option, which is what runs a block's
     * items together.
     */
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
                        return readFromPort(port, evaluator);
                    }
                    if (routesToAScheme(arguments.getFirst())) {
                        throw schemeRefusal("reads", arguments.getFirst());
                    }
                    requireService(HostService.FILES);
                    return throughPort(() -> FileReading
                            .asAskedFor(arguments, refinements)
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
                        return writeToPort(port, arguments.get(1), evaluator);
                    }
                    if (routesToAScheme(arguments.getFirst())) {
                        throw schemeRefusal("writes", arguments.getFirst());
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
                        ((StringValue) arguments.getFirst()).text()
                                .replace(localFileSeparator, '/'),
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

        define("get-env", List.of(Parameter.required("name", Set.of(Datatype.STRING))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.ENVIRONMENT);
                    return throughPort(() -> {
                        String held = evaluator.environment().valueOf(
                                ((StringValue) arguments.getFirst()).text());
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
                        Parameter.required("name", Set.of(Datatype.STRING)),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    throw Raised.of(EvaluationFailure.NO_SERVICE,
                            "set-env is " + ServiceRefusal.NOT_PRESENT.name()
                                    .toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                                    + ": a JVM cannot change its own environment");
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
                    return throughPort(() -> {
                        evaluator.files().changeDirectory(
                                ((StringValue) arguments.getFirst()).text());
                        return arguments.getFirst();
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

        define("delete", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> {
                        evaluator.files().delete(((StringValue) arguments.getFirst()).text());
                        return arguments.getFirst();
                    });
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
                    return throughPort(() -> {
                        evaluator.files().rename(
                                ((StringValue) arguments.getFirst()).text(),
                                ((StringValue) arguments.get(1)).text());
                        return arguments.get(1);
                    });
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
                    if (!(target instanceof StringValue named) || named.text().isEmpty()) {
                        return LogicValue.of(false);
                    }
                    if (refinements.contains("check")
                            && target.datatype() == Datatype.FILE
                            && liesOnTheDiskAsADirectory(evaluator, named.text())) {
                        return LogicValue.of(true);
                    }
                    return LogicValue.of(endsTheWayADirectoryIsWritten(named.text()));
                });

        define("set-scheme", List.of(
                        Parameter.required("scheme", Set.of(Datatype.OBJECT))),
                (arguments, evaluator, context) -> {
                    ObjectValue scheme = (ObjectValue) arguments.getFirst();
                    Value named = scheme.context().holds("name")
                            ? scheme.context().ownSlotFor("name").value()
                            : NoneValue.none();
                    if (!(named instanceof WordValue name)
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
                    Value built = evaluator.applyFunction(
                            systemInternalFunction(context, "make-port*"),
                            List.of(arguments.getFirst()));
                    if (!(built instanceof PortValue port)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "nothing knows how to open that");
                    }
                    requireServiceForScheme(port.schemeName());
                    if (port.schemeName().equals("tcp")) {
                        connectTheTcpPort(port, evaluator);
                    }
                    port.markOpen(true);
                    return port;
                });

        define("update", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> NoneValue.none());

        define("flush", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    evaluator.output().flush();
                    return arguments.getFirst();
                });

        define("open?", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> LogicValue.of(
                        ((PortValue) arguments.getFirst()).isOpen()));

        define("close", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> {
                    PortValue port = (PortValue) arguments.getFirst();
                    port.markOpen(false);
                    return port;
                });

        define("modify", List.of(
                        Parameter.required("target", Set.of(Datatype.PORT, Datatype.FILE)),
                        Parameter.required("field", Set.of(Datatype.WORD, Datatype.NONE)),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
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
                    if (target instanceof PortValue || routesToAScheme(target)) {
                        throw schemeRefusal("queries", target);
                    }
                    requireService(HostService.FILES);
                    return throughPort(() -> queryAnswerFor(
                            evaluator.files().informationAbout(
                                    ((StringValue) target).text()),
                            field));
                });
    }

    /**
     * QUERY's answer, in the shape the question was asked.
     *
     * <p>{@code Ret_Query_File} has three branches and the middle one is the
     * one nobody guesses. A word asks for one fact and gets it bare. None asks
     * for everything and gets an object. A block asks for several and gets a
     * block -- and whether each fact is labelled depends on how its word was
     * written, per word rather than per block: a plain word puts itself in the
     * answer as a set-word before its value, a get-word contributes the value
     * alone.
     *
     * <p>So {@code query %a [type size]} is {@code [type: file size: 5]} and
     * {@code query %a [:type :size]} is {@code [file 5]}. Rebol's own LIST-DIR
     * asks the second form and reads the answer by position, so reading the
     * block as a plain list of field names breaks it.
     *
     * <p>A path with nothing at it answers none whichever shape was asked,
     * because "there is nothing there" is an answer a script acts on.
     */
    private static Value queryAnswerFor(
            java.util.Optional<FileInformation> found, Value field) {

        if (found.isEmpty()) {
            return NoneValue.none();
        }
        FileInformation about = found.get();
        if (field instanceof BlockValue wanted) {
            List<Value> answer = new ArrayList<>();
            for (Value item : wanted.remaining()) {
                if (!(item instanceof WordValue named)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG,
                            "a query field is a word, not "
                                    + item.datatype().literalSpelling());
                }
                if (named.datatype() != Datatype.GET_WORD) {
                    answer.add(named.as(Datatype.SET_WORD));
                }
                answer.add(queryFieldOf(about, named));
            }
            return BlockValue.block(answer);
        }
        if (field instanceof WordValue named) {
            return queryFieldOf(about, named);
        }
        return everythingKnownAbout(about);
    }

    /** The seven field names {@code Set_File_Mode_Value} answers, and no others. */
    private static Value queryFieldOf(FileInformation about, WordValue named) {
        return switch (named.canonical()) {
            case "size" -> about.size().<Value>map(IntegerValue::of).orElseGet(NoneValue::none);
            case "type" -> WordValue.of(about.isDirectory() ? "dir" : "file");
            case "date", "modified" -> asDateValue(about.modified());
            case "accessed" -> asDateValue(about.accessed());
            case "created" -> asDateValue(about.created());
            case "name" -> StringValue.of(about.name(), Datatype.FILE);
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    named.spelling() + " is not a field a file has");
        };
    }

    /** All six facts as an object, which is what a field of none asks for. */
    private static Value everythingKnownAbout(FileInformation about) {
        Context fields = Context.root();
        fields.set("name", StringValue.of(about.name(), Datatype.FILE));
        fields.set("size", about.size().<Value>map(IntegerValue::of).orElseGet(NoneValue::none));
        fields.set("type", WordValue.of(about.isDirectory() ? "dir" : "file"));
        fields.set("modified", asDateValue(about.modified()));
        fields.set("date", asDateValue(about.modified()));
        fields.set("accessed", asDateValue(about.accessed()));
        fields.set("created", asDateValue(about.created()));
        return new ObjectValue(fields);
    }

    /** A moment as a date, or none where the host could not say. */
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
                    outputKindOf(errors), fileOf(errors, evaluator));
        }

        private static ProcessPort.ProgramInput inputKindOf(Optional<Value> redirection) {
            if (redirection.isEmpty()) {
                return ProcessPort.ProgramInput.THE_HOSTS_OWN;
            }
            return switch (redirection.orElseThrow()) {
                case BinaryValue piped -> ProcessPort.ProgramInput.SUPPLIED_BYTES;
                case StringValue text when text.datatype() == Datatype.STRING ->
                        ProcessPort.ProgramInput.SUPPLIED_BYTES;
                case StringValue named -> ProcessPort.ProgramInput.A_FILES_CONTENTS;
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
                case StringValue named -> ProcessPort.ProgramOutput.INTO_A_FILE;
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
            if (path.startsWith("/")) {
                return path;
            }
            try {
                return evaluator.files().workingDirectory() + path;
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

    /**
     * READ on a port, sent to the port's actor.
     *
     * <p>Only the console actor exists. It answers one line, which is what
     * {@code Console_Actor}'s {@code A_READ} does once the read-line mode is
     * set, and INPUT sets that mode with MODIFY before it reads.
     *
     * <p>A line that is not there answers none, as the C does, and a script
     * must be able to tell that from an empty line.
     */
    private Value readFromPort(PortValue port, Evaluator evaluator) {
        return switch (port.schemeName()) {
            case "console" -> lineReadFromTheConsole(evaluator);
            case "tcp" -> bytesReadFromTheConnection(port);
            case "dns" -> addressesOfTheNameThePortNames(port, evaluator);
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

    /**
     * The bytes that have arrived on a connection.
     *
     * <p>An empty binary means the other end has finished and closed, which
     * is how a reader knows to stop rather than waiting for ever.
     */
    private Value bytesReadFromTheConnection(PortValue port) {
        requireService(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughNetwork(() -> BinaryValue.of(unsignedOctets(connection.read())));
    }

    /**
     * The addresses a name stands for, or none.
     *
     * <p>None rather than a failure, because "there is no such host" is a
     * true answer a script has to act on and one it will meet often. The
     * refusal is for the service not being granted, and it has already
     * happened by here.
     */
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

    /**
     * The context behind a value IN will look a word up in.
     *
     * <p>An object, an error and a port are all a context underneath, which is
     * why the C reads {@code IS_ERROR(val) ? VAL_ERR_OBJECT(val) :
     * VAL_OBJ_FRAME(val)} and why its comment on the argument says "object,
     * error, port, block".
     */
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

    /**
     * An error as a context, so that IN can look a field up in one.
     *
     * <p>An error is read like an object and is not built as one, thus its
     * fields are gathered here rather than held in a context of their own.
     */
    private static Context contextOfError(ErrorValue raised) {
        Context fields = Context.root();
        for (String name : ErrorValue.FIELDS) {
            raised.field(name).ifPresent(value -> fields.set(name, value));
        }
        return fields;
    }

    /**
     * The first object in a block that holds the wanted word, as that word
     * bound to it, or none.
     *
     * <p>The C reads each item through {@code Get_Simple_Value}, thus a word
     * naming an object counts as the object. This is how code asks a list of
     * objects which of them owns a field.
     */
    private static Value firstHolderIn(
            BlockValue searched, Value wanted, Evaluator evaluator, Context context) {

        if (!(wanted instanceof WordValue word) || wanted instanceof BlockValue) {
            return raiseWrongArgument(wanted, "in", "word");
        }
        for (Value item : searched.remaining()) {
            Value resolved = item instanceof WordValue named && named.isBound()
                    ? evaluator.evaluateOrRaise(BlockValue.block(List.of(named)), context)
                    : item;
            if (resolved instanceof ObjectValue object
                    && object.context().holds(word.canonical())) {
                return word.boundTo(object.context());
            }
        }
        return NoneValue.none();
    }

    /**
     * ASSERT/TYPE: word and datatype in pairs, each word's value checked.
     *
     * <p>A different function under the same name. Plain ASSERT evaluates the
     * block and refuses a false result; /TYPE reads the block as pairs and
     * never evaluates it as code. The C splits on the refinement before it
     * looks at the block at all.
     *
     * <p>The refinement was declared and ignored, so `assert/type [x string!]`
     * evaluated `x string!`, saw a datatype at the end and passed. Every
     * caller of it was unguarded, MAKE-MODULE*'s header check among them:
     * that is eight fields it is supposed to reject and did not.
     */
    private static Value assertedTypes(
            BlockValue pairs, Evaluator evaluator, Context context) {

        List<Value> items = pairs.remaining();
        for (int at = 0; at < items.size(); at += 2) {
            Value named = items.get(at);
            Value held = switch (named) {
                case WordValue word when word.datatype() == Datatype.WORD ->
                        evaluator.evaluateOrRaise(
                                BlockValue.block(List.of(named)), context);
                case BlockValue path when path.datatype() == Datatype.PATH ->
                        evaluator.evaluateOrRaise(
                                BlockValue.block(List.of(named)), context);
                default -> raiseWrongArgument(named, "assert/type", "word or path");
            };
            if (at + 1 >= items.size()) {
                throw Raised.of(EvaluationFailure.NO_ARG, "assert/type wants a type");
            }
            if (!isOfType(held, items.get(at + 1), context)) {
                throw Raised.of(EvaluationFailure.WRONG_TYPE, Molder.mold(named));
            }
        }
        return LogicValue.of(true);
    }

    /**
     * Whether a value is of a type, named as a datatype, a word, a block of
     * either, or a typeset.
     *
     * <p>{@code Is_Of_Type} in the C, and the four spellings are what
     * ASSERT/TYPE's block may hold:
     * {@code if (IS_BLOCK(type) || IS_WORD(type) || IS_TYPESET(type) || IS_DATATYPE(type))}.
     */
    private static boolean isOfType(Value held, Value type, Context context) {
        return switch (type) {
            case DatatypeValue named -> held.datatype() == named.represents();
            case TypesetValue set -> set.holds(held.datatype());
            case WordValue named -> {
                Value resolved = context.knows(named.canonical())
                        ? context.slotFor(named.canonical()).value()
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

    /** What a refinement's own slot can hold: none unasked, a logic asked. */
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

    /**
     * A spec block built from a parameter list, as SPEC-OF answers it.
     *
     * <p>The shape R3 writes: a word per argument, a block of datatype words
     * after one that is narrowed, and a refinement written as a refinement
     * with its own argument following. No doc strings, because a native's
     * documentation lives in its Java comment rather than in the spec, and
     * inventing one here would make `first spec-of` answer a string that
     * says nothing.
     */
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

    /** Where a bound word lives, or null when it is unbound. */
    private static Context boundContextOf(WordValue named) {
        return named.isBound() ? named.binding() : null;
    }

    /**
     * A binary read as UTF-8 text, refusing bytes that are not valid.
     *
     * <p>`Decode_UTF_String` in the C, and the refusal matters as much as the
     * decoding: `if (!ser) Trap1(RE_INVALID_UTF, arg)`. Bytes that are not
     * text have no text form, and answering the replacement character instead
     * would make a round trip through a binary lossy without saying so.
     */
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
        try {
            return strictly.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException notText) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, "binary");
        }
    }

    /**
     * Bytes read as whatever their byte order mark says, with the mark
     * dropped.
     *
     * <p>Text that arrives from a file or a wire is as likely to be UTF-16 as
     * UTF-8, and the mark is what says which. Reading it as UTF-8 regardless
     * turned every such file into a refusal, which is what
     * {@code issue-2186} in Rebol's own tests is about.
     *
     * <p>The four-byte marks have to be tested before the two-byte ones they
     * begin with: {@code FF FE 00 00} is UTF-32 little-endian and its first
     * two bytes are the UTF-16 little-endian mark, so the wrong order reads a
     * UTF-32 file as UTF-16 and finds a null after every character.
     */
    private static String textBehindTheMark(byte[] bytes, int marked) {
        java.nio.charset.Charset named = switch (marked) {
            case 8 -> StandardCharsets.UTF_8;
            case 16 -> StandardCharsets.UTF_16BE;
            case -16 -> StandardCharsets.UTF_16LE;
            case 32 -> java.nio.charset.Charset.forName("UTF-32BE");
            default -> java.nio.charset.Charset.forName("UTF-32LE");
        };
        int width = Math.abs(marked) == 8 ? 3 : Math.abs(marked) / 8;
        try {
            return named.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, width, bytes.length - width))
                    .toString();
        } catch (java.nio.charset.CharacterCodingException notText) {
            throw Raised.of(EvaluationFailure.INVALID_UTF, "binary");
        }
    }

    /**
     * Which encoding a byte order mark names, negative for little-endian.
     *
     * <p>{@code What_UTF} in s-unicode.c. Zero for no mark, which is the
     * ordinary answer: a mark is optional and UTF-8 rarely carries one.
     */
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

    /** The word datatypes, which is the range /AS on COLLECT-WORDS allows. */
    private static final Set<Datatype> ANY_WORD_DATATYPES = Typeset.ANY_WORD.members();

    /**
     * The any-object datatypes, plus whatever else a spec names beside them.
     *
     * <p>`any-object!` in a spec is five datatypes, and writing them out at
     * each site is how one gets left off. `collect-words/ignore` takes
     * `[any-object! block! none!]`.
     */
    private static Set<Datatype> anyObjectOr(Datatype... alsoAccepted) {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.ANY_OBJECT.members());
        accepted.addAll(List.of(alsoAccepted));
        return Set.copyOf(accepted);
    }

    /**
     * What /PART accepts: a count, or a position to read up to.
     *
     * <p>{@code Partial1} takes an integer, a decimal or a series position,
     * and the position form is the one Rebol's own code leans on. Declaring
     * the argument as an integer refused a string before the body ever saw
     * it, so the position form could not be reached at all.
     */
    private static final Set<Datatype> PART_LIMIT = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(Datatype.INTEGER, Datatype.DECIMAL,
                    Datatype.PERCENT, Datatype.PAIR),
            java.util.Arrays.stream(Datatype.values()).filter(Datatype::isSeries))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * {@code [number! series!]}, which is {@link #PART_LIMIT} without a pair.
     *
     * <p>DECOMPRESS and SWAP-ENDIAN declare their limit this way and the
     * functions that share a limit with REMOVE declare a pair alongside it.
     * The difference is a whole datatype's worth of arguments a real Rebol
     * turns away before the body runs.
     */
    private static final Set<Datatype> COUNT_OR_POSITION = PART_LIMIT.stream()
            .filter(accepted -> accepted != Datatype.PAIR)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** CHECKSUM's declared data: {@code [binary! string! file!]}. */
    private static final Set<Datatype> CHECKSUMMABLE =
            Set.of(Datatype.BINARY, Datatype.STRING, Datatype.FILE);

    /** COMPRESS's declared data, which has no file among it. */
    private static final Set<Datatype> COMPRESSIBLE =
            Set.of(Datatype.BINARY, Datatype.STRING);

    /** REMOVE's declared range: {@code [number! series! pair! char!]}. */
    private static final Set<Datatype> REMOVE_RANGE = java.util.stream.Stream.concat(
            PART_LIMIT.stream(), java.util.stream.Stream.of(Datatype.CHAR))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** The declared /dup count: {@code [number! pair!]}. */
    private static final Set<Datatype> DUP_COUNT = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT, Datatype.PAIR);

    /** The three modes a console port has, from Console_Actor's A_MODIFY. */
    private static final Set<String> CONSOLE_MODES = Set.of("echo", "line", "error");

    /**
     * A function the loaded library defines, by name.
     *
     * <p>The seam where Java calls Rebol's own REBOL. Rebol's C has the same
     * seam and uses it in four places: MAKE PORT!, MAKE MODULE!, DO of a file,
     * and the boot. Building a port needs the scheme registry and the URL
     * parser, and both of those are REBOL, thus OPEN cannot do its own work.
     */
    private static Value libraryFunction(Context context, String name) {
        if (!context.knows(name)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, name);
        }
        return context.slotFor(name).value();
    }

    /**
     * Whether the value is an object underneath: an object, a module, a port
     * or an error.
     *
     * <p>All four are one frame of words and values in R3, which is why one
     * arm of {@code t-object.c} answers for all of them. An error is the odd
     * one here only because JEBOL holds its fields in a record rather than in
     * a context.
     */
    private static boolean isAnyObject(Value value) {
        return value instanceof ErrorValue || fieldsOf(value) != null;
    }

    /**
     * Whether an object has a field of this name for FIND and SELECT to
     * report, which is narrower than whether the name can be read through it.
     *
     * <p>Three things make it narrower, each a line of the C rather than a
     * choice made here. Only a plain word asks -- {@code if (IS_WORD(arg))} --
     * so a set-word or a lit-word spelled the same finds nothing. A hidden
     * field is not there: {@code return (!always && VAL_GET_OPT(word,
     * OPTS_HIDE)) ? 0 : n;}. And SELF is not there either, because the search
     * starts one slot past it: {@code word = FRM_WORDS(frame) + 1;}.
     */
    private static boolean objectHasFieldToFind(Value subject, Value wanted) {
        if (!(wanted instanceof WordValue named) || named.datatype() != Datatype.WORD) {
            return false;
        }
        String field = named.canonical();
        if (field.equals("self")) {
            return false;
        }
        if (subject instanceof ErrorValue) {
            return ErrorValue.FIELDS.contains(field);
        }
        return fieldsOf(subject).holds(field);
    }

    /**
     * The fields of anything that is an object underneath, or null.
     *
     * <p>An object, an error, a port and a module all are. {@code types.reb}
     * puts every one of them in the {@code object} typeset, which is what
     * makes SELECT, FIND and IN work the same way on all four.
     */
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

    /**
     * A helper the library keeps to itself, reached through
     * {@code system/contexts/sys}.
     *
     * <p>MAKE-MODULE* and MAKE-PORT* are not standard functions and a script
     * has no business calling either by a bare name. R3 reaches them as
     * {@code sys/make-module*}, which is what loading the sys files into
     * their own context means, so this reads them the same way rather than
     * hoping the library context happens to hold them.
     */
    private static Value systemInternalFunction(Context context, String name) {
        if (!(pathInto(context, "system", "contexts", "sys")
                instanceof ObjectValue internals)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, name);
        }
        if (!internals.context().knows(name)) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, name);
        }
        return internals.context().slotFor(name).value();
    }

    /**
     * Puts what /ARGS carried where a script reads it.
     *
     * <p>`system/script/args`, which is where the vendored `sys/do*` writes it
     * and the only place a script looks: `system/script: make
     * system/standard/script compose [... args: :arg]`.
     */
    private static void recordTheScriptArguments(Evaluator evaluator, Value given) {
        if (pathInto(evaluator.systemContext(), "system", "script")
                instanceof ObjectValue script) {
            script.context().set("args", given);
        }
    }

    /**
     * Where the first byte sequence that is not UTF-8 begins, or -1.
     *
     * <p>{@code UTF8_Check} in {@code s-unicode.c}, which answers {@code acc +
     * 1}: one past the last whole character it accepted. So the position is the
     * START of the sequence that failed rather than the byte that gave it away,
     * and a two-byte sequence with a bad second byte is reported at its lead.
     *
     * <p>An unfinished sequence at the end counts as a failure. The C's loop
     * ends with the decoder part way through a character and the line after it
     * answers the position regardless: `if (state == UTF8_ACCEPT) return 0;`
     * and then `return acc + 1;`.
     *
     * <p>The one thing accepted that well-formed UTF-8 does not allow is a
     * surrogate pair written as two three-byte sequences. The C rejects the
     * first half in the decoder and then gives it a second chance:
     * {@code Decode_Surrogate_Pair} reads all six bytes and lets them through
     * when they are a high half followed by a low one. The C reads those six
     * bytes without checking that they are there; this asks first, because
     * reading past the end is not behaviour worth keeping.
     *
     * <p>Answered as a one-based index into the whole binary, because that is
     * what the caller gets: `VAL_INDEX(arg) = bp - VAL_BIN_HEAD(arg)` measures
     * from the head while the walk began at the position.
     */
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

    /**
     * How many bytes a sequence with this lead byte takes, or 0 for a byte that
     * cannot lead one.
     *
     * <p>The ranges the C's table refuses outright: a continuation byte with
     * nothing in front of it, C0 and C1 -- which could only ever be an overlong
     * spelling of an ASCII character -- and F5 upwards, which would decode
     * above the last codepoint Unicode has.
     */
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

    /**
     * Whether the bytes after the lead one are continuations, and the sequence
     * spells a codepoint that is allowed to be spelled that way.
     *
     * <p>The three exclusions the state machine makes beyond counting
     * continuation bytes: an overlong three-byte or four-byte form, a surrogate
     * (which is refused here and reconsidered as half of a pair), and anything
     * above the top of Unicode.
     */
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

    /** Whether a three-byte sequence spelling a surrogate begins here. */
    private static boolean isSurrogateHalfAt(BinaryValue bytes, int at, int end) {
        if (at + 3 > end || (bytes.storage().at(at) & 0xFF) != 0xED) {
            return false;
        }
        int second = bytes.storage().at(at + 1) & 0xFF;
        int third = bytes.storage().at(at + 2) & 0xFF;
        return second >= 0xA0 && second <= 0xBF && third >= 0x80 && third <= 0xBF;
    }

    /**
     * Whether the surrogate beginning here is a low half, which pairs second.
     *
     * <p>{@code c1 >= 0xD800 && c1 <= 0xDBFF && c2 >= 0xDC00 && c2 <= 0xDFFF}:
     * the high half first and the low one after it. Two of the same kind are
     * not a pair, so the order is part of the test.
     */
    private static boolean isLowSurrogateAt(BinaryValue bytes, int at) {
        return (bytes.storage().at(at + 1) & 0xFF) >= 0xB0;
    }

    /**
     * A REBOL path as the local system writes one, from {@code To_Local_Path}.
     *
     * <p>Two things happen whatever is asked for: the separator changes to the
     * one this system uses, and a run of slashes becomes a single one --
     * {@code if (n == 0 || out[n-1] != OS_DIR_SEP) out[n++] = OS_DIR_SEP;}.
     *
     * <p>The dots are read only when /FULL asked for them. A single dot, alone
     * or with a slash after it, is dropped. A double dot backs out of the
     * directory built so far and leaves a separator behind it, so the answer
     * ends with one.
     *
     * <p>One divergence, and it is the C that is wrong. A segment such as
     * {@code ..x} falls through the double-dot branch into a line that writes
     * the character it looked ahead at and then copies the segment anyway, so
     * the C answers {@code x..x}. Here it is copied as it stands.
     */
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

    /**
     * Past a leading {@code .} or {@code ..} segment, having acted on it.
     *
     * <p>Answers where the segment that follows begins, which is the same
     * place it was given for anything that is not one of those two.
     */
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

    /**
     * Drops the last directory from what has been built, leaving a separator.
     *
     * <p>{@code n -= (n > 2) ? 2 : n;} and then a walk back to the separator
     * before it. The two characters are the trailing separator and the last
     * character of the name, so the walk cannot stop on the separator it
     * started from.
     */
    private static void backOutOneDirectory(StringBuilder built, char separator) {
        int length = built.length() > 2 ? built.length() - 2 : 0;
        while (length > 0 && built.charAt(length) != separator) {
            length--;
        }
        built.setLength(length);
        built.append(separator);
    }

    /**
     * Which of the target's words RESOLVE may write, and where its walk starts.
     *
     * <p>The C keeps this in a bind table both contexts share, and a mark of -1
     * means "named, and the source has not got it". Both shapes of /ONLY are
     * marks, which is why one rule covers them both: a marked word the source
     * lacks is unset in the target, where an unmarked one is left as it was.
     */
    private record WordsToResolve(int startAt, Set<String> named, boolean limited) {

        /** No /ONLY: every word the source has, from the first onwards. */
        static WordsToResolve everything() {
            return new WordsToResolve(1, Set.of(), false);
        }

        boolean allows(String canonical) {
            return !limited || named.contains(canonical);
        }
    }

    /**
     * What /ONLY asked for, in either of its two shapes.
     *
     * <p>A block names the words outright. An integer is a position in the
     * target -- "an index to tail" -- and marks its words from there on, which
     * is how the C resolves the words a binding has just added: it records the
     * context's length, binds, and resolves from that length.
     *
     * <p>{@code if (i == 0) i = 1;} and a position past the end is nothing to
     * do rather than a failure.
     */
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
        if (onlyThese instanceof BlockValue named) {
            return new WordsToResolve(1, named.remaining().stream()
                    .filter(word -> word instanceof WordValue spelled
                            && (spelled.datatype() == Datatype.WORD
                                    || spelled.datatype() == Datatype.SET_WORD))
                    .map(word -> ((WordValue) word).canonical())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    true);
        }
        return WordsToResolve.everything();
    }

    /**
     * Copies one context's values into another, as {@code Resolve_Context}.
     *
     * <p>The C walks the TARGET and asks the bind table where each of its words
     * sits in the source, which is why a word the source has not got is
     * ordinarily left as it was: nothing marked it.
     *
     * <p>A protected slot is skipped without complaint --
     * {@code if (!VAL_PROTECTED(words) && ...)} -- and a hidden source word is
     * not a source of anything: {@code && !VAL_HIDDEN(words)}.
     *
     * <p>/EXTEND then adds the source words the target has not got at all,
     * limited the same way: a word /ONLY did not name was never marked, so the
     * expand loop does not see it either.
     */
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

    /**
     * QUERY on a vector, in the four shapes the field argument can take.
     *
     * <p>Written out rather than sent through {@link #questionedByField},
     * because a vector answers to three names its object does not list --
     * {@code min}, {@code max} and {@code average} -- and because an unknown
     * word inside a block is an invalid argument here where the shared helper
     * calls it something else. Both differences come straight from the C.
     */
    private Value queriedVector(VectorValue vector, Value field, Evaluator evaluator) {
        if (field instanceof NoneValue) {
            return BlockValue.block(
                    VectorQuery.FIELDS.stream().<Value>map(WordValue::of).toList());
        }
        if (field instanceof WordValue named) {
            return VectorQuery.field(vector, named.canonical())
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG, named));
        }
        if (field instanceof BlockValue asked) {
            List<Value> answer = new ArrayList<>();
            for (Value item : asked.remaining()) {
                if (!(item instanceof WordValue named)) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG, item);
                }
                if (named.datatype() != Datatype.GET_WORD) {
                    answer.add(named.as(Datatype.SET_WORD));
                }
                answer.add(VectorQuery.field(vector, named.canonical()).orElseThrow(
                        () -> Raised.of(EvaluationFailure.INVALID_ARG, named)));
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
            java.util.function.Function<String, Value> partOf) {
        return switch (field) {
            case WordValue named when named.canonical().equals("words") ->
                    namesAsWords(partNames);
            case WordValue named -> oneKnownPart(named, partNames, partOf);
            case BlockValue asked -> {
                List<Value> answer = new ArrayList<>();
                for (Value item : asked.remaining()) {
                    if (!(item instanceof WordValue named)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                Molder.mold(item) + " names no part");
                    }
                    if (named.datatype() != Datatype.GET_WORD) {
                        answer.add(named.as(Datatype.SET_WORD));
                    }
                    answer.add(oneKnownPart(named, partNames, partOf));
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

    private static Value oneKnownPart(WordValue named, List<String> partNames,
            java.util.function.Function<String, Value> partOf) {
        if (!partNames.contains(named.canonical())) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "query has no " + named.canonical() + " to answer here");
        }
        return partOf.apply(named.canonical());
    }

    private static boolean routesToAScheme(Value source) {
        return source.datatype() == Datatype.URL
                || source.datatype() == Datatype.BLOCK
                || source instanceof WordValue;
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

    private Value writeToPort(PortValue port, Value data, Evaluator evaluator) {
        return switch (port.schemeName()) {
            case "console" -> writtenToTheConsole(port, data, evaluator);
            case "tcp" -> sentDownTheConnection(port, data);
            default -> throw schemeRefusal("writes", port);
        };
    }

    private Value writtenToTheConsole(
            PortValue port, Value data, Evaluator evaluator) {
        requireService(HostService.CONSOLE);
        evaluator.output().write(Molder.form(data));
        return port;
    }

    /** Sends bytes and answers the port, so writes chain as a caller expects. */
    private Value sentDownTheConnection(PortValue port, Value data) {
        requireService(HostService.NETWORK);
        NetworkPort.Connection connection = connectionBehind(port);
        return throughNetwork(() -> {
            connection.write(octetsOf(data));
            return port;
        });
    }

    private static Set<Datatype> asTypeOrExample() {
        Set<Datatype> accepted = EnumSet.of(Datatype.DATATYPE);
        accepted.addAll(Typeset.ANY_BLOCK.members());
        accepted.addAll(Typeset.ANY_STRING.members());
        return Set.copyOf(accepted);
    }

    /**
     * What COPY will duplicate: the eight datatypes its spec names.
     *
     * <p>{@code value [series! port! map! object! bitset! any-function! error!
     * struct!]}. Declared rather than left open, because the list is what decides
     * the error: a gob is not on it, so `copy make gob! []` is the wrong argument
     * rather than an operation a gob does not support. STRUCT is absent here for
     * the reason it is absent everywhere -- the datatype belongs to a build with
     * the FFI in it.
     */
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

    /**
     * What INDEX? and INDEXZ? accept: `series! gob! port! none!`.
     *
     * <p>Declaring it matters, because the declaration and the arm refuse
     * differently and a script can tell them apart. An integer is not on the
     * list at all, so it never reaches an arm: `indexz? 5` is `expect-arg`,
     * which the corpus confirms against 3.22.1. NONE is on the list, so it does
     * reach one -- and the none arm answers for INDEX? and falls through to
     * `Trap_Action` for INDEXZ?, which is `cannot-use`.
     *
     * <p>GOB is here now that the datatype is, and it has to be named rather than
     * arrive with the series: `boot/types.reb` gives a gob no typeset, so
     * `series? make gob! []` is false while `index?` still answers.
     */
    private static Set<Datatype> positionable() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.SERIES.members());
        accepted.add(Datatype.PORT);
        accepted.add(Datatype.NONE);
        accepted.add(Datatype.GOB);
        return Set.copyOf(accepted);
    }

    /** Walks a path of field names, answering none where any of them is absent. */
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

    /**
     * MAKE MODULE!, which asks the library to do the work.
     *
     * <p>MAKE-MODULE* answers none for a header it will not accept, and none
     * is not a module. Answering it would hand the caller a value of the
     * wrong datatype and no reason why, so this raises invalid-spec about the
     * spec, which is what {@code Make_Module} does:
     * {@code if (IS_NONE(value)) Trap1(RE_INVALID_SPEC, spec);}
     */
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

    /** TO MODULE!, which joins a header object and a words object. */
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

    /**
     * Refuses a scheme whose service the host did not grant.
     *
     * <p>A port is a way out of the interpreter, thus opening one asks the
     * same question every other host call asks. The scheme names which
     * service: console for a console port, files for a file port.
     */
    private void requireServiceForScheme(String scheme) {
        switch (scheme) {
            case "console" -> requireService(HostService.CONSOLE);
            case "file", "dir" -> requireService(HostService.FILES);
            case "tcp", "dns" -> requireService(HostService.NETWORK);
            case "event" -> requireService(HostService.WINDOWS);
            default -> {
                throw Raised.of(EvaluationFailure.NO_SERVICE,
                        scheme.isEmpty()
                                ? "that port has no scheme"
                                : "nothing here serves the " + scheme + " scheme");
            }
        }
    }

    private record FileReading(
            String path,
            Optional<Long> bound,
            Optional<Long> position,
            boolean answersText,
            boolean answersLines) {

        private static final List<String> ARGUMENT_ORDER = List.of("part", "seek");

        static FileReading asAskedFor(List<Value> arguments, Set<String> refinements) {
            return new FileReading(
                    ((StringValue) arguments.getFirst()).text(),
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
                    : Optional.of((long) asMagnitude(asked));
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

        private byte[] theBytesAskedFor(byte[] whole) {
            int from = (int) Math.min(position.orElse(0L), whole.length);
            if (bound.isEmpty()) {
                return java.util.Arrays.copyOfRange(whole, from, whole.length);
            }
            long asked = bound.orElseThrow();
            if (asked >= 0) {
                int to = (int) Math.min(from + asked, whole.length);
                return java.util.Arrays.copyOfRange(whole, from, to);
            }
            long backwards = -asked;
            if (backwards > from) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                        "a backwards read of " + backwards
                                + " reaches before the file's start");
            }
            return java.util.Arrays.copyOfRange(whole, (int) (from - backwards), from);
        }

        private static Optional<String> decodedUtfText(byte[] bytes) {
            try {
                return Optional.of(
                        strictlyDecodedByItsMark(bytes).replace("\r\n", "\n"));
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
                    : Optional.of((long) asMagnitude(asked));
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
            byte[] fed = java.util.Arrays.copyOf(octets, octets.length + 1);
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
            return java.util.Arrays.copyOf(octets, (int) (long) bound.orElseThrow());
        }

        private static byte[] utf8(String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * The reason code a failed open reports as the error's second argument:
     * {@code RFE_OPEN_FAIL} is 3, and {@code Trap_Port} pushes it, so the
     * catalogue's {@code "reason:" :arg2} reduces to the number 3.
     */
    private static final int OPEN_FAILED = 3;

    /**
     * Makes the connection a TCP port stands for, and keeps it in the port.
     *
     * <p>In EXTRA, which sysobj.reb describes as "the host's own storage" --
     * exactly what a socket is. The port then reads and writes through it,
     * and CLOSE gives it back.
     */
    private void connectTheTcpPort(PortValue port, Evaluator evaluator) {
        String host = hostNamedBy(port);
        int number = portNumberOf(port);
        throughNetwork(() -> {
            port.setField("extra", JavaObjectValue.of(
                    evaluator.network().connectTo(host, number)));
            return port;
        });
    }

    /**
     * Which numbered port a spec names, or the one its scheme is known by.
     *
     * <p>A URL need not say: {@code tcp://example.com} is a whole address to
     * a person and half of one to a socket, and the well-known number is what
     * fills the gap.
     */
    private static int portNumberOf(PortValue port) {
        if (port.fieldNamed("spec") instanceof ObjectValue spec
                && spec.context().holds("port")
                && spec.context().ownSlotFor("port").value()
                        instanceof IntegerValue given) {
            return (int) given.magnitude();
        }
        return NetworkPort.wellKnownPortFor(port.schemeName()).orElse(0);
    }

    /**
     * Turns a network refusal into an ordinary error a script can catch.
     *
     * <p>The same shape {@link #throughPort} gives a filesystem refusal: the
     * adapter throws its own kind and nothing of the host's escapes into a
     * script.
     */
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

    /**
     * The connection a TCP port is holding, or a refusal saying it has none.
     *
     * <p>A port that was never opened, or has been closed, has nothing to
     * read from -- and saying so is better than answering no bytes, which a
     * caller cannot tell from a quiet connection.
     */
    private static NetworkPort.Connection connectionBehind(PortValue port) {
        if (port.fieldNamed("extra") instanceof JavaObjectValue carried
                && carried.held().orElse(null) instanceof NetworkPort.Connection open) {
            return open;
        }
        throw Raised.of(EvaluationFailure.NOT_OPEN, port.schemeName());
    }

    /**
     * The host a port's spec names.
     *
     * <p>From the spec's HOST field where there is one, and from its REF
     * otherwise -- a DNS port is usually opened as {@code dns://name} and the
     * name is all of it.
     */
    private static String hostNamedBy(PortValue port) {
        if (port.fieldNamed("spec") instanceof ObjectValue spec) {
            if (spec.context().holds("host")
                    && spec.context().ownSlotFor("host").value()
                            instanceof StringValue named) {
                return named.text();
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

    /** Turns a port's refusal into an error the script can catch. */
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

    /**
     * The same, for a window port.
     *
     * <p>A separate wrapper because the two refusals mean different things and
     * carry different ids. A window port refuses because the host granted the
     * service and supplied no screen, which is {@code not_present}; a file port
     * refuses for reasons of its own about the path.
     */
    private static Value throughWindow(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (WindowPort.Denied denied) {
            throw refusedByTheHost(denied.errorId(), denied.getMessage());
        }
    }

    /**
     * A host service that is there to grant and has nothing behind it.
     *
     * <p>The reason goes into ARG1 and not only into the message, for the same
     * reason {@link Raised#of(EvaluationFailure, String)} says: a script that
     * has to read the reason back out of prose cannot tell the three refusals
     * apart, and telling them apart is the whole point of having three. A host
     * that granted the screen and supplied none can be fixed by supplying one;
     * a host that granted nothing cannot.
     */
    private static Raised refusedByTheHost(String errorId, String because) {
        String reason = because + ", which is "
                + ServiceRefusal.NOT_PRESENT.name()
                        .toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return new Raised(ErrorValue.about(
                ErrorCategory.ACCESS, errorId, reason, StringValue.of(reason)));
    }

    /**
     * A refinement's string argument, or empty when the refinement was not
     * asked for.
     *
     * <p>The dialogs take several optional arguments between them and every
     * one of them is "use the host's own default if I say nothing", so an
     * absent refinement has to arrive as an absence rather than as an empty
     * string.
     */
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
        define("parse", List.of(Parameter.required("input"), Parameter.required("rule")),
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

    /** What CLAMP will hold: {@code [number! tuple! pair! money!]}. */
    private static final Set<Datatype> CLAMPABLE = Set.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
            Datatype.TUPLE, Datatype.PAIR, Datatype.MONEY);

    /**
     * CLAMP: a value held inside a range.
     *
     * <p>The bounds must be the same datatype as the value and are not
     * converted -- {@code Trap2(RE_TYPE_MISMATCH, val, vmin)} before anything
     * else happens. So {@code clamp 5 1.0 3} is refused rather than quietly
     * treating 1.0 as 1, which is the choice worth having: a caller who mixed
     * them almost certainly meant one type throughout.
     *
     * <p>Bounds written the wrong way round answer the lower one, and that is
     * not a check anybody wrote. It falls out of the order the two are applied
     * in: the inner minimum pulls the value down to the maximum and the outer
     * maximum pushes it back up to the minimum.
     */
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
            case MoneyValue amount -> clampedMoney(
                    amount, (MoneyValue) lowest, (MoneyValue) highest);
            default -> value;
        };
    }

    /**
     * A tuple clamped octet by octet, with a missing bound reading as zero.
     *
     * <p>Surprising and it is what the C does: {@code REBYTE lo = i <
     * VAL_TUPLE_LEN(vmin) ? b1[i] : 0}. So a maximum shorter than the value
     * clamps the rest of it to zero rather than leaving it alone, which is a
     * trap for anybody writing {@code clamp 200.100.50 0.0.0 128.128}.
     */
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

    /**
     * Money compared rather than clipped, because a money is a decimal with a
     * scale and clipping it through a double would lose the scale.
     */
    private static Value clampedMoney(
            MoneyValue value, MoneyValue lowest, MoneyValue highest) {

        if (value.amount().compareTo(lowest.amount()) <= 0) {
            return lowest;
        }
        if (highest.amount().compareTo(value.amount()) <= 0) {
            return highest;
        }
        return value;
    }

    private static double clipped(double value, double lowest, double highest) {
        return Math.max(lowest, Math.min(highest, value));
    }

    /**
     * DISTANCE: how far apart two points are.
     *
     * <p>As the crow flies by default and by the streets with /taxicab, which
     * is the sum of the two absolute differences. Always a decimal, even when
     * the answer is whole, because a distance is a measurement rather than a
     * count.
     */
    private static Value betweenTwoPoints(
            PairValue from, PairValue to, boolean alongTheStreets) {

        double across = from.x() - to.x();
        double down = from.y() - to.y();
        return DecimalValue.of(alongTheStreets
                ? Math.abs(across) + Math.abs(down)
                : Math.hypot(across, down));
    }

    /** The largest factorial that fits a whole number, and the largest at all. */
    private static final int LARGEST_EXACT_FACTORIAL = 20;
    private static final int LARGEST_FACTORIAL_AT_ALL = 170;

    /**
     * FACTORIAL: exact while it fits, then approximate, then refused.
     *
     * <p>Three ranges and the C draws both lines deliberately. Up to twenty it
     * fits a whole number. Up to a hundred and seventy it fits a double. The
     * next one is over a double's largest and would silently be infinity, so
     * it is refused until there is a bignum rather than answered wrongly.
     */
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

    /**
     * Splitting a string on delimiters, which is what PARSE does when its
     * rule is not a block.
     *
     * <p>Splits on any of the characters in the delimiter string. Empty
     * input gives no pieces at all, while two delimiters in a row give an
     * empty piece between them, so emptiness is counted at both ends.
     */
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

    /**
     * LAYOUT is not defined, and that is the whole of this method.
     *
     * <p>There was a native here that answered its own argument, so a VID
     * program ran, reported success and drew nothing. Worse than a fork,
     * because there is nothing to fork: {@code layout} is defined nowhere in
     * {@code src/mezz} or {@code src/boot}, so a real 3.22.1 has no such
     * function either and {@code view-funcs.reb:117} calls it anyway.
     *
     * <p>So a block handed to VIEW fails here exactly as it fails there, on a
     * word with no value, until VID is written -- in REBOL, because a dialect
     * is REBOL. A stub that says yes is worse than the failure it hides.
     */
    private void defineLayout() {
    }

    /**
     * The three commands a windowing host must answer, from
     * {@code boot/window.reb}.
     *
     * <p>VIEW, UNVIEW, DO-EVENTS and the handler list are not here. Those are
     * REBOL's own, in {@code view-funcs.reb}, and they are borrowed and loaded
     * rather than rewritten. These three are what that file calls out to.
     */
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

    /**
     * INIT-TOP-WINDOW: the gob every window hangs under.
     *
     * <p>Three things, and the C does them in three lines. The gob is
     * remembered, its parent is cut loose because a root has none, and the
     * screen's size is written onto it.
     *
     * <p>The size is the one that matters downstream. VIEW centres a window
     * with {@code screen/size - window/size / 2}, so a root of the wrong size
     * puts every centred window in the wrong place.
     */
    private static Value theRootGobTakenBy(ScreenPort screen, Value given) {
        if (!(given instanceof GobValue root)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "init-top-window takes a gob, not "
                            + given.datatype().literalSpelling());
        }
        ScreenPort.takeAsTheRoot(screen, root);
        return NoneValue.none();
    }

    /**
     * GUI-METRIC: one measurement of the screen.
     *
     * <p>Eleven of the twelve keywords measure and answer a pair. SCREENS
     * counts and answers an integer, which is why the C writes it into the
     * frame and returns before reaching the code that makes a pair.
     */
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

    /**
     * A word no host serves is refused rather than answered with none.
     *
     * <p>Because a metric is a number the caller is about to compute with. A
     * none reaching {@code screen/size - window/size / 2} fails somewhere
     * else entirely, and blames the subtraction rather than the misspelling.
     *
     * <p>{@code virtual-screen-size} is the case that proves this is not
     * hypothetical. It is in the word list {@code boot/window.reb} hands the
     * host, so it reads as supported, and neither host has a branch for it.
     */
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

    /** Which display was asked about. The first, unless /display said. */
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

    /**
     * SHOW: makes the screen's windows match the gob tree, and answers what it
     * was given.
     *
     * <p>Answering the argument is the C returning {@code RXR_VALUE} without
     * touching the frame slot, and VIEW depends on it. Showing a none does
     * nothing and answers none, which UNVIEW depends on under a comment
     * reading "none ok".
     */
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

    /**
     * What PRINT writes for a value.
     *
     * <p>A block is reduced first and its results joined with spaces, which is
     * why {@code print ["count:" count]} shows the number rather than the
     * word. Printing a block without reducing it would make the commonest
     * thing anyone writes with PRINT print the wrong thing.
     */
    private static String forOutput(Value value, Evaluator evaluator) {
        if (!(value instanceof BlockValue block)) {
            return Molder.form(value);
        }
        return evaluator.evaluateEachOrRaise(block, evaluator.systemContext()).stream()
                .map(Molder::form)
                .collect(Collectors.joining(" "));
    }

    private void defineOutput() {
        define("mold", List.of(Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER))),
                Set.of("all", "only", "flat", "part"),
                (arguments, evaluator, context, refinements) -> {
                    String written = refinements.contains("flat")
                            ? Molder.moldFlat(arguments.get(0))
                            : refinements.contains("all")
                                    ? Molder.moldAll(arguments.get(0))
                                    : Molder.mold(arguments.get(0));
                    if (refinements.contains("only")
                            && arguments.getFirst() instanceof BlockValue block
                            && block.datatype() == Datatype.BLOCK) {
                        written = Molder.moldOnly(block);
                    }
                    if (refinements.contains("part") && arguments.size() > 1
                            && arguments.get(1) instanceof IntegerValue limit) {
                        int wanted = (int) Math.max(0, limit.magnitude());
                        written = written.length() <= wanted
                                ? written
                                : written.substring(0, wanted);
                    }
                    return StringValue.of(written);
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
