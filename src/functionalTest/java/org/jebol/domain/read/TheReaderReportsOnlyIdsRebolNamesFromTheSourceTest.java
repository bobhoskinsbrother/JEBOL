package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheReaderReportsOnlyIdsRebolNamesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String loading) {
        return answerTo("e: try [load " + loading + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("a string that never closes is the generic syntax failure")
    void aStringThatNeverClosesIsInvalid() {
        assertThat(errorIdOf("""
                {"abc}""")).isEqualTo("[Syntax invalid]");
        assertThat(errorIdOf("""
                {^{abc}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("and so is a caret escape naming no character")
    void aCaretEscapeNamingNoCharacterIsInvalid() {
        assertThat(errorIdOf("""
                {"a^^(zz)b"}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("and so are digits past the range an integer holds")
    void digitsPastTheIntegerRangeAreInvalid() {
        assertThat(errorIdOf("""
                {99999999999999999999999}""")).isEqualTo("[Syntax invalid]");
        assertThat(errorIdOf("""
                {-99999999999999999999999}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("a file literal that never closes is invalid too, not a string failure")
    void aFileLiteralThatNeverClosesIsInvalid() {
        assertThat(errorIdOf("""
                {%"a}""")).isEqualTo("[Syntax invalid]");
        assertThat(errorIdOf("""
                {#"ab}""")).isEqualTo("[Syntax invalid]");
    }

    @Test
    @DisplayName("an unbalanced delimiter keeps its own id, which the catalogue does name")
    void anUnbalancedDelimiterKeepsItsOwnId() {
        assertThat(errorIdOf("{[}")).isEqualTo("[Syntax missing]");
        assertThat(errorIdOf("{]}")).isEqualTo("[Syntax missing]");
    }

    @Test
    @DisplayName("every id the reader can report is one Rebol's own catalogue names")
    void everyIdTheReaderReportsIsOneRebolNames() {
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
