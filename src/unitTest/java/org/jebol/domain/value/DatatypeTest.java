package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DatatypeTest {

    private static final List<String> TYPES_REB_ORDER = List.of(
            "end", "unset", "none", "logic", "integer", "decimal", "percent", "money", "char",
            "pair", "tuple", "time", "date", "binary", "string", "file", "email", "ref", "url",
            "tag", "bitset", "image", "vector", "block", "paren", "path", "set-path",
            "get-path", "lit-path", "hash", "map", "datatype", "typeset", "word", "set-word",
            "get-word", "lit-word", "refinement", "issue", "native", "action", "rebcode",
            "command", "op", "closure", "function", "frame", "object", "module", "error",
            "task", "port", "gob", "event", "handle", "struct", "library", "utype",
            "java-object");

    private static final Set<Datatype> EXPECTED_ANY_STRING = Set.of(
            StringValue.TYPE, FileValue.TYPE, UrlValue.TYPE, EmailValue.TYPE, TagValue.TYPE,
            RefValue.TYPE);

    private static final Set<Datatype> EXPECTED_ANY_BLOCK = Set.of(
            BlockValue.TYPE, ParenValue.TYPE, PathValue.TYPE,
            SetPathValue.TYPE, GetPathValue.TYPE, LitPathValue.TYPE,
            HashValue.TYPE);

    private static final Set<Datatype> EXPECTED_ANY_PATH = Set.of(
            PathValue.TYPE, SetPathValue.TYPE, GetPathValue.TYPE, LitPathValue.TYPE);

    private static final Set<Datatype> EXPECTED_ANY_WORD = Set.of(
            WordValue.TYPE, SetWordValue.TYPE, GetWordValue.TYPE,
            LitWordValue.TYPE, RefinementValue.TYPE, IssueValue.TYPE);

    private static final Set<Datatype> EXPECTED_SERIES = Set.of(
            StringValue.TYPE, FileValue.TYPE, UrlValue.TYPE, EmailValue.TYPE, TagValue.TYPE,
            RefValue.TYPE,
            BlockValue.TYPE, ParenValue.TYPE, PathValue.TYPE,
            SetPathValue.TYPE, GetPathValue.TYPE, LitPathValue.TYPE,
            HashValue.TYPE,
            BinaryValue.TYPE,
            ImageValue.TYPE, VectorValue.TYPE);

    private static final Set<Datatype> EXPECTED_NUMBER = Set.of(
            IntegerValue.TYPE, DecimalValue.TYPE, PercentValue.TYPE);

    private static final Set<Datatype> EXPECTED_SCALAR = Set.of(
            IntegerValue.TYPE, DecimalValue.TYPE, PercentValue.TYPE, MoneyValue.TYPE,
            CharacterValue.TYPE, PairValue.TYPE, TupleValue.TYPE, TimeValue.TYPE, DateValue.TYPE);

    private static final Set<Datatype> EXPECTED_ANY_FUNCTION = Set.of(
            NativeValue.TYPE, ActionValue.TYPE, RebcodeValue.TYPE, CommandValue.TYPE,
            OperatorValue.TYPE, ClosureValue.TYPE, FunctionValue.TYPE);

    private static final Set<Datatype> EXPECTED_ANY_OBJECT = Set.of(
            ObjectValue.TYPE, ModuleValue.TYPE, ErrorValue.TYPE, TaskValue.TYPE, PortValue.TYPE);

    static Stream<Datatype> everyDatatype() {
        return Catalogue.DATATYPES.entries().stream();
    }

    @Nested
    @DisplayName("the catalogue")
    class TheCatalogue {

        @Test
        @DisplayName("lists every datatype once, in the order of types.reb, java-object! last")
        void inTypesRebOrder() {
            assertThat(everyDatatype().map(Datatype::spelling)).containsExactlyElementsOf(TYPES_REB_ORDER);
        }

        @Test
        @DisplayName("gives each entry its one-based place, end! first and java-object! last")
        void positionsAreOneBased() {
            assertThat(EndValue.TYPE.position()).isEqualTo(1);
            assertThat(UnsetValue.TYPE.position()).isEqualTo(2);
            assertThat(IntegerValue.TYPE.position()).isEqualTo(5);
            assertThat(UtypeValue.TYPE.position()).isEqualTo(58);
            assertThat(JavaObjectValue.TYPE.position()).isEqualTo(59);
        }

        @Test
        @DisplayName("numbers the entries as Rebol's C does, end! at nought")
        void numberTheCGivesIt() {
            assertThat(EndValue.TYPE.numberTheCGivesIt()).isZero();
            assertThat(IntegerValue.TYPE.numberTheCGivesIt()).isEqualTo(4);
        }

        @Test
        @DisplayName("finds an entry by its name, with or without the mark, in any case")
        void findsByName() {
            assertThat(Catalogue.DATATYPES.named("integer!")).contains(IntegerValue.TYPE);
            assertThat(Catalogue.DATATYPES.named("set-word")).contains(SetWordValue.TYPE);
            assertThat(Catalogue.DATATYPES.named("STRING!")).contains(StringValue.TYPE);
        }

        @Test
        @DisplayName("finds nothing for a name no datatype has, nor for an empty one")
        void findsNothingForAnUnknownName() {
            assertThat(Catalogue.DATATYPES.named("integers!")).isEmpty();
            assertThat(Catalogue.DATATYPES.named("number!")).isEmpty();
            assertThat(Catalogue.DATATYPES.named("")).isEmpty();
            assertThat(Catalogue.DATATYPES.named("!")).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        @DisplayName("every entry is itself a value whose datatype is datatype!")
        void everyEntryIsADatatypeValue(Datatype datatype) {
            assertThat(datatype.datatype()).isSameAs(Datatype.TYPE);
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        @DisplayName("orders the entries by their place")
        void ordersByPlace(Datatype datatype) {
            assertThat(datatype.compareTo(EndValue.TYPE)).isEqualTo(datatype == EndValue.TYPE ? 0 : 1);
            assertThat(datatype.compareTo(JavaObjectValue.TYPE))
                    .isEqualTo(datatype == JavaObjectValue.TYPE ? 0 : -1);
        }
    }

    @Nested
    @DisplayName("typeset membership, read off the catalogue for every datatype")
    class TypesetMembership {

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void anyStringMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_STRING))
                    .as("%s in any-string!", datatype)
                    .isEqualTo(EXPECTED_ANY_STRING.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void anyBlockMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_BLOCK))
                    .as("%s in any-block!", datatype)
                    .isEqualTo(EXPECTED_ANY_BLOCK.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void anyPathMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_PATH))
                    .as("%s in any-path!", datatype)
                    .isEqualTo(EXPECTED_ANY_PATH.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void anyWordMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_WORD))
                    .as("%s in any-word!", datatype)
                    .isEqualTo(EXPECTED_ANY_WORD.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void seriesMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.SERIES))
                    .as("%s in series!", datatype)
                    .isEqualTo(EXPECTED_SERIES.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void numberMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.NUMBER))
                    .as("%s in number!", datatype)
                    .isEqualTo(EXPECTED_NUMBER.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void scalarMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.SCALAR))
                    .as("%s in scalar!", datatype)
                    .isEqualTo(EXPECTED_SCALAR.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void anyFunctionMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_FUNCTION))
                    .as("%s in any-function!", datatype)
                    .isEqualTo(EXPECTED_ANY_FUNCTION.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void anyObjectMatchesTheTable(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_OBJECT))
                    .as("%s in any-object!", datatype)
                    .isEqualTo(EXPECTED_ANY_OBJECT.contains(datatype));
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void anyTypeIsEverythingButEnd(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_TYPE))
                    .as("%s in any-type!", datatype)
                    .isEqualTo(datatype != EndValue.TYPE);
        }

        @Test
        @DisplayName("a typeset lists its members in the catalogue's order")
        void membersComeInCatalogueOrder() {
            assertThat(TypesetValue.ANY_STRING.members()).containsExactly(
                    StringValue.TYPE, FileValue.TYPE, EmailValue.TYPE, RefValue.TYPE,
                    UrlValue.TYPE, TagValue.TYPE);
        }
    }

    @Nested
    @DisplayName("relationships the typesets must satisfy")
    class TypesetRelationships {

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        @DisplayName("series! is any-string!, any-block!, binary!, image! and vector!")
        void seriesIsTheUnionOfItsParts(Datatype datatype) {
            boolean expected = datatype.belongsTo(TypesetValue.ANY_STRING)
                    || datatype.belongsTo(TypesetValue.ANY_BLOCK)
                    || datatype == BinaryValue.TYPE
                    || datatype == ImageValue.TYPE
                    || datatype == VectorValue.TYPE;
            assertThat(datatype.belongsTo(TypesetValue.SERIES)).isEqualTo(expected);
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        @DisplayName("every any-path! is also an any-block!")
        void pathsAreBlocks(Datatype datatype) {
            assertThat(!datatype.belongsTo(TypesetValue.ANY_PATH) || datatype.belongsTo(TypesetValue.ANY_BLOCK))
                    .isTrue();
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        @DisplayName("no datatype is both a word and a block")
        void wordsAreNotBlocks(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.ANY_WORD) && datatype.belongsTo(TypesetValue.ANY_BLOCK))
                    .isFalse();
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        @DisplayName("every number! is also a scalar!")
        void numbersAreScalars(Datatype datatype) {
            assertThat(!datatype.belongsTo(TypesetValue.NUMBER) || datatype.belongsTo(TypesetValue.SCALAR))
                    .isTrue();
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        @DisplayName("nothing is both a scalar! and a series!")
        void scalarsAreNotSeries(Datatype datatype) {
            assertThat(datatype.belongsTo(TypesetValue.SCALAR) && datatype.belongsTo(TypesetValue.SERIES))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("spelling")
    class Spelling {

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void spellingIsLowercaseWithHyphensAndNoBang(Datatype datatype) {
            assertThat(datatype.spelling()).matches("[a-z][a-z-]*");
        }

        @ParameterizedTest
        @MethodSource("org.jebol.domain.value.DatatypeTest#everyDatatype")
        void literalSpellingAddsTheBang(Datatype datatype) {
            assertThat(datatype.literalSpelling()).isEqualTo(datatype.spelling() + "!");
        }

        @Test
        @DisplayName("the hyphenated names match REBOL")
        void hyphenatedNamesUseRebolSpelling() {
            assertThat(SetWordValue.TYPE.spelling()).isEqualTo("set-word");
            assertThat(GetPathValue.TYPE.spelling()).isEqualTo("get-path");
            assertThat(JavaObjectValue.TYPE.spelling()).isEqualTo("java-object");
            assertThat(LitWordValue.TYPE.literalSpelling()).isEqualTo("lit-word!");
        }

        @Test
        @DisplayName("no two datatypes share a spelling")
        void spellingsAreUnique() {
            assertThat(everyDatatype().map(Datatype::spelling)).doesNotHaveDuplicates();
        }
    }
}
