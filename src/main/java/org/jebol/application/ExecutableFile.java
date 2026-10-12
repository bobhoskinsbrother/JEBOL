package org.jebol.application;

import java.io.File;

final class ExecutableFile {

    private ExecutableFile() {
    }

    static boolean markRunnable(File file) {
        return file.setExecutable(true);
    }
}
