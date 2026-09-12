package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UPPERCASE and LOWERCASE take every kind of string, not only a quoted one.
 *
 * <p>{@code string [any-string! char!]} is what the declaration says, and a
 * file, a url, a tag and an email are all any-string. Each comes back as
 * itself: {@code lowercase %Thru-Cache.reb} is {@code %thru-cache.reb}, still a
 * file.
 *
 * <p>Not a corner. Rebol's own module loader works out where to save a
 * downloaded extension with {@code lowercase second split-path source}, and
 * SPLIT-PATH of a url answers a file -- so IMPORT of any module named in
 * {@code system/modules} stopped there with "lowercase does not allow file!".
 *
 * <p>Every expectation was run against a real 3.22.5 first, including the four
 * it refuses.
 */
class ChangingCaseOfEveryStringFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a file changes case and stays a file")
    void aFileChangesCaseAndStaysAFile() {
        assertThat(answerTo("lowercase %Thru-Cache.reb")).isEqualTo("%thru-cache.reb");
        assertThat(answerTo("uppercase %thru-cache.reb")).isEqualTo("%THRU-CACHE.REB");
    }

    @Test
    @DisplayName("and so do a url, a tag and an email")
    void andSoDoAUrlATagAndAnEmail() {
        assertThat(answerTo("lowercase https://A.B/C")).isEqualTo("https://a.b/c");
        assertThat(answerTo("lowercase <A>")).isEqualTo("<a>");
        assertThat(answerTo("lowercase A@B.com")).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("a character does too, which it always did")
    void aCharacterDoesToo() {
        assertThat(answerTo("lowercase #\"A\"")).isEqualTo("#\"a\"");
        assertThat(answerTo("uppercase #\"a\"")).isEqualTo("#\"A\"");
    }

    /**
     * /PART counts from where the series stands, so the part that changes is
     * measured from the position and not from the head. The rest of the value
     * is left exactly as it was.
     */
    @Test
    @DisplayName("/PART changes only that many, on any of them")
    void partChangesOnlyThatMany() {
        assertThat(answerTo("uppercase/part %abc.def 2")).isEqualTo("%ABc.def");
        assertThat(answerTo("uppercase/part next \"abcd\" 2")).isEqualTo("\"BCd\"");
    }

    /**
     * A binary is not a string here, although it holds bytes that could be
     * letters -- the declaration says any-string and stops. So do a number, a
     * block and none.
     */
    @Test
    @DisplayName("and what is not a string is refused")
    void whatIsNotAStringIsRefused() {
        assertThat(errorIdOf("lowercase #{4142}")).isEqualTo("expect-arg");
        assertThat(errorIdOf("lowercase 5")).isEqualTo("expect-arg");
        assertThat(errorIdOf("lowercase [a]")).isEqualTo("expect-arg");
        assertThat(errorIdOf("lowercase none")).isEqualTo("expect-arg");
    }

    /**
     * The line that found this, written out. SPLIT-PATH of a url answers a
     * block of the base url and the file, and Rebol's module loader lowercases
     * the second of those to name what it saves.
     */
    @Test
    @DisplayName("the line the module loader stopped on")
    void theLineTheModuleLoaderStoppedOn() {
        assertThat(answerTo("""
                lowercase second split-path https://src.rebol.tech/Modules/Thru-Cache.reb"""))
                .isEqualTo("%thru-cache.reb");
    }
}
