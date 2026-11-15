package org.jebol.domain.eval.compression.brotli;

import org.jebol.domain.eval.CompressionLevelsOfOneMethod;

class BrotliLevelsFromTheSourceTest extends CompressionLevelsOfOneMethod {

    @Override
    protected String method() {
        return "br";
    }

    @Override
    protected int top() {
        return 11;
    }

    @Override
    protected int levelNobodyAskedFor() {
        return 6;
    }
}
