package org.jebol.domain.host;

import java.util.Optional;
import java.util.Set;

@FunctionalInterface
public interface HostNames {

    Set<Integer> FORBIDDEN_IN_A_DOMAIN = Set.of(
            0x20, (int) '#', (int) '%', (int) '/', (int) ':', (int) '<', (int) '>', (int) '?', (int) '@',
            (int) '[', (int) '\\', (int) ']', (int) '^', (int) '|', 0x7F);

    int FIRST_PRINTABLE_ASCII = 0x20;

    Optional<String> asciiHostName(String hostName);

    static Optional<String> keepUsableAsHostName(Optional<String> ascii) {
        return ascii.filter(converted -> !converted.isEmpty()
                && converted.codePoints().noneMatch(HostNames::isForbiddenInDomain));
    }

    private static boolean isForbiddenInDomain(int codePoint) {
        return codePoint < FIRST_PRINTABLE_ASCII || FORBIDDEN_IN_A_DOMAIN.contains(codePoint);
    }
}
