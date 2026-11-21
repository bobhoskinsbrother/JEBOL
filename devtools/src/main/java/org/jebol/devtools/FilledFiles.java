package org.jebol.devtools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

public final class FilledFiles {

    private static final Set<String> TEXT_ENDINGS = Set.of(".html", ".htm", ".webmanifest", ".json", ".js", ".css",
            ".txt", ".xml", ".plist", ".desktop", ".wxs", ".wxi", ".properties", ".kts", ".gradle", "");

    private FilledFiles() {
    }

    public static boolean isText(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return TEXT_ENDINGS.contains(dot < 0 ? "" : name.substring(dot));
    }

    public static void fill(Path template, Path target, Placeholders placeholders) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            if (isText(template)) {
                String text = Files.readString(template, StandardCharsets.UTF_8);
                Files.writeString(target, placeholders.fill(text, Placeholders.escapingFor(template.getFileName().toString())));
            } else {
                Files.copy(template, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }
}
