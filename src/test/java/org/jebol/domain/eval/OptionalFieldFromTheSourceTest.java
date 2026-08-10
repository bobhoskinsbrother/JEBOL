package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading a field an object may not have, from Rebol's own idiom for it:
 * {@code any [get in obj 'field  default]}.
 *
 * <p>IN answers none for a word the object does not hold, and GET of none
 * answers none. The two are written to work together, and both were confirmed
 * against the binary. JEBOL refused on each half, thus the idiom failed at the
 * first absent field.
 *
 * <p>Rebol's own MAKE-PORT* reads its awake handler this way:
 * {@code port/awake: any [get in port/spec 'awake :scheme/awake]}. The port
 * specification has three fields and awake is not one of them, so every call
 * to OPEN went through the absent case.
 */
class OptionalFieldFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Test
    @DisplayName("IN answers none for a word the object does not hold")
    void inAnswersNoneForAnAbsentWord() {
        assertThat(answerTo("o: make object! [a: 1] none? in o 'zz")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("IN still answers the word for one the object does hold")
    void inAnswersThePresentWord() {
        assertThat(answerTo("o: make object! [a: 1] word? in o 'a")).isEqualTo(TRUE);
        assertThat(answerTo("o: make object! [a: 1] get in o 'a")).isEqualTo("1");
    }

    @Test
    @DisplayName("GET of none answers none")
    void getOfNoneAnswersNone() {
        // GET's argument is untyped in Rebol -- `word {Word, path, object to
        // get}` -- so none reaches it and answers itself.
        assertThat(answerTo("none? get none")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("the two together are Rebol's way to read an optional field")
    void theIdiomWorks() {
        assertThat(answerTo("o: make object! [a: 1] any [get in o 'zz 9]")).isEqualTo("9");
        assertThat(answerTo("o: make object! [a: 1] any [get in o 'a 9]")).isEqualTo("1");
    }

    @Test
    @DisplayName("a field holding none falls through to the default, as ANY means it to")
    void aNoneFieldFallsThrough() {
        // Worth pinning because it looks like a bug and is not: ANY passes
        // over a field that is present and none, so the default wins. Rebol's
        // MAKE-PORT* depends on it, because port-spec-head sets every field to
        // none.
        assertThat(answerTo("o: make object! [a: none] any [get in o 'a 9]")).isEqualTo("9");
    }
}
