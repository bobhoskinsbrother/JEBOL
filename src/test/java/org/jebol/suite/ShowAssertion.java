package org.jebol.suite;

public final class ShowAssertion {

    private ShowAssertion() {
    }

    public static void main(String[] argued) throws Exception {
        org.jebol.application.Interpreter.create();
        String wanted = argued[0];
        for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
            for (SuiteFile.Assertion assertion : file.assertions()) {
                if (assertion.toString().contains(wanted)) {
                    System.out.println("=== " + assertion);
                    System.out.println(assertion.source());
                    System.out.println("--- lines "
                            + assertion.from() + " to " + assertion.to());
                }
            }
        }
    }
}
