package org.jebol.application;

import org.jebol.domain.value.AnyStringValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFilesOnWindowsFromTheSourceTest {

    private static String textOf(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.succeeded()).as(source + " ran").isTrue();
        return ((AnyStringValue) outcome.value()).text();
    }

    private static String onWindows(String source) {
        return textOf(AnInterpreter.writingLocalFilesForWindows(), source);
    }

    private static String onPosix(String source) {
        return textOf(AnInterpreter.writingLocalFilesForPosix(), source);
    }

    private static String errorIdOnWindows(String source) {
        Interpreter interpreter = AnInterpreter.writingLocalFilesForWindows();
        String asking = "e: try [" + source + "] either error? e [form e/id] [{no-error}]";
        return textOf(interpreter, asking);
    }

    @Nested
    @DisplayName("TO-LOCAL-FILE on Windows")
    class ToLocalFile {

        @Test
        @DisplayName("the first segment of an absolute path is the drive")
        void writesTheFirstSegmentAsTheDrive() {
            assertThat(onWindows("to-local-file %/c/dir/file.txt")).isEqualTo("c:\\dir\\file.txt");
            assertThat(onWindows("to-local-file %/a/b")).isEqualTo("a:\\b");
            assertThat(onWindows("to-local-file %/C/")).isEqualTo("C:\\");
        }

        @Test
        @DisplayName("a drive alone is its root, with or without the slash")
        void writesADriveAloneAsItsRoot() {
            assertThat(onWindows("to-local-file %/c")).isEqualTo("c:\\");
            assertThat(onWindows("to-local-file %/c/")).isEqualTo("c:\\");
        }

        @Test
        @DisplayName("a first segment longer than a letter is a network share")
        void writesALongerFirstSegmentAsAShare() {
            assertThat(onWindows("to-local-file %/rodan/shareddocs")).isEqualTo("\\\\rodan\\shareddocs");
            assertThat(onWindows("to-local-file %/rodan/shareddocs/")).isEqualTo("\\\\rodan\\shareddocs\\");
        }

        @Test
        @DisplayName("the root alone is the empty string")
        void writesTheRootAloneAsNothing() {
            assertThat(onWindows("to-local-file %/")).isEmpty();
        }

        @Test
        @DisplayName("two slashes in front are one separator, not a drive")
        void writesTwoLeadingSlashesAsOneSeparator() {
            assertThat(onWindows("to-local-file %//cc")).isEqualTo("\\cc");
        }

        @Test
        @DisplayName("a relative path takes the backslash and no drive")
        void leavesARelativePathWithoutADrive() {
            assertThat(onWindows("to-local-file %a/b/")).isEqualTo("a\\b\\");
            assertThat(onWindows("to-local-file {a/b}")).isEqualTo("a\\b");
        }

        @Test
        @DisplayName("/FULL resolves the dots after the drive")
        void resolvesTheDotsAfterTheDrive() {
            assertThat(onWindows("to-local-file/full %/c/a/../b")).isEqualTo("c:\\b");
            assertThat(onWindows("to-local-file/full %/c/a/./b/.")).isEqualTo("c:\\a\\b\\");
        }

        @Test
        @DisplayName("and the same path away from Windows keeps its slashes and has no drive")
        void keepsSlashesAwayFromWindows() {
            assertThat(onPosix("to-local-file %/c/dir/file.txt")).isEqualTo("/c/dir/file.txt");
            assertThat(onPosix("to-local-file %/")).isEqualTo("/");
            assertThat(onPosix("to-local-file %/rodan/shareddocs")).isEqualTo("/rodan/shareddocs");
        }
    }

    @Nested
    @DisplayName("TO-REBOL-FILE on Windows")
    class ToRebolFile {

        @Test
        @DisplayName("a drive becomes the first segment")
        void writesTheDriveAsTheFirstSegment() {
            assertThat(onWindows("form to-rebol-file {C:\\temp\\x.txt}")).isEqualTo("/C/temp/x.txt");
            assertThat(onWindows("form to-rebol-file {c:/dir}")).isEqualTo("/c/dir");
        }

        @Test
        @DisplayName("a drive with no separator after it is still a drive")
        void readsADriveWithoutASeparator() {
            assertThat(onWindows("form to-rebol-file {C:x}")).isEqualTo("/C/x");
            assertThat(onWindows("form to-rebol-file {C:}")).isEqualTo("/C/");
        }

        @Test
        @DisplayName("a network share loses its doubled separator, as every run of them does")
        void readsAShareAsAnAbsolutePath() {
            assertThat(onWindows("form to-rebol-file {\\\\rodan\\shareddocs\\}")).isEqualTo("/rodan/shareddocs/");
        }

        @Test
        @DisplayName("a second colon, or a colon straight after a separator, is refused")
        void refusesAColonThatCannotBeADrive() {
            assertThat(errorIdOnWindows("to-rebol-file {a:0:0}")).isEqualTo("invalid-arg");
            assertThat(errorIdOnWindows("to-rebol-file {a\\:b}")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("away from Windows a colon is part of the name")
        void keepsTheColonAwayFromWindows() {
            assertThat(onPosix("form to-rebol-file {a:0:0}")).isEqualTo("a:0:0");
            assertThat(onPosix("form to-rebol-file {C:\\temp}")).isEqualTo("C:/temp");
        }

        @Test
        @DisplayName("and the two directions undo each other on Windows")
        void roundTrips() {
            assertThat(onWindows("form to-rebol-file to-local-file %/c/dir/file.txt")).isEqualTo("/c/dir/file.txt");
            assertThat(onWindows("to-local-file to-rebol-file {D:\\work\\notes}")).isEqualTo("D:\\work\\notes");
        }
    }
}
