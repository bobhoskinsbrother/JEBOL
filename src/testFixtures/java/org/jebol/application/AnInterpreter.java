package org.jebol.application;

import org.jebol.adapter.host.JavaHost;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.OutputPort;

public final class AnInterpreter {

    private static final JavaHost THE_JVM_HOST = new JavaHost();

    private AnInterpreter() {
    }

    public static Interpreter create() {
        return THE_JVM_HOST.create();
    }

    public static Interpreter withBounds(Bounds bounds) {
        return THE_JVM_HOST.withBounds(bounds);
    }

    public static Interpreter createWithOutput(OutputPort output) {
        return THE_JVM_HOST.createWithOutput(output);
    }

    public static Interpreter createWithOutput(OutputPort output, Bounds bounds) {
        return THE_JVM_HOST.createWithOutput(output, bounds);
    }

    public static HostPorts standardPorts() {
        return THE_JVM_HOST.ports();
    }
}
