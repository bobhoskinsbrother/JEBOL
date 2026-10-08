package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class SwapAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "swap";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("series", "with");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value here = arguments.getFirst();
            Value there = arguments.get(1);
            return switch (here) {
                case GobValue gob -> refuseTheDatatype(gob);
                case StringValue text when there instanceof StringValue other ->
                        text.swapFirstItemWith(other);
                case BinaryValue bytes when there instanceof BinaryValue other ->
                        bytes.swapFirstItemWith(other);
                case BlockValue block when there instanceof BlockValue other ->
                        block.swapFirstItemWith(other);
                case Value anythingElse -> refuseTheArgument(anythingElse, "series1");
            };
        };
    }
}
