package org.jebol.domain.read;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LineMarksTravelWithTheirValuesFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static final String FALSE = "#(false)";

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerWithFilesUnder(Path root, String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(root)),
                OutputPort.discard(),
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("what LOAD and TRANSCODE read keeps its marks however it was read")
    class Reading {

        @Test
        @DisplayName("a binary source is marked as a string source is")
        void markABinarySource() {
            assertThat(answerTo("""
                    new-line? next transcode to binary! {a^/b}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("LOAD/ALL marks what it reads")
        void markUnderAll() {
            assertThat(answerTo("""
                    equal? mold load/all {a^/b} {[a^/b]}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the line feed that ends a header is skipped, so the first value of the body is unmarked")
        void skipTheHeadersOwnLineFeed() {
            assertThat(answerTo("""
                    equal? mold load {Rebol []^/a^/b} {[a^/b]}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a second blank line after a header does mark the first value")
        void markAfterABlankLineBelowTheHeader() {
            assertThat(answerTo("""
                    new-line? load {Rebol []^/^/a^/b}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("LOAD/HEADER puts the header in front, and each mark stays with its value")
        void keepTheMarksBehindTheHeader() {
            assertThat(answerTo("""
                    object? first load/header {Rebol []^/a^/b}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    new-line? next load/header {Rebol []^/a^/b}""")).isEqualTo(FALSE);
            assertThat(answerTo("""
                    new-line? next next load/header {Rebol []^/a^/b}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a script read from a file keeps the lines it was written on")
        void markWhatIsLoadedFromAFile(@TempDir Path root) {
            assertThat(answerWithFilesUnder(root, """
                    write %script.r3 {Rebol []^/x: 1^/y: 2^/}
                    new-line? find load %script.r3 quote y:""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and a mark belongs to its value, so it moves when the block changes")
    class MarksMoveWithTheirValues {

        @Test
        @DisplayName("INSERT at the head pushes every mark along by one")
        void moveMarksAlongOnInsert() {
            assertThat(answerTo("""
                    b: transcode {^/a^/b} insert b 'x
                    reduce [new-line? b new-line? next b new-line? next next b]""")).isEqualTo("""
                    [#(false) #(true) #(true)]""");
        }

        @Test
        @DisplayName("REMOVE takes the removed value's mark with it and pulls the rest back")
        void moveMarksBackOnRemove() {
            assertThat(answerTo("""
                    b: transcode {x^/a b} remove b
                    reduce [new-line? b new-line? next b]""")).isEqualTo("""
                    [#(true) #(false)]""");
        }

        @Test
        @DisplayName("TRIM/ALL keeps the marks of what it keeps, and drops the marks of the nones")
        void keepTheMarksOfWhatTrimKeeps() {
            assertThat(answerTo("""
                    b: transcode {_^/a^/_ b _} trim/all b
                    reduce [length? b new-line? b new-line? next b]""")).isEqualTo("""
                    [2 #(true) #(false)]""");
        }

        @Test
        @DisplayName("TRIM from the head only leaves the middle where it was")
        void keepTheMiddleOnTrimHead() {
            assertThat(answerTo("""
                    b: transcode {_^/a^/_ b _} trim/head b
                    reduce [length? b new-line? b new-line? next b]""")).isEqualTo("""
                    [4 #(true) #(true)]""");
        }

        @Test
        @DisplayName("TRIM refuses a protected block even when there is nothing to trim")
        void refuseToTrimAProtectedBlock() {
            assertThat(answerTo("""
                    e: try [trim protect []] e/id""")).isEqualTo("protected");
        }

        @Test
        @DisplayName("REMOVE-EACH keeps the marks of what it keeps")
        void keepTheMarksOfWhatRemoveEachKeeps() {
            assertThat(answerTo("""
                    b: transcode {1^/2 3^/4} remove-each n b [odd? n]
                    reduce [length? b new-line? b new-line? next b]""")).isEqualTo("""
                    [2 #(true) #(true)]""");
        }
    }
}
