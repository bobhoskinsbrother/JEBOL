package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A path read on a file joins rather than selects, from {@code PD_File}.
 *
 * <p>Every other member of the string family reads a path the way
 * {@code PD_String} does: a number picks a character, and LENGTH, SIZE,
 * WIDTH, USER and HOST answer something about the text. A file and a URL do
 * not. {@code boot/types.reb} gives both of them their own handler in the
 * Path column -- {@code file} rather than {@code *} -- and that handler builds
 * a longer path:
 *
 * <pre>
 *   if (n == 0 || c != '/') Append_Byte(ser, '/');
 *   ...
 *   n += (c == '/' || c == '\\') ? 1 : 0;
 *   Append_String(ser, arg, n, arg->tail-n);
 * </pre>
 *
 * <p>So {@code %a/length} is a file named length inside a directory named a,
 * and not the number one. There is no way to ask a file how long it is with a
 * path, which is why the C gives it a handler of its own rather than a case in
 * the string one.
 *
 * <p>This is not a curiosity either. Rebol's own MAKE-DIR builds each level
 * with {@code path: either empty? path [dir][path/:dir]}, so {@code
 * make-dir/deep %a/b/c/} cannot work at all without it.
 */
class FilePathReadFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("the join")
    class WhatItBuilds {

        @Test
        @DisplayName("a slash goes in between when the file has none of its own")
        void aSlashIsAdded() {
            assertThat(answerTo("p: %a d: %b p/:d")).isEqualTo("%a/b");
        }

        @Test
        @DisplayName("and none goes in when it already ends with one")
        void anExistingSlashIsKept() {
            assertThat(answerTo("p: %a/ d: %b p/:d")).isEqualTo("%a/b");
        }

        @Test
        @DisplayName("one leading slash on the selector is dropped, so there is never a double")
        void aLeadingSlashOnTheSelectorIsDropped() {
            assertThat(answerTo("p: %a d: %/b p/:d")).isEqualTo("%a/b");
            assertThat(answerTo("p: %a/ d: %/b p/:d")).isEqualTo("%a/b");
            assertThat(answerTo("p: %a d: %//b p/:d")).isEqualTo("%a//b");
        }

        @Test
        @DisplayName("an empty file leaves the slash it added, so the path is absolute")
        void anEmptyFileBecomesRooted() {
            assertThat(answerTo("p: %\"\" d: %b p/:d")).isEqualTo("%/b");
        }

        @Test
        @DisplayName("a string selector goes in as its text")
        void aStringSelectorIsItsText() {
            assertThat(answerTo("p: %a d: \"b\" p/:d")).isEqualTo("%a/b");
        }

        @Test
        @DisplayName("and anything else is molded into one")
        void anythingElseIsMolded() {
            assertThat(answerTo("p: %a d: 5 p/:d")).isEqualTo("%a/5");
            assertThat(answerTo("p: %a d: 'b p/:d")).isEqualTo("%a/b");
            assertThat(answerTo("p: %a d: 1.2.3 p/:d")).isEqualTo("%a/1.2.3");
        }

        @Test
        @DisplayName("a word written straight into the path is molded the same way")
        void aWrittenWordJoinsToo() {
            assertThat(answerTo("p: %a p/length")).isEqualTo("%a/length");
            assertThat(answerTo("p: %a p/size")).isEqualTo("%a/size");
        }

        @Test
        @DisplayName("and a string still answers what it always did")
        void aStringIsUnaffected() {
            assertThat(answerTo("s: \"abc\" s/length")).isEqualTo("3");
            assertThat(answerTo("s: \"abc\" s/2")).isEqualTo("#\"b\"");
        }

        @Test
        @DisplayName("segment after segment builds the whole path")
        void severalSegmentsInOnePath() {
            assertThat(answerTo("p: %a b: %b c: %c p/:b/:c")).isEqualTo("%a/b/c");
        }

        @Test
        @DisplayName("a URL joins the same way and stays a URL")
        void aUrlKeepsItsDatatype() {
            assertThat(answerTo("p: http://example.com d: %b p/:d"))
                    .isEqualTo("http://example.com/b");
            assertThat(answerTo("p: %a d: %b type? p/:d")).isEqualTo("#(file!)");
        }

        @Test
        @DisplayName("and the join reads the file from where it stands, not from its head")
        void aSkippedFileJoinsFromWhereItIs() {
            assertThat(answerTo("p: skip %ab/ 1 d: %c p/:d")).isEqualTo("%b/c");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class WhatItRefuses {

        @Test
        @DisplayName("writing through a file path, which is the first line of the handler")
        void aPathSetIsRefused() {
            assertThat(errorIdFrom("p: %a d: %b p/:d: 5")).isEqualTo("bad-path-set");
        }

        @Test
        @DisplayName("and a written segment cannot be set either")
        void aWrittenSegmentCannotBeSet() {
            assertThat(errorIdFrom("p: %a p/b: 5")).isEqualTo("bad-path-set");
        }
    }

    @Nested
    @DisplayName("MAKE-DIR/DEEP, which is what needs it")
    class TheCallerThatNeedsIt {

        @Test
        @DisplayName("builds each level from the one above")
        void deepBuildsEachLevel() {
            assertThat(answerTo(
                    "path: copy %\"\" foreach dir [%a %b %c] "
                    + "[path: either empty? path [dir][path/:dir] append path \"/\"] "
                    + "path")).isEqualTo("%a/b/c/");
        }
    }
}
