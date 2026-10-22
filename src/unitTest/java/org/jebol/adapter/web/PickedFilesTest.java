package org.jebol.adapter.web;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.WindowPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PickedFilesTest {

    private final WebFiles store = new WebFiles();
    private final AScriptedPage page = new AScriptedPage();
    private final PickedFiles picked = new PickedFiles(store, "/picked/", page);

    private PageAnswer chosenFile(String name, String text) {
        return new PageAnswer("""
                {"name":"%s","octets":"%s"}""".formatted(name, encode(text)));
    }

    private PageAnswer chosenInFolder(String path, String text) {
        return new PageAnswer("""
                {"path":"%s","octets":"%s"}""".formatted(path, encode(text)));
    }

    private String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String readBack(String path) {
        return new String(store.readBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the first pick in an empty store goes in folder one, under the name it was chosen by")
    void firstPickGoesInFolderOne() {
        List<String> kept = picked.keepPick(List.of(chosenFile("notes.txt", "hello")));

        assertThat(kept).containsExactly("/picked/1/notes.txt");
        assertThat(readBack("/picked/1/notes.txt")).isEqualTo("hello");
    }

    @Test
    @DisplayName("each pick takes the next folder, so the same name picked twice keeps both copies")
    void eachPickTakesTheNextFolder() {
        picked.keepPick(List.of(chosenFile("notes.txt", "first")));
        List<String> second = picked.keepPick(List.of(chosenFile("notes.txt", "second")));

        assertThat(second).containsExactly("/picked/2/notes.txt");
        assertThat(readBack("/picked/1/notes.txt")).isEqualTo("first");
        assertThat(readBack("/picked/2/notes.txt")).isEqualTo("second");
    }

    @Test
    @DisplayName("a store that outlived a reload is counted from the highest folder already in it")
    void countsOnFromTheHighestFolderAlreadyKept() {
        store.makeDirectory("/picked/1", true);
        store.makeDirectory("/picked/7", true);

        assertThat(picked.keepPick(List.of(chosenFile("a.txt", "a")))).containsExactly("/picked/8/a.txt");
    }

    @Test
    @DisplayName("a folder under the picks that is not a number, and a file beside them, do not count")
    void ignoresWhatIsNotANumberedFolder() {
        store.makeDirectory("/picked/notes", true);
        store.makeDirectory("/picked/3x", true);
        store.write("/picked/9", new byte[0]);

        assertThat(picked.keepPick(List.of(chosenFile("a.txt", "a")))).containsExactly("/picked/1/a.txt");
    }

    @Test
    @DisplayName("a file the script wrote itself under the same name is never overwritten")
    void neverOverwritesTheScriptsOwnFile() {
        store.write("/notes.txt", "mine".getBytes(StandardCharsets.UTF_8));

        picked.keepPick(List.of(chosenFile("notes.txt", "picked")));

        assertThat(readBack("/notes.txt")).isEqualTo("mine");
    }

    @Test
    @DisplayName("several files chosen at once sit side by side in one folder, in the order they were listed")
    void severalFilesShareOneFolder() {
        List<String> kept = picked.keepPick(List.of(
                chosenFile("b.txt", "b"), chosenFile("a.txt", "a")));

        assertThat(kept).containsExactly("/picked/1/b.txt", "/picked/1/a.txt");
    }

    @Test
    @DisplayName("an empty file is kept as an empty file")
    void keepsAnEmptyFile() {
        picked.keepPick(List.of(chosenFile("empty.txt", "")));

        assertThat(store.readBytes("/picked/1/empty.txt")).isEmpty();
    }

    @Test
    @DisplayName("a name that would climb out of its folder, nest inside it, or say nothing is refused")
    void refusesAHostileName() {
        for (String hostile : List.of("../escape.txt", "a/b.txt", "", ".", "..")) {
            assertThatThrownBy(() -> picked.keepPick(List.of(chosenFile(hostile, "x"))))
                    .as(hostile)
                    .isInstanceOf(WindowPort.Denied.class)
                    .hasMessageContaining("will not keep");
        }
        assertThat(store.exists("/escape.txt")).isFalse();
    }

    @Test
    @DisplayName("a chosen file the page sent no bytes for is refused rather than kept empty")
    void refusesAFileWithoutBytes() {
        assertThatThrownBy(() -> picked.keepPick(List.of(new PageAnswer("""
                {"name":"a.txt"}"""))))
                .isInstanceOf(WindowPort.Denied.class);
    }

    @Test
    @DisplayName("a folder is copied whole, its tree intact, and the answer is that folder")
    void copiesAFolderWhole() {
        String kept = picked.keepFolder(List.of(
                chosenInFolder("photos/a.txt", "a"),
                chosenInFolder("photos/inner/b.txt", "b")));

        assertThat(kept).isEqualTo("/picked/1/photos/");
        assertThat(readBack("/picked/1/photos/a.txt")).isEqualTo("a");
        assertThat(readBack("/picked/1/photos/inner/b.txt")).isEqualTo("b");
    }

    @Test
    @DisplayName("an empty folder answers the pick's own folder, since a browser cannot say its name")
    void anEmptyFolderAnswersThePicksFolder() {
        assertThat(picked.keepFolder(List.of())).isEqualTo("/picked/1/");
        assertThat(store.isDirectory("/picked/1")).isTrue();
    }

    @Test
    @DisplayName("a folder whose files do not share one top folder, or that climbs out, is refused")
    void refusesAFolderThatIsNotOneTree() {
        assertThatThrownBy(() -> picked.keepFolder(List.of(
                chosenInFolder("photos/a.txt", "a"), chosenInFolder("other/b.txt", "b"))))
                .isInstanceOf(WindowPort.Denied.class);
        assertThatThrownBy(() -> picked.keepFolder(List.of(chosenInFolder("photos/../../x.txt", "x"))))
                .isInstanceOf(WindowPort.Denied.class);
        assertThatThrownBy(() -> picked.keepFolder(List.of(chosenInFolder("loose.txt", "x"))))
                .isInstanceOf(WindowPort.Denied.class);
    }

    @Test
    @DisplayName("a save answers a path in a fresh folder, and nothing is sent until the file is finished with")
    void aSaveIsReservedAndSentWhenFinished() {
        String path = picked.reserveSave("report.txt");
        picked.write(path, "draft".getBytes(StandardCharsets.UTF_8));

        assertThat(path).isEqualTo("/picked/1/report.txt");
        assertThat(page.wasAskedNothing()).isTrue();

        page.answering("""
                {"ask":1,"answer":"given"}""");
        picked.finishWriting(path);

        assertThat(page.kindsAsked()).containsExactly("send-saved-file");
        assertThat(page.writeLastAsk()).contains("""
                "name":"report.txt",""").contains(encode("draft"));
    }

    @Test
    @DisplayName("a file written in pieces is sent once, as it stands when finished")
    void aFileWrittenInPiecesIsSentOnce() {
        String path = picked.reserveSave("log.txt");
        picked.write(path, "one".getBytes(StandardCharsets.UTF_8));
        picked.appendTo(path, " two".getBytes(StandardCharsets.UTF_8));
        picked.writeAt(path, 0, "ONE".getBytes(StandardCharsets.UTF_8));
        page.answering("""
                {"ask":1,"answer":"given"}""");

        picked.finishWriting(path);

        assertThat(page.kindsAsked()).containsExactly("send-saved-file");
        assertThat(page.writeLastAsk()).contains(encode("ONE two"));
    }

    @Test
    @DisplayName("each time the saved file is finished with it is sent again, so a second save reaches the operator too")
    void eachFinishSendsAgain() {
        String path = picked.reserveSave("log.txt");
        picked.write(path, "a".getBytes(StandardCharsets.UTF_8));
        page.answering("""
                {"ask":1,"answer":"given"}""", """
                {"ask":2,"answer":"given"}""");

        picked.finishWriting(path);
        picked.finishWriting(path);

        assertThat(page.kindsAsked()).containsExactly("send-saved-file", "send-saved-file");
    }

    @Test
    @DisplayName("finishing with a file that is not a save sends nothing")
    void finishingAnOrdinaryFileSendsNothing() {
        picked.write("/notes.txt", "x".getBytes(StandardCharsets.UTF_8));

        picked.finishWriting("/notes.txt");

        assertThat(page.wasAskedNothing()).isTrue();
    }

    @Test
    @DisplayName("a save is recognised however the script spells its path")
    void recognisesTheSaveByWhereItIs() {
        String path = picked.reserveSave("report.txt");
        picked.write(path, "x".getBytes(StandardCharsets.UTF_8));
        picked.changeDirectory("/picked/1/");
        page.answering("""
                {"ask":1,"answer":"given"}""");

        picked.finishWriting("report.txt");

        assertThat(page.kindsAsked()).containsExactly("send-saved-file");
    }

    @Test
    @DisplayName("a save that never reached the operator is a write error naming why")
    void aSaveThatCouldNotGoOutIsAWriteError() {
        String path = picked.reserveSave("report.txt");
        picked.write(path, "x".getBytes(StandardCharsets.UTF_8));
        page.answering("""
                {"ask":1,"answer":"refused","why":"the chosen file could not be written"}""");

        assertThatThrownBy(() -> picked.finishWriting(path))
                .isInstanceOf(FilePort.Denied.class)
                .hasMessageContaining("the chosen file could not be written")
                .satisfies(denied -> assertThat(((FilePort.Denied) denied).errorId()).isEqualTo("write-error"));
        assertThat(readBack(path)).isEqualTo("x");
    }

    @Test
    @DisplayName("a saved file that was deleted before it was finished with sends nothing")
    void aDeletedSaveSendsNothing() {
        String path = picked.reserveSave("report.txt");
        picked.write(path, "x".getBytes(StandardCharsets.UTF_8));
        picked.delete(path);

        picked.finishWriting(path);

        assertThat(page.wasAskedNothing()).isTrue();
    }

    @Test
    @DisplayName("a save's name is held to the same rule as an opened file's")
    void refusesAHostileSaveName() {
        assertThatThrownBy(() -> picked.reserveSave("../x.txt")).isInstanceOf(WindowPort.Denied.class);
        assertThatThrownBy(() -> picked.reserveSave("")).isInstanceOf(WindowPort.Denied.class);
    }

    @Test
    @DisplayName("a store that refuses the copy is a refusal the script can catch, never the store's own exception")
    void aRefusingStoreIsADialogRefusal() {
        PickedFiles refusing = new PickedFiles(FilePort.none(), "/picked/", page);

        assertThatThrownBy(() -> refusing.keepPick(List.of(chosenFile("a.txt", "a"))))
                .isInstanceOf(WindowPort.Denied.class)
                .hasMessageContaining("could not be kept");
        assertThatThrownBy(() -> refusing.keepFolder(List.of(chosenInFolder("photos/a.txt", "a"))))
                .isInstanceOf(WindowPort.Denied.class);
        assertThatThrownBy(() -> refusing.reserveSave("a.txt"))
                .isInstanceOf(WindowPort.Denied.class);
    }

    @Test
    @DisplayName("a folder named from where the run stands is found there when the pick is made, and answered whole")
    void aRelativeFolderIsFoundWhenThePickIsMade() {
        PickedFiles besideTheScript = new PickedFiles(store, "picked/", page);
        store.makeDirectory("/app", true);
        store.changeDirectory("/app");

        assertThat(besideTheScript.keepPick(List.of(chosenFile("a.txt", "a")))).containsExactly("/app/picked/1/a.txt");
        assertThat(besideTheScript.reserveSave("b.txt")).isEqualTo("/app/picked/2/b.txt");
        assertThat(besideTheScript.keepFolder(List.of())).isEqualTo("/app/picked/3/");
    }

    @Test
    @DisplayName("everything else a file port does passes straight through to the store")
    void passesEverythingElseThrough() {
        picked.makeDirectory("/docs", false);
        picked.write("/docs/a.txt", "a".getBytes(StandardCharsets.UTF_8));
        picked.rename("/docs/a.txt", "/docs/b.txt");

        assertThat(picked.listFileNames("/docs/")).containsExactly("b.txt");
        assertThat(picked.exists("/docs/b.txt")).isTrue();
        assertThat(picked.readBytes("/docs/b.txt")).isEqualTo("a".getBytes(StandardCharsets.UTF_8));
        assertThat(picked.workingDirectory()).isEqualTo("/");
        assertThat(picked.describe("/docs/b.txt")).isPresent();
        assertThat(picked.delete("/docs/b.txt")).isTrue();
    }
}
