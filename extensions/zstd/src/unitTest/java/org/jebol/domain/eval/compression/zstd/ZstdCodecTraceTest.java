package org.jebol.domain.eval.compression.zstd;

import org.jebol.domain.eval.compression.CodecTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated
class ZstdCodecTraceTest {

    @Test
    @DisplayName("An installed trace hears the block splitter's estimates, and the default trace builds nothing")
    void installedTraceHearsTheSplitter() {
        byte[] input = ZstdTestInputs.make(ZstdTestInputs.TWO_SYMBOLS, 150000, 66, 0, 1437);
        List<String> heard = new ArrayList<>();
        CodecTrace previous = CodecTrace.install(message -> heard.add(message.get()));
        try {
            ZstdCompressionContext.compressOnce(input, 16);
        } finally {
            CodecTrace.install(previous);
        }
        assertThat(heard).contains("zstd split 0 894 1789: 5968 3756 2212");
        List<String> built = new ArrayList<>();
        CodecTrace.NONE.note(() -> {
            built.add("built");
            return "";
        });
        assertThat(built).isEmpty();
    }
}
