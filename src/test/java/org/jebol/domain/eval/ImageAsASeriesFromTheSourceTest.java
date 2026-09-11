package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An image used as what it is: a run of pixels with a width beside it.
 *
 * <p>{@code t-image.c}. {@code QUAD_SKIP} turns a pixel index into a byte
 * offset and nothing else about navigating one is special, so APPEND, INSERT,
 * CHANGE, FIND and REPEAT mean on an image what they mean on a block. JEBOL
 * refused all five, which made an image a series that could not be used as
 * one.
 *
 * <p>The part worth reading carefully is the height. It is not stored: it is
 * how many whole rows the pixels make. So three pixels in an image two wide
 * are one row and a spare, the size says two by one, and the length says
 * three. The spare is really there -- it reads back, it can be changed, and
 * the next pixel appended completes the row and the size grows.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written.
 */
class ImageAsASeriesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("REPEAT walks an image pixel by pixel")
    void repeatWalksAnImagePixelByPixel() {
        assertThat(answerTo("""
                img: make image! 1x2
                repeat n img [n/1: 1.2.3 n/1: index? n]
                reduce [img/1 img/2]""")).isEqualTo("[1.2.3.1 1.2.3.2]");
    }

    /**
     * A tuple of three names a colour whatever its alpha; a whole number on
     * its own names an alpha, which is the spelling that lets a caller find a
     * transparent pixel without saying what colour it is.
     */
    @Test
    @DisplayName("FIND answers where a pixel is, by colour or by alpha")
    void findAnswersWhereAPixelIs() {
        assertThat(answerTo("""
                img: make image! 2x2 img/2: 66.66.66
                p: find img 66.66.66
                reduce [image? p  index? p  index?/xy p]"""))
                .isEqualTo("[#(true) 2 2x1]");
        assertThat(answerTo("""
                img: make image! 2x2 img/2: 66.66.66.66
                p: find img 66
                reduce [image? p  index? p]""")).isEqualTo("[#(true) 2]");
    }

    @Test
    @DisplayName("and /TAIL, /ONLY and /MATCH mean what they mean elsewhere")
    void andTheRefinementsMeanWhatTheyMeanElsewhere() {
        assertThat(answerTo("""
                img: make image! 2x2 img/2: 66.66.66
                p: find/tail img 66.66.66
                reduce [index? p  index?/xy p]""")).isEqualTo("[3 1x2]");
        assertThat(answerTo("""
                img: make image! 2x2 img/2: 66.66.66
                reduce [
                    index? find/only img 66.66.66
                    index? find/only img 66.66.66.22
                    index? find/match img 255.255.255
                    index? find/match/tail img 255.255.255
                ]""")).isEqualTo("[2 2 1 2]");
    }

    @Test
    @DisplayName("a pixel that is not there answers none")
    void aPixelThatIsNotThereAnswersNone() {
        assertThat(answerTo("""
                img: make image! 2x2 img/2: 66.66.66
                none? find img 66.66.66.22""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                img: make image! 2x2
                none? find img 66.66.66""")).isEqualTo("#(true)");
    }

    /**
     * The height is how many whole rows there are, so it does not move until a
     * row is finished. Three pixels in an image two wide are a row and a
     * spare: the size says two by one, the length says three.
     */
    @Test
    @DisplayName("APPEND adds a pixel, and the height follows the whole rows")
    void appendAddsAPixelAndTheHeightFollowsTheWholeRows() {
        assertThat(answerTo("""
                img: make image! 2x0
                append img 170.170.170
                before: img/size
                append img 187.187.187
                reduce [before img/size enbase/flat img/rgb 16]"""))
                .isEqualTo("""
                        [2x0 2x1 "AAAAAABBBBBB"]""");
    }

    @Test
    @DisplayName("and a block appends every pixel in it")
    void aBlockAppendsEveryPixelInIt() {
        assertThat(answerTo("""
                img: make image! 2x0
                append img 170.170.170
                append img 187.187.187
                append img [1.1.1 2.2.2]
                enbase/flat img/rgb 16"""))
                .isEqualTo("\"AAAAAABBBBBB010101020202\"");
    }

    /**
     * A partial row at three different fillings of an image three wide, so the
     * boundary is walked rather than sampled: one pixel and two leave the
     * height at nought, the third lifts it to one, and a fourth leaves it
     * there.
     */
    @Test
    @DisplayName("the height moves only when a row is finished")
    void theHeightMovesOnlyWhenARowIsFinished() {
        for (int howMany = 1; howMany <= 4; howMany++) {
            String expected = switch (howMany) {
                case 1 -> "[3x0 1 3]";
                case 2 -> "[3x0 2 6]";
                case 3 -> "[3x1 3 9]";
                default -> "[3x1 4 12]";
            };
            assertThat(answerTo("""
                    img: make image! 3x0
                    repeat n %d [append img 1.1.1]
                    reduce [img/size length? img length? img/rgb]"""
                    .formatted(howMany))).as(howMany + " pixels").isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("INSERT puts a pixel at the position and answers just past it")
    void insertPutsAPixelAtThePositionAndAnswersJustPastIt() {
        assertThat(answerTo("""
                img: make image! 2x0
                reduce [
                    index? img
                    index? insert img 170.170.170
                    index? insert img 187.187.187
                    index? tail img
                    enbase/flat img/rgb 16
                ]""")).isEqualTo("""
                        [1 2 2 3 "BBBBBBAAAAAA"]""");
    }

    @Test
    @DisplayName("and inserting into the middle pushes the rest along")
    void insertingIntoTheMiddlePushesTheRestAlong() {
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                insert at img 2 9.9.9
                reduce [img/size enbase/flat img/rgb 16]"""))
                .isEqualTo("""
                        [2x2 "010101090909010101010101010101"]""");
    }

    /**
     * Inserting at the tail answers a position at the tail, so the image read
     * from there is empty even though the pixel went in. Taking the head back
     * shows it.
     */
    @Test
    @DisplayName("inserting at the tail leaves the position at the tail")
    void insertingAtTheTailLeavesThePositionAtTheTail() {
        assertThat(answerTo("""
                img: make image! 2x0
                img: insert tail img 170.170.170
                reduce [img/size tail? img empty? img/rgb]"""))
                .isEqualTo("[2x0 #(true) #(true)]");
        assertThat(answerTo("""
                img: make image! 2x0
                img: insert tail img 170.170.170
                img: insert img 187.187.187
                whole: head img
                reduce [img/size tail? img enbase/flat whole/rgb 16]"""))
                .isEqualTo("""
                        [2x1 #(true) "AAAAAABBBBBB"]""");
    }

    @Test
    @DisplayName("and what is not a pixel is refused without changing the image")
    void whatIsNotAPixelIsRefusedWithoutChangingTheImage() {
        assertThat(answerTo("""
                img: make image! 2x0
                reduce [length? img  error? try [append img {a}]  length? img]"""))
                .isEqualTo("[0 #(true) 0]");
    }

    /**
     * CHANGE writes over what is there and does not lengthen the image, so
     * more pixels than there is room for are dropped. The width is fixed and a
     * longer image would be a different shape.
     */
    @Test
    @DisplayName("CHANGE writes over the pixels that are there")
    void changeWritesOverThePixelsThatAreThere() {
        assertThat(answerTo("""
                a: make image! [2x1 170.170.170]
                b: make image! [2x1 187.187.187]
                change a b
                enbase/flat a/rgb 16""")).isEqualTo("\"BBBBBBBBBBBB\"");
        assertThat(answerTo("""
                a: make image! [2x1 170.170.170]
                change at a 2 0.0.0
                enbase/flat a/rgb 16""")).isEqualTo("\"AAAAAA000000\"");
    }

    @Test
    @DisplayName("and answers the position just past what it wrote")
    void andAnswersThePositionJustPastWhatItWrote() {
        assertThat(answerTo("""
                a: make image! [2x1 170.170.170]
                reduce [tail? change/dup a 200.200.200 2  enbase/flat a/rgb 16]"""))
                .isEqualTo("""
                        [#(true) "C8C8C8C8C8C8"]""");
    }

    @Test
    @DisplayName("and more pixels than there is room for are dropped")
    void morePixelsThanThereIsRoomForAreDropped() {
        assertThat(answerTo("""
                a: make image! [2x1 170.170.170]
                change at a 2 [1.1.1 2.2.2]
                reduce [a/size enbase/flat a/rgb 16]"""))
                .isEqualTo("""
                        [2x1 "AAAAAA010101"]""");
    }

    @Test
    @DisplayName("SINGLE? is one pixel, which it already was")
    void singleIsOnePixel() {
        assertThat(answerTo("""
                reduce [single? make image! 1x1  single? make image! 1x2]"""))
                .isEqualTo("[#(true) #(false)]");
    }
}
