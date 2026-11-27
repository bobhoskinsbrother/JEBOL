package org.jebol.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public enum TestTarget {

    DESKTOP_LINUX(Runtime.JVM, "Linux"),
    DESKTOP_MAC(Runtime.JVM, "Mac"),
    DESKTOP_WINDOWS(Runtime.JVM, "Windows"),
    WEB_LINUX(Runtime.JVM, "Linux"),
    WEB_MAC(Runtime.JVM, "Mac"),
    WEB_WINDOWS(Runtime.JVM, "Windows"),
    ANDROID(Runtime.ART, ""),
    IOS(Runtime.NONE_YET, "");

    public static final String PROPERTY = "jebol.target";
    private static final String CARRIED_BY_A_TEST_PACKAGE = "/jebol-target";
    private static final String ARTS_OWN_NAME = "Dalvik";

    private enum Runtime { JVM, ART, NONE_YET }

    private final Runtime runtime;
    private final String systemNameStart;

    TestTarget(Runtime runtime, String systemNameStart) {
        this.runtime = runtime;
        this.systemNameStart = systemNameStart;
    }

    public static TestTarget current() {
        return TheOneThisRunNamed.TARGET;
    }

    public static TestTarget named(String given, String systemName, String virtualMachineName) {
        if (given == null || given.isBlank()) {
            throw new IllegalStateException("a test run names its target in the system property " + PROPERTY
                    + ", which the build sets, as one of " + Arrays.toString(values()));
        }
        TestTarget target = Arrays.stream(values())
                .filter(each -> each.name().equals(given.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(PROPERTY + " is " + given + ", which is not one of "
                        + Arrays.toString(values())));
        target.checkRunnableOn(systemName, virtualMachineName);
        return target;
    }

    private void checkRunnableOn(String systemName, String virtualMachineName) {
        boolean onArt = virtualMachineName != null && virtualMachineName.contains(ARTS_OWN_NAME);
        switch (runtime) {
            case NONE_YET -> throw new IllegalStateException(name()
                    + " has no test runner until goal 3 gives it one");
            case ART -> {
                if (!onArt) {
                    throw new IllegalStateException(name() + " runs on Android's runtime, and this is "
                            + virtualMachineName);
                }
            }
            case JVM -> {
                if (onArt) {
                    throw new IllegalStateException(name() + " runs on a desktop's JVM, and this is "
                            + virtualMachineName);
                }
                if (systemName == null || !systemName.startsWith(systemNameStart)) {
                    throw new IllegalStateException(name() + " runs on " + systemNameStart + ", and this is "
                            + systemName);
                }
            }
        }
    }

    private static final class TheOneThisRunNamed {

        private static final TestTarget TARGET = named(
                Optional.ofNullable(System.getProperty(PROPERTY)).orElseGet(TheOneThisRunNamed::readCarried),
                System.getProperty("os.name"),
                System.getProperty("java.vm.name"));

        private TheOneThisRunNamed() {
        }

        private static String readCarried() {
            try (InputStream carried = TestTarget.class.getResourceAsStream(CARRIED_BY_A_TEST_PACKAGE)) {
                return carried == null ? null : new String(carried.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }
}
