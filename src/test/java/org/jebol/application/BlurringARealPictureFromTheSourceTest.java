package org.jebol.application;

import org.jebol.adapter.host.JavaImages;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BLUR over a real photograph, checked by the checksum of every byte.
 *
 * <p>A synthetic picture pins the shape of the blur -- one bright spot on
 * black shows the kernel directly -- and cannot pin much else. Two hundred and
 * fifty-six pixels square of a real flower exercises the running total over
 * its whole length, every channel at once, and the widest radius the picture
 * allows, and a checksum over sixty-five thousand pixels catches a single byte
 * out of place where looking at the answer would not.
 *
 * <p>The checksums came from REBOL's own {@code image-test.r3}, and two of the
 * four are written there as negative numbers that a real 3.22.5 no longer
 * answers -- CHECKSUM stopped giving a signed number and the file was never
 * updated. What is asserted here is what a real 3.22.5 answers now, which is
 * the same number plus four thousand million, confirmed against the reference
 * C compiled on its own.
 *
 * <p>The last of the three is the one worth reading twice. A hundred thousand
 * comes down to half the shorter side, which on a picture of even width is one
 * pixel wider than the row -- so the C runs off the end of every row, reads
 * what the allocator left there, and blurs the bottom right of the picture
 * darker than it otherwise would be. What it reads is nothing, and this
 * answers the same.
 */
class BlurringARealPictureFromTheSourceTest {

    private static final Path VENDORED =
            Path.of("src", "test", "resources", "rebol-suite", "units", "files");

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(VENDORED));
        interpreter.useImages(new JavaImages());
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the picture arrives as the bytes REBOL reads from it")
    void thePictureArrivesAsTheBytesRebolReads() {
        assertThat(answerTo("""
                flower: load %flower.png
                reduce [mold flower/size  checksum to binary! flower 'crc32]"""))
                .isEqualTo("[\"256x256\" 922455477]");
    }

    @Test
    @DisplayName("a radius of nothing leaves every byte where it was")
    void aRadiusOfNothingLeavesEveryByteWhereItWas() {
        assertThat(answerTo("""
                flower: load %flower.png
                blur flower 0
                checksum to binary! flower 'crc32""")).isEqualTo("922455477");
    }

    @Test
    @DisplayName("and blurring it, and blurring the blur, to the byte")
    void blurringItAndBlurringTheBlur() {
        assertThat(answerTo("""
                flower: load %flower.png
                once: blur flower 5
                first-checksum: checksum to binary! once 'crc32
                twice: blur once 5
                reduce [first-checksum  checksum to binary! twice 'crc32]"""))
                .isEqualTo("[2594223955 3711460599]");
    }

    /**
     * Half of two hundred and fifty-six, which is as blurred as this picture
     * gets and the case where the C reads past the end of each row.
     */
    @Test
    @DisplayName("and the widest radius the picture allows")
    void theWidestRadiusThePictureAllows() {
        assertThat(answerTo("""
                flower: load %flower.png
                blur flower 100000
                checksum to binary! flower 'crc32""")).isEqualTo("1523895462");
    }
}
