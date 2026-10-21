package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingAFilePatternFromTheSourceTest {

    private static Interpreter makeAnInterpreterReaching(Path directory) {
        return Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.FILES));
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static void layOut(Path directory) throws IOException {
        Files.createDirectories(directory.resolve("files"));
        for (String name : new String[] {
                "issue-2186-UTF16-BE.txt", "issue-2186-UTF16-LE.txt",
                "issue-2186-UTF32-BE.txt", "one.png", "two.png"}) {
            Files.writeString(directory.resolve("files").resolve(name), "x");
        }
        Files.createDirectories(directory.resolve("files").resolve("icons"));
    }

    @Test
    @DisplayName("a star matches every name that fits around it, and answers names")
    void matchesEveryNameThatFitsAroundAStar(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %files/issue-2186*.txt"))
                .isEqualTo("[%issue-2186-UTF16-BE.txt %issue-2186-UTF16-LE.txt"
                        + " %issue-2186-UTF32-BE.txt]");
    }

    @Test
    @DisplayName("a question mark stands for exactly one character")
    void takesAQuestionMarkForOneCharacter(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory),
                "read %files/issue-218?-UTF16-BE.txt"))
                .isEqualTo("[%issue-2186-UTF16-BE.txt]");
    }

    @Test
    @DisplayName("a star with a suffix takes only the names ending that way")
    void takesOnlyTheNamesAStarWithASuffixFits(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %files/*.png"))
                .isEqualTo("[%one.png %two.png]");
    }

    @Test
    @DisplayName("a star on its own names everything in the directory")
    void namesEverythingForAStarAlone(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "length? read %files/*"))
                .isEqualTo("6");
    }

    @Test
    @DisplayName("a directory that matches keeps its trailing slash")
    void keepsAMatchedDirectorysSlash(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %files/ic*"))
                .isEqualTo("[%icons/]");
    }

    @Test
    @DisplayName("a pattern that matches nothing answers an empty block")
    void answersAnEmptyBlockForAPatternThatMatchesNothing(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %files/nothing-like*.zzz"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("and so does a directory that is not there at all")
    void answersAnEmptyBlockForAnAbsentDirectory(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %no-such-place/*.txt"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("matching minds the case")
    void mindsTheCase(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %files/*.PNG"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("a pattern reaches into one directory and no further")
    void staysOutOfSubdirectories(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %files/*/anything.txt"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("a path with no wildcard in it is still read as a file")
    void stillReadsAPathWithNoWildcardAsAFile(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), "read %files/one.png"))
                .isEqualTo("#{78}");
    }

    @Test
    @DisplayName("the shape Rebol's own ZIP encoder reads a pattern with")
    void readsAPatternInTheShapeRebolsZipEncoderUses(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(makeAnInterpreterReaching(directory), """
                found: copy []
                pattern: %files/issue-2186*.txt
                dir: first split-path pattern
                foreach name read pattern [append found dir/:name]
                found"""))
                .isEqualTo("[%files/issue-2186-UTF16-BE.txt"
                        + " %files/issue-2186-UTF16-LE.txt"
                        + " %files/issue-2186-UTF32-BE.txt]");
    }
}
