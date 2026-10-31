package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickingByAFractionOrAPairFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("PICK takes a decimal or a percent as Get_Num_Arg does, truncating it, for blocks, strings, binaries, pairs and tuples")
    void truncatesAFraction() {
        assertThat(answerTo("""
                reduce [pick [a b c] 2.7 pick {abc} 2.0 pick #{010203} 3.9 pick 1x2 2.0 pick 1.2.3 3.0 pick [a b c] 250%]"""))
                .isEqualTo("""
                        [b #"b" 3 2.0 3 b]""");
    }

    @Test
    @DisplayName("a fraction that truncates to nought picks nothing, and one past 32 bits is out of range")
    void truncatesToNoughtOrRefuses() {
        assertThat(answerTo("""
                reduce [pick [a b c] 0.9 pick [a b c] 50% (e: try [pick [a b c] 1e10] e/id)]""")).isEqualTo("[_ _ out-of-range]");
    }

    @Test
    @DisplayName("a vector takes a decimal and refuses a percent, as PD_Vector's Int32 does")
    void aVectorTakesADecimalButNotAPercent() {
        assertThat(answerTo("""
                v: make vector! [integer! 8 [5 6 7]]
                reduce [pick v 2.5 (e: try [pick v 50%] e/id)]""")).isEqualTo("[6 cannot-use]");
    }

    @Test
    @DisplayName("an image takes a pair, rounded half up, as PD_Image does, and a decimal truncated, and refuses a percent")
    void anImageTakesAPairRounded() {
        assertThat(answerTo("""
                img: make image! 3x2
                img/5: 9.9.9
                reduce [pick img 2x2 pick img 1.6x1.5 img/(1.6x1.5) pick img 5.9 (e: try [pick img 50%] e/id)]""")).isEqualTo("[9.9.9.255 9.9.9.255 9.9.9.255 9.9.9.255 cannot-use]");
    }

    @Test
    @DisplayName("POKE into an image takes a pair as PICK does")
    void pokesAnImageByAPair() {
        assertThat(answerTo("""
                img: make image! 3x2
                poke img 3x2 1.2.3
                img/6""")).isEqualTo("1.2.3.255");
    }
}
