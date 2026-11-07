package org.jebol.extension.sqlite;

import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.typedarrays.Int8Array;

import java.nio.charset.StandardCharsets;

final class BrowserSqliteEngine implements SqliteEngine {

    private static final int POINTER_BYTES = 4;
    private static final int LENGTH_BYTES = 8;
    private static final int OPEN_READ_WRITE_CREATE = 0x02 | 0x04;
    private static final int OPEN_MEMORY = 0x80;
    private static final int TRANSIENT = -1;
    private static final int FREE_ON_CLOSE_AND_RESIZEABLE = 1 | 2;
    private static final String MAIN = "main";
    private static final String IN_MEMORY = ":memory:";

    private static boolean started;

    BrowserSqliteEngine() {
        if (!started) {
            jebolSqliteStart().await();
            started = true;
        }
    }

    private static int callForInt(String name, double... arguments) {
        double[] six = new double[6];
        System.arraycopy(arguments, 0, six, 0, arguments.length);
        return (int) jebolSqliteCall(name, six[0], six[1], six[2], six[3], six[4], six[5]);
    }

    private static int allocate(int bytes) {
        return callForInt("sqlite3_malloc", Math.max(bytes, 1));
    }

    private static void free(int pointer) {
        callForInt("sqlite3_free", pointer);
    }

    private static int writeCString(byte[] utf8) {
        Int8Array array = new Int8Array(utf8.length);
        for (int at = 0; at < utf8.length; at++) {
            array.set(at, utf8[at]);
        }
        return jebolSqliteWrite(array);
    }

    private static byte[] readBytes(int pointer, int length) {
        Int8Array read = jebolSqliteRead(pointer, length);
        byte[] bytes = new byte[read.getLength()];
        for (int at = 0; at < bytes.length; at++) {
            bytes[at] = read.get(at);
        }
        return bytes;
    }

    @Override
    public String libraryVersion() {
        return jebolSqliteReadText(jebolSqliteCallForPointer("sqlite3_libversion", 0, 0));
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
        return callForInt("sqlite3_initialize");
    }

    @Override
    public int shutdown() {
        return callForInt("sqlite3_shutdown");
    }

    @Override
    public Opened openInMemory() {
        int answer = allocate(POINTER_BYTES);
        int name = writeCString(IN_MEMORY.getBytes(StandardCharsets.UTF_8));
        try {
            int resultCode = callForInt("sqlite3_open_v2", name, answer, OPEN_READ_WRITE_CREATE | OPEN_MEMORY, 0);
            return new Opened(resultCode, jebolSqliteReadInt(answer));
        } finally {
            free(name);
            free(answer);
        }
    }

    @Override
    public int deserialize(long database, byte[] image) {
        int schema = writeCString(MAIN.getBytes(StandardCharsets.UTF_8));
        try {
            int copy = writeCString(image);
            return jebolSqliteDeserialize(database, schema, copy, image.length, FREE_ON_CLOSE_AND_RESIZEABLE);
        } finally {
            free(schema);
        }
    }

    @Override
    public byte[] serialize(long database) {
        int schema = writeCString(MAIN.getBytes(StandardCharsets.UTF_8));
        int size = allocate(LENGTH_BYTES);
        try {
            int image = callForInt("sqlite3_serialize", database, schema, size, 0);
            if (image == 0) {
                return new byte[0];
            }
            byte[] serialized = readBytes(image, (int) jebolSqliteReadLong(size));
            free(image);
            return serialized;
        } finally {
            free(size);
            free(schema);
        }
    }

    @Override
    public int close(long database) {
        return callForInt("sqlite3_close_v2", database);
    }

    @Override
    public Prepared prepare(long database, byte[] sql, int offset) {
        int text = writeCString(sql);
        int statement = allocate(POINTER_BYTES);
        int tail = allocate(POINTER_BYTES);
        try {
            int resultCode = callForInt("sqlite3_prepare_v2", database, text + offset, sql.length - offset,
                    statement, tail);
            int tailPointer = jebolSqliteReadInt(tail);
            return new Prepared(resultCode, jebolSqliteReadInt(statement),
                    tailPointer == 0 ? sql.length : tailPointer - text);
        } finally {
            free(tail);
            free(statement);
            free(text);
        }
    }

    @Override
    public int step(long statement) {
        return callForInt("sqlite3_step", statement);
    }

    @Override
    public int reset(long statement) {
        return callForInt("sqlite3_reset", statement);
    }

    @Override
    public int finalizeStatement(long statement) {
        return callForInt("sqlite3_finalize", statement);
    }

    @Override
    public int clearBindings(long statement) {
        return callForInt("sqlite3_clear_bindings", statement);
    }

    @Override
    public int bindParameterCount(long statement) {
        return callForInt("sqlite3_bind_parameter_count", statement);
    }

    @Override
    public int bindLong(long statement, int column, long value) {
        return jebolSqliteCallWithBigInt("sqlite3_bind_int64", statement, column, Long.toString(value));
    }

    @Override
    public int bindDouble(long statement, int column, double value) {
        return callForInt("sqlite3_bind_double", statement, column, value);
    }

    @Override
    public int bindText(long statement, int column, byte[] utf8) {
        int text = writeCString(utf8);
        try {
            return callForInt("sqlite3_bind_text", statement, column, text, utf8.length, TRANSIENT);
        } finally {
            free(text);
        }
    }

    @Override
    public int bindNull(long statement, int column) {
        return callForInt("sqlite3_bind_null", statement, column);
    }

    @Override
    public int dataCount(long statement) {
        return callForInt("sqlite3_data_count", statement);
    }

    @Override
    public int columnCount(long statement) {
        return callForInt("sqlite3_column_count", statement);
    }

    @Override
    public int columnType(long statement, int column) {
        return callForInt("sqlite3_column_type", statement, column);
    }

    @Override
    public long columnLong(long statement, int column) {
        return Long.parseLong(jebolSqliteCallForBigInt("sqlite3_column_int64", statement, column));
    }

    @Override
    public double columnDouble(long statement, int column) {
        double[] six = {statement, column, 0, 0, 0, 0};
        return jebolSqliteCall("sqlite3_column_double", six[0], six[1], six[2], six[3], six[4], six[5]);
    }

    @Override
    public byte[] columnText(long statement, int column) {
        int text = jebolSqliteCallForPointer("sqlite3_column_text", statement, column);
        int bytes = callForInt("sqlite3_column_bytes", statement, column);
        return text == 0 ? new byte[0] : readBytes(text, bytes);
    }

    @Override
    public byte[] columnBlob(long statement, int column) {
        int blob = jebolSqliteCallForPointer("sqlite3_column_blob", statement, column);
        int bytes = callForInt("sqlite3_column_bytes", statement, column);
        return blob == 0 ? new byte[0] : readBytes(blob, bytes);
    }

    @Override
    public String columnName(long statement, int column) {
        return jebolSqliteReadText(jebolSqliteCallForPointer("sqlite3_column_name", statement, column));
    }

    @Override
    public boolean isReadOnly(long statement) {
        return callForInt("sqlite3_stmt_readonly", statement) != 0;
    }

    @Override
    public long changes(long database) {
        return callForInt("sqlite3_changes", database);
    }

    @Override
    public long lastInsertRowid(long database) {
        return Long.parseLong(jebolSqliteCallForBigInt("sqlite3_last_insert_rowid", database, 0));
    }

    @Override
    public boolean isAutocommit(long database) {
        return callForInt("sqlite3_get_autocommit", database) != 0;
    }

    @Override
    public String errorMessage(long database) {
        return jebolSqliteReadText(jebolSqliteCallForPointer("sqlite3_errmsg", database, 0));
    }

    @Override
    public String errorString(int resultCode) {
        return jebolSqliteReadText(jebolSqliteCallForPointer("sqlite3_errstr", resultCode, 0));
    }

    @Override
    public String expandedSql(long statement) {
        int expanded = jebolSqliteCallForPointer("sqlite3_expanded_sql", statement, 0);
        String sql = jebolSqliteReadText(expanded);
        if (expanded != 0) {
            free(expanded);
        }
        return sql;
    }

    @JSBody(script = "if (typeof jebolSqliteStart === 'undefined') { importScripts('jebol-sqlite.js'); }"
            + " return jebolSqliteStart();")
    private static native JSPromise<Object> jebolSqliteStart();

    @JSBody(params = {"name", "a", "b", "c", "d", "e", "f"},
            script = "return jebolSqliteCall(name, a, b, c, d, e, f);")
    private static native double jebolSqliteCall(String name, double a, double b, double c, double d, double e,
                                                 double f);

    @JSBody(params = {"name", "a", "b", "big"}, script = "return jebolSqliteCallWithBigInt(name, a, b, big);")
    private static native int jebolSqliteCallWithBigInt(String name, double a, double b, String big);

    @JSBody(params = {"name", "a", "b"}, script = "return jebolSqliteCallForBigInt(name, a, b);")
    private static native String jebolSqliteCallForBigInt(String name, double a, double b);

    @JSBody(params = {"name", "a", "b"}, script = "return jebolSqliteCallForPointer(name, a, b);")
    private static native int jebolSqliteCallForPointer(String name, double a, double b);

    @JSBody(params = {"database", "schema", "image", "length", "flags"},
            script = "return jebolSqliteDeserialize(database, schema, image, length, flags);")
    private static native int jebolSqliteDeserialize(double database, int schema, int image, int length, int flags);

    @JSBody(params = "octets", script = "return jebolSqliteWrite(octets);")
    private static native int jebolSqliteWrite(Int8Array octets);

    @JSBody(params = {"pointer", "length"}, script = "return jebolSqliteRead(pointer, length);")
    private static native Int8Array jebolSqliteRead(int pointer, int length);

    @JSBody(params = "pointer", script = "return jebolSqliteReadInt(pointer);")
    private static native int jebolSqliteReadInt(int pointer);

    @JSBody(params = "pointer", script = "return jebolSqliteReadLong(pointer);")
    private static native double jebolSqliteReadLong(int pointer);

    @JSBody(params = "pointer", script = "return jebolSqliteReadText(pointer);")
    private static native String jebolSqliteReadText(int pointer);
}
