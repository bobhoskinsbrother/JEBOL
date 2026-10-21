package org.jebol.adapter.host;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.TaskPort;

/**
 * A way for a task's body to run on a thread of its own, on the host that has
 * threads to give.
 *
 * <p>The body runs in a fresh interpreter, on a thread of its own. It sees
 * none of the parent's contexts -- series cannot be shared with a second
 * thread safely -- and it writes where the parent writes. Its own edge is its
 * own: what it raises, and how long it takes, stay inside the thread, because
 * the parent answered the task and moved on before the body produced
 * anything. A body that never ends ends only when the process does, which is
 * why the thread is a daemon and the body carries no deadline.
 *
 * <p>Specified in {@code spec/natives.allium}.
 */
public final class ThreadTasks implements TaskPort {

    private final Bounds bounds;

    public ThreadTasks(Bounds bounds) {
        this.bounds = bounds;
    }

    @Override
    public void runTheBody(String bodyAsWritten, OutputPort output) {
        Interpreter interpreter = new JavaHost().createWithOutput(output, bounds.withoutATimeLimit());
        Thread.ofPlatform().daemon().name("jebol-task")
                .start(() -> interpreter.run("do " + bodyAsWritten));
    }

}
