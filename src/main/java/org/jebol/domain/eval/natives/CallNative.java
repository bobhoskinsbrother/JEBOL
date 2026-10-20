package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.security.PolicyAccess;
import org.jebol.domain.eval.security.PolicyClass;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GetPathValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.TypesetValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.GetWordValue;
import org.jebol.domain.value.WordValue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CallNative extends HostNative {

    public CallNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "call";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        Set<Datatype> aRedirection = Set.of(StringValue.TYPE, BinaryValue.TYPE, FileValue.TYPE, NoneValue.TYPE);
        return List.of(
                Parameter.of("command", TypesetValue.ANY_STRING.membersAnd(BlockValue.TYPE)),
                Parameter.belongTo("input", "in", aRedirection),
                Parameter.belongTo("output", "out", aRedirection),
                Parameter.belongTo("error", "err", aRedirection));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("wait", "console", "shell", "info", "input", "output", "error");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.PROCESSES);
            evaluator.policies().require(PolicyClass.CALL, PolicyAccess.EXECUTE, Optional.of(arguments.getFirst()));
            ProgramCalling calling = new ProgramCalling(
                    readTheCommandWordsOf(arguments.getFirst(), evaluator, context), refinements,
                    readTheArgumentOf("input", 0, arguments, refinements),
                    readTheArgumentOf("output", 0, arguments, refinements),
                    readTheArgumentOf("error", 0, arguments, refinements));
            return runThroughTheFileSystem(() -> calling.answerThrough(evaluator.ports().processes(), evaluator));
        };
    }

    private List<String> readTheCommandWordsOf(Value command, Evaluator evaluator, Context context) {
        if (!(command instanceof BlockValue block)) {
            return List.of(((AnyStringValue) command).text());
        }
        List<Value> items = block.remaining();
        if (items.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT);
        }
        return items.stream().map(item -> readTheCommandWordOf(item, evaluator, context)).toList();
    }

    private String readTheCommandWordOf(Value item, Evaluator evaluator, Context context) {
        Value resolved = item;
        if (item instanceof GetWordValue word) {
            resolved = word.findTheBoundSlot().value();
        } else if (item instanceof GetPathValue path) {
            resolved = evaluator.evaluateOrRaise(BlockValue.of(List.of(path)), context);
        }
        return switch (resolved) {
            case AnyStringValue text -> text.text();
            case WordValue word -> word.spelling();
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    Molder.mold(resolved) + " names nothing a command line can hold");
        };
    }
}
