package org.jebol.timing;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;

import java.util.Arrays;

public final class BootTime {

    private BootTime() {
    }

    private static final int RUNS_WHEN_NONE_ASKED_FOR = 20;

    static void main(String[] argued) {
        int runs = argued.length > 0 ? Integer.parseInt(argued[0]) : RUNS_WHEN_NONE_ASKED_FOR;
        long first = timeOneCreate();
        timeOneCreate();
        long[] warm = new long[runs];
        for (int run = 0; run < runs; run++) {
            warm[run] = timeOneCreate();
        }
        Arrays.sort(warm);
        System.out.printf("first %.1f ms%n", convertToMilliseconds(first));
        System.out.printf("median %.1f ms%n", convertToMilliseconds(warm[runs / 2]));
        System.out.printf("p95 %.1f ms%n", convertToMilliseconds(warm[Math.min(runs - 1, (runs * 95) / 100)]));
        System.out.printf("min %.1f ms%n", convertToMilliseconds(warm[0]));
        System.out.printf("max %.1f ms%n", convertToMilliseconds(warm[runs - 1]));
    }

    private static long timeOneCreate() {
        long before = System.nanoTime();
        AnInterpreter.create();
        return System.nanoTime() - before;
    }

    private static double convertToMilliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000.0;
    }
}
