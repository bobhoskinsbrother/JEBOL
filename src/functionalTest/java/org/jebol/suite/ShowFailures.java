package org.jebol.suite;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;

public final class ShowFailures {

    private ShowFailures() {
    }

    static void main(String[] argued) {
        AnInterpreter.create();
        for (SuiteFile file : SuiteGrading.filesInSuite()) {
            if (argued.length > 0 && !file.name().equals(argued[0])) {
                continue;
            }
            for (SuiteFile.Assertion assertion : file.assertions()) {
                System.out.println("=== " + assertion);
                System.out.println("    " + SuiteGrading.judge(assertion).reason());
                System.out.println("    "
                        + assertion.source().strip().lines().findFirst().orElse(""));
            }
        }
    }
}
