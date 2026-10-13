package org.jebol.application;

import org.jebol.domain.read.LibraryImage;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.Molder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class WritingTheLibraryImage {

    private WritingTheLibraryImage() {
    }

    public static void main(String[] argued) throws IOException {
        Path resources = Path.of(argued[0]);
        Path destination = Path.of(argued[1], "org", "jebol", "library.image");
        Map<String, AnyBlockValue> files = everyLibraryFileUnder(resources);
        Files.createDirectories(destination.getParent());
        Files.write(destination, LibraryImage.asBytes(files));
        System.out.println("wrote " + destination + " with " + files.size()
                + " files, " + Files.size(destination) + " bytes");
    }

    private static final Set<String> FILES_A_REAL_REBOL_ALSO_REFUSES =
            Set.of("/org/jebol/modules/unicode-utils.reb");

    public static Map<String, AnyBlockValue> everyLibraryFileUnder(Path resources)
            throws IOException {

        Path library = resources.resolve("org").resolve("jebol");
        Map<String, AnyBlockValue> files = new TreeMap<>();
        try (Stream<Path> walked = Files.walk(library)) {
            for (Path file : walked.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".reb"))
                    .sorted()
                    .toList()) {
                String name = "/" + resources.relativize(file).toString()
                        .replace('\\', '/');
                String source = Files.readString(file, StandardCharsets.UTF_8);
                TranscodeResult read = Transcoder.transcode(source);
                if (read.values().isEmpty()) {
                    String why = read.error().map(Molder::mold).orElse("no reason given");
                    if (!FILES_A_REAL_REBOL_ALSO_REFUSES.contains(name)) {
                        throw new IllegalStateException(name + " would not read: " + why);
                    }
                    System.out.println("skipping " + name
                            + ", which a real 3.22.5 also refuses: " + why);
                    continue;
                }
                files.put(name, read.values().orElseThrow());
            }
        }
        return files;
    }
}
