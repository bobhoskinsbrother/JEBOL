package org.jebol.extension.sqlite;

import org.jebol.application.Extension;
import org.jebol.application.ExtensionProvider;

public final class SqliteOnAndroid implements ExtensionProvider {

    @Override
    public Extension provide() {
        return SqliteModule.runningOn(AndroidSqliteEngine::new);
    }
}
