package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;

import java.util.Set;

public class UnprotectNative extends ProtectingNative {

    private static final boolean UNPROTECTED = false;

    @Override
    public String nativeName() {
        return "unprotect";
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("deep", "words", "values");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            protectionChanged(arguments.getFirst(), UNPROTECTED, refinements);
            return arguments.getFirst();
        };
    }
}
