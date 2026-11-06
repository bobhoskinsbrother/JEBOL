package org.jebol.domain.eval.compression.zstd;

public final class ZstdStreamBuffer {

    public final byte[] octets;
    public final int size;
    public int position;

    public ZstdStreamBuffer(byte[] octets, int size, int position) {
        this.octets = octets;
        this.size = size;
        this.position = position;
    }
}
