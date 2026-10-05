package org.jebol.domain.eval.definition;

public class WhetherLatin1Native extends WithinCodepointsNative {

    private static final int THE_LAST_LATIN1_CODEPOINT = 0xFF;

    @Override
    public String name() {
        return "latin1?";
    }

    @Override
    protected int highest() {
        return THE_LAST_LATIN1_CODEPOINT;
    }
}
