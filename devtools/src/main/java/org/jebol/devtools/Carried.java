package org.jebol.devtools;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class Carried {

    private static final String LISTING = "carried.txt";

    private Carried() {
    }

    public static List<String> listed() {
        try (InputStream listing = Carried.class.getResourceAsStream(LISTING)) {
            if (listing == null) {
                throw new IllegalStateException("jebol-devtools carries no " + LISTING + "; it was built without its runtime");
            }
            return new String(listing.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(line -> !line.isEmpty()).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    public static List<String> listedUnder(String folder) {
        String prefix = folder.endsWith("/") ? folder : folder + "/";
        return listed().stream().filter(name -> name.startsWith(prefix)).map(name -> name.substring(prefix.length())).toList();
    }

    public static boolean has(String folder) {
        return !listedUnder(folder).isEmpty();
    }

    public static void copyFolder(String folder, Path target) {
        String prefix = folder.endsWith("/") ? folder : folder + "/";
        for (String name : listedUnder(folder)) {
            copy(prefix + name, target.resolve(name));
        }
    }

    public static void copy(String carried, Path target) {
        try (InputStream in = Carried.class.getResourceAsStream(carried)) {
            if (in == null) {
                throw new IllegalStateException("jebol-devtools lists " + carried + " but does not carry it");
            }
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException uncopied) {
            throw new UncheckedIOException(uncopied);
        }
    }
}
