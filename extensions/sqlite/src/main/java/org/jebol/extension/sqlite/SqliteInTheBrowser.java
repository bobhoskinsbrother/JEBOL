package org.jebol.extension.sqlite;

import org.jebol.application.Extension;
import org.jebol.application.WebExtensionProvider;

public final class SqliteInTheBrowser implements WebExtensionProvider {

    @Override
    public Extension provide() {
        return SqliteModule.runningOn(BrowserSqliteEngine::new);
    }
}
