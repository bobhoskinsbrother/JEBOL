package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.List;
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
        Set<Datatype> aRedirection = Set.of(Datatype.STRING, Datatype.BINARY, Datatype.FILE, Datatype.NONE);
        return List.of(
                Parameter.required("command", Typeset.ANY_STRING.membersAnd(Datatype.BLOCK)),
                Parameter.belongingTo("input", "in", aRedirection),
                Parameter.belongingTo("output", "out", aRedirection),
                Parameter.belongingTo("error", "err", aRedirection));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("wait", "console", "shell", "info", "input", "output", "error");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.PROCESSES);
            ProgramCalling calling = new ProgramCalling(
                    commandWordsOf(arguments.getFirst(), evaluator, context), refinements,
                    argumentOf("input", 0, arguments, refinements),
                    argumentOf("output", 0, arguments, refinements),
                    argumentOf("error", 0, arguments, refinements));
            return throughTheFileSystem(() -> calling.answerThrough(evaluator.processes(), evaluator));
        };
    }

    private List<String> commandWordsOf(Value command, Evaluator evaluator, Context context) {
        if (!(command instanceof BlockValue block) || command.datatype() != Datatype.BLOCK) {
            return List.of(((StringValue) command).text());
        }
        List<Value> items = block.remaining();
        if (items.isEmpty()) {
            throw Raised.of(EvaluationFailure.TOO_SHORT);
        }
        return items.stream().map(item -> commandWordOf(item, evaluator, context)).toList();
    }

    private String commandWordOf(Value item, Evaluator evaluator, Context context) {
        Value resolved = item;
        if (item instanceof WordValue word && word.datatype() == Datatype.GET_WORD) {
            resolved = word.boundSlot().value();
        } else if (item instanceof BlockValue path && path.datatype() == Datatype.GET_PATH) {
            resolved = evaluator.evaluateOrRaise(BlockValue.block(List.of(path)), context);
        }
        return switch (resolved) {
            case StringValue text -> text.text();
            case WordValue word when word.datatype() == Datatype.WORD -> word.spelling();
            default -> throw Raised.of(EvaluationFailure.INVALID_ARG,
                    Molder.mold(resolved) + " names nothing a command line can hold");
        };
    }
}
