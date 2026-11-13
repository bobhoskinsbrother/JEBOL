package org.jebol.domain.eval;

import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Context;
import org.jebol.domain.value.DefinedFunctionValue;
import org.jebol.domain.value.FunctionValue;
import org.jebol.domain.value.RefinementValue;
import org.jebol.domain.value.WordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ACallFrameSharesItsBodysNamesTest {

    private static FunctionValue functionTakingTotal() {
        return Binder.bindFunctionsBody(
                BlockValue.of(List.of(WordValue.of("Total"), RefinementValue.of("Only"))),
                BlockValue.of(List.of(WordValue.of("TOTAL"), WordValue.of("only"))),
                Context.root());
    }

    private static AnyWordValue bodyWordAt(DefinedFunctionValue function, int offset) {
        AnyBlockValue body = function.body();
        return (AnyWordValue) body.storage().at(body.index() + offset);
    }

    @Test
    @DisplayName("a function's call frame holds the very names its body's words carry")
    void sharesTheBodysNames() {
        FunctionValue function = functionTakingTotal();
        Context frame = function.makeFreshCallFrame();

        assertThat(frame.ownSlotAt(0).canonical()).isSameAs(bodyWordAt(function, 0).canonical());
        assertThat(frame.ownSlotAt(1).canonical()).isSameAs(bodyWordAt(function, 1).canonical());
    }

    @Test
    @DisplayName("a closure made from a function shares the same names in its own frames")
    void sharesTheBodysNamesInAClosure() {
        FunctionValue function = functionTakingTotal();
        Context frame = function.asClosure().makeFreshCallFrame();

        assertThat(frame.ownSlotAt(0).canonical()).isSameAs(bodyWordAt(function, 0).canonical());
        assertThat(frame.ownSlotAt(1).canonical()).isSameAs(bodyWordAt(function, 1).canonical());
    }
}
