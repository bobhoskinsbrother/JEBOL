package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Declared parameter types the C has and JEBOL lacked, or the other way
 * round: the /part range family ({@code Partial1} in f-stubs.c), /dup
 * counts ({@code Int32}), ENBASE and DEBASE limits, COMPOSE/INTO targets,
 * IN on a module, and TO-HEX on a char or tuple (n-strings.c).
 */
class SurfaceTypesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Nested
    @DisplayName("the /part range: number, same-series position, or refused")
    class ThePartRange {

        @Test
        @DisplayName("a pair is declared and refused at runtime, as Partial1 refuses it")
        void aPairIsDeclaredAndRefusedAtRuntime() {
            assertThat(errorIdOf("""
                    append/part copy [1] [2 3] 1x1""")).isEqualTo("invalid-part");
            assertThat(errorIdOf("""
                    find/part [1 2] 1 1x1""")).isEqualTo("invalid-part");
        }

        @Test
        @DisplayName("a percent is not a number to Partial1, and is refused the same way")
        void aPercentIsRefusedTheSameWay() {
            assertThat(errorIdOf("""
                    append/part copy [] [1 2] 50%""")).isEqualTo("invalid-part");
            assertThat(errorIdOf("""
                    change/part copy [1 2] 3 50%""")).isEqualTo("invalid-part");
        }

        @Test
        @DisplayName("a position into the same series still bounds the part")
        void aSameSeriesPositionStillBounds() {
            assertThat(answerTo("""
                    b: [1 2 3 4] append/part copy [] b next next b"""))
                    .isEqualTo("[1 2]");
        }

        @Test
        @DisplayName("a pair as a /dup count raises invalid-type, as Int32 does")
        void aPairAsADupCountRaisesInvalidType() {
            assertThat(errorIdOf("""
                    append/dup copy [] 1 1x2""")).isEqualTo("invalid-type");
        }

        @Test
        @DisplayName("a decimal /dup count is truncated, a percent refused")
        void aDecimalDupTruncatesAndAPercentIsRefused() {
            assertThat(answerTo("""
                    append/dup copy [] 1 2.9""")).isEqualTo("[1 1]");
            assertThat(errorIdOf("""
                    append/dup copy [] 1 50%""")).isEqualTo("invalid-type");
        }
    }

    @Nested
    @DisplayName("ENBASE and DEBASE bound their reading like /part")
    class TheBaseLimits {

        @Test
        @DisplayName("a block as the limit is refused as an argument")
        void aBlockAsTheLimitIsRefused() {
            assertThat(errorIdOf("""
                    enbase/part {abc} 64 [1]""")).isEqualTo("expect-arg");
            assertThat(errorIdOf("""
                    debase/part {MTI=} 64 [1]""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a string limit is declared, and refused unless it is the same series")
        void aStringLimitIsDeclaredAndBoundToTheSameSeries() {
            assertThat(errorIdOf("""
                    enbase/part {abc} 64 {xy}""")).isEqualTo("invalid-part");
        }

        @Test
        @DisplayName("a numeric limit bounds what is encoded")
        void aNumericLimitBoundsWhatIsEncoded() {
            assertThat(answerTo("""
                    (enbase/part {abc} 64 2) = enbase {ab} 64""")).isEqualTo("#(true)");
            assertThat(answerTo("""
                    s: {abc} (enbase/part s 64 next next s) = enbase {ab} 64"""))
                    .isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("COMPOSE/INTO fills any block-family target")
    void composeIntoFillsAnyBlockFamilyTarget() {
        assertThat(errorIdOf("""
                compose/into [(1 + 1)] make hash! []""")).isEqualTo("no-error");
        assertThat(errorIdOf("""
                compose/into [(1 + 1)] quote (a)""")).isEqualTo("no-error");
    }

    @Nested
    @DisplayName("IN reaches a module and refuses a paren")
    class InOnAModule {

        @Test
        @DisplayName("a module is a context IN can look into")
        void aModuleIsAContextInCanLookInto() {
            assertThat(errorIdOf("""
                    in make module! [[] []] 'a""")).isNotEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a paren is not a context, and R3 refuses it")
        void aParenIsRefused() {
            assertThat(errorIdOf("""
                    in quote (a) 'a""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("AT and SKIP count the way Get_Num_Arg counts")
    class AtAndSkip {

        @Test
        @DisplayName("a decimal offset truncates and a percent is its fraction")
        void aDecimalTruncates() {
            assertThat(answerTo("""
                    skip [1 2 3] 1.7""")).isEqualTo("[2 3]");
            assertThat(answerTo("""
                    skip [1 2 3] 50%""")).isEqualTo("[1 2 3]");
            assertThat(answerTo("""
                    at [1 2 3] 150%""")).isEqualTo("[1 2 3]");
        }

        @Test
        @DisplayName("true counts one and false counts two, as the C has it")
        void trueCountsOneAndFalseCountsTwo() {
            assertThat(answerTo("""
                    skip [1 2 3] true""")).isEqualTo("[2 3]");
            assertThat(answerTo("""
                    skip [1 2 3] false""")).isEqualTo("[3]");
            assertThat(answerTo("""
                    at [1 2 3] true""")).isEqualTo("[1 2 3]");
            assertThat(answerTo("""
                    at [1 2 3] false""")).isEqualTo("[2 3]");
        }

        @Test
        @DisplayName("a pair names no position in a flat series")
        void aPairNamesNoPositionInAFlatSeries() {
            assertThat(errorIdOf("""
                    skip [1 2] 1x1""")).isEqualTo("invalid-arg");
            assertThat(errorIdOf("""
                    at [1 2] 1x1""")).isEqualTo("invalid-arg");
        }
    }

    @Nested
    @DisplayName("READ, WRITE and RENAME declare the port-machinery types")
    class ThePortMachineryTypes {

        @Test
        @DisplayName("a url routes to its scheme, and this host has no such scheme")
        void aUrlRoutesToItsScheme() {
            assertThat(errorIdOf("""
                    read http://example.com""")).isEqualTo("no-service");
            assertThat(errorIdOf("""
                    write http://example.com {x}""")).isEqualTo("no-service");
            assertThat(errorIdOf("""
                    rename http://a http://b""")).isEqualTo("no-service");
        }

        @Test
        @DisplayName("a block is a port specification, routed the same way")
        void aBlockIsAPortSpecification() {
            assertThat(errorIdOf("""
                    read [scheme: 'http]""")).isEqualTo("no-service");
            assertThat(errorIdOf("""
                    write [scheme: 'http] {x}""")).isEqualTo("no-service");
        }

        @Test
        @DisplayName("a word names a scheme")
        void aWordNamesAScheme() {
            assertThat(errorIdOf("""
                    read 'nowhere""")).isEqualTo("no-service");
            assertThat(errorIdOf("""
                    write 'nowhere {x}""")).isEqualTo("no-service");
        }
    }

    @Nested
    @DisplayName("AS reads a series as a sibling type")
    class AsReadsASeries {

        @Test
        @DisplayName("an example value can stand for its own type")
        void anExampleValueStandsForItsType() {
            assertThat(answerTo("""
                    as %x {text}""")).isEqualTo("%text");
            assertThat(answerTo("""
                    mold as quote a/b [c d]""")).isEqualTo("\"c/d\"");
        }

        @Test
        @DisplayName("the answer shares the storage of what it was given")
        void theAnswerSharesStorage() {
            assertThat(answerTo("""
                    s: {ab} f: as file! s append s {c} f""")).isEqualTo("%abc");
        }

        @Test
        @DisplayName("a cross-class request stays refused")
        void aCrossClassRequestStaysRefused() {
            assertThat(errorIdOf("""
                    as block! {x}""")).isEqualTo("not-same-class");
        }

        @Test
        @DisplayName("a number is no type at all")
        void aNumberIsNoType() {
            assertThat(errorIdOf("""
                    as 5 {x}""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("QUERY reaches a date and the schemes")
    class QueryTargets {

        @Test
        @DisplayName("a word field answers one part of a date")
        void aWordFieldAnswersOnePart() {
            assertThat(answerTo("""
                    query 1-Jan-2000 'year""")).isEqualTo("2000");
            assertThat(answerTo("""
                    query 1-Jan-2000 'time""")).isEqualTo("_");
        }

        @Test
        @DisplayName("a block field answers parts, labeled or bare per word")
        void aBlockFieldAnswersParts() {
            assertThat(answerTo("""
                    query 1-Jan-2000 [year: :month]""")).isEqualTo("[year: 2000 1]");
        }

        @Test
        @DisplayName("a none field answers the names of the parts")
        void aNoneFieldAnswersTheNames() {
            assertThat(answerTo("""
                    mold query 1-Jan-2000 none"""))
                    .startsWith("{[year month day time date zone");
        }

        @Test
        @DisplayName("a datatype field answers the whole object")
        void aDatatypeFieldAnswersTheObject() {
            assertThat(answerTo("""
                    o: query 1-Jan-2000 date! reduce [o/year o/month o/day]"""))
                    .isEqualTo("[2000 1 1]");
        }

        @Test
        @DisplayName("a part no date has is refused")
        void aPartNoDateHasIsRefused() {
            assertThat(errorIdOf("""
                    query 1-Jan-2000 'nonsense""")).isEqualTo("cannot-use");
        }

        @Test
        @DisplayName("a url, a word and a port route to schemes this host has not got")
        void theSchemeTargetsAreRefused() {
            assertThat(errorIdOf("""
                    query http://example.com none""")).isEqualTo("no-service");
            assertThat(errorIdOf("""
                    query 'nowhere none""")).isEqualTo("no-service");
        }
    }

    @Test
    @DisplayName("QUERY's field takes a datatype and no longer a literal get-word")
    void queryFieldTakesADatatype() {
        assertThat(errorIdOf("""
                query %a.txt file!""")).isEqualTo("no-service");
        assertThat(errorIdOf("""
                query %a.txt 5""")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("CALL takes any text-like command, as ANY_STR does")
    void callTakesAnyTextLikeCommand() {
        assertThat(errorIdOf("""
                call http://example.com""")).isEqualTo("no-service");
        assertThat(errorIdOf("""
                call @runnable""")).isEqualTo("no-service");
        assertThat(errorIdOf("""
                call a@b""")).isEqualTo("no-service");
    }

    @Test
    @DisplayName("POKE writes a pixel of an image")
    void pokeWritesAPixelOfAnImage() {
        assertThat(answerTo("""
                img: make image! 2x1 poke img 1 9.8.7.6 img/1"""))
                .isEqualTo("9.8.7.6");
    }

    @Test
    @DisplayName("COMPLEMENT on an image answers a new image, every channel flipped")
    void complementOnAnImageFlipsEveryChannel() {
        assertThat(answerTo("""
                img: make image! 1x1 img/1: 0.0.0.255 first complement img"""))
                .isEqualTo("255.255.255.0");
        assertThat(answerTo("""
                img: make image! 1x1 img/1: 1.2.3.255
                (complement complement img) = img""")).isEqualTo("#(true)");
    }

    @Nested
    @DisplayName("TAIL? declares the six kinds the C serves")
    class TailQuestion {

        @Test
        @DisplayName("none, an object and an issue are refused")
        void noneAnObjectAndAnIssueAreRefused() {
            assertThat(errorIdOf("""
                    tail? none""")).isEqualTo("expect-arg");
            assertThat(errorIdOf("""
                    tail? make object! []""")).isEqualTo("expect-arg");
            assertThat(errorIdOf("""
                    tail? #ab""")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("EMPTY? keeps its wider spec, none included")
        void emptyKeepsItsWiderSpec() {
            assertThat(answerTo("""
                    empty? none""")).isEqualTo("#(true)");
            assertThat(answerTo("""
                    empty? []""")).isEqualTo("#(true)");
        }
    }

    @Test
    @DisplayName("UNIQUE takes the five kinds a set can be made of")
    void uniqueTakesTheFiveSetKinds() {
        assertThat(errorIdOf("""
                unique %ab""")).isEqualTo("expect-arg");
        assertThat(errorIdOf("""
                unique <a>""")).isEqualTo("expect-arg");
        assertThat(answerTo("""
                unique {aab}""")).isEqualTo("\"ab\"");
        assertThat(answerTo("""
                unique [1 1 2]""")).isEqualTo("[1 2]");
    }

    @Nested
    @DisplayName("TO-HEX takes a char and a tuple, as the C declares")
    class ToHexArms {

        @Test
        @DisplayName("a char becomes its codepoint, two digits per byte of width")
        void aCharBecomesItsCodepointHex() {
            assertThat(answerTo("""
                    to-hex #"a\"""")).isEqualTo("#61");
            assertThat(answerTo("""
                    to-hex #"^(2603)\"""")).isEqualTo("#2603");
        }

        @Test
        @DisplayName("a tuple becomes its bytes, padded to three segments")
        void aTupleBecomesItsBytes() {
            assertThat(answerTo("""
                    to-hex 1.2.3""")).isEqualTo("#010203");
            assertThat(answerTo("""
                    to-hex 1.2.3.4""")).isEqualTo("#01020304");
        }

        @Test
        @DisplayName("/size widens a char's answer")
        void sizeWidensAChar() {
            assertThat(answerTo("""
                    to-hex/size #"a" 4""")).isEqualTo("#0061");
        }

        @Test
        @DisplayName("the answer is an issue, as it is for an integer")
        void theAnswerIsAnIssue() {
            assertThat(answerTo("""
                    (type? to-hex #"a") = issue!""")).isEqualTo("#(true)");
        }
    }
}
