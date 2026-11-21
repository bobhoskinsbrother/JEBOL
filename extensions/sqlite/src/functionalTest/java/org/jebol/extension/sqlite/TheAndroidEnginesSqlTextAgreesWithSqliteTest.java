package org.jebol.extension.sqlite;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TheAndroidEnginesSqlTextAgreesWithSqliteTest {

    private final Sqlite4jEngine sqlite = new Sqlite4jEngine();
    private long database;

    @BeforeEach
    void openADatabase() {
        database = sqlite.openInMemory().database();
        run("CREATE TABLE t (a, b, c)");
    }

    @AfterEach
    void closeIt() {
        sqlite.close(database);
    }

    private void run(String sql) {
        SqliteEngine.Prepared prepared = sqlite.prepare(database, sql.getBytes(StandardCharsets.UTF_8), 0);
        sqlite.step(prepared.statement());
        sqlite.finalizeStatement(prepared.statement());
    }

    private List<Integer> sqlitesTails(byte[] sql) {
        List<Integer> tails = new ArrayList<>();
        int offset = 0;
        while (offset < sql.length) {
            SqliteEngine.Prepared prepared = sqlite.prepare(database, sql, offset);
            sqlite.finalizeStatement(prepared.statement());
            tails.add(prepared.tailOffset());
            if (prepared.tailOffset() <= offset) {
                break;
            }
            offset = prepared.tailOffset();
        }
        return tails;
    }

    private static List<Integer> ourTails(byte[] sql) {
        List<Integer> tails = new ArrayList<>();
        int offset = 0;
        while (offset < sql.length) {
            int tail = SqlText.endOfStatement(sql, offset);
            tails.add(tail);
            if (tail <= offset) {
                break;
            }
            offset = tail;
        }
        return tails;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "SELECT 1",
            "SELECT 1; SELECT 2;",
            "SELECT 1;SELECT 2",
            "  ;  SELECT 3  ;  ",
            "SELECT 'a;b'; SELECT 2",
            "SELECT 'it''s; here'; SELECT 2",
            """
            SELECT "x;y" FROM t; SELECT 2""",
            "SELECT [a;b] FROM t; SELECT 2",
            "SELECT `a;b` FROM t; SELECT 2",
            "SELECT 1 -- a comment; still\n; SELECT 2",
            "SELECT 1 /* a ; comment */ ; SELECT 2",
            "-- only a comment",
            "/* only a comment */ ; SELECT 4",
            "SELECT 'héllo;'; SELECT 'wörld'",
            "CREATE TRIGGER g AFTER INSERT ON t BEGIN UPDATE t SET b = 1; UPDATE t SET c = 2; END; SELECT 5",
            "CREATE TEMP TRIGGER h AFTER INSERT ON t BEGIN SELECT 1; END ; SELECT 6",
            "SELECT CASE WHEN 1 THEN 'end;' END; SELECT 7",
    })
    @DisplayName("a statement ends where SQLite's prepare says its tail begins, string by string")
    void aStatementEndsWhereSqlitesTailBegins(String text) {
        byte[] sql = text.getBytes(StandardCharsets.UTF_8);

        assertThat(ourTails(sql)).isEqualTo(sqlitesTails(sql));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "SELECT 1",
            "SELECT ?",
            "SELECT ?, ?, ?",
            "SELECT ?3",
            "SELECT ?2, ?",
            "SELECT :a, :b, :a",
            "SELECT @x, $y, :z, ?",
            """
            SELECT '?', "?", ? -- ?
            """,
            "SELECT :a, ?5, :b",
            "INSERT INTO t VALUES (?, ?, ?)",
    })
    @DisplayName("a statement has as many parameters as SQLite counts")
    void aStatementHasAsManyParametersAsSqliteCounts(String text) {
        byte[] sql = text.getBytes(StandardCharsets.UTF_8);
        SqliteEngine.Prepared prepared = sqlite.prepare(database, sql, 0);
        int counted = sqlite.bindParameterCount(prepared.statement());
        sqlite.finalizeStatement(prepared.statement());

        assertThat(SqlText.shape(text).parameterCount()).isEqualTo(counted);
    }

    @Test
    @DisplayName("each result code is described in SQLite's own words")
    void eachResultCodeIsDescribedAsSqliteDoes() {
        List<Integer> codes = new ArrayList<>();
        for (int code = 0; code <= 30; code++) {
            codes.add(code);
        }
        codes.addAll(List.of(100, 101, 516, 1555, 2067, 999));
        for (int code : codes) {
            assertThat(ResultCodes.describe(code)).as("result code %d", code).isEqualTo(sqlite.errorString(code));
        }
    }

    @Test
    @DisplayName("a statement expands with its bound values as SQLite writes them")
    void aStatementExpandsAsSqliteWrites() {
        String text = "SELECT ?, ?, ?, ?, :n, :n, ?";
        byte[] sql = text.getBytes(StandardCharsets.UTF_8);
        SqliteEngine.Prepared prepared = sqlite.prepare(database, sql, 0);
        long statement = prepared.statement();
        sqlite.bindLong(statement, 1, -42);
        sqlite.bindDouble(statement, 2, 1.5);
        sqlite.bindText(statement, 3, "it's".getBytes(StandardCharsets.UTF_8));
        sqlite.bindNull(statement, 4);
        sqlite.bindDouble(statement, 5, 2.0);
        String sqlites = sqlite.expandedSql(statement);
        sqlite.finalizeStatement(statement);

        Object[] bound = {-42L, 1.5, "it's", null, 2.0, null};

        assertThat(SqlText.expanded(text, SqlText.shape(text), bound)).isEqualTo(sqlites);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "SELECT 1", "VALUES (1)", "EXPLAIN SELECT 1", "WITH x AS (SELECT 1) SELECT * FROM x",
            "INSERT INTO t VALUES (1, 2, 3)", "UPDATE t SET a = 1", "DELETE FROM t", "CREATE TABLE u (x)",
            "PRAGMA user_version", "PRAGMA user_version = 3",
    })
    @DisplayName("a statement is read-only exactly when SQLite says so")
    void aStatementIsReadOnlyWhenSqliteSays(String text) {
        SqliteEngine.Prepared prepared = sqlite.prepare(database, text.getBytes(StandardCharsets.UTF_8), 0);
        boolean readOnly = sqlite.isReadOnly(prepared.statement());
        sqlite.finalizeStatement(prepared.statement());

        assertThat(SqlText.shape(text).isReadOnly()).isEqualTo(readOnly);
    }
}
