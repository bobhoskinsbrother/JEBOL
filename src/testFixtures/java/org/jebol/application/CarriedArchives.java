package org.jebol.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HexFormat;

public final class CarriedArchives {

    private CarriedArchives() {
    }

    public static byte[] bytes(String name) {
        try {
            return Files.readAllBytes(CarriedFixtures.file("archives/" + name));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    public static String asBinary(String name) {
        return "#{" + HexFormat.of().withUpperCase().formatHex(bytes(name)) + "}";
    }
}
