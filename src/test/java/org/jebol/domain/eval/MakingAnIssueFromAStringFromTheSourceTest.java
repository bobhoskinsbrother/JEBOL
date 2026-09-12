package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which of two refusals a string gets when it will not make an issue.
 *
 * <p>{@code Qualify_String} in {@code s-ops.c} does it in three steps: skip
 * leading space, copy characters up to the next space, then require everything
 * after that run to be space as well. Nothing copied is {@code too-short};
 * anything but a blank afterwards is {@code invalid-chars}.
 *
 * <p><b>A control character counts as space for the skipping and not for the
 * checking</b>, and that asymmetry is the whole of what was wrong here. The
 * lexer's default class says so in its own comment -- {@code LEX_DEFAULT
 * (LEX_DELIMIT|LEX_DELIMIT_SPACE) /* control chars = spaces *}{@code /} -- so a
 * control character at the head is stepped over like a blank. The trailing
 * check uses {@code IS_SPACE}, which only a real blank passes, so the same
 * character after the word is not.
 *
 * <p>Which means the same two characters in the other order give opposite
 * answers, and that is what the suite asserts twice.
 *
 * <p>Every expectation was read off {@code ./r3-head} first, including the
 * empty argument both refusals carry.
 */
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

    /**
     * Nothing left once the leading blanks are stepped over. A control
     * character on its own is the case the suite asserts, and it is the same
     * case as a string of spaces because the skipping does not tell them apart.
     */
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

    /**
     * And the same character after the word is not a blank, because the
     * trailing check is the stricter of the two.
     */
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

    /**
     * Neither refusal carries the string back. {@code Trap0} takes no argument,
     * so a caller reading {@code e/arg1} gets none -- and one that was handed
     * the string would print a control character into whatever it logged with.
     */
    @Test
    @DisplayName("and neither refusal hands the string back")
    void neitherRefusalHandsTheStringBack() {
        assertThat(answerTo("""
                e: try [make issue! {a a}] none? e/arg1""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                e: try [make issue! {}] none? e/arg1""")).isEqualTo("#(true)");
    }
}
