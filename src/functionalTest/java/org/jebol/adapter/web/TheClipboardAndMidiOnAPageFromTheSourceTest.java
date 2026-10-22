package org.jebol.adapter.web;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheClipboardAndMidiOnAPageFromTheSourceTest {

    private final AScriptedPage page = new AScriptedPage();
    private final Interpreter interpreter = Interpreter.createOn(
            AnInterpreter.standardPorts()
                    .withClipboard(new PageClipboard(page))
                    .withMidiDevices(new PageMidiDevices(page, () -> { })),
            OutputPort.discard(),
            new ShippedPageBounds().bounds());

    private String answer(String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        return outcome.succeeded() ? Molder.form(outcome.value()) : interpreter.display(outcome);
    }

    @Test
    @DisplayName("READ clipboard:// answers what the operator copied")
    void readsTheClipboard() {
        page.answering("""
                {"ask":1,"answer":"given","text":"copied"}""");

        assertThat(answer("read clipboard://")).isEqualTo("copied");
    }

    @Test
    @DisplayName("a clipboard read the operator refused permission for answers none")
    void aDeclinedReadIsNone() {
        page.answering("""
                {"ask":1,"answer":"declined"}""");

        assertThat(answer("none? read clipboard://")).isEqualTo("true");
    }

    @Test
    @DisplayName("WRITE clipboard:// answers the port, and none when the operator refused")
    void writesTheClipboard() {
        page.answering("""
                {"ask":1,"answer":"given"}""", """
                {"ask":2,"answer":"declined"}""");

        assertThat(answer("port? write clipboard:// {to paste}")).isEqualTo("true");
        assertThat(answer("none? write clipboard:// {to paste}")).isEqualTo("true");
    }

    @Test
    @DisplayName("a browser with no clipboard at all is an error the script can catch")
    void aRefusedClipboardIsCatchable() {
        page.answering("""
                {"ask":1,"answer":"refused","why":"this browser has no clipboard API"}""");

        assertThat(answer("""
                e: try [read clipboard://]
                e/id""")).isEqualTo("read-error");
    }

    @Test
    @DisplayName("QUERY midi:// lists what the page's MIDI access reaches")
    void queriesTheDevices() {
        page.answering("""
                {"ask":1,"answer":"given","inputs":["Keys"],"outputs":["Synth"]}""", """
                {"ask":2,"answer":"given","inputs":["Keys"],"outputs":["Synth"]}""");

        assertThat(answer("""
                mold reduce [query midi:// 'devices-in query midi:// 'devices-out]""")).isEqualTo("""
                [["Keys"] ["Synth"]]""");
    }

    @Test
    @DisplayName("a MIDI permission the operator denied lists none rather than an empty block")
    void aDeniedQueryIsNone() {
        page.answering("""
                {"ask":1,"answer":"declined"}""", """
                {"ask":2,"answer":"declined"}""");

        assertThat(answer("""
                mold reduce [none? query midi:// 'devices-in none? query midi:// 'devices-out]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("opening a device the operator denied answers none")
    void aDeniedOpenIsNone() {
        page.answering("""
                {"ask":1,"answer":"declined"}""");

        assertThat(answer("none? open midi://1")).isEqualTo("true");
    }

    @Test
    @DisplayName("a note written to an opened output is sent to the page as bytes")
    void writesANote() {
        page.answering("""
                {"ask":1,"answer":"given","inputs":[],"outputs":["Synth"]}""", """
                {"ask":2,"answer":"given","inputs":[],"outputs":["Synth"]}""", """
                {"ask":3,"answer":"given","connection":1}""", """
                {"ask":4,"answer":"given"}""", """
                {"ask":5,"answer":"given"}""");

        answer("""
                out: open/write midi://1
                write out #{903C7F}
                close out""");

        assertThat(page.kindsAsked())
                .containsExactly("midi-ports", "midi-ports", "midi-open", "midi-send", "midi-close");
        assertThat(page.writeAsk(4)).endsWith("""
                "connection":1,"octets":"kDx/"}""");
    }
}
