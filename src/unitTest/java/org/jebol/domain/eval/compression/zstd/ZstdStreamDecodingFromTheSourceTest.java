package org.jebol.domain.eval.compression.zstd;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ZstdStreamDecodingFromTheSourceTest {

    private static final int WHOLE = 0;
    private static final int RANDOM_CHUNKS = 1;
    private static final long FNV_START = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int MOST_CALLS_WITHOUT_PROGRESS = 32;

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                ZstdStreamDecodingFromTheSourceTest.class.getResourceAsStream("/zstd/" + name))) {
            return in.readAllBytes();
        }
    }

    private static List<String[]> rowsOf(String table) throws IOException {
        return new String(resource(table), StandardCharsets.US_ASCII).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .toList();
    }

    private static long fnv(long running, byte[] octets, int from, int length) {
        long hash = running;
        for (int at = from; at < from + length; at++) {
            hash ^= octets[at] & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static long foldWord(long running, long word) {
        byte[] octets = new byte[8];
        ZstdOctets.writeLong(octets, 0, word);
        return fnv(running, octets, 0, 8);
    }

    private static int nextRandom(int[] seed) {
        int value = seed[0];
        value *= (int) 2654435761L;
        value ^= (int) 2246822519L;
        value = Integer.rotateLeft(value, 13);
        seed[0] = value;
        return value >>> 5;
    }

    private record Frames(byte[] all, Map<Integer, int[]> placed) {

        byte[] frame(int id) {
            int[] at = placed.get(id);
            return Arrays.copyOfRange(all, at[0], at[0] + at[1]);
        }
    }

    private static Frames frames() throws IOException {
        Map<Integer, int[]> placed = new HashMap<>();
        for (String[] row : rowsOf("zstd-frames.tsv")) {
            placed.put(Integer.parseInt(row[0]), new int[]{Integer.parseInt(row[1]), Integer.parseInt(row[2])});
        }
        return new Frames(resource("zstd-frames.bin"), placed);
    }

    private static byte[] mutate(Frames frames, int id, String mutation) {
        byte[] frame = frames.frame(id);
        return switch (mutation.charAt(0)) {
            case 't' -> Arrays.copyOf(frame, Integer.parseInt(mutation.substring(1)));
            case 'x' -> {
                int at = Integer.parseInt(mutation.substring(1, mutation.indexOf(':')));
                frame[at] ^= (byte) Integer.parseInt(mutation.substring(mutation.indexOf(':') + 1));
                yield frame;
            }
            case 'a' -> {
                int count = Integer.parseInt(mutation.substring(1, mutation.indexOf(':')));
                int[] seed = {Integer.parseInt(mutation.substring(mutation.indexOf(':') + 1))};
                byte[] longer = Arrays.copyOf(frame, frame.length + count);
                for (int each = 0; each < count; each++) {
                    longer[frame.length + each] = (byte) nextRandom(seed);
                }
                yield longer;
            }
            case 'f' -> {
                byte[] other = frames.frame(Integer.parseInt(mutation.substring(1)));
                byte[] both = Arrays.copyOf(frame, frame.length + other.length);
                System.arraycopy(other, 0, both, frame.length, other.length);
                yield both;
            }
            default -> frame;
        };
    }

    private static String decode(byte[] source, int schedule, int seedValue, int capacity) {
        ZstdDecompressionStream stream = new ZstdDecompressionStream();
        byte[] output = new byte[Math.max(capacity, 1)];
        int read = 0;
        int written = 0;
        long returns = FNV_START;
        long calls = 0;
        int callsWithoutProgress = 0;
        long result = 0;
        String outcome;
        int[] seed = {seedValue};
        for (;;) {
            int available;
            int room;
            if (schedule == WHOLE) {
                available = source.length - read;
                room = capacity - written;
            } else {
                int maxLog = schedule == RANDOM_CHUNKS ? 18 : 4;
                int inLog = Integer.remainderUnsigned(nextRandom(seed), maxLog);
                int inChunk = 1 + (nextRandom(seed) & ((1 << inLog) - 1));
                int outLog = Integer.remainderUnsigned(nextRandom(seed), maxLog);
                int outChunk = nextRandom(seed) & ((1 << outLog) - 1);
                available = Math.min(inChunk, source.length - read);
                room = Math.min(outChunk, capacity - written);
            }
            ZstdStreamBuffer input = new ZstdStreamBuffer(source, read + available, read);
            ZstdStreamBuffer out = new ZstdStreamBuffer(output, written + room, written);
            boolean isFailed = false;
            try {
                result = stream.decompress(out, input);
                outcome = Long.toString(result);
            } catch (ZstdFailure failure) {
                result = -failure.error().code();
                outcome = "e" + failure.error().code();
                isFailed = true;
            }
            calls++;
            returns = foldWord(returns, result);
            callsWithoutProgress = input.position == read && out.position == written ? callsWithoutProgress + 1 : 0;
            read = input.position;
            written = out.position;
            if (isFailed || (result == 0 && read == source.length)
                    || callsWithoutProgress >= MOST_CALLS_WITHOUT_PROGRESS) {
                break;
            }
        }
        return "%d\t%d\t%d\t%016x\t%016x\t%s".formatted(calls, read, written,
                fnv(FNV_START, output, 0, written), returns, outcome);
    }

    @Test
    @DisplayName("Every frame zstd's stream decoder was fed, whole, damaged, cut or chunked, decodes as zstd decoded it")
    void everyFrameDecodesAsZstdDecodedIt() throws IOException {
        Frames frames = frames();
        List<String[]> rows = rowsOf("zstd-decode.tsv");
        List<String> wrong = new ArrayList<>();
        for (String[] row : rows) {
            byte[] source = mutate(frames, Integer.parseInt(row[0]), row[1]);
            String got = decode(source, Integer.parseInt(row[2]), (int) Long.parseLong(row[3]),
                    Integer.parseInt(row[4]));
            String want = String.join("\t", Arrays.copyOfRange(row, 5, 11));
            if (!got.equals(want)) {
                wrong.add(String.join(" ", Arrays.copyOfRange(row, 0, 5)) + " -> " + got + " wanted " + want);
            }
        }
        assertThat(rows).hasSize(9426);
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("The frames the table decodes are the ones its generator wrote, checked by length and digest")
    void theFramesAreTheOnesTheGeneratorWrote() throws IOException {
        byte[] all = resource("zstd-frames.bin");
        assertThat(all).hasSize(636659);
        assertThat("%016x".formatted(fnv(FNV_START, all, 0, all.length))).isEqualTo(FRAMES_DIGEST);
    }

    @Test
    @DisplayName("The table holds frames that decode, and frames refused for each reason zstd gives")
    void theTableHoldsSuccessesAndEachRefusal() throws IOException {
        List<String> results = rowsOf("zstd-decode.tsv").stream().map(row -> row[10]).distinct().toList();
        assertThat(results).contains("0", "e10", "e14", "e16", "e20", "e22", "e30", "e32", "e70", "e72", "e80",
                "e82");
    }

    @Test
    @DisplayName("A frame cut inside its header is never refused: zstd goes on asking for the octets that will not come")
    void aFrameCutInsideItsHeaderGoesOnAsking() throws IOException {
        List<String[]> stopped = rowsOf("zstd-decode.tsv").stream()
                .filter(row -> Integer.parseInt(row[5]) >= MOST_CALLS_WITHOUT_PROGRESS)
                .filter(row -> row[1].startsWith("t") && row[7].equals("0") && !row[10].startsWith("e"))
                .toList();
        assertThat(stopped).hasSize(2109);
        assertThat(stopped).allSatisfy(row -> assertThat(Integer.parseInt(row[6])).isLessThanOrEqualTo(9));
    }

    private static final String FRAMES_DIGEST ="15c1a1f2ca780529";
}
