package org.jebol.extension.sqlite;

import org.jebol.application.CommandFiles;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

final class SqliteDatabase {

    private final SqliteEngine engine;

    private final long connection;

    private final Optional<DatabaseFile> file;

    private boolean closedByScript;

    private boolean connectionClosed;

    private long lastInsertCount;

    private int traceMask;

    private int liveStatements;

    private int runningStatements;

    private boolean changedSinceSaved;

    private static final byte[] HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);

    private static final int NOT_A_DATABASE = 26;

    private static final byte[] DEFAULT_PAGE_SIZE = "PRAGMA page_size = 4096".getBytes(StandardCharsets.US_ASCII);

    private static final int OPCODE_COLUMN = 1;

    private static final String OPENS_THE_FILE = "Transaction";

    private boolean notADatabase;

    SqliteDatabase(SqliteEngine engine, long connection, Optional<DatabaseFile> file) {
        this.engine = engine;
        this.connection = connection;
        this.file = file;
    }

    void load(byte[] contents) {
        if (contents.length == 0) {
            useSqlitesDefaultPageSize();
            return;
        }
        if (Arrays.equals(contents, 0, Math.min(contents.length, HEADER.length), HEADER, 0, HEADER.length)) {
            engine.deserialize(connection, contents);
        } else {
            notADatabase = true;
        }
    }

    private void useSqlitesDefaultPageSize() {
        SqliteEngine.Prepared pragma = engine.prepare(connection, DEFAULT_PAGE_SIZE, 0);
        if (pragma.resultCode() == SqliteEngine.OK && pragma.statement() != 0) {
            engine.step(pragma.statement());
            engine.finalizeStatement(pragma.statement());
        }
    }

    SqliteEngine.Prepared prepare(byte[] sql, int offset) {
        SqliteEngine.Prepared prepared = engine.prepare(connection, sql, offset);
        if (!notADatabase || prepared.resultCode() != SqliteEngine.OK || prepared.statement() == 0
                || !opensFile(sql, offset, prepared.tailOffset())) {
            return prepared;
        }
        engine.finalizeStatement(prepared.statement());
        return new SqliteEngine.Prepared(NOT_A_DATABASE, 0, prepared.tailOffset());
    }

    private boolean opensFile(byte[] sql, int offset, int tail) {
        byte[] explain = ("EXPLAIN " + new String(sql, offset, tail - offset, StandardCharsets.UTF_8))
                .getBytes(StandardCharsets.UTF_8);
        SqliteEngine.Prepared program = engine.prepare(connection, explain, 0);
        if (program.resultCode() != SqliteEngine.OK || program.statement() == 0) {
            return false;
        }
        boolean opens = false;
        while (!opens && engine.step(program.statement()) == SqliteEngine.ROW) {
            opens = OPENS_THE_FILE.equals(
                    new String(engine.columnText(program.statement(), OPCODE_COLUMN), StandardCharsets.UTF_8));
        }
        engine.finalizeStatement(program.statement());
        return opens;
    }

    String errorMessage() {
        return notADatabase && engine.errorMessage(connection).equals(engine.errorString(SqliteEngine.OK))
                ? engine.errorString(NOT_A_DATABASE)
                : engine.errorMessage(connection);
    }

    SqliteEngine engine() {
        return engine;
    }

    long connection() {
        return connection;
    }

    boolean isClosed() {
        return closedByScript;
    }

    void closeForScript() {
        closedByScript = true;
    }

    boolean closeConnectionWhenUnused() {
        if (connectionClosed || !closedByScript || liveStatements > 0) {
            return false;
        }
        connectionClosed = engine.close(connection) == SqliteEngine.OK;
        return connectionClosed;
    }

    long lastInsertCount() {
        return lastInsertCount;
    }

    void startCountingChanges() {
        lastInsertCount = 0;
    }

    void countChanges() {
        lastInsertCount += engine.changes(connection);
    }

    int traceMask() {
        return traceMask;
    }

    void traceWith(int mask) {
        traceMask = mask;
    }

    void holdStatement() {
        liveStatements++;
    }

    void releaseStatement() {
        liveStatements--;
    }

    void startRunningStatement() {
        runningStatements++;
    }

    void stopRunningStatement() {
        runningStatements--;
    }

    void markChanged() {
        changedSinceSaved = true;
    }

    void bringUpToDate(CommandFiles files) {
        if (file.isEmpty() || connectionClosed || runningStatements > 0 || !engine.isAutocommit(connection)) {
            return;
        }
        DatabaseFile backing = file.get();
        if (backing.hasChangedSinceSeen(files)) {
            backing.read(files).ifPresent(this::load);
        }
    }

    void saveIfChanged(CommandFiles files) {
        if (file.isEmpty() || connectionClosed || !changedSinceSaved || !engine.isAutocommit(connection)) {
            return;
        }
        file.get().write(files, engine.serialize(connection));
        changedSinceSaved = false;
    }
}
