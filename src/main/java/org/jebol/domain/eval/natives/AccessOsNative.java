package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.ProcessPort.WhoTheProcessRunsAs;
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
                Parameter.required("field", Set.of(Datatype.WORD)),
                Parameter.belongingTo("set", "value", Set.of(Datatype.INTEGER, Datatype.BLOCK)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("set");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            WordValue field = (WordValue) arguments.getFirst();
            Optional<Value> setTo = argumentOf("set", 0, arguments, refinements);
            if (THE_PROCESS_NUMBER.equals(field.canonical())) {
                return setTo.map(this::signalled)
                        .orElseGet(() -> IntegerValue.of(ProcessHandle.current().pid()));
            }
            WhoTheProcessRunsAs asked = Optional.ofNullable(THE_IDENTITY_FIELDS.get(field.canonical()))
                    .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG, field));
            return setTo.isPresent()
                    ? refuseToChangeWhoTheProcessRunsAs(field, setTo.get())
                    : whoTheHostSaysTheProcessRunsAs(field, asked, evaluator);
        };
    }

    private Value whoTheHostSaysTheProcessRunsAs(WordValue field, WhoTheProcessRunsAs asked,
            Evaluator evaluator) {
        long answered = evaluator.processes().identity(asked)
                .orElseThrow(() -> Raised.of(EvaluationFailure.NOT_HERE, field));
        return IntegerValue.of(answered);
    }

    private Value refuseToChangeWhoTheProcessRunsAs(WordValue field, Value given) {
        if (!(given instanceof IntegerValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, given);
        }
        throw Raised.of(EvaluationFailure.NOT_HERE, field);
    }

    private Value signalled(Value asked) {
        if (asked instanceof IntegerValue(long process)) {
            return ended(process, TERMINATE);
        }
        List<Value> pair = ((BlockValue) asked).remaining();
        if (pair.size() != A_PROCESS_AND_A_SIGNAL) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, asked);
        }
        long process = wholeNumberOrRefuse(pair.get(0));
        int signal = (int) wholeNumberOrRefuse(pair.get(1));
        return ended(process, signal);
    }

    private long wholeNumberOrRefuse(Value given) {
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, given);
    }

    private Value ended(long process, int signal) {
        grantedServices.require(HostService.PROCESSES);
        ProcessHandle running = ProcessHandle.of(process)
                .orElseThrow(() -> Raised.of(EvaluationFailure.PROCESS_NOT_FOUND, IntegerValue.of(process)));
        boolean ended = signal == TERMINATE ? running.destroy() : running.destroyForcibly();
        if (!ended) {
            throw Raised.of(EvaluationFailure.PERMISSION_DENIED);
        }
        return LogicValue.yes();
    }
}
