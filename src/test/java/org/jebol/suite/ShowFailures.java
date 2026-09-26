package org.jebol.suite;

import java.util.List;

import org.jebol.application.Interpreter;

public final class ShowFailures {

    private ShowFailures() {
    }

    static void main(String[] argued) throws Exception {
        Interpreter.create();
        List<String> gaps = RebolSuiteTest.knownGaps();
        for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
            if (argued.length > 0 && !file.name().equals(argued[0])) {
                continue;
            }
            for (SuiteFile.Assertion assertion : file.assertions()) {
                if (!gaps.contains(assertion.toString())) {
                    continue;
                }
                System.out.println("=== " + assertion);
                System.out.println("    " + RebolSuiteTest.verdictFor(assertion).reason());
                System.out.println("    "
                        + assertion.source().strip().lines().findFirst().orElse(""));
            }
        }
    }
}
