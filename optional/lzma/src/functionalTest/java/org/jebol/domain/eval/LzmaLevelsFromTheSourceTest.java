package org.jebol.domain.eval;

class LzmaLevelsFromTheSourceTest extends CompressionLevelsOfOneMethod {

    @Override
    protected String method() {
        return "lzma";
    }

    @Override
    protected int top() {
        return 9;
    }

    @Override
    protected int levelNobodyAskedFor() {
        return 5;
    }
}
