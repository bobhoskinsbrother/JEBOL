package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ABoundWordSharesItsContextsNameTest {

    @Test
    @DisplayName("a word bound to a context carries the very name that context's slot holds")
    void carriesTheSlotsOwnName() {
        Context context = Context.root();
        ContextSlot slot = context.register("total", IntegerValue.of(1));

        AnyWordValue bound = WordValue.of(new StringBuilder("total").toString()).bindTo(context);

        assertThat(bound.canonical()).isSameAs(slot.canonical());
    }

    @Test
    @DisplayName("a word spelled in another case still carries the slot's own name")
    void carriesTheSlotsOwnNameWhateverItsCase() {
        Context context = Context.root();
        ContextSlot slot = context.register("Total", IntegerValue.of(1));

        AnyWordValue bound = WordValue.of("TOTAL").bindTo(context);

        assertThat(bound.canonical()).isSameAs(slot.canonical());
        assertThat(bound.spelling()).isEqualTo("TOTAL");
    }

    @Test
    @DisplayName("every kind of word shares the slot's name when bound")
    void sharesTheNameForEveryKind() {
        Context context = Context.root();
        ContextSlot slot = context.register("Total", IntegerValue.of(1));

        for (AnyWordValue unbound : new AnyWordValue[]{
                WordValue.of("TOTAL"), SetWordValue.of("TOTAL"), GetWordValue.of("TOTAL"),
                LitWordValue.of("TOTAL"), RefinementValue.of("TOTAL"), IssueValue.of("TOTAL")}) {
            assertThat(unbound.bindTo(context).canonical()).as(unbound.mold()).isSameAs(slot.canonical());
        }
    }

    @Test
    @DisplayName("a bound word turned into a word or set-word keeps the shared name")
    void keepsTheSharedNameAcrossShapes() {
        Context context = Context.root();
        ContextSlot slot = context.register("Total", IntegerValue.of(1));
        AnyWordValue bound = GetWordValue.of("TOTAL").bindTo(context);

        assertThat(bound.asWord().canonical()).isSameAs(slot.canonical());
        assertThat(bound.asSetWord().canonical()).isSameAs(slot.canonical());
    }

    @Test
    @DisplayName("a word bound to a context with no slot of its own for it keeps its own name")
    void keepsItsOwnNameWhereTheContextHasNoSlot() {
        Context above = Context.root();
        above.register("total", IntegerValue.of(1));
        Context beneath = Context.makeChildOf(above);
        WordValue unbound = WordValue.of("Total");

        AnyWordValue bound = unbound.bindTo(beneath);

        assertThat(bound.canonical()).isSameAs(unbound.canonical());
        assertThat(bound.findBoundSlot().value()).isEqualTo(IntegerValue.of(1));
    }

    @Test
    @DisplayName("an unbound word whose spelling is already lower case is its own name")
    void isItsOwnNameWhenAlreadyLowerCase() {
        String spelling = new StringBuilder("total").toString();

        assertThat(WordValue.of(spelling).canonical()).isSameAs(spelling);
    }
}
