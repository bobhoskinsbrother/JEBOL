package org.jebol.domain.eval;

class LzwLevelsFromTheSourceTest extends CompressionLevelsOfOneMethod {

    @Override
    protected String method() {
        return "lzw";
    }

    @Override
    protected int top() {
        return 9;
    }

    @Override
    protected int levelNobodyAskedFor() {
        return 9;
    }
}
