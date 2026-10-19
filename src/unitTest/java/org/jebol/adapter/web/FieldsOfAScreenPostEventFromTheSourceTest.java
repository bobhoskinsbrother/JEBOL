package org.jebol.adapter.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldsOfAScreenPostEventFromTheSourceTest {

    @Test
    @DisplayName("a comma in a string is part of the string, not the next field")
    void keepsACommaInTheString() {
        EventFields said = new EventFields("""
                {"type":"line","text":"one, two"}\
                """);

        assertThat(said.text("type")).contains("line");
        assertThat(said.text("text")).contains("one, two");
    }

    @Test
    @DisplayName("an escaped quotation mark comes back as a quotation mark")
    void bringsBackAnEscapedQuote() {
        EventFields said = new EventFields("""
                {"text":"say \\"hi\\""}\
                """);

        assertThat(said.text("text")).contains("say \"hi\"");
    }

    @Test
    @DisplayName("and so do escaped newlines, tabs and a unicode escape")
    void bringsBackTheOtherEscapes() {
        assertThat(new EventFields("""
                {"text":"a\\nb"}\
                """).text("text")).contains("a\nb");
        assertThat(new EventFields("""
                {"text":"a\\tb"}\
                """).text("text")).contains("a\tb");
        assertThat(new EventFields("""
                {"text":"\\u00e9t\\u00e9"}\
                """).text("text")).contains("été");
    }

    @Test
    @DisplayName("a whole file crosses as one string, commas, quotes and newlines included")
    void carriesAFileWhole() {
        PageEvent said = new PageEvent("""
                {"type":"file","path":"examples/r3-gui/sliders.r",\
                "text":"REBOL [Title: \\"Sliders, and more\\"]\\nview [title \\"x\\"]"}\
                """);

        assertThat(said.isFile()).isTrue();
        assertThat(said.readFileName()).contains("examples/r3-gui/sliders.r");
        assertThat(said.readFileText()).contains("""
                REBOL [Title: "Sliders, and more"]\nview [title "x"]""");
    }

    @Test
    @DisplayName("a number is a number and a quoted one is not")
    void keepsNumbersAsNumbers() {
        assertThat(new EventFields("""
                {"wide":800,"high":"600"}\
                """).readWholeNumber("wide")).contains(800);
        assertThat(new EventFields("""
                {"wide":800,"high":"600"}\
                """).readWholeNumber("high")).isEmpty();
    }

    @Test
    @DisplayName("a field that is not there, and something that is not an object, are both nothing")
    void readsWhatIsNotThereAsNothing() {
        assertThat(new EventFields("""
                {"type":"line"}\
                """).text("text")).isEmpty();
        assertThat(new EventFields("not an object").saysNothing()).isTrue();
        assertThat(new EventFields("{}").saysNothing()).isTrue();
    }
}
