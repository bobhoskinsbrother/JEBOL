package org.jebol.domain.eval.natives;

public class IsAsciiNative extends WithinCodepointsNative {

    private static final int LAST_ASCII_CODEPOINT = 0x7F;

    @Override
    public String nativeName() {
        return "ascii?";
    }

    @Override
    protected int highest() {
        return LAST_ASCII_CODEPOINT;
    }
}
