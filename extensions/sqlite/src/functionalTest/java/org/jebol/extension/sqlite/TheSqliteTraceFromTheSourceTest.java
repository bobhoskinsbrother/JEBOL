package org.jebol.extension.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteTraceFromTheSourceTest {

    @TempDir
    Path directory;

    private ASqliteInterpreter sqlite;

    @BeforeEach
    void openADatabase() {
        sqlite = new ASqliteInterpreter(directory);
        sqlite.answerTo("""
                s: import 'sqlite
                db: s/open to file! {:memory:}
                s/exec db {create table t(a)}""");
    }

    private String printedBy(String source) {
        return sqlite.printedBy(source).replace("\r\n", "\n").replaceAll("PROFILE: [0-9]+ns", "PROFILE: <n>ns");
    }

    @Test
    @DisplayName("mask 1 prints each statement as it starts, with its values in place")
    void tracesStatements() {
        assertThat(printedBy("""
                s/trace db 1
                s/eval db [{insert into t values (?)} 7]
                s/eval db [{select a from t where a = ?} {text}]
                st: s/prepare db {select ?, ?}
                s/step/with st [1.5 #(none)]""")).isEqualTo("""
                TRACE[1] STMT: insert into t values (7)
                TRACE[1] STMT: select a from t where a = 'text'
                TRACE[1] STMT: select 1.5, NULL
                """);
    }

    @Test
    @DisplayName("each statement exec runs is traced with the text it was cut from, semicolon and all")
    void tracesEachStatementOfExec() {
        assertThat(printedBy("s/trace db 1 s/exec db {select 1 as a; select 2 as b}")).isEqualTo("""
                TRACE[1] STMT: select 1 as a;
                a = 1

                TRACE[1] STMT: select 2 as b
                b = 2

                """);
    }

    @Test
    @DisplayName("mask 4 prints each row, and 2 the time a statement took when it ends")
    void tracesRowsAndTimings() {
        assertThat(printedBy("s/trace db 4 s/eval db {select 1 union all select 2} s/trace db 6 s/eval db {select 1}"))
                .isEqualTo("""
                        TRACE[4] ROW
                        TRACE[4] ROW
                        TRACE[4] ROW
                        TRACE[2] PROFILE: <n>ns
                        """);
    }

    @Test
    @DisplayName("a statement reset before it ends is timed at the reset")
    void timesAResetStatement() {
        assertThat(printedBy("""
                st: s/prepare db {select 1 union all select 2}
                s/trace db 2
                s/step st
                s/reset st""")).isEqualTo("""
                TRACE[2] PROFILE: <n>ns
                """);
    }

    @Test
    @DisplayName("mask 0 prints nothing, and only the low four bits count")
    void masks() {
        assertThat(printedBy("s/trace db 0 s/eval db {select 1} s/trace db 17 s/eval db {select 17}")).isEqualTo("""
                TRACE[1] STMT: select 17
                """);
    }

    @Test
    @DisplayName("mask 8 prints the close")
    void tracesTheClose() {
        assertThat(printedBy("s/trace db 8 s/close db")).isEqualTo("""
                TRACE[8] CLOSE
                """);
    }

    @Test
    @DisplayName("the close is printed when it is asked for, even while a statement keeps the connection open")
    void tracesTheCloseWhenAsked() {
        assertThat(printedBy("""
                st: s/prepare db {select 1}
                s/trace db 8
                s/close db
                print {closed}
                s/finalize st""")).isEqualTo("""
                TRACE[8] CLOSE
                closed
                """);
    }
}
