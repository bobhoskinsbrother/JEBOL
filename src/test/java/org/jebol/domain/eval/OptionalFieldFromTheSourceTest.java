package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
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
        assertThat(answerTo("none? get none")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("the two together are Rebol's way to read an optional field")
    void theIdiomWorks() {
        assertThat(answerTo("o: make object! [a: 1] any [get in o 'zz 9]")).isEqualTo("9");
        assertThat(answerTo("o: make object! [a: 1] any [get in o 'a 9]")).isEqualTo("1");
    }

    @Test
    @DisplayName("an error answers IN as an object does")
    void anErrorAnswersIn() {
        assertThat(answerTo("e: try [1 / 0] word? in e 'id")).isEqualTo(TRUE);
        assertThat(answerTo("e: try [1 / 0] none? in e 'zz")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("IN with a block searches it for the object that holds the word")
    void inSearchesABlock() {
        assertThat(answerTo("o: make object! [a: 1] "
                + "word? in reduce [1 o] 'a")).isEqualTo(TRUE);
        assertThat(answerTo("o: make object! [a: 1] "
                + "1 = get in reduce [1 o] 'a")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("a block with no object holding the word answers none")
    void aBlockWithNoSuchObjectAnswersNone() {
        assertThat(answerTo("none? in [1 2] 'a")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("the block form refuses a second argument that is not a word")
    void theBlockFormNeedsAWord() {
        assertThat(errorIdOf("in [1 2] 5")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("IN with a block SECOND binds that block and answers it")
    void inWithABlockBinds() {
        assertThat(answerTo("o: make object! [a: 1] mold in o [a]")).isEqualTo("\"[a]\"");
        assertThat(answerTo("o: make object! [a: 1] b: [a] in o b do b")).isEqualTo("1");
    }

    @Test
    @DisplayName("GET of anything that is not a word, a path or an object answers itself")
    void getOfAnythingElseAnswersItself() {
        assertThat(answerTo("get 5")).isEqualTo("5");
        assertThat(answerTo("get \"a\"")).isEqualTo("\"a\"");
        assertThat(answerTo("mold get [1 2]")).isEqualTo("\"[1 2]\"");
    }

    @Test
    @DisplayName("GET of a path evaluates the path")
    void getOfAPathEvaluatesIt() {
        assertThat(answerTo("o: make object! [a: 1] get 'o/a")).isEqualTo("1");
    }

    @Test
    @DisplayName("GET of an object answers a block of its values, without SELF")
    void getOfAnObjectAnswersItsValues() {
        assertThat(answerTo("o: make object! [a: 1 b: 2] mold get o")).isEqualTo("\"[1 2]\"");
        assertThat(answerTo("o: make object! [a: 1] block? get o")).isEqualTo(TRUE);
    }

    @Test
    @DisplayName("a field holding none falls through to the default, as ANY means it to")
    void aNoneFieldFallsThrough() {
        assertThat(answerTo("o: make object! [a: none] any [get in o 'a 9]")).isEqualTo("9");
    }
}
