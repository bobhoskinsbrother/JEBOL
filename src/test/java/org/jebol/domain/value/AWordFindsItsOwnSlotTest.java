package org.jebol.domain.value;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AWordFindsItsOwnSlotTest {

    private static String errorIdOf(Throwable thrown) {
        return ((Raised) thrown).error().errorId();
    }

    @Test
    @DisplayName("a word bound where it is held finds the slot holding its value")
    void aWordBoundWhereItIsHeld() {
        Context context = Context.root();
        context.set("x", IntegerValue.of(1));

        assertThat(WordValue.of("x").boundTo(context).boundSlot().value())
                .isEqualTo(IntegerValue.of(1));
    }

    @Test
    @DisplayName("a word bound beneath where it is held finds the slot above")
    void aWordBoundBeneathWhereItIsHeld() {
        Context above = Context.root();
        above.set("x", IntegerValue.of(1));
        Context beneath = Context.childOf(above);

        assertThat(WordValue.of("x").boundTo(beneath).boundSlot().value())
                .isEqualTo(IntegerValue.of(1));
    }

    @Test
    @DisplayName("the case a word is spelled in does not matter")
    void spellingCaseDoesNotMatter() {
        Context context = Context.root();
        context.set("Foo", IntegerValue.of(1));

        assertThat(WordValue.of("FOO").boundTo(context).boundSlot().value())
                .isEqualTo(IntegerValue.of(1));
    }

    @Test
    @DisplayName("the slot is the live one, so writing through it is seen by the context")
    void theSlotIsTheLiveOne() {
        Context context = Context.root();
        context.set("x", IntegerValue.of(1));

        WordValue.of("x").boundTo(context).boundSlot().setValue(IntegerValue.of(2));

        assertThat(context.slotFor("x").value()).isEqualTo(IntegerValue.of(2));
    }

    @Test
    @DisplayName("an unbound word has no slot, and says so as not-defined")
    void anUnboundWordIsRefused() {
        assertThatThrownBy(() -> WordValue.of("x").boundSlot())
                .isInstanceOf(Raised.class)
                .extracting(AWordFindsItsOwnSlotTest::errorIdOf)
                .isEqualTo("not-defined");
    }

    @Test
    @DisplayName("a word bound to a context that does not know it is refused the same way")
    void aWordItsContextDoesNotKnowIsRefused() {
        Context context = Context.root();
        context.set("y", IntegerValue.of(1));

        assertThatThrownBy(() -> WordValue.of("x").boundTo(context).boundSlot())
                .isInstanceOf(Raised.class)
                .extracting(AWordFindsItsOwnSlotTest::errorIdOf)
                .isEqualTo("not-defined");
    }
}
