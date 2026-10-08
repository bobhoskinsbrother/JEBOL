package org.jebol.domain.eval.definition;

public class ObjectNative extends ObjectFromSpecNative {

    @Override
    public String nativeName() {
        return "object";
    }

    @Override
    protected String whatTheSpecIsCalled() {
        return "spec";
    }
}
