package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.ServiceRefusal;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

import java.util.Locale;

public abstract class ExtensionPointNative extends DefaultNative {

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            throw Raised.of(EvaluationFailure.NO_SERVICE,
                    nativeName() + " calls code written in C, which is " + theRefusalInWords());
        };
    }

    private String theRefusalInWords() {
        return ServiceRefusal.NEVER_PORTABLE.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
