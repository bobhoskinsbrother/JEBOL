package org.jebol.extension.sqlite;

import org.jebol.application.Extension;
import org.jebol.application.ExtensionProvider;

import java.util.List;

public final class SqliteOnTheJvm implements ExtensionProvider {

    @Override
    public Extension provide() {
        return new Extension(SqliteModule.NAME, SqliteModule.VERSION, List.of()).withHeader(SqliteModule.HEADER);
    }
}
