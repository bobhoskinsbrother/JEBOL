package org.jebol.suite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.HostService;

public final class SuiteStops {

    private SuiteStops() {
    }

    private static final int ENOUGH_STOPS_TO_SEE_THE_SHAPE = 10;

    private static Interpreter fullyBounded() throws Exception {
        Interpreter interpreter = SuiteHost.installOn(
                Interpreter.withBounds(SuiteHost.grantingEverything()));
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
        interpreter.defineFreshWordsIn(dialect);
        interpreter.run(dialect);
        return interpreter;
    }

    public static void main(String[] argued) throws Exception {
        long least = argued.length > 0 ? Long.parseLong(argued[0]) : 1;
        Map<String, Long> owed = new TreeMap<>();
        for (String entry : RebolSuiteTest.knownGaps()) {
            owed.merge(entry.split(" / ")[0], 1L, Long::sum);
        }
        for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
            long owes = owed.getOrDefault(file.name(), 0L);
            if (owes < least) {
                continue;
            }
            List<String> stops = stopsIn(file);
            System.out.printf("=== %d gaps  %s  (%d steps stopped)%n",
                    owes, file.name(), stops.size());
            stops.stream().limit(ENOUGH_STOPS_TO_SEE_THE_SHAPE)
                    .forEach(System.out::println);
        }
    }

    private static List<String> stopsIn(SuiteFile file) throws Exception {
        Interpreter interpreter = fullyBounded();
        List<String> stops = new ArrayList<>();
        for (SuiteFile.Step step : file.steps()) {
            String source = step.sourceToRun();
            if (source == null || source.isBlank()) {
                continue;
            }
            String went;
            try {
                interpreter.defineFreshWordsIn(source);
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
