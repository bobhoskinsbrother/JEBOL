package org.jebol.domain.eval;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockStorage;
import org.jebol.domain.value.BinaryStorage;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockStorage;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.ContextSlot;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.NativeValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.parse.Parser;
import org.jebol.domain.parse.StringParser;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.OperatorValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.SeriesValue;
import org.jebol.domain.value.StringValue;
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

    private final Map<String, Callable> behaviours = new LinkedHashMap<>();
    private final Map<String, NativeValue> definitions = new LinkedHashMap<>();
    private final Map<String, String> operatorTwins = new LinkedHashMap<>();

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
    }

    public static Natives standard() {
        return new Natives();
    }

    /** What the evaluator dispatches on: native name to behaviour. */
    public Map<String, Callable> behaviours() {
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
        definitions.put(name, new NativeValue(name, parameters));
        behaviours.put(name, behaviour);
    }

    private void defineOperator(String spelling, String prefixTwin) {
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

    private static List<Parameter> takesNumbers(String... names) {
        Set<Datatype> numbers = Set.of(
                Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT, Datatype.MONEY);
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
        define("negate", takesNumbers("value"),
                (arguments, evaluator, context) -> arithmetic(
                        List.of(IntegerValue.of(0), arguments.get(0)), Operation.SUBTRACT));

        defineOperator("+", "add");
        defineOperator("-", "subtract");
        defineOperator("*", "multiply");
        defineOperator("/", "divide");
        defineOperator("//", "remainder");
    }

    private enum Operation { ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER }

    /**
     * Integer arithmetic raises on overflow rather than wrapping. The JVM
     * wraps silently, which is the worst available behaviour: a wrong answer
     * that looks like a right one.
     */
    private static Value arithmetic(List<Value> arguments, Operation operation) {
        Value left = arguments.get(0);
        Value right = arguments.get(1);

        if (left instanceof MoneyValue || right instanceof MoneyValue) {
            return moneyArithmetic(asBigDecimal(left), asBigDecimal(right), operation);
        }
        if (left instanceof IntegerValue leftInteger && right instanceof IntegerValue rightInteger) {
            return integerArithmetic(leftInteger.magnitude(), rightInteger.magnitude(), operation);
        }
        return decimalArithmetic(asDouble(left), asDouble(right), operation);
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
            };
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW, overflowed.getMessage());
        }
    }

    private static Value decimalArithmetic(double left, double right, Operation operation) {
        return switch (operation) {
            case ADD -> DecimalValue.of(left + right);
            case SUBTRACT -> DecimalValue.of(left - right);
            case MULTIPLY -> DecimalValue.of(left * right);
            case DIVIDE -> {
                requireNonZero(right);
                yield DecimalValue.of(left / right);
            }
            case REMAINDER -> {
                requireNonZero(right);
                yield DecimalValue.of(left % right);
            }
        };
    }

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
        };
    }

    private static void requireNonZero(double divisor) {
        if (divisor == 0.0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    private static double asDouble(Value value) {
        return switch (value) {
            case IntegerValue integer -> integer.magnitude();
            case DecimalValue decimal -> decimal.quantity();
            case MoneyValue money -> money.amount().doubleValue();
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    value.datatype().literalSpelling() + " is not a number");
        };
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
        define("equal?", takes("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(looselyEqual(
                        arguments.get(0), arguments.get(1))));
        define("strict-equal?", takes("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).equals(arguments.get(1))));
        define("not-equal?", takes("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(!looselyEqual(
                        arguments.get(0), arguments.get(1))));
        define("greater?", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(
                        compare(arguments.get(0), arguments.get(1)) > 0));
        define("lesser?", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(
                        compare(arguments.get(0), arguments.get(1)) < 0));
        define("greater-or-equal?", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(
                        compare(arguments.get(0), arguments.get(1)) >= 0));
        define("lesser-or-equal?", takesNumbers("value1", "value2"),
                (arguments, evaluator, context) -> LogicValue.of(
                        compare(arguments.get(0), arguments.get(1)) <= 0));

        defineOperator("=", "equal?");
        defineOperator("==", "strict-equal?");
        defineOperator("<>", "not-equal?");
        defineOperator(">", "greater?");
        defineOperator("<", "lesser?");
        defineOperator(">=", "greater-or-equal?");
        defineOperator("<=", "lesser-or-equal?");
    }

    /** REBOL's {@code =}: ignores case, and lets an integer equal a decimal. */
    private static boolean looselyEqual(Value left, Value right) {
        if (left instanceof StringValue leftText && right instanceof StringValue rightText) {
            return leftText.equalsIgnoringCase(rightText);
        }
        if (left instanceof WordValue leftWord && right instanceof WordValue rightWord) {
            return leftWord.namesSameAs(rightWord);
        }
        if (isNumeric(left) && isNumeric(right)) {
            return compare(left, right) == 0;
        }
        return left.equals(right);
    }

    private static boolean isNumeric(Value value) {
        return value.datatype().isNumber() || value.datatype() == Datatype.MONEY;
    }

    private static int compare(Value left, Value right) {
        if (left instanceof IntegerValue leftInteger && right instanceof IntegerValue rightInteger) {
            return Long.compare(leftInteger.magnitude(), rightInteger.magnitude());
        }
        return Double.compare(asDouble(left), asDouble(right));
    }

    // ---- control ---------------------------------------------------------

    private void defineControl() {
        define("if", List.of(Parameter.required("condition"),
                        Parameter.required("branch", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    requireConditional(arguments.get(0));
                    return arguments.get(0).isTruthy()
                            ? evaluator.evaluateOrRaise(
                                    (BlockValue) arguments.get(1), context)
                            : NoneValue.none();
                });

        define("either", List.of(Parameter.required("condition"),
                        Parameter.required("true-branch", Set.of(Datatype.BLOCK)),
                        Parameter.required("false-branch", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    requireConditional(arguments.get(0));
                    BlockValue taken = (BlockValue) (arguments.get(0).isTruthy()
                            ? arguments.get(1)
                            : arguments.get(2));
                    return evaluator.evaluateOrRaise(taken, context);
                });

        define("not", takes("value"),
                (arguments, evaluator, context) -> {
                    requireConditional(arguments.get(0));
                    return LogicValue.of(!arguments.get(0).isTruthy());
                });

        define("do", takes("value"),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case BlockValue block ->
                            evaluator.evaluateOrRaise(block, context);
                    case StringValue text -> evaluator.evaluateSource(text.text());
                    default -> arguments.get(0);
                });

        define("any", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    Value found = evaluator.evaluateUntilOrRaise(
                            (BlockValue) arguments.get(0),
                            evaluator.systemContext(),
                            Value::isTruthy);
                    return found.isTruthy() ? found : NoneValue.none();
                });

        define("all", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    BlockValue block = (BlockValue) arguments.get(0);
                    if (block.lengthFromHere() == 0) {
                        return NoneValue.none();
                    }
                    Value found = evaluator.evaluateUntilOrRaise(
                            block, evaluator.systemContext(), value -> !value.isTruthy());
                    return found.isTruthy() ? found : NoneValue.none();
                });

        define("unless", List.of(Parameter.required("condition"),
                        Parameter.required("branch", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    requireConditional(arguments.get(0));
                    return arguments.get(0).isTruthy()
                            ? NoneValue.none()
                            : evaluator.evaluateOrRaise(
                                    (BlockValue) arguments.get(1), context);
                });

        // SWITCH compares the value against each choice in turn and runs the
        // block after the first that matches. Nothing matching is NONE.
        define("switch", List.of(Parameter.required("value"),
                        Parameter.required("choices", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    List<Value> choices = ((BlockValue) arguments.get(1)).remaining();
                    for (int at = 0; at + 1 < choices.size(); at += 2) {
                        if (looselyEqual(choices.get(at), arguments.get(0))) {
                            return choices.get(at + 1) instanceof BlockValue branch
                                    ? evaluator.evaluateOrRaise(
                                            branch, context)
                                    : choices.get(at + 1);
                        }
                    }
                    return NoneValue.none();
                });

        // CASE takes condition and block in pairs and runs the first block
        // whose condition is true, evaluating no further conditions.
        define("case", List.of(Parameter.required("choices", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    BlockValue choices = (BlockValue) arguments.get(0);
                    BlockValue at = choices;

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
                            return branch instanceof BlockValue taken
                                    ? evaluator.evaluateOrRaise(taken, context)
                                    : branch;
                        }
                        at = afterCondition.atIndex(afterCondition.index() + 1);
                    }
                    return NoneValue.none();
                });

        // ATTEMPT is TRY with the error swallowed: the value, or none.
        define("attempt", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    try {
                        return evaluator.evaluateOrRaise(
                                (BlockValue) arguments.get(0), context);
                    } catch (Raised raised) {
                        return NoneValue.none();
                    }
                });

        define("try", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    try {
                        return evaluator.evaluateOrRaise(
                                (BlockValue) arguments.get(0), context);
                    } catch (Raised raised) {
                        return raised.error();
                    }
                });
    }

    private static void requireConditional(Value value) {
        if (!value.isConditional()) {
            throw Raised.of(EvaluationFailure.NO_VALUE,
                    "unset cannot be used as a condition");
        }
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

        define("throw", takes("value"),
                (arguments, evaluator, context) -> {
                    throw new ThrownSignal(arguments.get(0));
                });

        define("catch", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    try {
                        return evaluator.evaluateOrRaise(
                                (BlockValue) arguments.get(0), context);
                    } catch (ThrownSignal thrown) {
                        return thrown.value();
                    }
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

        define("does", List.of(Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> makeFunction(
                        BlockValue.block(),
                        (BlockValue) arguments.get(0),
                        context));

        // FUNCTION is FUNC with the locals given as a separate block rather
        // than tacked on after /local.
        define("function", List.of(
                        Parameter.required("spec", Set.of(Datatype.BLOCK)),
                        Parameter.required("locals", Set.of(Datatype.BLOCK)),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    List<Value> combined = new ArrayList<>(
                            ((BlockValue) arguments.get(0)).remaining());
                    combined.add(WordValue.of("local", Datatype.REFINEMENT));
                    combined.addAll(((BlockValue) arguments.get(1)).remaining());
                    return makeFunction(
                            BlockValue.block(combined),
                            (BlockValue) arguments.get(2),
                            context);
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
                    case DatatypeValue wanted when wanted.represents() == Datatype.OBJECT ->
                            makeObject(evaluator, context, Optional.empty(),
                                    (BlockValue) arguments.get(1));
                    case ObjectValue prototype ->
                            makeObject(evaluator, context, Optional.of(prototype),
                                    (BlockValue) arguments.get(1));
                    case DatatypeValue wanted when wanted.represents() == Datatype.ERROR ->
                            ErrorValue.of(ErrorCategory.USER, "user-error",
                                    Molder.form(arguments.get(1)));
                    default -> raiseCannotUse(arguments.get(0), "make");
                });

        define("context", List.of(Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> makeObject(
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

        define("bind", List.of(
                        Parameter.required("block", Set.of(Datatype.BLOCK)),
                        Parameter.required("target", Set.of(Datatype.OBJECT))),
                (arguments, evaluator, context) -> Binder.bind(
                        (BlockValue) arguments.get(0),
                        ((ObjectValue) arguments.get(1)).context()));
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
                            last = evaluator.evaluateOrRaise(body, evaluator.systemContext());
                        }
                    } catch (LoopSignal stopped) {
                        return NoneValue.none();
                    }
                    return last;
                });

        define("repeat", List.of(
                        Parameter.literal("counter"),
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
                            last = evaluator.evaluateOrRaise(body, evaluator.systemContext());
                        }
                    } catch (LoopSignal stopped) {
                        return NoneValue.none();
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
                            last = evaluator.evaluateOrRaise(body, evaluator.systemContext());
                        } while (!last.isTruthy());
                    } catch (LoopSignal stopped) {
                        return NoneValue.none();
                    }
                    return last;
                });

        // FOR steps a value from one bound to another. The step decides the
        // direction, so a negative step counts down and a step that would
        // never arrive runs no passes rather than for ever.
        define("for", List.of(
                        Parameter.literal("counter"),
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
                        Parameter.literal("target"),
                        Parameter.required("series"),
                        Parameter.required("body", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> forEachLoop(
                        evaluator,
                        arguments.get(0),
                        arguments.get(1),
                        (BlockValue) arguments.get(2)));

        // FORALL moves the series itself rather than binding each item, so
        // the word holds a position. It ends at the tail, which is why the
        // guide follows every FORALL with HEAD.
        define("forall", List.of(
                        Parameter.literal("word"),
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
                            last = evaluator.evaluateOrRaise(body, evaluator.systemContext());
                        }
                        slot.setValue(start.tail());
                    } catch (LoopSignal stopped) {
                        return NoneValue.none();
                    }
                    return last;
                });

        define("break", List.of(),
                (arguments, evaluator, context) -> {
                    throw LoopSignal.breaking();
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
                last = evaluator.evaluateOrRaise(bound, locals);
            }
        } catch (LoopSignal stopped) {
            return NoneValue.none();
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

        double stepBy = asDouble(step);
        if (stepBy == 0.0) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "a for loop with a step of zero would never end");
        }
        boolean counting = start instanceof CharacterValue;
        double from = counting ? ((CharacterValue) start).codepoint() : asDouble(start);
        double to = counting ? ((CharacterValue) end).codepoint() : asDouble(end);
        boolean wholeNumbers = start instanceof IntegerValue && step instanceof IntegerValue;

        Context locals = Context.childOf(evaluator.systemContext());
        locals.define(counter.spelling());
        BlockValue bound = Binder.bind(body, locals);
        Value last = NoneValue.none();

        try {
            for (double at = from; stepBy > 0 ? at <= to : at >= to; at += stepBy) {
                locals.set(counter.spelling(), counting
                        ? CharacterValue.of((int) at)
                        : wholeNumbers ? IntegerValue.of((long) at) : DecimalValue.of(at));
                last = evaluator.evaluateOrRaise(bound, locals);
            }
        } catch (LoopSignal stopped) {
            return NoneValue.none();
        }
        return last;
    }

    private static Value forEachLoop(
            Evaluator evaluator, Value target, Value series, BlockValue body) {

        List<WordValue> names = target instanceof BlockValue block
                ? block.remaining().stream().map(WordValue.class::cast).toList()
                : List.of((WordValue) target);
        List<Value> items = itemsOf(series);

        Context locals = Context.childOf(evaluator.systemContext());
        names.forEach(name -> locals.define(name.spelling()));
        BlockValue bound = Binder.bind(body, locals);
        Value last = NoneValue.none();

        try {
            for (int at = 0; at + names.size() <= items.size(); at += names.size()) {
                for (int which = 0; which < names.size(); which++) {
                    locals.set(names.get(which).spelling(), items.get(at + which));
                }
                last = evaluator.evaluateOrRaise(bound, locals);
            }
        } catch (LoopSignal stopped) {
            return NoneValue.none();
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

    private void defineReflection() {
        define("type?", takes("value"),
                (arguments, evaluator, context) -> DatatypeValue.of(arguments.get(0).datatype()));
        define("unset?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.UNSET));
        define("none?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.NONE));
        define("error?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.ERROR));
        define("block?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.BLOCK));
        define("string?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.STRING));
        define("integer?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.INTEGER));
        define("word?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.WORD));
        define("native?", takes("value"),
                (arguments, evaluator, context) -> LogicValue.of(
                        arguments.get(0).datatype() == Datatype.NATIVE));
        define("zero?", takesNumbers("value"),
                (arguments, evaluator, context) -> LogicValue.of(asDouble(arguments.get(0)) == 0.0));

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

        define("protect", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    slotOf((WordValue) arguments.get(0)).protectFromAssignment();
                    return UnsetValue.unset();
                });

        define("unprotect", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> {
                    slotOf((WordValue) arguments.get(0)).allowAssignment();
                    return UnsetValue.unset();
                });

        define("set", List.of(Parameter.required("target"), Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    Value target = arguments.get(0);
                    Value supplied = arguments.get(1);
                    if (target instanceof WordValue word) {
                        slotOf(word).setValue(supplied);
                        return supplied;
                    }
                    if (target instanceof BlockValue words) {
                        List<Value> names = words.remaining();
                        List<Value> values = supplied instanceof BlockValue block
                                ? block.remaining()
                                : null;
                        for (int index = 0; index < names.size(); index++) {
                            Value assigned = values == null
                                    ? supplied
                                    : index < values.size() ? values.get(index) : NoneValue.none();
                            slotOf((WordValue) names.get(index)).setValue(assigned);
                        }
                        return supplied;
                    }
                    return raiseCannotUse(target, "set");
                });

        define("select", List.of(Parameter.required("series"), Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseCannotUse(arguments.get(0), "select");
                    }
                    List<Value> items = block.remaining();
                    for (int index = 0; index + 1 < items.size(); index++) {
                        if (looselyEqual(items.get(index), arguments.get(1))) {
                            return items.get(index + 1);
                        }
                    }
                    return NoneValue.none();
                });

        define("get", List.of(Parameter.required("word", Set.of(Datatype.WORD))),
                (arguments, evaluator, context) -> slotOf((WordValue) arguments.get(0)).value());
    }

    private static ContextSlot slotOf(WordValue word) {
        if (!word.isBound() || !word.binding().knows(word.canonical())) {
            throw Raised.of(EvaluationFailure.NOT_DEFINED, word.spelling());
        }
        return word.binding().slotFor(word.canonical());
    }

    // ---- series ----------------------------------------------------------

    private void defineSeries() {
        define("length?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? IntegerValue.of(series.lengthFromHere())
                        : raiseCannotUse(arguments.get(0), "length?"));

        define("first", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 1));
        define("second", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 2));
        define("third", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 3));
        define("pick", List.of(Parameter.required("series"),
                        Parameter.required("index", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> pick(arguments.get(0),
                        (int) ((IntegerValue) arguments.get(1)).magnitude()));

        define("head?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? LogicValue.of(series.atHead())
                        : raiseCannotUse(arguments.get(0), "head?"));
        define("tail?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? LogicValue.of(series.atTail())
                        : raiseCannotUse(arguments.get(0), "tail?"));
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
        define("index?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? IntegerValue.of(series.index())
                        : raiseCannotUse(arguments.get(0), "index?"));

        // APPEND mutates the storage, so every value pointing into it sees the
        // change. It gives back the series at its head, which is what makes
        // `append a b` usable as an expression.
        define("append", List.of(Parameter.required("series"), Parameter.required("value")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case BlockValue block -> {
                        if (arguments.get(1) instanceof BlockValue added) {
                            added.remaining().forEach(block.storage()::append);
                        } else {
                            block.storage().append(arguments.get(1));
                        }
                        yield block.head();
                    }
                    case StringValue string -> {
                        Molder.form(arguments.get(1)).codePoints()
                                .forEach(string.storage()::append);
                        yield string.head();
                    }
                    default -> raiseCannotUse(arguments.get(0), "append");
                });

        define("last", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? pick((Value) series, series.lengthFromHere())
                        : raiseCannotUse(arguments.get(0), "last"));

        define("empty?", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? LogicValue.of(series.lengthFromHere() == 0)
                        : raiseCannotUse(arguments.get(0), "empty?"));

        define("back", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> arguments.get(0) instanceof SeriesValue series
                        ? (Value) series.atIndex(Math.max(1, series.index() - 1))
                        : raiseCannotUse(arguments.get(0), "back"));

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
        define("copy/part", List.of(
                        Parameter.required("series"),
                        Parameter.required("count", Set.of(Datatype.INTEGER))),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "copy/part");
                    }
                    long wanted = ((IntegerValue) arguments.get(1)).magnitude();
                    int taking = (int) Math.max(0, Math.min(wanted, series.lengthFromHere()));
                    return switch (series) {
                        case BlockValue block -> BlockValue.block(
                                block.remaining().subList(0, taking));
                        case StringValue text0 -> StringValue.of(
                                text0.text().substring(0, taking), text0.datatype());
                        default -> raiseCannotUse(arguments.get(0), "copy/part");
                    };
                });

        define("copy", List.of(Parameter.required("value")),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case BlockValue block ->
                            new BlockValue(new BlockStorage(block.remaining()), 1,
                                    block.datatype());
                    case StringValue text -> StringValue.of(text.text(), text.datatype());
                    case BinaryValue binary -> {
                        BinaryStorage copied = new BinaryStorage();
                        for (int at = 0; at < binary.lengthFromHere(); at++) {
                            copied.append(binary.storage().at(binary.index() + at));
                        }
                        yield new BinaryValue(copied, 1);
                    }
                    default -> arguments.get(0);
                });

        // FIND gives the series positioned where the match starts, so the
        // result is both an answer and somewhere to carry on from.
        define("find", List.of(Parameter.required("series"), Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof SeriesValue series)) {
                        return raiseCannotUse(arguments.get(0), "find");
                    }
                    List<Value> items = itemsOf(series);
                    for (int at = 0; at < items.size(); at++) {
                        if (looselyEqual(items.get(at), arguments.get(1))) {
                            return series.atIndex(series.index() + at);
                        }
                    }
                    return NoneValue.none();
                });

        define("insert", List.of(Parameter.required("series"), Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseCannotUse(arguments.get(0), "insert");
                    }
                    block.storage().insertAt(block.index(), arguments.get(1));
                    return block.atIndex(block.index() + 1);
                });

        define("remove", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseCannotUse(arguments.get(0), "remove");
                    }
                    if (!block.atTail()) {
                        block.storage().removeAt(block.index());
                    }
                    return block;
                });

        define("reverse", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseCannotUse(arguments.get(0), "reverse");
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
        define("change", List.of(Parameter.required("series"), Parameter.required("value")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof BlockValue block) || block.atTail()) {
                        return raiseCannotUse(arguments.get(0), "change");
                    }
                    block.storage().set(block.index(), arguments.get(1));
                    return block.atIndex(block.index() + 1);
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
                    default -> raiseCannotUse(arguments.get(0), "clear");
                });

        define("sort", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> {
                    if (!(arguments.get(0) instanceof BlockValue block)) {
                        return raiseCannotUse(arguments.get(0), "sort");
                    }
                    List<Value> ordered = new ArrayList<>(block.remaining());
                    ordered.sort(Natives::compareForSorting);
                    for (int at = 0; at < ordered.size(); at++) {
                        block.storage().set(block.index() + at, ordered.get(at));
                    }
                    return block;
                });

        define("intersect", List.of(
                        Parameter.required("first", Set.of(Datatype.BLOCK)),
                        Parameter.required("second", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> combined(arguments, Combination.INTERSECT));
        define("union", List.of(
                        Parameter.required("first", Set.of(Datatype.BLOCK)),
                        Parameter.required("second", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> combined(arguments, Combination.UNION));
        define("exclude", List.of(
                        Parameter.required("first", Set.of(Datatype.BLOCK)),
                        Parameter.required("second", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> combined(arguments, Combination.EXCLUDE));

        define("fourth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 4));
        define("fifth", List.of(Parameter.required("series")),
                (arguments, evaluator, context) -> pick(arguments.get(0), 5));

        // REDUCE evaluates every expression and collects the results, which is
        // the contrast case to DO returning only the last.
        define("reduce", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> BlockValue.block(evaluator.evaluateEachOrRaise(
                        (BlockValue) arguments.get(0), evaluator.systemContext())));
    }

    private static Value pick(Value target, int oneBasedIndex) {
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

    /**
     * The set operations, which keep the order they found things in rather
     * than sorting, because a block is ordered and the answer should be too.
     */
    private static Value combined(List<Value> arguments, Combination how) {
        List<Value> first = ((BlockValue) arguments.get(0)).remaining();
        List<Value> second = ((BlockValue) arguments.get(1)).remaining();
        List<Value> result = new ArrayList<>();

        for (Value candidate : first) {
            boolean inSecond = second.stream().anyMatch(other -> looselyEqual(other, candidate));
            boolean wanted = switch (how) {
                case INTERSECT -> inSecond;
                case UNION, EXCLUDE -> how == Combination.UNION || !inSecond;
            };
            if (wanted && result.stream().noneMatch(kept -> looselyEqual(kept, candidate))) {
                result.add(candidate);
            }
        }
        if (how == Combination.UNION) {
            for (Value candidate : second) {
                if (result.stream().noneMatch(kept -> looselyEqual(kept, candidate))) {
                    result.add(candidate);
                }
            }
        }
        return BlockValue.block(result);
    }

    /** Orders by value where there is one, and by printed form otherwise. */
    private static int compareForSorting(Value left, Value right) {
        if (isNumeric(left) && isNumeric(right)) {
            return compare(left, right);
        }
        return Molder.form(left).compareToIgnoreCase(Molder.form(right));
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
        define("uppercase", List.of(Parameter.required("text", Set.of(Datatype.STRING))),
                (arguments, evaluator, context) -> StringValue.of(
                        ((StringValue) arguments.get(0)).text().toUpperCase(Locale.ROOT)));
        define("lowercase", List.of(Parameter.required("text", Set.of(Datatype.STRING))),
                (arguments, evaluator, context) -> StringValue.of(
                        ((StringValue) arguments.get(0)).text().toLowerCase(Locale.ROOT)));
        define("trim", List.of(Parameter.required("text", Set.of(Datatype.STRING))),
                (arguments, evaluator, context) -> StringValue.of(
                        ((StringValue) arguments.get(0)).text().strip()));
        define("join", List.of(Parameter.required("first"), Parameter.required("second")),
                (arguments, evaluator, context) -> StringValue.of(
                        Molder.form(arguments.get(0)) + Molder.form(arguments.get(1))));
        define("rejoin", List.of(Parameter.required("block", Set.of(Datatype.BLOCK))),
                (arguments, evaluator, context) -> {
                    StringBuilder joined = new StringBuilder();
                    evaluator.evaluateEachOrRaise(
                            (BlockValue) arguments.get(0), evaluator.systemContext())
                            .forEach(value -> joined.append(Molder.form(value)));
                    return StringValue.of(joined.toString());
                });
    }

    // ---- conversion ------------------------------------------------------

    private void defineConversion() {
        define("to-string", takes("value"),
                (arguments, evaluator, context) -> StringValue.of(Molder.form(arguments.get(0))));
        define("to-word", takes("value"),
                (arguments, evaluator, context) -> WordValue.of(Molder.form(arguments.get(0))));
        define("to-block", takes("value"),
                (arguments, evaluator, context) -> arguments.get(0) instanceof BlockValue block
                        ? block
                        : BlockValue.block(arguments.get(0)));
        define("to-integer", takes("value"),
                (arguments, evaluator, context) -> switch (arguments.get(0)) {
                    case IntegerValue integer -> integer;
                    case DecimalValue decimal -> IntegerValue.of((long) decimal.quantity());
                    case CharacterValue character ->
                            IntegerValue.of(character.codepoint());
                    case StringValue text -> parseInteger(text.text());
                    default -> raiseCannotUse(arguments.get(0), "to-integer");
                });
        define("to-decimal", takes("value"),
                (arguments, evaluator, context) -> DecimalValue.of(asDouble(arguments.get(0))));
    }

    private static Value parseInteger(String text) {
        try {
            return IntegerValue.of(Long.parseLong(text.strip()));
        } catch (NumberFormatException notANumber) {
            throw Raised.of(EvaluationFailure.CANNOT_USE,
                    "cannot read \"" + text + "\" as an integer");
        }
    }

    // ---- ports -----------------------------------------------------------
    //
    // Everything here goes through a port the host supplied. A script given
    // no port reaches nothing, and whatever the port refuses arrives as an
    // ordinary error the script could have caught.

    private void definePorts() {
        define("read", List.of(Parameter.required("source", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> throughPort(() ->
                        StringValue.of(evaluator.files().read(
                                ((StringValue) arguments.get(0)).text()))));

        define("write", List.of(
                        Parameter.required("destination", Set.of(Datatype.FILE)),
                        Parameter.required("contents")),
                (arguments, evaluator, context) -> throughPort(() -> {
                    evaluator.files().write(
                            ((StringValue) arguments.get(0)).text(),
                            Molder.form(arguments.get(1)));
                    return arguments.get(0);
                }));

        define("exists?", List.of(Parameter.required("path", Set.of(Datatype.FILE))),
                (arguments, evaluator, context) -> throughPort(() -> LogicValue.of(
                        evaluator.files().exists(((StringValue) arguments.get(0)).text()))));
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
                (arguments, evaluator, context) -> switch (arguments.get(1)) {
                    case BlockValue rule -> LogicValue.of(
                            arguments.get(0) instanceof StringValue text0
                                    ? StringParser.matches(
                                            evaluator, context, text0.text(), rule)
                                    : Parser.matches(
                                            evaluator, context,
                                            itemsOf(arguments.get(0)), rule));
                    case StringValue delimiters -> splitOn(
                            arguments.get(0), delimiters.text());
                    case NoneValue ignored -> splitOn(arguments.get(0), "");
                    default -> raiseCannotUse(arguments.get(1), "parse");
                });
    }

    /**
     * Splitting a string, which is what PARSE does when handed delimiters
     * rather than a rule. An empty delimiter set means whitespace and the
     * usual punctuation, as REBOL has always had it.
     */
    private static Value splitOn(Value input, String delimiters) {
        if (!(input instanceof StringValue text)) {
            return raiseCannotUse(input, "parse");
        }
        String separators = delimiters.isEmpty() ? " \t\n\r,;" : delimiters;
        List<Value> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();

        for (int codepoint : text.text().codePoints().toArray()) {
            if (separators.indexOf(codepoint) >= 0) {
                if (!piece.isEmpty()) {
                    pieces.add(StringValue.of(piece.toString()));
                    piece.setLength(0);
                }
                continue;
            }
            piece.appendCodePoint(codepoint);
        }
        if (!piece.isEmpty()) {
            pieces.add(StringValue.of(piece.toString()));
        }
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
        define("mold", takes("value"),
                (arguments, evaluator, context) -> StringValue.of(Molder.mold(arguments.get(0))));
        define("form", takes("value"),
                (arguments, evaluator, context) -> StringValue.of(Molder.form(arguments.get(0))));
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
