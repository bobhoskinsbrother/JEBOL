package org.jebol.domain.value;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TheStandardTypesetsAnswerAsR3DoesTest {

    private String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return Molder.form(interpreter.run(source).value());
    }

    @ParameterizedTest(name = "{0} holds {1}")
    @CsvSource(delimiter = '|', value = {
            "number!       | integer! decimal! percent!",
            "scalar!       | integer! decimal! percent! money! char! pair! tuple! time! date!",
            "series!       | binary! string! file! email! ref! url! tag! image! vector! block! paren! path! set-path! get-path! lit-path! hash!",
            "any-string!   | string! file! email! ref! url! tag!",
            "any-block!    | block! paren! path! set-path! get-path! lit-path! hash!",
            "any-path!     | path! set-path! get-path! lit-path!",
            "any-word!     | word! set-word! get-word! lit-word! refinement! issue!",
            "any-function! | native! action! rebcode! command! op! closure! function!",
            "any-object!   | object! module! error! task! port!",
            "immediate!    | none! logic! integer! decimal! percent! money! char! pair! tuple! time! date! datatype! typeset! word! set-word! get-word! lit-word! refinement! issue! event!",
            "copyable!     | binary! string! file! email! ref! url! tag! bitset! image! vector! block! paren! path! set-path! get-path! lit-path! hash! map! native! action! rebcode! command! op! closure! function! object! error! port!",
            "internal!     | end! unset! frame! handle!",
            "any-type!     | unset! none! logic! integer! decimal! percent! money! char! pair! tuple! time! date! binary! string! file! email! ref! url! tag! bitset! image! vector! block! paren! path! set-path! get-path! lit-path! hash! map! datatype! typeset! word! set-word! get-word! lit-word! refinement! issue! native! action! rebcode! command! op! closure! function! frame! object! module! error! task! port! gob! event! handle! struct! library! utype! java-object!",
    })
    @DisplayName("each standard typeset holds what r3 gives it, in the catalogue's order")
    void holdWhatR3Gives(String typeset, String members) {
        assertThat(answerTo("mold " + typeset)).isEqualTo("make typeset! [" + members + "]");
    }

    @ParameterizedTest(name = "type? {0} is typeset!")
    @CsvSource({"number!", "any-string!", "any-type!", "internal!"})
    @DisplayName("a standard typeset is a value of typeset!")
    void areValuesOfTypeset(String typeset) {
        assertThat(answerTo("mold type? " + typeset)).isEqualTo("#(typeset!)");
    }

    @Test
    @DisplayName("a standard typeset is equal to one made from the same datatypes")
    void equalOnesMadeFromTheSameDatatypes() {
        assertThat(answerTo("any-path! = make typeset! [path! set-path! get-path! lit-path!]"))
                .isEqualTo("true");
        assertThat(answerTo("any-path! = make typeset! [path! set-path! get-path!]"))
                .isEqualTo("false");
    }

    @Test
    @DisplayName("a datatype is a value of datatype!")
    void treatADatatypeAsAValue() {
        assertThat(answerTo("mold type? integer!")).isEqualTo("#(datatype!)");
    }

    @Test
    @DisplayName("the catalogue finds each standard typeset by its name, with or without the mark")
    void areFoundByNameInTheCatalogue() {
        assertThat(Catalogue.DATATYPES.findTypesetNamed("any-string")).contains(TypesetValue.ANY_STRING);
        assertThat(Catalogue.DATATYPES.findTypesetNamed("Any-String!")).contains(TypesetValue.ANY_STRING);
        assertThat(Catalogue.DATATYPES.findTypesetNamed("internal")).contains(TypesetValue.INTERNAL);
        assertThat(Catalogue.DATATYPES.findTypesetNamed("string")).isEmpty();
        assertThat(Catalogue.DATATYPES.findTypesetNamed("")).isEmpty();
    }

    @Test
    @DisplayName("the catalogue lists the thirteen standard typesets")
    void numberThirteenInTheCatalogue() {
        assertThat(Catalogue.DATATYPES.standardTypesets()).containsExactly(
                TypesetValue.ANY_TYPE, TypesetValue.NUMBER, TypesetValue.SCALAR, TypesetValue.SERIES,
                TypesetValue.ANY_STRING, TypesetValue.ANY_BLOCK, TypesetValue.ANY_PATH,
                TypesetValue.ANY_WORD, TypesetValue.ANY_FUNCTION, TypesetValue.ANY_OBJECT,
                TypesetValue.IMMEDIATE, TypesetValue.COPYABLE, TypesetValue.INTERNAL);
    }

    @Test
    @DisplayName("a standard typeset is spelt with its name, and one made by a script has none")
    void areSpeltWithTheirNames() {
        assertThat(TypesetValue.ANY_FUNCTION.spelling()).contains("any-function");
        assertThat(TypesetValue.of(Set.of(IntegerValue.TYPE)).spelling()).isEmpty();
    }

    @Test
    @DisplayName("a datatype of the scalar type is a scalar! though it is in no catalogue")
    void stillHoldAScalarOutsideTheCatalogue() {
        Datatype probe = new ScalarDatatype("probe") {
        };
        assertThat(TypesetValue.SCALAR.holds(probe)).isTrue();
        assertThat(TypesetValue.NUMBER.holds(probe)).isFalse();
        assertThat(TypesetValue.SERIES.holds(probe)).isFalse();
    }

    @Test
    @DisplayName("a datatype of the number type is both a number! and a scalar!")
    void holdANumberOutsideTheCatalogueAsANumberAndAScalar() {
        Datatype probe = new NumberDatatype("probe") {
        };
        assertThat(TypesetValue.NUMBER.holds(probe)).isTrue();
        assertThat(TypesetValue.SCALAR.holds(probe)).isTrue();
    }

    @Test
    @DisplayName("a datatype of the series type is a series! and nothing narrower")
    void holdASeriesOutsideTheCatalogue() {
        Datatype probe = new SeriesDatatype("probe") {
        };
        assertThat(TypesetValue.SERIES.holds(probe)).isTrue();
        assertThat(TypesetValue.ANY_STRING.holds(probe)).isFalse();
        assertThat(TypesetValue.ANY_BLOCK.holds(probe)).isFalse();
    }

    @Test
    @DisplayName("a datatype of the function type is an any-function!")
    void holdAFunctionOutsideTheCatalogue() {
        Datatype probe = new AnyFunctionDatatype("probe") {
        };
        assertThat(TypesetValue.ANY_FUNCTION.holds(probe)).isTrue();
        assertThat(TypesetValue.ANY_OBJECT.holds(probe)).isFalse();
    }

    @Test
    @DisplayName("a datatype of the object type is an any-object!")
    void holdAnObjectOutsideTheCatalogue() {
        Datatype probe = new AnyObjectDatatype("probe") {
        };
        assertThat(TypesetValue.ANY_OBJECT.holds(probe)).isTrue();
        assertThat(TypesetValue.ANY_FUNCTION.holds(probe)).isFalse();
    }

    @Test
    @DisplayName("a plain datatype is in any-type! and in none of the families")
    void holdAPlainDatatypeOnlyInAnyType() {
        Datatype probe = new Datatype("probe") {
        };
        assertThat(TypesetValue.ANY_TYPE.holds(probe)).isTrue();
        assertThat(Catalogue.DATATYPES.standardTypesets())
                .filteredOn(typeset -> typeset != TypesetValue.ANY_TYPE)
                .noneMatch(typeset -> typeset.holds(probe));
    }

    @Test
    @DisplayName("end! is in internal! and in no other standard typeset, any-type! included")
    void endIsOnlyInternal() {
        assertThat(Catalogue.DATATYPES.standardTypesets())
                .filteredOn(typeset -> typeset.holds(EndValue.TYPE))
                .containsExactly(TypesetValue.INTERNAL);
    }

    @Test
    @DisplayName("the datatypes with no values still belong to the typesets r3 puts them in")
    void keepTheNeverBuiltDatatypes() {
        assertThat(TypesetValue.ANY_FUNCTION.holds(RebcodeValue.TYPE)).isTrue();
        assertThat(TypesetValue.ANY_FUNCTION.holds(CommandValue.TYPE)).isTrue();
        assertThat(TypesetValue.INTERNAL.holds(FrameValue.TYPE)).isTrue();
        assertThat(TypesetValue.ANY_TYPE.holds(LibraryValue.TYPE)).isTrue();
        assertThat(TypesetValue.ANY_TYPE.holds(UtypeValue.TYPE)).isTrue();
    }
}
