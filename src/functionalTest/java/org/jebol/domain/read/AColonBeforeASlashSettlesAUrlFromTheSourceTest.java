package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AColonBeforeASlashSettlesAUrlFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the characters a word may not hold are ordinary inside a url")
    void readsTheCharactersAWordMayNotHoldAsOrdinaryInsideAUrl() {
        assertThat(answerTo("""
                collect [
                    foreach one ["a:/x<y" "a:/x>y" "a:/x,y" "a:/x%y" "a:/x$y"] [
                        keep either error? e: try [load one] [e/id] [e]
                    ]
                ]""")).isEqualTo("[a:/x<y a:/x>y a:/x,y a:/x%y a:/x$y]");
    }

    @Test
    @DisplayName("and every one of the first 256 characters survives the round trip")
    void roundTripsEveryOneOfTheFirstTwoHundredAndFiftySix() {
        assertThat(answerTo("""
                collect [
                    for i 0 255 1 [
                        text: append copy a:/ to char! i
                        unless (try [load mold text]) == text [keep i]
                    ]
                ]""")).isEqualTo("[]");
    }

    @Test
    @DisplayName("a file does too")
    void roundTripsThemInAFileToo() {
        assertThat(answerTo("""
                collect [
                    for i 0 255 1 [
                        text: append copy %a to char! i
                        unless (try [load mold text]) == text [keep i]
                    ]
                ]""")).isEqualTo("[]");
    }

    @Test
    @DisplayName("but a colon with no slash after it is still a set-word")
    void stillReadsAColonWithNoSlashAfterItAsASetWord() {
        assertThat(answerTo("""
                reduce [type? first load "[a: 1]"  type? first load "[a:/b]"]"""))
                .isEqualTo("[#(set-word!) #(url!)]");
    }
}
