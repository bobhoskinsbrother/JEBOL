package org.jebol.domain.eval.natives;

public class EntabNative extends TabbingNative {

    @Override
    public String nativeName() {
        return "entab";
    }

    @Override
    String tab(String text, String aTabsWorthOfSpaces) {
        return text.replace(aTabsWorthOfSpaces, "\t");
    }
}
