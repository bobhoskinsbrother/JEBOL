package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ReturnSignal;
import org.jebol.domain.read.SyntaxFailure;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DoNative extends DefaultNative {

    private static final int WHERE_SCRIPT_ARGUMENTS_ARRIVE = 1;

    @Override
    public String nativeName() {
        return "do";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("value", TypesetValue.ANY_TYPE.members()),
                Parameter.belongTo("args", "arg", Set.of()),
                Parameter.belongTo("next", "var", Set.of(WordValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("next", "args");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("args") && arguments.size() > 1) {
                recordScriptArguments(
                        evaluator, arguments.get(WHERE_SCRIPT_ARGUMENTS_ARRIVE));
            }
            if (refinements.contains("next") && arguments.size() > 1
                    && arguments.getLast() instanceof AnyWordValue var) {
                return stepOnceThrough(arguments.getFirst(), var, evaluator, context);
            }
            return evaluate(arguments.getFirst(), evaluator, context);
        };
    }

    private Value stepOnceThrough(
            Value value, AnyWordValue var, Evaluator evaluator, Context context) {

        Optional<AnyBlockValue> steppable = findTheSteppableBlock(value, evaluator, context);
        if (steppable.isEmpty()) {
            var.findBoundSlot().setValue(NoneValue.none());
            return value;
        }
        AnyBlockValue stepping = steppable.get();
        if (stepping.atTail()) {
            var.findBoundSlot().setValue(stepping);
            return UnsetValue.unset();
        }
        Evaluator.Step taken = evaluator.evaluateNextOrRaise(stepping, context);
        var.findBoundSlot().setValue(stepping.atIndex(taken.nextIndex()));
        return taken.value();
    }

    private Optional<AnyBlockValue> findTheSteppableBlock(
            Value value, Evaluator evaluator, Context context) {

        return switch (value) {
            case BlockValue block -> Optional.of(block);
            case ParenValue paren -> Optional.of(paren);
            case StringValue text ->
                    Optional.of(loadForStepping(text.text(), evaluator, context));
            default -> Optional.empty();
        };
    }

    private Value evaluate(Value value, Evaluator evaluator, Context context) {
        return switch (value) {
            case BlockValue block -> evaluator.evaluateOrRaise(block, context);
            case ParenValue paren -> evaluator.evaluateOrRaise(paren, context);
            case AnyStringValue address when address.isALocation() ->
                    runAsScript(address, evaluator);
            case AnyStringValue text -> evaluateSource(text.text(), evaluator);
            case BinaryValue bytes -> doAsScript(bytes, evaluator);
            case ErrorValue built -> throw new Raised(built.raisedAsItStands());
            case AnyWordValue word when word.looksUpItsDeclaration() ->
                    evaluator.readTheValueOfWordIn(word, context);
            case LitWordValue quoted -> quoted.asWord();
            case LitPathValue quoted -> quoted.asPath();
            case PathValue path -> evaluator.readTheValueOfPathIn(path, context);
            case SetWordValue assigning ->
                    raiseHalfExpression(assigning);
            case SetPathValue assigning ->
                    raiseHalfExpression(assigning);
            case TaskValue task -> handToHost(task, evaluator);
            default -> value;
        };
    }

    private Value handToHost(TaskValue task, Evaluator evaluator) {
        evaluator.ports().tasks().runBody(Molder.mold(task.body()), evaluator.findOutput());
        return task;
    }

    private Value evaluateSource(String source, Evaluator evaluator) {
        try {
            return evaluator.evaluateSource(source);
        } catch (ReturnSignal returned) {
            return returned.value();
        }
    }

    private Value doAsScript(BinaryValue bytes, Evaluator evaluator) {
        Value loadHeader = evaluator.systemContext().findSystemFunctionNamed("load-header");
        Value read = evaluator.applyFunction(loadHeader, List.of(bytes));
        if (read instanceof AnyWordValue why) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, why.spelling());
        }
        List<Value> parts = ((AnyBlockValue) read).remaining();
        refuseAScriptThatNeedsANewerInterpreter(parts.getFirst(), evaluator);
        return evaluateSource(scriptBody(parts), evaluator);
    }

    private void refuseAScriptThatNeedsANewerInterpreter(
            Value header, Evaluator evaluator) {

        if (header instanceof ObjectValue(Context fields)
                && fields.holds("needs")
                && fields.findSlotFor("needs").value() instanceof TupleValue wanted
                && !doesInterpreterMeet(wanted, evaluator)) {
            throw new Raised(ErrorValue.of(SyntaxFailure.NEEDS.category(),
                    SyntaxFailure.NEEDS.errorId(),
                    SyntaxFailure.NEEDS.description()));
        }
    }

    private String scriptBody(List<Value> parts) {
        return parts.get(1) instanceof BinaryValue mark
                && parts.get(2) instanceof BinaryValue remaining
                && mark.sharesStorageWith(remaining)
                ? mark.asStrictTextUpTo(remaining.index())
                : ((BinaryValue) parts.get(1)).asStrictText();
    }

    private boolean doesInterpreterMeet(TupleValue wanted, Evaluator evaluator) {
        return evaluator.systemContext().readValueAt("system", "version") instanceof TupleValue own
                && !Comparison.holds(wanted, own, Comparison.Strictness.GREATER);
    }

    private Value runAsScript(AnyStringValue address, Evaluator evaluator) {
        Value doStar = evaluator.systemContext().findSystemFunctionNamed("do*");
        return evaluator.applyFunction(doStar, List.of(address));
    }

    private AnyBlockValue loadForStepping(
            String source, Evaluator evaluator, Context context) {

        TranscodeResult read = evaluator.read(source);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return Binder.bindAndDefine(read.values().orElseThrow(), context);
    }

    private Value raiseHalfExpression(Value assigning) {
        throw Raised.of(EvaluationFailure.INVALID_ARG,
                Molder.mold(assigning) + " assigns, and there is nothing here to assign");
    }

    private void recordScriptArguments(Evaluator evaluator, Value given) {
        if (evaluator.systemContext().readValueAt("system", "script")
                instanceof ObjectValue(Context script)) {
            script.register("args", given);
        }
    }
}
