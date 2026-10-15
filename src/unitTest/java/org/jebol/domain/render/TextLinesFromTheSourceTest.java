package org.jebol.domain.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextLinesFromTheSourceTest {

    private final TextMeasure halfASizePerCharacter = run -> new TextExtent(
            run.text().codePointCount(0, run.text().length()) * run.size() / 2,
            run.size() * 3 / 4,
            run.size() / 4);

    private final Placement aBox = new Placement(0, 0, 200, 100, ClipRectangle.wholeSurface(400, 400), Placement.OPAQUE);

    private final TextLayout noInset = new TextLayout(0, 0, 0, 0,
            TextAlignment.LEFT, TextVerticalAlignment.TOP, 0, 0);

    private TextRun plain(String text) {
        return new TextRun(text, Colour.BLACK, 12, false, false);
    }

    private TextLines linesOf(TextRun... runs) {
        return new TextLines(List.of(runs), halfASizePerCharacter);
    }

    @Nested
    @DisplayName("how much room the text takes")
    class TheRoom {

        @Test
        @DisplayName("one line is as wide as its characters and as tall as its ascent and descent")
        void oneLine() {
            TextLines lines = linesOf(plain("abc"));

            assertThat(lines.widest()).isEqualTo(18);
            assertThat(lines.tallness()).isEqualTo(12);
        }

        @Test
        @DisplayName("a newline starts a second line, and the widest line sets the width")
        void twoLines() {
            TextLines lines = linesOf(plain("abcd\nab"));

            assertThat(lines.lines()).hasSize(2);
            assertThat(lines.widest()).isEqualTo(24);
            assertThat(lines.tallness()).isEqualTo(24);
        }

        @Test
        @DisplayName("a string with no characters is still one line, one line tall")
        void anEmptyStringIsOneLine() {
            TextLines lines = linesOf(plain(""));

            assertThat(lines.lines()).hasSize(1);
            assertThat(lines.widest()).isZero();
            assertThat(lines.tallness()).isEqualTo(12);
        }

        @Test
        @DisplayName("a newline at the end leaves an empty last line")
        void aTrailingNewlineLeavesAnEmptyLine() {
            assertThat(linesOf(plain("ab\n")).lines()).hasSize(2);
        }

        @Test
        @DisplayName("no runs at all take no room")
        void noRunsNoRoom() {
            TextLines lines = linesOf();

            assertThat(lines.widest()).isZero();
            assertThat(lines.tallness()).isZero();
        }

        @Test
        @DisplayName("two runs on one line add their widths, and the taller sets the line's height")
        void twoRunsOnALine() {
            TextLines lines = linesOf(plain("ab"), new TextRun("cd", Colour.BLACK, 24, false, false));

            assertThat(lines.widest()).isEqualTo(12 + 24);
            assertThat(lines.tallness()).isEqualTo(24);
        }

        @Test
        @DisplayName("a newline inside a run splits that run across two lines")
        void aRunSplitsAcrossLines() {
            TextLines lines = linesOf(plain("ab"), plain("c\nde"));

            assertThat(lines.lines()).hasSize(2);
            assertThat(lines.lines().get(0).wide()).isEqualTo(18);
            assertThat(lines.lines().get(1).wide()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("where a caret is drawn")
    class WhereACaretGoes {

        @ParameterizedTest(name = "before character {1} of run {0} is {2} across and {3} down")
        @CsvSource({
                "0, 0, 0,  0",
                "0, 1, 6,  0",
                "0, 4, 24, 0",
                "0, 5, 0,  12",
                "0, 7, 12, 12",
                "0, 99, 12, 12",
                "1, 0, 12, 12",
                "1, 1, 18, 12",
        })
        @DisplayName("across, the text before it on its line; down, the top of its line")
        void aCaretSitsAfterTheTextBeforeIt(int run, int character, double across, double down) {
            TextLines lines = linesOf(plain("abcd\nab"), plain("xy"));

            TextLines.CaretPlace placed = lines.whereTheCaretIs(run, character, noInset, aBox);

            assertThat(placed.across()).isEqualTo(across);
            assertThat(placed.top()).isEqualTo(down);
            assertThat(placed.high()).isEqualTo(12);
        }

        @Test
        @DisplayName("a centred line moves its caret with it")
        void aCentredLineMovesItsCaret() {
            TextLayout centred = new TextLayout(0, 0, 0, 0,
                    TextAlignment.CENTRE, TextVerticalAlignment.TOP, 0, 0);

            assertThat(linesOf(plain("abcd")).whereTheCaretIs(0, 0, centred, aBox).across())
                    .isEqualTo((200 - 24) / 2.0);
        }

        @Test
        @DisplayName("the para's origin moves the caret in from the gob's top left")
        void theOriginMovesTheCaret() {
            TextLines.CaretPlace placed = linesOf(plain("ab")).whereTheCaretIs(0, 1, TextLayout.STANDARD, aBox);

            assertThat(placed.across()).isEqualTo(2 + 6);
            assertThat(placed.top()).isEqualTo(2);
        }

        @Test
        @DisplayName("a run that does not exist puts the caret at the very end")
        void aRunPastTheEndIsTheEnd() {
            TextLines.CaretPlace placed = linesOf(plain("ab\ncd")).whereTheCaretIs(9, 0, noInset, aBox);

            assertThat(placed.across()).isEqualTo(12);
            assertThat(placed.top()).isEqualTo(12);
        }
    }

    @Nested
    @DisplayName("a line too wide for its room")
    class Wrapping {

        private TextLines wrappedIn(double room, TextRun... runs) {
            return new TextLines(List.of(runs), halfASizePerCharacter, room);
        }

        private List<String> theLinesWritten(TextLines lines) {
            return lines.lines().stream()
                    .map(line -> line.pieces().stream().map(TextLines.Piece::text).reduce("", String::concat))
                    .toList();
        }

        @ParameterizedTest(name = "in a room {0} wide, {1} is written as {2}")
        @CsvSource(delimiter = ';', value = {
                "36; abc de; abc de",
                "35; abc de; abc |de",
                "30; abcde fg; abcde |fg",
                "29; abcde fg; abcd|e fg",
                "30; abcdefgh; abcde|fgh",
                "3; ab; a|b",
                "0; ab; a|b",
                "30; abc de\\nf; abc |de|f",
                "30; ab   cd; ab   |cd",
        })
        @DisplayName("the break comes before the word that would not fit, and a word too wide on its own is broken where it overflows")
        void whereTheLineBreaks(double room, String text, String expected) {
            assertThat(theLinesWritten(wrappedIn(room, plain(text.replace("\\n", "\n")))))
                    .containsExactly(expected.split("\\|"));
        }

        @Test
        @DisplayName("with no limit to the room, a line never breaks but at a newline")
        void noLimitNoBreak() {
            assertThat(theLinesWritten(wrappedIn(Double.POSITIVE_INFINITY, plain("abc de fgh ijk"))))
                    .containsExactly("abc de fgh ijk");
        }

        @Test
        @DisplayName("a string with no characters is still one line when wrapping")
        void anEmptyStringIsOneLine() {
            TextLines lines = wrappedIn(30, plain(""));

            assertThat(lines.lines()).hasSize(1);
            assertThat(lines.tallness()).isEqualTo(12);
        }

        @Test
        @DisplayName("the spaces hanging off a broken line are not counted in its width")
        void hangingSpacesTakeNoRoom() {
            TextLines lines = wrappedIn(35, plain("abc de"));

            assertThat(lines.lines().get(0).wide()).isEqualTo(18);
            assertThat(lines.widest()).isEqualTo(18);
            assertThat(lines.tallness()).isEqualTo(24);
        }

        @Test
        @DisplayName("a break between two runs keeps each run's piece on its own line")
        void aBreakBetweenRuns() {
            TextLines lines = wrappedIn(24, plain("ab "), plain("cd"));

            assertThat(lines.lines().get(0).pieces()).extracting(TextLines.Piece::run).containsExactly(0);
            assertThat(lines.lines().get(1).pieces()).extracting(TextLines.Piece::run).containsExactly(1);
        }

        @Test
        @DisplayName("a word spread over two runs is one word, broken where it overflows")
        void aWordOverTwoRuns() {
            TextLines lines = wrappedIn(18, plain("ab"), plain("cd"));

            assertThat(theLinesWritten(lines)).containsExactly("abc", "d");
            assertThat(lines.lines().get(1).pieces().getFirst().from()).isEqualTo(1);
        }

        @ParameterizedTest(name = "before character {0} is {1} across and {2} down")
        @CsvSource({
                "3, 18, 0",
                "4, 0,  12",
                "5, 6,  12",
        })
        @DisplayName("a caret on a wrapped line is drawn on it, and the place where a line was broken starts the next")
        void aCaretOnAWrappedLine(int character, double across, double down) {
            TextLines.CaretPlace placed = wrappedIn(35, plain("abc de")).whereTheCaretIs(0, character, noInset, aBox);

            assertThat(placed.across()).isEqualTo(across);
            assertThat(placed.top()).isEqualTo(down);
        }

        @Test
        @DisplayName("a point on the second wrapped line finds the caret there")
        void thePointFindsTheWrappedLine() {
            TextLines.RunAndCharacter found = wrappedIn(35, plain("abc de")).caretNearest(7, 13, noInset, aBox);

            assertThat(found.character()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("the caret nearest a point")
    class TheNearestCaret {

        @ParameterizedTest(name = "{0}x{1} is before character {3} of run {2}")
        @CsvSource({
                "0,   5,  0, 0",
                "2,   5,  0, 0",
                "3,   5,  0, 1",
                "8,   5,  0, 1",
                "9,   5,  0, 2",
                "23,  5,  0, 4",
                "500, 5,  0, 4",
                "-50, 5,  0, 0",
                "0,   -9, 0, 0",
                "7,   13, 0, 6",
                "500, 13, 1, 2",
                "7,   500, 0, 6",
        })
        @DisplayName("the line it is level with, and the character boundary nearest it across")
        void theNearestBoundary(double across, double down, int run, int character) {
            TextLines lines = linesOf(plain("abcd\nab"), plain("xy"));

            TextLines.RunAndCharacter found = lines.caretNearest(across, down, noInset, aBox);

            assertThat(found.run()).isEqualTo(run);
            assertThat(found.character()).isEqualTo(character);
        }

        @Test
        @DisplayName("with no runs there is no caret")
        void noRunsNoCaret() {
            assertThat(linesOf().caretNearest(5, 5, noInset, aBox).isNowhere()).isTrue();
        }
    }
}
