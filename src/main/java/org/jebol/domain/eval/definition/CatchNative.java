package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.HaltRequested;
import org.jebol.domain.eval.QuitRequested;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ThrownSignal;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CatchNative extends DefaultNative {

    private static final int WHERE_THE_NAMES_ARRIVE = 1;

    @Override
    public String name() {
        return "catch";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("block", Set.of(Datatype.BLOCK)),
                Parameter.belongingTo("name", "word", Set.of(Datatype.WORD, Datatype.BLOCK)),
                Parameter.belongingTo("with", "callback", Set.of()));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("name", "all", "quit", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value handled;
            Value carriedName = NoneValue.none();
            try {
                return evaluator.evaluateOrRaise((BlockValue) arguments.getFirst(), context);
            } catch (ThrownSignal thrown) {
                if (letsTheThrowPass(thrown, arguments, refinements)) {
                    throw thrown;
                }
                handled = thrown.value();
                carriedName = thrown.name().<Value>map(WordValue::of).orElseGet(NoneValue::none);
            } catch (QuitRequested quit) {
                if (!refinements.contains("quit")) {
                    throw quit;
                }
                handled = quit.answer();
                evaluator.setSystemState("quit?", LogicValue.of(true));
            } catch (HaltRequested halted) {
                if (!refinements.contains("quit")) {
                    throw halted;
                }
                handled = UnsetValue.unset();
            }
            evaluator.setSystemState("last-result", handled);
            if (!refinements.contains("with")) {
                return handled;
            }
            if (arguments.getLast() instanceof BlockValue block) {
                Value answered = evaluator.evaluateOrRaise(block, context);
                evaluator.setSystemState("last-result", answered);
                return answered;
            }
            return evaluator.applyToCaught(arguments.getLast(), handled, carriedName);
        };
    }

    private boolean letsTheThrowPass(
            ThrownSignal thrown, List<Value> arguments, Set<String> refinements) {

        boolean catchesQuitOnly = refinements.contains("quit")
                && !refinements.contains("name")
                && !refinements.contains("all");
        return catchesQuitOnly
                || (!refinements.contains("all")
                        && !answersTo(thrown, expectedNames(arguments, refinements)));
    }

    private Set<String> expectedNames(List<Value> arguments, Set<String> refinements) {
        if (!refinements.contains("name") || arguments.size() <= WHERE_THE_NAMES_ARRIVE) {
            return Set.of();
        }
        return switch (arguments.get(WHERE_THE_NAMES_ARRIVE)) {
            case WordValue single -> Set.of(single.canonical());
            case BlockValue several -> several.remaining().stream()
                    .filter(WordValue.class::isInstance)
                    .map(WordValue.class::cast)
                    .map(WordValue::canonical)
                    .collect(Collectors.toSet());
            default -> Set.of();
        };
    }

    private boolean answersTo(ThrownSignal thrown, Set<String> expected) {
        return thrown.name()
                .map(expected::contains)
                .orElseGet(expected::isEmpty);
    }
}
