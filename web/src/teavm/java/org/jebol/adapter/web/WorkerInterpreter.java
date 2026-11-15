package org.jebol.adapter.web;

import org.teavm.jso.JSExport;

public class WorkerInterpreter {

    private static WorkerSession session;

    public static void main(String[] args) {
        session = new WorkerSession();
        session.serve();
    }

    @JSExport
    public static void runScript(String source) {
        session.handOver(source);
    }
}
