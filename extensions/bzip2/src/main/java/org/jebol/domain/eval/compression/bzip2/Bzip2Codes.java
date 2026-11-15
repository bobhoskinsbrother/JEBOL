package org.jebol.domain.eval.compression.bzip2;

public final class Bzip2Codes {

    public static final int RUN = 0;
    public static final int FLUSH = 1;
    public static final int FINISH = 2;

    public static final int OK = 0;
    public static final int RUN_OK = 1;
    public static final int FLUSH_OK = 2;
    public static final int FINISH_OK = 3;
    public static final int STREAM_END = 4;
    public static final int SEQUENCE_ERROR = -1;
    public static final int PARAM_ERROR = -2;
    public static final int DATA_ERROR = -4;
    public static final int DATA_ERROR_MAGIC = -5;
    public static final int UNEXPECTED_EOF = -7;
    public static final int OUTBUFF_FULL = -8;

    private Bzip2Codes() {
    }
}
