package org.jebol.domain.eval.compression.zstd;

import java.util.Arrays;

final class ZstdTableRegion {

    private static final int PAGE_LOG = 16;
    private static final int PAGE_SIZE = 1 << PAGE_LOG;
    private static final int PAGE_MASK = PAGE_SIZE - 1;

    private int[][] pages = new int[0][];

    int get(int at) {
        int page = at >>> PAGE_LOG;
        if (page >= pages.length) {
            return 0;
        }
        int[] entries = pages[page];
        return entries == null ? 0 : entries[at & PAGE_MASK];
    }

    void set(int at, int value) {
        int page = at >>> PAGE_LOG;
        if (page >= pages.length) {
            if (value == 0) {
                return;
            }
            pages = Arrays.copyOf(pages, page + 1);
        }
        int[] entries = pages[page];
        if (entries == null) {
            if (value == 0) {
                return;
            }
            entries = new int[PAGE_SIZE];
            pages[page] = entries;
        }
        entries[at & PAGE_MASK] = value;
    }

    void zero(int from, int to) {
        int at = from;
        while (at < to) {
            int page = at >>> PAGE_LOG;
            if (page >= pages.length) {
                return;
            }
            int pageEnd = Math.min(to, (page + 1) << PAGE_LOG);
            int[] entries = pages[page];
            if (entries != null) {
                if ((at & PAGE_MASK) == 0 && pageEnd - at == PAGE_SIZE) {
                    pages[page] = null;
                } else {
                    Arrays.fill(entries, at & PAGE_MASK, ((pageEnd - 1) & PAGE_MASK) + 1, 0);
                }
            }
            at = pageEnd;
        }
    }

    void forget() {
        pages = new int[0][];
    }
}
