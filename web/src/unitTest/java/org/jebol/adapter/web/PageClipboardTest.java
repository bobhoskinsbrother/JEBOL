package org.jebol.adapter.web;

import org.jebol.domain.host.ClipboardPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageClipboardTest {

    private final AScriptedPage page = new AScriptedPage();
    private final PageClipboard clipboard = new PageClipboard(page);

    @Test
    @DisplayName("reading asks the page behind the prompt and answers what was copied")
    void readsWhatWasCopied() {
        page.answering("""
                {"ask":1,"answer":"given","text":"copied text"}""");

        assertThat(clipboard.read()).contains("copied text");
        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"clipboard-read","gesture":true}""");
    }

    @Test
    @DisplayName("an empty clipboard is empty text, not nothing")
    void readsAnEmptyClipboard() {
        page.answering("""
                {"ask":1,"answer":"given","text":""}""");

        assertThat(clipboard.read()).contains("");
    }

    @Test
    @DisplayName("a refused permission is the operator's answer, so it reads as nothing")
    void aDeclinedReadIsNothing() {
        page.answering("""
                {"ask":1,"answer":"declined"}""");

        assertThat(clipboard.read()).isEmpty();
    }

    @Test
    @DisplayName("a browser with no clipboard at all is unreachable, naming why")
    void aRefusedReadIsUnreachable() {
        page.answering("""
                {"ask":1,"answer":"refused","why":"this browser has no clipboard API"}""");

        assertThatThrownBy(clipboard::read)
                .isInstanceOf(ClipboardPort.Unreachable.class)
                .hasMessage("this browser has no clipboard API");
    }

    @Test
    @DisplayName("a read answer that carries no text is refused rather than read as empty")
    void aReadWithoutTextIsUnreachable() {
        page.answering("""
                {"ask":1,"answer":"given"}""");

        assertThatThrownBy(clipboard::read).isInstanceOf(ClipboardPort.Unreachable.class);
    }

    @Test
    @DisplayName("writing sends the text, and asks for no gesture until the browser wants one")
    void writesText() {
        page.answering("""
                {"ask":1,"answer":"given"}""");

        assertThat(clipboard.write("to paste")).isTrue();
        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"clipboard-write","gesture":false,"text":"to paste"}""");
    }

    @Test
    @DisplayName("a write the operator declined says so, and a write the browser refused is unreachable")
    void declinedAndRefusedWrites() {
        page.answering("""
                {"ask":1,"answer":"declined"}""", """
                {"ask":2,"answer":"refused","why":"no clipboard here"}""");

        assertThat(clipboard.write("x")).isFalse();
        assertThatThrownBy(() -> clipboard.write("x"))
                .isInstanceOf(ClipboardPort.Unreachable.class)
                .hasMessage("no clipboard here");
    }
}
