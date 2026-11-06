package org.jebol.domain.eval.compression;

import java.util.function.Supplier;

@FunctionalInterface
public interface CodecTrace {

    CodecTrace NONE = message -> {
    };

    void note(Supplier<String> message);

    static CodecTrace install(CodecTrace trace) {
        return CodecTraceHolder.swap(trace);
    }

    static CodecTrace current() {
        return CodecTraceHolder.current;
    }
}
