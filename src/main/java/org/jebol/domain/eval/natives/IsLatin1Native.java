package org.jebol.domain.eval.natives;

public class IsLatin1Native extends WithinCodepointsNative {

    private static final int LAST_LATIN1_CODEPOINT = 0xFF;

    @Override
    public String nativeName() {
        return "latin1?";
    }

    @Override
    protected int highest() {
        return LAST_LATIN1_CODEPOINT;
    }
}
