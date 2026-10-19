package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheReaderReportsOnlyIdsRebolNamesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdOf(String loading) {
        return answerTo("e: try [load " + loading + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("a string that never closes is the generic syntax failure")
    void refusesAStringThatNeverClosesAsInvalid() {
        assertThat(readTheErrorIdOf("""
                {"abc}""")).isEqualTo("[Syntax invalid]");
        assertThat(readTheErrorIdOf("""
                {^{abc}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("and so is a caret escape naming no character")
    void refusesACaretEscapeNamingNoCharacterAsInvalid() {
        assertThat(readTheErrorIdOf("""
                {"a^^(zz)b"}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("and so are digits past the range an integer holds")
    void refusesDigitsPastTheIntegerRangeAsInvalid() {
        assertThat(readTheErrorIdOf("""
                {99999999999999999999999}""")).isEqualTo("[Syntax invalid]");
        assertThat(readTheErrorIdOf("""
                {-99999999999999999999999}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("a file literal that never closes is invalid too, not a string failure")
    void refusesAFileLiteralThatNeverClosesAsInvalid() {
        assertThat(readTheErrorIdOf("""
                {%"a}""")).isEqualTo("[Syntax invalid]");
        assertThat(readTheErrorIdOf("""
                {#"ab}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("an unbalanced delimiter keeps its own id, which the catalogue does name")
    void keepsItsOwnIdForAnUnbalancedDelimiter() {
        assertThat(readTheErrorIdOf("{[}")).isEqualTo("[Syntax missing]");
        assertThat(readTheErrorIdOf("{]}")).isEqualTo("[Syntax missing]");
    }

    @Test
    @DisplayName("every id the reader can report is one Rebol's own catalogue names")
    void reportsOnlyIdsRebolNames() {
        assertThat(answerTo("""
                sources: [
                    {"abc}  {^{abc}  {"a^^(zz)b"}  {99999999999999999999999}
                    {%"a}  {#"ab}  {[}  {]}  {(]}  {#(nosuchtype! [])}
                    {#[a 1 b]}  {2#{01}}  {a^^(00)}
                ]
                unknown: copy []
                foreach one sources [
                    e: try [load one]
                    if error? e [
                        unless any [
                            not none? select system/catalog/errors/syntax e/id
                            not none? select system/catalog/errors/script e/id
                            not none? select system/catalog/errors/internal e/id
                        ] [append unknown e/id]
                    ]
                ]
                unknown""")).isEqualTo("[]");
    }
}
