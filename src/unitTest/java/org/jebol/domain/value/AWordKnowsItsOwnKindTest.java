package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AWordKnowsItsOwnKindTest {

    private final List<AnyWordValue.AnyWordDatatype> everyWordDatatype = List.of(
            WordValue.TYPE, SetWordValue.TYPE, GetWordValue.TYPE, LitWordValue.TYPE,
            RefinementValue.TYPE, IssueValue.TYPE);

    @Nested
    @DisplayName("building one from a datatype")
    class FromADatatype {

        @Test
        @DisplayName("each any-word! datatype builds a word that answers to that datatype")
        void buildsEachWordDatatypesOwnKind() {
            assertThat(everyWordDatatype).allSatisfy(asked ->
                    assertThat(asked.spell("a").datatype()).isSameAs(asked));
        }

        @Test
        @DisplayName("and the class is the type, so the tag never has to be asked")
        void takesItsKindFromTheClass() {
            assertThat(WordValue.TYPE.spell("a")).isInstanceOf(WordValue.class);
            assertThat(SetWordValue.TYPE.spell("a")).isInstanceOf(SetWordValue.class);
            assertThat(GetWordValue.TYPE.spell("a")).isInstanceOf(GetWordValue.class);
            assertThat(LitWordValue.TYPE.spell("a")).isInstanceOf(LitWordValue.class);
            assertThat(RefinementValue.TYPE.spell("a")).isInstanceOf(RefinementValue.class);
            assertThat(IssueValue.TYPE.spell("a")).isInstanceOf(IssueValue.class);
        }

        @Test
        @DisplayName("a value outside any-word! cannot be seen as a word")
        void refusesAValueOutsideTheFamily() {
            assertThat(List.<Value>of(StringValue.of("a"), BlockValue.of(), PathValue.of(List.of()),
                    IntegerValue.TYPE)).allSatisfy(outside ->
                    assertThatThrownBy(() -> WordValue.TYPE.as(outside))
                            .isInstanceOfSatisfying(Raised.class, raised ->
                                    assertThat(raised.error().errorId()).isEqualTo("not-same-class")));
        }
    }

    @Nested
    @DisplayName("what a word is made of")
    class ItsParts {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("a word needs a spelling")
        void requireASpelling(String spelling) {
            assertThatThrownBy(() -> SetWordValue.of(spelling))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs a spelling");
        }

        @Test
        @DisplayName("a one-letter spelling is enough")
        void needOnlyOneLetter() {
            assertThat(WordValue.of("a").spelling()).isEqualTo("a");
        }

        @Test
        @DisplayName("a binding is required, an unbound word carries the unbound context")
        void requireABinding() {
            assertThatThrownBy(() -> WordValue.of("a").bindTo(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("never null");
            assertThat(WordValue.of("a").isBound()).isFalse();
        }

        @Test
        @DisplayName("the canonical spelling ignores case")
        void ignoreCaseInTheCanonical() {
            assertThat(GetWordValue.of("Total").canonical()).isEqualTo(GetWordValue.of("TOTAL").canonical());
        }
    }

    @Nested
    @DisplayName("binding")
    class Binding {

        @Test
        @DisplayName("binding a word keeps its type")
        void keepsTheKind() {
            Context somewhere = Context.root();
            assertThat(everyWordDatatype).allSatisfy(kind -> {
                AnyWordValue bound = kind.spell("a").bindTo(somewhere);

                assertThat(bound.datatype()).isSameAs(kind);
                assertThat(bound.binding()).isSameAs(somewhere);
            });
        }

        @Test
        @DisplayName("seeing a word as another type keeps its binding")
        void survivesInAnotherKind() {
            Context somewhere = Context.root();
            AnyWordValue bound = LitWordValue.of("a").bindTo(somewhere);

            assertThat(bound.asWord().binding()).isSameAs(somewhere);
            assertThat(bound.asSetWord().binding()).isSameAs(somewhere);
            assertThat(GetWordValue.TYPE.as(bound)).isInstanceOfSatisfying(GetWordValue.class,
                    seen -> assertThat(seen.binding()).isSameAs(somewhere));
        }

        @Test
        @DisplayName("seeing a word as a type outside any-word! is refused")
        void refusesAnotherKindOutsideTheFamily() {
            assertThatThrownBy(() -> StringValue.TYPE.as(WordValue.of("a")))
                    .isInstanceOfSatisfying(Raised.class, raised ->
                            assertThat(raised.error().errorId()).isEqualTo("not-same-class"));
        }
    }

    @Nested
    @DisplayName("how each type writes itself")
    class WritingItself {

        @Test
        @DisplayName("each type is molded with its own mark")
        void marksEachKind() {
            assertThat(WordValue.of("a").mold()).isEqualTo("a");
            assertThat(SetWordValue.of("a").mold()).isEqualTo("a:");
            assertThat(GetWordValue.of("a").mold()).isEqualTo(":a");
            assertThat(LitWordValue.of("a").mold()).isEqualTo("'a");
            assertThat(RefinementValue.of("a").mold()).isEqualTo("/a");
            assertThat(IssueValue.of("a").mold()).isEqualTo("#a");
        }

        @Test
        @DisplayName("every type is formed as its bare spelling")
        void formsEveryKindBare() {
            assertThat(everyWordDatatype).allSatisfy(kind ->
                    assertThat(kind.spell("Abc").form()).isEqualTo("Abc"));
        }
    }

    @Nested
    @DisplayName("what each type means")
    class WhatItMeans {

        @Test
        @DisplayName("a word and a get-word look up their declaration")
        void looksUpTheDeclarationForAWordOrGetWord() {
            assertThat(WordValue.of("a").looksUpItsDeclaration()).isTrue();
            assertThat(GetWordValue.of("a").looksUpItsDeclaration()).isTrue();
        }

        @Test
        @DisplayName("no other type looks up its declaration")
        void looksUpNoOtherDeclaration() {
            assertThat(SetWordValue.of("a").looksUpItsDeclaration()).isFalse();
            assertThat(LitWordValue.of("a").looksUpItsDeclaration()).isFalse();
            assertThat(RefinementValue.of("a").looksUpItsDeclaration()).isFalse();
            assertThat(IssueValue.of("a").looksUpItsDeclaration()).isFalse();
        }

        @Test
        @DisplayName("in a function spec a lit-word is soft quoted, a get-word hard quoted, and a word evaluated")
        void declaresAParameterForEach() {
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
        @DisplayName("the same spelling in one type is equal and hashes alike, whatever it is bound to")
        void holdsForTheSameKind() {
            AnyWordValue bound = GetWordValue.of("a").bindTo(Context.root());

            assertThat(bound).isEqualTo(GetWordValue.of("a"));
            assertThat(bound).hasSameHashCodeAs(GetWordValue.of("a"));
        }

        @Test
        @DisplayName("Rebol's own equality compares the names, whatever the type and the case")
        void rebolEqualityComparesNames() {
            assertThat(SetWordValue.of("a").isEqualTo(WordValue.of("A"), Sameness.insideASeries())).isTrue();
        }
    }
}
