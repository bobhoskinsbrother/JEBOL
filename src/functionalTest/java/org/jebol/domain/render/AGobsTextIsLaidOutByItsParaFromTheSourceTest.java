package org.jebol.domain.render;

import org.jebol.application.Bounds;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AGobsTextIsLaidOutByItsParaFromTheSourceTest {

    private final Interpreter interpreter = AnInterpreter.withBounds(
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

    private PaintInstruction.Writing findTheOnlyLineOf(String richText) {
        List<PaintInstruction.Writing> written = writeFor("""
                labelled: make gob! [size: 200x40]
                labelled/text: compose [%s]
                labelled""".formatted(richText));
        assertThat(written).hasSize(1);
        return written.getFirst();
    }

    private TextLayout readTheLayoutUnder(String para) {
        return findTheOnlyLineOf("""
                para (make object! [%s])
                "Set 0%%"
                """.formatted(para)).layout();
    }

    @Nested
    @DisplayName("a gob whose text is a plain string")
    class APlainString {

        @Test
        @DisplayName("is laid out by the standard para: origin 2x2, margin 2x2, left, top")
        void takesTheStandardPara() {
            List<PaintInstruction.Writing> written = writeFor("""
                    make gob! [size: 100x30 text: "plain"]""");

            assertThat(written).hasSize(1);
            assertThat(written.getFirst().layout()).isEqualTo(TextLayout.STANDARD);
            assertThat(TextLayout.STANDARD).isEqualTo(new TextLayout(
                    2, 2, 2, 2, TextAlignment.LEFT, TextVerticalAlignment.TOP, 0, 0));
        }

        @Test
        @DisplayName("and is written in the gob's whole box, the layout doing the insetting")
        void isWrittenInTheWholeBox() {
            PaintInstruction.Writing written = writeFor("""
                    make gob! [offset: 5x7 size: 100x30 text: "plain"]""").getFirst();

            assertThat(written.where().wide()).isEqualTo(100);
            assertThat(written.where().high()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("a rich-text block's para")
    class TheParaObject {

        @Test
        @DisplayName("the 2010 GUI's button para centres the line both ways and takes nothing off")
        void centresTheButtonsLine() {
            assertThat(readTheLayoutUnder("origin: 0x0 margin: 0x0 align: 'center valign: 'middle"))
                    .isEqualTo(new TextLayout(0, 0, 0, 0,
                            TextAlignment.CENTRE, TextVerticalAlignment.MIDDLE, 0, 0));
        }

        @Test
        @DisplayName("the 2010 GUI's radio para starts the line 18 across, clear of the circle")
        void clearsTheRadiosCircle() {
            TextLayout laidOut = readTheLayoutUnder("origin: 18x0 valign: 'middle");

            assertThat(laidOut.originAcross()).isEqualTo(18);
            assertThat(laidOut.originDown()).isZero();
            assertThat(laidOut.valign()).isEqualTo(TextVerticalAlignment.MIDDLE);
        }

        @Test
        @DisplayName("a field the para does not have keeps the standard's value")
        void keepsTheStandardForAMissingField() {
            assertThat(readTheLayoutUnder("align: 'right"))
                    .isEqualTo(new TextLayout(2, 2, 2, 2,
                            TextAlignment.RIGHT, TextVerticalAlignment.TOP, 0, 0));
        }

        @ParameterizedTest(name = "wrap?: {0} wraps: {1}")
        @CsvSource(quoteCharacter = '"', value = {
                "false, false",
                "off,   false",
                "true,  true",
                "none,  true",
                "1,     true",
                "'no,   true",
        })
        @DisplayName("wrap? as a logic says whether a long line breaks, and anything else leaves the standard's true")
        void wrapIsTakenOnlyAsALogic(String given, boolean wraps) {
            assertThat(readTheLayoutUnder("wrap?: " + given).wraps()).isEqualTo(wraps);
        }

        @Test
        @DisplayName("a para without wrap? wraps, as the standard para does, and so does a plain string")
        void wrapsByDefault() {
            assertThat(readTheLayoutUnder("align: 'left").wraps()).isTrue();
            assertThat(TextLayout.STANDARD.wraps()).isTrue();
        }

        @ParameterizedTest(name = "align {0} is {1}")
        @CsvSource(quoteCharacter = '"', value = {
                "'left,   LEFT",
                "'center, CENTRE",
                "'right,  RIGHT",
                "'middle, LEFT",
                "'CENTER, CENTRE",
                "none,    LEFT",
                "3,       LEFT",
                "{center}, LEFT",
        })
        @DisplayName("align is left, center or right, and anything else is left as the C defaults it")
        void alignIsReadFromItsWord(String said, TextAlignment meant) {
            assertThat(readTheLayoutUnder("align: " + said).align()).isEqualTo(meant);
        }

        @ParameterizedTest(name = "valign {0} is {1}")
        @CsvSource(quoteCharacter = '"', value = {
                "'top,    TOP",
                "'middle, MIDDLE",
                "'bottom, BOTTOM",
                "'center, TOP",
                "none,    TOP",
                "1.5,     TOP",
        })
        @DisplayName("valign is top, middle or bottom, and anything else is top as the C defaults it")
        void valignIsReadFromItsWord(String said, TextVerticalAlignment meant) {
            assertThat(readTheLayoutUnder("valign: " + said).valign()).isEqualTo(meant);
        }

        @ParameterizedTest(name = "origin {0} is {1} across and {2} down")
        @CsvSource({
                "0x0,      0,  0",
                "18x0,     18, 0",
                "-3x4,     -3, 4",
                "2.6x1.4,  2,  1",
                "none,     2,  2",
                "{18x0},   2,  2",
                "18,       2,  2",
        })
        @DisplayName("origin is a pair, its fractions cut off as the C's copy into whole pixels does, and anything else keeps the standard 2x2")
        void originIsAPair(String said, int across, int down) {
            TextLayout laidOut = readTheLayoutUnder("origin: " + said);

            assertThat(laidOut.originAcross()).isEqualTo(across);
            assertThat(laidOut.originDown()).isEqualTo(down);
        }

        @ParameterizedTest(name = "margin {0} is {1} across and {2} down")
        @CsvSource({
                "0x0,   0, 0",
                "5x6,   5, 6",
                "none,  2, 2",
                "true,  2, 2",
        })
        @DisplayName("margin is a pair too, and anything else keeps the standard 2x2")
        void marginIsAPair(String said, int across, int down) {
            TextLayout laidOut = readTheLayoutUnder("margin: " + said);

            assertThat(laidOut.marginAcross()).isEqualTo(across);
            assertThat(laidOut.marginDown()).isEqualTo(down);
        }

        @Test
        @DisplayName("an object after FONT is read as a font, not as a para")
        void givesWayToAnObjectAfterFont() {
            PaintInstruction.Writing written = findTheOnlyLineOf("""
                    font (make object! [size: 14 align: 'center origin: 0x0])
                    "label"
                    """);

            assertThat(written.layout()).isEqualTo(TextLayout.STANDARD);
            assertThat(written.runs().getFirst().size()).isEqualTo(14);
        }

        @Test
        @DisplayName("and an object after PARA sets no font, however it is spelt")
        void setsNoFont() {
            PaintInstruction.Writing written = findTheOnlyLineOf("""
                    para (make object! [size: 30 color: 255.0.0 align: 'center])
                    "label"
                    """);

            assertThat(written.runs().getFirst().size()).isEqualTo(12);
            assertThat(written.runs().getFirst().colour()).isEqualTo(Colour.BLACK);
            assertThat(written.layout().align()).isEqualTo(TextAlignment.CENTRE);
        }
    }

    @Nested
    @DisplayName("a font's shadow")
    class TheShadow {

        @Test
        @DisplayName("the 2010 GUI's button font has a shadow 2 across and 2 down")
        void fallsTwoAcrossAndTwoDownUnderTheButton() {
            TextLayout laidOut = findTheOnlyLineOf("""
                    font (make object! [color: 255.250.250 style: 'bold size: 14 shadow: 2x2])
                    "Set 0%"
                    """).layout();

            assertThat(laidOut.shadowAcross()).isEqualTo(2);
            assertThat(laidOut.shadowDown()).isEqualTo(2);
            assertThat(laidOut.castsAShadow()).isTrue();
        }

        @ParameterizedTest(name = "shadow {0} casts {1} across and {2} down")
        @CsvSource({
                "none,              0,  0",
                "0x0,               0,  0",
                "-1x3,              -1, 3",
                "[2x1 255.0.0 3],   2,  1",
                "{2x2},             0,  0",
                "5,                 0,  0",
        })
        @DisplayName("a shadow is a pair, or a block whose first value is one, and anything else casts none")
        void isAPairOrABlockStartingWithOne(String said, int across, int down) {
            TextLayout laidOut = findTheOnlyLineOf("""
                    font (make object! [shadow: (%s)])
                    "label"
                    """.formatted(said.startsWith("[") ? "quote " + said : said)).layout();

            assertThat(laidOut.shadowAcross()).isEqualTo(across);
            assertThat(laidOut.shadowDown()).isEqualTo(down);
        }

        @Test
        @DisplayName("a shadow of 0x0 casts nothing, as it would land under the text")
        void castsNothingAtNoOffset() {
            assertThat(findTheOnlyLineOf("""
                    font (make object! [shadow: 0x0])
                    "label"
                    """).layout().castsAShadow()).isFalse();
        }
    }

    @Nested
    @DisplayName("the line")
    class TheLine {

        @Test
        @DisplayName("is one instruction holding every run, so it can be placed as a whole")
        void severalRunsAreOneLine() {
            PaintInstruction.Writing written = findTheOnlyLineOf("""
                    "plain " bold "heavy" /bold " plain again"
                    """);

            assertThat(written.runs()).extracting(TextRun::text)
                    .containsExactly("plain ", "heavy", " plain again");
            assertThat(written.runs()).extracting(TextRun::bold)
                    .containsExactly(false, true, false);
            assertThat(written.text()).isEqualTo("plain heavy plain again");
        }

        @Test
        @DisplayName("a block with no strings writes nothing at all")
        void isLeftOutWithNoStrings() {
            assertThat(writeFor("""
                    labelled: make gob! [size: 200x40]
                    labelled/text: [bold]
                    labelled""")).isEmpty();
        }

        @Test
        @DisplayName("an empty string is a run, so an empty field still has a line and a place for its caret")
        void takesAnEmptyStringAsARun() {
            assertThat(findTheOnlyLineOf("""
                    "" "label" ""
                    """).runs()).extracting(TextRun::text).containsExactly("", "label", "");
        }
    }
}
