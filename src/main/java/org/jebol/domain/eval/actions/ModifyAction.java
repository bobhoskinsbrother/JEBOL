package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ports.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.WordValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.AnyWordValue;

import java.util.List;
import java.util.Set;

public class ModifyAction extends PortAction {

    private static final Set<String> MODES_FILE_TAKES = Set.of(
            "owner-read", "owner-write", "owner-execute",
            "group-read", "group-write", "group-execute",
            "world-read", "world-write", "world-execute");

    public ModifyAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String nativeName() {
        return "modify";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("target", Set.of(PortValue.TYPE, FileValue.TYPE)),
                Parameter.of("field", Set.of(WordValue.TYPE, NoneValue.TYPE)),
                Parameter.of("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                modify(arguments.getFirst(), arguments.get(1), arguments.get(2));
    }

    private Value modify(Value target, Value field, Value given) {
        if (target instanceof PortValue port && port.schemeName().equals("crypt")) {
            ports.cryptPort().refuseWhenClosed(port);
            return field instanceof AnyWordValue setting
                    ? ports.cryptPort().modify(port, setting.canonical(), given)
                    : port;
        }
        if (target instanceof FileValue || target instanceof PortValue port && port.isAFile()) {
            return modifyFile(target, field);
        }
        return setConsoleMode(target, field, given);
    }

    private Value modifyFile(Value target, Value field) {
        if (field instanceof AnyWordValue mode && !MODES_FILE_TAKES.contains(mode.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, field);
        }
        return target instanceof PortValue ? LogicValue.of(true) : NoneValue.none();
    }

    private Value setConsoleMode(Value target, Value field, Value given) {
        if (!(field instanceof AnyWordValue mode) || !ports.consoleModes().contains(mode.canonical())) {
            throw Raised.of(EvaluationFailure.BAD_FILE_MODE, field);
        }
        if (!(given instanceof LogicValue)) {
            throw Raised.of(EvaluationFailure.INVALID_VALUE_FOR, given, mode);
        }
        if (target instanceof PortValue port) {
            port.setField(mode.canonical(), given);
        }
        return given;
    }
}
