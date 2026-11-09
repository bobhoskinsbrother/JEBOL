package org.jebol.adapter.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class Uts46Test {

    private static final Set<String> STATUSES_OF_CHECKS_A_BROWSER_LEAVES_OFF = Set.of("V2", "V3", "U1", "A4_1", "A4_2");

    private static final Pattern ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})|\\\\x\\{([0-9A-Fa-f]+)}");

    private record ConformanceCase(int line, String source, String expected, boolean refused) {
    }

    @Nested
    @DisplayName("Unicode's own conformance file for UTS #46 version 16.0.0")
    class TheConformanceFile {

        @Test
        @DisplayName("is the file it says it is: version 16.0.0, with 6,389 cases")
        void isTheFileItSaysItIs() throws IOException {
            String text = readConformanceFile();

            assertThat(text).contains("# Version: 16.0.0");
            assertThat(readCases(text)).hasSize(6389);
        }

        @Test
        @DisplayName("converts every case to ASCII without transitional processing, as the file says, the checks a browser leaves off aside")
        void convertsEveryCase() throws IOException {
            List<String> disagreements = new ArrayList<>();
            for (ConformanceCase each : readCases(readConformanceFile())) {
                Optional<String> converted = Uts46.toAscii(each.source());
                Optional<String> expected = each.refused() ? Optional.empty() : Optional.of(each.expected());
                if (!converted.equals(expected)) {
                    disagreements.add("line " + each.line() + ": " + each.source()
                            + " gave " + converted.orElse("(refused)") + ", the file says " + expected.orElse("(refused)"));
                }
            }

            assertThat(disagreements).isEmpty();
        }
    }

    @Nested
    @DisplayName("a host name")
    class AHostName {

        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
                "bücher.de, xn--bcher-kva.de",
                "BÜCHER.de, xn--bcher-kva.de",
                "münchen.de, xn--mnchen-3ya.de",
                "faß.de, xn--fa-hia.de",
                "bücher.test, xn--bcher-kva.test",
                "r3---sn.bücher.de, r3---sn.xn--bcher-kva.de",
                "bücher.de., xn--bcher-kva.de.",
                "bücher。de, xn--bcher-kva.de",
        })
        @DisplayName("written in Unicode is converted as a browser converts it")
        void convertsAsABrowserDoes(String written, String ascii) {
            assertThat(Uts46.toAsciiHostName(written)).contains(ascii);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"bü cher.de", "bü<cher.de", "bü%cher.de", "bü/cher.de", "bü:cher.de", "bü@cher.de"})
        @DisplayName("carrying a code point no domain may hold is refused")
        void refusesForbiddenCodePoints(String written) {
            assertThat(Uts46.toAsciiHostName(written)).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"­", "xn--ß.de", "a‍.de", "￿.de"})
        @DisplayName("that UTS #46 refuses, or that comes out empty, is refused")
        void refusesWhatUts46Refuses(String written) {
            assertThat(Uts46.toAsciiHostName(written)).isEmpty();
        }
    }

    private static String readConformanceFile() throws IOException {
        try (InputStream file = Uts46Test.class.getResourceAsStream("/unicode/IdnaTestV2.txt")) {
            assertThat(file).isNotNull();
            return new String(file.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<ConformanceCase> readCases(String text) {
        List<ConformanceCase> cases = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int at = 0; at < lines.length; at++) {
            String content = lines[at].replaceFirst("#.*", "");
            if (content.isBlank()) {
                continue;
            }
            String[] columns = Arrays.stream(content.split(";", -1)).map(String::strip).toArray(String[]::new);
            cases.add(readCase(at + 1, columns));
        }
        return cases;
    }

    private static ConformanceCase readCase(int line, String[] columns) {
        String source = unescape(columns[0]);
        String toUnicode = columns[1].isEmpty() ? source : unescape(columns[1]);
        String toAsciiN = columns[3].isEmpty() ? toUnicode : unescape(columns[3]);
        String statuses = columns[4].isEmpty() ? columns[2] : columns[4];
        return new ConformanceCase(line, source, toAsciiN, hasStatusABrowserChecks(statuses));
    }

    private static boolean hasStatusABrowserChecks(String statuses) {
        String inside = statuses.replace("[", "").replace("]", "").strip();
        return !inside.isEmpty() && Arrays.stream(inside.split(","))
                .map(String::strip)
                .anyMatch(status -> !STATUSES_OF_CHECKS_A_BROWSER_LEAVES_OFF.contains(status));
    }

    private static String unescape(String column) {
        if (column.equals("\"\"")) {
            return "";
        }
        Matcher escape = ESCAPE.matcher(column);
        StringBuilder plain = new StringBuilder();
        while (escape.find()) {
            String hex = escape.group(1) != null ? escape.group(1) : escape.group(2);
            escape.appendReplacement(plain, Matcher.quoteReplacement(Character.toString(Integer.parseInt(hex, 16))));
        }
        escape.appendTail(plain);
        return plain.toString();
    }
}
