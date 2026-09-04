package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specified in {@code spec/natives.allium} and {@code spec/values.allium},
 * read from {@code t-char.c}, {@code n-control.c}, {@code t-function.c},
 * {@code s-unicode.c} and the error catalog, with the suite lines named
 * beside each group.
 */
class SmallSuiteBehavioursFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("a char answers width and size through a path")
    class CharPaths {

        @Test
        @DisplayName("a narrow char is one column, a wide one two")
        void widthCountsColumns() {
            assertThat(answerTo("""
                    s: {a⚡b} s/2/width""")).isEqualTo("2");
            assertThat(answerTo("""
                    s: {ab} s/1/width""")).isEqualTo("1");
        }

        @Test
        @DisplayName("size counts the UTF-8 bytes")
        void sizeCountsBytes() {
            assertThat(answerTo("""
                    s: {a⚡} reduce [s/1/size s/2/size]""")).isEqualTo("[1 3]");
        }

        @Test
        @DisplayName("a field a char has not got is a bad selection")
        void anUnknownFieldIsRefused() {
            assertThat(errorIdOf("""
                    s: {a} s/1/nonsense""")).isEqualTo("invalid-path");
        }
    }

    @Nested
    @DisplayName("SWITCH compares cases as values")
    class SwitchCases {

        @Test
        @DisplayName("a paren case matches an equal paren, unevaluated")
        void aParenCaseMatches() {
            assertThat(answerTo("""
                    switch first [(1 2 3)] [
                        (3 2 1) [{Earl}]
                        (1 2 3) [{Red}]
                    ]""")).isEqualTo("\"Red\"");
            assertThat(answerTo("""
                    switch first [(2)] [(1) [{Earl}] (2) [{Red}]]"""))
                    .isEqualTo("\"Red\"");
        }
    }

    @Nested
    @DisplayName("making an error names its mistake")
    class MakingErrors {

        @Test
        @DisplayName("a spec with neither type nor id is the internal invalid-error")
        void aSpecWithNeitherTypeNorId() {
            assertThat(answerTo("""
                    e: try [make error! []] reduce [e/type e/id]"""))
                    .isEqualTo("[Internal invalid-error]");
            assertThat(answerTo("""
                    e: try [make error! [code: 400]] e/id""")).isEqualTo("invalid-error");
        }

        @Test
        @DisplayName("a string over an existing error re-messages it")
        void aStringOverAnExistingError() {
            assertThat(answerTo("""
                    base: make error! {base}
                    e: make base {message}
                    reduce [e/type e/arg1]""")).isEqualTo("[User \"message\"]");
        }
    }

    @Test
    @DisplayName("READ on a missing file names the file in the error")
    void readOnAMissingFileNamesTheFile(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                org.jebol.application.Bounds.standard()
                        .granting(org.jebol.domain.host.HostService.FILES));
        interpreter.useFileSystem(
                org.jebol.application.FileSystemPort.rootedAt(directory));
        String source = """
                e: try [read %nonsense] reduce [e/id = 'cannot-open e/arg1 = %nonsense]""";
        interpreter.defineFreshWordsIn(source);
        assertThat(interpreter.display(interpreter.run(source)))
                .isEqualTo("[#(true) #(true)]");
    }

    @Nested
    @DisplayName("TYPES-OF a function")
    class TypesOfAFunction {

        @Test
        @DisplayName("answers one typeset per parameter")
        void oneTypesetPerParameter() {
            assertThat(answerTo("""
                    mold third types-of :insert"""))
                    .isEqualTo("\"make typeset! [none! logic!]\"");
        }

        @Test
        @DisplayName("an untyped parameter is the any-type set")
        void anUntypedParameterIsAnyType() {
            assertThat(answerTo("""
                    (first types-of func [a] [a]) = any-type!"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("the datatype catalog ends with end!")
    class TheDatatypeCatalog {

        @Test
        @DisplayName("end! is in the catalog and outside any-type!")
        void endIsInTheCatalogAndOutsideAnyType() {
            assertThat(answerTo("""
                    mold difference system/catalog/datatypes to-block any-type!"""))
                    .isEqualTo("\"[#(end!)]\"");
        }

        @Test
        @DisplayName("end! sits first, so the two lists end on the same entry")
        void theLastEntriesAgree() {
            assertThat(answerTo("""
                    (last to-block any-type!) == (last system/catalog/datatypes)"""))
                    .isEqualTo("#(true)");
            assertThat(answerTo("""
                    (first system/catalog/datatypes) = end!"""))
                    .isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("POWER refuses a tuple by id, so a script can catch it")
    void powerRefusesATupleById() {
        assertThat(answerTo("""
                e: try [1.2.3.4 ** 1]
                reduce [e/id = 'cannot-use  e/arg2 = tuple!]"""))
                .isEqualTo("[#(true) #(true)]");
    }
}
