package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRIM refuses refinements that ask for two different things.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1, the whole matrix of refinements against string, binary and
 * block.
 *
 * <p>Two quarrels, both raising bad-refines. /HEAD and /TAIL say which end
 * to work on while /ALL and /WITH say to work everywhere. And /WITH, /AUTO
 * and /LINES are about text, so a binary or a block refuses all three
 * while still accepting /HEAD, /TAIL and /ALL.
 */
class TrimRefinementsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id of the error a snippet raises, or "no-error" if it raises none. */
    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("naming an end and asking for everywhere is refused")
    void oneEndAndEverywhereContradict() {
        assertThat(errorIdOf("trim/head/all \"  a  \"")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/tail/all \"  a  \"")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/head/with \"-a-\" \"-\"")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/tail/with \"-a-\" \"-\"")).isEqualTo("bad-refines");
    }

    @Test
    @DisplayName("/AUTO is not in that quarrel")
    void autoCombinesWithAnEnd() {
        assertThat(answerTo("(trim/auto/tail \"  a  \") = \"a\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a binary and a block refuse the three that are about text")
    void theTextRefinementsNeedText() {
        assertThat(errorIdOf("trim/with #{0011} #{11}")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/auto #{00}")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/lines #{00}")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/with [1] 1")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/auto [1]")).isEqualTo("bad-refines");
    }

    @Test
    @DisplayName("a binary and a block still take the other three")
    void theEndRefinementsWorkOnBoth() {
        assertThat(answerTo("(trim #{0011}) = #{11}")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/head #{0011}) = #{11}")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/tail #{0011}) = #{0011}")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/all #{0011}) = #{11}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a block drops nones the way a string drops spaces")
    void aBlockDropsNones() {
        assertThat(answerTo("(trim [1 _ 2]) = [1 _ 2]"))
                .as("nothing at either end to drop")
                .isEqualTo("#(true)");
        assertThat(answerTo("(trim/head [_ 1]) = [1]")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/all [1 _ 2]) = [1 2]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty series is refused for the same reason, not a different one")
    void theDegenerateSeriesStillChecksTheRefinements() {
        assertThat(errorIdOf("trim/head/all []")).isEqualTo("bad-refines");
        assertThat(errorIdOf("trim/tail/all []")).isEqualTo("bad-refines");
    }

    @Test
    @DisplayName("the ordinary calls are unaffected")
    void theStringCasesStillWork() {
        assertThat(answerTo("(trim \"  a  \") = \"a\"")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/head \"  a  \") = \"a  \"")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/tail \"  a  \") = \"  a\"")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/all \" a b \") = \"ab\"")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/with \"-a-\" \"-\") = \"a\"")).isEqualTo("#(true)");
        assertThat(answerTo("(trim/lines \"a^/b\") = \"a b\"")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("TRIM changes the series it was given")
    void trimChangesInPlace() {
        assertThat(answerTo("b: copy [_ 1 _] trim b b = [1]")).isEqualTo("#(true)");
    }
}
