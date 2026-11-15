package org.jebol.application;

import org.jebol.adapter.host.JavaSecureTransport;
import org.jebol.adapter.host.WakeablePause;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.OutputPort;

public final class AnInterpreter {

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
        return Interpreter.createOn(PlatformUnderTest.current().ports(), output, bounds);
    }

    public static HostPorts standardPorts() {
        return PlatformUnderTest.current().ports()
                .withSecureTransport(new JavaSecureTransport())
                .withPause(new WakeablePause());
    }
}
