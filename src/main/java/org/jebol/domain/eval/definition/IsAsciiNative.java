package org.jebol.domain.eval.definition;

public class IsAsciiNative extends WithinCodepointsNative {

    private static final int THE_LAST_ASCII_CODEPOINT = 0x7F;

    @Override
    public String name() {
        return "ascii?";
    }

    @Override
    protected int highest() {
        return THE_LAST_ASCII_CODEPOINT;
    }
}
