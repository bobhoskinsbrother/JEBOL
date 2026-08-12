package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Typeset membership is asserted against explicit tables rather than against
 * the implementation's own sets, so that adding a datatype forces a decision
 * about every typeset instead of silently defaulting to "not a member".
 */
class DatatypeTest {

    private static final Set<Datatype> EXPECTED_ANY_STRING = EnumSet.of(
            Datatype.STRING, Datatype.FILE, Datatype.URL, Datatype.EMAIL, Datatype.TAG,
            Datatype.REF);

    // HASH belongs to both any-block! and series!, which the last column of
    // Rebol's `boot/types.reb` says outright: `hash  self  block  +  f*  *  *
    // [series block]`. Its typeclass is `block`, so every arm in REBTYPE(Block)
    // serves it. This table left it out and JEBOL agreed with the table, which
    // meant a datatype JEBOL declared could not be built: every operation on a
    // hash threw an IllegalArgumentException out of the interpreter.
    private static final Set<Datatype> EXPECTED_ANY_BLOCK = EnumSet.of(
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH,
            Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH,
            Datatype.HASH);

    private static final Set<Datatype> EXPECTED_ANY_PATH = EnumSet.of(
            Datatype.PATH, Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH);

    private static final Set<Datatype> EXPECTED_ANY_WORD = EnumSet.of(
            Datatype.WORD, Datatype.SET_WORD, Datatype.GET_WORD,
            Datatype.LIT_WORD, Datatype.REFINEMENT, Datatype.ISSUE);

    // IMAGE and VECTOR belong to series! too, and this table left both out the
    // way it once left out HASH. The last column of `boot/types.reb` names
    // sixteen: binary, the six strings, image, vector, the six blocks and hash.
    // Transcribing it by hand is what keeps going wrong, and the cost is the same
    // each time -- every navigation native refused an image until this was
    // corrected, because `isSeries()` had been written to agree with the table.
    //
    // VECTOR is here and JEBOL has no vector value yet. That is deliberate: the
    // typeset is what the source says, and a datatype JEBOL cannot build is still
    // a member of the sets it belongs to.
    private static final Set<Datatype> EXPECTED_SERIES = EnumSet.of(
            Datatype.STRING, Datatype.FILE, Datatype.URL, Datatype.EMAIL, Datatype.TAG,
            Datatype.REF,
            Datatype.BLOCK, Datatype.PAREN, Datatype.PATH,
            Datatype.SET_PATH, Datatype.GET_PATH, Datatype.LIT_PATH,
            Datatype.HASH,
            Datatype.BINARY,
            Datatype.IMAGE, Datatype.VECTOR);

    private static final Set<Datatype> EXPECTED_NUMBER = EnumSet.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT);

    private static final Set<Datatype> EXPECTED_SCALAR = EnumSet.of(
            Datatype.INTEGER, Datatype.DECIMAL, Datatype.PERCENT, Datatype.MONEY,
            Datatype.CHAR, Datatype.PAIR, Datatype.TUPLE, Datatype.TIME, Datatype.DATE);

    private static final Set<Datatype> EXPECTED_ANY_FUNCTION = EnumSet.of(
            Datatype.NATIVE, Datatype.FUNCTION, Datatype.OP);

    @Nested
    @DisplayName("typeset membership, checked for every datatype")
    class TypesetMembership {

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void anyStringMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isAnyString())
                    .as("%s in any-string!", datatype)
                    .isEqualTo(EXPECTED_ANY_STRING.contains(datatype));
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void anyBlockMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isAnyBlock())
                    .as("%s in any-block!", datatype)
                    .isEqualTo(EXPECTED_ANY_BLOCK.contains(datatype));
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void anyPathMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isAnyPath())
                    .as("%s in any-path!", datatype)
                    .isEqualTo(EXPECTED_ANY_PATH.contains(datatype));
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void anyWordMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isAnyWord())
                    .as("%s in any-word!", datatype)
                    .isEqualTo(EXPECTED_ANY_WORD.contains(datatype));
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void seriesMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isSeries())
                    .as("%s in series!", datatype)
                    .isEqualTo(EXPECTED_SERIES.contains(datatype));
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void numberMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isNumber())
                    .as("%s in number!", datatype)
                    .isEqualTo(EXPECTED_NUMBER.contains(datatype));
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void scalarMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isScalar())
                    .as("%s in scalar!", datatype)
                    .isEqualTo(EXPECTED_SCALAR.contains(datatype));
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void anyFunctionMatchesTheTable(Datatype datatype) {
            assertThat(datatype.isAnyFunction())
                    .as("%s in any-function!", datatype)
                    .isEqualTo(EXPECTED_ANY_FUNCTION.contains(datatype));
        }
    }

    @Nested
    @DisplayName("relationships the typesets must satisfy")
    class TypesetRelationships {

        @Test
        @DisplayName("series! is any-string!, any-block!, binary!, image! and vector!")
        void seriesIsTheUnionOfItsParts() {
            // Not a union of the other typesets: `series` is a column in
            // `boot/types.reb` and two of its members belong to no other set.
            // This used to read as any-string plus any-block plus binary, which
            // is a rule that happens to hold until a datatype arrives that does
            // not fit it -- and `image` and `vector` are both such datatypes.
            for (Datatype datatype : Datatype.values()) {
                boolean expected = datatype.isAnyString()
                        || datatype.isAnyBlock()
                        || datatype == Datatype.BINARY
                        || datatype == Datatype.IMAGE
                        || datatype == Datatype.VECTOR;
                assertThat(datatype.isSeries()).as("%s", datatype).isEqualTo(expected);
            }
        }

        @Test
        @DisplayName("every any-path! is also an any-block!")
        void pathsAreBlocks() {
            for (Datatype datatype : Datatype.values()) {
                if (datatype.isAnyPath()) {
                    assertThat(datatype.isAnyBlock()).as("%s", datatype).isTrue();
                }
            }
        }

        @Test
        @DisplayName("no datatype is both a word and a block")
        void wordsAreNotBlocks() {
            for (Datatype datatype : Datatype.values()) {
                assertThat(datatype.isAnyWord() && datatype.isAnyBlock())
                        .as("%s", datatype)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("every number! is also a scalar!")
        void numbersAreScalars() {
            for (Datatype datatype : Datatype.values()) {
                if (datatype.isNumber()) {
                    assertThat(datatype.isScalar()).as("%s", datatype).isTrue();
                }
            }
        }

        @Test
        @DisplayName("nothing is both a scalar! and a series!")
        void scalarsAreNotSeries() {
            for (Datatype datatype : Datatype.values()) {
                assertThat(datatype.isScalar() && datatype.isSeries())
                        .as("%s", datatype)
                        .isFalse();
            }
        }
    }

    @Nested
    @DisplayName("spelling")
    class Spelling {

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void spellingIsLowercaseWithHyphensAndNoBang(Datatype datatype) {
            assertThat(datatype.spelling()).matches("[a-z][a-z-]*");
        }

        @ParameterizedTest
        @EnumSource(Datatype.class)
        void literalSpellingAddsTheBang(Datatype datatype) {
            assertThat(datatype.literalSpelling()).isEqualTo(datatype.spelling() + "!");
        }

        @Test
        @DisplayName("the hyphenated names match REBOL, not Java's enum names")
        void hyphenatedNamesUseRebolSpelling() {
            assertThat(Datatype.SET_WORD.spelling()).isEqualTo("set-word");
            assertThat(Datatype.GET_PATH.spelling()).isEqualTo("get-path");
            assertThat(Datatype.JAVA_OBJECT.spelling()).isEqualTo("java-object");
            assertThat(Datatype.LIT_WORD.literalSpelling()).isEqualTo("lit-word!");
        }

        @Test
        @DisplayName("no two datatypes share a spelling")
        void spellingsAreUnique() {
            assertThat(EnumSet.allOf(Datatype.class).stream().map(Datatype::spelling))
                    .doesNotHaveDuplicates();
        }
    }
}
