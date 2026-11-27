package org.jebol.suite;

import org.jebol.testing.TestTarget;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jebol.testing.TestTarget.DESKTOP_LINUX;
import static org.jebol.testing.TestTarget.DESKTOP_MAC;
import static org.jebol.testing.TestTarget.DESKTOP_WINDOWS;

public final class WhereRebolsSuiteEntriesRun {

    private static final Set<TestTarget> DESKTOPS = EnumSet.of(DESKTOP_LINUX, DESKTOP_MAC, DESKTOP_WINDOWS);
    private static final String STARTS_A_PROGRAM =
            "an Android app runs no programs: CALL refuses as a page's does (decision 77), so nothing starts a second "
                    + "interpreter or a shell";

    private record Entry(String named, Set<TestTarget> runsOn, String because) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("evaluation-test.r3 / BOOT / issue-232", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("evaluation-test.r3 / CATCH / catch/quit [quit]", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("unicode-test.r3 / natives / call", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("lexer-test.r3 / Special tests / NULLs inside loaded string", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("module-test.r3 / module import / import to user context", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("module-test.r3 / module import / import/no-lib", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("module-test.r3 / module import / import block", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("module-test.r3 / module import / import/no-lib block", DESKTOPS, STARTS_A_PROGRAM),
            new Entry("file-test.r3 / to-local-file / issue-2538", DESKTOPS,
                    "file-test.r3 writes issue-2538's block on the same step as issue-1115's last assertion, which "
                            + "starts a program, so withholding the one withholds that assertion too"),
            new Entry("file-test.r3 / QUERY / query file dates (some asserts may fail in CI and WSL)", DESKTOPS,
                    "Android's file system keeps no creation time, so a file's created date moves on with its modified "
                            + "date; Rebol's own test says it may fail in CI and WSL for the same reason"),
            new Entry("checksum-test.r3 / file-checksum / file-checksum with bigger file", DESKTOPS,
                    "it checksums the file system/options/boot names, the launcher a program would start, and an "
                            + "Android app starts none, so boot is none (decision 77)"));

    private WhereRebolsSuiteEntriesRun() {
    }

    public static List<String> entriesNotRunOn(TestTarget target) {
        return ENTRIES.stream()
                .filter(entry -> !entry.runsOn().contains(target))
                .map(Entry::named)
                .toList();
    }

    public static Map<String, String> reasonsByEntry() {
        Map<String, String> reasons = new LinkedHashMap<>();
        ENTRIES.forEach(entry -> reasons.put(entry.named(), entry.because()));
        return reasons;
    }
}
