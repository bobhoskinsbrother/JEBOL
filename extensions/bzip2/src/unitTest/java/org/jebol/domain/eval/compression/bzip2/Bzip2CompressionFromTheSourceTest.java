package org.jebol.domain.eval.compression.bzip2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bzip2CompressionFromTheSourceTest {

    private static final int ROOM_FOR_FINISH_BOUND = 600;

    private static byte[] inputOf(String[] row) {
        return Bzip2TestInputs.make(Integer.parseInt(row[0]), Integer.parseInt(row[1]), Integer.parseInt(row[2]),
                Integer.parseInt(row[3]), (int) Long.parseLong(row[4]));
    }

    private static byte[] compressWhole(byte[] input, int level) {
        Bzip2Buffers buffers = new Bzip2Buffers();
        buffers.input = input;
        buffers.availableIn = input.length;
        buffers.output = new byte[input.length + input.length / 100 + ROOM_FOR_FINISH_BOUND];
        buffers.availableOut = buffers.output.length;
        int answer = new Bzip2CompressionStream(level).compress(buffers, Bzip2Codes.FINISH);
        assertThat(answer).isEqualTo(Bzip2Codes.STREAM_END);
        return Arrays.copyOf(buffers.output, buffers.nextOut);
    }

    private static String oneShotAnswer(String[] row) {
        byte[] input = inputOf(row);
        String digest = Bzip2TestInputs.digest(input, 0, input.length);
        if (!digest.equals(row[5])) {
            return "input " + digest;
        }
        byte[] compressed = compressWhole(input, Integer.parseInt(row[6]));
        return compressed.length + "\t" + Bzip2TestInputs.digest(compressed, 0, compressed.length);
    }

    private static final class Stepping {
        final Bzip2CompressionStream stream;
        final Bzip2Buffers buffers = new Bzip2Buffers();
        final int[] schedule;
        final int roomLog;
        long calls = Bzip2TestInputs.FNV_START;
        long output = Bzip2TestInputs.FNV_START;
        long length;
        int count;

        Stepping(int level, int schedule, int roomLog) {
            this.stream = new Bzip2CompressionStream(level);
            this.schedule = new int[]{schedule};
            this.roomLog = roomLog;
            buffers.output = new byte[1 << roomLog];
        }

        int callWith(int action) {
            int give = Math.min(1 + (Bzip2TestInputs.nextRandom(schedule) & ((1 << roomLog) - 1)), 1 << roomLog);
            buffers.nextOut = 0;
            buffers.availableOut = give;
            int before = buffers.availableIn;
            int answer = stream.compress(buffers, action);
            int produced = give - buffers.availableOut;
            output = Bzip2TestInputs.fold(output, buffers.output, 0, produced);
            length += produced;
            long consumed = before - buffers.availableIn;
            long word = (answer & 0xFFL) | (action == Bzip2Codes.FINISH ? 0 : consumed << 8) | ((long) produced << 36);
            calls = Bzip2TestInputs.foldWord(calls, word);
            count++;
            assertThat(answer).isNotNegative();
            return answer;
        }
    }

    private static String streamAnswer(String[] row) {
        byte[] input = inputOf(row);
        String digest = Bzip2TestInputs.digest(input, 0, input.length);
        if (!digest.equals(row[5])) {
            return "input " + digest;
        }
        Stepping stepping = new Stepping(Integer.parseInt(row[6]), (int) Long.parseLong(row[7]),
                Integer.parseInt(row[9]));
        int chunkLog = Integer.parseInt(row[8]);
        stepping.buffers.input = input;
        int at = 0;
        while (at < input.length) {
            int chunk = Bzip2TestInputs.nextRandom(stepping.schedule) & ((1 << chunkLog) - 1);
            chunk = Math.min(chunk, input.length - at);
            int action = Bzip2TestInputs.nextRandom(stepping.schedule) % 10 < 8 ? Bzip2Codes.RUN : Bzip2Codes.FLUSH;
            stepping.buffers.nextIn = at;
            stepping.buffers.availableIn = chunk;
            at += chunk;
            for (;;) {
                if (action == Bzip2Codes.RUN && stepping.buffers.availableIn == 0) {
                    break;
                }
                int answer = stepping.callWith(action);
                if (action == Bzip2Codes.FLUSH && answer == Bzip2Codes.RUN_OK) {
                    break;
                }
            }
        }
        stepping.buffers.nextIn = input.length;
        stepping.buffers.availableIn = 0;
        while (stepping.callWith(Bzip2Codes.FINISH) != Bzip2Codes.STREAM_END) {
            assertThat(stepping.count).isLessThan(50_000_000);
        }
        return stepping.count + "\t%016x\t".formatted(stepping.calls) + stepping.length
                + "\t%016x".formatted(stepping.output);
    }

    @Test
    @DisplayName("Every input bzip2 compressed in one call, either side of each size its code changes at, compresses to the same octets")
    void everyOneShotCompressionMatchesBzip2() throws IOException {
        List<String[]> rows = Bzip2TestInputs.rows("bzip2-compress.tsv");
        assertThat(rows).hasSize(442);
        List<String> wrong = new ArrayList<>();
        for (String[] row : rows) {
            String want = row[7] + "\t" + row[8];
            String got = oneShotAnswer(row);
            if (!got.equals(want)) {
                wrong.add(String.join(" ", Arrays.copyOfRange(row, 0, 7)) + " -> " + got + " wanted " + want);
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("Every stream bzip2 compressed in chunks and flushes, in small rooms, answers each call as the C does")
    void everySteppedCompressionMatchesBzip2() throws IOException {
        List<String[]> rows = Bzip2TestInputs.rows("bzip2-compress-stream.tsv");
        assertThat(rows).hasSize(96);
        List<String> wrong = new ArrayList<>();
        for (String[] row : rows) {
            String want = String.join("\t", row[10], row[11], row[12], row[13]);
            String got = streamAnswer(row);
            if (!got.equals(want)) {
                wrong.add(String.join(" ", Arrays.copyOfRange(row, 0, 10)) + " -> " + got + " wanted " + want);
            }
        }
        assertThat(wrong).isEmpty();
    }

    @ParameterizedTest(name = "{0}.ref at level {1} compresses to {0}.bz2")
    @CsvSource({"sample1, 1", "sample2, 2", "sample3, 3"})
    @DisplayName("bzip2's own quick test: each sample compresses to the stream bzip2 checks in beside it")
    void compressesBzip2sOwnSamples(String sample, int level) throws IOException {
        byte[] reference = Bzip2TestInputs.resource("corpus/samples/" + sample + ".ref");
        byte[] compressed = Bzip2TestInputs.resource("corpus/samples/" + sample + ".bz2");
        assertThat(compressWhole(reference, level)).isEqualTo(compressed);
    }

    @Test
    @DisplayName("What every corpus stream decodes to, compressed again, is the octets the C wrote at levels 1, 5 and 9")
    void recompressesTheCorpusAsBzip2Does() throws IOException {
        List<String[]> rows = Bzip2TestInputs.rows("bzip2-recompress.tsv");
        assertThat(rows).hasSize(105);
        List<String> wrong = new ArrayList<>();
        for (String[] row : rows) {
            byte[] decoded = Bzip2DecodingFromTheSourceTest.decodeWhole(Bzip2TestInputs.resource(row[0]));
            String decodedDigest = Bzip2TestInputs.digest(decoded, 0, decoded.length);
            if (decoded.length != Integer.parseInt(row[1]) || !decodedDigest.equals(row[2])) {
                wrong.add(row[0] + " decoded " + decoded.length + " " + decodedDigest);
                continue;
            }
            byte[] compressed = compressWhole(decoded, Integer.parseInt(row[3]));
            String got = compressed.length + "\t" + Bzip2TestInputs.digest(compressed, 0, compressed.length);
            if (!got.equals(row[4] + "\t" + row[5])) {
                wrong.add(row[0] + " at " + row[3] + " -> " + got);
            }
        }
        assertThat(wrong).isEmpty();
    }
}
