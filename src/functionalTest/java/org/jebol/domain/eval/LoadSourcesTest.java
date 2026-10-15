package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoadSourcesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a binary is read as UTF-8")
    void aBinaryIsReadAsText() {
        assertThat(answerTo("\"3\" = load #{223322}")).isEqualTo("#(true)");
        assertThat(answerTo("load #{31}")).isEqualTo("1");
    }

    @Test
    @DisplayName("a byte order mark at the front is dropped")
    void aByteOrderMarkIsNotPartOfTheSource() {
        assertThat(answerTo("\"3\" = load #{EFBBBF223322}")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a block loads each item and keeps each answer whole")
    void aBlockIsABlockOfSources() {
        assertThat(answerTo("(load [\"1\" \"2\"]) = [1 2]")).isEqualTo("#(true)");
        assertThat(answerTo("(load [#{31} \"2\"]) = [1 2]"))
                .as("the items may be of different kinds")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a source holding two values arrives nested")
    void aMultipleValuedSourceKeepsItsBlock() {
        assertThat(answerTo("(load [\"print 'a\" \"print 'b\"]) = [[print 'a] [print 'b]]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("loading nothing answers an empty block")
    void theDegenerateSourcesLoadToNothing() {
        assertThat(answerTo("empty? load \"\"")).isEqualTo("#(true)");
        assertThat(answerTo("empty? load #{}")).isEqualTo("#(true)");
        assertThat(answerTo("empty? load []")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a source that is not a source is refused")
    void aWrongTypeIsRefused() {
        assertThat(errorIdOf("load 'word")).isEqualTo("expect-arg");
        assertThat(errorIdOf("load 5")).isEqualTo("expect-arg");
        assertThat(errorIdOf("load [1]"))
                .as("an item inside a block is checked too")
                .isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("/ALL keeps the block around a single value")
    void allTurnsOffTheUnwrapping() {
        assertThat(answerTo("load \"1\"")).isEqualTo("1");
        assertThat(answerTo("(load/all \"1\") = [1]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a string with several values loads as a block either way")
    void severalValuesAreABlockWithOrWithoutAll() {
        assertThat(answerTo("(load \"1 2\") = [1 2]")).isEqualTo("#(true)");
        assertThat(answerTo("(load/all \"1 2\") = [1 2]")).isEqualTo("#(true)");
    }
}
