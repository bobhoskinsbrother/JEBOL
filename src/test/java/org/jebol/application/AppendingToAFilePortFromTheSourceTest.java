package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * APPEND on a file port is WRITE/APPEND, and the C makes them the same arm:
 * {@code case A_APPEND} sets the end position and falls through into
 * {@code case A_WRITE} without a break.
 *
 * <p>So it writes at the end, answers the file rather than the port, and takes
 * every one of WRITE's refinements -- while refusing the two that are APPEND's
 * own, because /dup and /only mean nothing to a write and guessing at them
 * would put ten line feeds where the caller asked for ten, or one.
 *
 * <p>Every expectation was read off {@code ./r3-head} first.
 */
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

        /** A freshly opened port is at nought, so the end is where the size is. */
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

        /** A seek position beats it, which is the order the C sets them in. */
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

        /** And refuses them before writing anything, so nothing lands. */
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

    /**
     * A port opened only to read refuses a change rather than taking it and
     * doing nothing, which is what the C's own comment on the CLEAR arm was
     * written for: "When the port is opened with a read-only policy, this call
     * would be silently ignored without the check below."
     *
     * <p>Two different ids, because the two arms trap differently:
     * {@code Trap1(RE_WRITE_ERROR, path)} on CLEAR and
     * {@code Trap1(RE_READ_ONLY, path)} on WRITE.
     */
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

        /** Where a port opened the ordinary way takes both. */
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
