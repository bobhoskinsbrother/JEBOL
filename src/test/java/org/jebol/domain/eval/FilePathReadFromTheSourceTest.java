package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            // `if (n == 0 || c != '/')` -- so the two forms of the same
            // directory build the same path.
            assertThat(answerTo("p: %a/ d: %b p/:d")).isEqualTo("%a/b");
        }

        @Test
        @DisplayName("one leading slash on the selector is dropped, so there is never a double")
        void aLeadingSlashOnTheSelectorIsDropped() {
            // `n += (c == '/' || c == '\\') ? 1 : 0;` -- one, not all of
            // them, and a backslash counts as one too.
            assertThat(answerTo("p: %a d: %/b p/:d")).isEqualTo("%a/b");
            assertThat(answerTo("p: %a/ d: %/b p/:d")).isEqualTo("%a/b");
            assertThat(answerTo("p: %a d: %//b p/:d")).isEqualTo("%a//b");
        }

        @Test
        @DisplayName("an empty file leaves the slash it added, so the path is absolute")
        void anEmptyFileBecomesRooted() {
            // The `n == 0` half of the same line: an empty series gets the
            // slash as well, so joining onto nothing gives a rooted path
            // rather than a bare name.
            assertThat(answerTo("p: %\"\" d: %b p/:d")).isEqualTo("%/b");
        }

        @Test
        @DisplayName("a string selector goes in as its text")
        void aStringSelectorIsItsText() {
            // `if (ANY_STR(pvs->select))` takes the series and the index, so
            // a string, a file, a URL or a tag all contribute their own
            // characters.
            assertThat(answerTo("p: %a d: \"b\" p/:d")).isEqualTo("%a/b");
        }

        @Test
        @DisplayName("and anything else is molded into one")
        void anythingElseIsMolded() {
            // `Mold_Value(&mo, pvs->select, 0)`, thus a number joins as its
            // digits and a word as its spelling.
            assertThat(answerTo("p: %a d: 5 p/:d")).isEqualTo("%a/5");
            assertThat(answerTo("p: %a d: 'b p/:d")).isEqualTo("%a/b");
            assertThat(answerTo("p: %a d: 1.2.3 p/:d")).isEqualTo("%a/1.2.3");
        }

        @Test
        @DisplayName("a word written straight into the path is molded the same way")
        void aWrittenWordJoinsToo() {
            // Which is the whole difference from PD_String: `p/length` on a
            // string answers how long it is, and on a file it is a file named
            // length. A file has no path form that asks about the text.
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
            // Each read answers a file, so the next segment joins onto that.
            // This is the shape MAKE-DIR/DEEP walks.
            assertThat(answerTo("p: %a b: %b c: %c p/:b/:c")).isEqualTo("%a/b/c");
        }

        @Test
        @DisplayName("a URL joins the same way and stays a URL")
        void aUrlKeepsItsDatatype() {
            // `Set_Series(VAL_TYPE(pvs->value), ...)` -- the datatype comes
            // from the left, so the join never turns one into the other.
            assertThat(answerTo("p: http://example.com d: %b p/:d"))
                    .isEqualTo("http://example.com/b");
            assertThat(answerTo("p: %a d: %b type? p/:d")).isEqualTo("#(file!)");
        }

        @Test
        @DisplayName("and the join reads the file from where it stands, not from its head")
        void aSkippedFileJoinsFromWhereItIs() {
            // `Copy_Series_Value` copies from the index, so a file that has
            // been walked into joins from there.
            assertThat(answerTo("p: skip %ab/ 1 d: %c p/:d")).isEqualTo("%b/c");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class WhatItRefuses {

        @Test
        @DisplayName("writing through a file path, which is the first line of the handler")
        void aPathSetIsRefused() {
            // `if (pvs->setval) return PE_BAD_SET;` -- there is nothing to
            // write to, because the path names a new value rather than a place
            // inside the old one.
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
            // `path: either empty? path [dir][path/:dir]` in base-files.reb.
            // Without the join this raised on the first level and the borrowed
            // file's own function could not run at all. Tested here without
            // touching the disk: the same expression, with the file grant left
            // out of it.
            assertThat(answerTo(
                    "path: copy %\"\" foreach dir [%a %b %c] "
                    + "[path: either empty? path [dir][path/:dir] append path \"/\"] "
                    + "path")).isEqualTo("%a/b/c/");
        }
    }
}
