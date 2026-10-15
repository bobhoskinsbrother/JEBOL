package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("and the refusal names the type, not the verb")
    void theRefusalNamesTheType() {
        assertThat(answerTo("""
                img: make image! 2x0
                e: try [append img {a}] reduce [e/id mold e/arg1]"""))
                .isEqualTo("""
                        [invalid-type "#(string!)"]""");
    }

    @Test
    @DisplayName("a run of bytes is four to a pixel")
    void aRunOfBytesIsFourToAPixel() {
        assertThat(answerTo("""
                img: make image! [2x0 1.1.1]
                append img #{FF000000}
                reduce [length? img  mold img/1]"""))
                .isEqualTo("""
                        [1 "255.0.0.0"]""");
        assertThat(answerTo("""
                img: make image! [2x0 1.1.1]
                append img #{FF00000000FF0080}
                reduce [length? img  mold img/1  mold img/2]"""))
                .isEqualTo("""
                        [2 "255.0.0.0" "0.255.0.128"]""");
    }

    @Test
    @DisplayName("a whole number writes the alpha and leaves the colour alone")
    void aWholeNumberWritesTheAlphaAndLeavesTheColourAlone() {
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                change img 7
                enbase/flat to binary! img 16""")).isEqualTo("""
                        "01010107010101FF010101FF010101FF\"""");
    }

    @Test
    @DisplayName("and appending one adds a white pixel wearing that alpha")
    void appendingOneAddsAWhitePixelWearingThatAlpha() {
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                append img 7
                reduce [length? img  mold last img]"""))
                .isEqualTo("""
                        [5 "255.255.255.7"]""");
    }

    @Test
    @DisplayName("CHANGE/ONLY keeps the alpha that was there")
    void changeOnlyKeepsTheAlphaThatWasThere() {
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                img/1: 9.9.9.77
                change/only img 1.2.3
                mold img/1""")).isEqualTo("\"1.2.3.77\"");
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                img/1: 9.9.9.77
                change img 1.2.3
                mold img/1""")).isEqualTo("\"1.2.3.255\"");
    }

    @Test
    @DisplayName("CHANGE/PART with a pair is the size of the rectangle")
    void changePartWithAPairIsTheSizeOfTheRectangle() {
        assertThat(answerTo("""
                img: make image! [4x4 1.1.1]
                change/part img make image! [3x3 9.9.9] 2x2
                enbase/flat img/rgb 16""")).isEqualTo("""
                        {090909090909010101010101\
                        090909090909010101010101\
                        010101010101010101010101\
                        010101010101010101010101}""");
    }

    @Test
    @DisplayName("and a count where a shape belongs writes nothing")
    void aCountWhereAShapeBelongsWritesNothing() {
        assertThat(answerTo("""
                img: make image! [4x4 1.1.1]
                change/part img make image! [3x3 9.9.9] 2
                enbase/flat img/rgb 16""")).isEqualTo("""
                        {010101010101010101010101\
                        010101010101010101010101\
                        010101010101010101010101\
                        010101010101010101010101}""");
    }

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
    @DisplayName("but an image written into an image goes in as a rectangle")
    void anImageWrittenIntoAnImageGoesInAsARectangle() {
        assertThat(answerTo("""
                img: make image! 4x4
                change img make image! [2x2 0.0.0]
                enbase/flat img/rgb 16""")).isEqualTo("""
                        {000000000000FFFFFFFFFFFF\
                        000000000000FFFFFFFFFFFF\
                        FFFFFFFFFFFFFFFFFFFFFFFF\
                        FFFFFFFFFFFFFFFFFFFFFFFF}""");
    }

    @Test
    @DisplayName("and the position says which column and which row it starts at")
    void thePositionSaysWhichColumnAndWhichRow() {
        assertThat(answerTo("""
                img: make image! [4x2 1.1.1]
                change at img 2x2 make image! [2x1 9.9.9]
                enbase/flat img/rgb 16""")).isEqualTo("""
                        "010101010101010101010101\
                        010101090909090909010101\"""");
    }

    @Test
    @DisplayName("what will not fit on the row is dropped, not wrapped onto the next")
    void whatWillNotFitOnTheRowIsDropped() {
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                change at img 2x1 make image! [3x1 9.9.9]
                enbase/flat img/rgb 16""")).isEqualTo("""
                        "010101090909010101010101\"""");
    }

    @Test
    @DisplayName("and rows past the bottom are not written at all")
    void rowsPastTheBottomAreNotWritten() {
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                change at img 1x2 make image! [2x3 9.9.9]
                enbase/flat img/rgb 16""")).isEqualTo("""
                        "010101010101090909090909\"""");
    }

    @Test
    @DisplayName("the source is read from its head whatever position it stands at")
    void theSourceIsReadFromItsHead() {
        assertThat(answerTo("""
                img: make image! [3x1 1.1.1]
                change img next make image! [2x1 9.9.9]
                enbase/flat img/rgb 16""")).isEqualTo("""
                        "090909090909010101\"""");
    }

    @Test
    @DisplayName("and the answer steps one pixel, not the size of the rectangle")
    void theAnswerStepsOnePixel() {
        assertThat(answerTo("""
                index? change make image! 4x4 make image! [2x2 0.0.0]"""))
                .isEqualTo("2");
        assertThat(answerTo("""
                index? change at make image! 4x4 2x2 make image! [2x2 0.0.0]"""))
                .isEqualTo("7");
    }

    @Test
    @DisplayName("/DUP moves the answer along and leaves the picture alone")
    void duplicatingMovesOnlyTheAnswer() {
        assertThat(answerTo("""
                img: make image! [4x1 1.1.1]
                reduce [
                    index? change/dup img make image! [1x1 9.9.9] 3
                    enbase/flat img/rgb 16
                ]""")).isEqualTo("""
                        [4 "090909010101010101010101"]""");
    }

    @Test
    @DisplayName("and a count of nought writes nothing and stays where it is")
    void aCountOfNoughtWritesNothing() {
        assertThat(answerTo("""
                img: make image! [2x1 1.1.1]
                reduce [
                    index? change/dup img make image! [1x1 9.9.9] 0
                    enbase/flat img/rgb 16
                ]""")).isEqualTo("""
                        [1 "010101010101"]""");
    }

    @Test
    @DisplayName("a rectangle with nowhere to go writes nothing")
    void aRectangleWithNowhereToGoWritesNothing() {
        assertThat(answerTo("""
                img: make image! [2x1 1.1.1]
                reduce [
                    index? change tail img make image! [1x1 9.9.9]
                    enbase/flat img/rgb 16
                ]""")).isEqualTo("""
                        [3 "010101010101"]""");
        assertThat(answerTo("""
                img: make image! [2x1 1.1.1]
                reduce [
                    index? change img make image! 0x0
                    enbase/flat img/rgb 16
                ]""")).isEqualTo("""
                        [2 "010101010101"]""");
        assertThat(answerTo("""
                index? change make image! 0x0 make image! [1x1 9.9.9]"""))
                .isEqualTo("1");
    }

    private static final String A_COUNTED_PICTURE = """
            img: make image! 4x4
            repeat n 16 [poke img n to tuple! reduce [n n n]]
            """;

    @Test
    @DisplayName("COPY answers a separate picture, not the same one again")
    void copyAnswersASeparatePicture() {
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                same? img copy img""")).isEqualTo("#(false)");
        assertThat(answerTo("""
                img: make image! [2x2 1.1.1]
                other: copy img
                other/1: 9.9.9
                mold img/1""")).isEqualTo("\"1.1.1.255\"");
    }

    @Test
    @DisplayName("and it copies from the position, keeping the width")
    void copyTakesFromThePosition() {
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy next img
                reduce [mold taken/size  length? taken  mold taken/1]"""))
                .isEqualTo("""
                        ["4x3" 12 "2.2.2.255"]""");
    }

    @Test
    @DisplayName("COPY/PART with a pair takes a rectangle from where it stands")
    void copyPartWithAPairTakesARectangle() {
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part img 2x2
                reduce [mold taken/size  enbase/flat taken/rgb 16]"""))
                .isEqualTo("""
                        ["2x2" "010101020202050505060606"]""");
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part at img 2 2x2
                reduce [mold taken/size  enbase/flat taken/rgb 16]"""))
                .isEqualTo("""
                        ["2x2" "020202030303060606070707"]""");
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part at img 2x2 2x2
                reduce [mold taken/size  enbase/flat taken/rgb 16]"""))
                .isEqualTo("""
                        ["2x2" "0606060707070A0A0A0B0B0B"]""");
    }

    @Test
    @DisplayName("and it is clipped to what is left of the row and of the picture")
    void theRectangleIsClippedToWhatIsLeft() {
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part at img 4x1 3x3
                reduce [mold taken/size  enbase/flat taken/rgb 16]"""))
                .isEqualTo("""
                        ["1x3" "0404040808080C0C0C"]""");
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part img 99x99  mold taken/size""")).isEqualTo("\"4x4\"");
    }

    @Test
    @DisplayName("a rectangle of nothing, or of less than nothing, is empty")
    void aRectangleOfNothingIsEmpty() {
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part img 0x0  mold taken/size""")).isEqualTo("\"0x0\"");
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part img -2x-2  mold taken/size""")).isEqualTo("\"0x0\"");
        assertThat(answerTo(A_COUNTED_PICTURE + """
                taken: copy/part tail img 2x2  mold taken/size""")).isEqualTo("\"2x0\"");
    }

    @Test
    @DisplayName("SINGLE? is one pixel, which it already was")
    void singleIsOnePixel() {
        assertThat(answerTo("""
                reduce [single? make image! 1x1  single? make image! 1x2]"""))
                .isEqualTo("[#(true) #(false)]");
    }
}
