package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AWordKnowsItsOwnKindTest {

    @Nested
    @DisplayName("building one from a datatype")
    class FromADatatype {

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"WORD", "SET_WORD", "GET_WORD", "LIT_WORD", "REFINEMENT", "ISSUE"})
        @DisplayName("each any-word! datatype builds a word that answers to that datatype")
        void eachWordDatatypeBuildsItsOwnKind(Datatype asked) {
            assertThat(AnyWordValue.ofTheDatatype("a", asked).datatype()).isEqualTo(asked);
        }

        @Test
        @DisplayName("and the class is the kind, so the tag never has to be asked")
        void theClassIsTheKind() {
            assertThat(AnyWordValue.ofTheDatatype("a", Datatype.WORD)).isInstanceOf(WordValue.class);
            assertThat(AnyWordValue.ofTheDatatype("a", Datatype.SET_WORD)).isInstanceOf(SetWordValue.class);
            assertThat(AnyWordValue.ofTheDatatype("a", Datatype.GET_WORD)).isInstanceOf(GetWordValue.class);
            assertThat(AnyWordValue.ofTheDatatype("a", Datatype.LIT_WORD)).isInstanceOf(LitWordValue.class);
            assertThat(AnyWordValue.ofTheDatatype("a", Datatype.REFINEMENT)).isInstanceOf(RefinementValue.class);
            assertThat(AnyWordValue.ofTheDatatype("a", Datatype.ISSUE)).isInstanceOf(IssueValue.class);
        }

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"STRING", "BLOCK", "PATH", "SET_PATH", "DATATYPE"})
        @DisplayName("a datatype outside any-word! is refused")
        void aDatatypeOutsideTheFamilyIsRefused(Datatype asked) {
            assertThatThrownBy(() -> AnyWordValue.ofTheDatatype("a", asked))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("any-word!");
        }
    }

    @Nested
    @DisplayName("what a word is made of")
    class ItsParts {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("a word needs a spelling")
        void aSpellingIsRequired(String spelling) {
            assertThatThrownBy(() -> SetWordValue.of(spelling))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs a spelling");
        }

        @Test
        @DisplayName("a one-letter spelling is enough")
        void oneLetterIsEnough() {
            assertThat(WordValue.of("a").spelling()).isEqualTo("a");
        }

        @Test
        @DisplayName("a binding is required, an unbound word carries the unbound context")
        void aBindingIsRequired() {
            assertThatThrownBy(() -> WordValue.of("a").boundTo(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never null");
            assertThat(WordValue.of("a").isBound()).isFalse();
        }

        @Test
        @DisplayName("the canonical spelling ignores case")
        void theCanonicalIgnoresCase() {
            assertThat(GetWordValue.of("Total").canonical()).isEqualTo(GetWordValue.of("TOTAL").canonical());
        }
    }

    @Nested
    @DisplayName("binding")
    class Binding {

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"WORD", "SET_WORD", "GET_WORD", "LIT_WORD", "REFINEMENT", "ISSUE"})
        @DisplayName("binding a word keeps its kind")
        void bindingKeepsTheKind(Datatype kind) {
            Context somewhere = Context.root();
            AnyWordValue bound = AnyWordValue.ofTheDatatype("a", kind).boundTo(somewhere);

            assertThat(bound.datatype()).isEqualTo(kind);
            assertThat(bound.binding()).isSameAs(somewhere);
        }

        @Test
        @DisplayName("seeing a word as another kind keeps its binding")
        void anotherKindKeepsTheBinding() {
            Context somewhere = Context.root();
            AnyWordValue bound = LitWordValue.of("a").boundTo(somewhere);

            assertThat(bound.asWord().binding()).isSameAs(somewhere);
            assertThat(bound.asSetWord().binding()).isSameAs(somewhere);
            assertThat(bound.as(Datatype.GET_WORD).binding()).isSameAs(somewhere);
            assertThat(bound.as(Datatype.GET_WORD)).isInstanceOf(GetWordValue.class);
        }

        @Test
        @DisplayName("seeing a word as a kind outside any-word! is refused")
        void anotherKindOutsideTheFamilyIsRefused() {
            assertThatThrownBy(() -> WordValue.of("a").as(Datatype.STRING))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("any-word!");
        }
    }

    @Nested
    @DisplayName("how each kind writes itself")
    class WritingItself {

        @Test
        @DisplayName("each kind is molded with its own mark")
        void eachKindHasItsMark() {
            assertThat(WordValue.of("a").mold()).isEqualTo("a");
            assertThat(SetWordValue.of("a").mold()).isEqualTo("a:");
            assertThat(GetWordValue.of("a").mold()).isEqualTo(":a");
            assertThat(LitWordValue.of("a").mold()).isEqualTo("'a");
            assertThat(RefinementValue.of("a").mold()).isEqualTo("/a");
            assertThat(IssueValue.of("a").mold()).isEqualTo("#a");
        }

        @ParameterizedTest
        @EnumSource(value = Datatype.class, names = {"WORD", "SET_WORD", "GET_WORD", "LIT_WORD", "REFINEMENT", "ISSUE"})
        @DisplayName("every kind is formed as its bare spelling")
        void everyKindIsFormedBare(Datatype kind) {
            assertThat(AnyWordValue.ofTheDatatype("Abc", kind).form()).isEqualTo("Abc");
        }
    }

    @Nested
    @DisplayName("what each kind means")
    class WhatItMeans {

        @Test
        @DisplayName("a word and a get-word look up their declaration")
        void aWordAndAGetWordLookUpTheirDeclaration() {
            assertThat(WordValue.of("a").looksUpItsDeclaration()).isTrue();
            assertThat(GetWordValue.of("a").looksUpItsDeclaration()).isTrue();
        }

        @Test
        @DisplayName("no other kind looks up its declaration")
        void nothingElseLooksUpItsDeclaration() {
            assertThat(SetWordValue.of("a").looksUpItsDeclaration()).isFalse();
            assertThat(LitWordValue.of("a").looksUpItsDeclaration()).isFalse();
            assertThat(RefinementValue.of("a").looksUpItsDeclaration()).isFalse();
            assertThat(IssueValue.of("a").looksUpItsDeclaration()).isFalse();
        }

        @Test
        @DisplayName("in a function spec a lit-word is soft quoted, a get-word hard quoted, and a word evaluated")
        void theParameterEachDeclares() {
            assertThat(LitWordValue.of("a").kindOfParameterItDeclares()).isEqualTo(ParameterKind.SOFT_QUOTED);
            assertThat(GetWordValue.of("a").kindOfParameterItDeclares()).isEqualTo(ParameterKind.HARD_QUOTED);
            assertThat(WordValue.of("a").kindOfParameterItDeclares()).isEqualTo(ParameterKind.NORMAL);
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("the same spelling in two kinds is two different values")
        void differentKindsDiffer() {
            assertThat(SetWordValue.of("a")).isNotEqualTo(WordValue.of("a"));
            assertThat(IssueValue.of("a")).isNotEqualTo(RefinementValue.of("a"));
        }

        @Test
        @DisplayName("the same spelling in one kind is equal and hashes alike, whatever it is bound to")
        void theSameKindIsEqual() {
            AnyWordValue bound = GetWordValue.of("a").boundTo(Context.root());

            assertThat(bound).isEqualTo(GetWordValue.of("a"));
            assertThat(bound).hasSameHashCodeAs(GetWordValue.of("a"));
        }

        @Test
        @DisplayName("Rebol's own equality compares the names, whatever the kind and the case")
        void rebolEqualityComparesNames() {
            assertThat(SetWordValue.of("a").equalTo(WordValue.of("A"), Sameness.insideASeries())).isTrue();
        }
    }
}
