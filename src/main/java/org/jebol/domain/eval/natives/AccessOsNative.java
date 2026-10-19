package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.ProcessPort;
import org.jebol.domain.host.ProcessPort.WhoTheProcessRunsAs;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AccessOsNative extends DefaultNative {

    private static final String THE_PROCESS_NUMBER = "pid";

    private static final Map<String, WhoTheProcessRunsAs> THE_IDENTITY_FIELDS = Map.of(
            "uid", WhoTheProcessRunsAs.REAL_USER,
            "euid", WhoTheProcessRunsAs.EFFECTIVE_USER,
            "gid", WhoTheProcessRunsAs.REAL_GROUP,
            "egid", WhoTheProcessRunsAs.EFFECTIVE_GROUP);

    private static final int TERMINATE = 15;

    private static final int A_PROCESS_AND_A_SIGNAL = 2;

    private final GrantedServices grantedServices;

    public AccessOsNative(GrantedServices grantedServices) {
        this.grantedServices = grantedServices;
    }

    @Override
    public String nativeName() {
        return "access-os";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.of("field", Set.of(WordValue.TYPE)),
                Parameter.belongTo("set", "value", Set.of(IntegerValue.TYPE, BlockValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("set");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyWordValue field = (AnyWordValue) arguments.getFirst();
            Optional<Value> setTo = readTheArgumentOf("set", 0, arguments, refinements);
            if (THE_PROCESS_NUMBER.equals(field.canonical())) {
                return setTo.map(asked -> signal(asked, evaluator.processes()))
                        .orElseGet(() -> IntegerValue.of(evaluator.machine().processNumber()));
            }
            WhoTheProcessRunsAs asked = Optional.ofNullable(THE_IDENTITY_FIELDS.get(field.canonical()))
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG, field));
            return setTo.isPresent()
                    ? refuseToChangeWhoTheProcessRunsAs(field, setTo.get())
                    : askWhoTheProcessRunsAs(field, asked, evaluator);
        };
    }

    private Value askWhoTheProcessRunsAs(AnyWordValue field, WhoTheProcessRunsAs asked,
                                         Evaluator evaluator) {
        long answered = evaluator.processes().identify(asked)
                .orElseThrow(() -> Raised.of(EvaluationFailure.NOT_HERE, field));
        return IntegerValue.of(answered);
    }

    private Value refuseToChangeWhoTheProcessRunsAs(AnyWordValue field, Value given) {
        if (!(given instanceof IntegerValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, given);
        }
        throw Raised.of(EvaluationFailure.NOT_HERE, field);
    }

    private Value signal(Value asked, ProcessPort processes) {
        if (asked instanceof IntegerValue(long process)) {
            return end(process, TERMINATE, processes);
        }
        List<Value> pair = ((AnyBlockValue) asked).remaining();
        if (pair.size() != A_PROCESS_AND_A_SIGNAL) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
        long process = readAWholeNumberOrRefuse(pair.get(0));
        int signal = (int) readAWholeNumberOrRefuse(pair.get(1));
        return end(process, signal, processes);
    }

    private long readAWholeNumberOrRefuse(Value given) {
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, given);
    }

    private Value end(long process, int signal, ProcessPort processes) {
        grantedServices.require(HostService.PROCESSES);
        try {
            if (!processes.isRunning(process)) {
                throw Raised.of(EvaluationFailure.PROCESS_NOT_FOUND, IntegerValue.of(process));
            }
            if (!processes.end(process, signal)) {
                throw Raised.of(EvaluationFailure.PERMISSION_DENIED);
            }
            return LogicValue.yes();
        } catch (FilePort.Denied refused) {
            throw refused.raise();
        }
    }
}
