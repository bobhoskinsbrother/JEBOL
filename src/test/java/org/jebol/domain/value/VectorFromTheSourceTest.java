package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * VECTOR!, the last datatype R3 has and JEBOL had not.
 *
 * <p>A vector holds numbers of one fixed machine width rather than REBOL
 * values, which is the whole point of it: ten thousand bytes are ten thousand
 * bytes and not ten thousand boxed integers. The width is part of the value,
 * so a number put into an {@code int8!} vector comes back wrapped to eight
 * bits, and that wrapping is the behaviour rather than an overflow to be
 * guarded against.
 *
 * <p>Read from {@code t-vector.c}, the ten encodings in {@code sys-value.h},
 * and Rebol's own {@code vector-test.r3}. Where the vendored source and the
 * 3.22.1 binary disagree the source wins, and they disagree in one place:
 * asking an empty vector for its sum answers zero on the binary and none in
 * the C, and the vendored tests agree with the C.
 *
 * <p>Every expectation not taken from Rebol's own test file was checked
 * against {@code ./r3} before it was written down. Three of them were wrong
 * first time.
 */
class VectorFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /**
     * An answer that is a string, without the delimiters molding puts round it.
     *
     * <p>Which pair those are depends on the text: one holding a newline molds
     * inside braces rather than quotes, which is exactly the case a vector of
     * more than ten numbers produces.
     */
    private static String theTextOf(String source) {
        String molded = answerTo(source);
        boolean inBraces = molded.startsWith("{") && molded.endsWith("}");
        return inBraces
                ? molded.substring(1, molded.length() - 1)
                : molded.replace("\"", "");
    }

    private static String moldOf(String source) {
        return theTextOf("mold " + source);
    }

    private static String whatHappensTo(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] ['accepted]");
    }

    @Nested
    @DisplayName("the ten encodings, each named several ways")
    class TheEncodings {

        @ParameterizedTest
        @CsvSource({
                "i8!,       int8!",
                "i16!,      int16!",
                "i32!,      int32!",
                "i64!,      int64!",
                "u8!,       uint8!",
                "u16!,      uint16!",
                "u32!,      uint32!",
                "u64!,      uint64!",
                "f32!,      float32!",
                "f64!,      float64!",
                "int8!,     int8!",
                "int16!,    int16!",
                "int32!,    int32!",
                "int64!,    int64!",
                "uint8!,    uint8!",
                "uint16!,   uint16!",
                "uint32!,   uint32!",
                "uint64!,   uint64!",
                "float32!,  float32!",
                "float64!,  float64!",
                "byte!,     uint8!",
                "float!,    float32!",
                "single!,   float32!",
                "double!,   float64!",
        })
        @DisplayName("every alias makes a vector that molds under its settled name")
        void everyAliasIsAccepted(String written, String settled) {
            assertThat(moldOf("make vector! [" + written + "]"))
                    .isEqualTo("#(" + settled + " [])");
        }

        @ParameterizedTest
        @ValueSource(strings = {"half!", "f8!", "f16!", "float8!", "float16!"})
        @DisplayName("but the two widths of float the C names and does not implement are refused")
        void theUnimplementedFloatWidthsAreRefused(String written) {
            assertThat(whatHappensTo("make vector! [" + written + "]"))
                    .as("sys-value.h declares VTSF08 and VTSF16 and marks both "
                            + "\"not used\", and Get_Vector_Spec_From_Symbol has no "
                            + "case for either")
                    .isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("making one")
    class Making {

        @Test
        @DisplayName("from a count, which is a signed 32-bit vector of zeros")
        void fromACount() {
            assertThat(moldOf("make vector! 0")).isEqualTo("#(int32! [])");
            assertThat(moldOf("make vector! 3")).isEqualTo("#(int32! [0 0 0])");
        }

        @Test
        @DisplayName("and a fractional count is truncated rather than refused")
        void afractionalCountIsTruncated() {
            assertThat(moldOf("make vector! 1.5")).isEqualTo("#(int32! [0])");
        }

        @Test
        @DisplayName("but a negative count is out of range")
        void anegativeCountIsRefused() {
            assertThat(whatHappensTo("make vector! -1")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("from an empty block, which is the same as from zero")
        void fromAnEmptyBlock() {
            assertThat(moldOf("make vector! []")).isEqualTo("#(int32! [])");
        }

        @Test
        @DisplayName("from bare numbers, which choose the widest of their kind")
        void fromBareNumbers() {
            assertThat(moldOf("make vector! [1 2 3]")).isEqualTo("#(int64! [1 2 3])");
            assertThat(moldOf("make vector! [1.0 2]"))
                    .as("one decimal among them makes the whole vector decimal")
                    .isEqualTo("#(float64! [1.0 2.0])");
        }

        @Test
        @DisplayName("from a name and a count")
        void fromANameAndACount() {
            assertThat(moldOf("make vector! [i8! 3]")).isEqualTo("#(int8! [0 0 0])");
            assertThat(moldOf("make vector! [f32! 3]"))
                    .isEqualTo("#(float32! [0.0 0.0 0.0])");
        }

        @Test
        @DisplayName("from a name and a block of values")
        void fromANameAndABlock() {
            assertThat(moldOf("make vector! [u8! [1 2]]")).isEqualTo("#(uint8! [1 2])");
            assertThat(moldOf("make vector! [f32! [1 2]]"))
                    .as("a whole number in a decimal vector becomes a decimal")
                    .isEqualTo("#(float32! [1.0 2.0])");
        }

        @Test
        @DisplayName("from a name, a block and a starting position")
        void fromANameABlockAndAnIndex() {
            assertThat(moldOf("make vector! [i8! [1 2] 2]")).isEqualTo("#(int8! [2])");
            assertThat(answerTo("index? make vector! [i8! [1 2] 2]")).isEqualTo("2");
        }

        @Test
        @DisplayName("from the older spelling, which names the kind and the width apart")
        void fromTheOlderSpelling() {
            assertThat(moldOf("make vector! [integer! 8 [1 2 3]]"))
                    .isEqualTo("#(int8! [1 2 3])");
            assertThat(moldOf("make vector! [signed integer! 32 2]"))
                    .isEqualTo("#(int32! [0 0])");
            assertThat(moldOf("make vector! [unsigned integer! 32 2]"))
                    .isEqualTo("#(uint32! [0 0])");
            assertThat(moldOf("make vector! [decimal! 32 [1.0]]"))
                    .isEqualTo("#(float32! [1.0])");
        }

        @Test
        @DisplayName("and an unsigned decimal is refused, because there is no such thing")
        void anUnsignedDecimalIsRefused() {
            assertThat(whatHappensTo("make vector! [unsigned decimal! 32]"))
                    .isEqualTo("bad-make-arg");
        }

        @ParameterizedTest
        @ValueSource(strings = {"- integer! 32", "- decimal! 32", "integer! 12",
                "decimal! 8", "decimal! 16", "integer! 0", "string! 32"})
        @DisplayName("a width the C does not allow is refused")
        void abadWidthIsRefused(String written) {
            assertThat(whatHappensTo("make vector! [" + written + "]"))
                    .as("the C allows 32 and 64 for both kinds and 8 and 16 for "
                            + "integers only")
                    .isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("a count beside a block crops the block")
        void acountBesideABlockCrops() {
            assertThat(answerTo("length? make vector! [integer! 16 2 [1 2 3 4]]"))
                    .isEqualTo("2");
            assertThat(moldOf("make vector! [integer! 16 2 [1 2 3 4]]"))
                    .isEqualTo("#(int16! [1 2])");
        }

        @Test
        @DisplayName("and a longer count pads it with zeros")
        void alongerCountPads() {
            assertThat(moldOf("make vector! [integer! 16 4 [1 2]]"))
                    .isEqualTo("#(int16! [1 2 0 0])");
        }

        @Test
        @DisplayName("from binary, which is read as the vector's own width")
        void fromBinary() {
            assertThat(moldOf("make vector! [integer! 16 #{010002000300}]"))
                    .isEqualTo("#(int16! [1 2 3])");
        }

        @Test
        @DisplayName("from characters, which are their code points")
        void fromCharacters() {
            assertThat(answerTo("""
                    v: make vector! [integer! 8 [#"^(00)" #"a"]]
                    reduce [v/1 v/2]""")).isEqualTo("[0 97]");
        }

        @Test
        @DisplayName("and the size, the data and the index may all come from get-words")
        void fromGetWords() {
            assertThat(moldOf("data: [1 2 3 4] make vector! [uint8! :data]"))
                    .isEqualTo("#(uint8! [1 2 3 4])");
            assertThat(moldOf("data: [1 2 3 4] size: 2 make vector! [uint8! :size :data]"))
                    .isEqualTo("#(uint8! [1 2])");
            assertThat(moldOf("data: [1 2 3 4] index: 3 make vector! [uint8! :data :index]"))
                    .isEqualTo("#(uint8! [3 4])");
        }
    }

    @Nested
    @DisplayName("TO another datatype, and from one")
    class Converting {

        @Test
        @DisplayName("TO VECTOR! of a binary reads it as unsigned octets")
        void tovectorOfABinary() {
            assertThat(moldOf("to vector! #{01FF}")).isEqualTo("#(uint8! [1 255])");
            assertThat(moldOf("to vector! #{}")).isEqualTo("#(uint8! [])");
        }

        @Test
        @DisplayName("TO VECTOR! of a block is the widest signed integer")
        void tovectorOfABlock() {
            assertThat(moldOf("to vector! [1 2]")).isEqualTo("#(int64! [1 2])");
        }

        @Test
        @DisplayName("TO BLOCK! gives the numbers back as REBOL values")
        void toblock() {
            assertThat(moldOf("to block! #(u16! [1 2])")).isEqualTo("[1 2]");
            assertThat(moldOf("to block! make vector! [integer! 32 2]")).isEqualTo("[0 0]");
            assertThat(moldOf("to block! make vector! 0")).isEqualTo("[]");
        }

        @ParameterizedTest
        @CsvSource({
                "'to binary! #(u16! [1 2])',   '#{01000200}'",
                "'to binary! #(i32! [1 2])',   '#{0100000002000000}'",
                "'to binary! #(f32! [1 2])',   '#{0000803F00000040}'",
                "'to binary! #(i64! [1 2])',   '#{01000000000000000200000000000000}'",
                "'to binary! #(f64! [1 2])',   '#{000000000000F03F0000000000000040}'",
                "'to binary! #(i8! [1 2])',    '#{0102}'",
        })
        @DisplayName("TO BINARY! gives the stored bytes, least significant first")
        void tobinary(String written, String expected) {
            assertThat(answerTo(written)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "'to binary! next #(u16! [1 2])',  '#{0200}'",
                "'to binary! next #(i32! [1 2])',  '#{02000000}'",
                "'to binary! next #(f32! [1 2])',  '#{00000040}'",
                "'to binary! next #(i64! [1 2])',  '#{0200000000000000}'",
                "'to binary! next #(f64! [1 2])',  '#{0000000000000040}'",
        })
        @DisplayName("and only from the current position onwards")
        void tobinaryFromTheIndex(String written, String expected) {
            assertThat(answerTo(written)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("a number that will not fit is wrapped, not refused")
    class Wrapping {

        @ParameterizedTest
        @CsvSource({
                "'#(i8! [200])',    '#(int8! [-56])'",
                "'#(i8! [-129])',   '#(int8! [127])'",
                "'#(i8! [127])',    '#(int8! [127])'",
                "'#(i8! [-128])',   '#(int8! [-128])'",
                "'#(u8! [300])',    '#(uint8! [44])'",
                "'#(u8! [-1])',     '#(uint8! [255])'",
                "'#(u8! [255])',    '#(uint8! [255])'",
                "'#(u8! [256])',    '#(uint8! [0])'",
                "'#(i16! [32768])', '#(int16! [-32768])'",
                "'#(u16! [65536])', '#(uint16! [0])'",
        })
        @DisplayName("at every boundary and one step past it")
        void atTheBoundaries(String written, String expected) {
            assertThat(moldOf(written)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an unsigned 64-bit value reads back as REBOL's signed integer")
        void thewidestUnsignedReadsBackSigned() {
            assertThat(moldOf("#(u64! [-1])"))
                    .as("REBOL's integer! is 64 bits and signed, so the largest "
                            + "uint64 has nowhere else to go; the C stores the bits "
                            + "and reads them back as they are")
                    .isEqualTo("#(uint64! [-1])");
            assertThat(answerTo("to binary! #(u64! [-1])"))
                    .as("and the bits really are all ones, which is what makes it "
                            + "the largest rather than the smallest")
                    .isEqualTo("#{FFFFFFFFFFFFFFFF}");
        }

        @Test
        @DisplayName("but the widest unsigned 32-bit value fits and stays positive")
        void thewidestUnsigned32Fits() {
            assertThat(answerTo("first #(u32! [4294967295])")).isEqualTo("4294967295");
        }

        @Test
        @DisplayName("and a 32-bit decimal keeps only the precision it has room for")
        void thenarrowerDecimalLosesPrecision() {
            assertThat(answerTo("first #(f32! [0.1])"))
                    .as("0.1 is not a float and not a double either, and the two "
                            + "disagree from the eighth digit")
                    .isEqualTo("0.100000001490116");
        }
    }

    @Nested
    @DisplayName("molding and forming")
    class Molding {

        @Test
        @DisplayName("a vector molds as the construction syntax that would rebuild it")
        void moldingIsConstruction() {
            assertThat(moldOf("#(i8! [1 2])")).isEqualTo("#(int8! [1 2])");
            assertThat(moldOf("#(f64! [1 2])")).isEqualTo("#(float64! [1.0 2.0])");
        }

        @Test
        @DisplayName("and forming gives the numbers alone")
        void formingIsTheNumbersAlone() {
            assertThat(theTextOf("form #(i8! [1 2 3])")).isEqualTo("1 2 3");
        }

        @Test
        @DisplayName("molding from a position shows only what is left")
        void moldingFromAPosition() {
            assertThat(moldOf("skip #(i32! [10 20 30]) 2")).isEqualTo("#(int32! [30])");
        }

        @Test
        @DisplayName("but MOLD/ALL shows the whole series and says where the position is")
        void moldAllShowsTheWholeSeries() {
            assertThat(theTextOf("mold/all skip #(i32! [10 20 30]) 2"))
                    .isEqualTo("#(int32! [10 20 30] 3)");
            assertThat(theTextOf("mold/all #(i32! [10 20 30])"))
                    .as("and says nothing when the position is the head")
                    .isEqualTo("#(int32! [10 20 30])");
        }

        @Test
        @DisplayName("more than ten numbers are broken across lines, ten to a line")
        void alongVectorIsBrokenAcrossLines() {
            assertThat(moldOf("make vector! [i8! 11]"))
                    .isEqualTo("""
                            #(int8! [
                                0 0 0 0 0 0 0 0 0 0
                                0
                            ])""");
        }

        @Test
        @DisplayName("and MOLD/FLAT keeps it on one line however long it is")
        void moldFlatStaysOnOneLine() {
            assertThat(theTextOf("mold/flat make vector! [i8! 12]"))
                    .isEqualTo("#(int8! [0 0 0 0 0 0 0 0 0 0 0 0])");
        }

        @Test
        @DisplayName("what molds can be loaded back, and is the same vector")
        void moldingRoundTrips() {
            assertThat(answerTo("v: #(u16! [1 2]) v = load mold/all v")).isEqualTo("#(true)");
            assertThat(answerTo(
                    "2 = index? load mold/all next make vector! [integer! 32 4 [1 2 3 4]]"))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("reading one")
    class Reading {

        @Test
        @DisplayName("a vector is a series and is not a block")
        void avectorIsASeries() {
            assertThat(answerTo("series? #(i8! [1])")).isEqualTo("#(true)");
            assertThat(answerTo("any-block? #(i8! [1])")).isEqualTo("#(false)");
            assertThat(answerTo("vector? #(i8! [1])")).isEqualTo("#(true)");
            assertThat(moldOf("type? #(i8! [1])"))
                    .isEqualTo("#(vector!)");
        }

        @Test
        @DisplayName("FIRST, LAST and a numbered path all read an element")
        void readingAnElement() {
            assertThat(answerTo("v: #(i8! [1 2 3]) first v")).isEqualTo("1");
            assertThat(answerTo("v: #(i8! [1 2 3]) last v")).isEqualTo("3");
            assertThat(answerTo("v: #(i8! [1 2 3]) v/3")).isEqualTo("3");
        }

        @Test
        @DisplayName("LENGTH? and INDEX? count from the position, not the head")
        void lengthAndIndex() {
            assertThat(answerTo("length? #(i8! [1 2 3])")).isEqualTo("3");
            assertThat(answerTo("length? next #(i8! [1 2 3])")).isEqualTo("2");
            assertThat(answerTo("index? next #(i8! [1 2 3])")).isEqualTo("2");
        }

        @Test
        @DisplayName("HEAD?, TAIL? and EMPTY? answer about the position")
        void thepositionQuestions() {
            assertThat(answerTo("head? head #(u8! [1 2 3])")).isEqualTo("#(true)");
            assertThat(answerTo("tail? tail #(u8! [1 2 3])")).isEqualTo("#(true)");
            assertThat(answerTo("empty? #(i8! [])")).isEqualTo("#(true)");
            assertThat(answerTo("empty? tail #(i8! [1])")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("PICK answers none outside the vector rather than failing")
        void pickOutsideAnswersNone() {
            assertThat(answerTo("v: #(u32! [1 2 3]) pick v 1")).isEqualTo("1");
            assertThat(theTextOf("v: #(u32! [1 2 3]) mold pick v 0")).isEqualTo("_");
            assertThat(theTextOf("v: #(u32! [1 2 3]) mold pick v -1")).isEqualTo("_");
            assertThat(theTextOf("v: #(u32! [1 2 3]) mold pick v 10")).isEqualTo("_");
        }

        @Test
        @DisplayName("and a negative PICK counts back from the position, not the tail")
        void anegativePickCountsBackFromHere() {
            assertThat(answerTo("v: #(i32! [10 20 30 40 50]) pick skip v 2 -1"))
                    .as("n-io.c's PD_Vector adds one to a negative selector so that "
                            + "both signs share the index + n - 1 formula")
                    .isEqualTo("20");
        }

        @Test
        @DisplayName("an empty vector is still true, the way every series is")
        void anemptyVectorIsTrue() {
            assertThat(answerTo("either #(i8! []) ['yes] ['no]")).isEqualTo("yes");
        }
    }

    @Nested
    @DisplayName("writing to one")
    class Writing {

        @Test
        @DisplayName("POKE and a numbered set-path both write an element")
        void writingAnElement() {
            assertThat(answerTo("v: #(u32! [1 2 3]) poke v 1 10 v/1")).isEqualTo("10");
            assertThat(answerTo("v: #(u32! [1 2 3]) v/1: 9 v/1")).isEqualTo("9");
        }

        @Test
        @DisplayName("and a decimal vector takes a decimal")
        void adecimalVectorTakesADecimal() {
            assertThat(answerTo("v: make vector! [decimal! 32 3] poke v 1 1.0 v/1"))
                    .isEqualTo("1.0");
        }

        @Test
        @DisplayName("but POKE outside the vector is out of range, where PICK was none")
        void pokeOutsideIsAnError() {
            assertThat(whatHappensTo("poke #(u32! [1 2 3]) 10 1")).isEqualTo("out-of-range");
            assertThat(whatHappensTo("poke #(u32! [1 2 3]) 0 1"))
                    .as("PD_Vector answers PE_NONE for a zero selector when reading "
                            + "and PE_BAD_RANGE when writing, which is the one place "
                            + "the two directions differ")
                    .isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("a value that is not a number is refused")
        void anonNumberIsRefused() {
            assertThat(whatHappensTo("poke #(i8! [1 2]) 1 {x}")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and protected storage refuses the write")
        void protectedStorageRefuses() {
            assertThat(whatHappensTo("poke protect #(i8! [1 2]) 1 9")).isEqualTo("protected");
        }

        @Test
        @DisplayName("two names for one vector see each other's writes")
        void thestorageIsShared() {
            assertThat(answerTo("v1: #(u16! [1 2]) v2: v1 v2/1: 3 v1/1")).isEqualTo("3");
            assertThat(answerTo("v1: #(u16! [1 2]) v3: copy v1 v1/1: 3 v3/1"))
                    .as("but a copy is its own storage")
                    .isEqualTo("1");
            assertThat(answerTo("v1: #(u16! [1 2]) same? v1 copy v1")).isEqualTo("#(false)");
        }
    }
}
