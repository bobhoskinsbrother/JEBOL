package org.jebol.extension.sqlite;

import org.jebol.adapter.host.FileSystemPort;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TheSqliteFilesFromTheSourceTest {

    private static final String PRELUDE = """
            s: import 'sqlite
            fail-of: func [code /local e] [
                either error? set/any 'e try code [reduce [e/type e/id e/arg1]] ['no-error]
            ]
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
    @DisplayName("open makes an empty file at once, and the first write fills it with whole pages")
    void makesTheFileOnOpen() {
        assertThat(answerTo("""
                before: exists? %made.db
                db: s/open %made.db
                empty: size? %made.db
                s/exec db {create table t(a)}
                reduce [before empty size? %made.db copy/part read %made.db 16]"""))
                .isEqualTo("[_ 0 8192 #{53514C69746520666F726D6174203300}]");
    }

    @Test
    @DisplayName("what one handle writes and closes, the next reads back")
    void readsBackWhatWasWritten() {
        assertThat(answerTo("""
                db: s/open %made.db
                s/exec db {create table t(a); insert into t values (1), (2)}
                s/close db
                db: s/open %made.db
                s/eval db {select a from t order by a}""")).isEqualTo("[1 2]");
    }

    @Test
    @DisplayName("a write reaches the file before close, so a second handle sees it at once")
    void writesBeforeClose() {
        assertThat(answerTo("""
                one: s/open %shared.db
                two: s/open %shared.db
                s/exec one {create table t(a); insert into t values ('first')}
                a: s/eval two {select a from t}
                s/eval one {insert into t values ('second')}
                reduce [a s/eval two {select a from t order by rowid}]"""))
                .isEqualTo("""
                        [["first"] ["first" "second"]]""");
    }

    @Test
    @DisplayName("an open transaction is not written, and its commit is")
    void waitsForCommit() {
        assertThat(answerTo("""
                one: s/open %txn.db
                s/exec one {create table t(a)}
                s/exec one {begin; insert into t values (1)}
                two: s/open %txn.db
                a: s/eval two {select count(*) from t}
                s/exec one {commit}
                reduce [a s/eval two {select count(*) from t}]""")).isEqualTo("[[0] [1]]");
    }

    @Test
    @DisplayName("a relative path counts from what-dir when the database is opened, and keeps meaning that file")
    void countsFromWhatDir() throws IOException {
        Files.createDirectories(directory.resolve("sub"));

        assertThat(answerTo("""
                change-dir %sub/
                db: s/open %inner.db
                change-dir %..
                s/exec db {create table t(a)}
                reduce [0 < size? %sub/inner.db exists? %inner.db]""")).isEqualTo("[#(true) _]");
    }

    @Test
    @DisplayName("a missing directory, or a directory itself, cannot be opened")
    void refusesWhatIsNotAFile() throws IOException {
        Files.createDirectories(directory.resolve("sub"));

        assertThat(answerTo("reduce [fail-of [s/open %no/such/dir/x.db] fail-of [s/open %sub/]]")).isEqualTo("""
                [[Command command-fail "[SQLITE] unable to open database file"] [Command command-fail "[SQLITE] unable to open database file"]]""");
    }

    @Test
    @DisplayName("a file that is not a database opens, and fails a statement only when it would read the file")
    void opensSomethingElse() {
        assertThat(answerTo("""
                write %text.db {This is not a database, only some words in a file.}
                db: s/open %text.db
                reduce [s/eval db {select 1} fail-of [s/exec db {select * from sqlite_master}]]""")).isEqualTo("""
                [[1] [Command command-fail {[SQLITE] file is not a database file is not a database}]]""");
    }

    @Test
    @DisplayName("an empty file is an empty database")
    void opensAnEmptyFile() {
        assertThat(answerTo("""
                write %empty.db #{}
                db: s/open %empty.db
                reduce [s/eval db {select count(*) from sqlite_master} unset? s/exec db {create table t(a)} 0 < size? %empty.db]"""))
                .isEqualTo("[[0] #(true) #(true)]");
    }

    @Test
    @DisplayName(":memory: and the empty name never touch a file")
    void keepsMemoryInMemory() {
        assertThat(answerTo("""
                a: s/open to file! {:memory:}
                b: s/open %""
                s/exec a {create table t(a)}
                s/exec b {create table t(a)}
                reduce [exists? to file! {:memory:} read %./]""")).isEqualTo("[_ []]");
    }

    @Test
    @DisplayName("SECURE refuses an open it would refuse READ, where the C never asks it")
    void obeysSecure() {
        assertThat(answerTo("""
                db: s/open %allowed.db
                s/exec db {create table t(a)}
                s/close db
                secure [file throw]
                fail-of [s/open %allowed.db]""")).isEqualTo("[Access security %/allowed.db]");
    }

    @Test
    @DisplayName("SECURE that forbids writing refuses a database's write")
    void obeysSecureOnWrite() {
        assertThat(answerTo("""
                db: s/open %allowed.db
                s/exec db {create table t(a)}
                secure [file [allow read throw write]]
                fail-of [s/exec db {insert into t values (1)}]""")).isEqualTo("[Access security %/allowed.db]");
    }

    @Test
    @DisplayName("without the files grant, open is refused as READ is")
    void needsTheGrant() {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withFiles(FileSystemPort.rootFileSystem(directory)),
                OutputPort.discard(),
                Bounds.standard().add(new SqliteOnTheJvm().provide()));
        String source = PRELUDE + "reduce [fail-of [s/open %a.db] equal? fail-of [s/open %a.db] fail-of [read %a.db]]";
        interpreter.defineNewWordsIn(source);

        assertThat(interpreter.display(interpreter.run(source)))
                .startsWith("[[Access no-service files")
                .endsWith("#(true)]");
    }
}
