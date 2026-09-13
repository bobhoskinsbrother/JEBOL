package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirstPlusMovesTheWordFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("it answers the first item and moves the word on")
    void itAnswersTheFirstItemAndMovesTheWordOn() {
        assertThat(answerTo("""
                walking: [1 2 3]
                reduce [first+ walking  walking]""")).isEqualTo("[1 [2 3]]");
    }

    @Test
    @DisplayName("and the series it names is left exactly as it was")
    void theSeriesItNamesIsLeftAsItWas() {
        assertThat(answerTo("""
                walking: [1 2 3]
                first+ walking
                head walking""")).isEqualTo("[1 2 3]");
    }

    @Test
    @DisplayName("so a block walked inside a function comes back to the caller whole")
    void aBlockWalkedInsideAFunctionComesBackWhole() {
        assertThat(answerTo("""
                theirs: [1 2 3]
                taking: func [given] [first+ given]
                reduce [taking theirs  theirs]""")).isEqualTo("[1 [1 2 3]]");
    }

    @Test
    @DisplayName("a string steps one character, however many bytes that is")
    void aStringStepsOneCharacter() {
        assertThat(answerTo("""
                letters: "abc"
                accented: {áb}
                reduce [
                    first+ letters  letters  head letters
                    first+ accented  accented  head accented
                ]"""))
                .isEqualTo("""
                        [#"a" "bc" "abc" #"á" "b" "áb"]""");
    }

    @Test
    @DisplayName("and a binary steps one byte")
    void aBinaryStepsOneByte() {
        assertThat(answerTo("""
                bytes: #{010203}
                reduce [first+ bytes  bytes]""")).isEqualTo("[1 #{0203}]");
    }

    @Test
    @DisplayName("walking with it reaches every item and stops")
    void walkingWithItReachesEveryItemAndStops() {
        assertThat(answerTo("""
                walking: [1 2 3]
                collect [loop 5 [keep/only first+ walking]]"""))
                .isEqualTo("[1 2 3 _ _]");
    }

    @Test
    @DisplayName("a word standing part way along moves on from there")
    void aWordStandingPartWayAlongMovesOnFromThere() {
        assertThat(answerTo("""
                walking: next [1 2 3]
                reduce [first+ walking  walking  head walking]"""))
                .isEqualTo("[2 [3] [1 2 3]]");
    }

    @Test
    @DisplayName("at the tail there is nothing to answer and nowhere to go")
    void atTheTailThereIsNothingToAnswerAndNowhereToGo() {
        assertThat(answerTo("""
                empty-one: []
                at-the-end: tail [1 2]
                reduce [
                    first+ empty-one  empty-one
                    first+ at-the-end  index? at-the-end
                ]""")).isEqualTo("[_ [] _ 3]");
    }

    @Test
    @DisplayName("a word holding something that is not a series is refused")
    void aWordHoldingSomethingThatIsNotASeriesIsRefused() {
        assertThat(answerTo("""
                collect [
                    counting: 5
                    coordinates: 1x1
                    nothing: none
                    foreach named [counting coordinates nothing neverdefinedhere] [
                        keep either error? e: try [do reduce ['first+ named]] [
                            e/id
                        ] ['accepted]
                    ]
                ]""")).isEqualTo("[invalid-arg invalid-arg invalid-arg invalid-arg]");
    }
}
