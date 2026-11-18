package org.jebol.domain.host;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HostNameCases {

    public static final Map<String, String> CONVERTED_AS_A_BROWSER_CONVERTS = Map.of(
            "bücher.de", "xn--bcher-kva.de",
            "BÜCHER.de", "xn--bcher-kva.de",
            "münchen.de", "xn--mnchen-3ya.de",
            "faß.de", "xn--fa-hia.de",
            "bücher.test", "xn--bcher-kva.test",
            "r3---sn.bücher.de", "r3---sn.xn--bcher-kva.de",
            "bücher.de.", "xn--bcher-kva.de.",
            "bücher。de", "xn--bcher-kva.de");

    public static final List<String> CARRYING_A_CODE_POINT_NO_DOMAIN_MAY_HOLD = List.of(
            "bü cher.de", "bü<cher.de", "bü%cher.de", "bü/cher.de", "bü:cher.de", "bü@cher.de");

    public static final List<String> REFUSED_BY_UTS46_OR_EMPTY = List.of("­", "xn--ß.de", "a‍.de", "￿.de");

    public static final String CONFORMANCE_VERSION = "# Version: 16.0.0";

    public static final int CONFORMANCE_CASES = 6389;

    private static final Set<String> STATUSES_OF_CHECKS_A_BROWSER_LEAVES_OFF = Set.of("V2", "V3", "U1", "A4_1", "A4_2");

    private static final Pattern ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})|\\\\x\\{([0-9A-Fa-f]+)\\}");

    public record ConformanceCase(int line, String source, String expected, boolean refused) {

        public Optional<String> expectedAnswer() {
            return refused ? Optional.empty() : Optional.of(expected);
        }
    }

    private HostNameCases() {
    }

    public static List<String> disagreementsWith(HostNames hostNames) {
        List<String> disagreements = new ArrayList<>();
        CONVERTED_AS_A_BROWSER_CONVERTS.forEach((written, ascii) -> {
            Optional<String> given = hostNames.asciiHostName(written);
            if (!given.equals(Optional.of(ascii))) {
                disagreements.add(written + " gave " + given.orElse("(refused)") + ", a browser gives " + ascii);
            }
        });
        for (String written : CARRYING_A_CODE_POINT_NO_DOMAIN_MAY_HOLD) {
            hostNames.asciiHostName(written).ifPresent(given ->
                    disagreements.add(written + " gave " + given + " where it carries a forbidden code point"));
        }
        for (String written : REFUSED_BY_UTS46_OR_EMPTY) {
            hostNames.asciiHostName(written).ifPresent(given ->
                    disagreements.add(written + " gave " + given + " where UTS #46 refuses it"));
        }
        return disagreements;
    }

    public static List<String> conformanceDisagreementsWith(Function<String, Optional<String>> toAscii) {
        List<String> disagreements = new ArrayList<>();
        for (ConformanceCase each : readConformanceCases()) {
            Optional<String> converted = toAscii.apply(each.source());
            if (!converted.equals(each.expectedAnswer())) {
                disagreements.add("line " + each.line() + ": " + each.source() + " gave "
                        + converted.orElse("(refused)") + ", the file says " + each.expectedAnswer().orElse("(refused)"));
            }
        }
        return disagreements;
    }

    public static String readConformanceFile() {
        try (InputStream file = HostNameCases.class.getResourceAsStream("/unicode/IdnaTestV2.txt")) {
            if (file == null) {
                throw new IllegalStateException("/unicode/IdnaTestV2.txt is not on the test's class path");
            }
            return new String(file.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    public static List<ConformanceCase> readConformanceCases() {
        List<ConformanceCase> cases = new ArrayList<>();
        String[] lines = readConformanceFile().split("\n", -1);
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
