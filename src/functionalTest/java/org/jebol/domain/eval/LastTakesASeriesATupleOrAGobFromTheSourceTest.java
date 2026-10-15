package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LastTakesASeriesATupleOrAGobFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a tuple answers the last part that was written")
    void aTupleAnswersTheLastPartThatWasWritten() {
        assertThat(answerTo("""
                reduce [last 1.2.3  last 1.2.3.4  last 1.2.3.0  last 0.0.0]"""))
                .isEqualTo("[3 4 0 0]");
    }

    @Test
    @DisplayName("a series answers its last item, and a gob its last child")
    void aSeriesAnswersItsLastItemAndAGobItsLastChild() {
        assertThat(answerTo("""
                holder: make gob! []
                append holder make gob! [size: 2x2]
                reduce [last "ab"  last [1 2]  last holder  last make gob! []]"""))
                .isEqualTo("""
                        [#"b" 2 make gob! [offset: 0x0 size: 2x2] _]""");
    }

    @Test
    @DisplayName("and anything else is refused by the declaration, naming the parameter")
    void anythingElseIsRefusedByTheDeclaration() {
        assertThat(answerTo("""
                collect [
                    foreach subject reduce [:print 1 1x1 #(none) make map! [a 1]] [
                        keep/only either error? e: try [last :subject] [
                            reduce [e/id e/arg1 e/arg2]
                        ] ['accepted]
                    ]
                ]""")).isEqualTo("""
                        [[expect-arg last value] [expect-arg last value] \
                        [expect-arg last value] [expect-arg last value] \
                        [expect-arg last value]]""");
    }
}
