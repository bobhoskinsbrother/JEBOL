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

    private static final int WHERE_THE_SCRIPT_ARGUMENTS_ARRIVE = 1;

    @Override
    public String nativeName() {
        return "do";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value", Typeset.ANY_TYPE.members()),
                Parameter.belongingTo("args", "arg", Set.of()),
                Parameter.belongingTo("next", "var", Set.of(Datatype.WORD)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("next", "args");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            if (refinements.contains("args") && arguments.size() > 1) {
                recordTheScriptArguments(
                        evaluator, arguments.get(WHERE_THE_SCRIPT_ARGUMENTS_ARRIVE));
            }
            if (refinements.contains("next") && arguments.size() > 1
                    && arguments.getLast() instanceof WordValue var) {
                return oneStepThrough(arguments.getFirst(), var, evaluator, context);
            }
            return evaluated(arguments.getFirst(), evaluator, context);
        };
    }

    private Value oneStepThrough(
            Value value, WordValue var, Evaluator evaluator, Context context) {

        Optional<BlockValue> steppable = steppable(value, evaluator, context);
        if (steppable.isEmpty()) {
            var.boundSlot().setValue(NoneValue.none());
            return value;
        }
        BlockValue stepping = steppable.get();
        if (stepping.atTail()) {
            var.boundSlot().setValue(stepping);
            return UnsetValue.unset();
        }
        Evaluator.Step taken = evaluator.evaluateNextOrRaise(stepping, context);
        var.boundSlot().setValue(stepping.atIndex(taken.nextIndex()));
        return taken.value();
    }

    private Optional<BlockValue> steppable(
            Value value, Evaluator evaluator, Context context) {

        return switch (value) {
            case BlockValue block when block.datatype() == Datatype.BLOCK
                    || block.datatype() == Datatype.PAREN -> Optional.of(block);
            case StringValue text ->
                    Optional.of(loadedForStepping(text.text(), evaluator, context));
            default -> Optional.empty();
        };
    }

    private Value evaluated(Value value, Evaluator evaluator, Context context) {
        return switch (value) {
            case BlockValue block when block.datatype() == Datatype.BLOCK
                    || block.datatype() == Datatype.PAREN ->
                    evaluator.evaluateOrRaise(block, context);
            case AnyStringValue address when address.isALocation() ->
                    runAsAScript(address, evaluator);
            case AnyStringValue text -> evaluatedSource(text.text(), evaluator);
            case BinaryValue bytes -> doneAsAScript(bytes, evaluator);
            case ErrorValue built -> throw new Raised(built.raisedAsItStands());
            case WordValue word when word.datatype() == Datatype.WORD
                    || word.datatype() == Datatype.GET_WORD ->
                    evaluator.valueOfWordIn(word, context);
            case WordValue quoted when quoted.datatype() == Datatype.LIT_WORD ->
                    quoted.as(Datatype.WORD);
            case BlockValue quoted when quoted.datatype() == Datatype.LIT_PATH ->
                    quoted.as(Datatype.PATH);
            case BlockValue path when path.datatype() == Datatype.PATH ->
                    evaluator.valueOfPathIn(path, context);
            case WordValue assigning when assigning.datatype() == Datatype.SET_WORD ->
                    raiseHalfAnExpression(assigning);
            case BlockValue assigning when assigning.datatype() == Datatype.SET_PATH ->
                    raiseHalfAnExpression(assigning);
            default -> value;
        };
    }

    private Value evaluatedSource(String source, Evaluator evaluator) {
        try {
            return evaluator.evaluateSource(source);
        } catch (ReturnSignal returned) {
            return returned.value();
        }
    }

    private Value doneAsAScript(BinaryValue bytes, Evaluator evaluator) {
        Value loadHeader = evaluator.systemContext().systemFunctionNamed("load-header");
        Value read = evaluator.applyFunction(loadHeader, List.of(bytes));
        if (read instanceof WordValue why) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, why.spelling());
        }
        List<Value> parts = ((BlockValue) read).remaining();
        refuseAScriptThatNeedsANewerInterpreter(parts.getFirst(), evaluator);
        return evaluatedSource(theBodyOf(parts), evaluator);
    }

    private void refuseAScriptThatNeedsANewerInterpreter(
            Value header, Evaluator evaluator) {

        if (header instanceof ObjectValue(Context fields)
                && fields.holds("needs")
                && fields.slotFor("needs").value() instanceof TupleValue wanted
                && !interpreterMeets(wanted, evaluator)) {
            throw new Raised(ErrorValue.of(SyntaxFailure.NEEDS.category(),
                    SyntaxFailure.NEEDS.errorId(),
                    SyntaxFailure.NEEDS.description()));
        }
    }

    private String theBodyOf(List<Value> parts) {
        return parts.get(1) instanceof BinaryValue mark
                && parts.get(2) instanceof BinaryValue remaining
                && mark.sharesStorageWith(remaining)
                ? mark.asStrictTextUpTo(remaining.index())
                : ((BinaryValue) parts.get(1)).asStrictText();
    }

    private boolean interpreterMeets(TupleValue wanted, Evaluator evaluator) {
        return evaluator.systemContext().valueAt("system", "version") instanceof TupleValue own
                && !Comparison.holds(wanted, own, Comparison.Strictness.GREATER);
    }

    private Value runAsAScript(AnyStringValue address, Evaluator evaluator) {
        Value doStar = evaluator.systemContext().systemFunctionNamed("do*");
        return evaluator.applyFunction(doStar, List.of(address));
    }

    private BlockValue loadedForStepping(
            String source, Evaluator evaluator, Context context) {

        TranscodeResult read = evaluator.read(source);
        if (!read.succeeded()) {
            throw new Raised(read.error().orElseThrow());
        }
        return Binder.bindAndDefine(read.values().orElseThrow(), context);
    }

    private Value raiseHalfAnExpression(Value assigning) {
        throw Raised.of(EvaluationFailure.INVALID_ARG,
                Molder.mold(assigning) + " assigns, and there is nothing here to assign");
    }

    private void recordTheScriptArguments(Evaluator evaluator, Value given) {
        if (evaluator.systemContext().valueAt("system", "script")
                instanceof ObjectValue(Context script)) {
            script.register("args", given);
        }
    }
}
