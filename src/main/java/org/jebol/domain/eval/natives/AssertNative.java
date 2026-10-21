package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class AssertNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "assert";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("conditions", Set.of(BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("type");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue conditions = (AnyBlockValue) arguments.getFirst();
            return refinements.contains("type")
                    ? assertEveryType(conditions, evaluator, context)
                    : assertEveryCondition(conditions, evaluator, context);
        };
    }

    private Value assertEveryType(AnyBlockValue pairs, Evaluator evaluator, Context context) {
        List<Value> items = pairs.remaining();
        for (int at = 0; at < items.size(); at += 2) {
            Value subject = items.get(at);
            Value held = readValueNamedBy(subject, evaluator, context);
            if (at + 1 >= items.size()) {
                throw Raised.of(EvaluationFailure.MISSING_ARG);
            }
            if (!isOfType(held, items.get(at + 1), context)) {
                throw Raised.of(EvaluationFailure.WRONG_TYPE, subject);
            }
        }
        return LogicValue.of(true);
    }

    private Value readValueNamedBy(Value subject, Evaluator evaluator, Context context) {
        boolean namesValue = subject instanceof WordValue || subject instanceof PathValue;
        if (!namesValue) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, subject);
        }
        return evaluator.evaluateOrRaise(BlockValue.of(List.of(subject)), context);
    }

    private Value assertEveryCondition(
            AnyBlockValue conditions, Evaluator evaluator, Context context) {

        AnyBlockValue at = conditions;
        while (!at.atTail()) {
            Evaluator.Step step = evaluator.evaluateNextOrRaise(at, context);
            at = at.atIndex(step.nextIndex());
            if (!step.value().isTruthy()) {
                throw Raised.of(EvaluationFailure.ASSERT_FAILED,
                        BlockValue.of(conditions.remaining()));
            }
        }
        return LogicValue.of(true);
    }

    private boolean isOfType(Value held, Value type, Context context) {
        return switch (type) {
            case Datatype wanted -> held.datatype() == wanted;
            case TypesetValue set -> set.holds(held.datatype());
            case AnyWordValue word -> {
                Value resolved = context.contains(word.canonical())
                        ? context.findSlotFor(word.canonical()).value()
                        : NoneValue.none();
                yield resolved != type && isOfType(held, resolved, context);
            }
            case AnyBlockValue any -> any.remaining().stream()
                    .anyMatch(one -> isOfType(held, one, context));
            default -> throw Raised.of(EvaluationFailure.EXPECT_ARG,
                    "assert/type wants a datatype, not " + type.datatype().literalSpelling());
        };
    }
}
