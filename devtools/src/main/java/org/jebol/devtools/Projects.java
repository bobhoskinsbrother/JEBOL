package org.jebol.devtools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Projects {

    public static final String OVERLAY_FOLDER = "files";
    private static final String TEMPLATES = "templates/";

    private Projects() {
    }

    public static Path folderFor(Path appDirectory, Platform platform) {
        return appDirectory.resolve(platform.folder());
    }

    public static boolean generate(Path appDirectory, Platform platform) {
        Path project = folderFor(appDirectory, platform);
        if (Files.exists(project)) {
            return false;
        }
        Carried.copyFolder(TEMPLATES + platform.folder(), project);
        try {
            Files.createDirectories(project.resolve(OVERLAY_FOLDER));
        } catch (IOException unmade) {
            throw new UncheckedIOException(unmade);
        }
        return true;
    }
}
