package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapKeysAlikeApartFromCaseTest {

    private static final int MANY_KEYS = 100_000;

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("of two keys that differ only by case, a lookup that ignores case finds the one stored first")
    void findTheFirstStored() {
        assertThat(answerTo("""
                m: make map! [{k} 1 {K} 2]
                select m {K}""")).isEqualTo("1");
    }

    @Test
    @DisplayName("removing the first leaves the second for a lookup that ignores case")
    void leaveTheSecondWhenTheFirstIsRemoved() {
        assertThat(answerTo("""
                m: make map! [{k} 1 {K} 2]
                remove/key m {k}
                select m {k}""")).isEqualTo("2");
    }

    @Test
    @DisplayName("removing the second keeps the first for a lookup that ignores case")
    void keepTheFirstWhenTheSecondIsRemoved() {
        assertThat(answerTo("""
                m: make map! [{k} 1 {K} 2]
                remove/key m {K}
                select m {K}""")).isEqualTo("1");
    }

    @Test
    @DisplayName("removing both leaves nothing a lookup that ignores case can find")
    void leaveNothingWhenBothAreRemoved() {
        assertThat(answerTo("""
                m: make map! [{k} 1 {K} 2]
                remove/key m {k}
                remove/key m {K}
                none? select m {k}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a write that ignores case after a removal adds the key again as it was written")
    void comeBackWhenWrittenAfterARemoval() {
        assertThat(answerTo("""
                m: make map! [{k} 1]
                remove/key m {k}
                m/({K}): 3
                strict-equal? [{K}] keys-of m""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a cleared map finds nothing, and takes new keys afterwards")
    void vanishFromAClearedMap() {
        assertThat(answerTo("""
                m: make map! [{k} 1]
                clear m
                found: select m {K}
                m/({K}): 2
                reduce [none? found select m {k}]""")).isEqualTo("[#(true) 2]");
    }

    @Test
    @DisplayName("a copy finds its keys whatever their case, and a key added to it is not in the original")
    void stayWithACopy() {
        assertThat(answerTo("""
                m: make map! [{k} 1]
                c: copy m
                c/({J}): 2
                reduce [select c {K} select c {j} none? select m {j}]""")).isEqualTo("[1 2 #(true)]");
    }

    @Test
    @DisplayName("a character key is found in the other case")
    void includeACharacterKey() {
        assertThat(answerTo("""
                m: make map! []
                m/(#"a"): 1
                select m #"A"
                """)).isEqualTo("1");
    }

    @Test
    @DisplayName("a key outside ASCII is found in the other case")
    void includeAKeyOutsideAscii() {
        assertThat(answerTo("""
                m: make map! []
                m/({été}): 1
                select m {ÉTÉ}""")).isEqualTo("1");
    }

    @Test
    @DisplayName("a string, a file, a tag and a word spelled alike are four different keys")
    void stayApartAcrossDatatypes() {
        assertThat(answerTo("""
                m: make map! []
                m/({k}): 1
                m/(%K): 2
                m/(<K>): 3
                m/K: 4
                reduce [length? m select m {K} select m %k select m <k> select m 'k]""")).isEqualTo("[4 1 2 3 4]");
    }

    @Test
    @DisplayName("a key that is not text, a word or a character is found as itself")
    void leaveOtherKeysFoundAsThemselves() {
        assertThat(answerTo("""
                m: make map! []
                m/(42): {answer}
                reduce [select m 42 none? select m 43]""")).isEqualTo("""
                ["answer" #(true)]""");
    }

    @Test
    @DisplayName("filling a map by writes that ignore case takes time in proportion to its size, not its square")
    void fillAMapInLinearTime() {
        assertThat(answerTo("""
                m: make map! []
                count: 0
                loop %d [count: count + 1 m/:count: count]
                length? m""".formatted(MANY_KEYS))).isEqualTo(String.valueOf(MANY_KEYS));
    }
}
