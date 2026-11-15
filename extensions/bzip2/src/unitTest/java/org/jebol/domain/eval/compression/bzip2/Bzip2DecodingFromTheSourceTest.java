package org.jebol.domain.eval.compression.bzip2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Bzip2DecodingFromTheSourceTest {

    private static final int ROOM_WHEN_NOT_SCHEDULED = 1 << 22;
    private static final int ERRORS_BEFORE_GIVING_UP = 3;

    static byte[] decodeWhole(byte[] input) {
        Bzip2DecompressionStream stream = new Bzip2DecompressionStream();
        Bzip2Buffers buffers = new Bzip2Buffers();
        buffers.input = input;
        buffers.availableIn = input.length;
        buffers.output = new byte[ROOM_WHEN_NOT_SCHEDULED];
        ByteArrayOutputStream whole = new ByteArrayOutputStream();
        for (;;) {
            buffers.nextOut = 0;
            buffers.availableOut = buffers.output.length;
            int answer = stream.decompress(buffers);
            whole.write(buffers.output, 0, buffers.nextOut);
            if (answer == Bzip2Codes.STREAM_END) {
                return whole.toByteArray();
            }
            assertThat(answer).isEqualTo(Bzip2Codes.OK);
        }
    }

    private static Map<String, byte[]> namedStreams() throws IOException {
        Map<String, byte[]> named = new HashMap<>();
        for (String[] row : Bzip2TestInputs.rows("bzip2-streams.tsv")) {
            named.put(row[0], HexFormat.of().parseHex(row[1]));
        }
        return named;
    }

    private static byte[] sourceOf(String source, Map<String, byte[]> named) throws IOException {
        String[] parts = source.split(":");
        return switch (parts[0]) {
            case "crafted" -> named.get(parts[1]);
            case "flip" -> {
                byte[] copy = named.get(parts[1]).clone();
                int bit = Integer.parseInt(parts[2]);
                copy[bit / 8] ^= (byte) (0x80 >> (bit % 8));
                yield copy;
            }
            case "cut" -> Arrays.copyOf(named.get(parts[1]), Integer.parseInt(parts[2]));
            case "stray" -> {
                byte[] whole = named.get(parts[1]);
                byte[] longer = Arrays.copyOf(whole, whole.length + 2);
                longer[whole.length] = 0x42;
                longer[whole.length + 1] = 0x5A;
                yield longer;
            }
            default -> Bzip2TestInputs.resource(source);
        };
    }

    private static String answerFor(byte[] input, int chunkLog, int roomLog, int schedule) {
        Bzip2DecompressionStream stream = new Bzip2DecompressionStream();
        Bzip2Buffers buffers = new Bzip2Buffers();
        buffers.input = input;
        int capacity = roomLog < 0 ? ROOM_WHEN_NOT_SCHEDULED : 1 << roomLog;
        buffers.output = new byte[capacity];
        int[] state = {schedule};
        int at = 0;
        int errors = 0;
        long totalIn = 0;
        long totalOut = 0;
        int calls = 0;
        long callsHash = Bzip2TestInputs.FNV_START;
        long outputHash = Bzip2TestInputs.FNV_START;
        int last;
        for (;;) {
            if (buffers.availableIn == 0 && at < input.length) {
                int chunk = chunkLog < 0
                        ? input.length - at
                        : 1 + (Bzip2TestInputs.nextRandom(state) & ((1 << chunkLog) - 1));
                chunk = Math.min(chunk, input.length - at);
                buffers.nextIn = at;
                buffers.availableIn = chunk;
                at += chunk;
            }
            int give = roomLog < 0 ? capacity : 1 + (Bzip2TestInputs.nextRandom(state) & (capacity - 1));
            buffers.nextOut = 0;
            buffers.availableOut = give;
            int before = buffers.availableIn;
            last = stream.decompress(buffers);
            int consumed = before - buffers.availableIn;
            int produced = give - buffers.availableOut;
            outputHash = Bzip2TestInputs.fold(outputHash, buffers.output, 0, produced);
            totalIn += consumed;
            totalOut += produced;
            callsHash = Bzip2TestInputs.foldWord(callsHash,
                    (last & 0xFFL) | ((long) consumed << 8) | ((long) produced << 36));
            calls++;
            if (last == Bzip2Codes.STREAM_END || last == Bzip2Codes.SEQUENCE_ERROR) {
                break;
            }
            if (last < 0 && stream.isReadingSelectors()) {
                break;
            }
            if (last < 0 && ++errors == ERRORS_BEFORE_GIVING_UP) {
                break;
            }
            if (last >= 0 && consumed == 0 && produced == 0 && buffers.availableIn == 0 && at >= input.length) {
                break;
            }
        }
        return last + "\t" + totalIn + "\t" + totalOut + "\t" + calls
                + "\t%016x\t%016x".formatted(callsHash, outputHash);
    }

    private static List<String> mismatches(List<String[]> rows, Map<String, byte[]> named) throws IOException {
        List<String> wrong = new ArrayList<>();
        for (String[] row : rows) {
            byte[] input = sourceOf(row[0], named);
            String digest = Bzip2TestInputs.digest(input, 0, input.length);
            String label = String.join(" ", row[0], row[2], row[3], row[4]);
            if (!digest.equals(row[1])) {
                wrong.add(label + " input " + digest);
                continue;
            }
            String want = String.join("\t", Arrays.copyOfRange(row, 5, 11));
            String got;
            try {
                got = answerFor(input, Integer.parseInt(row[2]), Integer.parseInt(row[3]),
                        (int) Long.parseLong(row[4]));
            } catch (RuntimeException failure) {
                got = failure.toString();
            }
            if (!got.equals(want)) {
                wrong.add(label + " -> " + got + " wanted " + want);
            }
        }
        return wrong;
    }

    private static List<String[]> rowsWhoseSourceStartsWith(String... prefixes) throws IOException {
        return Bzip2TestInputs.rows("bzip2-decode.tsv").stream()
                .filter(row -> Arrays.stream(prefixes).anyMatch(row[0]::startsWith))
                .toList();
    }

    @Test
    @DisplayName("Bzip2's own corpus, good and bad, decodes call by call as the C decodes it, whatever size the input and output arrive in")
    void decodesTheCorpusAsBzip2Does() throws IOException {
        List<String[]> rows = rowsWhoseSourceStartsWith("corpus/");
        assertThat(rows).hasSize(189);
        assertThat(mismatches(rows, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("Streams built to break each of the decoder's checks in turn answer as the C does, called again after each error")
    void decodesEveryCraftedStreamAsBzip2Does() throws IOException {
        List<String[]> rows = rowsWhoseSourceStartsWith("crafted:");
        assertThat(rows).hasSize(96);
        assertThat(mismatches(rows, namedStreams())).isEmpty();
    }

    @Test
    @DisplayName("Every flipped bit, every cut and stray octets after the end answer as the C does")
    void decodesEveryDamagedStreamAsBzip2Does() throws IOException {
        List<String[]> rows = rowsWhoseSourceStartsWith("flip:", "cut:", "stray:");
        assertThat(rows).hasSize(4887);
        assertThat(mismatches(rows, namedStreams())).isEmpty();
    }

    @Test
    @DisplayName("Called again after a selector error, where the C runs off its stack, the decoder answers a data error")
    void answersADataErrorWhereTheCRunsOffItsStack() throws IOException {
        byte[] input = namedStreams().get("selector-past-groups");
        Bzip2DecompressionStream stream = new Bzip2DecompressionStream();
        Bzip2Buffers buffers = new Bzip2Buffers();
        buffers.input = input;
        buffers.availableIn = input.length;
        buffers.output = new byte[64];
        buffers.availableOut = buffers.output.length;
        assertThat(stream.decompress(buffers)).isEqualTo(Bzip2Codes.DATA_ERROR);
        assertThat(stream.isReadingSelectors()).isTrue();
        for (int again = 0; again < 8; again++) {
            assertThat(stream.decompress(buffers)).isLessThanOrEqualTo(Bzip2Codes.OK);
        }
    }
}
