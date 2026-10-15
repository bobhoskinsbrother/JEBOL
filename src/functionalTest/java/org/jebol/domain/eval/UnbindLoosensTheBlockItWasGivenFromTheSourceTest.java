package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnbindLoosensTheBlockItWasGivenFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = "target: make object! [held: 99]\n" + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Test
    @DisplayName("UNBIND answers the block it was given, not a copy of it")
    void unbindAnswersTheBlockItWasGiven() {
        assertThat(answerTo("""
                subject: [held]
                same? subject unbind subject""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and so does UNBIND/DEEP")
    void unbindDeepAnswersTheBlockItWasGiven() {
        assertThat(answerTo("""
                subject: [held [held]]
                same? subject unbind/deep subject""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and the blocks inside keep the identity they had")
    void theBlocksInsideKeepTheIdentityTheyHad() {
        assertThat(answerTo("""
                subject: [held [held]]
                same? second subject second unbind/deep subject""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the words at the top level come loose, and the ones below stay bound")
    void theTopLevelComesLooseAndTheRestStaysBound() {
        assertThat(answerTo("""
                subject: bind copy/deep [held [held]] target
                unbind subject
                reduce [
                    none? context? first subject
                    same? target context? first second subject
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and with /DEEP the ones below come loose too")
    void withDeepTheWordsBelowComeLooseToo() {
        assertThat(answerTo("""
                subject: bind copy/deep [held [held]] target
                unbind/deep subject
                reduce [
                    none? context? first subject
                    none? context? first second subject
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("an empty block comes back as itself and stays empty")
    void anEmptyBlockComesBackAsItself() {
        assertThat(answerTo("""
                subject: []
                reduce [same? subject unbind subject  empty? subject]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("a single word comes back unbound")
    void aSingleWordComesBackUnbound() {
        assertThat(answerTo("""
                none? context? unbind first [held]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and anything that is neither a word nor a block is refused")
    void anythingElseIsRefused() {
        assertThat(answerTo("""
                collect [
                    foreach wrong [5 #{00} 1.5 "text" %file.txt] [
                        keep either error? e: try [unbind wrong] [e/id] ['accepted]
                    ]
                ]""")).isEqualTo(
                "[expect-arg expect-arg expect-arg expect-arg expect-arg]");
    }

    @Test
    @DisplayName("MODULE takes the EXPORT keyword out of the block it was handed")
    void moduleTakesTheExportKeywordOutOfTheBlockItWasHanded() {
        assertThat(answerTo("""
                subject: [export 'exported]
                module [] subject
                subject""")).isEqualTo("['exported]");
    }
}
