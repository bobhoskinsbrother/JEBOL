package org.jebol.adapter.cli;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.LayeredFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

record PackagedApp(String identity, Path carried) {

    static final String PACKAGED_SWITCH = "--packaged";
    static final String CARRIED_SWITCH = "--carried";

    static Optional<PackagedApp> readFrom(String[] arguments) {
        Optional<String> identity = trailingArgument(PACKAGED_SWITCH, arguments);
        Optional<String> carried = trailingArgument(CARRIED_SWITCH, arguments);
        if (identity.isEmpty() && carried.isEmpty()) {
            return Optional.empty();
        }
        if (identity.isEmpty() || carried.isEmpty()) {
            throw new IllegalArgumentException("a packaged app is started with both " + PACKAGED_SWITCH
                    + " <identity> and " + CARRIED_SWITCH + " <folder>");
        }
        return Optional.of(new PackagedApp(identity.get(), Path.of(carried.get()).toAbsolutePath().normalize()));
    }

    private static Optional<String> trailingArgument(String switchName, String[] arguments) {
        for (int at = 0; at + 1 < arguments.length; at++) {
            if (arguments[at].equals(switchName)) {
                return Optional.of(arguments[at + 1]);
            }
        }
        return Optional.empty();
    }

    static boolean isOwnSwitch(String argument) {
        return argument.equals(PACKAGED_SWITCH) || argument.equals(CARRIED_SWITCH);
    }

    Path store() {
        Path store = storeFolder(identity, System.getProperty("os.name", ""), System.getenv(),
                System.getProperty("user.home", "."));
        try {
            return Files.createDirectories(store);
        } catch (IOException unmade) {
            throw new UncheckedIOException(unmade);
        }
    }

    static Path storeFolder(String identity, String systemName, Map<String, String> environment, String userHome) {
        String system = systemName.toLowerCase(Locale.ROOT);
        String home = Optional.ofNullable(environment.get("HOME")).filter(value -> !value.isEmpty()).orElse(userHome);
        if (system.startsWith("windows")) {
            return Path.of(Optional.ofNullable(environment.get("APPDATA")).orElse(home + "/AppData/Roaming"), identity);
        }
        if (system.startsWith("mac")) {
            return Path.of(home, "Library", "Application Support", identity);
        }
        return Optional.ofNullable(environment.get("XDG_DATA_HOME"))
                .filter(PackagedApp::isAbsoluteXdgPath)
                .map(value -> Path.of(value, identity))
                .orElseGet(() -> Path.of(home, ".local", "share", identity));
    }

    private static boolean isAbsoluteXdgPath(String value) {
        return value.startsWith("/");
    }

    LayeredFiles filesOver(FileSystemPort store) {
        return new LayeredFiles(store, FileSystemPort.rootFileSystem(carried));
    }
}
