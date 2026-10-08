package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ReadKeyNative extends HostNative {

    private static final List<String> THE_MODIFIER_KEYS = List.of("control?", "shift?", "alt?");

    private static final int NO_KEY_CAME = 0;

    public ReadKeyNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "read-key";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.CONSOLE);
            int code = evaluator.console().readKey();
            noModifierKeyIsHeld(evaluator);
            return code < NO_KEY_CAME
                    ? NoneValue.none()
                    : CharacterValue.of(code);
        };
    }

    private void noModifierKeyIsHeld(Evaluator evaluator) {
        if (evaluator.systemContext().valueAt("system", "state") instanceof ObjectValue state) {
            THE_MODIFIER_KEYS.forEach(key -> state.context().register(key, LogicValue.of(false)));
        }
    }
}
