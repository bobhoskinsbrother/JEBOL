package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WordCharactersTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String idOf(String source) {
        return answerTo("e: try [load " + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a word may not mix letters with an angle bracket")
    void aMixedLexemeIsRefused() {
        assertThat(idOf("{a<b}")).isEqualTo("invalid");
        assertThat(idOf("{a>b}")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("a run of only symbols is a word, whatever it contains")
    void anAllSymbolRunIsAWord() {
        assertThat(answerTo("mold load {<}")).isEqualTo("\"<\"");
        assertThat(answerTo("mold load {<=}")).isEqualTo("\"<=\"");
        assertThat(answerTo("mold load {-->}")).isEqualTo("\"-->\"");
    }

    @Test
    @DisplayName("a complete tag is still a tag")
    void aTagIsUnaffected() {
        assertThat(answerTo("mold load {<a>}")).isEqualTo("\"<a>\"");
    }

    @Test
    @DisplayName("a number followed by a symbol run splits in two")
    void aNumberSplitsFromWhatFollows() {
        assertThat(answerTo("mold load {1<}")).isEqualTo("\"[1 <]\"");
        assertThat(answerTo("mold load {1.2<}")).isEqualTo("\"[1.2 <]\"");
        assertThat(answerTo("mold load {19-Jan-2010<}"))
                .isEqualTo("\"[19-Jan-2010 <]\"");
    }

    @Test
    @DisplayName("what follows has to be a symbol run of its own")
    void aNumberDoesNotSplitFromAMixedRun() {
        assertThat(idOf("{1<2}")).isEqualTo("invalid");
    }

    @Test
    @DisplayName("a word prefix never splits, which is the whole difference")
    void aWordPrefixNeverSplits() {
        assertThat(idOf("{a<}")).isEqualTo("invalid");
        assertThat(answerTo("mold load {1<}")).isEqualTo("\"[1 <]\"");
    }

    @Test
    @DisplayName("an ordinary word is unaffected")
    void ordinaryWordsStillRead() {
        assertThat(answerTo("mold load {abc}")).isEqualTo("\"abc\"");
        assertThat(answerTo("mold load {a-b?}")).isEqualTo("\"a-b?\"");
        assertThat(answerTo("mold load {a.b}")).isEqualTo("\"a.b\"");
    }

    @Test
    @DisplayName("a tag may follow a number as readily as a word can")
    void aTagFollowsANumber() {
        assertThat(answerTo("(load {1.0<a>}) = [1.0 <a>]")).isEqualTo("#(true)");
        assertThat(answerTo("(load {1.#INF<a>}) = [1.#INF <a>]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the angle bracket wins over an earlier illegal character")
    void theBracketEndsTheValue() {
        assertThat(answerTo("(load {1.#INF<}) = [1.#INF <]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a lexeme starting with a bracket is a word only if it is all symbols")
    void aBracketLedLexemeNeedsSymbolsThroughout() {
        assertThat(idOf("{1<2}")).isEqualTo("invalid");
    }
}
