package org.jebol.adapter.cli;

import java.util.Optional;
import java.util.regex.Pattern;

final class JavaVersionCheck {

    private static final String LEGACY_FIRST_PART = "1";
    private static final Pattern WHOLE_NUMBER = Pattern.compile("[0-9]+");

    private final int oldestJava;

    JavaVersionCheck(int oldestJava) {
        this.oldestJava = oldestJava;
    }

    Optional<String> refusalFor(String specificationVersion) {
        Optional<Integer> running = readFeatureVersion(specificationVersion);
        if (!running.isPresent() || running.get() >= oldestJava) {
            return Optional.empty();
        }
        return Optional.of("JEBOL needs Java " + oldestJava + " or later; this is Java " + running.get() + ".");
    }

    private Optional<Integer> readFeatureVersion(String specificationVersion) {
        if (specificationVersion == null) {
            return Optional.empty();
        }
        String[] parts = specificationVersion.split("\\.", -1);
        String feature = parts[0].equals(LEGACY_FIRST_PART) && parts.length > 1 ? parts[1] : parts[0];
        if (!WHOLE_NUMBER.matcher(feature).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(feature));
        } catch (NumberFormatException tooLarge) {
            return Optional.empty();
        }
    }
}
