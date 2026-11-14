package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.ClipboardPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class APortsStateGatesItsVerbsFromTheSourceTest {

    private static final class AnEmptyClipboard implements ClipboardPort {

        @Override
        public Optional<String> read() {
            return Optional.of("");
        }

        @Override
        public boolean write(String text) {
            return true;
        }
    }

    private static String answerTo(Path root, String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(root))
                        .withClipboard(new AnEmptyClipboard()),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY)
                        .grant(HostService.CLIPBOARD));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String refusalOf(Path root, String setUp, String expression) {
        return answerTo(root, setUp + "\ne: try [" + expression + "] reduce [e/id e/arg1]");
    }

    private static void holding(Path root, String name) throws IOException {
        Files.writeString(root.resolve(name), "x");
    }

    @Nested
    @DisplayName("an open file port holds its file")
    class AnOpenFile {

        @Test
        @DisplayName("a second OPEN is already-open, naming the file")
        void refusesASecondOpen(@TempDir Path root) throws IOException {
            holding(root, "held.txt");
            assertThat(refusalOf(root, "p: open %held.txt", "open p"))
                    .isEqualTo("[already-open %held.txt]");
            assertThat(answerTo(root, "p: open %held.txt try [open p] open? p")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("DELETE is no-delete, naming the file, and the file stays")
        void refusesDelete(@TempDir Path root) throws IOException {
            holding(root, "held.txt");
            assertThat(refusalOf(root, "p: open %held.txt", "delete p"))
                    .isEqualTo("[no-delete %held.txt]");
            assertThat(root.resolve("held.txt")).exists();
        }

        @Test
        @DisplayName("RENAME is no-rename, naming the file, and nothing moves")
        void refusesRename(@TempDir Path root) throws IOException {
            holding(root, "held.txt");
            assertThat(refusalOf(root, "p: open %held.txt", "rename p %renamed.txt"))
                    .isEqualTo("[no-rename %held.txt]");
            assertThat(root.resolve("held.txt")).exists();
            assertThat(root.resolve("renamed.txt")).doesNotExist();
        }

        @Test
        @DisplayName("and the port still reads its file afterwards")
        void stillReads(@TempDir Path root) throws IOException {
            holding(root, "held.txt");
            assertThat(answerTo(root, """
                    p: open %held.txt
                    try [delete p]
                    try [rename p %renamed.txt]
                    read p""")).isEqualTo("#{78}");
        }

        @Test
        @DisplayName("a port opened anew is open, so DELETE is refused there too")
        void refusesDeleteOfANewPort(@TempDir Path root) {
            assertThat(refusalOf(root, "", "delete open/new %gone.txt"))
                    .isEqualTo("[no-delete %gone.txt]");
            assertThat(root.resolve("gone.txt")).exists();
        }

        @Test
        @DisplayName("CREATE leaves an open port alone and answers it")
        void leavesItAloneOnCreate(@TempDir Path root) throws IOException {
            holding(root, "held.txt");
            assertThat(answerTo(root, "p: open %held.txt port? create p")).isEqualTo("#(true)");
            assertThat(root.resolve("held.txt")).hasContent("x");
        }

        @Test
        @DisplayName("renaming the file by its name, not its port, still moves it")
        void movesItByName(@TempDir Path root) throws IOException {
            holding(root, "named.txt");
            assertThat(answerTo(root, """
                    p: open %named.txt
                    rename %named.txt %moved.txt
                    close p
                    reduce [exists? %named.txt exists? %moved.txt]""")).isEqualTo("[_ file]");
        }

        @Test
        @DisplayName("a port on a file url names the file it reached")
        void namesTheFileOfAUrl(@TempDir Path root) throws IOException {
            holding(root, "u.txt");
            assertThat(answerTo(root, """
                    p: open join file:// clean-path %u.txt
                    a: try [open p]
                    d: try [delete p]
                    reduce [a/id a/arg1 = clean-path %u.txt d/id d/arg1 = clean-path %u.txt]"""))
                    .isEqualTo("[already-open #(true) no-delete #(true)]");
        }
    }

    @Nested
    @DisplayName("a closed file port is its file")
    class AClosedFile {

        @Test
        @DisplayName("RENAME moves it and answers the port")
        void renames(@TempDir Path root) throws IOException {
            holding(root, "held.txt");
            assertThat(answerTo(root, """
                    p: open %held.txt
                    close p
                    r: rename p %renamed.txt
                    reduce [port? r exists? %held.txt exists? %renamed.txt]"""))
                    .isEqualTo("[#(true) _ file]");
        }

        @Test
        @DisplayName("DELETE removes it and answers the port")
        void deletes(@TempDir Path root) throws IOException {
            holding(root, "doomed.txt");
            assertThat(answerTo(root, """
                    p: open %doomed.txt
                    close p
                    reduce [port? delete p exists? %doomed.txt]""")).isEqualTo("[#(true) _]");
        }

        @Test
        @DisplayName("DELETE of a file that is gone answers false")
        void answersFalseForAFileGone(@TempDir Path root) {
            assertThat(answerTo(root, """
                    p: open %missing.txt
                    close p
                    delete %missing.txt
                    delete p""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("closing twice is harmless")
        void closesTwice(@TempDir Path root) throws IOException {
            holding(root, "held.txt");
            assertThat(answerTo(root, "p: open %held.txt close p port? close p")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a file that cannot be made names itself and the number 3")
    class AFileThatCannotBeMade {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "open %missing-dir/x",
                "open/new %missing-dir/x",
                "open/read %missing-dir/x",
                "write %missing-dir/x {a}",
                "write/append %missing-dir/x {a}",
                "create %missing-dir/x"})
        @DisplayName("is cannot-open, naming the file")
        void namesTheFile(String expression, @TempDir Path root) {
            assertThat(answerTo(root, "e: try [" + expression + "] reduce [e/id e/arg1 e/arg2]"))
                    .isEqualTo("[cannot-open %missing-dir/x 3]");
        }

        @Test
        @DisplayName("and a file url is named by its url")
        void namesTheUrl(@TempDir Path root) {
            assertThat(answerTo(root, """
                    u: join file:// clean-path %missing-dir/x
                    e: try [open u]
                    reduce [e/id e/arg1 = u e/arg2]""")).isEqualTo("[cannot-open #(true) 3]");
        }
    }

    @Nested
    @DisplayName("an open directory port")
    class AnOpenDirectory {

        @Test
        @DisplayName("refuses CREATE as already-open, naming the directory")
        void refusesCreate(@TempDir Path root) {
            assertThat(refusalOf(root, "make-dir %d/ d: open %d/", "create d"))
                    .isEqualTo("[already-open %d/]");
        }

        @Test
        @DisplayName("reads the listing again on a second OPEN and stays open")
        void opensAgain(@TempDir Path root) {
            assertThat(answerTo(root, "make-dir %d/ d: open %d/ reduce [port? open d open? d]"))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("is deleted by DELETE, and is closed afterwards")
        void deletesAndCloses(@TempDir Path root) {
            assertThat(answerTo(root, """
                    make-dir %d/
                    d: open %d/
                    r: delete d
                    reduce [port? r exists? %d/ open? d]""")).isEqualTo("[#(true) _ #(false)]");
            assertThat(root.resolve("d")).doesNotExist();
        }

        @Test
        @DisplayName("is renamed by RENAME")
        void renames(@TempDir Path root) {
            assertThat(answerTo(root, """
                    make-dir %r/
                    r: open %r/
                    rename r %s/
                    reduce [exists? %r/ exists? %s/]""")).isEqualTo("[_ dir]");
        }

        @Test
        @DisplayName("and one opened, closed and not there cannot be created by CREATE")
        void refusesCreateOfOneGone(@TempDir Path root) {
            assertThat(refusalOf(root, "", "d: open %e/ close d create d"))
                    .isEqualTo("[cannot-open %e/]");
            assertThat(root.resolve("e")).doesNotExist();
        }
    }

    @Nested
    @DisplayName("CREATE and OPEN/NEW make a directory and leave its port open")
    class MakingADirectory {

        @Test
        @DisplayName("CREATE makes it, answers the port and leaves it open")
        void createsAndOpens(@TempDir Path root) {
            assertThat(answerTo(root, """
                    d: make port! %q/
                    r: create d
                    reduce [port? r open? d exists? %q/]""")).isEqualTo("[#(true) #(true) dir]");
            assertThat(root.resolve("q")).isDirectory();
        }

        @Test
        @DisplayName("OPEN/NEW makes it and answers it open")
        void opensAnew(@TempDir Path root) {
            assertThat(answerTo(root, "d: open/new %n2/ reduce [open? d exists? %n2/]"))
                    .isEqualTo("[#(true) dir]");
            assertThat(root.resolve("n2")).isDirectory();
        }

        @Test
        @DisplayName("and it lists what is put in it afterwards")
        void listsWhatIsInIt(@TempDir Path root) {
            assertThat(answerTo(root, """
                    d: open/new %n4/
                    write %n4/x.txt "a"
                    read d""")).isEqualTo("[%x.txt]");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"open/new %n2/", "create %n2/"})
        @DisplayName("a directory already there is no-create, naming it")
        void refusesOneAlreadyThere(String expression, @TempDir Path root) throws IOException {
            Files.createDirectory(root.resolve("n2"));
            assertThat(refusalOf(root, "", expression)).isEqualTo("[no-create %n2/]");
        }

        @Test
        @DisplayName("a directory whose parent is not there is no-create, and nothing is made")
        void refusesOneWithoutItsParent(@TempDir Path root) {
            assertThat(refusalOf(root, "", "open/new %deep/er/")).isEqualTo("[no-create %deep/er/]");
            assertThat(root.resolve("deep")).doesNotExist();
        }

        @Test
        @DisplayName("OPEN/NEW of an open directory port is already-open")
        void refusesOpenNewWhileOpen(@TempDir Path root) {
            assertThat(refusalOf(root, "d: open/new %n3/", "open/new d")).isEqualTo("[already-open %n3/]");
        }

        @Test
        @DisplayName("MAKE-DIR of a directory already there is still no error")
        void makeDirStaysQuiet(@TempDir Path root) {
            assertThat(answerTo(root, "make-dir %m/ make-dir %m/")).isEqualTo("%m/");
        }
    }

    @Nested
    @DisplayName("the clipboard opened twice")
    class TheClipboard {

        @Test
        @DisplayName("is already-open, naming the clipboard")
        void refusesASecondOpen(@TempDir Path root) {
            assertThat(refusalOf(root, "c: open clipboard://", "open c"))
                    .isEqualTo("[already-open clipboard://]");
        }

        @Test
        @DisplayName("and stays open")
        void staysOpen(@TempDir Path root) {
            assertThat(answerTo(root, "c: open clipboard:// try [open c] open? c")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but opens again once closed")
        void opensAgainAfterClosing(@TempDir Path root) {
            assertThat(answerTo(root, "c: open clipboard:// close c port? open c")).isEqualTo("#(true)");
        }
    }
}
