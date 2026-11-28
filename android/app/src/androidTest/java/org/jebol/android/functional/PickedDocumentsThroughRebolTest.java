package org.jebol.android.functional;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.adapter.android.AndroidHost;
import org.jebol.adapter.android.PickedDocuments;
import org.jebol.android.AppSession;
import org.jebol.android.TestDocuments;
import org.jebol.application.Interpreter;
import org.jebol.application.ListedFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
class PickedDocumentsThroughRebolTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Path documents = TestDocuments.folder(context);
    private static final String TASK_DONE = "the task is done";

    private final PrintedOutput printed = new PrintedOutput();

    @BeforeEach
    void startWithNothingPicked() throws IOException {
        emptyTheDocuments();
        Files.deleteIfExists(AndroidHost.pickTable(context));
    }

    @AfterEach
    void leaveNothingPicked() throws IOException {
        emptyTheDocuments();
        Files.deleteIfExists(AndroidHost.pickTable(context));
    }

    private void emptyTheDocuments() throws IOException {
        if (Files.exists(documents)) {
            try (Stream<Path> held = Files.walk(documents)) {
                for (Path each : held.sorted(Comparator.reverseOrder()).toList()) {
                    if (!each.equals(documents)) {
                        Files.delete(each);
                    }
                }
            }
        }
        Files.createDirectories(documents);
    }

    private Path document(String relative, String contents) throws IOException {
        Path made = documents.resolve(relative);
        Files.createDirectories(made.getParent());
        return Files.write(made, contents.getBytes(StandardCharsets.UTF_8));
    }

    private static String contents(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private void pickFile(String relative) {
        Uri reference = DocumentsContract.buildDocumentUri(TestDocuments.AUTHORITY, TestDocuments.idFor(relative));
        PickedDocuments.keptIn(AndroidHost.pickTable(context))
                .keep(reference.toString(), Path.of(relative).getFileName().toString(), false);
    }

    private void pickFolder(String relative) {
        Uri reference = DocumentsContract.buildTreeDocumentUri(TestDocuments.AUTHORITY, TestDocuments.idFor(relative));
        PickedDocuments.keptIn(AndroidHost.pickTable(context))
                .keep(reference.toString(), Path.of(relative).getFileName().toString(), true);
    }

    private Interpreter app() {
        return Interpreter.createOn(AndroidHost.appPorts(context, ListedFiles.of(List.of(), name -> new byte[0]),
                () -> false, AppSession.appBounds()), printed, AppSession.appBounds());
    }

    private static String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a picked file reads at the page's path, and a WRITE replaces the user's document itself")
    void aPickedFileIsReadAndWrittenInPlace() throws IOException {
        Path notes = document("notes.txt", "the user's notes");
        pickFile("notes.txt");

        assertThat(run(app(), "read/string %/picked/1/notes.txt")).isEqualTo("\"the user's notes\"");
        assertThat(run(app(), "write %/picked/1/notes.txt {rewritten} true")).isEqualTo("#(true)");

        assertThat(contents(notes)).isEqualTo("rewritten");
        assertThat(Files.exists(AndroidHost.storeFolder(context).resolve("picked"))).isFalse();
    }

    @Test
    @DisplayName("a shorter WRITE leaves nothing of the longer contents behind")
    void aShorterWriteTruncates() throws IOException {
        Path notes = document("notes.txt", "a much longer first version");
        pickFile("notes.txt");

        run(app(), "write %/picked/1/notes.txt {short}");

        assertThat(contents(notes)).isEqualTo("short");
    }

    @Test
    @DisplayName("WRITE/APPEND adds to the end of the user's document")
    void anAppendAdds() throws IOException {
        Path log = document("log.txt", "one");
        pickFile("log.txt");

        run(app(), "write/append %/picked/1/log.txt { two}");

        assertThat(contents(log)).isEqualTo("one two");
    }

    @Test
    @DisplayName("a write at a position changes the document where it is, as Rebol's own write/seek test does")
    void aWriteAtAPosition() throws IOException {
        Path hello = document("hello.txt", "Hello World!");
        pickFile("hello.txt");

        assertThat(run(app(), """
                f: open/seek %/picked/1/hello.txt
                write/seek f to-binary "a" 4
                write/seek f to-binary " Goodbye World!" 12
                close f
                read/string %/picked/1/hello.txt""")).isEqualTo("\"Hella World! Goodbye World!\"");
        assertThat(contents(hello)).isEqualTo("Hella World! Goodbye World!");
    }

    @Test
    @DisplayName("QUERY answers the document's size and modification time from its columns")
    void queryReadsTheColumns() throws IOException {
        Path data = document("data.bin", "12345");
        pickFile("data.bin");
        Instant modified = Instant.parse("2030-06-01T12:00:00Z");
        Files.setLastModifiedTime(data, FileTime.from(modified));

        assertThat(run(app(), "size? %/picked/1/data.bin")).isEqualTo("5");
        assertThat(run(app(), "query %/picked/1/data.bin 'type")).isEqualTo("file");
        assertThat(run(app(), "to integer! modified? %/picked/1/data.bin")).isEqualTo(Long.toString(modified.getEpochSecond()));
    }

    @Test
    @DisplayName("a column the provider leaves empty answers none for that field alone")
    void anEmptyColumnIsNone() throws IOException {
        document("unsized.bin", "12345");
        pickFile("unsized.bin");

        assertThat(run(app(), """
                f: %/picked/1/unsized.bin
                all ['file = query f 'type  none? query f 'size  none? query f 'date]"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("%/picked/ lists each pick's number, a pick lists its name, and the root lists picked/")
    void thePicksList() throws IOException {
        document("notes.txt", "n");
        document("Docs/a.txt", "a");
        pickFile("notes.txt");
        pickFolder("Docs");

        Interpreter interpreter = app();

        assertThat(run(interpreter, "read %/picked/")).isEqualTo("[%1/ %2/]");
        assertThat(run(interpreter, "read %/picked/1/")).isEqualTo("[%notes.txt]");
        assertThat(run(interpreter, "read %/picked/2/")).isEqualTo("[%Docs/]");
        assertThat(run(interpreter, "to logic! find read %/ %picked/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the root lists no picked/ before anything has been picked")
    void noPicksNoFolder() {
        assertThat(run(app(), "none? find read %/ %picked/")).isEqualTo("#(true)");
        assertThat(run(app(), "not exists? %/picked/")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a picked folder lists and walks its children, a child folder with a slash after its name")
    void aPickedFolderWalks() throws IOException {
        document("Docs/a.txt", "a");
        document("Docs/inner/b.txt", "b");
        document("Docs/inner/deeper/c.txt", "c");
        pickFolder("Docs");

        Interpreter interpreter = app();

        assertThat(run(interpreter, "sort read %/picked/1/Docs/")).isEqualTo("[%a.txt %inner/]");
        assertThat(run(interpreter, "read/string %/picked/1/Docs/inner/deeper/c.txt")).isEqualTo("\"c\"");
        assertThat(run(interpreter, "dir? %/picked/1/Docs/inner/")).isEqualTo("#(true)");
        assertThat(run(interpreter, "not exists? %/picked/1/Docs/nothing.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("change-dir into a picked folder makes relative paths count from there")
    void changeDirectoryIntoAPick() throws IOException {
        document("Docs/a.txt", "a");
        pickFolder("Docs");

        Interpreter interpreter = app();

        assertThat(run(interpreter, "change-dir %/picked/1/Docs/ what-dir")).isEqualTo("%/picked/1/Docs/");
        assertThat(run(interpreter, "read/string %a.txt")).isEqualTo("\"a\"");
    }

    @Test
    @DisplayName("inside a picked folder a new file, MAKE-DIR, RENAME and DELETE reach the user's folder")
    void aPickedFolderChanges() throws IOException {
        document("Docs/a.txt", "a");
        pickFolder("Docs");

        Interpreter interpreter = app();
        run(interpreter, "write %/picked/1/Docs/new.txt {made by the script}");
        run(interpreter, "make-dir %/picked/1/Docs/made/");
        run(interpreter, "write %/picked/1/Docs/made/inside.txt {deeper}");
        run(interpreter, "rename %/picked/1/Docs/a.txt %b.txt");

        assertThat(contents(documents.resolve("Docs/new.txt"))).isEqualTo("made by the script");
        assertThat(contents(documents.resolve("Docs/made/inside.txt"))).isEqualTo("deeper");
        assertThat(Files.exists(documents.resolve("Docs/a.txt"))).isFalse();
        assertThat(contents(documents.resolve("Docs/b.txt"))).isEqualTo("a");

        assertThat(run(interpreter, "port? delete %/picked/1/Docs/b.txt")).isEqualTo("#(true)");
        assertThat(run(interpreter, "false = delete %/picked/1/Docs/b.txt")).isEqualTo("#(true)");
        assertThat(Files.exists(documents.resolve("Docs/b.txt"))).isFalse();
    }

    @Test
    @DisplayName("MAKE-DIR/DEEP makes each folder missing on the way")
    void makeDirectoryDeep() throws IOException {
        Files.createDirectories(documents.resolve("Docs"));
        pickFolder("Docs");

        run(app(), "make-dir/deep %/picked/1/Docs/one/two/");

        assertThat(Files.isDirectory(documents.resolve("Docs/one/two"))).isTrue();
    }

    @Test
    @DisplayName("a picked file renamed keeps its number and is found under its new name")
    void aPickedFileRenamed() throws IOException {
        document("old.txt", "kept");
        pickFile("old.txt");

        Interpreter interpreter = app();
        run(interpreter, "rename %/picked/1/old.txt %new.txt");

        assertThat(run(interpreter, "read/string %/picked/1/new.txt")).isEqualTo("\"kept\"");
        assertThat(run(app(), "read %/picked/1/")).isEqualTo("[%new.txt]");
    }

    @Test
    @DisplayName("a document the provider will not let be written is refused as a desktop refuses a write")
    void aReadOnlyDocumentRefusesAWrite() throws IOException {
        Path fixed = document("read-only.txt", "fixed");
        pickFile("read-only.txt");

        assertThat(run(app(), "e: try [write %/picked/1/read-only.txt {changed}] e/id")).isEqualTo("cannot-open");
        assertThat(contents(fixed)).isEqualTo("fixed");
    }

    @Test
    @DisplayName("a folder the provider will not change refuses a new file, MAKE-DIR and DELETE with the desktop's ids, as does a file in MAKE-DIR's way")
    void aReadOnlyFolderRefuses() throws IOException {
        document("read-only-folder/a.txt", "a");
        pickFolder("read-only-folder");

        Interpreter interpreter = app();
        String folder = "%/picked/1/read-only-folder/";

        assertThat(run(interpreter, "e: try [write " + folder + "new.txt {x}] e/id")).isEqualTo("cannot-open");
        assertThat(run(interpreter, "e: try [make-dir " + folder + "made/] e/id")).isEqualTo("no-create");
        assertThat(run(interpreter, "e: try [make-dir " + folder + "a.txt] e/id")).isEqualTo("no-create");
        assertThat(run(interpreter, "e: try [delete " + folder + "a.txt] e/id")).isEqualTo("no-delete");
        assertThat(Files.exists(documents.resolve("read-only-folder/a.txt"))).isTrue();
    }

    @Test
    @DisplayName("a document gone from its provider leaves its path naming nothing, and READ raises cannot-open")
    void aGoneDocumentNamesNothing() throws IOException {
        Path notes = document("notes.txt", "n");
        pickFile("notes.txt");
        Files.delete(notes);

        assertThat(run(app(), "not exists? %/picked/1/notes.txt")).isEqualTo("#(true)");
        assertThat(run(app(), "e: try [read %/picked/1/notes.txt] e/id")).isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("a pick number never used names nothing, and a name the pick does not have names nothing")
    void anUnknownPathNamesNothing() throws IOException {
        document("notes.txt", "n");
        pickFile("notes.txt");

        assertThat(run(app(), "not exists? %/picked/7/notes.txt")).isEqualTo("#(true)");
        assertThat(run(app(), "not exists? %/picked/1/other.txt")).isEqualTo("#(true)");
        assertThat(run(app(), "e: try [write %/picked/1/other.txt {x}] e/id")).isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("a task reaches the same picks as the interpreter that started it")
    void aTaskReachesThePicks() throws Exception {
        document("notes.txt", "from a task");
        pickFile("notes.txt");

        run(app(), "do make task! [write %copied.txt read %/picked/1/notes.txt print {" + TASK_DONE + "}]");

        assertThat(printed.awaitPrinted(TASK_DONE, Duration.ofSeconds(30)))
                .as(() -> "the task printed that it was done; it printed: " + printed.text())
                .isTrue();
        Path written = AndroidHost.storeFolder(context).resolve("copied.txt");
        assertThat(contents(written)).isEqualTo("from a task");
        Files.delete(written);
    }
}
