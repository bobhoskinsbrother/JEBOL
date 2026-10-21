package org.jebol.domain.host;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

record IpAddressText(String written) {

    private static final Pattern OCTET = Pattern.compile("0|[1-9][0-9]{0,2}");
    private static final Pattern HEXTET = Pattern.compile("[0-9A-Fa-f]{1,4}");
    private static final int IPV4_OCTETS = 4;
    private static final int IPV6_HEXTETS = 8;
    private static final int MAX_OCTET = 255;
    private static final String ZERO_RUN = "::";

    Optional<String> writeCanonicalHex() {
        if (written.contains(":")) {
            return writeIpv6AsHex(unbracket(written));
        }
        return writeIpv4AsHex(written);
    }

    private String unbracket(String text) {
        return text.startsWith("[") && text.endsWith("]") ? text.substring(1, text.length() - 1) : text;
    }

    private Optional<String> writeIpv4AsHex(String text) {
        String[] octets = text.split("\\.", -1);
        if (octets.length != IPV4_OCTETS || !Arrays.stream(octets).allMatch(this::isOctet)) {
            return Optional.empty();
        }
        StringBuilder hex = new StringBuilder();
        for (String octet : octets) {
            hex.append("%02x".formatted(Integer.parseInt(octet)));
        }
        return Optional.of(hex.toString());
    }

    private boolean isOctet(String text) {
        return OCTET.matcher(text).matches() && Integer.parseInt(text) <= MAX_OCTET;
    }

    private Optional<String> writeIpv6AsHex(String text) {
        int zeroRun = text.indexOf(ZERO_RUN);
        if (zeroRun != text.lastIndexOf(ZERO_RUN)) {
            return Optional.empty();
        }
        Optional<List<String>> hextets = zeroRun < 0
                ? splitIntoHextets(text).filter(found -> found.size() == IPV6_HEXTETS)
                : expandZeroRun(text.substring(0, zeroRun), text.substring(zeroRun + ZERO_RUN.length()));
        return hextets.map(this::joinAsHex);
    }

    private Optional<List<String>> expandZeroRun(String before, String after) {
        Optional<List<String>> leading = before.isEmpty() ? Optional.of(List.of()) : splitIntoHextets(before);
        Optional<List<String>> trailing = after.isEmpty() ? Optional.of(List.of()) : splitIntoHextets(after);
        if (leading.isEmpty() || trailing.isEmpty()) {
            return Optional.empty();
        }
        int zeroes = IPV6_HEXTETS - leading.get().size() - trailing.get().size();
        if (zeroes < 1) {
            return Optional.empty();
        }
        List<String> expanded = new ArrayList<>(leading.get());
        expanded.addAll(Collections.nCopies(zeroes, "0"));
        expanded.addAll(trailing.get());
        return Optional.of(expanded);
    }

    private Optional<List<String>> splitIntoHextets(String text) {
        List<String> hextets = List.of(text.split(":", -1));
        return hextets.stream().allMatch(hextet -> HEXTET.matcher(hextet).matches())
                ? Optional.of(hextets)
                : Optional.empty();
    }

    private String joinAsHex(List<String> hextets) {
        StringBuilder hex = new StringBuilder();
        for (String hextet : hextets) {
            hex.append("%04x".formatted(Integer.parseInt(hextet, 16)));
        }
        return hex.toString();
    }
}
