package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class CompressionLevelsOfOneMethod {

    protected abstract String method();

    protected abstract int top();

    protected abstract int levelNobodyAskedFor();

    private String answerTo(String source) {
        String withData = "method: '" + method() + "\n"
                + "top: " + top() + "\n"
                + "data: {Lorem ipsum dolor sit amet, consectetur adipisici elit, sed eiusmod tempor incidunt ut labore.}\n"
                + source;
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(withData);
        return interpreter.display(interpreter.run(withData));
    }

    @Test
    @DisplayName("one above the top, far above it, below zero and far below it all answer what the top answers")
    void clampsEveryLevelOutsideTheRangeToTheTop() {
        assertThat(answerTo("""
                collect [
                    foreach level [1 99 -2 -99] [
                        keep (compress/level data method either level = 1 [top + 1] [level])
                            = compress/level data method top
                    ]
                ]""")).isEqualTo("[#(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("no level from minus ninety-nine to ninety-nine is refused")
    void refusesNoLevel() {
        assertThat(answerTo("""
                refused: copy []
                foreach level [-99 -2 -1 0 1 13 99] [
                    if error? e: try [compress/level data method level] [append refused reduce [level e/id]]
                ]
                refused""")).isEqualTo("[]");
    }

    @Test
    @DisplayName("minus one is the level nobody asked for, and that is the method's own default")
    void defaultsAsTheCDoes() {
        assertThat(answerTo("""
                reduce [
                    (compress/level data method -1) = compress data method
                    (compress data method) = compress/level data method %d
                ]""".formatted(levelNobodyAskedFor()))).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("compress and decompress are inverses at eighteen levels")
    void readsBackAtEveryLevel() {
        assertThat(answerTo("""
                bad: copy []
                foreach level [-99 -2 -1 0 1 2 3 4 5 6 7 8 9 10 11 12 13 99] [
                    either error? e: try [packed: compress/level data method level] [
                        append bad reduce [level 'compress e/id]
                    ][
                        either error? e2: try [back: decompress packed method] [
                            append bad reduce [level 'decompress e2/id]
                        ][
                            unless data = to string! back [append bad reduce [level 'differs]]
                        ]
                    ]
                ]
                bad""")).isEqualTo("[]");
    }
}
