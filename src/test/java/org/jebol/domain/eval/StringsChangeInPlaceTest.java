package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * UPPERCASE, LOWERCASE and TRIM change the string they were given.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Building a new string agrees on the answer and disagrees on
 * everything else: a caller holding the string never sees the change, and
 * a protected string is quietly rewritten rather than refusing. The
 * refusal lives in the storage, so a native that never touches the storage
 * never meets it.
 */
class StringsChangeInPlaceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("UPPERCASE changes the string the caller holds")
    void uppercaseChangesInPlace() {
        assertThat(answerTo("s: \"ab\" uppercase s s")).isEqualTo("\"AB\"");
    }

    @Test
    @DisplayName("LOWERCASE does too")
    void lowercaseChangesInPlace() {
        assertThat(answerTo("s: \"AB\" lowercase s s")).isEqualTo("\"ab\"");
    }

    @Test
    @DisplayName("TRIM does too")
    void trimChangesInPlace() {
        assertThat(answerTo("s: \" a \" trim s s")).isEqualTo("\"a\"");
    }

    @Test
    @DisplayName("each still answers the string as well")
    void eachAnswersTheStringToo() {
        assertThat(answerTo("uppercase \"ab\"")).isEqualTo("\"AB\"");
        assertThat(answerTo("lowercase \"AB\"")).isEqualTo("\"ab\"");
        assertThat(answerTo("trim \" a \"")).isEqualTo("\"a\"");
    }

    @Test
    @DisplayName("another name for the same string sees the change")
    void anAliasSeesTheChange() {
        assertThat(answerTo("s: \"ab\" t: s uppercase s t")).isEqualTo("\"AB\"");
    }

    @Test
    @DisplayName("a protected string refuses each of them")
    void aProtectedStringRefuses() {
        assertThat(errorIdOf("s: protect \"ab\" uppercase s")).isEqualTo("protected");
        assertThat(errorIdOf("s: protect \"AB\" lowercase s")).isEqualTo("protected");
        assertThat(errorIdOf("s: protect \" a \" trim s")).isEqualTo("protected");
    }

    @Test
    @DisplayName("DELINE and ENLINE change the string too")
    void theLineEndingPairChangesInPlace() {
        assertThat(answerTo("s: \"a^M^/b\" ignore: deline s s = \"a^/b\""))
                .isEqualTo("#(true)");
        assertThat(answerTo("s: \"a^/b\" ignore: enline s s = \"a^/b\""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a protected string refuses them as well")
    void theLineEndingPairRefusesAProtectedString() {
        assertThat(errorIdOf("s: protect \"a^M^/b\" deline s")).isEqualTo("protected");
        assertThat(errorIdOf("s: protect \"a^/b\" enline s")).isEqualTo("protected");
    }

    @Test
    @DisplayName("DELINE/LINES answers the lines rather than rewriting")
    void linesAnswersABlock() {
        assertThat(answerTo("(deline/lines \"a^/b^/c\") = [\"a\" \"b\" \"c\"]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty string is the degenerate case")
    void anEmptyStringIsUnchanged() {
        assertThat(answerTo("s: \"\" uppercase s s")).isEqualTo("\"\"");
        assertThat(answerTo("s: \"\" trim s s")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("TRIM/head and TRIM/tail each take one end")
    void trimTakesOneEndAtATime() {
        assertThat(answerTo("s: \" a \" trim/head s s")).isEqualTo("\"a \"");
        assertThat(answerTo("s: \" a \" trim/tail s s")).isEqualTo("\" a\"");
    }
}
