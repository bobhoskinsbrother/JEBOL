package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.EffectRefusal;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ShowNative extends ScreenNative {

    public ShowNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "show";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("gob", Set.of(GobValue.TYPE, NoneValue.TYPE, BlockValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            EffectRefusal.refuseAnyUnder(listGobsToBeShown(arguments.getFirst(), evaluator),
                    evaluator.systemContext().readValueAt("system", "dialects", "draw"));
            if (arguments.getFirst() instanceof GobValue gob) {
                runThroughScreen(() -> evaluator.ports().screen().show(gob));
            }
            return arguments.getFirst();
        };
    }

    private static List<GobValue> listGobsToBeShown(Value given, Evaluator evaluator) {
        return switch (given) {
            case GobValue gob -> List.of(gob);
            case AnyBlockValue gobs -> gobs.remaining().stream()
                    .filter(GobValue.class::isInstance)
                    .map(GobValue.class::cast)
                    .toList();
            default -> evaluator.systemContext().readValueAt("system", "view", "screen-gob")
                    instanceof GobValue root
                    ? List.of(root)
                    : List.of();
        };
    }
}
