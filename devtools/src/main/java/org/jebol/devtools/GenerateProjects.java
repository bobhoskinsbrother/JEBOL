package org.jebol.devtools;

import org.gradle.api.tasks.TaskAction;

public abstract class GenerateProjects extends AppTask {

    @TaskAction
    public void generateEach() {
        declaration();
        for (Platform platform : Platform.values()) {
            generate(platform);
        }
    }
}
