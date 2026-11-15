package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Order(2)
@ExtendWith(TheSuitesPageBrowsers.class)
class TheDialogsOnAPageEndToEndTest {

    private final PageBrowsers browsers;

    @TempDir
    Path operatorsDisk;

    TheDialogsOnAPageEndToEndTest(PageBrowsers browsers) {
        this.browsers = browsers;
    }

    private Path aFileOnTheOperatorsDisk(String name, String text) throws IOException {
        Path file = operatorsDisk.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("REQUEST-COLOR opens the browser's colour control behind the prompt and answers the colour chosen")
    void choosesAColour(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print mold request-color/default 0.0.255
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            operator.waitForThePromptSaying("colour");
            assertThat(operator.readTheValueOf("jebol-ask-colour")).isEqualTo("#0000ff");
            operator.choosesColour("#ff8000");

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("255.128.0");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("dismissing the prompt answers none, for every dialog that has one")
    void dismissingAnswersNone(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["colour" mold request-color]
                print ["file" mold request-file]
                print ["files" mold request-file/multi]
                print ["folder" mold request-dir]
                print ["save" mold request-file/save]
                print ["browse" mold browse https://example.com/]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            for (String asked : new String[] {"colour", "file", "files", "folder", "save", "open"}) {
                operator.waitForThePromptSaying(asked);
                operator.declines();
            }

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains(
                    "colour _", "file _", "files _", "folder _", "save _", "browse _");
            assertThat(operator.countTabs()).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("REQUEST-FILE answers a path in the page's store, READ reads what was chosen, and a second pick keeps the first")
    void opensAFile(String browserName) throws IOException {
        Path notes = aFileOnTheOperatorsDisk("notes.txt", "picked text");
        Path again = aFileOnTheOperatorsDisk("again/notes.txt", "picked again");
        try (ShippedRunner runner = new ShippedRunner().running("""
                first: request-file
                print mold first
                print read/string first
                second: request-file
                print mold second
                print read/string second
                print read/string first
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            operator.waitForThePromptSaying("file");
            operator.choosesFiles(notes);
            operator.waitUntilOutputContains("picked text");
            operator.waitForThePromptSaying("file");
            operator.choosesFiles(again);

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).containsSubsequence(
                    "%/picked/1/notes.txt", "picked text", "%/picked/2/notes.txt", "picked again", "picked text");
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("REQUEST-FILE/MULTI/FILTER lets the chooser take several, accepting only the filter's suffixes")
    void opensSeveralFiles(String browserName) throws IOException {
        Path first = aFileOnTheOperatorsDisk("a.r3", "a");
        Path second = aFileOnTheOperatorsDisk("b.r3", "b");
        try (ShippedRunner runner = new ShippedRunner().running("""
                print mold request-file/multi/filter [{Rebol} {*.r3}]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            operator.waitForThePromptSaying("file");
            assertThat(operator.readTheAttributeOf("jebol-ask-file", "accept")).isEqualTo(".r3");
            assertThat(operator.readTheAttributeOf("jebol-ask-file", "multiple")).isNotNull();
            operator.choosesFiles(first, second);

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("[%/picked/1/a.r3 %/picked/1/b.r3]");
        }
    }

    @Test
    @DisplayName("REQUEST-DIR copies the whole folder chosen in Chrome, its tree intact, and answers it")
    void opensAFolderInChrome() throws IOException {
        aFileOnTheOperatorsDisk("photos/a.txt", "a");
        aFileOnTheOperatorsDisk("photos/inner/b.txt", "b");
        try (ShippedRunner runner = new ShippedRunner().running("""
                chosen: request-dir
                print mold chosen
                print read/string chosen/inner/b.txt
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"chrome", runner).opensThePage()) {

            operator.waitForThePromptSaying("folder");
            operator.choosesFolder(operatorsDisk.resolve("photos"));

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).containsSubsequence("%/picked/1/photos/", "b");
        }
    }

    @Test
    @DisplayName("REQUEST-FILE/SAVE in Firefox, which has no save picker, answers a path and downloads the bytes once the WRITE is done")
    void savesThroughADownloadInFirefox() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                chosen: request-file/save/file %draft.txt
                print mold chosen
                write chosen "saved text"
                print read/string chosen
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"firefox", runner).opensThePage()) {

            operator.waitForThePromptSaying("save");
            assertThat(operator.readTheValueOf("jebol-ask-name")).isEqualTo("draft.txt");
            operator.accepts();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).containsSubsequence("%/picked/1/draft.txt", "saved text");
            assertThat(Files.readString(operator.waitForDownload("draft.txt"), StandardCharsets.UTF_8))
                    .isEqualTo("saved text");
            assertThat(operator.listDownloads()).hasSize(1);
        }
    }

    @Test
    @DisplayName("a file saved through a port in Firefox downloads once, when the port is closed, holding everything written")
    void savesThroughAPortInFirefox() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                chosen: request-file/save/file %log.txt
                port: open/write chosen
                write port "one "
                write port "two"
                close port
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"firefox", runner).opensThePage()) {

            operator.waitForThePromptSaying("save");
            operator.accepts();

            operator.waitUntilOutputContains("done");
            assertThat(Files.readString(operator.waitForDownload("log.txt"))).isEqualTo("one two");
            assertThat(operator.listDownloads()).hasSize(1);
        }
    }

    @Test
    @DisplayName("REQUEST-FILE/SAVE in Chrome opens the browser's save picker inside the click, and a picker that came back with nothing is none")
    void savesThroughThePickerInChrome() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["save" mold request-file/save/file %draft.txt]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"chrome", runner).opensThePage()) {

            operator.waitForThePromptSaying("save");
            operator.accepts();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("save _");
            assertThat(operator.listDownloads()).isEmpty();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome", "firefox"})
    @DisplayName("BROWSE opens a new tab inside the click on the prompt, leaves the app's own tab where it is, and answers unset")
    void browseOpensANewTab(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner()
                .servingFile("/elsewhere.html", "<p>elsewhere</p>".getBytes(StandardCharsets.UTF_8), "text/html")
                .running("""
                        print ["opened" unset? browse to url! rejoin [{THE-ORIGIN} "/elsewhere.html"]]
                        print "done"
                        """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            operator.waitForThePromptSaying("open");
            operator.accepts();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("opened true");
            operator.waitForTabs(2);
        }
    }

    @Test
    @DisplayName("a browser that blocks every popup refuses the tab, and the script catches an error saying so")
    void aBlockedTabIsAnError() throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                e: try [browse https://example.com/]
                print [e/id e/arg1]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,"firefox", runner)
                     .whoseBrowserBlocksEveryPopup().opensThePage()) {

            operator.waitForThePromptSaying("open");
            operator.accepts();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("call-fail the browser blocked the new window");
            assertThat(operator.countTabs()).isEqualTo(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"chrome"})
    @DisplayName("REQUEST-PASSWORD draws JEBOL's own form on the page, takes the typing through it, and answers it")
    void asksForAPassword(String browserName) throws IOException {
        try (ShippedRunner runner = new ShippedRunner().running("""
                print ["typed" request-password]
                print ["declined" none? request-password]
                print "done"
                """);
             AnOperatorAtThePage operator = new AnOperatorAtThePage(browsers,browserName, runner).opensThePage()) {

            operator.types("s3cret");
            operator.pressesEnter();
            operator.waitUntilOutputContains("typed s3cret");
            operator.types("half");
            operator.pressesEscape();

            operator.waitUntilOutputContains("done");
            assertThat(operator.readOutput()).contains("declined true");
            assertThat(operator.readOutput()).noneMatch(line -> line.contains("half"));
        }
    }
}
