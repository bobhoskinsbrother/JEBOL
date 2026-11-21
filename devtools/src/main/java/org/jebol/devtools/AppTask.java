package org.jebol.devtools;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Internal;

import java.nio.file.Path;
import java.util.stream.Collectors;

public abstract class AppTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getAppDirectory();

    protected Path appDirectory() {
        return getAppDirectory().get().getAsFile().toPath();
    }

    protected AppDeclaration declaration() {
        return switch (AppReb.read(appDirectory())) {
            case AppReb.Declared declared -> declared.declaration();
            case AppReb.Refused refused -> throw new GradleException(refused.faults().stream()
                    .map(fault -> "  " + fault.describe())
                    .collect(Collectors.joining("\n", "app.reb is not right, so nothing was made:\n", "")));
        };
    }

    protected void generate(Platform platform) {
        if (Projects.generate(appDirectory(), platform)) {
            getLogger().lifecycle("generated " + platform.folder() + "/, which is yours from now on");
        }
    }
}
