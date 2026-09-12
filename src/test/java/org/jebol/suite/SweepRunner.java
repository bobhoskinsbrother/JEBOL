package org.jebol.suite;

import java.nio.file.Files;
import java.nio.file.Path;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.OutputPort;

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
