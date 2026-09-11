package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding a block moves the words the target holds and leaves the rest alone.
 *
 * <p>{@code Bind_Block(frame, BLK_HEAD(body), BIND_DEEP)} walks the block
 * looking each word up in one frame. A word it does not find keeps whatever
 * binding it was written with -- the C has nowhere else to look, because an
 * object's frame has no parent to walk.
 *
 * <p>Which is what makes binding composable. A block written inside a module
 * and then bound into a small object goes on reading the module's words; only
 * the object's own fields move. Rebinding the rest as well is the difference
 * between "read these fields here" and "re-resolve this code somewhere else",
 * and the second is never what a caller asked for.
 *
 * <p>The damage is invisible until a name exists in two places, which is why
 * it survived so long here. Rebol's own TLS opens with {@code log-error:
 * log-info: log-more: log-debug: log-----: none} so that its debug lines cost
 * nothing, and every one of those lines sits inside {@code with ctx [...]}.
 * Binding the whole block found the library's LOG-DEBUG instead, which takes a
 * lit-word first, so {@code read https://} stopped on a log line rather than
 * on anything to do with TLS.
 *
 * <p>The blocks below are written <em>inside</em> the module on purpose. A
 * block written at the top level and passed in as an argument carries the
 * library's binding already, so both behaviours agree on it and it proves
 * nothing. Every expectation was run against a real 3.22.5 first.
 */
class BindingLeavesOtherWordsAloneFromTheSourceTest {

    /**
     * A module with a word of its own that the library also has, which is the
     * only arrangement in which the two behaviours differ.
     */
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

    /**
     * The object does not hold LOG-DEBUG. An ancestor of it does -- everything
     * here hangs beneath the library -- and that is exactly the reach the C
     * does not have.
     */
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

    /**
     * A word with no binding stays unbound, so evaluating it says it is in no
     * context. The target's ancestors are not searched for it either -- the
     * rule is about the target alone, not about which words already had a
     * home.
     */
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
    @DisplayName("BIND answers the block it was given, and BIND/COPY a copy")
    void bindAnswersTheBlockAndBindCopyACopy() {
        assertThat(answerTo("""
                b: [inside]
                reduce [same? b bind b small  same? b bind/copy b small]"""))
                .isEqualTo("[#(true) #(false)]");
    }
}
