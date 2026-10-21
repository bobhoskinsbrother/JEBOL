package org.jebol.adapter.host;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.JavaMachine;
import org.jebol.application.ThreadPause;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.JvmDigests;
import org.jebol.domain.host.OutputPort;

public final class JavaHost {

    public HostPorts ports() {
        return HostPorts.none()
                .withCarriedFiles(new ClasspathFiles())
                .withDigests(new JvmDigests())
                .withMachine(new JavaMachine())
                .withPause(new ThreadPause());
    }

    public Interpreter create() {
        return Interpreter.createOn(ports(), OutputPort.discard(), Bounds.standard());
    }

    public Interpreter withBounds(Bounds bounds) {
        return Interpreter.createOn(ports(), OutputPort.discard(), bounds);
    }

    public Interpreter createWithOutput(OutputPort output) {
        return Interpreter.createOn(ports(), output, Bounds.standard());
    }

    public Interpreter createWithOutput(OutputPort output, Bounds bounds) {
        return Interpreter.createOn(ports(), output, bounds);
    }
}
