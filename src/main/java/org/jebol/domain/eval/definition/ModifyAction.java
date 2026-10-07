package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.Ports;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Set;

public class ModifyAction extends PortAction {

    private static final Set<String> THE_MODES_A_FILE_TAKES = Set.of(
            "owner-read", "owner-write", "owner-execute",
            "group-read", "group-write", "group-execute",
            "world-read", "world-write", "world-execute");

    public ModifyAction(GrantedServices granted, Ports ports) {
        super(granted, ports);
    }

    @Override
    public String name() {
        return "modify";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("target", Set.of(Datatype.PORT, Datatype.FILE)),
                Parameter.required("field", Set.of(Datatype.WORD, Datatype.NONE)),
                Parameter.required("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> evaluator
                .theRebolActorsAnswer(name(), arguments, Set.of())
                .orElseGet(() -> modified(arguments.getFirst(), arguments.get(1), arguments.get(2)));
    }

    private Value modified(Value target, Value field, Value given) {
        if (target instanceof PortValue port && port.schemeName().equals("crypt")) {
            ports.cryptPort().refuseWhenClosed(port);
            return field instanceof WordValue setting
                    ? ports.cryptPort().modify(port, setting.canonical(), given)
                    : port;
        }
        if (target.datatype() == Datatype.FILE || target instanceof PortValue port && port.isAFile()) {
            return aFileModified(target, field);
        }
        return aConsoleModeSet(target, field, given);
    }

    private Value aFileModified(Value target, Value field) {
        if (field instanceof WordValue mode && !THE_MODES_A_FILE_TAKES.contains(mode.canonical())) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, field);
        }
        return target instanceof PortValue ? LogicValue.of(true) : NoneValue.none();
    }

    private Value aConsoleModeSet(Value target, Value field, Value given) {
        if (!(field instanceof WordValue mode) || !ports.consoleModes().contains(mode.canonical())) {
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
