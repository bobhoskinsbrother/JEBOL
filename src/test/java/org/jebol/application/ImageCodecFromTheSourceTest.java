package org.jebol.application;

import org.jebol.adapter.host.JavaImages;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCodecFromTheSourceTest {

    private static final Path VENDORED =
            Path.of("src", "test", "resources", "rebol-suite", "units", "files");

    private static Interpreter reaching(Path directory) {
        Interpreter interpreter = withAFilesystemOn(directory);
        interpreter.useImages(new JavaImages());
        return interpreter;
    }

    private static Interpreter withAFilesystemOn(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static void layOut(Path directory) throws IOException {
        for (String name : new String[] {
                "r3.png", "animation.gif", "rbgw.png",
                "flower.jpg", "flower.bmp", "issue-1677.txt"}) {
            Files.copy(VENDORED.resolve(name), directory.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    @DisplayName("a PNG binary loads as an image of the right size")
    void aPngBinaryLoadsAsAnImage(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                i: image/load/as read %r3.png 'PNG
                reduce [type? i i/size]""")).isEqualTo("[#(image!) 24x24]");
    }

    @Test
    @DisplayName("and so does a file, with the bytes left to say what they are")
    void aFileLoadsWithoutBeingToldTheFormat(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                a: image/load %r3.png
                b: image/load %flower.jpg
                c: image/load %flower.bmp
                d: image/load %animation.gif
                reduce [a/size b/size c/size d/size]"""))
                .isEqualTo("[24x24 256x256 256x256 11x29]");
    }

    @Test
    @DisplayName("saving to none answers a binary, and every format reads back")
    void savingToNoneAnswersABinaryThatReadsBack(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                one: make image! 1x1
                p: image/load/as image/save/as none one 'PNG 'PNG
                j: image/load/as image/save/as none one 'JPEG 'JPEG
                g: image/load/as image/save/as none one 'GIF 'GIF
                m: image/load/as image/save/as none one 'BMP 'BMP
                reduce [type? image/save/as none one 'PNG p/rgb j/rgb g/rgb m/rgb]"""))
                .isEqualTo("[#(binary!) #{FFFFFF} #{FFFFFF} #{FFFFFF} #{FFFFFF}]");
    }

    @Test
    @DisplayName("an image with alpha, saved to a format that has none")
    void anImageWithAlphaSavedToAFormatWithoutIt(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                see-through: make image! 2x2
                see-through/1: 200.0.0.0
                back-again: image/load/as
                    image/save/as none see-through 'JPEG 'JPEG
                reduce [back-again/size back-again/1/4]"""))
                .isEqualTo("[2x2 255]");
    }

    @Test
    @DisplayName("a BMP keeps its alpha, which the runtime's own writer will not")
    void aBmpKeepsItsAlpha(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                see-through: make image! [2x2 255.0.0.10]
                back-again: image/load/as
                    image/save/as none see-through 'BMP 'BMP
                reduce [back-again/size  to binary! back-again]"""))
                .isEqualTo("[2x2 #{FF00000AFF00000AFF00000AFF00000A}]");
    }

    @Test
    @DisplayName("and it writes the same bytes a real Rebol writes")
    void itWritesTheSameBytesARealRebolWrites(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                written: image/save/as none make image! [2x2 255.0.0.10] 'BMP
                reduce [length? written  enbase/flat written 16]"""))
                .isEqualTo("""
                        [154 {424D9A000000000000008A0000007C00000002000000\
                        FEFFFFFF010020000300000010000000000000000000\
                        000000000000000000000000FF0000FF0000FF000000\
                        000000FF424752730000000000000000000000000000\
                        00000000000000000000000000000000000000000000\
                        00000000000000000000000000000000000000000000\
                        0000000000000000FF0A0000FF0A0000FF0A0000FF0A}]""");
    }

    @Test
    @DisplayName("and the rows come back in the order they went in")
    void theRowsComeBackInTheOrderTheyWentIn(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                striped: make image! 2x3
                repeat n 6 [poke striped n to tuple! reduce [n n n 255]]
                back-again: image/load/as
                    image/save/as none striped 'BMP 'BMP
                reduce [back-again/size  enbase/flat back-again/rgb 16]"""))
                .isEqualTo("""
                        [2x3 "010101020202030303040404050505060606"]""");
    }

    @Test
    @DisplayName("saving into a binary fills the one it was given")
    void savingIntoABinaryFillsTheOneItWasGiven(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                destination: copy #{}
                answered: image/save/as destination make image! 2x2 'PNG
                elsewhere: image/save/as none make image! 2x2 'PNG
                read-back: image/load/as destination 'PNG
                reduce [
                    same? destination answered
                    destination = elsewhere
                    read-back/size
                ]"""))
                .isEqualTo("[#(true) #(true) 2x2]");
    }

    @Test
    @DisplayName("and it writes from the position, dropping whatever followed")
    void itWritesFromThePositionDroppingWhateverFollowed(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                destination: copy #{DEADBEEF}
                image/save/as destination make image! 2x2 'PNG
                copy/part destination 4"""))
                .isEqualTo("#{89504E47}");
        assertThat(answerTo(reaching(directory), """
                destination: copy #{DEADBEEF}
                image/save/as (next destination) make image! 2x2 'PNG
                whole: head destination
                reduce [length? whole  copy/part whole 4]"""))
                .isEqualTo("[72 #{DE89504E}]");
    }

    @Test
    @DisplayName("saving to a file answers the file, and puts the bytes there")
    void savingToAFileAnswersTheFile(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                where: image/save/as %made.png make image! 2x2 'PNG
                back: image/load %made.png
                reduce [where exists? %made.png back/size]"""))
                .isEqualTo("[%made.png file 2x2]");
    }

    @Test
    @DisplayName("a codec word this build has not got is refused by name")
    void aCodecWordItHasNotGotIsRefused(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(errorIdFrom(reaching(directory),
                "image/load/as read %r3.png 'NOPE")).isEqualTo("bad-func-arg");
    }

    @Test
    @DisplayName("bytes that are not an image and a file that is not there fail apart")
    void badBytesAndABadFileFailApart(@TempDir Path directory) throws IOException {
        layOut(directory);
        Interpreter interpreter = reaching(directory);
        assertThat(errorIdFrom(interpreter, "image/load/as #{} 'PNG"))
                .isEqualTo("no-codec");
        assertThat(errorIdFrom(interpreter, "image/load/as #{0102030405} 'PNG"))
                .isEqualTo("no-codec");
        assertThat(errorIdFrom(interpreter, "image/load %no-such-file-at-all.png"))
                .isEqualTo("cannot-open");
        assertThat(errorIdFrom(interpreter, "image/load %issue-1677.txt"))
                .isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("/FRAME picks one image out of a file holding several")
    void frameChoosesOneOfSeveral(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                i1: image/load/frame %animation.gif 1
                i2: image/load/frame %animation.gif 2
                i3: image/load/frame %animation.gif 3
                reduce [
                    checksum i1/rgba 'md5
                    checksum i2/rgba 'md5
                    checksum i3/rgba 'md5
                ]"""))
                .isEqualTo("[#{4D99990699791F57F238C7195ABB0DE7}"
                        + " #{878BACEFB949C6435702F87D5B62F9FA}"
                        + " #{1414A649C4CF2E2D9DE3A8502A4425AA}]");
    }

    @Test
    @DisplayName("a frame at either end of the range, and one step outside it")
    void aFrameAtEitherEndOfTheRange(@TempDir Path directory) throws IOException {
        layOut(directory);
        Interpreter interpreter = reaching(directory);
        assertThat(answerTo(interpreter, """
                last-one: image/load/frame %animation.gif 3
                last-one/size""")).isEqualTo("11x29");
        assertThat(errorIdFrom(interpreter, "image/load/frame %animation.gif 4"))
                .isEqualTo("cannot-open");
        assertThat(errorIdFrom(interpreter, "image/load/frame %animation.gif 0"))
                .isEqualTo("cannot-open");
        assertThat(errorIdFrom(interpreter, "image/load/frame %animation.gif -1"))
                .isEqualTo("cannot-open");
    }

    @Test
    @DisplayName("a file holding one image has a first frame like any other")
    void aSingleImageFileHasAFirstFrame(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                s: image/load/frame %r3.png 1
                s/size""")).isEqualTo("24x24");
    }

    @Test
    @DisplayName("the pixels come back in the order REBOL holds them")
    void thePixelsComeBackInRebolsOrder(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                i: image/load %rbgw.png
                reduce [i/1 i/2 i/3 i/rgb]"""))
                .isEqualTo("[200.0.0.255 0.0.200.255 0.200.0.255"
                        + " #{C800000000C800C800FFFFFF}]");
    }

    @Test
    @DisplayName("the codecs Rebol's own codec-image.reb registers now work")
    void theRegisteredCodecsWork(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                e: encode 'png make image! 1x1
                d: decode 'png e
                reduce [type? load %r3.png type? e d/rgb]"""))
                .isEqualTo("[#(image!) #(binary!) #{FFFFFF}]");
    }

    @Test
    @DisplayName("saving something that is not an image is refused by the declaration")
    void savingSomethingThatIsNotAnImageIsRefused(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(errorIdFrom(reaching(directory),
                "image/save/as none \"not an image\" 'PNG")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a palette holds every colour the picture has")
    void aPaletteHoldsEveryColourThePictureHas(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                one: decode 'gif encode 'gif make image! [1x1 #{C80000}]
                four: decode 'gif encode 'gif
                    make image! [2x2 #{C800000000C800C800FFFFFF}]
                reduce [one/rgb four/rgb]"""))
                .isEqualTo("[#{C80000} #{C800000000C800C800FFFFFF}]");
    }

    @Test
    @DisplayName("colours a quantiser would merge stay apart")
    void closeColoursAreNotMergedTogether(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                near: decode 'gif encode 'gif make image! [2x1 #{000000 010000}]
                near/rgb"""))
                .isEqualTo("#{000000010000}");
    }

    @Test
    @DisplayName("a palette exactly full is still exact")
    void aFullPaletteIsStillExact(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                full: make image! 256x1
                repeat n 256 [full/:n: to tuple! reduce [n - 1 0 0]]
                back: decode 'gif encode 'gif full
                reduce [back/size equal? full/rgb back/rgb]"""))
                .isEqualTo("[256x1 #(true)]");
    }

    @Test
    @DisplayName("one colour too many still reads back, at the right size")
    void oneTooManyColoursStillReadsBack(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                over: make image! 257x1
                repeat n 257 [
                    over/:n: to tuple! reduce [
                        (n - 1) // 256  to integer! (n - 1) / 256  0
                    ]
                ]
                back: decode 'gif encode 'gif over
                reduce [type? back back/size]"""))
                .isEqualTo("[#(image!) 257x1]");
    }

    @Test
    @DisplayName("the round trip the suite makes, through a file")
    void theSuitesRoundTripThroughAFile(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                src: make image! [2x2 #{C800000000C800C800FFFFFF}]
                img: load save %new.gif src
                reduce [image? img img/rgb]"""))
                .isEqualTo("[#(true) #{C800000000C800C800FFFFFF}]");
    }

    @Test
    @DisplayName("an interpreter given no codec refuses, as the C does without one")
    void withoutAHostCodecItRefuses(@TempDir Path directory) throws IOException {
        layOut(directory);
        Interpreter without = withAFilesystemOn(directory);
        assertThat(errorIdFrom(without, "image/load %r3.png")).isEqualTo("feature-na");
        assertThat(errorIdFrom(without, "image/save none make image! 1x1"))
                .isEqualTo("feature-na");
        assertThat(errorIdFrom(without, "image/load/as read %r3.png 'PNG"))
                .isEqualTo("feature-na");
        assertThat(answerTo(without, "unset? image")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("and asked for nothing it still answers nothing")
    void askedForNothingItAnswersNothing(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), "unset? image")).isEqualTo("#(true)");
    }
}
