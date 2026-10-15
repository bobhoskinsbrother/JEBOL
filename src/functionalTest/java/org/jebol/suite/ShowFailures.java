package org.jebol.suite;

import org.jebol.application.Interpreter;

public final class ShowFailures {

    private ShowFailures() {
    }

    static void main(String[] argued) {
        Interpreter.create();
        for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
            if (argued.length > 0 && !file.name().equals(argued[0])) {
                continue;
            }
            for (SuiteFile.Assertion assertion : file.assertions()) {
                System.out.println("=== " + assertion);
                System.out.println("    " + RebolSuiteTest.verdictFor(assertion).reason());
                System.out.println("    "
                        + assertion.source().strip().lines().findFirst().orElse(""));
            }
        }
    }
}
