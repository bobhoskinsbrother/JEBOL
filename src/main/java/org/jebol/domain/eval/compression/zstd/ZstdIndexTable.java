package org.jebol.domain.eval.compression.zstd;

final class ZstdIndexTable {

    private final ZstdTableRegion region;
    private final int offset;
    private final int size;

    ZstdIndexTable(ZstdTableRegion region, int offset, int size) {
        this.region = region;
        this.offset = offset;
        this.size = size;
    }

    int size() {
        return size;
    }

    int get(int at) {
        return region.get(offset + at);
    }

    long getIndex(int at) {
        return region.get(offset + at) & 0xFFFFFFFFL;
    }

    void set(int at, int value) {
        region.set(offset + at, value);
    }

    void setIndex(int at, long index) {
        region.set(offset + at, (int) index);
    }
}
