package org.jebol.suite;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.OutputPort;

/**
 * Runs a script the way the suite harness does, for the differential sweep.
 *
 * <p>The REPL grants one service and the harness grants all of them, so a
 * sweep run through the REPL reports every clock, filesystem and environment
 * question as a refusal and buries whatever the real difference was.
 *
 * <p>It builds its host through {@link SuiteHost}, which the gate uses too. It
 * used to build its own and claimed in this very comment to grant what the
 * harness grants; it granted the same services and installed neither the
 * environment nor the process runner, which is not the same thing and is not a
 * smaller host but a differently wrong one.
 */
public final class SweepRunner {

    private SweepRunner() {
    }

    public static void main(String[] argued) throws Exception {
        Interpreter interpreter = SuiteHost.installOn(
                Interpreter.writingTo(new OutputPort() {
                    @Override
                    public void write(String text) {
                        System.out.print(text);
                    }

                    @Override
                    public void writeLine(String text) {
                        System.out.println(text);
                    }
                }, SuiteHost.grantingEverything()));
        String source = Files.readString(Path.of(argued[0]));
        interpreter.defineFreshWordsIn(source);
        interpreter.run(source);
    }
}
