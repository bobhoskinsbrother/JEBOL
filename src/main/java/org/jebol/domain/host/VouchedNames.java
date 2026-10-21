package org.jebol.domain.host;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record VouchedNames(Set<String> dnsNames, Set<String> ipAddresses) {

    private static final String WILDCARD_PREFIX = "*.";
    private static final char LABEL_SEPARATOR = '.';

    public VouchedNames {
        dnsNames = Set.copyOf(dnsNames);
        ipAddresses = Set.copyOf(ipAddresses);
    }

    public boolean cover(String hostName) {
        Optional<String> address = new IpAddressText(hostName).writeCanonicalHex();
        if (address.isPresent()) {
            return vouchesForAddress(address.get());
        }
        String host = normalise(hostName);
        return isWellFormed(host) && dnsNames.stream().anyMatch(name -> matches(normalise(name), host));
    }

    private boolean vouchesForAddress(String canonicalHex) {
        return ipAddresses.stream()
                .map(vouched -> new IpAddressText(vouched).writeCanonicalHex())
                .anyMatch(vouched -> vouched.equals(Optional.of(canonicalHex)));
    }

    private boolean matches(String pattern, String host) {
        if (!pattern.startsWith(WILDCARD_PREFIX)) {
            return pattern.equals(host);
        }
        String suffix = pattern.substring(WILDCARD_PREFIX.length());
        return isWellFormed(suffix)
                && hasTwoOrMoreLabels(suffix)
                && findParentDomain(host).equals(Optional.of(suffix));
    }

    private Optional<String> findParentDomain(String host) {
        int firstSeparator = host.indexOf(LABEL_SEPARATOR);
        return firstSeparator > 0 ? Optional.of(host.substring(firstSeparator + 1)) : Optional.empty();
    }

    private boolean hasTwoOrMoreLabels(String name) {
        return name.indexOf(LABEL_SEPARATOR) > 0;
    }

    private boolean isWellFormed(String name) {
        return !name.isEmpty()
                && name.chars().noneMatch(character -> character == '*' || Character.isWhitespace(character))
                && name.charAt(0) != LABEL_SEPARATOR
                && name.charAt(name.length() - 1) != LABEL_SEPARATOR
                && !name.contains("..");
    }

    private String normalise(String name) {
        String lowered = name.toLowerCase(Locale.ROOT);
        return lowered.endsWith(".") ? lowered.substring(0, lowered.length() - 1) : lowered;
    }
}
