package org.jebol.extension.sqlite;

import org.jebol.application.Command;
import org.jebol.application.ExtensionCommand;
import org.jebol.application.HandleType;
import org.jebol.domain.host.FileInformation;
import org.jebol.domain.value.AnyBlockValue;
import org.jebol.domain.value.AnyDecimalValue;
import org.jebol.domain.value.AnyStringValue;
import org.jebol.domain.value.AnyWordValue;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.FileValue;
import org.jebol.domain.value.GetWordValue;
import org.jebol.domain.value.HandleValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

final class SqliteCommands {

    static final String DATABASE_HANDLE = "sqlite-db";
    static final String STATEMENT_HANDLE = "sqlite-stmt";

    private static final String PREFIX = "[SQLITE] ";
    private static final String INVALID_DATABASE = "Invalid SQLite DB handle!";
    private static final String INVALID_STATEMENT = "Invalid SQLite STMT handle!";
    private static final String UNSUPPORTED = "[SQLITE] Unsupported value type!";
    private static final String BUSY = "[SQLITE] Statement is busy!";
    private static final String STATEMENT_MISUSE = "[SQLITE] Statement misuse!";
    private static final String NO_CONNECTION_MESSAGE = "out of memory";
    private static final String IN_MEMORY = ":memory:";
    private static final int CANNOT_OPEN = 14;
    private static final int RANGE = 25;
    private static final int TRACE_STATEMENT = 1;
    private static final int TRACE_PROFILE = 2;
    private static final int TRACE_ROW = 4;
    private static final int TRACE_CLOSE = 8;
    private static final int TRACE_EVERYTHING = 15;
    private static final String REBOL_NEEDED = "3.10.2";
    private static final Set<String> HAS_NO_EXTENSION_TYPE = Set.of(
            "money!", "hash!", "map!", "datatype!", "typeset!", "native!", "action!", "rebcode!", "command!",
            "op!", "closure!", "function!", "frame!", "error!", "task!", "port!", "event!", "library!", "utype!");

    private final Supplier<SqliteEngine> engines;

    private SqliteEngine library;

    SqliteCommands(Supplier<SqliteEngine> engines) {
        this.engines = engines;
    }

    static List<HandleType> handleTypes() {
        return List.of(
                new HandleType(DATABASE_HANDLE, SqliteDatabase.class),
                new HandleType(STATEMENT_HANDLE, SqliteStatement.class));
    }

    List<ExtensionCommand> commands() {
        return List.of(
                new ExtensionCommand("info",
                        "{Returns info about SQLite extension library} /of handle [handle!] {SQLite Extension handle}",
                        this::info),
                new ExtensionCommand("open", "{Opens a new database connection} file [file!]", this::open),
                new ExtensionCommand("exec", "{Runs zero or more semicolon-separate SQL statements}"
                        + " db [handle!] {sqlite-db} sql [string!] {statements}", this::exec),
                new ExtensionCommand("eval", "{Evaluates SQL statement with optional paramaters}"
                        + " db [handle!] {sqlite-db} query [string! block! handle!]"
                        + " {single statement, a single statement with parameters or a prepared statement}",
                        this::eval),
                new ExtensionCommand("last-insert-id", "{Returns the rowid of the most recent successful INSERT"
                        + " into a rowid table or virtual table on database connection} db [handle!] {sqlite-db}",
                        this::lastInsertId),
                new ExtensionCommand("finalize", "{Deletes prepared statement} stmt [handle!] {sqlite-stmt}",
                        this::finalizeStatement),
                new ExtensionCommand("trace", "{Traces debug output} db [handle!] {sqlite-db} mask [integer!]",
                        this::trace),
                new ExtensionCommand("prepare", "{Prepares SQL statement} db [handle!] {sqlite-db}"
                        + " sql [string!] {statement}", this::prepare),
                new ExtensionCommand("reset", "{Resets prepared statement} stmt [handle!] {sqlite-stmt}",
                        this::reset),
                new ExtensionCommand("step", "{Executes prepared statement} stmt [handle!] {sqlite-stmt}"
                        + " /rows {Multiple times if there is enough rows in the result} count [integer!]"
                        + " /with parameters [block!]", this::step),
                new ExtensionCommand("close", "{Closes a database connection} db [handle!] {sqlite-db}",
                        this::close),
                new ExtensionCommand("columns", "{Returns column names associated with the statement}"
                        + " stmt [handle!] {sqlite-stmt}", this::columns),
                new ExtensionCommand("initialize", "{Initializes the SQLite library}", this::initialize),
                new ExtensionCommand("shutdown", "{Deallocate any resources that were allocated}", this::shutdown));
    }

    private SqliteEngine library() {
        if (library == null) {
            library = engines.get();
        }
        return library;
    }

    private SqliteDatabase resolveDatabase(Command call, Value handle) {
        if (!call.handleKind(handle).filter(DATABASE_HANDLE::equals).isPresent()) {
            throw new SqliteFailure(INVALID_DATABASE);
        }
        return call.unwrap(handle, SqliteDatabase.class);
    }

    private SqliteStatement resolveStatement(Command call, Value handle) {
        if (!call.handleKind(handle).filter(STATEMENT_HANDLE::equals).isPresent()) {
            throw new SqliteFailure(INVALID_STATEMENT);
        }
        SqliteStatement statement = call.unwrap(handle, SqliteStatement.class);
        if (statement.isFinalized()) {
            throw new SqliteFailure(INVALID_STATEMENT);
        }
        return statement;
    }

    private synchronized Value info(Command call) {
        if (call.refinements().contains("of")) {
            return describeHandle(call, call.arguments().getFirst());
        }
        SqliteEngine engine = library();
        return StringValue.of("\n"
                + "Rebol-needed:   " + REBOL_NEEDED + "\n"
                + "Rebol-current:  " + readRebolVersion(call) + "\n"
                + "SQLite-version: " + engine.libraryVersion() + "\n"
                + "SQLite-memory:  " + Long.toUnsignedString(engine.memoryUsed()) + "\n"
                + "SQLite-mem-top: " + Long.toUnsignedString(engine.memoryHighwater()) + "\n");
    }

    private String readRebolVersion(Command call) {
        if (call.readSystemField("version") instanceof TupleValue version) {
            int[] parts = version.segments();
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        return "0.0.0";
    }

    private Value describeHandle(Command call, Value handle) {
        Optional<String> kind = call.handleKind(handle);
        if (kind.filter(DATABASE_HANDLE::equals).isPresent()) {
            SqliteDatabase database = call.unwrap(handle, SqliteDatabase.class);
            return StringValue.of("sqlite-ctx-Ptr: <" + pointerFor(readHandleIdentity(handle)) + ">\n"
                    + "sqlite-ctx-DB:  <" + pointerFor(database.isClosed() ? 0 : database.connection()) + ">\n"
                    + "sqlite-buffer-size: 0\n");
        }
        if (kind.filter(STATEMENT_HANDLE::equals).isPresent()) {
            SqliteStatement statement = call.unwrap(handle, SqliteStatement.class);
            SqliteEngine engine = statement.database().engine();
            boolean finalized = statement.isFinalized();
            return StringValue.of("sqlite-stmt-Ptr:  <" + pointerFor(readHandleIdentity(handle)) + ">\n"
                    + "sqlite-stmt:      <" + pointerFor(statement.pointer()) + ">\n"
                    + "last-result-code:  " + statement.lastResultCode() + "\n"
                    + "bind-parameters:   " + (finalized ? 0 : engine.bindParameterCount(statement.pointer())) + "\n"
                    + "data-count:        " + (finalized ? 0 : engine.dataCount(statement.pointer())) + "\n");
        }
        return StringValue.of("");
    }

    private static long readHandleIdentity(Value handle) {
        return ((HandleValue) handle).identity();
    }

    private static String pointerFor(long address) {
        return address == 0 ? "(nil)" : "0x" + Long.toHexString(address);
    }

    private Value open(Command call) {
        String path = ((FileValue) call.arguments().getFirst()).text();
        SqliteEngine engine = engines.get();
        Optional<DatabaseFile> file = path.equals(IN_MEMORY) || path.isEmpty()
                ? Optional.empty()
                : Optional.of(DatabaseFile.at(call.files(), path));
        Optional<byte[]> contents = file.isEmpty() ? Optional.of(new byte[0]) : readOrCreate(call, file.get());
        if (contents.isEmpty()) {
            throw new SqliteFailure(PREFIX + engine.errorString(CANNOT_OPEN));
        }
        SqliteEngine.Opened opened = engine.openInMemory();
        if (opened.resultCode() != SqliteEngine.OK) {
            throw new SqliteFailure(PREFIX + engine.errorString(opened.resultCode()));
        }
        SqliteDatabase database = new SqliteDatabase(engine, opened.database(), file);
        database.load(contents.get());
        return call.makeHandle(DATABASE_HANDLE, database);
    }

    private Optional<byte[]> readOrCreate(Command call, DatabaseFile file) {
        Optional<FileInformation> found = file.describe(call.files());
        if (found.isPresent()) {
            return found.get().isDirectory() ? Optional.empty() : file.read(call.files());
        }
        return file.write(call.files(), new byte[0]) ? Optional.of(new byte[0]) : Optional.empty();
    }

    private Value exec(Command call) {
        SqliteDatabase database = resolveDatabase(call, call.arguments().getFirst());
        byte[] sql = readSql((AnyStringValue) call.arguments().get(1));
        if (database.isClosed()) {
            throw new SqliteFailure(PREFIX + database.engine().errorString(SqliteEngine.MISUSE) + " "
                    + NO_CONNECTION_MESSAGE);
        }
        database.bringUpToDate(call.files());
        try {
            runEveryStatement(call, database, sql);
        } finally {
            database.saveIfChanged(call.files());
        }
        return UnsetValue.unset();
    }

    private void runEveryStatement(Command call, SqliteDatabase database, byte[] sql) {
        SqliteEngine engine = database.engine();
        int offset = 0;
        while (offset < sql.length) {
            SqliteEngine.Prepared prepared = database.prepare(sql, offset);
            if (prepared.resultCode() != SqliteEngine.OK) {
                throw failWithMessage(database, prepared.resultCode());
            }
            offset = skipSpaces(sql, prepared.tailOffset());
            if (prepared.statement() == 0) {
                continue;
            }
            SqliteStatement statement = new SqliteStatement(database, prepared.statement());
            markChangedIfWriting(database, statement);
            while (stepAndTrace(call, statement) == SqliteEngine.ROW) {
                printRow(call, engine, statement.pointer());
            }
            int resultCode = finishRunAndThen(call, statement, () -> engine.finalizeStatement(statement.pointer()));
            if (resultCode != SqliteEngine.OK) {
                throw failWithMessage(database, resultCode);
            }
        }
    }

    private static int skipSpaces(byte[] sql, int offset) {
        int at = offset;
        while (at < sql.length && isSqlSpace(sql[at])) {
            at++;
        }
        return at;
    }

    private static boolean isSqlSpace(byte octet) {
        return octet == ' ' || octet == '\t' || octet == '\n' || octet == '\f' || octet == '\r' || octet == 0x0B;
    }

    private void printRow(Command call, SqliteEngine engine, long statement) {
        int columns = engine.columnCount(statement);
        for (int column = 0; column < columns; column++) {
            String value = engine.columnType(statement, column) == SqliteEngine.NULL
                    ? "NULL"
                    : textUpToNul(engine.columnText(statement, column));
            call.writeLine(engine.columnName(statement, column) + " = " + value);
        }
        call.writeLine("");
    }

    private static String textUpToNul(byte[] utf8) {
        int end = 0;
        while (end < utf8.length && utf8[end] != 0) {
            end++;
        }
        return new String(utf8, 0, end, StandardCharsets.UTF_8);
    }

    private SqliteFailure failWithMessage(SqliteDatabase database, int resultCode) {
        SqliteEngine engine = database.engine();
        return new SqliteFailure(PREFIX + engine.errorString(resultCode) + " " + database.errorMessage());
    }

    private Value lastInsertId(Command call) {
        SqliteDatabase database = resolveDatabase(call, call.arguments().getFirst());
        return IntegerValue.of(database.isClosed() ? 0 : database.engine().lastInsertRowid(database.connection()));
    }

    private Value trace(Command call) {
        SqliteDatabase database = resolveDatabase(call, call.arguments().getFirst());
        if (!database.isClosed()) {
            database.traceWith((int) (((IntegerValue) call.arguments().get(1)).magnitude() & TRACE_EVERYTHING));
        }
        return UnsetValue.unset();
    }

    private Value prepare(Command call) {
        SqliteDatabase database = resolveDatabase(call, call.arguments().getFirst());
        byte[] sql = readSql((AnyStringValue) call.arguments().get(1));
        if (database.isClosed()) {
            throw new SqliteFailure(PREFIX + database.engine().errorString(SqliteEngine.MISUSE) + " "
                    + NO_CONNECTION_MESSAGE);
        }
        database.bringUpToDate(call.files());
        SqliteEngine.Prepared prepared = database.prepare(sql, 0);
        if (prepared.resultCode() != SqliteEngine.OK) {
            throw failWithMessage(database, prepared.resultCode());
        }
        if (prepared.statement() != 0) {
            database.holdStatement();
        }
        return call.makeHandle(STATEMENT_HANDLE, new SqliteStatement(database, prepared.statement()));
    }

    private Value reset(Command call) {
        SqliteStatement statement = resolveStatement(call, call.arguments().getFirst());
        SqliteEngine engine = statement.database().engine();
        finishRunAndThen(call, statement, () -> engine.reset(statement.pointer()));
        statement.rememberResultCode(SqliteEngine.ROW);
        return UnsetValue.unset();
    }

    private Value finalizeStatement(Command call) {
        SqliteStatement statement = resolveStatement(call, call.arguments().getFirst());
        SqliteDatabase database = statement.database();
        finishRunAndThen(call, statement, () -> database.engine().finalizeStatement(statement.pointer()));
        statement.forgetPointer();
        database.releaseStatement();
        database.closeConnectionWhenUnused();
        return UnsetValue.unset();
    }

    private Value close(Command call) {
        SqliteDatabase database = resolveDatabase(call, call.arguments().getFirst());
        if (!database.isClosed()) {
            database.saveIfChanged(call.files());
            if ((database.traceMask() & TRACE_CLOSE) != 0) {
                call.writeLine("TRACE[" + TRACE_CLOSE + "] CLOSE");
            }
            database.closeForScript();
            database.closeConnectionWhenUnused();
        }
        return UnsetValue.unset();
    }

    private Value columns(Command call) {
        SqliteStatement statement = resolveStatement(call, call.arguments().getFirst());
        SqliteEngine engine = statement.database().engine();
        int count = engine.columnCount(statement.pointer());
        if (count == 0) {
            return NoneValue.none();
        }
        List<Value> names = new ArrayList<>(count);
        for (int column = 0; column < count; column++) {
            names.add(StringValue.of(engine.columnName(statement.pointer(), column)));
        }
        return BlockValue.of(names);
    }

    private synchronized Value initialize(Command call) {
        int resultCode = library().initialize();
        if (resultCode != SqliteEngine.OK) {
            throw new SqliteFailure(PREFIX + library().errorString(resultCode));
        }
        return LogicValue.of(true);
    }

    private synchronized Value shutdown(Command call) {
        int resultCode = library().shutdown();
        if (resultCode != SqliteEngine.OK) {
            throw new SqliteFailure(PREFIX + library().errorString(resultCode));
        }
        return LogicValue.of(true);
    }

    private Value step(Command call) {
        SqliteStatement statement = resolveStatement(call, call.arguments().getFirst());
        SqliteDatabase database = statement.database();
        SqliteEngine engine = database.engine();
        boolean countsRows = call.refinements().contains("rows");
        long maxRows = countsRows ? ((IntegerValue) call.arguments().get(1)).magnitude() : 1;
        boolean allRows = maxRows < 1;
        if (allRows) {
            maxRows = 1;
        }
        if (call.refinements().contains("with")) {
            bindEveryValueOrRefuse(call, statement, (AnyBlockValue) call.arguments().get(countsRows ? 2 : 1));
        }
        database.bringUpToDate(call.files());
        try {
            return stepRows(call, statement, engine, maxRows, allRows);
        } finally {
            database.saveIfChanged(call.files());
        }
    }

    private Value stepRows(Command call, SqliteStatement statement, SqliteEngine engine, long maxRows,
                           boolean allRows) {
        long pointer = statement.pointer();
        int resultCode = engine.isReadOnly(pointer) ? statement.lastResultCode() : SqliteEngine.ROW;
        List<Value> rows = null;
        markChangedIfWriting(statement.database(), statement);
        for (long row = 0; resultCode == SqliteEngine.ROW && (allRows || row < maxRows); row++) {
            resultCode = stepAndTrace(call, statement);
            statement.rememberResultCode(resultCode);
            switch (resultCode) {
                case SqliteEngine.ROW -> {
                    if (rows == null) {
                        rows = new ArrayList<>();
                    }
                    appendRow(rows, engine, pointer, engine.dataCount(pointer));
                }
                case SqliteEngine.DONE -> {
                    if (rows != null) {
                        return BlockValue.of(rows);
                    }
                    finishRunAndThen(call, statement, () -> engine.reset(pointer));
                    return NoneValue.none();
                }
                case SqliteEngine.BUSY -> throw new SqliteFailure(BUSY);
                case SqliteEngine.ERROR ->
                        throw new SqliteFailure(engine.errorString(finishRunAndThen(call, statement,
                                () -> engine.reset(pointer))));
                case SqliteEngine.MISUSE -> throw new SqliteFailure(STATEMENT_MISUSE);
                default -> {
                }
            }
        }
        if (resultCode < SqliteEngine.ROW && resultCode != SqliteEngine.OK) {
            throw new SqliteFailure(engine.errorString(finishRunAndThen(call, statement,
                    () -> engine.reset(pointer))));
        }
        return rows == null ? NoneValue.none() : BlockValue.of(rows);
    }

    private void bindEveryValueOrRefuse(Command call, SqliteStatement statement, AnyBlockValue parameters) {
        SqliteEngine engine = statement.database().engine();
        long pointer = statement.pointer();
        engine.clearBindings(pointer);
        List<Value> values = parameters.head().remaining();
        for (int column = 0; column < values.size(); column++) {
            Value value = values.get(column);
            String type = spellDatatype(value);
            if (type.equals("word!") || type.equals("get-word!") || type.equals("get-path!")) {
                value = resolve(call, value);
            }
            if (bind(engine, pointer, column + 1, value).isEmpty()) {
                throw new SqliteFailure(UNSUPPORTED);
            }
        }
    }

    private Optional<Integer> bind(SqliteEngine engine, long statement, int column, Value value) {
        return switch (spellDatatype(value)) {
            case "integer!" -> Optional.of(engine.bindLong(statement, column, ((IntegerValue) value).magnitude()));
            case "decimal!" -> Optional.of(engine.bindDouble(statement, column,
                    ((AnyDecimalValue) value).quantity()));
            case "string!" -> Optional.of(engine.bindText(statement, column, readTextFromHead(value)));
            case "none!" -> Optional.of(engine.bindNull(statement, column));
            case "logic!" -> Optional.of(engine.bindLong(statement, column, value.isTruthy() ? 1 : 0));
            default -> Optional.empty();
        };
    }

    private Value eval(Command call) {
        SqliteDatabase database = resolveDatabase(call, call.arguments().getFirst());
        Value query = call.arguments().get(1);
        database.startCountingChanges();
        if (!database.isClosed()) {
            database.bringUpToDate(call.files());
        }
        try {
            return new Evaluation(call, database).answer(query);
        } finally {
            if (!database.isClosed()) {
                database.saveIfChanged(call.files());
            }
        }
    }

    private final class Evaluation {

        private final Command call;

        private final SqliteDatabase database;

        private SqliteEngine engine;

        private SqliteStatement statement;

        private SqliteStatement prepared;

        private SqliteStatement given;

        private List<Value> parameters;

        private int index;

        Evaluation(Command call, SqliteDatabase database) {
            this.call = call;
            this.database = database;
            this.engine = database.engine();
        }

        Value answer(Value query) {
            int resultCode;
            if (query instanceof HandleValue) {
                given = resolveStatement(call, query);
                statement = given;
                engine = given.database().engine();
            } else if (query instanceof AnyStringValue text) {
                resultCode = prepareOwn(readSql(text));
                if (resultCode != SqliteEngine.OK) {
                    throw fail(resultCode);
                }
            } else {
                AnyBlockValue block = (AnyBlockValue) query;
                parameters = block.head().remaining();
                index = block.index() - 1;
                Value first = valueAt(index);
                String type = spellDatatype(first);
                if (type.equals("string!")) {
                    resultCode = prepareOwn(readSql((AnyStringValue) first));
                    if (resultCode != SqliteEngine.OK) {
                        throw fail(resultCode);
                    }
                } else if (type.equals("handle!")
                        && call.handleKind(first).filter(STATEMENT_HANDLE::equals).isPresent()) {
                    given = call.unwrap(first, SqliteStatement.class);
                    statement = given;
                    engine = given.database().engine();
                } else {
                    throw fail(SqliteEngine.MISUSE);
                }
                index++;
            }
            return run();
        }

        private int prepareOwn(byte[] sql) {
            if (database.isClosed()) {
                return SqliteEngine.MISUSE;
            }
            SqliteEngine.Prepared made = database.prepare(sql, 0);
            if (made.resultCode() == SqliteEngine.OK) {
                prepared = new SqliteStatement(database, made.statement());
                statement = prepared;
            }
            return made.resultCode();
        }

        private Value run() {
            int resultCode = SqliteEngine.OK;
            if (parameters != null) {
                if (given != null && given.lastResultCode() == SqliteEngine.DONE) {
                    finishRunAndThen(call, given, () -> engine.reset(given.pointer()));
                }
                resultCode = bindParameters();
                if (resultCode != SqliteEngine.OK) {
                    return finish(resultCode);
                }
            }
            List<Value> rows = null;
            int columns = 0;
            markChangedIfWriting(database, statement);
            while (true) {
                resultCode = stepAndTrace(call, statement);
                if (given != null) {
                    given.rememberResultCode(resultCode);
                }
                if (resultCode == SqliteEngine.ROW) {
                    if (rows == null) {
                        columns = engine.dataCount(statement.pointer());
                        rows = new ArrayList<>();
                    }
                    appendRow(rows, engine, statement.pointer(), columns);
                } else if (resultCode == SqliteEngine.DONE) {
                    if (rows != null) {
                        finalizeOwn();
                        return BlockValue.of(rows);
                    }
                    if (!database.isClosed()) {
                        database.countChanges();
                    }
                    finishRunAndThen(call, statement, () -> engine.reset(statement.pointer()));
                    if (parameters != null && index < parameters.size()) {
                        resultCode = bindParameters();
                        if (resultCode != SqliteEngine.OK) {
                            return finish(resultCode);
                        }
                        continue;
                    }
                    finalizeOwn();
                    return IntegerValue.of(database.lastInsertCount());
                } else {
                    return finish(resultCode);
                }
            }
        }

        private int bindParameters() {
            int resultCode = SqliteEngine.OK;
            List<Value> values = parameters;
            int at = index;
            boolean blockData = false;
            if (!statement.isFinalized()) {
                Value first = resolveAt(values, at);
                if (first != null && spellDatatype(first).equals("block!")) {
                    AnyBlockValue row = (AnyBlockValue) first;
                    values = row.head().remaining();
                    at = row.index() - 1;
                    blockData = true;
                }
                int count = engine.bindParameterCount(statement.pointer());
                if (count > 0) {
                    engine.clearBindings(statement.pointer());
                }
                for (int column = 1; column <= count; column++, at++) {
                    Value value = resolveAt(values, at);
                    resultCode = value == null
                            ? SqliteEngine.OK
                            : bindForEval(statement.pointer(), column, value);
                }
            }
            index = blockData ? index + 1 : at;
            return resultCode;
        }

        private int bindForEval(long pointer, int column, Value value) {
            if (HAS_NO_EXTENSION_TYPE.contains(spellDatatype(value))) {
                return SqliteEngine.OK;
            }
            return bind(engine, pointer, column, value).orElse(SqliteEngine.MISUSE);
        }

        private Value resolveAt(List<Value> values, int at) {
            if (at >= values.size()) {
                return null;
            }
            Value value = values.get(at);
            return switch (spellDatatype(value)) {
                case "word!", "get-word!", "path!", "get-path!" -> resolve(call, value);
                default -> value;
            };
        }

        private Value valueAt(int at) {
            Value resolved = resolveAt(parameters, at);
            return resolved == null ? UnsetValue.unset() : resolved;
        }

        private Value finish(int resultCode) {
            finalizeOwn();
            if (resultCode != SqliteEngine.OK) {
                throw fail(resultCode);
            }
            return UnsetValue.unset();
        }

        private void finalizeOwn() {
            if (prepared != null) {
                SqliteStatement own = prepared;
                finishRunAndThen(call, own, () -> engine.finalizeStatement(own.pointer()));
                own.forgetPointer();
                prepared = null;
            }
        }

        private SqliteFailure fail(int resultCode) {
            return new SqliteFailure(PREFIX + engine.errorString(resultCode));
        }
    }

    private void appendRow(List<Value> rows, SqliteEngine engine, long statement, int columns) {
        for (int column = 0; column < columns; column++) {
            rows.add(switch (engine.columnType(statement, column)) {
                case SqliteEngine.INTEGER -> IntegerValue.of(engine.columnLong(statement, column));
                case SqliteEngine.FLOAT -> DecimalValue.of(engine.columnDouble(statement, column));
                case SqliteEngine.TEXT -> StringValue.of(
                        new String(engine.columnText(statement, column), StandardCharsets.UTF_8));
                case SqliteEngine.BLOB -> BinaryValue.ofBytes(engine.columnBlob(statement, column));
                default -> NoneValue.none();
            });
        }
    }

    private void markChangedIfWriting(SqliteDatabase database, SqliteStatement statement) {
        if (!statement.isFinalized() && !statement.database().engine().isReadOnly(statement.pointer())) {
            database.markChanged();
            statement.database().markChanged();
        }
    }

    private int stepAndTrace(Command call, SqliteStatement statement) {
        SqliteDatabase database = statement.database();
        SqliteEngine engine = database.engine();
        if (!statement.isFinalized() && statement.startRun(System.nanoTime())) {
            database.startRunningStatement();
            if ((database.traceMask() & TRACE_STATEMENT) != 0) {
                call.writeLine("TRACE[" + TRACE_STATEMENT + "] STMT: " + engine.expandedSql(statement.pointer()));
            }
        }
        int resultCode = engine.step(statement.pointer());
        if (resultCode == SqliteEngine.ROW) {
            if ((database.traceMask() & TRACE_ROW) != 0) {
                call.writeLine("TRACE[" + TRACE_ROW + "] ROW");
            }
        } else {
            endRun(call, statement);
        }
        return resultCode;
    }

    private int finishRunAndThen(Command call, SqliteStatement statement, java.util.function.IntSupplier then) {
        endRun(call, statement);
        return then.getAsInt();
    }

    private void endRun(Command call, SqliteStatement statement) {
        if (!statement.isRunning()) {
            return;
        }
        SqliteDatabase database = statement.database();
        long took = statement.endRun(System.nanoTime());
        database.stopRunningStatement();
        if ((database.traceMask() & TRACE_PROFILE) != 0) {
            call.writeLine("TRACE[" + TRACE_PROFILE + "] PROFILE: " + took + "ns");
        }
    }

    private static Value resolve(Command call, Value value) {
        if (value instanceof AnyWordValue word) {
            return call.evaluate(BlockValue.of(GetWordValue.TYPE.spell(word.spelling(), word.binding())));
        }
        return call.evaluate(BlockValue.of(value));
    }

    private static String spellDatatype(Value value) {
        return value.datatype().literalSpelling();
    }

    private static byte[] readSql(AnyStringValue text) {
        return cutAtNul(text.text());
    }

    private static byte[] readTextFromHead(Value value) {
        return cutAtNul(((AnyStringValue) value).head().text());
    }

    private static byte[] cutAtNul(String text) {
        int nul = text.indexOf('\0');
        return (nul < 0 ? text : text.substring(0, nul)).getBytes(StandardCharsets.UTF_8);
    }
}
