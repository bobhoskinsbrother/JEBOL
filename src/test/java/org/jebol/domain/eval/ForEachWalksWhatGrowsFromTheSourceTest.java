package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForEachWalksWhatGrowsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return withoutTheDelimitersAroundAText(
                interpreter.display(interpreter.run(source)));
    }

    private static String withoutTheDelimitersAroundAText(String shown) {
        return isWrappedIn(shown, '"', '"') || isWrappedIn(shown, '{', '}')
                ? shown.substring(1, shown.length() - 1)
                : shown;
    }

    private static boolean isWrappedIn(String shown, char opening, char closing) {
        return shown.length() >= 2
                && shown.charAt(0) == opening
                && shown.charAt(shown.length() - 1) == closing;
    }

    @Test
    @DisplayName("a key put into a map during the walk is walked")
    void akeyPutIntoAMapDuringTheWalk() {
        assertThat(answerTo("""
                m: map [a: 1 b: 2]
                total: 0
                foreach [key held] m [
                    if held = 1 [put m 'c 30]
                    total: total + held
                ]
                total"""))
                .as("the walk visited a and b only, so the key put in during it was missed")
                .isEqualTo("33");
    }

    @Test
    @DisplayName("and the value it was given is the one the walk sees")
    void thevalueItWasGiven() {
        assertThat(answerTo("""
                m: map [a: 1]
                seen: copy []
                foreach [key held] m [
                    if key = 'a [put m 'b 2]
                    append seen held
                ]
                mold seen"""))
                .isEqualTo("[1 2]");
    }

    @Test
    @DisplayName("a walk over one name sees the new key too")
    void awalkOverOneName() {
        assertThat(answerTo("""
                m: map [a: 1]
                seen: copy []
                foreach key m [
                    if key = 'a [put m 'b 2]
                    append seen key
                ]
                mold seen"""))
                .isEqualTo("[a b]");
    }

    @Test
    @DisplayName("a block the body appends to keeps the walk going")
    void ablockTheBodyAppendsTo() {
        assertThat(answerTo("""
                rounds: 0
                items: [1 2]
                foreach item items [
                    rounds: rounds + 1
                    if rounds > 6 [break]
                    append items 9
                ]
                rounds"""))
                .as("a copy taken up front would have ended the walk after two rounds")
                .isEqualTo("7");
    }

    @Test
    @DisplayName("a walk over a block nobody touches still ends on its own")
    void awalkOverAnUntouchedBlock() {
        assertThat(answerTo("""
                rounds: 0
                foreach item [1 2 3] [rounds: rounds + 1]
                rounds"""))
                .isEqualTo("3");
    }

    @Test
    @DisplayName("and a key removed during the walk shortens it")
    void akeyRemovedDuringTheWalk() {
        assertThat(answerTo("""
                m: map [a: 1 b: 2 c: 3]
                seen: copy []
                foreach [key held] m [
                    if key = 'a [remove/key m 'c]
                    append seen key
                ]
                mold seen"""))
                .isEqualTo("[a b]");
    }
}
