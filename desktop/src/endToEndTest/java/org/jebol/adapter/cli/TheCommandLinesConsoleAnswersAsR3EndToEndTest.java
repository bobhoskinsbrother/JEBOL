package org.jebol.adapter.cli;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Order(3)
@Timeout(600)
class TheCommandLinesConsoleAnswersAsR3EndToEndTest {

    private static final String CASES = "/console/cases.txt";
    private static final String TRANSCRIPTS = "/console/transcripts.txt";
    private static final String WRAPPER = "/console/pty-run.sh";
    private static final long WAIT_MILLISECONDS = 20_000;
    private static final int CASES_AT_ONCE = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
    private static final HexFormat HEX = HexFormat.of();

    private static final Map<String, String> ANSWERED = new LinkedHashMap<>();
    private static final Map<String, String> RECORDED = new LinkedHashMap<>();

    private record Case(String name, String mode, List<String> switches, String script, List<String> steps) {
    }

    private static final class Printed {

        private final ByteArrayOutputStream seen = new ByteArrayOutputStream();
        private boolean ended;

        private synchronized void append(byte[] chunk, int length) {
            seen.write(chunk, 0, length);
            notifyAll();
        }

        private synchronized void end() {
            ended = true;
            notifyAll();
        }

        private synchronized int waitFor(byte[] wanted, int from, long deadline) throws InterruptedException {
            while (true) {
                int found = indexOf(seen.toByteArray(), wanted, from);
                if (found >= 0) {
                    return found + wanted.length;
                }
                long left = deadline - System.currentTimeMillis();
                if (ended || left <= 0) {
                    return -1;
                }
                wait(left);
            }
        }

        private synchronized boolean waitForEnd(long deadline) throws InterruptedException {
            while (!ended) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    return false;
                }
                wait(left);
            }
            return true;
        }

        private synchronized byte[] bytes() {
            return seen.toByteArray();
        }
    }

    @BeforeAll
    static void runEveryCase() throws Exception {
        Path wrapper = Files.createTempFile("pty-run", ".sh");
        Files.write(wrapper, readResource(WRAPPER));
        RECORDED.putAll(readRecordedTranscripts());
        List<Case> cases = readCases();
        ExecutorService pool = Executors.newFixedThreadPool(CASES_AT_ONCE);
        try {
            Map<String, Future<String>> running = new LinkedHashMap<>();
            for (Case each : cases) {
                running.put(each.name(), pool.submit(() -> runCase(wrapper, each)));
            }
            for (Map.Entry<String, Future<String>> each : running.entrySet()) {
                ANSWERED.put(each.getKey(), each.getValue().get());
            }
        } finally {
            pool.shutdownNow();
            Files.deleteIfExists(wrapper);
        }
    }

    @TestFactory
    Stream<DynamicTest> eachCaseAnswersAsR3Does() {
        return ANSWERED.keySet().stream().map(name -> DynamicTest.dynamicTest(name, () ->
                assertThat(ANSWERED.get(name)).isEqualTo(RECORDED.get(name))));
    }

    @TestFactory
    Stream<DynamicTest> everyRecordedCaseIsRun() {
        return Stream.of(DynamicTest.dynamicTest("the cases and the transcripts name the same cases", () ->
                assertThat(ANSWERED.keySet()).containsExactlyElementsOf(RECORDED.keySet())));
    }

    private static byte[] readResource(String named) throws IOException {
        try (InputStream carried = TheCommandLinesConsoleAnswersAsR3EndToEndTest.class.getResourceAsStream(named)) {
            assertThat(carried).as("%s is not on the test classpath", named).isNotNull();
            return carried.readAllBytes();
        }
    }

    private static List<Case> readCases() throws IOException {
        List<Case> cases = new ArrayList<>();
        for (String line : new String(readResource(CASES), StandardCharsets.UTF_8).split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            cases.add(new Case(fields[0], fields[1], List.of(fields[2].split(" ")),
                    new String(unescape(fields[3]), StandardCharsets.UTF_8),
                    List.of(Arrays.copyOfRange(fields, 4, fields.length))));
        }
        return cases;
    }

    private static Map<String, String> readRecordedTranscripts() throws IOException {
        Map<String, String> recorded = new LinkedHashMap<>();
        String whole = new String(readResource(TRANSCRIPTS), StandardCharsets.UTF_8);
        for (String block : whole.split("(?m)^(?=== )")) {
            if (block.isBlank()) {
                continue;
            }
            String name = block.substring(3, block.indexOf('\n'));
            recorded.put(name, block.stripTrailing());
        }
        return recorded;
    }

    private static String runCase(Path wrapper, Case asked) throws Exception {
        Path folder = Files.createTempDirectory("console-case");
        try {
            List<String> command = new ArrayList<>(List.of("bash", wrapper.toString(), asked.mode(), folder.toString(),
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-jar", System.getProperty("jebol.shippedJar")));
            command.addAll(asked.switches());
            if (!asked.script().isEmpty()) {
                Files.writeString(folder.resolve("case.r3"), asked.script() + "\n");
                command.add("case.r3");
            }
            Process running = new ProcessBuilder(command).directory(folder.toFile()).start();
            Printed printed = new Printed();
            Thread reading = Thread.ofVirtual().start(() -> readEverything(running.getInputStream(), printed));
            String verdict = drive(running.getOutputStream(), printed, asked.steps());
            long deadline = System.currentTimeMillis() + WAIT_MILLISECONDS;
            boolean exited = printed.waitForEnd(deadline)
                    && running.waitFor(Math.max(0, deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
            if (!exited) {
                verdict = verdict.isEmpty() ? "timed out" : verdict;
                running.destroyForcibly().waitFor();
            }
            reading.join();
            return transcript(asked.name(), folder, printed.bytes(), verdict);
        } finally {
            try (Stream<Path> inside = Files.walk(folder)) {
                inside.sorted(Comparator.reverseOrder()).forEach(each -> each.toFile().delete());
            }
        }
    }

    private static String drive(OutputStream typing, Printed printed, List<String> steps) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MILLISECONDS;
        int matched = 0;
        for (String step : steps) {
            if (step.startsWith(">")) {
                int after = printed.waitFor(unescape(step.substring(1)), matched, deadline);
                if (after < 0) {
                    return "never saw " + step.substring(1);
                }
                matched = after;
            } else if (step.startsWith("<")) {
                typing.write(unescape(step.substring(1)));
                typing.flush();
            } else if (step.equals(".")) {
                typing.close();
            }
        }
        return "";
    }

    private static void readEverything(InputStream from, Printed into) {
        byte[] chunk = new byte[65536];
        try {
            int read;
            while ((read = from.read(chunk)) >= 0) {
                into.append(chunk, read);
            }
        } catch (IOException closed) {
            throw new UncheckedIOException(closed);
        } finally {
            into.end();
        }
    }

    private static String transcript(String name, Path folder, byte[] seen, String verdict) throws IOException {
        Path before = folder.resolve("before");
        Path after = folder.resolve("after");
        Path exit = folder.resolve("exit");
        Path stderr = folder.resolve("stderr");
        String terminal = !Files.exists(before) ? "pipe"
                : Arrays.equals(Files.readAllBytes(before), readIfThere(after)) ? "restored" : "changed";
        List<String> lines = new ArrayList<>(List.of(
                "== " + name,
                "stdout: " + escape(seen),
                "stderr: " + escape(readIfThere(stderr)),
                "exit: " + (Files.exists(exit) ? Files.readString(exit).strip() : "none"),
                "terminal: " + terminal));
        if (!verdict.isEmpty()) {
            lines.add("verdict: " + verdict);
        }
        return String.join("\n", lines);
    }

    private static byte[] readIfThere(Path file) throws IOException {
        return Files.exists(file) ? Files.readAllBytes(file) : new byte[0];
    }

    private static int indexOf(byte[] within, byte[] wanted, int from) {
        for (int at = from; at + wanted.length <= within.length; at++) {
            if (Arrays.equals(within, at, at + wanted.length, wanted, 0, wanted.length)) {
                return at;
            }
        }
        return -1;
    }

    private static byte[] unescape(String written) {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        int at = 0;
        while (at < written.length()) {
            char each = written.charAt(at);
            if (each == '\\' && written.charAt(at + 1) == 'x') {
                reply.write(HEX.parseHex(written, at + 2, at + 4)[0]);
                at += 4;
            } else if (each == '\\') {
                reply.write(namedEscape(written.charAt(at + 1)));
                at += 2;
            } else {
                reply.writeBytes(String.valueOf(each).getBytes(StandardCharsets.UTF_8));
                at++;
            }
        }
        return reply.toByteArray();
    }

    private static int namedEscape(char named) {
        return switch (named) {
            case 'r' -> '\r';
            case 'n' -> '\n';
            case 't' -> '\t';
            case 'e' -> 0x1B;
            case '\\' -> '\\';
            default -> throw new IllegalArgumentException("no escape \\" + named);
        };
    }

    private static String escape(byte[] raw) {
        StringBuilder reply = new StringBuilder();
        for (byte signed : raw) {
            int each = signed & 0xFF;
            switch (each) {
                case 0x5C -> reply.append("\\\\");
                case 0x0D -> reply.append("\\r");
                case 0x0A -> reply.append("\\n");
                case 0x1B -> reply.append("\\e");
                default -> {
                    if (each >= 0x20 && each < 0x7F) {
                        reply.append((char) each);
                    } else {
                        reply.append("\\x").append(HEX.toHexDigits((byte) each));
                    }
                }
            }
        }
        return reply.toString();
    }
}
