package org.jebol.domain.eval.definition;

public class ContextNative extends ObjectFromSpecNative {

    @Override
    public String name() {
        return "context";
    }

    @Override
    protected String whatTheSpecIsCalled() {
        return "body";
    }
}
