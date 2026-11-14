package org.jebol.adapter.host;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.Crossing;
import org.jebol.domain.host.HostPorts;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.TaskPort;

import java.util.function.Supplier;

public final class ThreadTasks implements TaskPort {

    private final Bounds bounds;
    private final Supplier<HostPorts> childPorts;

    public ThreadTasks(Bounds bounds, Supplier<HostPorts> childPorts) {
        this.bounds = bounds;
        this.childPorts = childPorts;
    }

    @Override
    public void runBody(Crossing body, OutputPort output) {
        Interpreter interpreter = Interpreter.createOn(childPorts.get(), output, bounds.withoutTimeLimit());
        Crossing.CrossedCopy copy = interpreter.receive(body);
        DaemonThreads.start("jebol-task", () -> interpreter.runReceived(copy));
    }
}
