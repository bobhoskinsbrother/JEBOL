package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ACharacterSpellsAWordOrNothingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return answerTo("e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id mold e/arg1]] [reduce ['ok mold :e]]""");
    }

    @Test
    @DisplayName("the characters that spell a word alone are letters and the scanner's own punctuation")
    void theCharactersThatSpellAWordAloneAreLettersAndPunctuation() {
        assertThat(answerTo("""
                accepted: copy {}
                repeat n 127 [
                    ch: to char! n
                    unless error? try [to word! ch] [append accepted ch]
                ]
                either accepted = {!%&*+-./<=>?ABCDEFGHIJKLMNOPQRSTUVWXYZ^^`abcdefghijklmnopqrstuvwxyz|~} [
                    'same
                ] [accepted]""")).isEqualTo("same");
    }

    @Test
    @DisplayName("and every character that spells none is refused as a syntax bad-char naming itself")
    void everyCharacterThatSpellsNoneIsRefusedAsBadChar() {
        assertThat(answerTo("""
                answered-otherwise: copy []
                repeat n 127 [
                    ch: to char! n
                    e: try [to word! ch]
                    if error? e [
                        unless all [e/type = 'Syntax  e/id = 'bad-char  e/arg1 = ch] [
                            append answered-otherwise n
                        ]
                    ]
                ]
                answered-otherwise""")).isEqualTo("[]");
    }

    @Test
    @DisplayName("the three the reader spends on something else are refused, not read as words")
    void theThreeTheReaderSpendsOnSomethingElseAreRefused() {
        assertThat(failureOf("""
                to word! #"'"
                """)).isEqualTo("""
                [Syntax bad-char {#"'"}]""");
        assertThat(failureOf("""
                to word! #":"
                """)).isEqualTo("""
                [Syntax bad-char {#":"}]""");
        assertThat(failureOf("""
                to word! #"\\"
                """)).isEqualTo("""
                [Syntax bad-char {#"\\"}]""");
    }

    @Test
    @DisplayName("a blank character is not stepped over the way a blank string is")
    void aBlankCharacterIsNotSteppedOverTheWayABlankStringIs() {
        assertThat(failureOf("""
                to word! #" "
                """)).isEqualTo("""
                [Syntax bad-char {#" "}]""");
        assertThat(failureOf("""
                to word! " "
                """)).startsWith("[Script too-short");
        assertThat(failureOf("""
                to word! #"^(00)"
                """)).isEqualTo("""
                [Syntax bad-char {#"^^@"}]""");
    }

    @Test
    @DisplayName("a character becoming an issue is scanned as a word, so a digit is refused")
    void aCharacterBecomingAnIssueIsScannedAsAWord() {
        assertThat(failureOf("""
                to issue! #"5"
                """)).isEqualTo("""
                [Syntax bad-char {#"5"}]""");
        assertThat(answerTo("""
                to issue! "5"
                """)).isEqualTo("#5");
        assertThat(answerTo("""
                to issue! #"a"
                """)).isEqualTo("#a");
    }

    @Test
    @DisplayName("every word datatype goes through the same door, MAKE as well as TO")
    void everyWordDatatypeGoesThroughTheSameDoor() {
        assertThat(answerTo("""
                reduce [
                    to set-word! #"a"  to lit-word! #"a"
                    to get-word! #"a"  to refinement! #"a"
                ]""")).isEqualTo("[a: 'a :a /a]");
        assertThat(failureOf("""
                to set-word! #"5"
                """)).isEqualTo("""
                [Syntax bad-char {#"5"}]""");
        assertThat(failureOf("""
                to refinement! #"5"
                """)).isEqualTo("""
                [Syntax bad-char {#"5"}]""");
        assertThat(failureOf("""
                make word! #"5"
                """)).isEqualTo("""
                [Syntax bad-char {#"5"}]""");
    }

    @Test
    @DisplayName("a character above ASCII is a word, because the scanner reads bytes not letters")
    void aCharacterAboveAsciiIsAWord() {
        assertThat(answerTo("""
                reduce [
                    word? to word! to char! 233
                    word? to word! to char! 128
                    word? to word! #"^(FFFF)"
                ]""")).isEqualTo("[#(true) #(true) #(true)]");
    }
}
