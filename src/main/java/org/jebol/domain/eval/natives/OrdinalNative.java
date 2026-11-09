package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ports.TcpPort;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;

import java.util.List;

public abstract class OrdinalNative extends DefaultNative {

    protected abstract int position();

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("series");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> TcpPort.isTcp(arguments.getFirst())
                ? TcpPort.takeAccepted((PortValue) arguments.getFirst(), IntegerValue.of(position()))
                : arguments.getFirst().pick(position());
    }
}
