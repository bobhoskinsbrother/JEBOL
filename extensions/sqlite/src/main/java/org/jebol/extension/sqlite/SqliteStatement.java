package org.jebol.extension.sqlite;

final class SqliteStatement {

    private final SqliteDatabase database;

    private long pointer;

    private int lastResultCode = SqliteEngine.ROW;

    private boolean running;

    private long startedAt;

    SqliteStatement(SqliteDatabase database, long pointer) {
        this.database = database;
        this.pointer = pointer;
    }

    SqliteDatabase database() {
        return database;
    }

    long pointer() {
        return pointer;
    }

    boolean isFinalized() {
        return pointer == 0;
    }

    int lastResultCode() {
        return lastResultCode;
    }

    void rememberResultCode(int resultCode) {
        lastResultCode = resultCode;
    }

    boolean startRun(long now) {
        if (running) {
            return false;
        }
        running = true;
        startedAt = now;
        return true;
    }

    long endRun(long now) {
        running = false;
        return now - startedAt;
    }

    boolean isRunning() {
        return running;
    }

    void forgetPointer() {
        pointer = 0;
    }
}
