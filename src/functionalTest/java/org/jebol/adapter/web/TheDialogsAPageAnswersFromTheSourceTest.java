package org.jebol.adapter.web;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.value.Molder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TheDialogsAPageAnswersFromTheSourceTest {

    private final AScriptedPage page = new AScriptedPage();
    private final PickedFiles picked = new PickedFiles(new WebFiles(), "/picked/", page);
    private final Interpreter interpreter = Interpreter.createOn(
            AnInterpreter.standardPorts()
                    .withFiles(picked)
                    .withWindows(new PageWindows(page, picked)),
            OutputPort.discard(),
            new ShippedPageBounds().bounds());

    private String answer(String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        return outcome.succeeded() ? Molder.form(outcome.value()) : interpreter.display(outcome);
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("REQUEST-COLOR answers the colour the operator chose as a tuple")
    void requestColorAnswersATuple() {
        page.answering("""
                {"ask":1,"answer":"given","colour":"#ff8000"}""");

        assertThat(answer("request-color")).isEqualTo("255.128.0");
    }

    @Test
    @DisplayName("REQUEST-COLOR/DEFAULT suggests its colour to the page")
    void requestColorSuggests() {
        page.answering("""
                {"ask":1,"answer":"given","colour":"#0000ff"}""");

        answer("request-color/default 0.0.255");

        assertThat(page.writeLastAsk()).endsWith("""
                "suggested":"#0000ff"}""");
    }

    @Test
    @DisplayName("a dialog the operator declined answers none")
    void aDeclinedDialogIsNone() {
        page.answering("""
                {"ask":1,"answer":"declined"}""", """
                {"ask":2,"answer":"declined"}""", """
                {"ask":3,"answer":"declined"}""");

        assertThat(answer("none? request-color")).isEqualTo("true");
        assertThat(answer("none? request-file")).isEqualTo("true");
        assertThat(answer("none? request-dir")).isEqualTo("true");
    }

    @Test
    @DisplayName("REQUEST-FILE/MULTI cancelled answers none, as the C does, not an empty block")
    void aCancelledMultiIsNone() {
        page.answering("""
                {"ask":1,"answer":"declined"}""");

        assertThat(answer("none? request-file/multi")).isEqualTo("true");
    }

    @Test
    @DisplayName("REQUEST-FILE answers a path in the page's store, and READ of it reads the chosen bytes")
    void requestFileAnswersAPathToTheCopy() {
        page.answering("""
                {"ask":1,"answer":"given","files":[{"name":"notes.txt","octets":"%s"}]}""".formatted(encode("hello")));

        assertThat(answer("""
                chosen: request-file
                mold reduce [chosen read/string chosen]""")).isEqualTo("""
                [%/picked/1/notes.txt "hello"]""");
    }

    @Test
    @DisplayName("REQUEST-FILE/MULTI answers a block of paths, and /FILTER becomes what the chooser accepts")
    void requestFileMultiAnswersABlock() {
        page.answering("""
                {"ask":1,"answer":"given","files":[{"name":"a.r3","octets":"YQ=="},{"name":"b.r3","octets":"Yg=="}]}""");

        assertThat(answer("mold request-file/multi/filter [{Rebol} {*.r3}]"))
                .isEqualTo("[%/picked/1/a.r3 %/picked/1/b.r3]");
        assertThat(page.writeLastAsk()).endsWith("""
                "many":true,"accept":".r3"}""");
    }

    @Test
    @DisplayName("REQUEST-DIR answers the copied folder, its tree intact")
    void requestDirAnswersTheFolder() {
        page.answering("""
                {"ask":1,"answer":"given","files":[{"path":"photos/inner/b.txt","octets":"Yg=="}]}""");

        assertThat(answer("""
                chosen: request-dir
                mold reduce [chosen read/string chosen/inner/b.txt]""")).isEqualTo("""
                [%/picked/1/photos/ "b"]""");
    }

    @Test
    @DisplayName("REQUEST-FILE/SAVE answers a path, and the file goes out once, when the WRITE has finished with it")
    void savingSendsTheFileWhenWritten() {
        page.answering("""
                {"ask":1,"answer":"given","name":"report.txt"}""", """
                {"ask":2,"answer":"given"}""");

        assertThat(answer("""
                chosen: request-file/save/file %draft.txt
                write chosen "saved text"
                mold reduce [chosen read/string chosen]""")).isEqualTo("""
                [%/picked/1/report.txt "saved text"]""");
        assertThat(page.kindsAsked()).containsExactly("save-file", "send-saved-file");
        assertThat(page.writeAsk(1)).contains("""
                "name":"draft.txt",""");
        assertThat(page.writeLastAsk()).contains(encode("saved text"));
    }

    @Test
    @DisplayName("a saved file written through an open port goes out once, when the port is closed")
    void savingThroughAPortSendsOnClose() {
        page.answering("""
                {"ask":1,"answer":"given","name":"log.txt"}""");
        answer("""
                chosen: request-file/save
                port: open/write chosen
                write port "one "
                write port "two"
                """);
        assertThat(page.kindsAsked()).containsExactly("save-file");

        page.answering("""
                {"ask":2,"answer":"given"}""");
        answer("close port");

        assertThat(page.kindsAsked()).containsExactly("save-file", "send-saved-file");
        assertThat(page.writeLastAsk()).contains(encode("one two"));
    }

    @Test
    @DisplayName("a save that could not reach the operator is an error the script can catch")
    void aSaveThatFailedIsCatchable() {
        page.answering("""
                {"ask":1,"answer":"given","name":"report.txt"}""", """
                {"ask":2,"answer":"refused","why":"the chosen file could not be written"}""");

        assertThat(answer("""
                chosen: request-file/save
                e: try [write chosen "x"]
                mold reduce [e/id read/string chosen]""")).isEqualTo("""
                [write-error "x"]""");
    }

    @Test
    @DisplayName("BROWSE answers unset when the tab opened, as a real 3.22.5 does, and none when the operator dismissed it")
    void browseAnswersUnsetOrNone() {
        page.answering("""
                {"ask":1,"answer":"given"}""", """
                {"ask":2,"answer":"declined"}""");

        assertThat(answer("unset? browse https://example.com/")).isEqualTo("true");
        assertThat(answer("none? browse https://example.com/")).isEqualTo("true");
    }

    @Test
    @DisplayName("BROWSE NONE asks nothing and answers unset")
    void browseNoneAsksNothing() {
        assertThat(answer("unset? browse none")).isEqualTo("true");
        assertThat(page.wasAskedNothing()).isTrue();
    }

    @Test
    @DisplayName("a browser that blocked the tab is an error the script can catch, saying so")
    void aBlockedTabIsCatchable() {
        page.answering("""
                {"ask":1,"answer":"refused","why":"the browser blocked the new window"}""");

        assertThat(answer("""
                e: try [browse https://example.com/]
                mold reduce [e/id e/arg1]""")).isEqualTo("""
                [call-fail "the browser blocked the new window"]""");
    }

    @Test
    @DisplayName("a picker the browser refused outright is an error the script can catch, never a hang")
    void aRefusedPickerIsCatchable() {
        page.answering("""
                {"ask":1,"answer":"refused","why":"this browser has no folder chooser"}""");

        assertThat(answer("""
                e: try [request-dir]
                e/id""")).isEqualTo("call-fail");
    }

    @Test
    @DisplayName("with no screen attached a password is asked of the port, and a page's port refuses it as not present")
    void aPasswordWithNoScreenIsRefused() {
        assertThat(answer("""
                e: try [request-password]
                e/id""")).isEqualTo("no-service");
        assertThat(page.wasAskedNothing()).isTrue();
    }
}
