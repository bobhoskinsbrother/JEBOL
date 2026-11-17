package org.jebol.android;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class NotOnADevice implements ExecutionCondition {

    private static final String THE_LIST = "/not-on-a-device.txt";
    private static final String TESTS_THAT_EXHAUST_THE_HEAP = "exhausts-the-heap";
    private static final Map<String, String> LISTED = readTheList();

    private static Map<String, String> readTheList() {
        Map<String, String> listed = new HashMap<>();
        try (InputStream in = NotOnADevice.class.getResourceAsStream(THE_LIST)) {
            if (in == null) {
                throw new IllegalStateException(THE_LIST + " is not in the test APK");
            }
            BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                String kept = line.strip();
                if (kept.isEmpty() || kept.startsWith("#")) {
                    continue;
                }
                int gap = kept.indexOf(' ');
                listed.put(gap < 0 ? kept : kept.substring(0, gap), gap < 0 ? "" : kept.substring(gap).strip());
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return listed;
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (context.getTags().contains(TESTS_THAT_EXHAUST_THE_HEAP)) {
            return ConditionEvaluationResult.disabled("runs the heap out on purpose, which ends the instrumentation process");
        }
        Optional<String> reason = context.getTestClass()
                .flatMap(testClass -> reasonListedFor(testClass, context));
        return reason.map(ConditionEvaluationResult::disabled)
                .orElseGet(() -> ConditionEvaluationResult.enabled("not listed in " + THE_LIST));
    }

    private static Optional<String> reasonListedFor(Class<?> testClass, ExtensionContext context) {
        for (Class<?> each = testClass; each != null; each = each.getEnclosingClass()) {
            String whole = LISTED.get(each.getName());
            if (whole != null) {
                return Optional.of(whole);
            }
        }
        return context.getTestMethod()
                .map(method -> LISTED.get(testClass.getName() + "#" + method.getName()));
    }
}
