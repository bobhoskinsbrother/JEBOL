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
        return List.of(Parameter.required("conditions", Set.of(Datatype.BLOCK)));
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
                    ? everyTypeHeld(conditions, evaluator, context)
                    : everyConditionHeld(conditions, evaluator, context);
        };
    }

    private Value everyTypeHeld(AnyBlockValue pairs, Evaluator evaluator, Context context) {
        List<Value> items = pairs.remaining();
        for (int at = 0; at < items.size(); at += 2) {
            Value subject = items.get(at);
            Value held = theValueNamedBy(subject, evaluator, context);
            if (at + 1 >= items.size()) {
                throw Raised.of(EvaluationFailure.MISSING_ARG);
            }
            if (!isOfType(held, items.get(at + 1), context)) {
                throw Raised.of(EvaluationFailure.WRONG_TYPE, subject);
            }
        }
        return LogicValue.of(true);
    }

    private Value theValueNamedBy(Value subject, Evaluator evaluator, Context context) {
        boolean namesAValue = subject instanceof WordValue || subject instanceof PathValue;
        if (!namesAValue) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, subject);
        }
        return evaluator.evaluateOrRaise(BlockValue.block(List.of(subject)), context);
    }

    private Value everyConditionHeld(
            AnyBlockValue conditions, Evaluator evaluator, Context context) {

        AnyBlockValue at = conditions;
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

    private boolean isOfType(Value held, Value type, Context context) {
        return switch (type) {
            case DatatypeValue wanted -> held.datatype() == wanted.represents();
            case TypesetValue set -> set.holds(held.datatype());
            case AnyWordValue word -> {
                Value resolved = context.knows(word.canonical())
                        ? context.slotFor(word.canonical()).value()
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
