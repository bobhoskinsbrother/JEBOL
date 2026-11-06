package org.jebol.domain.eval.compression;

final class CodecTraceHolder {

    static volatile CodecTrace current = CodecTrace.NONE;

    private CodecTraceHolder() {
    }

    static synchronized CodecTrace swap(CodecTrace trace) {
        CodecTrace previous = current;
        current = trace;
        return previous;
    }
}
