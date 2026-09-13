package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ACommaEndsNothingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = """
                said: func [answer] [
                    either error? answer [ajoin ["!" answer/id]] [
                        ajoin [mold answer " " mold type? first answer]
                    ]
                ]
                each: func [written] [
                    collect [
                        foreach one written [
                            keep said try [load ajoin ["[" one "]"]]
                        ]
                    ]
                ]
                """ + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Nested
    @DisplayName("in a number it is the other decimal point")
    class InANumber {

        @Test
        @DisplayName("one comma reads as the point, wherever the digits sit")
        void oneCommaReadsAsThePoint() {
            assertThat(answerTo("""
                    each ["1,2" "1," ",1" "1,5%" "$1,5" "1:2,5"]""")).isEqualTo("""
                            ["[1.2] #(decimal!)" "[1.0] #(decimal!)" \
                            "[0.1] #(decimal!)" "[1.5%] #(percent!)" \
                            "[$1.5] #(money!)" "[0:01:02.5] #(time!)"]""");
        }

        @Test
        @DisplayName("and there is exactly one of it, spelt one way")
        void thereIsExactlyOneOfItSpeltOneWay() {
            assertThat(answerTo("""
                    each ["1,2,3" "1.2,3" ",.5" "1.,5" ",," ","]"""))
                    .isEqualTo("""
                            ["!invalid" "!invalid" "!invalid" "!invalid" \
                            "!invalid" "!invalid"]""");
        }

        @Test
        @DisplayName("a tuple still takes dots alone")
        void aTupleStillTakesDotsAlone() {
            assertThat(answerTo("""
                    each ["1.2.3" "1,2,3"]""")).isEqualTo("""
                            ["[1.2.3] #(tuple!)" "!invalid"]""");
        }
    }

    @Test
    @DisplayName("a word with a comma in it is invalid, not two words")
    void aWordWithACommaIsInvalid() {
        assertThat(answerTo("""
                each ["a,b" "a," "a,,b" ",a"]""")).isEqualTo("""
                        ["!invalid" "!invalid" "!invalid" "!invalid"]""");
    }

    @Test
    @DisplayName("a file, a url, an email, an issue and a tag all keep it")
    void theOnesThatRunToADelimiterAllKeepIt() {
        assertThat(answerTo("""
                each ["%a,b" "a:/x,y" "x@y,z" "#a,b" "<a,b>"]"""))
                .isEqualTo("""
                        ["[%a,b] #(file!)" "[a:/x,y] #(url!)" \
                        "[x@y,z] #(email!)" "[#a,b] #(issue!)" \
                        "[<a,b>] #(tag!)"]""");
    }

    @Test
    @DisplayName("and a string keeps it without any of this applying")
    void aStringKeepsItWithoutAnyOfThisApplying() {
        assertThat(answerTo("""
                each [{"a,b"}]""")).isEqualTo("""
                        [{["a,b"] #(string!)}]""");
    }
}
