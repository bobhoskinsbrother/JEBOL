package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Evaluator;
import org.jebol.domain.eval.OutputPort;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.util.List;

public abstract class OutputNative extends DefaultNative {

    protected abstract void write(OutputPort output, String text);

    @Override
    public List<Parameter> parameters() {
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
