package org.jebol.application;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public final class TextFiles {

    private TextFiles() {
    }

    public static String read(Path file) throws IOException {
        return read(file, StandardCharsets.UTF_8);
    }

    public static String read(Path file, Charset encoding) throws IOException {
        return new String(Files.readAllBytes(file), encoding);
    }

    public static Path write(Path file, CharSequence text, OpenOption... options) throws IOException {
        return write(file, text, StandardCharsets.UTF_8, options);
    }

    public static Path write(Path file, CharSequence text, Charset encoding, OpenOption... options)
            throws IOException {
        return Files.write(file, text.toString().getBytes(encoding), options);
    }
}
