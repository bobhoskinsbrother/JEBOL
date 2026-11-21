package org.jebol.extension.sqlite;

import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteProgram;
import android.database.sqlite.SQLiteStatement;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AndroidSqliteEngine implements SqliteEngine {

    private static final int NOT_A_DATABASE = 26;
    private static final int RANGE = 25;
    private static final String NOT_AN_ERROR = "not an error";
    private static final String NO_AUTOMATIC_VACUUM = "PRAGMA auto_vacuum = NONE";
    private static final Pattern RESULT_CODE = Pattern.compile("\\(code (\\d+)");
    private static final DatabaseErrorHandler KEEP_THE_FILE = database -> {
    };

    private final Map<Long, Database> databases = new HashMap<>();
    private final Map<Long, Statement> statements = new HashMap<>();
    private final AtomicLong handles = new AtomicLong();

    private static final class Database {
        private final File file;
        private SQLiteDatabase opened;
        private String error = NOT_AN_ERROR;

        Database(File file) {
            this.file = file;
        }
    }

    private static final class Statement {
        private final Database database;
        private final String sql;
        private final SqlText.Shape shape;
        private final Object[] bound;
        private final int columns;
        private Cursor cursor;
        private boolean onARow;
        private boolean ran;
        private boolean finished;
        private int failedWith = OK;

        Statement(Database database, String sql, SqlText.Shape shape, int columns) {
            this.database = database;
            this.sql = sql;
            this.shape = shape;
            this.bound = new Object[shape.parameterCount()];
            this.columns = columns;
        }
    }

    private long nextHandle() {
        return handles.incrementAndGet();
    }

    private static File scratchFile() {
        try {
            File file = File.createTempFile("jebol-sqlite", ".db", new File(System.getProperty("java.io.tmpdir", "/data/local/tmp")));
            if (!file.delete()) {
                throw new IOException("could not clear " + file);
            }
            return file;
        } catch (IOException unmade) {
            throw new UncheckedIOException(unmade);
        }
    }

    private static SQLiteDatabase open(File file) {
        boolean isNew = !file.exists() || file.length() == 0;
        SQLiteDatabase opened = SQLiteDatabase.openDatabase(file.getPath(), null,
                SQLiteDatabase.CREATE_IF_NECESSARY | SQLiteDatabase.OPEN_READWRITE
                        | SQLiteDatabase.NO_LOCALIZED_COLLATORS, KEEP_THE_FILE);
        opened.disableWriteAheadLogging();
        if (isNew) {
            opened.execSQL(NO_AUTOMATIC_VACUUM);
        }
        return opened;
    }

    private static int codeOf(SQLiteException failed) {
        Matcher found = RESULT_CODE.matcher(String.valueOf(failed.getMessage()));
        return found.find() ? Integer.parseInt(found.group(1)) & 0xff : ERROR;
    }

    private static String messageOf(SQLiteException failed) {
        String message = String.valueOf(failed.getMessage());
        int detail = message.indexOf(" (code ");
        return detail < 0 ? message : message.substring(0, detail);
    }

    private int fail(Database database, SQLiteException failed) {
        database.error = messageOf(failed);
        return codeOf(failed);
    }

    @Override
    public String libraryVersion() {
        SQLiteDatabase scratch = SQLiteDatabase.create(null);
        try (Cursor answer = scratch.rawQuery("SELECT sqlite_version()", null)) {
            return answer.moveToFirst() ? answer.getString(0) : "";
        } finally {
            scratch.close();
        }
    }

    @Override
    public long memoryUsed() {
        return 0;
    }

    @Override
    public long memoryHighwater() {
        return 0;
    }

    @Override
    public int initialize() {
        return OK;
    }

    @Override
    public int shutdown() {
        return OK;
    }

    @Override
    public Opened openInMemory() {
        Database database = new Database(scratchFile());
        try {
            database.opened = open(database.file);
        } catch (SQLiteException failed) {
            return new Opened(codeOf(failed), 0);
        }
        long handle = nextHandle();
        databases.put(handle, database);
        return new Opened(OK, handle);
    }

    @Override
    public int deserialize(long handle, byte[] image) {
        Database database = databases.get(handle);
        database.opened.close();
        deleteFiles(database.file);
        try {
            Files.write(database.file.toPath(), image);
            database.opened = open(database.file);
            database.error = NOT_AN_ERROR;
            return OK;
        } catch (IOException unwritten) {
            database.error = unwritten.getMessage();
            return ERROR;
        } catch (SQLiteException failed) {
            database.opened = null;
            database.error = messageOf(failed);
            return NOT_A_DATABASE;
        }
    }

    @Override
    public byte[] serialize(long handle) {
        Database database = databases.get(handle);
        try {
            return database.file.exists() ? Files.readAllBytes(database.file.toPath()) : new byte[0];
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Override
    public int close(long handle) {
        Database database = databases.remove(handle);
        if (database == null) {
            return MISUSE;
        }
        statements.values().removeIf(statement -> {
            if (statement.database == database && statement.cursor != null) {
                statement.cursor.close();
            }
            return statement.database == database;
        });
        if (database.opened != null) {
            database.opened.close();
        }
        deleteFiles(database.file);
        return OK;
    }

    private static void deleteFiles(File file) {
        for (String ending : new String[]{"", "-journal", "-wal", "-shm"}) {
            File each = new File(file.getPath() + ending);
            if (each.exists() && !each.delete()) {
                throw new UncheckedIOException(new IOException("could not delete " + each));
            }
        }
    }

    @Override
    public Prepared prepare(long handle, byte[] sql, int offset) {
        Database database = databases.get(handle);
        int end = SqlText.endOfStatement(sql, offset);
        String text = new String(sql, offset, end - offset, StandardCharsets.UTF_8);
        SqlText.Shape shape = SqlText.shape(text);
        if (shape.isEmpty()) {
            database.error = NOT_AN_ERROR;
            return new Prepared(OK, 0, end);
        }
        if (database.opened == null) {
            database.error = "file is not a database";
            return new Prepared(NOT_A_DATABASE, 0, end);
        }
        int columns;
        try (SQLiteStatement compiled = database.opened.compileStatement(text);
             Cursor unexecuted = query(database, text, new Object[shape.parameterCount()])) {
            columns = unexecuted.getColumnCount();
            database.error = NOT_AN_ERROR;
        } catch (SQLiteException failed) {
            return new Prepared(fail(database, failed), 0, end);
        }
        long statementHandle = nextHandle();
        statements.put(statementHandle, new Statement(database, text, shape, columns));
        return new Prepared(OK, statementHandle, end);
    }

    private static Cursor query(Database database, String sql, Object[] bound) {
        SQLiteDatabase.CursorFactory bindingTypes = (opened, driver, editTable, query) -> {
            bindEach(query, bound);
            return new SQLiteCursor(driver, editTable, query);
        };
        return database.opened.rawQueryWithFactory(bindingTypes, sql, null, null);
    }

    private static Cursor query(Statement statement) {
        return query(statement.database, statement.sql, statement.bound);
    }

    private static void bindEach(SQLiteProgram program, Object[] bound) {
        for (int at = 0; at < bound.length; at++) {
            Object value = bound[at];
            int index = at + 1;
            if (value == null) {
                program.bindNull(index);
            } else if (value instanceof Long whole) {
                program.bindLong(index, whole);
            } else if (value instanceof Double fraction) {
                program.bindDouble(index, fraction);
            } else {
                program.bindString(index, (String) value);
            }
        }
    }

    @Override
    public int step(long handle) {
        Statement statement = statements.get(handle);
        if (statement == null) {
            return MISUSE;
        }
        if (statement.finished) {
            startAgain(statement);
        }
        try {
            if (statement.columns == 0) {
                statement.finished = true;
                return runWithoutRows(statement);
            }
            if (statement.cursor == null) {
                statement.cursor = query(statement);
                statement.cursor.getCount();
            }
            statement.onARow = statement.cursor.moveToNext();
            statement.finished = !statement.onARow;
            statement.database.error = NOT_AN_ERROR;
            return statement.onARow ? ROW : DONE;
        } catch (SQLiteException failed) {
            statement.onARow = false;
            statement.finished = true;
            statement.failedWith = fail(statement.database, failed);
            return statement.failedWith;
        }
    }

    private static void startAgain(Statement statement) {
        if (statement.cursor != null) {
            statement.cursor.close();
            statement.cursor = null;
        }
        statement.onARow = false;
        statement.ran = false;
        statement.finished = false;
    }

    private static int runWithoutRows(Statement statement) {
        if (!statement.ran) {
            try (SQLiteStatement compiled = statement.database.opened.compileStatement(statement.sql)) {
                bindEach(compiled, statement.bound);
                compiled.execute();
            }
            statement.ran = true;
        }
        statement.database.error = NOT_AN_ERROR;
        return DONE;
    }

    @Override
    public int reset(long handle) {
        Statement statement = statements.get(handle);
        if (statement == null) {
            return MISUSE;
        }
        startAgain(statement);
        int failed = statement.failedWith;
        statement.failedWith = OK;
        return failed;
    }

    @Override
    public int finalizeStatement(long handle) {
        if (handle == 0) {
            return OK;
        }
        int reset = reset(handle);
        statements.remove(handle);
        return reset;
    }

    @Override
    public int clearBindings(long handle) {
        Statement statement = statements.get(handle);
        Arrays.fill(statement.bound, null);
        return OK;
    }

    @Override
    public int bindParameterCount(long handle) {
        return statements.get(handle).bound.length;
    }

    private int bind(long handle, int column, Object value) {
        Statement statement = statements.get(handle);
        if (column < 1 || column > statement.bound.length) {
            return RANGE;
        }
        statement.bound[column - 1] = value;
        return OK;
    }

    @Override
    public int bindLong(long handle, int column, long value) {
        return bind(handle, column, value);
    }

    @Override
    public int bindDouble(long handle, int column, double value) {
        return bind(handle, column, value);
    }

    @Override
    public int bindText(long handle, int column, byte[] utf8) {
        return bind(handle, column, new String(utf8, StandardCharsets.UTF_8));
    }

    @Override
    public int bindNull(long handle, int column) {
        return bind(handle, column, null);
    }

    @Override
    public int dataCount(long handle) {
        Statement statement = statements.get(handle);
        return statement.onARow ? statement.cursor.getColumnCount() : 0;
    }

    @Override
    public int columnCount(long handle) {
        return statements.get(handle).columns;
    }

    @Override
    public int columnType(long handle, int column) {
        Statement statement = statements.get(handle);
        if (!statement.onARow) {
            return NULL;
        }
        return switch (statement.cursor.getType(column)) {
            case Cursor.FIELD_TYPE_INTEGER -> INTEGER;
            case Cursor.FIELD_TYPE_FLOAT -> FLOAT;
            case Cursor.FIELD_TYPE_STRING -> TEXT;
            case Cursor.FIELD_TYPE_BLOB -> BLOB;
            default -> NULL;
        };
    }

    @Override
    public long columnLong(long handle, int column) {
        Statement statement = statements.get(handle);
        return statement.onARow ? statement.cursor.getLong(column) : 0;
    }

    @Override
    public double columnDouble(long handle, int column) {
        Statement statement = statements.get(handle);
        return statement.onARow ? statement.cursor.getDouble(column) : 0;
    }

    @Override
    public byte[] columnText(long handle, int column) {
        return columnBytes(statements.get(handle), column);
    }

    @Override
    public byte[] columnBlob(long handle, int column) {
        return columnBytes(statements.get(handle), column);
    }

    private static byte[] columnBytes(Statement statement, int column) {
        if (!statement.onARow) {
            return new byte[0];
        }
        Cursor row = statement.cursor;
        return switch (row.getType(column)) {
            case Cursor.FIELD_TYPE_NULL -> new byte[0];
            case Cursor.FIELD_TYPE_BLOB -> row.getBlob(column);
            case Cursor.FIELD_TYPE_INTEGER -> Long.toString(row.getLong(column)).getBytes(StandardCharsets.UTF_8);
            case Cursor.FIELD_TYPE_FLOAT -> SqlText.writeDouble(row.getDouble(column)).getBytes(StandardCharsets.UTF_8);
            default -> row.getString(column).getBytes(StandardCharsets.UTF_8);
        };
    }

    @Override
    public String columnName(long handle, int column) {
        Statement statement = statements.get(handle);
        if (statement.cursor != null) {
            return statement.cursor.getColumnName(column);
        }
        try (Cursor unexecuted = query(statement)) {
            return unexecuted.getColumnName(column);
        }
    }

    @Override
    public boolean isReadOnly(long handle) {
        return statements.get(handle).shape.isReadOnly();
    }

    private long askLong(long handle, String sql) {
        Database database = databases.get(handle);
        if (database == null || database.opened == null) {
            return 0;
        }
        try (Cursor answer = database.opened.rawQuery(sql, null)) {
            return answer.moveToFirst() ? answer.getLong(0) : 0;
        }
    }

    @Override
    public long changes(long handle) {
        return askLong(handle, "SELECT changes()");
    }

    @Override
    public long lastInsertRowid(long handle) {
        return askLong(handle, "SELECT last_insert_rowid()");
    }

    @Override
    public boolean isAutocommit(long handle) {
        Database database = databases.get(handle);
        return database == null || database.opened == null || !database.opened.inTransaction();
    }

    @Override
    public String errorMessage(long handle) {
        Database database = databases.get(handle);
        return database == null ? "out of memory" : database.error;
    }

    @Override
    public String errorString(int resultCode) {
        return ResultCodes.describe(resultCode);
    }

    @Override
    public String expandedSql(long handle) {
        Statement statement = statements.get(handle);
        return SqlText.expanded(statement.sql, statement.shape, statement.bound);
    }
}
