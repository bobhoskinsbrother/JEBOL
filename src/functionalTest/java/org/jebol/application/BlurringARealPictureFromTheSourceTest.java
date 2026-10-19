package org.jebol.application;

import org.jebol.adapter.host.JavaImage;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BlurringARealPictureFromTheSourceTest {

    private static final Path VENDORED =
            Path.of("src", "testFixtures", "resources", "rebol-suite", "units", "files");

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootFileSystem(VENDORED));
        interpreter.useImages(new JavaImage());
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the picture arrives as the bytes REBOL reads from it")
    void readsThePictureAsTheBytesRebolReads() {
        assertThat(answerTo("""
                flower: load %flower.png
                reduce [mold flower/size  checksum to binary! flower 'crc32]"""))
                .isEqualTo("[\"256x256\" 922455477]");
    }

    @Test
    @DisplayName("a radius of nothing leaves every byte where it was")
    void leavesEveryByteWhereItWasAtARadiusOfNothing() {
        assertThat(answerTo("""
                flower: load %flower.png
                blur flower 0
                checksum to binary! flower 'crc32""")).isEqualTo("922455477");
    }

    @Test
    @DisplayName("and blurring it, and blurring the blur, to the byte")
    void blursItAndBlursTheBlur() {
        assertThat(answerTo("""
                flower: load %flower.png
                once: blur flower 5
                first-checksum: checksum to binary! once 'crc32
                twice: blur once 5
                reduce [first-checksum  checksum to binary! twice 'crc32]"""))
                .isEqualTo("[2594223955 3711460599]");
    }

    @Test
    @DisplayName("and the widest radius the picture allows")
    void blursAtTheWidestRadiusThePictureAllows() {
        assertThat(answerTo("""
                flower: load %flower.png
                blur flower 100000
                checksum to binary! flower 'crc32""")).isEqualTo("1523895462");
    }
}
