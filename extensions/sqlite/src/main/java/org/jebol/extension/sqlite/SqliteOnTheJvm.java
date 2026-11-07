package org.jebol.extension.sqlite;

import org.jebol.application.Extension;
import org.jebol.application.ExtensionProvider;

public final class SqliteOnTheJvm implements ExtensionProvider {

    @Override
    public Extension provide() {
        return SqliteModule.runningOn(Sqlite4jEngine::new);
    }
}
