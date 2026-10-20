package org.jebol.application;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.read.LibraryFile;
import org.jebol.domain.read.TranscodeResult;

import java.nio.charset.StandardCharsets;

record RebolFile(String path, FilePort filePort) {

    boolean exists() {
        return filePort.exists(path);
    }

    byte[] readBytes() {
        try {
            return filePort.readBytes(path);
        } catch (FilePort.Denied missing) {
            throw new IllegalStateException(path + " is missing from the build", missing);
        }
    }

    String textSource() {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    TranscodeResult readTranscoded() {
        return LibrarySource.read(path, textSource());
    }

    boolean isLibraryFile() {
        return exists() && readTranscoded().values().isPresent();
    }

    LibraryFile readLibraryFile() {
        return LibraryFile.readFrom(readTranscoded().values().orElseThrow(() ->
                new IllegalStateException(path + " does not read as REBOL")));
    }
}
