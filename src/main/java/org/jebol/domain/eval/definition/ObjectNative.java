package org.jebol.domain.eval.definition;

public class ObjectNative extends ObjectFromSpecNative {

    @Override
    public String name() {
        return "object";
    }

    @Override
    protected String whatTheSpecIsCalled() {
        return "spec";
    }
}
