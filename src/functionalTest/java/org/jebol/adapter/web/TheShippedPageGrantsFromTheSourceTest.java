package org.jebol.adapter.web;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.value.Molder;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TheShippedPageGrantsFromTheSourceTest {

    private final Bounds shipped = new ShippedPageBounds().bounds();

    private String answer(String source) {
        Interpreter page = AnInterpreter.withBounds(shipped);
        page.defineNewWordsIn(source);
        ScriptOutcome outcome = page.run(source);
        return outcome.succeeded() ? Molder.form(outcome.value()) : page.display(outcome);
    }

    @Test
    @DisplayName("the shipped page grants everything a page has: the clock, the clipboard and MIDI among them")
    void grantsEverythingAPageHas() {
        assertThat(Arrays.stream(HostService.values()).filter(shipped::grants))
                .containsExactlyInAnyOrder(
                        HostService.CONSOLE, HostService.FILES, HostService.WORKING_DIRECTORY,
                        HostService.CLOCK, HostService.WINDOWS, HostService.CLIPBOARD,
                        HostService.MIDI, HostService.WEB);
    }

    @Test
    @DisplayName("and marks the four a page has not got unavailable, naming a browser")
    void marksWhatAPageHasNotGot() {
        assertThat(Arrays.stream(HostService.values()).filter(shipped::isUnavailable))
                .containsExactlyInAnyOrder(
                        HostService.NETWORK, HostService.PROCESSES,
                        HostService.ENVIRONMENT, HostService.CERTIFICATES);
        assertThat(shipped.hostName()).isEqualTo("a browser");
    }

    @Test
    @DisplayName("NOW answers the time on the shipped page rather than refusing the clock")
    void nowAnswersOnThePage() {
        assertThat(answer("date? now")).isEqualTo("true");
        assertThat(answer("e: try [now/precise] either error? e [e/id] ['answered]")).isEqualTo("answered");
    }

    @Test
    @DisplayName("a script on the page has no time limit, as one a person is using must not")
    void hasNoTimeLimit() {
        assertThat(shipped.timeLimit()).isEqualTo(ChronoUnit.FOREVER.getDuration());
    }
}
