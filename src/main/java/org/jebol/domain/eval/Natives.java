package org.jebol.domain.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.DoublePredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.jebol.domain.parse.Parser;
import org.jebol.domain.parse.StringParser;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import java.nio.charset.StandardCharsets;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.BlockStorage;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.ErrorCatalogue;
import org.jebol.domain.read.SyntaxFailure;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.EventCatalogue;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.ImageStorage;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.ModuleValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.ParameterKind;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

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

    private final java.util.Random randomness = new java.util.Random();

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
        defineOutput();
        defineOperators();
    }

    private void defineOperators() {
        // Every operator is registered here, last, because an operator
        // names a prefix twin that has to exist by the time it is
        // registered. Spread through the define methods, adding one
        // meant finding out at startup that it had been written above
        // the native it names -- three times in one sitting.
        //
        // Three of the pairings are not what the spelling suggests. =? is
        // SAME? rather than EQUAL?. And the division leftovers group the
        // wrong way round twice: % is REMAINDER, although the operator is
        // called modulo in most other languages, while %% is MODULO. MOD,
        // whose name is the first three letters of MODULO, is REMAINDER.
        // Confirmed against a real R3 at every combination of signs, and
        // silent when wrong, because the two groups agree on positives.
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
        // // is INTEGER-DIVIDE and not REMAINDER. ops.reb reads
        // `// integer-divide`, and every other language that spells a
        // remainder with two characters spells it this way, which is what
        // makes this the pairing most likely to be got wrong. `23 // 10` is 2
        // and `23 % 10` is 3.
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

        // REBOL's own library reads this and builds itself out of what
        // it finds. base-defs.reb walks the reflectors and defines
        // SPEC-OF, BODY-OF, WORDS-OF, VALUES-OF, TYPES-OF and TITLE-OF
        // from them rather than writing six functions out, and
        // base-series.reb binds a block to the bitsets to define the
        // named character sets. A missing field here is not a missing
        // field; it is every function that would have been generated
        // from it.
        catalog.set("reflectors", BlockValue.block(List.of(
                WordValue.of("spec"), typeNames("any-function", "any-object", "datatype"),
                WordValue.of("body"), typeNames("any-function", "any-object", "map"),
                WordValue.of("words"), typeNames("any-function", "any-object", "map", "date"),
                WordValue.of("values"), typeNames("any-object", "map"),
                WordValue.of("types"), typeNames("any-function"),
                WordValue.of("title"), typeNames("any-function", "datatype"))));

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
        // The four Rebol has that JEBOL had not. sys-ports.reb's url-parser
        // reads URI, and without it that file stops on its first rule -- which
        // takes MAKE-PORT* and the whole scheme registry with it.
        //
        // NOT-CRLF is computed rather than written out, exactly as
        // sysobj.reb computes it, so the two cannot drift apart.
        bitsets.set("not-crlf",
                BitsetValue.ofCharacters('\r', '\n').complemented());
        // The characters that need no percent-encoding in a URL, and the
        // narrower set for one component of one. Copied from sysobj.reb,
        // where each is written as the octets rather than as a range, because
        // neither is a range.
        bitsets.set("uri", charactersIn(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                        + "!#$&'()*+,-./:;=?@_~"));
        bitsets.set("uri-component", charactersIn(
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                        + "!'()*-._~"));
        // Everything a quoted-printable body may carry without escaping,
        // which is every octet but four.
        bitsets.set("quoted-printable", quotedPrintableOctets());
        catalog.set("bitsets", new ObjectValue(bitsets));

        // The two lists an event's type and its named keys are positions in. A
        // script reads them here and the event datatype reads the same order to
        // turn a word into a number, so both come from EventCatalogue rather than
        // being written out twice -- the same reason the character sets above are
        // computed rather than listed.
        // `Register_Handle` appends the type word here as each kind registers, up
        // to `MAX_HANDLE_TYPES` of 64, and a handle stores its position. Only
        // `codec` registers in this build; the crypto kinds arrive with the
        // ciphers.
        catalog.set("handles", BlockValue.block(List.of(WordValue.of("codec"))));

        catalog.set("event-types", EventCatalogue.typesBlock());
        catalog.set("event-keys", EventCatalogue.keysBlock());

        // Which checksums and compressions exist is the host's business
        // rather than the language's, which is why R3 fills these two from
        // Init_Crypt and Init_Compression rather than writing them out in
        // sysobj.reb. A script asks the catalogue before asking for one.
        catalog.set("checksums", BlockValue.block(
                Encodings.checksumMethods().stream()
                        .<Value>map(WordValue::of).toList()));
        catalog.set("compressions", BlockValue.block(
                Encodings.COMPRESSIONS.stream()
                        .<Value>map(WordValue::of).toList()));

        // Suffix then name, in pairs. REGISTER-CODEC appends to this as
        // each codec arrives, so it starts as the few JEBOL knows about
        // rather than as R3's full list -- the list is a record of what
        // has registered, not a declaration of what may.
        catalog.set("file-types", BlockValue.block(List.of(
                StringValue.of(".txt", Datatype.FILE), WordValue.of("text"),
                StringValue.of(".html", Datatype.FILE), WordValue.of("markup"),
                StringValue.of(".htm", Datatype.FILE), WordValue.of("markup"))));

        // system/options, with every field sysobj.reb declares.
        //
        // The whole list rather than the ones something has asked for so
        // far, because a set-path cannot create a field an object has not
        // got -- R3 refuses that too -- so a missing field is not a missing
        // convenience, it stops the file that writes to it. mezz-tail.reb
        // reads `system/options/data` on its fourth-from-last line and lost
        // everything below it, PROTECT-SYSTEM included.
        Context options = Context.root();
        for (String field : new String[] {
                "boot", "path", "home", "data", "modules", "flags", "script",
                "args", "do-arg", "import", "debug", "secure", "version",
                "boot-level", "domain-name", "module-paths", "result-types"}) {
            options.set(field, NoneValue.none());
        }
        // FLAGS is read as an object of named boot flags, not as a number:
        // mezz-secure.reb ends with `unless system/options/flags/secure-min`.
        // The names are system/catalog/boot-flags, and none of them is set,
        // because JEBOL is embedded rather than started from a command line.
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
        // Where the interpreter was started from and where it lives. Both
        // are files in a real R3, and code reads them to find things
        // relative to itself.
        options.set("home", StringValue.of(
                System.getProperty("user.home", "") + "/", Datatype.FILE));
        // BOOT is always a file in a real R3 -- Init_Main_Args sets it from
        // the exe path unconditionally -- and here it names the JVM running
        // the interpreter. The path is already absolute, so the CLEAN-PATH
        // mezz-tail runs over it changes nothing.
        options.set("boot", StringValue.of(
                ProcessHandle.current().info().command()
                        .orElse(System.getProperty("java.home", "") + "/bin/java")
                        .replace('\\', '/'),
                Datatype.FILE));
        options.set("path", StringValue.of(
                System.getProperty("user.dir", "") + "/", Datatype.FILE));
        options.set("data", StringValue.of(
                System.getProperty("user.home", "") + "/.jebol/", Datatype.FILE));
        // LOG and ANSI are maps in R3 and are filled in by the prelude,
        // which can write a map literally and this cannot.

        // system/state, again the whole of sysobj.reb's list. POLICIES is an
        // object of eleven security policies, all set to 0.0.0 meaning allow,
        // and mezz-secure.reb reads and writes every one of them.
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

        // The catalogue as data a script can walk: one object per category
        // holding its code and one field per error id, each holding the
        // message template errors.reb declares -- a string, or a block whose
        // :arg1 get-words a script binds against a caught error. Rebol's own
        // suite reduces exactly that.
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
        // VERSION is a tuple in R3, not a string. sysobj.reb writes
        // `version: 0.0.0`, and code compares it with a tuple: mezz-banner.reb
        // prints it and sys-load.reb checks a module's Needs against it.
        system.set("version", TupleValue.of(new int[] {0, 1, 0}));
        system.set("platform", WordValue.of("JVM"));
        // PRODUCT names which build this is. mezz-banner.reb reads it.
        system.set("product", WordValue.of("core"));
        system.set("license", NoneValue.none());

        // The rest of sysobj.reb's top-level fields, as objects of none.
        // Absent ones are not a missing convenience: a set-path cannot make
        // a field, so the file that writes to one stops there.
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
        system.set("locale", new ObjectValue(locale));
        // `Init_Codecs` registers two at boot, and `Register_Codec` puts a handle
        // of type `codec` in here for each: `SET_HANDLE(value, dispatcher,
        // SYM_CODEC, HANDLE_FUNCTION)`. Those handles are what DO-CODEC is given,
        // and registering them is what makes the handle datatype reachable at all --
        // nothing else in a build without the crypto family or an extension API
        // produces one.
        //
        // Every other codec in the C sits behind an `#ifdef INCLUDE_*_CODEC`, so
        // these two are what a stock build always has.
        Context codecs = Context.root();
        for (int at = 0; at < Codecs.REGISTERED.size(); at++) {
            String named = Codecs.REGISTERED.get(at);
            codecs.set(named, HandleValue.function(
                    "codec", CODEC_HANDLE_IDENTITY + at, WordValue.of(named)));
        }
        system.set("codecs", new ObjectValue(codecs));
        system.set("console", new ObjectValue(Context.root()));

        // base-constants.reb starts by naming the contexts it works in, and
        // everything after that line in the file depends on the names
        // existing. LIB is where the standard functions live and SYS is the
        // library's own private helpers.
        //
        // SYS is a child of LIB, which is how R3 arranges it in the two lines
        // of Do_Global_Block that run the sys files: new words are added to
        // sys, and the block then binds deep into lib so that a helper can
        // still call a standard function. USER is filled in by the
        // interpreter, which owns it.
        Context internals = Context.childOf(systemContext);
        // NATIVE and ACTION exist before any file loads, because Rebol's
        // boot puts them there: `Do_Global_Block(actions, -1)` and
        // `Do_Global_Block(natives, -1)`. Rebind -1 adds the set-words to
        // lib and then binds the block into sys as well, so the words live
        // in lib. They are how the C's function specs get declared.
        //
        // JEBOL's natives are Java and declare themselves, so nothing here
        // needs the words -- but base-funcs.reb ends with
        // `unset 'action ; this native was only for internal use`, and
        // unsetting a word that was never defined is an error. sys-base.reb
        // sets both to none for the same reason, and it runs later.
        systemContext.set("native", NoneValue.none());
        systemContext.set("action", NoneValue.none());
        Context contexts = Context.root();
        contexts.set("lib", new ObjectValue(systemContext));
        contexts.set("sys", new ObjectValue(internals));
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
        // The words that name values rather than functions. Without these,
        // `if true [...]` fails on the condition rather than the branch.
        context.set("true", LogicValue.yes());
        context.set("false", LogicValue.no());
        context.set("none", NoneValue.none());
        context.set("on", LogicValue.yes());
        context.set("off", LogicValue.no());
        context.set("yes", LogicValue.yes());
        context.set("no", LogicValue.no());
        // A constant rather than a computed value, because a script that
        // writes 3.14159265358979 and compares it with this one has to
        // get the same bits back, and the printed form is fifteen digits
        // shorter than the number underneath.
        context.set("pi", DecimalValue.of(Math.PI));

        // Every datatype and typeset name is a word bound here, because
        // that is what it is: the reader hands back a word for `integer!`
        // and this is what the word means. A function spec, a parse rule
        // and `make integer! ...` all reach a datatype the same way, by
        // looking the word up.
        for (Datatype datatype : Datatype.values()) {
            context.set(datatype.literalSpelling(), DatatypeValue.of(datatype));
        }
        for (Typeset typeset : Typeset.values()) {
            context.set(typeset.literalSpelling(), TypesetValue.of(typeset));
        }
        // SYSTEM is data rather than a function: what the interpreter
        // knows about itself, reached by path. A native set alone could
        // never hold it, because a native set is a list of things to call.
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

    // ---- registration helpers -------------------------------------------

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
                Datatype.DATATYPE, Datatype.PAIR);
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

    private static List<Parameter> takesNumbers(String... names) {
        // PAIR belongs here because arithmetic on a pair is arithmetic on
        // each half, so every operation below already means something for
        // one. It is not a number in any other sense: NUMBER? refuses it.
        //
        // CHAR belongs here for the same reason and with less warning: a
        // character in arithmetic is its code point, so `1.0 * #"a"` is 97.0.
        // The character does not survive -- the answer is a decimal -- and it
        // is the argument list that has to allow it, because the door is where
        // it was being turned away.
        Set<Datatype> numbers = Set.of(
                Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT,
                Datatype.MONEY, Datatype.PAIR, Datatype.TUPLE,
                Datatype.TIME, Datatype.DATE, Datatype.CHAR);
        List<Parameter> parameters = new ArrayList<>();
        for (String name : names) {
            parameters.add(Parameter.required(name, numbers));
        }
        return parameters;
    }

    // ---- arithmetic ------------------------------------------------------

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
        // The transcendental functions and roots reach machine floating
        // point, which is the test for belonging in Java rather than the
        // prelude. Each is a line of libm in Rebol's C and a line of
        // java.lang.Math here.
        define("square-root", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.sqrt(Comparison.asDouble(arguments.get(0)))));
        // SQRT is not a short name for SQUARE-ROOT. They compute the same
        // curve and differ in what they accept: SQUARE-ROOT takes any number
        // and SQRT takes a decimal, so `sqrt 4` raises expect-arg naming
        // integer! while `square-root 4` is 2.0.
        //
        // The shorter name being the fussier one is the wrong way round from
        // what the spelling suggests, and it is a trap because the forgiving
        // twin exists: code written with SQRT works on every decimal and
        // fails the first time an integer reaches it.
        define("sqrt", List.of(Parameter.required("value", Set.of(Datatype.DECIMAL))),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.sqrt(Comparison.asDouble(arguments.get(0)))));
        // The first host service JEBOL offers. It reaches outside the
        // interpreter for the time, thus it asks whether the host granted
        // the clock before it answers.
        // Ten refinements, and each names a part of one reading rather than a
        // question of its own. Only one may be asked at a time, which is why
        // they are listed as alternatives here and enforced below.
        define("now", List.of(),
                Set.of("year", "month", "day", "time", "zone", "date",
                        "weekday", "yearday", "precise", "utc"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.CLOCK);
                    return whatTheClockSays(refinements);
                });

        // ALSO answers its first argument and evaluates the second for its
        // effect. `return R_ARG1;` is the whole function: both arguments are
        // ordinary, so both are evaluated before it runs, and the second's value
        // is dropped.
        define("also", takesAnything("value1", "value2"),
                (arguments, evaluator, context) -> arguments.getFirst());

        // COMMENT evaluates its argument and answers nothing. `return R_UNSET;`,
        // and the spec takes an ordinary `value` rather than a quoted one -- so
        // `comment print "x"` prints. What it ignores is the value, not the
        // work.
        define("comment", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> UnsetValue.unset());

        // TO-VALUE turns an unset into a none and leaves everything else alone:
        // `return (IS_UNSET(D_ARG(1)) ? R_NONE : R_ARG1);`. It is how a caller
        // passes something on without testing it first, because most functions
        // refuse an unset and accept a none.
        define("to-value", takesAnything("value"),
                (arguments, evaluator, context) ->
                        arguments.getFirst() instanceof UnsetValue
                                ? NoneValue.none()
                                : arguments.getFirst());

        // FOREVER runs its block until something leaves the loop, and answers
        // the last value the block gave: `while (1) { result = DO_BLK(...); if
        // (THROWN(result) && Check_Error(result) >= 0) break; } return R_TOS1;`.
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

        // The four ordinals JEBOL was missing. `Do_Ordinal(ds, n)` pushes the
        // number and dispatches PICK, so an ordinal is PICK with the position
        // written into the name -- and every rule PICK has, these have.
        define("seventh", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 7));
        define("eighth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 8));
        define("ninth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 9));
        define("tenth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.getFirst(), 10));

        // TRACE turns the evaluator's walk into output, and the level is a
        // limit rather than a switch: `Trace_Level = IS_TRUE(arg) ? 100000 : 0;`
        // for a logic, and the number itself for an integer, so `trace 3` shows
        // three levels of nesting and nothing deeper.
        //
        // /BACK keeps the lines instead of printing them, and asking for them
        // also turns tracing off -- `Trace_Flags = 0; Display_Backtrace(...)`.
        // A caller that wants both has to ask for the level again.
        define("trace", List.of(Parameter.required("mode",
                        Set.of(Datatype.INTEGER, Datatype.LOGIC))),
                Set.of("back", "function"),
                (arguments, evaluator, context, refinements) -> {
                    // `Check_Security(SYM_DEBUG, POL_READ, 0)` guards this in
                    // the C, which is Rebol's SECURE policy rather than a host
                    // service: tracing writes to the output the interpreter
                    // already has, so there is nothing outside to reach for and
                    // no grant to ask. PRINT is unguarded here for the same
                    // reason.
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
                        // `else Enable_Backtrace(FALSE);` -- a plain TRACE turns
                        // the buffer off, so the lines print as they happen.
                        tracing.keepRatherThanPrint(false);
                    }
                    int wanted = mode instanceof IntegerValue level
                            ? (int) level.magnitude()
                            : (mode.isTruthy() ? Trace.EVERYTHING : 0);
                    tracing.level(wanted, refinements.contains("function"));
                    return UnsetValue.unset();
                });

        // The four that exist to call code written in C. Each one is
        // refused whatever the host granted, and the error says that
        // nothing can offer it rather than that this host did not.
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
        define("access-os", takes("field"),
                (arguments, evaluator, context) -> refuseExtensionPoint("access-os"));

        // The angle of a point measured anticlockwise from the x axis,
        // between -180 and 180. Two arguments in one, which is why it
        // takes a pair rather than a pair of numbers: the y comes first
        // in the mathematics and second in the pair, and passing them
        // separately is how that gets swapped.
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
        // E raised to a power, the other way round from LOG. Named for
        // the mathematics rather than for what it does to its argument,
        // which is why it is not called power-of-e.
        define("exp", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.exp(Comparison.asDouble(arguments.get(0)))));
        // What is left of a decimal once the whole part is taken off, and
        // it keeps the sign: the fraction of -1.25 is -0.25 and not 0.75.
        define("fraction", List.of(Parameter.required("number", Set.of(
                        Datatype.DECIMAL, Datatype.PERCENT))),
                (arguments, evaluator, context) -> {
                    double whole = Comparison.asDouble(arguments.get(0));
                    return DecimalValue.of(whole - (long) whole);
                });
        define("log-2", takesOnlyNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.log(Comparison.asDouble(arguments.get(0))) / Math.log(2)));
        // Degrees by default and radians when asked, for the three that
        // take an angle and the three that answer one. Degrees is the
        // default because that is what a script written for R3 expects,
        // however unusual it looks beside every other language's library.
        // Each of the three snaps an almost-right answer to the right one, and
        // each snaps a different thing. SINE and COSINE force a result within
        // one step of the representation to an exact zero, so `cosine 90` is
        // 0.0 rather than 6.1e-17. TANGENT forces an angle close enough to a
        // right angle to an infinity, rather than handing back the very large
        // number the hardware gives.
        //
        // Without the snaps every assertion about a right angle fails on a
        // number that prints as though it were the answer.
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

        // ABSOLUTE keeps the datatype it was given, so the whole number
        // stays whole rather than becoming a decimal on the way.
        // ABS is the action; ABSOLUTE is the same thing under its long
        // name. Both take the types arithmetic takes, and neither takes
        // the sign off negative zero -- `abs -0.0` is -0.0, because the
        // sign is not something the magnitude carries.
        define("abs", List.of(Parameter.required("value", MEASURABLE)),
                (arguments, evaluator, context) -> magnitudeOf(arguments.get(0)));
        define("absolute", List.of(Parameter.required("value", MEASURABLE)),
                (arguments, evaluator, context) -> magnitudeOf(arguments.get(0)));

        // RANDOM answers a value between one and its argument. Without a
        // seed it is not repeatable, which is why the corpus pins only
        // the two arguments that leave it no choice.
        define("random", List.of(Parameter.required("value")),
                Set.of("seed", "only", "secure"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("seed")) {
                        randomness.setSeed((long) asMagnitude(arguments.get(0)));
                        return UnsetValue.unset();
                    }
                    return switch (arguments.get(0)) {
                        case IntegerValue whole -> IntegerValue.of(whole.magnitude() == 0
                                ? 0
                                : 1 + (long) (randomness.nextDouble()
                                        * Math.abs(whole.magnitude()))
                                        * Long.signum(whole.magnitude()));
                        case DecimalValue quantity -> DecimalValue.of(
                                randomness.nextDouble() * quantity.quantity());
                        // Given a series RANDOM shuffles it and answers
                        // it. Picking one item at random is /only, which
                        // is the opposite way round from what the name
                        // suggests.
                        case BlockValue block when refinements.contains("only") ->
                                block.remaining().isEmpty()
                                        ? NoneValue.none()
                                        : block.remaining().get(randomness.nextInt(
                                                block.remaining().size()));
                        case BlockValue block -> shuffled(block);
                        case StringValue text -> shuffledText(text);
                        case BinaryValue bytes -> shuffledBytes(bytes);
                        // Each octet on its own rather than one number
                        // spread over all of them, so an octet never
                        // grows and a zero one stays zero.
                        case TupleValue tuple -> randomisedOctets(tuple);
                        case PairValue point -> randomisedHalves(point);
                        // A character answers one between the first and its own
                        // codepoint, skipping the codepoints no character can
                        // hold: `do { chr = 1 + (Random_Int(...) % chr); } while
                        // (IS_INVALID_CHAR(chr));`. A codepoint of zero has
                        // nothing to pick from and answers itself.
                        case CharacterValue letter -> letter.codepoint() == 0
                                ? letter
                                : CharacterValue.of(aValidCodepointUpTo(
                                        letter.codepoint()));
                        // A time answers one between zero and itself, in
                        // whatever precision it carries: `secs =
                        // Random_Range(secs, D_REF(3));`.
                        case TimeValue span -> TimeValue.ofNanoseconds(
                                randomLongUpTo(span.nanoseconds()));
                        // A date randomises the year, the month, the day and
                        // the time, each within its own range, which is why the
                        // answer is a date somewhere in the past rather than a
                        // number of days from this one.
                        case DateValue when -> randomisedDate(when);
                        // A logic answers true or false: `SET_LOGIC(D_RET,
                        // Random_Int(...) & 1);`.
                        case LogicValue ignored ->
                                LogicValue.of(randomness.nextBoolean());
                        default -> raiseCannotUse(arguments.get(0), "random");
                    };
                });

        // COMPLEMENT is one word for two jobs: bits for a number, truth
        // for a logic.
        // Whether a set was made by COMPLEMENT. The set answers the same
        // questions either way, so nothing else can tell.
        define("complement?", List.of(Parameter.required("value", Set.of(Datatype.BITSET))),
                (arguments, evaluator, context) -> LogicValue.of(
                        ((BitsetValue) arguments.getFirst()).isComplemented()));

        // `value<logic! integer! tuple! binary! bitset! typeset! image!>` and
        // nothing else. The arm for a decimal exists in `REBTYPE(Decimal)` --
        // `case A_COMPLEMENT: SET_INTEGER(D_RET, ~(REBINT)d1);` -- and is
        // unreachable through COMPLEMENT because the declaration turns the
        // decimal away first. A parameter that accepted everything let it
        // through, which is how `complement 5.5` came to answer -6 here and an
        // error in a real R3.
        define("complement", List.of(Parameter.required("value", Set.of(
                        Datatype.LOGIC, Datatype.INTEGER, Datatype.TUPLE,
                        Datatype.BINARY, Datatype.BITSET, Datatype.TYPESET,
                        Datatype.IMAGE))),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case LogicValue truth -> LogicValue.of(!truth.truth());
                    case IntegerValue whole -> IntegerValue.of(~whole.magnitude());
                    // The set of everything the bitset does not hold,
                    // which is what a rule means by "any but these".
                    case BitsetValue members -> members.complemented();
                    case BinaryValue bytes -> newBytesEachFlipped(bytes);
                    case ImageValue image -> newImageEachChannelFlipped(image);
                    // Every datatype the typeset does not hold. One line in
                    // the C -- `VAL_TYPESET(val) = ~VAL_TYPESET(val)` -- and
                    // it covers every datatype the build knows rather than
                    // only the ones the set was written with.
                    case TypesetValue kinds -> complementOfTypeset(kinds);
                    // Every kept octet flipped, and the zeros behind them
                    // left alone, so complementing 1.0.0 gives 254.255.255
                    // and not twelve flipped octets.
                    case TupleValue tuple -> flippedOctets(tuple);
                    // Bits are a whole-number idea, so a fraction is
                    // refused rather than truncated.
                    default -> raiseWrongArgument(
                            arguments.get(0), "complement", "logic or integer");
                });

        // The short trigonometric names are not aliases of the long
        // ones. SINE takes degrees and accepts a whole number; SIN takes
        // radians and refuses one. Defining either in terms of the other
        // is wrong by a factor of fifty-seven for every angle.
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

        define("to-degrees", takesNumbers("radians"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.toDegrees(Comparison.asDouble(arguments.get(0)))));
        define("to-radians", takesNumbers("degrees"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.toRadians(Comparison.asDouble(arguments.get(0)))));

        // The whole-number functions. Each refuses a fraction rather
        // than truncating it, because divisors and primes are ideas
        // about whole numbers and a truncated answer would look right.
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
                    // Decimals are accepted and their fractions thrown
                    // away. Refusing them is the tempting reading, since
                    // the name says integer, and it is not what a real R3
                    // does: `integer-divide 23.5 10` is 2.
                    long divisor = (long) Comparison.asDouble(arguments.get(1));
                    requireNonZero(divisor);
                    // Towards zero rather than downwards, so -7 over 2 is
                    // -3 and not -4.
                    return IntegerValue.of((long) Comparison.asDouble(arguments.get(0)) / divisor);
                });

        // A tuple gets in and is turned away inside rather than at the
        // argument, so `1.2.3.4 ** 1` says the datatype cannot do this
        // rather than that the argument was the wrong shape. R3 answers
        // cannot-use here and names the tuple.
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

        // NEGATE and COMPLEMENT are one case group for a bitset -- `case
        // A_COMPLEMENT: case A_NEGATE:` -- so negating a set is complementing
        // it, and the parameter list has to admit one.
        define("negate", withBitsets(takesNumbers("value")),
                (arguments, evaluator, context) -> arguments.getFirst()
                        instanceof BitsetValue members
                        ? members.complemented()
                        : arithmetic(List.of(IntegerValue.of(0), arguments.get(0)),
                                Operation.SUBTRACT));

        // MIN and MAX were REBOL in the prelude, written as a comparison
        // and a choice between the two arguments. That cannot answer for
        // pairs: min 1x2 2x1 is 1x1, a pair that neither argument was, and
        // choosing one whole argument can never produce it.
        define("maximum", takesComparable("value1", "value2"),
                (arguments, evaluator, context) ->
                        extreme(arguments.get(0), arguments.get(1), true));
        define("minimum", takesComparable("value1", "value2"),
                (arguments, evaluator, context) ->
                        extreme(arguments.get(0), arguments.get(1), false));

        // AND, OR and XOR are one word each for two jobs. Given logic they
        // combine truth; given numbers they combine bits. A pair takes the
        // number reading on each half, which is the one place where the
        // decimal halves are treated as whole numbers.
        // A decimal is not one of the datatypes they take, so `1.2.3 or
        // 1.0` is turned away at the argument rather than being rounded
        // into one. Bits are a question about whole numbers.
        define("and~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        combined(arguments.get(0), arguments.get(1), Bitwise.AND));
        define("or~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        combined(arguments.get(0), arguments.get(1), Bitwise.OR));
        define("xor~", takesCombinable("value1", "value2"),
                (arguments, evaluator, context) ->
                        combined(arguments.get(0), arguments.get(1), Bitwise.XOR));
        // The symbol spellings of the same three. & and | are the whole
        // of it; there is no ^ for XOR because ^ starts an escape.

        // LERP is Oldes's rather than R3-Alpha's. It walks from one value
        // to another by a fraction.
        define("lerp", List.of(Parameter.required("value1"),
                        Parameter.required("value2"), Parameter.required("fraction")),
                (arguments, evaluator, context) -> interpolated(
                        arguments.get(0), arguments.get(1), arguments.get(2)));

        // Three names, two behaviours, and the spellings group them the
        // wrong way round. MOD sits with REMAINDER despite being the
        // first three letters of MODULO: `mod -7 3` is -1 and `modulo -7
        // 3` is 2. What MOD and MODULO share instead is the answer's
        // datatype -- the dividend's, with the divisor having no say, so
        // `mod 7 2.5` is the integer 2 where `remainder 7 2.5` is 2.0.
        //
        // Confirmed against a real R3 at every combination of signs and
        // at each datatype pairing.
        define("mod", List.of(
                        Parameter.required("dividend", DIVISIBLE),
                        Parameter.required("divisor", DIVISIBLE)),
                (arguments, evaluator, context) -> remainderOf(
                        arguments.get(0), arguments.get(1), Division.TRUNCATED));

        // MODULO divides the way Euclid did: the answer is never negative,
        // whatever the signs, so a divisor of -3 still gives 2 for -7.
        // /FLOOR switches it to the third definition, where the sign follows
        // the divisor instead, so `modulo/floor -7 -3` is -1.
        define("modulo", List.of(
                        Parameter.required("dividend", DIVISIBLE),
                        Parameter.required("divisor", DIVISIBLE)),
                Set.of("floor"),
                (arguments, evaluator, context, refinements) -> remainderOf(
                        arguments.get(0), arguments.get(1),
                        refinements.contains("floor") ? Division.FLOORED : Division.EUCLIDEAN));

        // A count past the width wraps, which is what the JVM does on
        // its own. A negative count does not: the JVM wraps that too and
        // shifts by 63, where R3 shifts by nothing at all.
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
        // The Euclidean form takes the divisor's magnitude first and then
        // shares the floored form's arithmetic, which is what makes the two
        // agree whenever the divisor is positive.
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
            // Through a plain BigDecimal rather than valueOf, which
            // carries the double's scale and molds $1 as $1.0.
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
            // `VAL_INT64(a) >>= 63` rather than answering zero, so the sign
            // survives a shift wider than the word.
            return rightwards >= Long.SIZE ? value >> (Long.SIZE - 1) : value >> rightwards;
        }
        if (places >= Long.SIZE) {
            if (value != 0) {
                throw Raised.of(EvaluationFailure.OVERFLOW,
                        "shifting " + value + " left by " + places + " loses every bit");
            }
            return 0;
        }
        // `c` is the largest magnitude that still fits after the shift, worked
        // out as the sign bit shifted down; `d` is the magnitude being shifted.
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
        if (left instanceof LogicValue leftTruth && right instanceof LogicValue rightTruth) {
            boolean ours = leftTruth.isTruthy();
            boolean theirs = rightTruth.isTruthy();
            return LogicValue.of(switch (operation) {
                case AND -> ours && theirs;
                case OR -> ours || theirs;
                case XOR -> ours ^ theirs;
            });
        }
        // A pair meets a pair or a plain number, and each half is rounded to
        // a whole number before the bits are combined. `16x15.6 or 16x4` is
        // 16x20 because the 15.6 rounds to 16; truncating gives 15, and
        // 15 or 4 is 15, so the answer would be 16x15 and look near enough
        // right to pass review.
        if (left instanceof PairValue leftPair) {
            return PairValue.of(
                    combinedBits(roundedHalfUp(leftPair.x()),
                            roundedHalfUp(firstHalfOf(right)), operation),
                    combinedBits(roundedHalfUp(leftPair.y()),
                            roundedHalfUp(secondHalfOf(right)), operation));
        }
        // The whole integer meets each octet and the answer clamps
        // afterwards, so `1.2.3.255 or -1` is 0.0.0.0 rather than the
        // 255s that cutting the integer to a byte first would give.
        if (left instanceof TupleValue) {
            return tupleCombined(left, right, operation);
        }
        return IntegerValue.of(combinedBits(
                wholeNumberOf(left, "and"), wholeNumberOf(right, "and"), operation));
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
        // `if (IS_CHAR(D_ARG(2))) { DS_RET_INT(chr); return R_RET; }` -- the
        // distance between two characters is a count and not a character.
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
            int picked = 1 + randomness.nextInt(limit);
            boolean surrogate = picked >= 0xD800 && picked <= 0xDFFF;
            if (!surrogate && picked <= MAXIMUM_CODEPOINT) {
                return picked;
            }
        }
    }

    /** A whole number between zero and the limit, keeping its sign. */
    private long randomLongUpTo(long limit) {
        if (limit == 0) {
            return 0;
        }
        long picked = 1 + (long) (randomness.nextDouble() * Math.abs(limit));
        return limit < 0 ? -picked : picked;
    }

    /**
     * A date with every part randomised inside its own range.
     *
     * <p>The C randomises the year, then the month, then the day, then the
     * seconds, each with {@code Random_Range} of that part alone. So the answer
     * is a date no later than this one in every part rather than a random
     * instant before it, and a date in January can only come back in January.
     */
    private Value randomisedDate(DateValue when) {
        int year = (int) randomLongUpTo(when.year());
        int month = (int) Math.max(1, randomLongUpTo(when.month()));
        int day = (int) Math.max(1, randomLongUpTo(when.day()));
        return when.timeOfDay().isEmpty()
                ? DateValue.of(year, month, day)
                : new DateValue(year, month, day,
                        java.util.Optional.of(TimeValue.ofNanoseconds(
                                randomLongUpTo(when.timeOfDay().orElseThrow()
                                        .nanoseconds()))),
                        when.zoneMinutes());
    }

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
     * Integer arithmetic raises on overflow rather than wrapping. The JVM
     * wraps silently, which is the worst available behaviour: a wrong answer
     * that looks like a right one.
     */
    private static Value arithmetic(List<Value> arguments, Operation operation) {
        Value left = arguments.get(0);
        Value right = arguments.get(1);

        // A character on the LEFT is its own arithmetic, and the answer is a
        // character: `case A_ADD: chr += arg; break;` in REBTYPE(Char), with the
        // whole dispatcher ending in `SET_CHAR(DS_RETURN, chr)`. So `#"a" + 1`
        // is #"b" and not 98. The one exception is written into the C beside it:
        // subtracting one character from another answers the distance as an
        // integer, because that is the only useful thing it could be.
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
        // A character on the right of a number is its code point, and the
        // answer keeps the number's datatype except that a percent gives a
        // plain decimal: REBTYPE(Decimal) sets `type = REB_DECIMAL` for a
        // char, and REBTYPE(Integer) leaves the integer alone. So
        // `1 + #"a"` is 98, `1.0 * #"a"` is 97.0 and `100% * #"a"` is 97.0.
        //
        // A character on the LEFT is a different question with a different
        // answer -- `#"a" + 1` is #"b" -- so this is not symmetric and must
        // not be made so.
        //
        // A money does not widen a character either. REBTYPE(Money) names
        // integer, decimal, percent and time for the right side and stops, so
        // `$1 / #"a"` raises where `1.0 / #"a"` answers.
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
        // A money on the left decides, even against a time on the right,
        // because REBTYPE(Money) runs before anything asks the time what it
        // thinks. `$5 * 1:30:0` is $7.5 and not a duration, and `$5 + 1:30:0`
        // raises rather than adding five seconds. Letting the time branch go
        // first gave both of those the wrong answer, and the wrong answer was
        // a plausible time in each case.
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
        // Only here may a division by zero answer an infinity. Pairs,
        // money and time all still raise, so the licence belongs to the
        // one call site where the result is a plain decimal rather than
        // to the arithmetic itself.
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
                // MODULO never answers a negative, which is where it parts
                // company with REMAINDER: remainder -7 3 is -1 and modulo
                // -7 3 is 2. The divisor's sign does not carry across
                // either, so modulo 7 -3 is 1 rather than -2.
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
            // Dividing by zero raises only when both sides are whole
            // numbers; with a decimal on either side the hardware answer
            // stands, so `1.0 / 0` is 1.#INF and `0.0 / 0.0` is 1.#NaN.
            // A pair, money or time value divided by zero still raises,
            // which is why the caller says whether that applies here.
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
        // The C guards both halves of the divisor before dividing either, so
        // `1x1 / 1x0` raises rather than dividing the x half and then
        // stopping. The distinction is invisible in the answer and visible in
        // the order things happen, which is what a partial answer would show.
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
        // Trap_Math_Args raises not-related, whose catalogue wording is
        // "incompatible argument for <action> of <datatype>". Not cannot-use,
        // which is a different id a script may be matching on.
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
                        // A zero octet is left alone and a factor above
                        // 255 saturates before the multiplication rather
                        // than after it, so no intermediate ever leaves
                        // the range of a machine integer.
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
                        // A whole divisor truncates and a fractional one
                        // rounds half away from zero, so 1 / 2 is 0 and
                        // 1 / 0.625 is 2 rather than 1.
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
                octets[at] = randomness.nextInt(octets[at] + 1);
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
        return (1 + (long) (randomness.nextDouble() * Math.abs(bound))) * Long.signum(bound);
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
     * Arithmetic on a date, in days.
     *
     * <p>A bare number is days here where it was seconds for a time, so
     * the same spelling means a different unit either side. Subtracting
     * two dates gives a whole number rather than a date, because the
     * difference of two moments is a span.
     */
    private static Value dateArithmetic(Value left, Value right, Operation operation) {
        if (left instanceof DateValue from && right instanceof DateValue to) {
            if (operation != Operation.SUBTRACT) {
                return raiseCannotUse(left, "date arithmetic");
            }
            return IntegerValue.of(dayNumberOf(from) - dayNumberOf(to));
        }
        DateValue moment = left instanceof DateValue date ? date : (DateValue) right;
        long days = (long) Comparison.asDouble(left instanceof DateValue ? right : left);
        long moved = operation == Operation.SUBTRACT
                ? dayNumberOf(moment) - days
                : dayNumberOf(moment) + days;
        java.time.LocalDate shifted = java.time.LocalDate.ofEpochDay(moved);
        return DateValue.of(shifted.getYear(), shifted.getMonthValue(), shifted.getDayOfMonth());
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

    // ---- comparison ------------------------------------------------------

    private void defineComparison() {
        // Ten natives, six questions. Each is one call to Comparison.holds
        // with its strictness, and the negating half asks the same question
        // and flips the answer rather than asking its own -- which is what
        // Compare_Values does, and what keeps `<` refusing the pairings
        // `>=` refuses.
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
        // =? is SAME? rather than EQUAL?: it asks whether two references are
        // one value, so `"a" =? "a"` is false.
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

    // ---- control ---------------------------------------------------------

    private void defineControl() {
        // /ONLY hands the branch back without running it, so
        // `if/only true [1]` is the block rather than 1. It is how a
        // caller picks between two blocks with neither being evaluated.
        define("if", List.of(Parameter.required("condition", ANYTHING),
                        Parameter.required("branch", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    if (!arguments.get(0).isTruthy()) {
                        return NoneValue.none();
                    }
                    return branchTaken((BlockValue) arguments.get(1), evaluator, context,
                            refinements);
                });

        define("either", List.of(Parameter.required("condition", ANYTHING),
                        Parameter.required("true-branch", Set.of(Datatype.BLOCK)),
                        Parameter.required("false-branch", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue taken = (BlockValue) (arguments.get(0).isTruthy()
                            ? arguments.get(1)
                            : arguments.get(2));
                    return branchTaken(taken, evaluator, context, refinements);
                });

        define("not", takesAnything("value"),
                (arguments, evaluator, context) -> {
                    return LogicValue.of(!arguments.get(0).isTruthy());
                });

        // /NEXT evaluates one expression and moves the given word on to
        // what is left, so a caller can walk a block an expression at a
        // time. It takes a word rather than answering a pair because the
        // caller almost always wants to keep stepping the same variable.
        // /ARGS carries what a script reads as `system/script/args`, and the
        // vendored `sys/do*` is what puts it there: `system/script: make
        // system/standard/script compose [... args: :arg]`. DO declared the
        // refinement and no argument for it, so a caller could ask for /ARGS and
        // had nowhere to put the value -- which reads as the script being
        // handed nothing.
        define("do", List.of(Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("args", "arg", Set.of()),
                        Parameter.belongingTo("next", "var", Set.of(Datatype.WORD))),
                Set.of("next", "args"),
                (arguments, evaluator, context, refinements) -> {
                    // Set before the value runs, so the script sees it. A script
                    // reads `system/script/args` and nothing else, so this is
                    // the whole of what /ARGS does.
                    if (refinements.contains("args") && arguments.size() > 1) {
                        Value given = argumentFor("args", List.of("args", "next"),
                                arguments, refinements, 1);
                        recordTheScriptArguments(evaluator, given);
                    }
                    if (refinements.contains("next") && arguments.size() > 1
                            && arguments.getLast() instanceof WordValue var) {
                        Value value = arguments.getFirst();
                        // A loadable source steps like a block: the C routes
                        // strings through sys do*, which loads and then runs
                        // `do/next body mark`. Anything that is neither a
                        // block nor loadable answers itself and sets the
                        // word to none -- `Set_Var(D_ARG(5), NONE_VALUE)`.
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
                        // A top-level RETURN in a do'd script unwinds to the
                        // DO itself: the C runs sys/do*, a plain FUNC, so
                        // the return lands there and DO answers its value.
                        case StringValue text -> {
                            try {
                                yield evaluator.evaluateSource(text.text());
                            } catch (ReturnSignal returned) {
                                yield returned.value();
                            }
                        }
                        // A binary is a script: the C routes it through
                        // sys/do* -> load/header, which honors the header's
                        // length and refuses an unmet needs.
                        case BinaryValue bytes ->
                                doneAsAScript(bytes, evaluator, context);
                        // DO of an error raises it. That is how a script
                        // raises an error it built itself, and Rebol's own
                        // CAUSE-ERROR is written as `do make error! [...]`
                        // and nothing else. Answering the error as a value
                        // makes every such call do nothing at all, and the
                        // value is then dropped when anything follows it.
                        case ErrorValue built -> throw new Raised(built);
                        // A word or a get-word is looked up: `*D_RET =
                        // *Get_Var(value);`. So `do 'a` is what A holds, which
                        // is how a script runs a name it was handed. A path is
                        // the same question one segment deeper.
                        case WordValue named when named.datatype() == Datatype.WORD
                                || named.datatype() == Datatype.GET_WORD ->
                                evaluator.valueOfWordIn(named, context);
                        // A lit-word answers the plain word rather than what it
                        // names, and a lit-path the plain path:
                        // `*D_RET = *value; SET_TYPE(D_RET, REB_WORD);`. That is
                        // the one step of evaluation a quoted value has left.
                        case WordValue quoted when quoted.datatype() == Datatype.LIT_WORD ->
                                quoted.as(Datatype.WORD);
                        case BlockValue quoted when quoted.datatype() == Datatype.LIT_PATH ->
                                quoted.as(Datatype.PATH);
                        case BlockValue path when path.datatype() == Datatype.PATH ->
                                evaluator.valueOfPathIn(path, context);
                        // A set-word or a set-path is refused rather than
                        // answered: `case REB_SET_WORD: case REB_SET_PATH:
                        // Trap_Arg(value);`. There is nothing after it to
                        // assign, so the caller has handed over half an
                        // expression.
                        case WordValue assigning
                                when assigning.datatype() == Datatype.SET_WORD ->
                                raiseHalfAnExpression(assigning);
                        case BlockValue assigning
                                when assigning.datatype() == Datatype.SET_PATH ->
                                raiseHalfAnExpression(assigning);
                        default -> arguments.getFirst();
                    };
                });

        // ANY and ALL, copied from n-control.c. Both walk the block one
        // expression at a time and both pass over an unset without
        // letting it decide anything -- `if (IS_UNSET(ds)) continue` in
        // each. That is what lets a block hold a call that answers
        // nothing without the whole test changing meaning.
        //
        // ANY answers the first value that is neither false nor unset,
        // and none when there is none.
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

        // ALL answers none as soon as anything is false, and otherwise
        // the last value that was not unset. An empty block, or one whose
        // every expression answered unset, leaves nothing to answer with
        // and the answer is unset itself -- `R_RET` with nothing stored.
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
                        Parameter.required("branch", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0).isTruthy()) {
                        return NoneValue.none();
                    }
                    return branchTaken((BlockValue) arguments.get(1), evaluator, context,
                            refinements);
                });

        // SWITCH compares the value against each choice in turn and runs the
        // block after the first that matches. Nothing matching is NONE.
        define("switch", List.of(Parameter.required("value"),
                        Parameter.required("choices", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("default", "fallback", Set.of(Datatype.BLOCK))),
                Set.of("case", "default", "all"),
                (arguments, evaluator, context, refinements) -> {
                    // Walked one value at a time rather than in pairs, and a
                    // value that matched runs the next block there is rather
                    // than the item straight after it. The C is four lines
                    // and the second one is the whole of it:
                    //     if (!IS_BLOCK(blk) && 0 == Cmp_Value(...)) {
                    //         for (; !IS_BLOCK(blk) && NOT_END(blk); blk++);
                    //         if (IS_END(blk)) break;
                    //         result = DO_BLK(blk);
                    //
                    // So several values may share one block by writing them
                    // one after another with no block between. Rebol's own
                    // JSON codec does exactly that:
                    //     integer!
                    //     decimal! [append output value]
                    //
                    // Walking in pairs read INTEGER! against DECIMAL! and
                    // matched nothing, so `to-json 5` answered an empty
                    // string with no error to say why.
                    List<Value> choices = ((BlockValue) arguments.get(1)).remaining();
                    boolean runsThemAll = refinements.contains("all");
                    boolean matchedSomething = false;
                    Value lastBranchTaken = NoneValue.none();
                    for (int at = 0; at < choices.size(); at++) {
                        // A block is never a choice, only a branch. A paren
                        // stays a choice: the C's IS_BLOCK is block! exactly,
                        // so `(1 2 3)` matches an equal paren, unevaluated.
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
                        // A match with no block after it anywhere answers
                        // none, as though nothing had matched at all.
                        if (branchAt >= choices.size()) {
                            break;
                        }
                        matchedSomething = true;
                        lastBranchTaken = evaluator.evaluateOrRaise(
                                (BlockValue) choices.get(branchAt), context);
                        if (!runsThemAll) {
                            return lastBranchTaken;
                        }
                        // Resume after the block, so the branch's own
                        // contents are never read as further choices.
                        at = branchAt;
                    }
                    if (matchedSomething) {
                        return lastBranchTaken;
                    }
                    // /DEFAULT names the branch for when nothing matched.
                    // Without it the answer is none, which a caller cannot
                    // tell from a branch that answered none itself.
                    Value fallback = argumentFor(
                            "default", List.of("default"), arguments, refinements, 2);
                    if (fallback instanceof BlockValue branch) {
                        return evaluator.evaluateOrRaise(branch, context);
                    }
                    return NoneValue.none();
                });

        // CASE takes condition and block in pairs and runs the first block
        // whose condition is true, evaluating no further conditions.
        // /ALL runs every branch whose condition holds rather than
        // stopping at the first, and answers the last one it ran. With
        // nothing matching it answers none either way, and a branch whose
        // block is empty leaves the whole thing answering unset.
        define("case", List.of(Parameter.required("choices", Set.of(Datatype.BLOCK))),
                Set.of("all"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue choices = (BlockValue) arguments.get(0);
                    BlockValue at = choices;
                    boolean runsThemAll = refinements.contains("all");
                    Value lastTaken = NoneValue.none();

                    // A condition is an expression of however many values it
                    // takes, not one value, so this steps through rather than
                    // pairing off. `case [size < 10 ["small"]]` is four values
                    // and only the first three are the condition.
                    while (!at.atTail()) {
                        Evaluator.Step condition = evaluator.evaluateNextOrRaise(at, context);
                        BlockValue afterCondition = at.atIndex(condition.nextIndex());
                        // A truthy condition with nothing after it answers
                        // logic true. `if (index >= SERIES_TAIL(block)) return
                        // R_TRUE;` in the C, and it is what makes a trailing
                        // expression a default clause whose side effect is the
                        // point: Rebol's own CLEAN-PATH ends
                        // `case [ ... file: append what-dir file ]`, with no
                        // block, to do the assignment.
                        //
                        // Raising here instead stopped CLEAN-PATH from
                        // loading, and CLEAN-PATH is in the file every other
                        // file function comes from.
                        if (afterCondition.atTail()) {
                            // Only a truthy one. A false condition at the end
                            // does `index++`, the loop runs out and the C
                            // reaches `return R_NONE`, so `case [false]` is
                            // none where `case [true]` is true.
                            return condition.value().isTruthy()
                                    ? LogicValue.of(true)
                                    : lastTaken;
                        }
                        Value branch = afterCondition.first();
                        if (condition.value().isTruthy()) {
                            Value taken = branch instanceof BlockValue block
                                    ? evaluator.evaluateOrRaise(block, context)
                                    : branch;
                            if (!runsThemAll) {
                                return taken;
                            }
                            lastTaken = taken;
                        }
                        at = afterCondition.atIndex(afterCondition.index() + 1);
                    }
                    return lastTaken;
                });

        // ATTEMPT is TRY with the error swallowed: the value, or none.
        // /SAFER widens ATTEMPT the way /ALL widens TRY. Both forms
        // answer none, so the refinement changes what is caught and never
        // what a caught thing looks like.
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

        // /ALL widens TRY to the control-flow signals as well, so a throw
        // or a break becomes an ordinary error value. Plain TRY still
        // catches only failures: a throw is a decision, and having to ask
        // for the wider net keeps the default honest.
        //
        // /WITH runs a handler instead of answering the error, and only on
        // a failure, so it is a handler rather than a finally.
        define("try", List.of(
                        Parameter.required("block", Set.of(Datatype.BLOCK, Datatype.PAREN)),
                        Parameter.belongingTo("with", "handler", Set.of())),
                Set.of("all", "with"),
                (arguments, evaluator, context, refinements) -> {
                    // Reset on the way in, whatever happens next:
                    // `REBVAL *error = Get_System(SYS_STATE, STATE_LAST_ERROR);
                    // SET_NONE(error); // reset the last error`. So a TRY that
                    // succeeds clears whatever the last one left, and a caller
                    // reading last-error afterwards is reading this call.
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
                        // `Make_Error(code, arg1, sym ? &word : 0, 0)` in
                        // Disarm_Throw_Error: the value thrown becomes arg1 and
                        // the name becomes arg2, so a handler reading the error
                        // can see what was thrown and where it was addressed.
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
                        // A continue with no loop around it is its own
                        // error rather than a break by another name, so a
                        // caller catching it can tell which word was used.
                        if (!refinements.contains("all")) {
                            throw skipped;
                        }
                        failure = ErrorValue.of(ErrorCategory.THROW, "continue",
                                "a continue outside a loop");
                    } catch (ReturnSignal returned) {
                        if (!refinements.contains("all")) {
                            throw returned;
                        }
                        // RETURN carries a value too, and EXIT carries unset --
                        // which is why arg1 is none for `try/all/with [exit]`
                        // and the returned value for `[return 1]`.
                        failure = ErrorValue.about(ErrorCategory.THROW, "return",
                                "a return outside a function",
                                returned.value() instanceof UnsetValue
                                        ? NoneValue.none()
                                        : returned.value());
                    }
                    // Recorded so a handler written as a block, which has
                    // no argument to read, can still reach what it is
                    // handling.
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

    // ---- leaving early ---------------------------------------------------
    //
    // Three ways out, each stopping somewhere different. BREAK leaves one
    // loop, RETURN leaves one function, THROW leaves everything up to the
    // nearest CATCH. None of them is an error, so TRY catches none of them.

    private void defineNonLocalExit() {
        define("return", takesAnything("value"),
                (arguments, evaluator, context) -> {
                    throw new ReturnSignal(arguments.get(0));
                });

        // EXIT is RETURN with nothing, so the caller gets UNSET rather than
        // NONE: a function that returned nothing is not one that returned
        // the value none.
        define("exit", List.of(),
                (arguments, evaluator, context) -> {
                    throw new ReturnSignal(UnsetValue.unset());
                });

        // /NAME addresses the throw, and only a CATCH expecting that name
        // takes it. Without a name it is caught by an unnamed CATCH and by
        // nothing else.
        define("throw", List.of(Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("name", "word", Set.of(Datatype.WORD))),
                Set.of("name"),
                (arguments, evaluator, context, refinements) -> {
                    throw new ThrownSignal(arguments.getFirst(),
                            refinements.contains("name") && arguments.size() > 1
                                    ? ((WordValue) arguments.get(1)).canonical()
                                    : null);
                });

        // An unnamed CATCH is not a catch-all. A throw addressed to an
        // outer handler travels past an inner one that was not expecting
        // it, which is the whole reason for naming a throw; /ALL is the
        // deliberate catch-all and is a separate refinement because
        // catching somebody else's throw is almost always a bug.
        define("catch", List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("name", "word",
                                Set.of(Datatype.WORD, Datatype.BLOCK)),
                        Parameter.belongingTo("with", "callback", Set.of())),
                Set.of("name", "all", "quit", "with"),
                (arguments, evaluator, context, refinements) -> {
                    Value handled;
                    // The name the throw carried, which the handler is
                    // given alongside the value. An unnamed throw gives
                    // none rather than nothing, so a handler can ask.
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
                        // The one thing nothing else can catch. It lets a
                        // host run a script that stops itself without the
                        // stopping reaching the host.
                        if (!refinements.contains("quit")) {
                            throw quit;
                        }
                        handled = quit.answer();
                        // The C tells the user where the exit came from:
                        //     SET_LOGIC(Get_System(SYS_STATE, STATE_QUITQ), TRUE);
                        // so a caller can tell a caught quit from a caught
                        // throw that happened to answer the same value.
                        runState.set("quit?", LogicValue.of(true));
                    } catch (HaltRequested halted) {
                        // /QUIT catches HALT as well, and answers unset. Two
                        // arms of one branch in the C -- `Try_Block_Halt`
                        // catches both, and then:
                        //     if (VAL_ERR_NUM(ret) == RE_QUIT) ... quit value
                        //     else if (VAL_ERR_NUM(ret) == RE_HALT)
                        //         VAL_SET(DS_RETURN, REB_UNSET);
                        //
                        // A halt carries no value, so there is nothing else it
                        // could answer.
                        if (!refinements.contains("quit")) {
                            throw halted;
                        }
                        handled = UnsetValue.unset();
                    }
                    runState.set("last-result", handled);
                    // /WITH runs on a throw and not on the way out, so it
                    // is a handler rather than a finally.
                    if (refinements.contains("with")) {
                        Value handler = arguments.getLast();
                        // A block handler reads the caught value through
                        // system/state/last-result, which is why last-result
                        // is set before it runs -- and is set again to the
                        // handler's own result afterwards:
                        //     *last_result = *DO_BLK(&callback);
                        // So `catch/with [throw 1][2 * system/state/last-result]`
                        // answers 2 and leaves last-result at 2.
                        // Only a block handler writes its result back into
                        // last-result: the C reassigns it after DO_BLK but
                        // leaves it at the caught value for a function
                        // handler, which takes the value as an argument
                        // rather than reading it from state.
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

    // ---- making functions ------------------------------------------------

    private void defineFunctionMaking() {
        // FUNC takes its spec and body unevaluated, which is why they are
        // blocks in the source and blocks here: a spec that had been
        // evaluated would have looked its own words up.
        define("func", List.of(
                        Parameter.required("spec", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> makeFunction(
                        (BlockValue) arguments.get(0),
                        (BlockValue) arguments.get(1),
                        context));

        // FUNCTION takes a spec and a body, and works the locals out
        // from the body: every set-word in it is one. That is the whole
        // difference from FUNC, and it is why there is no third argument
        // -- JEBOL had the R3-Alpha shape, which declared them.
        //
        // /with gives the function an object to work in, and the object
        // outlives the call, so it is where state that survives between
        // calls goes.
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
                    // A word the object holds is not a local. Making it
                    // one shadows the field, and then the state the
                    // object exists to keep is thrown away every call.
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

    // ---- objects ---------------------------------------------------------

    private void defineObjects() {
        // MAKE takes a prototype and a body. The prototype is either the
        // datatype object!, for something new, or an existing object, which
        // is copied rather than linked: REBOL objects have no live
        // inheritance, so changing a child leaves its parent alone.
        define("make", takesAnything("prototype", "body"),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    // A number is how big to make it rather than what to
                    // put in it, so it answers an empty object. Rebol's
                    // own WRAP asks for `make object! 0`, and the cast
                    // this used to do turned that into a Java exception.
                    case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT
                            && !(arguments.get(1) instanceof BlockValue) ->
                            makeObject(evaluator, context, Optional.empty(),
                                    BlockValue.block(List.of()));
                    case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT ->
                            makeObject(evaluator, context, Optional.empty(),
                                    (BlockValue) arguments.get(1));
                    // Two objects merge rather than one being read as a
                    // body. The second is already built, so there is
                    // nothing to evaluate: its fields are written over the
                    // prototype's and its extra ones added.
                    case ObjectValue prototype when arguments.get(1) instanceof ObjectValue other ->
                            mergedObject(prototype, other, context);
                    case ObjectValue prototype ->
                            makeObject(evaluator, context, Optional.of(prototype),
                                    (BlockValue) arguments.get(1));
                    // A number is how much room to make rather than what to
                    // put in, so it answers an empty map -- the same reading
                    // MAKE OBJECT! gives a number above. `else if
                    // (IS_NUMBER(arg)) { if (action == A_TO) Trap_Arg(arg);
                    // n = Int32s(arg, 0); }`, and the map is made with room
                    // for n. TO MAP! refuses a number on that same line, which
                    // is why only MAKE reads it this way.
                    //
                    // Rebol's own codec-mime-types.reb opens with
                    // `make map! 111` and stopped there, taking the whole
                    // MIME type table with it.
                    case DatatypeValue wanted when wanted.represents() == Datatype.MAP ->
                            mapMadeFrom(arguments.get(1));
                    case DatatypeValue wanted when wanted.represents() == Datatype.BITSET ->
                            bitsetOf(arguments.get(1));
                    case DatatypeValue wanted when wanted.represents() == Datatype.PAIR ->
                            asPair(arguments.get(1));
                    // MAKE FUNCTION! is what every other way of writing a
                    // function is built on. R3 defines FUNC, FUNCTION,
                    // USE, CLOSURE and DOES in REBOL on top of it, so an
                    // implementation that does not expose it cannot be
                    // extended with a new way of making functions without
                    // being changed itself.
                    // MAKE CLOSURE! is the same constructor. Rebol has two
                    // datatypes because its FUNC frame dies with the call and
                    // its closure frame does not; JEBOL's frame already
                    // outlives the call, so the two mean the same thing here.
                    // That is JEBOL's FUNC being wrong rather than this being
                    // right, and the note on CLOSURE in the prelude says so.
                    //
                    // It has to exist because Rebol's own mezz-func.reb ends
                    // CLOSURE with `make closure! reduce [spec body]`, and
                    // without it that file stops there -- taking LIST-DIR,
                    // and then everything mezz-shell.reb defines, with it.
                    case DatatypeValue wanted when wanted.represents() == Datatype.FUNCTION ->
                            functionFrom(arguments.get(1), context);
                    // A closure's call frame is a real object, which is the
                    // whole observable difference here: CONTEXT? of its
                    // words answers that object rather than the function.
                    case DatatypeValue wanted when wanted.represents() == Datatype.CLOSURE ->
                            functionFrom(arguments.get(1), context)
                                    instanceof FunctionValue made
                                    ? made.asClosure()
                                    : NoneValue.none();
                    case DatatypeValue wanted when wanted.represents() == Datatype.ERROR ->
                            errorFromSpec(arguments.get(1), evaluator, context);
                    // A string over an existing error re-messages it: the
                    // answer is the User category's message entry, arg1 the
                    // text, whatever the prototype was.
                    case ErrorValue prototype
                            when arguments.get(1) instanceof StringValue ->
                            errorFromSpec(arguments.get(1), evaluator, context);
                    // MAKE MODULE! does not build the module. It hands the
                    // spec to MAKE-MODULE* in sys-base.reb, exactly as MAKE
                    // PORT! hands its spec to MAKE-PORT*, and for the same
                    // reason: Rebol's C does. Make_Module is four lines and
                    // one of them is
                    // `Do_Sys_Func(SYS_CTX_MAKE_MODULE_P, spec, 0)`.
                    //
                    // Those ninety lines of REBOL are where the EXPORT and
                    // HIDDEN keywords in a module body are handled, where the
                    // header is checked, and where the choice between a
                    // shared and an isolated namespace is made. A copy of
                    // them here would be a second set of answers.
                    case DatatypeValue wanted when wanted.represents() == Datatype.MODULE ->
                            moduleFromSpec(arguments.get(1), evaluator, context);
                    // MAKE on any other datatype converts, exactly as TO
                    // does, with one addition: a number is a capacity
                    // hint rather than a value, so `make block! 5` is an
                    // empty block and not a block holding 5. That is the
                    // whole difference between MAKE and TO.
                    // MAKE on a function keeps what it does and replaces
                    // what it says about itself. The argument is a block
                    // holding a spec block, which is why a bare body is
                    // refused. Rebol's own library derives EMPTY? from
                    // TAIL? this way, to widen the declared interface
                    // without writing the behaviour twice.
                    case NativeValue original when arguments.get(1) instanceof BlockValue given ->
                            derivedFunction(original, given);
                    case FunctionValue original when arguments.get(1) instanceof BlockValue given ->
                            derivedFunction(original, given);
                    // MAKE takes a value as readily as a datatype, and reads
                    // the value's own datatype off it:
                    //     type = VAL_TYPE(value);
                    //     if (type == REB_DATATYPE) type = VAL_DATATYPE(value);
                    // Two lines, and the first is the one to notice. Rebol's
                    // own CLEAN-PATH writes `out: make file length? file` with
                    // the comment "same datatype", because it wants an empty
                    // series of whatever kind it was handed and does not know
                    // which that is.
                    case SeriesValue prototype -> {
                        // `if (IS_NONE(arg)) Trap_Make(type, arg);` is the
                        // first line of the arm, before the datatype is even
                        // worked out, so it applies to the prototype form too.
                        if (arguments.get(1) instanceof NoneValue) {
                            yield raiseBadMakeArg(arguments.get(1),
                                    prototype.datatype().literalSpelling());
                        }
                        yield makeOfDatatype(DatatypeValue.of(prototype.datatype()),
                                arguments.get(1), evaluator, context);
                    }
                    // An event is not a series, so it needs its own arm to be a
                    // prototype: `if (IS_EVENT(value) || IS_DATATYPE(value))` in
                    // `REBTYPE(Event)` takes either. It contributes nothing to the
                    // result -- the arm clears the event before filling it -- so
                    // `make some-event [...]` is a fresh event and not a copy.
                    case EventValue prototype -> EventPath.made(prototype,
                            arguments.get(1),
                            value -> simpleValueOf(value, evaluator, context));
                    case DatatypeValue wanted ->
                            makeOfDatatype(wanted, arguments.get(1), evaluator, context);
                    default -> raiseCannotUse(arguments.get(0), "make");
                });

        // CONSTRUCT builds an object from a block without evaluating it,
        // so the values arrive as written. For data that happens to be
        // shaped like a spec, where evaluating would be wrong or unsafe.
        // CONSTRUCT does not evaluate, but it still reads NONE, TRUE and
        // FALSE as the values they name. /ONLY turns even that off, which
        // is the point of the plainer form: data that arrived from
        // somewhere untrusted and happens to be shaped like a spec should
        // not have its words quietly become values.
        define("construct", List.of(
                        Parameter.required("body", Set.of(Datatype.BLOCK,
                                Datatype.STRING, Datatype.BINARY)),
                        Parameter.belongingTo("with", "object", Set.of(Datatype.OBJECT))),
                Set.of("only", "with"),
                (arguments, evaluator, context, refinements) -> {
                    Context built = Context.childOf(evaluator.systemContext());
                    Value body = arguments.getFirst();
                    // A string or a binary is not source: it is an
                    // internet-style header, and every field in one is
                    // text. The C reads it into a block of set-word and
                    // string pairs first and then builds the object from
                    // that, so the two paths meet again here.
                    List<Value> items = body instanceof BlockValue block
                            ? block.remaining()
                            : headerFieldsIn(body instanceof StringValue text
                                    ? text.text()
                                    : textOfBytes((BinaryValue) body));
                    // /WITH names a prototype, and the C makes it the parent:
                    // `if (D_REF(2)) parent = VAL_OBJ_FRAME(D_ARG(3));` and
                    // then `Construct_Object(parent, ...)`. So the result
                    // holds every field the prototype has, and the block
                    // overwrites the ones it mentions.
                    //
                    // The refinement was declared here and ignored, which
                    // made `construct/with` a plain CONSTRUCT. MAKE-MODULE*
                    // opens with `construct/with :spec system/standard/header`
                    // and then reads spec/name, so a header that did not
                    // mention NAME had no such field and the read raised
                    // invalid-path.
                    if (refinements.contains("with") && arguments.size() > 1
                            && arguments.get(1) instanceof ObjectValue prototype) {
                        prototype.context().fieldsExcludingSelf()
                                .forEach(built::set);
                    }
                    constructInto(built, items, refinements.contains("only"));
                    return new ObjectValue(built);
                });

        // CONTEXT? answers the object a bound word lives in, which is how
        // code holding a word can reach the rest of what surrounds it.
        // `word [any-word!]` -- a set-word and a lit-word carry a binding
        // just as a plain word does, and code asking where one lives has no
        // reason to convert it first.
        define("context?", List.of(Parameter.required("word", Typeset.ANY_WORD.members())),
                (arguments, evaluator, context) -> {
                    WordValue word = (WordValue) arguments.getFirst();
                    if (!word.isBound() || word.binding().isALoopFrame()) {
                        // A loop's own frame is internal, not an object:
                        // `if (IS_INT_SERIES(VAL_WORD_FRAME(word))) return
                        // R_NONE;` names `foreach x [1] [context? 'x]`.
                        return NoneValue.none();
                    }
                    // A word bound into a call frame answers the function
                    // itself, so a body can reach its own spec.
                    if (word.binding().functionOwningThisFrame() != null) {
                        return word.binding().functionOwningThisFrame();
                    }
                    return new ObjectValue(word.binding());
                });

        // RESOLVE fills in only what the target has no value for. /ALL
        // overwrites what it has, /EXTEND adds the words it lacks. Without
        // /EXTEND a word only the source has is skipped rather than added.
        // `target [any-object!] source [any-object!]`, so a module counts:
        // Rebol's IMPORT ends by resolving a module's exports into lib, and
        // OBJECT! alone refused it.
        // /ONLY says which words may be written, in either of two shapes:
        // `from [block! integer!]` -- a block of the words to copy, or a
        // position in the target from which its words are new. The C marks
        // both in one bind table, so one rule follows from either: a word
        // that /ONLY named and the source has not got is UNSET in the target
        // rather than left alone. Without /ONLY the same word is left as it
        // was, because nothing marked it.
        define("resolve", List.of(
                        Parameter.required("target", Typeset.ANY_OBJECT.members()),
                        Parameter.required("source", Typeset.ANY_OBJECT.members()),
                        Parameter.belongingTo("only", "from",
                                Set.of(Datatype.BLOCK, Datatype.INTEGER))),
                Set.of("only", "all", "extend"),
                (arguments, evaluator, context, refinements) -> {
                    Context into = fieldsOf(arguments.getFirst());
                    // Filling an object from another changes it as a
                    // container, so a target protected as a container
                    // raises `protected` rather than the `locked-word` a
                    // slot would give. Same split APPEND and PUT are on.
                    //
                    // The test is whether the object is closed, not
                    // whether any slot is guarded: an object with one
                    // hidden field is still open, and Rebol's own suite
                    // resolves into exactly that.
                    if (into.isClosedToNewNames()) {
                        throw Raised.of(EvaluationFailure.PROTECTED, "resolve");
                    }
                    return resolvedFrom(into, fieldsOf(arguments.get(1)),
                            arguments.getFirst(), refinements,
                            argumentFor("only", List.of("only"),
                                    arguments, refinements, 2));
                });

        // USE makes a context holding the words it is given, binds the
        // block to it and evaluates it there. A word it names shadows an
        // outer one and leaves it untouched, which is what makes this a
        // scope rather than an assignment. It is how REBOL scopes local
        // words outside a function.
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

        // /ONLY is accepted and makes no difference to the answer, which
        // is what a real R3 does with it too.
        define("context", List.of(Parameter.required("body", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> makeObject(
                        evaluator, context, Optional.empty(), (BlockValue) arguments.get(0)));

        // IN gives a word bound to the object's own context, which is how a
        // field can be reached by a name worked out at runtime rather than
        // written into a path.
        // Three forms under one name, and Rebol's C tells them apart by which
        // argument is a block.
        //
        //   in <object> <word>   the word bound to the object, or none
        //   in <object> <block>  the block bound to the object, and the block
        //   in <block> <word>    the first object in the block holding it
        //
        // The middle one the C calls "Special form: IN object block". Reading
        // the specification as "IN takes an object and a word" gets two of the
        // three wrong.
        //
        // An error and a port are objects underneath and answer as one does:
        // `frame = IS_ERROR(val) ? VAL_ERR_OBJECT(val) : VAL_OBJ_FRAME(val)`.
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
                    // A block as the second argument is the special form: bind
                    // it into the object and answer the block.
                    // Bound where it stands, and the same block answered:
                    // `Bind_Block(...); return R_ARG2`. The caller's block is
                    // bound afterwards, which is what `b: [a] in o b do b`
                    // depends on. Copying instead leaves the caller's block
                    // unbound and the call looking as though it did nothing.
                    if (arguments.get(1) instanceof BlockValue body) {
                        return Binder.bindInPlace(body, frame);
                    }
                    WordValue word = (WordValue) arguments.get(1);
                    // A word the object does not hold answers none rather
                    // than refusing. That is what makes
                    // `any [get in obj 'field  default]` the ordinary way to
                    // read an optional field, and Rebol's own MAKE-PORT*
                    // reads its awake handler exactly that way. Refusing
                    // broke the idiom at the first absent field, and the
                    // refusal read as a bad path rather than as no field.
                    if (!frame.holds(word.canonical())) {
                        return NoneValue.none();
                    }
                    return word.boundTo(frame);
                });

        // COLLECT-WORDS gathers the words a block uses, which is what
        // building a context out of a block needs. Nested blocks are not
        // looked into unless /deep says so.
        // APPLY reaches a function when the arguments are already a
        // block rather than written after the call. The arity is still
        // checked, so a short block is the same mistake as a short call.
        // APPLY reduces the block, then makes it exactly as long as the
        // function's argument list: extra values are dropped and missing
        // ones are filled with none. `Apply_Block` in the C does both in
        // two lines, and the second is the one JEBOL had backwards:
        //     if (len < n) n = len;              // drop the extra
        //     for (; n < len; n++) DS_PUSH_NONE; // pad out missing args
        //
        // Refusing a short block instead is why base-defs.reb and
        // mezz-types.reb both stopped. Rebol's own library calls APPLY with
        // a block whose length it does not know, exactly because APPLY is
        // the call that does not mind.
        define("apply", List.of(Parameter.required("func"),
                        Parameter.required("block", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue given = (BlockValue) arguments.get(1);
                    // Reduced first, unless /ONLY asks for the values as
                    // written. `Apply_Block(D_ARG(1), D_ARG(2), !D_REF(3))`.
                    List<Value> supplied = refinements.contains("only")
                            ? new ArrayList<>(given.remaining())
                            : new ArrayList<>(
                                    evaluator.evaluateEachOrRaise(given, context));
                    Value callee = arguments.get(0);
                    // Applying DO to a function re-applies that function to
                    // the rest of the block -- `goto reapply` -- so
                    // `apply :do [:add 1 1]` is 2 rather than the function
                    // value that DO of it would answer.
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

        // ASSERT raises with an id of its own rather than a generic
        // script error, so a caller can tell an assertion from anything
        // else that went wrong.
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

        // HASH answers a number that equal values share. What the number
        // is belongs to the implementation; only the sharing is promised.
        define("hash", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> IntegerValue.of(
                        Molder.mold(arguments.get(0)).hashCode()));

        // /IGNORE names words to leave out, which is how a caller asks
        // "what is here that I do not already know about".
        // The spec is the C's, verbatim:
        //     block [block!]
        //     /deep    "Include nested blocks"
        //     /set     "Only include set-words"
        //     /ignore  "Ignore prior words"
        //      words [any-object! block! none!] "Words to ignore"
        //     /as   "Datatype of the words in the returned block"
        //      type [datatype!] "Any word type"
        //
        // NONE on /IGNORE is not a nicety. `collect-words/ignore body
        // select spec 'exports` passes whatever SELECT answered, and SELECT
        // answers none for a field the object has not got. Refusing it
        // stopped ten of Rebol's own files, MAKE-MODULE* among them.
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
                    // /AS names the word datatype to answer, and only a word
                    // datatype will do:
                    //     type = D_REF(6) ? VAL_DATATYPE(D_ARG(7)) : REB_WORD;
                    //     if (type < REB_WORD || type > REB_ISSUE)
                    //         Trap1(RE_BAD_FUNC_ARG, D_ARG(7));
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

        // The line-break marker belongs to a position rather than to a
        // value, so it takes a pair of natives of its own: there is
        // nothing you could insert into the block to put one there.
        // The whole of the C is one loop with one condition in it:
        //     REBINT skip = -1;
        //     if (D_REF(3)) skip = 1;                      // all
        //     if (D_REF(4)) skip = MAX(1, Int32s(size));   // skip
        //     for (n = 0; NOT_END(val); n++, val++) {
        //         if (cond ^ (n % skip != 0)) VAL_SET_LINE(val);
        //         else VAL_CLR_LINE(val);
        //         if (skip < 0) break;
        //     }
        //
        // Three things follow that reading the name would not give. A plain
        // call marks one position and stops -- `if (skip < 0) break;`. /ALL is
        // /SKIP with a size of one. And the condition is an exclusive or, so
        // /SKIP marks every nth position and *clears* the rest: it sets the
        // pattern rather than adding to it.
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
                        stride = (int) Math.max(1, size.magnitude());
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

        // OBJECT is MAKE OBJECT! with the datatype left out.
        define("object", List.of(Parameter.required("spec", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> makeObject(
                        evaluator, context, Optional.empty(), (BlockValue) arguments.get(0)));

        // WITH evaluates a block bound to an object, so a bare word in it
        // reaches the object's field and an assignment lands there too.
        define("with", List.of(
                        Parameter.required("context", Set.of(Datatype.OBJECT)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    Context inside = ((ObjectValue) arguments.get(0)).context();
                    return evaluator.evaluateOrRaise(
                            Binder.bind((BlockValue) arguments.get(1), inside), inside);
                });

        // An ordinary object holds SELF, so this is false for one. The
        // question is only interesting for the contexts that do not.
        define("selfless?", List.of(Parameter.required("context")),
                (arguments, evaluator, context) -> LogicValue.of(
                        !(arguments.get(0) instanceof ObjectValue object)
                                || !object.context().holds("self")));

        define("protected?", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> LogicValue.of(switch (arguments.get(0)) {
                    // Asked before the plain block, because a path is a
                    // block and asking a path about its own storage
                    // answers no every time -- including right after a
                    // PROTECT of that very path.
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

        // UNBIND takes the binding off its words. A word comes back loose,
        // so `unset unbind 'x` raises not-defined rather than reading the
        // slot x still pointed at. A block has each of its words unbound,
        // and /deep reaches the words in the blocks it holds.
        define("unbind", List.of(Parameter.required("word")),
                Set.of("deep"),
                (arguments, evaluator, context, refinements) ->
                        unbound(arguments.get(0), refinements.contains("deep")));

        // BIND takes a word as readily as a block. /new adds a word the
        // context has not got rather than leaving it loose, which is how
        // a generated word is given somewhere to live -- base-defs.reb
        // uses it to define SPEC-OF and its five siblings.
        define("bind", List.of(
                        Parameter.required("word"),
                        Parameter.required("target")),
                Set.of("copy", "only", "new", "set"),
                (arguments, evaluator, context, refinements) -> {
                    // The target may be a word rather than an object, and
                    // then it means "wherever this word is bound".
                    // base-defs.reb uses that to hang six generated words
                    // off REFLECT's own context without naming it.
                    // `context [any-word! any-object!]`, so a module, a port
                    // and an error all serve. Rebol's own MAKE-MODULE* binds
                    // a body to the module it is building.
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
                        // The same question a block gets asked, one word at
                        // a time: the answer names whichever context holds
                        // the slot, which may be above the target. Naming
                        // the target instead let BIND/NEW hang a fresh name
                        // off a scope that had nothing to do with the word.
                        //
                        // An object target answers from its own frame or
                        // refuses: `else Trap1(RE_NOT_IN_CONTEXT, arg);` --
                        // Bind_Word searches the frame's own words, and only
                        // /NEW and /SET may add a name. A word target keeps
                        // the ancestor search, which is what lets
                        // base-defs.reb hang names off REFLECT's context.
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
                    // BIND changes the caller's own block. /COPY is what asks
                    // for a copy, and the C makes that the whole of the
                    // difference in one line:
                    //     blk = D_REF(3) ? Clone_Block_Value(arg) : VAL_SERIES(arg);
                    //
                    // Copying always was what stopped MAKE-MODULE* working.
                    // It binds one body four times over -- `bind/only/set body
                    // context`, `bind body lib`, `bind body mixins`,
                    // `bind body context` -- and then evaluates that body. With
                    // every bind answering a copy nobody kept, `do body` ran an
                    // unbound block and the module came out holding a slot per
                    // word and a value for none of them.
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
        // SELF first, because rehoming a method rebinds its body to these
        // fields and a method that says `self` has to reach the copy. Setting
        // it afterwards left SELF bound to the prototype, so Rebol's own ENUM
        // read the shape it was made from rather than the one it made.
        ObjectValue built = new ObjectValue(fields);
        fields.set("self", built);

        // Copying an object copies its methods too, and a method that still
        // closed over the object it was written in would move money in the
        // original when called on the copy.
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

    // ---- loops -----------------------------------------------------------
    //
    // Every loop catches BREAK and nothing else, so a break leaves the
    // nearest loop and an error keeps travelling. A loop that ran no passes
    // gives NONE, because there is no last value to give back.

    private void defineLoops() {
        // `count [number!]` -- a decimal is truncated rather than refused.
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

        // UNTIL runs the block and then asks, so it always runs once, and it
        // stops when the block is true rather than when it is false.
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

        // FOR steps a value from one bound to another. The step decides the
        // direction, so a negative step counts down and a step that would
        // never arrive runs no passes rather than for ever.
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

        // FOREACH walks a series. A block of words takes that many items per
        // pass, which is how a flat block of records gets read.
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

        // REMOVE-EACH drops what the block accepts and answers the
        // series, which is the opposite way round from POKE.
        define("remove-each", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("series",
                                Set.of(Datatype.BLOCK, Datatype.BINARY,
                                        Datatype.STRING, Datatype.MAP)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                Set.of("count"),
                (arguments, evaluator, context, refinements) -> {
                    // A map is walked in pairs rather than one item at a time,
                    // and what it answers for /COUNT is halved for the same
                    // reason /PART is halved:
                    //     SET_INTEGER(DS_RETURN, IS_MAP(value) ? index / 2 : index);
                    if (arguments.get(1) instanceof MapValue map) {
                        return removedEachPairFrom(
                                map, arguments, refinements, evaluator, context);
                    }
                    // A binary and a string are walked the same way a
                    // block is, one element at a time. Taking only a
                    // block made a protected binary report the wrong
                    // failure, and refused a perfectly ordinary call.
                    if (arguments.get(1) instanceof SeriesValue other
                            && !(other instanceof BlockValue)) {
                        return removedEachFrom(other, arguments, evaluator, context);
                    }
                    BlockValue series = (BlockValue) arguments.get(1);
                    // As FOREACH: the body sees the frame it was written in,
                    // and walks with one name or a block of them.
                    Context locals = Context.loopFrameOf(context);
                    List<WordValue> names = loopNamesIn(arguments.get(0), "remove-each");
                    names.forEach(name -> locals.define(name.spelling()));
                    BlockValue bound = Binder.bind((BlockValue) arguments.get(2), locals);
                    List<Value> items = series.remaining();
                    List<Value> kept = new ArrayList<>();
                    int taken = 0;
                    for (int at = 0; at < items.size(); at += names.size()) {
                        setLoopNames(locals, names, items, at);
                        int through = Math.min(at + names.size(), items.size());
                        if (evaluator.evaluateOrRaise(bound, locals).isTruthy()) {
                            taken += through - at;
                        } else {
                            kept.addAll(items.subList(at, through));
                        }
                    }
                    int had = series.lengthFromHere();
                    for (int removed = 0; removed < had; removed++) {
                        series.storage().removeAt(series.index());
                    }
                    for (int at = kept.size(); at > 0; at--) {
                        series.storage().insertAt(series.index(), kept.get(at - 1));
                    }
                    return refinements.contains("count")
                            ? IntegerValue.of(taken)
                            : series;
                });

        // MAP-EACH walks for an answer where FOREACH walks for effect. It
        // is a native rather than prelude because it binds the caller's
        // block to the word it walks with, and binding a block to a
        // context is not something the language can say without BIND.
        //
        // A map is refused, and the refusal is in the spec above the walk
        // rather than in the walk: MAP-EACH declares `data [block! vector!]`
        // where FOREACH declares `data [series! any-object! map! none!]`.
        define("map-each", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("series", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    // As FOREACH: the body sees the frame it was written in.
                    // Rebol's own LOAD maps over a block of sources and reads
                    // its own /AS argument inside the body.
                    //
                    // And as FOREACH, the walk takes a block of names as
                    // readily as one name, because one Init_Loop reads the
                    // list for all four walks. The answer holds one value a
                    // round rather than one value a name.
                    Context locals = Context.loopFrameOf(context);
                    List<WordValue> names = loopNamesIn(arguments.get(0), "map-each");
                    names.forEach(name -> locals.define(name.spelling()));
                    BlockValue bound = Binder.bind(
                            (BlockValue) arguments.get(2), locals);
                    List<Value> items = itemsOf(arguments.get(1));
                    List<Value> gathered = new ArrayList<>();
                    for (int at = 0; at < items.size(); at += names.size()) {
                        setLoopNames(locals, names, items, at);
                        gathered.add(evaluator.evaluateOrRaise(bound, locals));
                    }
                    return BlockValue.block(gathered);
                });

        // FORALL moves the series itself rather than binding each item, so
        // the word holds a position. It puts the word back at the head when
        // it is done, however it finishes: running out, breaking out, or
        // never starting because the series was empty. The guide's habit of
        // following every FORALL with HEAD suggested otherwise and is a
        // leftover; a real R3 reports index? 1 in all three cases.
        // FORSKIP is FORALL with a step, and one C function serves both:
        // `Loop_All(ds, 0)` for FORALL and `Loop_All(ds, 1)` for FORSKIP. Three
        // things in that function are not guessable.
        //
        // A negative step starts from the tail: `if (inc < 0 && VAL_INDEX(var)
        // >= VAL_TAIL(var)) VAL_INDEX(var) = VAL_TAIL(var) + inc;`, so a word
        // sitting at the tail walks backwards from the last item rather than
        // doing nothing.
        //
        // The word is put back where it started -- `*var = *DS_ARG(1);` on the
        // way out -- except when the loop was left by BREAK, which returns
        // before that line. So a loop that ran out restores the word and a loop
        // that broke leaves it where it stopped.
        //
        // And a word holding none answers none rather than raising: `if
        // (IS_NONE(var)) return R_NONE;` is the first thing it checks.
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

        // /RETURN gives the loop a value to answer, which is how a search
        // loop reports what it found without a variable outside the loop
        // to put it in. A plain BREAK leaves the loop answering unset, and
        // that is distinguishable from BREAK/RETURN NONE.
        // CONTINUE stops this round and starts the next, where BREAK
        // stops the loop. Both are thrown, because either may sit several
        // blocks deep inside the body.
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

        // As FOREACH: the body sees the frame it was written in.
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

    private static Value forEachLoop(
            Evaluator evaluator, Context within,
            Value target, Value series, BlockValue body) {

        List<WordValue> names = loopNamesIn(target, "foreach");
        refuseMoreNamesThanAPairHas(series, names);
        // An object or a map walks as its keys, or as its keys and values when
        // the loop takes two. Which it is depends on the loop rather than on
        // what is being walked, so it cannot be settled where the items are
        // gathered -- `foreach w o` gives [a b] and `foreach [k v] o` gives
        // [a 1 b 2].
        //
        // For a map that is `*vars = *BLK_SKIP(series, index & ~1)`: with one
        // name the mask keeps reading the key while the walk steps two slots,
        // so the values are never reached.
        List<Value> items = keysOnly(series, names.size());

        // A loop body runs inside whatever called it, so its context hangs
        // off the caller's frame and holds only the loop's own words. Hanging
        // it off the system context instead hid every local of the enclosing
        // function: `f: func [a /as type] [foreach i [1] [type]]` failed on
        // TYPE, and Rebol's own LOAD is exactly that shape.
        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.define(name.spelling()));
        BlockValue bound = Binder.bind(body, locals);
        Value last = NoneValue.none();

        try {
            for (int at = 0; at < items.size(); at += names.size()) {
                setLoopNames(locals, names, items, at);
                last = oneRound(evaluator, bound, locals);
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
    private static void setLoopNames(
            Context locals, List<WordValue> names, List<Value> items, int at) {

        for (int which = 0; which < names.size(); which++) {
            locals.set(names.get(which).spelling(),
                    at + which < items.size()
                            ? items.get(at + which)
                            : NoneValue.none());
        }
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
        // A step of zero would never reach the tail. The C does not guard it and
        // hangs; refusing is the one place this parts from the C on purpose,
        // because a hang is not an answer a script can act on.
        if (step == 0) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    "a step of zero would never reach the end");
        }
        int at = start.index();
        // `if (inc < 0 && VAL_INDEX(var) >= VAL_TAIL(var)) VAL_INDEX(var) =
        // VAL_TAIL(var) + inc;`
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
            // No restoring here: the C returns before `*var = *DS_ARG(1)`, so a
            // loop that broke leaves the word where it stopped and a caller can
            // read the position the break happened at.
            return stopped.answer();
        }
        slot.setValue(start);
        return last;
    }

    /** A series as a list of its values, whatever kind of series it is. */
    private static List<Value> itemsOf(Value series) {
        return switch (series) {
            case BlockValue block -> block.remaining();
            // A gob walks the children it has from where it stands, which is what
            // `Pane_To_Block(gob, index, -1)` builds.
            case GobValue gob -> gob.storage().pane()
                    .subList(Math.min(gob.index() - 1, gob.storage().length()),
                            gob.storage().length());
            case StringValue text -> text.text().codePoints()
                    .mapToObj(codepoint -> (Value) CharacterValue.of(codepoint))
                    .toList();
            // An object walks as its words and their values, so FOREACH
            // can inspect one without asking for WORDS-OF first. SELF is
            // left out, or every walk would reach the object again.
            // Each word is bound to the object, not handed out loose. A
            // walk that answered unbound words would be readable and not
            // writable, and Rebol's own DELTA-PROFILE writes through them:
            //     foreach [key num] adjust [set key end/:key - num]
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
            // A map walks as its pairs, with the keys handed out as plain
            // words: `if (IS_SET_WORD(vars)) SET_TYPE(vars, REB_WORD);`. So a
            // walk agrees with KEYS-OF rather than with BODY-OF, and a body
            // that compares its key against a word it wrote finds it.
            case MapValue map -> map.walkable();
            default -> throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "cannot walk " + series.datatype().literalSpelling() + " value");
        };
    }

    // ---- reflection ------------------------------------------------------

    /**
     * Source text read into values.
     *
     * <p>A syntax failure comes back as an ordinary error! that the script
     * can catch, rather than as a host exception, because the reader's
     * failures are values in this language like any other.
     */
    private static Value loaded(Value source, boolean unwrapSingle) {
        // A block is a block of sources rather than something to read.
        // Each one is loaded on its own and its answer added whole, so a
        // source holding two values arrives nested and one holding a
        // single value does not.
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
        // LOAD reads source text into values at run time, which is what
        // makes REBOL homoiconic in practice rather than only in principle.
        // Source holding exactly one value loads as that value rather than
        // as a block wrapping it, so `load "1"` is the integer; /all turns
        // that off for a caller who would rather not test what came back.
        define("load", takes("source"), Set.of("all"),
                (arguments, evaluator, context, refinements) -> loaded(
                        arguments.get(0), !refinements.contains("all")));

        // SAME? asks whether two values are one thing, which for a series
        // means one storage. EQUAL? deliberately does not ask that, so
        // "a" and "a" are equal and not the same.
        // QUOTE hands its argument back as written, so `quote (1 + 1)` is
        // the paren rather than 2. How a value is passed on without being
        // evaluated on the way.
        define("quote", List.of(Parameter.hardQuoted("value")),
                (arguments, evaluator, context) -> arguments.get(0));

        // SHIFT moves bits left for a positive count and right for a
        // negative one, the sign choosing the direction as FOR's step does.
        // /LOGICAL shifts without regard to sign, filling with zeros from
        // whichever end, so a right shift of a negative number stops being
        // negative. Plain SHIFT keeps the sign.
        //
        // A shift of sixty-four or more is handled here rather than left
        // to the hardware, which reads only the low six bits of the count
        // and so treats 64 as no shift at all.
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

        // =? is SAME? rather than EQUAL?: it asks whether two references
        // are one value, so `"a" =? "a"` is false. Registered here rather
        // than beside the other operators because a twin has to exist
        // before the operator naming it does.
        // A pair is even when both halves are, so one even half and one
        // odd is false rather than true-ish. That boundary is the whole
        // difference between "asks about both" and "asks about either".
        //
        // Each half is rounded before the question is asked, because
        // A_ODDQ reads it through VAL_PAIR_X_INT and that is ROUND_TO_INT.
        // So `odd? 1.1x2.9` is true: the 2.9 rounds to 3. Testing the
        // fraction directly answers false for every fractional half, which
        // looks right until a test says otherwise.
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
        // /WORD answers the datatype's name rather than the datatype, so
        // the answer can be put in a block being built without carrying a
        // datatype value along with it.
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
        // Every datatype gets its own predicate, rather than a handful
        // being written out. The hand-written set was missing pair? and
        // decimal? and would have gone on missing whichever datatype was
        // added last, because nothing connected the two lists.
        for (Datatype datatype : Datatype.values()) {
            Datatype asked = datatype;
            define(datatype.spelling() + "?", takesAnything("value"),
                    (arguments, evaluator, context) -> LogicValue.of(
                            arguments.get(0).datatype() == asked));
        }
        // And one per typeset, for the same reason: a family has a
        // question too. SERIES? was missing and Rebol's own REJOIN
        // branches on it, so that one absence stopped a borrowed
        // function running at all.
        for (Typeset typeset : Typeset.values()) {
            Typeset asked = typeset;
            define(typeset.spelling() + "?", takesAnything("value"),
                    (arguments, evaluator, context) -> LogicValue.of(
                            asked.members().contains(arguments.get(0).datatype())));
        }
        // TRUE? and DID ask the one question every conditional asks:
        // only NONE and logic FALSE are false, so zero, the empty string
        // and the empty block are all true.
        define("true?", takesAnything("value"),
                (arguments, evaluator, context) -> LogicValue.of(arguments.get(0).isTruthy()));
        define("did", takesAnything("value"),
                (arguments, evaluator, context) -> LogicValue.of(arguments.get(0).isTruthy()));

        // NUMBER? is wider than the NUMBER! typeset it is named after:
        // money counts, and the typeset does not say so.
        // The one predicate in the family that looks at the value and not only
        // at the datatype: a NaN is a decimal and is not a number. Rebol's own
        // doc string says so -- "any type of number and not a NaN" -- and the
        // C guards the decimal case with isnan.
        //
        // A money counts, which the number! typeset does not: that covers
        // integer, decimal and percent only.
        define("number?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(switch (arguments.get(0)) {
                    case DecimalValue quantity -> !Double.isNaN(quantity.quantity());
                    case IntegerValue whole -> true;
                    case MoneyValue amount -> true;
                    default -> false;
                }));

        // EQUIV? is the loose comparison despite the name; STRICT-EQUAL?
        // is the one that minds the datatype.
        //
        // Loose in every way but one. It folds case and lets an integer
        // meet a decimal, and then compares the bits exactly, so
        // `equiv? 1 1.0` is true while `equiv? 0.5 0.5000000000000001` is
        // false. It is the only comparison that splits those two hairs
        // differently, which is why it takes the allowance by hand.
        // The three character-range questions. Empty answers true, which
        // is the useful way round for a guard.
        defineCodepointRange("ascii?", 0x7F);
        defineCodepointRange("latin1?", 0xFF);
        // utf?: data [binary!] -- the byte order mark's encoding, negative
        // for little-endian and zero when there is no mark. `What_UTF` in
        // s-unicode.c, which is four comparisons and no guessing: a mark or
        // nothing.
        //
        // This was a codepoint-range predicate here, alongside ASCII? and
        // LATIN1?, which is a different question with the same spelling. It
        // refused a binary, and Rebol's own ASSERT-UTF8 opens with
        // `unless find [0 8] encoding: utf? source`.
        define("utf?", List.of(Parameter.required("data", Set.of(Datatype.BINARY))),
                (arguments, evaluator, context) -> IntegerValue.of(
                        byteOrderMarkOf(((BinaryValue) arguments.getFirst())
                                .octetsFromHere())));

        // INVALID-UTF? answers where the trouble is rather than whether
        // there is any, so none means well-formed.
        // /UTF and its NUM are declared and never read. `REBNATIVE(invalid_utfq)`
        // touches D_ARG(1) alone -- "Bit size - positive for BE negative for
        // LE" describes an argument no line of the function looks at. Declared
        // here so a script written for Rebol can make the same call.
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
        define("zero?", takesNumbers("value"),
                (arguments, evaluator, context) -> LogicValue.of(Comparison.asDouble(arguments.get(0)) == 0.0));

        // These take a word and act on its slot, so they are written
        // `value? 'word` rather than `value? word`: the lit-word is what
        // stops the word being looked up before the native sees it.
        define("value?", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    WordValue word = (WordValue) arguments.get(0);
                    boolean known = word.isBound() && word.binding().knows(word.canonical());
                    return LogicValue.of(known
                            && !word.binding().slotFor(word.canonical()).holdsUnset());
                });

        // The spec is R3's: `word [word! block! none!]`. A block unsets each
        // word in it, and none unsets nothing rather than being refused.
        //
        // NONE matters because Rebol's own files reach this with a value they
        // did not check. mezz-tail.reb ends `unset 'protect-system`, and
        // base-funcs.reb ends `unset 'action`; a build where the word was
        // never defined passes none, and refusing it loses everything below
        // that line.
        define("unset", List.of(Parameter.required("word",
                        Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.NONE))),
                (arguments, evaluator, context) -> {
                    // `if (IS_NONE(word)) return R_NONE;` -- unset of none
                    // answers none, so `unset in ctx 'absent` passes through.
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

        // PROTECT takes an object as well as a word, and protects every
        // field it holds. Protecting the word that holds the object and
        // protecting the object are different things, and it is the second
        // that stops `o/a: 1`.
        define("protect", List.of(Parameter.required("target")),
                Set.of("deep", "words", "values", "hide", "lock"),
                (arguments, evaluator, context, refinements) -> {
                    // /HIDE conceals a field rather than locking it, and
                    // the two are separate: a hidden field is not locked
                    // and a locked one is not hidden.
                    if (refinements.contains("hide")
                            && arguments.getFirst() instanceof WordValue named) {
                        slotOf(named).hide(true);
                        return arguments.getFirst();
                    }
                    if (!protectFieldNamedBy(arguments.getFirst(), true, refinements)) {
                        // `if (GET_FLAG(flags, PROT_HIDE)) Trap0(RE_BAD_REFINES);`
                        // -- only a word or a field can hide.
                        if (refinements.contains("hide")) {
                            throw Raised.of(EvaluationFailure.BAD_REFINES,
                                    "protect/hide needs a word");
                        }
                        protectNamed(arguments.getFirst(), true, refinements);
                        setProtection(arguments.get(0), true, refinements.contains("deep"),
                                refinements.contains("words"));
                    }
                    // The value itself, so `b: protect #{0102}` builds a
                    // protected value and keeps hold of it in one step.
                    // Answering unset left b with no value at all, and
                    // every later use then failed on the missing word --
                    // which reads as though the protection is working.
                    return arguments.getFirst();
                });

        // No /HIDE: PROTECT has one and UNPROTECT does not, in the C's own
        // declarations. Hiding a word is not a lock to be lifted -- `/hide
        // "Hide variables (avoid binding and lookup)"` -- and there is no
        // native that reveals one again. JEBOL had offered the undoing, which
        // is a promise Rebol does not make.
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

        // DELECT checks the output block's protection as its first act --
        // `if (IS_PROTECT_SERIES(dia.out)) Trap0(RE_PROTECTED);` -- before
        // any parsing. The dialect parse itself is not built; a caller who
        // gets past the gate is told so rather than given a wrong answer.
        define("delect", List.of(
                        Parameter.required("dialect", Set.of(Datatype.OBJECT)),
                        Parameter.required("input", Set.of(Datatype.BLOCK)),
                        Parameter.required("output", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("in", "where", Set.of(Datatype.BLOCK))),
                Set.of("in", "all"),
                (arguments, evaluator, context, refinements) -> {
                    requireChangeable(arguments.get(2));
                    throw Raised.of(EvaluationFailure.FEATURE_NA,
                            "the delect dialect parse is not built");
                });

        // Several words at once take a block of values one for one, and
        // anything else goes to every word. Too few values pads with none
        // rather than failing; too many leaves the extras unused.
        //
        // /ONLY turns the spreading off, so each word gets the whole
        // block. /SOME leaves a word holding what it held where the value
        // would have been none, which is for filling defaults in from a
        // partly populated block.
        // The argument check is where a number is turned away, so `set 1 1` is
        // expect-arg rather than the cannot-use a hand-written guard inside
        // would raise. natives.reb spells the accepted shapes out:
        // `word [word! lit-word! any-path! block! object!]`.
        //
        // An issue and a refinement are words underneath and get past this,
        // which is why the guard inside still exists.
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
                    // An issue and a refinement are words underneath and
                    // read as words at a glance, which is what makes
                    // assigning to them plausible and wrong.
                    refuseUnassignableName(target, EvaluationFailure.EXPECT_ARG);
                    if (target instanceof WordValue word) {
                        slotOf(word).setValue(supplied);
                        return supplied;
                    }
                    // `if (ANY_PATH(word)) { Do_Path(&word, val); return
                    // R_ARG2; }` -- a path target assigns through the path,
                    // before the block-of-words reading gets a chance to
                    // misread its segments as separate words.
                    if (target instanceof BlockValue path
                            && PATH_SHAPED.contains(path.datatype())) {
                        if (!refinements.contains("any")
                                && supplied.datatype() == Datatype.UNSET) {
                            throw Raised.of(EvaluationFailure.NEED_VALUE, "set");
                        }
                        return writtenThroughPath(path, supplied);
                    }
                    // An object takes the values into its fields in order,
                    // which is the same rule with its own words standing
                    // in for the block.
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
                        // A wrong item inside a right argument, which is
                        // a different mistake from a wrong argument and
                        // carries a different id.
                        names.forEach(name -> refuseUnassignableName(
                                name, EvaluationFailure.INVALID_ARG));
                    }
                    if (names == null) {
                        return raiseCannotUse(target, "set");
                    }
                    // An unset value is refused before anything is written,
                    // which is the first thing the C does:
                    //     if (not_any && !IS_SET(val)) Trap1(RE_NEED_VALUE, word);
                    boolean anyValue = refinements.contains("any");
                    if (!anyValue && supplied.datatype() == Datatype.UNSET) {
                        throw Raised.of(EvaluationFailure.NEED_VALUE, "set");
                    }
                    // Object to object is its own rule: each word of the
                    // TARGET takes the value the source holds for that same
                    // word, and a word the source has not got is left alone.
                    // Position plays no part, which is what makes it different
                    // from every other shape of SET.
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
                    // And an unset inside a block is refused the same way,
                    // also before anything is written:
                    //     if (not_any && is_blk && not_only && !IS_END(tmp)
                    //             && IS_UNSET(tmp++)) Trap1(RE_NEED_VALUE, word);
                    if (!anyValue && values != null) {
                        for (int index = 0; index < names.size()
                                && index < values.size(); index++) {
                            if (values.get(index).datatype() == Datatype.UNSET) {
                                throw Raised.of(EvaluationFailure.NEED_VALUE, "set");
                            }
                        }
                    }
                    for (int index = 0; index < names.size(); index++) {
                        // Past the end of a block the target words take none,
                        // unless /SOME, which stops instead:
                        //     if (IS_END(val)) { if (ref_some) break; ... }
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

        // TAKE removes from the front and answers what it removed: one
        // item as itself, or several as a series of the same kind. An
        // empty series gives NONE rather than raising, because taking from
        // something that has run out is an ordinary thing for a loop to do.
        //
        // A negative /part takes backwards from the position toward the
        // head, so at the head it takes nothing.
        define("take",
                List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "count", Set.of())),
                Set.of("part", "last", "deep", "all"),
                (arguments, evaluator, context, refinements) -> {
                    // Taking from nothing answers nothing.
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
                        // /LAST takes from the tail rather than the head,
                        // which is the same operation one place along.
                        Value taken = refinements.contains("last")
                                && series.lengthFromHere() > 0
                                ? takeOne(series.atIndex(
                                        series.index() + series.lengthFromHere() - 1))
                                : takeOne(series);
                        // `if (D_REF(ARG_TAKE_DEEP) && ANY_SERIES(D_RET))`
                        // in the C: /DEEP copies what was taken, thus the
                        // caller holds something the series no longer
                        // shares. A block is cloned all the way down and
                        // any other series is copied once.
                        return deepenedIfAsked(taken, refinements);
                    }
                    if (arguments.size() > 1 && arguments.get(1) instanceof SeriesValue upTo) {
                        return deepenedIfAsked(takeSeveral(earlierOf(series, upTo),
                                Math.abs(upTo.index() - series.index())), refinements);
                    }
                    long wanted = arguments.size() > 1
                            ? countUpTo(series, arguments.get(1))
                            : 1;
                    // Partial1 in the C: a negative count moves the
                    // series back by that much and makes the length
                    // positive, thus the span always runs forwards from
                    // wherever it ends up. It is clamped to what is
                    // behind the position, not to what is ahead.
                    if (wanted < 0) {
                        long back = Math.min(-wanted, series.index() - 1L);
                        series = series.atIndex((int) (series.index() - back));
                        wanted = back;
                    } else {
                        wanted = Math.min(wanted, series.lengthFromHere());
                    }
                    // `if (D_REF(ARG_TAKE_LAST)) index = tail - len` in
                    // the C, with no clamp. /LAST with /PART moves where
                    // the taking starts, thus it takes the last few and
                    // not the first few.
                    //
                    // A negative count is how a caller reads backwards
                    // from where the series is, and the C lets the index
                    // move above the tail for it. Clamping to the
                    // position turned `take/last/part tail "123" -3` into
                    // nothing, and Rebol's own tests say it is "123".
                    // `index = tail - len` in the C. The length is
                    // already positive by here, thus this simply moves
                    // the start back by that many from the end.
                    if (refinements.contains("last")) {
                        int tail = series.storageLength() + 1;
                        long from = Math.max(1, tail - wanted);
                        return deepenedIfAsked(
                                takeSeveral(series.atIndex((int) from), wanted), refinements);
                    }
                    return deepenedIfAsked(takeSeveral(series, wanted), refinements);
                });

        // AJOIN drops NONE by default, which is the behaviour /all exists
        // to undo. /with puts a separator between the pieces -- between,
        // not after, so three pieces give two separators.
        define("ajoin", List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("with", "separator", ANYTHING)),
                Set.of("all", "with"),
                (arguments, evaluator, context, refinements) -> {
                    // Without /ALL, a none and an unset both drop out, which
                    // is the C's `VAL_TYPE(top) <= REB_NONE && !all`: unset
                    // and none both sort at or below none. A surviving unset
                    // is invisible until /WITH puts a separator beside it.
                    List<Value> pieces = evaluator.evaluateEachOrRaise(
                                    (BlockValue) arguments.get(0), context).stream()
                            .filter(piece -> refinements.contains("all")
                                    || !(piece instanceof NoneValue
                                            || piece instanceof UnsetValue))
                            .toList();
                    String separator = refinements.contains("with") && arguments.size() > 1
                            ? Molder.form(arguments.get(1))
                            : "";
                    // The first value decides the datatype when it is one
                    // that can carry a run of text with no markers of its
                    // own: a file gives a file, a url a url, an email an
                    // email. Everything else gives a string -- including a
                    // tag, whose text carries its own angle brackets, so
                    // `ajoin [<a> "b"]` is the string "<a>b" rather than
                    // a tag. Confirmed against a real R3, which the
                    // suite's own comment calls out as by design.
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

        // POKE answers what it put in rather than the series, which makes
        // it awkward in a chain and is worth knowing before you write one.
        // POKE asks for something that holds its elements, so a tuple is
        // turned away here even though `t/2: 99` writes one. The two are
        // not the same operation: a set-path builds a new tuple and puts
        // it back, and there is nowhere for POKE to put one.
        // `series<series! port! map! gob! bitset!>`, and series! is the whole
        // family: the any-path datatypes are blocks and REF is a string, so all
        // five were missing from a list that named their siblings.
        define("poke", List.of(Parameter.required("series",
                                Set.of(Datatype.BLOCK, Datatype.PAREN, Datatype.HASH,
                                        Datatype.PATH, Datatype.SET_PATH,
                                        Datatype.GET_PATH, Datatype.LIT_PATH,
                                        Datatype.STRING, Datatype.FILE, Datatype.URL,
                                        Datatype.TAG, Datatype.EMAIL, Datatype.REF,
                                        Datatype.BINARY, Datatype.MAP, Datatype.BITSET,
                                        Datatype.PORT, Datatype.GOB, Datatype.IMAGE)),
                        // `index {Index offset, symbol, or other value to use as
                        // index}` -- declared with no datatypes at all, because a
                        // map takes any value as a key. Which means the refusal of
                        // a word here is the arm's and not the declaration's, and
                        // the two raise different errors.
                        Parameter.required("index"),
                        Parameter.required("value", ANYTHING)),
                (arguments, evaluator, context) -> {
                    // A gob pokes into its pane, and `Get_Num_Arg` is what refuses
                    // a field name: `poke g 'offset 1x1` is invalid-arg rather than
                    // a write to the offset. So a gob's fields are reachable
                    // through a path and through nothing else.
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
                    long at = positionPokedAt(arguments.get(1));
                    // POKE names an element rather than a place to stand,
                    // so outside the series there is nothing to write to.
                    // AT clamps and PICK answers none for the same number;
                    // this one raises, because a change that cannot happen
                    // must not look as though it did.
                    if (arguments.get(0) instanceof SeriesValue series
                            && (at < 1 || at > series.lengthFromHere())) {
                        throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                "poke at " + at + " on a series of "
                                        + series.lengthFromHere());
                    }
                    if (arguments.get(0) instanceof StringValue text) {
                        // A character or a number that is one:
                        //     if (IS_CHAR(arg)) c = VAL_CHAR(arg);
                        //     else if (IS_INTEGER(arg) && VAL_UNT64(arg) <= MAX_CHAR)
                        //         c = VAL_INT32(arg);
                        //     else Trap_Arg(arg);
                        // So `poke s 1 65` writes an A, which is how code that
                        // has a codepoint in hand writes it without converting.
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
                    // A char writes its code point as a byte too, and one past
                    // 0xFF is out of range -- a char is never negative, so it
                    // is the out-of-range half of asAnOctet's two answers.
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
                    // A bitset's index is a character code rather than a
                    // position, so nothing is clamped and nothing is out of
                    // range: every code names a bit. The complement flag is
                    // minded exactly as PD_Bitset minds it --
                    // `t = IS_TRUE(val); if (BITS_NOT(ser)) t = !t;` -- which
                    // changes nothing for an ordinary set and everything for
                    // a complemented one.
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

        // What is in one and not the other, from both directions. /CASE
        // stops the case folding as it does for the other three.
        define("difference", List.of(
                        Parameter.required("first",
                                setOperandOr(Datatype.BLOCK)),
                        Parameter.required("second",
                                setOperandOr(Datatype.BLOCK)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    // A typeset and a bitset each combine bit by bit rather
                    // than item by item, and for DIFFERENCE that is a
                    // symmetric difference: `VAL_TYPESET(val1) ^= ...`.
                    if (arguments.get(0) instanceof TypesetValue
                            || arguments.get(0) instanceof BitsetValue) {
                        return combined(arguments, Combination.DIFFERENCE);
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

        // REFLECT is the general form of the -OF functions, so WORDS-OF
        // is REFLECT with the field named. A field that does not apply
        // gives none rather than raising, so a caller can ask about one
        // that may not.
        define("reflect", List.of(Parameter.required("value"),
                        Parameter.required("field", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    String field = ((WordValue) arguments.get(1)).canonical();
                    // A datatype describes itself: SPEC gives an object
                    // holding a title and a category, and TITLE and TYPE
                    // give those two directly.
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
                    // A module answers SPEC and TITLE from its header, and
                    // everything else from its words. t-object.c takes the
                    // first two through their own arm, before the arm every
                    // other object shares:
                    //     if (action == OF_SPEC || action == OF_TITLE) {
                    //         if (!VAL_MOD_SPEC(value)) return R_NONE;
                    //         VAL_OBJ_FRAME(value) = VAL_MOD_SPEC(value);
                    //
                    // So SPEC-OF a module is the header as an object, and
                    // TITLE-OF is one field read out of it. This is how any
                    // reader of a module asks what it is called and what
                    // version it is.
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
                    // A map answers WORDS, VALUES and BODY, and one C function
                    // answers all three with a flag saying which is asking:
                    // `Map_To_Block(mapser, what)` with -1 for the words, +1
                    // for the values and 0 for the pairs. Only the words are
                    // normalised back from set-words, which is why KEYS-OF
                    // answers `[a]` and BODY-OF answers `[a: 1]`.
                    if (arguments.get(0) instanceof MapValue map) {
                        return switch (field) {
                            case "words" -> BlockValue.block(map.keys());
                            case "values" -> BlockValue.block(map.values());
                            case "body" -> BlockValue.block(map.flattened());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof FunctionValue written) {
                        return switch (field) {
                            case "spec" -> written.spec();
                            // A deep copy, so a caller that clears or changes
                            // what BODY-OF answers does not reach into the
                            // function: `clear second body-of :f` must leave
                            // :f as it was. R3's OF_BODY does Clone_Block.
                            case "body" -> copied(written.body(), true);
                            case "types" -> typesetsOf(written.parameters(), Set.of());
                            default -> NoneValue.none();
                        };
                    }
                    // A native answers SPEC too, rebuilt from its declared
                    // parameters. In R3 every one of the 580 functions
                    // answers SPEC-OF, because the spec is a real block the
                    // boot read; JEBOL's natives are Java and hold a
                    // parameter list instead, so the block is made from that.
                    //
                    // It is not decoration. Rebol's own mezz-types.reb builds
                    // the whole TO-* family and branches on
                    // `either string? first spec-of :make`, which is how it
                    // asks whether this build carries doc strings. With SPEC
                    // answering none that reads `first none` and stops the
                    // file.
                    if (arguments.get(0) instanceof NativeValue built) {
                        return switch (field) {
                            case "spec" -> specBlockOf(built.parameters());
                            case "body" -> NoneValue.none();
                            case "types" -> typesetsOf(
                                    built.parameters(), built.declaredRefinements());
                            default -> NoneValue.none();
                        };
                    }
                    // An operator dispatches to something else, so it
                    // answers what that answers.
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
                        // BODY is the object written out as source: each
                        // field as a set-word with its value after it, so
                        // `body-of make object! [a: 1]` is `[a: 1]`. It is
                        // what MAKE OBJECT! would take to build the same
                        // object again, which is the point of it. Every
                        // set-word carries a line break -- `VAL_SET_LINE` in
                        // Make_Object_Block -- so the body molds one field
                        // a line, which is how SAVE writes its header.
                        case "body" -> {
                            BlockValue body = BlockValue.block(
                                    object.context().slots().stream()
                                            .filter(slot -> !slot.canonical()
                                                    .equals("self"))
                                            .flatMap(slot -> java.util.stream.Stream.of(
                                                    (Value) WordValue.of(slot.spelling(),
                                                            Datatype.SET_WORD),
                                                    slot.value()))
                                            .toList());
                            for (int at = 1; at <= body.storageLength(); at += 2) {
                                body.storage().setLineBreakAt(at, true);
                            }
                            yield body;
                        }
                        // SELF is the word every object holds for itself.
                        // It is never listed, or every object would report
                        // a field containing itself.
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


        // PUT adds or replaces a key in place, and answers the value put.
        define("put", List.of(Parameter.required("target"),
                        Parameter.required("key", ANYTHING),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    // Anything that holds values by key: a map, an
                    // object's fields, or a block read as key and value
                    // pairs the way SELECT reads one.
                    switch (arguments.get(0)) {
                        case MapValue map -> map.put(arguments.get(1), arguments.get(2));
                        case ObjectValue object when arguments.get(1) instanceof WordValue field -> {
                            // Changing the object as a container, which
                            // REBOL reports as `protected` rather than as
                            // the `locked-word` an assignment through a
                            // name gets. The same single question APPEND
                            // asks, and it is about the object rather
                            // than about the word: after UNPROTECT/WORDS
                            // an assignment goes through and this does
                            // not. Asking whether any slot happened to be
                            // protected answered a third question nobody
                            // had asked, and let EXTEND past, since
                            // EXTEND is written in terms of PUT.
                            refuseHiddenField(object, field);
                            if (object.context().isClosedToNewNames()) {
                                throw Raised.of(EvaluationFailure.PROTECTED, "put");
                            }
                            // The spelling and not the canonical form, so a
                            // field put in as MySQL reads back as MySQL. Words
                            // compare without regard to case and are written
                            // with it, and `put system/catalog/errors 'MySQL
                            // ...` in prot-mysql.reb is how a category gets its
                            // name -- lowercasing it renamed the category.
                            object.context().set(field.spelling(), arguments.get(2));
                        }
                        case ObjectValue object -> throw Raised.of(
                                EvaluationFailure.INVALID_ARG,
                                Molder.mold(arguments.get(1))
                                        + " is not a word an object can hold a field under");
                        case BlockValue block -> {
                            List<Value> items = block.remaining();
                            // /CASE matches the key exactly, so a block
                            // holding both "A" and "a" has two entries to
                            // it and one to a plain PUT.
                            boolean mindingCase = refinements.contains("case");
                            // `ret = IS_INTEGER(D_ARG(ARG_PUT_SIZE)) ?
                            // Int32s(D_ARG(ARG_PUT_SIZE), 1) : 1;` -- one and
                            // not two. Without /SKIP every position is a
                            // candidate key, so `put [a b b c] 'b 0` writes
                            // after the FIRST b and leaves the second alone.
                            // Stepping in twos instead reads the block as pairs
                            // and finds the wrong one.
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
                                // `Expand_Series(ser, tail, 2)` and the key and
                                // value go in at the end: a key PUT has never
                                // seen is added rather than refused.
                                block.storage().append(arguments.get(1));
                                block.storage().append(arguments.get(2));
                            } else if (found + 1 >= items.size()) {
                                // "when key is last value in the block" --
                                // `Expand_Series(ser, tail, 1)`. There is
                                // nowhere to write, so the block grows.
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
                    // A map is asked about keys it may not have, so a miss
                    // is NONE rather than an error, exactly as it is here
                    // for a block that does not hold the value.
                    if (arguments.get(0) instanceof MapValue map) {
                        return map.select(arguments.get(1));
                    }
                    if (arguments.get(0) instanceof NoneValue) {
                        return NoneValue.none();
                    }
                    // An object is asked about a field it may not have, and
                    // answers none for one it has not got. t-object.c takes
                    // SELECT and FIND through the same arm, so they agree
                    // about which fields are there and part company only in
                    // what they answer: the value here, TRUE there.
                    //
                    // MAKE-MODULE* asks this way -- `select spec 'exports` --
                    // and so does every other reader of a module header.
                    //
                    // An error is an object with a fixed set of fields, so it
                    // is asked the same way and answers from the same list.
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
                    // The record width decides only WHERE to look: at the
                    // first element of each record and nowhere else. The
                    // answer is always the very next element, whatever the
                    // width. Reading it as "the last field of the record"
                    // agrees at a width of two and is wrong at every other
                    // width, which is a hard way to find out.
                    //
                    // So the default is one, not two: without /skip every
                    // position is a record start. A default of two never
                    // looked at an even position, so `select [1 2 3] 2`
                    // answered none instead of 3.
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
                    // `ret += len;` and the item that lands on. Everything
                    // above this line is FIND, which is why SELECT has
                    // /PART, /LAST, /REVERSE and /ONLY at all.
                    //
                    // The length is the length of the MATCH and not of the
                    // needle, so a wildcard needle steps over however much
                    // it took.
                    List<Value> items = itemsOf(series.head());
                    int end = searchEnd(series, items, limit);
                    int after = found - 1
                            + matchLength(series, arguments.get(1), refinements,
                                    found, wildcards, end);
                    // A match with nothing after it has no answer, which is
                    // none rather than a failure: SELECT is asked a question
                    // it is allowed to miss. /PART bounds this as well as
                    // the search, thus a match on the last item inside the
                    // range answers none.
                    if (after >= end) {
                        return NoneValue.none();
                    }
                    return items.get(after);
                });

        // /ANY answers the unset rather than refusing, which is how code
        // asks whether a word has a value without having to catch an
        // error to find out.
        // GET's argument is untyped in Rebol -- `word {Word, path, object to
        // get}` -- and the C has four branches for it. A word is looked up. A
        // path is evaluated. An object answers a block of its values. And
        // anything else answers itself, which is one line: `else val = word;`
        //
        // That last branch is the general rule, and `get none` answering none
        // is one case of it. Writing only the none case leaves `get 5`
        // refusing a number for no reason a caller can see.
        define("get", List.of(Parameter.required("word")),
                Set.of("any"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof BlockValue path
                            && path.datatype() == Datatype.PATH) {
                        return evaluator.evaluateOrRaise(
                                BlockValue.block(List.of(path)), context);
                    }
                    // SELF is left out, which is what the 1 in
                    // `Copy_Block(VAL_OBJ_FRAME(word), 1)` skips. Every object
                    // holds one and it points back at the object, thus a block
                    // carrying it could not be printed.
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
        // /NAME takes one word or a block of them, and a block means any
        // of these rather than all of them.
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
                        // A character steps to its neighbour, the same
                        // one-operation-covers-everything shape these have
                        // for a series position.
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
            // Three spellings each, because a header written by a person
            // says `yes` where a program says `true`. Every other word
            // is left alone, which is what keeps CONSTRUCT from running
            // anything.
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
            // `else SET_NONE(temp);` -- anything below none, which is an
            // unset, floors to none. /ONLY copies it as written.
            Value held = asWritten ? item : namedConstant(item);
            if (!asWritten && held instanceof UnsetValue) {
                held = NoneValue.none();
            }
            for (WordValue name : waiting) {
                built.set(name.spelling(), held);
            }
            waiting.clear();
        }
        // A field that was named and never given a value still exists. The
        // frame's slots start as none -- `SET_NONE` on every one in
        // Create_Frame -- so without /ONLY the field holds none, not unset.
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
        // The spec is an object body, so its values are evaluated:
        // `make error! [type: err-type]` means the error named by what
        // err-type holds, not an error whose type is the word "err-type".
        // Reading the block as written made CAUSE-ERROR raise an error
        // called err-id every time, whatever it was asked for.
        if (spec instanceof BlockValue body && body.datatype() == Datatype.BLOCK) {
            Value built = makeObject(evaluator, context, Optional.empty(), body);
            if (built instanceof ObjectValue holder) {
                spec = BlockValue.block(holder.context().slots().stream()
                        .filter(slot -> !slot.canonical().equals("self"))
                        .flatMap(slot -> java.util.stream.Stream.of(
                                WordValue.of(slot.spelling(), Datatype.SET_WORD),
                                slot.value()))
                        .toList());
            }
        }
        if (!(spec instanceof BlockValue fields)) {
            return new ErrorValue(ErrorCategory.USER, "message",
                    Molder.form(spec), Optional.of(spec),
                    Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty());
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
            // The values are usually lit-words -- `type: 'math` -- and
            // the name is what is wanted, not the sigil it was written
            // with.
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
                // What caused it. Dropped before, so an error raised
                // through CAUSE-ERROR carried its name and nothing else.
                //
                // All three, because a catalogue entry words up to three and
                // a script reads them by name.
                case "arg1" -> subject = Optional.of(items.get(at + 1));
                case "arg2" -> second = Optional.of(items.get(at + 1));
                case "arg3" -> third = Optional.of(items.get(at + 1));
                default -> { }
            }
        }
        // `Trap0(RE_INVALID_ERROR)` when the spec names no type or no id:
        // a spec that says neither is not an error a script meant, and
        // half of one is no better.
        if (!namedAType || !namedAnId) {
            throw new Raised(ErrorValue.of(ErrorCategory.INTERNAL,
                    "invalid-error", "an error spec names a type and an id"));
        }
        return new ErrorValue(category, errorId, errorId, subject, second, third,
                Optional.empty(), Optional.empty());
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
        // Zero spaces are as good as many: `while (IS_LEX_SPACE(*cp)) cp++;`
        // accepts none before the bracket, so `Rebol[]` is a header.
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
            // Trim the head when /head is named, or when neither end is --
            // the bare TRIM trims both. Reading it as "not /tail" made
            // trim/head/tail trim nothing, because both flags went false.
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

    // ---- series ----------------------------------------------------------

    private void defineSeries() {
        // A map counts pairs rather than items, so #[a: 1 b: 2] is two
        // long where the same source as a block would be four.
        define("length?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case MapValue map -> IntegerValue.of(map.pairCount());
                    // The shown length, never below three, so a tuple
                    // that keeps one octet still answers three.
                    case TupleValue tuple -> IntegerValue.of(tuple.shownCount());
                    // A word has a length, which is the count of code
                    // points in its spelling. The sigil takes no part,
                    // so a lit-word is as long as the plain word.
                    case WordValue word -> IntegerValue.of(
                            word.spelling().codePointCount(0, word.spelling().length()));
                    case SeriesValue series -> IntegerValue.of(series.lengthFromHere());
                    // An object answers how many fields it holds, which the C
                    // gives as `SERIES_TAIL(VAL_OBJ_FRAME(value)) - 1` -- the
                    // slot count without SELF. A module and a port answer the
                    // same way, being objects underneath.
                    //
                    // Rebol's own INTERN opens with
                    // `index: 1 + length? usr: system/contexts/user`, so LOAD
                    // could not run at all without this.
                    case ObjectValue object ->
                            IntegerValue.of(object.context().fieldCount());
                    case ModuleValue module ->
                            IntegerValue.of(module.context().fieldCount());
                    case PortValue port ->
                            IntegerValue.of(port.context().fieldCount());
                    case BitsetValue set -> IntegerValue.of(set.octets().length * 8);
                    // A pair has two halves rather than two items, which
                    // makes this the wrong argument rather than an
                    // operation a pair does not support.
                    default -> raiseWrongArgument(arguments.get(0), "length?", "series");
                });

        define("first", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 1));
        define("second", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 2));
        define("third", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 3));
        // A logic index is the first item for true and the second for
        // false, which is how a rule chooses between two blocks without
        // an EITHER. Rebol's own REPLACE picks its condition that way,
        // and refusing a logic left that function unreachable.
        // `aggregate<series! map! gob! pair! date! time! tuple! bitset! port!>`
        // and `index` with no datatypes at all, because for four of those the
        // selector is a field name rather than a number.
        define("pick", List.of(Parameter.required("series"),
                        Parameter.required("index")),
                (arguments, evaluator, context) -> arguments.get(1)
                        instanceof LogicValue chosen
                        // A logic picks the first or the second, which is how
                        // `pick [a b] some-condition` reads as a choice.
                        ? pick(arguments.get(0), chosen.truth() ? 1 : 2)
                        : pickFrom(arguments.get(0), arguments.get(1)));

        // The z family counts from zero where the rest of the language
        // counts from one. Both spellings are legal at once, which is the
        // trap: `pick b 1` and `pickz b 1` are different items and
        // neither is wrong.
        define("atz", List.of(Parameter.required("series"),
                        Parameter.required("position", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? (Value) series.atIndex(clampedPosition(series,
                                ((IntegerValue) arguments.get(1)).magnitude() + 1))
                        : raiseWrongArgument(arguments.get(0), "atz", "series"));
        // None is declared and refused: the none arm answers for A_INDEXQ and
        // falls through to `Trap_Action` for A_INDEXZQ, which is cannot-use
        // rather than a rejected argument.
        define("indexz?", List.of(Parameter.required("series", positionable())),
                Set.of("xy"),
                (arguments, evaluator, context, refinements) ->
                        arguments.get(0) instanceof SeriesValue series
                                ? IntegerValue.of(series.index() - 1)
                                : raiseCannotUse(arguments.get(0), "indexz?"));
        // PICKZ counts from zero, and only forwards.
        //
        // `if (VAL_INT64(D_ARG(2)) >= 0 && !IS_BITSET(D_ARG(1)))
        // VAL_INT64(D_ARG(2)) += 1;` and then the ordinary PICK. So a negative
        // index is passed through untouched and means what it means to PICK --
        // counting back from the position -- while zero and up are shifted by
        // one. `pickz s -1` and `pick s -1` are the same question.
        define("pickz", List.of(Parameter.required("series"),
                        Parameter.required("index", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    int wanted = (int) ((IntegerValue) arguments.get(1)).magnitude();
                    return pick(arguments.get(0),
                            wanted >= 0 && !(arguments.getFirst() instanceof BitsetValue)
                                    ? wanted + 1
                                    : wanted);
                });

        // PAST? asks whether the position is beyond the tail, which an
        // ordinary series can never be: even the tail itself is not past
        // it. It answers true only for a position left behind by a
        // series that has since shrunk.
        define("past?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? LogicValue.of(series.index() > series.storageLength() + 1)
                        : raiseWrongArgument(arguments.get(0), "past?", "series"));

        define("swap", List.of(Parameter.required("series"), Parameter.required("with")),
                (arguments, evaluator, context) -> {
                    // SWAP names gob! in its spec and `REBTYPE(Gob)` has no arm
                    // for it, so the declaration lets a gob through and
                    // `Trap_Action` refuses it. Which is a different error from
                    // the one COPY gives, and COPY does not name gob! at all.
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
                    // A binary swaps bytes exactly as a string swaps
                    // characters. Left out rather than deliberately
                    // refused, which made a protected binary report the
                    // wrong failure.
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

        // FIRST+ answers the first item and leaves the series one along,
        // which is the whole difference from FIRST.
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
        // None answers true here and yet `tail? none` still refuses,
        // because the two are not the same call. EMPTY? is this same
        // action under a spec that admits none -- Rebol's own
        // mezz-series.reb writes it as `make :tail? [...]` -- so the body
        // has to answer for none while the word's own parameter list
        // turns it away. Putting the none handling in EMPTY? instead does
        // not work: that definition is overwritten by the borrowed file.
        define("tail?", List.of(Parameter.required("series", SERIES_LIKE)),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case NoneValue ignored -> LogicValue.yes();
                    // A map has no position to be at the end of, so this
                    // asks how many pairs it holds. R3's EMPTY? spec
                    // admits a map, and EMPTY? is this same action under
                    // that wider spec.
                    case MapValue map -> LogicValue.of(map.pairCount() == 0);
                    // A typeset answers TAIL? for one reason, and the C says
                    // so in a comment: "Necessary to make EMPTY? work". There
                    // is no position to be at the end of; the question is
                    // whether the set holds anything.
                    case TypesetValue kinds ->
                            LogicValue.of(kinds.members().isEmpty());
                    // And a bitset for the same reason and with the same
                    // comment beside it: `return (VAL_TAIL(value) == 0) ?
                    // R_TRUE : R_FALSE; // Necessary to make EMPTY? work`.
                    case BitsetValue members ->
                            LogicValue.of(members.octets().length == 0);
                    // `SERIES_TAIL(VAL_OBJ_FRAME(value)) <= 1` -- slot one is
                    // self, so an object is empty when self is all it holds.
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
        // None answers none rather than refusing, which is one of the
        // three places in this family that forgives it -- LENGTH? and
        // EMPTY? are the others, and FIRST, HEAD, NEXT and the rest all
        // still raise. Three named exceptions, not a rule about none.
        // /XY asks for the position as a pair, and one typeclass answers it:
        // `VAL_PAIR_X(D_RET) = index % VAL_IMAGE_WIDE(value)` in t-image.c.
        // Every other one goes through f-series.c, whose arm never reads the
        // refinement at all, so the answer is the plain number. There is no
        // image datatype here, thus nothing to divide by a width.
        define("index?", List.of(Parameter.required("series", positionable())),
                Set.of("xy"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case NoneValue nothing -> nothing;
                    case SeriesValue series -> IntegerValue.of(series.index());
                    default -> raiseCannotUse(arguments.get(0), "index?");
                });

        // APPEND mutates the storage, so every value pointing into it sees the
        // change. It gives back the series at its head, which is what makes
        // `append a b` usable as an expression.
        // /DUP repeats what is being added, so `append/dup [1] 2 3` is
        // [1 2 2 2]. Its count had no parameter, so the number was never
        // consumed: it leaked out as the expression's value and the value
        // went in once.
        define("append", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("dup", "count", DUP_COUNT)),
                Set.of("part", "only", "dup"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case BlockValue block -> {
                        if (duplicated(arguments.get(1), arguments, refinements)
                                instanceof BlockValue added) {
                            firstFew(arguments.get(1), added.remaining(),
                                    arguments, refinements, 2)
                                    .forEach(block.storage()::append);
                        } else {
                            block.storage().append(arguments.get(1));
                        }
                        yield block.head();
                    }
                    case BinaryValue bytes -> {
                        // Worked out in full before anything is added,
                        // because the source may be the same binary:
                        // `append b b` would otherwise grow the thing it
                        // is reading and never finish.
                        for (int octet : octetsContributedBy(
                                duplicated(arguments.get(1), arguments, refinements),
                                partCountFor(arguments, refinements))) {
                            bytes.storage().append(octet);
                        }
                        yield bytes.head();
                    }
                    case ObjectValue object ->
                            objectGainingFields(object, arguments, refinements, "append");
                    // A map takes pairs rather than items, and has no tail to
                    // add them at, so APPEND on one means "put these in".
                    case MapValue map ->
                            addPairsToMap(map, arguments, refinements, "append");
                    case BitsetValue members -> {
                        requireChangeable(members);
                        members.holdAll((BitsetValue) bitsetOf(arguments.get(1)), true);
                        yield members;
                    }
                    case StringValue string -> {
                        // A count past the end takes what is there rather
                        // than raising, the same way COPY/PART does.
                        //
                        // A block is run together rather than formed: each
                        // item is formed and the results concatenated with
                        // nothing between. Forming the block puts a space
                        // between every pair, which is what FORM means and
                        // what APPEND must not do -- Rebol's own REJOIN
                        // builds strings this way and came out spaced.
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
                    // A gob appends into its pane, and /part, /only and /dup are
                    // all refused outright: `if (DS_REF(AN_PART) || DS_REF(AN_ONLY)
                    // || DS_REF(AN_DUP)) Trap0(RE_NOT_DONE);`.
                    case GobValue gob -> {
                        refuseUnfinishedRefinements(refinements, "append");
                        insertChildren(gob, gob.storage().length() + 1,
                                arguments.get(1));
                        yield gob;
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

        // ++ and -- change the word in place and answer what it held
        // BEFORE the change, which is the reverse of the obvious reading.
        // On a series they step the position rather than the contents, so
        // one operation covers counting and walking: in REBOL a position
        // is a value like any other.
        //
        // The word is taken as written rather than evaluated, because the
        // point is to change the slot it names.
        defineStepper("++", 1);
        defineStepper("--", -1);

        // TRUNCATE throws away everything before the current position, so
        // a series skipped past its first two items keeps only what is
        // left. /PART bounds how much of the rest is kept.
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

        // `index [number! logic! pair!]` -- a pair because an image reads one as
        // a coordinate: `diff = ((y - (action == A_AT ? 1 : 0)) * wide + x)` in
        // t-image.c, which is the one place a navigation action consults a
        // width. Every other series refuses a pair at the door.
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

        // COPY is what stops a mutation reaching everywhere. Series share
        // storage by design, so anything that wants its own must ask.
        // /PART takes the first few rather than all of them, and /DEEP
        // copies what the series holds as well as the series itself.
        // Without /DEEP the new block's items are the very same inner
        // ones, so a change through either is visible through both.
        //
        // /PART was a separate function named "copy/part" until this was
        // written. A word with a slash in it is not a refinement: it
        // answers the one call it is named for, which is why `copy/part`
        // worked and `copy/deep` raised no-refine.
        // /TYPES names which datatypes get copied and which are shared, and it
        // changes what /DEEP means:
        //     if (D_REF(ARG_COPY_DEEP))
        //         types |= CP_DEEP | (D_REF(ARG_COPY_TYPES) ? 0 : TS_DEEP_COPIED);
        //     if (D_REF(ARG_COPY_TYPES)) { ... types |= the named set ... }
        //
        // So /DEEP alone copies the standard set at every level, and
        // /DEEP/TYPES copies only what was named -- recursing, but reaching into
        // nothing else on the way. `copy/deep/types b string!` copies the
        // strings and leaves every block inside shared, which no combination of
        // the other refinements can express.
        define("copy", List.of(Parameter.required("value", copyable()),
                        Parameter.belongingTo("part", "limit", Set.of()),
                        Parameter.belongingTo("types", "kinds",
                                Set.of(Datatype.TYPESET, Datatype.DATATYPE))),
                Set.of("part", "deep", "types"),
                (arguments, evaluator, context, refinements) -> {
                    Value original = arguments.getFirst();
                    boolean deeply = refinements.contains("deep");
                    Set<Datatype> kinds = whichDatatypesToCopy(arguments, refinements);
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

        // FIND gives the series positioned where the match starts, so the
        // result is both an answer and somewhere to carry on from.
        // FIND answers the position of what it found rather than the value
        // or its index, so what follows can be read from it.
        //
        // On a string it searches for a substring. Comparing item by item
        // is right for a block and wrong for a string, and the difference
        // stayed invisible for as long as every corpus entry searched a
        // block.
        define("find",
                List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("with", "wild", Set.of(Datatype.STRING)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("tail", "last", "only", "case", "any", "same", "part",
                        "with", "skip", "reverse", "match"),
                (arguments, evaluator, context, refinements) -> {
                    // Looking in nothing finds nothing, so a result can be
                    // passed straight on without being tested first.
                    if (arguments.get(0) instanceof NoneValue) {
                        return NoneValue.none();
                    }
                    // An object answers whether it has the field, where
                    // SELECT answers what the field holds. One arm of
                    // t-object.c serves both and one line parts them:
                    //     if (action == A_FIND) goto is_true;
                    //
                    // So the answer is TRUE or NONE, never the value, and a
                    // field holding none still answers true. That is the
                    // whole reason to ask FIND rather than SELECT.
                    if (isAnyObject(arguments.getFirst())) {
                        return objectHasFieldToFind(arguments.getFirst(), arguments.get(1))
                                ? LogicValue.of(true)
                                : NoneValue.none();
                    }
                    // A map answers the key it stored rather than TRUE, which
                    // is the one thing FIND on a map is good for: the stored
                    // key may not be the one that was asked for. `// find
                    // returns the key` says so in the C, and a word key is
                    // held as a set-word, so `find m 'a` answers `a:`.
                    if (arguments.getFirst() instanceof MapValue map) {
                        return map.storedKeyLike(arguments.get(1));
                    }
                    // A set has no position, so FIND cannot answer one.
                    // This is the one place it gives a logic.
                    if (arguments.get(0) instanceof BitsetValue bitset) {
                        return LogicValue.of(arguments.get(1) instanceof CharacterValue character
                                && bitset.holds(character.codepoint()));
                    }
                    // A typeset answers whether rather than where, the
                    // way a bitset does: it has no order and no position,
                    // so there is nowhere to point at.
                    if (arguments.get(0) instanceof TypesetValue typeset) {
                        return LogicValue.of(arguments.get(1) instanceof DatatypeValue wanted
                                && typeset.holds(wanted.represents()));
                    }
                    // A gob looks through its pane, and only for a gob:
                    // `if (IS_GOB(arg)) { index = Find_Gob(...); } goto is_none;`
                    // -- so anything else answers none rather than raising, and
                    // none of FIND's ten refinements reaches the code at all.
                    if (arguments.get(0) instanceof GobValue searched) {
                        if (!(arguments.get(1) instanceof GobValue wanted)) {
                            return NoneValue.none();
                        }
                        int at = searched.storage().positionOf(wanted.storage());
                        return at == 0 ? NoneValue.none() : searched.atIndex(at);
                    }
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "find");
                    }
                    int limit = searchLimit(series, arguments, refinements);
                    // /skip makes the series records of that width and
                    // looks only at the first item of each, so a match
                    // halfway through a record is not a match at all.
                    long stride = searchStride(arguments, refinements);
                    // /ANY reads two of the needle's characters as
                    // wildcards, and /WITH says which two.
                    Wildcards wildcards = Wildcards.named(argumentFor(
                            "with", SEARCH_ARGUMENTS, arguments, refinements, 2));
                    // A byte holds 0 to 255, so searching a binary for
                    // anything outside that is a mistake in the caller
                    // rather than a search that missed. Answering none
                    // would hide it.
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
                    // /match insists the needle be at the position rather
                    // than anywhere ahead of it, and answers the series
                    // from there so what follows can be read off.
                    if (found < 0 || (refinements.contains("match") && found != series.index())) {
                        return NoneValue.none();
                    }
                    // ret += len in the C: /TAIL steps over the whole
                    // needle, thus a run of two steps over two.
                    return series.atIndex(refinements.contains("tail")
                            ? found + matchLength(
                                    series, arguments.get(1), refinements, found, wildcards,
                                    searchEnd(series, itemsOf(series.head()), limit))
                            : found);
                });

        // A block goes in item by item unless /only says to keep it
        // whole, which is the same way APPEND behaves.
        define("insert", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("dup", "count", DUP_COUNT)),
                Set.of("only", "part", "dup"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case BitsetValue members -> {
                        requireChangeable(members);
                        members.holdAll((BitsetValue) bitsetOf(arguments.get(1)), true);
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
                        String added = Molder.form(arguments.get(1));
                        for (int at = 0; at < added.length(); at++) {
                            text.storage().insertAt(text.index() + at, added.charAt(at));
                        }
                        yield text.atIndex(text.index() + added.length());
                    }
                    case BinaryValue strandedBytes -> {
                        BinaryValue bytes = (BinaryValue) clampedToTail(strandedBytes);
                        // Worked out in full before anything moves,
                        // because the source may be the same binary and
                        // inserting shifts what is still to be read:
                        // `insert c c` gave #{04040304} instead of
                        // #{03040304} while the source was read live.
                        int[] octets = octetsContributedBy(
                                duplicated(arguments.get(1), arguments, refinements),
                                partCountFor(arguments, refinements));
                        for (int at = octets.length; at > 0; at--) {
                            bytes.storage().insertAt(bytes.index(), octets[at - 1]);
                        }
                        yield bytes.atIndex(bytes.index() + octets.length);
                    }
                    // A map has no position, so there is no difference
                    // between adding at the front and adding at the end.
                    // One arm of the C serves both: `case A_INSERT: case
                    // A_APPEND:`.
                    case MapValue map ->
                            addPairsToMap(map, arguments, refinements, "insert");
                    case GobValue gob -> {
                        refuseUnfinishedRefinements(refinements, "insert");
                        insertChildren(gob, gob.index(), arguments.get(1));
                        yield gob;
                    }
                    default -> raiseCannotUse(arguments.get(0), "insert");
                });

        define("remove", List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "count", REMOVE_RANGE),
                        Parameter.belongingTo("key", "which", Set.of())),
                Set.of("part", "key"),
                (arguments, evaluator, context, refinements) -> {
                    // Removing from nothing answers nothing.
                    if (arguments.get(0) instanceof NoneValue nothing) {
                        return nothing;
                    }
                    if (arguments.get(0) instanceof MapValue map) {
                        // Without /KEY there is no key to remove, and the C does
                        // nothing rather than refusing: `n = Find_Entry(series,
                        // D_ARG(ARG_REMOVE_KEY_ARG), 0, TRUE);` is called with
                        // none, and `if (IS_NONE(key)) return NOT_FOUND;` stops
                        // it. The map comes back either way.
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
                    // A block read as keys and values: /KEY takes out the
                    // pair whose key matches, and a key sits at an odd
                    // place. Ignored, it took the first item out instead,
                    // whatever the caller asked for.
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

        // /PART turns round only the first few and leaves the rest where
        // they were, so `reverse/part [1 2 3 4] 2` is [2 1 3 4].
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
                    // A gob turns its pane round and answers itself: the C swaps
                    // the pointers in place and ends with `return R_ARG1`.
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

        // CHANGE replaces what is at the position rather than making room,
        // which is what separates it from INSERT.
        // /part says how much to take out, and the replacement need not
        // be the same length, so the series may grow or shrink.
        define("change", List.of(Parameter.required("series"),
                        Parameter.required("value", ANYTHING),
                        Parameter.belongingTo("part", "range", PART_LIMIT),
                        Parameter.belongingTo("dup", "count", DUP_COUNT)),
                Set.of("part", "only", "dup"),
                (arguments, evaluator, context, refinements) -> {
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
                        // CHANGE answers the position after what it wrote, where
                        // INSERT answers the head. `change/part b 1.2.3.4 2` is
                        // four bytes in and two out, so the answer is six along.
                        return series.atIndex(
                                series.index() + series.storageLength() - before);
                    }
                    // A gob changes its pane, and the C inserts rather than
                    // replaces: the replacing code sits beside it commented out.
                    // So `change g child` makes the pane one longer, and the child
                    // gains a parent.
                    if (arguments.get(0) instanceof GobValue gob) {
                        refuseUnfinishedRefinements(refinements, "change");
                        GobPath.poke(gob, gob.index(), arguments.get(1));
                        return gob.atIndex(gob.index() + 1);
                    }
                    Value replacing = duplicated(
                            arguments.get(1), arguments, refinements);
                    // A binary is changed byte by byte, and the bytes come from
                    // whatever was given by the same rules APPEND uses: a
                    // string contributes its UTF-8, a tuple its octets, a
                    // number one byte. `change #{} "^(1234)"` is three bytes
                    // into an empty binary, so this grows the binary where the
                    // replacement runs off the end rather than stopping.
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
                    if (!(arguments.get(0) instanceof BlockValue strandedBlock)) {
                        return raiseCannotUse(arguments.get(0), "change");
                    }
                    BlockValue block = (BlockValue) clampedToTail(strandedBlock);
                    // /DUP replaces that many elements rather than one, so
                    // `change/dup [1 2 3 4] 9 3` is [9 9 9 4].
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

        // CLEAR empties from here to the tail, not the whole series, which is
        // why clearing the second position keeps the first value.
        define("clear", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    // Nothing to clear, and that is an answer rather than a
                    // mistake: `if (IS_NONE(val)) return R_NONE;`.
                    case NoneValue nothing -> nothing;
                    // `Clear_Series(VAL_SERIES(value))` -- every bit goes, and
                    // the length with it, so a cleared set holds nothing rather
                    // than holding zeros. The C weighs the two readings in a
                    // comment and takes this one.
                    case BitsetValue members -> {
                        requireChangeable(members);
                        members.clear();
                        yield members;
                    }
                    // A map empties in place and answers itself, as a series
                    // does: `Clear_Series(series); if (series->series)
                    // Clear_Series(series->series);`.
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
                    // A binary is a series like the other two, and was
                    // missing here rather than deliberately left out.
                    case BinaryValue bytes -> {
                        while (bytes.storage().length() >= bytes.index()) {
                            bytes.storage().removeAt(bytes.index());
                        }
                        yield bytes;
                    }
                    // `if (tail > index) Remove_Gobs(gob, index, tail - index)` --
                    // the children from the position on, so a gob standing at its
                    // second child keeps the first.
                    case GobValue gob -> {
                        gob.storage().removeChildren(gob.index(),
                                gob.storage().length() - gob.index() + 1);
                        yield gob;
                    }
                    default -> raiseCannotUse(arguments.get(0), "clear");
                });

        // SORT folds case and is stable: equal keys keep the order they
        // arrived in, which is what makes a second sort on another key a
        // usable way to sort on two. /case compares exactly, /compare takes
        // a function of two values, and /skip sorts records by their first
        // item so a flat block of pairs stays paired.
        define("sort",
                // `series [series!]` and no more, so a gob and a map are the
                // wrong argument here rather than an operation they cannot do.
                List.of(Parameter.required("series", Typeset.SERIES.members()),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("compare", "comparator", Set.of()),
                        Parameter.belongingTo("part", "count", PART_LIMIT)),
                // /UNSTABLE names a different algorithm rather than a
                // different answer, so it is accepted and changes nothing.
                Set.of("case", "compare", "skip", "reverse", "all", "part", "unstable"),
                (arguments, evaluator, context, refinements) -> {
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "sort");
                    }
                    // Only the refinements that were asked for contribute
                    // arguments, so a position has to be worked out rather
                    // than written down.
                    List<String> declared = List.of("skip", "compare", "part");
                    Value skipSize = argumentFor("skip", declared, arguments, refinements);
                    Value partCount = argumentFor("part", declared, arguments, refinements);
                    Value comparator = argumentFor("compare", declared, arguments, refinements);
                    // /PART sorts the front and leaves the rest where it
                    // was, and /REVERSE turns the order round. The whole
                    // series comes back either way.
                    int howMany = partCount instanceof IntegerValue wanted
                            ? (int) Math.min(wanted.magnitude(), series.lengthFromHere())
                            : series.lengthFromHere();
                    howMany = Math.max(0, howMany);
                    // Nothing below is even looked at for a series of one
                    // or none, because the C leaves before it validates
                    // anything: there is no order to get wrong.
                    if (howMany <= 1) {
                        return series;
                    }
                    int stride = skipSize instanceof IntegerValue size
                            ? (int) size.magnitude()
                            : 1;
                    // A record width has to make whole records out of
                    // what is being sorted, so a width that leaves a
                    // remainder is refused rather than leaving a short
                    // record at the end to be dropped.
                    if (refinements.contains("skip")
                            && (stride < 1 || stride > howMany || howMany % stride != 0)) {
                        throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                "a record width of " + stride + " does not divide "
                                        + howMany);
                    }
                    // A column number needs records to take a column
                    // from, and without /skip there are none. Saying so
                    // beats treating every item as a record of one and
                    // sorting by the only column there is.
                    if (comparator instanceof IntegerValue column
                            && (!refinements.contains("skip")
                                    || column.magnitude() < 1
                                    || column.magnitude() > stride)) {
                        throw Raised.of(EvaluationFailure.INVALID_ARG,
                                "there is no column " + Molder.mold(comparator)
                                        + " to sort by");
                    }
                    // /ALL says compare everything and a column says
                    // compare that one, so the pair is a contradiction. A
                    // comparator function is a different matter: /ALL
                    // hands it whole records rather than single values.
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
                            howMany, evaluator);
                });

        // /CASE stops the case folding, so "a" and "A" are two members
        // rather than one. /SKIP reads the series as records of a fixed
        // width and compares whole records, so the first field of each is
        // what decides.
        defineSetOperation("intersect", Combination.INTERSECT);
        defineSetOperation("union", Combination.UNION);
        defineSetOperation("exclude", Combination.EXCLUDE);
        // UNIQUE is the fifth flag on the same C function -- `return
        // Do_Set_Operation(ds, SET_OP_UNIQUE);` -- and the only one that takes
        // one series rather than two. Written as a union of the series with
        // itself, which is what SET_OP_UNIQUE amounts to: walk it once, keep the
        // first of each.
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

        // REDUCE evaluates every expression and collects the results, which is
        // the contrast case to DO returning only the last.
        // /into puts the results into a block the caller already has and
        // answers the position after what it put, which is neither the
        // target nor the results and reads as though nothing happened
        // until you look at the target again.
        define("reduce", List.of(Parameter.required("block"),
                        Parameter.belongingTo("into", "target", Typeset.ANY_BLOCK.members()),
                        Parameter.belongingTo("only", "words", Set.of())),
                Set.of("into", "only", "no-set"),
                (arguments, evaluator, context, refinements) -> {
                    // The C's shape: work out the values, then store
                    // them. Copy_Stack_Values does the storing and it is
                    // the same for every branch, thus a value that is not
                    // a block still goes into the target when /INTO was
                    // asked for.
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
                        // Anything that is not a block comes back
                        // unchanged rather than refused. Rebol's own JOIN
                        // leans on it: `reduce :rest` where rest may be a
                        // single value.
                        return source;
                    } else {
                        results = List.of(source);
                    }

                    if (!(target instanceof BlockValue into)) {
                        // A paren reduces to a paren, which the C does by
                        // setting the answer's type after the walk.
                        return BlockValue.block(results).as(
                                source.datatype() == Datatype.PAREN
                                        ? Datatype.PAREN
                                        : Datatype.BLOCK);
                    }
                    // Insert_Series at the target's own position, and the
                    // answer is the target just past what went in. That
                    // is what lets a run of these build one series, each
                    // carrying on where the last stopped.
                    for (int at = results.size(); at > 0; at--) {
                        into.storage().insertAt(into.index(), results.get(at - 1));
                    }
                    return into.atIndex(into.index() + results.size());
                });

        // COMPOSE evaluates the parens and leaves everything else as
        // written, which is how a block of code is built from a template
        // without reducing the parts that were meant to stay.
        // Anything that is not a block is handed straight back. Refusing
        // it looks like good hygiene and is not what REBOL does, and it
        // matters for generated code: a template may come out as a single
        // value, and the caller should not have to check before composing.
        define("compose", List.of(Parameter.required("block"),
                        Parameter.belongingTo("into", "out", Typeset.ANY_BLOCK.members())),
                Set.of("only", "deep", "into"),
                (arguments, evaluator, context, refinements) -> {
                    // Anything that is not a block composes to itself,
                    // and /INTO must still put it in. Answering early
                    // here left the target untouched and handed the
                    // caller the source back where a position was owed.
                    // `if (IS_BLOCK(value) || IS_MAP(value))` in the C.
                    // A map composes as the block of pairs it holds, and
                    // a paren inside one is never spread -- `&&
                    // !IS_MAP(block)` guards the spreading.
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
                    // /INTO fills a block the caller already has, at its
                    // position, and answers the position after what it
                    // put there -- the same shape REDUCE/INTO has. A
                    // string is refused: a composed template is values,
                    // and there is no sensible text for them.
                    BlockValue target = (BlockValue) arguments.get(1);
                    for (int at = built.size(); at > 0; at--) {
                        target.storage().insertAt(target.index(), built.get(at - 1));
                    }
                    return target.atIndex(target.index() + built.size());
                });

        // TRANSCODE is the reader with no binding step, which is what LOAD
        // adds. A script reaches source text as data through it.
        define("transcode",
                List.of(Parameter.required("source"),
                        Parameter.belongingTo("line", "count", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("part", "length", Set.of(Datatype.INTEGER))),
                Set.of("one", "error", "next", "part", "line", "only"),
                (arguments, evaluator, context, refinements) ->
                        SourceReading.asAskedFor(arguments, refinements).answer());

        // ROUND goes half away from zero, not to even. A JVM rounds 2.5 to
        // 2 by default and REBOL rounds it to 3, which is the kind of
        // difference that shows up in totals long after anyone remembers.
        // ROUND goes half away from zero, not to even. /to rounds to a
        // multiple of what it is given rather than to a number of places,
        // so one rule covers money to the penny and time to the nearest
        // five minutes alike.
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
                    // A scale of zero rounds nothing, because there is no
                    // multiple of zero to round to. What happens next is the
                    // datatype conversion, and that is where the two zeroes
                    // part company: `round/to 11.65 0` is the integer 11
                    // because an integer scale truncates the answer, and
                    // `round/to 11.65 0.0` is 11.65 because a decimal scale
                    // does not. A scale so small it underflows to zero, such
                    // as 1e-400, is the decimal case.
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
                // /deep composes the blocks inside the template as well,
                // to any depth. Without it only the top level is filled
                // and an inner paren stays as it was written.
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
            // The paren runs in the caller's context, not the system's, so
            // it can see the caller's words -- `compose [(x)]` inside a
            // function reads that function's x.
            for (Value produced : evaluator.evaluateEachOrRaise(
                    paren.as(Datatype.BLOCK), context)) {
                // A paren that answers nothing contributes nothing, so
                // `compose [x (print "") y]` is [x y] rather than a block
                // with an unset sitting in the middle of it. An unset is
                // not a value a block can hold and go on being usable.
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
            // The remainder is the SOURCE advanced, not a copy of what is
            // left: the C's `Append_Val(blk, src)` hands back a position in
            // the very series it was given, which is what lets sys-load
            // measure `checksum/part mark remaining` between two of them.
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

        // The C works in [start, end) with a step. Everything below is
        // that, one for one.
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
            // /MATCH looks at one place only.
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

        // The C's datatype branch. A datatype or a typeset needle asks
        // about each value's type rather than comparing the values, thus
        // `find [1 "a"] string!` answers the string. With /ONLY it asks
        // for the datatype value itself instead -- "not checking value
        // types, only if value and target are really same".
        if ((wanted instanceof DatatypeValue || wanted instanceof TypesetValue)
                && !refinements.contains("only")) {
            return wanted instanceof DatatypeValue named
                    ? items.get(at).datatype() == named.represents()
                    : ((TypesetValue) wanted).holds(items.get(at).datatype());
        }
        // A block needle is a run of values to match, one for one, and
        // not one value. /ONLY says to treat it as one value instead.
        if (wanted instanceof BlockValue run
                && run.datatype() == Datatype.BLOCK
                && !refinements.contains("only")) {
            return runMatchesAt(items, at, run.remaining(), refinements.contains("same"));
        }
        // /SAME asks for the very same value. With /ONLY as well, a block
        // needle is that one value rather than a run, thus two blocks
        // holding the same items are still two blocks.
        // /SAME asks for the very same value, which for a block means one
        // block and not two holding the same items. On a string it means
        // the same characters, thus the run comparison below serves and
        // only the case folding changes.
        if (refinements.contains("same")
                && !(series instanceof StringValue)
                && !(series instanceof BinaryValue)) {
            return refinements.contains("only")
                    ? Comparison.isSameValue(items.get(at), wanted)
                    : sameRunAt(items, at, wanted);
        }
        // A string or a binary matches a run of its own items, thus the
        // needle is formed to text and compared character for character.
        // A block holds whole values, thus a string needle in a block is
        // one value and not a run of characters -- which is what makes
        // `find ["end"] "end"` answer the block and not none.
        if (series instanceof StringValue || series instanceof BinaryValue) {
            return textRunMatchesAt(series, items, at, wanted, refinements);
        }
        // A block holds whole values, thus a string needle is one value
        // here. matchesAtRecord treats it as a run of characters, which
        // is right for a string being searched and wrong for a block.
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
        // /SAME on a string is the same question /CASE asks, because a
        // string is compared by its characters either way. The C says so
        // outright: "/SAME has same functionality as /CASE for
        // any-string!".
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
        // A number searched for in a binary is one byte and not the text
        // of the number: `find #{0063} 99` looks for the byte 99 rather
        // than for the two characters "99".
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
            // /SAME asks series-storage identity, not content equality:
            // Compare_Values mode 3 is the same node, so two equal but
            // distinct strings do not match. isSameValue draws that line
            // where identicallyEqual only compared content.
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
        // Backwards starts at the item just before the position, so a
        // search from the tail begins on the last item rather than a
        // record's worth past it.
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
            // Formed rather than taken as text, because a tag's text is
            // what is inside the angle brackets: `<a>` searched as "a"
            // matched in the wrong places and missed the right ones. The
            // search that does not use records has always formed it.
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
            // A star at the end of the pattern takes the rest of the
            // series; a star with anything after it takes as little as it
            // can. Both confirmed against a real R3 across every
            // placement: `c*` in "abcd" ends at the tail, `*bc` in
            // "abcabc" ends at the third character rather than the sixth.
            //
            // The distinction only shows in where the match ENDS, which
            // is what /TAIL stands after, so a single rule either way
            // finds the same positions and reports half the lengths
            // wrongly.
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
        // A handle is compared by `Cmp_Handle` and not by `CT_Handle`, because
        // FIND and SORT go through `Cmp_Value` rather than through the equality
        // dispatch: `case REB_HANDLE: return Cmp_Handle(s, t);` in `f-series.c`,
        // and `if (0 == Cmp_Value(value+index, target, FALSE)) return index;` in
        // `Find_Block`.
        //
        // Which matters, because the two comparisons disagree about handles more
        // than about anything else: EQUAL? on a codec is false even against
        // itself, so a search that used it could never find one. Rebol's own
        // handle test walks a block of four with FIND and finds each at its own
        // position, which only the ordering comparison can do.
        if (item instanceof HandleValue found && wanted instanceof HandleValue looking) {
            return found.compareWith(looking) == 0;
        }
        // Minding case means minding the datatype too, so 1, 1.0 and 100%
        // stop being one another. Comparing with equals() alone left them
        // equal, because a decimal and a percent hold the same number.
        return mindingCase ? Comparison.identicallyEqual(item, wanted) : Comparison.looselyEqual(item, wanted);
    }

    /** How far past the match /tail lands, which a substring makes more than one. */
    private static int matchLength(
            SeriesValue series, Value wanted, Set<String> refinements, int found,
            Wildcards wildcards, int end) {
        if (series instanceof StringValue patterned && refinements.contains("any")) {
            // Measured from the head in absolute terms, because under
            // /REVERSE the match sits BEHIND the position and an offset
            // measured from the position is negative.
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
        // A block needle is a run of items in a block, thus /TAIL steps
        // over all of them. The C computes this as `len` before the
        // search and adds it after, and /ONLY sets it back to one.
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
            int howMany, Evaluator evaluator) {
        // A stride of zero would step nowhere and loop for ever, so a
        // caller asking for one gets records of a single item instead.
        int step = Math.max(1, stride);
        // Only the first HOWMANY items take part; whatever follows keeps
        // its place, which is what /PART means.
        List<Value> items = itemsOf(series).subList(
                0, Math.min(howMany, itemsOf(series).size()));
        List<List<Value>> records = new ArrayList<>();
        for (int at = 0; at + step <= items.size(); at += step) {
            records.add(List.copyOf(items.subList(at, at + step)));
        }
        records = mergeSorted(records, (left, right) -> {
            int order = wholeRecord && comparator == null
                    ? compareWholeRecords(left, right, mindingCase)
                    : compareRecords(
                            left, right, comparator, mindingCase, wholeRecord, series, evaluator);
            return reversed ? -order : order;
        });

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
        // With /ALL the comparator is handed whole records rather than
        // single values, which is the only way it can reach a field
        // other than the first.
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
        // An object taken out is the object, not a copy: /DEEP copies with
        // TS_DEEP_COPIED, and objects sit outside it.
        return refinements.contains("deep") && !(taken instanceof ObjectValue)
                ? copied(taken, taken instanceof BlockValue)
                : taken;
    }

    private static Value takeSeveral(SeriesValue series, long wanted) {
        // Clamped to where the series actually reaches, not just to how
        // many are left. A position can be stranded past the end --
        // `s: next [1 2] clear head s` leaves one there -- and then a
        // count of zero is still read from an index that is not there.
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
        // What comes back is a series of the kind it was taken from.
        // Answering a block for everything loses the datatype silently,
        // and the loss only shows up somewhere else -- when the result is
        // molded, or appended to something that minds what kind it is.
        return switch (series) {
            case StringValue text -> StringValue.of(taken.stream()
                    .map(Molder::form).collect(Collectors.joining()), text.datatype());
            case BinaryValue bytes -> BinaryValue.of(taken.stream()
                    .mapToInt(item -> (int) ((IntegerValue) item).magnitude()).toArray());
            case BlockValue block -> BlockValue.block(taken).as(block.datatype());
            // Pixels taken out of an image come back as an image one pixel tall,
            // because the width is fixed and the height follows the count.
            case ImageValue image -> takenPixels(image, taken);
            // `Set_Block(D_RET, Pane_To_Block(gob, index, len))` -- children come
            // out as a block of gobs, not as a gob, because there is no gob for
            // them to be the pane of.
            case GobValue ignored -> BlockValue.block(taken);
        };
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
                // `Remove_Series(series, VAL_INDEX(value), len)` and then
                // `Reset_Height`, so the image loses pixels and gets shorter.
                case ImageValue image -> image.storage().removeFrom(oneBasedIndex, 1);
                case GobValue gob -> gob.storage().removeChildren(oneBasedIndex, 1);
            }
        }
    }

    /** Half away from zero, which is what REBOL rounds and a JVM does not. */
    private static double roundedHalfAway(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** A bitset holding every character code in what it is given. */
    private static Value bitsetOf(Value source) {
        return switch (source) {
            case StringValue text -> BitsetValue.ofCharacters(text.text().codePoints().toArray());
            case CharacterValue character ->
                    BitsetValue.ofCharacters(character.codepoint());
            // A number names a bit by its position, which is what
            // `Find_Max_Bit` makes of an integer: `case REB_INTEGER: maxi =
            // (REBCNT)VAL_INT64(val) + 1;`. So `append bs 3` sets bit three, and
            // a set built from a number holds that one bit.
            case IntegerValue position ->
                    BitsetValue.ofCharacters((int) position.magnitude());
            case BinaryValue octets -> BitsetValue.of(octets.octetsFromHere());
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
        // A block of nothing but NOT and a binary is the octets as written.
        List<Value> named = rest.remaining();
        if (named.size() == 1 && named.getFirst() instanceof BinaryValue octets) {
            BitsetValue set = BitsetValue.of(octets.octetsFromHere());
            return complemented ? set.complemented() : set;
        }
        BitsetValue set = BitsetValue.ofCharacters(codePointsIn(rest));
        return complemented ? set.complemented() : set;
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
                // A string member adds each of its characters, so
                // `charset ["ab"]` holds a and b. The C reads any-string
                // this way -- REB_STRING and its siblings each spread.
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

    private Value shuffled(BlockValue block) {
        List<Value> items = new ArrayList<>(block.remaining());
        java.util.Collections.shuffle(items, randomness);
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
        java.util.Collections.shuffle(letters, randomness);
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
        java.util.Collections.shuffle(octets, randomness);
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
            // APPLY fills a function's whole word frame positionally --
            // `len = SERIES_TAIL(words) - 1` counts refinements and their
            // arguments too, so `apply :f [1 2 3]` with `func [a /b c]`
            // makes b true and c 3.
            case FunctionValue function -> function.parameters().size();
            case OperatorValue operator -> arityOf(operator.underlying());
            default -> 0;
        };
    }

    /** Spaces to tabs, or tabs to spaces, at a stop of the given width. */
    private void defineTabbing(String name, boolean toTabs) {
        define(name, List.of(
                        Parameter.required("text", Set.of(Datatype.STRING)),
                        Parameter.belongingTo("size", "width", Set.of(Datatype.INTEGER))),
                Set.of("size"),
                (arguments, evaluator, context, refinements) -> {
                    int width = refinements.contains("size") && arguments.size() > 1
                            ? (int) ((IntegerValue) arguments.get(1)).magnitude()
                            : 4;
                    String text = ((StringValue) arguments.get(0)).text();
                    return StringValue.of(toTabs
                            ? text.replace(" ".repeat(width), "\t")
                            : text.replace("\t", " ".repeat(width)));
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
                // The one source whose /part is counted in bytes,
                // because its characters are bytes.
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
            // A character is not a series, so there is nothing to take
            // part of and the count is ignored rather than cutting the
            // bytes it encodes to.
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
            // A block spreads, and each item goes in by these same
            // rules with one exception: a block inside a block is
            // refused rather than spreading again. The /part count is
            // of the block's items rather than of any one of them.
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
     * the vendored copy, past its own header.
     */
    private static List<Value> catalogueEntries() {
        try (java.io.InputStream stream =
                Natives.class.getResourceAsStream("/org/jebol/errors.reb")) {
            String source = new String(stream.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            TranscodeResult read = Transcoder.transcode(source);
            List<Value> values = read.values().orElseThrow().remaining();
            return values.subList(2, values.size());
        } catch (java.io.IOException | RuntimeException unreadable) {
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
            // `Modify_Image` puts pixels in and `Reset_Height` follows, so an
            // image grows by whole pixels and its height is recomputed. A tuple
            // is one pixel; an image contributes its own.
            case ImageValue image -> insertPixels(image, value);
            case GobValue gob -> insertChildren(gob, gob.index(), value);
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
     * The /part count a call asked for, or -1 for all of the source.
     *
     * <p>INSERT and APPEND both declare it in the same place, so the two
     * read it the same way rather than each working out where it sits.
     */
    private static int partCountFor(List<Value> arguments, Set<String> refinements) {
        Value limit = argumentFor(
                "part", List.of("part", "dup"), arguments, refinements, 2);
        return limit instanceof IntegerValue wanted ? (int) wanted.magnitude() : -1;
    }

    /** Removing one item, whichever kind of series holds it. */
    private static void removeOneAt(SeriesValue series, int index) {
        switch (series) {
            case BlockValue block -> block.storage().removeAt(index);
            case StringValue text -> text.storage().removeAt(index);
            case BinaryValue bytes -> bytes.storage().removeAt(index);
            case ImageValue image -> image.storage().removeFrom(index, 1);
            case GobValue gob -> gob.storage().removeChildren(index, 1);
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

        // Refused up front rather than on the first removal, so a series
        // whose body happens to match nothing still says no. A guard that
        // only fires when something changes is not a guard.
        refuseIfProtected(series);
        // As FOREACH: the body sees the frame it was written in.
        Context locals = Context.loopFrameOf(within);
        WordValue word = (WordValue) arguments.getFirst();
        locals.define(word.spelling());
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        for (int at = series.storageLength(); at >= series.index(); at--) {
            Value item = series instanceof BinaryValue bytes
                    ? IntegerValue.of(((BinaryValue) series).storage().at(at))
                    : CharacterValue.of(((StringValue) series).storage().at(at));
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

        // Up front rather than on the first removal, so a map whose body
        // matches nothing still says no. `if (mode==LM_REMOVE &&
        // IS_PROTECT_SERIES(series)) Trap0(RE_PROTECTED);` is checked before
        // the walk starts, for the same reason.
        requireChangeable(map);
        List<WordValue> names = loopNamesIn(arguments.getFirst(), "remove-each");
        refuseMoreNamesThanAPairHas(map, names);
        Context locals = Context.loopFrameOf(within);
        names.forEach(name -> locals.define(name.spelling()));
        BlockValue body = Binder.bind((BlockValue) arguments.get(2), locals);
        List<Value> pairs = map.walkable();
        List<Value> takeOut = new ArrayList<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            setLoopNames(locals, names, pairs, at);
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
            // A gob's pane is not a REBSER a script can PROTECT: PROTECT declares
            // `[word! series! bitset! map! object! module!]` and a gob is on none
            // of those lists, so there is no flag to read.
            case GobValue ignored -> false;
        };
        if (guarded) {
            throw new org.jebol.domain.value.ProtectedFromChange();
        }
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
        // trim_auto walks past leading whitespace, blank lines included,
        // before it measures the indent, and never emits what it skipped.
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
     * What TAIL? will look at: everything with a position or a size.
     *
     * <p>Written out rather than taken as "every series", because the
     * list is what decides whether `tail? none` refuses. NONE is
     * deliberately absent: EMPTY? is the same action under a spec that
     * adds it.
     */
    private static final Set<Datatype> SERIES_LIKE = EnumSet.of(
            Datatype.STRING, Datatype.FILE, Datatype.URL, Datatype.EMAIL,
            Datatype.TAG, Datatype.REF, Datatype.BINARY,
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH, Datatype.SET_PATH,
            Datatype.GET_PATH, Datatype.LIT_PATH, Datatype.HASH,
            Datatype.PORT, Datatype.BITSET, Datatype.OBJECT, Datatype.MODULE,
            Datatype.TYPESET, Datatype.MAP, Datatype.GOB, Datatype.IMAGE);

    /**
     * UNION, INTERSECT or EXCLUDE, which differ only in what they keep.
     *
     * <p>/CASE stops the case folding and /SKIP reads both series as
     * records of a fixed width, comparing whole records rather than
     * single items.
     */
    private void defineSetOperation(String name, Combination how) {
        define(name, List.of(
                        Parameter.required("first"),
                        Parameter.required("second"),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    Value width = argumentFor("skip", List.of("skip"), arguments, refinements, 2);
                    int stride = width instanceof IntegerValue wanted
                            ? (int) Math.max(1, wanted.magnitude())
                            : 1;
                    return combined(arguments, how, refinements.contains("case"), stride);
                });
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
            return DEEP_COPIED;
        }
        Value kinds = arguments.getLast();
        return switch (kinds) {
            case DatatypeValue one -> EnumSet.of(one.represents());
            case TypesetValue several -> EnumSet.copyOf(several.members());
            default -> DEEP_COPIED;
        };
    }

    private static Value copied(Value original, boolean deeply, Set<Datatype> kinds) {
        // The top level is always copied. What /DEEP and /TYPES decide is what
        // happens to the values inside it: `Copy_Block_Values` copies the block
        // and then `Copy_Deep_Values` walks the copy replacing the series of
        // every value whose datatype is in the set.
        // A top-level object still copies -- `copy stats/profile` is a real
        // snapshot -- but a nested one inside a deep copy stays shared:
        // objects are outside TS_DEEP_COPIED, so the block arm's recursion
        // passes them through, which is what take/deep relies on.
        if (!kinds.contains(original.datatype()) && original != null
                && !(original instanceof SeriesValue) && !(original instanceof MapValue)
                && !(original instanceof BitsetValue)
                && !(original instanceof ObjectValue)) {
            return original;
        }
        return switch (original) {
            case BlockValue block -> new BlockValue(new BlockStorage(
                    deeply
                            ? block.remaining().stream()
                                    .map(item -> kinds.contains(item.datatype())
                                            ? copied(item, true, kinds)
                                            : item)
                                    .toList()
                            : block.remaining()),
                    1, block.datatype());
            case StringValue text -> StringValue.of(text.text(), text.datatype());
            case BinaryValue binary -> copiedBytes(binary, binary.lengthFromHere());
            // A bitset can be written through a path, thus COPY has to
            // duplicate its octets. Without this, writing to the copy wrote
            // to the original, and Rebol's own url-parser silently added a
            // percent sign to the catalogue's URI set.
            case BitsetValue members -> members.duplicate();
            // A copied object holds its own slots. Sharing them made
            // `copy stats/profile` no snapshot at all, and DELTA-PROFILE
            // subtracted a window from itself.
            case ObjectValue object -> {
                Context fields = Context.root();
                ObjectValue duplicate = new ObjectValue(fields);
                fields.set("self", duplicate);
                object.context().slots().stream()
                        .filter(slot -> !slot.canonical().equals("self"))
                        .forEach(slot -> fields.set(slot.spelling(),
                                deeply && kinds.contains(slot.value().datatype())
                                        ? copied(slot.value(), true, kinds)
                                        : slot.value()));
                yield duplicate;
            }
            default -> original;
        };
    }

    /** The first few of a series, copied, and deeply when asked. */
    private static Value copiedFront(
            SeriesValue series, Value limit, boolean deeply, Set<Datatype> kinds) {
        long wanted = countUpTo(series, limit);
        SeriesValue from = limit instanceof SeriesValue upTo
                ? earlierOf(series, upTo)
                : series;
        // A negative count takes that many from BEHIND the position, and moves
        // the start back to reach them. `Partial1` is three lines:
        //     len = -len;
        //     if (len > VAL_INDEX(sval)) len = VAL_INDEX(sval);
        //     VAL_INDEX(sval) -= len;
        //
        // So `copy/part tail "ABC" -3` is "ABC". Clamping the count at zero
        // instead made every negative /PART answer nothing, which looks like
        // an empty series rather than a count read the wrong way round.
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
            // A copy of an image is an image, and `makeCopy2` keeps the width so
            // the copy has the shape the original's first rows had.
            case ImageValue image -> copiedPixels(image, taking);
            // `REBTYPE(Gob)` has no A_COPY arm, so `Trap_Action` refuses it: a gob
            // is a thing on a screen with one parent, and a copy of it would be a
            // second gob claiming the same children.
            case GobValue gob -> raiseCannotUse(gob, "copy");
        };
    }

    private static BinaryValue copiedBytes(BinaryValue bytes, int howMany) {
        BinaryStorage copiedStorage = new BinaryStorage();
        for (int at = 0; at < howMany; at++) {
            copiedStorage.append(bytes.storage().at(bytes.index() + at));
        }
        return new BinaryValue(copiedStorage, 1);
    }

    /** A branch run, or handed back untouched when /ONLY asked for that. */
    private static Value branchTaken(
            BlockValue branch, Evaluator evaluator, Context context, Set<String> refinements) {

        return refinements.contains("only")
                ? branch
                : evaluator.evaluateOrRaise(branch, context);
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
            // A gob's REVERSE arm takes no /PART: `for (index = 0; index <
            // tail/2; index++)` walks the whole pane and nothing narrows it.
            case GobValue gob -> raiseCannotUse(gob, "reverse/part");
            // REVERSE has no arm in `t-image.c`, so the shared series one serves
            // and it reverses pixels in place.
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
        // `IS_WORD(val) || (ANY_PATH(val) && !D_REF(4))` -- a path WITH
        // /values is the path value itself, protected as a series, so it
        // falls through to setProtection instead of locking the field.
        if (refinements.contains("values")) {
            return false;
        }
        ContextSlot field = fieldNamedBy(path.remaining());
        if (field == null) {
            return true;
        }
        // /HIDE conceals rather than locks, so it is a separate thing to
        // set and the two do not imply each other.
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
        // A lone word is a list of one. The C decides the same way, on
        // whether the argument is a block rather than on the refinement:
        // `if (IS_BLOCK(D_ARG(2)) && !D_REF(3))`. Rebol's own mezz-logger.reb
        // writes `protect/words/lock 'log-levels`, one word and no block.
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
            // Whatever the word happens to hold. A number or an unset
            // carries no protection, and /VALUES walks a list the caller
            // wrote rather than one it chose, so those are passed over
            // rather than refused. Confirmed against a real R3.
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
                    // /LOCK protects for good: UNPROTECT will not release it.
                    // The C states it on the releasing side -- "unprotect
                    // series only when not locked" -- so the flag lives with
                    // the slot rather than with this call.
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
                    // Only what can carry protection. Walking into the
                    // numbers and refusing them made `protect/deep` fail
                    // on every block that held one.
                    block.remaining().stream()
                            .filter(item -> item instanceof SeriesValue
                                    || item instanceof ObjectValue)
                            .forEach(item -> setProtection(item, protectedNow, true));
                }
            }
            case StringValue text -> text.storage().protectFromChange(protectedNow);
            case BinaryValue bytes -> bytes.storage().protectFromChange(protectedNow);
            // A map is protected the way a series is, by the same line:
            //     if (ANY_SERIES(value) || IS_MAP(value) || IS_BITSET(value))
            //         Protect_Series(value, flags);
            //
            // /DEEP stops here. Protect_Series walks into what it holds only
            // for a block -- `if (!ANY_BLOCK(val) || !GET_FLAG(flags,
            // PROT_DEEP)) return;` -- and a map is not one.
            case MapValue map -> map.protectFromChange(protectedNow);
            case ObjectValue object -> {
                // Three separate things, and the refinements pick which
                // an UNPROTECT releases: plain frees the object and its
                // words but not their values, /WORDS frees only the
                // words, /DEEP frees all three, /WORDS/DEEP frees the
                // words and their values but not the object.
                //
                // /WORDS locks the words it has and leaves the object open
                // to new ones: `if (!GET_FLAG(flags, PROT_WORDS))
                // PROTECT_SERIES(series);` -- so EXTEND and APPEND still
                // work on a protect/words object.
                if (!onlyTheWords) {
                    object.context().closeToNewNames(protectedNow);
                }
                object.context().slots().forEach(slot -> {
                if (protectedNow) {
                    slot.protectFromAssignment();
                } else {
                    slot.allowAssignment();
                }
                // /DEEP reaches what the fields hold, including an object
                // inside an object. Stopping at the outer one leaves
                // `o/b/c: 4` free on a supposedly deeply protected o,
                // which is the case a caller reached for /DEEP to cover.
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
                // `if (GET_FLAG(flags, PROT_DEEP)) { val = Get_Var(word);
                // ... Protect_Value(val, flags); }` -- /DEEP reaches what
                // the word holds, so `protect/deep 'obj` locks the object.
                if (deeply && carriesProtection(slotOf(word).value())) {
                    setProtection(slotOf(word).value(), protectedNow, true, onlyTheWords);
                }
            }
            case BitsetValue members -> members.protectFromChange(protectedNow);
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
            return count.magnitude();
        }
        // `n = (REBINT)VAL_DECIMAL(val);` in Int32, which is a C cast and
        // so cuts the fraction off rather than rounding it. A percent is
        // not a decimal to Partial1 and is refused below.
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
            case BitsetValue members -> LogicValue.of(
                    selector instanceof CharacterValue letter
                            && members.holds(letter.codepoint()));
            case MapValue map -> map.select(selector);
            case DateValue date -> DateParts.of(date, selector);
            case TimeValue time -> pickTimePart(time, selector);
            // `if (!IS_NUMBER(arg) && !IS_NONE(arg)) Trap_Arg(arg)` -- so a field
            // name is the wrong argument rather than a field read, and NONE reads
            // as a zero and answers none.
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
            // Read as far as the shown length rather than the kept one,
            // so a tuple of one octet answers zero for its second and
            // third and none only past the third.
            return oneBasedIndex < 1 || oneBasedIndex > tuple.shownCount()
                    ? NoneValue.none()
                    : IntegerValue.of(tuple.octetAt(oneBasedIndex));
        }
        if (target instanceof PairValue pair) {
            return pair.halfAt(oneBasedIndex).orElseGet(NoneValue::none);
        }
        // A gob picks out of its pane, and it counts differently from every other
        // series here: `index` is unsigned in the C, so `pick g 0` and `pick g -1`
        // wrap to something enormous and answer none rather than reaching behind
        // the position.
        if (target instanceof GobValue gob) {
            return GobPath.childOf(gob, oneBasedIndex);
        }
        if (!(target instanceof SeriesValue series)) {
            return raiseCannotUse(target, "pick");
        }
        // `Pick_Block`: `if (n == 0) return 0; if (n < 0) n++; n += VAL_INDEX
        // (block) - 1; if (n < 0 || n >= VAL_TAIL(block)) return 0;`
        //
        // The same three lines PD_Block uses, so a PICK and a path agree. Two
        // things follow that a count-from-one reading misses: there is no
        // position zero, and a negative position counts back from where the
        // series is and reaches behind it. `pick tail s -1` is the last item,
        // and `pick s -1` at the head is none.
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
            // PICK on an image is its path handler: `Pick_Path(value, arg, 0)`,
            // so a pixel comes back as a tuple.
            case ImageValue image -> ImagePath.read(image.head(), IntegerValue.of(at));
            // Unreachable: the gob branch above answers first, because a gob
            // counts its positions the way the C's unsigned arithmetic does.
            case GobValue gob -> GobPath.childOf(gob.head(), at);
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
            // `val = Get_Simple_Value(val);` -- after the two checks above and
            // before the setter, so a word standing for an unset raises need-value
            // for the word rather than for what it holds.
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
        // `if (codi.error != 0) { if (result == CODI_CHECK) return R_FALSE;
        // Trap0(RE_BAD_MEDIA); }` -- so an error after an identify is an answer and
        // an error after anything else is a failure.
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

    /** What a set operation takes besides a block, plus whatever is named. */
    private static Set<Datatype> setOperandOr(Datatype... alsoAccepted) {
        Set<Datatype> accepted = EnumSet.of(
                Datatype.BITSET, Datatype.TYPESET, Datatype.STRING,
                Datatype.BINARY, Datatype.DATE);
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
                // Symmetric, where EXCLUDE is one-sided. Xandor_Bitset takes
                // the same four operators over a bitset's octets.
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
        // A bitset combines bit by bit rather than item by item, which is
        // how a rule builds a character class out of named pieces instead
        // of spelling every member.
        if (arguments.get(0) instanceof BitsetValue ours
                && arguments.get(1) instanceof BitsetValue theirs) {
            return combinedBitsets(ours, theirs, how);
        }
        // A typeset combines datatype by datatype, which n-sets.c does with
        // four bitwise operators over the one word that holds the whole set:
        //     UNION      |=
        //     INTERSECT  &=
        //     DIFFERENCE ^=
        //     EXCLUDE    &= ~
        if (arguments.get(0) instanceof TypesetValue oursByType
                && arguments.get(1) instanceof TypesetValue theirsByType) {
            return combinedTypesets(oursByType, theirsByType, how);
        }
        // `set1 [block! string! bitset! typeset! map!]` is what every one of
        // these declares, so a string is an ordinary argument and not an
        // afterthought: it is a series of characters and the same set rules
        // apply. Casting straight to a block threw a ClassCastException out of
        // the interpreter, which spec/embed.allium forbids outright.
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
                // A block's DIFFERENCE is built by the native itself, from
                // both directions, so it never reaches here.
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
        // The two half rules differ from the default only on an exact
        // half, and from each other only in which way that half goes.
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

    // ---- strings ---------------------------------------------------------

    /**
     * Turning bytes into text and back: ENHEX, DEHEX, ENBASE, DEBASE,
     * CHECKSUM, COMPRESS, DECOMPRESS and SWAP-ENDIAN.
     *
     * <p>Every spec here is the one declared in the C, verbatim. The work is
     * in {@link Encodings}, which knows nothing about REBOL values; these are
     * the thinnest wrapper that reaches it.
     */
    private void defineEncodings() {
        // enhex: value [any-string! binary!] /escape char [char!]
        //        /except unescaped [bitset!] /uri
        //
        // The default unescaped set follows the datatype of the value: the URI
        // set for a file or a url, the narrower component set for anything
        // else. That is what makes `enhex http://a/b` keep its slashes and
        // `enhex "a/b"` not have to.
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

        // dehex: value [any-string! binary!] /escape char [char!] /uri
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

        // enbase: value [binary! any-string! integer!] base [integer!]
        //         /url /part limit /flat
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

        // debase: value [binary! any-string!] base [integer!] /url /part limit
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

        // checksum: data [binary! string! file!] method [word!]
        //           /with spec [any-string! binary! integer!] /part length
        //
        // A string is hashed as its UTF-8 bytes, so the same text hashes the
        // same however it arrived. Hashing codepoints would make a file and
        // the string read out of it disagree.
        define("checksum", List.of(
                        Parameter.required("data",
                                anyStringOr(Datatype.BINARY)),
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

        // compress: data [binary! string!] method [word!] /part length
        //           /level lvl [integer!]
        define("compress", List.of(
                        Parameter.required("data", anyStringOr(Datatype.BINARY)),
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

        // decompress: data [binary!] method [word!] /part length /size bytes
        define("decompress", List.of(
                        Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.required("method", Set.of(Datatype.WORD)),
                        Parameter.belongingTo("part", "length", PART_LIMIT),
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

        // encloak / decloak: data [binary!] "(modified)"
        //                    key [string! binary! integer!] /with
        //
        // Rebol's own scrambler, in place. `Cloak` in s-ops.c does both
        // directions, and the C returns R_ARG1 -- the binary it was given.
        //
        // A protected series is refused before anything is written:
        // `if (IS_PROTECT_SERIES(VAL_SERIES(data))) Trap0(RE_PROTECTED);`
        defineCloak("encloak", false);
        defineCloak("decloak", true);

        // iconv: data [binary!] codepage [word! integer! tag! string!]
        //        /to target [word! integer! tag! string!]
        //
        // Two answers from one function, and the refinement chooses. Without
        // /TO it answers a string; with /TO it answers a binary. Decoding and
        // transcoding are different operations, and one of them has no text
        // form to answer.
        define("iconv", List.of(
                        Parameter.required("data", Set.of(Datatype.BINARY)),
                        Parameter.required("codepage", characterSetNames()),
                        Parameter.belongingTo("to", "target", characterSetNames())),
                Set.of("to"),
                (arguments, evaluator, context, refinements) -> {
                    byte[] octets = ((BinaryValue) arguments.getFirst()).octetsFromHere();
                    java.nio.charset.Charset from = characterSetFor(arguments.get(1));
                    String text = new String(octets, from);
                    if (!refinements.contains("to")) {
                        return StringValue.of(text);
                    }
                    Value target = argumentFor("to", List.of("to"),
                            arguments, refinements, 2);
                    return binaryOfBytes(text.getBytes(characterSetFor(target)));
                });

        // filter: data [binary!] width [number!] type [integer! word!]
        //         /skip bpp [integer!]
        //
        // The five per-scanline transforms a PNG applies before compressing.
        // Byte arithmetic and nothing else -- the work is in Encodings.
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

        // unfilter: data [binary!] width [number!]
        //           /as type [integer! word!] /skip bpp [integer!]
        //
        // Without /AS each line opens with a byte naming its own filter, which
        // is how a PNG stores it. So the two forms take different widths and
        // the type byte is the difference -- the C adjusts before it checks:
        // `if (!ref_as) width++;`.
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

        // swap-endian: value [binary!] "At position (modified)"
        //              /width bytes [integer!] /part range
        //
        // In place, and it answers the binary it was given: the spec says
        // "(modified)" and the C writes through `VAL_BIN_DATA`.
        define("swap-endian", List.of(
                        Parameter.required("value", Set.of(Datatype.BINARY)),
                        Parameter.belongingTo("width", "bytes", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("part", "range", PART_LIMIT)),
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
                    try {
                        Encodings.swapEndian(octets, 0, width);
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
            // "More than none" is the C's `VAL_TYPE > REB_NONE`, which counts
            // unset and none as the empty end of the order and everything else
            // as a value.
            boolean targetHoldsSomething = slot.value().datatype() != Datatype.NONE
                    && slot.value().datatype() != Datatype.UNSET;
            boolean sourceHoldsNothing = supplied.datatype() == Datatype.NONE
                    || supplied.datatype() == Datatype.UNSET;
            if (onlySome && targetHoldsSomething && sourceHoldsNothing) {
                continue;
            }
            slot.setValue(supplied);
        }
        // After the name-matched copy, every taken value is cloned and its
        // words rebound from the source frame to the target's --
        // `Copy_Deep_Values(obj, 1, tail, TS_CLONE)` then `Rebind_Block` --
        // so the target shares no series with the source and a copied
        // method reads the target's own fields.
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
        // version: /data "loadable version"
        //
        // Two forms because two callers want different things: a banner
        // prints the text, and a module header compares the tuple against its
        // Needs field. A string will not compare with a tuple.
        define("version", List.of(), Set.of("data"),
                (arguments, evaluator, context, refinements) ->
                        refinements.contains("data")
                                ? TupleValue.of(VERSION_PARTS)
                                : StringValue.of(VERSION_TEXT));

        // pokez: series [series! bitset! tuple!] index [integer!] value
        //
        // POKE underneath, counting from zero. The C is three lines and the
        // condition on the first is all of it:
        //     if (VAL_INT64(D_ARG(2)) >= 0 && !IS_BITSET(D_ARG(1)))
        //         VAL_INT64(D_ARG(2)) += 1;
        //
        // A negative index is left alone, because counting back from the tail
        // means the same in both conventions. A bitset is left alone too: its
        // index is a character code rather than a position, so adding one
        // would name a different character.
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

        // to-real-file: path [file! string!]
        //
        // None for a file that is not there. The C's own summary says so:
        // "resolves symbolic links and returns NONE if file does not exists!".
        // The question is "what is this really", and nothing is a true answer.
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

        // recycle: /off /on /ballast size [integer!] /torture /pools
        //
        // Answers the bytes the collection RELEASED, not the bytes in use.
        // The C ends `released_bytes = Recycle(TRUE, D_REF(6));
        // DS_Ret_Int(released_bytes);` and the two readings move in opposite
        // directions as a program allocates, so it matters which one this is.
        //
        // /OFF answers nothing and collects nothing. It is the first thing the
        // C does: `if (D_REF(1)) { GC_Active = FALSE; return R_UNSET; }`.
        //
        // On a host that collects when it chooses, the collection is a request
        // and the number is measured either side of it. A collector that did
        // nothing answers zero, which is the truth rather than a placeholder.
        define("recycle", List.of(
                        Parameter.belongingTo("ballast", "size", Set.of(Datatype.INTEGER))),
                Set.of("off", "on", "ballast", "torture", "pools"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("off")) {
                        return UnsetValue.unset();
                    }
                    Runtime running = Runtime.getRuntime();
                    long before = running.totalMemory() - running.freeMemory();
                    System.gc();
                    long after = running.totalMemory() - running.freeMemory();
                    return IntegerValue.of(Math.max(0, before - after));
                });

        // stats: /show /profile /timer /evals /clear
        // /DUMP-SERIES takes the pool to dump and answers none:
        // `Dump_Series_In_Pool(VAL_INT32(pool_id)); return R_NONE;`. What it
        // prints is a walk of Rebol's own memory pools, which a JVM does not
        // have -- so the printing is what is missing here and the answer is
        // not. There is no /CLEAR: JEBOL had declared one that no line read.
        define("stats", List.of(
                        Parameter.belongingTo("dump-series", "pool-id",
                                Set.of(Datatype.INTEGER))),
                Set.of("show", "profile", "timer", "evals", "dump-series"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("dump-series")) {
                        return NoneValue.none();
                    }
                    // Time since this interpreter started, not the time of
                    // day: `OS_Delta_Time(PG_Boot_Time, 0) * 1000`. That is
                    // what makes it useful for measuring a stretch of work.
                    if (refinements.contains("timer")) {
                        return TimeValue.ofNanoseconds(System.nanoTime() - startedAt);
                    }
                    // /EVALS is the one with a rule rather than a reading: it
                    // counts values the evaluator has walked, and it must
                    // never go backwards inside one interpreter or a caller
                    // timing a stretch of work reads a negative difference.
                    if (refinements.contains("evals")) {
                        return IntegerValue.of(evaluator.valuesWalked());
                    }
                    // /PROFILE fills in system/standard/stats and answers
                    // that same object, refreshed in place. The C does exactly
                    // that -- `stats = Get_System(SYS_STANDARD, STD_STATS);
                    // *ds = *stats;` -- and DELTA-PROFILE depends on the
                    // sharing: it copies the object, runs a block, asks again,
                    // and differences the copy against the object it still
                    // holds. Answering a fresh object each time would make
                    // every difference zero.
                    if (refinements.contains("profile")) {
                        return filledInProfile(evaluator);
                    }
                    // The plain form is `Inspect_Series(flags)`, which
                    // measures the interpreter's own series rather than the
                    // whole heap. The JVM does not separate the two, so this
                    // reports the heap in use and says so rather than
                    // pretending to a figure it cannot get.
                    Runtime running = Runtime.getRuntime();
                    return IntegerValue.of(running.totalMemory() - running.freeMemory());
                });

        // echo: target [file! none! logic!]
        //
        // "Copies console output to a file" -- a copy, not a redirection, so a
        // script that echoes still prints. NONE and FALSE turn it off; TRUE
        // echoes to output.txt, which the C spells out:
        //     else if (IS_LOGIC(val) && IS_TRUE(val))
        //         ser = To_Local_Path("output.txt", 10, OS_WIDE, TRUE);
        //
        // And the C turns the previous echo off first, with `Echo_File(0)`
        // before it looks at the argument, so echoing twice replaces rather
        // than stacking.
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
                        // Emptied first, so a second run does not read as one
                        // long session. The C opens the file fresh too.
                        files.write(path, new byte[0]);
                        evaluator.alsoWriteTo(text -> files.appendTo(
                                path, text.getBytes(StandardCharsets.UTF_8)));
                        return UnsetValue.unset();
                    });
                });

        // tty?: "Returns TRUE if standard input is connected to a terminal."
        //
        // Asked of the console port rather than of the JVM. Whether the input
        // is a terminal is the host's to know, and `System.console()` answers
        // a `java.io.Console` -- which the dependency rule keeps out of the
        // domain, and rightly: a domain that can see a console can be written
        // to depend on there being one.
        define("tty?", List.of(),
                (arguments, evaluator, context) ->
                        LogicValue.of(evaluator.console().isATerminal()));

        // wait: value [number! time! port! block! none!] /all /only
        //
        // A number is seconds, a decimal is fractional seconds, a time is
        // itself, and a negative one is clamped to zero rather than refused:
        //     case REB_INTEGER: timeout = 1000 * Int32(val); goto chk_neg;
        //     chk_neg: if (timeout < 0) timeout = 0; //Trap_Range(val);
        //
        // Waiting on a port needs event polling, which JEBOL has not got. The
        // C's own answer for a port with nothing pending is none --
        // `if (!Pending_Port(val)) return R_NONE;` -- and nothing is pending
        // here, so none is the truthful answer rather than a stub.
        define("wait", List.of(Parameter.required("value",
                        waitableDatatypes())),
                Set.of("all", "only"),
                (arguments, evaluator, context, refinements) -> {
                    Value asked = arguments.getFirst();
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

        // read-key: "Reads a single keypress from the console without echoing it."
        //
        // A char, or none when the host cannot read one. The C also updates
        // three state flags from the key's modifiers:
        //     SET_LOGIC(...STATE_CONTROLQ, GET_FLAG(key.flags, EVF_CONTROL));
        //     SET_LOGIC(...STATE_SHIFTQ,   GET_FLAG(key.flags, EVF_SHIFT));
        //     SET_LOGIC(...STATE_ALTQ,     GET_FLAG(key.flags, EVF_ALT));
        //
        // Nothing reachable from the JDK reports a modifier separately from the
        // character it produced, so all three are set false rather than left
        // holding whatever the last call put there. False is a claim, and the
        // honest one: this host did not see a modifier.
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

        // halt: takes nothing, answers nothing.
        //
        // Not QUIT. Quitting ends the host's run; halting ends the script and
        // leaves the host running, so a console that halts is a console
        // afterwards. Both are signals rather than answers, which is the only
        // thing they share.
        define("halt", List.of(),
                (arguments, evaluator, context) -> {
                    throw new HaltRequested();
                });

        // === Colour: as-color and the functions of n-image.c ===
        //
        // A colour is a tuple and an image is a series of them, so each of these
        // has two arms over one formula. Every one MODIFIES its target and
        // answers it -- `return R_ARG1` -- which is why each argument's own doc
        // string says "(modified)".
        //
        // as-color: r g b [integer! decimal! percent!]
        //
        // Runs a codec. The handle has to be a codec's and nothing else --
        // `if (VAL_HANDLE_TYPE(hnd) != SYM_CODEC) Trap0(RE_INVALID_HANDLE);` -- and
        // what comes back depends on which of three things the codec was asked and
        // what it answered.
        //
        // The error code and the answer are read together. A non-zero error is
        // `bad-media`, except after an IDENTIFY, where "error code is inverted
        // result": no error means yes.
        define("do-codec", List.of(
                        Parameter.required("handle", Set.of(Datatype.HANDLE)),
                        Parameter.required("action", Set.of(Datatype.WORD)),
                        Parameter.required("data", Set.of(Datatype.BINARY,
                                Datatype.IMAGE, Datatype.STRING))),
                (arguments, evaluator, context) -> ranCodec(
                        (HandleValue) arguments.get(0),
                        (WordValue) arguments.get(1),
                        arguments.get(2)));

        // Gives up a handle's resources, and answers whether there were any. Two
        // lines of C and the whole of the behaviour: `if (IS_CONTEXT_HANDLE(val)) {
        // Free_Hob(...); return R_TRUE; } return R_FALSE;`.
        //
        // So it answers false for a function handle, which is every handle this
        // build makes: a codec wraps a dispatcher rather than owning anything, and
        // there is nothing to free. The true branch waits on a kind of handle that
        // owns a resource -- a cipher's key schedule is the C's own example -- and
        // that waits on a cipher.
        define("release", List.of(Parameter.required("handle", Set.of(Datatype.HANDLE))),
                (arguments, evaluator, context) ->
                        LogicValue.of(((HandleValue) arguments.get(0)).isContext()));

        // MAP-GOB-OFFSET with the gob and the point taken out of an event, and the
        // result written back into it. The same `Map_Gob_Inner` walk, one caller
        // along: a window system hands the language a click on a window and asks
        // which of the window's children was actually clicked.
        //
        // The event comes back changed and the caller's own word does not, because
        // an event is inline in a value cell rather than a series: `return R_ARG1`
        // hands back the copy on the data stack. `e: map-event e` is how a script
        // keeps the result.
        define("map-event", List.of(Parameter.required("event", Set.of(Datatype.EVENT))),
                (arguments, evaluator, context) -> mappedEvent(
                        (EventValue) arguments.get(0)));

        // Asks a port to deal with an event, and answers whether it is finished
        // waiting. Two steps with a condition on each, and the second is the one
        // that matters: a port with no AWAKE function is woken, and the function
        // exists to say no.
        define("wake-up", List.of(
                        Parameter.required("port", Set.of(Datatype.PORT)),
                        Parameter.required("event", Set.of(Datatype.EVENT))),
                (arguments, evaluator, context) -> wokenPort(
                        (PortValue) arguments.get(0), arguments.get(1), evaluator));

        // What a window system asks when a click arrives: given a point in the
        // outermost gob, which gob was actually clicked and where in that gob.
        //
        // So it walks down the tree by default and /REVERSE walks back up, and
        // the two are one piece of arithmetic with the sign flipped: going down,
        // each gob entered has its offset taken off the point; going up, each gob
        // left has its offset added.
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

        // Three bytes into a three-part tuple, and each part is read differently:
        // an integer as itself, a decimal ROUNDED -- `(REBI64)(VAL_DECIMAL(val) +
        // 0.5)`, the opposite of every other decimal conversion here -- and a
        // percent as a fraction of 255. Then clamped: `MAX(0, MIN(255, num))`.
        define("as-color", List.of(
                        Parameter.required("r", Typeset.NUMBER.members()),
                        Parameter.required("g", Typeset.NUMBER.members()),
                        Parameter.required("b", Typeset.NUMBER.members())),
                (arguments, evaluator, context) -> TupleValue.of(
                        colourByteOf(arguments.get(0)),
                        colourByteOf(arguments.get(1)),
                        colourByteOf(arguments.get(2))));

        // grayscale: target [tuple! image!] -- the average of the three parts.
        define("grayscale", List.of(Parameter.required("target",
                        Set.of(Datatype.TUPLE, Datatype.IMAGE))),
                (arguments, evaluator, context) -> overEveryColour(arguments.getFirst(),
                        parts -> IntegerValue.of(
                                Colours.grey(parts[0], parts[1], parts[2])),
                        parts -> {
                            int grey = Colours.grey(parts[0], parts[1], parts[2]);
                            return new int[] {grey, grey, grey};
                        }));

        // luminosity: target [tuple! image!] /luma -- BT.709, or BT.601 with
        // /LUMA. A tuple answers the one number; an image is turned grey.
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

        // hsv-to-rgb and rgb-to-hsv: hsv [tuple!] -- the same three bytes read
        // the other way about, and both change the tuple they were given.
        define("hsv-to-rgb", List.of(Parameter.required("hsv", Set.of(Datatype.TUPLE))),
                (arguments, evaluator, context) -> recolouredTuple(
                        (TupleValue) arguments.getFirst(),
                        parts -> Colours.hsvToRgb(parts[0], parts[1], parts[2])));
        define("rgb-to-hsv", List.of(Parameter.required("rgb", Set.of(Datatype.TUPLE))),
                (arguments, evaluator, context) -> recolouredTuple(
                        (TupleValue) arguments.getFirst(),
                        parts -> Colours.rgbToHsv(parts[0], parts[1], parts[2])));

        // color-distance: a b [tuple!] -- weighted, so green counts most.
        define("color-distance", List.of(
                        Parameter.required("a", Set.of(Datatype.TUPLE)),
                        Parameter.required("b", Set.of(Datatype.TUPLE))),
                (arguments, evaluator, context) -> DecimalValue.of(Colours.distance(
                        threeParts((TupleValue) arguments.get(0)),
                        threeParts((TupleValue) arguments.get(1)))));

        // tint: target [tuple! image!] rgb [tuple!] amount [number!]
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

        // limit-usage: field [word!] "eval (count) or memory (bytes)"
        //              limit [number!]
        //
        // Records a number and answers unset. Set-once, because the C tests for
        // zero before it writes: `if (Eval_Limit == 0) Eval_Limit = Int64(...)`.
        // A field that is neither of the two falls out of the bottom untouched --
        // an if, an else-if and no else -- and SECURE is the only caller Rebol
        // has, which passes one of the two.
        //
        // Nothing enforces it, here or there. The limit is read in one place,
        // inside Do_Signals, and handed to `Check_Security(SYM_EVAL, POL_EXEC,
        // 0)`; every policy in boot/sysobj.reb defaults to `0.0.0`, which is
        // ALLOW, and an allowed policy does nothing. So a stock Rebol records the
        // limit and runs past it, and `secure [eval throw]` is what changes that.
        // JEBOL has no SECURE, so enforcing this would stop a script Rebol would
        // let run.
        //
        // `number!` rather than `integer!` because `Int64` takes a decimal and
        // cuts the fraction off.
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

        // ds: "Temporary stack debug" -- `Dump_Stack(0, 0); return R_UNSET;`
        //
        // What it prints is the frame stack, not a C memory structure, and it is
        // compiled into every build: one line per open frame giving the word the
        // call was made through, how many slots it has and the function's
        // datatype, then one line per slot with its value. So this is what STACK
        // answers, printed instead of returned.
        //
        // The formats are Rebol's own, from the `stack` block of
        // boot/strings.reb: `^/STACK[%d] %s[%d] %s` and `\t%s: %72r`.
        define("ds", List.of(),
                (arguments, evaluator, context) -> {
                    printTheFrameStack(evaluator);
                    return UnsetValue.unset();
                });

        // dump: v /fmt "only series format"
        //
        // The whole body is inside `#ifdef DEBUG`, so a released 3.22.1 reaches
        // `return R_ARG1;` and nothing else: `dump [1 2]` prints nothing and
        // answers `[1 2]`. That is the behaviour to port. The debug build's
        // series walk is not something any shipped Rebol does, and writing it
        // would mean reporting on a REBSER that is not there.
        define("dump", List.of(Parameter.required("value")),
                Set.of("fmt"),
                (arguments, evaluator, context, refinements) -> arguments.getFirst());

        // check: val [series!] -- "Temporary series debug check"
        //
        // Not debug-only. It walks the series looking for a terminator in the
        // wrong place and raises `bad-series` when it finds one:
        //
        //     for (n = 0; n < SERIES_TAIL(ser); n++)
        //         if (IS_END(BLK_SKIP(ser, n))) goto err;
        //     if (!IS_END(BLK_SKIP(ser, n))) goto err;
        //
        // That is the C's own invariant -- every REBSER holds a terminator past
        // its tail and none before it -- and a JEBOL series has no terminator to
        // be in the wrong place. So the check holds for every series, always,
        // and the answer is `*D_RET = *val`: the value, at the position it came
        // in at.
        define("check", List.of(Parameter.required("series", Typeset.SERIES.members())),
                (arguments, evaluator, context) -> arguments.getFirst());

        // evoke: chant [word! block! integer!] -- "Special guru meditations."
        define("evoke", List.of(Parameter.required("chant",
                        Set.of(Datatype.WORD, Datatype.BLOCK, Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    List<Value> chants = arguments.getFirst() instanceof BlockValue several
                            ? several.remaining()
                            : List.of(arguments.getFirst());
                    // Walked by position rather than by item, because one chant
                    // takes an argument: `case SYM_STACK_SIZE: arg++;` reads the
                    // next value as the size and steps over it, so the number in
                    // `evoke [stack-size 100]` is never a chant of its own.
                    for (int at = 0; at < chants.size(); at++) {
                        at += obey(chants.get(at), evaluator);
                    }
                    return UnsetValue.unset();
                });

        // stack: offset [integer!] /block /word /func /args /size /depth /limit
        define("stack", List.of(Parameter.required("offset", Set.of(Datatype.INTEGER))),
                Set.of("block", "word", "func", "args", "size", "depth", "limit"),
                (arguments, evaluator, context, refinements) -> {
                    int offset = (int) ((IntegerValue) arguments.getFirst()).magnitude();
                    // Offset zero is the STACK call itself. The C counts
                    // every call including this native's own frame; JEBOL
                    // opens no frame for a native, so the innermost entry
                    // is put back here and everything outward shifts one.
                    //
                    // An offset naming no frame answers none, whatever was
                    // asked for. The C tests it before it reads a single
                    // refinement:
                    //     sp = Stack_Frame(index);
                    //     if (!sp) return R_NONE;
                    // So a caller walking outwards can tell where the stack
                    // ends; answering a number regardless would make the walk
                    // run for ever.
                    if (offset < 0 || offset > evaluator.framesOpen()) {
                        return NoneValue.none();
                    }
                    if (refinements.contains("word")) {
                        if (offset == 0) {
                            return WordValue.of("stack");
                        }
                        // A call made on a value rather than through a word
                        // carries no name, and answers none rather than an
                        // empty word -- there is no such thing.
                        return evaluator.functionBeingRun(offset - 1)
                                .filter(name -> !name.isEmpty())
                                .<Value>map(WordValue::of)
                                .orElseGet(NoneValue::none);
                    }
                    if (refinements.contains("depth")) {
                        return IntegerValue.of(evaluator.framesOpen());
                    }
                    if (refinements.contains("limit")) {
                        return IntegerValue.of(Evaluator.DEFAULT_MAXIMUM_DEPTH);
                    }
                    // /SIZE is in value units rather than frames, and a frame
                    // is more than one value, so the two answers differ on
                    // purpose.
                    if (refinements.contains("size")) {
                        return IntegerValue.of(evaluator.framesOpen() * FRAME_VALUE_UNITS);
                    }
                    // No refinement asks for the backtrace: a block of frame
                    // words from the offset inward-to-outward --
                    // `Set_Block(D_RET, Make_Backtrace(index));`.
                    List<Value> backtrace = new ArrayList<>();
                    if (offset == 0) {
                        backtrace.add(WordValue.of("stack"));
                    }
                    for (int at = Math.max(0, offset - 1);
                            at < evaluator.framesOpen(); at++) {
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
     * <p>A line is printed even with nothing open, because the C prints before
     * it tests {@code if (dsf > 0)}. Printing nothing there would read as a DS
     * that did not run.
     */
    private void printTheFrameStack(Evaluator evaluator) {
        List<Evaluator.OpenCall> open = evaluator.callsInProgress();
        if (open.isEmpty()) {
            evaluator.output().writeLine(String.format(
                    FRAME_LINE, 0, NO_NAME, 0, Datatype.FUNCTION.literalSpelling()));
            return;
        }
        int slotsInUse = open.size() * FRAME_VALUE_UNITS;
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
        // The C tests IS_WORD and IS_INTEGER separately on the same item, so a
        // block carries both forms. 0 runs both memory checks, 1 the pools and
        // 2 the bind table; anything else prints the list.
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
                // The host asked, through the thread rather than through the
                // interruption hook. Both mean stop, so honour it and restore
                // the flag for whatever is above.
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

    /** JEBOL's version, as text and as the tuple /DATA answers. */
    private static final String VERSION_TEXT = "0.1.0";
    private static final int[] VERSION_PARTS = {0, 1, 0};

    /** What POKE and POKEZ will write into. */
    private static Set<Datatype> pokeableDatatypes() {
        Set<Datatype> accepted = EnumSet.copyOf(Typeset.SERIES.members());
        accepted.add(Datatype.BITSET);
        accepted.add(Datatype.TUPLE);
        return Set.copyOf(accepted);
    }

    private void defineStrings() {
        // These change the string they were given and answer it, rather
        // than building a new one. A caller holding the string sees the
        // change, which is what makes them useful on a series someone
        // else is also looking at -- and what makes them refuse when it
        // is protected, without needing a check of their own.
        // /PART changes only the first few characters from where the
        // series is, counting from the position rather than from the
        // head, so `uppercase/part next "abcd" 2` is "BCd".
        // Lines, however they were ended. A carriage return before the
        // newline goes with it rather than staying on the end of the line
        // before, which is the whole reason this is not a SPLIT on "^/".
        // Nothing at all gives no lines, not one empty one.
        // Where a script's header starts inside a binary, or none. The
        // header has to begin a line: only spaces may come before it, and
        // a byte order mark counts as one of them. `xx rebol []` has a
        // header nowhere.
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
        // A name with a wildcard in it is a pattern rather than a file,
        // and the caller has to know which it is holding before it reads
        // anything.
        define("wildcard?", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> LogicValue.of(
                        ((StringValue) arguments.getFirst()).text().chars()
                                .anyMatch(letter -> letter == '*' || letter == '?')));
        defineCaseChange("uppercase", text -> text.toUpperCase(Locale.ROOT));
        defineCaseChange("lowercase", text -> text.toLowerCase(Locale.ROOT));
        // /head and /tail each trim one end. Asking for neither trims
        // both, which is the same answer as asking for both.
        // Five ways to trim, and they are not variations on each other.
        // /ALL takes every space out; /LINES folds the whole thing onto
        // one line; /AUTO removes the indentation the first line has from
        // all of them, keeping any deeper indentation; /WITH takes out the
        // characters it is given. /HEAD and /TAIL bound the plain form.
        // A binary drops zero bytes and a block drops nones, which is the
        // same idea as a string dropping spaces.
        // `series<series! object! error! module!>` is what TRIM declares, and
        // series! is the whole family: the any-path datatypes and a hash trim
        // as a block does, because they are blocks. An object, an error and a
        // module trim differently -- see trimmedObject.
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
                    // An object takes no refinements at all:
                    // `if (Find_Refines(ds, ALL_TRIM_REFS)) Trap0(RE_BAD_REFINES);`
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
                        // /AUTO takes the shared indent off and then the
                        // ends are still trimmed, so `trim/auto/tail` does
                        // both. Returning here left the trailing spaces on.
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
        // JOIN keeps the datatype of what it joins onto, so a file joined
        // with a string is a file. Joining onto something that is not a
        // series has no datatype to keep, so that falls back to a string.
    }

    // ---- conversion ------------------------------------------------------

    private void defineConversion() {
        // TO is the general conversion; the to-x spellings are the common
        // ones given names. A script that computes which type it wants can
        // only use this one.
        define("to", takesAnything("type", "value"),
                (arguments, evaluator, context) -> {
                    // `if (action == A_MAKE || action == A_TO)` -- an event's one
                    // arm serves both, so TO does exactly what MAKE does here.
                    // Which is unusual: for most datatypes the two part company on
                    // whether a number is a capacity or a value.
                    if (arguments.get(0) instanceof DatatypeValue wanted
                            && wanted.represents() == Datatype.EVENT) {
                        return EventPath.made(wanted, arguments.get(1),
                                value -> simpleValueOf(value, evaluator, context));
                    }
                    return convertedTo(arguments.get(0), arguments.get(1));
                });

        define("as-pair", takesOnlyNumbers("x", "y"),
                (arguments, evaluator, context) -> PairValue.of(
                        Comparison.asDouble(arguments.get(0)), Comparison.asDouble(arguments.get(1))));

        // The answer is an issue rather than a string, padded to the
        // full width of a whole number unless /size narrows it.
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

        // Four spaces to a tab by default, which is not the eight a
        // terminal usually means.
        defineTabbing("entab", true);
        defineTabbing("detab", false);

        // DELINE turns the two-character line ending into the one, which
        // is what reading a file written elsewhere needs.
        // Both change the string they were given rather than building a
        // new one, so a caller holding it sees the change and a protected
        // string refuses. /LINES answers the lines as a block instead of
        // rewriting anything.
        // `string [any-string!]` -- a file or a url holds line breaks too.
        define("deline", List.of(Parameter.required("text", Typeset.ANY_STRING.members())),
                Set.of("lines"),
                (arguments, evaluator, context, refinements) -> {
                    StringValue text = (StringValue) arguments.getFirst();
                    if (refinements.contains("lines")) {
                        return BlockValue.block(
                                java.util.Arrays.stream(
                                                text.text().replace("\r\n", "\n").split("\n", -1))
                                        .<Value>map(StringValue::of)
                                        .toList());
                    }
                    return rewritten(text, whole -> whole.replace("\r\n", "\n"));
                });
        // ENLINE is DELINE's counterpart and answers the same text on a
        // platform whose line ending is a bare newline, which is what a
        // JVM gives. It still goes through the storage, so a protected
        // string refuses rather than being quietly accepted.
        define("enline", List.of(Parameter.required("text", Set.of(Datatype.STRING))),
                (arguments, evaluator, context) -> rewritten(
                        (StringValue) arguments.getFirst(),
                        whole -> whole.replace("\r\n", "\n")));

        // AS reads a value as a sibling type without copying it. Two
        // datatypes that hold different things cannot be read as each
        // other, and that is where AS stops and TO starts.
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
        // Deriving from a native replaces only what it says about itself,
        // because its behaviour is C and there is no REBOL body to give. A
        // second block is a body, and `make :read [[][]]` is refused for
        // exactly that -- issue-1052. Rebol's own `empty?: make :tail? [[...]]`
        // gives the one spec block and no more.
        //
        // cannot-use rather than bad-make-arg: the prototype form reaches
        // Copy_Function, which fails and reports through Trap_Reflect. The
        // datatype forms `make native! [...]` are the bad-make-arg door,
        // refused earlier in Make_Function.
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
                // `VAL_INDEX(val) = (Int32s(block, 1) - 1)` -- one-based, and
                // Int32s refuses anything below one.
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
        // `if (!IS_END(block)) return 0;` and the caller traps: anything left
        // over means the block was not one of the four shapes.
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
            // `else if (IS_LOGIC(val)) n = (VAL_LOGIC(val) ? 1 : 2);` in
            // Get_Num_Arg -- false counts two, however odd that reads.
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

    private static Value makeOfDatatype(
            DatatypeValue wanted, Value from, Evaluator evaluator, Context context) {
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
        if (from instanceof IntegerValue && wanted.represents().isSeries()) {
            return switch (wanted.represents()) {
                case BLOCK, PAREN, PATH -> BlockValue.block(List.of()).as(wanted.represents());
                case BINARY -> BinaryValue.of();
                default -> StringValue.of("", wanted.represents());
            };
        }
        // MAKE BLOCK! of text tokenizes it as source -- "make from string!
        // or binary! with tokenization" routes through Scan_Source -- where
        // TO BLOCK! wraps the value as one item. sys-load builds a module's
        // body exactly this way.
        if ((wanted.represents() == Datatype.BLOCK
                || wanted.represents() == Datatype.PAREN)
                && (from instanceof StringValue || from instanceof BinaryValue)) {
            TranscodeResult read = Transcoder.transcode(textOfSource(from));
            if (!read.succeeded()) {
                throw new Raised(read.error().orElseThrow());
            }
            return read.values().orElseThrow().as(wanted.represents());
        }
        return convertedTo(wanted, from);
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

    /** A value converted to whatever datatype was named. */
    private static Value convertedTo(Value type, Value value) {
        if (!(type instanceof DatatypeValue wanted)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "to needs a datatype, not " + type.datatype().literalSpelling());
        }
        return switch (wanted.represents()) {
            // The same conversions the to-x spellings do, since TO is the
            // general form of them rather than a second implementation.
            case INTEGER -> wholeNumberFrom(value);
            // A binary is the bits of the double itself, big-endian, not
            // text and not a number to be scaled. That is how a test suite
            // pins floating point exactly, without writing decimals whose
            // text form has already rounded.
            case DECIMAL -> value instanceof BinaryValue bits
                    ? DecimalValue.of(Double.longBitsToDouble(bitsOf(bits)))
                    : DecimalValue.of(Comparison.asDouble(value));
            // A percent shares the decimal's representation and stores the
            // fraction rather than the printed number, so `make percent! $100`
            // is 10000% and `make money! 100%` is $1. The two conversions are
            // inverses and neither multiplies by a hundred.
            case PERCENT -> DecimalValue.percent(Comparison.asDouble(value));
            // A binary is DECODED as UTF-8, never printed as hexadecimal.
            // `make_string` in t-string.c reads it with `Decode_UTF_String`
            // and raises invalid-utf if the bytes are not valid.
            //
            // Printing the hex instead is what stopped DECODE-URL: Rebol's
            // url-parser works on a binary throughout and ends every rule
            // with `to string! dehex value`, so a host came back as
            // "6578616D706C65".
            case STRING -> value instanceof BinaryValue octets
                    ? StringValue.of(textDecodedFrom(octets))
                    : StringValue.of(runTogether(value));
            // The rest of the string family takes the text of the value
            // the same way, so `to file! [a b]` is %ab: nothing inserts
            // separators between the parts.
            case FILE, URL, EMAIL, TAG, REF -> value instanceof BinaryValue octets
                    ? StringValue.of(textDecodedFrom(octets), wanted.represents())
                    : StringValue.of(runTogether(value), wanted.represents());
            // TO BINARY! reads bytes rather than text. A string gives its
            // UTF-8 and a block gives one byte per number, but an integer
            // gives its whole machine width -- 65 is eight bytes, not one.
            case BINARY -> switch (value) {
                case BinaryValue already -> already;
                case StringValue text -> binaryOfBytes(
                        text.text().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                case IntegerValue whole -> binaryOfBytes(
                        java.nio.ByteBuffer.allocate(Long.BYTES)
                                .putLong(whole.magnitude()).array());
                // The bits of the double, so the trip back through
                // TO DECIMAL! gives exactly what went in, sign of a zero
                // and all.
                case DecimalValue fractional -> binaryOfBytes(
                        java.nio.ByteBuffer.allocate(Long.BYTES)
                                .putLong(Double.doubleToRawLongBits(
                                        fractional.quantity())).array());
                // Twelve bytes, which is the whole of the deci form: one
                // sign bit, an eight bit power of ten and eighty-seven bits
                // of significand. Nothing normalises on the way, so a money
                // made from twelve bytes converts back to the same twelve.
                case MoneyValue amount -> binaryOfBytes(amount.toBytes());
                case BlockValue block -> bytesOfEach(block);
                default -> raiseCannotUse(value, "to binary!");
            };
            // The word family converts within itself, which is how code
            // builds an assignment it did not spell out. Forming first
            // would lose the sigil and give a word called "c:".
            case WORD, SET_WORD, GET_WORD, LIT_WORD, REFINEMENT, ISSUE ->
                    wordFrom(value, wanted.represents());
            // A paren and a hash hold what a block holds and differ only
            // in how they are written and run, so the same conversion
            // serves all three and keeps the one that was asked for.
            // The whole any-block family, not just the three that hold data.
            // A path is a block with a different spelling, and `to path!` of a
            // block is how code builds one it could not write literally:
            // Rebol's own ANY-OF and ALL-OF do `to path! reduce [...]`.
            case BLOCK, PAREN, HASH, PATH, SET_PATH, GET_PATH, LIT_PATH ->
                    // A typeset becomes the datatypes it holds, not a block
                    // holding the typeset. That is what makes
                    // `exclude reduce [integer! decimal!] to-block scalar!`
                    // the ordinary way to ask which datatypes a family covers.
                    // In the boot table's order rather than alphabetical,
                    // because the C keeps every datatype list in types.reb
                    // order and the suite compares the tails of two of them.
                    (value instanceof TypesetValue kinds
                            ? BlockValue.block(kinds.members().stream()
                                    .sorted()
                                    .<Value>map(DatatypeValue::of).toList())
                            // A map becomes its pairs, which is the same
                            // question BODY-OF asks: `Set_Block(arg,
                            // Map_To_Block(mapser, 0))`. A block holding the
                            // map would be the answer to no question anyone
                            // asks, and is what a conversion that did not know
                            // about maps produces.
                            : value instanceof MapValue map
                                    ? BlockValue.block(map.flattened())
                            : value instanceof BlockValue block
                                    ? block
                                    : BlockValue.block(value)).as(wanted.represents());
            // TO MAP! is MAKE MAP! without the room-count reading. The C parts
            // them on one line -- `if (action == A_TO) Trap_Arg(arg);` inside
            // the number arm -- because a conversion answers something made of
            // what it was given, and there are no pairs in a number.
            case MAP -> {
                if (value instanceof IntegerValue || value instanceof DecimalValue) {
                    throw Raised.of(EvaluationFailure.INVALID_ARG,
                            "to map! wants pairs, and a number is room for pairs "
                                    + "rather than any: make map! reads it that way");
                }
                yield mapMadeFrom(value);
            }
            case CHAR -> asCharacter(value);
            case PAIR -> asPair(value);
            case MONEY -> asMoney(value);
            // MAKE-PORT* in sys-ports.reb ends with `port: to port! port`,
            // having built an ordinary object from system/standard/port. So
            // this conversion is the last step of building every port, and a
            // port is an object whose datatype sends an action to its actor.
            case PORT -> value instanceof ObjectValue built
                    ? new PortValue(built.context())
                    : raiseBadMakeArg(value, "port!");
            // And MAKE-MODULE* in sys-base.reb ends with
            // `to module! reduce [spec context]`, so this is the last step of
            // building every module. The block holds the header first and the
            // words second, and t-object.c refuses anything else:
            //     if (!IS_BLOCK(arg) || IS_EMPTY(arg)) Trap_Make(REB_MODULE, arg);
            //     val = VAL_BLK_DATA(arg);
            //     if (!IS_OBJECT(val)) Trap_Arg(val);
            //     obj = VAL_OBJ_FRAME(val);
            //     val++;
            //     if (!IS_OBJECT(val)) Trap_Arg(val);
            case MODULE -> moduleFromHeaderAndWords(value);
            case BITSET -> bitsetOf(value);
            // A typeset need not be one of the named families: code can
            // build its own from whichever datatypes it cares about, and
            // Rebol's base-defs.reb builds one per generated function.
            case TYPESET -> value instanceof BlockValue named
                    ? TypesetValue.of(datatypesNamedIn(named))
                    : raiseBadMakeArg(value, "typeset!");
            case TIME -> TimeValue.ofNanoseconds(
                    (long) (Comparison.asDouble(value) * NANOSECONDS_A_SECOND));
            case TUPLE -> tupleFrom(value);
            // Truthiness, not zero-ness. Only none and false are false, so
            // `to logic! 0` and `to logic! ""` are both true -- the case a
            // reader arriving from another language expects to go the
            // other way.
            case LOGIC -> LogicValue.of(value.isTruthy());
            // A datatype is named by a word, and only by a word. The
            // string "integer!" is refused rather than parsed, so nothing
            // can reach a datatype by spelling one at run time.
            case DATATYPE -> value instanceof WordValue named
                    ? datatypeNamed(named, value)
                    : raiseBadMakeArg(value, "datatype!");
            default -> raiseCannotUse(value, "to " + wanted.represents().literalSpelling());
        };
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
     */
    private static Value wholeNumberFrom(Value value) {
        return switch (value) {
            case IntegerValue whole -> whole;
            case StringValue text -> parseInteger(text.text());
            case CharacterValue character -> IntegerValue.of(character.codepoint());
            case BinaryValue bytes -> IntegerValue.of(bitsOf(bytes));
            case DateValue moment -> IntegerValue.of(instantOf(moment));
            case DecimalValue number -> IntegerValue.of((long) number.quantity());
            case MoneyValue amount -> IntegerValue.of(amount.amount().longValue());
            case TimeValue clock -> IntegerValue.of(clock.nanoseconds() / NANOSECONDS_A_SECOND);
            default -> raiseBadMakeArg(value, "integer!");
        };
    }

    /**
     * A date counted in seconds from the start of 1970.
     *
     * <p>A date without a time of day counts as its midnight, which is
     * what makes {@code to integer! 1-Jan-2000} a round number of days.
     */
    private static long instantOf(DateValue moment) {
        long midnight = dayNumberOf(moment) * (NANOSECONDS_A_DAY / NANOSECONDS_A_SECOND);
        return midnight + moment.timeOfDay()
                .map(clock -> clock.nanoseconds() / NANOSECONDS_A_SECOND)
                .orElse(0L);
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
        // The text a string holds, not the way it is written down. A tag
        // holds "a" and is written <a>, and it is the former that has to
        // read as a word.
        String spelling = switch (value) {
            case CharacterValue letter -> Character.toString(letter.codepoint());
            case StringValue text -> text.text();
            // A datatype answers the word that names it, exclamation mark and
            // all: `to word! logic!` is `logic!`. Which is what makes a
            // datatype comparable against a name a script wrote down, and it
            // is the pair of `to datatype! 'logic!` going the other way.
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
        // Only spaces and tabs at the end are dropped. The scanner takes
        // the word and stops, and what is left over is whitespace; a
        // space at the front is a different matter and belongs to
        // whatever came before it.
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\t')) {
            end--;
        }
        String trimmed = text.substring(0, end);
        if (trimmed.isEmpty()) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS, text);
        }
        // A control character is not a word character. The lexical map
        // in l-scan.c files every byte below a space, and the delete
        // character, as neither word nor number nor anything else a
        // word may be made of, so the scanner stops at one.
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
        // The reader has to have taken the whole of it. A comma or a
        // semicolon after the letters reads as the word alone, dropping
        // what followed, and a word built from that would be a different
        // word from the one that was asked for.
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
            // A trailing dot leaves an empty last part, which the C's
            // scan simply stops before rather than refusing.
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
        if (value instanceof StringValue text) {
            if (text.text().isEmpty()) {
                return raiseBadMakeArg(value, "char!");
            }
            return CharacterValue.of(text.text().codePointAt(0));
        }
        if (!(value instanceof IntegerValue || value instanceof DecimalValue)) {
            return raiseBadMakeArg(value, "char!");
        }
        double codepoint = Comparison.asDouble(value);
        if (codepoint < 0 || codepoint > Character.MAX_CODE_POINT) {
            throw Raised.of(EvaluationFailure.INVALID_CHAR,
                    ((long) codepoint) + " is not a code point");
        }
        return CharacterValue.of((int) codepoint);
    }

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
            switch (item) {
                case DatatypeValue datatype -> found.add(datatype.represents());
                case TypesetValue typeset -> found.addAll(typeset.members());
                // A word may name a typeset as readily as a datatype:
                // `[any-function! any-object!]` is a perfectly good
                // typeset block. Resolving only datatypes dropped every
                // family name and built a set holding nothing.
                case WordValue word -> {
                    Datatype.named(word.spelling()).ifPresent(found::add);
                    // The existing lookup wants the bare name, so the
                    // exclamation mark comes off first.
                    Typeset.named(word.spelling().endsWith("!")
                                    ? word.spelling().substring(
                                            0, word.spelling().length() - 1)
                                    : word.spelling())
                            .ifPresent(family -> found.addAll(family.members()));
                }
                default -> { }
            }
        }
        return found;
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
     */
    private static Value asMoney(Value value) {
        return withinTheDeciRange(switch (value) {
            case MoneyValue already -> already;
            case IntegerValue whole -> MoneyValue.of(BigDecimal.valueOf(whole.magnitude()));
            case DecimalValue quantity ->
                    MoneyValue.of(BigDecimal.valueOf(quantity.quantity()));
            case StringValue text -> readMoney(text.text());
            case BinaryValue bytes -> MoneyValue.fromBytes(bytesFromHere(bytes));
            case LogicValue truth ->
                    MoneyValue.of(truth.truth() ? BigDecimal.ONE : BigDecimal.ZERO);
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

    private static MoneyValue readMoney(String text) {
        List<Value> read = Transcoder.transcode("$" + text.strip()).values()
                .map(BlockValue::remaining)
                .orElseGet(List::of);
        if (read.size() != 1 || !(read.getFirst() instanceof MoneyValue amount)) {
            return (MoneyValue) raiseBadMakeArg(StringValue.of(text), "money!");
        }
        return amount;
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
        // `if (!D_REF(9)) dat.nano = 0; // Not /precise` -- dropped rather than
        // rounded, so a caller timing something short and not asking for
        // precision measures nothing at all.
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
        // Monday is day one, which the C says in as many words and which is not
        // what every calendar does. A JVM agrees, so this is a coincidence
        // worth naming rather than relying on quietly.
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
            // `Int32s(arg, 0)` is the signed read with a floor of zero, and it
            // raises rather than clamping: there is no negative amount of room.
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
        // An odd number of items is a key with no value, which is a typo
        // rather than a map: `if (n & 1) return FALSE;` and the caller
        // raises on it.
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
        // A negative is refused as a wrong argument and a large one as out of
        // range, which is the distinction a real R3 makes: only the second ever
        // reaches `if (c > 0xff) Trap_Range(val);`.
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
     * Reading an integer out of text, failing in the three ways R3 does.
     *
     * <p>Which failure you get depends on the text rather than on the call,
     * and the order below is the order R3 decides in. Whitespace around the
     * number is trimmed first and is never a problem; whitespace left inside
     * what remains always is. A quote is a digit separator, so
     * {@code "1'000"} is 1000, and a decimal point truncates toward zero
     * rather than being refused.
     */
    private static Value parseInteger(String text) {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT,
                    "there is nothing in \"" + text + "\" to read as an integer");
        }
        if (containsWhitespace(trimmed)) {
            throw Raised.of(EvaluationFailure.INVALID_CHARS,
                    "\"" + text + "\" has whitespace inside the number");
        }
        String withoutSeparators = trimmed.replace("'", "");
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
        // Three datatypes have a parity of their own, each in its own
        // dispatcher, and each counts something different.
        //
        // A character counts its codepoint: `case A_EVENQ: chr = ~chr; case
        // A_ODDQ: DECIDE(chr & 1);`.
        //
        // A time counts its whole seconds: `DECIDE((SECS_IN(secs) & 1) != 0)`.
        //
        // A date counts the day of the month, and the C gets there by two
        // inversions that cancel. It counts from zero -- `day = VAL_DAY(val) -
        // 1` at the top of the dispatcher -- and then asks the opposite
        // question: `case A_ODDQ: DECIDE((day & 1) == 0);`. So ODD? is true when
        // the zero-based day is even, which is when the calendar day is odd.
        // The first of the month is odd, as a person would say it is.
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
            // Past the point where a double can tell neighbouring whole
            // numbers apart, every value it can hold is an even one, so
            // the answer is even without going through a long that would
            // saturate at an odd Long.MAX_VALUE.
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
        // Found by which refinements were asked for rather than by a
        // fixed position: adding /DUP after /PART moved everything along,
        // and a fixed index then read one for the other.
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
        // `if (!IS_BLOCK(arg)) Trap_Arg(val);` -- a block, and not any block:
        // a paren of the same words is refused, and so is another map. One
        // value is not half a pair, so there is nothing sensible to do with
        // anything else.
        if (!(arguments.get(1) instanceof BlockValue pairs)
                || pairs.datatype() != Datatype.BLOCK) {
            throw Raised.of(EvaluationFailure.INVALID_ARG,
                    nativeName + " puts pairs into a map, and needs a block of them, "
                            + "not a " + arguments.get(1).datatype().literalSpelling());
        }
        // `if (DS_REF(AN_DUP)) Trap0(RE_BAD_REFINES);` -- adding the same key
        // twice would set it once and answer as though it had done the work
        // twice, so the C refuses rather than picking a meaning.
        if (refinements.contains("dup")) {
            throw Raised.of(EvaluationFailure.BAD_REFINES,
                    nativeName + "/dup means nothing for a map, where adding a key "
                            + "twice over leaves one key");
        }
        List<Value> wanted = pairsWantedBy(pairs, arguments, refinements);
        for (int at = 0; at + 1 < wanted.size(); at += 2) {
            map.put(wanted.get(at), wanted.get(at + 1));
        }
        // `*D_RET = *val;` is set before the work is done, so the answer is
        // the map rather than a position: a map has not got one.
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
        // Partial1 clamps a count to what is there and reads a negative one
        // as the values behind the position, which is how
        // `append/part m tail block -2` adds the last pair.
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
            members.clearAllDirectly((BitsetValue) bitsetOf(argumentFor(
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
            members.clearAllDirectly((BitsetValue) bitsetOf(range));
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

    private static boolean containsWhitespace(String text) {
        return text.chars().anyMatch(Character::isWhitespace);
    }

    /**
     * A value as text with nothing between its parts, which is what
     * TO-STRING means and FORM does not.
     *
     * <p>The two agree on every value that is not a series, which is how
     * they came to be conflated here: {@code to-string [1 2 3]} is "123"
     * and {@code form [1 2 3]} is "1 2 3". Nesting makes no difference to
     * the running together, so {@code to-string [1 [2 3]]} is also "123".
     */
    private static String runTogether(Value value) {
        if (value instanceof BlockValue block) {
            return block.remaining().stream()
                    .map(Natives::runTogether)
                    .collect(Collectors.joining());
        }
        return Molder.form(value);
    }

    // ---- ports -----------------------------------------------------------
    //
    // Everything here goes through a port the host supplied. A script given
    // no port reaches nothing, and whatever the port refuses arrives as an
    // ordinary error the script could have caught.

    private void definePorts() {
        // Two separate things must hold before any of these answers. The
        // host must grant the kind, and the host must supply somewhere
        // for the reading to go. A grant with no adapter reaches nothing,
        // and an adapter with no grant is not asked -- thus the grant is
        // tested first, so the error says which of the two is missing.
        // READ takes a file or a port. A port sends the action to its actor,
        // which is what Do_Port_Action does: one verb reaches every kind of
        // thing a script can open, and the actor decides what reading means.
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

        // A REBOL path uses a slash between the parts on every machine.
        // A local path uses whatever the machine uses. On a machine that
        // already uses a slash the two are the same text, which is why
        // these look as though they do nothing here.
        // /FULL puts the current directory in front of a relative path, and
        // is also what turns on the reading of `.` and `..`: the dot loop in
        // To_Local_Path is inside `if (full)`, so without it a dot is just a
        // character in a name. Asking for the full path is asking where the
        // process is, thus it needs the same grant WHAT-DIR does.
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
                    // The answer is a string whatever came in, because the C
                    // sets one: `Set_Series(REB_STRING, D_RET, ser)`. A local
                    // path is not a REBOL file and molding it as one would put
                    // a percent sign in front of a Windows drive letter.
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

        // A script that stops and waits for a person is a script a host
        // has to have agreed to. /HIDE reads without showing the typing,
        // and a host with no way to hide it refuses rather than showing
        // a password in the open.
        define("input", List.of(), Set.of("hide"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.CONSOLE);
                    return throughPort(() -> {
                        String line = refinements.contains("hide")
                                ? evaluator.console().readHiddenLine()
                                : evaluator.console().readLine();
                        // Nothing more to read is none, which a script
                        // must be able to tell from an empty line.
                        return line == null ? NoneValue.none() : StringValue.of(line);
                    });
                });

        // The question is written out first, then the line is read. One
        // call rather than two, because the two must not be separated by
        // anything else the script prints.
        // `question [series!]` -- a block is reformed into the prompt, which
        // is how a caller builds one from parts without joining it first.
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

        // The environment is read only. A JVM cannot change the
        // environment of its own process, thus SET-ENV has nothing to
        // call and says that no host can offer it.
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
                                    // A string key, as a real R3 gives.
                                    // A set-word reads better and is not
                                    // what a script written for R3 will
                                    // look the name up with.
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

        // Where a relative path counts from. It belongs to the port and
        // not to the Java process, thus one interpreter cannot move
        // another and a JVM's own inability to change directory does not
        // stop a script having one.
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

        // /DEEP makes the directories above it as well. Without it a
        // missing parent is a failure, which is what makes the refinement
        // worth asking for.
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

        // create: path [file! url!] -- makes a directory, or an empty file.
        //
        // Rebol's own MAKE-DIR is written on top of it: `if any [not deep
        // url? path] [create path return path]`. So JEBOL's MAKE-DIR native
        // is what MAKE-DIR used to be, and is now what CREATE is for. Both
        // are here because the mezzanine one shadows the native and calls it
        // back through CREATE.
        define("create", List.of(Parameter.required("path",
                        Set.of(Datatype.FILE, Datatype.URL))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> {
                        String path = ((StringValue) arguments.getFirst()).text();
                        // A trailing slash means a directory, which is the
                        // same distinction every other file native draws.
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

        // A directory's own name ends with a slash and a file's does not,
        // which is how a caller tells them apart without asking again.
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

        // SET-SCHEME attaches an actor to a scheme, and it is the seam where
        // Java meets Rebol's own REBOL. MAKE-SCHEME in sys-ports.reb builds
        // the scheme object and then calls this, exactly as Rebol's C does:
        // the REBOL half decides what a scheme is and the host half decides
        // what it can actually reach.
        //
        // A scheme JEBOL has no actor for is left alone and answers none,
        // which is what the C does for the same case. So registering a scheme
        // JEBOL cannot serve is not an error, it just gives a scheme nothing
        // can open.
        define("set-scheme", List.of(
                        Parameter.required("scheme", Set.of(Datatype.OBJECT))),
                (arguments, evaluator, context) -> {
                    ObjectValue scheme = (ObjectValue) arguments.getFirst();
                    Value named = scheme.context().holds("name")
                            ? scheme.context().ownSlotFor("name").value()
                            : NoneValue.none();
                    if (!(named instanceof WordValue name)
                            || !name.canonical().equals("console")) {
                        return NoneValue.none();
                    }
                    scheme.context().set("actor", WordValue.of("console"));
                    return LogicValue.of(true);
                });

        define("port?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.getFirst() instanceof PortValue));

        // OPEN builds a port from a spec and then opens it. Building it is
        // MAKE-PORT* in sys-ports.reb, which is Rebol's own REBOL, thus this
        // native calls back into the library rather than doing the work.
        // That is what Rebol's C does too: Make_Port is four lines and one of
        // them is `Do_Sys_Func(SYS_CTX_MAKE_PORT_P, spec, 0)`.
        // /ALLOW takes a block of "protection attributes" that nothing reads.
        // `args = Find_Refines(ds, ALL_OPEN_REFS)` collects the refinement
        // FLAGS and hands them to Setup_File, and no arm anywhere looks up the
        // block itself. Declared because a script written for Rebol passes one.
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
                    port.markOpen(true);
                    return port;
                });

        // READ on a port sends the action to the port's actor. The console
        // actor answers one line, which is what Console_Actor's A_READ does
        // once RDM_READ_LINE is set -- and INPUT sets it with MODIFY.
        //
        // The answer is a string rather than a binary because /STRING is the
        // shape INPUT asks for, and none at the end of the input, because
        // Console_Actor answers none for a line that is not there.
        // update: port [port!] -- "Updates external and internal states".
        //
        // The console actor answers none and does nothing, and says why in a
        // comment: "no wake-up, events should be handled by user defined
        // port's awake function". So this is a real answer rather than a stub.
        define("update", List.of(Parameter.required("port", Set.of(Datatype.PORT))),
                (arguments, evaluator, context) -> NoneValue.none());

        // flush: port [port!] -- "Flush output stream buffer."
        //
        // The console actor pushes the device and answers the port. Nothing is
        // buffered in the domain, so the work is the adapter's; a port that
        // writes straight through has nothing held back.
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

        // MODIFY sets one mode of a port. The console has three -- echo, line
        // and error -- and each takes a logic. Rebol raises bad-file-mode for
        // any other word and invalid-value-for a value that is not a logic.
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

        // The five things a script may ask the operator for through a window.
        // One grant covers all of them, because a host that will put one
        // dialog on the screen will put any of them there.
        //
        // Every one answers none when the operator declines, and raises when
        // the service is refused. Those must never look the same: closing a
        // chooser is an answer, and a script told none for a refusal retries
        // the dialog for as long as the operator keeps closing it.
        define("browse", List.of(Parameter.required("url",
                        Set.of(Datatype.URL, Datatype.FILE, Datatype.NONE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.WINDOWS);
                    return throughWindow(() -> {
                        // None is in Rebol's own spec, so browsing nothing is
                        // a call rather than a mistake. Nothing to open means
                        // nothing to do.
                        if (!(arguments.getFirst() instanceof StringValue target)) {
                            return NoneValue.none();
                        }
                        evaluator.windows().browse(target.text());
                        return NoneValue.none();
                    });
                });

        // The datatype of the answer follows the refinement rather than the
        // outcome: /MULTI always answers a block, so code walking it need not
        // test for none first, and the plain form always answers a file or
        // none, so `read request-file` works.
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

        // A colour goes in as a tuple and comes back as one, per Rebol's own
        // `/default color [tuple!]`.
        define("request-color", List.of(
                        Parameter.belongingTo("default", "color", Set.of(Datatype.TUPLE))),
                Set.of("default", "rgb16"),
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

        // What the operator types must not reach the screen, which is the
        // whole reason this is its own request rather than a text dialog.
        // No prompt, because the C declares no argument at all:
        // `request-password: native [{Asks user for input without echoing...}]`
        // and nothing else. A caller that wants a prompt prints one, which is
        // what Rebol's own ASK-PASSWORD does -- `prin question` and then this.
        define("request-password", List.of(),
                (arguments, evaluator, context) -> {
                    requireService(HostService.WINDOWS);
                    return throughWindow(() -> evaluator.windows()
                            .askForPassword()
                            .<Value>map(StringValue::of)
                            .orElseGet(NoneValue::none));
                });

        // QUERY is what the rest of the file library is built on: SIZE? and
        // MODIFIED? are one line each over it, and LIST-DIR and DIR-TREE both
        // ask it for three fields at once. One crossing of the boundary, so
        // there is one place for the host to be asked.
        //
        // The shape of the second argument decides the shape of the answer.
        // See queryAnswerFor.
        //
        // /MODE is declared and does nothing, and the C says why in the spec
        // itself: `/mode "** DEPRECATED **"`. No arm reads it -- there is no
        // ARG_QUERY_MODE anywhere in the source -- so a script that still asks
        // for it gets the same answer as one that does not. Declared so that
        // script can be run at all.
        define("query", List.of(
                        Parameter.required("target", Set.of(Datatype.FILE, Datatype.DATE,
                                Datatype.HANDLE, Datatype.PORT, Datatype.URL,
                                Datatype.BLOCK, Datatype.WORD)),
                        Parameter.required("field",
                                Set.of(Datatype.WORD, Datatype.BLOCK,
                                        Datatype.NONE, Datatype.DATATYPE))),
                Set.of("mode"),
                (arguments, evaluator, context, refinements) -> {
                    Value target = arguments.getFirst();
                    Value field = arguments.get(1);
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
            // A word rather than a logic, and both words are truthy, so
            // `if query %a 'type` says nothing and a caller has to compare.
            case "type" -> WordValue.of(about.isDirectory() ? "dir" : "file");
            // Two names for one fact. The C's own comment says DATE is there
            // for backward compatibility, and the object form fills both from
            // the same source.
            case "date", "modified" -> asDateValue(about.modified());
            case "accessed" -> asDateValue(about.accessed());
            case "created" -> asDateValue(about.created());
            case "name" -> StringValue.of(about.name(), Datatype.FILE);
            // A misspelled field is a mistake in the script, so it raises.
            // Answering none would let the script read its own typo as a
            // missing file.
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
        if (!port.schemeName().equals("console")) {
            throw Raised.of(EvaluationFailure.NO_SERVICE,
                    "nothing here reads the " + port.schemeName() + " scheme");
        }
        requireService(HostService.CONSOLE);
        return throughPort(() -> {
            String line = evaluator.console().readLine();
            return line == null ? NoneValue.none() : StringValue.of(line);
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
            // A word or a path names what to check. Anything else is the
            // caller having written the block wrongly.
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
        java.nio.charset.CharsetDecoder strictly =
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        try {
            return strictly.decode(
                    java.nio.ByteBuffer.wrap(octets.octetsFromHere())).toString();
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
            // An error binds like the object its fields make -- `else if
            // (IS_ERROR(arg)) frame = VAL_ERR_OBJECT(arg);` -- which is how
            // the catalogue's :arg1 get-words read a caught error's values.
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
            // The top of the C's loop "always follows / or start", which is
            // what makes a dot here the beginning of a segment rather than a
            // character inside one.
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
        // The slash after the segment is left where it is, because the loop
        // that copies a segment is what consumes one -- and that is what brings
        // the walk back to the top of the loop, where the next segment gets its
        // own look for dots. Stepping over it here read `../../c` as `../c`,
        // because the second pair of dots was copied as a name.
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
            // `if (IS_WORD(words) || IS_SET_WORD(words))` -- anything else in
            // the block is passed over rather than refused.
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
     * What COPY will duplicate: the eight datatypes its spec names.
     *
     * <p>{@code value [series! port! map! object! bitset! any-function! error!
     * struct!]}. Declared rather than left open, because the list is what decides
     * the error: a gob is not on it, so `copy make gob! []` is the wrong argument
     * rather than an operation a gob does not support. STRUCT is absent here for
     * the reason it is absent everywhere -- the datatype belongs to a build with
     * the FFI in it.
     */
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
        if (!port.schemeName().equals("console")) {
            throw schemeRefusal("writes", port);
        }
        requireService(HostService.CONSOLE);
        evaluator.output().write(Molder.form(data));
        return port;
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
        // A gob has a position and is not a series: `boot/types.reb` gives it no
        // typeset at all, so `series? make gob! []` is false while `index?` still
        // answers. Every one of the eleven navigation actions names it by hand,
        // and so must this.
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
            default -> {
                // A scheme with no service behind it is one JEBOL cannot
                // serve. Opening it is refused rather than answering a port
                // that nothing can read.
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
            throw new Raised(ErrorValue.of(
                    ErrorCategory.ACCESS, denied.errorId(),
                    denied.getMessage() + ", which is "
                            + ServiceRefusal.NOT_PRESENT.name()
                                    .toLowerCase(java.util.Locale.ROOT).replace('_', ' ')));
        }
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

    // ---- parse -----------------------------------------------------------

    private void defineParse() {
        // PARSE with a block matches a grammar and answers whether the whole
        // input fitted. PARSE with a string or none splits on delimiters,
        // which is a different job under the same name and always has been.
        define("parse", List.of(Parameter.required("input"), Parameter.required("rule")),
                Set.of("case"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(1)) {
                    // A binary is walked one byte at a time by the same
                    // walker that takes a string, each byte standing in
                    // for a character. Sent to the block walker instead
                    // it arrived as a single item in a list of one, so
                    // `parse #{0102} [2 skip]` saw one thing rather than
                    // two bytes and no binary rule could match.
                    case BlockValue rule -> arguments.get(0) instanceof StringValue
                            || arguments.get(0) instanceof BinaryValue
                            ? StringParser.answer(evaluator, context,
                                    (SeriesValue) arguments.get(0), rule,
                                    refinements.contains("case"))
                            : Parser.answer(evaluator, context, arguments.get(0), rule,
                                    refinements.contains("case"));
                    // REBOL 2 and R3-Alpha both read a string, character or
                    // NONE rule as delimiters to split on. 3.22.1 removed
                    // that: one name was doing two unrelated jobs, and SPLIT
                    // now does the second one.
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

    // ---- layout ----------------------------------------------------------

    private void defineLayout() {
        // LAYOUT hands its block back rather than drawing anything. What the
        // block means is decided by whatever renders it, which is how the
        // same layout can become markup here and a window somewhere else.
        define("layout", List.of(Parameter.required("description", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> arguments.get(0));

        // VIEW is what a script calls to show a layout. Here it is the
        // identity, because showing is the host's job.
        define("view", takes("layout"),
                (arguments, evaluator, context) -> arguments.get(0));
    }

    // ---- output ----------------------------------------------------------

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
        // /all asks for the form that reads back under every setting.
        // JEBOL's MOLD already writes that -- construction syntax for the
        // values with no literal spelling -- so the refinement is accepted
        // and changes nothing rather than being refused.
        // /ONLY drops a block's own brackets, /FLAT drops the indentation, and
        // /PART cuts the answer to a length. /PART declared no argument here, so
        // asking for it did nothing at all -- a caller molding a large value to
        // a bounded width got the whole of it.
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

        // QUIT ends the run and nothing else. A real REBOL ends the process
        // with it, because there the script is the process; here the script
        // is a guest and only the host may decide that anything exits.
        //
        // /NOW skips REBOL's shutdown work. There is none to skip here, so
        // it is accepted and ignored: borrowed code passes it, and refusing
        // the refinement would fail that code for no gain.
        define("quit", List.of(Parameter.belongingTo("return", "value", Set.of())),
                Set.of("now", "return"),
                (arguments, evaluator, context, refinements) -> {
                    // A plain QUIT carries UNSET, on the authority of Rebol's
                    // own test suite: `unset? catch/quit [quit]`, five
                    // assertions in evaluation-test.r3.
                    //
                    // The C's comment on the line that does it says otherwise
                    // -- `Halt_Code(RE_QUIT, val); // NONE if /return not set`
                    // -- and `val` really is /RETURN's argument, which an
                    // unsupplied refinement leaves as none. So the comment
                    // describes what QUIT hands over and not what a caller
                    // gets back; something between there and CATCH turns it
                    // into unset.
                    //
                    // Measured both ways: none costs five suite assertions and
                    // unset costs none. The suite is Rebol's own and is the
                    // stronger authority, so it decides.
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
