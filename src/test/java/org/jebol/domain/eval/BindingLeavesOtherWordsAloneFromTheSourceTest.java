package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BindingLeavesOtherWordsAloneFromTheSourceTest {

    private static final String A_MODULE_SHADOWING_THE_LIBRARY = """
            sheltered: module [title: "Sheltered"] [
                log-debug: none
                only-here: "the module's own"
                export plain: function [] [mold type? :log-debug]
                export through-bind: function [target] [
                    do bind [mold type? :log-debug] target
                ]
                export through-with: function [target] [
                    with target [mold type? :log-debug]
                ]
                export nested-with: function [target] [
                    with target [do [mold type? :log-debug]]
                ]
                export the-targets-own: function [target] [with target [inside]]
                export both: function [target] [
                    with target [reduce [inside only-here]]
                ]
            ]
            small: make object! [inside: "the object's own"]
            """;

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = A_MODULE_SHADOWING_THE_LIBRARY + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    private static final String THE_MODULES_OWN = """
            "#(none!)\"""";

    @Test
    @DisplayName("the module's own word is what it sees before any binding")
    void theModulesOwnWordIsWhatItSeesBeforeAnyBinding() {
        assertThat(answerTo("sheltered/plain")).isEqualTo(THE_MODULES_OWN);
    }

    @Test
    @DisplayName("and a word the target does not hold keeps that binding through BIND")
    void aWordTheTargetDoesNotHoldKeepsThatBindingThroughBind() {
        assertThat(answerTo("sheltered/through-bind small")).isEqualTo(THE_MODULES_OWN);
    }

    @Test
    @DisplayName("and through WITH, which binds the same way before it evaluates")
    void aWordTheTargetDoesNotHoldKeepsThatBindingThroughWith() {
        assertThat(answerTo("sheltered/through-with small")).isEqualTo(THE_MODULES_OWN);
    }

    @Test
    @DisplayName("nested blocks are treated the same, because the walk is deep")
    void nestedBlocksAreTreatedTheSame() {
        assertThat(answerTo("sheltered/nested-with small")).isEqualTo(THE_MODULES_OWN);
    }

    @Test
    @DisplayName("a word the target does hold moves to it")
    void aWordTheTargetHoldsMovesToIt() {
        assertThat(answerTo("sheltered/the-targets-own small")).isEqualTo("""
                "the object's own\"""");
    }

    @Test
    @DisplayName("and both kinds of word work side by side in one block")
    void bothKindsOfWordWorkSideBySide() {
        assertThat(answerTo("sheltered/both small")).isEqualTo("""
                ["the object's own" "the module's own"]""");
    }

    @Test
    @DisplayName("a word nothing bound stays unbound, however reachable the name is")
    void aWordNothingBoundStaysUnbound() {
        assertThat(answerTo("""
                greeting: "hello"
                e: try [do bind load "greeting" small] e/id"""))
                .isEqualTo("not-in-context");
        assertThat(answerTo("""
                error? try [do bind load "neverdefinedanywhere" small]"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("WITH evaluates where the caller stands, not inside the target")
    void withEvaluatesWhereTheCallerStands() {
        assertThat(answerTo("""
                with small [fresh-word: 99]
                reduce [fresh-word  true? find words-of small 'fresh-word]"""))
                .isEqualTo("[99 #(false)]");
    }

    @Test
    @DisplayName("BIND answers the block it was given, and BIND/COPY a copy")
    void bindAnswersTheBlockAndBindCopyACopy() {
        assertThat(answerTo("""
                b: [inside]
                reduce [same? b bind b small  same? b bind/copy b small]"""))
                .isEqualTo("[#(true) #(false)]");
    }
}
