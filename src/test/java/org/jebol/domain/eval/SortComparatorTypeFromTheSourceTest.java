package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SORT/compare hands the comparator values of the series' own kind, from
 * {@code series-test.r3}. A byte of a binary arrives as a char; a whole record
 * of a binary under /all arrives as a binary; a string's element is a char.
 */
class SortComparatorTypeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a byte of a binary reaches the comparator as a char")
    void binaryElementIsAChar() {
        assertThat(answerTo("""
                ok: true
                sort/compare #{030201} func [a b] [unless char? a [ok: false] a < b]
                ok""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a whole binary record under /all reaches the comparator as a binary")
    void binaryRecordIsABinary() {
        assertThat(answerTo("""
                ok: true
                sort/all/compare #{030201}
                    func [a b] [unless binary? a [ok: false] (first a) < (first b)]
                ok""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an element of a string reaches the comparator as a char")
    void stringElementIsAChar() {
        assertThat(answerTo("""
                ok: true
                sort/compare "cba" func [a b] [unless char? a [ok: false] a < b]
                ok""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain sort of a binary still orders its bytes")
    void plainBinarySortStillOrders() {
        assertThat(answerTo("""
                (sort #{030201}) = #{010203}""")).isEqualTo("#(true)");
    }
}
