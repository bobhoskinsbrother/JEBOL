package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public abstract class OutputNative extends DefaultNative {

    protected abstract void write(OutputPort output, String text);

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            write(evaluator.output(), forOutput(arguments.getFirst(), evaluator));
            return UnsetValue.unset();
        };
    }

    private String forOutput(Value value, Evaluator evaluator) {
        return Molder.form(value instanceof BlockValue block && block.datatype() == Datatype.BLOCK
                ? BlockValue.block(evaluator.evaluateEachOrRaise(block, evaluator.systemContext()))
                : value);
    }
}
