package org.jebol.android.unit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class IcuDiffersFromUts46 {

    private static final String LISTED = "/icu-differs-from-uts46.txt";

    static final List<String> CASES = readListed();

    private IcuDiffersFromUts46() {
    }

    private static List<String> readListed() {
        try (InputStream listed = IcuDiffersFromUts46.class.getResourceAsStream(LISTED)) {
            if (listed == null) {
                throw new IllegalStateException(LISTED + " is not in the test APK");
            }
            return new String(listed.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
