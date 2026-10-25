package org.jebol.domain.value;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoldLaysAStructAndAnEventOverLinesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String A_STRUCT_OF_TWO_FIELDS =
            "s: make struct! [a [int32!] b [int32!]]\n";

    @Nested
    @DisplayName("a struct, which the C lays out over lines")
    class AStruct {

        @Test
        @DisplayName("plain MOLD names the spec by a number and puts each value on its own line")
        void laysTheValuesOverLines() {
            assertThat(answerTo(A_STRUCT_OF_TWO_FIELDS + """
                    digit: charset {0123456789}
                    lines: split mold s newline
                    reduce [
                        length? lines
                        parse first lines [{#(struct! } some digit { [} end]
                        second lines
                        third lines
                        fourth lines
                    ]""")).isEqualTo("""
                    [4 #(true) "    a: 0" "    b: 0" "])"]""");
        }

        @Test
        @DisplayName("MOLD/ALL writes the spec out, over lines of its own")
        void laysTheSpecOverLinesToo() {
            assertThat(answerTo(A_STRUCT_OF_TWO_FIELDS + """
                    (mold/all s) = rejoin [
                        {#(struct! [} newline
                        {    a [int32!]} newline
                        {    b [int32!]} newline
                        {] [} newline
                        {    a: 0} newline
                        {    b: 0} newline
                        {])}
                    ]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("MOLD/FLAT puts it back on one line, and /ALL/FLAT needs no number")
        void collapsesToOneLine() {
            assertThat(answerTo(A_STRUCT_OF_TWO_FIELDS + """
                    digit: charset {0123456789}
                    reduce [
                        parse mold/flat s [{#(struct! } some digit { [a: 0 b: 0])} end]
                        (mold/all/flat s) = {#(struct! [a [int32!] b [int32!]] [a: 0 b: 0])}
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("inside an object the lines are indented to where the struct sits")
        void indentsToWhereItSits() {
            assertThat(answerTo(A_STRUCT_OF_TWO_FIELDS + """
                    held: make object! [held: none]
                    set in held 'held s
                    lines: split mold held newline
                    reduce [length? lines  third lines  fourth lines  fifth lines  last lines]"""))
                    .isEqualTo("""
                    [6 "        a: 0" "        b: 0" "    ])" "]"]""");
        }

        @Test
        @DisplayName("the number is the same every time the same struct is molded")
        void namesTheSameStructTheSameWay() {
            assertThat(answerTo(A_STRUCT_OF_TWO_FIELDS + """
                    (mold/flat s) = (mold/flat s)""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an array field keeps its values on the one line")
        void keepsAnArrayFieldOnOneLine() {
            assertThat(answerTo("""
                    lines: split mold make struct! [c [int32! [3]]] newline
                    reduce [length? lines second lines]""")).isEqualTo("""
                    [3 "    c: [0 0 0]"]""");
        }
    }

    @Nested
    @DisplayName("an event, whose fields the C molds even when forming")
    class AnEvent {

        @Test
        @DisplayName("FORM writes the field as a set-word, as MOLD does")
        void formsTheFieldAsASetWord() {
            assertThat(answerTo("""
                    e: make event! [type: 'down]
                    (form e) = (mold e)""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and lays the fields over lines until /FLAT")
        void laysTheFieldsOverLines() {
            assertThat(answerTo("""
                    e: make event! [type: 'down]
                    reduce [
                        (mold e) = rejoin [{make event! [} newline {    type: 'down} newline {]}]
                        (mold/flat e) = {make event! [type: 'down]}
                        (mold/flat/all e) = {#(event! [type: 'down])}
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }
    }

    @Nested
    @DisplayName("forming a block, which delimits with a space it does not always need")
    class FormingABlock {

        @Test
        @DisplayName("no space follows an item that ended a line")
        void putsNoSpaceAfterALine() {
            assertThat(answerTo("""
                    reduce [
                        (reform [{a} newline {b}]) = {a ^/b}
                        (reform [1 newline 2 newline 3]) = {1 ^/2 ^/3}
                        (form reduce [{x} #"^/" {y}]) = {x ^/y}
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("and a space still follows one that did not")
        void keepsTheSpaceOtherwise() {
            assertThat(answerTo("""
                    reduce [
                        (reform [{a} {b}]) = {a b}
                        (reform [1 2 3]) = {1 2 3}
                    ]""")).isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("so an event inside a block forms without a space before the bracket")
        void formsAnEventInsideABlock() {
            assertThat(answerTo("""
                    e: make event! [type: 'down]
                    (form reduce [e]) = rejoin [
                        {make event! [} newline {    type: 'down} newline {]}
                    ]""")).isEqualTo("#(true)");
        }
    }
}
