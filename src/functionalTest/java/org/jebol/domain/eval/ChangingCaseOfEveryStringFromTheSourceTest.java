package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("/PART changes only that many, on any of them")
    void partChangesOnlyThatMany() {
        assertThat(answerTo("uppercase/part %abc.def 2")).isEqualTo("%ABc.def");
        assertThat(answerTo("uppercase/part next \"abcd\" 2")).isEqualTo("\"BCd\"");
    }

    @Test
    @DisplayName("and what is not a string is refused")
    void whatIsNotAStringIsRefused() {
        assertThat(errorIdOf("lowercase #{4142}")).isEqualTo("expect-arg");
        assertThat(errorIdOf("lowercase 5")).isEqualTo("expect-arg");
        assertThat(errorIdOf("lowercase [a]")).isEqualTo("expect-arg");
        assertThat(errorIdOf("lowercase none")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("the line the module loader stopped on")
    void theLineTheModuleLoaderStoppedOn() {
        assertThat(answerTo("""
                lowercase second split-path https://src.rebol.tech/Modules/Thru-Cache.reb"""))
                .isEqualTo("%thru-cache.reb");
    }
}
