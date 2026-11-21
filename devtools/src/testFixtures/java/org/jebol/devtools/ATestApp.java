package org.jebol.devtools;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ATestApp {

    public static final String COMPLETE_APP_REB = """
            title:     "Notes"
            id:        com.example.notes
            version:   1.2.3
            build:     7
            icon:      %icon.png
            summary:   "Quick notes that sync nowhere"
            publisher: "Example Ltd"
            contact:   dev@example.com
            website:   https://example.com/notes
            category:  productivity
            colour:    0.120.215
            uses: [
                notifications "Notes reminds you of a note at the time you set"
                camera        "Notes attaches a photo to a note"
            ]
            requires: [sqlite]
            """;

    public static final String SMALLEST_APP_REB = """
            title:     "Notes"
            id:        com.example.notes
            version:   1.2.3
            build:     7
            icon:      %icon.png
            summary:   "Quick notes that sync nowhere"
            publisher: "Example Ltd"
            contact:   dev@example.com
            """;

    public static final String MAIN_SCRIPT = """
            REBOL [title: "Notes"]
            print "notes"
            """;

    private final Path directory;

    private ATestApp(Path directory) {
        this.directory = directory;
    }

    public static ATestApp in(Path directory) {
        return new ATestApp(directory);
    }

    public static ATestApp smallestIn(Path directory) {
        return in(directory).withAppReb(SMALLEST_APP_REB).withIcon("icon.png", 1024, 1024).withFile("main.reb", MAIN_SCRIPT);
    }

    public static ATestApp completeIn(Path directory) {
        return in(directory).withAppReb(COMPLETE_APP_REB).withIcon("icon.png", 1024, 1024).withFile("main.reb", MAIN_SCRIPT);
    }

    public Path directory() {
        return directory;
    }

    public ATestApp withAppReb(String source) {
        return withFile("app.reb", source);
    }

    public ATestApp withFile(String name, String contents) {
        return withBytes(name, contents.getBytes(StandardCharsets.UTF_8));
    }

    public ATestApp withBytes(String name, byte[] contents) {
        try {
            Path file = directory.resolve(name);
            Files.createDirectories(file.getParent());
            Files.write(file, contents);
            return this;
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }

    public ATestApp withIcon(String name, int width, int height) {
        try {
            Path file = directory.resolve(name);
            Files.createDirectories(file.getParent());
            ImageIO.write(drawIcon(width, height), "png", file.toFile());
            return this;
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }

    public static BufferedImage drawIcon(int width, int height) {
        BufferedImage icon = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D painting = icon.createGraphics();
        painting.setColor(new Color(0, 120, 215));
        painting.fillRect(0, 0, width, height);
        painting.setColor(Color.WHITE);
        painting.fillOval(width / 4, height / 4, width / 2, height / 2);
        painting.dispose();
        return icon;
    }
}
