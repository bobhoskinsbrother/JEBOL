package org.jebol.application;

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
 * Rebol's WAV codec, and why nobody has noticed its test is wrong.
 *
 * <p>{@code codec-wav.reb}, which JEBOL loads rather than rewrites. The codec
 * reads the RIFF chunks, takes the {@code data} chunk as a vector of samples
 * of whatever width the {@code fmt } chunk declared, and writes one back.
 *
 * <p>REBOL's own {@code codecs-test.r3} asks for two CRC-24 checksums of that
 * sample data and both are stale. The codec is a delayed module, so
 * {@code find codecs 'wav} is false at boot and the whole {@code if} block
 * guarding those assertions is skipped -- which is why nothing has had to
 * update them since version 0.2.0 stopped storing the sound as a raw binary
 * and started storing it as a vector. Import the module by hand and a real
 * 3.22.5 answers 14119576 and 5445824 where the file asks for 3097828 and
 * 4283614.
 *
 * <p>So this is the record of what the codec really does, and the two suite
 * lines are on {@code fails-on-rebol-too.txt} with the session that settles
 * it. Every figure here was read off {@code ./r3-head} first.
 *
 * <p>The checksum alone would not be enough: two different decodings of the
 * same file can share a length, so the sample count, the first samples, the
 * last samples and the declared format are all pinned beside it.
 */
class WavCodecFromTheSourceTest {

    private static final Path VENDORED =
            Path.of("src", "test", "resources", "rebol-suite", "units", "files");

    private static Interpreter reaching(Path directory) {
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
        for (String name : new String[] {"drumloop.wav", "zblunk_02.wav"}) {
            Files.copy(VENDORED.resolve(name), directory.resolve(name),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    @DisplayName("a WAV file loads as its declared format and its samples")
    void aFileLoadsAsItsFormatAndItsSamples(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                sound: load %drumloop.wav
                reduce [
                    sound/type sound/rate sound/channels sound/bits
                    length? sound/data
                ]""")).isEqualTo("[wave 44100 1 16 42158]");
    }

    /**
     * The checksum the suite gets wrong, and enough beside it that a decoding
     * of the right length and the wrong contents cannot slip through.
     */
    @Test
    @DisplayName("and its samples are these, whatever REBOL's own test asks for")
    void theSamplesAreTheseWhateverTheSuiteAsksFor(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                sound: load %drumloop.wav
                reduce [
                    checksum to-binary sound/data 'crc24
                    copy/part sound/data 6
                    copy/part skip sound/data ((length? sound/data) - 4) 4
                ]"""))
                .isEqualTo("[14119576 #(int16! [256 0 0 0 0 0])"
                        + " #(int16! [-256 -256 -256 -256])]");
    }

    @Test
    @DisplayName("DECODE of the bytes answers the same as LOAD of the file")
    void decodingTheBytesMatchesLoadingTheFile(@TempDir Path directory)
            throws IOException {

        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                sound: decode 'WAV read %zblunk_02.wav
                reduce [
                    sound/type sound/rate sound/channels sound/bits
                    length? sound/data
                    checksum to-binary sound/data 'crc24
                    copy/part sound/data 6
                ]"""))
                .isEqualTo("[wave 44100 1 16 26505 5445824"
                        + " #(int16! [-148 -3 -115 -320 -359 -176])]");
    }

    /**
     * Nine samples go in and sixty-two bytes come out: forty-four of header
     * and eighteen of sound. The whole thing is pinned rather than its length,
     * because the header is where a reader on the other end finds the rate and
     * the width, and a wrong byte in it is silent until something tries to
     * play the file.
     */
    @Test
    @DisplayName("ENCODE writes a forty-four byte header and the samples after it")
    void encodeWritesAHeaderAndTheSamples(@TempDir Path directory) {
        assertThat(answerTo(reaching(directory), """
                enbase/flat encode 'wav #(i16! [0 -1000 -2000 -1000 0 1000 2000 1000 0]) 16"""))
                .isEqualTo("""
                        {524946463600000057415645666D7420100000000100010044AC0000885801000200\
                        10006461746112000000000018FC30F818FC0000E803D007E8030000}""");
    }

    @Test
    @DisplayName("and what it wrote decodes back to the samples it was given")
    void whatItWroteDecodesBack(@TempDir Path directory) {
        assertThat(answerTo(reaching(directory), """
                samples: #(i16! [0 -1000 -2000 -1000 0 1000 2000 1000 0])
                sound: decode 'wav encode 'wav :samples
                reduce [
                    sound/type sound/rate sound/channels sound/bits
                    samples = sound/data
                ]""")).isEqualTo("[wave 44100 1 16 #(true)]");
    }

    /**
     * Nothing, four bytes that are not a RIFF header, and the four letters
     * without the length that has to follow them. All three run off the end of
     * the binary dialect rather than being recognised and refused, which is
     * what the codec does: it reads the chunks and lets the reader complain.
     */
    @Test
    @DisplayName("decoding what is not a WAV runs off the end of the bytes")
    void decodingWhatIsNotAWavRunsOffTheEnd(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory);
        assertThat(errorIdFrom(interpreter, "decode 'wav #{}"))
                .isEqualTo("out-of-range");
        assertThat(errorIdFrom(interpreter, "decode 'wav #{00010203}"))
                .isEqualTo("out-of-range");
        assertThat(errorIdFrom(interpreter, """
                decode 'wav to-binary {RIFF}""")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("IDENTIFY says yes to a WAV and no to anything else")
    void identifySaysYesToAWavOnly(@TempDir Path directory) throws IOException {
        layOut(directory);
        assertThat(answerTo(reaching(directory), """
                reduce [
                    codecs/wav/identify read %drumloop.wav
                    codecs/wav/identify #{00010203}
                    codecs/wav/identify #{}
                ]""")).isEqualTo("[#(true) #(false) #(false)]");
    }

    /**
     * ENCODE declares its argument a vector, so a block of the same numbers
     * and a string of the same bytes are both refused at the call rather than
     * inside the codec.
     */
    @Test
    @DisplayName("ENCODE refuses anything that is not a vector of samples")
    void encodeRefusesWhatIsNotAVector(@TempDir Path directory) {
        Interpreter interpreter = reaching(directory);
        assertThat(errorIdFrom(interpreter, "encode 'wav [1 2 3]"))
                .isEqualTo("expect-arg");
        assertThat(errorIdFrom(interpreter, """
                encode 'wav {hello}""")).isEqualTo("expect-arg");
        assertThat(errorIdFrom(interpreter, "encode 'wav 5"))
                .isEqualTo("expect-arg");
    }
}
