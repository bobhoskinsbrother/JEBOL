package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HowFarBindingReachesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = "target: make object! [held: 99]\n" + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Nested
    @DisplayName("how far the binding itself goes")
    class TheWalk {

        @Test
        @DisplayName("/only places the words the block holds at its top level")
        void onlyPlacesTheWordsAtTheTopLevel() {
            assertThat(answerTo("""
                    subject: [held]
                    bind/only subject target
                    same? target context? first subject""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and leaves a word one block down exactly as it was")
        void aWordOneBlockDownIsLeftAlone() {
            assertThat(answerTo("""
                    subject: [held [held]]
                    bind/only subject target
                    same? target context? first second subject""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and one two blocks down")
        void aWordTwoBlocksDownIsLeftAlone() {
            assertThat(answerTo("""
                    subject: [held [[held]]]
                    bind/only subject target
                    same? target context? first first second subject"""))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and one inside a paren, which the deep walk would have reached")
        void aWordInsideAParenIsLeftAlone() {
            assertThat(answerTo("""
                    subject: [held (held)]
                    bind/only subject target
                    same? target context? first second subject""")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("without /only the same word one block down does move")
        void withoutOnlyTheWalkGoesAllTheWayDown() {
            assertThat(answerTo("""
                    subject: [held [held]]
                    bind subject target
                    same? target context? first second subject""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("/only answers the block it was given, as BIND does")
        void onlyAnswersTheBlockItWasGiven() {
            assertThat(answerTo("""
                    subject: [held]
                    same? subject bind/only subject target""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an empty block binds without complaint")
        void anEmptyBlockBindsWithoutComplaint() {
            assertThat(answerTo("""
                    subject: []
                    reduce [same? subject bind/only subject target  empty? subject]"""))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("how far the collecting goes")
    class TheCollecting {

        @Test
        @DisplayName("/only/set takes the set-words written at the top level")
        void onlySetTakesTheSetWordsAtTheTopLevel() {
            assertThat(answerTo("""
                    bind/only/set [fresh: 1] target
                    words-of target""")).isEqualTo("[held fresh]");
        }

        @Test
        @DisplayName("and leaves a set-word one block down uncollected")
        void aSetWordOneBlockDownIsNotCollected() {
            assertThat(answerTo("""
                    bind/only/set [fresh: 1 attempt [buried: 2]] target
                    words-of target""")).isEqualTo("[held fresh]");
        }

        @Test
        @DisplayName("without /only the buried set-word is collected too")
        void withoutOnlyTheBuriedSetWordIsCollected() {
            assertThat(answerTo("""
                    bind/set [fresh: 1 attempt [buried: 2]] target
                    words-of target""")).isEqualTo("[held fresh buried]");
        }

        @Test
        @DisplayName("/only/new takes plain words at the top level and no deeper")
        void onlyNewTakesPlainWordsAtTheTopLevelAndNoDeeper() {
            assertThat(answerTo("""
                    bind/only/new [fresh [buried]] target
                    words-of target""")).isEqualTo("[held fresh]");
        }

        @Test
        @DisplayName("without /only /new reaches the buried word")
        void withoutOnlyNewReachesTheBuriedWord() {
            assertThat(answerTo("""
                    bind/new [fresh [buried]] target
                    words-of target""")).isEqualTo("[held fresh buried]");
        }

        @Test
        @DisplayName("/set passes over plain words however far down they are")
        void setPassesOverPlainWords() {
            assertThat(answerTo("""
                    bind/set [plain [alsoplain]] target
                    words-of target""")).isEqualTo("[held]");
        }

        @Test
        @DisplayName("a word the target already holds is not added a second time")
        void aWordTheTargetAlreadyHoldsIsNotAddedTwice() {
            assertThat(answerTo("""
                    bind/only/set [held: 1] target
                    reduce [words-of target  target/held]""")).isEqualTo("[[held] 99]");
        }
    }
}
