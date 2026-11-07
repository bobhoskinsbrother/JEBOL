package org.jebol.extension.sqlite;

import org.jebol.application.CommandFiles;
import org.jebol.domain.host.FileInformation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class DatabaseFile {

    private static final Map<String, AtomicLong> GENERATIONS = new ConcurrentHashMap<>();

    private final String location;

    private final AtomicLong generation;

    private long generationSeen;

    private Optional<Long> sizeSeen = Optional.empty();

    private Optional<Instant> modifiedSeen = Optional.empty();

    private DatabaseFile(String location, String hostPath) {
        this.location = location;
        this.generation = GENERATIONS.computeIfAbsent(hostPath, path -> new AtomicLong());
    }

    static DatabaseFile at(CommandFiles files, String path) {
        return new DatabaseFile(files.locationFor(path), files.hostPath(path));
    }

    String location() {
        return location;
    }

    Optional<FileInformation> describe(CommandFiles files) {
        return files.describe(location);
    }

    boolean hasChangedSinceSeen(CommandFiles files) {
        if (generation.get() != generationSeen) {
            return true;
        }
        Optional<FileInformation> now = files.describe(location);
        return !Objects.equals(now.flatMap(FileInformation::size), sizeSeen)
                || !Objects.equals(now.flatMap(FileInformation::modified), modifiedSeen);
    }

    Optional<byte[]> read(CommandFiles files) {
        long before = generation.get();
        Optional<byte[]> contents = files.read(location);
        contents.ifPresent(read -> remember(files, before));
        return contents;
    }

    boolean write(CommandFiles files, byte[] contents) {
        boolean written = files.write(location, contents);
        if (written) {
            remember(files, generation.incrementAndGet());
        }
        return written;
    }

    private void remember(CommandFiles files, long generationNow) {
        Optional<FileInformation> now = files.describe(location);
        generationSeen = generationNow;
        sizeSeen = now.flatMap(FileInformation::size);
        modifiedSeen = now.flatMap(FileInformation::modified);
    }
}
