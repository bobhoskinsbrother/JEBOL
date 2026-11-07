package org.jebol.extension.sqlite;

final class SqliteFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SqliteFailure(String message) {
        super(message, null, false, false);
    }
}
