package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class WhetherATerminalNative extends DefaultNative {

    @Override
    public String name() {
        return "tty?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(evaluator.console().isATerminal());
    }
}
