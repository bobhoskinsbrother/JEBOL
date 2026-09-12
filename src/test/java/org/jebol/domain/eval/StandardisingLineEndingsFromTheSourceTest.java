package org.jebol.domain.eval;

import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StandardisingLineEndingsFromTheSourceTest {

    private static Path root;

    @org.junit.jupiter.api.BeforeEach
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

    private static String readAsText(String bytes) {
        return answerTo("read/string write/binary %tmp " + bytes);
    }

    @Nested
    @DisplayName("reading bytes back as text")
    class ReadingBytesBackAsText {

        @Test
        @DisplayName("a line feed is left alone")
        void aLineFeedIsLeftAlone() {
            assertThat(readAsText("#{0A}")).isEqualTo("\"^/\"");
            assertThat(readAsText("#{0A0A}")).isEqualTo("\"^/^/\"");
        }

        @Test
        @DisplayName("a carriage return and line feed together are one line feed")
        void aPairIsOneLineFeed() {
            assertThat(readAsText("#{0D0A}")).isEqualTo("\"^/\"");
        }

        @Test
        @DisplayName("and a carriage return on its own is a line feed too")
        void aLoneCarriageReturnIsALineFeed() {
            assertThat(readAsText("#{0D}")).isEqualTo("\"^/\"");
            assertThat(readAsText("#{610D62}")).isEqualTo("\"a^/b\"");
        }

        @Test
        @DisplayName("and a line feed followed by a return is one ending, not two")
        void aLineFeedThenAReturnIsOneEnding() {
            assertThat(readAsText("#{0A0D}")).isEqualTo("\"^/\"");
        }

        @Test
        @DisplayName("but two returns then a line feed are two endings")
        void twoReturnsThenALineFeedAreTwoEndings() {
            assertThat(readAsText("#{0D0D0A}")).isEqualTo("\"^/^/\"");
            assertThat(readAsText("#{0D0D}")).isEqualTo("\"^/^/\"");
        }

        @Test
        @DisplayName("and a return then a line feed then another line feed is two")
        void aReturnThenTwoLineFeedsIsTwoEndings() {
            assertThat(readAsText("#{0D0A0A}")).isEqualTo("\"^/^/\"");
        }

        @Test
        @DisplayName("nothing at all converts to nothing")
        void nothingConvertsToNothing() {
            assertThat(readAsText("#{}")).isEqualTo("\"\"");
        }
    }

    @Nested
    @DisplayName("DELINE, which is the same conversion")
    class DelineIsTheSameConversion {

        @Test
        @DisplayName("converts a lone return and both orders of the pair")
        void convertsALoneReturnAndBothOrdersOfThePair() {
            assertThat(answerTo("deline {a^Mb}")).isEqualTo("\"a^/b\"");
            assertThat(answerTo("deline {a^M^/b}")).isEqualTo("\"a^/b\"");
            assertThat(answerTo("deline {a^/^Mb}")).isEqualTo("\"a^/b\"");
        }

        @Test
        @DisplayName("and two returns then a line feed are two endings here as well")
        void twoReturnsThenALineFeedAreTwoEndingsHereAsWell() {
            assertThat(answerTo("deline {a^M^Mb}")).isEqualTo("\"a^/^/b\"");
            assertThat(answerTo("deline {a^M^M^/b}")).isEqualTo("\"a^/^/b\"");
        }

        @Test
        @DisplayName("and on a wide string, which the C converts separately")
        void andOnAWideString() {
            assertThat(answerTo("deline {á^Mb}")).isEqualTo("\"á^/b\"");
            assertThat(readAsText("#{C3A10D62}")).isEqualTo("\"á^/b\"");
            assertThat(readAsText("#{C3A10D0D0A}")).isEqualTo("\"á^/^/\"");
        }
    }
}
