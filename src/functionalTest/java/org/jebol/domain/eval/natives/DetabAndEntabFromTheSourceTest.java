package org.jebol.domain.eval.natives;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetabAndEntabFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("DETAB pads each tab to the next stop")
    class Detab {

        @Test
        @DisplayName("a tab at the start of a line is a whole stop of spaces")
        void padsAWholeStopAtTheStart() {
            assertThat(answerTo("{    x} == detab {^-x}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a tab after some characters pads only to the next stop")
        void padsToTheNextStop() {
            assertThat(answerTo("{a   x} == detab {a^-x}")).isEqualTo(TRUE);
            assertThat(answerTo("{abc x} == detab {abc^-x}")).isEqualTo(TRUE);
            assertThat(answerTo("{abcd    x} == detab {abcd^-x}")).isEqualTo(TRUE);
            assertThat(answerTo("{a   b   c} == detab {a^-b^-c}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a line feed starts the count again, and a carriage return is a column")
        void countsFromEachLine() {
            assertThat(answerTo("{abc^/d   e} == detab {abc^/d^-e}")).isEqualTo(TRUE);
            assertThat(answerTo("#{610D202078} == to binary! detab {a^M^-x}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a character takes as many columns as its UTF-8 bytes, as the C counts them")
        void countsTheBytesOfACharacter() {
            assertThat(answerTo("#{C3A9202078} == to binary! detab {é^-x}")).isEqualTo(TRUE);
            assertThat(answerTo("#{E282AC2078} == to binary! detab {€^-x}")).isEqualTo(TRUE);
            assertThat(answerTo("#{F09F98802020202078} == to binary! detab {😀^-x}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/size sets the stop")
        void takesTheStopFromSize() {
            assertThat(answerTo("{;       x} == detab/size {;^-x} 8")).isEqualTo(TRUE);
            assertThat(answerTo("{abc d} == detab/size {abc^-d} 2")).isEqualTo(TRUE);
            assertThat(answerTo("{a b} == detab/size {a^-b} 1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a stop that would ask for two gigabytes or more is past-end, as the C's buffer refuses it")
        void refusesAnAnswerPastTwoGigabytes() {
            assertThat(answerTo("""
                    e: try [detab/size {a^-b^-c} 2000000000] e/id""")).isEqualTo("past-end");
        }

        @Test
        @DisplayName("a binary is detabbed as its bytes")
        void detabsABinary() {
            assertThat(answerTo("detab #{3B0978}")).isEqualTo("#{3B20202078}");
        }

        @Test
        @DisplayName("from a position it works to the tail, counting from the position")
        void worksFromThePositionToTheTail() {
            assertThat(answerTo("{b   c} == detab next {ab^-c}")).isEqualTo(TRUE);
            assertThat(answerTo("{ef  g} == detab skip {abcdef^-g} 4")).isEqualTo(TRUE);
            assertThat(answerTo("detab next #{610962}")).isEqualTo("#{2020202062}");
        }

        @Test
        @DisplayName("the answer keeps the type it was given")
        void keepsTheType() {
            assertThat(answerTo("detab to file! {a^-b}")).isEqualTo("%a%20%20%20b");
        }
    }

    @Nested
    @DisplayName("ENTAB turns the spaces leading a line into tabs")
    class Entab {

        @Test
        @DisplayName("each whole stop of leading spaces is a tab")
        void turnsEachStopIntoATab() {
            assertThat(answerTo("{^-x} == entab {    x}")).isEqualTo(TRUE);
            assertThat(answerTo("{^-^-x} == entab {        x}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("spaces short of a stop are kept as spaces")
        void keepsAPartialStop() {
            assertThat(answerTo("{   x} == entab {   x}")).isEqualTo(TRUE);
            assertThat(answerTo("{^- x} == entab {     x}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("spaces after the first character of a line are left alone")
        void leavesTheRestOfALine() {
            assertThat(answerTo("{ab    x} == entab {ab    x}")).isEqualTo(TRUE);
            assertThat(answerTo("{^-a    b} == entab {    a    b}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("each line starts again after a line feed")
        void startsEachLineAgain() {
            assertThat(answerTo("{a^/^-b} == entab {a^/    b}")).isEqualTo(TRUE);
            assertThat(answerTo("{  ^/x} == entab {  ^/x}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a tab among the leading spaces is kept and drops the spaces before it")
        void keepsALeadingTab() {
            assertThat(answerTo("{^-x} == entab { ^-x}")).isEqualTo(TRUE);
            assertThat(answerTo("{^-^-x} == entab {^-    x}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("/size sets the stop")
        void takesTheStopFromSize() {
            assertThat(answerTo("{^-x  y} == entab/size {  x  y} 2")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a binary is entabbed as its bytes")
        void entabsABinary() {
            assertThat(answerTo("entab #{2020202078}")).isEqualTo("#{0978}");
        }

        @Test
        @DisplayName("spaces still waiting at the end are kept, where R3 drops them")
        void keepsTrailingSpaces() {
            assertThat(answerTo("{  } == entab {  }")).isEqualTo(TRUE);
            assertThat(answerTo("{a^/  } == entab {a^/  }")).isEqualTo(TRUE);
            assertThat(answerTo("{^-  } == entab {      }")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("from a position it works to the tail, where R3 loses the tail")
        void worksFromThePositionToTheTail() {
            assertThat(answerTo("{^-x} == entab next {a    x}")).isEqualTo(TRUE);
            assertThat(answerTo("entab next #{612020202078}")).isEqualTo("#{0978}");
        }
    }
}
