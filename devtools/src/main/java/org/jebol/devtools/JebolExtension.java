package org.jebol.devtools;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

public abstract class JebolExtension {

    public abstract DirectoryProperty getAppDirectory();

    public abstract Property<Boolean> getRelease();
}
