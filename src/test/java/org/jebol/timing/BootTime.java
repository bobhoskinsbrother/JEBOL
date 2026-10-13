package org.jebol.timing;

import org.jebol.application.Interpreter;

import java.util.Arrays;

public final class BootTime {

    private BootTime() {
    }

    private static final int RUNS_WHEN_NONE_ASKED_FOR = 20;

    static void main(String[] argued) {
        int runs = argued.length > 0 ? Integer.parseInt(argued[0]) : RUNS_WHEN_NONE_ASKED_FOR;
        long first = oneCreate();
        oneCreate();
        long[] warm = new long[runs];
        for (int run = 0; run < runs; run++) {
            warm[run] = oneCreate();
        }
        Arrays.sort(warm);
        System.out.printf("first %.1f ms%n", milliseconds(first));
        System.out.printf("median %.1f ms%n", milliseconds(warm[runs / 2]));
        System.out.printf("p95 %.1f ms%n", milliseconds(warm[Math.min(runs - 1, (runs * 95) / 100)]));
        System.out.printf("min %.1f ms%n", milliseconds(warm[0]));
        System.out.printf("max %.1f ms%n", milliseconds(warm[runs - 1]));
    }

    private static long oneCreate() {
        long before = System.nanoTime();
        Interpreter.create();
        return System.nanoTime() - before;
    }

    private static double milliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }
}
