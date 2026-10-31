package org.jebol.domain.eval.compression.zlibng;

import org.jebol.domain.eval.compression.deflate.DeflateFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ZlibNgFromTheSourceTest {

    private static final long BASIS = 0xcbf29ce484222325L;
    private static final int ROOM = 4 * 1024 * 1024;
    private static final int UNIX = 3;

    private final DeflateFixtures fixtures = new DeflateFixtures();

    private static byte[] read(String table) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                ZlibNgFromTheSourceTest.class.getResourceAsStream("/zlib-ng/" + table))) {
            return in.readAllBytes();
        }
    }

    private static List<String[]> rowsOf(byte[] table) {
        return new String(table, StandardCharsets.US_ASCII).lines().skip(1)
                .map(line -> line.split("\t", -1))
                .toList();
    }

    private static long fnv(long h, byte[] p, int from, int n) {
        long running = h;
        for (int i = 0; i < n; i++) {
            running ^= p[from + i] & 0xFF;
            running *= 0x100000001b3L;
        }
        return running;
    }

    private static String hexOf(long digest) {
        return "%016x".formatted(digest);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Squeezed(byte[] output, long codes) {
    }

    private static Squeezed squeeze(byte[] in, int level, int window, int mem, int strategy,
                                    int chunk, int flushMode, int outstep) {
        ZlibNgDeflater s = ZlibNgDeflater.open(level, window, mem, strategy, UNIX).orElseThrow();
        byte[] out = new byte[ROOM];
        int pos = 0;
        int at = 0;
        long codes = BASIS;
        do {
            int n = chunk == 0 ? in.length : Math.min(in.length - at, chunk);
            s.input(in, at, n);
            at += n;
            for (;;) {
                int room = outstep != 0 ? Math.min(ROOM - pos, outstep) : ROOM - pos;
                s.output(out, pos, room);
                int r = s.deflate(chunk != 0 ? flushMode : ZlibNgDeflater.Z_NO_FLUSH);
                pos += room - s.availOut();
                codes = fnv(codes, new byte[] {(byte) r}, 0, 1);
                if (s.availIn() == 0 && s.availOut() != 0) {
                    break;
                }
            }
        } while (at < in.length);
        for (;;) {
            int room = outstep != 0 ? Math.min(ROOM - pos, outstep) : ROOM - pos;
            s.output(out, pos, room);
            int r = s.deflate(ZlibNgDeflater.Z_FINISH);
            pos += room - s.availOut();
            codes = fnv(codes, new byte[] {(byte) r}, 0, 1);
            if (r == ZlibNgDeflater.Z_STREAM_END) {
                break;
            }
        }
        return new Squeezed(Arrays.copyOf(out, pos), codes);
    }

    private static byte[] damage(byte[] stream, int how) {
        int n = stream.length;
        byte[] damaged = stream.clone();
        return switch (how) {
            case 1 -> Arrays.copyOf(damaged, n / 2);
            case 2 -> n > 0 ? Arrays.copyOf(damaged, n - 1) : damaged;
            case 3 -> flip(damaged, n > 0, (int) ((n * 7L) / 13), 8);
            case 4 -> flip(damaged, n > 2, 2, 1);
            case 5 -> flip(damaged, n > 0, n - 1, 0x80);
            default -> damaged;
        };
    }

    private static byte[] flip(byte[] stream, boolean isLongEnough, int at, int bit) {
        if (isLongEnough) {
            stream[at] ^= (byte) bit;
        }
        return stream;
    }

    private static String inflateAsTheDriverDoes(byte[] in, int window, int chunk, int outstep) {
        ZlibNgInflater s = ZlibNgInflater.open(window).orElseThrow();
        byte[] out = new byte[ROOM];
        int at = 0;
        int pos = 0;
        int r;
        int idle = 0;
        for (;;) {
            int m = in.length - at;
            if (chunk != 0 && m > chunk) {
                m = chunk;
            }
            s.input(in, at, m);
            int room = outstep != 0 ? Math.min(ROOM - pos, outstep) : ROOM - pos;
            s.output(out, pos, room);
            r = s.inflate(ZlibNgInflater.Z_NO_FLUSH);
            int used = m - s.availIn();
            int made = room - s.availOut();
            at += used;
            pos += made;
            if (r == ZlibNgInflater.Z_STREAM_END || r == ZlibNgInflater.Z_NEED_DICT
                    || (r < 0 && r != ZlibNgInflater.Z_BUF_ERROR)) {
                break;
            }
            if (used == 0 && made == 0) {
                if (++idle >= 2) {
                    break;
                }
            } else {
                idle = 0;
            }
            if (r == ZlibNgInflater.Z_BUF_ERROR && at >= in.length) {
                break;
            }
        }
        return String.join("\t", String.valueOf(r), String.valueOf(s.totalIn()), String.valueOf(s.totalOut()),
                hexOf(fnv(BASIS, out, 0, pos)));
    }

    @Nested
    @DisplayName("the tables")
    class TheTables {

        @Test
        @DisplayName("are the ones the README describes: their row counts and checksums")
        void areWhatTheySay() throws IOException, NoSuchAlgorithmException {
            byte[] deflate = read("zlib-ng-deflate.tsv");
            byte[] inflate = read("zlib-ng-inflate.tsv");
            assertThat(rowsOf(deflate)).hasSize(6892).allMatch(row -> row.length == 12);
            assertThat(rowsOf(inflate)).hasSize(11520).allMatch(row -> row.length == 12);
            assertThat(sha256(deflate)).isEqualTo("c81dfaf9bf8c6e2192f9eb62d10062796caeb720ba0d93c6a2fe13bc89bbc726");
            assertThat(sha256(inflate)).isEqualTo("b948fbde2783142daf9204e5759ab6f7f6fc41012717b9c63e845b7979086cb3");
        }
    }

    @Nested
    @DisplayName("compressing")
    class Compressing {

        @Test
        @DisplayName("writes zlib-ng's bytes and return codes for every input, level, window, strategy and flush")
        void writesZlibNgsBytes() {
            List<String> differing = new ArrayList<>();
            for (String[] row : rowsOf(readUnchecked("zlib-ng-deflate.tsv"))) {
                byte[] in = fixtures.of(row[0], Integer.parseInt(row[1]));
                Squeezed squeezed = squeeze(in, Integer.parseInt(row[2]), Integer.parseInt(row[3]),
                        Integer.parseInt(row[4]), Integer.parseInt(row[5]), Integer.parseInt(row[6]),
                        Integer.parseInt(row[7]), Integer.parseInt(row[8]));
                String written = squeezed.output().length + "\t"
                        + hexOf(fnv(BASIS, squeezed.output(), 0, squeezed.output().length)) + "\t"
                        + hexOf(squeezed.codes());
                String expected = String.join("\t", row[9], row[10], row[11]);
                if (!written.equals(expected)) {
                    differing.add(String.join(" ", row) + " wrote " + written);
                }
            }
            assertThat(differing).isEmpty();
        }
    }

    @Nested
    @DisplayName("decompressing")
    class Decompressing {

        @Test
        @DisplayName("answers zlib-ng's result, totals and bytes for whole, cut and damaged streams under every window")
        void answersZlibNgsResults() {
            List<String> differing = new ArrayList<>();
            for (String[] row : rowsOf(readUnchecked("zlib-ng-inflate.tsv"))) {
                byte[] in = fixtures.of(row[0], Integer.parseInt(row[1]));
                byte[] stream = squeeze(in, Integer.parseInt(row[2]), Integer.parseInt(row[3]),
                        ZlibNgDeflater.DEFAULT_MEM_LEVEL, 0, 0, 0, 0).output();
                String answered = inflateAsTheDriverDoes(damage(stream, Integer.parseInt(row[4])),
                        Integer.parseInt(row[5]), Integer.parseInt(row[6]), Integer.parseInt(row[7]));
                String expected = String.join("\t", row[8], row[9], row[10], row[11]);
                if (!answered.equals(expected)) {
                    differing.add(String.join(" ", row) + " answered " + answered);
                }
            }
            assertThat(differing).isEmpty();
        }
    }

    private static byte[] readUnchecked(String table) {
        try {
            return read(table);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
