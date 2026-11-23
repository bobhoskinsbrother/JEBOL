package org.jebol.application;

import org.jebol.adapter.host.JavaSecureTransport;
import org.jebol.adapter.host.JavaSocket;
import org.jebol.adapter.host.WakeablePause;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.OutputPort;

import java.time.Duration;

public final class AnInterpreter {

    private static final Duration WITHIN_ANY_STRETCH = Duration.ofDays(1);
    public static final char WINDOWS_SEPARATOR = '\\';
    public static final char POSIX_SEPARATOR = '/';

    private AnInterpreter() {
    }

    public static Interpreter create() {
        return createWithOutput(OutputPort.discard(), Bounds.standard());
    }

    public static Interpreter withBounds(Bounds bounds) {
        return createWithOutput(OutputPort.discard(), bounds);
    }

    public static Interpreter createWithOutput(OutputPort output) {
        return createWithOutput(output, Bounds.standard());
    }

    public static Interpreter createWithOutput(OutputPort output, Bounds bounds) {
        return Interpreter.createOn(PlatformUnderTest.current().ports(), output, stretchForThePlatform(bounds));
    }

    public static Interpreter writingLocalFilesForWindows() {
        return writingLocalFilesWith(WINDOWS_SEPARATOR);
    }

    public static Interpreter writingLocalFilesForPosix() {
        return writingLocalFilesWith(POSIX_SEPARATOR);
    }

    public static HostPorts portsWritingLocalFilesWith(char separator) {
        HostPorts ports = standardPorts();
        return ports.withMachine(new AMachineWithItsOwnSeparator(ports.machine(), separator));
    }

    private static Interpreter writingLocalFilesWith(char separator) {
        return Interpreter.createOn(portsWritingLocalFilesWith(separator), OutputPort.discard(),
                stretchForThePlatform(Bounds.standard()));
    }

    public static Bounds stretchForThePlatform(Bounds bounds) {
        int factor = PlatformUnderTest.current().timeLimitFactor();
        Duration limit = bounds.timeLimit();
        if (factor == 1 || limit.compareTo(WITHIN_ANY_STRETCH) > 0) {
            return bounds;
        }
        return bounds.withTimeLimit(limit.multipliedBy(factor));
    }

    public static JavaSocket socket() {
        return new JavaSocket(PlatformUnderTest.current().hostNames());
    }

    public static HostPorts standardPorts() {
        return PlatformUnderTest.current().ports()
                .withSecureTransport(new JavaSecureTransport())
                .withPause(new WakeablePause());
    }
}
