package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class VectorSeriesActionsFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String whatHappensTo(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] ['accepted]");
    }

    private static String holds(String expression, String expected) {
        return answerTo("(" + expression + ") == " + expected);
    }

    @Nested
    @DisplayName("moving about, which the generic series action already knows")
    class Moving {

        @Test
        @DisplayName("NEXT, BACK, SKIP and AT all keep the same storage")
        void movingKeepsTheStorage() {
            assertThat(answerTo("v: #(i32! [10 20 30]) same? v next v")).isEqualTo("#(false)");
            assertThat(answerTo("v: #(i32! [10 20 30]) first next v")).isEqualTo("20");
            assertThat(answerTo("v: #(i32! [10 20 30]) first back tail v")).isEqualTo("30");
            assertThat(answerTo("v: #(i32! [10 20 30]) first at v 3")).isEqualTo("30");
            assertThat(answerTo("v: #(i32! [10 20 30]) index? skip v 2")).isEqualTo("3");
        }

        @Test
        @DisplayName("and skipping back from the head stays at the head")
        void skippingBackFromTheHeadStays() {
            assertThat(answerTo("v: #(i32! [10 20 30]) index? skip v -1")).isEqualTo("1");
        }
    }

    @Nested
    @DisplayName("COPY")
    class Copying {

        @Test
        @DisplayName("copies the numbers rather than sharing them")
        void copyingIsNotSharing() {
            assertThat(answerTo("v: #(u16! [1 2]) not same? v copy v")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /PART takes only the first few, from the position")
        void copyPart() {
            assertThat(answerTo("length? copy/part #(u16! [1 2 3 4]) 2")).isEqualTo("2");
            assertThat(answerTo("to-binary copy/part #(u16! [1 2 3 4]) 2"))
                    .isEqualTo("#{01000200}");
            assertThat(answerTo("to-binary copy/part skip #(u16! [1 2 3 4]) 2 2"))
                    .isEqualTo("#{03000400}");
        }
    }

    @Nested
    @DisplayName("CLEAR drops everything from the position on")
    class Clearing {

        @Test
        @DisplayName("from the head, which empties it")
        void clearingFromTheHead() {
            assertThat(holds("clear #(i8! [1 2])", "#(i8! [])")).isEqualTo(TRUE);
            assertThat(answerTo("v: #(i8! [1 2]) clear v empty? v")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and from further in, which keeps what is behind")
        void clearingFromFurtherIn() {
            assertThat(holds("head clear next #(i8! [1 2 3])", "#(i8! [1])"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("REVERSE")
    class Reversing {

        @ParameterizedTest
        @ValueSource(strings = {"u8!", "u16!", "u32!", "u64!", "i8!", "i16!", "i32!", "i64!"})
        @DisplayName("turns the numbers round, at every width")
        void reversingCounts(String kind) {
            assertThat(holds("reverse #(" + kind + " [1 2 3])", "#(" + kind + " [3 2 1])"))
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"f32!", "f64!"})
        @DisplayName("and the same for the two that measure")
        void reversingMeasures(String kind) {
            assertThat(holds("reverse #(" + kind + " [1 2 3])",
                    "#(" + kind + " [3.0 2.0 1.0])")).isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"u8!", "i16!", "i64!", "f64!"})
        @DisplayName("/PART turns only the front round")
        void reversingPart(String kind) {
            assertThat(answerTo("(reverse/part #(" + kind + " [1 2 3]) 2) == "
                    + "#(" + kind + " [2 1 3])")).isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"u8!", "i16!", "i64!", "f64!"})
        @DisplayName("and from a position it leaves what is behind alone")
        void reversingFromAPosition(String kind) {
            assertThat(answerTo("(head reverse next #(" + kind + " [1 2 3])) == "
                    + "#(" + kind + " [1 3 2])")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("SORT")
    class Sorting {

        @ParameterizedTest
        @ValueSource(strings = {"i8!", "i16!", "i32!", "i64!", "f32!", "f64!"})
        @DisplayName("puts the numbers in order")
        void sorting(String kind) {
            assertThat(holds("sort #(" + kind + " [2 4 1 3])", "#(" + kind + " [1 2 3 4])"))
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"i8!", "i64!", "f32!"})
        @DisplayName("/REVERSE puts them in the other order")
        void sortingBackwards(String kind) {
            assertThat(holds("sort/reverse #(" + kind + " [2 4 1 3])",
                    "#(" + kind + " [4 3 2 1])")).isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"i8!", "i64!", "f32!"})
        @DisplayName("/PART sorts only the front and leaves the rest where it was")
        void sortingPart(String kind) {
            assertThat(holds("sort/part #(" + kind + " [2 4 1 3]) 3",
                    "#(" + kind + " [1 2 4 3])")).isEqualTo(TRUE);
            assertThat(holds("sort/part/reverse #(" + kind + " [2 4 1 3]) 3",
                    "#(" + kind + " [4 2 1 3])")).isEqualTo(TRUE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"i8!", "i64!", "f32!"})
        @DisplayName("and from a position it sorts only what is in front of it")
        void sortingFromAPosition(String kind) {
            assertThat(holds("head sort next #(" + kind + " [2 4 1 3])",
                    "#(" + kind + " [2 1 3 4])")).isEqualTo(TRUE);
            assertThat(holds("head sort/part next #(" + kind + " [2 4 1 3]) 2",
                    "#(" + kind + " [2 1 4 3])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but /SKIP and /COMPARE are not available on a vector")
        void therefinementsAvectorHasNot() {
            assertThat(whatHappensTo("sort/skip #(i8! [2 4 1 3]) 2"))
                    .as("the C names both in one Trap0(RE_FEATURE_NA) rather than "
                            + "sorting the wrong way round")
                    .isEqualTo("feature-na");
            assertThat(whatHappensTo("sort/compare #(i8! [2 4 1 3]) func [a b] [a < b]"))
                    .isEqualTo("feature-na");
        }
    }

    @Nested
    @DisplayName("TAKE")
    class Taking {

        @Test
        @DisplayName("removes and answers one number, or none when there is none left")
        void takingOne() {
            assertThat(answerTo("v: #(i32! [10 20]) take v")).isEqualTo("10");
            assertThat(answerTo("v: #(i32! [10 20]) take/last v")).isEqualTo("20");
            assertThat(answerTo("v: #(i32! []) mold take v").replace("\"", ""))
                    .isEqualTo("_");
            assertThat(answerTo("v: #(i32! []) mold take/last v").replace("\"", ""))
                    .isEqualTo("_");
        }

        @Test
        @DisplayName("and /PART answers a vector of the same kind")
        void takingSeveral() {
            assertThat(holds("take/part #(i32! [10 20 30]) 2", "#(i32! [10 20])"))
                    .isEqualTo(TRUE);
            assertThat(holds("take/part/last #(i32! [10 20 30]) 1", "#(i32! [30])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("asking for more than is there takes what is there")
        void takingMoreThanIsThere() {
            assertThat(holds("take/part #(i32! [1 2 3]) 100", "#(i32! [1 2 3])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("taking from a position takes from there, and both names see it")
        void takingFromAPosition() {
            assertThat(answerTo("""
                    v: #(i32! [10 20 30 40 50])
                    w: skip v 2
                    all [(take w) == 30
                         w == #(i32! [40 50])
                         v == #(i32! [10 20 40 50])]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /PART/LAST never reaches back past the position")
        void takingTheLastNeverReachesBehind() {
            assertThat(answerTo("""
                    v: #(i32! [10 20 30 40 50])
                    w: skip v 3
                    all [(take/part/last w 5) == #(i32! [40 50])
                         empty? w
                         v == #(i32! [10 20 30])]""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("APPEND, INSERT and CHANGE")
    class Modifying {

        @Test
        @DisplayName("a single number goes on the end, at the front, or over what is there")
        void onenumber() {
            assertThat(holds("append #(i8! [1 2]) 3", "#(i8! [1 2 3])")).isEqualTo(TRUE);
            assertThat(answerTo("all [(insert v: #(i8! [1 2]) 3) == #(i8! [1 2]) "
                    + "v == #(i8! [3 1 2])]")).isEqualTo(TRUE);
            assertThat(answerTo("all [(change v: #(i8! [1 2]) 3) == #(i8! [2]) "
                    + "v == #(i8! [3 2])]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a decimal is truncated on the way in")
        void adecimalIsTruncated() {
            assertThat(holds("append #(i32! [1 2]) 3.5", "#(i32! [1 2 3])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a block adds each of its values")
        void ablock() {
            assertThat(holds("append #(i8! [1 2]) [3 4]", "#(i8! [1 2 3 4])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append #(i16! [1 2]) [3.5 4.1]", "#(i16! [1 2 3 4])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("another vector adds its numbers, whatever width it holds them at")
        void anothervector() {
            assertThat(holds("append #(i8! [1 2]) #(i8! [3 4])", "#(i8! [1 2 3 4])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append #(i16! [1 2]) #(f32! [3.5 4.1])", "#(i16! [1 2 3 4])"))
                    .as("the numbers are converted to the target's kind, not its bits")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a binary is read at the vector's own width")
        void abinary() {
            assertThat(holds("append #(i8! [1 2]) #{0304}", "#(i8! [1 2 3 4])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append #(i16! [1 2]) #{03000400}", "#(i16! [1 2 3 4])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a stray byte past the last whole number is dropped without a word")
        void thePartialElementIsDropped() {
            assertThat(holds("append #(i16! [1 2]) #{030004}", "#(i16! [1 2 3])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append #(i16! [1]) #{0100020003}", "#(i16! [1 1 2])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append #(i32! [1]) #{0200000003}", "#(i32! [1 2])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a binary too short to spell one number is refused, empty included")
        void tooShortForOneNumberIsRefused() {
            assertThat(whatHappensTo("append #(i16! [1 2]) #{03}"))
                    .isEqualTo("invalid-data");
            assertThat(whatHappensTo("append #(i16! [1 2]) #{}"))
                    .isEqualTo("invalid-data");
            assertThat(whatHappensTo("append #(i8! [1]) #{}"))
                    .isEqualTo("invalid-data");
            assertThat(whatHappensTo("append #(i32! [1]) #{030004}"))
                    .isEqualTo("invalid-data");
            assertThat(whatHappensTo("append #(f32! [1.0]) #{0304}"))
                    .isEqualTo("invalid-data");

            assertThat(whatHappensTo("append #(i8! [1]) #{02}"))
                    .as("one byte is a whole number at a width of one")
                    .isEqualTo("accepted");
        }

        @Test
        @DisplayName("the failure hands back the binary itself, not a rendering of it")
        void theFailureNamesTheBinary() {
            assertThat(answerTo(
                    "e: try [append #(i16! [1 2]) #{03}] type? e/arg1"))
                    .isEqualTo("#(binary!)");
            assertThat(answerTo(
                    "e: try [append #(i16! [1 2]) #{03}] e/arg1 = #{03}"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: try [append/part #(i16! [1 2]) #{0304} 1] e/arg1 = #{0304}"))
                    .as("the whole binary, not the part the limit named")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/PART caps the bytes read for APPEND and INSERT and not for CHANGE")
        void partCapsTheBytesRead() {
            assertThat(holds("append/part #(i16! [1 2]) #{03000400} 2", "#(i16! [1 2 3])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append/part #(i16! [1 2]) #{03000400} 3", "#(i16! [1 2 3])"))
                    .as("three bytes is one whole number and a byte dropped")
                    .isEqualTo(TRUE);
            assertThat(holds("append/part #(i16! [1 2]) #{03000400} 9", "#(i16! [1 2 3 4])"))
                    .as("a limit past the end takes what is there")
                    .isEqualTo(TRUE);
            assertThat(whatHappensTo("append/part #(i16! [1 2]) #{03000400} 0"))
                    .isEqualTo("invalid-data");
            assertThat(whatHappensTo("append/part #(i16! [1 2]) #{03000400} -1"))
                    .isEqualTo("invalid-data");
            assertThat(whatHappensTo("insert/part #(i16! [1 2]) #{05000600} 0"))
                    .isEqualTo("invalid-data");

            assertThat(holds("head change/part #(i16! [1 2 3 4]) #{05000600} 1",
                    "#(i16! [5 6 2 3 4])"))
                    .as("CHANGE reads the whole binary whatever the limit says")
                    .isEqualTo(TRUE);
            assertThat(holds("head change/part #(i16! [1 2 3 4]) #{05000600} 3",
                    "#(i16! [5 6 4])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a negative /PART counts back from where the source stands")
        void anegativePartCountsBackwards() {
            assertThat(holds("append/part #(i16! [1 2]) tail #{03000400} -2",
                    "#(i16! [1 2 4])"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(
                    "e: try [append/part #(i16! [1 2]) tail #{03000400} -1] e/arg1"))
                    .isEqualTo("#{00}");

            assertThat(holds("append/part #(i16! [1 2]) [3 4] -1", "#(i16! [1 2])"))
                    .as("at the head there is nothing behind it to take")
                    .isEqualTo(TRUE);
            assertThat(holds("append/part #(i16! [1 2]) #(i16! [3 4]) -1", "#(i16! [1 2])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and /PART does not reach a bare number at all")
        void partDoesNotReachABareNumber() {
            assertThat(holds("append/part #(i16! [1 2]) 3 0", "#(i16! [1 2 3])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append/part #(i16! [1 2]) [3 4] 0", "#(i16! [1 2])"))
                    .as("a block of the same length does take the limit")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("INSERT and CHANGE ask the same of a binary as APPEND does")
        void theOtherTwoAskTheSame() {
            assertThat(holds("head insert #(i16! [1 2]) #{050006}", "#(i16! [5 1 2])"))
                    .isEqualTo(TRUE);
            assertThat(whatHappensTo("insert #(i16! [1 2]) #{03}"))
                    .isEqualTo("invalid-data");
            assertThat(holds("head change #(i16! [1 2 3 4]) #{050006}", "#(i16! [5 2 3 4])"))
                    .isEqualTo(TRUE);
            assertThat(whatHappensTo("change #(i16! [1 2]) #{}"))
                    .isEqualTo("invalid-data");
        }

        @Test
        @DisplayName("a binary is read from where it stands, not from its head")
        void aBinaryIsReadFromItsPosition() {
            assertThat(holds("append #(i16! [1 2]) next #{030004}", "#(i16! [1 2 1024])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and something that is neither a number nor bytes is a bad argument")
        void awrongTypeIsABadArgument() {
            assertThat(whatHappensTo("append #(i16! [1 2]) \"ab\""))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("/PART limits how much is taken and /DUP repeats it")
        void partAndDup() {
            assertThat(holds("append/part #(i64! [1 2]) [3 4] 1", "#(i64! [1 2 3])"))
                    .isEqualTo(TRUE);
            assertThat(holds("append/part #(f32! [1 2]) [3 4] 3", "#(f32! [1 2 3 4])"))
                    .as("a limit past the end takes what is there")
                    .isEqualTo(TRUE);
            assertThat(holds("append/dup #(f64! [1 2]) [3 4] 2", "#(f64! [1 2 3 4 3 4])"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("appending at a position still appends at the end")
        void appendingFromAPosition() {
            assertThat(holds("append next #(i16! [1 2]) 3", "#(i16! [1 2 3])"))
                    .as("APPEND ignores where the value points, which is what "
                            + "distinguishes it from INSERT")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but inserting at one puts the numbers there")
        void insertingFromAPosition() {
            assertThat(answerTo("all [(insert next v: #(i8! [1 2]) 3) == #(i8! [2]) "
                    + "v == #(i8! [1 3 2])]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("CHANGE writes over as many as it adds, and lengthens when it runs out")
        void changing() {
            assertThat(answerTo("all [(change v: #(i8! [1 2 3]) [3 4]) == #(i8! [3]) "
                    + "v == #(i8! [3 4 3])]")).isEqualTo(TRUE);
            assertThat(answerTo("all [(change/part v: #(i8! [1 2]) 3 3) == #(i8! []) "
                    + "v == #(i8! [3])]")).isEqualTo(TRUE);
            assertThat(answerTo("all [(change/dup v: #(i8! [1 2]) 3 2) == #(i8! []) "
                    + "v == #(i8! [3 3])]")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the rest of the series actions")
    class TheRest {

        @Test
        @DisplayName("REMOVE takes one out at the position")
        void removing() {
            assertThat(holds("remove #(i8! [1 2])", "#(i8! [2])")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("FIND-MAX and FIND-MIN answer the position of the extreme")
        void findingTheExtremes() {
            assertThat(answerTo("first find-max #(i32! [1 2 3 -1])")).isEqualTo("3");
            assertThat(answerTo("first find-min #(i32! [1 2 3 -1])")).isEqualTo("-1");
        }

        @Test
        @DisplayName("RANDOM shuffles in place and answers the very same vector")
        void randomShufflesInPlace() {
            assertThat(answerTo("v: #(i32! [1 2 3 4 5]) same? v random v"))
                    .as("a block behaves the same way, and a vector that answered a "
                            + "copy would break every script that relies on it")
                    .isEqualTo(TRUE);
        }

        @ParameterizedTest
        @CsvSource({"select, cannot-use", "find, cannot-use"})
        @DisplayName("but searching a vector is not something a vector does")
        void searchingIsRefused(String native0, String expected) {
            assertThat(whatHappensTo(native0 + " #(i8! [1 2 3]) 2")).isEqualTo(expected);
        }
    }
}
