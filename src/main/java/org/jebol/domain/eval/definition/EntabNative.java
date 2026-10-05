package org.jebol.domain.eval.definition;

public class EntabNative extends TabbingNative {

    @Override
    public String name() {
        return "entab";
    }

    @Override
    String tabbed(String text, String aTabsWorthOfSpaces) {
        return text.replace(aTabsWorthOfSpaces, "\t");
    }
}
