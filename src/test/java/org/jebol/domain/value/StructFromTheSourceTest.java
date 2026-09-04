package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code struct!} datatype, from {@code t-struct.c}.
 *
 * <p>A struct is three things: a layout saying what the bytes mean, a run of
 * bytes, and an offset into them. Everything that looks surprising follows
 * from the bytes being shared. A field of struct type answers a value at a
 * further offset in the same bytes, so {@code s/pos/x: 22} reaches the
 * parent; an element taken out of an array of structs keeps seeing what the
 * parent writes afterwards.
 *
 * <p>Fields are packed with no alignment padding at all -- the C accumulates
 * {@code size * dimension} into a running offset and never rounds it up -- so
 * a {@code uint16!} followed by a two-byte struct is four bytes and not six.
 *
 * <p>Two asymmetries are worth naming because neither is a mistake. Reading
 * an array field through a path gives a vector for the numeric types and a
 * block for the rest, while reflecting the same field gives a block either
 * way, because {@code Get_Struct_Field_Value} and {@code Get_Struct_Reflect}
 * build different things from the same bytes. And reflection tests
 * {@code dimension > 1} where the reader tests whether a count was written at
 * all, so {@code [uint8! [1]]} reflects as a bare number and reads as a
 * vector of one.
 *
 * <p>Every expectation here was run against a Rebol built by
 * {@code scripts/build-r3.sh} before it was written down.
 */
class StructFromTheSourceTest {

    /**
     * What JEBOL answers, with the quotes MOLD's own result arrives in taken
     * off. Everything here asks for a molded form, and displaying a string
     * molds it a second time.
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
    @DisplayName("a layout says how wide each field is and where it sits")
    class TheLayout {

        @ParameterizedTest(name = "{0} is {1} bytes")
        @CsvSource({
            "int8!,    1", "int16!,   2", "int32!,   4", "int64!,   8",
            "uint8!,   1", "uint16!,  2", "uint32!,  4", "uint64!,  8",
            "float!,   4", "double!,  8", "word!,    4",
        })
        @DisplayName("eleven field types, each with the width the C gives it")
        void elevenFieldTypes(String type, String bytes) {
            assertThat(answerTo("length? make struct! [a [" + type + "]]"))
                    .isEqualTo(bytes);
        }

        @Test
        @DisplayName("a count multiplies the width")
        void acountMultipliesTheWidth() {
            assertThat(answerTo("length? make struct! [a [int64! [2]]]")).isEqualTo("16");
            assertThat(answerTo("length? make struct! [a [uint8! [7]]]")).isEqualTo("7");
        }

        @Test
        @DisplayName("fields are packed, so nothing is rounded up to an alignment")
        void fieldsArePacked() {
            assertThat(answerTo("""
                    register pair8!: make struct! [x [uint8!] y [uint8!]]
                    length? make struct! [id [uint16!] pos [struct! pair8!]]"""))
                    .as("four bytes, not six: the C never aligns a field")
                    .isEqualTo("4");
        }

        @ParameterizedTest(name = "{0} is settled to {1}")
        @CsvSource({
            "float!,  float32!",
            "double!, float64!",
            "u8!,     uint8!",
            "i32!,    int32!",
        })
        @DisplayName("an alias is rewritten in the spec, as the C rewrites the symbol")
        void analiasIsRewrittenInTheSpec(String written, String settled) {
            assertThat(answerTo("mold spec-of make struct! [a [" + written + "]]"))
                    .isEqualTo("[a [" + settled + "]]");
        }
    }

    @Nested
    @DisplayName("a layout that declares no struct is refused")
    class WhatIsRefused {

        @ParameterizedTest(name = "make struct! {0} is a malconstruct")
        @ValueSource(strings = {
            "[]", "[[]]", "[a]", "[[] a]", "[{test} []]", "[{test} {test}]",
        })
        @DisplayName("a shape that is not word-then-block at all")
        void ashapeThatIsNotWordThenBlock(String layout) {
            assertThat(errorOr("make struct! " + layout)).isEqualTo("malconstruct");
        }

        @ParameterizedTest(name = "make struct! {0} is an invalid argument")
        @ValueSource(strings = {
            "[a [23]]", "[a [int8! foo]]", "[a [int8! 23]]", "[a [int8! [foo]]]",
        })
        @DisplayName("the right shape naming a type that is not one")
        void therightShapeWithAWrongType(String layout) {
            assertThat(errorOr("make struct! " + layout)).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("construction evaluates nothing, so a word cannot become a number")
        void constructionEvaluatesNothing() {
            assertThat(answerTo("""
                    e: transcode/one/error {#(struct! [a [uint8!]] [random 10])}
                    e/id"""))
                    .isEqualTo("malconstruct");
        }
    }

    @Nested
    @DisplayName("initial values, named or in order")
    class StartingValues {

        @Test
        @DisplayName("a set-word names the field, and the rest keep their zero")
        void asetWordNamesTheField() {
            assertThat(answerTo("""
                    mold/all/flat #(struct! [a [uint16!] b [int8!]] [a: 23])"""))
                    .isEqualTo("#(struct! [a [uint16!] b [int8!]] [a: 23 b: 0])");
        }

        @Test
        @DisplayName("without set-words the values fill the fields in order")
        void withoutSetWordsTheValuesFillInOrder() {
            assertThat(answerTo("""
                    mold/all/flat #(struct! [a [uint16!] b [int8!]] [23])"""))
                    .as("a short list leaves the rest of the fields alone")
                    .isEqualTo("#(struct! [a [uint16!] b [int8!]] [a: 23 b: 0])");
        }

        @Test
        @DisplayName("an integer written to a float field becomes the number")
        void anintegerWrittenToAFloatField() {
            assertThat(answerTo("""
                    mold/all/flat #(struct! [a [float!]] [23])"""))
                    .isEqualTo("#(struct! [a [float32!]] [a: 23.0])");
        }

        @Test
        @DisplayName("a word field starts as none and holds a word")
        void awordFieldStartsAsNone() {
            assertThat(answerTo("mold/all/flat make struct! [a [word!]]"))
                    .isEqualTo("#(struct! [a [word!]] [a: _])");
            assertThat(answerTo("mold/all/flat #(struct! [a [word!]] [a: foo])"))
                    .isEqualTo("#(struct! [a [word!]] [a: foo])");
        }
    }

    @Nested
    @DisplayName("a prototype is a layout with bytes already in it")
    class FromAPrototype {

        private static final String PAIR = """
                register pair8!: make struct! [x [uint8!] y [uint8!]]
                proto: #(struct! [a [uint8!] b [uint8!]] [a: 1 b: 2])
                """;

        @ParameterizedTest(name = "make proto {0} gives {1}")
        @CsvSource(delimiter = '|', value = {
            "[a: 10]       | [10 2]",
            "[b: 20]       | [1 20]",
            "[b: 20 a: 10] | [10 20]",
            "[10]          | [10 2]",
        })
        @DisplayName("what the block does not name keeps the prototype's value")
        void whatTheBlockDoesNotName(String given, String expected) {
            assertThat(answerTo(PAIR + "s: make proto " + given.strip()
                    + " mold reduce [s/a s/b]"))
                    .isEqualTo(expected.strip());
        }

        @Test
        @DisplayName("the block is reduced, with its set-words left standing")
        void theblockIsReducedLeavingSetWords() {
            assertThat(answerTo(PAIR
                    + "s: make proto [3 * 10 4 * 10] mold reduce [s/a s/b]"))
                    .isEqualTo("[30 40]");
            assertThat(answerTo(PAIR
                    + "s: make proto [b: 3 * 10 a: 4 * 10] mold reduce [s/a s/b]"))
                    .as("MAKE on a struct calls Reduce_Block_No_Set, which MT_Struct "
                            + "does not, so construction syntax evaluates nothing")
                    .isEqualTo("[40 30]");
        }

        @Test
        @DisplayName("a copy has bytes of its own")
        void acopyHasBytesOfItsOwn() {
            assertThat(answerTo(PAIR + """
                    s: make proto [1 2]
                    other: copy s
                    s/a: 99
                    mold reduce [to binary! s to binary! other]"""))
                    .isEqualTo("[#{6302} #{0102}]");
        }

        @ParameterizedTest(name = "copy/{0} on a struct is refused")
        @ValueSource(strings = {"part s 1", "deep s 1"})
        @DisplayName("and COPY takes no refinements at all")
        void copyTakesNoRefinements(String call) {
            assertThat(errorOr(PAIR + "s: make proto [1 2] copy/" + call))
                    .isEqualTo("bad-refines");
        }
    }

    @Nested
    @DisplayName("CHANGE writes fields from a block and bytes from a binary")
    class Changing {

        private static final String PAIR = """
                register pair8!: make struct! [x [uint8!] y [uint8!]]
                s: make pair8! [1 2]
                """;

        @ParameterizedTest(name = "change s {0} leaves {1}")
        @CsvSource(delimiter = '|', value = {
            "[3 4]        | #{0304}",
            "[y: 3 x: 4]  | #{0403}",
            "[5]          | #{0502}",
            "#{07}        | #{0702}",
            "#{0101}      | #{0101}",
            "#{020202}    | #{0202}",
        })
        @DisplayName("as far as the shorter of the two reaches")
        void asfarAsTheShorterReaches(String given, String expected) {
            assertThat(answerTo(PAIR + "change s " + given.strip()
                    + " mold to binary! s"))
                    .isEqualTo(expected.strip());
        }

        @Test
        @DisplayName("CLEAR zeroes the bytes")
        void clearZeroesTheBytes() {
            assertThat(answerTo(PAIR + "clear s mold to binary! s"))
                    .isEqualTo("#{0000}");
        }
    }

    @Nested
    @DisplayName("a field of struct type shares the parent's bytes")
    class Nesting {

        private static final String NESTED = """
                register pair8!: make struct! [x [uint8!] y [uint8!]]
                s: make struct! [id [uint16!] pos [struct! pair8!]]
                """;

        @Test
        @DisplayName("writing through the inner struct reaches the outer bytes")
        void writingThroughTheInnerStruct() {
            assertThat(answerTo(NESTED + """
                    s/pos/x: 22
                    s/pos/y: 33
                    mold to binary! s"""))
                    .isEqualTo("#{00001621}");
        }

        @Test
        @DisplayName("and changing the outer bytes shows through the inner struct")
        void changingTheOuterBytes() {
            assertThat(answerTo(NESTED + """
                    change s #{0100 0203}
                    mold reduce [s/id s/pos/x s/pos/y]"""))
                    .isEqualTo("[1 2 3]");
        }

        @Test
        @DisplayName("the inner struct molds its whole layout, not the name it was given")
        void theinnerStructMoldsItsWholeLayout() {
            assertThat(answerTo(NESTED + "s/id: 1 s/pos/x: 22 s/pos/y: 33 mold/flat/all s"))
                    .isEqualTo("#(struct! [id [uint16!] pos [struct! pair8!]] "
                            + "[id: 1 pos: #(struct! [x [uint8!] y [uint8!]] "
                            + "[x: 22 y: 33])])");
        }

        @Test
        @DisplayName("three levels deep, each level sees its own span of the bytes")
        void threeLevelsDeep() {
            assertThat(answerTo("""
                    s: make struct! [
                        a [uint32!]
                        b [struct! [x [uint32!] y [struct! [yy [uint32!]]]]]
                    ]
                    s/b/x: 1
                    s/b/y/yy: 2
                    mold reduce [to binary! s/b/y to binary! s/b to binary! s]"""))
                    .isEqualTo("[#{02000000} #{0100000002000000} "
                            + "#{000000000100000002000000}]");
        }

        @Test
        @DisplayName("an element of a struct array keeps seeing what the parent writes")
        void anelementOfAStructArray() {
            assertThat(answerTo("""
                    s: make struct! [a [struct! [n [int8!]] [2]]]
                    s/a/1/n: 1
                    s/a/2/n: 2
                    held: s/a/1
                    s/a/1: s/a/2
                    s/a/1/n: 3
                    mold reduce [to binary! held to binary! s]"""))
                    .as("writing one slot of a struct array copies bytes rather than "
                            + "replacing the slot, so a value taken out earlier still "
                            + "points at the same place")
                    .isEqualTo("[#{03} #{0302}]");
        }
    }

    @Nested
    @DisplayName("an array field reads as a vector or as a block")
    class ArrayFields {

        @Test
        @DisplayName("numbers give a vector, because one can hold them")
        void numbersGiveAVector() {
            assertThat(answerTo("""
                    s: make struct! [a [uint8! [2]]]
                    type? s/a"""))
                    .isEqualTo("#(vector!)");
            assertThat(answerTo("""
                    s: make struct! [a [uint8! [2]]]
                    s/a = #(u8! [0 0])"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("anything else gives a block, because no vector holds it")
        void anythingElseGivesABlock() {
            assertThat(answerTo("""
                    s: make struct! [a [word! [2]]]
                    mold s/a"""))
                    .isEqualTo("[_ _]");
        }

        @Test
        @DisplayName("a vector of the right width and length writes the whole field")
        void avectorWritesTheWholeField() {
            assertThat(answerTo("""
                    s: make struct! [a [uint8! [2]]]
                    s/a: #(u8! [1 2])
                    mold to binary! s"""))
                    .isEqualTo("#{0102}");
        }

        @Test
        @DisplayName("reflection gives a block either way, and only past a count of one")
        void reflectionGivesABlockEitherWay() {
            assertThat(answerTo("mold body-of make struct! [a [uint8! [2]]]"))
                    .isEqualTo("[a: [0 0]]");
            assertThat(answerTo("mold body-of make struct! [a [uint8! [1]]]"))
                    .as("Get_Struct_Reflect tests dimension > 1, where the reader "
                            + "tests whether a count was written at all")
                    .isEqualTo("[a: 0]");
        }
    }

    @Nested
    @DisplayName("what a struct answers about itself")
    class Reflection {

        private static final String SHAPE = """
                s: #(struct! [
                    a [uint16!]
                    b [int32!]
                    c [word!]
                    d [uint8! [2]]
                ] [a: 1 b: -1 c: foo])
                """;

        @ParameterizedTest(name = "{0} answers {1}")
        @CsvSource(delimiter = '|', value = {
            "spec-of   | [a [uint16!] b [int32!] c [word!] d [uint8! [2]]]",
            "body-of   | [a: 1 b: -1 c: foo d: [0 0]]",
            "words-of  | [a b c d]",
            "keys-of   | [a b c d]",
            "values-of | [1 -1 foo [0 0]]",
        })
        @DisplayName("the four questions REBTYPE(Struct) takes, and KEYS-OF as WORDS")
        void thefourQuestions(String asked, String expected) {
            assertThat(answerTo(SHAPE + "mold/flat " + asked.strip() + " s"))
                    .isEqualTo(expected.strip());
        }

        @Test
        @DisplayName("TO BINARY! gives the bytes, least significant first")
        void tobinaryGivesTheBytes() {
            assertThat(answerTo("""
                    mold to binary! #(struct! [a [uint16!] b [int32!]] [1 -1])"""))
                    .isEqualTo("#{0100FFFFFFFF}");
        }
    }

    @Nested
    @DisplayName("equality compares the shape, strict equality compares the value")
    class Comparing {

        @Test
        @DisplayName("two layouts of the same widths are equal however they are spelt")
        void twolayoutsOfTheSameWidths() {
            assertThat(answerTo("""
                    (make struct! [a [u8!] b [u8!]]) = (make struct! [a [uint8!] b [uint8!]])"""))
                    .as("same_fields hashes the field types and dimensions and "
                            + "nothing else, so the names do not come into it")
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("but two separately made structs are never the same one")
        void twoseparatelyMadeStructs() {
            assertThat(answerTo("""
                    (make struct! [a [u8!] b [u8!]]) == (make struct! [a [uint8!] b [uint8!]])"""))
                    .isEqualTo("#(false)");
        }

        @Test
        @DisplayName("and differing bytes are not equal")
        void differingBytesAreNotEqual() {
            assertThat(answerTo("""
                    first: make struct! [a [uint8!]]
                    second: make struct! [a [uint8!]]
                    first/a: 1
                    first = second"""))
                    .isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("a field holding a whole REBOL value")
    class LiveValues {

        @Test
        @DisplayName("holds what was put in it, and the series stays the same series")
        void holdsWhatWasPutInIt() {
            assertThat(answerTo("""
                    s: make struct! [a [rebval!]]
                    s/a: text: "Hello"
                    clear s/a
                    mold reduce [s/a text]"""))
                    .as("clearing through the struct clears the string the caller "
                            + "still holds, because it is one string")
                    .isEqualTo("""
                            ["" ""]""");
        }

        @Test
        @DisplayName("refuses a raw binary, because bytes would land on a value")
        void refusesARawBinary() {
            assertThat(errorOr("""
                    s: make struct! [a [rebval!] b [rebval!]]
                    change s #{FF}"""))
                    .isEqualTo("protected");
        }

        @Test
        @DisplayName("and so does any struct holding one, however deep")
        void andsoDoesAnyStructHoldingOne() {
            assertThat(errorOr("""
                    s: make struct! [id [uint8!] inner [struct! [val [rebval!]]]]
                    change s #{0102}"""))
                    .as("STRUCT_FLAGS accumulates upward from the inner struct")
                    .isEqualTo("protected");
        }

        @Test
        @DisplayName("CLEAR forgets the values as well as the bytes")
        void clearForgetsTheValues() {
            assertThat(answerTo("""
                    s: make struct! [a [rebval!] b [rebval!]]
                    s/a: "Hello"
                    s/b: 1-Jan-2000
                    clear s
                    mold reduce [none? s/a none? s/b]"""))
                    .isEqualTo("[#(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("REGISTER files a layout under a name")
    class Registering {

        @Test
        @DisplayName("a set-word is set here, so the name is usable as a prototype")
        void asetWordIsSetHere() {
            assertThat(answerTo("""
                    register pair8!: make struct! [x [uint8!] y [uint8!]]
                    type? :pair8!"""))
                    .isEqualTo("#(struct!)");
        }

        @Test
        @DisplayName("and the name resolves inside another layout")
        void thenameResolvesInsideAnotherLayout() {
            assertThat(answerTo("""
                    register pair8!: make struct! [x [uint8!] y [uint8!]]
                    mold to binary! make struct! [id [uint16!] pos [struct! pair8!]]"""))
                    .isEqualTo("#{00000000}");
        }

        @Test
        @DisplayName("a name already taken by a different layout is refused")
        void anameAlreadyTaken() {
            assertThat(errorOr("""
                    register taken!: make struct! [x [uint8!]]
                    register taken!: make struct! [y [uint16!]]"""))
                    .isEqualTo("already-used");
        }
    }
}
