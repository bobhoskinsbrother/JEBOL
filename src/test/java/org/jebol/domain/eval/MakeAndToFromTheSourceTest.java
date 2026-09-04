package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What MAKE and TO accept, and where the two of them differ.
 *
 * <p>JEBOL had them as one operation, which is why {@code make block! #"a"}
 * answered {@code [#"a"]} where a real Rebol refuses it. They are not the
 * same: TO converts, so it wraps whatever it is given; MAKE builds, so it
 * takes a list of shapes and reads a number as room rather than as a value.
 * {@code Make_Block_Type} carries its {@code make} flag through for exactly
 * that reason.
 *
 * <p>The other half is dates, and it grew twice. A Unix timestamp converts
 * both ways and neither direction existed. Reaching for the comparison that
 * would have proved it turned up something worse: two dates were ordered by
 * their written form, so {@code 9-Jan-2000} came after {@code 10-Jan-2000} and
 * {@code 1-Jan-2000} came before {@code 2-Feb-1999}. A date fell through every
 * arm of the ordering to the one that compares molded text. They are ordered
 * by the instant they name now, zone taken off, which is what Rebol compares
 * from the other end -- it stores the instant and adds the zone back only to
 * write the date out. {@code docs/rebol-findings.md} entry 21 has that.
 *
 * <p>Then running every date expression through both implementations side by
 * side found the third: adding to a date dropped its clock and its zone, read
 * a time as a count of days, and truncated a fraction of a day to none. Entry
 * 22 has that one, and {@link MovingADate} pins it.
 *
 * <p>Every expectation here was run against a real Rebol before it was written
 * down, and against both of the ones in the repo root -- the 3.22.1 download
 * and the 3.22.5 built by {@code scripts/build-r3.sh}. The two agree on all of
 * it, which is not something to assume: the porting guide records four wrong
 * readings traced to asking the older one.
 */
class MakeAndToFromTheSourceTest {

    /**
     * What JEBOL answers, with the quotes MOLD's own result arrives in taken
     * off. Displaying a string molds it a second time.
     */
    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        String shown = interpreter.display(interpreter.run(source));
        boolean quoted = shown.length() >= 2
                && ((shown.charAt(0) == '"' && shown.endsWith("\""))
                        || (shown.charAt(0) == '{' && shown.endsWith("}")));
        return quoted ? shown.substring(1, shown.length() - 1) : shown;
    }

    private static String errorOr(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] [" + source + "]");
    }

    @Nested
    @DisplayName("a date from a count of seconds since 1970")
    class FromATimestamp {

        @ParameterizedTest(name = "to date! {0} is {1}")
        @CsvSource({
            "1632142001,     20-Sep-2021/12:46:41",
            "0,              1-Jan-1970/0:00",
            "-1,             31-Dec-1969/23:59:59",
            "1051876800.0,   2-May-2003/12:00",
            "-3506716800.0,  17-Nov-1858/0:00",
            "1686360600.0,   10-Jun-2023/1:30",
        })
        @DisplayName("whole seconds and fractions, forwards and back through zero")
        void wholeSecondsAndFractions(String seconds, String expected) {
            assertThat(answerTo("mold to date! " + seconds)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "to date! {0} is {1}")
        @CsvSource({
            "-0.1,  31-Dec-1969/23:59:59.9",
            "0.0,   1-Jan-1970/0:00",
            "0.1,   1-Jan-1970/0:00:00.1",
        })
        @DisplayName("a fraction is kept to the microsecond, which is the C's own choice")
        void afractionIsKeptToTheMicrosecond(String seconds, String expected) {
            assertThat(answerTo("mold to date! " + seconds))
                    .as("Timestamp_Decimal_To_Date multiplies out to microseconds "
                            + "rather than nanoseconds, and says why: a decimal "
                            + "count of seconds does not land where it should")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("MAKE and TO answer the same, because the C shares the arm")
        void makeAndToAnswerTheSame() {
            assertThat(answerTo("mold make date! 1632142001"))
                    .isEqualTo("20-Sep-2021/12:46:41");
        }
    }

    @Nested
    @DisplayName("a count of seconds back out of a date")
    class BackToATimestamp {

        @ParameterizedTest(name = "to decimal! {0} is {1}")
        @CsvSource({
            "17-Nov-1858/00:00:00,  -3506716800.0",
            "01-Jan-1900/00:00:00,  -2208988800.0",
            "02-May-2003/12:00:00,  1051876800.0",
        })
        @DisplayName("as a decimal, which keeps the fraction")
        void asadecimal(String written, String expected) {
            assertThat(answerTo("mold to decimal! " + written)).isEqualTo(expected);
        }

        @Test
        @DisplayName("as an integer, rounding the fraction rather than dropping it")
        void asanIntegerRounding() {
            assertThat(answerTo("to integer! 20-Sep-2021/12:46:41.7"))
                    .as("the second after 12:46:41, not the same one")
                    .isEqualTo("1632142002");
        }

        @Test
        @DisplayName("and the zone comes off, because it says how far ahead of UTC")
        void thezoneComesOff() {
            assertThat(answerTo("to integer! 20-Sep-2021/12:58:32+2:00"))
                    .isEqualTo("1632135512");
        }
    }

    @Nested
    @DisplayName("two dates that name one moment are equal")
    class ComparingDates {

        @Test
        @DisplayName("however the zone was or was not written down")
        void howeverTheZoneWasWritten() {
            assertThat(answerTo("20-Sep-2021/12:46:41 = make date! 1632142001"))
                    .as("the timestamp writes a zone of zero down and the literal "
                            + "leaves it out, and comparing the two records whole "
                            + "made that difference an inequality where the C has "
                            + "nowhere to keep it")
                    .isEqualTo("#(true)");
            assertThat(answerTo("20-Sep-2021/12:46:41 == make date! 1632142001"))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and equality agrees with ordering, which it did not")
        void equalityAgreesWithOrdering() {
            assertThat(answerTo("""
                    a: make date! 1632142001
                    b: 20-Sep-2021/12:46:41
                    mold reduce [a = b  a < b  a > b  (a - b)]"""))
                    .isEqualTo("[#(true) #(false) #(false) 0]");
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "'9-Jan-2000 < 10-Jan-2000',   '#(true)'",
            "'1-Jan-2000 < 2-Feb-1999',    '#(false)'",
            "'1-Jan-2000 > 2-Feb-1999',    '#(true)'",
            "'2-Feb-1999 < 1-Jan-2000',    '#(true)'",
        })
        @DisplayName("ordered by the day they name, not by how they are written")
        void orderedByTheDayTheyName(String asked, String expected) {
            assertThat(answerTo(asked))
                    .as("as text, 9-Jan comes after 10-Jan and 1-Jan-2000 comes "
                            + "before 2-Feb-1999, and both of those were the "
                            + "answer until the instant was compared instead")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("and the zone counts, because it says which instant is meant")
        void thezoneCounts() {
            assertThat(answerTo("""
                    mold reduce [
                        20-Sep-2021/12:00+2:00 = 20-Sep-2021/10:00
                        20-Sep-2021/12:00+2:00 = 20-Sep-2021/12:00
                        20-Sep-2021/12:00+2:00 < 20-Sep-2021/12:00]"""))
                    .isEqualTo("[#(true) #(false) #(true)]");
        }

        @Test
        @DisplayName("but strict equality reads the written form, zone included")
        void strictEqualityReadsTheWrittenForm() {
            assertThat(answerTo("20-Sep-2021/12:00+2:00 == 20-Sep-2021/10:00"))
                    .as("the same instant, written two ways, and == is the "
                            + "question about the writing")
                    .isEqualTo("#(false)");
            assertThat(answerTo("20-Sep-2021/12:00 == 20-Sep-2021/12:00+0:00"))
                    .as("a zone of zero and no zone at all are one thing in the "
                            + "C, which has nowhere to put the difference")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a date with no time is its midnight for ordering and is not "
                + "midnight for the written form")
        void adateWithNoTimeIsItsMidnight() {
            assertThat(answerTo("""
                    mold reduce [
                        1-Jan-2000 = 1-Jan-2000/0:00
                        1-Jan-2000 == 1-Jan-2000/0:00
                        1-Jan-2000 < 1-Jan-2000/0:01]"""))
                    .as("NO_TIME counts as zero in Cmp_Time and is its own value "
                            + "in the bits strict equality reads")
                    .isEqualTo("[#(true) #(false) #(true)]");
        }

        @Test
        @DisplayName("and SORT puts them in order too, for the same reason")
        void sortputsThemInOrder() {
            assertThat(answerTo(
                    "mold sort [10-Jan-2000 9-Jan-2000 1-Jan-2000 2-Feb-1999]"))
                    .isEqualTo("[2-Feb-1999 1-Jan-2000 9-Jan-2000 10-Jan-2000]");
        }
    }

    @Nested
    @DisplayName("adding to a date means three different units")
    class MovingADate {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "'20-Sep-2021/12:00+2:00 + 1',    21-Sep-2021/12:00+2:00",
            "'20-Sep-2021/12:00+2:00 - 1',    19-Sep-2021/12:00+2:00",
            "'1 + 20-Sep-2021/12:00+2:00',    21-Sep-2021/12:00+2:00",
            "'31-Dec-2021/12:00+2:00 + 1',    1-Jan-2022/12:00+2:00",
            "'1-Mar-2000/12:00-5:00 - 1',     29-Feb-2000/12:00-5:00",
            "'20-Sep-2021 + 1',               21-Sep-2021",
        })
        @DisplayName("a whole number is days, and the clock and the zone come "
                + "through untouched")
        void awholeNumberIsDays(String asked, String expected) {
            assertThat(answerTo("mold " + asked))
                    .as("the C ends every arm at Normalize_Date with the zone it "
                            + "read off the original, and dropping both of them "
                            + "here turned a moment into a bare day")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "'20-Sep-2021/12:00+2:00 + 0:30',  20-Sep-2021/12:30+2:00",
            "'20-Sep-2021/12:00+2:00 - 13:00', 19-Sep-2021/23:00+2:00",
            "'20-Sep-2021/23:00 + 2:00',       21-Sep-2021/1:00",
            "'20-Sep-2021/0:30 - 1:00',        19-Sep-2021/23:30",
            "'1-Jan-2000/0:00 - 0:00:01',      31-Dec-1999/23:59:59",
        })
        @DisplayName("a time is a duration and carries into the day either way")
        void atimeIsADuration(String asked, String expected) {
            assertThat(answerTo("mold " + asked))
                    .as("reading a time as a count of days put "
                            + "20-Sep-2021 + 1:00 five years out")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("and a date carrying no time comes back carrying one")
        void adateWithNoTimeComesBackWithOne() {
            assertThat(answerTo("mold 20-Sep-2021 + 1:00"))
                    .as("if (secs == NO_TIME) secs = 0, before anything is added")
                    .isEqualTo("20-Sep-2021/1:00");
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "'20-Sep-2021/12:00+2:00 + 1.9',   22-Sep-2021/9:36+2:00",
            "'20-Sep-2021/12:00+2:00 + 2.25',  22-Sep-2021/18:00+2:00",
            "'20-Sep-2021/12:00+2:00 + 0.5',   21-Sep-2021/0:00+2:00",
            "'20-Sep-2021/12:00+2:00 - 0.5',   20-Sep-2021/0:00+2:00",
            "'20-Sep-2021/12:00+2:00 + 0.0',   20-Sep-2021/12:00+2:00",
        })
        @DisplayName("a decimal is a fraction of a day, so it moves the clock too")
        void adecimalIsAFractionOfADay(String asked, String expected) {
            assertThat(answerTo("mold " + asked))
                    .as("1.9 days is one day and 21.6 hours, which lands two days "
                            + "on at 9:36 rather than one day on at noon")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "'20-Sep-2021 - 19-Sep-2021',                    1",
            "'20-Sep-2021/23:00 - 20-Sep-2021/1:00',         0",
            "'20-Sep-2021/12:00+2:00 - 20-Sep-2021/10:00',   0",
        })
        @DisplayName("but one date from another counts whole days and no clock")
        void onedateFromAnotherCountsDays(String asked, String expected) {
            assertThat(answerTo(asked))
                    .as("Diff_Date never looks at the time, so twenty-two hours "
                            + "apart is a difference of nothing -- and that is the "
                            + "same subtraction that says less-than is true")
                    .isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("MAKE on a block takes shapes, where TO takes anything")
    class MakingBlocks {

        @ParameterizedTest(name = "make block! {0} is an empty block")
        @ValueSource(strings = {"4.0", "2x2", "0x2.2", "10"})
        @DisplayName("a number or a pair is room for values, not a value")
        void anumberOrAPairIsRoom(String given) {
            assertThat(answerTo("mold make block! quote " + given)).isEqualTo("[]");
        }

        @ParameterizedTest(name = "make block! {0} is refused")
        @ValueSource(strings = {
            "4%", "$4", "%file", "u@email", "@ref", "http://aa", "<tag>",
        })
        @DisplayName("and everything else is an invalid argument, as Trap_Arg says")
        void everythingElseIsRefused(String given) {
            assertThat(errorOr("make block! quote " + given)).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("while TO wraps the same values without complaint")
        void towrapsTheSameValues() {
            assertThat(answerTo("""
                    letter: #"a"
                    mold to block! letter""")).isEqualTo("""
                    [#"a"]""");
            assertThat(answerTo("""
                    letter: #"a"
                    either error? e: try [make block! letter] [e/id] ['worked]"""))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a percent is not a decimal, which is what makes 4% refused")
        void apercentIsNotADecimal() {
            assertThat(errorOr("make block! quote 4%")).isEqualTo("invalid-arg");
            assertThat(answerTo("mold make block! quote 4.0")).isEqualTo("[]");
        }
    }

    @Nested
    @DisplayName("nothing is not an empty something")
    class MakingFromNone {

        @ParameterizedTest(name = "make {0}! none is refused")
        @ValueSource(strings = {"string", "file", "email", "ref", "url", "tag", "object"})
        @DisplayName("a string or an object refuses it as a bad make argument")
        void astringOrAnObjectRefusesIt(String named) {
            assertThat(errorOr("make " + named + "! none")).isEqualTo("bad-make-arg");
        }

        @ParameterizedTest(name = "make {0}! none is refused")
        @ValueSource(strings = {
            "block", "paren", "path", "set-path", "get-path", "lit-path",
        })
        @DisplayName("and a block shape as an invalid argument, which is a different arm")
        void ablockShapeRefusesItDifferently(String named) {
            assertThat(errorOr("make " + named + "! none"))
                    .as("Make_Block_Type reaches Trap_Arg where the string maker "
                            + "reaches Trap_Make, and Rebol reports the two "
                            + "differently")
                    .isEqualTo("invalid-arg");
        }

        @ParameterizedTest(name = "to {0}! none is refused")
        @ValueSource(strings = {"string", "file", "email", "ref", "url", "tag"})
        @DisplayName("and TO refuses it too, where it used to answer the word none")
        void toRefusesItToo(String named) {
            assertThat(errorOr("to " + named + "! none")).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("but UNSET and NONE answer their own single value")
        void unsetAndNoneAnswerTheirOwn() {
            assertThat(answerTo("unset? make unset! none")).isEqualTo("#(true)");
            assertThat(answerTo("none? make none! none")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a logic is one and zero, in every numeric shape")
    class FromALogic {

        @ParameterizedTest(name = "make {0}! true is {1}")
        @CsvSource({
            "decimal, 1.0",
            "percent, 100%",
            "integer, 1",
            "money,   $1",
        })
        @DisplayName("true")
        void whenTrue(String named, String expected) {
            assertThat(answerTo("mold make " + named + "! true")).isEqualTo(expected);
        }

        @ParameterizedTest(name = "make {0}! false is {1}")
        @CsvSource({
            "decimal, 0.0",
            "percent, 0%",
            "integer, 0",
            "money,   $0",
        })
        @DisplayName("and false")
        void whenFalse(String named, String expected) {
            assertThat(answerTo("mold make " + named + "! false")).isEqualTo(expected);
        }

        @Test
        @DisplayName("a value standing in for the datatype works the same way")
        void avalueStandingInForTheDatatype() {
            assertThat(answerTo("mold make 0.0 true")).isEqualTo("1.0");
            assertThat(answerTo("mold make 42 false")).isEqualTo("0");
            assertThat(answerTo("mold make $111 true")).isEqualTo("$1");
        }

        @ParameterizedTest(name = "to {0}! true is refused")
        @ValueSource(strings = {"integer", "decimal", "percent", "money"})
        @DisplayName("but TO will not read one at all, which is the sharpest place "
                + "MAKE and TO come apart")
        void tostillWillNotReadOne(String named) {
            assertThat(errorOr("to " + named + "! true"))
                    .as("the C leaves a note where it refuses: no integer is "
                            + "uniquely representative of true, so converting one "
                            + "is a question with no answer, where building one "
                            + "from true is a choice that can be made")
                    .isEqualTo("bad-make-arg");
            assertThat(errorOr("to " + named + "! false")).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("TO puts a lone value inside a block shape where MAKE refuses it")
    class WrappingRatherThanBuilding {

        @ParameterizedTest(name = "to {0}! none holds the none")
        @ValueSource(strings = {
            "block", "paren", "path", "set-path", "get-path", "lit-path",
        })
        @DisplayName("nothing included, where MAKE calls the same thing invalid")
        void nothingIncluded(String named) {
            assertThat(answerTo("mold reduce ["
                    + "(" + named + "? made: to " + named + "! none) "
                    + "(1 = length? made) "
                    + "(none? first made)]"))
                    .as("the shape asked for, holding the one value and nothing "
                            + "else -- two empty results would compare equal, so "
                            + "the length is worth checking separately")
                    .isEqualTo("[#(true) #(true) #(true)]");
            assertThat(errorOr("make " + named + "! none")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and text stays text, where MAKE reads it as source")
        void textStaysText() {
            assertThat(answerTo("""
                    mold to block! "1 2\"""")).isEqualTo("""
                    ["1 2"]""");
            assertThat(answerTo("""
                    mold make block! "1 2\"""")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("a binary the same way, decoded rather than read, one way only")
        void abinaryTheSameWay() {
            assertThat(answerTo("mold to block! #{312032}")).isEqualTo("[#{312032}]");
            assertThat(answerTo("mold make block! #{312032}")).isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("a typeset names its members, and only into a block or a paren")
        void atypesetNamesItsMembers() {
            assertThat(answerTo("mold to block! #(typeset! [integer! percent!])"))
                    .isEqualTo("[#(integer!) #(percent!)]");
            assertThat(errorOr("make block! #(typeset! [integer! percent!])"))
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a hash is an any-block! for the typeset and not for this")
        void ahashIsNotOneOfThem() {
            assertThat(errorOr("to hash! 4"))
                    .as("ANY_BLOCK_TYPE is a range test over the datatype table, "
                            + "block to lit-path, and hash sits one row past the "
                            + "end of it -- so TO has nothing to wrap it into and "
                            + "falls through to the refusal")
                    .isEqualTo("invalid-arg");
            assertThat(answerTo("mold make hash! 4")).isEqualTo("make hash! []");
        }
    }

    @Nested
    @DisplayName("an object is already a list of names and values")
    class FromAnObject {

        @Test
        @DisplayName("so both MAKE and TO answer its fields rather than wrapping it")
        void bothAnswerItsFields() {
            assertThat(answerTo("mold to block! make object! [a: 1]"))
                    .as("the answer is a string holding a line break, and showing "
                            + "a string molds it a second time, so the break "
                            + "arrives written as ^/ rather than as itself")
                    .isEqualTo("[^/    a: 1^/]");
            assertThat(answerTo("mold make block! make object! [a: 1]"))
                    .isEqualTo("[^/    a: 1^/]");
        }

        @Test
        @DisplayName("with SELF left out, because the C starts counting one slot in")
        void withSelfLeftOut() {
            assertThat(answerTo("mold to block! make object! [a: 1 b: 2]"))
                    .as("two fields put enough line breaks in the answer that "
                            + "showing it reaches for braces instead of quotes, "
                            + "and inside braces a break writes as itself")
                    .isEqualTo("[\n    a: 1\n    b: 2\n]");
        }

        @Test
        @DisplayName("and the fields come back whatever is asked for them")
        void thefieldsComeBackWhateverIsAsked() {
            assertThat(answerTo("mold words-of make object! [a: 1 b: 2]"))
                    .isEqualTo("[a b]");
            assertThat(answerTo("mold values-of make object! [a: 1 b: 2]"))
                    .isEqualTo("[1 2]");
        }
    }

    @Nested
    @DisplayName("a date from a block is a positional grammar, read to the end")
    class ReadingADateFromParts {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "'[2000 2 1]',                  1-Feb-2000",
            "'[1 1 2000]',                  1-Jan-2000",
            "'[2000 2 1 1 2 3]',            1-Feb-2000/1:02:03",
            "'[2000 2 1 1 2 3.4]',          1-Feb-2000/1:02:03.4",
            "'[1-Feb-2000 1 2 3]',          1-Feb-2000/1:02:03",
            "'[1-Feb-2000 10:30]',          1-Feb-2000/10:30",
            "'[2000 2 1 23 59 59.99]',      1-Feb-2000/23:59:59.99",
        })
        @DisplayName("a calendar day, then a clock written either way")
        void acalendarDayThenAClock(String parts, String expected) {
            assertThat(answerTo("mold make date! " + parts))
                    .as("a first number over ninety-nine is the year, so "
                            + "[2000 2 1] and [1 2 2000] are the same day and "
                            + "neither is ambiguous")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} carries its zone")
        @CsvSource({
            "'[2000 2 1 1 2 3 2:00]',       1-Feb-2000/1:02:03+2:00",
            "'[2000 2 1 1 2 3.4 2:00]',     1-Feb-2000/1:02:03.4+2:00",
            "'[1-Feb-2000 1 2 3 2:00]',     1-Feb-2000/1:02:03+2:00",
        })
        @DisplayName("and then a zone, which is a second time after the first")
        void andthenAZone(String parts, String expected) {
            assertThat(answerTo("mold make date! " + parts))
                    .as("that a block may hold two times and the second is not "
                            + "another clock is the part worth knowing. Feeding "
                            + "all four remaining parts to the clock reader "
                            + "refused every one of these")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} is refused")
        @ValueSource(strings = {
            "[2000 2 1 24 0 0]", "[2000 2 1 23 60 0]", "[2000 2 1 23 59 60]",
        })
        @DisplayName("a clock is held to its own bounds, which a time is not")
        void aclockIsHeldToItsOwnBounds(String parts) {
            assertThat(errorOr("make date! " + parts))
                    .as("make time! [24 0 0] is a legal day's worth of hours and "
                            + "make date! [2000 2 1 24 0 0] is no date at all")
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("and the twenty-fourth hour is a time, to show the difference")
        void thetwentyFourthHourIsATime() {
            assertThat(answerTo("mold make time! [24 0 0]")).isEqualTo("24:00");
        }

        @ParameterizedTest(name = "{0} is refused")
        @ValueSource(strings = {
            "[2000 13 1]", "[2000 2 30]", "[2001 2 29]",
            "[2000 2 1 1 2 3 2:00 9]",
        })
        @DisplayName("and a day that is not one, or a part left over, is refused")
        void adaythatIsNotOneIsRefused(String parts) {
            assertThat(errorOr("make date! " + parts))
                    .as("a part the grammar cannot account for is not something "
                            + "to step over: if (!IS_END(arg)) return FALSE")
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("while a leap year takes the twenty-ninth of February")
        void aleapYearTakesTheTwentyNinth() {
            assertThat(answerTo("mold make date! [2000 2 29]"))
                    .isEqualTo("29-Feb-2000");
        }
    }

    @Nested
    @DisplayName("the reader builds a construct the way MAKE-DISPATCH does")
    class ReadingAConstruct {

        @Test
        @DisplayName("which for a date is parts only, where MAKE also takes a count "
                + "of seconds")
        void adateFromPartsOnly() {
            assertThat(errorOr("load {#(date! 1)}"))
                    .as("MT_Date reads a date or a block of parts and nothing "
                            + "else; the timestamp lives in the arm one level up")
                    .isEqualTo("malconstruct");
            assertThat(answerTo("mold make date! 1"))
                    .isEqualTo("1-Jan-1970/0:00:01");
        }

        @Test
        @DisplayName("and a block of parts still reads")
        void ablockOfPartsStillReads() {
            assertThat(answerTo("mold load {#(date! [2000 1 1])}"))
                    .isEqualTo("1-Jan-2000");
        }
    }

    @Nested
    @DisplayName("a number that is not there is a bad make argument")
    class RefusingNonNumbers {

        @ParameterizedTest(name = "to {0}! #FF is refused")
        @ValueSource(strings = {"decimal", "percent"})
        @DisplayName("an issue has digits in it and is still not a number")
        void anissueIsStillNotANumber(String named) {
            assertThat(errorOr("to " + named + "! #FF"))
                    .as("bad-make-arg rather than expect-arg: the caller passed a "
                            + "value this conversion cannot use, not the wrong kind "
                            + "of thing to a function")
                    .isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("a decimal and a percent share one arm and part company at the end")
    class BuildingADecimal {

        @ParameterizedTest(name = "to percent! {0} is {1}")
        @CsvSource({
            "4,        400%",
            "4.5,      450%",
            "4%,       4%",
            "$4,       400%",
            "#\"a\",   9700%",
        })
        @DisplayName("a number-like source is the value itself, so four is four "
                + "hundred percent")
        void anumberLikeSourceIsTheValueItself(String source, String expected) {
            assertThat(answerTo("mold to percent! " + source))
                    .as("these reach setDec, which is above the division by a "
                            + "hundred rather than through it")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "to percent! {0} is {1}")
        @CsvSource({
            "10:00,                   36000%",
            "0:00:01,                 1%",
            "1-Jan-2000,              946684800%",
            "#{3FF0000000000000},     1%",
        })
        @DisplayName("and everything else is a count of hundredths, which is the "
                + "one thing here that cannot be guessed")
        void everythingElseIsACountOfHundredths(String source, String expected) {
            assertThat(answerTo("mold to percent! " + source))
                    .as("ten hours is thirty-six thousand seconds, and thirty-six "
                            + "thousand percent -- not a hundred times that")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "to decimal! {0} is {1}")
        @CsvSource({
            "10:00,                  36000.0",
            "1-Jan-2000,             946684800.0",
            "#{3FF0000000000000},    1.0",
            "#\"a\",                 97.0",
            "$4,                     4.0",
        })
        @DisplayName("a decimal never divides, so the two agree there")
        void adecimalNeverDivides(String source, String expected) {
            assertThat(answerTo("mold to decimal! " + source)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("reading a number out of text is Rebol's own scanner")
    class ScanningANumber {

        @ParameterizedTest(name = "to decimal! {0} is {1}")
        @CsvSource({
            "'\"50\"',        50.0",
            "'\"50.5\"',      50.5",
            "'\"1,5\"',       1.5",
            "'\"1''000\"',    1000.0",
            "'\" 50 \"',      50.0",
            "'\"1e3\"',       1000.0",
            "'\"1e-3\"',      0.001",
            "'\"1e\"',        1.0",
            "'\"1.\"',        1.0",
            "'\".5\"',        0.5",
        })
        @DisplayName("a comma is a decimal point and an apostrophe separates digits")
        void acommaIsADecimalPoint(String written, String expected) {
            assertThat(answerTo("mold to decimal! " + written))
                    .as("Scan_Decimal is not what Double.parseDouble accepts, "
                            + "which is why it is ported rather than delegated to")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("and a trailing percent sign is allowed only when a percent "
                + "is what is being read")
        void atrailingPercentSignIsForPercentsOnly() {
            assertThat(errorOr("""
                    to decimal! "50%\""""))
                    .as("the dec_only flag, and the whole of what it is for")
                    .isEqualTo("bad-make-arg");
            assertThat(answerTo("""
                    mold to percent! "50%\"""")).isEqualTo("50%");
        }

        @ParameterizedTest(name = "to decimal! {0} is a {1}")
        @CsvSource({
            "'\"\"',                             too-short",
            "'\"1 2\"',                          invalid-chars",
            "'\"abc\"',                          bad-make-arg",
            "'\"1.2.3\"',                        bad-make-arg",
            "'\"1234567890123456789012345\"',    too-long",
        })
        @DisplayName("and the failure tells the caller which of four things went "
                + "wrong, decided by the text and not by the call")
        void thefailureTellsTheCallerWhich(String written, String expected) {
            assertThat(errorOr("to decimal! " + written))
                    .as("Qualify_String answers the first three before the "
                            + "scanner is reached at all")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("a whole number may be written one character longer than a "
                + "fraction, which the largest of them needs")
        void awholeNumberMayBeOneLonger() {
            assertThat(answerTo("""
                    to integer! "9'223'372'036'854'775'807\""""))
                    .as("twenty-five characters with its separators, and reading "
                            + "it against the decimal limit made the largest whole "
                            + "number there is too long to write down")
                    .isEqualTo("9223372036854775807");
            assertThat(errorOr("""
                    to integer! "9'223'372'036'854'775'808\""""))
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a number may have a line feed in front of it and not behind")
        void alineFeedInFrontAndNotBehind() {
            assertThat(answerTo("""
                    mold to decimal! "^-1 \""""))
                    .as("IS_LEX_SPACE skips a tab in front; IS_SPACE lets a space "
                            + "follow. Rebol's suite measures the second set "
                            + "exactly and it is these two characters")
                    .isEqualTo("1.0");
            assertThat(errorOr("""
                    to decimal! "1^/\"""")).isEqualTo("bad-make-arg");
            assertThat(errorOr("""
                    to decimal! "1 ^/\"""")).isEqualTo("invalid-chars");
        }

        @ParameterizedTest(name = "to money! {0} is {1}")
        @CsvSource({
            "'\"1\"',      $1",
            "'\"$1\"',     $1",
            "'\"1.5\"',    $1.5",
            "'\"1,5\"',    $1.5",
            "'\"-$1\"',    -$1",
        })
        @DisplayName("a money reads the same number a decimal does, with one "
                + "currency mark allowed in front of it")
        void amoneyReadsTheSameNumber(String written, String expected) {
            assertThat(answerTo("mold to money! " + written))
                    .as("sharing the scanner is what makes the two accept the "
                            + "same characters, which Rebol's suite measures "
                            + "separately for each and gets the same answer twice")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "to money! {0} is refused")
        @CsvSource({
            "'\"$-1\"',     bad-make-arg",
            "'\"USD$1\"',   bad-make-arg",
            "'\"\"',        too-short",
            "'\"1 2\"',     invalid-chars",
        })
        @DisplayName("and a sign comes before the mark, never after it")
        void asignComesBeforeTheMark(String written, String expected) {
            assertThat(errorOr("to money! " + written)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an endless number has a written form, and what came before "
                + "the hash is thrown away")
        void anendlessNumberHasAWrittenForm() {
            assertThat(answerTo("""
                    mold reduce [
                        to decimal! "#INF"
                        to decimal! "-#INF"
                        to decimal! "1#INF"]"""))
                    .as("the C has already copied those digits into its buffer "
                            + "and abandons them where it meets the hash")
                    .isEqualTo("[1.#INF -1.#INF 1.#INF]");
        }

        @ParameterizedTest(name = "to decimal! {0} is refused")
        @ValueSource(strings = {"%file", "u@email", "http://aa", "<tag>", "@ref"})
        @DisplayName("only a plain string is read as text, never a file or a tag")
        void onlyaplainStringIsReadAsText(String written) {
            assertThat(errorOr("to decimal! " + written))
                    .as("case REB_STRING and not ANY_STR, which JEBOL has to ask "
                            + "the datatype about because one class holds them all")
                    .isEqualTo("bad-make-arg");
        }

        @ParameterizedTest(name = "to decimal! {0} is {1}")
        @CsvSource({
            "'[1 2]',      100.0",
            "'[1.5 2]',    150.0",
            "'[1 -2]',     0.01",
        })
        @DisplayName("a block of exactly two is a mantissa and an exponent")
        void ablockOfTwoIsAMantissaAndAnExponent(String written, String expected) {
            assertThat(answerTo("mold to decimal! " + written)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "to decimal! {0} is refused")
        @ValueSource(strings = {"[1]", "[1 2 3]", "[a b]"})
        @DisplayName("and anything else in a block is refused")
        void anythingElseInABlockIsRefused(String written) {
            assertThat(errorOr("to decimal! " + written)).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("a binary is a list of datatypes, not a rule about bytes")
    class BuildingABinary {

        @ParameterizedTest(name = "to binary! {0} is {1}")
        @CsvSource({
            "1.1.1,                  '#{010101}'",
            "1.2.3.4.5,              '#{0102030405}'",
            "'#(bitset! #{FF})',     '#{FF}'",
            "'#(image! 1x1 #{FFFFFF})', '#{FFFFFFFF}'",
            "'[1 2]',                '#{0102}'",
            "'\"1 2\"',              '#{312032}'",
            "%file,                  '#{66696C65}'",
            "'#(uint32! [0 0])',     '#{0000000000000000}'",
        })
        @DisplayName("what is on the list converts, including three that JEBOL "
                + "used to refuse")
        void whatIsOnTheListConverts(String source, String expected) {
            assertThat(answerTo("mold to binary! quote " + source))
                    .as("a tuple keeps its own length rather than the three it "
                            + "shows, and an image is four bytes a pixel")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "to binary! {0} is refused")
        @ValueSource(strings = {
            "4%", "(1 2)", "a/b", "/ref", "#FF", "#(true)", "2x2", "10:00",
            "2000-01-01", "#(object! [a: 1])", "#(typeset! [integer! percent!])",
        })
        @DisplayName("and what is not on it is an invalid argument, however many "
                + "bytes it has underneath")
        void whatIsNotOnItIsRefused(String source) {
            assertThat(errorOr("to binary! quote " + source))
                    .as("reading the list as a rule is what went wrong: a percent, "
                            + "a paren, a path and an issue all have bytes and are "
                            + "all refused, where the decimal, block, string and "
                            + "word they resemble are taken")
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("a number is room to MAKE and eight big-endian bytes to TO")
        void anumberIsRoomToMakeAndBytesToConvert() {
            assertThat(answerTo("mold make binary! 4")).isEqualTo("#{}");
            assertThat(answerTo("mold to binary! 4"))
                    .isEqualTo("#{0000000000000004}");
        }

        @Test
        @DisplayName("and a complemented bitset answers the complement of its bytes")
        void acomplementedBitsetAnswersTheComplement() {
            assertThat(answerTo("""
                    mold to binary! charset [#"a"]"""))
                    .isEqualTo("#{00000000000000000000000040}");
            assertThat(answerTo("""
                    mold to binary! complement charset [#"a"]"""))
                    .as("a complemented bitset keeps the bytes of what it leaves "
                            + "out and a flag saying to read them the other way "
                            + "round, so asking it for its octets gives the same "
                            + "answer either way -- VAL_BITSET_NOT sends it "
                            + "through Complement_Binary instead")
                    .isEqualTo("#{FFFFFFFFFFFFFFFFFFFFFFFFBF}");
        }
    }

    @Nested
    @DisplayName("a path writes itself as a construct when it would not read back")
    class WritingAPath {

        @ParameterizedTest(name = "{0} molds as a construct")
        @CsvSource({
            "'to path! quote [a]',     '#(path! [a])'",
            "'to path! quote [a: b]',  '#(path! [a: b])'",
            "'to path! quote [:a b]',  '#(path! [:a b])'",
            "'to path! quote [''a b]', '#(path! [''a b])'",
            "'to path! quote [/a b]',  '#(path! [/a b])'",
            "'to path! quote [#a b]',  '#(path! [#a b])'",
            "'to path! quote [1 b]',   '#(path! [1 b])'",
        })
        @DisplayName("one item, or a first item that is not a plain word")
        void oneitemOrNotAPlainWord(String asked, String expected) {
            assertThat(answerTo("mold " + asked))
                    .as("IS_WORD is one datatype and not the typeset: a set-word, "
                            + "a get-word, a lit-word, a refinement and an issue "
                            + "are all any-word! and none of them may open a "
                            + "path, because a:/b reads back as a set-path and "
                            + "/a/b as a refinement")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0} molds with slashes")
        @CsvSource({
            "'to path! quote [a b]',    a/b",
            "'to path! quote [a 1]',    a/1",
            "'to path! quote [a b c]',  a/b/c",
        })
        @DisplayName("and anything after the first item may be whatever it likes")
        void anythingAfterTheFirstItem(String asked, String expected) {
            assertThat(answerTo("mold " + asked)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "make {0}! 4 molds as nothing")
        @ValueSource(strings = {"path", "set-path", "get-path", "lit-path"})
        @DisplayName("an empty path writes nothing at all, not even its own mark")
        void anemptyPathWritesNothing(String named) {
            assertThat(answerTo("mold make " + named + "! 4"))
                    .as("the line above the rule in the C returns before writing "
                            + "anything, so a set-path with room for four things "
                            + "does not write the colon that would read back as "
                            + "something else")
                    .isEqualTo("");
        }

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "'mold next quote a/b',        b",
            "'mold next quote a/b/c',      b/c",
            "'mold next next quote a/b/c', c",
            "'mold at quote a/b/c 3',      c",
            "'mold back tail quote a/b',   b",
        })
        @DisplayName("a path standing part way along is not a path of one")
        void apathStandingPartWayAlong(String asked, String expected) {
            assertThat(answerTo(asked))
                    .as("the length asked about is the whole series and the first "
                            + "item asked about is the one at the index, and using "
                            + "the remaining count for both made this a construct")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("and one standing at its tail writes nothing, emptied or built empty")
        void onestandingAtItsTailWritesNothing() {
            assertThat(answerTo("mold clear quote a/b")).isEqualTo("");
            assertThat(answerTo("mold tail quote a/b")).isEqualTo("");
        }

        @Test
        @DisplayName("and MOLD/ALL writes where it is standing as well as what it holds")
        void moldallWritesWhereItIsStanding() {
            assertThat(answerTo("mold/all next quote a/b"))
                    .isEqualTo("#(path! [a b] 2)");
        }
    }

    @Nested
    @DisplayName("a block of an object's fields keeps one to a line")
    class LiningUpAnObject {

        @ParameterizedTest(name = "to {0}! an object molds across lines")
        @CsvSource({
            "block,  '[^/    a: 1^/]'",
            "paren,  '(^/    a: 1^/)'",
            "hash,   'make hash! [^/    a: 1^/]'",
        })
        @DisplayName("because Make_Object_Block sets the line flag on every "
                + "set-word it writes")
        void becauseTheLineFlagIsSet(String named, String expected) {
            assertThat(answerTo("mold to " + named + "! make object! [a: 1]"))
                    .as("a property of the block rather than of how it is later "
                            + "printed, and the molder honours it for the three "
                            + "shapes that have brackets to put a break inside")
                    .isEqualTo(expected);
        }

        @Test
        @DisplayName("and a path has nowhere to put a break, so it stays on one line")
        void apathStaysOnOneLine() {
            assertThat(answerTo("mold to path! make object! [a: 1]"))
                    .isEqualTo("#(path! [a: 1])");
        }
    }

    @Nested
    @DisplayName("MAKE LOGIC! lets a zero be false where TO does not")
    class BuildingALogic {

        @ParameterizedTest(name = "make logic! {0} is false")
        @ValueSource(strings = {"0", "0.0", "0%", "$0", "none", "false"})
        @DisplayName("a number that is nothing counts as false to MAKE")
        void anumberThatIsNothingIsFalse(String source) {
            assertThat(answerTo("make logic! " + source))
                    .as("the C says why in as many words: TO falls in line with "
                            + "the rest of the interpreter where everything that "
                            + "is not none and not false is true, and MAKE takes "
                            + "more liberties with the meaning of its argument")
                    .isEqualTo("#(false)");
        }

        @ParameterizedTest(name = "to logic! {0} is true")
        @ValueSource(strings = {"0", "0.0", "0%", "$0"})
        @DisplayName("and the same number counts as true to TO")
        void thesameNumberIsTrueToConvert(String source) {
            assertThat(answerTo("to logic! " + source)).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("while none and false are false to both")
        void noneandFalseAreFalseToBoth() {
            assertThat(answerTo("mold reduce [to logic! none  to logic! false]"))
                    .isEqualTo("[#(false) #(false)]");
        }
    }

    @Nested
    @DisplayName("TO STRING! is FORM, except where it is not")
    class BuildingAString {

        @ParameterizedTest(name = "to string! {0} is {1}")
        @CsvSource({
            "<tag>,        tag",
            "%file,        file",
            "u@email,      u@email",
            "http://aa,    http://aa",
        })
        @DisplayName("an any-string is copied as it stands, so a tag comes out "
                + "without the brackets FORM keeps")
        void ananyStringIsCopiedAsItStands(String source, String expected) {
            assertThat(answerTo("to string! quote " + source)).isEqualTo(expected);
        }

        @Test
        @DisplayName("and FORM keeps them, which is the whole of the difference")
        void andformKeepsThem() {
            assertThat(answerTo("form <tag>")).isEqualTo("<tag>");
            assertThat(answerTo("to string! <tag>")).isEqualTo("tag");
        }

        @Test
        @DisplayName("so everything that runs through FORM keeps them too")
        void everythingThroughFormKeepsThem() {
            assertThat(answerTo("""
                    "<a>b3" == ajoin [<a> "b" 3]"""))
                    .as("one helper stood for both FORM and the arm TO STRING! "
                            + "uses, and taking the brackets off in the shared "
                            + "place broke AJOIN, AJOIN/with and COMBINE at once")
                    .isEqualTo("#(true)");
            assertThat(answerTo("""
                    "<a>/b/3" == ajoin/with [<a> "b" 3] #"/\"""")).isEqualTo("#(true)");
            assertThat(answerTo("""
                    mold combine [<span> "one" </span>]"""))
                    .isEqualTo("""
                            "<span>one</span>\"""");
        }

        @ParameterizedTest(name = "to string! {0} is {1}")
        @CsvSource({
            "'quote a/b',   a/b",
            "'quote a/b:',  a/b",
            "'quote :a/b',  a/b",
            "'quote ''a/b', a/b",
        })
        @DisplayName("a path keeps its slashes and loses its own mark")
        void apathKeepsItsSlashes(String source, String expected) {
            assertThat(answerTo("to string! " + source))
                    .as("running the segments together gave ab, because a path "
                            + "is a block underneath and the block arm ran first")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "to string! {0} is {1}")
        @CsvSource({
            "'quote a',     a",
            "'quote a:',    a",
            "'quote :a',    a",
            "'quote ''a',   a",
            "'quote /ref',  ref",
            "'quote #FF',   FF",
        })
        @DisplayName("and a word comes out bare, whatever marks it")
        void awordComesOutBare(String source, String expected) {
            assertThat(answerTo("to string! " + source)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an object writes one field to a line and nothing around them")
        void anobjectWritesOneFieldToALine() {
            assertThat(answerTo("to string! make object! [a: 1 b: 2]"))
                    .as("Form_Object emits N: V and a newline for each field, "
                            + "then takes the last newline off again. Showing the "
                            + "answer molds it a second time, so the break arrives "
                            + "written as ^/ rather than as itself")
                    .isEqualTo("a: 1^/b: 2");
            assertThat(answerTo("to string! make object! []")).isEqualTo("");
        }

        @Test
        @DisplayName("with the value molded even though the object is formed")
        void withthevalueMoldedNotFormed() {
            assertThat(answerTo("""
                    to string! make object! [a: "x"]"""))
                    .as("the quotes round the x survive, which is the part of "
                            + "Form_Object that cannot be guessed from its name")
                    .isEqualTo("""
                            a: "x\"""");
        }

        @Test
        @DisplayName("and a typeset names what it holds, with nothing around them")
        void atypesetNamesWhatItHolds() {
            assertThat(answerTo("to string! make typeset! [integer! percent!]"))
                    .isEqualTo("integer! percent!");
            assertThat(answerTo("to string! make typeset! []")).isEqualTo("");
        }

        @Test
        @DisplayName("which is what PRINT, REJOIN and AJOIN show as well")
        void whichIsWhatPrintAndRejoinShow() {
            assertThat(answerTo("""
                    rejoin ["x" make object! [a: 1]]""")).isEqualTo("xa: 1");
            assertThat(answerTo("ajoin [make typeset! [integer!]]"))
                    .isEqualTo("integer!");
        }

        @Test
        @DisplayName("but a block still runs its items together with nothing between")
        void ablockStillRunsItsItemsTogether() {
            assertThat(answerTo("to string! [1 2 3]")).isEqualTo("123");
            assertThat(answerTo("to string! [1 [2 3]]"))
                    .as("nesting makes no difference to the running together")
                    .isEqualTo("123");
        }
    }

    @Nested
    @DisplayName("a number that names no whole number overflows")
    class RefusingWhatWillNotFit {

        @ParameterizedTest(name = "to integer! {0} overflows")
        @ValueSource(strings = {
            "1.#NaN", "1.#INF", "-1.#INF", "1e300", "9.2233720368547758e18",
        })
        @DisplayName("a not-a-number overflows as surely as an endless one")
        void anotANumberOverflows(String source) {
            assertThat(errorOr("to integer! " + source))
                    .as("all three are refused before the cast rather than "
                            + "after it. Casting first saturates in silence, "
                            + "which made an infinity the largest whole number "
                            + "there is and a not-a-number nothing at all")
                    .isEqualTo("overflow");
        }

        @Test
        @DisplayName("and the two bounds are not a mirror image")
        void thetwoBoundsAreNotAMirrorImage() {
            assertThat(errorOr("to integer! 9.2233720368547758e18"))
                    .as("at the ceiling is out, so the most positive whole "
                            + "number does not convert")
                    .isEqualTo("overflow");
            assertThat(answerTo("to integer! -9.2233720368547758e18"))
                    .as("below the floor is out, so the most negative one does")
                    .isEqualTo("-9223372036854775808");
        }

        @Test
        @DisplayName("while an ordinary fraction still truncates toward zero")
        void anordinaryFractionStillTruncates() {
            assertThat(answerTo("mold reduce [to integer! 1.5  to integer! -1.5]"))
                    .isEqualTo("[1 -1]");
        }
    }

    @Nested
    @DisplayName("a code point that names no character is refused, not thrown")
    class RefusingANonCharacter {

        @ParameterizedTest(name = "to char! {0} is refused")
        @ValueSource(strings = {"-1", "55296", "56319", "57343", "1114112"})
        @DisplayName("the surrogates among them, which are reserved for writing "
                + "a large code point as a pair")
        void thesurrogatesAmongThem(String codepoint) {
            assertThat(errorOr("to char! " + codepoint))
                    .as("the value class knew this and said so by throwing, "
                            + "which left the interpreter as a Java exception "
                            + "and stopped it dead where a script should have "
                            + "caught an error")
                    .isEqualTo("invalid-char");
        }

        @ParameterizedTest(name = "to char! {0} is a character")
        @CsvSource({
            "55295,    '#\"^^(D7FF)\"'",
            "57344,    '#\"^^(E000)\"'",
            "1114111,  '#\"^^(10FFFF)\"'",
        })
        @DisplayName("and the code points either side of them are not")
        void thecodePointsEitherSide(String codepoint, String ignored) {
            assertThat(errorOr("to char! " + codepoint))
                    .as("the range is 2,048 wide and both of its edges hold")
                    .isNotEqualTo("invalid-char");
        }

        @Test
        @DisplayName("and one past the sixteen-bit mark is not a surrogate at all")
        void onepastTheSixteenBitMark() {
            assertThat(errorOr("to char! 120832"))
                    .as("narrowing to a char truncates, so 0x1D800 keeps only "
                            + "its low half and looks like a surrogate when it "
                            + "is an ordinary character well past them")
                    .isNotEqualTo("invalid-char");
        }
    }

    @Nested
    @DisplayName("an issue is a hexadecimal number to TO INTEGER! alone")
    class ReadingAnIssueAsHex {

        @ParameterizedTest(name = "to integer! {0} is {1}")
        @CsvSource({
            "#FF,                255",
            "#ff,                255",
            "#00FF,              255",
            "#0,                 0",
            "#1234,              4660",
            "#7FFFFFFFFFFFFFFF,  9223372036854775807",
            "#FFFFFFFFFFFFFFFF,  -1",
        })
        @DisplayName("sixteen digits fill the number and run past the top of it")
        void sixteenDigitsFillTheNumber(String issue, String expected) {
            assertThat(answerTo("to integer! " + issue))
                    .as("nothing in Rebol's own suite asks this, so it was a gap "
                            + "with no name until every conversion was run through "
                            + "both implementations and compared")
                    .isEqualTo(expected);
        }

        @ParameterizedTest(name = "to integer! {0} is refused")
        @ValueSource(strings = {
            "#FFFFFFFFFFFFFFFFFF", "#zz", "#FG", "#-1",
        })
        @DisplayName("and one digit too many is an error, not the first sixteen")
        void onedigitTooManyIsAnError(String issue) {
            assertThat(errorOr("to integer! " + issue))
                    .as("Scan_Hex fails when there are more characters than will "
                            + "fit, and fails on a character that is not a digit "
                            + "wherever it sits -- the minus in #-1 stops it before "
                            + "the one is reached")
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("MAKE reads it the same way, because the C shares the arm")
        void makereadsItTheSameWay() {
            assertThat(answerTo("make integer! #FF")).isEqualTo("255");
        }
    }
}
