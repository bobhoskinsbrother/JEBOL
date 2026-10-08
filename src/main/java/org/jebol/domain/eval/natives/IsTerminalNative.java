package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class IsTerminalNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "tty?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of();
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(evaluator.console().isATerminal());
    }
}
