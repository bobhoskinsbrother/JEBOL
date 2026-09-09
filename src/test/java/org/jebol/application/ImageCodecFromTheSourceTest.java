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

/**
 * IMAGE reaching the platform's own codec, which on a JVM is always there.
 *
 * <p>{@code n-image.c} compiles the whole native only where
 * {@code INCLUDE_IMAGE_OS_CODEC} is defined -- "only on Windows and macOS so
 * far" -- and refuses with feature-na elsewhere. A JVM carries a codec
 * wherever it runs, so this is one of the platforms that has one, and the
 * refusal belongs to an interpreter given no port rather than to every
 * interpreter.
 *
 * <p>It is worth more than one native. Rebol's own {@code codec-image.reb}
 * writes every png, jpeg, gif and bmp entry of {@code system/codecs} as a call
 * to this, so refusing here does not leave the codec family to supply a
 * portable one -- it lists four codecs in the catalogue that cannot do
 * anything.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written,
 * the three GIF frame checksums included. Two of them corrected a guess:
 * {@code animation.gif} is eleven by twenty-nine and not the size of the other
 * pictures beside it, and a frame at either end of the range fails with
 * cannot-open rather than answering the nearest one.
 */
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

    /**
     * JPEG has nowhere to put an alpha channel, so a transparent pixel comes
     * back opaque rather than the write failing. A real 3.22.5 answers 255 for
     * that pixel's fourth channel, and so does this.
     */
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

    /**
     * Bytes and files fail differently because the caller looks in different
     * places, and neither may reach the host as a throwable -- which a missing
     * file did, straight out of the filesystem port.
     */
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

    /**
     * A GIF frame after the first is a patch rather than a picture -- a small
     * rectangle at an offset, covering only what changed -- so reading one
     * alone gives that patch. What a viewer shows, and what these checksums
     * are of, is every frame up to it drawn over the canvas in turn.
     */
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
