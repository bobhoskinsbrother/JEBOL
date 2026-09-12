package org.jebol.application;

import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QoiCodecFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static Interpreter reaching(Path directory) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.FILES));
        interpreter.useFileSystem(FileSystemPort.rootedAt(directory));
        return interpreter;
    }

    @Test
    @DisplayName("the codec is registered at boot, like text and markup")
    void theCodecIsRegisteredAtBoot() {
        assertThat(answerTo("""
                reduce [
                    system/codecs/qoi/name system/codecs/qoi/type
                    system/codecs/qoi/suffixes handle? system/codecs/qoi/entry
                ]""")).isEqualTo("[qoi image [%.qoi] #(true)]");
    }

    @Test
    @DisplayName("and its suffix reaches it through the file-types catalogue")
    void theSuffixReachesTheCodec() {
        assertThat(answerTo("select system/catalog/file-types %.qoi"))
                .isEqualTo("qoi");
    }

    @Test
    @DisplayName("one white pixel is a header, one difference chunk and the end marker")
    void aOnePixelImageEncodesToTheseBytes() {
        assertThat(answerTo("""
                enbase/flat encode 'qoi make image! 1x1 16"""))
                .isEqualTo("\"716F696600000001000000010400550000000000000001\"");
    }

    @Test
    @DisplayName("pixels the same as the one before become a run")
    void aRunOfIdenticalPixels() {
        assertThat(answerTo("""
                enbase/flat encode 'qoi make image! 4x1 16"""))
                .isEqualTo("\"716F69660000000400000001040055C20000000000000001\"");
    }

    @Test
    @DisplayName("a run stops at sixty-two and starts another")
    void aRunAtItsLongest() {
        assertThat(answerTo("""
                reduce [
                    enbase/flat encode 'qoi make image! 63x1 16
                    enbase/flat encode 'qoi make image! 64x1 16
                ]"""))
                .isEqualTo("[\"716F69660000003F00000001040055FD0000000000000001\""
                        + " \"716F69660000004000000001040055FDC00000000000000001\"]");
    }

    @Test
    @DisplayName("a colour seen before is written as an index into the table")
    void aColourSeenBeforeIsIndexed() {
        assertThat(answerTo("""
                i: make image! [4x1 #{C80000 000000 C80000 000000}]
                enbase/flat encode 'qoi i 16"""))
                .isEqualTo("{716F696600000004000000010400FE0000C8FE0000002D35"
                        + "0000000000000001}");
    }

    @Test
    @DisplayName("a step of one, of five, and one too far for either")
    void theThreeSizesOfStep() {
        assertThat(answerTo("""
                small: make image! [2x1 #{000000 010101}]
                middling: make image! [2x1 #{000000 050505}]
                far: make image! [2x1 #{000000 C8FF32}]
                reduce [
                    enbase/flat encode 'qoi small 16
                    enbase/flat encode 'qoi middling 16
                    enbase/flat encode 'qoi far 16
                ]"""))
                .isEqualTo("[\"716F696600000002000000010400C07F0000000000000001\""
                        + " \"716F696600000002000000010400C0A5880000000000000001\""
                        + " {716F696600000002000000010400C0FE32FFC80000000000000001}]");
    }

    @Test
    @DisplayName("a change of alpha writes all four channels")
    void aChangeOfAlphaWritesAllFour() {
        assertThat(answerTo("""
                i: make image! 2x1
                i/1: 0.0.0.255
                i/2: 0.0.0.128
                enbase/flat encode 'qoi i 16"""))
                .isEqualTo("{716F696600000002000000010400C0FF00000080"
                        + "0000000000000001}");
    }

    @Test
    @DisplayName("the channels go in in REBOL's order, not the format's")
    void theChannelsGoInInRebolsOrder() {
        assertThat(answerTo("""
                reduce [
                    enbase/flat encode 'qoi make image! [1x1 #{FF0000}] 16
                    enbase/flat encode 'qoi make image! [1x1 #{0000FF}] 16
                ]"""))
                .isEqualTo("[\"716F696600000001000000010400690000000000000001\""
                        + " \"716F6966000000010000000104005A0000000000000001\"]");
    }

    @Test
    @DisplayName("every picture survives the round trip")
    void everyPictureSurvivesTheRoundTrip() {
        assertThat(answerTo("""
                one: make image! 1x1
                four: make image! [2x2 #{C800000000C800C800FFFFFF}]
                big: make image! 256x256
                wide: make image! 5x1
                tall: make image! 1x5
                clear-ish: make image! 3x1
                clear-ish/1: 10.20.30.40
                clear-ish/2: 10.20.30.200
                clear-ish/3: 90.20.30.200
                reduce [
                    equal? one decode 'qoi encode 'qoi one
                    equal? four decode 'qoi encode 'qoi four
                    equal? big decode 'qoi encode 'qoi big
                    equal? wide decode 'qoi encode 'qoi wide
                    equal? tall decode 'qoi encode 'qoi tall
                    equal? clear-ish decode 'qoi encode 'qoi clear-ish
                ]"""))
                .isEqualTo("[#(true) #(true) #(true) #(true) #(true) #(true)]");
    }

    @Test
    @DisplayName("bytes that are not a QOI image are refused, but a short one is not")
    void bytesThatAreNotAQoiImageAreRefused() {
        assertThat(errorIdFrom("decode 'qoi #{}")).isEqualTo("bad-media");
        assertThat(errorIdFrom(
                "decode 'qoi #{6E6F70650000000100000001040055 0000000000000001}"))
                .isEqualTo("bad-media");
        assertThat(errorIdFrom("decode 'qoi #{716F696600000001000000010400}"))
                .isEqualTo("bad-media");
        assertThat(answerTo("""
                short: decode 'qoi #{716F6966000000FF000000FF040055 0000000000000001}
                short/size""")).isEqualTo("255x255");
    }

    @Test
    @DisplayName("something that is not an image, and something that is not bytes")
    void theWrongDatatypesAreRefused() {
        assertThat(errorIdFrom("encode 'qoi \"not a picture\"")).isEqualTo("invalid-arg");
        assertThat(errorIdFrom("decode 'qoi \"not bytes\"")).isEqualTo("invalid-arg");
    }

    @Test
    @DisplayName("SAVE and LOAD find the codec by the file's suffix")
    void saveAndLoadFindItBySuffix(@TempDir Path directory) {
        assertThat(answerTo(reaching(directory), """
                img: make image! 256x256
                saved: save %test.qoi img
                back: load %test.qoi
                reduce [file? saved equal? img back]"""))
                .isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("and codecs/qoi/size? reads the header, as codec-image-ext.reb adds")
    void theSizeFunctionTheMezzAdds(@TempDir Path directory) {
        assertThat(answerTo(reaching(directory), """
                save %test.qoi make image! 2x2
                system/codecs/qoi/size? %test.qoi""")).isEqualTo("2x2");
    }
}
