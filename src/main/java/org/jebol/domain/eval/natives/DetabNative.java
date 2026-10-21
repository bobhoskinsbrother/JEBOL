package org.jebol.domain.eval.natives;

public class DetabNative extends TabbingNative {

    @Override
    public String nativeName() {
        return "detab";
    }

    @Override
    String tab(String text, String spacesForTab) {
        return text.replace("\t", spacesForTab);
    }
}
