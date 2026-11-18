package org.jebol.application;

import java.time.Duration;

final class HeapInUse {

    private static final long COLLECTION_TRUSTED_FOR = Duration.ofMillis(250).toNanos();

    private final long ceiling;
    private long collectedAt;
    private boolean overAfterCollecting;
    private boolean collectedOnce;

    HeapInUse(long ceiling) {
        this.ceiling = ceiling;
    }

    boolean isOver() {
        if (measure() <= ceiling) {
            return false;
        }
        long now = System.nanoTime();
        if (collectedOnce && now - collectedAt < COLLECTION_TRUSTED_FOR) {
            return overAfterCollecting;
        }
        Runtime.getRuntime().gc();
        collectedOnce = true;
        collectedAt = System.nanoTime();
        overAfterCollecting = measure() > ceiling;
        return overAfterCollecting;
    }

    private static long measure() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
