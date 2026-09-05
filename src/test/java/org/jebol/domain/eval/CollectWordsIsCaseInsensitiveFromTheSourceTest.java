package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * COLLECT-WORDS counts two spellings of one word once.
 *
 * <p>A REBOL word is case-insensitive: {@code Domain} and {@code domain} are
 * the same word and the same slot. COLLECT-WORDS deduplicated on the spelling,
 * so it answered both, and the first spelling seen is the one R3 keeps.
 *
 * <p>It surfaced through FUNCTION, which builds its {@code /local} list with
 * COLLECT-WORDS/DEEP/SET. {@code set-cookies} in the borrowed
 * {@code prot-http.reb} assigns {@code domain:} and {@code Domain:} in
 * different branches, so JEBOL gave it three locals R3 does not have --
 * {@code Domain}, {@code Path} and {@code Max-Age} beside the lowercase ones
 * already there.
 *
 * <p>Harmless in what the function computes, since both spellings reach the
 * same slot either way, and not harmless in what it says about itself: a
 * duplicated local is a word the caller is told exists twice.
 *
 * <p>Found by `scripts/runtime-parity.py`, and every expectation here read off
 * `./r3-head`.
 */
class CollectWordsIsCaseInsensitiveFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("two spellings of a set-word are collected once")
    void twoSpellingsAreCollectedOnce() {
        assertThat(answerTo("mold collect-words/set [a: 1 A: 2 b: 3]"))
                .isEqualTo("\"[a b]\"");
    }

    @Test
    @DisplayName("and the first spelling seen is the one kept")
    void thefirstSpellingIsKept() {
        assertThat(answerTo("mold collect-words/set [Alpha: 1 alpha: 2]"))
                .isEqualTo("\"[Alpha]\"");
    }

    @Test
    @DisplayName("the same holds without /set")
    void thesameHoldsWithoutSet() {
        assertThat(answerTo("mold collect-words [a A b]")).isEqualTo("\"[a b]\"");
    }

    @Test
    @DisplayName("so FUNCTION gives one local, not two")
    void functionGivesOneLocal() {
        assertThat(answerTo("mold words-of function [x][Foo: 1 foo: 2 bar: 3]"))
                .isEqualTo("\"[x /local Foo bar]\"");
    }

    @Test
    @DisplayName("and the borrowed SET-COOKIES has the locals R3 gives it")
    void setCookiesHasTheLocalsRebolGivesIt() {
        assertThat(answerTo("""
                (words-of :set-cookies) = [
                    host data /local timestamp Expires domain path max-age
                    attr c-name c-value set? dcooks
                ]"""))
                .as("this is the function the difference was found in; compared "
                        + "inside REBOL because a list this long molds with braces")
                .isEqualTo("#(true)");
    }
}
