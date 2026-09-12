package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MakingAnIssueFromAStringFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorFrom(String source) {
        return answerTo("e: try [" + source
                + "] either error? e [reduce [e/id e/arg1]] ['no-error]");
    }

    @Test
    @DisplayName("a control character at the head is stepped over, like a blank")
    void aControlCharacterAtTheHeadIsSteppedOver() {
        assertThat(answerTo("""
                make issue! {^(01)a}""")).isEqualTo("#a");
        assertThat(answerTo("""
                make issue! { a}""")).isEqualTo("#a");
    }

    @Test
    @DisplayName("and a blank after the word is allowed")
    void aBlankAfterTheWordIsAllowed() {
        assertThat(answerTo("""
                make issue! {a }""")).isEqualTo("#a");
        assertThat(answerTo("""
                make issue! { a }""")).isEqualTo("#a");
    }

    @Test
    @DisplayName("nothing but blanks is too short")
    void nothingButBlanksIsTooShort() {
        assertThat(errorFrom("""
                make issue! {^(01)}""")).isEqualTo("[too-short _]");
        assertThat(errorFrom("""
                make issue! {     }""")).isEqualTo("[too-short _]");
        assertThat(errorFrom("""
                make issue! {}""")).isEqualTo("[too-short _]");
    }

    @Test
    @DisplayName("but anything that is not a blank after the word is invalid")
    void anythingThatIsNotABlankAfterTheWordIsInvalid() {
        assertThat(errorFrom("""
                make issue! {a^(01)}""")).isEqualTo("[invalid-chars _]");
        assertThat(errorFrom("""
                make issue! {a^(01)a}""")).isEqualTo("[invalid-chars _]");
        assertThat(errorFrom("""
                make issue! {a a}""")).isEqualTo("[invalid-chars _]");
        assertThat(errorFrom("""
                make issue! {a^-b}""")).isEqualTo("[invalid-chars _]");
    }

    @Test
    @DisplayName("and neither refusal hands the string back")
    void neitherRefusalHandsTheStringBack() {
        assertThat(answerTo("""
                e: try [make issue! {a a}] none? e/arg1""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                e: try [make issue! {}] none? e/arg1""")).isEqualTo("#(true)");
    }
}
