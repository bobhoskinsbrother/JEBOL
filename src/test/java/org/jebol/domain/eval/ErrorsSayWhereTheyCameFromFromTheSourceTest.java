package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A raised error says what it was near and what it was raised through.
 *
 * <p>Both fields existed and both were none on every error the evaluator
 * raised: only the reader ever set NEAR, and only for a syntax error. A script
 * that caught one was told what went wrong and nothing about where.
 *
 * <p>Neither can be filled in where the failure happens -- a native raising
 * {@code zero-divide} has no idea what block it is in -- so they are attached
 * as the error passes back out through the evaluator, which is the one place
 * that has the frames. NEAR is the fragment from where the innermost call
 * began, and WHERE is the chain of names those calls were reached through,
 * innermost first.
 *
 * <p><b>WHERE is not R3's answer in full, and cannot be.</b> R3 builds it from
 * its own data stack, so the chain runs on down into the console's frames:
 * {@code [/ f try all print do either either if -apply-]} where this answers
 * {@code [/ f]}. The part that is about the script matches exactly; the tail is
 * the interpreter talking about itself, and JEBOL's interpreter is a different
 * one. Asserting the whole list would be asserting the shape of the C's
 * evaluator.
 *
 * <p>Every expectation here was read off `./r3-head` and only the matching part
 * is asserted.
 */
class ErrorsSayWhereTheyCameFromFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("NEAR is the fragment the failing call started at")
    void nearIsTheFragmentTheCallStartedAt() {
        assertThat(answerTo("mold all [error? e: try [1 / 0] e/near]"))
                .as("the block is [1 / 0] and the division starts at the slash")
                .isEqualTo("\"[/ 0]\"");
    }

    @Test
    @DisplayName("and it is the innermost block, not the one that called it")
    void nearIsTheInnermostBlock() {
        assertThat(answerTo("""
                mold all [f: does [1 / 0] error? e: try [f] e/near]"""))
                .as("R3 answers the same: the fragment inside F, not [f]")
                .isEqualTo("\"[/ 0]\"");
    }

    @Test
    @DisplayName("WHERE is a block, innermost first")
    void whereIsAblockInnermostFirst() {
        assertThat(answerTo("mold all [error? e: try [1 / 0] block? e/where]"))
                .isEqualTo("\"#(true)\"");
        assertThat(answerTo("mold all [error? e: try [1 / 0] first e/where]"))
                .as("the call that raised comes first, as in R3")
                .isEqualTo("\"/\"");
    }

    @Test
    @DisplayName("a function the failure happened inside is named after it")
    void thefunctionItHappenedInsideIsNamed() {
        assertThat(answerTo("""
                mold all [f: does [1 / 0] error? e: try [f] e/where]"""))
                .as("R3 answers [/ f try all print do either either if -apply-]; "
                        + "the tail is its own console's frames and is not "
                        + "reproducible, the head is about the script and matches")
                .isEqualTo("\"[/ f]\"");
    }

    @Test
    @DisplayName("an error nobody raised has neither, because nothing happened")
    void anerrorNobodyRaisedHasNeither() {
        assertThat(answerTo("""
                mold all [
                    e: make error! [type: 'Script id: 'invalid-arg]
                    none? e/near
                ]""")).isEqualTo("\"#(true)\"");
    }

    @Test
    @DisplayName("and a name a script reads is still what it was")
    void thefieldsAScriptReadsAreUnchanged() {
        assertThat(answerTo("mold all [error? e: try [1 / 0] e/id]"))
                .as("filling NEAR and WHERE must not disturb the id")
                .isEqualTo("\"zero-divide\"");
    }
}
