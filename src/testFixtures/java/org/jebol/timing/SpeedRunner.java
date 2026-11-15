package org.jebol.timing;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SpeedRunner {

    private SpeedRunner() {
    }

    static void main(String[] argued) throws Exception {
        Interpreter interpreter = AnInterpreter.createWithOutput(new OutputPort() {
            @Override
            public void write(String text) {
                System.out.print(text);
            }

            @Override
            public void writeLine(String text) {
                System.out.println(text);
            }
        }, Bounds.standard());
        String source = org.jebol.application.TextFiles.read(Path.of(argued[0]));
        interpreter.defineNewWordsIn(source);
        interpreter.run(source);
    }
}
