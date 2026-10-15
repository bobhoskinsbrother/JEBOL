package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingAFilePatternFromTheSourceTest {

    private static Interpreter reaching(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
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
    void aStarMatchesEveryNameThatFitsAroundIt(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %files/issue-2186*.txt"))
                .isEqualTo("[%issue-2186-UTF16-BE.txt %issue-2186-UTF16-LE.txt"
                        + " %issue-2186-UTF32-BE.txt]");
    }

    @Test
    @DisplayName("a question mark stands for exactly one character")
    void aQuestionMarkStandsForOneCharacter(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory),
                "read %files/issue-218?-UTF16-BE.txt"))
                .isEqualTo("[%issue-2186-UTF16-BE.txt]");
    }

    @Test
    @DisplayName("a star with a suffix takes only the names ending that way")
    void aStarWithASuffixTakesOnlyThoseNames(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %files/*.png"))
                .isEqualTo("[%one.png %two.png]");
    }

    @Test
    @DisplayName("a star on its own names everything in the directory")
    void aStarAloneNamesEverythingInTheDirectory(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "length? read %files/*"))
                .isEqualTo("6");
    }

    @Test
    @DisplayName("a directory that matches keeps its trailing slash")
    void aMatchedDirectoryKeepsItsSlash(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %files/ic*"))
                .isEqualTo("[%icons/]");
    }

    @Test
    @DisplayName("a pattern that matches nothing answers an empty block")
    void aPatternThatMatchesNothingAnswersAnEmptyBlock(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %files/nothing-like*.zzz"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("and so does a directory that is not there at all")
    void anAbsentDirectoryAnswersAnEmptyBlock(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %no-such-place/*.txt"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("matching minds the case")
    void matchingMindsTheCase(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %files/*.PNG"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("a pattern reaches into one directory and no further")
    void aPatternDoesNotReachThroughDirectories(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %files/*/anything.txt"))
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("a path with no wildcard in it is still read as a file")
    void aPathWithNoWildcardIsStillReadAsAFile(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), "read %files/one.png"))
                .isEqualTo("#{78}");
    }

    @Test
    @DisplayName("the shape Rebol's own ZIP encoder reads a pattern with")
    void theShapeRebolsZipEncoderReadsAPatternWith(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
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
