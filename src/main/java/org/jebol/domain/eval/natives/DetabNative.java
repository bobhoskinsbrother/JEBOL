package org.jebol.domain.eval.natives;

public class DetabNative extends TabbingNative {

    @Override
    public String nativeName() {
        return "detab";
    }

    @Override
    String tabbed(String text, String aTabsWorthOfSpaces) {
        return text.replace("\t", aTabsWorthOfSpaces);
    }
}
