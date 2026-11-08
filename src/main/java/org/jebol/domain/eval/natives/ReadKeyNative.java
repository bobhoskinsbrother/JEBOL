package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.ConsoleKey;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Optional;

public class ReadKeyNative extends HostNative {

    private static final String EVENT_KEYS = "event-keys";

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
            Optional<ConsoleKey> read = evaluator.ports().console().readKey();
            if (read.isEmpty()) {
                return NoneValue.none();
            }
            ConsoleKey key = read.orElseThrow();
            if (key.isCtrlC()) {
                evaluator.takeEscape();
            }
            recordModifiers(evaluator, key);
            return answerFor(evaluator, key);
        };
    }

    private Value answerFor(Evaluator evaluator, ConsoleKey key) {
        if (key.character() != 0) {
            return CharacterValue.of(key.character());
        }
        if (key.virtualKey() != 0
                && readSystemField(evaluator, "catalog", EVENT_KEYS) instanceof AnyBlockValue named
                && key.virtualKey() <= named.remaining().size()) {
            return named.remaining().get(key.virtualKey() - 1);
        }
        return NoneValue.none();
    }

    private void recordModifiers(Evaluator evaluator, ConsoleKey key) {
        if (evaluator.systemContext().readValueAt("system", "state") instanceof ObjectValue state) {
            state.context().register("control?", LogicValue.of(key.control()));
            state.context().register("shift?", LogicValue.of(key.shift()));
            state.context().register("alt?", LogicValue.of(key.alt()));
        }
    }

    private Value readSystemField(Evaluator evaluator, String object, String field) {
        if (evaluator.systemContext().readValueAt("system", object) instanceof ObjectValue found
                && found.context().contains(field)) {
            return found.context().findSlotFor(field).value();
        }
        return NoneValue.none();
    }
}
