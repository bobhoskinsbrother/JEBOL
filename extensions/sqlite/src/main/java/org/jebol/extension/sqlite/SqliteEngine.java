package org.jebol.extension.sqlite;

public interface SqliteEngine {

    int OK = 0;
    int ERROR = 1;
    int BUSY = 5;
    int MISUSE = 21;
    int ROW = 100;
    int DONE = 101;

    int INTEGER = 1;
    int FLOAT = 2;
    int TEXT = 3;
    int BLOB = 4;
    int NULL = 5;

    record Opened(int resultCode, long database) {
    }

    record Prepared(int resultCode, long statement, int tailOffset) {
    }

    String libraryVersion();

    long memoryUsed();

    long memoryHighwater();

    int initialize();

    int shutdown();

    Opened openInMemory();

    int deserialize(long database, byte[] image);

    byte[] serialize(long database);

    int close(long database);

    Prepared prepare(long database, byte[] sql, int offset);

    int step(long statement);

    int reset(long statement);

    int finalizeStatement(long statement);

    int clearBindings(long statement);

    int bindParameterCount(long statement);

    int bindLong(long statement, int column, long value);

    int bindDouble(long statement, int column, double value);

    int bindText(long statement, int column, byte[] utf8);

    int bindNull(long statement, int column);

    int dataCount(long statement);

    int columnCount(long statement);

    int columnType(long statement, int column);

    long columnLong(long statement, int column);

    double columnDouble(long statement, int column);

    byte[] columnText(long statement, int column);

    byte[] columnBlob(long statement, int column);

    String columnName(long statement, int column);

    boolean isReadOnly(long statement);

    long changes(long database);

    long lastInsertRowid(long database);

    boolean isAutocommit(long database);

    String errorMessage(long database);

    String errorString(int resultCode);

    String expandedSql(long statement);
}
