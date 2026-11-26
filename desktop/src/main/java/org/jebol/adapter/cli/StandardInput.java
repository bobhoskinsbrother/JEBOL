package org.jebol.adapter.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class StandardInput {

    static final Path ITSELF = Path.of("/dev/fd/0");
    static final Path NOTHING = Path.of("/dev/null");

    private static final int FILE_TYPE_BITS = 0170000;
    private static final int CHARACTER_DEVICE = 0020000;
    private static final String MODE = "unix:mode";
    private static final String DEVICE_NUMBER = "unix:rdev";

    private StandardInput() {
    }

    static boolean canBeATerminal(Path input, Path nothing) {
        try {
            int mode = (Integer) Files.getAttribute(input, MODE);
            if ((mode & FILE_TYPE_BITS) != CHARACTER_DEVICE) {
                return false;
            }
            return !Objects.equals(Files.getAttribute(input, DEVICE_NUMBER), Files.getAttribute(nothing, DEVICE_NUMBER));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException unreadable) {
            return true;
        }
    }
}
