package org.jebol.application;

import org.jebol.domain.host.FileInformation;

import java.util.Optional;

public interface CommandFiles {

    Optional<byte[]> read(String path);

    boolean write(String path, byte[] contents);

    Optional<FileInformation> describe(String path);

    String hostPath(String path);

    String locationFor(String path);
}
