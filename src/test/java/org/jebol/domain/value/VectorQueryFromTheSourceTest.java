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
 * What a vector will tell you about itself.
 *
 * <p>Four ways in and one answer behind them: a path, REFLECT, QUERY and the
 * mezzanine words built on QUERY all end at {@code Query_Vector_Field}. The
 * fields divide in two. Four describe the vector -- whether it is signed, what
 * its elements are, how wide they are, how many there are -- and the rest are
 * statistics of the numbers it holds.
 *
 * <p>All of them read the whole storage rather than what is left from where
 * the value points, because the C's loops start at {@code SERIES_DATA} and run
 * to {@code SERIES_TAIL}. LENGTH? is the exception and disagrees with
 * {@code v/length} for exactly that reason: LENGTH? never reaches the vector's
 * own arm.
 *
 * <p>An empty vector answers none to every statistic. The 3.22.1 binary
 * answers zero, and the vendored source and its tests both say none; the C
 * wins, and this is the one place in the vector work where they differ.
 */
class VectorQueryFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String moldOf(String source) {
        return answerTo("mold " + source).replace("\"", "");
    }

    private static String whatHappensTo(String source) {
        return answerTo("either error? e: try [" + source + "] [e/id] ['accepted]");
    }

    /**
     * One field of a vector, read through a path.
     *
     * <p>The vector is given a name first because a path written straight onto
     * a construction literal does not read as a path: {@code #(u8! [1])/size}
     * lexes as two values, the vector and the refinement {@code /size}.
     */
    private static String fieldOf(String vector, String field) {
        return answerTo("v: " + vector + " v/" + field);
    }

    private static String moldedFieldOf(String vector, String field) {
        return answerTo("v: " + vector + " mold v/" + field).replace("\"", "");
    }

    @Nested
    @DisplayName("the four that describe the vector rather than its numbers")
    class TheShape {

        @ParameterizedTest
        @CsvSource({
                "'#(u16! [1 2])',  signed,  '#(false)'",
                "'#(i16! [1 2])',  signed,  '#(true)'",
                "'#(u16! [1 2])',  size,    16",
                "'#(f64! [1 2])',  size,    64",
                "'#(u16! [1 2])',  length,  2",
                "'#(u16! [])',     length,  0",
        })
        @DisplayName("read through a path")
        void throughApath(String vector, String field, String expected) {
            assertThat(fieldOf(vector, field)).isEqualTo(expected);
        }

        @Test
        @DisplayName("and a measuring vector calls itself signed, having no sign bit")
        void ameasuringVectorIsSigned() {
            assertThat(fieldOf("#(f32! [1])", "signed"))
                    .as("the C asks whether the kind is one of the four unsigned "
                            + "integers, so everything else answers true")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("the element type is a datatype word, not the vector's own kind")
        void theelementType() {
            assertThat(fieldOf("#(u16! [1 2])", "type")).isEqualTo("integer!");
            assertThat(fieldOf("#(f32! [1 2])", "type")).isEqualTo("decimal!");
        }

        @Test
        @DisplayName("and the length counts the whole storage where LENGTH? counts from here")
        void lengthAgainstLengthOf() {
            assertThat(answerTo("w: next #(u16! [1 2 3]) w/length"))
                    .as("Query_Vector_Field reads vect->tail, which is the whole of it")
                    .isEqualTo("3");
            assertThat(answerTo("length? next #(u16! [1 2 3])"))
                    .as("but LENGTH? is answered by the generic series action before "
                            + "the vector's own arm is reached")
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("a name that is no field at all is an invalid path")
        void anunknownField() {
            assertThat(whatHappensTo("v: #(u16! [1 2]) v/nonsense"))
                    .isEqualTo("invalid-path");
        }
    }

    @Nested
    @DisplayName("the smallest and the largest")
    class TheExtremes {

        @ParameterizedTest
        @ValueSource(strings = {"i8!", "i16!", "i32!", "i64!", "f32!", "f64!"})
        @DisplayName("a signed vector finds a negative one")
        void thesignedKinds(String kind) {
            String makes = "v: #(" + kind + " [1 -2 0]) ";
            boolean measuring = kind.startsWith("f");
            assertThat(answerTo(makes + "v/min")).isEqualTo(measuring ? "-2.0" : "-2");
            assertThat(answerTo(makes + "v/max")).isEqualTo(measuring ? "1.0" : "1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"u8!", "u16!", "u32!", "u64!"})
        @DisplayName("and an unsigned one never does")
        void theunsignedKinds(String kind) {
            String makes = "v: #(" + kind + " [1 2 0]) ";
            assertThat(answerTo(makes + "v/min")).isEqualTo("0");
            assertThat(answerTo(makes + "v/max")).isEqualTo("2");
        }

        @Test
        @DisplayName("MINIMUM and MAXIMUM are the same fields spelt out")
        void thelongerSpellings() {
            assertThat(answerTo("v: #(i8! [1 -2 0]) v/minimum")).isEqualTo("-2");
            assertThat(answerTo("v: #(i8! [1 -2 0]) v/maximum")).isEqualTo("1");
        }

        @Test
        @DisplayName("and the widest unsigned kind is ordered as unsigned")
        void thewidestUnsignedIsOrderedUnsigned() {
            assertThat(answerTo("v: #(u64! [1 -1]) v/max"))
                    .as("Find_Maximum_Of_Vector compares through a u64 pointer, so "
                            + "the value whose bits are all ones is the largest "
                            + "rather than the smallest")
                    .isEqualTo("-1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"i8!", "u64!", "f32!"})
        @DisplayName("an empty vector has neither")
        void anemptyVectorHasNeither(String kind) {
            assertThat(moldedFieldOf("#(" + kind + " [])", "min")).isEqualTo("_");
            assertThat(moldedFieldOf("#(" + kind + " [])", "max")).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("the statistics")
    class TheStatistics {

        private static final String WHOLE_NUMBERS = "v: #(int8! [-2 -1 1 2 4]) ";

        @ParameterizedTest
        @CsvSource({
                "minimum,               -2",
                "maximum,               4",
                "range,                 6",
                "sum,                   4",
                "mean,                  0.8",
                "median,                1.0",
                "variance,              4.56",
                "sample-variance,       5.7",
        })
        @DisplayName("over a counting vector, quoted from Rebol's own test file")
        void overACountingVector(String field, String expected) {
            assertThat(answerTo(WHOLE_NUMBERS + "v/" + field)).isEqualTo(expected);
        }

        @Test
        @DisplayName("and the two deviations, which are the roots of the two variances")
        void thetwoDeviations() {
            assertThat(answerTo(WHOLE_NUMBERS + "v/population-deviation"))
                    .isEqualTo("2.13541565040626");
            assertThat(answerTo(WHOLE_NUMBERS + "v/sample-deviation"))
                    .isEqualTo("2.38746727726266");
        }

        @Test
        @DisplayName("a sum and a range come back as whole numbers, a mean never does")
        void thecountingOnesStayWhole() {
            assertThat(answerTo(WHOLE_NUMBERS + "integer? v/sum")).isEqualTo("#(true)");
            assertThat(answerTo(WHOLE_NUMBERS + "integer? v/range")).isEqualTo("#(true)");
            assertThat(answerTo(WHOLE_NUMBERS + "decimal? v/mean"))
                    .as("the C turns the answer back into an integer at its "
                            + "return_number label, and only the sum and the range "
                            + "go through it")
                    .isEqualTo("#(true)");
            assertThat(answerTo(WHOLE_NUMBERS + "decimal? v/median")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("over a measuring vector they stay decimals throughout")
        void overAmeasuringVector() {
            String makes = "v: #(float64! [1.62 1.72 1.64 1.7 1.78 1.64 1.65 1.64 1.66 1.74]) ";
            assertThat(answerTo(makes + "v/minimum")).isEqualTo("1.62");
            assertThat(answerTo(makes + "v/maximum")).isEqualTo("1.78");
            assertThat(answerTo(makes + "v/sum")).isEqualTo("16.79");
            assertThat(answerTo(makes + "v/mean")).isEqualTo("1.679");
            assertThat(answerTo(makes + "v/median")).isEqualTo("1.655");
        }

        @Test
        @DisplayName("a median averages the middle pair when there is no middle one")
        void themedianOfAnEvenCount() {
            assertThat(answerTo("v: #(u8! [1 2 3 4]) v/median")).isEqualTo("2.5");
            assertThat(answerTo("v: #(u8! [1 2 3]) v/median")).isEqualTo("2.0");
        }

        @ParameterizedTest
        @ValueSource(strings = {"sum", "range", "mean", "median", "variance",
                "sample-variance", "population-deviation", "sample-deviation"})
        @DisplayName("an empty vector answers none to every one of them")
        void anemptyVectorAnswersNone(String field) {
            assertThat(moldedFieldOf("#(u8! [])", field)).isEqualTo("_");
        }

        @Test
        @DisplayName("and one number is not enough for the two that need a sample")
        void oneNumberIsNotASample() {
            assertThat(answerTo("v: #(u8! [1]) v/variance")).isEqualTo("0.0");
            assertThat(answerTo("v: #(u8! [1]) v/population-deviation")).isEqualTo("0.0");
            assertThat(moldedFieldOf("#(u8! [1])", "sample-variance"))
                    .as("dividing by one less than the count has nothing to divide by")
                    .isEqualTo("_");
            assertThat(moldedFieldOf("#(u8! [1])", "sample-deviation")).isEqualTo("_");
        }
    }

    @Nested
    @DisplayName("REFLECT and SPEC-OF")
    class Reflecting {

        @Test
        @DisplayName("reflect answers the same fields a path does")
        void reflectAnswersTheSameFields() {
            String makes = "v: make vector! [unsigned integer! 16 2] ";
            assertThat(answerTo(makes + "reflect v 'size")).isEqualTo("16");
            assertThat(answerTo(makes + "reflect v 'length")).isEqualTo("2");
            assertThat(answerTo(makes + "reflect v 'type")).isEqualTo("integer!");
            assertThat(answerTo(makes + "reflect v 'signed")).isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and the spec is how the vector would be written down again")
        void thespec() {
            assertThat(moldOf("spec-of make vector! [unsigned integer! 16 2]"))
                    .isEqualTo("[unsigned integer! 16 2]");
            assertThat(moldOf("reflect make vector! [unsigned integer! 16 2] 'spec"))
                    .isEqualTo("[unsigned integer! 16 2]");
        }

        @Test
        @DisplayName("with the sign word left off when there is nothing to say")
        void asignedSpecSaysNothingAboutIt() {
            assertThat(moldOf("spec-of #(i32! [1 2])")).isEqualTo("[integer! 32 2]");
            assertThat(moldOf("spec-of #(f32! [1 2])")).isEqualTo("[decimal! 32 2]");
        }
    }

    @Nested
    @DisplayName("QUERY")
    class Querying {

        private static final String MAKES = "v: make vector! [unsigned integer! 16 2] ";

        @Test
        @DisplayName("one named field answers that field")
        void onefield() {
            assertThat(answerTo(MAKES + "query v 'size")).isEqualTo("16");
            assertThat(answerTo(MAKES + "size? v")).isEqualTo("16");
        }

        @Test
        @DisplayName("none lists what there is to ask for")
        void listingTheFields() {
            assertThat(moldOf(MAKES + "query v none")).isEqualTo(
                    "[signed type size length minimum maximum range sum mean median "
                            + "variance sample-variance population-deviation "
                            + "sample-deviation]");
        }

        @Test
        @DisplayName("a block of words answers them as a record")
        void ablockOfWords() {
            assertThat(moldOf(MAKES + "query v [signed length]"))
                    .isEqualTo("[signed: #(false) length: 2]");
        }

        @Test
        @DisplayName("and a block of get-words answers the values alone")
        void ablockOfGetWords() {
            assertThat(moldOf(MAKES + "query v [:size :type]")).isEqualTo("[16 integer!]");
        }

        @Test
        @DisplayName("a datatype asks for the whole thing as an object")
        void anobject() {
            assertThat(answerTo(MAKES + "object? query v object!")).isEqualTo("#(true)");
            assertThat(answerTo(MAKES + "o: query v object! o/size")).isEqualTo("16");
            assertThat(answerTo(MAKES + "o: query v object! o/signed")).isEqualTo("#(false)");
            assertThat(answerTo(MAKES + "o: query v object! o/type")).isEqualTo("integer!");
            assertThat(answerTo(MAKES + "o: query v object! o/length")).isEqualTo("2");
        }

        @Test
        @DisplayName("and a word that is no field is an invalid argument")
        void anunknownWordInABlock() {
            assertThat(whatHappensTo(MAKES + "query v [nonsense]"))
                    .isEqualTo("invalid-arg");
        }
    }
}
