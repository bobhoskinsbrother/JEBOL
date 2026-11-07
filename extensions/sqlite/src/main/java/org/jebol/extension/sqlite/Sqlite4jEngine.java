package org.jebol.extension.sqlite;

import io.roastedroot.sqlite4j.SQLiteModule;
import io.roastedroot.sqlite4j.core.wasm.DummyWasmDBImports;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.ExportFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class Sqlite4jEngine implements SqliteEngine {

    private static final int POINTER_BYTES = 4;
    private static final int LENGTH_BYTES = 8;
    private static final int OPEN_READ_WRITE_CREATE = 0x02 | 0x04;
    private static final int OPEN_MEMORY = 0x80;
    private static final int TRANSIENT = -1;
    private static final int FREE_ON_CLOSE_AND_RESIZEABLE = 1 | 2;
    private static final String MAIN = "main";
    private static final String IN_MEMORY = ":memory:";

    private final Instance instance;

    private final Memory memory;

    private final Map<String, ExportFunction> functions = new HashMap<>();

    Sqlite4jEngine() {
        WasiPreview1 wasi = WasiPreview1.builder().withOptions(WasiOptions.builder().build()).build();
        this.instance = Instance.builder(SQLiteModule.load())
                .withMachineFactory(SQLiteModule::create)
                .withMemoryFactory(ByteArrayMemory::new)
                .withImportValues(ImportValues.builder()
                        .addFunction(wasi.toHostFunctions())
                        .addFunction(new DummyWasmDBImports().toHostFunctions())
                        .build())
                .build();
        this.memory = instance.memory();
    }

    private long call(String name, long... arguments) {
        long[] answer = functions.computeIfAbsent(name, found -> instance.exports().function(found)).apply(arguments);
        return answer == null || answer.length == 0 ? 0 : answer[0];
    }

    private int callForInt(String name, long... arguments) {
        return (int) call(name, arguments);
    }

    private int allocate(int bytes) {
        return callForInt("malloc", Math.max(bytes, 1));
    }

    private void free(int pointer) {
        call("free", pointer);
    }

    private int writeCString(byte[] utf8) {
        int pointer = allocate(utf8.length + 1);
        memory.write(pointer, utf8);
        memory.writeByte(pointer + utf8.length, (byte) 0);
        return pointer;
    }

    private String readCString(int pointer) {
        if (pointer == 0) {
            return null;
        }
        int end = pointer;
        while (memory.read(end) != 0) {
            end++;
        }
        return new String(memory.readBytes(pointer, end - pointer), StandardCharsets.UTF_8);
    }

    @Override
    public String libraryVersion() {
        return readCString(callForInt("sqlite3_libversion"));
    }

    @Override
    public long memoryUsed() {
        return call("sqlite3_memory_used");
    }

    @Override
    public long memoryHighwater() {
        return call("sqlite3_memory_highwater", 0);
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
            return new Opened(resultCode, Integer.toUnsignedLong(memory.readInt(answer)));
        } finally {
            free(name);
            free(answer);
        }
    }

    @Override
    public int deserialize(long database, byte[] image) {
        int schema = writeCString(MAIN.getBytes(StandardCharsets.UTF_8));
        try {
            int copy = callForInt("sqlite3_malloc64", Math.max(image.length, 1));
            memory.write(copy, image);
            return callForInt("sqlite3_deserialize", database, schema, copy, image.length, image.length,
                    FREE_ON_CLOSE_AND_RESIZEABLE);
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
            byte[] serialized = memory.readBytes(image, (int) memory.readLong(size));
            call("sqlite3_free", image);
            return serialized;
        } finally {
            free(size);
            free(schema);
        }
    }

    @Override
    public int close(long database) {
        return callForInt("sqlite3_close", database);
    }

    @Override
    public Prepared prepare(long database, byte[] sql, int offset) {
        int text = writeCString(sql);
        int statement = allocate(POINTER_BYTES);
        int tail = allocate(POINTER_BYTES);
        try {
            int resultCode = callForInt("sqlite3_prepare_v2", database, text + offset, sql.length - offset,
                    statement, tail);
            int tailPointer = memory.readInt(tail);
            return new Prepared(resultCode, Integer.toUnsignedLong(memory.readInt(statement)),
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
        return callForInt("sqlite3_bind_int64", statement, column, value);
    }

    @Override
    public int bindDouble(long statement, int column, double value) {
        long[] arguments = {statement, column, Double.doubleToRawLongBits(value)};
        return callForInt("sqlite3_bind_double", arguments);
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
        return call("sqlite3_column_int64", statement, column);
    }

    @Override
    public double columnDouble(long statement, int column) {
        return Double.longBitsToDouble(call("sqlite3_column_double", statement, column));
    }

    @Override
    public byte[] columnText(long statement, int column) {
        int text = callForInt("sqlite3_column_text", statement, column);
        int bytes = callForInt("sqlite3_column_bytes", statement, column);
        return text == 0 ? new byte[0] : memory.readBytes(text, bytes);
    }

    @Override
    public byte[] columnBlob(long statement, int column) {
        int blob = callForInt("sqlite3_column_blob", statement, column);
        int bytes = callForInt("sqlite3_column_bytes", statement, column);
        return blob == 0 ? new byte[0] : memory.readBytes(blob, bytes);
    }

    @Override
    public String columnName(long statement, int column) {
        return readCString(callForInt("sqlite3_column_name", statement, column));
    }

    @Override
    public boolean isReadOnly(long statement) {
        return call("sqlite3_stmt_readonly", statement) != 0;
    }

    @Override
    public long changes(long database) {
        return call("sqlite3_changes", database);
    }

    @Override
    public long lastInsertRowid(long database) {
        return call("sqlite3_last_insert_rowid", database);
    }

    @Override
    public boolean isAutocommit(long database) {
        return call("sqlite3_get_autocommit", database) != 0;
    }

    @Override
    public String errorMessage(long database) {
        return readCString(callForInt("sqlite3_errmsg", database));
    }

    @Override
    public String errorString(int resultCode) {
        return readCString(callForInt("sqlite3_errstr", resultCode));
    }

    @Override
    public String expandedSql(long statement) {
        int expanded = callForInt("sqlite3_expanded_sql", statement);
        String sql = readCString(expanded);
        if (expanded != 0) {
            call("sqlite3_free", expanded);
        }
        return sql;
    }
}
