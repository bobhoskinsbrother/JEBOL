package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DELECT: the parser every REBOL dialect is read by.
 *
 * <p>{@code u-dialect.c}, 560 lines. DRAW, EFFECT, TEXT and REBCODE are all
 * read this way -- {@code system/dialects} holds one object per dialect and
 * each object's fields are its commands -- so this is the thing that has to
 * work before any of them can.
 *
 * <p>The idea is not how any other parser works and it is worth stating before
 * reading the tests. A command declares the <em>types</em> of its arguments
 * rather than their order, and each argument goes to whichever slot will take
 * it. So {@code cmd 3 a@b} comes back as {@code [cmd a@b 3]}: neither argument
 * moved to where it was written, both went to where they fit. That is what
 * lets a dialect read as a description instead of as a call.
 *
 * <p>Rebol's own {@code delect-test.r3} is the third authority and its five
 * assertions are the first five tests here. Everything else was run against a
 * real 3.22.1 before it was written down, which is how the truncation and the
 * lit-word rules got settled -- both of them the opposite of the obvious guess.
 *
 * <p>Specified in {@code spec/dialect.allium}.
 */
class DelectFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The dialect Rebol's own test uses: one command, a string and a number. */
    private static final String REBOLS_OWN_DIALECT = """
            d: context [default: [] cmd: [any-string! integer!]]
            out: make block! 4
            """;

    @Nested
    @DisplayName("what Rebol's own test asserts")
    class TheBorrowedAssertions {

        @Test
        @DisplayName("a string and a number, written in slot order")
        void inSlotOrder() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    delect d [cmd "a" 1] out
                    mold out""")).isEqualTo("{[cmd \"a\" 1]}");
        }

        @Test
        @DisplayName("and written the other way round, answered in slot order all the same")
        void outOfOrderComesBackInOrder() {
            // The whole point of a dialect, in one assertion. An email is an
            // any-string!, so it takes the first slot however late it was
            // written.
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    delect d [cmd 3 a@b] out
                    mold out""")).isEqualTo("\"[cmd a@b 3]\"");
        }

        @Test
        @DisplayName("a tag counts as a string, because any-string! is a typeset")
        void atagIsAString() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    delect d [cmd <a> 2] out
                    mold out""")).isEqualTo("\"[cmd <a> 2]\"");
        }

        @Test
        @DisplayName("a slot nobody filled is none, and the answer is still full length")
        void anUnfilledSlotIsNone() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    delect d [cmd http://] out
                    mold out""")).isEqualTo("\"[cmd http:// _]\"");
        }

        @Test
        @DisplayName("and the none can be the first slot as readily as the last")
        void theNoneCanComeFirst() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    delect d [cmd 5] out
                    mold out""")).isEqualTo("\"[cmd _ 5]\"");
        }
    }

    @Nested
    @DisplayName("how far one call reads")
    class TheWalk {

        @Test
        @DisplayName("it answers the input standing after the command it read")
        void itAnswersTheInputAdvanced() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    mold delect d [cmd "a" 1 cmd 5] out"""))
                    .isEqualTo("\"[cmd 5]\"");
        }

        @Test
        @DisplayName("which is what makes a walk a loop")
        void awalkIsALoop() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    seen: copy []
                    inp: [cmd "a" 1 cmd 5 cmd <t> 2]
                    while [inp: delect d inp out] [append/only seen copy out]
                    mold seen"""))
                    .isEqualTo("{[[cmd \"a\" 1] [cmd _ 5] [cmd <t> 2]]}");
        }

        @Test
        @DisplayName("and at the end of the block it answers none, so the loop stops")
        void attheEndItAnswersNone() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    none? delect d [] out""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a command runs until the next command, not until its slots are full")
        void acommandRunsUntilTheNextOne() {
            assertThat(answerTo("""
                    d: context [default: [] box: [pair! pair! decimal!] dot: [pair!]]
                    out: make block! 8
                    delect d [box 1x1 2x2 dot 3x3] out
                    mold out"""))
                    .as("BOX's third slot is never filled and DOT still starts on time")
                    .isEqualTo("\"[box 1x1 2x2 _]\"");
        }

        @Test
        @DisplayName("the output holds only the command just read, never the one before")
        void theOutputIsRebuilt() {
            assertThat(answerTo(REBOLS_OWN_DIALECT + """
                    inp: delect d [cmd "a" 1 cmd 5] out
                    delect d inp out
                    mold out""")).isEqualTo("\"[cmd _ 5]\"");
        }
    }

    @Nested
    @DisplayName("fitting a value to a slot")
    class TheNumbers {

        private static final String NUMBERS = """
                d: context [default: [] whole: [integer!] fraction: [decimal!]]
                out: make block! 4
                """;

        @Test
        @DisplayName("a fraction in a whole number's slot is cut down, not rounded")
        void afractionIsTruncated() {
            // `(REBI64)VAL_DECIMAL(value)`. Rounding is the reasonable guess
            // and it is wrong: 3.7 becomes 3.
            assertThat(answerTo(NUMBERS + """
                    delect d [whole 3.7] out
                    mold out""")).isEqualTo("\"[whole 3]\"");
        }

        @Test
        @DisplayName("and one just under a half is cut down the same way")
        void thecuttingIsNotRoundingEither() {
            assertThat(answerTo(NUMBERS + """
                    delect d [whole 3.2] out
                    mold out""")).isEqualTo("\"[whole 3]\"");
        }

        @Test
        @DisplayName("a whole number in a fraction's slot becomes a fraction")
        void awholeNumberBecomesAFraction() {
            assertThat(answerTo(NUMBERS + """
                    delect d [fraction 4] out
                    mold out""")).isEqualTo("\"[fraction 4.0]\"");
        }

        @Test
        @DisplayName("which is what lets somebody write line-width 2 rather than 2.0")
        void thatIsWhyADialectIsWritable() {
            assertThat(answerTo(NUMBERS + """
                    delect d [fraction 4] out
                    decimal? second out""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a slot that repeats")
    class TheRepeater {

        private static final String POINTS = """
                d: context [default: [] many: [* pair!]]
                out: make block! 8
                """;

        @Test
        @DisplayName("takes as many as are written")
        void ittakesAsManyAsAreWritten() {
            assertThat(answerTo(POINTS + """
                    delect d [many 1x1 2x2 3x3] out
                    mold out""")).isEqualTo("\"[many 1x1 2x2 3x3]\"");
        }

        @Test
        @DisplayName("takes one when one is written")
        void ittakesOne() {
            assertThat(answerTo(POINTS + """
                    delect d [many 1x1] out
                    mold out""")).isEqualTo("\"[many 1x1]\"");
        }

        @Test
        @DisplayName("and adds nothing when none is, which is where none padding stops")
        void itaddsNothingForNone() {
            assertThat(answerTo(POINTS + """
                    delect d [many] out
                    mold out""")).isEqualTo("\"[many]\"");
        }
    }

    @Nested
    @DisplayName("what a value is, before it is placed")
    class TheEvaluation {

        private static final String BOXES = """
                d: context [default: [] box: [pair! pair!]]
                out: make block! 8
                """;

        @Test
        @DisplayName("a word the dialect does not know stands for whatever it holds")
        void awordIsLookedUp() {
            assertThat(answerTo(BOXES + """
                    n: 9x9
                    delect d [box n 3x3] out
                    mold out""")).isEqualTo("\"[box 9x9 3x3]\"");
        }

        @Test
        @DisplayName("a paren is evaluated, so a dialect can carry a computed value")
        void aparenIsEvaluated() {
            assertThat(answerTo(BOXES + """
                    delect d [box (1x1 + 1x1) 3x3] out
                    mold out""")).isEqualTo("\"[box 2x2 3x3]\"");
        }

        @Test
        @DisplayName("but a word the dialect does know is never looked up")
        void adialectsOwnWordIsNotLookedUp() {
            // Or every option word would have to be a defined variable, and a
            // dialect could not use a word that happened to name something.
            assertThat(answerTo("""
                    d: context [default: [] spline: [* pair! word!] closed: none]
                    out: make block! 8
                    closed: 99
                    delect d [spline 1x1 2x2 closed] out
                    mold out""")).isEqualTo("\"[spline 1x1 2x2 closed]\"");
        }
    }

    @Nested
    @DisplayName("values written before any command")
    class TheDefault {

        @Test
        @DisplayName("reach the dialect's own DEFAULT, named like any other command")
        void theyReachTheDefault() {
            assertThat(answerTo("""
                    d: context [default: [pair!] box: [pair! pair!]]
                    out: make block! 8
                    delect d [1x1 box 2x2 3x3] out
                    mold out""")).isEqualTo("\"[default 1x1]\"");
        }

        @Test
        @DisplayName("and the command after them is read by the next call")
        void thecommandAfterIsNext() {
            assertThat(answerTo("""
                    d: context [default: [pair!] box: [pair! pair!]]
                    out: make block! 8
                    mold delect d [1x1 box 2x2 3x3] out"""))
                    .isEqualTo("\"[box 2x2 3x3]\"");
        }

        @Test
        @DisplayName("a word nothing will take is refused rather than skipped")
        void awordNothingTakesIsRefused() {
            // The decision worth defending. A dialect is something somebody
            // typed, and a word nobody serves is almost always a misspelling.
            // Skipping it draws a picture missing one shape, with nothing
            // anywhere saying which.
            assertThat(answerTo("""
                    d: context [default: [pair!] box: [pair! pair!]]
                    out: make block! 8
                    e: try [delect d [nosuch 1x1] out]
                    e/id""")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("a command written as a lit-word")
    class TheLitWord {

        @Test
        @DisplayName("answers as a lit-word, so the mark is not lost")
        void alitWordStaysALitWord() {
            assertThat(answerTo("""
                    d: context [default: [] box: [pair! pair!]]
                    out: make block! 8
                    delect d ['box 2x2 3x3] out
                    mold out""")).isEqualTo("\"['box 2x2 3x3]\"");
        }

        @Test
        @DisplayName("and its arguments are placed exactly as a plain command's are")
        void itsArgumentsArePlacedTheSame() {
            assertThat(answerTo("""
                    d: context [default: [] box: [pair! decimal!]]
                    out: make block! 8
                    delect d ['box 1.5 3x3] out
                    mold out""")).isEqualTo("\"['box 3x3 1.5]\"");
        }
    }

    @Nested
    @DisplayName("reading the whole block at once")
    class TheWholeBlock {

        @Test
        @DisplayName("gathers every command into one answer, in the order written")
        void itgathersEveryCommand() {
            assertThat(answerTo("""
                    d: context [default: [] box: [pair! pair!] flag: [logic!]]
                    o: make block! 20
                    delect/all d [box 2x2 3x3 flag #(true)] o
                    mold o""")).isEqualTo("\"[box 2x2 3x3 flag #(true)]\"");
        }

        @Test
        @DisplayName("including a default at the front")
        void adefaultAtTheFrontIsGatheredToo() {
            assertThat(answerTo("""
                    d: context [default: [pair!] box: [pair! pair!]]
                    p: make block! 20
                    delect/all d [1x1 box 2x2 3x3] p
                    mold p""")).isEqualTo("\"[default 1x1 box 2x2 3x3]\"");
        }

        @Test
        @DisplayName("which is what a renderer wants: one pass, no loop around the parser")
        void onePassIsWhatARendererWants() {
            assertThat(answerTo("""
                    d: context [default: [] pen: [tuple!] box: [pair! pair!]]
                    o: make block! 30
                    delect/all d [pen 255.0.0 box 0x0 10x10 box 20x20 30x30] o
                    length? o""")).isEqualTo("8");
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class TheRefusals {

        @Test
        @DisplayName("an output block that may not be changed")
        void aprotectedOutputIsRefused() {
            assertThat(answerTo("""
                    d: context [default: [] cmd: [integer!]]
                    out: protect make block! 4
                    e: try [delect d [cmd 1] out]
                    error? e""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a dialect that is not an object")
        void anondialectIsRefused() {
            assertThat(answerTo("""
                    out: make block! 4
                    error? try [delect [not an object] [cmd 1] out]"""))
                    .isEqualTo("#(true)");
        }
    }
}
