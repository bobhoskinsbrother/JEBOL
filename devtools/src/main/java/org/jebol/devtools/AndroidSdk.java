package org.jebol.devtools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class AndroidSdk {

    private AndroidSdk() {
    }

    public static Optional<Path> find(Map<String, String> environment) {
        String home = System.getProperty("user.home", ".");
        return Stream.of(environment.get("ANDROID_HOME"), environment.get("ANDROID_SDK_ROOT"),
                        home + "/Android/Sdk", home + "/Library/Android/sdk")
                .filter(candidate -> candidate != null && !candidate.isEmpty())
                .map(Path::of)
                .filter(candidate -> Files.isDirectory(candidate.resolve("platforms")))
                .findFirst();
    }
}
