package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathConversionTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("SPLIT-PATH gives the directory and the name")
    void aPathSplitsAtItsLastSlash() {
        assertThat(answerTo("(split-path %a/b/c.txt) = [%a/b/ %c.txt]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a name with no slash sits in the current directory")
    void theDirectoryIsNeverEmpty() {
        assertThat(answerTo("(split-path %c.txt) = [%./ %c.txt]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path that names a directory keeps its slash on the name")
    void theNameKeepsItsSlash() {
        assertThat(answerTo("(split-path %a/b/) = [%a/ %b/]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a directory with no parent above it")
    void theDegenerateDirectory() {
        assertThat(answerTo("(split-path %b/) = [%./ %b/]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("SPLIT-PATH does not change the path it was given")
    void theAnswerIsACopy() {
        assertThat(answerTo("p: %a/b.txt split-path p p = %a/b.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TO-REBOL-FILE answers a file and TO-LOCAL-FILE answers a string")
    void theTwoAnswerDifferentTypes() {
        assertThat(answerTo("file? to-rebol-file \"a/b.txt\"")).isEqualTo("#(true)");
        assertThat(answerTo("string? to-local-file %a/b.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the two undo each other")
    void theRoundTripHolds() {
        assertThat(answerTo("(to-rebol-file to-local-file %a/b.txt) = %a/b.txt"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path that starts at the root keeps its first slash")
    void theRootIsKept() {
        assertThat(answerTo("(to-rebol-file \"/a/b.txt\") = %/a/b.txt")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a path of only slashes and dots is a directory with no name")
    void theWholePathCanBeTheDirectory() {
        assertThat(answerTo("(split-path %/) = [%/ %\"\"]")).isEqualTo("#(true)");
        assertThat(answerTo("(split-path %./) = [%./ %\"\"]")).isEqualTo("#(true)");
        assertThat(answerTo("(split-path %../) = [%../ %\"\"]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a bare name has no directory above it")
    void aBareNameSitsInThisDirectory() {
        assertThat(answerTo("(split-path %dir) = [%./ %dir]")).isEqualTo("#(true)");
        assertThat(answerTo("(split-path %dir/) = [%./ %dir/]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a backslash is a separator whatever machine is reading it")
    void abackslashIsASeparatorEverywhere() {
        assertThat(answerTo("(to-rebol-file {a\\b}) = %a/b")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {..\\a}) = %../a")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {\\a}) = %/a")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file %a\\b) = %a/b")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and a run of separators is one separator")
    void arunOfSeparatorsCollapses() {
        assertThat(answerTo("(to-rebol-file {\\\\rodan\\shareddocs}) = %/rodan/shareddocs"))
                .isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {\\\\rodan\\shareddocs\\}) = %/rodan/shareddocs/"))
                .isEqualTo("#(true)");

        assertThat(answerTo("(to-rebol-file {a//b}) = %a/b")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {a///b}) = %a/b")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {a/\\b}) = %a/b")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {a//}) = %a/")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {///}) = %/")).isEqualTo("#(true)");
        assertThat(answerTo("(to-rebol-file {\\\\\\a}) = %/a")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("nothing else in the path is touched, escaping included")
    void therestOfThePathSurvives() {
        assertThat(answerTo("all [file? f: to-rebol-file {} empty? f]")).isEqualTo("#(true)");
        assertThat(answerTo("mold to-rebol-file {a b}")).isEqualTo("\"%a%20b\"");
        assertThat(answerTo("mold to-rebol-file {a%b}")).isEqualTo("\"%a%25b\"");
        assertThat(answerTo("mold to-rebol-file {a:0:0}")).isEqualTo("\"%a%3A0%3A0\"");
        assertThat(answerTo("mold to-rebol-file {C:\\temp\\x.txt}"))
                .as("the drive letter's colon is not a separator away from Windows")
                .isEqualTo("\"%C%3A/temp/x.txt\"");
    }

    @Test
    @DisplayName("the other direction writes this machine's separator and undoes it")
    void thelocalDirectionRoundTrips() {
        assertThat(answerTo("to-local-file %a/b")).isEqualTo("\"a/b\"");
        assertThat(answerTo("to-local-file %/a/b")).isEqualTo("\"/a/b\"");
        assertThat(answerTo("to-local-file %a/b/")).isEqualTo("\"a/b/\"");
        assertThat(answerTo("to-local-file %a%3A0%3A0"))
                .as("the escaping is undone on the way out")
                .isEqualTo("\"a:0:0\"");
        assertThat(answerTo("to-local-file to-rebol-file {\\\\rodan\\shareddocs}"))
                .isEqualTo("\"/rodan/shareddocs\"");
    }
}
