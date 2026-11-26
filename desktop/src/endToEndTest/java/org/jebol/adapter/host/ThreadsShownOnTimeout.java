package org.jebol.adapter.host;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ThreadsShownOnTimeout implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable thrown) throws Throwable {
        throw shownIfTimedOut(thrown);
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable thrown) throws Throwable {
        throw shownIfTimedOut(thrown);
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable thrown) throws Throwable {
        throw shownIfTimedOut(thrown);
    }

    private static Throwable shownIfTimedOut(Throwable thrown) {
        if (thrown instanceof TimeoutException) {
            System.err.println("Every thread when the time ran out:");
            System.err.println(everyThreadsStack());
        }
        return thrown;
    }

    private static String everyThreadsStack() {
        return Thread.getAllStackTraces().entrySet().stream()
                .map(ThreadsShownOnTimeout::describe)
                .collect(Collectors.joining("\n"));
    }

    private static String describe(Map.Entry<Thread, StackTraceElement[]> each) {
        Thread thread = each.getKey();
        String heading = "thread %s, %s".formatted(thread.getName(), thread.getState());
        return Stream.concat(Stream.of(heading), Stream.of(each.getValue()).map(frame -> "    at " + frame))
                .collect(Collectors.joining("\n"));
    }
}
