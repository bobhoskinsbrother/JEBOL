package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeclaredArgumentTypesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureFrom(String call) {
        return answerTo("""
                e: try [""" + call + """
                ] rejoin [e/id " | " mold e/arg1 " " mold e/arg2]""");
    }

    @Test
    @DisplayName("TRIM on an image raises cannot-use, not expect-arg")
    void trimOnAnImageRaisesCannotUse() {
        assertThat(failureFrom("""
                trim make image! [2x2]"""))
                .isEqualTo("\"cannot-use | trim: #(image!)\"");
    }

    @Test
    @DisplayName("TRIM on a vector raises cannot-use, not expect-arg")
    void trimOnAVectorRaisesCannotUse() {
        assertThat(failureFrom("""
                trim make vector! [integer! 8 [1 2 3]]"""))
                .isEqualTo("\"cannot-use | trim: #(vector!)\"");
    }

    @Test
    @DisplayName("TRIM on an integer is outside the declaration and raises expect-arg")
    void trimOnAnIntegerRaisesExpectArg() {
        assertThat(failureFrom("trim 1"))
                .isEqualTo("\"expect-arg | trim series\"");
    }

    @Test
    @DisplayName("TRIM still trims a string")
    void trimStillTrimsAString() {
        assertThat(answerTo("""
                trim "  a  \"""")).isEqualTo("\"a\"");
    }

    @Test
    @DisplayName("TRIM still trims a block")
    void trimStillTrimsABlock() {
        assertThat(answerTo("""
                trim [#(none) 1 #(none)]""")).isEqualTo("[1]");
    }

    @Test
    @DisplayName("TRIM still takes an object")
    void trimStillWorksOnAnObject() {
        assertThat(answerTo("""
                type? trim make object! [a: 1]""")).isEqualTo("#(object!)");
    }

    @Test
    @DisplayName("SET refuses a set-word, naming the function and the parameter")
    void setRefusesASetWord() {
        assertThat(failureFrom("""
                set quote a: 1""")).isEqualTo("\"expect-arg | set word\"");
    }

    @Test
    @DisplayName("SET refuses a get-word, naming the function and the parameter")
    void setRefusesAGetWord() {
        assertThat(failureFrom("""
                set quote :a 1""")).isEqualTo("\"expect-arg | set word\"");
    }

    @Test
    @DisplayName("SET refuses an issue, naming the parameter rather than the value")
    void setRefusesAnIssueNamingTheParameter() {
        assertThat(failureFrom("set #ab 1"))
                .isEqualTo("\"expect-arg | set word\"");
    }

    @Test
    @DisplayName("SET refuses a refinement, naming the parameter rather than the value")
    void setRefusesARefinementNamingTheParameter() {
        assertThat(failureFrom("set /ab 1"))
                .isEqualTo("\"expect-arg | set word\"");
    }

    @Test
    @DisplayName("SET refuses an integer, naming the parameter")
    void setRefusesAnInteger() {
        assertThat(failureFrom("set 1 1"))
                .isEqualTo("\"expect-arg | set word\"");
    }

    @Test
    @DisplayName("SET still assigns through a word and a lit-word")
    void setStillAssignsThroughAWordAndALitWord() {
        assertThat(answerTo("set 'a 1 a")).isEqualTo("1");
        assertThat(answerTo("""
                set quote 'b 2 b""")).isEqualTo("2");
    }

    @Test
    @DisplayName("SET still assigns through a path, a block and an object")
    void setStillAssignsThroughAPathABlockAndAnObject() {
        assertThat(answerTo("""
                o: make object! [a: 1] set 'o/a 2 o/a""")).isEqualTo("2");
        assertThat(answerTo("""
                set [x y] [1 2] x""")).isEqualTo("1");
        assertThat(answerTo("""
                set make object! [q: 1] 5""")).isEqualTo("5");
    }

    @Test
    @DisplayName("TAIL? takes the five datatypes that act like a series as well")
    void tailTakesTheFiveThatActLikeASeries() {
        assertThat(answerTo("""
                tail? charset "ab\"""")).isEqualTo("#(false)");
        assertThat(answerTo("tail? series!")).isEqualTo("#(false)");
        assertThat(answerTo("""
                tail? make map! [a 1]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("PARSE takes an image and a vector, which are series too")
    void parseTakesAnImageAndAVector() {
        assertThat(answerTo("""
                parse make image! [2x2] []""")).isEqualTo("#(false)");
        assertThat(answerTo("""
                parse make vector! [integer! 8 [1]] []""")).isEqualTo("#(false)");
    }
}
