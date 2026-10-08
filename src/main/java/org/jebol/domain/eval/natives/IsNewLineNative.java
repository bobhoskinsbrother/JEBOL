package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class IsNewLineNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "new-line?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("position", Set.of(BlockValue.TYPE, ParenValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            AnyBlockValue block = (AnyBlockValue) arguments.getFirst();
            return LogicValue.of(block.storage().breaksLineAt(block.index()));
        };
    }
}
