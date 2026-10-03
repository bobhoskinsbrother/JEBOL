package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.ContinueSignal;
import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.LoopSignal;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ReturnSignal;
import org.jebol.domain.eval.ThrownSignal;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ErrorCategory;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class TryNative extends DefaultNative {

    @Override
    public String name() {
        return "try";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("block", Set.of(Datatype.BLOCK, Datatype.PAREN)),
                Parameter.belongingTo("with", "handler", Set.of()));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("all", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            evaluator.setSystemState("last-error", NoneValue.none());
            Value failure;
            try {
                return evaluator.evaluateOrRaise((BlockValue) arguments.getFirst(), context);
            } catch (Raised raised) {
                failure = raised.error();
            } catch (ThrownSignal | LoopSignal | ContinueSignal | ReturnSignal escaping) {
                if (!refinements.contains("all")) {
                    throw escaping;
                }
                failure = theErrorStandingFor(escaping);
            }
            evaluator.setSystemState("last-error", failure);
            return refinements.contains("with")
                    ? handled(failure, arguments.getLast(), evaluator, context)
                    : failure;
        };
    }

    private static ErrorValue theErrorStandingFor(RuntimeException escaping) {
        return switch (escaping) {
            case ThrownSignal thrown -> ErrorValue.about(ErrorCategory.THROW, "throw",
                    "a throw that nothing caught",
                    thrown.value(),
                    thrown.name().<Value>map(WordValue::of).orElseGet(NoneValue::none),
                    NoneValue.none());
            case LoopSignal stopped -> ErrorValue.of(ErrorCategory.THROW, "break",
                    "a break outside a loop");
            case ContinueSignal skipped -> ErrorValue.of(ErrorCategory.THROW, "continue",
                    "a continue outside a loop");
            case ReturnSignal returned -> ErrorValue.about(ErrorCategory.THROW, "return",
                    "a return outside a function",
                    returned.value() instanceof UnsetValue
                            ? NoneValue.none()
                            : returned.value());
            default -> throw escaping;
        };
    }

    private static Value handled(
            Value failure, Value handler, Evaluator evaluator, Context context) {

        return handler instanceof BlockValue block
                ? evaluator.evaluateOrRaise(block, context)
                : evaluator.applyFunction(handler, List.of(failure));
    }
}
