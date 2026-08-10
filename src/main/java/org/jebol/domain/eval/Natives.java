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
import java.util.function.IntPredicate;
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
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MapValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
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
        defineOperator("//", "remainder");
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
        catalog.set("bitsets", new ObjectValue(bitsets));

        // Suffix then name, in pairs. REGISTER-CODEC appends to this as
        // each codec arrives, so it starts as the few JEBOL knows about
        // rather than as R3's full list -- the list is a record of what
        // has registered, not a declaration of what may.
        catalog.set("file-types", BlockValue.block(List.of(
                StringValue.of(".txt", Datatype.FILE), WordValue.of("text"),
                StringValue.of(".html", Datatype.FILE), WordValue.of("markup"),
                StringValue.of(".htm", Datatype.FILE), WordValue.of("markup"))));

        Context options = Context.root();
        options.set("binary-base", IntegerValue.of(16));
        options.set("log", new ObjectValue(Context.root()));

        // What a value must be for the console to print it as a result.
        // base-defs.reb assigns this on startup, and a set-path cannot
        // create a field an object has not got -- R3 refuses that too --
        // so the slot has to be here for the assignment to land.
        options.set("result-types", NoneValue.none());
        // Where the interpreter was started from and where it lives. Both
        // are files in a real R3, and code reads them to find things
        // relative to itself.
        options.set("home", StringValue.of(
                System.getProperty("user.home", "") + "/", Datatype.FILE));
        options.set("boot", StringValue.of(
                System.getProperty("user.dir", "") + "/jebol", Datatype.FILE));
        options.set("script", NoneValue.none());

        Context state = runState;
        state.set("last-error", NoneValue.none());
        state.set("last-result", NoneValue.none());

        // The catalogue as data a script can walk: one object per
        // category holding its code and one field per error id. Rebol's
        // own suite reaches for both.
        Context errors = Context.root();
        ErrorCatalogue.categories().forEach(category -> {
            Context inside = Context.root();
            inside.set("code", IntegerValue.of(ErrorCatalogue.baseCodeOf(category)));
            inside.set("type", StringValue.of(category.toLowerCase(Locale.ROOT)));
            ErrorCatalogue.idsIn(category)
                    .forEach(errorId -> inside.set(errorId, StringValue.of(errorId)));
            errors.set(category, new ObjectValue(inside));
        });
        catalog.set("errors", new ObjectValue(errors));

        Context system = Context.root();
        system.set("catalog", new ObjectValue(catalog));
        system.set("options", new ObjectValue(options));
        system.set("state", new ObjectValue(state));
        system.set("version", StringValue.of("0.1.0"));
        system.set("platform", WordValue.of("JVM"));
        system.set("codecs", new ObjectValue(Context.root()));
        system.set("console", new ObjectValue(Context.root()));

        // base-constants.reb starts by naming the two contexts it works
        // in, and everything after that line in the file depends on the
        // names existing. LIB is where the natives live; SYS is the
        // interpreter's own, and here they are the same context because
        // JEBOL has no second one to give.
        Context contexts = Context.root();
        contexts.set("lib", new ObjectValue(systemContext));
        contexts.set("sys", new ObjectValue(systemContext));
        system.set("contexts", new ObjectValue(contexts));
        return new ObjectValue(system);
    }

    /** What the evaluator dispatches on: native name to behaviour. */
    public Map<String, RefinedCallable> behaviours() {
        return Map.copyOf(behaviours);
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
        define("square-root", takesNumbers("value"),
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
        define("now", List.of(),
                (arguments, evaluator, context) -> {
                    requireService(HostService.CLOCK);
                    java.time.LocalDateTime here = java.time.LocalDateTime.now();
                    return DateValue.of(here.getYear(), here.getMonthValue(),
                            here.getDayOfMonth(),
                            TimeValue.ofNanoseconds(here.toLocalTime().toNanoOfDay()));
                });

        // The four that exist to call code written in C. Each one is
        // refused whatever the host granted, and the error says that
        // nothing can offer it rather than that this host did not.
        define("load-extension", takes("path"),
                (arguments, evaluator, context) -> refuseExtensionPoint("load-extension"));
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
        define("exp", takesNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.exp(Comparison.asDouble(arguments.get(0)))));
        define("log-e", takesNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.log(Comparison.asDouble(arguments.get(0)))));
        define("log-10", takesNumbers("value"),
                (arguments, evaluator, context) -> DecimalValue.of(
                        Math.log10(Comparison.asDouble(arguments.get(0)))));
        // E raised to a power, the other way round from LOG. Named for
        // the mathematics rather than for what it does to its argument,
        // which is why it is not called power-of-e.
        define("exp", takesNumbers("value"),
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
        define("log-2", takesNumbers("value"),
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
        define("sine", takesNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        withoutTheNoiseNearZero(
                                Math.sin(inRadians(arguments.get(0), refinements)))));
        define("cosine", takesNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        withoutTheNoiseNearZero(
                                Math.cos(inRadians(arguments.get(0), refinements)))));
        define("tangent", takesNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) ->
                        DecimalValue.of(tangentOf(inRadians(arguments.get(0), refinements))));
        define("arcsine", takesNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        refinements.contains("radians")
                                ? Math.asin(Comparison.asDouble(arguments.get(0)))
                                : Math.toDegrees(Math.asin(Comparison.asDouble(arguments.get(0))))));
        define("arccosine", takesNumbers("value"), Set.of("radians"),
                (arguments, evaluator, context, refinements) -> DecimalValue.of(
                        refinements.contains("radians")
                                ? Math.acos(Comparison.asDouble(arguments.get(0)))
                                : Math.toDegrees(Math.acos(Comparison.asDouble(arguments.get(0))))));
        define("arctangent", takesNumbers("value"), Set.of("radians"),
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

        define("complement", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case LogicValue truth -> LogicValue.of(!truth.truth());
                    case IntegerValue whole -> IntegerValue.of(~whole.magnitude());
                    // The set of everything the bitset does not hold,
                    // which is what a rule means by "any but these".
                    case BitsetValue members -> members.complemented();
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

        define("negate", takesNumbers("value"),
                (arguments, evaluator, context) -> arithmetic(
                        List.of(IntegerValue.of(0), arguments.get(0)), Operation.SUBTRACT));

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
            Datatype.MONEY, Datatype.CHAR, Datatype.TIME, Datatype.PAIR);

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
     * Integer arithmetic raises on overflow rather than wrapping. The JVM
     * wraps silently, which is the worst available behaviour: a wrong answer
     * that looks like a right one.
     */
    private static Value arithmetic(List<Value> arguments, Operation operation) {
        Value left = arguments.get(0);
        Value right = arguments.get(1);

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
        asksAbout("greater-or-equal?", Comparison.Strictness.GREATER_OR_EQUAL, true);
        asksAbout("lesser?", Comparison.Strictness.GREATER_OR_EQUAL, false);
        asksAbout("greater?", Comparison.Strictness.GREATER, true);
        asksAbout("lesser-or-equal?", Comparison.Strictness.GREATER, false);
        // =? is SAME? rather than EQUAL?: it asks whether two references are
        // one value, so `"a" =? "a"` is false.
        asksAbout("same?", Comparison.Strictness.SAME, true);
    }

    /**
     * One comparison native: the strictness it asks about, and whether it
     * reports the answer or its opposite.
     */
    private void asksAbout(String name, Comparison.Strictness strictness, boolean asAsked) {
        define(name, takes("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(asAsked
                        == Comparison.holds(arguments.get(0), arguments.get(1), strictness)));
    }

    // ---- control ---------------------------------------------------------

    private void defineControl() {
        // /ONLY hands the branch back without running it, so
        // `if/only true [1]` is the block rather than 1. It is how a
        // caller picks between two blocks with neither being evaluated.
        define("if", List.of(Parameter.required("condition"),
                        Parameter.required("branch", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    if (!arguments.get(0).isTruthy()) {
                        return NoneValue.none();
                    }
                    return branchTaken((BlockValue) arguments.get(1), evaluator, context,
                            refinements);
                });

        define("either", List.of(Parameter.required("condition"),
                        Parameter.required("true-branch", Set.of(Datatype.BLOCK)),
                        Parameter.required("false-branch", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue taken = (BlockValue) (arguments.get(0).isTruthy()
                            ? arguments.get(1)
                            : arguments.get(2));
                    return branchTaken(taken, evaluator, context, refinements);
                });

        define("not", takes("value"),
                (arguments, evaluator, context) -> {
                    return LogicValue.of(!arguments.get(0).isTruthy());
                });

        // /NEXT evaluates one expression and moves the given word on to
        // what is left, so a caller can walk a block an expression at a
        // time. It takes a word rather than answering a pair because the
        // caller almost always wants to keep stepping the same variable.
        define("do", List.of(Parameter.required("value"),
                        Parameter.belongingTo("next", "var", Set.of(Datatype.WORD))),
                Set.of("next", "args"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("next") && arguments.size() > 1
                            && arguments.getFirst() instanceof BlockValue block) {
                        Evaluator.Step taken =
                                evaluator.evaluateNextOrRaise(block, context);
                        slotOf((WordValue) arguments.get(1))
                                .setValue(block.atIndex(taken.nextIndex()));
                        return taken.value();
                    }
                    return switch (arguments.getFirst()) {
                        case BlockValue block ->
                                evaluator.evaluateOrRaise(block, context);
                        case StringValue text -> evaluator.evaluateSource(text.text());
                        // DO of an error raises it. That is how a script
                        // raises an error it built itself, and Rebol's own
                        // CAUSE-ERROR is written as `do make error! [...]`
                        // and nothing else. Answering the error as a value
                        // makes every such call do nothing at all, and the
                        // value is then dropped when anything follows it.
                        case ErrorValue built -> throw new Raised(built);
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

        define("unless", List.of(Parameter.required("condition"),
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
                    List<Value> choices = ((BlockValue) arguments.get(1)).remaining();
                    for (int at = 0; at + 1 < choices.size(); at += 2) {
                        boolean chosen = refinements.contains("case")
                                ? choices.get(at).equals(arguments.get(0))
                                : Comparison.looselyEqual(choices.get(at), arguments.get(0));
                        if (chosen) {
                            return choices.get(at + 1) instanceof BlockValue branch
                                    ? evaluator.evaluateOrRaise(
                                            branch, context)
                                    : choices.get(at + 1);
                        }
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
                        if (afterCondition.atTail()) {
                            throw Raised.of(EvaluationFailure.NEED_VALUE,
                                    "a case condition has no block after it");
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
                        failure = ErrorValue.of(ErrorCategory.THROW, "throw",
                                "a throw that nothing caught");
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
                        failure = ErrorValue.of(ErrorCategory.THROW, "return",
                                "a return outside a function");
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
        define("return", takes("value"),
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
        define("throw", List.of(Parameter.required("value"),
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
                        if (!refinements.contains("all")
                                && !answersTo(thrown, expectedNames(arguments, refinements))) {
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
                    }
                    runState.set("last-result", handled);
                    // /WITH runs on a throw and not on the way out, so it
                    // is a handler rather than a finally.
                    if (refinements.contains("with")) {
                        Value handler = arguments.getLast();
                        return handler instanceof BlockValue block
                                ? evaluator.evaluateOrRaise(block, context)
                                : evaluator.applyFunction(
                                        handler, List.of(handled, carriedName));
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
        define("make", List.of(Parameter.required("prototype"), Parameter.required("body")),
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
                    case DatatypeValue wanted when wanted.represents() == Datatype.MAP ->
                            MapValue.of(itemsOf(arguments.get(1)));
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
                    case DatatypeValue wanted when wanted.represents() == Datatype.FUNCTION ->
                            functionFrom(arguments.get(1), context);
                    case DatatypeValue wanted when wanted.represents() == Datatype.ERROR ->
                            errorFromSpec(arguments.get(1), evaluator, context);
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
                    case DatatypeValue wanted -> makeOfDatatype(wanted, arguments.get(1));
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
                    constructInto(built, items, refinements.contains("only"));
                    return new ObjectValue(built);
                });

        // CONTEXT? answers the object a bound word lives in, which is how
        // code holding a word can reach the rest of what surrounds it.
        define("context?", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    WordValue word = (WordValue) arguments.getFirst();
                    return word.isBound()
                            ? new ObjectValue(word.binding())
                            : NoneValue.none();
                });

        // RESOLVE fills in only what the target has no value for. /ALL
        // overwrites what it has, /EXTEND adds the words it lacks. Without
        // /EXTEND a word only the source has is skipped rather than added.
        define("resolve", List.of(
                        Parameter.required("target", Set.of(Datatype.OBJECT)),
                        Parameter.required("source", Set.of(Datatype.OBJECT))),
                Set.of("only", "all", "extend"),
                (arguments, evaluator, context, refinements) -> {
                    Context into = ((ObjectValue) arguments.getFirst()).context();
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
                    for (ContextSlot slot : ((ObjectValue) arguments.get(1)).context().slots()) {
                        if (slot.canonical().equals("self")) {
                            continue;
                        }
                        if (!into.holds(slot.canonical())) {
                            if (refinements.contains("extend")) {
                                into.set(slot.spelling(), slot.value());
                            }
                            continue;
                        }
                        if (refinements.contains("all")
                                || into.slotFor(slot.canonical()).value() instanceof UnsetValue) {
                            into.set(slot.spelling(), slot.value());
                        }
                    }
                    return arguments.getFirst();
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
        define("in", List.of(
                        Parameter.required("object", Set.of(Datatype.OBJECT)),
                        Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    ObjectValue object = (ObjectValue) arguments.get(0);
                    WordValue word = (WordValue) arguments.get(1);
                    if (!object.context().holds(word.canonical())) {
                        throw Raised.of(EvaluationFailure.INVALID_PATH, word.spelling());
                    }
                    return word.boundTo(object.context());
                });

        // COLLECT-WORDS gathers the words a block uses, which is what
        // building a context out of a block needs. Nested blocks are not
        // looked into unless /deep says so.
        // APPLY reaches a function when the arguments are already a
        // block rather than written after the call. The arity is still
        // checked, so a short block is the same mistake as a short call.
        define("apply", List.of(Parameter.required("callee"),
                        Parameter.required("arguments", Set.of(Datatype.BLOCK))),
                Set.of("only"),
                (arguments, evaluator, context, refinements) -> {
                    List<Value> given = ((BlockValue) arguments.get(1)).remaining();
                    long wanted = arityOf(arguments.get(0));
                    if (given.size() < wanted) {
                        return raiseWrongArgument(arguments.get(1), "apply",
                                wanted + " arguments and got " + given.size());
                    }
                    return evaluator.applyFunction(arguments.get(0), given);
                });

        // ASSERT raises with an id of its own rather than a generic
        // script error, so a caller can tell an assertion from anything
        // else that went wrong.
        define("assert", List.of(Parameter.required("conditions", Set.of(Datatype.BLOCK))),
                Set.of("type"),
                (arguments, evaluator, context, refinements) -> {
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
        define("collect-words", List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("ignore", "known",
                                Set.of(Datatype.BLOCK, Datatype.OBJECT))),
                Set.of("deep", "set", "ignore", "as"),
                (arguments, evaluator, context, refinements) -> {
                    List<Value> found = new ArrayList<>();
                    gatherWords((BlockValue) arguments.get(0), refinements.contains("deep"),
                            refinements.contains("set"), found);
                    Value ignoring = argumentFor(
                            "ignore", List.of("ignore"), arguments, refinements, 1);
                    if (ignoring != null) {
                        Set<String> known = namesIn(ignoring);
                        found.removeIf(word -> word instanceof WordValue named
                                && known.contains(named.canonical()));
                    }
                    return BlockValue.block(found);
                });

        // The line-break marker belongs to a position rather than to a
        // value, so it takes a pair of natives of its own: there is
        // nothing you could insert into the block to put one there.
        define("new-line", List.of(
                        Parameter.required("position", Set.of(Datatype.BLOCK, Datatype.PAREN)),
                        Parameter.required("value")),
                Set.of("all", "skip"),
                (arguments, evaluator, context, refinements) -> {
                    BlockValue block = (BlockValue) arguments.get(0);
                    block.storage().setLineBreakAt(block.index(), arguments.get(1).isTruthy());
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

        // UNBIND takes the binding off and answers what it was given, so
        // a block comes back as the same block with its words loose.
        define("unbind", List.of(Parameter.required("word")),
                Set.of("deep"),
                (arguments, evaluator, context, refinements) -> arguments.get(0));

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
                    Context target = switch (arguments.get(1)) {
                        case ObjectValue object -> object.context();
                        case WordValue named when named.isBound() -> named.binding();
                        default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                                "bind wanted an object or a bound word, not "
                                        + arguments.get(1).datatype().literalSpelling());
                    };
                    if (arguments.get(0) instanceof WordValue word) {
                        if (refinements.contains("new") || refinements.contains("set")) {
                            target.define(word.canonical());
                        }
                        // The same question a block gets asked, one word at
                        // a time: the answer names whichever context holds
                        // the slot, which may be above the target. Naming
                        // the target instead let BIND/NEW hang a fresh name
                        // off a scope that had nothing to do with the word.
                        return word.boundTo(target.knows(word.canonical())
                                ? target.holderOf(word.canonical())
                                : target);
                    }
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseWrongArgument(arguments.get(0), "bind", "word or block");
                    }
                    if (refinements.contains("new") || refinements.contains("set")) {
                        defineFreshWordsOf(block, target, refinements.contains("set"));
                    }
                    return Binder.bind(block, target);
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
        // Copying an object copies its methods too, and a method that still
        // closed over the object it was written in would move money in the
        // original when called on the copy.
        prototype.ifPresent(existing -> existing.context().slots()
                .forEach(slot -> fields.set(
                        slot.spelling(), rehomed(slot.value(), fields))));

        declaredFieldsIn(body).forEach(fields::define);
        ObjectValue built = new ObjectValue(fields);
        fields.set("self", built);

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
        fields.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .toList()
                .forEach(slot -> fields.set(slot.spelling(), rehomed(slot.value(), fields)));
        return merged;
    }

    /** A function copied into a new object belongs to that object now. */
    private static Value rehomed(Value value, Context fields) {
        return value instanceof FunctionValue function
                ? new FunctionValue(function.spec(), function.body(),
                        function.parameters(), function.localNames(), fields)
                : value;
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
        define("loop", List.of(
                        Parameter.required("count", Set.of(Datatype.INTEGER)),
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
                        Parameter.required("count", Set.of(Datatype.INTEGER)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    long passes = ((IntegerValue) arguments.get(1)).magnitude();
                    return countedLoop(
                            evaluator,
                            (WordValue) arguments.get(0),
                            (BlockValue) arguments.get(2),
                            index -> IntegerValue.of(index + 1),
                            passes);
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
                        arguments.get(0),
                        arguments.get(1),
                        (BlockValue) arguments.get(2)));

        // REMOVE-EACH drops what the block accepts and answers the
        // series, which is the opposite way round from POKE.
        define("remove-each", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("series",
                                Set.of(Datatype.BLOCK, Datatype.BINARY, Datatype.STRING)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    // A binary and a string are walked the same way a
                    // block is, one element at a time. Taking only a
                    // block made a protected binary report the wrong
                    // failure, and refused a perfectly ordinary call.
                    if (arguments.get(1) instanceof SeriesValue other
                            && !(other instanceof BlockValue)) {
                        return removedEachFrom(other, arguments, evaluator);
                    }
                    BlockValue series = (BlockValue) arguments.get(1);
                    Context locals = Context.childOf(evaluator.systemContext());
                    WordValue word = (WordValue) arguments.get(0);
                    locals.define(word.spelling());
                    BlockValue bound = Binder.bind((BlockValue) arguments.get(2), locals);
                    List<Value> kept = new ArrayList<>();
                    for (Value item : series.remaining()) {
                        locals.set(word.spelling(), item);
                        if (!evaluator.evaluateOrRaise(bound, locals).isTruthy()) {
                            kept.add(item);
                        }
                    }
                    int had = series.lengthFromHere();
                    for (int removed = 0; removed < had; removed++) {
                        series.storage().removeAt(series.index());
                    }
                    for (int at = kept.size(); at > 0; at--) {
                        series.storage().insertAt(series.index(), kept.get(at - 1));
                    }
                    return series;
                });

        // MAP-EACH walks for an answer where FOREACH walks for effect. It
        // is a native rather than prelude because it binds the caller's
        // block to the word it walks with, and binding a block to a
        // context is not something the language can say without BIND.
        define("map-each", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("series"),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    Context locals = Context.childOf(evaluator.systemContext());
                    WordValue word = (WordValue) arguments.get(0);
                    locals.define(word.spelling());
                    BlockValue bound = Binder.bind(
                            (BlockValue) arguments.get(2), locals);
                    List<Value> gathered = new ArrayList<>();
                    for (Value item : itemsOf(arguments.get(1))) {
                        locals.set(word.spelling(), item);
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
        define("forall", List.of(
                        Parameter.softQuoted("word"),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    WordValue word = (WordValue) arguments.get(0);
                    ContextSlot slot = slotOf(word);
                    if (!(slot.value() instanceof SeriesValue start)) {
                        return raiseCannotUse(slot.value(), "forall");
                    }
                    BlockValue body = (BlockValue) arguments.get(1);
                    Value last = NoneValue.none();
                    try {
                        for (int at = start.index(); at <= start.storageLength(); at++) {
                            slot.setValue(start.atIndex(at));
                            last = oneRound(evaluator, body, evaluator.systemContext());
                        }
                    } catch (LoopSignal stopped) {
                        return stopped.answer();
                    } finally {
                        slot.setValue(start);
                    }
                    return last;
                });

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

        define("break", List.of(Parameter.belongingTo("return", "value", Set.of())),
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
            WordValue counter,
            BlockValue body,
            java.util.function.LongFunction<Value> valueAt,
            long passes) {

        Context locals = Context.childOf(evaluator.systemContext());
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

    private static Value steppedLoop(
            Evaluator evaluator,
            WordValue counter,
            Value start,
            Value end,
            Value step,
            BlockValue body) {

        double stepBy = Comparison.asDouble(step);
        if (stepBy == 0.0) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "a for loop with a step of zero would never end");
        }
        // A character range looks like it ought to work and does not. The
        // guide's `for c #"a" #"e" 1 [...]` was implemented here from that
        // example; a real R3 raises expect-arg on it, and FOREACH over a
        // string is how the alphabet actually gets walked.
        rejectCharacterBound(start);
        rejectCharacterBound(end);

        double from = Comparison.asDouble(start);
        double to = Comparison.asDouble(end);
        boolean wholeNumbers = start instanceof IntegerValue && step instanceof IntegerValue;

        Context locals = Context.childOf(evaluator.systemContext());
        locals.define(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);
        Value last = NoneValue.none();

        try {
            for (double at = from; stepBy > 0 ? at <= to : at >= to; at += stepBy) {
                locals.set(counter.spelling(),
                        wholeNumbers ? IntegerValue.of((long) at) : DecimalValue.of(at));
                last = oneRound(evaluator, bound, locals);
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
            Evaluator evaluator, Value target, Value series, BlockValue body) {

        List<WordValue> names = target instanceof BlockValue block
                ? block.remaining().stream().map(WordValue.class::cast).toList()
                : List.of((WordValue) target);
        // An object walks as its words, or as its words and values when
        // the loop takes two. Which it is depends on the loop rather than
        // on the object, so it cannot be settled where the items are
        // gathered -- `foreach w o` gives [a b] and `foreach [k v] o`
        // gives [a 1 b 2].
        List<Value> items = series instanceof ObjectValue object && names.size() == 1
                ? object.context().slots().stream()
                        .filter(slot -> !slot.canonical().equals("self"))
                        .<Value>map(slot -> WordValue.of(slot.spelling()))
                        .toList()
                : itemsOf(series);

        Context locals = Context.childOf(evaluator.systemContext());
        names.forEach(name -> locals.define(name.spelling()));
        BlockValue bound = Binder.bind(body, locals);
        Value last = NoneValue.none();

        try {
            for (int at = 0; at + names.size() <= items.size(); at += names.size()) {
                for (int which = 0; which < names.size(); which++) {
                    locals.set(names.get(which).spelling(), items.get(at + which));
                }
                last = oneRound(evaluator, bound, locals);
            }
        } catch (LoopSignal stopped) {
            return stopped.answer();
        }
        return last;
    }

    /** A series as a list of its values, whatever kind of series it is. */
    private static List<Value> itemsOf(Value series) {
        return switch (series) {
            case BlockValue block -> block.remaining();
            case StringValue text -> text.text().codePoints()
                    .mapToObj(codepoint -> (Value) CharacterValue.of(codepoint))
                    .toList();
            // An object walks as its words and their values, so FOREACH
            // can inspect one without asking for WORDS-OF first. SELF is
            // left out, or every walk would reach the object again.
            case ObjectValue object -> object.context().slots().stream()
                    .filter(slot -> !slot.canonical().equals("self"))
                    .<Value>mapMulti((slot, accept) -> {
                        accept.accept(WordValue.of(slot.spelling()));
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
        String text = bytes.asText();
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
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
                (arguments, evaluator, context, refinements) -> {
                    long value = ((IntegerValue) arguments.get(0)).magnitude();
                    long places = ((IntegerValue) arguments.get(1)).magnitude();
                    if (!refinements.contains("logical")) {
                        return IntegerValue.of(
                                places >= 0 ? value << places : value >> -places);
                    }
                    if (Math.abs(places) >= Long.SIZE) {
                        return IntegerValue.of(0);
                    }
                    return IntegerValue.of(
                            places >= 0 ? value << places : value >>> -places);
                });

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
        define("type?", takes("value"), Set.of("word"),
                (arguments, evaluator, context, refinements) -> refinements.contains("word")
                        ? WordValue.of(arguments.get(0).datatype().literalSpelling())
                        : DatatypeValue.of(arguments.get(0).datatype()));
        define("unset?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.UNSET));
        define("none?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.NONE));
        define("error?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.ERROR));
        // Every datatype gets its own predicate, rather than a handful
        // being written out. The hand-written set was missing pair? and
        // decimal? and would have gone on missing whichever datatype was
        // added last, because nothing connected the two lists.
        for (Datatype datatype : Datatype.values()) {
            Datatype asked = datatype;
            define(datatype.spelling() + "?", takes("value"),
                    (arguments, evaluator, context) -> LogicValue.of(
                            arguments.get(0).datatype() == asked));
        }
        // And one per typeset, for the same reason: a family has a
        // question too. SERIES? was missing and Rebol's own REJOIN
        // branches on it, so that one absence stopped a borrowed
        // function running at all.
        for (Typeset typeset : Typeset.values()) {
            Typeset asked = typeset;
            define(typeset.spelling() + "?", takes("value"),
                    (arguments, evaluator, context) -> LogicValue.of(
                            asked.members().contains(arguments.get(0).datatype())));
        }
        // TRUE? and DID ask the one question every conditional asks:
        // only NONE and logic FALSE are false, so zero, the empty string
        // and the empty block are all true.
        define("true?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(arguments.get(0).isTruthy()));
        define("did", takes("value"),
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
        defineCodepointRange("utf?", Character.MAX_CODE_POINT);

        // INVALID-UTF? answers where the trouble is rather than whether
        // there is any, so none means well-formed.
        define("invalid-utf?", List.of(Parameter.required("data", Set.of(Datatype.BINARY))),
                (arguments, evaluator, context) -> {
                    BinaryValue bytes = (BinaryValue) arguments.get(0);
                    for (int at = bytes.index(); at <= bytes.storageLength(); at++) {
                        if ((bytes.storage().at(at) & 0xFF) >= 0xF8) {
                            return bytes.atIndex(at);
                        }
                    }
                    return NoneValue.none();
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

        define("unset", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    slotOf((WordValue) arguments.get(0)).setValue(UnsetValue.unset());
                    return UnsetValue.unset();
                });

        // PROTECT takes an object as well as a word, and protects every
        // field it holds. Protecting the word that holds the object and
        // protecting the object are different things, and it is the second
        // that stops `o/a: 1`.
        define("protect", List.of(Parameter.required("target")),
                Set.of("deep", "words", "values", "hide"),
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

        define("unprotect", List.of(Parameter.required("target")),
                Set.of("deep", "words", "values", "hide"),
                (arguments, evaluator, context, refinements) -> {
                    // /HIDE conceals a field rather than locking it, and
                    // the two are separate: a hidden field is not locked
                    // and a locked one is not hidden.
                    if (refinements.contains("hide")
                            && arguments.getFirst() instanceof WordValue named) {
                        slotOf(named).hide(false);
                        return arguments.getFirst();
                    }
                    if (!protectFieldNamedBy(arguments.getFirst(), false, refinements)) {
                        protectNamed(arguments.getFirst(), false, refinements);
                        setProtection(arguments.get(0), false, refinements.contains("deep"),
                                refinements.contains("words"));
                    }
                    return arguments.getFirst();
                });

        // Several words at once take a block of values one for one, and
        // anything else goes to every word. Too few values pads with none
        // rather than failing; too many leaves the extras unused.
        //
        // /ONLY turns the spreading off, so each word gets the whole
        // block. /SOME leaves a word holding what it held where the value
        // would have been none, which is for filling defaults in from a
        // partly populated block.
        define("set", List.of(Parameter.required("target"), Parameter.required("value")),
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
                    List<Value> values = !refinements.contains("only")
                            && supplied instanceof BlockValue block
                            ? block.remaining()
                            : null;
                    for (int index = 0; index < names.size(); index++) {
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
                        return refinements.contains("deep")
                                ? copied(taken, taken instanceof BlockValue)
                                : taken;
                    }
                    if (arguments.size() > 1 && arguments.get(1) instanceof SeriesValue upTo) {
                        return takeSeveral(earlierOf(series, upTo),
                                Math.abs(upTo.index() - series.index()));
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
                        return takeSeveral(series.atIndex((int) from), wanted);
                    }
                    return takeSeveral(series, wanted);
                });

        // AJOIN drops NONE by default, which is the behaviour /all exists
        // to undo. /with puts a separator between the pieces -- between,
        // not after, so three pieces give two separators.
        define("ajoin", List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("with", "separator", Set.of())),
                Set.of("all", "with"),
                (arguments, evaluator, context, refinements) -> {
                    List<Value> pieces = evaluator.evaluateEachOrRaise(
                                    (BlockValue) arguments.get(0), context).stream()
                            .filter(piece -> refinements.contains("all")
                                    || !(piece instanceof NoneValue))
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
        define("poke", List.of(Parameter.required("series",
                                Set.of(Datatype.BLOCK, Datatype.PAREN, Datatype.HASH,
                                        Datatype.STRING, Datatype.FILE, Datatype.URL,
                                        Datatype.TAG, Datatype.EMAIL, Datatype.BINARY,
                                        Datatype.MAP, Datatype.BITSET)),
                        Parameter.required("index", Set.of(Datatype.INTEGER)),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    long at = ((IntegerValue) arguments.get(1)).magnitude();
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
                    if (arguments.get(0) instanceof StringValue text
                            && arguments.get(2) instanceof CharacterValue letter) {
                        text.storage().set(text.index() + (int) at - 1, letter.codepoint());
                        return arguments.get(2);
                    }
                    if (arguments.get(0) instanceof BinaryValue bytes
                            && arguments.get(2) instanceof IntegerValue octet) {
                        bytes.storage().set(
                                bytes.index() + (int) at - 1, (int) octet.magnitude());
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
                        Parameter.required("first", Set.of(Datatype.BLOCK)),
                        Parameter.required("second", Set.of(Datatype.BLOCK)),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER))),
                Set.of("case", "skip"),
                (arguments, evaluator, context, refinements) -> {
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
                    // A map answers WORDS and VALUES the way an object
                    // does, which is what KEYS-OF and VALUES-OF need now
                    // they are written in REBOL on top of this.
                    if (arguments.get(0) instanceof MapValue map) {
                        return switch (field) {
                            case "words" -> BlockValue.block(map.keys());
                            case "values" -> BlockValue.block(map.values());
                            default -> NoneValue.none();
                        };
                    }
                    if (arguments.get(0) instanceof FunctionValue written) {
                        return switch (field) {
                            case "spec" -> written.spec();
                            case "body" -> written.body();
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
                        // object again, which is the point of it.
                        case "body" -> BlockValue.block(
                                object.context().slots().stream()
                                        .filter(slot -> !slot.canonical().equals("self"))
                                        .flatMap(slot -> java.util.stream.Stream.of(
                                                WordValue.of(slot.spelling(),
                                                        Datatype.SET_WORD),
                                                slot.value()))
                                        .toList());
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
                        Parameter.required("key"), Parameter.required("value")),
                Set.of("case"),
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
                            object.context().set(field.canonical(), arguments.get(2));
                        }
                        case BlockValue block -> {
                            List<Value> items = block.remaining();
                            // /CASE matches the key exactly, so a block
                            // holding both "A" and "a" has two entries to
                            // it and one to a plain PUT.
                            boolean mindingCase = refinements.contains("case");
                            for (int at = 0; at + 1 < items.size(); at += 2) {
                                if (mindingCase
                                        ? Comparison.identicallyEqual(items.get(at), arguments.get(1))
                                        : Comparison.looselyEqual(items.get(at), arguments.get(1))) {
                                    block.storage().set(
                                            block.index() + at + 1, arguments.get(2));
                                    break;
                                }
                            }
                        }
                        default -> raiseCannotUse(arguments.get(0), "put");
                    }
                    return arguments.get(2);
                });

        define("select", List.of(Parameter.required("series"), Parameter.required("value"),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("with", "wildcards", Set.of(Datatype.STRING))),
                Set.of("case", "skip", "any", "only", "last", "part", "same", "with"),
                (arguments, evaluator, context, refinements) -> {
                    // A map is asked about keys it may not have, so a miss
                    // is NONE rather than an error, exactly as it is here
                    // for a block that does not hold the value.
                    if (arguments.get(0) instanceof MapValue map) {
                        return map.select(arguments.get(1));
                    }
                    // A string is searched for a run of characters and
                    // answers the one after the run, the same shape of
                    // answer a block gives.
                    if (arguments.get(0) instanceof StringValue text) {
                        Value given = argumentFor(
                                "with", List.of("skip", "with"), arguments, refinements, 2);
                        return selectedFromText(text, arguments.get(1), refinements,
                                given instanceof StringValue chosen ? chosen.text() : null);
                    }
                    if (arguments.get(0) instanceof NoneValue) {
                        return NoneValue.none();
                    }
                    // An error is an object with a fixed set of fields, so
                    // the things that read an object's fields read one of
                    // these too.
                    if (arguments.getFirst() instanceof ErrorValue raised
                            && arguments.get(1) instanceof WordValue field) {
                        return raised.field(field.canonical()).orElseGet(NoneValue::none);
                    }
                    refuseUnbyteableNeedle(arguments.getFirst(), arguments.get(1), "select");
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseCannotUse(arguments.get(0), "select");
                    }
                    List<Value> items = block.remaining();
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
                    int stride = refinements.contains("skip") && arguments.size() > 2
                            ? (int) ((IntegerValue) arguments.get(2)).magnitude()
                            : 1;
                    // A record width is a count of elements, so it starts
                    // at one. Zero is the dangerous one: it reads as "do
                    // not move", and a search that does not move does not
                    // end. This loop ran forever on it, past the wall
                    // clock bound, because nothing inside a native's own
                    // loop asks whether the script should still be
                    // running.
                    if (stride < 1) {
                        throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                "select/skip needs a record width of at least "
                                        + "one, not " + stride);
                    }
                    // /SAME asks for the very same value rather than an
                    // equal one, so 1, 1.0 and 100% are three keys to it
                    // and one key to everything else.
                    boolean mindingCase = refinements.contains("case");
                    boolean mindingIdentity = refinements.contains("same");
                    for (int index = 0; index < items.size(); index += stride) {
                        boolean matches;
                        if (mindingIdentity) {
                            matches = Comparison.identicallyEqual(items.get(index), arguments.get(1));
                        } else if (mindingCase) {
                            matches = items.get(index).equals(arguments.get(1));
                        } else {
                            matches = Comparison.looselyEqual(items.get(index), arguments.get(1));
                        }
                        if (matches) {
                            // A match with nothing after it has no answer,
                            // which is none rather than a failure: SELECT
                            // is asked a question it is allowed to miss.
                            return index + 1 < items.size()
                                    ? items.get(index + 1)
                                    : NoneValue.none();
                        }
                    }
                    return NoneValue.none();
                });

        // /ANY answers the unset rather than refusing, which is how code
        // asks whether a word has a value without having to catch an
        // error to find out.
        define("get", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                Set.of("any"),
                (arguments, evaluator, context, refinements) -> {
                    Value held = slotOf((WordValue) arguments.get(0)).value();
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
            Value held = asWritten ? item : namedConstant(item);
            for (WordValue name : waiting) {
                built.set(name.spelling(), held);
            }
            waiting.clear();
        }
        // A field that was named and never given a value still exists.
        for (WordValue name : waiting) {
            if (!built.knows(name.canonical())) {
                built.define(name.spelling());
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
                case "type" -> category = ErrorCategory.named(said).orElse(category);
                case "id" -> errorId = said;
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
        return forward > at && forward < text.length() && text.charAt(forward) == '[';
    }

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

    /** A binary with its zero bytes dropped, from whichever end was asked for. */
    private static Value trimmedBinary(BinaryValue bytes, Set<String> refinements) {
        List<Integer> kept = new ArrayList<>();
        for (int at = 0; at < bytes.lengthFromHere(); at++) {
            kept.add(bytes.storage().at(bytes.index() + at));
        }
        if (refinements.contains("all")) {
            kept.removeIf(octet -> octet == 0);
        } else {
            boolean fromHead = !refinements.contains("tail");
            boolean fromTail = !refinements.contains("head");
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
        define("pick", List.of(Parameter.required("series"),
                        Parameter.required("index",
                                Set.of(Datatype.INTEGER, Datatype.LOGIC))),
                (arguments, evaluator, context) -> pick(arguments.get(0),
                        arguments.get(1) instanceof LogicValue chosen
                                ? (chosen.truth() ? 1 : 2)
                                : (int) ((IntegerValue) arguments.get(1)).magnitude()));

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
        define("indexz?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? IntegerValue.of(series.index() - 1)
                        : raiseWrongArgument(arguments.get(0), "indexz?", "series"));
        define("pickz", List.of(Parameter.required("series"),
                        Parameter.required("index", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> pick(arguments.get(0),
                        (int) ((IntegerValue) arguments.get(1)).magnitude() + 1));

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
        define("index?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
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
        define("append", List.of(Parameter.required("series"), Parameter.required("value"),
                        Parameter.belongingTo("part", "count", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("dup", "times", Set.of(Datatype.INTEGER))),
                Set.of("part", "only", "dup"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case BlockValue block -> {
                        if (duplicated(arguments.get(1), arguments, refinements)
                                instanceof BlockValue added) {
                            firstFew(added.remaining(), arguments, refinements, 2)
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
                    // An object is not a series either, so APPEND on one
                    // means "extend it". The block is read as set-word
                    // and value pairs, and the set-word may have been
                    // computed -- which is how an object gains a field
                    // whose name is not in the source.
                    case ObjectValue object -> {
                        // Changing an object as a container, which REBOL
                        // refuses as `protected` rather than as the
                        // `locked-word` an assignment through a name gets.
                        // Checked here rather than left to the slot,
                        // because only this end knows which was asked.
                        if (object.context().isClosedToNewNames()) {
                            throw Raised.of(EvaluationFailure.PROTECTED, "append");
                        }
                        // /PART and /DUP mean here what they mean for a
                        // block: how much of the source to read, and how
                        // many times over. Ignored, `append/part obj [a 1
                        // b 2] 2` added both pairs and looked right.
                        refuseHiddenField(object, arguments.get(1));
                        List<Value> pairs = duplicated(
                                arguments.get(1), arguments, refinements)
                                instanceof BlockValue added
                                ? partOf(added, arguments, refinements)
                                : List.of(arguments.get(1));
                        for (int at = 0; at + 1 < pairs.size(); at += 2) {
                            if (pairs.get(at) instanceof WordValue field) {
                                object.context().set(field.canonical(), pairs.get(at + 1));
                            }
                        }
                        yield object;
                    }
                    // A bitset gains members rather than gaining a tail,
                    // because it has no end to put anything at.
                    case BitsetValue members -> {
                        members.addAll((BitsetValue) bitsetOf(arguments.get(1)));
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
                        int wanted = howManyWanted(arguments, refinements, 2)
                                .map(count -> Math.min(count.intValue(), text.length()))
                                .orElse(text.length());
                        text.substring(0, wanted).codePoints()
                                .forEach(string.storage()::append);
                        yield string.head();
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
                        Parameter.belongingTo("part", "count", Set.of(Datatype.INTEGER))),
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
                        Parameter.required("offset", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "skip");
                    }
                    long by = ((IntegerValue) arguments.get(1)).magnitude();
                    return series.atIndex(clampToSeries(series, series.index() + by));
                });

        define("at", List.of(
                        Parameter.required("series"),
                        Parameter.required("index", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "at");
                    }
                    long wanted = ((IntegerValue) arguments.get(1)).magnitude();
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
        define("copy", List.of(Parameter.required("value"),
                        Parameter.belongingTo("part", "limit", Set.of())),
                Set.of("part", "deep"),
                (arguments, evaluator, context, refinements) -> {
                    Value original = arguments.getFirst();
                    boolean deeply = refinements.contains("deep");
                    if (!refinements.contains("part")) {
                        return copied(original, deeply);
                    }
                    if (!(original instanceof SeriesValue series)) {
                        return raiseCannotUse(original, "copy");
                    }
                    Value limit = argumentFor("part", List.of("part"), arguments, refinements, 1);
                    return copiedFront(series, limit, deeply);
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
                List.of(Parameter.required("series"), Parameter.required("value"),
                        Parameter.belongingTo("part", "limit", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("skip", "stride", Set.of(Datatype.INTEGER))),
                Set.of("tail", "last", "only", "case", "any", "same", "part",
                        "skip", "reverse", "match"),
                (arguments, evaluator, context, refinements) -> {
                    // Looking in nothing finds nothing, so a result can be
                    // passed straight on without being tested first.
                    if (arguments.get(0) instanceof NoneValue) {
                        return NoneValue.none();
                    }
                    // An error is an object with a fixed set of fields, so
                    // asking whether it has one is an ordinary question,
                    // and the answer is a logic rather than a position.
                    if (arguments.getFirst() instanceof ErrorValue
                            && arguments.get(1) instanceof WordValue field) {
                        return LogicValue.of(ErrorValue.FIELDS.contains(field.canonical()));
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
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "find");
                    }
                    int limit = refinements.contains("part") && arguments.size() > 2
                            ? (int) ((IntegerValue) arguments.get(2)).magnitude()
                            : Integer.MAX_VALUE;
                    // /skip makes the series records of that width and
                    // looks only at the first item of each, so a match
                    // halfway through a record is not a match at all.
                    long stride = refinements.contains("skip")
                            ? ((IntegerValue) arguments.get(arguments.size() - 1)).magnitude()
                            : 1;
                    // An error is an object with a fixed set of fields, so
                    // asking whether it has one is an ordinary question.
                    if (arguments.getFirst() instanceof ErrorValue raised
                            && arguments.get(1) instanceof WordValue field) {
                        return LogicValue.of(ErrorValue.FIELDS.contains(field.canonical()));
                    }
                    // A byte holds 0 to 255, so searching a binary for
                    // anything outside that is a mistake in the caller
                    // rather than a search that missed. Answering none
                    // would hide it.
                    refuseUnbyteableNeedle(arguments.getFirst(), arguments.get(1), "find");
                    // A record width below one is refused rather than read
                    // as counting backwards. Confirmed against a real R3:
                    // find/skip with 0 and with -2 both raise out-of-range.
                    //
                    // Unless the search is already going backwards, where
                    // a negative width is no contradiction: find/reverse
                    // /skip accepts -2 and 2 alike, and answers none for
                    // a width of zero rather than raising.
                    if (refinements.contains("skip") && stride < 1
                            && !refinements.contains("reverse")) {
                        throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                                "find/skip needs a record width of at least "
                                        + "one, not " + stride);
                    }
                    int found = stride == 1 || stride == 0
                            ? positionOfMatch(series, arguments.get(1), refinements, limit)
                            : positionOfMatchInRecords(
                                    series, arguments.get(1), refinements, (int) stride);
                    // /match insists the needle be at the position rather
                    // than anywhere ahead of it, and answers the series
                    // from there so what follows can be read off.
                    if (found < 0 || (refinements.contains("match") && found != series.index())) {
                        return NoneValue.none();
                    }
                    // ret += len in the C: /TAIL steps over the whole
                    // needle, thus a run of two steps over two.
                    return series.atIndex(refinements.contains("tail")
                            ? found + matchLength(series, arguments.get(1), refinements, found)
                            : found);
                });

        // A block goes in item by item unless /only says to keep it
        // whole, which is the same way APPEND behaves.
        define("insert", List.of(Parameter.required("series"), Parameter.required("value"),
                        Parameter.belongingTo("part", "count", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("dup", "times", Set.of(Datatype.INTEGER))),
                Set.of("only", "part", "dup"),
                (arguments, evaluator, context, refinements) -> switch (arguments.get(0)) {
                    case BlockValue block -> {
                        if (duplicated(arguments.get(1), arguments, refinements)
                                instanceof BlockValue added
                                && added.datatype() == Datatype.BLOCK
                                && !refinements.contains("only")) {
                            List<Value> items = added.remaining();
                            for (int at = items.size(); at > 0; at--) {
                                block.storage().insertAt(block.index(), items.get(at - 1));
                            }
                            yield block.atIndex(block.index() + items.size());
                        }
                        block.storage().insertAt(block.index(), arguments.get(1));
                        yield block.atIndex(block.index() + 1);
                    }
                    case StringValue text -> {
                        String added = Molder.form(arguments.get(1));
                        for (int at = 0; at < added.length(); at++) {
                            text.storage().insertAt(text.index() + at, added.charAt(at));
                        }
                        yield text.atIndex(text.index() + added.length());
                    }
                    case BinaryValue bytes -> {
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
                    default -> raiseCannotUse(arguments.get(0), "insert");
                });

        define("remove", List.of(Parameter.required("series"),
                        Parameter.belongingTo("part", "count", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("key", "which", Set.of())),
                Set.of("part", "key"),
                (arguments, evaluator, context, refinements) -> {
                    if (arguments.get(0) instanceof MapValue map && refinements.contains("key")) {
                        map.remove(arguments.get(1));
                        return map;
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
                    long howMany = howManyWanted(arguments, refinements, 1).orElse(1L);
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
        define("change", List.of(Parameter.required("series"), Parameter.required("value"),
                        Parameter.belongingTo("part", "count", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("dup", "times", Set.of(Datatype.INTEGER))),
                Set.of("part", "only", "dup"),
                (arguments, evaluator, context, refinements) -> {
                    if (refinements.contains("part") && arguments.size() > 2
                            && arguments.get(0) instanceof SeriesValue series) {
                        long taking = ((IntegerValue) arguments.get(2)).magnitude();
                        for (long gone = 0; gone < taking && !series.atTail(); gone++) {
                            removeOneAt(series, series.index());
                        }
                        return insertInto(series, arguments.get(1));
                    }
                    Value replacing = duplicated(
                            arguments.get(1), arguments, refinements);
                    if (arguments.get(0) instanceof StringValue text && !text.atTail()) {
                        String replacement = replacing instanceof BlockValue several
                                ? runTogether(several)
                                : Molder.form(replacing);
                        for (int at = 0; at < replacement.length() && !text.atTail(); at++) {
                            text.storage().set(text.index() + at, replacement.charAt(at));
                        }
                        return text.head();
                    }
                    if (!(arguments.get(0) instanceof BlockValue block) || block.atTail()) {
                        return raiseCannotUse(arguments.get(0), "change");
                    }
                    // /DUP replaces that many elements rather than one, so
                    // `change/dup [1 2 3 4] 9 3` is [9 9 9 4].
                    List<Value> replacements = replacing instanceof BlockValue several
                            && refinements.contains("dup")
                            ? several.remaining()
                            : List.of(arguments.get(1));
                    for (int at = 0; at < replacements.size()
                            && block.index() + at <= block.storageLength(); at++) {
                        block.storage().set(block.index() + at, replacements.get(at));
                    }
                    return block.atIndex(block.index() + replacements.size());
                });

        // CLEAR empties from here to the tail, not the whole series, which is
        // why clearing the second position keeps the first value.
        define("clear", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
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
                    default -> raiseCannotUse(arguments.get(0), "clear");
                });

        // SORT folds case and is stable: equal keys keep the order they
        // arrived in, which is what makes a second sort on another key a
        // usable way to sort on two. /case compares exactly, /compare takes
        // a function of two values, and /skip sorts records by their first
        // item so a flat block of pairs stays paired.
        define("sort",
                List.of(Parameter.required("series"),
                        Parameter.belongingTo("skip", "size", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("compare", "comparator", Set.of()),
                        Parameter.belongingTo("part", "count", Set.of(Datatype.INTEGER))),
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
                        Parameter.belongingTo("into", "target", Set.of(Datatype.BLOCK)),
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
                        Parameter.belongingTo("into", "out", Set.of(Datatype.BLOCK))),
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
                        List<Value> pairs = new ArrayList<>();
                        List<Value> keys = template.keys();
                        List<Value> held = template.values();
                        for (int at = 0; at < keys.size(); at++) {
                            pairs.add(keys.get(at));
                            pairs.add(held.get(at));
                        }
                        return MapValue.of(composed(
                                BlockValue.block(pairs), evaluator,
                                true, refinements.contains("deep")));
                    }
                    List<Value> built =
                            arguments.getFirst() instanceof BlockValue template
                                    ? composed(template, evaluator,
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
                        Parameter.belongingTo("part", "length", Set.of(Datatype.INTEGER))),
                Set.of("one", "error", "next", "part", "line", "only"),
                (arguments, evaluator, context, refinements) -> {
                    String whole = textOfSource(arguments.get(0));
                    String source = whole;
                    // /part bounds the text rather than the values, so
                    // three characters of "1 23]" is "1 2" and reads as
                    // two integers.
                    //
                    // It bounds only what is read. What /NEXT hands back
                    // as unread is the rest of the whole source, not the
                    // rest of the bounded piece -- otherwise a caller
                    // walking a source with a bound would find it empty
                    // after one step.
                    if (refinements.contains("part") && arguments.size() > 1) {
                        int howMany = (int) ((IntegerValue) arguments.get(1)).magnitude();
                        source = whole.substring(0, Math.min(whole.length(), howMany));
                    }
                    // /next answers the first value and the text still
                    // unread, so a caller can walk a source a value at a
                    // time without counting characters itself.
                    // Asking for a value where there is none left is a
                    // failure rather than an answer of none, because none
                    // is a value a source can genuinely hold. Both /NEXT
                    // and /ONE ask for one.
                    if (source.isBlank()
                            && (refinements.contains("next") || refinements.contains("one"))) {
                        throw new Raised(ErrorValue.of(ErrorCategory.SYNTAX,
                                SyntaxFailure.PAST_END.errorId(),
                                SyntaxFailure.PAST_END.description()));
                    }
                    // /next answers the first value and the text still
                    // unread, and the unread part comes back as the kind
                    // that went in: reading a binary leaves a binary.
                    if (refinements.contains("next")) {
                        return firstValueAndRest(source, whole,
                                arguments.getFirst() instanceof BinaryValue);
                    }
                    // /error hands the failure back as a value instead of
                    // raising it: TRY built into the reader, for a caller
                    // reading text they did not write who wants to look at
                    // what went wrong rather than catch it.
                    Value read;
                    try {
                        read = transcodedText(source);
                    } catch (Raised refused) {
                        if (!refinements.contains("error")) {
                            throw refused;
                        }
                        return refinements.contains("one")
                                ? refused.error()
                                : BlockValue.block(List.of(refused.error()));
                    }
                    return refinements.contains("one") && read instanceof BlockValue block
                            ? (block.atTail() ? NoneValue.none() : block.first())
                            : read;
                });

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
            BlockValue template, Evaluator evaluator,
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
                            nested, evaluator, keepingBlocksWhole, true)));
                    continue;
                }
                built.add(item);
                continue;
            }
            for (Value produced : evaluator.evaluateEachOrRaise(
                    paren.as(Datatype.BLOCK), evaluator.systemContext())) {
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
     * Source text read into values, with no binding.
     *
     * <p>Takes a string or a binary, because a script that has read a file
     * has a binary and a script that built the text has a string.
     */
    private static String textOfSource(Value source) {
        return switch (source) {
            case StringValue given -> given.text();
            case BinaryValue given -> given.asText();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "transcode reads text, not " + source.datatype().literalSpelling());
        };
    }

    private static Value transcodedText(String text) {
        TranscodeResult read = Transcoder.transcode(text);
        return read.values().orElseThrow(() -> new Raised(read.error().orElseThrow()));
    }

    /**
     * The first value of a source and whatever is left after it.
     *
     * <p>Found by reading ever-longer prefixes, because the reader
     * reports what it read and not where it stopped. Exposing a position
     * would be the better answer and is a larger change than this
     * refinement justifies on its own.
     *
     * <p>The longest run of text that still reads as one value, not the
     * shortest. Stopping at the shortest reads "12" as the 1 and leaves
     * the 2, because "1" is already a whole value on its own -- a value
     * has to be taken as far as it goes.
     *
     * <p>Trailing whitespace is then given back, because it belongs to
     * what follows rather than to the value: reading "1 23]" leaves
     * " 23]" and not "23]".
     *
     * <p>{@code text} may be shorter than {@code whole} when /PART bounded
     * the reading. The bound says how much may be read; what is left over
     * is still the rest of the whole source.
     */
    private static Value firstValueAndRest(String text, String whole, boolean asBytes) {
        int taken = -1;
        Value first = NoneValue.none();
        for (int upTo = 1; upTo <= text.length(); upTo++) {
            TranscodeResult read = Transcoder.transcode(text.substring(0, upTo));
            if (!read.succeeded()) {
                continue;
            }
            BlockValue values = read.values().orElseThrow();
            if (values.remaining().size() > 1) {
                break;
            }
            if (values.remaining().size() == 1) {
                taken = upTo;
                first = values.first();
            }
        }
        if (taken < 0) {
            return BlockValue.block(List.of(NoneValue.none(), remainderOf(whole, asBytes)));
        }
        while (taken > 1 && Character.isWhitespace(text.charAt(taken - 1))) {
            taken--;
        }
        return BlockValue.block(List.of(
                first, remainderOf(whole.substring(taken), asBytes)));
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
            SeriesValue series, Value wanted, Set<String> refinements, int limit) {

        boolean lookingBehind = refinements.contains("reverse");
        boolean takingTheLast = refinements.contains("last");
        List<Value> items = itemsOf(series.head());
        int here = series.index() - 1;
        int needleWidth = widthOfNeedle(series, wanted, refinements);

        // The C works in [start, end) with a step. Everything below is
        // that, one for one.
        int end = limit < 0
                ? items.size()
                : (int) Math.min(items.size(), (long) here + limit);
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
            if (matchesHere(series, items, at, wanted, refinements)) {
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
            Set<String> refinements) {

        if (series instanceof StringValue text && refinements.contains("any")) {
            return patternEnd(text.head().text(), at, Molder.form(wanted),
                    refinements.contains("case")) >= 0;
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
            boolean same = mindingIdentity
                    ? Comparison.identicallyEqual(items.get(at + step), run.get(step))
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
            SeriesValue series, Value wanted, Set<String> refinements, int stride) {
        boolean backwards = stride < 0 || refinements.contains("reverse")
                || refinements.contains("last");
        int width = Math.abs(stride);
        List<Value> items = itemsOf(series.head());
        // Backwards starts at the item just before the position, so a
        // search from the tail begins on the last item rather than a
        // record's worth past it.
        int from = backwards ? series.index() - 2 : series.index() - 1;

        for (int at = from; at >= 0 && at < items.size(); at += backwards ? -width : width) {
            if (matchesAtRecord(items, at, wanted, refinements)) {
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
            List<Value> items, int at, Value wanted, Set<String> refinements) {
        if (wanted instanceof BitsetValue members) {
            return items.get(at) instanceof CharacterValue character
                    && members.holds(character.codepoint());
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
     * Whether a wildcard pattern matches the text from {@code from}.
     *
     * <p>A star stands for any run of characters including none, and a
     * question mark for exactly one. Matched by walking both and letting
     * a star try every length, which is enough for patterns this short and
     * avoids turning them into regular expressions whose other characters
     * would then mean something.
     */
    private static boolean matchesPattern(
            String within, int from, String pattern, boolean mindingCase) {
        return patternEnd(within, from, pattern, mindingCase) >= 0;
    }

    /**
     * Where a wildcard match ends, or -1 if it does not match.
     *
     * <p>Needed as well as whether it matched, because how much a wildcard
     * took varies: /tail cannot land a fixed distance along the way it can
     * for a plain needle. A star tries its shortest length first, so the
     * end reported is the earliest one that works.
     */
    private static int patternEnd(
            String within, int from, String pattern, boolean mindingCase) {
        if (pattern.isEmpty()) {
            return from;
        }
        char first = pattern.charAt(0);
        if (first == '*') {
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
                return within.length();
            }
            for (int taken = from; taken <= within.length(); taken++) {
                int end = patternEnd(within, taken, pattern.substring(1), mindingCase);
                if (end >= 0) {
                    return end;
                }
            }
            return -1;
        }
        if (from >= within.length()) {
            return -1;
        }
        if (first != '?' && !sameCharacter(within.charAt(from), first, mindingCase)) {
            return -1;
        }
        return patternEnd(within, from + 1, pattern.substring(1), mindingCase);
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
        // Minding case means minding the datatype too, so 1, 1.0 and 100%
        // stop being one another. Comparing with equals() alone left them
        // equal, because a decimal and a percent hold the same number.
        return mindingCase ? Comparison.identicallyEqual(item, wanted) : Comparison.looselyEqual(item, wanted);
    }

    /** How far past the match /tail lands, which a substring makes more than one. */
    private static int matchLength(
            SeriesValue series, Value wanted, Set<String> refinements, int found) {
        if (series instanceof StringValue patterned && refinements.contains("any")) {
            // Measured from the head in absolute terms, because under
            // /REVERSE the match sits BEHIND the position and an offset
            // measured from the position is negative.
            String within = patterned.head().text();
            int from = found - 1;
            int end = patternEnd(within, from,
                    Molder.form(wanted), refinements.contains("case"));
            return end < 0 ? 1 : end - from;
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
                            left, right, comparator, mindingCase, wholeRecord, evaluator);
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
            Value comparator, boolean mindingCase, boolean wholeRecord, Evaluator evaluator) {

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
                ? askComparator(comparator, lentRecord(left), lentRecord(right), evaluator)
                : askComparator(comparator, left.getFirst(), right.getFirst(), evaluator);
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
        };
    }

    private static void removeFrom(SeriesValue series, int oneBasedIndex, int howMany) {
        for (int removed = 0; removed < howMany; removed++) {
            switch (series) {
                case BlockValue block -> block.storage().removeAt(oneBasedIndex);
                case StringValue text -> text.storage().removeAt(oneBasedIndex);
                case BinaryValue bytes -> bytes.storage().removeAt(oneBasedIndex);
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
            case BinaryValue octets -> BitsetValue.of(octets.asText()
                    .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            case BlockValue members -> BitsetValue.ofCharacters(codePointsIn(members));
            default -> raiseCannotUse(source, "make bitset!");
        };
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
            case FunctionValue function -> function.parameters().stream()
                    .filter(Parameter::consumesAnArgument)
                    .filter(parameter -> parameter.owningRefinement().isEmpty())
                    .count();
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

    /** Putting a value in at the position, whichever kind of series. */
    private static Value insertInto(SeriesValue series, Value value) {
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
            SeriesValue series, List<Value> arguments, Evaluator evaluator) {

        // Refused up front rather than on the first removal, so a series
        // whose body happens to match nothing still says no. A guard that
        // only fires when something changes is not a guard.
        refuseIfProtected(series);
        Context locals = Context.childOf(evaluator.systemContext());
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

    /** Refuses a change to a protected series before attempting it. */
    private static void refuseIfProtected(SeriesValue series) {
        boolean guarded = switch (series) {
            case BlockValue block -> block.storage().isProtected();
            case StringValue text -> text.storage().isProtected();
            case BinaryValue bytes -> bytes.storage().isProtected();
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
        if (!(times instanceof IntegerValue count)) {
            return value;
        }
        List<Value> pieces = value instanceof BlockValue block
                && block.datatype() == Datatype.BLOCK
                ? block.remaining()
                : List.of(value);
        List<Value> repeated = new ArrayList<>();
        for (long round = 0; round < count.magnitude(); round++) {
            repeated.addAll(pieces);
        }
        return BlockValue.block(repeated);
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
        int indent = 0;
        for (String line : lines) {
            if (!line.isBlank()) {
                indent = line.length() - line.stripLeading().length();
                break;
            }
        }
        StringBuilder trimmed = new StringBuilder();
        for (int at = 0; at < lines.length; at++) {
            String line = lines[at];
            int take = Math.min(indent, line.length() - line.stripLeading().length());
            trimmed.append(line.substring(take));
            if (at + 1 < lines.length) {
                trimmed.append('\n');
            }
        }
        return trimmed.toString();
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
            Datatype.TAG, Datatype.REF, Datatype.BINARY, Datatype.ISSUE,
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH, Datatype.SET_PATH,
            Datatype.GET_PATH, Datatype.LIT_PATH, Datatype.HASH,
            Datatype.OBJECT, Datatype.PORT, Datatype.BITSET,
            Datatype.TYPESET, Datatype.MAP, Datatype.GOB);

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
        return switch (original) {
            case BlockValue block -> new BlockValue(new BlockStorage(
                    deeply
                            ? block.remaining().stream()
                                    .map(item -> copied(item, true)).toList()
                            : block.remaining()),
                    1, block.datatype());
            case StringValue text -> StringValue.of(text.text(), text.datatype());
            case BinaryValue binary -> copiedBytes(binary, binary.lengthFromHere());
            default -> original;
        };
    }

    /** The first few of a series, copied, and deeply when asked. */
    private static Value copiedFront(SeriesValue series, Value limit, boolean deeply) {
        long wanted = countUpTo(series, limit);
        SeriesValue from = limit instanceof SeriesValue upTo
                ? earlierOf(series, upTo)
                : series;
        int taking = (int) Math.max(0, Math.min(wanted, from.lengthFromHere()));
        return switch (from) {
            case BlockValue block -> new BlockValue(new BlockStorage(
                    block.remaining().subList(0, taking).stream()
                            .map(item -> deeply ? copied(item, true) : item).toList()),
                    1, block.datatype());
            case StringValue text -> StringValue.of(
                    text.text().substring(0, taking), text.datatype());
            case BinaryValue bytes -> copiedBytes(bytes, taking);
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

    /** Where a literal needle sits, as a start and an end, or null. */
    private static int[] plainSpan(String haystack, String needle, boolean fromTheEnd) {
        int found = fromTheEnd ? haystack.lastIndexOf(needle) : haystack.indexOf(needle);
        return found < 0 ? null : new int[] {found, found + needle.length()};
    }

    /**
     * Where a needle holding ? and * sits, as a start and an end.
     *
     * <p>A question mark stands for one character and a star for any run
     * of them, including none. Built as a regular expression because the
     * shapes are the same idea, with everything else in the needle quoted
     * so a needle full of punctuation cannot become a pattern of its own.
     *
     * <p>The match is not greedy: "*d" against "abcde" ends at the d and
     * not at the end of the text, which is what makes the character after
     * it the e.
     */
    private static int[] spanMatchingShape(
            String haystack, String needle, boolean fromTheEnd, String wildcards) {

        char anyRun = wildcards.isEmpty() ? '*' : wildcards.charAt(0);
        char oneCharacter = wildcards.length() < 2 ? '?' : wildcards.charAt(1);
        StringBuilder shape = new StringBuilder();
        for (int at = 0; at < needle.length(); at++) {
            char letter = needle.charAt(at);
            if (letter == oneCharacter) {
                shape.append('.');
            } else if (letter == anyRun) {
                // A star at the end takes the rest and one in the middle
                // takes as little as it can. `"*d"` against "abcde" ends
                // at the d so the answer is the e; `"c*"` runs to the end
                // so there is nothing after it to answer with. The same
                // rule PARSE's star follows.
                shape.append(at == needle.length() - 1 ? ".*" : ".*?");
            } else {
                shape.append(java.util.regex.Pattern.quote(String.valueOf(letter)));
            }
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile(shape.toString()).matcher(haystack);
        int[] span = null;
        while (matcher.find()) {
            span = new int[] {matcher.start(), matcher.end()};
            if (!fromTheEnd) {
                return span;
            }
        }
        return span;
    }

    /**
     * A string searched for a run of characters, answering the one after it.
     *
     * <p>The same shape of answer a block gives, and a miss is none rather
     * than a failure for the same reason: SELECT is asked a question it is
     * allowed to miss. /SAME and /CASE both stop the case folding, and
     * /LAST searches from the end instead of the front.
     */
    private static Value selectedFromText(
            StringValue text, Value needle, Set<String> refinements, String withCharacters) {

        String haystack = text.text();
        String wanted = needle instanceof CharacterValue character
                ? Character.toString(character.codepoint())
                : Molder.form(needle);
        if (wanted.isEmpty()) {
            return NoneValue.none();
        }
        boolean folding = !refinements.contains("case") && !refinements.contains("same");
        String searched = folding ? haystack.toLowerCase(java.util.Locale.ROOT) : haystack;
        String sought = folding ? wanted.toLowerCase(java.util.Locale.ROOT) : wanted;
        // /ANY reads ? as one character and * as any run of them, so the
        // needle is a shape rather than a literal and the match may be
        // longer or shorter than what was asked for.
        // /WITH names the two wildcard characters itself, the run one
        // first and the single one second, so a needle full of stars can
        // be searched for literally by choosing others.
        String wildcards = refinements.contains("with") && withCharacters != null
                ? withCharacters
                : "*?";
        int[] span = refinements.contains("any")
                ? spanMatchingShape(searched, sought, refinements.contains("last"), wildcards)
                : plainSpan(searched, sought, refinements.contains("last"));
        if (span == null || span[1] >= haystack.length()) {
            return NoneValue.none();
        }
        return CharacterValue.of(haystack.codePointAt(span[1]));
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
        if (!(target instanceof BlockValue named) || !(values || words)) {
            return false;
        }
        for (Value item : named.remaining()) {
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
            if (words) {
                if (protectedNow) {
                    slot.protectFromAssignment();
                } else {
                    slot.allowAssignment();
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
            case ObjectValue object -> {
                // Three separate things, and the refinements pick which
                // an UNPROTECT releases: plain frees the object and its
                // words but not their values, /WORDS frees only the
                // words, /DEEP frees all three, /WORDS/DEEP frees the
                // words and their values but not the object.
                if (protectedNow || !onlyTheWords) {
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
            }
            default -> raiseCannotUse(target, "protect");
        }
    }

    /** Refuses a change to a series that was protected from changing. */
    private static void requireChangeable(Value series) {
        boolean refused = switch (series) {
            case BlockValue block -> block.storage().isProtected();
            case StringValue text -> text.storage().isProtected();
            case BinaryValue bytes -> bytes.storage().isProtected();
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
        if (!(howMuch instanceof SeriesValue upTo)) {
            throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "/part wanted a count or a position, not "
                            + howMuch.datatype().literalSpelling());
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
        if (!(target instanceof SeriesValue series)) {
            return raiseCannotUse(target, "pick");
        }
        if (oneBasedIndex < 1 || oneBasedIndex > series.lengthFromHere()) {
            return NoneValue.none();
        }
        return switch (series) {
            case BlockValue block -> block.storage().at(block.index() + oneBasedIndex - 1);
            case StringValue string -> CharacterValue.of(
                    string.storage().at(string.index() + oneBasedIndex - 1));
            case BinaryValue binary -> IntegerValue.of(
                    binary.storage().at(binary.index() + oneBasedIndex - 1));
        };
    }

    private enum Combination { INTERSECT, UNION, EXCLUDE }

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
        List<List<Value>> first = inRecords(((BlockValue) arguments.get(0)).remaining(), stride);
        List<List<Value>> second = inRecords(((BlockValue) arguments.get(1)).remaining(), stride);
        List<List<Value>> result = new ArrayList<>();

        for (List<Value> candidate : first) {
            boolean inSecond = second.stream()
                    .anyMatch(other -> sameRecord(other, candidate, mindingCase));
            boolean wanted = switch (how) {
                case INTERSECT -> inSecond;
                case UNION, EXCLUDE -> how == Combination.UNION || !inSecond;
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
        throw Raised.of(EvaluationFailure.CANNOT_USE,
                "cannot use " + nativeName + " on "
                        + value.datatype().literalSpelling() + " value");
    }

    // ---- strings ---------------------------------------------------------

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
        define("trim", List.of(
                        Parameter.required("text", Set.of(
                                Datatype.STRING, Datatype.FILE, Datatype.URL,
                                Datatype.EMAIL, Datatype.TAG, Datatype.REF,
                                Datatype.BINARY, Datatype.BLOCK)),
                        Parameter.belongingTo("with", "characters", Set.of())),
                Set.of("head", "tail", "auto", "lines", "all", "with"),
                (arguments, evaluator, context, refinements) -> {
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
                            String unwanted = Molder.form(arguments.get(1));
                            StringBuilder kept = new StringBuilder();
                            text.chars()
                                    .filter(letter -> unwanted.indexOf(letter) < 0)
                                    .forEach(letter -> kept.append((char) letter));
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
                            return indented.strip();
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
        define("to", List.of(Parameter.required("type"), Parameter.required("value")),
                (arguments, evaluator, context) -> convertedTo(
                        arguments.get(0), arguments.get(1)));

        define("as-pair", takesNumbers("x", "y"),
                (arguments, evaluator, context) -> PairValue.of(
                        Comparison.asDouble(arguments.get(0)), Comparison.asDouble(arguments.get(1))));

        // The answer is an issue rather than a string, padded to the
        // full width of a whole number unless /size narrows it.
        define("to-hex", List.of(
                        Parameter.required("value", Set.of(Datatype.INTEGER)),
                        Parameter.belongingTo("size", "width", Set.of(Datatype.INTEGER))),
                Set.of("size"),
                (arguments, evaluator, context, refinements) -> {
                    String hex = "%016X".formatted(
                            ((IntegerValue) arguments.get(0)).magnitude());
                    if (refinements.contains("size") && arguments.size() > 1) {
                        int width = (int) ((IntegerValue) arguments.get(1)).magnitude();
                        hex = hex.substring(Math.max(0, hex.length() - width));
                    }
                    return WordValue.of(hex, Datatype.ISSUE);
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
        define("deline", List.of(Parameter.required("text", Set.of(Datatype.STRING))),
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
        define("as", List.of(Parameter.required("type", Set.of(Datatype.DATATYPE)),
                        Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    Datatype wanted = ((DatatypeValue) arguments.get(0)).represents();
                    Value value = arguments.get(1);
                    if (value.datatype() == wanted) {
                        return value;
                    }
                    if (value instanceof BlockValue block && wanted.isAnyBlock()) {
                        return block.as(wanted);
                    }
                    if (value instanceof StringValue text && wanted.isAnyString()) {
                        return StringValue.of(text.text(), wanted);
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
        List<Parameter> parameters = FunctionSpec.parametersIn(spec);
        return switch (original) {
            case NativeValue built -> new NativeValue(built.nativeName(), parameters);
            case FunctionValue written -> new FunctionValue(
                    spec, written.body(), parameters,
                    FunctionSpec.localNamesIn(spec), written.closedOver());
            default -> raiseCannotUse(original, "make");
        };
    }

    private static Value makeOfDatatype(DatatypeValue wanted, Value from) {
        if (from instanceof IntegerValue && wanted.represents().isSeries()) {
            return switch (wanted.represents()) {
                case BLOCK, PAREN, PATH -> BlockValue.block(List.of()).as(wanted.represents());
                case BINARY -> BinaryValue.of();
                default -> StringValue.of("", wanted.represents());
            };
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
            case STRING -> StringValue.of(runTogether(value));
            // The rest of the string family takes the text of the value
            // the same way, so `to file! [a b]` is %ab: nothing inserts
            // separators between the parts.
            case FILE, URL, EMAIL, TAG, REF ->
                    StringValue.of(runTogether(value), wanted.represents());
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
            case BLOCK, PAREN, HASH -> (value instanceof BlockValue block
                    ? block
                    : BlockValue.block(value)).as(wanted.represents());
            case MAP -> MapValue.of(itemsOf(value));
            case CHAR -> asCharacter(value);
            case PAIR -> asPair(value);
            case MONEY -> asMoney(value);
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

    private static Value truncatedDecimal(String candidate, String original) {
        try {
            return IntegerValue.of((long) Double.parseDouble(candidate));
        } catch (NumberFormatException notANumberEither) {
            throw Raised.of(EvaluationFailure.BAD_MAKE_ARG,
                    "cannot read \"" + original + "\" as an integer");
        }
    }

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
        // Found by which refinements were asked for rather than by a
        // fixed position: adding /DUP after /PART moved everything along,
        // and a fixed index then read one for the other.
        Value count = argumentFor(
                "part", List.of("part", "dup"), arguments, refinements, where);
        return count instanceof IntegerValue wanted
                ? Optional.of(wanted.magnitude())
                : Optional.empty();
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

        return howManyWanted(arguments, refinements, 2)
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

    private static List<Value> firstFew(
            List<Value> items, List<Value> arguments, Set<String> refinements, int where) {
        return howManyWanted(arguments, refinements, where)
                .map(count -> items.subList(0, (int) Math.min(count, items.size())))
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
        define("read", List.of(Parameter.required("source", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() ->
                            StringValue.of(evaluator.files().read(
                                    ((StringValue) arguments.get(0)).text())));
                });

        define("write", List.of(
                        Parameter.required("destination", Set.of(Datatype.FILE)),
                        Parameter.required("contents")),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> {
                        evaluator.files().write(
                                ((StringValue) arguments.get(0)).text(),
                                Molder.form(arguments.get(1)));
                        return arguments.get(0);
                    });
                });

        // A REBOL path uses a slash between the parts on every machine.
        // A local path uses whatever the machine uses. On a machine that
        // already uses a slash the two are the same text, which is why
        // these look as though they do nothing here.
        define("to-local-file", List.of(Parameter.required("path",
                        Set.of(Datatype.FILE, Datatype.STRING))),
                (arguments, evaluator, context) -> StringValue.of(
                        ((StringValue) arguments.getFirst()).text()
                                .replace('/', localFileSeparator)));

        define("to-rebol-file", List.of(Parameter.required("path",
                        Set.of(Datatype.FILE, Datatype.STRING))),
                (arguments, evaluator, context) -> StringValue.of(
                        ((StringValue) arguments.getFirst()).text()
                                .replace(localFileSeparator, '/'),
                        Datatype.FILE));

        // Starting another program. A string is one word by itself and a
        // block is the program and its arguments already separated. The
        // block is the safe form: a string handed to a shell is read as
        // text, thus anything the script put in it becomes part of the
        // command.
        //
        // /WAIT answers the exit code and /OUTPUT answers what the
        // program wrote. Without either, the answer is the number the
        // host gave the new process.
        define("call", List.of(Parameter.required("command",
                        Set.of(Datatype.STRING, Datatype.BLOCK, Datatype.FILE))),
                Set.of("wait", "shell", "output"),
                (arguments, evaluator, context, refinements) -> {
                    requireService(HostService.PROCESSES);
                    List<String> command = wordsOfCommand(arguments.getFirst());
                    boolean throughShell = refinements.contains("shell")
                            || arguments.getFirst() instanceof StringValue;
                    return throughPort(() -> {
                        if (!refinements.contains("wait") && !refinements.contains("output")) {
                            return IntegerValue.of(
                                    evaluator.processes().start(command, throughShell));
                        }
                        ProcessPort.Finished done =
                                evaluator.processes().runAndWait(command, throughShell);
                        return refinements.contains("output")
                                ? StringValue.of(done.output())
                                : IntegerValue.of(done.exitCode());
                    });
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
        define("ask", List.of(Parameter.required("question", Set.of(Datatype.STRING))),
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

        define("delete", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
                    requireService(HostService.FILES);
                    return throughPort(() -> {
                        evaluator.files().delete(((StringValue) arguments.getFirst()).text());
                        return arguments.getFirst();
                    });
                });

        define("rename", List.of(
                        Parameter.required("from", Set.of(Datatype.FILE)),
                        Parameter.required("to", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> {
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
    }

    /**
     * A command as the separate words a host needs.
     *
     * <p>A block is already separated and each item is taken as it
     * stands. A string is one word, because splitting it here would guess
     * at quoting that only a shell knows the rules for.
     */
    private static List<String> wordsOfCommand(Value command) {
        if (command instanceof BlockValue given) {
            return given.remaining().stream().map(Molder::form).toList();
        }
        return List.of(Molder.form(command));
    }

    /** Turns a port's refusal into an error the script can catch. */
    private static Value throughPort(Supplier<Value> operation) {
        try {
            return operation.get();
        } catch (FilePort.Denied denied) {
            throw new Raised(ErrorValue.of(
                    ErrorCategory.ACCESS, denied.errorId(), denied.getMessage()));
        }
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
        define("mold", List.of(Parameter.required("value")),
                Set.of("all", "only", "flat", "part"),
                (arguments, evaluator, context, refinements) -> StringValue.of(
                        refinements.contains("flat")
                                ? Molder.moldFlat(arguments.get(0))
                                : Molder.mold(arguments.get(0))));
        define("form", takes("value"),
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
                    throw new QuitRequested(refinements.contains("return")
                            ? arguments.getFirst()
                            : NoneValue.none());
                });
        define("print", takes("value"),
                (arguments, evaluator, context) -> {
                    evaluator.output().writeLine(forOutput(arguments.get(0), evaluator));
                    return UnsetValue.unset();
                });
        define("prin", takes("value"),
                (arguments, evaluator, context) -> {
                    evaluator.output().write(forOutput(arguments.get(0), evaluator));
                    return UnsetValue.unset();
                });
        define("make-error", takes("id", "message"),
                (arguments, evaluator, context) -> ErrorValue.script(
                        Molder.form(arguments.get(0)), Molder.form(arguments.get(1))));
    }
}
