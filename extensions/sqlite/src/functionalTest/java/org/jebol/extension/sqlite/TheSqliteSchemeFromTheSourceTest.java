package org.jebol.extension.sqlite;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteSchemeFromTheSourceTest {

    private static final String PRELUDE = """
            import 'sqlite
            fail-of: func [code /local e] [
                either error? e: try code [reduce [e/type e/id e/arg1]] ['no-error]
            ]
            """;

    private static final String CARS = """
            db: open/new sqlite:cars.db
            write db {create table cars(id integer primary key, name text);
                insert into cars values (1, 'Audi'), (2, 'Bentley'), (3, 'Citroen'), (4, 'Dacia'), (5, 'Elan')}
            """;

    @TempDir
    Path directory;

    private ASqliteInterpreter sqlite;

    @BeforeEach
    void importIt() {
        sqlite = new ASqliteInterpreter(directory);
        sqlite.answerTo(PRELUDE);
    }

    private String answerTo(String source) {
        return sqlite.answerTo(source);
    }

    @Test
    @DisplayName("importing the module makes the scheme")
    void comesWithTheModule() {
        assertThat(answerTo("{SQLite database scheme} = select system/schemes/sqlite 'title")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("open without /new refuses a missing file, and /new makes it")
    void opensOnlyWhatExists() {
        assertThat(answerTo("""
                a: fail-of [open sqlite:new.db]
                db: open/new sqlite:new.db
                reduce [a exists? %new.db open? db db/spec/path]""")).isEqualTo("""
                [[Access cannot-open %/new.db] file #(true) %/new.db]""");
    }

    @Test
    @DisplayName("open/new in a missing directory is the command's failure")
    void refusesAMissingDirectory() {
        assertThat(answerTo("fail-of [open/new sqlite:not-exists/dir]")).isEqualTo("""
                [Command command-fail "[SQLITE] unable to open database file"]""");
    }

    @Test
    @DisplayName("take answers one row at a time, take/part several, then none")
    void takesRows() {
        assertThat(answerTo(CARS + """
                insert db {select id, name from cars order by id}
                reduce [copy take db copy take/part db 2 copy take/part db 5 take db]""")).isEqualTo("""
                [[1 "Audi"] [2 "Bentley" 3 "Citroen"] [4 "Dacia" 5 "Elan"] _]""");
    }

    @Test
    @DisplayName("every read answers the port's one data block, which the next read empties and fills again")
    void answersOneDataBlock() {
        assertThat(answerTo(CARS + """
                insert db {select id from cars order by id}
                a: take db
                b: take db
                reduce [same? a b same? a db/data a]""")).isEqualTo("[#(true) #(true) [2]]");
    }

    @Test
    @DisplayName("read/part answers that many, read the rest, and a second read starts again")
    void readsRows() {
        assertThat(answerTo(CARS + """
                insert db {select id from cars order by id}
                reduce [copy read/part db 2 copy read db copy read db]""")).isEqualTo("[[1 2] [3 4 5] [1 2 3 4 5]]");
    }

    @Test
    @DisplayName("insert of the same text again reuses its statement, starting it again")
    void reusesAStatement() {
        assertThat(answerTo(CARS + """
                insert db {select id from cars order by id}
                a: take db
                insert db {select id from cars order by id}
                reduce [a take db length? db/state/statements]""")).isEqualTo("[[1] [1] 1]");
    }

    @Test
    @DisplayName("pick reads what a query answers, none for no rows and for a write")
    void picks() {
        assertThat(answerTo(CARS + """
                reduce [
                    copy pick db {select name from cars where id > 3}
                    pick db {select name from cars where id > 99}
                    pick db {insert into cars values (6, 'Ford')}
                    copy pick db {select count(*) from cars}
                ]""")).isEqualTo("""
                [["Dacia" "Elan"] _ _ [6]]""");
    }

    @Test
    @DisplayName("modify 'trace-level traces, and any other field does nothing")
    void modifiesTheTrace() {
        assertThat(sqlite.printedBy(CARS + """
                modify db 'trace-level 1
                pick db {select 1}
                modify db 'trace-level 0
                print mold modify db 'other 1
                pick db {select 2}""").replace("\r\n", "\n")).isEqualTo("""
                TRACE[1] STMT: select 1
                _
                """);
    }

    @Test
    @DisplayName("after close every action is not-open")
    void refusesAClosedPort() {
        assertThat(answerTo(CARS + """
                close db
                unique collect [foreach code [
                    [close db] [write db {select 1}] [insert db {select 1}] [read db] [take db] [pick db {select 1}]
                ] [keep/only fail-of code]]""")).isEqualTo("[[Access not-open sqlite:cars.db]]");
    }

    @Test
    @DisplayName("what it wrote is there when it is opened again")
    void keepsItsData() {
        assertThat(answerTo(CARS + """
                close db
                db: open sqlite:cars.db
                pick db {select count(*) from cars}""")).isEqualTo("[5]");
    }
}
