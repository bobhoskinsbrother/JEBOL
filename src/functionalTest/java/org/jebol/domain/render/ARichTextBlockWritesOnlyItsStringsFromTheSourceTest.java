package org.jebol.domain.render;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ARichTextBlockWritesOnlyItsStringsFromTheSourceTest {

    private final Interpreter interpreter = Interpreter.withBounds(
            Bounds.standard().grant(HostService.WINDOWS));

    private List<PaintInstruction.Writing> writeFor(String gobBuiltBy) {
        interpreter.defineNewWordsIn(gobBuiltBy);
        GobValue gob = (GobValue) interpreter.run(gobBuiltBy).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.of(gob, dialect).instructions().stream()
                .filter(PaintInstruction.Writing.class::isInstance)
                .map(PaintInstruction.Writing.class::cast)
                .toList();
    }

    private List<PaintInstruction.Writing> writeForTheText(String richText) {
        return writeFor("""
                labelled: make gob! [size: 200x40]
                labelled/text: compose [%s]
                labelled""".formatted(richText));
    }

    @Test
    @DisplayName("the 2010 GUI's label writes its string and none of its font's fields")
    void writesOnlyTheGuisLabelString() {
        List<PaintInstruction.Writing> written = writeForTheText("""
                font (make object! [name: "Arial" style: 'bold size: 16 color: 10.20.30
                    offset: 2x2 space: 0x0 shadow: _])
                para (make object! [origin: 0x0 margin: 0x0 align: 'center valign: 'middle])
                anti-alias (true)
                "These will change:"
                """);

        assertThat(written).extracting(PaintInstruction.Writing::text)
                .containsExactly("These will change:");
        TextRun run = written.getFirst().runs().getFirst();
        assertThat(run.size()).isEqualTo(16);
        assertThat(run.bold()).isTrue();
        assertThat(run.colour()).isEqualTo(new Colour(10, 20, 30));
    }

    @ParameterizedTest(name = "a font with style {0} writes bold {1} and italic {2}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "'bold          | true  | false",
            "'italic        | false | true",
            "[bold italic]  | true  | true",
            "_              | false | false",
    })
    @DisplayName("a font's style decides the weight and the slant")
    void takesWeightAndSlantFromAFontsStyle(String style, boolean bold, boolean italic) {
        TextRun written = writeForTheText(
                "font (make object! [style: %s]) {x}".formatted(style)).getFirst().runs().getFirst();

        assertThat(written.bold()).isEqualTo(bold);
        assertThat(written.italic()).isEqualTo(italic);
    }

    @Test
    @DisplayName("a font that says no size or colour leaves the ordinary size and black")
    void keepsTheDefaultsForAFontThatSaysLittle() {
        TextRun written = writeForTheText(
                "font (make object! [name: {Arial} size: _ color: _]) {x}").getFirst().runs().getFirst();

        assertThat(written.size()).isEqualTo(PaintInstruction.Writing.THE_ORDINARY_SIZE);
        assertThat(written.colour()).isEqualTo(Colour.BLACK);
    }

    @Test
    @DisplayName("two strings are two runs of one line")
    void writesTwoStringsAsTwoRuns() {
        List<PaintInstruction.Writing> written = writeForTheText("{one} {two}");

        assertThat(written).hasSize(1);
        assertThat(written.getFirst().runs()).extracting(TextRun::text).containsExactly("one", "two");
    }

    @Test
    @DisplayName("a block with no strings writes nothing")
    void writesNothingWithNoStrings() {
        assertThat(writeForTheText("font (make object! [size: 16]) anti-alias (true)")).isEmpty();
    }

    @Test
    @DisplayName("an empty block writes nothing")
    void writesNothingForAnEmptyBlock() {
        assertThat(writeForTheText("")).isEmpty();
    }

    @Test
    @DisplayName("a plain string on a gob is still written as it stands")
    void stillWritesAPlainString() {
        assertThat(writeFor("make gob! [size: 200x40 text: {hello}]"))
                .extracting(PaintInstruction.Writing::text)
                .containsExactly("hello");
    }

    @Test
    @DisplayName("DRAW's text command takes a font object the same way")
    void drawsTextTakesAFontObject() {
        List<PaintInstruction.Writing> written = writeFor("""
                drawn: make gob! [size: 200x40]
                drawn/draw: compose/deep [
                    text vectorial 0x0 200x40 [font (make object! [size: 20 style: 'italic]) para (make object! []) {y}]]
                drawn
                """);

        assertThat(written).extracting(PaintInstruction.Writing::text).containsExactly("y");
        assertThat(written.getFirst().runs().getFirst().size()).isEqualTo(20);
        assertThat(written.getFirst().runs().getFirst().italic()).isTrue();
    }
}
