package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;

import java.util.ArrayList;
import java.util.List;

public final class SuiteStops {

    private SuiteStops() {
    }

    private static final int ENOUGH_STOPS_TO_SEE_THE_SHAPE = 10;

    private static Interpreter fullyBounded() {
        Interpreter interpreter = SuiteHost.installOn(
                Interpreter.withBounds(SuiteHost.grantEverything()));
        String dialect = String.join("\n",
                "--assert: func [x [any-type!]][:x]",
                "--assert-numbered: func [n [integer!] x [any-type!]][:x]",
                "--assert-er: func [x [any-type!]][:x]",
                "--red--: does []",
                "--assertf~=: func [a [any-type!] b [any-type!] c [any-type!]][]",
                "===start-group===: func [n [any-type!]][]",
                "===end-group===: does []",
                "--test--: func [n [any-type!]][]",
                "~~~start-file~~~: func [n [any-type!]][]",
                "~~~end-file~~~: does []");
        interpreter.defineNewWordsIn(dialect);
        interpreter.run(dialect);
        return interpreter;
    }

    static void main(String[] argued) {
        for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
            if (argued.length > 0 && !file.name().equals(argued[0])) {
                continue;
            }
            List<String> stops = stopsIn(file);
            if (stops.isEmpty()) {
                continue;
            }
            System.out.printf("%s  (%d steps stopped)%n", file.name(), stops.size());
            stops.stream().limit(ENOUGH_STOPS_TO_SEE_THE_SHAPE)
                    .forEach(System.out::println);
        }
    }

    private static List<String> stopsIn(SuiteFile file) {
        Interpreter interpreter = fullyBounded();
        List<String> stops = new ArrayList<>();
        for (SuiteFile.Step step : file.steps()) {
            String source = step.sourceToRun();
            if (source == null || source.isBlank()) {
                continue;
            }
            String went;
            try {
                interpreter.defineNewWordsIn(source);
                ScriptOutcome ran = interpreter.run(source);
                went = ran.succeeded() ? null : interpreter.display(ran);
            } catch (RuntimeException | StackOverflowError broke) {
                went = broke.toString();
            }
            if (went != null) {
                stops.add("      %s%n        %s".formatted(went,
                        source.lines().findFirst().orElse("").strip()));
            }
        }
        return stops;
    }
}
