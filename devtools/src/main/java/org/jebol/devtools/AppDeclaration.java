package org.jebol.devtools;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record AppDeclaration(
        Path directory,
        String title,
        String identity,
        String version,
        int build,
        Path icon,
        String summary,
        String publisher,
        String contact,
        Optional<String> website,
        Category category,
        Colour colour,
        Map<Service, String> uses,
        List<String> requires,
        String entry,
        Optional<Path> iconForeground) {
}
