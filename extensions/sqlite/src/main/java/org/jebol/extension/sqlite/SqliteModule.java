package org.jebol.extension.sqlite;

import org.jebol.application.Extension;
import org.jebol.application.HandleType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

final class SqliteModule {

    static final String NAME = "sqlite";

    static final String VERSION = "3.46.0.0";

    static final String HEADER = "REBOL [Title: {Rebol SQLite Extension} Name: sqlite Type: module Exports: []"
            + " Version: 3.46.0.0 Needs: 3.13.1 Author: Oldes Date: 4-Jun-2024/15:08:32+2:00 License: MIT"
            + " Url: https://github.com/Siskin-framework/Rebol-SQLite]";

    private static final String SCHEME = "sqlite-scheme.reb";

    private SqliteModule() {
    }

    static Extension runningOn(Supplier<SqliteEngine> engines) {
        Extension extension = new Extension(NAME, VERSION, new SqliteCommands(engines).commands())
                .withHeader(HEADER)
                .withScript(readScheme());
        for (HandleType type : SqliteCommands.handleTypes()) {
            extension = extension.withHandleType(type);
        }
        return extension;
    }

    private static String readScheme() {
        try (InputStream in = SqliteModule.class.getResourceAsStream(SCHEME)) {
            if (in == null) {
                throw new IllegalStateException("the extension's jar carries no " + SCHEME);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
