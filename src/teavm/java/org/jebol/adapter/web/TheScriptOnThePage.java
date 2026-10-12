package org.jebol.adapter.web;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;

public class TheScriptOnThePage {

    public static void main(String[] args) {
        Interpreter interpreter = Interpreter.writingTo(System.out::print);
        ScriptOutcome outcome = interpreter.run(args.length == 0 ? "" : args[0]);
        if (!outcome.succeeded()) {
            System.out.println(interpreter.display(outcome));
        }
    }
}
