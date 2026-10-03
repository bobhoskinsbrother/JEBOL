package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class WhetherANewLineNative extends DefaultNative {

    @Override
    public String name() {
        return "new-line?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("position", Set.of(Datatype.BLOCK, Datatype.PAREN)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            BlockValue block = (BlockValue) arguments.getFirst();
            return LogicValue.of(block.storage().breaksLineAt(block.index()));
        };
    }
}
