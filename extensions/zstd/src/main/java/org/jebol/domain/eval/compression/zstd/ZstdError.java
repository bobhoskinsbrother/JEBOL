package org.jebol.domain.eval.compression.zstd;

public enum ZstdError {
    GENERIC(1),
    PREFIX_UNKNOWN(10),
    FRAME_PARAMETER_UNSUPPORTED(14),
    FRAME_PARAMETER_WINDOW_TOO_LARGE(16),
    CORRUPTION_DETECTED(20),
    CHECKSUM_WRONG(22),
    LITERALS_HEADER_WRONG(24),
    DICTIONARY_CORRUPTED(30),
    DICTIONARY_WRONG(32),
    PARAMETER_UNSUPPORTED(40),
    PARAMETER_OUT_OF_BOUND(42),
    TABLE_LOG_TOO_LARGE(44),
    MAX_SYMBOL_VALUE_TOO_LARGE(46),
    MAX_SYMBOL_VALUE_TOO_SMALL(48),
    STAGE_WRONG(60),
    MEMORY_ALLOCATION(64),
    DST_SIZE_TOO_SMALL(70),
    SRC_SIZE_WRONG(72),
    DST_BUFFER_NULL(74),
    NO_FORWARD_PROGRESS_DEST_FULL(80),
    NO_FORWARD_PROGRESS_INPUT_EMPTY(82);

    private final int code;

    ZstdError(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    ZstdFailure failure() {
        return new ZstdFailure(this);
    }
}
