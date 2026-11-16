package org.jebol.domain.eval.compression.brotli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrotliStreamingFromTheSourceTest {

    private static final String WORDS = "the quick brown fox jumps over a lazy dog ";

    private record Row(String key, List<String> calls) {
    }

    private static byte[] readCorpusSlice(int size) {
        try (InputStream in = BrotliStreamingFromTheSourceTest.class
                .getResourceAsStream("/brotli/mapsdatazrh-first-30k.bin")) {
            byte[] slice = in.readAllBytes();
            assertThat(slice).hasSizeGreaterThanOrEqualTo(size);
            return Arrays.copyOf(slice, size);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static byte[] makeInput(String kind, int size) {
        if (kind.equals("maps")) {
            return readCorpusSlice(size);
        }
        byte[] out = new byte[size];
        long x = 12345;
        for (int at = 0; at < size; at++) {
            x = (x * 1103515245L + 12345L) & 2147483647L;
            out[at] = kind.equals("noise")
                    ? (byte) ((x >> 16) & 255)
                    : (byte) WORDS.charAt((int) ((x >> 16) % WORDS.length()));
        }
        return out;
    }

    static String digest(byte[] data) {
        long hash = 0xcbf29ce484222325L;
        for (byte each : data) {
            hash ^= each & 0xff;
            hash *= 0x100000001b3L;
        }
        return String.format("%016x", hash);
    }

    static Stream<String> readRows(String kind) {
        try (InputStream in = BrotliStreamingFromTheSourceTest.class
                .getResourceAsStream("/brotli/module/stream-table.tsv")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> line.startsWith(kind + " "))
                    .toList().stream();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static Stream<String> encodeRows() {
        return readRows("encode");
    }

    private static Row parse(String line) {
        String[] fields = line.split("\t");
        return new Row(fields[0], new ArrayList<>(Arrays.asList(fields).subList(1, fields.length - 1)));
    }

    private static String call(char op, byte[] out) {
        return op + "1:" + out.length + ":" + digest(out);
    }

    private static List<String> replayEncode(String key) {
        String[] parts = key.split(" ");
        byte[] input = makeInput(parts[1], Integer.parseInt(parts[2]));
        BrotliStreamEncoder encoder = new BrotliStreamEncoder(Integer.parseInt(parts[3]));
        encoder.useMode(Integer.parseInt(parts[4]));
        String hint = parts[5];
        String ops = parts[6];
        List<String> calls = new ArrayList<>();
        boolean late = ops.startsWith("late-hint:");
        if (!hint.equals("-") && !late) {
            encoder.useSizeHint(Long.parseLong(hint));
        }
        if (ops.equals("finish")) {
            calls.add(call('E', encoder.compress(BrotliStreamEncoder.Operation.FINISH, input)));
            return calls;
        }
        if (late) {
            int first = Math.min(Integer.parseInt(ops.substring(10)), input.length);
            calls.add(call('P', encoder.compress(BrotliStreamEncoder.Operation.PROCESS,
                    Arrays.copyOfRange(input, 0, first))));
            calls.add("h" + (encoder.useSizeHint(Long.parseLong(hint)) ? 1 : 0));
            calls.add(call('E', encoder.compress(BrotliStreamEncoder.Operation.FINISH,
                    Arrays.copyOfRange(input, first, input.length))));
            return calls;
        }
        String style = ops.substring(0, ops.indexOf(':'));
        int chunk = Integer.parseInt(ops.substring(ops.indexOf(':') + 1));
        for (int at = 0; at < input.length; at += chunk) {
            byte[] piece = Arrays.copyOfRange(input, at, Math.min(input.length, at + chunk));
            if (style.equals("flush")) {
                calls.add(call('F', encoder.compress(BrotliStreamEncoder.Operation.FLUSH, piece)));
            } else {
                calls.add(call('P', encoder.compress(BrotliStreamEncoder.Operation.PROCESS, piece)));
                if (style.equals("read")) {
                    calls.add(call('R', encoder.compress(BrotliStreamEncoder.Operation.FLUSH, new byte[0])));
                }
            }
        }
        if (input.length == 0 && style.equals("flush")) {
            calls.add(call('F', encoder.compress(BrotliStreamEncoder.Operation.FLUSH, new byte[0])));
        }
        if (input.length == 0 && style.equals("read")) {
            calls.add(call('R', encoder.compress(BrotliStreamEncoder.Operation.FLUSH, new byte[0])));
        }
        calls.add(call('E', encoder.compress(BrotliStreamEncoder.Operation.FINISH, new byte[0])));
        return calls;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("encodeRows")
    @DisplayName("each streaming call gives out the bytes Brotli's own streaming API gives out")
    void encodesAsTheC(String line) {
        Row row = parse(line);

        assertThat(replayEncode(row.key())).containsExactlyElementsOf(row.calls());
    }

    static Stream<String> decodeRows() {
        return readRows("decode");
    }

    static Stream<String> cveRows() {
        return readRows("cve");
    }

    private static String outcome(BrotliStreamDecoder decoder, byte[] out) {
        return "D" + (decoder.isFinished() ? 1 : 2) + ":" + out.length + ":" + digest(out);
    }

    private static final byte[] EMPTY_STREAM_OF_ENCODER_COMPRESS = {6};

    private static byte[] compressAsEncoderCompress(byte[] input, int level) {
        return input.length == 0 ? EMPTY_STREAM_OF_ENCODER_COMPRESS : Brotli.compress(input, level);
    }

    private static List<String> replayDecode(String key) {
        String[] parts = key.split(" ");
        byte[] input = makeInput(parts[1], Integer.parseInt(parts[2]));
        byte[] encoded = compressAsEncoderCompress(input, Integer.parseInt(parts[3]));
        int chunk = Integer.parseInt(parts[4]);
        List<String> calls = new ArrayList<>();
        calls.add("stream:" + encoded.length + ":" + digest(encoded));
        BrotliStreamDecoder decoder = new BrotliStreamDecoder();
        for (int at = 0; at < encoded.length; at += chunk) {
            byte[] piece = Arrays.copyOfRange(encoded, at, Math.min(encoded.length, at + chunk));
            calls.add(outcome(decoder, decoder.decompress(piece)));
        }
        calls.add("finished:" + (decoder.isFinished() ? 1 : 0));
        return calls;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("decodeRows")
    @DisplayName("each streaming call to the decoder gives out what Brotli's own decoder gives out")
    void decodesAsTheC(String line) {
        Row row = parse(line);

        assertThat(replayDecode(row.key())).containsExactlyElementsOf(row.calls());
    }

    private static final int BLOCK_SIZE_CAP = BrotliBlockSwitchStreams.BLOCK_SIZE_CAP;
    private static final int WORD_LENGTH = BrotliBlockSwitchStreams.WORD_LENGTH;
    private static final int ONE_COMMAND_TYPE = 1;
    private static final int TWO_COMMAND_TYPES = 2;

    private static byte[] buildEmptyWordsThenWord(int emptyWords, int commandTypes) {
        return BrotliBlockSwitchStreams.buildEmptyWordsThenWord(emptyWords, commandTypes);
    }

    private static List<String> replayCve(String key) {
        String[] parts = key.split(" ");
        byte[] stream = buildEmptyWordsThenWord(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        List<String> calls = new ArrayList<>();
        calls.add("transform:" + BrotliBlockSwitchStreams.findFirstTransformEmptyingWord());
        calls.add("stream:" + stream.length + ":" + digest(stream));
        BrotliStreamDecoder decoder = new BrotliStreamDecoder();
        try {
            calls.add(outcome(decoder, decoder.decompress(stream)));
            calls.add("error:" + SUCCESS_CODE);
        } catch (IllegalArgumentException refused) {
            calls.add("D0:0:" + digest(new byte[0]));
            calls.add("error:" + BLOCK_SWITCH_ERROR_CODE);
            assertThat(refused).hasMessageContaining("block switch");
        }
        return calls;
    }

    private static final int BLOCK_SWITCH_ERROR_CODE = -17;
    private static final int SUCCESS_CODE = 1;

    private static Row parseCve(String line) {
        String[] fields = line.split("\t");
        return new Row(fields[0], new ArrayList<>(Arrays.asList(fields).subList(1, fields.length)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cveRows")
    @DisplayName("past 2^24 commands of one block type a stream is refused as Brotli 1.2.1 refuses it")
    void refusesBlockSwitchAsFixedC(String line) {
        Row row = parseCve(line);

        assertThat(replayCve(row.key())).containsExactlyElementsOf(row.calls());
    }

    @Nested
    class TheDecoder {

        @ParameterizedTest(name = "command block types {0}")
        @ValueSource(ints = {ONE_COMMAND_TYPE, TWO_COMMAND_TYPES})
        @DisplayName("2^24 commands, or distances, of one block type decode, and the next one is refused")
        void blockSizeCapIsInclusive(int commandTypes) {
            assertThat(Brotli.decompress(buildEmptyWordsThenWord(BLOCK_SIZE_CAP - 1, commandTypes), 0))
                    .hasSize(WORD_LENGTH);
            assertThatThrownBy(() -> Brotli.decompress(buildEmptyWordsThenWord(BLOCK_SIZE_CAP, commandTypes), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("block switch");
        }

        @ParameterizedTest(name = "command block types {0}")
        @ValueSource(ints = {ONE_COMMAND_TYPE, TWO_COMMAND_TYPES})
        @DisplayName("the refused block switch is refused when it arrives a byte at a time, never waited on")
        void blockSwitchRefusedWhileStreaming(int commandTypes) {
            byte[] stream = buildEmptyWordsThenWord(BLOCK_SIZE_CAP, commandTypes);
            BrotliStreamDecoder decoder = new BrotliStreamDecoder();
            int head = stream.length - 8;
            decoder.decompress(Arrays.copyOfRange(stream, 0, head));

            assertThatThrownBy(() -> {
                for (int at = head; at < stream.length; at++) {
                    decoder.decompress(Arrays.copyOfRange(stream, at, at + 1));
                }
            }).hasMessageContaining("block switch");
        }

        @ParameterizedTest(name = "quality {0}")
        @ValueSource(ints = {0, 1, 5, 11})
        @DisplayName("a stream fed a byte at a time gives back what one-shot decompression gives")
        void byteAtATimeIsOneShot(int quality) {
            byte[] input = makeInput("text", 5000);
            byte[] encoded = Brotli.compress(input, quality);
            BrotliStreamDecoder decoder = new BrotliStreamDecoder();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int at = 0; at < encoded.length; at++) {
                out.writeBytes(decoder.decompress(Arrays.copyOfRange(encoded, at, at + 1)));
            }

            assertThat(out.toByteArray()).isEqualTo(input);
            assertThat(decoder.isFinished()).isTrue();
        }

        @Test
        @DisplayName("a stream longer than its window decodes in pieces past several windows")
        void decodesPastSeveralWindows() {
            byte[] input = makeInput("noise", 300_000);
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(5);
            byte[] encoded = encoder.compress(BrotliStreamEncoder.Operation.FINISH, input);
            BrotliStreamDecoder decoder = new BrotliStreamDecoder();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int at = 0; at < encoded.length; at += 4096) {
                out.writeBytes(decoder.decompress(
                        Arrays.copyOfRange(encoded, at, Math.min(encoded.length, at + 4096))));
            }

            assertThat(out.toByteArray()).isEqualTo(input);
        }

        @Test
        @DisplayName("an uncompressed meta-block gives out its bytes as they arrive")
        void uncompressedBytesComeAsTheyArrive() {
            byte[] input = makeInput("noise", 1000);
            byte[] encoded = Brotli.compress(input, 5);
            BrotliStreamDecoder decoder = new BrotliStreamDecoder();

            byte[] first = decoder.decompress(Arrays.copyOfRange(encoded, 0, 500));

            assertThat(first).isNotEmpty();
            assertThat(input).startsWith(first);
        }

        @Test
        @DisplayName("a finished stream gives nothing more")
        void finishedGivesNothing() {
            BrotliStreamDecoder decoder = new BrotliStreamDecoder();
            decoder.decompress(Brotli.compress(makeInput("text", 100), 5));

            assertThat(decoder.isFinished()).isTrue();
            assertThat(decoder.decompress(new byte[0])).isEmpty();
        }

        private static byte[] buildCopyPastMetaBlock() {
            BrotliBits bits = new BrotliBits(64);
            bits.write(1, 0);
            bits.write(1, 1);
            bits.write(1, 0);
            bits.write(2, 0);
            bits.write(16, 3 - 1);
            bits.write(3, 0);
            bits.write(6, 0);
            bits.write(2, 0);
            bits.write(2, 0);
            bits.write(2, 1);
            bits.write(2, 0);
            bits.write(8, 'a');
            bits.write(2, 1);
            bits.write(2, 0);
            bits.write(10, INSERT_TWO_COPY_TWO_WITH_DISTANCE);
            bits.write(2, 1);
            bits.write(2, 0);
            bits.write(6, FIRST_PLAIN_DISTANCE_SYMBOL);
            bits.write(1, 0);
            bits.jumpToByteBoundary();
            return bits.written();
        }

        private static final int INSERT_TWO_COPY_TWO_WITH_DISTANCE = 128 + (2 << 3);
        private static final int FIRST_PLAIN_DISTANCE_SYMBOL = 16;

        @Test
        @DisplayName("a copy reaching past its meta-block is refused, as the C refuses it")
        void copyPastMetaBlockRefused() {
            assertThatThrownBy(() -> Brotli.decompress(buildCopyPastMetaBlock(), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("past the end of their meta-block");
        }

        private static byte[] buildInsertPastMetaBlock() {
            BrotliBits bits = new BrotliBits(64);
            bits.write(1, 0);
            bits.write(1, 1);
            bits.write(1, 0);
            bits.write(2, 0);
            bits.write(16, 2 - 1);
            bits.write(3, 0);
            bits.write(6, 0);
            bits.write(2, 0);
            bits.write(2, 0);
            bits.write(2, 1);
            bits.write(2, 0);
            bits.write(8, 'a');
            bits.write(2, 1);
            bits.write(2, 0);
            bits.write(10, INSERT_THREE_COPY_TWO);
            bits.write(2, 1);
            bits.write(2, 0);
            bits.write(6, 0);
            bits.jumpToByteBoundary();
            return bits.written();
        }

        private static final int INSERT_THREE_COPY_TWO = 3 << 3;

        @Test
        @DisplayName("a command inserting more than its meta-block holds is refused, as the C refuses it")
        void insertPastMetaBlockRefused() {
            assertThatThrownBy(() -> Brotli.decompress(buildInsertPastMetaBlock(), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("past the end of their meta-block");
        }
    }

    @Nested
    class TheEncoder {

        private static byte[] streamInPieces(BrotliStreamEncoder encoder, byte[] input, int piece,
                BrotliStreamEncoder.Operation each) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int at = 0; at < input.length; at += piece) {
                out.writeBytes(encoder.compress(each,
                        Arrays.copyOfRange(input, at, Math.min(input.length, at + piece))));
            }
            out.writeBytes(encoder.compress(BrotliStreamEncoder.Operation.FINISH, new byte[0]));
            return out.toByteArray();
        }

        @ParameterizedTest(name = "quality {0}")
        @ValueSource(ints = {0, 1, 2, 4, 5, 9, 10, 11})
        @DisplayName("a stream flushed every thousand bytes decodes back to its input")
        void flushedStreamDecodes(int quality) {
            byte[] input = makeInput("text", 70_000);

            byte[] stream = streamInPieces(new BrotliStreamEncoder(quality), input, 1000,
                    BrotliStreamEncoder.Operation.FLUSH);

            assertThat(Brotli.decompress(stream, 0)).isEqualTo(input);
        }

        @ParameterizedTest(name = "quality {0}")
        @ValueSource(ints = {0, 1, 3, 5, 11})
        @DisplayName("a stream fed in pieces past the first block decodes back to its input")
        void processedStreamDecodes(int quality) {
            byte[] input = makeInput("noise", 200_000);

            byte[] stream = streamInPieces(new BrotliStreamEncoder(quality), input, 65_537,
                    BrotliStreamEncoder.Operation.PROCESS);

            assertThat(Brotli.decompress(stream, 0)).isEqualTo(input);
        }

        @ParameterizedTest(name = "quality {0}")
        @ValueSource(ints = {2, 6, 11})
        @DisplayName("a stream given all its input at once with FINISH is the one-shot compression")
        void finishedAtOnceIsOneShot(int quality) {
            byte[] input = makeInput("text", 100_000);
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(quality);
            encoder.useSizeHint(input.length);

            assertThat(encoder.compress(BrotliStreamEncoder.Operation.FINISH, input))
                    .isEqualTo(Brotli.compress(input, quality));
        }

        @ParameterizedTest(name = "quality {0}")
        @ValueSource(ints = {0, 1})
        @DisplayName("one-shot compression one byte past the four megabyte fragment is the streamed compression")
        void oneShotPastFragmentIsStreamed(int quality) {
            byte[] input = makeInput("text", (1 << 22) + 1);
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(quality);

            byte[] streamed = encoder.compress(BrotliStreamEncoder.Operation.FINISH, input);

            assertThat(Brotli.compress(input, quality)).isEqualTo(streamed);
            assertThat(Brotli.decompress(streamed, 0)).isEqualTo(input);
        }

        @Test
        @DisplayName("a size hint and a mode are taken before the first call and refused after it")
        void settingsRefusedOnceStarted() {
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(5);

            assertThat(encoder.useSizeHint(1000)).isTrue();
            assertThat(encoder.useMode(2)).isTrue();
            encoder.compress(BrotliStreamEncoder.Operation.PROCESS, new byte[0]);

            assertThat(encoder.useSizeHint(2000)).isFalse();
            assertThat(encoder.useMode(0)).isFalse();
        }

        @Test
        @DisplayName("only FINISH finishes a stream, and a finished one gives nothing more for nothing")
        void finishingIsFinal() {
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(5);
            encoder.compress(BrotliStreamEncoder.Operation.FLUSH, makeInput("text", 100));
            assertThat(encoder.isFinished()).isFalse();

            encoder.compress(BrotliStreamEncoder.Operation.FINISH, new byte[0]);

            assertThat(encoder.isFinished()).isTrue();
            assertThat(encoder.compress(BrotliStreamEncoder.Operation.FINISH, new byte[0])).isEmpty();
            assertThat(encoder.compress(BrotliStreamEncoder.Operation.FLUSH, new byte[0])).isEmpty();
        }

        @Test
        @DisplayName("a finished stream refuses more input")
        void finishedRefusesInput() {
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(0);
            encoder.compress(BrotliStreamEncoder.Operation.FINISH, makeInput("text", 10));
            byte[] more = makeInput("text", 1);

            assertThat(encoder.canTake(more)).isFalse();
            assertThatThrownBy(() -> encoder.compress(BrotliStreamEncoder.Operation.PROCESS, more))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("a flush with nothing new pending gives out nothing")
        void emptyFlushAfterFlushIsEmpty() {
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(5);
            encoder.compress(BrotliStreamEncoder.Operation.FLUSH, makeInput("text", 5000));

            assertThat(encoder.compress(BrotliStreamEncoder.Operation.FLUSH, new byte[0])).isEmpty();
        }

        private static byte[] encodeInMode(int quality, int mode, byte[] input) {
            BrotliStreamEncoder encoder = new BrotliStreamEncoder(quality);
            encoder.useMode(mode);
            return encoder.compress(BrotliStreamEncoder.Operation.FINISH, input);
        }

        @Test
        @DisplayName("font mode changes nothing below quality four and changes the bytes from four, both decoding")
        void fontModeStartsAtQualityFour() {
            byte[] input = makeInput("text", 20_000);

            assertThat(encodeInMode(3, 2, input)).isEqualTo(encodeInMode(3, 0, input));
            byte[] font = encodeInMode(4, 2, input);
            assertThat(font).isNotEqualTo(encodeInMode(4, 0, input));
            assertThat(Brotli.decompress(font, 0)).isEqualTo(input);
        }
    }
}
