package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldsOfAPostedEventFromTheSourceTest {

    @Test
    @DisplayName("a comma in a string is part of the string, not the next field")
    void aCommaStaysInTheString() {
        FieldsOfAPostedEvent said = new FieldsOfAPostedEvent("""
                {"kind":"line","text":"one, two"}\
                """);

        assertThat(said.text("kind")).contains("line");
        assertThat(said.text("text")).contains("one, two");
    }

    @Test
    @DisplayName("an escaped quotation mark comes back as a quotation mark")
    void anEscapedQuoteComesBack() {
        FieldsOfAPostedEvent said = new FieldsOfAPostedEvent("""
                {"text":"say \\"hi\\""}\
                """);

        assertThat(said.text("text")).contains("say \"hi\"");
    }

    @Test
    @DisplayName("and so do escaped newlines, tabs and a unicode escape")
    void theOtherEscapesComeBack() {
        assertThat(new FieldsOfAPostedEvent("""
                {"text":"a\\nb"}\
                """).text("text")).contains("a\nb");
        assertThat(new FieldsOfAPostedEvent("""
                {"text":"a\\tb"}\
                """).text("text")).contains("a\tb");
        assertThat(new FieldsOfAPostedEvent("""
                {"text":"\\u00e9t\\u00e9"}\
                """).text("text")).contains("été");
    }

    @Test
    @DisplayName("a whole file crosses as one string, commas, quotes and newlines included")
    void aFileCrossesWhole() {
        WhatThePageSaid said = new WhatThePageSaid("""
                {"kind":"file","path":"examples/r3-gui/sliders.r",\
                "text":"REBOL [Title: \\"Sliders, and more\\"]\\nview [title \\"x\\"]"}\
                """);

        assertThat(said.isAFile()).isTrue();
        assertThat(said.theFileName()).contains("examples/r3-gui/sliders.r");
        assertThat(said.theFileText()).contains("""
                REBOL [Title: "Sliders, and more"]\nview [title "x"]""");
    }

    @Test
    @DisplayName("a number is a number and a quoted one is not")
    void numbersAreNumbers() {
        assertThat(new FieldsOfAPostedEvent("""
                {"wide":800,"high":"600"}\
                """).wholeNumber("wide")).contains(800);
        assertThat(new FieldsOfAPostedEvent("""
                {"wide":800,"high":"600"}\
                """).wholeNumber("high")).isEmpty();
    }

    @Test
    @DisplayName("a field that is not there, and something that is not an object, are both nothing")
    void whatIsNotThereIsNothing() {
        assertThat(new FieldsOfAPostedEvent("""
                {"kind":"line"}\
                """).text("text")).isEmpty();
        assertThat(new FieldsOfAPostedEvent("not an object").saysNothing()).isTrue();
        assertThat(new FieldsOfAPostedEvent("{}").saysNothing()).isTrue();
    }
}
