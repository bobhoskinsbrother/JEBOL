package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adding pairs to a map, reaching into one through a path, and asking an object
 * whether it has a field.
 *
 * <p>Read out of {@code t-map.c} ({@code PD_Map}, {@code Append_Map},
 * {@code Find_Entry}) and {@code t-object.c} (the arm FIND and SELECT share),
 * and every case checked against the R3 binary rather than against the port.
 *
 * <p>Four of these are easy to get defensibly wrong. APPEND on a map answers
 * the map and not a position. /PART counts pairs, so a /PART of one adds
 * nothing at all. FIND on an object answers TRUE where SELECT answers the
 * value. And SELF, the one name every object answers to, is the one name FIND
 * says it has not got.
 *
 * <p>Specified in {@code spec/natives.allium} under "Adding to a map, and
 * asking an object whether" and "Reaching into a map through a path".
 */
class MapAndObjectAccessFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("APPEND on a map")
    class AppendingPairs {

        @Test
        @DisplayName("takes key and value pairs")
        void itTakesPairs() {
            assertThat(answerTo(
                    "m: make map! [] append m [a 1 b 2] "
                    + "reduce [select m 'a select m 'b]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("answers the map, not a position after the insert")
        void itAnswersTheMap() {
            assertThat(answerTo("m: make map! [] map? append m [a 1]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("m: make map! [] same? m append m [a 1]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("only a block will do, because one value is not half a pair")
        void onlyABlockWillDo() {
            assertThat(errorIdFrom("append make map! [] 5")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("append make map! [] \"ab\"")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("append make map! [] none")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("append make map! [] 'a")).isEqualTo("invalid-arg");
            assertThat(errorIdFrom("append make map! [] quote (a 1)"))
                    .isEqualTo("invalid-arg");
            assertThat(errorIdFrom("append make map! [] make map! [a 1]"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("an empty block adds nothing and is not an error")
        void anEmptyBlockAddsNothing() {
            assertThat(answerTo("m: make map! [] append m [] length? m")).isEqualTo("0");
        }

        @Test
        @DisplayName("/DUP is refused rather than ignored")
        void dupIsRefused() {
            assertThat(errorIdFrom("append/dup make map! [] [a 1] 2"))
                    .isEqualTo("bad-refines");
        }

        @Test
        @DisplayName("/PART counts pairs, so an odd count adds one entry fewer")
        void partCountsPairs() {
            assertThat(answerTo("m: make map! [] append/part m [a 1 b 2] 0 length? m"))
                    .isEqualTo("0");
            assertThat(answerTo("m: make map! [] append/part m [a 1 b 2] 1 length? m"))
                    .isEqualTo("0");
            assertThat(answerTo("m: make map! [] append/part m [a 1 b 2] 2 length? m"))
                    .isEqualTo("1");
            assertThat(answerTo("m: make map! [] append/part m [a 1 b 2] 3 length? m"))
                    .isEqualTo("1");
            assertThat(answerTo("m: make map! [] append/part m [a 1 b 2] 4 length? m"))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("and takes the first pair, not the last, when asked for one")
        void partTakesFromTheFront()  {
            assertThat(answerTo(
                    "m: make map! [] append/part m [a 1 b 2] 2 "
                    + "reduce [select m 'a none? select m 'b]")).isEqualTo("[1 #(true)]");
        }

        @Test
        @DisplayName("a /PART past the end of the block stops at the end")
        void partPastTheEndStopsAtTheEnd() {
            assertThat(answerTo("m: make map! [] append/part m [a 1 b 2] 9 length? m"))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("a negative /PART adds nothing, and is not refused")
        void aNegativePartAddsNothing() {
            assertThat(answerTo("m: make map! [] append/part m [a 1 b 2] -1 length? m"))
                    .isEqualTo("0");
        }

        @Test
        @DisplayName("a /PART may be a position in the block rather than a count")
        void partMayBeAPosition() {
            assertThat(answerTo(
                    "b: [a 1 b 2] m: make map! [] append/part m b skip b 2 length? m"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("a trailing key with no value is left out")
        void aTrailingKeyIsLeftOut() {
            assertThat(answerTo(
                    "m: make map! [] append m [a 1 b] "
                    + "reduce [length? m select m 'a none? select m 'b]"))
                    .isEqualTo("[1 1 #(true)]");
        }

        @Test
        @DisplayName("a key repeated inside one block keeps the last value")
        void aRepeatedKeyKeepsTheLast() {
            assertThat(answerTo(
                    "m: make map! [] append m [a 1 a 2] "
                    + "reduce [length? m select m 'a]")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("a set-word key is stored under the plain word")
        void aSetWordKeyIsStoredPlain() {
            assertThat(answerTo(
                    "m: make map! [] append m [a: 1] "
                    + "reduce [select m 'a words-of m]")).isEqualTo("[1 [a]]");
        }

        @Test
        @DisplayName("a protected map is refused")
        void aProtectedMapIsRefused() {
            assertThat(errorIdFrom("append protect make map! [] [a 1]"))
                    .isEqualTo("protected");
        }

        @Test
        @DisplayName("and refused before the block is looked at")
        void protectionIsCheckedFirst() {
            assertThat(errorIdFrom("append protect make map! [] 5"))
                    .isEqualTo("protected");
        }

        @Test
        @DisplayName("INSERT does the same thing, a map having no front")
        void insertIsTheSame() {
            assertThat(answerTo(
                    "m: make map! [] insert m [a 1] "
                    + "reduce [length? m select m 'a same? m insert m [b 2]]"))
                    .isEqualTo("[1 1 #(true)]");
            assertThat(errorIdFrom("insert/dup make map! [] [a 1] 2"))
                    .isEqualTo("bad-refines");
            assertThat(errorIdFrom("insert make map! [] 5")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("FIND on a map answers the key it holds")
    class FindingAKey {

        @Test
        @DisplayName("the key, not true and not the value")
        void itAnswersTheKey() {
            assertThat(answerTo("find make map! [a 1] 'a")).isEqualTo("a:");
            assertThat(answerTo("select make map! [a 1] 'a")).isEqualTo("1");
        }

        @Test
        @DisplayName("and a word key is held as a set-word, so that is what comes back")
        void aWordKeyComesBackAsASetWord() {
            assertThat(answerTo("set-word? find make map! [a 1] 'a")).isEqualTo(TRUE);
            assertThat(answerTo("'a = to word! find make map! [a 1] 'a"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a key of another type comes back as it went in")
        void anotherTypeOfKeyIsUnchanged() {
            assertThat(answerTo("m: make map! [] m/(\"k\"): 1 find m \"k\""))
                    .isEqualTo("\"k\"");
            assertThat(answerTo("m: make map! [] m/(7): 1 find m 7")).isEqualTo("7");
        }

        @Test
        @DisplayName("a key it has not got answers none")
        void aMissingKeyAnswersNone() {
            assertThat(answerTo("none? find make map! [a 1] 'zz")).isEqualTo(TRUE);
            assertThat(answerTo("none? find make map! [] 'a")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("reaching into a map through a path")
    class ReachingThroughAPath {

        @Test
        @DisplayName("a path write adds a key that was not there")
        void aPathWriteAddsAKey() {
            assertThat(answerTo("m: make map! [] m/a: 1 select m 'a")).isEqualTo("1");
        }

        @Test
        @DisplayName("and replaces the value of one that was")
        void aPathWriteReplaces() {
            assertThat(answerTo("m: make map! [a: 1] m/a: 9 select m 'a")).isEqualTo("9");
        }

        @Test
        @DisplayName("the key may be a value of any type, not only a word")
        void anyValueIsAKey() {
            assertThat(answerTo("m: make map! [] m/(1): 'one select m 1"))
                    .isEqualTo("one");
            assertThat(answerTo("m: make map! [] m/(\"k\"): 5 select m \"k\""))
                    .isEqualTo("5");
            assertThat(answerTo("m: make map! [] m/(#\"x\"): 1 select m #\"x\""))
                    .isEqualTo("1");
            assertThat(answerTo("m: make map! [] m/([1 2]): 1 select m [1 2]"))
                    .isEqualTo("1");
        }

        @Test
        @DisplayName("reading a key it has not got answers none")
        void readingAMissingKeyAnswersNone() {
            assertThat(answerTo("m: make map! [a: 1] none? m/zz")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a key of none stores nothing at all, and says nothing")
        void aKeyOfNoneStoresNothing() {
            assertThat(answerTo("m: make map! [] m/(none): 1 length? m")).isEqualTo("0");
            assertThat(errorIdFrom("m: make map! [] m/(none): 1"))
                    .isEqualTo("no-error");
        }

        @Test
        @DisplayName("but a key set to none keeps its place, holding none")
        void aValueOfNoneKeepsItsPlace() {
            assertThat(answerTo("m: make map! [a 1] m/a: none "
                    + "reduce [length? m none? select m 'a]"))
                    .isEqualTo("[1 #(true)]");
        }

        @Test
        @DisplayName("a protected map is refused before the key is looked at")
        void aProtectedMapIsRefused() {
            assertThat(errorIdFrom("m: protect make map! [a: 1] m/a: 9"))
                    .isEqualTo("protected");
        }

        @Test
        @DisplayName("the map itself changes, so another name for it sees the write")
        void theMapItselfChanges() {
            assertThat(answerTo("m: make map! [] n: m m/a: 1 select n 'a"))
                    .isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("FIND on an object answers whether, where SELECT answers what")
    class AskingAnObject {

        @Test
        @DisplayName("FIND answers true for a field it has")
        void findAnswersTrue() {
            assertThat(answerTo("find make object! [a: 1] 'a")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and none for one it has not")
        void findAnswersNone() {
            assertThat(answerTo("none? find make object! [a: 1] 'zz")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a field holding none still answers true, because the field is there")
        void aFieldHoldingNoneIsStillAField() {
            assertThat(answerTo("find make object! [a: none] 'a")).isEqualTo(TRUE);
            assertThat(answerTo("none? select make object! [a: none] 'a"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("SELECT answers the value, which is the other question")
        void selectAnswersTheValue() {
            assertThat(answerTo("select make object! [a: 7] 'a")).isEqualTo("7");
        }

        @Test
        @DisplayName("only a word asks the question, and anything else finds nothing")
        void onlyAWordAsks() {
            assertThat(answerTo("none? find make object! [a: 1] 5")).isEqualTo(TRUE);
            assertThat(answerTo("none? find make object! [a: 1] \"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("none? find make object! [a: 1] none")).isEqualTo(TRUE);
            assertThat(answerTo("none? find make object! [a: 1] [a]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a word of another kind spelled the same finds nothing either")
        void aWordOfAnotherKindFindsNothing() {
            assertThat(answerTo("none? find make object! [a: 1] first [a:]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? find make object! [a: 1] first [:a]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? find make object! [a: 1] to lit-word! 'a"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? find make object! [a: 1] to refinement! 'a"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an error answers the same way, being an object underneath")
        void anErrorAnswersTheSameWay() {
            assertThat(answerTo("e: try [1 / 0] find e 'id")).isEqualTo(TRUE);
            assertThat(answerTo("e: try [1 / 0] none? find e 'invented"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("SELF is not found, although the object answers to it")
        void selfIsNotFound() {
            assertThat(answerTo("none? find make object! [a: 1] 'self")).isEqualTo(TRUE);
            assertThat(answerTo("none? select make object! [a: 1] 'self"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("object? get in make object! [a: 1] 'self"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a hidden field is not found, because it is not there to outside eyes")
        void aHiddenFieldIsNotFound() {
            assertThat(answerTo(
                    "o: make object! [a: 1 b: 2] protect/hide in o 'b "
                    + "reduce [true? find o 'a  none? find o 'b  none? select o 'b]"))
                    .isEqualTo("[#(true) #(true) #(true)]");
        }
    }
}
