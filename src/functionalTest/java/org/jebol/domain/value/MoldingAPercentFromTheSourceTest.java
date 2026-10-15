package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoldingAPercentFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String THE_SPREAD_OF_SIZES =
            "[0% 1% 50% 100% 1.5% -2.5% 0.001% 1e-8% 3e34% "
                    + "1e15% 1e16% 1e17% -0% 12.345% 1e-6% 1e-7%]";

    @Test
    @DisplayName("MOLD writes a percent the way a real Rebol writes it")
    void moldWritesAPercentTheWayRebolDoes() {
        assertThat(answerTo(
                "collect [foreach v " + THE_SPREAD_OF_SIZES + " [keep mold v]]"))
                .isEqualTo("""
                        ["0%" "1%" "50%" "100%" "1.5%" "-2.5%" "0.001%" "1e-8%" \
                        "3e34%" "1e15%" "1e16%" "1e17%" "-0%" "12.345%" \
                        "0.000001%" "1e-7%"]""");
    }

    @Test
    @DisplayName("and MOLD/ALL writes the digits the stored double really has")
    void moldAllWritesTheDigitsTheStoredDoubleReallyHas() {
        assertThat(answerTo(
                "collect [foreach v " + THE_SPREAD_OF_SIZES + " [keep mold/all v]]"))
                .isEqualTo("""
                        ["0%" "1%" "50%" "100%" "1.4999999999999999%" \
                        "-2.5000000000000001%" "0.0010000000000000001%" "1e-8%" \
                        "3.0000000000000003e34%" "1000000000000000%" \
                        "10000000000000000%" "1e17%" "-0%" "12.345%" \
                        "0.000001%" "9.9999999999999986e-8%"]""");
    }

    @Test
    @DisplayName("a radix point with nothing after it is dropped, where a decimal keeps it")
    void aRadixPointWithNothingAfterItIsDropped() {
        assertThat(answerTo("reduce [mold 3e34%  mold 3e34  mold 1e-8%  mold 1e-8]"))
                .isEqualTo("""
                        ["3e34%" "3.0e34" "1e-8%" "1.0e-8"]""");
    }

    @Test
    @DisplayName("and what it writes reads back as the value it was")
    void whatItWritesReadsBackAsTheValueItWas() {
        assertThat(answerTo(
                "collect [foreach v " + THE_SPREAD_OF_SIZES + " ["
                        + "keep all [v = load mold v  v = load mold/all v]]]"))
                .isEqualTo("""
                        [#(true) #(true) #(true) #(true) #(true) #(true) #(true) \
                        #(true) #(true) #(true) #(true) #(true) #(true) #(true) \
                        #(true) #(true)]""");
    }
}
