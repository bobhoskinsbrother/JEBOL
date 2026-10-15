package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCategoryFollowsTheCatalogueFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String typeAndIdOf(String raising) {
        return answerTo("e: try [" + raising + """
                ]
                either error? e [reduce [e/type e/id]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("past-end is filed under script, not syntax, however it is reached")
    void pastEndIsFiledUnderScript() {
        assertThat(typeAndIdOf("transcode/only #{}"))
                .isEqualTo("[Script past-end]");
        assertThat(typeAndIdOf("""
                transcode/only to binary! {}""")).isEqualTo("[Script past-end]");
    }

    @Test
    @DisplayName("an odd map literal is a script failure, because invalid-arg is one")
    void anOddMapLiteralIsAScriptFailure() {
        assertThat(typeAndIdOf("""
                load {#[a 1 b]}""")).isEqualTo("[Script invalid-arg]");
    }

    @Test
    @DisplayName("bytes that are not text, a spec that will not do and rubbish for a decoder are access failures")
    void theThreeThatReadAsScriptFailuresAreAccessOnes() {
        assertThat(typeAndIdOf("to string! #{FF}")).isEqualTo("[Access invalid-utf]");
        assertThat(typeAndIdOf("make port! 5")).isEqualTo("[Access invalid-spec]");
        assertThat(typeAndIdOf("decode 'qoi #{}")).isEqualTo("[Access bad-media]");
    }

    @Test
    @DisplayName("and something this machine has no call for is an internal failure")
    void somethingThisMachineHasNoCallForIsInternal() {
        assertThat(typeAndIdOf("""
                access-os 'uid""")).isEqualTo("[Internal not-here]");
    }

    @Test
    @DisplayName("the reader's own ids stay under syntax where the catalogue puts them")
    void theReadersOwnIdsStayUnderSyntax() {
        assertThat(typeAndIdOf("""
                load {[}""")).isEqualTo("[Syntax missing]");
        assertThat(typeAndIdOf("""
                load {#(nosuchtype! [])}""")).isEqualTo("[Syntax malconstruct]");
    }

    @Test
    @DisplayName("an error keeps whichever spelling of its type word it was given")
    void anErrorKeepsTheSpellingOfItsTypeWord() {
        assertThat(answerTo("""
                made: make error! [type: 'script id: 'no-value arg1: 'x]
                reduce [made/type]""")).isEqualTo("[script]");
        assertThat(answerTo("""
                made: make error! [type: 'Script id: 'no-value arg1: 'x]
                reduce [made/type]""")).isEqualTo("[Script]");
        assertThat(answerTo("""
                e: try [cause-error 'syntax 'bad-char {x}]
                reduce [e/type e/id]""")).isEqualTo("[syntax bad-char]");
    }

    @Test
    @DisplayName("but one it raised itself is spelled the way the catalogue spells it")
    void oneItRaisedItselfIsSpelledTheWayTheCatalogueDoes() {
        assertThat(typeAndIdOf("1 / 0")).isEqualTo("[Math zero-divide]");
        assertThat(typeAndIdOf("nosuchwordanywhere")).isEqualTo("[Script no-value]");
    }

    @Test
    @DisplayName("and the code still agrees with the type, whichever spelling that is")
    void theCodeStillAgreesWithTheType() {
        assertThat(answerTo("""
                made: make error! [type: 'math id: 'zero-divide]
                reduce [made/code made/type]""")).isEqualTo("[400 math]");
        assertThat(answerTo("""
                e: try [transcode/only #{}]
                reduce [e/code e/type]""")).isEqualTo("[327 Script]");
    }
}
