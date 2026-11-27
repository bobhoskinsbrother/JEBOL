package org.jebol.suite;

import org.jebol.testing.TestTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhereRebolsSuiteEntriesRunTest {

    @Test
    @DisplayName("Android withholds the eleven entries a device cannot run, by file / group / test as the suite names them")
    void androidWithholdsWhatADeviceCannotRun() {
        assertThat(WhereRebolsSuiteEntriesRun.entriesNotRunOn(TestTarget.ANDROID)).containsExactlyInAnyOrder(
                "evaluation-test.r3 / BOOT / issue-232",
                "evaluation-test.r3 / CATCH / catch/quit [quit]",
                "unicode-test.r3 / natives / call",
                "lexer-test.r3 / Special tests / NULLs inside loaded string",
                "module-test.r3 / module import / import to user context",
                "module-test.r3 / module import / import/no-lib",
                "module-test.r3 / module import / import block",
                "module-test.r3 / module import / import/no-lib block",
                "file-test.r3 / to-local-file / issue-2538",
                "file-test.r3 / QUERY / query file dates (some asserts may fail in CI and WSL)",
                "checksum-test.r3 / file-checksum / file-checksum with bigger file");
    }

    @Test
    @DisplayName("a desktop withholds nothing")
    void aDesktopWithholdsNothing() {
        for (TestTarget desktop : new TestTarget[] {TestTarget.DESKTOP_LINUX, TestTarget.DESKTOP_MAC, TestTarget.DESKTOP_WINDOWS}) {
            assertThat(WhereRebolsSuiteEntriesRun.entriesNotRunOn(desktop)).as("%s", desktop).isEmpty();
        }
    }

    @Test
    @DisplayName("every entry gives its reason")
    void everyEntryGivesItsReason() {
        assertThat(WhereRebolsSuiteEntriesRun.reasonsByEntry().values()).allSatisfy(reason -> assertThat(reason).isNotBlank());
    }
}
