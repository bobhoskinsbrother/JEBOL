package org.jebol.application;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.domain.host.ClipboardPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

class TheRebolBugsTranscriptFromTheSourceTest {

    private static final String SEPARATOR = " | ";

    private static final Set<String> NOT_PATCHED_IN_THE_FIXED_BUILD = Set.of("clipboard open twice");

    private static final Map<String, String> DELIBERATE = Map.ofEntries(
            entry("find 128", "#{80}"),
            entry("find 200", "#{C8}"),
            entry("find 254", "#{FE}"),
            entry("find 255", "#{FF}"),
            entry("find/case 255", "#{FF}"),
            entry("find/tail 255", "#{01}"),
            entry("find/match 255", "#{FF00}"),
            entry("find/last 255", "#{FF00}"),
            entry("find/reverse 255", "#{FF00}"),
            entry("find/skip 255", "#{FF00}"),
            entry("find/part 255 inside", "#{FF}"),
            entry("select 254", "1"),
            entry("select 255", "1"),
            entry("image block of two tuples", "[image 2x1 #{010203040506} #{FFFF}]"),
            entry("image block of four-tuples", "[image 2x1 #{010203040506} #{FF00}]"),
            entry("image block too short", "[image 2x1 #{010203FFFFFF} #{FFFF}]"),
            entry("image block too long", "[image 2x1 #{010203040506} #{FFFF}]"),
            entry("image block empty", "[image 2x1 #{FFFFFFFFFFFF} #{FFFF}]"),
            entry("image block not tuples", "[error invalid-arg x]"),
            entry("image zero size block", "[image 0x0 #{} #{}]"),
            entry("image block from its index", "[image 2x1 #{040506070809} #{FFFF}]"),
            entry("image block integer", "[error invalid-arg 7]"),
            entry("diff 0x0 1x3 past bottom", "49.9999999999999%"),
            entry("diff 0x0 3x3 past both", "49.9999999999999%"),
            entry("diff 1x0 1x3 past bottom", "49.9999999999999%"),
            entry("diff 0x1 2x2 past bottom", "100%"),
            entry("diff 1x1 5x5 past both", "100%"),
            entry("clipboard open twice", "[error already-open clipboard://]"));

    private static Map<String, String> rebol;
    private static Map<String, String> rebolFixed;
    private static Map<String, String> jebol;

    private static final class AnEmptyClipboard implements ClipboardPort {

        @Override
        public Optional<String> read() {
            return Optional.of("");
        }

        @Override
        public boolean write(String text) {
            return true;
        }
    }

    @BeforeAll
    static void runTheProbes(@TempDir Path root) throws IOException {
        rebol = readTranscript(readResource("probes.txt"));
        rebolFixed = readTranscript(readResource("probes-fixed.txt"));
        Files.writeString(root.resolve("probes.r3"), readResource("probes.r3"));
        StringBuilder printed = new StringBuilder();
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts()
                        .withFiles(FileSystemPort.rootFileSystem(root))
                        .withClipboard(new AnEmptyClipboard()),
                printed::append,
                Bounds.standard()
                        .grant(HostService.FILES)
                        .grant(HostService.WORKING_DIRECTORY)
                        .grant(HostService.CLIPBOARD)
                        .grant(HostService.CONSOLE)
                        .grant(HostService.NETWORK)
                        .grant(HostService.WINDOWS)
                        .withoutTimeLimit());
        interpreter.run("do %probes.r3");
        jebol = readTranscript(printed.toString());
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = TheRebolBugsTranscriptFromTheSourceTest.class
                .getResourceAsStream("/r3-bugs/" + name)) {
            assertThat(in).as("/r3-bugs/%s is on the test class path", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> readTranscript(String text) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            int split = line.indexOf(SEPARATOR);
            if (split > 0) {
                answers.put(line.substring(0, split), line.substring(split + SEPARATOR.length()));
            }
        }
        return answers;
    }

    @Test
    @DisplayName("JEBOL answers every probe the transcript holds, and nothing else")
    void answersEveryProbe() {
        assertThat(rebol).hasSize(147);
        assertThat(jebol.keySet()).containsExactlyElementsOf(rebol.keySet());
    }

    @Test
    @DisplayName("every probe that is not a deliberate difference answers what a real 3.22.5 answers")
    void agreesWithRebolOutsideTheDeliberateLines() {
        List<String> disagreeing = rebol.keySet().stream()
                .filter(label -> !DELIBERATE.containsKey(label))
                .filter(label -> !rebol.get(label).equals(jebol.get(label)))
                .map(label -> label + SEPARATOR + "R3 " + rebol.get(label) + SEPARATOR + "JEBOL " + jebol.get(label))
                .toList();
        assertThat(disagreeing).isEmpty();
    }

    @Test
    @DisplayName("every deliberate line names a probe in the transcript")
    void namesProbesThatExist() {
        assertThat(rebol.keySet()).containsAll(DELIBERATE.keySet());
    }

    @Test
    @DisplayName("every deliberate line answers otherwise than a real 3.22.5 does")
    void differsFromRebolOnTheDeliberateLines() {
        DELIBERATE.forEach((label, answer) ->
                assertThat(answer).as(label).isNotEqualTo(rebol.get(label)));
    }

    @Test
    @DisplayName("JEBOL answers each deliberate line as written here")
    void answersTheDeliberateLinesAsWritten() {
        DELIBERATE.forEach((label, answer) ->
                assertThat(jebol.get(label)).as(label).isEqualTo(answer));
    }

    @Test
    @DisplayName("an R3 built with fixes.patch answers each patched deliberate line the same way")
    void agreesWithTheFixedBuild() {
        assertThat(rebolFixed.keySet()).containsExactlyElementsOf(rebol.keySet());
        DELIBERATE.forEach((label, answer) -> {
            if (!NOT_PATCHED_IN_THE_FIXED_BUILD.contains(label)) {
                assertThat(rebolFixed.get(label)).as(label).isEqualTo(answer);
            }
        });
    }

    @Test
    @DisplayName("the fixed build differs from a real 3.22.5 only on the deliberate lines")
    void fixedBuildDiffersOnlyOnTheDeliberateLines() {
        List<String> differing = rebol.keySet().stream()
                .filter(label -> !rebol.get(label).equals(rebolFixed.get(label)))
                .toList();
        assertThat(DELIBERATE.keySet()).containsAll(differing);
    }
}
