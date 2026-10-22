package org.jebol.adapter.web;

import org.jebol.domain.host.WindowPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageWindowsTest {

    private final WebFiles store = new WebFiles();
    private final AScriptedPage page = new AScriptedPage();
    private final PageWindows windows = new PageWindows(page, new PickedFiles(store, "/picked/", page));

    private List<String> chooseToOpen(List<String> filters) {
        return windows.chooseFiles(false, false, Optional.empty(), Optional.empty(), filters);
    }

    @Test
    @DisplayName("a colour is asked behind the prompt and the answer comes back as three octets")
    void asksForAColour() {
        page.answering("""
                {"ask":1,"answer":"given","colour":"#ff8000"}""");

        assertThat(windows.chooseColour(Optional.empty())).hasValueSatisfying(colour ->
                assertThat(colour).containsExactly(255, 128, 0));
        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"colour","gesture":true}""");
    }

    @Test
    @DisplayName("a suggested colour crosses as the browser writes one, and only its first three octets")
    void suggestsAColour() {
        page.answering("""
                {"ask":1,"answer":"given","colour":"#0000ff"}""", """
                {"ask":2,"answer":"given","colour":"#0000ff"}""");

        windows.chooseColour(Optional.of(new int[] {0, 0, 255}));
        assertThat(page.writeLastAsk()).endsWith("""
                "suggested":"#0000ff"}""");
        windows.chooseColour(Optional.of(new int[] {255, 16, 1, 9}));
        assertThat(page.writeLastAsk()).endsWith("""
                "suggested":"#ff1001"}""");
    }

    @ParameterizedTest(name = "{0} reads as {1}")
    @CsvSource({
            "#000000, 0 0 0",
            "#ffffff, 255 255 255",
            "#FF8000, 255 128 0",
            "#0a0B0c, 10 11 12",
    })
    @DisplayName("every colour a browser writes reads back as its octets, upper or lower case")
    void readsEveryColourTheBrowserWrites(String written, String octets) {
        page.answering("""
                {"ask":1,"answer":"given","colour":"%s"}""".formatted(written));

        int[] expected = Arrays.stream(octets.split(" ")).mapToInt(Integer::parseInt).toArray();
        assertThat(windows.chooseColour(Optional.empty())).hasValueSatisfying(colour ->
                assertThat(colour).containsExactly(expected));
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource({"#fff", "ff8000", "#ff80000", "#gg0000", "''", "#ff 800"})
    @DisplayName("a colour the page wrote some other way is refused rather than guessed at")
    void refusesAColourItCannotRead(String written) {
        page.answering("""
                {"ask":1,"answer":"given","colour":"%s"}""".formatted(written));

        assertThatThrownBy(() -> windows.chooseColour(Optional.empty()))
                .isInstanceOf(WindowPort.Denied.class)
                .hasMessageContaining("colour");
    }

    @Test
    @DisplayName("a colour answer with no colour in it is refused too")
    void refusesAColourAnswerWithoutAColour() {
        page.answering("""
                {"ask":1,"answer":"given"}""");

        assertThatThrownBy(() -> windows.chooseColour(Optional.empty()))
                .isInstanceOf(WindowPort.Denied.class);
    }

    @Test
    @DisplayName("an operator declining any dialog is an empty answer, never a refusal")
    void decliningIsEmpty() {
        page.answering("""
                {"ask":1,"answer":"declined"}""", """
                {"ask":2,"answer":"declined"}""", """
                {"ask":3,"answer":"declined"}""", """
                {"ask":4,"answer":"declined"}""", """
                {"ask":5,"answer":"declined"}""");

        assertThat(windows.chooseColour(Optional.empty())).isEmpty();
        assertThat(chooseToOpen(List.of())).isEmpty();
        assertThat(windows.chooseFiles(true, false, Optional.empty(), Optional.empty(), List.of())).isEmpty();
        assertThat(windows.chooseDirectory(Optional.empty(), Optional.empty())).isEmpty();
        assertThat(windows.browse("https://example.com/")).isFalse();
    }

    @Test
    @DisplayName("the browser refusing is an error that names why, with the id the C raises for a browser that failed")
    void theBrowserRefusingIsAnError() {
        page.answering("""
                {"ask":1,"answer":"refused","why":"the browser blocked the new window"}""");

        assertThatThrownBy(() -> windows.browse("https://example.com/"))
                .isInstanceOf(WindowPort.Denied.class)
                .hasMessage("the browser blocked the new window")
                .satisfies(denied -> assertThat(((WindowPort.Denied) denied).errorId()).isEqualTo("call-fail"));
    }

    @Test
    @DisplayName("a page that opened the tab answers that it did")
    void browsingAUrlAsksForANewTab() {
        page.answering("""
                {"ask":1,"answer":"given"}""");

        assertThat(windows.browse("https://example.com/a?b=c")).isTrue();
        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"open-tab","gesture":true,"target":"https://example.com/a?b=c"}""");
    }

    @Test
    @DisplayName("a file in the store opens as its own bytes, since a tab cannot read the page's store")
    void browsingAFileSendsItsBytes() {
        store.write("/notes.html", "<p>hi</p>".getBytes(StandardCharsets.UTF_8));
        page.answering("""
                {"ask":1,"answer":"given"}""", """
                {"ask":2,"answer":"given"}""");

        windows.browse("/notes.html");
        assertThat(page.writeLastAsk()).contains("""
                "name":"notes.html",""").contains("PHA+aGk8L3A+");
        windows.browse("notes.html");
        assertThat(page.kindsAsked()).containsExactly("open-tab", "open-tab");
    }

    @Test
    @DisplayName("a file that is not in the store cannot be opened, and the page is not asked")
    void browsingAMissingFileIsRefused() {
        assertThatThrownBy(() -> windows.browse("/missing.html"))
                .isInstanceOf(WindowPort.Denied.class)
                .satisfies(denied -> assertThat(((WindowPort.Denied) denied).errorId()).isEqualTo("cannot-open"));
        assertThat(page.wasAskedNothing()).isTrue();
    }

    @Test
    @DisplayName("an opened file is copied into the store and its path is the answer")
    void opensAFile() {
        page.answering("""
                {"ask":1,"answer":"given","files":[{"name":"notes.txt","octets":"aGVsbG8="}]}""");

        assertThat(chooseToOpen(List.of())).containsExactly("/picked/1/notes.txt");
        assertThat(store.readBytes("/picked/1/notes.txt")).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"open-files","gesture":true,"many":false,"accept":""}""");
    }

    @Test
    @DisplayName("several files come back as several paths, and the page is told it may take several")
    void opensSeveralFiles() {
        page.answering("""
                {"ask":1,"answer":"given","files":[{"name":"a.txt","octets":"YQ=="},{"name":"b.txt","octets":"Yg=="}]}""");

        assertThat(windows.chooseFiles(false, true, Optional.empty(), Optional.of("Pick some"), List.of()))
                .containsExactly("/picked/1/a.txt", "/picked/1/b.txt");
        assertThat(page.writeLastAsk()).contains("""
                "many":true,""").endsWith("""
                "title":"Pick some"}""");
    }

    @Test
    @DisplayName("a chooser that came back with nothing in it is the operator declining")
    void anEmptyChoiceIsDeclining() {
        page.answering("""
                {"ask":1,"answer":"given","files":[]}""");

        assertThat(chooseToOpen(List.of())).isEmpty();
    }

    @ParameterizedTest(name = "{0} accepts [{1}]")
    @CsvSource(delimiter = '|', value = {
            "''|''",
            "Rebol,*.r3|.r3",
            "Text,*.txt;*.md|.txt,.md",
            "All,*.*|''",
            "All,*|''",
            "Odd,r3-*|''",
            "Rebol,*.r3,Odd,r3-*|.r3",
            "Rebol,*.r3,All,*|''",
            "Spaced, *.r3 |.r3",
            "Bare,*.|''",
            "Twice,*.r3,Again,*.r3|.r3",
            "Cased,*.R3|.R3",
    })
    @DisplayName("a filter becomes the suffixes the chooser accepts, and a pattern a browser cannot express is dropped")
    void turnsFiltersIntoWhatTheChooserAccepts(String filters, String accepted) {
        page.answering("""
                {"ask":1,"answer":"declined"}""");
        List<String> pairs = filters.isEmpty() ? List.of() : List.of(filters.split(","));

        chooseToOpen(pairs);

        assertThat(page.writeLastAsk()).endsWith("""
                "accept":"%s"}""".formatted(accepted));
    }

    @Test
    @DisplayName("a save answers a path in a fresh folder under the name the operator chose")
    void savesAFile() {
        page.answering("""
                {"ask":1,"answer":"given","name":"report.txt"}""");

        assertThat(windows.chooseFiles(true, false, Optional.of("/home/draft.txt"), Optional.empty(),
                List.of("Text", "*.txt"))).containsExactly("/picked/1/report.txt");
        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"save-file","gesture":true,"name":"draft.txt","accept":".txt"}""");
    }

    @Test
    @DisplayName("a save with nothing suggested offers a name of its own, and an answer with no name is refused")
    void savesWithoutASuggestion() {
        page.answering("""
                {"ask":1,"answer":"given","name":"untitled"}""", """
                {"ask":2,"answer":"given"}""");

        windows.chooseFiles(true, false, Optional.empty(), Optional.empty(), List.of());
        assertThat(page.writeAsk(1)).contains("""
                "name":"untitled",""");
        assertThatThrownBy(() -> windows.chooseFiles(true, false, Optional.empty(), Optional.empty(), List.of()))
                .isInstanceOf(WindowPort.Denied.class);
    }

    @Test
    @DisplayName("a folder is copied whole and its path is the answer")
    void opensAFolder() {
        page.answering("""
                {"ask":1,"answer":"given","files":[{"path":"photos/a.txt","octets":"YQ=="}]}""");

        assertThat(windows.chooseDirectory(Optional.empty(), Optional.of("Where?"))).contains("/picked/1/photos/");
        assertThat(page.writeLastAsk()).isEqualTo("""
                {"type":"ask","ask":1,"kind":"open-folder","gesture":true,"title":"Where?"}""");
    }

    @Test
    @DisplayName("a page has no password dialog of its own, so asking the port is refused and the screen's form is used instead")
    void refusesAPasswordThroughThePort() {
        assertThatThrownBy(windows::askForPassword)
                .isInstanceOf(WindowPort.Denied.class)
                .hasMessageContaining("form");
        assertThat(page.wasAskedNothing()).isTrue();
    }
}
