package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppendingToAFilePortFromTheSourceTest {

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

    private static String errorFrom(String source) {
        return answerTo("e: try [" + source
                + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("WRITE/APPEND, which the port arm had been ignoring")
    class WriteAppend {

        @Test
        @DisplayName("writes at the end of what is already there")
        void writesAtTheEnd() {
            assertThat(answerTo("""
                    p: open/new %w
                    write/append p "Hello"
                    write/append p newline
                    close p
                    p: open %w
                    write/append p #{5265626F6C}
                    close p
                    read/string %w""")).isEqualTo("\"Hello^/Rebol\"");
        }

        @Test
        @DisplayName("and answers the file, not the port")
        void answersTheFile() {
            assertThat(answerTo("""
                    p: open/new %w
                    answer: write/append p "Hello"
                    close p
                    reduce [file? answer  answer]""")).isEqualTo("[#(true) %w]");
        }

        @Test
        @DisplayName("from the end rather than from wherever the port was standing")
        void fromTheEndRatherThanTheCurrentPosition() {
            assertThat(answerTo("""
                    write %w "12345"
                    p: open %w
                    write/append p "X"
                    close p
                    read/string %w""")).isEqualTo("\"12345X\"");
        }

        @Test
        @DisplayName("unless a seek position was named too, which wins")
        void aSeekPositionWins() {
            assertThat(answerTo("""
                    write %w "12345"
                    p: open %w
                    write/append/seek p "X" 1
                    close p
                    read/string %w""")).isEqualTo("\"1X345\"");
        }
    }

    @Nested
    @DisplayName("APPEND, which is the same arm")
    class Append {

        @Test
        @DisplayName("writes at the end and answers the file")
        void writesAtTheEndAndAnswersTheFile() {
            assertThat(answerTo("""
                    p: open/new %a
                    append p "Hello"
                    append p newline
                    close p
                    p: open %a
                    answer: append p #{5265626F6C}
                    close p
                    reduce [read/string %a  answer]"""))
                    .isEqualTo("[\"Hello^/Rebol\" %a]");
        }

        @Test
        @DisplayName("and takes a char as readily as a string or a binary")
        void takesACharToo() {
            assertThat(answerTo("""
                    p: open/new %a
                    append p "ab"
                    append p #"c"
                    close p
                    read/string %a""")).isEqualTo("\"abc\"");
        }

        @Test
        @DisplayName("but refuses its own two refinements")
        void refusesItsOwnRefinements() {
            assertThat(errorFrom("""
                    p: open/new %a
                    append/dup p LF 10""")).isEqualTo("bad-refines");
            assertThat(errorFrom("""
                    p: open/new %a
                    append/only p "aa\"""")).isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("before writing anything")
        void beforeWritingAnything() {
            assertThat(answerTo("""
                    write %a "12345"
                    p: open %a
                    try [append/dup p LF 10]
                    close p
                    read/string %a""")).isEqualTo("\"12345\"");
        }
    }

    @Nested
    @DisplayName("a port opened only to read")
    class APortOpenedOnlyToRead {

        @Test
        @DisplayName("refuses CLEAR with write-error, and leaves the file whole")
        void refusesClearWithWriteError() {
            assertThat(errorFrom("""
                    write %r "No clear!"
                    p: open/read %r
                    clear p""")).isEqualTo("write-error");
            assertThat(answerTo("""
                    write %r "No clear!"
                    p: open/read %r
                    try [clear p]
                    close p
                    read/string %r""")).isEqualTo("\"No clear!\"");
        }

        @Test
        @DisplayName("and refuses a write with read-only")
        void refusesAWriteWithReadOnly() {
            assertThat(errorFrom("""
                    write %r "keep me"
                    p: open/read %r
                    write p "gone\"""")).isEqualTo("read-only");
            assertThat(errorFrom("""
                    write %r "keep me"
                    p: open/read %r
                    append p "more\"""")).isEqualTo("read-only");
        }

        @Test
        @DisplayName("where a port opened the ordinary way takes both")
        void aPortOpenedTheOrdinaryWayTakesBoth() {
            assertThat(answerTo("""
                    write %r "Hello World!"
                    p: open %r
                    clear p
                    close p
                    size? %r""")).isEqualTo("0");
            assertThat(answerTo("""
                    write %r "keep me"
                    p: open/read/write %r
                    append p "!"
                    close p
                    read/string %r""")).isEqualTo("\"keep me!\"");
        }
    }
}
