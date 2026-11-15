package org.jebol.domain.eval;

class CrushLevelsFromTheSourceTest extends CompressionLevelsOfOneMethod {

    @Override
    protected String method() {
        return "crush";
    }

    @Override
    protected int top() {
        return 2;
    }

    @Override
    protected int levelNobodyAskedFor() {
        return 2;
    }
}
