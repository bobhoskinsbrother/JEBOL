package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingTextThroughAFilePortFromTheSourceTest {

    private static Path root;

    @BeforeEach
    void freshRoot(@TempDir Path made) {
        root = made;
    }

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.FILES)
                        .granting(HostService.WORKING_DIRECTORY));
        interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String MIXED_ENDINGS = """
            write %f to-binary "a^M^/b^/c"
            """;

    @Test
    @DisplayName("/string answers text, and is a string rather than a binary")
    void stringAnswersText() {
        assertThat(answerTo(MIXED_ENDINGS + """
                p: open %f
                answer: read/string p
                close p
                reduce [string? answer  answer]""")).isEqualTo("[#(true) \"a^/b^/c\"]");
    }

    @Test
    @DisplayName("so it compares equal to the string a script wrote")
    void itComparesEqualToWhatWasWritten() {
        assertThat(answerTo("""
                write %f "Hello World!"
                p: open %f
                answer: "Hello World!" = read/string p
                close p
                answer""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and the line endings are standardised on the way")
    void theLineEndingsAreStandardised() {
        assertThat(answerTo(MIXED_ENDINGS + """
                p: open %f
                answer: read/string p
                close p
                answer = read/string %f""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/lines splits what /string would have answered")
    void linesSplitsTheText() {
        assertThat(answerTo(MIXED_ENDINGS + """
                p: open %f
                answer: read/lines p
                close p
                reduce [block? answer  answer = ["a" "b" "c"]]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and a plain read still answers the bytes as they are stored")
    void aPlainReadStillAnswersBytes() {
        assertThat(answerTo(MIXED_ENDINGS + """
                p: open %f
                answer: read p
                close p
                reduce [binary? answer  answer]"""))
                .isEqualTo("[#(true) #{610D0A620A63}]");
    }

    @Test
    @DisplayName("and it combines with /seek and /part, which cut the bytes first")
    void itCombinesWithSeekAndPart() {
        assertThat(answerTo(MIXED_ENDINGS + """
                p: open %f
                answer: read/seek/string/part p 0 3
                close p
                answer""")).isEqualTo("\"a^/\"");
    }
}
